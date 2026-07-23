package tui

import (
	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"

	"github.com/swayam-mishra/junto/internal/syncer"
)

// maxScrollback caps how many chat/notice lines the model retains, so a
// long session can't grow memory without bound.
const maxScrollback = 500

// Model is the bubbletea model for junto's session UI: banner, session
// info panel, live peer/room panel, chat/notice scrollback, and an input
// line. It is driven entirely by messages (see bridge.go) sent from the
// session goroutine plus key events; it never reads shared engine state
// directly.
type Model struct {
	width, height int

	info     SessionInfo
	snap     syncer.Snapshot
	haveSnap bool

	log     []string // scrollback (chat + notices), capped at maxScrollback
	status  string   // rendered download bar, or "" when no transfer active
	relayed bool     // best-effort: a transfer fell back to a TURN relay

	ti textinput.Model
	sp spinner.Model

	// lines is the input channel shared with the session engine
	// (syncer.Deps.Lines). The Enter handler pushes submitted strings onto
	// it via a tea.Cmd so Update never blocks on a busy engine loop.
	lines chan string

	quitting bool
	err      error // session error, shown in the post-exit summary
}

// newModel builds the initial model. lines is the shared input channel
// (may be nil in tests that don't exercise input).
func newModel(lines chan string) Model {
	ti := textinput.New()
	ti.Placeholder = "type to chat, or /pause /play /seek /peers /quit"
	ti.Prompt = promptStyle.Render("> ")
	ti.Focus()
	ti.CharLimit = 2000

	sp := spinner.New()
	sp.Spinner = spinner.Dot
	sp.Style = dimStyle

	return Model{
		ti:    ti,
		sp:    sp,
		lines: lines,
	}
}

// New constructs a Model with no input channel, for rendering-only tests.
func New() Model { return newModel(nil) }

func (m Model) Init() tea.Cmd {
	return tea.Batch(m.sp.Tick, textinput.Blink)
}
