package syncer

import (
	"strings"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestBuildSnapshot(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{SelfPub: "self", Nick: "me", Printf: pf, Host: true})
	e.host = true
	e.selfReady = true
	e.gateOpen = false

	now := time.Now().UnixMilli()
	e.peers["alice"] = &peerInfo{
		nick: "alice", hasState: true,
		state: protocol.PlayState{Position: 100, Paused: false, Speed: 1, SentAt: now, DL: 40},
	}
	e.ready["alice"] = true
	e.peers["bob"] = &peerInfo{nick: "bob", hasState: false}
	e.peers["carol"] = &peerInfo{
		nick: "carol", hasState: true,
		state: protocol.PlayState{Position: 100, Buffering: true, Speed: 1, SentAt: now},
	}
	e.ignored["carol"] = true

	s := e.buildSnapshot(102, true)

	if s.TotalCount != 4 || !s.Host || s.GateOpen {
		t.Fatalf("room fields wrong: %+v", s)
	}
	if s.ReadyCount != 2 { // self + alice
		t.Fatalf("ReadyCount = %d, want 2", s.ReadyCount)
	}
	if s.SelfColor != colorFor("self") {
		t.Fatal("self color not set from colorFor")
	}
	if s.SelfPos != 102 {
		t.Fatalf("SelfPos = %v", s.SelfPos)
	}
	if len(s.Peers) != 3 {
		t.Fatalf("peers = %d", len(s.Peers))
	}
	byNick := map[string]PeerStatus{}
	for _, p := range s.Peers {
		byNick[p.Nick] = p
	}
	if a := byNick["alice"]; !a.HasState || a.DL != 40 || !a.Ready || a.Color != colorFor("alice") {
		t.Fatalf("alice wrong: %+v", a)
	}
	// alice is at ~100, local 102 → ~2s behind.
	if a := byNick["alice"]; !a.DriftKnown || a.Drift > -1.5 || a.Drift < -2.5 {
		t.Fatalf("alice drift = %v (want ~-2)", a.Drift)
	}
	if b := byNick["bob"]; b.HasState || b.DriftKnown {
		t.Fatalf("bob should have no state: %+v", b)
	}
	if c := byNick["carol"]; !c.Buffering || !c.Ignored {
		t.Fatalf("carol wrong: %+v", c)
	}
}

func TestBuildSnapshotNoLocalPos(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{SelfPub: "self", Nick: "me", Printf: pf})
	e.peers["alice"] = &peerInfo{nick: "alice", hasState: true, state: protocol.PlayState{Position: 50, Speed: 1, SentAt: time.Now().UnixMilli()}}
	s := e.buildSnapshot(0, false)
	if s.SelfPos != -1 {
		t.Fatalf("SelfPos should be -1 when unknown, got %v", s.SelfPos)
	}
	if s.Peers[0].DriftKnown {
		t.Fatal("drift must be unknown without a local position")
	}
}

// TestEmitSnapshotHook checks the hook fires (only when wired) and
// carries the current peer state.
func TestEmitSnapshotHook(t *testing.T) {
	pf, _ := capture()
	var got []Snapshot
	m := newFakeMpv()
	e := New(Deps{
		SelfPub: "self", Nick: "me", Printf: pf, Mpv: m,
		OnSnapshot: func(s Snapshot) { got = append(got, s) },
	})
	e.peers["alice"] = &peerInfo{nick: "alice"}
	e.emitSnapshot(t.Context())
	if len(got) != 1 || got[0].TotalCount != 2 {
		t.Fatalf("hook not fired with current state: %+v", got)
	}

	// No hook wired ⇒ no panic, no emit.
	plain := New(Deps{SelfPub: "self", Nick: "me", Printf: pf, Mpv: m})
	plain.emitSnapshot(t.Context()) // must be a no-op, not a nil-deref
}

// TestPrintPeersStillFormats guards that the /peers text output — now
// formatted from buildSnapshot — keeps the fields it always showed.
func TestPrintPeersStillFormats(t *testing.T) {
	pf, lines := capture()
	m := newFakeMpv()
	m.pos = 102
	e := New(Deps{SelfPub: "self", Nick: "me", Printf: pf, Mpv: m})
	e.peers["alice"] = &peerInfo{nick: "alice", hasState: true, state: protocol.PlayState{Position: 100, Speed: 1, SentAt: time.Now().UnixMilli(), DL: 40}}
	e.ignored["alice"] = true

	e.printPeers(t.Context())
	out := strings.Join(lines(), "\n")
	for _, want := range []string{"watching", "alice", "↓40%", "(ignored)", "behind"} {
		if !strings.Contains(out, want) {
			t.Fatalf("/peers output missing %q:\n%s", want, out)
		}
	}
}
