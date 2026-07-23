package syncer

import (
	"context"
	"math/rand"
	"sync"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// safeRand wraps a *rand.Rand with a mutex: lossyBroadcast's delayed
// (reordered/duplicated) deliveries run in their own goroutines, so the
// shared RNG can be touched concurrently with the sender's own goroutine.
type safeRand struct {
	mu  sync.Mutex
	rng *rand.Rand
}

func newSafeRand(seed int64) *safeRand { return &safeRand{rng: rand.New(rand.NewSource(seed))} }

func (s *safeRand) chance(p float64) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.rng.Float64() < p
}

func (s *safeRand) jitter(maxMs int) time.Duration {
	s.mu.Lock()
	defer s.mu.Unlock()
	return time.Duration(s.rng.Intn(maxMs)) * time.Millisecond
}

// peerInbox pairs a peer's pubkey with the inbox lossyBroadcast delivers
// to on its behalf.
type peerInbox struct {
	pub   string
	inbox chan protocol.Message
}

// lossyBroadcast models relay unreliability far more realistically than
// a plain lossless channel send: each recipient independently may never
// receive a given message (dropProb), receive it twice (dupProb), or
// receive it after an out-of-order delay (reorderProb). This is the kind
// of harness that would have caught bugs a lossless two-peer loop can't
// — e.g. a correction that's only ever (re)sent once and silently lost.
func lossyBroadcast(rng *safeRand, fromPub string, peers []peerInbox, dropProb, dupProb, reorderProb float64) func(context.Context, protocol.Message) error {
	deliver := func(m protocol.Message, inbox chan protocol.Message) {
		select {
		case inbox <- m:
		default:
		}
	}
	return func(_ context.Context, m protocol.Message) error {
		m.From = fromPub
		for _, p := range peers {
			if m.To != "" && m.To != p.pub {
				continue
			}
			if rng.chance(dropProb) {
				continue // simulate a relay that never delivered this one
			}
			if rng.chance(reorderProb) {
				delay := rng.jitter(30)
				go func(msg protocol.Message, inbox chan protocol.Message) {
					time.Sleep(delay)
					deliver(msg, inbox)
				}(m, p.inbox)
			} else {
				deliver(m, p.inbox)
			}
			if rng.chance(dupProb) {
				delay := rng.jitter(10)
				go func(msg protocol.Message, inbox chan protocol.Message) {
					time.Sleep(delay)
					deliver(msg, inbox)
				}(m, p.inbox)
			}
		}
		return nil
	}
}

// TestThreePeerLossyConverges runs three full Engine.Run loops against
// each other with lossy, duplicating, reordering delivery between every
// pair — closing the gap a lossless two-peer harness leaves open (no
// loss/duplication/reordering, no third peer to expose a
// pairwise-only assumption). Convergence must still happen: with
// heartbeats retransmitting the full PlayState every beat, a single
// dropped message is not fatal as long as delivery isn't dropping
// everything forever, which is exactly the property this test checks
// under sustained, nontrivial unreliability.
func TestThreePeerLossyConverges(t *testing.T) {
	orig := heartbeatEvery
	heartbeatEvery = 20 * time.Millisecond
	defer func() { heartbeatEvery = orig }()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	const pubA, pubB, pubC = "aaaaaaaaaaaa", "bbbbbbbbbbbb", "cccccccccccc"
	inboxA := make(chan protocol.Message, 256)
	inboxB := make(chan protocol.Message, 256)
	inboxC := make(chan protocol.Message, 256)

	rng := newSafeRand(7)
	const dropProb, dupProb, reorderProb = 0.3, 0.2, 0.3

	mpvA, mpvB, mpvC := newFakeMpv(), newFakeMpv(), newFakeMpv()
	pfA, _ := capture()
	pfB, _ := capture()
	pfC, _ := capture()

	engA := New(Deps{
		Mpv: mpvA, Inbox: inboxA, Printf: pfA, SelfPub: pubA, Nick: "alice",
		Host: true, PlaylistLen: 2,
		Send: lossyBroadcast(rng, pubA, []peerInbox{{pubB, inboxB}, {pubC, inboxC}}, dropProb, dupProb, reorderProb),
	})
	engB := New(Deps{
		Mpv: mpvB, Inbox: inboxB, Printf: pfB, SelfPub: pubB, Nick: "bob",
		Host: false, PlaylistLen: 2,
		Send: lossyBroadcast(rng, pubB, []peerInbox{{pubA, inboxA}, {pubC, inboxC}}, dropProb, dupProb, reorderProb),
	})
	engC := New(Deps{
		Mpv: mpvC, Inbox: inboxC, Printf: pfC, SelfPub: pubC, Nick: "carol",
		Host: false, PlaylistLen: 2,
		Send: lossyBroadcast(rng, pubC, []peerInbox{{pubA, inboxA}, {pubB, inboxB}}, dropProb, dupProb, reorderProb),
	})

	go func() { _ = engA.Run(ctx) }()
	go func() { _ = engB.Run(ctx) }()
	go func() { _ = engC.Run(ctx) }()

	mpvA.start()
	mpvB.start()
	mpvC.start()

	// Generous timeouts throughout: lossy delivery means an action may
	// need a few retransmitted heartbeats (every 20ms here) to land on
	// every peer, unlike the lossless two-peer harness's 1s budget.
	const converge = 5 * time.Second

	mpvA.userPause(false)
	eventually(t, converge, "all three peers playing after host start", func() bool {
		return !mpvA.isPaused() && !mpvB.isPaused() && !mpvC.isPaused()
	})

	mpvA.userPause(true)
	eventually(t, converge, "B and C adopt pause", func() bool {
		return mpvB.isPaused() && mpvC.isPaused()
	})

	mpvA.userPause(false)
	eventually(t, converge, "B and C adopt resume", func() bool {
		return !mpvB.isPaused() && !mpvC.isPaused()
	})

	mpvB.userSpeed(1.5)
	eventually(t, converge, "A and C adopt speed ~1.5x", func() bool {
		sa, sc := mpvA.getSpeed(), mpvC.getSpeed()
		return sa >= 1.45 && sa <= 1.55 && sc >= 1.45 && sc <= 1.55
	})

	mpvC.userSeek(120)
	eventually(t, converge, "A and B converge near 120s", func() bool {
		pa, pb := mpvA.position(), mpvB.position()
		return pa >= 115 && pa <= 125 && pb >= 115 && pb <= 125
	})

	mpvA.userPlaylistPos(1)
	eventually(t, converge, "B and C adopt playlist item 1", func() bool {
		return mpvB.playlistPos() == 1 && mpvC.playlistPos() == 1
	})
}
