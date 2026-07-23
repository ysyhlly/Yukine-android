package syncer

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// TestSlowPeerAutomaticallyLeftBehindAfterMaxDesyncWindow is the
// regression test for the SECURITY-flagged DoS: a peer whose Buffering
// flag stays set (whether genuinely stuck or maliciously never clearing
// it) used to hold the whole room paused forever. Once an episode has
// run past maxDesyncWindow, checkDesyncTimeouts must mark it left behind
// so anyPeerBuffering stops honoring the hold — with no human needing to
// run /kick.
func TestSlowPeerAutomaticallyLeftBehindAfterMaxDesyncWindow(t *testing.T) {
	pf, lines := capture()
	m := newFakeMpv()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: m})
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 1, true))
	if !e.anyPeerBuffering() {
		t.Fatal("test setup: a fresh buffering peer should hold the room")
	}

	// Simulate the episode having run well past maxDesyncWindow, without
	// an actual real-time sleep.
	e.peers["a"].bufferingSince = time.Now().Add(-maxDesyncWindow - time.Second)
	e.checkDesyncTimeouts(ctx, time.Now())

	if e.anyPeerBuffering() {
		t.Error("a peer buffering past maxDesyncWindow must no longer hold the room")
	}
	if !e.peers["a"].leftBehind {
		t.Error("expected the peer to be marked left behind")
	}
	if out := strings.Join(lines(), "\n"); !strings.Contains(out, "continuing without them") {
		t.Errorf("expected a notice explaining the room continued without the peer, got:\n%s", out)
	}
}

// TestHostSeesBufferingOverrideHint checks the host specifically is told
// about the Enter-to-continue override inline in the "waiting for X to
// buffer" notice — mirroring the pre-start gate's own "press Enter to
// start anyway" discoverability pattern — while a non-host peer (who
// can't invoke it) isn't shown a command that wouldn't do anything for
// them.
func TestHostSeesBufferingOverrideHint(t *testing.T) {
	pfHost, hostLines := capture()
	eHost := New(Deps{Host: true, Nick: "host", SelfPub: "h", Printf: pfHost, Mpv: newFakeMpv()})
	eHost.gateOpen = true

	pfJoiner, joinerLines := capture()
	eJoiner := New(Deps{Host: false, Nick: "joiner", SelfPub: "j", Printf: pfJoiner, Mpv: newFakeMpv()})
	eJoiner.gateOpen = true

	ctx := context.Background()
	eHost.handleMessage(ctx, bufState("a", 1, true))
	eJoiner.handleMessage(ctx, bufState("a", 1, true))

	if out := strings.Join(hostLines(), "\n"); !strings.Contains(out, "press Enter to continue without them") {
		t.Errorf("expected the host to see the override hint, got:\n%s", out)
	}
	if out := strings.Join(joinerLines(), "\n"); strings.Contains(out, "press Enter to continue") {
		t.Errorf("a non-host must not be shown a command that wouldn't do anything for them, got:\n%s", out)
	}
}

// TestHeartbeatAppliesDesyncTimeout checks the automatic half is actually
// wired into the periodic tick (heartbeat), not just directly callable —
// exercising the real production path instead of only the unit-level
// checkDesyncTimeouts call the other tests use for determinism/speed.
func TestHeartbeatAppliesDesyncTimeout(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: m})
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 1, true))
	e.peers["a"].bufferingSince = time.Now().Add(-maxDesyncWindow - time.Second)

	e.heartbeat(ctx)

	if e.anyPeerBuffering() {
		t.Error("heartbeat should apply the desync timeout, but the peer still holds the room")
	}
	if !e.peers["a"].leftBehind {
		t.Error("expected heartbeat to mark the peer left behind")
	}
}

// TestSlowPeerUnderWindowStillHoldsRoom checks the common, non-malicious
// case is unaffected: a peer that's only just started buffering (well
// under maxDesyncWindow) still holds the room, exactly as before this
// change.
func TestSlowPeerUnderWindowStillHoldsRoom(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: m})
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 1, true))
	e.checkDesyncTimeouts(ctx, time.Now())

	if !e.anyPeerBuffering() {
		t.Error("a peer well under maxDesyncWindow must still hold the room")
	}
	if e.peers["a"].leftBehind {
		t.Error("a peer well under maxDesyncWindow must not be marked left behind")
	}
}

// TestSlowPeerFreshEpisodeGetsFullWindowAgain checks that catching up
// clears leftBehind, so a later, separate buffering episode from the
// same peer gets its own full window rather than being instantly
// skipped forever.
func TestSlowPeerFreshEpisodeGetsFullWindowAgain(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: m})
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 1, true))
	e.peers["a"].bufferingSince = time.Now().Add(-maxDesyncWindow - time.Second)
	e.checkDesyncTimeouts(ctx, time.Now())
	if !e.peers["a"].leftBehind {
		t.Fatal("test setup: expected the peer to be left behind")
	}

	e.handleMessage(ctx, bufState("a", 2, false)) // peer catches up
	if e.peers["a"].leftBehind {
		t.Error("leftBehind should reset once the peer's buffering clears")
	}

	e.handleMessage(ctx, bufState("a", 3, true)) // a new, separate episode
	if !e.anyPeerBuffering() {
		t.Error("a fresh buffering episode should hold the room again, not be instantly skipped")
	}
	if e.peers["a"].leftBehind {
		t.Error("a fresh episode must not start out already left behind")
	}
}

// TestHostOverridePeerBuffering checks slow-peer recovery's manual half:
// the host pressing Enter mid-session (gate already open) immediately
// skips maxDesyncWindow for whoever's currently holding the room.
func TestHostOverridePeerBuffering(t *testing.T) {
	pf, lines := capture()
	m := newFakeMpv()
	e := New(Deps{Host: true, Nick: "host", SelfPub: "h", Printf: pf, Mpv: m})
	e.gateOpen = true
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 1, true))
	if !e.anyPeerBuffering() {
		t.Fatal("test setup: peer should be holding the room")
	}

	if quit := e.handleLine(ctx, ""); quit {
		t.Fatal("a blank line must never quit the session")
	}

	if e.anyPeerBuffering() {
		t.Error("the host's override should immediately stop honoring the buffering peer")
	}
	if !e.peers["a"].leftBehind {
		t.Error("expected the peer to be marked left behind by the override")
	}
	if out := strings.Join(lines(), "\n"); !strings.Contains(out, "continuing without whoever's still buffering") {
		t.Errorf("expected the override notice, got:\n%s", out)
	}
}

// TestNonHostCannotOverridePeerBuffering checks the override is
// host-only, mirroring the existing host-only pre-start gate override.
func TestNonHostCannotOverridePeerBuffering(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{Host: false, Nick: "joiner", SelfPub: "j", Printf: pf, Mpv: m})
	e.gateOpen = true
	ctx := context.Background()

	e.handleMessage(ctx, bufState("a", 1, true))
	e.handleLine(ctx, "")

	if !e.anyPeerBuffering() {
		t.Error("a non-host's blank Enter must not override slow-peer recovery")
	}
	if e.peers["a"].leftBehind {
		t.Error("a non-host's blank Enter must not mark any peer left behind")
	}
}

// TestOverrideIsNoopWithNoOneBuffering checks pressing Enter mid-session
// with nobody currently buffering does nothing (no spurious notice).
func TestOverrideIsNoopWithNoOneBuffering(t *testing.T) {
	pf, lines := capture()
	m := newFakeMpv()
	e := New(Deps{Host: true, Nick: "host", SelfPub: "h", Printf: pf, Mpv: m})
	e.gateOpen = true
	ctx := context.Background()

	e.handleLine(ctx, "")

	if out := strings.Join(lines(), "\n"); out != "" {
		t.Errorf("expected no output when nobody is buffering, got:\n%s", out)
	}
}

// TestResyncOnCatchUpAfterBeingLeftBehind checks slow-peer recovery's
// other automatic half: once OUR OWN buffering has run long enough that
// the rest of the room would have continued without us, clearing it
// must resync to the room's live position instead of quietly resuming
// from wherever we paused and drifting behind for good.
func TestResyncOnCatchUpAfterBeingLeftBehind(t *testing.T) {
	pf, lines := capture()
	m := newFakeMpv()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: m})
	e.propsReady = true
	ctx := context.Background()

	// The room's live position while we were stuck.
	e.lastState = protocol.PlayState{Paused: false, Position: 500, Version: 1, Speed: 1.0}
	e.lastStateFrom = "a"
	e.haveLast = true

	e.setBuffering(ctx, true)
	e.bufferingSince = time.Now().Add(-maxDesyncWindow - time.Second) // simulate a long stall
	e.setBuffering(ctx, false)                                        // caught up

	if out := strings.Join(lines(), "\n"); !strings.Contains(out, "resynced to the room") {
		t.Errorf("expected an automatic resync after being left behind, got:\n%s", out)
	}
}

// TestNoResyncForOrdinaryShortBuffering checks the common case (the room
// held for us the whole time, exactly as intended) never triggers a
// resync — there's no drift to correct, and printing one anyway would be
// confusing noise for something that isn't actually happening.
func TestNoResyncForOrdinaryShortBuffering(t *testing.T) {
	pf, lines := capture()
	m := newFakeMpv()
	e := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: m})
	e.propsReady = true
	ctx := context.Background()

	e.lastState = protocol.PlayState{Paused: false, Position: 500, Version: 1, Speed: 1.0}
	e.lastStateFrom = "a"
	e.haveLast = true

	e.setBuffering(ctx, true)
	e.setBuffering(ctx, false) // clears almost immediately, well under maxDesyncWindow

	if out := strings.Join(lines(), "\n"); strings.Contains(out, "resynced to the room") {
		t.Errorf("an ordinary short buffering episode must not trigger a resync, got:\n%s", out)
	}
}
