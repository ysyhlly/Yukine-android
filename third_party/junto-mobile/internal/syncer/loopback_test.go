package syncer

import (
	"context"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// eventually polls cond until it returns true or the timeout elapses.
func eventually(t *testing.T, timeout time.Duration, desc string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(2 * time.Millisecond)
	}
	t.Fatalf("condition not met within %v: %s", timeout, desc)
}

// loopbackSend replicates what the nostr transport does on the wire: stamp the
// sender's pubkey into From (peers can't set it themselves), drop a message
// addressed to someone else, and deliver best-effort (never block) — mirroring
// the engine's own broadcast semantics.
func loopbackSend(fromPub, toPub string, toInbox chan protocol.Message) func(context.Context, protocol.Message) error {
	return func(_ context.Context, m protocol.Message) error {
		m.From = fromPub
		if m.To != "" && m.To != toPub {
			return nil
		}
		select {
		case toInbox <- m:
		default:
		}
		return nil
	}
}

// TestTwoPeerLoopbackConverges runs two full Engine.Run loops against each
// other over plain channels (no relay, no real mpv) and asserts that every
// synced dimension converges. Assertions read the fake player's observable
// state (what the engine drove mpv to do), which is mutex-guarded and so
// race-free, rather than the engine's private fields.
func TestTwoPeerLoopbackConverges(t *testing.T) {
	// Fast heartbeats keep the test snappy; restore the package default after.
	orig := heartbeatEvery
	heartbeatEvery = 20 * time.Millisecond
	defer func() { heartbeatEvery = orig }()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	const pubA, pubB = "aaaaaaaaaaaa", "bbbbbbbbbbbb"
	inboxA := make(chan protocol.Message, 256)
	inboxB := make(chan protocol.Message, 256)

	mpvA, mpvB := newFakeMpv(), newFakeMpv()
	pfA, _ := capture()
	pfB, _ := capture()

	engA := New(Deps{
		Mpv: mpvA, Send: loopbackSend(pubA, pubB, inboxB), Inbox: inboxA,
		Printf: pfA, SelfPub: pubA, Nick: "alice", Host: true, PlaylistLen: 2,
	})
	engB := New(Deps{
		Mpv: mpvB, Send: loopbackSend(pubB, pubA, inboxA), Inbox: inboxB,
		Printf: pfB, SelfPub: pubB, Nick: "bob", Host: false, PlaylistLen: 2,
	})

	go func() { _ = engA.Run(ctx) }()
	go func() { _ = engB.Run(ctx) }()

	// Both players signal their initial file load (clears the engine `loading`
	// flag so later seeks are treated as user actions, not internal seeks).
	mpvA.start()
	mpvB.start()

	// 1+2. Host starts playback → the readiness gate opens on both and both
	// play. This must happen fast — via the seenProps count path, well under
	// the 3 s safety valve. If the gate's property count no longer matches the
	// number of observed properties, propsReady would only flip via that
	// fallback and this 1 s assertion would fail. (Regression guard for the
	// readiness-gate magic-number bug.)
	mpvA.userPause(false)
	eventually(t, time.Second, "both peers playing after host start", func() bool {
		return !mpvA.isPaused() && !mpvB.isPaused()
	})

	// 3. Pause on A converges to B.
	mpvA.userPause(true)
	eventually(t, time.Second, "B adopts pause", mpvB.isPaused)

	// resume
	mpvA.userPause(false)
	eventually(t, time.Second, "B adopts resume", func() bool { return !mpvB.isPaused() })

	// 4. Speed change on A converges to B. Tolerance covers the ±3 % drift
	// nudge so the assertion isn't flaky — but the band excludes 1.0, proving
	// the 1.5 base actually propagated.
	mpvA.userSpeed(1.5)
	eventually(t, time.Second, "B adopts speed ~1.5x", func() bool {
		s := mpvB.getSpeed()
		return s >= 1.45 && s <= 1.55
	})

	// 5. Seek on A converges to B (hard seek; loose tolerance for the clock).
	mpvA.userSeek(120)
	eventually(t, time.Second, "B converges near 120s", func() bool {
		p := mpvB.position()
		return p >= 118 && p <= 123
	})

	// 6. Playlist jump on A converges to B.
	mpvA.userPlaylistPos(1)
	eventually(t, time.Second, "B adopts playlist item 1", func() bool {
		return mpvB.playlistPos() == 1
	})

	// 7. No rebroadcast storm: once paused and quiescent, B's pause is not
	// re-applied every heartbeat (which would mean an action is ping-ponging).
	mpvA.userPause(true)
	eventually(t, time.Second, "B settles paused", mpvB.isPaused)
	n1 := mpvB.pauseCalls()
	time.Sleep(150 * time.Millisecond) // several heartbeats at 20 ms
	if n2 := mpvB.pauseCalls(); n2 != n1 {
		t.Errorf("pause re-applied %d extra times while quiescent — rebroadcast storm?", n2-n1)
	}
}
