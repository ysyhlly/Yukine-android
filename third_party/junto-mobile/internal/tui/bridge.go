package tui

import (
	"fmt"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/swayam-mishra/junto/internal/syncer"
	"github.com/swayam-mishra/junto/internal/transfer"
)

// Messages the session goroutine sends into the TUI via p.Send (which is
// documented safe to call from any goroutine). The Model's Update
// switches over them.

// lineMsg is one scrolling notice/chat line for the scrollback.
type lineMsg string

// progressMsg updates the sticky download bar.
type progressMsg transfer.Progress

// clearProgressMsg removes the download bar (a transfer finished).
type clearProgressMsg struct{}

// snapshotMsg refreshes the live peer/room panel.
type snapshotMsg syncer.Snapshot

// sessionInfoMsg fills the session-info panel as fields become known.
type sessionInfoMsg SessionInfo

// sessionDoneMsg is sent when the session goroutine returns; Update must
// answer it with tea.Quit or the TUI outlives the session.
type sessionDoneMsg struct{ err error }

// SessionInfo is the room/session metadata shown in the info panel. Sent
// piecemeal (each field becomes known at a different point in
// Create/Join), so a later message with a set field updates just that
// field; zero values leave the model's existing value untouched.
type SessionInfo struct {
	RoomCode  string
	JoinLink  string
	Nick      string
	Host      bool
	Streaming bool
	Playlist  []string // file names
}

// NewPrintf returns a Printf-signature function that routes each line
// into the TUI scrollback. Safe to call from any goroutine.
func NewPrintf(p *tea.Program) func(string, ...any) {
	return func(format string, a ...any) {
		p.Send(lineMsg(fmt.Sprintf(format, a...)))
	}
}

// NewProgressFn returns a ShowProgress-signature function that updates
// the TUI's download bar.
func NewProgressFn(p *tea.Program) func(transfer.Progress) {
	return func(pr transfer.Progress) {
		p.Send(progressMsg(pr))
	}
}

// NewClearProgressFn returns a function that removes the TUI's download
// bar (a transfer finished).
func NewClearProgressFn(p *tea.Program) func() {
	return func() { p.Send(clearProgressMsg{}) }
}

// NewSnapshotFn returns an OnSnapshot-signature function that refreshes
// the live panel.
func NewSnapshotFn(p *tea.Program) func(syncer.Snapshot) {
	return func(s syncer.Snapshot) {
		p.Send(snapshotMsg(s))
	}
}

// NewSessionInfoFn returns a function that pushes session metadata into
// the info panel.
func NewSessionInfoFn(p *tea.Program) func(SessionInfo) {
	return func(si SessionInfo) {
		p.Send(sessionInfoMsg(si))
	}
}
