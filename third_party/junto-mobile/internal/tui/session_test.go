package tui

import (
	"bytes"
	"context"
	"errors"
	"io"
	"testing"
	"time"

	tea "github.com/charmbracelet/bubbletea"
)

// withPipes swaps the alt-screen program options for pipe-backed
// input/output so RunSession can run headless in a test, and returns the
// input writer plus a restore func.
func withPipes(t *testing.T) (io.Writer, *bytes.Buffer) {
	t.Helper()
	pr, pw := io.Pipe()
	var out bytes.Buffer
	prev := programOpts
	programOpts = []tea.ProgramOption{tea.WithInput(pr), tea.WithOutput(&out)}
	t.Cleanup(func() { programOpts = prev; pw.Close() })
	return pw, &out
}

// TestRunSessionReturnsWorkError: work runs with real (non-nil) hooks and
// its error propagates back through RunSession.
func TestRunSessionReturnsWorkError(t *testing.T) {
	withPipes(t)
	want := errors.New("boom")
	err := RunSession(context.Background(), func(ctx context.Context, h Hooks) error {
		// Hooks must be wired, not nil.
		if h.Printf == nil || h.Lines == nil || h.OnSnapshot == nil {
			t.Error("work received nil hooks")
		}
		h.Printf("a notice")
		return want
	})
	if err != want {
		t.Fatalf("RunSession returned %v, want %v", err, want)
	}
}

// TestRunSessionCancelsOnParentContext: cancelling the parent context
// tears the session down (the SIGTERM path), and work sees ctx.Done().
func TestRunSessionCancelsOnParentContext(t *testing.T) {
	withPipes(t)
	ctx, cancel := context.WithCancel(context.Background())
	sawCancel := make(chan struct{})
	done := make(chan error, 1)
	go func() {
		done <- RunSession(ctx, func(ctx context.Context, h Hooks) error {
			<-ctx.Done() // the session body blocks until torn down
			close(sawCancel)
			return ctx.Err()
		})
	}()
	cancel()
	select {
	case <-sawCancel:
	case <-time.After(5 * time.Second):
		t.Fatal("work never observed context cancellation")
	}
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("RunSession did not return after cancellation")
	}
}
