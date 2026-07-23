package tui

import (
	"strings"

	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"

	"github.com/swayam-mishra/junto/internal/syncer"
)

func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width, m.height = msg.Width, msg.Height
		return m, nil

	case tea.KeyMsg:
		return m.handleKey(msg)

	case lineMsg:
		m.appendLine(string(msg))
		// Best-effort connection-mode approximation: the downloader prints
		// this notice when it falls back to a TURN relay. It can be
		// inaccurate under swarm mode or once a direct path recovers;
		// real per-peer state is a documented fast-follow.
		if strings.Contains(string(msg), "via relay") {
			m.relayed = true
		}
		return m, nil

	case progressMsg:
		m.status = renderProgressStyled(m.width, msg)
		return m, nil

	case clearProgressMsg:
		m.status = ""
		return m, nil

	case snapshotMsg:
		m.snap = syncer.Snapshot(msg)
		m.haveSnap = true
		return m, nil

	case sessionInfoMsg:
		m.info = mergeInfo(m.info, SessionInfo(msg))
		return m, nil

	case sessionDoneMsg:
		// The session ended on its own (host quit, /quit, error). Record
		// the error for the post-exit summary and tear the TUI down.
		m.err = msg.err
		m.quitting = true
		return m, tea.Quit

	case spinner.TickMsg:
		var cmd tea.Cmd
		m.sp, cmd = m.sp.Update(msg)
		return m, cmd
	}

	// Anything else (textinput blink, etc.) goes to the input component.
	var cmd tea.Cmd
	m.ti, cmd = m.ti.Update(msg)
	return m, cmd
}

func (m Model) handleKey(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	switch msg.Type {
	case tea.KeyCtrlC:
		// In raw mode a typed Ctrl-C arrives here instead of as SIGINT.
		// Quit the program; RunSession then cancels the session context,
		// which drives the same cleanup the plain path's SIGINT does.
		m.quitting = true
		return m, tea.Quit

	case tea.KeyEnter:
		// Submit the current input onto the shared channel — including a
		// bare empty string, which is the load-bearing "press Enter to
		// start" / buffering-override gate the engine's handleLine treats
		// specially. Send via a Cmd so Update never blocks on the engine.
		v := m.ti.Value()
		m.ti.SetValue("")
		return m, m.submit(v)
	}

	var cmd tea.Cmd
	m.ti, cmd = m.ti.Update(msg)
	return m, cmd
}

// submit returns a Cmd that pushes v onto the input channel (or a no-op
// when no channel is wired, e.g. tests).
func (m Model) submit(v string) tea.Cmd {
	if m.lines == nil {
		return nil
	}
	lines := m.lines
	return func() tea.Msg {
		lines <- v
		return nil
	}
}

func (m *Model) appendLine(s string) {
	m.log = append(m.log, s)
	if len(m.log) > maxScrollback {
		m.log = m.log[len(m.log)-maxScrollback:]
	}
}

// mergeInfo overlays only the set fields of b onto a, so each piecemeal
// sessionInfoMsg updates just what it carries.
func mergeInfo(a, b SessionInfo) SessionInfo {
	if b.RoomCode != "" {
		a.RoomCode = b.RoomCode
	}
	if b.JoinLink != "" {
		a.JoinLink = b.JoinLink
	}
	if b.Nick != "" {
		a.Nick = b.Nick
	}
	if b.Host {
		a.Host = true
	}
	if b.Streaming {
		a.Streaming = true
	}
	if len(b.Playlist) > 0 {
		a.Playlist = b.Playlist
	}
	return a
}
