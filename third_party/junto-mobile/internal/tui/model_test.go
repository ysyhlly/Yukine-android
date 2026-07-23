package tui

import (
	"strings"
	"testing"
	"time"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/swayam-mishra/junto/internal/syncer"
	"github.com/swayam-mishra/junto/internal/transfer"
)

// step drives one message through Update and returns the new Model.
func step(m Model, msg tea.Msg) Model {
	next, _ := m.Update(msg)
	return next.(Model)
}

func sized(m Model) Model {
	return step(m, tea.WindowSizeMsg{Width: 100, Height: 30})
}

func TestLineAppendAndScrollbackCap(t *testing.T) {
	m := sized(New())
	m = step(m, lineMsg("hello"))
	m = step(m, lineMsg("* someone joined"))
	if len(m.log) != 2 || m.log[0] != "hello" {
		t.Fatalf("log = %v", m.log)
	}
	for i := 0; i < maxScrollback+50; i++ {
		m = step(m, lineMsg("line"))
	}
	if len(m.log) != maxScrollback {
		t.Fatalf("scrollback not capped: %d", len(m.log))
	}
}

func TestCtrlCQuits(t *testing.T) {
	m := sized(New())
	next, cmd := m.Update(tea.KeyMsg{Type: tea.KeyCtrlC})
	nm := next.(Model)
	if !nm.quitting {
		t.Fatal("Ctrl-C did not set quitting")
	}
	if cmd == nil {
		t.Fatal("Ctrl-C returned no command (expected tea.Quit)")
	}
	if got := cmd(); got == nil {
		t.Fatal("Ctrl-C command did not produce a quit message")
	}
	if nm.View() != "" {
		t.Fatal("a quitting model should render empty")
	}
}

func TestBareEnterSubmitsEmptyString(t *testing.T) {
	lines := make(chan string, 1)
	m := sized(newModel(lines))
	// No typing: a bare Enter must submit "" (the press-Enter-to-start gate).
	_, cmd := m.Update(tea.KeyMsg{Type: tea.KeyEnter})
	if cmd == nil {
		t.Fatal("bare Enter produced no submit command")
	}
	cmd() // performs the channel send
	select {
	case v := <-lines:
		if v != "" {
			t.Fatalf("submitted %q, want empty string", v)
		}
	case <-time.After(time.Second):
		t.Fatal("bare Enter did not reach the input channel")
	}
}

func TestTypedLineSubmits(t *testing.T) {
	lines := make(chan string, 1)
	m := sized(newModel(lines))
	for _, r := range "/pause" {
		m = step(m, tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune{r}})
	}
	_, cmd := m.Update(tea.KeyMsg{Type: tea.KeyEnter})
	if cmd == nil {
		t.Fatal("Enter produced no submit command")
	}
	cmd()
	if v := <-lines; v != "/pause" {
		t.Fatalf("submitted %q, want /pause", v)
	}
}

func TestSessionInfoMerge(t *testing.T) {
	m := sized(New())
	m = step(m, sessionInfoMsg(SessionInfo{RoomCode: "jun1abc", Host: true}))
	m = step(m, sessionInfoMsg(SessionInfo{JoinLink: "https://junto.watch/join#jun1abc"}))
	m = step(m, sessionInfoMsg(SessionInfo{Nick: "swayam"}))
	if m.info.RoomCode != "jun1abc" || !m.info.Host {
		t.Fatalf("room/host lost after merge: %+v", m.info)
	}
	if m.info.JoinLink == "" || m.info.Nick != "swayam" {
		t.Fatalf("later fields not merged: %+v", m.info)
	}
	// A later message with an unset field must not clobber an existing one.
	m = step(m, sessionInfoMsg(SessionInfo{Nick: "swayam2"}))
	if m.info.RoomCode != "jun1abc" {
		t.Fatal("unset field in a later message clobbered the room code")
	}
}

func TestSessionDoneQuits(t *testing.T) {
	m := sized(New())
	next, cmd := m.Update(sessionDoneMsg{err: nil})
	if !next.(Model).quitting || cmd == nil {
		t.Fatal("sessionDoneMsg did not quit")
	}
}

func TestViewRendersRoomAndPeers(t *testing.T) {
	m := sized(New())
	m = step(m, sessionInfoMsg(SessionInfo{RoomCode: "jun1xyz", Host: true, Nick: "host", Playlist: []string{"movie.mkv"}}))
	m = step(m, snapshotMsg(syncer.Snapshot{
		SelfNick: "host", Host: true, GateOpen: false, ReadyCount: 1, TotalCount: 2,
		Peers: []syncer.PeerStatus{
			{Nick: "alice", HasState: true, Pos: 62, DL: 40, Buffering: false, Ready: true},
			{Nick: "bob", HasState: false},
		},
	}))
	v := m.View()
	// Status is now shown as a colored glyph, not a label: alice (ready)
	// gets ●, bob (no heartbeat yet) gets the dim · default.
	for _, want := range []string{"jun1xyz", "movie.mkv", "alice", "bob", "●", "·", "press Enter to start"} {
		if !strings.Contains(v, want) {
			t.Fatalf("view missing %q\n---\n%s", want, v)
		}
	}
}

func TestViewProgressBar(t *testing.T) {
	m := sized(New())
	m = step(m, progressMsg(transfer.Progress{Name: "movie.mkv", Received: 50, Total: 100, BytesPerSec: 1 << 20, ETA: 10 * time.Second}))
	v := m.View()
	if !strings.Contains(v, "50%") || !strings.Contains(v, "ETA 10s") {
		t.Fatalf("progress bar not rendered:\n%s", v)
	}
	m = step(m, clearProgressMsg{})
	if m.status != "" {
		t.Fatal("clearProgressMsg did not clear the status")
	}
}

func TestTooSmallTerminal(t *testing.T) {
	m := step(New(), tea.WindowSizeMsg{Width: 20, Height: 6})
	if !strings.Contains(m.View(), "too small") {
		t.Fatalf("expected too-small hint, got:\n%s", m.View())
	}
}

func TestRelayModeApproximation(t *testing.T) {
	m := sized(New())
	m = step(m, sessionInfoMsg(SessionInfo{RoomCode: "jun1abc"}))
	if strings.Contains(m.View(), "relayed") {
		t.Fatal("mode should start as direct")
	}
	m = step(m, lineMsg("* direct connection failed — retrying via relay…"))
	if !m.relayed || !strings.Contains(m.View(), "relayed") {
		t.Fatalf("relay-fallback notice did not flip mode to relayed:\n%s", m.View())
	}
}
