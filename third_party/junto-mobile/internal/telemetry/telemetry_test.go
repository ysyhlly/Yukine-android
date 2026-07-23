package telemetry

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// TestFireBlocksUntilDelivered is the regression test for the
// never-delivered-in-production bug: Fire used to hand the whole POST off
// to a detached goroutine and return immediately, so it raced process
// exit — the caller (main.go) invokes Fire as its very last step before
// the process exits, and a goroutine that hasn't even opened its TCP
// connection yet is killed outright. Fire must block until the request
// actually completes (bounded by its own 3s timeout) so the caller can
// safely exit right after it returns.
func TestFireBlocksUntilDelivered(t *testing.T) {
	received := make(chan struct{}, 1)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(20 * time.Millisecond) // simulate real network latency
		received <- struct{}{}
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	saved := Endpoint
	Endpoint = srv.URL
	defer func() { Endpoint = saved }()

	c := New("create")
	c.Fire("1.2.3", "success", "")

	// If Fire returned before the request was actually delivered, this
	// would be empty — proving the send raced (and lost to) Fire's return.
	select {
	case <-received:
	default:
		t.Fatal("Fire returned before the request was delivered — it must block, not race a background goroutine")
	}
}

// TestFireNilCollectorIsNoop guards the documented nil-safety contract
// callers rely on to skip nil checks.
func TestFireNilCollectorIsNoop(t *testing.T) {
	var c *Collector
	c.Fire("1.2.3", "success", "") // must not panic
}

// TestFirePayloadHasNoRawErrorText locks in the no-PII invariant:
// ClassifyError must be the only thing that reaches the wire for
// failures, never a raw error string that could embed a file path, room
// code, or other user content.
func TestFirePayloadHasNoRawErrorText(t *testing.T) {
	var body []byte
	done := make(chan struct{})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer close(done)
		body, _ = io.ReadAll(r.Body)
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	saved := Endpoint
	Endpoint = srv.URL
	defer func() { Endpoint = saved }()

	c := New("join")
	rawErr := "failed to open /home/alice/Videos/super-secret-movie.mkv: permission denied"
	c.Fire("1.2.3", "failure", ClassifyError(errString(rawErr)))
	<-done

	var ev map[string]any
	if err := json.Unmarshal(body, &ev); err != nil {
		t.Fatalf("payload wasn't valid JSON: %v", err)
	}
	reason, _ := ev["failure_reason"].(string)
	if reason == "" || reason == rawErr {
		t.Fatalf("failure_reason must be a classified category, not raw error text; got %q", reason)
	}
	for k, v := range ev {
		if s, ok := v.(string); ok && (strings.Contains(s, "/home/") || strings.Contains(s, ".mkv")) {
			t.Errorf("payload field %q leaked file-path-shaped content: %q", k, s)
		}
	}
}

func TestClassifyError(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"", ""},
		{"dial relay wss://x: websocket: bad handshake", "relay_unreachable"},
		{"NAT traversal failed", "nat_failure"},
		{"failed to connect to peer", "nat_failure"},
		{"write /tmp/x.part: no space left on device", "disk_full"},
		{"invalid room code", "bad_room_code"},
		{"mpv exited unexpectedly", "mpv_error"},
		{"context canceled", "user_canceled"},
		{"received interrupt", "user_canceled"},
		{"something entirely unrelated", "other"},
	}
	for _, c := range cases {
		var err error
		if c.in != "" {
			err = errString(c.in)
		}
		if got := ClassifyError(err); got != c.want {
			t.Errorf("ClassifyError(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

type errString string

func (e errString) Error() string { return string(e) }
