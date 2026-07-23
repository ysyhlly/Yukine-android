package transfer

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"
)

// resetCredsCache clears the package-level TURN-credential cache so tests
// don't see state left over from an earlier one.
func resetCredsCache() {
	credsMu.Lock()
	credsExpiresAt = time.Time{}
	credsMu.Unlock()
	SetFallbackRelays(nil)
}

func withBuiltinCredsURL(t *testing.T, url string) {
	t.Helper()
	saved := builtinCredsURL
	builtinCredsURL = url
	t.Cleanup(func() { builtinCredsURL = saved })
}

func hasRelayConfigured() bool {
	return DefaultICEConfig().WithFallbackRelay().HasRelay()
}

func TestEnsureFallbackRelayFetchesAndCaches(t *testing.T) {
	resetCredsCache()
	var hits int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		fmt.Fprint(w, `{"urls":["turn:relay.example:3478"],"username":"u","credential":"p","ttl":300}`)
	}))
	defer srv.Close()
	withBuiltinCredsURL(t, srv.URL)

	if !ensureFallbackRelay(context.Background(), time.Second) {
		t.Fatal("expected the fetch to succeed")
	}
	if !hasRelayConfigured() {
		t.Error("FallbackRelays should be populated after a successful fetch")
	}

	// A second call within the TTL must be served from cache, not refetch.
	if !ensureFallbackRelay(context.Background(), time.Second) {
		t.Fatal("expected the cached credentials to still be reported usable")
	}
	if got := atomic.LoadInt32(&hits); got != 1 {
		t.Errorf("expected exactly 1 fetch while cache is fresh, got %d", got)
	}
}

// TestEnsureFallbackRelayRefetchesAfterTTLExpires is the regression test
// for the ignored-TTL bug: credentials were cached forever once fetched,
// so a reconnect after the server-declared TTL elapsed would present
// expired HMAC credentials and fail with a confusing terminal error. A
// refetch must happen once the cached credentials' TTL has passed.
func TestEnsureFallbackRelayRefetchesAfterTTLExpires(t *testing.T) {
	resetCredsCache()
	var hits int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		fmt.Fprint(w, `{"urls":["turn:relay.example:3478"],"username":"u","credential":"p","ttl":1}`)
	}))
	defer srv.Close()
	withBuiltinCredsURL(t, srv.URL)

	if !ensureFallbackRelay(context.Background(), time.Second) {
		t.Fatal("expected the first fetch to succeed")
	}
	// Force the cached credentials to look expired without a real sleep.
	credsMu.Lock()
	credsExpiresAt = time.Now().Add(-time.Second)
	credsMu.Unlock()

	if !ensureFallbackRelay(context.Background(), time.Second) {
		t.Fatal("expected the refetch to succeed")
	}
	if got := atomic.LoadInt32(&hits); got != 2 {
		t.Errorf("expected a refetch once the TTL elapsed, got %d total fetches", got)
	}
}

// TestEnsureFallbackRelayRetriesAfterFailure is the regression test for
// the one-shot-forever bug: a sync.Once-gated fetch that failed (a
// transient slow network at session start, say) used to permanently
// disable the fallback relay for the rest of the process. A failed
// attempt must not poison later ones — the very next call, once the
// server is reachable, should succeed.
func TestEnsureFallbackRelayRetriesAfterFailure(t *testing.T) {
	resetCredsCache()
	var fail atomic.Bool
	fail.Store(true)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if fail.Load() {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		fmt.Fprint(w, `{"urls":["turn:relay.example:3478"],"username":"u","credential":"p","ttl":300}`)
	}))
	defer srv.Close()
	withBuiltinCredsURL(t, srv.URL)

	if ensureFallbackRelay(context.Background(), time.Second) {
		t.Fatal("expected the first (failing) fetch to report false")
	}
	if hasRelayConfigured() {
		t.Error("a failed fetch must not populate FallbackRelays")
	}

	fail.Store(false)
	if !ensureFallbackRelay(context.Background(), time.Second) {
		t.Error("a later fetch must be able to succeed — a prior failure must not permanently disable it")
	}
}

// TestInitBuiltinRelayFetchesAndPopulatesRelay is the coverage test for
// InitBuiltinRelay itself (previously untested — only the shared
// ensureFallbackRelay it delegates to had direct tests): the session-
// start warm-up call must actually reach the credentials endpoint and
// leave FallbackRelays populated on success, exactly like a direct
// ensureFallbackRelay call would.
func TestInitBuiltinRelayFetchesAndPopulatesRelay(t *testing.T) {
	resetCredsCache()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, `{"urls":["turn:relay.example:3478"],"username":"u","credential":"p","ttl":300}`)
	}))
	defer srv.Close()
	withBuiltinCredsURL(t, srv.URL)

	InitBuiltinRelay(context.Background())

	if !hasRelayConfigured() {
		t.Error("InitBuiltinRelay should populate FallbackRelays on a successful fetch")
	}
}

// TestInitBuiltinRelayUsesShortTimeout checks InitBuiltinRelay bounds
// its fetch to builtinCredsTimeout (the short session-start warm-up
// budget), not the longer onDemandCredsTimeout used at NAT-failure
// time — a server that responds after the short timeout but within the
// long one must still result in a miss (FallbackRelays left empty),
// proving InitBuiltinRelay didn't wait for the longer budget.
func TestInitBuiltinRelayUsesShortTimeout(t *testing.T) {
	resetCredsCache()
	savedShort, savedLong := builtinCredsTimeout, onDemandCredsTimeout
	builtinCredsTimeout = 100 * time.Millisecond
	onDemandCredsTimeout = 2 * time.Second
	defer func() { builtinCredsTimeout, onDemandCredsTimeout = savedShort, savedLong }()

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(300 * time.Millisecond) // beyond the short timeout, within the long one
		fmt.Fprint(w, `{"urls":["turn:relay.example:3478"],"username":"u","credential":"p","ttl":300}`)
	}))
	defer srv.Close()
	withBuiltinCredsURL(t, srv.URL)

	start := time.Now()
	InitBuiltinRelay(context.Background())
	elapsed := time.Since(start)

	if hasRelayConfigured() {
		t.Error("a fetch slower than builtinCredsTimeout should not populate FallbackRelays via InitBuiltinRelay")
	}
	if elapsed >= onDemandCredsTimeout {
		t.Errorf("InitBuiltinRelay took %v — it should give up around builtinCredsTimeout, not wait the longer onDemandCredsTimeout", elapsed)
	}
}

// --- Disconnected grace period ---

// fakeConnNotifier is a connectionStateNotifier a test can drive
// directly, since pion's own state-change callback isn't invokable from
// outside a real ICE connection.
type fakeConnNotifier struct {
	cb func(webrtc.PeerConnectionState)
}

func (f *fakeConnNotifier) OnConnectionStateChange(cb func(webrtc.PeerConnectionState)) {
	f.cb = cb
}

// TestWatchConnectionToleratesTransientDisconnect is the regression test
// for treating a transient ICE flap as a hard failure: Disconnected is
// commonly a brief, self-healing connectivity blip, not a real failure
// like Failed/Closed. Recovering to Connected within the grace period
// must not close the failed channel.
func TestWatchConnectionToleratesTransientDisconnect(t *testing.T) {
	saved := disconnectGrace
	disconnectGrace = 150 * time.Millisecond
	defer func() { disconnectGrace = saved }()

	n := &fakeConnNotifier{}
	ch := watchConnection(n)

	n.cb(webrtc.PeerConnectionStateDisconnected)
	time.Sleep(30 * time.Millisecond) // well inside the grace period
	n.cb(webrtc.PeerConnectionStateConnected)

	select {
	case <-ch:
		t.Fatal("a Disconnected transition that recovered within the grace period must not close failed")
	case <-time.After(300 * time.Millisecond):
	}
}

// TestWatchConnectionFailsAfterSustainedDisconnect checks the flip side:
// a Disconnected transition that never recovers must still eventually
// close failed, just after the grace period rather than immediately.
func TestWatchConnectionFailsAfterSustainedDisconnect(t *testing.T) {
	saved := disconnectGrace
	disconnectGrace = 50 * time.Millisecond
	defer func() { disconnectGrace = saved }()

	n := &fakeConnNotifier{}
	ch := watchConnection(n)

	n.cb(webrtc.PeerConnectionStateDisconnected)

	select {
	case <-ch:
		t.Fatal("failed must not close before the grace period elapses")
	case <-time.After(10 * time.Millisecond):
	}
	select {
	case <-ch:
	case <-time.After(2 * time.Second):
		t.Fatal("failed should close once the grace period elapses with no recovery")
	}
}

// TestWatchConnectionFailedIsImmediate guards the other side: Failed
// (and Closed) are unambiguous and must not wait out any grace period.
func TestWatchConnectionFailedIsImmediate(t *testing.T) {
	saved := disconnectGrace
	disconnectGrace = 2 * time.Second // deliberately long, to prove Failed skips it
	defer func() { disconnectGrace = saved }()

	n := &fakeConnNotifier{}
	ch := watchConnection(n)

	n.cb(webrtc.PeerConnectionStateFailed)

	select {
	case <-ch:
	case <-time.After(300 * time.Millisecond):
		t.Fatal("Failed must close the channel immediately, not after the Disconnected grace period")
	}
}
