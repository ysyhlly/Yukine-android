package app

import (
	"strings"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/transfer"
)

func TestRenderProgress(t *testing.T) {
	// Half-done body segment.
	got := renderProgress(transfer.Progress{
		Name: "movie.mkv", Received: 50 << 20, Total: 100 << 20,
		BytesPerSec: 5 << 20, ETA: 10 * time.Second,
	})
	if !strings.Contains(got, "movie.mkv") {
		t.Errorf("missing file name: %q", got)
	}
	if !strings.Contains(got, " 50%") {
		t.Errorf("missing/incorrect percent: %q", got)
	}
	if !strings.Contains(got, "ETA 10s") {
		t.Errorf("missing ETA: %q", got)
	}
	half := strings.Repeat("█", progressBarWidth/2)
	if !strings.Contains(got, half) {
		t.Errorf("bar should be half filled: %q", got)
	}

	// Tail/index segment is labeled.
	tail := renderProgress(transfer.Progress{Name: "movie.mkv", IsTail: true, Received: 1, Total: 100, ETA: 0})
	if !strings.Contains(tail, "(index)") {
		t.Errorf("tail update should be labeled (index): %q", tail)
	}
	if !strings.Contains(tail, "ETA --") {
		t.Errorf("zero ETA should render as --: %q", tail)
	}

	// A finished update clamps to 100% even if Received nudges past Total.
	full := renderProgress(transfer.Progress{Name: "x", Received: 101, Total: 100})
	if !strings.Contains(full, "100%") {
		t.Errorf("should clamp to 100%%: %q", full)
	}
	if strings.Contains(full, "░") {
		t.Errorf("full bar should have no empty cells: %q", full)
	}
}

func TestFmtETA(t *testing.T) {
	cases := map[time.Duration]string{
		5 * time.Second:               "5s",
		90 * time.Second:              "1m30s",
		(2*time.Hour + 3*time.Minute): "2h03m",
		500 * time.Millisecond:        "1s", // rounds up
	}
	for d, want := range cases {
		if got := fmtETA(d); got != want {
			t.Errorf("fmtETA(%v) = %q, want %q", d, got, want)
		}
	}
}
