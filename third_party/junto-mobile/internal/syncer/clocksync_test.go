package syncer

import (
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestEma64(t *testing.T) {
	if got := ema64(0, 500); got != 500 {
		t.Errorf("first sample should be taken as-is: got %d want 500", got)
	}
	// 0.7*1000 + 0.3*0 = 700
	if got := ema64(1000, 0); got != 700 {
		t.Errorf("EMA blend wrong: got %d want 700", got)
	}
	// 0.7*100 + 0.3*200 = 130
	if got := ema64(100, 200); got != 130 {
		t.Errorf("EMA blend wrong: got %d want 130", got)
	}
}

// helloReply builds the echo-bearing message a peer sends back, modeling
// a peer whose clock is `offsetMs` ahead of ours and a one-way network
// delay of `delayMs` each direction. ourSend is T1 in our clock.
func helloReply(ourSend, offsetMs, delayMs int64) (protocol.Message, time.Time) {
	// Our message reaches the peer at our-time ourSend+delay; in the
	// peer's clock that instant is +offsetMs.
	peerRecv := ourSend + delayMs + offsetMs // T2 (peer clock)
	peerSend := peerRecv                     // T3 (peer clock), instant turnaround
	ourRecv := ourSend + 2*delayMs           // T4 (our clock)
	m := protocol.Message{
		SentAt:   peerSend,
		EchoAt:   ourSend,
		EchoRcvd: peerRecv,
	}
	return m, time.UnixMilli(ourRecv)
}

func TestUpdateClockSyncOffsetSign(t *testing.T) {
	e := New(Deps{})
	p := &peerInfo{}
	// Peer clock is +5000ms ahead of ours, 100ms each way.
	m, now := helloReply(1000, 5000, 100)
	e.updateClockSync(p, m, now)
	if p.clockOffset != 5000 {
		t.Fatalf("offset (peer−our) should be +5000, got %d", p.clockOffset)
	}

	// A peer 3000ms behind us.
	p2 := &peerInfo{}
	m2, now2 := helloReply(1000, -3000, 50)
	e.updateClockSync(p2, m2, now2)
	if p2.clockOffset != -3000 {
		t.Fatalf("offset should be −3000, got %d", p2.clockOffset)
	}
}

func TestUpdateClockSyncRejectsImplausibleRTT(t *testing.T) {
	e := New(Deps{})
	p := &peerInfo{}
	// Construct a sample whose round-trip delay exceeds maxClockSyncRTT.
	// T1=1000, huge round trip so RTT = (T4-T1)-(T3-T2) > 4000.
	m := protocol.Message{EchoAt: 1000, EchoRcvd: 6100, SentAt: 6100}
	now := time.UnixMilli(1000 + maxClockSyncRTT + 5000) // RTT far over the cap
	e.updateClockSync(p, m, now)
	if p.clockOffset != 0 {
		t.Errorf("an implausible-RTT sample must be rejected, leaving offset 0; got %d", p.clockOffset)
	}
}

func TestUpdateClockSyncRecordsTimestamps(t *testing.T) {
	e := New(Deps{})
	p := &peerInfo{}
	// A plain message (no echo fields) still records the peer's SentAt and
	// our receive time, so our next reply can echo them.
	now := time.UnixMilli(9000)
	e.updateClockSync(p, protocol.Message{SentAt: 4242}, now)
	if p.lastRcvdSentAt != 4242 || p.lastRcvdAt != 9000 {
		t.Errorf("timestamps not recorded: got sentAt=%d rcvdAt=%d", p.lastRcvdSentAt, p.lastRcvdAt)
	}
	// No echo fields → no offset computed yet.
	if p.clockOffset != 0 {
		t.Errorf("offset should stay 0 without echo fields, got %d", p.clockOffset)
	}
	// SentAt can also ride in the PlayState (heartbeats).
	p2 := &peerInfo{}
	e.updateClockSync(p2, protocol.Message{State: &protocol.PlayState{SentAt: 777}}, time.UnixMilli(8000))
	if p2.lastRcvdSentAt != 777 {
		t.Errorf("PlayState.SentAt should be recorded, got %d", p2.lastRcvdSentAt)
	}
}
