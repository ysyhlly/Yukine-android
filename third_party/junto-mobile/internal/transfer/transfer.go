// Package transfer moves the media file from the host to a joiner
// over a WebRTC data channel. Signaling (one SDP offer and one answer)
// travels through the room's nostr transport. ICE is non-trickle: each
// side waits for gathering to complete and sends a single SDP blob —
// one event each way instead of a stream of candidates, which matters
// over relay latency. STUN hole-punching is tried first; when it fails,
// the download silently retries through a built-in public TURN relay
// (or one supplied with -turn).
package transfer

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"

	"github.com/swayam-mishra/junto/internal/protocol"
)

var chunkSize = 16 * 1024

const (
	// Backpressure window: how many unsent bytes may queue on the data
	// channel before the sender pauses. One persistent connection now
	// carries a whole file, so this window is the throughput ceiling over
	// a high-latency link (≈ window ÷ RTT). 8 MiB covers fast links at
	// typical internet RTTs; the old 1 MiB throttled them badly.
	bufferedLow = 4 << 20
	bufferedMax = 8 << 20
)

// connectTimeout bounds ICE gathering/connection and signal waits.
// A var (not const) so tests can shrink it instead of waiting out the
// real timeout.
var connectTimeout = 30 * time.Second

// iceServers is the default STUN list. It's a var so tests can clear
// it (loopback host candidates need no STUN and gathering completes
// instantly); DefaultICEConfig snapshots it.
var iceServers = []webrtc.ICEServer{{URLs: []string{"stun:stun.l.google.com:19302"}}}

// SECURITY: the built-in relay list can be (re)written any number of
// times over a session's life — see ensureFallbackRelay in turn_creds.go,
// which refetches on demand at NAT-failure time when the cache is empty
// or its TTL has elapsed — by whichever goroutine happens to trigger a
// fetch (the session-start warm-up, doctor, or the downloader's own
// NAT-failure path), and read concurrently by the downloader and by
// doctor. Those goroutines have no other happens-before relationship, so
// a bare package var here is a data race (and a torn slice-header read
// could crash). All access goes through the mutex-guarded accessors below.
//
// The list is the TURN relay tried automatically when direct hole-punching
// fails and no -turn was supplied; it stays empty (direct-only) if the
// turn.junto.watch/creds fetch is unreachable.
var (
	fallbackMu     sync.RWMutex
	fallbackRelays []webrtc.ICEServer // guarded by fallbackMu
)

// SetFallbackRelays atomically replaces the built-in relay list. The slice
// is published wholesale and never mutated in place, so readers may hold
// the returned header after unlocking.
func SetFallbackRelays(relays []webrtc.ICEServer) {
	fallbackMu.Lock()
	fallbackRelays = relays
	fallbackMu.Unlock()
}

// getFallbackRelays returns the current built-in relay list under the lock.
func getFallbackRelays() []webrtc.ICEServer {
	fallbackMu.RLock()
	defer fallbackMu.RUnlock()
	return fallbackRelays
}

// natError marks a failure to establish the direct peer connection
// (hole punching failed or timed out) — the trigger for silently
// retrying via the fallback TURN relay.
type natError struct{ err error }

func (e natError) Error() string { return e.err.Error() }
func (e natError) Unwrap() error { return e.err }

func isNATFailure(err error) bool {
	var n natError
	return errors.As(err, &n)
}

// ICEConfig is the set of ICE servers a transfer's PeerConnections use.
// It is passed explicitly (rather than read from a global) so a TURN
// relay supplied on the command line reaches both ServeFile and the
// Downloader.
type ICEConfig struct {
	Servers []webrtc.ICEServer
}

// DefaultICEConfig returns the built-in STUN-only configuration.
func DefaultICEConfig() ICEConfig {
	return ICEConfig{Servers: append([]webrtc.ICEServer(nil), iceServers...)}
}

// WithTURN appends a TURN relay (credentials required by pion) when url
// is non-empty, and returns the extended config. STUN-only NAT
// traversal fails for symmetric NATs; a TURN relay is the fallback.
func (c ICEConfig) WithTURN(url, user, pass string) ICEConfig {
	if url == "" {
		return c
	}
	c.Servers = append(append([]webrtc.ICEServer(nil), c.Servers...), webrtc.ICEServer{
		URLs:       []string{url},
		Username:   user,
		Credential: pass,
	})
	return c
}

// HasRelay reports whether any configured server is a TURN relay.
func (c ICEConfig) HasRelay() bool {
	for _, s := range c.Servers {
		for _, u := range s.URLs {
			if strings.HasPrefix(u, "turn:") || strings.HasPrefix(u, "turns:") {
				return true
			}
		}
	}
	return false
}

// WithFallbackRelay returns the config extended with the built-in
// fallback relays (a no-op while the list is empty).
func (c ICEConfig) WithFallbackRelay() ICEConfig {
	c.Servers = append(append([]webrtc.ICEServer(nil), c.Servers...), getFallbackRelays()...)
	return c
}

// awaitGathering blocks until non-trickle ICE candidate gathering
// completes, then the caller sends a single SDP. An unbounded wait here
// could hang a transfer forever if gathering stalls (a slow/black-holed
// STUN server), so it's bounded by connectTimeout and ctx — a timeout
// flows into the normal connect-retry / relay-fallback path.
func awaitGathering(ctx context.Context, pc *webrtc.PeerConnection) error {
	select {
	case <-webrtc.GatheringCompletePromise(pc):
		return nil
	case <-time.After(connectTimeout):
		return fmt.Errorf("ICE gathering timed out")
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (c ICEConfig) newPeerConnection() (*webrtc.PeerConnection, error) {
	return webrtc.NewPeerConnection(webrtc.Configuration{ICEServers: c.Servers})
}

// AddressedSignal pairs a signal with the pubkey it came from.
type AddressedSignal struct {
	From string
	Sig  protocol.Signal
}

// Signaler carries SDP between the two endpoints of one transfer. The
// Signals channel must yield only signals relevant to this transfer
// (the app routes per peer).
type Signaler interface {
	SendSignal(ctx context.Context, to string, s protocol.Signal) error
	Signals() <-chan AddressedSignal
}

// ctrl is the JSON control frame sent as text messages on the data
// channel; file bytes travel as binary messages between them. One
// persistent connection serves a whole file: the joiner sends "start"
// frames to request (or redirect to) a byte range, and the host answers
// each with an "at" marking where the following bytes land, the bytes
// themselves as binary messages, then "eof" when the range is complete.
//
//	joiner → host:  {"t":"start","seq":S,"off":N,"len":L}  (L=0 ⇒ to EOF)
//	                {"t":"ob_req","seq":S}                  (send the bao outboard tree)
//	                {"t":"stop","seq":S}                    (abandon the in-flight range; idle)
//	                {"t":"buf","off":N}                     (buffer depth: N bytes ahead of the playhead)
//	                {"t":"done"}                            (whole file received)
//	host → joiner:  {"t":"at","seq":S,"off":N}             (next bytes begin at N)
//	                {"t":"eof","seq":S}                     (range for S fully sent)
//	                {"t":"ob","seq":S,"len":L}             (next binary frames are the outboard, L bytes)
//	                {"t":"ob_eof","seq":S}                 (outboard fully sent)
//	                {"t":"nak","seq":S}                    (request can't be served here; non-terminal)
//	                {"t":"err","msg":M}                     (host-side failure)
//
// Seq ties an "at"/"eof" back to the "start" that asked for it, so a
// redirect that races an in-flight range is never misattributed. Both
// sides ignore frames with an unknown "t", so the swarm additions
// (ob_req/ob/ob_eof/nak/stop) coexist with pre-swarm binaries — which
// never send or elicit them, since only bao-announcing rooms use them.
type ctrl struct {
	T   string `json:"t"`
	Msg string `json:"msg,omitempty"`
	Seq int64  `json:"seq,omitempty"`
	Off int64  `json:"off,omitempty"`
	Len int64  `json:"len,omitempty"`
}

// disconnectGrace bounds how long a Disconnected transition is allowed to
// self-heal before watchConnection treats it as a real failure.
// Disconnected commonly means nothing worse than a transient ICE
// connectivity flap (a brief network hiccup, NAT rebinding) that recovers
// back to Connected within about a second on its own — unlike Failed or
// Closed, which are unambiguous and never get this grace period. A var
// (not const) so tests can shrink it.
var disconnectGrace = 5 * time.Second

// connectionStateNotifier is the one method watchConnection needs from a
// *webrtc.PeerConnection — narrowed to an interface purely so tests can
// drive synthetic state transitions without a real ICE connection.
type connectionStateNotifier interface {
	OnConnectionStateChange(func(webrtc.PeerConnectionState))
}

// watchConnection returns a channel that closes if the connection fails,
// closes, or stays Disconnected past disconnectGrace. pion invokes the
// state-change callback on a fresh goroutine per transition, so
// near-simultaneous transitions (e.g. Disconnected then Closed during
// teardown, or Disconnected then a recovery back to Connected) run
// concurrently — both the once-only close and the grace timer's
// start/cancel need their own synchronization.
func watchConnection(pc connectionStateNotifier) <-chan struct{} {
	failed := make(chan struct{})
	var once sync.Once
	closeFailed := func() { once.Do(func() { close(failed) }) }

	var mu sync.Mutex
	var grace *time.Timer

	pc.OnConnectionStateChange(func(s webrtc.PeerConnectionState) {
		switch s {
		case webrtc.PeerConnectionStateFailed, webrtc.PeerConnectionStateClosed:
			mu.Lock()
			if grace != nil {
				grace.Stop()
			}
			mu.Unlock()
			closeFailed()
		case webrtc.PeerConnectionStateDisconnected:
			mu.Lock()
			if grace == nil {
				grace = time.AfterFunc(disconnectGrace, closeFailed)
			}
			mu.Unlock()
		default:
			// Most relevantly Connected: the flap self-healed before the
			// grace period elapsed, so cancel the pending failure.
			mu.Lock()
			if grace != nil {
				grace.Stop()
				grace = nil
			}
			mu.Unlock()
		}
	})
	return failed
}

// waitSignal blocks until a signal of the wanted kind arrives from
// peer, ignoring others.
func waitSignal(ctx context.Context, sig Signaler, peer, kind string) (protocol.Signal, error) {
	ctx, cancel := context.WithTimeout(ctx, connectTimeout)
	defer cancel()
	for {
		select {
		case <-ctx.Done():
			return protocol.Signal{}, fmt.Errorf("waiting for %s from %.8s: %w", kind, peer, ctx.Err())
		case as, ok := <-sig.Signals():
			if !ok {
				return protocol.Signal{}, fmt.Errorf("signal channel closed")
			}
			if as.From == peer && as.Sig.Kind == kind {
				return as.Sig, nil
			}
		}
	}
}

// drainSignals discards every signal currently queued, without blocking.
// Only one connection attempt is ever negotiating at a time on a given
// Signaler (Downloader.Run processes files strictly one at a time), so
// anything still sitting in the channel when a fresh attempt starts is
// necessarily left over from a dead one — waitSignal has no way to tell
// a stale offer from a fresh one otherwise, and would happily pair this
// attempt's answer with it.
func drainSignals(sig Signaler) {
	for {
		select {
		case <-sig.Signals():
		default:
			return
		}
	}
}

func marshalCtrl(c ctrl) string {
	b, _ := json.Marshal(c)
	return string(b)
}

func unmarshalCtrl(data []byte, c *ctrl) error {
	return json.Unmarshal(data, c)
}

// sanitizeName reduces a host-supplied file name to a safe basename so
// it cannot escape the download directory.
func sanitizeName(name string) string {
	name = strings.ReplaceAll(name, `\`, "/")
	name = filepath.Base(name)
	if name == "" || name == "." || name == ".." || name == "/" {
		return "download"
	}
	return name
}
