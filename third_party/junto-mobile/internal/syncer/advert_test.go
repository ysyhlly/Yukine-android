package syncer

import (
	"context"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// drainOutbox collects everything currently queued for broadcast.
func drainOutbox(e *Engine) []protocol.Message {
	var msgs []protocol.Message
	for {
		select {
		case m := <-e.outbox:
			msgs = append(msgs, m)
		default:
			return msgs
		}
	}
}

// TestHeartbeatCarriesHaveSnapshot: the coverage advertisement rides the
// regular state heartbeat when the hook is wired, and heartbeats stay
// clean without it.
func TestHeartbeatCarriesHaveSnapshot(t *testing.T) {
	pf, _ := capture()
	want := []protocol.HaveEntry{{File: 0, Pieces: [][2]int{{0, 3}}}}
	wantDone := [][2]int{{1, 2}}
	e := New(Deps{
		Nick: "me", SelfPub: "m", Printf: pf, Mpv: newFakeMpv(),
		HaveSnapshot: func() ([]protocol.HaveEntry, [][2]int) { return want, wantDone },
	})
	e.propsReady = true
	e.heartbeat(context.Background())

	found := false
	for _, m := range drainOutbox(e) {
		if m.Type == protocol.MsgState && m.State != nil {
			if len(m.Have) == 1 && m.Have[0].File == 0 && len(m.HaveDone) == 1 {
				found = true
			}
		}
	}
	if !found {
		t.Fatal("heartbeat did not carry the coverage advertisement")
	}

	plain := New(Deps{Nick: "me", SelfPub: "m", Printf: pf, Mpv: newFakeMpv()})
	plain.propsReady = true
	plain.heartbeat(context.Background())
	for _, m := range drainOutbox(plain) {
		if len(m.Have) != 0 || len(m.HaveDone) != 0 {
			t.Fatal("heartbeat advertised coverage with no snapshot hook wired")
		}
	}
}

// TestStateHandsAdvertisementToOnPeerHave: a peer's heartbeat carrying
// coverage reaches the downloader hook — except from ignored peers, who
// must never be considered sources.
func TestStateHandsAdvertisementToOnPeerHave(t *testing.T) {
	pf, _ := capture()
	var gotPeer string
	var gotHave []protocol.HaveEntry
	e := New(Deps{
		SelfPub: "self", Printf: pf,
		OnPeerHave: func(peer string, have []protocol.HaveEntry, done [][2]int) {
			gotPeer, gotHave = peer, have
		},
	})
	st := protocol.Message{
		Type: protocol.MsgState, From: "peer1", Nick: "p",
		State: &protocol.PlayState{Position: 1, SentAt: 1, Version: 1},
		Have:  []protocol.HaveEntry{{File: 0, Pieces: [][2]int{{2, 5}}}},
	}
	e.handleMessage(context.Background(), st)
	if gotPeer != "peer1" || len(gotHave) != 1 || gotHave[0].Pieces[0] != [2]int{2, 5} {
		t.Fatalf("advertisement not delivered: peer=%q have=%+v", gotPeer, gotHave)
	}

	// An ignored peer's advertisement is dropped.
	gotPeer, gotHave = "", nil
	e.ignored["peer2"] = true
	st.From = "peer2"
	e.handleMessage(context.Background(), st)
	if gotPeer != "" {
		t.Fatal("ignored peer's advertisement was delivered")
	}

	// A state without coverage doesn't fire the hook.
	gotPeer = ""
	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgState, From: "peer3", Nick: "p",
		State: &protocol.PlayState{Position: 1, SentAt: 1, Version: 1},
	})
	if gotPeer != "" {
		t.Fatal("empty advertisement fired the hook")
	}
}

// TestPeerGoneFiresOnEveryExit: timeout eviction, explicit leave, and
// kick-ignore all drop the peer's coverage.
func TestPeerGoneFiresOnEveryExit(t *testing.T) {
	newEngine := func(gone *[]string) *Engine {
		pf, _ := capture()
		return New(Deps{
			SelfPub: "self", Printf: pf, Mpv: newFakeMpv(),
			OnPeerGone: func(peer string) { *gone = append(*gone, peer) },
		})
	}

	t.Run("timeout eviction", func(t *testing.T) {
		var gone []string
		e := newEngine(&gone)
		e.propsReady = true
		e.peers["stale"] = &peerInfo{nick: "s", lastSeen: time.Now().Add(-peerTimeout - time.Second)}
		e.heartbeat(context.Background())
		if len(gone) != 1 || gone[0] != "stale" {
			t.Fatalf("eviction did not fire OnPeerGone: %v", gone)
		}
	})

	t.Run("explicit leave", func(t *testing.T) {
		var gone []string
		e := newEngine(&gone)
		e.peers["leaver"] = &peerInfo{nick: "l", lastSeen: time.Now()}
		e.handleMessage(context.Background(), protocol.Message{Type: protocol.MsgLeave, From: "leaver", Nick: "l"})
		if len(gone) != 1 || gone[0] != "leaver" {
			t.Fatalf("leave did not fire OnPeerGone: %v", gone)
		}
	})

	t.Run("kick-ignore", func(t *testing.T) {
		var gone []string
		e := newEngine(&gone)
		e.peers["kicker"] = &peerInfo{nick: "k", lastSeen: time.Now()}
		e.peers["target"] = &peerInfo{nick: "t", lastSeen: time.Now()}
		e.handleMessage(context.Background(), protocol.Message{Type: protocol.MsgKick, From: "kicker", Kicked: "target"})
		if len(gone) != 1 || gone[0] != "target" {
			t.Fatalf("kick did not fire OnPeerGone: %v", gone)
		}
	})
}
