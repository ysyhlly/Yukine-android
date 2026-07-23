package app

import (
	"fmt"
	"io"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/swayam-mishra/junto/internal/human"
	"github.com/swayam-mishra/junto/internal/transfer"
)

// console serializes terminal output so a live, in-place status line
// (the transfer progress bar) coexists with scrolling messages (chat,
// notices). On a TTY the status line is held on the bottom row and
// redrawn with a carriage return; a normal Line erases it first so it
// never scrolls away. When stdout isn't a terminal the status line
// degrades to ordinary lines (already throttled by the caller) and the
// ANSI control sequences are skipped.
type console struct {
	mu     sync.Mutex
	w      io.Writer
	tty    bool
	active bool // a status line is currently displayed on the bottom row
}

func newConsole(w *os.File) *console {
	return &console{w: w, tty: isTTY(w)}
}

// Line prints a scrolling message, lifting any active status line out of
// the way first so output never interleaves with the bar.
func (c *console) Line(format string, a ...any) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.tty && c.active {
		fmt.Fprint(c.w, "\r\x1b[K") // return to col 0, clear to end of line
		c.active = false
	}
	fmt.Fprintf(c.w, format+"\n", a...)
}

// SetStatus draws s as the sticky bottom line, replacing any previous
// status in place.
func (c *console) SetStatus(s string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if !c.tty {
		fmt.Fprintln(c.w, s)
		return
	}
	fmt.Fprint(c.w, "\r\x1b[K"+s)
	c.active = true
}

// ClearStatus erases the sticky line, if any (e.g. when a download
// finishes).
func (c *console) ClearStatus() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.tty && c.active {
		fmt.Fprint(c.w, "\r\x1b[K")
	}
	c.active = false
}

func isTTY(f *os.File) bool {
	fi, err := f.Stat()
	return err == nil && fi.Mode()&os.ModeCharDevice != 0
}

// term is the process-wide terminal; printLine and the progress bar both
// route through it so they never clobber each other.
var term = newConsole(os.Stdout)

func printLine(format string, a ...any) { term.Line(format, a...) }

// showProgress renders one transfer.Progress update as the live bar.
func showProgress(p transfer.Progress) { term.SetStatus(renderProgress(p)) }

const progressBarWidth = 24

func renderProgress(p transfer.Progress) string {
	var frac float64
	if p.Total > 0 {
		frac = float64(p.Received) / float64(p.Total)
	}
	if frac > 1 {
		frac = 1
	}
	filled := int(frac * progressBarWidth)
	bar := strings.Repeat("█", filled) + strings.Repeat("░", progressBarWidth-filled)
	label := p.Name
	if p.IsTail {
		label += " (index)"
	}
	eta := "--"
	if p.ETA > 0 {
		eta = fmtETA(p.ETA)
	}
	return fmt.Sprintf("⬇ %s  [%s] %3.0f%%  %s/%s  %s/s  ETA %s",
		label, bar, frac*100, human.Bytes(p.Received), human.Bytes(p.Total),
		human.Bytes(int64(p.BytesPerSec)), eta)
}

func fmtETA(d time.Duration) string {
	d = d.Round(time.Second)
	switch {
	case d >= time.Hour:
		return fmt.Sprintf("%dh%02dm", d/time.Hour, (d%time.Hour)/time.Minute)
	case d >= time.Minute:
		return fmt.Sprintf("%dm%02ds", d/time.Minute, (d%time.Minute)/time.Second)
	default:
		return fmt.Sprintf("%ds", d/time.Second)
	}
}
