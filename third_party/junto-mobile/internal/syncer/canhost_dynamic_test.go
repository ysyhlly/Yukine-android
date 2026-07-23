package syncer

import (
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// TestCanHostNow pins the eligibility matrix: the static flag wins
// outright, and the dynamic hook (a streaming joiner's store.AllDone)
// supplements it live.
func TestCanHostNow(t *testing.T) {
	pf, _ := capture()
	cases := []struct {
		name    string
		static_ bool
		dyn     func() bool
		want    bool
	}{
		{"neither", false, nil, false},
		{"static only", true, nil, true},
		{"dynamic false", false, func() bool { return false }, false},
		{"dynamic true", false, func() bool { return true }, true},
		{"static wins over dynamic false", true, func() bool { return false }, true},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			e := New(Deps{SelfPub: "zzz", Printf: pf, CanHost: c.static_, CanHostDynamic: c.dyn})
			if got := e.canHostNow(); got != c.want {
				t.Fatalf("canHostNow = %v, want %v", got, c.want)
			}
		})
	}
}

// TestShouldElectSelf_DynamicFlip: a streaming joiner is ineligible while
// its download is incomplete and becomes electable the moment the store
// reports done — with no other engine state changing.
func TestShouldElectSelf_DynamicFlip(t *testing.T) {
	pf, _ := capture()
	done := false
	e := New(Deps{
		SelfPub:        "zzz",
		Printf:         pf,
		CanHostDynamic: func() bool { return done },
		OnBecomeHost:   stubOnBecomeHost(),
	})
	e.peerFiles = testFiles

	if e.shouldElectSelf() {
		t.Fatal("mid-download streaming joiner must not elect itself")
	}
	done = true
	if !e.shouldElectSelf() {
		t.Fatal("completed streaming joiner must become electable")
	}
}

// TestShouldElectSelf_DynamicDefersToLargerPeer: dynamic eligibility uses
// the same deterministic election as the static kind.
func TestShouldElectSelf_DynamicDefersToLargerPeer(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{
		SelfPub:        "aaa",
		Printf:         pf,
		CanHostDynamic: func() bool { return true },
		OnBecomeHost:   stubOnBecomeHost(),
	})
	e.peerFiles = testFiles
	e.peers["zzz"] = &peerInfo{canHost: true, lastSeen: time.Now()}
	if e.shouldElectSelf() {
		t.Fatal("smaller pubkey must defer to a larger eligible peer")
	}
}

// TestGreetAdvertisesDynamicCanHost: the hello sent to a newly-seen peer
// carries live eligibility, so a completed streaming joiner advertises
// CanHost with no local files configured. Flipping the hook mid-session
// changes the very next advertisement.
func TestGreetAdvertisesDynamicCanHost(t *testing.T) {
	pf, _ := capture()
	done := false
	e := New(Deps{
		SelfPub:        "zzz",
		Printf:         pf,
		Nick:           "n",
		CanHostDynamic: func() bool { return done },
	})

	takeHello := func() protocol.Message {
		t.Helper()
		for {
			select {
			case m := <-e.outbox:
				if m.Type == protocol.MsgHello {
					return m
				}
			default:
				t.Fatal("no hello broadcast")
			}
		}
	}

	e.greet(t.Context(), "peer1", &peerInfo{}, time.Now())
	if m := takeHello(); m.CanHost {
		t.Fatal("mid-download joiner advertised CanHost")
	}
	done = true
	e.greet(t.Context(), "peer2", &peerInfo{}, time.Now())
	if m := takeHello(); !m.CanHost {
		t.Fatal("completed joiner failed to advertise CanHost")
	}
}
