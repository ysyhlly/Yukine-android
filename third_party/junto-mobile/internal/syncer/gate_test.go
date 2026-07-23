package syncer

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// capture returns a Printf sink and an accessor for what it collected.
func capture() (func(string, ...any), func() []string) {
	var mu sync.Mutex
	var lines []string
	pf := func(f string, a ...any) {
		mu.Lock()
		lines = append(lines, fmt.Sprintf(f, a...))
		mu.Unlock()
	}
	get := func() []string {
		mu.Lock()
		defer mu.Unlock()
		return append([]string(nil), lines...)
	}
	return pf, get
}

// As peers announce readiness, the host's roster count climbs and
// reports "everyone's ready" once all known peers and the host are
// ready. (The MsgReady path doesn't touch mpv, so no client is needed.)
func TestHostReadyTracking(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Host: true, Nick: "host", SelfPub: "h", Printf: pf})
	e.selfReady = true // host's file is local; treat as loaded

	ctx := context.Background()
	e.handleMessage(ctx, protocol.Message{Type: protocol.MsgReady, From: "a", Nick: "alice"})
	e.handleMessage(ctx, protocol.Message{Type: protocol.MsgReady, From: "b", Nick: "bob"})

	if !e.ready["a"] || !e.ready["b"] {
		t.Fatalf("expected both peers tracked ready, got %v", e.ready)
	}
	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "3/3") {
		t.Errorf("expected an everyone-ready (3/3) prompt, got:\n%s", out)
	}
	if !strings.Contains(out, "Enter to start") {
		t.Errorf("expected a start prompt, got:\n%s", out)
	}
}

// With a peer not yet ready, the host sees a partial count rather than
// the everyone-ready prompt.
func TestPartialReadyShowsCount(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Host: true, Nick: "host", SelfPub: "h", Printf: pf})
	e.selfReady = true
	ctx := context.Background()

	e.peers["a"] = &peerInfo{nick: "alice"} // joined, not yet ready
	e.handleMessage(ctx, protocol.Message{Type: protocol.MsgReady, From: "b", Nick: "bob"})

	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "2/3 ready") {
		t.Errorf("expected partial 2/3 count, got:\n%s", out)
	}
}

// A non-host that has loaded announces itself waiting for the host, and
// the gate opens explicitly via openGate.
func TestJoinerGate(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Host: false, Nick: "me", SelfPub: "m", Printf: pf})

	e.markSelfReady(context.Background())
	if !e.selfReady {
		t.Fatal("markSelfReady should set selfReady")
	}
	if got := strings.Join(lines(), "\n"); !strings.Contains(strings.ToLower(got), "waiting for the host") {
		t.Errorf("joiner should report waiting for the host, got:\n%s", got)
	}

	if e.gateOpen {
		t.Fatal("gate should start closed")
	}
	e.openGate()
	if !e.gateOpen {
		t.Error("openGate should open the gate")
	}
}

// TestStallWarningFiresBelowThreshold checks that a streaming joiner whose
// measured throughput looks meaningfully slower than the file's known
// bitrate gets a one-time warning right when the readiness gate opens.
func TestStallWarningFiresBelowThreshold(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Bitrate: 10 << 20, CurrentRate: func() float64 { return 1 << 20 }})
	e.markSelfReady(context.Background())

	if out := strings.Join(lines(), "\n"); !strings.Contains(out, "slower than this file needs") {
		t.Errorf("expected a stall-risk warning, got:\n%s", out)
	}
}

// TestStallWarningSkipsAboveThreshold checks that comfortable throughput
// (at or above stallWarnMargin of the bitrate) never warns.
func TestStallWarningSkipsAboveThreshold(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Bitrate: 10 << 20, CurrentRate: func() float64 { return 10 << 20 }})
	e.markSelfReady(context.Background())

	if out := strings.Join(lines(), "\n"); strings.Contains(out, "slower than this file needs") {
		t.Errorf("should not warn when throughput comfortably covers the bitrate, got:\n%s", out)
	}
}

// TestStallWarningSkipsWhenBitrateUnknown checks the non-mp4 case (or an
// mp4 whose duration couldn't be parsed): no prediction is possible, so
// there must be silence, not a warning about nothing.
func TestStallWarningSkipsWhenBitrateUnknown(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Bitrate: 0, CurrentRate: func() float64 { return 1 << 20 }})
	e.markSelfReady(context.Background())

	if out := strings.Join(lines(), "\n"); strings.Contains(out, "slower than this file needs") {
		t.Errorf("should not warn when the bitrate is unknown, got:\n%s", out)
	}
}

// TestStallWarningFiresOnlyOnce relies on markSelfReady's existing
// selfReady idempotency guard: a second call must not re-run the check
// (and so must not re-print the warning).
func TestStallWarningFiresOnlyOnce(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Bitrate: 10 << 20, CurrentRate: func() float64 { return 1 << 20 }})
	e.markSelfReady(context.Background())
	e.markSelfReady(context.Background())

	if got := strings.Count(strings.Join(lines(), "\n"), "slower than this file needs"); got != 1 {
		t.Errorf("expected the stall warning exactly once, got %d occurrences", got)
	}
}

// TestStallCheckSkippedWithoutDownloader checks the host/local-file shape
// (CurrentRate nil — nothing is being downloaded) never warns, even if
// Bitrate happens to be set.
func TestStallCheckSkippedWithoutDownloader(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Bitrate: 10 << 20, CurrentRate: nil})
	e.markSelfReady(context.Background())

	if out := strings.Join(lines(), "\n"); strings.Contains(out, "slower than this file needs") {
		t.Errorf("should not warn when CurrentRate is nil (host/local-file joiner), got:\n%s", out)
	}
}

// A solo host (no peers) is told it can start alone.
func TestSoloHostPrompt(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Host: true, Nick: "host", SelfPub: "h", Printf: pf})
	e.markSelfReady(context.Background()) // host loaded, nobody else present

	if got := strings.Join(lines(), "\n"); !strings.Contains(got, "no one else here yet") {
		t.Errorf("solo host should be prompted to start alone, got:\n%s", got)
	}
}

// bufState builds a paused heartbeat carrying a buffering flag. (These
// tests keep propsReady false so applyRemoteState is a no-op, and start
// paused so applyDesiredPause never needs the mpv client.)
func bufState(from string, version int64, buffering bool) protocol.Message {
	return protocol.Message{Type: protocol.MsgState, From: from, Nick: from,
		State: &protocol.PlayState{Paused: true, Version: version, Buffering: buffering}}
}

// The room is considered buffering while ANY peer is, and only clears
// once every peer has cleared — so one peer finishing can't resume the
// room out from under another that's still catching up.
func TestBufferingOrSemantics(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf})
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 1, true))
	e.handleMessage(ctx, bufState("b", 1, true))
	if !e.anyPeerBuffering() {
		t.Fatal("a buffering peer should make the room buffer")
	}
	e.handleMessage(ctx, bufState("a", 1, false))
	if !e.anyPeerBuffering() {
		t.Error("room should stay buffering while b still buffers")
	}
	e.handleMessage(ctx, bufState("b", 1, false))
	if e.anyPeerBuffering() {
		t.Error("room should clear once every peer has caught up")
	}
}

// outboxHasType drains e.outbox (non-blocking) and reports whether any
// queued message has the given type.
func outboxHasType(e *Engine, want protocol.MsgType) bool {
	found := false
	for {
		select {
		case msg := <-e.outbox:
			if msg.Type == want {
				found = true
			}
		default:
			return found
		}
	}
}

// TestHeartbeatWithholdsMsgReadyUntilPreBuffered is the regression test
// for the premature-ready bug: a streaming joiner's markSelfReady
// correctly holds e.selfReady false until its pre-buffer ReadyCh closes,
// but the MsgReady broadcast in heartbeat() used to fire unconditionally
// regardless — so the host would see the joiner as ready and start the
// room before the buffer cushion the gate exists to guarantee was there,
// reproducing the exact first-frame stall the gate shipped to prevent.
// The broadcast must only go out once selfReady is actually true.
func TestHeartbeatWithholdsMsgReadyUntilPreBuffered(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	readyCh := make(chan struct{}) // not yet closed: buffer cushion not met
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: m, ReadyCh: readyCh})
	e.propsReady = true

	e.heartbeat(context.Background())

	if e.selfReady {
		t.Fatal("selfReady must stay false while the pre-buffer channel is open")
	}
	if outboxHasType(e, protocol.MsgReady) {
		t.Fatal("must not broadcast MsgReady before the pre-buffer cushion is met")
	}

	close(readyCh) // buffer cushion met
	e.heartbeat(context.Background())

	if !e.selfReady {
		t.Fatal("selfReady should become true once the pre-buffer channel closes")
	}
	if !outboxHasType(e, protocol.MsgReady) {
		t.Fatal("expected a MsgReady broadcast once selfReady is true")
	}
}

// A peer's buffering flag is tracked even when its state would be
// dropped by the last-writer-wins version gate (same version, flag-only
// change) — otherwise a buffering signal could be silently ignored.
func TestBufferingIgnoresVersionGate(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf})
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 5, true))
	if p := e.peers["a"]; p == nil || !p.buffering {
		t.Fatal("buffering not tracked")
	}
	e.handleMessage(ctx, bufState("a", 5, false)) // same version, flag flips
	if e.peers["a"].buffering {
		t.Error("buffering flip ignored on equal version")
	}
}

// TestClosedBufferChannelStopsSelecting is the regression test for the
// 100%-CPU busy-loop bug: a closed (not nil) channel is always ready in a
// select, unlike a nil one which blocks forever — so a closed Buffer
// would win Run's select on every single iteration forever once
// triggered. Run must nil the field out after observing the close so the
// case stops being selected.
func TestClosedBufferChannelStopsSelecting(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	closedBuf := make(chan bool)
	close(closedBuf)
	e := New(Deps{
		SelfPub: "self", Printf: pf, Mpv: m, Buffer: closedBuf,
		Send: func(context.Context, protocol.Message) error { return nil },
	})

	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	done := make(chan struct{})
	go func() {
		_ = e.Run(ctx)
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("Run did not return after ctx cancellation — the loop may be wedged spinning on the closed channel")
	}

	if e.d.Buffer != nil {
		t.Error("a closed Buffer channel should be nilled out once observed, so the select stops picking it every iteration")
	}
}

// TestBroadcastNeverBlocksDropsOldest is the regression test for the
// engine-loop-freeze bug: broadcast() used to block the caller once the
// outbox was full for anything other than an unaddressed heartbeat
// state — during a sustained relay outage (senderLoop retrying at up to
// ~10s per attempt) that stalled mpv event handling, drift correction,
// and user input for up to 10s per queued message. It must never block,
// making room by dropping the oldest queued message instead.
func TestBroadcastNeverBlocksDropsOldest(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{SelfPub: "self", Printf: pf})

	// Fill the outbox to capacity with a message type that used to always
	// block once full (anything but an unaddressed MsgState).
	capacity := cap(e.outbox)
	for i := 0; i < capacity; i++ {
		e.broadcast(protocol.Message{Type: protocol.MsgHello, Nick: fmt.Sprintf("n%d", i)})
	}

	done := make(chan struct{})
	go func() {
		e.broadcast(protocol.Message{Type: protocol.MsgHello, Nick: "newest"})
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("broadcast blocked on a full outbox instead of dropping the oldest message")
	}

	var got []string
drain:
	for {
		select {
		case msg := <-e.outbox:
			got = append(got, msg.Nick)
		default:
			break drain
		}
	}
	if len(got) != capacity {
		t.Fatalf("expected the outbox to stay at capacity %d, got %d messages: %v", capacity, len(got), got)
	}
	if got[0] == "n0" {
		t.Error("the oldest message should have been dropped to make room")
	}
	if got[len(got)-1] != "newest" {
		t.Errorf("the newest message should be present in the outbox, got %v", got)
	}
}
