package transfer

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"math/rand"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// storeWith builds a FileStore whose single entry has exactly the given
// byte ranges of data present (written into the pre-sized sparse .part
// and marked received), simulating a mid-download streaming joiner.
func storeWith(t *testing.T, meta protocol.FileMeta, data []byte, spans [][2]int64) *FileStore {
	t.Helper()
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	e := store.files[0]
	f, err := os.OpenFile(e.partPath, os.O_RDWR|os.O_CREATE, 0o644)
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	if err := f.Truncate(meta.Size); err != nil {
		t.Fatal(err)
	}
	for _, sp := range spans {
		if _, err := f.WriteAt(data[sp[0]:sp[1]], sp[0]); err != nil {
			t.Fatal(err)
		}
		e.add(sp[0], sp[1])
	}
	return store
}

// rawJoiner is a minimal fetcher double: it completes the real
// answer-side handshake against a serving peer and exposes the raw ctrl
// frames and binary byte counts it receives, so tests can assert exactly
// what a server sends for a given request.
type rawJoiner struct {
	pc     *webrtc.PeerConnection
	dc     *webrtc.DataChannel
	frames chan ctrl
	nbytes chan int
}

func newRawJoiner(ctx context.Context, t *testing.T, sig Signaler, peer string) *rawJoiner {
	t.Helper()
	pc, err := noICE().newPeerConnection()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { pc.Close() })
	rj := &rawJoiner{pc: pc, frames: make(chan ctrl, 64), nbytes: make(chan int, 8192)}
	opened := make(chan struct{})
	pc.OnDataChannel(func(dc *webrtc.DataChannel) {
		rj.dc = dc
		dc.OnOpen(func() { close(opened) })
		dc.OnMessage(func(msg webrtc.DataChannelMessage) {
			if msg.IsString {
				var c ctrl
				if unmarshalCtrl(msg.Data, &c) == nil {
					rj.frames <- c
				}
				return
			}
			rj.nbytes <- len(msg.Data)
		})
	})
	offer, err := waitSignal(ctx, sig, peer, "offer")
	if err != nil {
		t.Fatalf("waiting for offer: %v", err)
	}
	if err := pc.SetRemoteDescription(webrtc.SessionDescription{Type: webrtc.SDPTypeOffer, SDP: offer.SDP}); err != nil {
		t.Fatal(err)
	}
	answer, err := pc.CreateAnswer(nil)
	if err != nil {
		t.Fatal(err)
	}
	if err := pc.SetLocalDescription(answer); err != nil {
		t.Fatal(err)
	}
	if err := awaitGathering(ctx, pc); err != nil {
		t.Fatal(err)
	}
	if err := sig.SendSignal(ctx, peer, protocol.Signal{Kind: "answer", SDP: pc.LocalDescription().SDP}); err != nil {
		t.Fatal(err)
	}
	select {
	case <-opened:
	case <-ctx.Done():
		t.Fatal("data channel never opened")
	}
	return rj
}

// start requests a range and returns the ctrl frame that answers it
// ("at" or "nak"), plus, once the range ends ("eof"), the byte total.
func (rj *rawJoiner) start(t *testing.T, seq, off, length int64) ctrl {
	t.Helper()
	if err := rj.dc.SendText(marshalCtrl(ctrl{T: "start", Seq: seq, Off: off, Len: length})); err != nil {
		t.Fatal(err)
	}
	select {
	case f := <-rj.frames:
		return f
	case <-time.After(10 * time.Second):
		t.Fatalf("no answer to start(seq=%d)", seq)
		return ctrl{}
	}
}

// drainRange consumes binary frames until the matching eof, returning
// the byte total.
func (rj *rawJoiner) drainRange(t *testing.T, seq int64) int64 {
	t.Helper()
	var total int64
	for {
		select {
		case n := <-rj.nbytes:
			total += int64(n)
		case f := <-rj.frames:
			if f.T == "eof" && f.Seq == seq {
				return total
			}
		case <-time.After(10 * time.Second):
			t.Fatalf("range %d never finished (got %d bytes)", seq, total)
		}
	}
}

// TestServeFromStoreCompleted covers the promotion data path end to end:
// a joiner whose download finished serves a fresh joiner over a real
// connection — including the outboard tree it fetched itself — and the
// fresh joiner's per-chunk verification accepts every byte.
func TestServeFromStoreCompleted(t *testing.T) {
	srcDir := t.TempDir()
	src := filepath.Join(srcDir, "media.bin")
	data := make([]byte, 900*1024)
	rand.New(rand.NewSource(7)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	meta, _ := baoMetaFor(t, src, "media.bin")
	h, err := HashForServe(src)
	if err != nil {
		t.Fatal(err)
	}

	// The "completed streaming joiner": store holds the whole file (done +
	// renamed) and the outboard it fetched during its own download.
	store := storeWith(t, meta, data, [][2]int64{{0, meta.Size}})
	store.files[0].setOutboard(h.Outboard)
	if err := store.files[0].finishRename(); err != nil {
		t.Fatal(err)
	}
	if !store.AllDone() {
		t.Fatal("store with a finished file should report AllDone")
	}

	seederSig, joinSig := newSignalerPair("seeder", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	serveErr := make(chan error, 1)
	go func() {
		serveErr <- ServeFromStore(ctx, seederSig, "joiner", store, 0, noICE(), t.Logf, nil, nil, nil)
	}()

	path, err := FetchFile(ctx, joinSig, "seeder", meta, t.TempDir(), noICE(), t.Logf)
	if err != nil {
		t.Fatalf("FetchFile from store-backed seeder: %v", err)
	}
	if err := <-serveErr; err != nil {
		t.Fatalf("ServeFromStore: %v", err)
	}
	got, _ := os.ReadFile(path)
	if string(got) != string(data) {
		t.Fatal("received file differs from source")
	}
}

// TestServeFromStorePartialCoverage: a mid-download store serves exactly
// the run it has — a request inside coverage is clamped to the covered
// end, and a request beyond it is nak'd, never zeros from the sparse
// .part.
func TestServeFromStorePartialCoverage(t *testing.T) {
	const size = 1 << 20
	const covered = 512 * 1024
	data := make([]byte, size)
	rand.New(rand.NewSource(8)).Read(data)
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}
	store := storeWith(t, meta, data, [][2]int64{{0, covered}})

	seederSig, joinSig := newSignalerPair("seeder", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	go func() { _ = ServeFromStore(ctx, seederSig, "joiner", store, 0, noICE(), t.Logf, nil, nil, nil) }()
	rj := newRawJoiner(ctx, t, joinSig, "seeder")

	// Whole-file request: served bytes stop at the coverage boundary.
	if f := rj.start(t, 1, 0, 0); f.T != "at" || f.Off != 0 {
		t.Fatalf("start(0) answered with %+v, want at@0", f)
	}
	if total := rj.drainRange(t, 1); total != covered {
		t.Fatalf("served %d bytes, want exactly the covered %d", total, covered)
	}

	// Request past coverage: nak, not zeros.
	if f := rj.start(t, 2, covered+128*1024, 0); f.T != "nak" || f.Seq != 2 {
		t.Fatalf("uncovered request answered with %+v, want nak", f)
	}

	// Request exactly at the coverage boundary (first missing byte): nak.
	if f := rj.start(t, 3, covered, 0); f.T != "nak" {
		t.Fatalf("boundary request answered with %+v, want nak", f)
	}
}

// TestServeFromStoreStopHaltsStream: a "stop" abandons the in-flight
// range without killing the connection — no eof follows, and a later
// start is served normally.
func TestServeFromStoreStopHaltsStream(t *testing.T) {
	const size = 64 << 20 // big enough that the sender is mid-range when stop lands
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: "irrelevant"}
	data := make([]byte, size) // zeros are fine; no verification in a legacy room
	store := storeWith(t, meta, data, [][2]int64{{0, size}})

	seederSig, joinSig := newSignalerPair("seeder", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	go func() { _ = ServeFromStore(ctx, seederSig, "joiner", store, 0, noICE(), t.Logf, nil, nil, nil) }()
	rj := newRawJoiner(ctx, t, joinSig, "seeder")

	if f := rj.start(t, 1, 0, 0); f.T != "at" {
		t.Fatalf("start answered with %+v", f)
	}
	// Let some bytes flow, then stop.
	var received int64
	received += int64(<-rj.nbytes)
	if err := rj.dc.SendText(marshalCtrl(ctrl{T: "stop", Seq: 1})); err != nil {
		t.Fatal(err)
	}
	// Drain until the flow stops; assert the range was abandoned (no eof,
	// not the whole file).
	idle := time.NewTimer(0)
	for done := false; !done; {
		idle.Reset(500 * time.Millisecond)
		select {
		case n := <-rj.nbytes:
			received += int64(n)
		case f := <-rj.frames:
			if f.T == "eof" && f.Seq == 1 {
				t.Fatal("range completed despite stop")
			}
		case <-idle.C:
			done = true
		}
	}
	if received >= size {
		t.Fatalf("received the whole %d bytes despite stop", size)
	}

	// The sender must still be alive for the next request.
	if f := rj.start(t, 2, 0, 64*1024); f.T != "at" {
		t.Fatalf("post-stop start answered with %+v", f)
	}
	if total := rj.drainRange(t, 2); total != 64*1024 {
		t.Fatalf("post-stop range served %d bytes, want %d", total, 64*1024)
	}
}
