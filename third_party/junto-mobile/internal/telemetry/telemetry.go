// Package telemetry fires a single anonymous HTTPS ping at the end of a
// create or join session. It is opt-in by default (fires unless the user
// opts out). Fire blocks the caller for at most 3 s (its hard timeout) —
// callers invoke it as the very last step before the process exits, so it
// must actually wait for the send to leave rather than racing a detached
// goroutine against process exit, which in practice never wins. If the
// endpoint is unreachable the failure is silent.
package telemetry

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

// Endpoint is the single value to swap when the server is ready. It's a
// package-level var, not a const, purely so tests can override it without
// build tags (same pattern as transfer.FallbackRelays).
var Endpoint = "https://telemetry.junto.watch/v1/event"

// RelaySnapshot carries the fields telemetry needs from nostrx.RelayHealth
// without importing that package (avoiding a potential cycle).
type RelaySnapshot struct {
	LatMs   float64
	Healthy bool
}

// event is the JSON payload posted to Endpoint.
type event struct {
	Event          string    `json:"event"`
	Version        string    `json:"version"`
	OS             string    `json:"os"`
	Arch           string    `json:"arch"`
	Outcome        string    `json:"outcome"`
	FailureReason  string    `json:"failure_reason,omitempty"`
	RelayLatencyMs []float64 `json:"relay_latency_ms,omitempty"`
	RelayHealthy   []bool    `json:"relay_healthy,omitempty"`
	TTFFMs         *int64    `json:"ttff_ms,omitempty"`
	TransferFailed *bool     `json:"transfer_failed,omitempty"`
}

// Collector accumulates session metrics during the session and fires them
// as a single ping at the end. All methods are safe to call on a nil
// *Collector (no-ops), so callers can skip nil checks.
type Collector struct {
	eventName string
	mu        sync.Mutex
	ttff      *int64
	xferFail  *bool
	relays    []RelaySnapshot
}

// New returns a Collector for the named event ("create" or "join").
func New(eventName string) *Collector {
	return &Collector{eventName: eventName}
}

// RecordTTFF records time-to-first-frame (streaming joiners only).
func (c *Collector) RecordTTFF(d time.Duration) {
	if c == nil {
		return
	}
	ms := d.Milliseconds()
	c.mu.Lock()
	c.ttff = &ms
	c.mu.Unlock()
}

// RecordTransferOutcome records whether the P2P transfer failed.
func (c *Collector) RecordTransferOutcome(failed bool) {
	if c == nil {
		return
	}
	c.mu.Lock()
	c.xferFail = &failed
	c.mu.Unlock()
}

// SetRelayStats records relay latency/health snapshots for the payload.
func (c *Collector) SetRelayStats(snaps []RelaySnapshot) {
	if c == nil {
		return
	}
	c.mu.Lock()
	c.relays = snaps
	c.mu.Unlock()
}

// Fire assembles the payload and posts it, blocking for at most 3 s.
// version is the junto binary version; outcome is "success" or "failure";
// reason is a high-level category (empty on success). Fire is a no-op on
// a nil Collector.
func (c *Collector) Fire(version, outcome, reason string) {
	if c == nil {
		return
	}
	c.mu.Lock()
	ev := event{
		Event:          c.eventName,
		Version:        version,
		OS:             runtime.GOOS,
		Arch:           runtime.GOARCH,
		Outcome:        outcome,
		FailureReason:  reason,
		TTFFMs:         c.ttff,
		TransferFailed: c.xferFail,
	}
	for _, r := range c.relays {
		ev.RelayLatencyMs = append(ev.RelayLatencyMs, r.LatMs)
		ev.RelayHealthy = append(ev.RelayHealthy, r.Healthy)
	}
	c.mu.Unlock()

	body, err := json.Marshal(ev)
	if err != nil {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, Endpoint, bytes.NewReader(body))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", "junto/"+version)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return
	}
	resp.Body.Close()
}

// PrintNoticeIfFirstRun prints a one-line disclosure the first time
// telemetry would fire, then creates a sentinel file so it only shows
// once. cfgDir is config.Path() dir (e.g. ~/.config/junto). Does nothing
// if the sentinel already exists or if cfgDir is empty.
func PrintNoticeIfFirstRun(cfgDir string, printf func(string, ...any)) {
	if cfgDir == "" {
		return
	}
	sentinel := filepath.Join(cfgDir, "telemetry-notice")
	if _, err := os.Stat(sentinel); err == nil {
		return // already shown
	}
	printf("junto collects anonymous usage data (version, OS, outcome, relay latency). Disable: --no-telemetry or add  telemetry = false  to %s", filepath.Join(cfgDir, "config.toml"))
	// Best-effort sentinel creation; failure is silent.
	_ = os.MkdirAll(cfgDir, 0o755)
	_ = os.WriteFile(sentinel, nil, 0o644)
}

// ClassifyError maps a session error to a short, high-level category
// with no user-identifiable content.
func ClassifyError(err error) string {
	if err == nil {
		return ""
	}
	msg := strings.ToLower(err.Error())
	switch {
	case strings.Contains(msg, "relay") || strings.Contains(msg, "websocket"):
		return "relay_unreachable"
	case strings.Contains(msg, "nat") || strings.Contains(msg, "traversal") || strings.Contains(msg, "connect"):
		return "nat_failure"
	case strings.Contains(msg, "no space") || strings.Contains(msg, "disk"):
		return "disk_full"
	case strings.Contains(msg, "room code") || strings.Contains(msg, "parse"):
		return "bad_room_code"
	case strings.Contains(msg, "mpv"):
		return "mpv_error"
	case strings.Contains(msg, "context canceled") || strings.Contains(msg, "interrupt"):
		return "user_canceled"
	default:
		return "other"
	}
}
