package tui

import (
	"os"
)

// UseTUI reports whether the interactive TUI should run, mirroring the
// app's detectColors gate: opt out when JUNTO_NO_TUI is set (any
// non-empty value), when TERM is dumb, or when stdout isn't a real
// terminal (piped output, CI, automation) — those keep today's exact
// plain line-per-message behavior. NO_COLOR does NOT disable the TUI: it
// yields a monochrome full-layout TUI, since color and layout are
// independent gates.
func UseTUI() bool {
	if os.Getenv("JUNTO_NO_TUI") != "" || os.Getenv("TERM") == "dumb" {
		return false
	}
	return isTTY(os.Stdout)
}

// isTTY reports whether f is a character device (a terminal). Mirrors
// app.isTTY; duplicated here so the tui package stays free of an app
// import.
func isTTY(f *os.File) bool {
	fi, err := f.Stat()
	return err == nil && fi.Mode()&os.ModeCharDevice != 0
}
