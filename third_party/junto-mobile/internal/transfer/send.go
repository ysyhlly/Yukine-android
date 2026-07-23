package transfer

import (
	"context"
	"fmt"
	"io"
	"os"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"

	"github.com/swayam-mishra/junto/internal/debug"
	"github.com/swayam-mishra/junto/internal/protocol"
)

// OutboardFn supplies a file's bao outboard tree on demand, for serving
// "ob_req" frames. It may block (a promoted host computes the tree from
// its local file on first request); ctx bounds that wait. A nil OutboardFn
// or a nil/error result means the tree isn't available here and the
// request is nak'd — the joiner treats that as a retryable miss.
type OutboardFn func(ctx context.Context) ([]byte, error)

// serveSource is where a serve's bytes come from: a complete file on
// disk (the original host), or a FileStore holding a possibly-partial
// download (a streaming joiner serving what it already has). covered is
// nil when the whole [0,size) range is guaranteed present.
type serveSource struct {
	open     func() (io.ReadSeeker, int64, func() error, error) // reader, size, close
	covered  func(off int64) (end int64, ok bool)
	outboard OutboardFn
	// fair coordinates this node's uplink across its viewers so the
	// hungriest is served first; nil disables it (single-viewer, tests).
	// fileIdx scopes fairness to viewers of the same file.
	fair    *UploadFairness
	fileIdx int
}

// ServeFile sends path to one joiner over a single persistent WebRTC
// connection: offer -> answer -> serve byte ranges on demand until the
// joiner says it has the whole file (or disconnects). The joiner drives
// it with "start" frames (see ctrl); the host streams the requested
// range with backpressure and can be redirected mid-range by a newer
// "start" — so a seek takes effect without tearing the connection down.
// outboard serves the file's bao verification tree (nil for pre-swarm
// rooms). The host calls this in its own goroutine per joiner request.
// fair coordinates the host's uplink across its concurrent viewers (nil
// to disable); idx is the playlist index being served, used to scope
// fairness to viewers of the same file.
func ServeFile(ctx context.Context, sig Signaler, peer string, path string, ice ICEConfig, printf func(string, ...any), log *debug.Logger, reportRate func(bytesPerSec float64), outboard OutboardFn, fair *UploadFairness, idx int) error {
	src := serveSource{
		open: func() (io.ReadSeeker, int64, func() error, error) {
			f, err := os.Open(path)
			if err != nil {
				return nil, 0, nil, err
			}
			fi, err := f.Stat()
			if err != nil {
				f.Close()
				return nil, 0, nil, err
			}
			return wrapServeReader(f), fi.Size(), f.Close, nil
		},
		outboard: outboard,
		fair:     fair,
		fileIdx:  idx,
	}
	return serve(ctx, sig, peer, src, ice, printf, log, reportRate)
}

// ServeFromStore serves file idx out of a FileStore: the path a
// streaming joiner uses to feed other peers, whether it holds the whole
// file (a finished download — including after promotion to host) or only
// part of one. Requests outside the store's verified coverage are nak'd,
// and a range is clamped to the covered run it starts in, so the peer
// never receives bytes this store can't vouch for. The outboard tree it
// serves is the one it fetched and hash-checked itself.
func ServeFromStore(ctx context.Context, sig Signaler, peer string, store *FileStore, idx int, ice ICEConfig, printf func(string, ...any), log *debug.Logger, reportRate func(bytesPerSec float64), fair *UploadFairness) error {
	var outboard OutboardFn
	if ob := store.OutboardFor(idx); ob != nil {
		outboard = func(context.Context) ([]byte, error) { return ob, nil }
	}
	src := serveSource{
		open: func() (io.ReadSeeker, int64, func() error, error) {
			f, err := store.OpenFor(idx)
			if err != nil {
				return nil, 0, nil, err
			}
			// The store's fd survives the .part → final rename, and size
			// comes from the announced meta (the .part is pre-sized sparse,
			// so Stat would report the same thing).
			return wrapServeReader(f), store.Meta(idx).Size, f.Close, nil
		},
		covered:  func(off int64) (int64, bool) { return store.Available(idx, off) },
		outboard: outboard,
		fair:     fair,
		fileIdx:  idx,
	}
	return serve(ctx, sig, peer, src, ice, printf, log, reportRate)
}

// serve is the shared body of ServeFile/ServeFromStore: one persistent
// connection, byte ranges on demand until the peer says "done".
func serve(ctx context.Context, sig Signaler, peer string, src serveSource, ice ICEConfig, printf func(string, ...any), log *debug.Logger, reportRate func(bytesPerSec float64)) error {
	// SECURITY: never log the served path — it's an absolute host
	// filesystem path (leaks the username and what's being served). The
	// debug log is meant to be shareable; the peer prefix (an ephemeral
	// key) is enough.
	log.E("serve_start", "peer", peer[:min(8, len(peer))])
	start := time.Now()
	pc, err := ice.newPeerConnection()
	if err != nil {
		return fmt.Errorf("creating peer connection: %w", err)
	}
	defer pc.Close()
	connFailed := watchConnection(pc)

	dc, err := pc.CreateDataChannel("file", nil) // defaults: ordered + reliable
	if err != nil {
		return fmt.Errorf("creating data channel: %w", err)
	}

	opened := make(chan struct{})
	dc.OnOpen(func() { close(opened) })

	lowSignal := make(chan struct{}, 1)
	dc.SetBufferedAmountLowThreshold(bufferedLow)
	dc.OnBufferedAmountLow(func() {
		select {
		case lowSignal <- struct{}{}:
		default:
		}
	})

	// Enroll this serve in the node's upload-fairness set (no-op when
	// fair is nil), so it's ranked against the node's other viewers.
	fairID, fairRelease := src.fair.register(src.fileIdx)
	defer fairRelease()

	box := newCmdBox()
	done := make(chan struct{})
	var doneOnce sync.Once
	dc.OnMessage(func(msg webrtc.DataChannelMessage) {
		if !msg.IsString {
			return // host expects only control frames from the joiner
		}
		var c ctrl
		if err := unmarshalCtrl(msg.Data, &c); err != nil {
			return
		}
		switch c.T {
		case "start":
			box.put(startCmd{seq: c.Seq, off: c.Off, length: c.Len})
		case "ob_req":
			box.put(startCmd{seq: c.Seq, ob: true})
		case "stop":
			// Abandon the in-flight range (and any queued one) without a
			// successor — the requester released its claim on that region
			// and will issue a fresh "start" when it wants more.
			box.stop()
		case "buf":
			// The viewer's current buffer depth (bytes ahead of its
			// playhead), used to bias the uplink toward whoever's closest
			// to stalling.
			src.fair.report(fairID, c.Off)
		case "done":
			doneOnce.Do(func() { close(done) })
		}
	})

	offer, err := pc.CreateOffer(nil)
	if err != nil {
		return fmt.Errorf("creating offer: %w", err)
	}
	if err := pc.SetLocalDescription(offer); err != nil {
		return fmt.Errorf("setting local description: %w", err)
	}
	if err := awaitGathering(ctx, pc); err != nil {
		return fmt.Errorf("gathering ICE candidates: %w", err)
	}
	if err := sig.SendSignal(ctx, peer, protocol.Signal{Kind: "offer", SDP: pc.LocalDescription().SDP}); err != nil {
		return fmt.Errorf("sending offer: %w", err)
	}

	answer, err := waitSignal(ctx, sig, peer, "answer")
	if err != nil {
		return err
	}
	if err := pc.SetRemoteDescription(webrtc.SessionDescription{Type: webrtc.SDPTypeAnswer, SDP: answer.SDP}); err != nil {
		return fmt.Errorf("applying answer: %w", err)
	}

	select {
	case <-opened:
		log.E("serve_open", "peer", peer[:min(8, len(peer))], "ms", time.Since(start).Milliseconds())
	case <-connFailed:
		log.E("serve_failed", "peer", peer[:min(8, len(peer))], "reason", "nat")
		return fmt.Errorf("couldn't connect directly to %.8s — their network may be blocking peer-to-peer connections; they can retry with -turn, or fetch the files another way", peer)
	case <-time.After(connectTimeout):
		log.E("serve_failed", "peer", peer[:min(8, len(peer))], "reason", "timeout")
		return fmt.Errorf("timed out waiting for a direct connection to %.8s — their network may be blocking peer-to-peer connections; they can retry with -turn, or fetch the files another way", peer)
	case <-ctx.Done():
		return ctx.Err()
	}
	printf("* sending file to %.8s...", peer)

	f, size, closeF, err := src.open()
	if err != nil {
		_ = dc.SendText(marshalCtrl(ctrl{T: "err", Msg: "host could not open file"}))
		return fmt.Errorf("opening served file: %w", err)
	}
	defer closeF()

	s := &sender{dc: dc, f: f, size: size, box: box, lowSignal: lowSignal, connFailed: connFailed, reportRate: reportRate, lastReport: time.Now(), outboard: src.outboard, covered: src.covered, fair: src.fair, fairID: fairID}
	if err := s.run(ctx, done); err != nil {
		// A clean finish (joiner sent "done") or a teardown is not an error
		// to surface loudly; a read/IO failure is.
		if ctx.Err() == nil && err != errServeDone && !isConnLost(err) {
			_ = dc.SendText(marshalCtrl(ctrl{T: "err", Msg: "host read error"}))
			return err
		}
		if err == errServeDone {
			printf("* transfer to %.8s complete", peer)
			return nil
		}
		return err
	}
	return nil
}

// startCmd is a joiner request to (re)start streaming a byte range, or
// (ob) to stream the file's bao outboard tree instead.
type startCmd struct {
	seq    int64
	off    int64
	length int64 // 0 ⇒ stream to EOF
	ob     bool  // outboard request, not a byte range
}

// cmdBox holds the most recent pending startCmd. A new request supersedes
// an unstarted one (only the latest matters), and wake signals a waiting
// sender. has is the source of truth; wake is just a nudge. A "stop"
// (stopped) interrupts the in-flight range like a new request does, but
// leaves nothing to stream next; any later put clears it.
type cmdBox struct {
	mu      sync.Mutex
	cmd     startCmd
	has     bool
	stopped bool
	wake    chan struct{}
}

func newCmdBox() *cmdBox { return &cmdBox{wake: make(chan struct{}, 1)} }

func (b *cmdBox) put(c startCmd) {
	b.mu.Lock()
	b.cmd, b.has, b.stopped = c, true, false
	b.mu.Unlock()
	select {
	case b.wake <- struct{}{}:
	default:
	}
}

func (b *cmdBox) stop() {
	b.mu.Lock()
	b.has, b.stopped = false, true
	b.mu.Unlock()
	select {
	case b.wake <- struct{}{}:
	default:
	}
}

func (b *cmdBox) take() (startCmd, bool) {
	b.mu.Lock()
	defer b.mu.Unlock()
	c, h := b.cmd, b.has
	b.has = false
	return c, h
}

// pending reports whether the current range should yield: a newer request
// is waiting, or a stop asked for it to be abandoned outright.
func (b *cmdBox) pending() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.has || b.stopped
}

var (
	errServeDone = fmt.Errorf("joiner has the whole file")
	errPreempted = fmt.Errorf("range superseded by a newer request")
	errConnLost  = fmt.Errorf("connection lost")
)

func isConnLost(err error) bool { return err == errConnLost }

// wrapServeReader lets a test substitute a deliberately slow reader around
// the real, already-opened file, to exercise the upload-side throughput
// EMA (and the app-level upload-capacity warning built on it) under a
// known, reproducible rate instead of the host machine's real (and
// non-deterministic in CI) disk/loopback speed. Identity in production.
var wrapServeReader = func(f *os.File) io.ReadSeeker { return f }

// sender streams byte ranges of one file to a joiner over the data
// channel, honoring backpressure and yielding promptly to a newer
// request. It is the sole writer of binary messages on the channel.
type sender struct {
	dc         *webrtc.DataChannel
	f          io.ReadSeeker // was *os.File; only Read/Seek are ever called on it
	size       int64
	box        *cmdBox
	lowSignal  <-chan struct{}
	connFailed <-chan struct{}
	outboard   OutboardFn // bao tree supplier for "ob_req"; nil ⇒ nak
	// covered reports the contiguous run of locally available bytes at an
	// offset (FileStore.Available). nil means the whole file is present.
	// Coverage only ever grows, so a positive answer can't go stale.
	covered func(off int64) (end int64, ok bool)

	// Upload-side throughput EMA, mirroring fileConn.progress()'s
	// download-side pattern (same 0.6/0.4 weights, same progressInterval
	// throttle) but for bytes sent. reportRate, if set, is called with the
	// smoothed bytes/sec — the host's proxy for how fast it can actually
	// feed this joiner. Touched only from run's goroutine (one per
	// ServeFile call), so no lock is needed here.
	reportRate func(float64)
	sentSince  int64
	lastReport time.Time
	rate       float64

	// Upload fairness: fair (nil = disabled) ranks this sender against the
	// node's other viewers by their reported buffer depth; fairID is this
	// sender's handle. streamRange paces against it before each chunk.
	fair   *UploadFairness
	fairID int
}

// trackProgress updates the upload EMA (throttled by progressInterval, the
// same var the download side uses) and reports it. No-op when no reporter
// is registered.
func (s *sender) trackProgress(n int64) {
	if s.reportRate == nil {
		return
	}
	s.sentSince += n
	now := time.Now()
	elapsed := now.Sub(s.lastReport)
	if elapsed < progressInterval {
		return
	}
	inst := float64(s.sentSince) / elapsed.Seconds()
	if s.rate == 0 {
		s.rate = inst
	} else {
		s.rate = 0.6*s.rate + 0.4*inst // EMA smooths the bursty data channel
	}
	s.reportRate(s.rate)
	s.lastReport, s.sentSince = now, 0
}

// run drives the sender: wait for a request, stream it (restarting if a
// newer request preempts it), and loop. Returns errServeDone when the
// joiner reports it has the whole file, ctx.Err() on cancel, or a
// read/connection error.
func (s *sender) run(ctx context.Context, done <-chan struct{}) error {
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-done:
			return errServeDone
		case <-s.connFailed:
			return errConnLost
		case <-s.box.wake:
		}
		// Drain every queued request; the latest wins. streamRange returns
		// errPreempted when a newer one arrives mid-range, so we loop.
		for {
			cmd, ok := s.box.take()
			if !ok {
				break
			}
			var err error
			if cmd.ob {
				err = s.streamOutboard(ctx, cmd)
			} else {
				err = s.streamRange(ctx, cmd)
			}
			if err == errPreempted {
				continue
			}
			if err != nil {
				return err
			}
		}
	}
}

// streamOutboard sends the file's bao outboard tree: "ob" + the blob as
// binary messages + "ob_eof". When no tree is available (a pre-swarm
// serve, or a promoted host whose local file doesn't match the announced
// root) it answers "nak" so the joiner can look elsewhere instead of
// timing out. The supplier may block (a promoted host computes the tree
// from its local file on first request) — the joiner's stall handling
// covers that wait like any other quiet stretch.
func (s *sender) streamOutboard(ctx context.Context, cmd startCmd) error {
	var ob []byte
	if s.outboard != nil {
		b, err := s.outboard(ctx)
		if err == nil {
			ob = b
		}
	}
	if ob == nil {
		if err := s.dc.SendText(marshalCtrl(ctrl{T: "nak", Seq: cmd.seq})); err != nil {
			return errConnLost
		}
		return nil
	}
	if err := s.dc.SendText(marshalCtrl(ctrl{T: "ob", Seq: cmd.seq, Len: int64(len(ob))})); err != nil {
		return errConnLost
	}
	for pos := 0; pos < len(ob); pos += int(chunkSize) {
		end := pos + int(chunkSize)
		if end > len(ob) {
			end = len(ob)
		}
		if err := s.dc.Send(ob[pos:end]); err != nil {
			return errConnLost
		}
		for s.dc.BufferedAmount() > bufferedMax {
			select {
			case <-s.lowSignal:
			case <-s.connFailed:
				return errConnLost
			case <-ctx.Done():
				return ctx.Err()
			}
		}
	}
	if err := s.dc.SendText(marshalCtrl(ctrl{T: "ob_eof", Seq: cmd.seq})); err != nil {
		return errConnLost
	}
	return nil
}

// streamRange sends "at", then the bytes of [off, off+len) (len 0 ⇒ to
// EOF) as binary messages, then "eof". It returns errPreempted the
// moment a newer request is queued, leaving that request for run to pick
// up.
func (s *sender) streamRange(ctx context.Context, cmd startCmd) error {
	end := s.size
	if cmd.length > 0 && cmd.off+cmd.length < end {
		end = cmd.off + cmd.length
	}
	if cmd.off < 0 || cmd.off > s.size {
		return fmt.Errorf("requested offset %d outside file size %d", cmd.off, s.size)
	}
	if s.covered != nil {
		covEnd, ok := s.covered(cmd.off)
		if !ok {
			// Nothing at that offset here (a stale advertisement, or a
			// requester probing beyond this store's coverage): tell it to
			// look elsewhere rather than serve zeros from the sparse file.
			if err := s.dc.SendText(marshalCtrl(ctrl{T: "nak", Seq: cmd.seq})); err != nil {
				return errConnLost
			}
			return nil
		}
		// Serve only the covered run the range starts in; the requester
		// re-schedules the remainder (here once coverage grows, or from
		// another peer). Coverage only grows, so the clamp can't overshoot.
		if covEnd < end {
			end = covEnd
		}
	}
	if _, err := s.f.Seek(cmd.off, 0); err != nil {
		return fmt.Errorf("seeking to %d: %w", cmd.off, err)
	}
	if err := s.dc.SendText(marshalCtrl(ctrl{T: "at", Seq: cmd.seq, Off: cmd.off})); err != nil {
		return errConnLost
	}

	buf := make([]byte, chunkSize)
	pos := cmd.off
	for pos < end {
		if s.box.pending() {
			return errPreempted // a newer request is waiting; abandon this range
		}
		toRead := int64(len(buf))
		if end-pos < toRead {
			toRead = end - pos
		}
		n, readErr := s.f.Read(buf[:toRead])
		if n > 0 {
			// Yield the uplink to a hungrier viewer before sending, while
			// this one is comfortably buffered (no-op otherwise).
			s.fair.pace(ctx, s.fairID)
			if err := s.dc.Send(buf[:n]); err != nil {
				return errConnLost
			}
			pos += int64(n)
			s.trackProgress(int64(n))
			for s.dc.BufferedAmount() > bufferedMax {
				select {
				case <-s.lowSignal:
				case <-s.box.wake:
					return errPreempted // don't stall a redirect behind a full buffer
				case <-s.connFailed:
					return errConnLost
				case <-ctx.Done():
					return ctx.Err()
				}
			}
		}
		if readErr == io.EOF {
			break // file ended (at or before the requested end); stop here
		}
		if readErr != nil {
			return fmt.Errorf("reading file: %w", readErr)
		}
	}
	if err := s.dc.SendText(marshalCtrl(ctrl{T: "eof", Seq: cmd.seq})); err != nil {
		return errConnLost
	}
	return nil
}
