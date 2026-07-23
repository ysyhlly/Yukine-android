package nostrx

import (
	"fmt"
	"testing"
	"time"

	"github.com/nbd-wtf/go-nostr"
)

// newTestTransport builds a Transport with only the relay-scoring state
// populated — enough to exercise liveRelays/RelayStats without a pool.
func newTestTransport(relays []string, scores map[string]*relayScore) *Transport {
	return &Transport{relays: relays, scores: scores}
}

func TestLiveRelaysSortsFastestFirst(t *testing.T) {
	relays := []string{"wss://slow", "wss://fast", "wss://mid"}
	tr := newTestTransport(relays, map[string]*relayScore{
		"wss://slow": {emaMs: 300},
		"wss://fast": {emaMs: 50},
		"wss://mid":  {emaMs: 150},
	})
	got := tr.liveRelays()
	want := []string{"wss://fast", "wss://mid", "wss://slow"}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("liveRelays order = %v; want %v", got, want)
		}
	}
}

func TestLiveRelaysUnscoredSortLast(t *testing.T) {
	relays := []string{"wss://unscored", "wss://scored"}
	tr := newTestTransport(relays, map[string]*relayScore{
		"wss://unscored": {emaMs: 0}, // never measured
		"wss://scored":   {emaMs: 120},
	})
	got := tr.liveRelays()
	if got[0] != "wss://scored" || got[1] != "wss://unscored" {
		t.Fatalf("unscored relay should sort last; got %v", got)
	}
}

func TestLiveRelaysExcludesDeadWithinCooldown(t *testing.T) {
	relays := []string{"wss://dead", "wss://ok"}
	tr := newTestTransport(relays, map[string]*relayScore{
		"wss://dead": {emaMs: 40, fails: relayFailThreshold, deadAt: time.Now()},
		"wss://ok":   {emaMs: 200},
	})
	got := tr.liveRelays()
	if len(got) != 1 || got[0] != "wss://ok" {
		t.Fatalf("dead relay should be excluded; got %v", got)
	}
}

func TestLiveRelaysReadmitsAfterCooldown(t *testing.T) {
	relays := []string{"wss://recovered"}
	tr := newTestTransport(relays, map[string]*relayScore{
		// died longer ago than the cooldown — eligible again as a probe.
		"wss://recovered": {emaMs: 40, fails: relayFailThreshold, deadAt: time.Now().Add(-relayDeadCooldown - time.Second)},
	})
	got := tr.liveRelays()
	if len(got) != 1 || got[0] != "wss://recovered" {
		t.Fatalf("relay past cooldown should be re-admitted; got %v", got)
	}
}

func TestLiveRelaysFallbackWhenAllDead(t *testing.T) {
	relays := []string{"wss://a", "wss://b"}
	dead := time.Now()
	tr := newTestTransport(relays, map[string]*relayScore{
		"wss://a": {fails: relayFailThreshold, deadAt: dead},
		"wss://b": {fails: relayFailThreshold, deadAt: dead},
	})
	got := tr.liveRelays()
	if len(got) != 2 {
		t.Fatalf("all-dead should fall back to the full relay list; got %v", got)
	}
}

func TestRelayStatsReportsHealth(t *testing.T) {
	relays := []string{"wss://up", "wss://down"}
	tr := newTestTransport(relays, map[string]*relayScore{
		"wss://up":   {emaMs: 75},
		"wss://down": {fails: relayFailThreshold, deadAt: time.Now()},
	})
	stats := tr.RelayStats()
	byURL := map[string]RelayHealth{}
	for _, s := range stats {
		byURL[s.URL] = s
	}
	if !byURL["wss://up"].Healthy || byURL["wss://up"].LatMs != 75 {
		t.Errorf("up relay: got %+v; want healthy, 75ms", byURL["wss://up"])
	}
	if byURL["wss://down"].Healthy {
		t.Errorf("down relay should report unhealthy; got %+v", byURL["wss://down"])
	}
}

// TestReceivingTrueWhenRecentlyRecorded is the regression test for the
// invisible-dead-subscription bug: relay health was publish-only, so a
// stalled receive side (go-nostr's own reconnect backoff can run up to 5
// minutes) looked exactly like a quiet room. Receiving must report
// healthy right after a receive is recorded, and unhealthy once every
// relay's last receive falls outside the window.
func TestReceivingTrueWhenRecentlyRecorded(t *testing.T) {
	tr := newTestTransport([]string{"wss://a"}, map[string]*relayScore{
		"wss://a": {lastRecv: time.Now()},
	})
	if !tr.Receiving() {
		t.Error("expected Receiving() true right after a recent receive")
	}
}

func TestReceivingFalseWhenStale(t *testing.T) {
	tr := newTestTransport([]string{"wss://a", "wss://b"}, map[string]*relayScore{
		"wss://a": {lastRecv: time.Now().Add(-receiveHealthWindow - time.Second)},
		"wss://b": {lastRecv: time.Now().Add(-receiveHealthWindow - time.Second)},
	})
	if tr.Receiving() {
		t.Error("expected Receiving() false when every relay's last receive is outside the window")
	}
}

// TestReceivingTrueIfAnyRelayHealthy checks the aggregate: only one of
// several relays needs to be delivering for the subscription overall to
// be considered alive.
func TestReceivingTrueIfAnyRelayHealthy(t *testing.T) {
	tr := newTestTransport([]string{"wss://dead", "wss://alive"}, map[string]*relayScore{
		"wss://dead":  {lastRecv: time.Now().Add(-receiveHealthWindow - time.Second)},
		"wss://alive": {lastRecv: time.Now()},
	})
	if !tr.Receiving() {
		t.Error("expected Receiving() true when at least one relay is delivering")
	}
}

// TestRecordReceiveUpdatesLastRecv checks recordReceive actually moves
// the needle recordReceive/Receiving/RelayStats all read from.
func TestRecordReceiveUpdatesLastRecv(t *testing.T) {
	tr := newTestTransport([]string{"wss://a"}, map[string]*relayScore{
		"wss://a": {lastRecv: time.Now().Add(-receiveHealthWindow - time.Second)},
	})
	if tr.Receiving() {
		t.Fatal("precondition: should start stale")
	}
	tr.recordReceive("wss://a")
	if !tr.Receiving() {
		t.Error("recordReceive should have refreshed lastRecv, making Receiving() true")
	}
}

func TestRelayStatsReportsReceiving(t *testing.T) {
	tr := newTestTransport([]string{"wss://fresh", "wss://stale"}, map[string]*relayScore{
		"wss://fresh": {lastRecv: time.Now()},
		"wss://stale": {lastRecv: time.Now().Add(-receiveHealthWindow - time.Second)},
	})
	byURL := map[string]RelayHealth{}
	for _, s := range tr.RelayStats() {
		byURL[s.URL] = s
	}
	if !byURL["wss://fresh"].Receiving {
		t.Errorf("fresh relay should report Receiving=true, got %+v", byURL["wss://fresh"])
	}
	if byURL["wss://stale"].Receiving {
		t.Errorf("stale relay should report Receiving=false, got %+v", byURL["wss://stale"])
	}
}

// --- replay protection ---

func TestCheckEventFreshnessAcceptsFreshEvent(t *testing.T) {
	now := time.Now()
	if got := checkEventFreshness(now, now.Add(-time.Second), time.Time{}, false); got != eventFresh {
		t.Errorf("a just-sent event should be fresh, got %v", got)
	}
}

func TestCheckEventFreshnessRejectsStaleEvent(t *testing.T) {
	now := time.Now()
	old := now.Add(-eventFreshnessWindow - time.Second)
	if got := checkEventFreshness(now, old, time.Time{}, false); got != eventTooStale {
		t.Errorf("an event past the freshness window should be rejected, got %v", got)
	}
	// Just inside the window must still be accepted.
	edge := now.Add(-eventFreshnessWindow + time.Second)
	if got := checkEventFreshness(now, edge, time.Time{}, false); got != eventFresh {
		t.Errorf("an event just inside the freshness window should be accepted, got %v", got)
	}
}

func TestCheckEventFreshnessRejectsFutureEvent(t *testing.T) {
	now := time.Now()
	future := now.Add(eventFutureSlack + time.Second)
	if got := checkEventFreshness(now, future, time.Time{}, false); got != eventTooFuture {
		t.Errorf("an implausibly future-dated event should be rejected, got %v", got)
	}
	// Small clock skew into the future must still be tolerated.
	skewed := now.Add(eventFutureSlack - time.Second)
	if got := checkEventFreshness(now, skewed, time.Time{}, false); got != eventFresh {
		t.Errorf("an event within the future-slack allowance should be accepted, got %v", got)
	}
}

// TestCheckEventFreshnessRejectsReplay is the regression test for the
// replay vulnerability: a signed event captured off a relay and
// re-published after go-nostr's own ~60s duplicate cache has evicted it
// (but still within the freshness window, which is deliberately wider —
// see checkEventFreshness) must be rejected once a newer event from the
// same sender has already been accepted, since staleness alone wouldn't
// catch it in that gap.
func TestCheckEventFreshnessRejectsReplay(t *testing.T) {
	now := time.Now()
	original := now.Add(-70 * time.Second) // past go-nostr's ~60s cache, still inside our 90s window
	lastSeen := now.Add(-time.Second)      // a newer event already arrived from this sender
	if got := checkEventFreshness(now, original, lastSeen, true); got != eventReplayed {
		t.Errorf("a captured-and-replayed old event should be rejected, got %v", got)
	}
}

// TestCheckEventFreshnessAllowsSameSecondTie guards against a false
// positive: nostr timestamps are second-granularity, so two genuine
// events from the same sender can legitimately share a CreatedAt. A tie
// must not be mistaken for a replay.
func TestCheckEventFreshnessAllowsSameSecondTie(t *testing.T) {
	now := time.Now()
	t0 := now.Add(-time.Second)
	if got := checkEventFreshness(now, t0, t0, true); got != eventFresh {
		t.Errorf("an event tied with lastSeen should be accepted, not treated as a replay, got %v", got)
	}
}

// TestCheckEventFreshnessAcceptsFirstEventFromSender guards the
// no-history case: a sender's very first event has no lastSeen entry yet
// and must not be rejected as a replay.
func TestCheckEventFreshnessAcceptsFirstEventFromSender(t *testing.T) {
	now := time.Now()
	if got := checkEventFreshness(now, now, time.Time{}, false); got != eventFresh {
		t.Errorf("a sender's first event should be accepted, got %v", got)
	}
}

// TestCollectAcksWaitsGraceWindowAfterFirstAck is the regression test for
// the single-relay-durability bug: Send used to return the instant the
// first relay acked, canceling the context and aborting the still-pending
// publishes to every other relay — a one-shot control message (kick/
// hello/signal/file-req) could then durably land on just one relay, and a
// peer subscribed elsewhere would silently miss it. collectAcks must now
// hold open for a second ack (up to sendAckGrace) instead of returning
// immediately after the first.
func TestCollectAcksWaitsGraceWindowAfterFirstAck(t *testing.T) {
	results := make(chan nostr.PublishResult, 4)
	results <- nostr.PublishResult{RelayURL: "wss://a"}
	// Deliberately never send a second result or close the channel: this
	// models a second relay whose publish is still in flight when the
	// first one acks — exactly the case the old code canceled outright.

	var recorded []string
	start := time.Now()
	err := collectAcks(results, func(res nostr.PublishResult) {
		recorded = append(recorded, res.RelayURL)
	})
	elapsed := time.Since(start)

	if err != nil {
		t.Fatalf("collectAcks returned an error after a successful ack: %v", err)
	}
	if elapsed < sendAckGrace {
		t.Fatalf("collectAcks returned after %v — it must wait the %v grace window for a second ack instead of returning instantly on the first", elapsed, sendAckGrace)
	}
	if len(recorded) != 1 || recorded[0] != "wss://a" {
		t.Fatalf("record callback got %v, want exactly one call for wss://a", recorded)
	}
}

// TestCollectAcksReturnsOnceTargetReached checks the other half of the
// fix: once sendAckTarget acks have actually landed, collectAcks returns
// right away rather than idling out the rest of the grace window.
func TestCollectAcksReturnsOnceTargetReached(t *testing.T) {
	results := make(chan nostr.PublishResult, 4)
	results <- nostr.PublishResult{RelayURL: "wss://a"}
	results <- nostr.PublishResult{RelayURL: "wss://b"}

	start := time.Now()
	err := collectAcks(results, func(nostr.PublishResult) {})
	elapsed := time.Since(start)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if elapsed >= sendAckGrace {
		t.Fatalf("collectAcks took %v to return two already-buffered acks — it should return as soon as sendAckTarget is reached", elapsed)
	}
}

// TestCollectAcksReturnsPromptlyWhenNoMoreRelaysPending covers the
// single-live-relay case: once the results channel closes there is
// nothing left to wait for, so collectAcks must not sit out the grace
// window it would otherwise use to wait for a second ack.
func TestCollectAcksReturnsPromptlyWhenNoMoreRelaysPending(t *testing.T) {
	results := make(chan nostr.PublishResult, 1)
	results <- nostr.PublishResult{RelayURL: "wss://only"}
	close(results)

	start := time.Now()
	err := collectAcks(results, func(nostr.PublishResult) {})
	elapsed := time.Since(start)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if elapsed >= sendAckGrace {
		t.Fatalf("collectAcks took %v after its only relay's result and a closed channel left nothing to wait for", elapsed)
	}
}

// TestCollectAcksErrorsWhenAllFail preserves Send's original contract:
// if no relay ever acks, the caller gets an error naming the failure.
func TestCollectAcksErrorsWhenAllFail(t *testing.T) {
	results := make(chan nostr.PublishResult, 2)
	results <- nostr.PublishResult{RelayURL: "wss://a", Error: fmt.Errorf("boom a")}
	results <- nostr.PublishResult{RelayURL: "wss://b", Error: fmt.Errorf("boom b")}
	close(results)

	if err := collectAcks(results, func(nostr.PublishResult) {}); err == nil {
		t.Fatal("expected an error when every relay fails")
	}
}

// TestInboundLimiterAllowsUpToLimit is the regression test for the
// decrypt-flood bug: the receive loop used to attempt a NIP-44 decrypt
// (an ECDH + AEAD operation) on every event matching the room's cleartext
// tag with no cap at all, so anyone who'd observed that tag — not just
// room members — could burn CPU by flooding garbage tagged events. The
// limiter must allow exactly `limit` attempts per window and reject the
// rest.
func TestInboundLimiterAllowsUpToLimit(t *testing.T) {
	now := time.Now()
	l := newInboundLimiter(3, time.Second)
	for i := 0; i < 3; i++ {
		if !l.allow(now) {
			t.Fatalf("attempt %d should be allowed (within limit)", i)
		}
	}
	if l.allow(now) {
		t.Fatal("4th attempt within the same window should be rejected")
	}
	if l.allow(now) {
		t.Fatal("5th attempt within the same window should also be rejected")
	}
}

// TestInboundLimiterResetsNextWindow checks the cap is per-window, not a
// lifetime total — legitimate traffic must resume once the window rolls
// over.
func TestInboundLimiterResetsNextWindow(t *testing.T) {
	now := time.Now()
	l := newInboundLimiter(2, time.Second)
	if !l.allow(now) || !l.allow(now) {
		t.Fatal("first two attempts should be allowed")
	}
	if l.allow(now) {
		t.Fatal("3rd attempt in the same window should be rejected")
	}
	if !l.allow(now.Add(time.Second)) {
		t.Fatal("first attempt in the next window should be allowed")
	}
}
