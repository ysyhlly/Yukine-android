package mobile

import (
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/syncer"
)

func TestFailureAndFinishEmitExactlyOneTerminalEvent(t *testing.T) {
	cb := &callbackRecorder{
		commands: make(chan string, 1),
		events:   make(chan string, 2),
	}
	session := newSession(cb, "room")
	defer session.player.Close()

	session.fail(errors.New("relay failed"), true)
	session.finish()

	select {
	case raw := <-cb.events:
		var event map[string]any
		if err := json.Unmarshal([]byte(raw), &event); err != nil {
			t.Fatal(err)
		}
		if event["type"] != "terminal" || event["reason"] != "error" {
			t.Fatalf("unexpected terminal event: %s", raw)
		}
		if event["message"] != "relay failed" || event["recoverable"] != true {
			t.Fatalf("unexpected terminal payload: %s", raw)
		}
	case <-time.After(time.Second):
		t.Fatal("missing terminal event")
	}

	select {
	case duplicate := <-cb.events:
		t.Fatalf("duplicate terminal event: %s", duplicate)
	case <-time.After(20 * time.Millisecond):
	}
}

func TestClosedSessionFinishDoesNotEmitTerminalEvent(t *testing.T) {
	cb := &callbackRecorder{
		commands: make(chan string, 1),
		events:   make(chan string, 1),
	}
	session := newSession(cb, "room")
	defer session.player.Close()
	session.mu.Lock()
	session.closed = true
	session.mu.Unlock()

	session.finish()

	select {
	case event := <-cb.events:
		t.Fatalf("closed session emitted terminal event: %s", event)
	case <-time.After(20 * time.Millisecond):
	}
}

func TestTerminalReasonMapsExpectedEngineFailures(t *testing.T) {
	tests := []struct {
		err         error
		reason      string
		recoverable bool
	}{
		{err: syncer.ErrKicked, reason: "kicked", recoverable: false},
		{err: syncer.ErrRelayDisconnected, reason: "relay_lost", recoverable: true},
		{err: syncer.ErrPlayerClosed, reason: "player_closed", recoverable: true},
	}
	for _, tt := range tests {
		reason, recoverable := terminalReason(tt.err)
		if reason != tt.reason || recoverable != tt.recoverable {
			t.Fatalf(
				"terminalReason(%v) = (%q, %v); want (%q, %v)",
				tt.err,
				reason,
				recoverable,
				tt.reason,
				tt.recoverable,
			)
		}
	}
}
