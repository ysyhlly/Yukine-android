// Package nostrx carries protocol messages over public nostr relays:
// each message is NIP-44-encrypted with the room's conversation key
// and published as an ephemeral event (relays forward, don't store)
// tagged with the public room ID. This is the only package that
// imports go-nostr.
package nostrx

import (
	"context"
	"fmt"
	"sort"
	"sync"
	"time"

	"github.com/nbd-wtf/go-nostr"
	"github.com/nbd-wtf/go-nostr/nip44"

	"github.com/swayam-mishra/junto/internal/debug"
	"github.com/swayam-mishra/junto/internal/protocol"
	"github.com/swayam-mishra/junto/internal/room"
)

// Kind is in the ephemeral range (20000-29999) and avoids kinds with
// assigned meanings (22242, 23194/5, 24133, 24242, 27235).
const Kind = 29888

// Replay-protection windows for the receive path. The room tag is
// cleartext, so anyone watching a relay (including the relay itself) can
// capture a validly signed event and re-publish it verbatim; go-nostr's
// own duplicate-event cache only catches that within ~60s
// (pool.seenAlreadyDropTick), so a replay after that window would
// otherwise be accepted as new. eventFreshnessWindow rejects anything
// older than this outright — generous enough for real propagation delay
// and relay/system clock skew, not tight NTP-grade sync (that's the
// syncer's job for playback timing, not this). eventFutureSlack catches
// an implausibly forward-dated event the same way.
const (
	eventFreshnessWindow = 90 * time.Second
	eventFutureSlack     = 30 * time.Second
)

// eventVerdict classifies one inbound event's timing, independent of any
// live relay connection so the logic is unit-testable on its own.
type eventVerdict int

const (
	eventFresh eventVerdict = iota
	eventTooStale
	eventTooFuture
	eventReplayed
)

// checkEventFreshness applies the freshness/future/replay checks. lastSeen
// is the highest CreatedAt already accepted from this same sender, if any
// (hasLastSeen false the first time a sender is seen). A tie (evTime
// exactly equal to lastSeen) is accepted, not treated as a replay — two
// legitimate events from the same sender can share a CreatedAt second
// (nostr timestamps are second-granularity), and go-nostr's own
// duplicate-event cache already catches an exact-duplicate republish
// within its own window.
func checkEventFreshness(now, evTime, lastSeen time.Time, hasLastSeen bool) eventVerdict {
	age := now.Sub(evTime)
	switch {
	case age > eventFreshnessWindow:
		return eventTooStale
	case age < -eventFutureSlack:
		return eventTooFuture
	case hasLastSeen && evTime.Before(lastSeen):
		return eventReplayed
	default:
		return eventFresh
	}
}

// inboundDecryptLimit and inboundDecryptWindow cap how many NIP-44 decrypt
// attempts the receive loop makes per window. The room tag is cleartext,
// so anyone who's observed it — not just room members — can flood the
// subscription with garbage tagged with the right room id; each one
// otherwise costs a decrypt (an ECDH + AEAD operation) before it's
// recognized as junk. Past the cap, an event is dropped before decrypting
// it at all, bounding the CPU cost of a flood. 200/s is far above any
// legitimate room's real event rate (heartbeats every 2s per peer) but
// low enough to keep a flood's CPU cost bounded.
const (
	inboundDecryptLimit  = 200
	inboundDecryptWindow = time.Second
)

// inboundLimiter is a plain fixed-window counter, not a smooth token
// bucket: the goal is a hard ceiling on worst-case decrypt attempts per
// window, not fairness. Not safe for concurrent use — the receive loop
// is single-goroutine.
type inboundLimiter struct {
	limit       int
	window      time.Duration
	windowStart time.Time
	count       int
}

func newInboundLimiter(limit int, window time.Duration) *inboundLimiter {
	return &inboundLimiter{limit: limit, window: window}
}

// allow reports whether one more attempt fits in the current window,
// resetting the window if it has elapsed.
func (l *inboundLimiter) allow(now time.Time) bool {
	if now.Sub(l.windowStart) >= l.window {
		l.windowStart = now
		l.count = 0
	}
	l.count++
	return l.count <= l.limit
}

// relayDeadCooldown is how long a relay is excluded from sends after
// accumulating relayFailThreshold consecutive failures.
const (
	relayDeadCooldown  = 60 * time.Second
	relayFailThreshold = 3
)

// sendAckTarget is how many relay acks Send waits for before returning
// success. A one-shot control message (kick/hello/signal/file-req) isn't
// retried or resent like a heartbeat is, so landing on just one relay
// risks a peer subscribed elsewhere silently missing it entirely.
// sendAckGrace bounds how long Send waits for a second ack once the first
// has landed — past that, returning with whatever landed beats holding
// up the caller for a straggling or dead relay.
const (
	sendAckTarget = 2
	sendAckGrace  = 750 * time.Millisecond
)

// relayScore tracks publish latency and health for one relay.
type relayScore struct {
	emaMs  float64   // EMA of publish-ack latency in ms (α=0.3)
	fails  int       // consecutive failures
	deadAt time.Time // non-zero while relay is in cooldown
	// lastRecv is the most recent time the receive loop got anything at
	// all from this relay (including our own broadcasts echoed back) —
	// a health signal for the subscription side, independent of fails/
	// deadAt which track only the publish side. Initialized to the
	// transport's start time (see New), not the zero value, so a
	// brand-new session isn't immediately reported as not receiving
	// before the first heartbeat has had a chance to round-trip.
	lastRecv time.Time
}

// receiveHealthWindow is how long without receiving anything — including
// our own heartbeat echoed back — before a relay's subscription is
// treated as possibly stalled rather than just quiet. Heartbeats go out
// every 2s per peer, including a lone host with nobody else in the room
// yet, so a healthy subscription echoes those back well within this
// window; go-nostr's own reconnect backoff for the read side can run up
// to 5 minutes, during which sends may keep succeeding.
const receiveHealthWindow = 15 * time.Second

// RelayHealth is a snapshot of one relay's health, exported for junto doctor.
type RelayHealth struct {
	URL       string
	LatMs     float64
	Healthy   bool // publish side: reachable and not in a failure cooldown
	Receiving bool // receive side: the subscription has delivered something recently
}

var DefaultRelays = []string{
	"wss://relay.damus.io",
	"wss://nos.lol",
	"wss://relay.primal.net",
	"wss://nostr.mom",
}

type Transport struct {
	pool    *nostr.SimplePool
	relays  []string
	rm      *room.Room
	sk      string
	selfPub string
	out     chan protocol.Message
	cancel  context.CancelFunc

	mu     sync.Mutex
	scores map[string]*relayScore
	log    *debug.Logger
}

// SetLogger attaches a debug logger. Call once after New, before any Send.
func (t *Transport) SetLogger(l *debug.Logger) { t.log = l }

// New connects to the relays and starts the room subscription. Each
// session uses a fresh throwaway keypair; the pubkey is the peer ID.
// The transport's lifetime is governed by Close, not by a caller
// context: the goodbye message must still be publishable after the
// session context is canceled (Ctrl-C).
func New(relays []string, rm *room.Room) (*Transport, error) {
	if len(relays) == 0 {
		relays = DefaultRelays
	}
	sk := nostr.GeneratePrivateKey()
	pub, err := nostr.GetPublicKey(sk)
	if err != nil {
		return nil, fmt.Errorf("deriving session pubkey: %w", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	now := time.Now()
	scores := make(map[string]*relayScore, len(relays))
	for _, r := range relays {
		scores[r] = &relayScore{lastRecv: now}
	}
	t := &Transport{
		pool:    nostr.NewSimplePool(ctx),
		relays:  relays,
		rm:      rm,
		sk:      sk,
		selfPub: pub,
		out:     make(chan protocol.Message, 64),
		cancel:  cancel,
		scores:  scores,
	}

	since := nostr.Timestamp(time.Now().Add(-5 * time.Second).Unix())
	sub := t.pool.SubscribeMany(ctx, relays, nostr.Filter{
		Kinds: []int{Kind},
		Tags:  nostr.TagMap{"r": []string{rm.ID}},
		Since: &since,
	})

	go func() {
		defer close(t.out)
		// lastSeen is the highest CreatedAt accepted from each sender so
		// far, for as long as this transport lives (unlike go-nostr's
		// short-lived dedup cache). A captured-and-replayed event's
		// CreatedAt can't be forged higher without the sender's key, so
		// once any newer event from that sender has been accepted, an old
		// one replayed at any point afterward — even long after the
		// freshness window and go-nostr's own cache have both moved on —
		// is rejected here.
		lastSeen := make(map[string]time.Time)
		limiter := newInboundLimiter(inboundDecryptLimit, inboundDecryptWindow)
		var rateDropped, decryptFailed int64
		for rev := range sub {
			if rev.Event == nil {
				continue
			}
			if rev.Relay != nil {
				// Recorded before the self-echo check: our own heartbeats
				// coming back are exactly the liveness signal a lone host
				// with nobody else in the room yet still gets every 2s.
				t.recordReceive(rev.Relay.URL)
			}
			if rev.PubKey == t.selfPub {
				continue // self-echo from relays
			}
			evTime := rev.CreatedAt.Time()
			last, hasLast := lastSeen[rev.PubKey]
			switch checkEventFreshness(time.Now(), evTime, last, hasLast) {
			case eventTooStale:
				t.log.E("event_stale", "peer", rev.PubKey[:min(8, len(rev.PubKey))])
				continue
			case eventTooFuture:
				t.log.E("event_future", "peer", rev.PubKey[:min(8, len(rev.PubKey))])
				continue
			case eventReplayed:
				t.log.E("event_replay", "peer", rev.PubKey[:min(8, len(rev.PubKey))])
				continue
			}
			lastSeen[rev.PubKey] = evTime

			if !limiter.allow(time.Now()) {
				rateDropped++
				// Logged every 100th drop (not every one) so a sustained
				// flood can't also turn the debug log itself into a burn.
				if rateDropped == 1 || rateDropped%100 == 0 {
					t.log.E("inbound_rate_limited", "dropped", rateDropped)
				}
				continue
			}
			plain, err := nip44.Decrypt(rev.Content, rm.ConvKey)
			if err != nil {
				decryptFailed++
				if decryptFailed == 1 || decryptFailed%100 == 0 {
					t.log.E("event_undecryptable", "count", decryptFailed)
				}
				continue // not a room member or garbage; drop silently
			}
			m, err := protocol.Decode([]byte(plain))
			if err != nil {
				continue
			}
			if m.To != "" && m.To != t.selfPub {
				continue // addressed to another peer
			}
			m.From = rev.PubKey
			select {
			case t.out <- m:
			case <-ctx.Done():
				return
			}
		}
	}()
	return t, nil
}

func (t *Transport) SelfPubKey() string { return t.selfPub }

// liveRelays returns the relay list with unhealthy relays excluded (after
// relayDeadCooldown they are re-admitted as probes). Survivors are sorted
// by EMA latency, fastest first, so PublishMany reaches the best relay
// first and we return on its ACK. Falls back to the full list if all
// relays are currently marked dead.
func (t *Transport) liveRelays() []string {
	now := time.Now()
	t.mu.Lock()
	defer t.mu.Unlock()
	var live []string
	for _, r := range t.relays {
		sc := t.scores[r]
		if sc.deadAt.IsZero() || now.Sub(sc.deadAt) >= relayDeadCooldown {
			live = append(live, r)
		}
	}
	if len(live) == 0 {
		live = t.relays // safety net: all dead? try everything
	}
	sort.Slice(live, func(i, j int) bool {
		si, sj := t.scores[live[i]], t.scores[live[j]]
		// unscored relays (emaMs==0) sort last
		if si.emaMs == 0 {
			return false
		}
		if sj.emaMs == 0 {
			return true
		}
		return si.emaMs < sj.emaMs
	})
	return live
}

// RelayStats returns a snapshot of each relay's health for junto doctor.
func (t *Transport) RelayStats() []RelayHealth {
	now := time.Now()
	t.mu.Lock()
	defer t.mu.Unlock()
	out := make([]RelayHealth, 0, len(t.relays))
	for _, r := range t.relays {
		sc := t.scores[r]
		healthy := sc.deadAt.IsZero() || now.Sub(sc.deadAt) >= relayDeadCooldown
		receiving := now.Sub(sc.lastRecv) < receiveHealthWindow
		out = append(out, RelayHealth{URL: r, LatMs: sc.emaMs, Healthy: healthy, Receiving: receiving})
	}
	return out
}

// recordReceive marks that the receive loop just got something — of any
// kind, including our own broadcasts echoed back — from relay url. It's
// the health signal Receiving (and RelayStats' per-relay Receiving field)
// are computed from.
func (t *Transport) recordReceive(url string) {
	now := time.Now()
	t.mu.Lock()
	sc, ok := t.scores[url]
	if !ok {
		sc = &relayScore{}
		t.scores[url] = sc
	}
	sc.lastRecv = now
	t.mu.Unlock()
}

// Receiving reports whether any relay has delivered anything within
// receiveHealthWindow. Unlike RelayStats' per-relay detail, this is a
// single aggregate signal meant for the session loop to detect a stalled
// subscription even while Send keeps succeeding — see relayScore.lastRecv.
func (t *Transport) Receiving() bool {
	now := time.Now()
	t.mu.Lock()
	defer t.mu.Unlock()
	for _, sc := range t.scores {
		if now.Sub(sc.lastRecv) < receiveHealthWindow {
			return true
		}
	}
	return false
}

// Send encrypts, signs and publishes m to all live relays. It succeeds if
// at least one relay accepts the event and records per-relay latency.
func (t *Transport) Send(ctx context.Context, m protocol.Message) error {
	plain, err := protocol.Encode(m)
	if err != nil {
		return err
	}
	cipher, err := nip44.Encrypt(string(plain), t.rm.ConvKey)
	if err != nil {
		return fmt.Errorf("encrypting message: %w", err)
	}
	ev := nostr.Event{
		Kind:      Kind,
		CreatedAt: nostr.Now(),
		Tags:      nostr.Tags{{"r", t.rm.ID}},
		Content:   cipher,
	}
	if err := ev.Sign(t.sk); err != nil {
		return fmt.Errorf("signing event: %w", err)
	}

	ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	start := time.Now()
	results := t.pool.PublishMany(ctx, t.liveRelays(), ev)

	return collectAcks(results, func(res nostr.PublishResult) {
		ms := float64(time.Since(start).Milliseconds())
		// Use RelayURL, not res.Relay.URL: when a relay can't be dialed
		// PublishMany emits a result with a nil Relay (the very failure
		// the scorer needs to record), so dereferencing Relay would panic.
		url := res.RelayURL
		t.mu.Lock()
		sc, ok := t.scores[url]
		if !ok {
			sc = &relayScore{}
			t.scores[url] = sc
		}
		if res.Error == nil {
			wasDown := !sc.deadAt.IsZero()
			if sc.emaMs == 0 {
				sc.emaMs = ms
			} else {
				sc.emaMs = sc.emaMs*0.7 + ms*0.3
			}
			sc.fails = 0
			sc.deadAt = time.Time{}
			t.mu.Unlock()
			t.log.E("relay_publish", "url", url, "latency_ms", ms, "ok", true)
			if wasDown {
				t.log.E("relay_alive", "url", url)
			}
			return
		}
		sc.fails++
		wasAlive := sc.deadAt.IsZero()
		if sc.fails >= relayFailThreshold {
			sc.deadAt = time.Now()
		}
		t.mu.Unlock()
		t.log.E("relay_publish", "url", url, "latency_ms", ms, "ok", false)
		if wasAlive && sc.fails >= relayFailThreshold {
			t.log.E("relay_dead", "url", url, "fails", sc.fails)
		}
	})
}

// collectAcks drains results (recording each one via record for scoring and
// logging) until sendAckTarget successes have landed, sendAckGrace has
// elapsed since the first success, or results closes — whichever comes
// first. It returns nil once at least one ack has landed, matching Send's
// "succeeds if at least one relay accepts" contract; the target/grace
// window only raises how many relays a message tends to durably reach
// before Send returns, not the minimum required for success. Kept
// independent of *Transport (and the live nostr.SimplePool it wraps) so
// the ack-collection policy is unit-testable with a synthetic channel.
func collectAcks(results <-chan nostr.PublishResult, record func(nostr.PublishResult)) error {
	var lastErr error
	acks := 0
	var grace <-chan time.Time // armed once the first ack lands
	for {
		var res nostr.PublishResult
		var open bool
		select {
		case res, open = <-results:
			if !open {
				if acks > 0 {
					return nil
				}
				if lastErr == nil {
					lastErr = fmt.Errorf("no relay responded")
				}
				return fmt.Errorf("publish failed on all relays: %w", lastErr)
			}
		case <-grace:
			return nil // at least one ack already landed; stop waiting on stragglers
		}

		record(res)
		if res.Error == nil {
			acks++
			if acks == 1 {
				grace = time.After(sendAckGrace)
			}
			if acks >= sendAckTarget {
				return nil
			}
			continue
		}
		lastErr = res.Error
	}
}

// Receive yields decrypted room messages from other peers. Messages
// addressed to someone else (To set, not us) are filtered out.
func (t *Transport) Receive() <-chan protocol.Message { return t.out }

func (t *Transport) Close() {
	t.cancel()
	t.pool.Close("session ended")
}
