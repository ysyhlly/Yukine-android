package transfer

import (
	"context"
	"errors"
	"fmt"
	"math"
	"math/rand"
	"os"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"github.com/pion/webrtc/v4"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// randUint32 breaks ties between equally-rare pieces. math/rand's global
// is concurrency-safe and auto-seeded per process, so different joiners
// diverge and don't all herd onto the same rarest piece. A package var so
// tests can make the shuffle deterministic.
var randUint32 = rand.Uint32

// Swarm coverage bookkeeping: what this peer advertises to the room
// (AdvertSnapshot, attached to every heartbeat) and what it knows about
// everyone else's coverage (peerCoverage, fed from their heartbeats).
// The multi-source scheduler consumes the table; until it does, this is
// inert plumbing observable only in the debug log.

// pieceGroups is how many bao chunk groups form one advertised piece —
// 16 groups of 256 KiB = 4 MiB pieces, the granularity of coverage
// advertisements and (later) swarm claims. Wire-level: every peer derives
// the same piece boundaries from the announced BaoGroup, so changing this
// is a protocol break. A var only for test-shrinkability.
var pieceGroups int64 = 16

// pieceBytes is the advertisement piece size for this file, or 0 in a
// legacy room (no group size ⇒ no piece math, and nothing to advertise).
func (e *fileEntry) pieceBytes() int64 {
	if e.bao == nil {
		return 0
	}
	return pieceGroups * e.bao.groupBytes
}

// AdvertSnapshot converts the store's verified coverage into the compact
// heartbeat advertisement: piece spans per still-downloading file, and
// [lo,hi) index ranges of fully completed files. It is conservative by
// construction — only whole verified pieces are claimed (the file's tail
// piece counts once coverage reaches EOF), and coverage that doesn't fit
// the wire caps is coarsened by dropping the smallest spans, never
// overstated.
func (s *FileStore) AdvertSnapshot() ([]protocol.HaveEntry, [][2]int) {
	var have []protocol.HaveEntry
	var done [][2]int
	runStart := -1
	closeRun := func(end int) {
		if runStart >= 0 && len(done) < protocol.MaxHaveDone {
			done = append(done, [2]int{runStart, end})
		}
		runStart = -1
	}
	for i, e := range s.files {
		e.mu.Lock()
		isDone := e.done
		pb := e.pieceBytes()
		size := e.meta.Size
		var pieces [][2]int
		if !isDone && pb > 0 {
			for _, sp := range e.have {
				pLo := int((sp.lo + pb - 1) / pb)
				var pHi int
				if sp.hi == size {
					pHi = int((sp.hi + pb - 1) / pb) // the short tail piece is complete at EOF
				} else {
					pHi = int(sp.hi / pb)
				}
				if pHi <= pLo {
					continue
				}
				if n := len(pieces); n > 0 && pieces[n-1][1] == pLo {
					pieces[n-1][1] = pHi // touching spans merge
				} else {
					pieces = append(pieces, [2]int{pLo, pHi})
				}
			}
		}
		e.mu.Unlock()

		if isDone {
			if runStart < 0 {
				runStart = i
			}
			continue
		}
		closeRun(i)
		if len(pieces) == 0 || len(have) >= protocol.MaxHaveFiles {
			continue
		}
		have = append(have, protocol.HaveEntry{File: i, Pieces: coarsenPieces(pieces, protocol.MaxHavePieces)})
	}
	closeRun(len(s.files))
	return have, done
}

// coarsenPieces drops the narrowest spans until at most max remain —
// under-advertising is always safe; a peer that wants the dropped pieces
// simply won't ask us for them.
func coarsenPieces(pieces [][2]int, max int) [][2]int {
	if len(pieces) <= max {
		return pieces
	}
	byWidth := make([][2]int, len(pieces))
	copy(byWidth, pieces)
	sort.Slice(byWidth, func(a, b int) bool {
		return byWidth[a][1]-byWidth[a][0] > byWidth[b][1]-byWidth[b][0]
	})
	keep := byWidth[:max]
	sort.Slice(keep, func(a, b int) bool { return keep[a][0] < keep[b][0] })
	return keep
}

// peerCov is one peer's advertised coverage: completed flat file indices,
// and verified piece spans for files it is still downloading.
type peerCov struct {
	done   map[int]bool
	pieces map[int][][2]int
}

// peerCoverage aggregates every peer's latest advertisement. Written from
// the engine loop (UpdatePeerHave/PeerGone via heartbeats), read by the
// scheduler.
type peerCoverage struct {
	mu     sync.Mutex
	byPeer map[string]peerCov
}

// UpdatePeerHave replaces peer's advertised coverage with its latest
// heartbeat's. Bounds were enforced at the decode boundary
// (protocol.validate), so the table's size is capped per peer.
func (d *Downloader) UpdatePeerHave(peer string, have []protocol.HaveEntry, doneRanges [][2]int) {
	cov := peerCov{done: make(map[int]bool), pieces: make(map[int][][2]int, len(have))}
	total := 0
	for _, r := range doneRanges {
		for i := r[0]; i < r[1]; i++ {
			cov.done[i] = true
		}
	}
	for _, h := range have {
		cov.pieces[h.File] = h.Pieces
		for _, p := range h.Pieces {
			total += p[1] - p[0]
		}
	}
	d.cov.mu.Lock()
	if d.cov.byPeer == nil {
		d.cov.byPeer = make(map[string]peerCov)
	}
	d.cov.byPeer[peer] = cov
	d.cov.mu.Unlock()
	d.log.E("peer_have", "peer", peer[:min(8, len(peer))], "done", len(cov.done), "files", len(have), "pieces", total)
}

// PeerGone drops peer's coverage (evicted, left, or kicked) so the
// scheduler stops considering it a source.
func (d *Downloader) PeerGone(peer string) {
	d.cov.mu.Lock()
	delete(d.cov.byPeer, peer)
	d.cov.mu.Unlock()
	d.log.E("peer_cov_gone", "peer", peer[:min(8, len(peer))])
}

// --- multi-source scheduling -------------------------------------------
//
// One primary source (the host; SetHost retargets it on migration) works
// the front of the playhead window exactly as the single-source code
// always has. Up to maxSourcesPerFile-1 aux sources — peers advertising
// verified coverage — fetch piece-sized claims alongside it: first
// helping inside the window (leapfrogging behind the primary's stride),
// then rarest-first beyond it. The claim table on fileEntry keeps them
// off each other's ranges; per-source preempt channels redirect each
// independently on a seek. Aux failures only ever cost their own claims:
// connect errors back the source off, verification failures poison it
// permanently, and with zero aux connected the primary path is
// byte-identical to the pre-swarm downloader.

// Tunables — vars per the package's test-shrinkability convention.
var (
	maxSourcesPerFile = 3                      // 1 primary + 2 aux
	auxConnectTimeout = 10 * time.Second       // aux dial budget; no TURN escalation
	auxIdleTick       = 500 * time.Millisecond // re-plan cadence when nothing to claim
	auxManagerTick    = time.Second            // how often new sources are considered
	// The primary's per-fetch range is capped at ~primaryStrideSecs of its
	// own measured throughput while any aux is connected, so aux claims
	// can land inside the window instead of behind one giant fetch. The
	// eof→start turnaround costs ~1 RTT per stride — a few percent.
	primaryStrideSecs int64 = 4
	minPrimaryStride  int64 = 8 << 20
	maxPrimaryStride  int64 = 64 << 20
)

// sourceState tracks one aux peer across connection attempts. rate is
// atomic (written from the connection's OnMessage-side progress ticks,
// read by the aggregate); the rest is guarded by swarmState.mu.
type sourceState struct {
	pub     string
	preempt chan struct{}
	rate    atomic.Uint64 // float64 bits; this source's smoothed bytes/sec
	got     atomic.Int64  // total bytes ever received from this source

	fails        int
	backoffUntil time.Time
	poisoned     bool // failed verification: never used again
	active       bool
}

type swarmState struct {
	mu      sync.Mutex
	sources map[string]*sourceState
	auxLive atomic.Int32 // aux sources with an open, serving connection
}

// auxLiveCount reports how many aux connections are currently open.
func (d *Downloader) auxLiveCount() int {
	if d.swarm == nil {
		return 0
	}
	return int(d.swarm.auxLive.Load())
}

// strideCapActive reports whether the primary should cap its per-fetch
// range. It keys on peers *advertising* useful coverage, not on open aux
// connections: the primary claims its next range before the first aux
// finishes dialing, and an uncapped claim over the whole remaining gap
// would leave a fresh aux nothing to help with until the next decision.
// The cost when every dial fails is just stride-sized fetches from the
// host — one extra eof/start round-trip per stride.
func (d *Downloader) strideCapActive(i int) bool {
	if d.swarm == nil {
		return false
	}
	if d.swarm.auxLive.Load() > 0 {
		return true
	}
	host := d.GetHost()
	d.cov.mu.Lock()
	defer d.cov.mu.Unlock()
	for pub, cov := range d.cov.byPeer {
		if pub == host {
			continue
		}
		if cov.done[i] || len(cov.pieces[i]) > 0 {
			return true
		}
	}
	return false
}

// primaryStride is the cap on one primary fetch while aux sources help.
func (d *Downloader) primaryStride() int64 {
	s := int64(math.Float64frombits(d.primaryRate.Load())) * primaryStrideSecs
	if s < minPrimaryStride {
		return minPrimaryStride
	}
	if s > maxPrimaryStride {
		return maxPrimaryStride
	}
	return s
}

// reportPrimaryRate feeds the primary connection's throughput EMA into
// the aggregate (or straight through, pre-swarm).
func (d *Downloader) reportPrimaryRate(r float64) {
	d.primaryRate.Store(math.Float64bits(r))
	d.recomputeRate()
}

// recomputeRate refreshes the downloader-wide rate (window sizing, /peers
// stall prediction) as the sum of every live source's smoothed rate.
func (d *Downloader) recomputeRate() {
	total := math.Float64frombits(d.primaryRate.Load())
	if d.swarm != nil {
		d.swarm.mu.Lock()
		for _, src := range d.swarm.sources {
			if src.active {
				total += math.Float64frombits(src.rate.Load())
			}
		}
		d.swarm.mu.Unlock()
	}
	d.rateEMA.Store(math.Float64bits(total))
}

// startAuxManager runs the source manager for file i until the returned
// stop func is called (idempotent). Stop waits for every aux goroutine to
// finish, so callers can hash/rename the file knowing nothing still
// writes it.
func (d *Downloader) startAuxManager(ctx context.Context, i int) func() {
	actx, cancel := context.WithCancel(ctx)
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		d.auxManager(actx, i, &wg)
	}()
	var once sync.Once
	return func() {
		once.Do(func() {
			cancel()
			wg.Wait()
			d.recomputeRate()
		})
	}
}

func (d *Downloader) auxManager(ctx context.Context, i int, wg *sync.WaitGroup) {
	t := time.NewTicker(auxManagerTick)
	defer t.Stop()
	for {
		for _, src := range d.pickSources(i) {
			wg.Add(1)
			go func(s *sourceState) {
				defer wg.Done()
				d.auxLoop(ctx, i, s)
			}(src)
		}
		select {
		case <-ctx.Done():
			return
		case <-t.C:
		}
	}
}

// pickSources chooses which advertising peers to dial for file i right
// now, marking them active. Eligible: advertising useful coverage, not
// the current primary, not poisoned, not backed off, not already active.
// Ranked by completed copy first, then broader advertised coverage, then
// fewer failures, then pubkey for determinism.
func (d *Downloader) pickSources(i int) []*sourceState {
	host := d.GetHost()
	type cand struct {
		pub    string
		done   bool
		pieces int
	}
	var cands []cand
	d.cov.mu.Lock()
	for pub, cov := range d.cov.byPeer {
		if pub == host {
			continue
		}
		n := 0
		for _, p := range cov.pieces[i] {
			n += p[1] - p[0]
		}
		if cov.done[i] || n > 0 {
			cands = append(cands, cand{pub: pub, done: cov.done[i], pieces: n})
		}
	}
	d.cov.mu.Unlock()
	sort.Slice(cands, func(a, b int) bool {
		if cands[a].done != cands[b].done {
			return cands[a].done
		}
		if cands[a].pieces != cands[b].pieces {
			return cands[a].pieces > cands[b].pieces
		}
		return cands[a].pub < cands[b].pub
	})

	now := time.Now()
	var picked []*sourceState
	d.swarm.mu.Lock()
	defer d.swarm.mu.Unlock()
	live := 0
	for _, src := range d.swarm.sources {
		if src.active {
			live++
		}
	}
	for _, c := range cands {
		if live+len(picked) >= maxSourcesPerFile-1 {
			break
		}
		src := d.swarm.sources[c.pub]
		if src == nil {
			src = &sourceState{pub: c.pub, preempt: make(chan struct{}, 1)}
			d.swarm.sources[c.pub] = src
		}
		if src.active || src.poisoned || now.Before(src.backoffUntil) {
			continue
		}
		src.active = true
		picked = append(picked, src)
	}
	return picked
}

// auxLoop runs one aux source's life: dial, fetch claims until the file
// completes or something breaks, then record the outcome for the
// manager's next pick.
func (d *Downloader) auxLoop(ctx context.Context, i int, src *sourceState) {
	pub8 := src.pub[:min(8, len(src.pub))]
	d.log.E("swarm_source_connect", "peer", pub8, "file", i)
	err := d.runAux(ctx, i, src)
	d.swarm.mu.Lock()
	src.active = false
	src.rate.Store(0)
	if err != nil && ctx.Err() == nil {
		var vErr verifyError
		if errors.As(err, &vErr) {
			// Bad bytes or a forged tree: this peer is never trusted again.
			src.poisoned = true
		}
		src.fails++
		shift := src.fails
		if shift > 5 {
			shift = 5
		}
		src.backoffUntil = time.Now().Add(time.Duration(1<<shift) * time.Second)
		poisoned := src.poisoned
		d.swarm.mu.Unlock()
		if poisoned {
			d.log.E("swarm_source_poisoned", "peer", pub8, "file", i, "err", err.Error())
		} else {
			d.log.E("swarm_source_fail", "peer", pub8, "file", i, "fails", src.fails, "err", err.Error())
		}
	} else {
		src.fails = 0
		d.swarm.mu.Unlock()
		d.log.E("swarm_source_done", "peer", pub8, "file", i)
	}
	d.recomputeRate()
}

func (d *Downloader) runAux(ctx context.Context, i int, src *sourceState) error {
	e := d.store.files[i]
	conn, err := d.connectAux(ctx, i, src)
	if err != nil {
		return err
	}
	d.swarm.auxLive.Add(1)
	defer d.swarm.auxLive.Add(-1)
	e.registerWriter(conn.f)
	owner := "aux:" + src.pub
	defer func() {
		src.got.Add(conn.got.Load())
		e.releaseClaims(owner)
		e.unregisterWriter(conn.f)
		conn.close()
	}()
	d.log.E("swarm_source_open", "peer", src.pub[:min(8, len(src.pub))], "file", i)

	for {
		if ctx.Err() != nil {
			return nil
		}
		if src.pub == d.GetHost() {
			// A migration just made this peer the primary; bow out so the
			// primary connection (and the serve-side per-peer cap) isn't
			// contending with a duplicate aux connection to the same peer.
			conn.finish()
			return nil
		}
		// Discard a stale nudge before planning, mirroring drainPreempt's
		// ordering rationale for the primary.
		select {
		case <-src.preempt:
		default:
		}
		lo, hi, st := d.nextAuxRange(i, src)
		switch st {
		case auxDone:
			conn.finish()
			return nil
		case auxIdle:
			select {
			case <-src.preempt:
			case <-time.After(auxIdleTick):
			case <-conn.connFailed:
				return resumableError{fmt.Errorf("connection lost")}
			case <-ctx.Done():
				return nil
			}
			continue
		}
		if !e.tryClaim(lo, hi, owner) {
			continue // raced another source to the same gap; re-plan
		}
		ferr := d.fetchOn(ctx, conn, src.preempt, lo, hi-lo, false)
		e.releaseClaims(owner)
		if ferr == errPreempted {
			continue // a seek moved the priority; re-plan at once
		}
		if ferr != nil {
			return ferr // auxLoop classifies (verify failures poison)
		}
	}
}

// connectAux dials one aux source for file i: the same request/answer
// handshake as the primary's connect, minus everything host-specific —
// no SetHost cancellation, no TURN escalation (an unreachable aux peer
// just isn't used), and a shorter dial budget. The connection writes
// through its own file handle, registered with the entry so checkpoints
// sync it.
func (d *Downloader) connectAux(ctx context.Context, i int, src *sourceState) (*fileConn, error) {
	e := d.store.files[i]
	f, err := os.OpenFile(e.partPath, os.O_RDWR, 0o644)
	if err != nil {
		return nil, err
	}
	sig, release := d.sigFor(src.pub)
	defer release()
	if d.sendReq != nil {
		if err := d.sendReq(ctx, src.pub, i, 0, 0); err != nil {
			f.Close()
			return nil, err
		}
	}
	pc, err := d.ice.newPeerConnection()
	if err != nil {
		f.Close()
		return nil, err
	}
	connFailed := watchConnection(pc)
	conn := &fileConn{
		pc: pc, f: f, idx: i, entry: e, connFailed: connFailed, log: d.log,
		eofCh: make(chan int64, 4), errCh: make(chan error, 1),
		printf: func(string, ...any) {}, lastPrint: time.Now(),
		// emit stays nil: the progress bar follows the primary; aux bytes
		// still appear in its "received" total through e.have.
		reportRate: func(r float64) {
			src.rate.Store(math.Float64bits(r))
			d.recomputeRate()
		},
	}
	if e.bao != nil {
		if ob := e.getOutboard(); ob != nil {
			conn.verifier.Store(newGroupVerifier(*e.bao, ob))
		} else {
			conn.obWant.Store(int64(e.bao.obLen))
			conn.obCh = make(chan obFetch, 1)
			conn.verifier.Store(newGroupVerifier(*e.bao, nil)) // fail-closed until the tree arrives
		}
	}

	opened := make(chan struct{})
	var once sync.Once
	pc.OnDataChannel(func(dc *webrtc.DataChannel) {
		conn.dc = dc
		dc.OnOpen(func() { once.Do(func() { close(opened) }) })
		dc.OnMessage(conn.onMessage)
	})

	actx, cancel := context.WithTimeout(ctx, auxConnectTimeout)
	defer cancel()
	offer, err := waitSignal(actx, sig, src.pub, "offer")
	if err != nil {
		conn.close()
		return nil, err
	}
	if err := pc.SetRemoteDescription(webrtc.SessionDescription{Type: webrtc.SDPTypeOffer, SDP: offer.SDP}); err != nil {
		conn.close()
		return nil, err
	}
	answer, err := pc.CreateAnswer(nil)
	if err != nil {
		conn.close()
		return nil, err
	}
	if err := pc.SetLocalDescription(answer); err != nil {
		conn.close()
		return nil, err
	}
	if err := awaitGathering(actx, pc); err != nil {
		conn.close()
		return nil, err
	}
	if err := sig.SendSignal(ctx, src.pub, protocol.Signal{Kind: "answer", SDP: pc.LocalDescription().SDP}); err != nil {
		conn.close()
		return nil, err
	}
	select {
	case <-opened:
	case <-connFailed:
		conn.close()
		return nil, fmt.Errorf("couldn't connect to peer")
	case <-actx.Done():
		conn.close()
		return nil, fmt.Errorf("timed out connecting to peer")
	case <-ctx.Done():
		conn.close()
		return nil, ctx.Err()
	}
	if e.bao != nil {
		if err := d.ensureOutboard(ctx, conn, e); err != nil {
			conn.close()
			return nil, err
		}
		conn.verifier.Store(newGroupVerifier(*e.bao, e.getOutboard()))
	}
	return conn, nil
}

type auxStatus int

const (
	auxFound auxStatus = iota
	auxIdle
	auxDone
)

// nextAuxRange plans one aux fetch: (a) help with a pending seek, (b)
// help fill the playhead window behind the primary's stride, (c) fetch
// the rarest advertised piece beyond the window. All candidates are
// limited to ranges this source actually advertises, so an honest peer
// is never asked for bytes it lacks.
func (d *Downloader) nextAuxRange(i int, src *sourceState) (lo, hi int64, st auxStatus) {
	e := d.store.files[i]
	spans := d.sourceSpans(i, src.pub)
	rarest := d.rarestCandidates(i, src.pub)
	d.prioMu.Lock()
	prioFile, prioOff := d.prioFile, d.prioOff
	d.prioMu.Unlock()
	windowEnd := int64(-1)
	if d.playheadFn != nil {
		windowEnd = d.playheadFn(i) + d.windowBytes()
	}
	P := e.pieceBytes()
	G := e.bao.groupBytes

	e.mu.Lock()
	defer e.mu.Unlock()
	if _, _, anyGap := e.nextGapLocked(0); !anyGap {
		return 0, 0, auxDone
	}
	if len(spans) == 0 {
		return 0, 0, auxIdle
	}
	if prioFile == i && prioOff >= 0 {
		if l, h, ok := e.nextUnclaimedGapWithinLocked(alignDown(prioOff, G), spans); ok && l < prioOff+d.windowBytes() {
			return l, min(h, l+P), auxFound
		}
	}
	if l, h, ok := e.nextUnclaimedGapWithinLocked(0, spans); ok && (windowEnd < 0 || l < windowEnd) {
		return l, min(h, l+P), auxFound
	}
	if windowEnd >= 0 {
		for _, p := range rarest {
			pLo := int64(p) * P
			if pLo < windowEnd {
				continue
			}
			pHi := min(pLo+P, e.meta.Size)
			if l, h, ok := e.nextUnclaimedGapWithinLocked(pLo, [][2]int64{{pLo, pHi}}); ok {
				return l, h, auxFound
			}
		}
	}
	return 0, 0, auxIdle
}

// sourceSpans converts a peer's advertised coverage of file i into byte
// ranges. A completed peer covers everything.
func (d *Downloader) sourceSpans(i int, pub string) [][2]int64 {
	e := d.store.files[i]
	pb := e.pieceBytes()
	d.cov.mu.Lock()
	defer d.cov.mu.Unlock()
	cov, ok := d.cov.byPeer[pub]
	if !ok {
		return nil
	}
	if cov.done[i] {
		return [][2]int64{{0, e.meta.Size}}
	}
	if pb == 0 {
		return nil
	}
	var spans [][2]int64
	for _, p := range cov.pieces[i] {
		lo := int64(p[0]) * pb
		if lo >= e.meta.Size {
			continue
		}
		spans = append(spans, [2]int64{lo, min(int64(p[1])*pb, e.meta.Size)})
	}
	return spans
}

// rarestCandidates returns the pieces of file i that pub advertises,
// rarest first (fewest advertising peers; the host implicitly has all,
// hence the +1 floor). Ties among equally-rare pieces are broken
// randomly (not by index), so several joiners picking the same rarity
// don't all herd onto the lowest-numbered piece. Computed outside
// fileEntry's lock — cov.mu and e.mu are never held together.
func (d *Downloader) rarestCandidates(i int, pub string) []int {
	e := d.store.files[i]
	pb := e.pieceBytes()
	if pb == 0 {
		return nil
	}
	d.cov.mu.Lock()
	defer d.cov.mu.Unlock()
	cov, ok := d.cov.byPeer[pub]
	if !ok {
		return nil
	}
	var mine [][2]int
	if cov.done[i] {
		last := int((e.meta.Size + pb - 1) / pb)
		mine = [][2]int{{0, last}}
	} else {
		mine = cov.pieces[i]
	}
	rarity := func(p int) int {
		n := 1 // the host
		for _, other := range d.cov.byPeer {
			if other.done[i] {
				n++
				continue
			}
			for _, sp := range other.pieces[i] {
				if p >= sp[0] && p < sp[1] {
					n++
					break
				}
			}
		}
		return n
	}
	type pr struct {
		piece, r int
		tie      uint32 // random, to break ties without herding on low indices
	}
	var cands []pr
	for _, sp := range mine {
		for p := sp[0]; p < sp[1]; p++ {
			cands = append(cands, pr{piece: p, r: rarity(p), tie: randUint32()})
		}
	}
	sort.Slice(cands, func(a, b int) bool {
		if cands[a].r != cands[b].r {
			return cands[a].r < cands[b].r // rarest first
		}
		return cands[a].tie < cands[b].tie // random among equal rarity
	})
	out := make([]int, len(cands))
	for j, c := range cands {
		out[j] = c.piece
	}
	return out
}

// nudgeAux pokes every active aux source to re-plan (a seek arrived or
// the host changed), after revoking claims that overlap the now-urgent
// region so the primary isn't stuck behind a slower source's claim on
// the exact bytes mpv wants.
func (d *Downloader) nudgeAux(i int, pos int64) {
	if d.swarm == nil {
		return
	}
	if i >= 0 && i < d.store.Len() && pos >= 0 {
		e := d.store.files[i]
		e.revokeClaimsOverlapping(pos, pos+d.windowBytes(), "primary")
	}
	d.swarm.mu.Lock()
	for _, src := range d.swarm.sources {
		if src.active {
			select {
			case src.preempt <- struct{}{}:
			default:
			}
		}
	}
	d.swarm.mu.Unlock()
}
