package transfer

import (
	"context"
	"encoding/json"
	"net/http"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"
)

// builtinCredsURL is the endpoint that issues short-lived HMAC credentials
// for the junto public TURN relay (RFC 8489 §9.2). No credential is
// embedded in the binary — only this URL. It is a var so tests can
// substitute an httptest.Server.
var builtinCredsURL = "https://turn.junto.watch/creds"

// builtinCredsTimeout and onDemandCredsTimeout are vars (not consts) so
// tests can shrink them instead of waiting out the real timeouts.
var (
	// builtinCredsTimeout bounds the background warm-up fetch at session
	// start, which must not delay startup — a miss there is not fatal,
	// since ensureFallbackRelay retries on demand at NAT-failure time.
	builtinCredsTimeout = 1 * time.Second
	// onDemandCredsTimeout bounds a NAT-failure-time (re)fetch: the
	// connection has already failed to establish directly, so there's a
	// little real time to spare for a fresh attempt rather than giving up
	// because the 1s warm-up fetch happened to miss.
	onDemandCredsTimeout = 5 * time.Second
)

const (
	// defaultCredsTTL is used when the server's TTL is absent or
	// non-positive, so a malformed response can't cache credentials
	// forever.
	defaultCredsTTL = 5 * time.Minute
)

type turnCredsResponse struct {
	URLs       []string `json:"urls"`
	Username   string   `json:"username"`
	Credential string   `json:"credential"`
	TTL        int      `json:"ttl"` // seconds
}

// credsMu guards credsExpiresAt, the one piece of state ensureFallbackRelay
// needs beyond FallbackRelays itself (which is already mutex-guarded — see
// transfer.go). Zero credsExpiresAt means no credentials have ever been
// successfully fetched.
var (
	credsMu        sync.Mutex
	credsExpiresAt time.Time
)

// InitBuiltinRelay does a best-effort background warm-up of TURN
// credentials at session start, so the common case (direct connection
// succeeds, or fails fast) doesn't pay fetch latency on the hot path. It's
// safe to call concurrently with, and repeatedly after, calls to
// ensureFallbackRelay (e.g. from junto doctor, which calls this
// synchronously) — see ensureFallbackRelay for the caching/retry rules.
func InitBuiltinRelay(ctx context.Context) {
	ensureFallbackRelay(ctx, builtinCredsTimeout)
}

// EnsureFallbackRelayFresh is called at NAT-failure time, once direct
// hole-punching has already failed: it's the last chance to get a working
// TURN fallback before giving up on this connection. Unlike the old
// sync.Once-gated fetch, this can retry — a transient failure or timeout
// during the session-start warm-up no longer permanently disables the
// fallback relay for the rest of the process, and credentials that have
// since expired (the server's TTL, previously ignored entirely) are
// refetched rather than reused past their lifetime.
func EnsureFallbackRelayFresh(ctx context.Context) bool {
	return ensureFallbackRelay(ctx, onDemandCredsTimeout)
}

// ensureFallbackRelay reports whether FallbackRelays holds usable
// credentials, fetching (or refetching) them if the cache is empty or the
// last fetch's TTL has elapsed.
func ensureFallbackRelay(ctx context.Context, timeout time.Duration) bool {
	credsMu.Lock()
	fresh := !credsExpiresAt.IsZero() && time.Now().Before(credsExpiresAt)
	credsMu.Unlock()
	if fresh {
		return true
	}
	srv, ttl, ok := fetchBuiltinCreds(ctx, timeout)
	if !ok {
		return false
	}
	credsMu.Lock()
	credsExpiresAt = time.Now().Add(ttl)
	credsMu.Unlock()
	// Published through the mutex-guarded setter: the downloader goroutine
	// reads this concurrently with no other happens-before.
	SetFallbackRelays([]webrtc.ICEServer{srv})
	return true
}

// fetchBuiltinCreds fetches one round of credentials. On any failure —
// network error, timeout, non-200, malformed body, missing fields — it
// reports ok=false and the caller's existing (possibly still-valid, or
// possibly absent) credentials are left untouched; the failure is silent
// by design.
func fetchBuiltinCreds(ctx context.Context, timeout time.Duration) (srv webrtc.ICEServer, ttl time.Duration, ok bool) {
	rctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	req, err := http.NewRequestWithContext(rctx, http.MethodGet, builtinCredsURL, nil)
	if err != nil {
		return webrtc.ICEServer{}, 0, false
	}
	req.Header.Set("User-Agent", "junto")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return webrtc.ICEServer{}, 0, false
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return webrtc.ICEServer{}, 0, false
	}
	var cr turnCredsResponse
	if err := json.NewDecoder(resp.Body).Decode(&cr); err != nil {
		return webrtc.ICEServer{}, 0, false
	}
	if len(cr.URLs) == 0 || cr.Username == "" || cr.Credential == "" {
		return webrtc.ICEServer{}, 0, false
	}
	ttl = defaultCredsTTL
	if cr.TTL > 0 {
		ttl = time.Duration(cr.TTL) * time.Second
	}
	return webrtc.ICEServer{
		URLs:       cr.URLs,
		Username:   cr.Username,
		Credential: cr.Credential,
	}, ttl, true
}
