package transfer

import (
	"context"
	"io"
	"math/rand"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// --- claim table units --------------------------------------------------

func claimEntry(t *testing.T, size int64) *fileEntry {
	t.Helper()
	e := &fileEntry{meta: protocol.FileMeta{Size: size}}
	e.cond = sync.NewCond(&e.mu)
	return e
}

func TestClaims(t *testing.T) {
	e := claimEntry(t, 1000)

	if !e.tryClaim(100, 200, "a") {
		t.Fatal("first claim rejected")
	}
	if e.tryClaim(150, 250, "b") {
		t.Fatal("overlapping claim accepted")
	}
	if !e.tryClaim(200, 300, "b") {
		t.Fatal("touching (non-overlapping) claim rejected")
	}
	e.releaseClaims("a")
	if !e.tryClaim(100, 200, "c") {
		t.Fatal("released range not claimable")
	}

	owners := e.revokeClaimsOverlapping(150, 260, "c")
	if len(owners) != 1 || owners[0] != "b" {
		t.Fatalf("revoke returned %v, want [b]", owners)
	}
	// c's claim overlapped the revoked range but was exempt.
	if e.tryClaim(100, 200, "d") {
		t.Fatal("exempt owner's claim was revoked")
	}
	if !e.tryClaim(200, 300, "d") {
		t.Fatal("revoked range not claimable")
	}
}

func TestNextUnclaimedGap(t *testing.T) {
	e := claimEntry(t, 1000)
	e.add(0, 100)   // have [0,100)
	e.add(500, 600) // have [500,600)

	// No claims: identical to the plain gap.
	if lo, hi, ok := e.nextUnclaimedGapLocked(0); !ok || lo != 100 || hi != 500 {
		t.Fatalf("plain gap = %d,%d,%v", lo, hi, ok)
	}

	// A claim at the gap front pushes the start past it.
	e.tryClaim(100, 200, "a")
	if lo, hi, ok := e.nextUnclaimedGapLocked(0); !ok || lo != 200 || hi != 500 {
		t.Fatalf("front-claimed gap = %d,%d,%v", lo, hi, ok)
	}

	// A claim mid-gap truncates the end.
	e.tryClaim(300, 400, "b")
	if lo, hi, ok := e.nextUnclaimedGapLocked(0); !ok || lo != 200 || hi != 300 {
		t.Fatalf("mid-claimed gap = %d,%d,%v", lo, hi, ok)
	}

	// Whole first gap claimed: selection skips to the next file gap.
	e.tryClaim(200, 300, "c")
	e.tryClaim(400, 500, "d")
	if lo, hi, ok := e.nextUnclaimedGapLocked(0); !ok || lo != 600 || hi != 1000 {
		t.Fatalf("skip-to-next gap = %d,%d,%v", lo, hi, ok)
	}

	// Everything claimed: no unclaimed gap, though bytes are missing.
	e.tryClaim(600, 1000, "e")
	if _, _, ok := e.nextUnclaimedGapLocked(0); ok {
		t.Fatal("fully-claimed file still reported an unclaimed gap")
	}
	if _, _, missing := e.nextGapLocked(0); !missing {
		t.Fatal("claims must not make missing bytes look present")
	}
}

func TestNextUnclaimedGapWithin(t *testing.T) {
	e := claimEntry(t, 1000)
	e.add(0, 100)
	// Source covers [300,500) and [800,900) only.
	within := [][2]int64{{300, 500}, {800, 900}}
	if lo, hi, ok := e.nextUnclaimedGapWithinLocked(0, within); !ok || lo != 300 || hi != 500 {
		t.Fatalf("within gap = %d,%d,%v", lo, hi, ok)
	}
	e.tryClaim(300, 500, "a")
	if lo, hi, ok := e.nextUnclaimedGapWithinLocked(0, within); !ok || lo != 800 || hi != 900 {
		t.Fatalf("claim-skipping within gap = %d,%d,%v", lo, hi, ok)
	}
	e.add(800, 900)
	if _, _, ok := e.nextUnclaimedGapWithinLocked(0, within); ok {
		t.Fatal("nothing fetchable from this source, but a gap was returned")
	}
}

// --- multi-source loopback ----------------------------------------------

// newSignalerMesh wires n in-process endpoints where anyone can signal
// anyone, generalizing newSignalerPair.
func newSignalerMesh(ids ...string) map[string]*chanSignaler {
	m := map[string]*chanSignaler{}
	for _, id := range ids {
		m[id] = &chanSignaler{self: id, in: make(chan AddressedSignal, 16)}
	}
	for _, s := range m {
		s.out = map[string]*chanSignaler{}
		for id2, s2 := range m {
			if id2 != s.self {
				s.out[id2] = s2
			}
		}
	}
	return m
}

// joinerHub demultiplexes the joiner's inbox the way the app's onSignal
// does: offers from the current host feed the primary signaler, offers
// from anyone else feed that peer's swarm channel (opened by sigFor).
type joinerHub struct {
	mesh *chanSignaler
	host string

	mu      sync.Mutex
	primary chan AddressedSignal
	perPeer map[string]chan AddressedSignal
	done    chan struct{}
}

func newJoinerHub(mesh *chanSignaler, host string) *joinerHub {
	h := &joinerHub{
		mesh: mesh, host: host,
		primary: make(chan AddressedSignal, 8),
		perPeer: map[string]chan AddressedSignal{},
		done:    make(chan struct{}),
	}
	go func() {
		for {
			select {
			case as := <-mesh.in:
				h.mu.Lock()
				var ch chan AddressedSignal
				if as.From == h.host {
					ch = h.primary
				} else {
					ch = h.perPeer[as.From]
				}
				h.mu.Unlock()
				if ch != nil {
					select {
					case ch <- as:
					default:
					}
				}
			case <-h.done:
				return
			}
		}
	}()
	return h
}

func (h *joinerHub) close() { close(h.done) }

type hubSignaler struct {
	h  *joinerHub
	ch chan AddressedSignal
}

func (s *hubSignaler) SendSignal(ctx context.Context, to string, sig protocol.Signal) error {
	return s.h.mesh.SendSignal(ctx, to, sig)
}
func (s *hubSignaler) Signals() <-chan AddressedSignal { return s.ch }

func (h *joinerHub) primarySignaler() Signaler { return &hubSignaler{h: h, ch: h.primary} }

func (h *joinerHub) sigFor(peer string) (Signaler, func()) {
	ch := make(chan AddressedSignal, 8)
	h.mu.Lock()
	h.perPeer[peer] = ch
	h.mu.Unlock()
	return &hubSignaler{h: h, ch: ch}, func() {
		h.mu.Lock()
		if h.perPeer[peer] == ch {
			delete(h.perPeer, peer)
		}
		h.mu.Unlock()
	}
}

// throttledSeeker rate-limits reads, standing in for a host whose upload
// is the bottleneck the swarm exists to relieve.
type throttledSeeker struct {
	f     io.ReadSeeker
	delay time.Duration // per 16 KiB read
}

func (t *throttledSeeker) Seek(off int64, whence int) (int64, error) { return t.f.Seek(off, whence) }
func (t *throttledSeeker) Read(p []byte) (int, error) {
	time.Sleep(t.delay)
	return t.f.Read(p)
}

// swarmScene wires the standard three-party scenario: a (throttled) host
// serving src, a seeder peer with a full verified copy, and a joiner
// downloading with the swarm enabled.
type swarmScene struct {
	data   []byte
	meta   protocol.FileMeta
	store  *FileStore
	dl     *Downloader
	hub    *joinerHub
	cancel context.CancelFunc
	ctx    context.Context
}

func newSwarmScene(t *testing.T, size int, hostDelay time.Duration, seederData []byte, seederSpans [][2]int64) *swarmScene {
	t.Helper()
	// Small pieces, a small primary stride, and a fast manager keep the
	// test quick at test-sized files; production values only change
	// scale, not logic.
	origPG, origTick, origIdle, origStride := pieceGroups, auxManagerTick, auxIdleTick, minPrimaryStride
	pieceGroups, auxManagerTick, auxIdleTick, minPrimaryStride = 1, 50*time.Millisecond, 50*time.Millisecond, 256<<10
	t.Cleanup(func() {
		pieceGroups, auxManagerTick, auxIdleTick, minPrimaryStride = origPG, origTick, origIdle, origStride
	})

	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, size)
	rand.New(rand.NewSource(99)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	meta, hostOb := baoMetaFor(t, src, "media.bin")
	h, err := HashForServe(src)
	if err != nil {
		t.Fatal(err)
	}

	if hostDelay > 0 {
		orig := wrapServeReader
		wrapServeReader = func(f *os.File) io.ReadSeeker {
			if f.Name() == src {
				return &throttledSeeker{f: f, delay: hostDelay}
			}
			return f
		}
		t.Cleanup(func() { wrapServeReader = orig })
	}

	mesh := newSignalerMesh("host", "seeder", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)

	// Seeder: a peer holding seederData with the given coverage (defaults
	// to the genuine file, fully covered).
	if seederData == nil {
		seederData = data
	}
	if seederSpans == nil {
		seederSpans = [][2]int64{{0, meta.Size}}
	}
	seederStore := storeWith(t, meta, seederData, seederSpans)
	seederStore.files[0].setOutboard(h.Outboard)

	// Serves are request-driven, exactly like production: each file-req
	// spawns one persistent serve from the requested peer. (Free-running
	// serve loops desynchronize the offer/answer pairing — an offer sent
	// before the requester is listening gets dropped, and both sides then
	// wait out full timeouts.)
	quiet := func(string, ...any) {}
	var wg sync.WaitGroup
	sendReq := func(c context.Context, peer string, i int, _, _ int64) error {
		wg.Add(1)
		go func() {
			defer wg.Done()
			switch peer {
			case "host":
				_ = ServeFile(ctx, mesh["host"], "joiner", src, noICE(), quiet, nil, nil, hostOb, nil, 0)
			case "seeder":
				_ = ServeFromStore(ctx, mesh["seeder"], "joiner", seederStore, 0, noICE(), quiet, nil, nil, nil)
			}
		}()
		return nil
	}

	// Joiner with the swarm enabled.
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	hub := newJoinerHub(mesh["joiner"], "host")
	dl := NewDownloader(hub.primarySignaler(), "host", store, noICE(), sendReq, func(string, ...any) {})
	dl.EnableSwarm(hub.sigFor)

	t.Cleanup(func() {
		cancel()
		wg.Wait()
		hub.close()
	})
	return &swarmScene{data: data, meta: meta, store: store, dl: dl, hub: hub, cancel: cancel, ctx: ctx}
}

func (sc *swarmScene) run(t *testing.T) error {
	t.Helper()
	err := sc.dl.Run(sc.ctx)
	if err == nil {
		got, rerr := os.ReadFile(sc.store.PathFor(0))
		if rerr != nil {
			t.Fatal(rerr)
		}
		if string(got) != string(sc.data) {
			t.Fatal("assembled file differs from source")
		}
	}
	return err
}

// TestSwarmFetchesFromTwoSources: with a slow host and a fast seeder
// advertising a full copy, the download completes correct with real
// bytes from both.
func TestSwarmFetchesFromTwoSources(t *testing.T) {
	sc := newSwarmScene(t, 4<<20, 4*time.Millisecond, nil, nil) // host ~4 MB/s
	sc.dl.UpdatePeerHave("seeder", nil, [][2]int{{0, 1}})

	if err := sc.run(t); err != nil {
		t.Fatalf("swarm download failed: %v", err)
	}
	src := sc.dl.swarm.sources["seeder"]
	if src == nil || src.got.Load() == 0 {
		t.Fatal("the seeder contributed no bytes — swarm never engaged")
	}
	if src.poisoned {
		t.Fatal("an honest seeder was poisoned")
	}
}

// TestSwarmCorruptSeederPoisoned: a seeder serving garbage is caught by
// per-chunk verification, permanently dropped, and the file still
// completes correct from the host.
func TestSwarmCorruptSeederPoisoned(t *testing.T) {
	// Flip one bit in EVERY 256 KiB verification group, so any group the
	// seeder manages to serve fails verification — the test can't pass by
	// the seeder happening to serve only clean regions.
	corrupt := make([]byte, 4<<20)
	rand.New(rand.NewSource(99)).Read(corrupt) // same seed = same data...
	for off := 0; off < len(corrupt); off += 256 << 10 {
		corrupt[off] ^= 0x01
	}

	sc := newSwarmScene(t, 4<<20, 2*time.Millisecond, corrupt, nil)
	sc.dl.UpdatePeerHave("seeder", nil, [][2]int{{0, 1}})

	if err := sc.run(t); err != nil {
		t.Fatalf("download failed despite an honest host: %v", err)
	}
	deadline := time.After(5 * time.Second)
	for {
		sc.dl.swarm.mu.Lock()
		src := sc.dl.swarm.sources["seeder"]
		poisoned := src != nil && src.poisoned
		var got int64
		if src != nil {
			got = src.got.Load()
		}
		sc.dl.swarm.mu.Unlock()
		if poisoned {
			return
		}
		select {
		case <-deadline:
			// Every group is corrupt, so serving even one complete group
			// must have poisoned the source; only a seeder that never got
			// to serve anything may remain clean.
			if got > 0 {
				t.Fatalf("seeder served %d corrupt bytes without being poisoned", got)
			}
			return
		case <-time.After(20 * time.Millisecond):
		}
	}
}

// TestSwarmStaleAdvertisementNak: a seeder advertising a full copy while
// holding only half of it gets nak'd and backed off; the download still
// completes from the host.
func TestSwarmStaleAdvertisementNak(t *testing.T) {
	sc := newSwarmScene(t, 4<<20, time.Millisecond, nil, [][2]int64{{0, 2 << 20}})
	sc.dl.UpdatePeerHave("seeder", nil, [][2]int{{0, 1}}) // claims done; has half

	if err := sc.run(t); err != nil {
		t.Fatalf("download failed despite an honest host: %v", err)
	}
}

// TestSwarmStallingSeeder: a seeder that answers the handshake and then
// goes silent forever must not wedge the download — its claim is
// released by the stall watchdog and the host finishes.
func TestSwarmStallingSeeder(t *testing.T) {
	origTimeout, origInterval := connectTimeout, stallCheckInterval
	connectTimeout = 500 * time.Millisecond
	stallCheckInterval = 25 * time.Millisecond
	t.Cleanup(func() { connectTimeout, stallCheckInterval = origTimeout, origInterval })

	origPG, origTick, origIdle := pieceGroups, auxManagerTick, auxIdleTick
	pieceGroups, auxManagerTick, auxIdleTick = 1, 50*time.Millisecond, 50*time.Millisecond
	t.Cleanup(func() { pieceGroups, auxManagerTick, auxIdleTick = origPG, origTick, origIdle })

	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, 2<<20)
	rand.New(rand.NewSource(77)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	meta, hostOb := baoMetaFor(t, src, "media.bin")

	mesh := newSignalerMesh("host", "staller", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	hostDone := make(chan struct{})
	go func() {
		defer close(hostDone)
		for ctx.Err() == nil {
			_ = ServeFile(ctx, mesh["host"], "joiner", src, noICE(), func(string, ...any) {}, nil, nil, hostOb, nil, 0)
		}
	}()
	stallerDone := make(chan struct{})
	go func() {
		defer close(stallerDone)
		for ctx.Err() == nil {
			_ = stallingHost(ctx, mesh["staller"], "joiner", data[:chunkSize])
		}
	}()

	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	hub := newJoinerHub(mesh["joiner"], "host")
	dl := NewDownloader(hub.primarySignaler(), "host", store, noICE(), nil, func(string, ...any) {})
	dl.EnableSwarm(hub.sigFor)
	dl.UpdatePeerHave("staller", nil, [][2]int{{0, 1}})
	t.Cleanup(func() {
		cancel()
		<-hostDone
		<-stallerDone
		hub.close()
	})

	if err := dl.Run(ctx); err != nil {
		t.Fatalf("stalling seeder wedged the download: %v", err)
	}
	got, err := os.ReadFile(store.PathFor(0))
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("assembled file differs from source")
	}
}

// TestSwarmZeroSourcesFallsBack: swarm enabled but nobody advertises —
// the download must complete exactly like the single-source path, with
// no aux machinery engaged.
func TestSwarmZeroSourcesFallsBack(t *testing.T) {
	sc := newSwarmScene(t, 1<<20, 0, nil, nil)
	// No UpdatePeerHave: the coverage table stays empty.
	if err := sc.run(t); err != nil {
		t.Fatalf("download failed: %v", err)
	}
	sc.dl.swarm.mu.Lock()
	n := len(sc.dl.swarm.sources)
	sc.dl.swarm.mu.Unlock()
	if n != 0 {
		t.Fatalf("aux sources were dialed with an empty coverage table: %d", n)
	}
}
