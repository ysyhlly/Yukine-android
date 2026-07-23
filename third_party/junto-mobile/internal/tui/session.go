package tui

import (
	"context"
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/swayam-mishra/junto/internal/syncer"
	"github.com/swayam-mishra/junto/internal/transfer"
)

// programOpts are the bubbletea options RunSession runs with, beyond the
// always-applied context binding. A var (not a literal) purely so tests
// can swap the alt-screen for pipe-backed input/output and drive the full
// lifecycle without a real terminal.
var programOpts = []tea.ProgramOption{tea.WithAltScreen()}

// Hooks carries everything the parameterized session body (createInner /
// joinInner / ...) needs for its output and input. On the plain path the
// caller fills Printf/ShowProgress with the app's package funcs and
// leaves the rest nil; on the TUI path RunSession fills all of them with
// program-backed bridges. A nil field means "behave as the plain path
// did": nil Lines ⇒ the inner spawns its own stdin scanner; nil
// OnSnapshot/SessionInfo ⇒ no-op.
type Hooks struct {
	Printf        func(string, ...any)
	ShowProgress  func(transfer.Progress)
	ClearProgress func()
	OnSnapshot    func(syncer.Snapshot)
	SessionInfo   func(SessionInfo)
	Lines         <-chan string
}

// RunSession runs work under a bubbletea program: it owns the terminal
// (raw mode, alt-screen) while work drives the actual session on a
// background goroutine, wiring work's output/input through the TUI. It
// blocks until either work returns or the user quits (Ctrl-C), cancels
// the session context on quit so the session tears down through its
// normal ctx.Done() path, and after the alt-screen is torn down prints a
// short plain-text summary (room code, join link, error) so the one
// thing a host still needs isn't lost with the interface.
func RunSession(ctx context.Context, work func(ctx context.Context, h Hooks) error) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	lines := make(chan string, 4) // fed by the model's Enter handler
	opts := append([]tea.ProgramOption{tea.WithContext(ctx)}, programOpts...)
	p := tea.NewProgram(newModel(lines), opts...)

	errCh := make(chan error, 1)
	go func() {
		err := work(ctx, Hooks{
			Printf:        NewPrintf(p),
			ShowProgress:  NewProgressFn(p),
			ClearProgress: NewClearProgressFn(p),
			OnSnapshot:    NewSnapshotFn(p),
			SessionInfo:   NewSessionInfoFn(p),
			Lines:         lines,
		})
		errCh <- err
		// Ask the model to quit; if the user already quit (Ctrl-C) the
		// program has stopped and this Send is a no-op.
		p.Send(sessionDoneMsg{err: err})
	}()

	final, runErr := p.Run()
	cancel() // covers a Ctrl-C-triggered exit before work() returned
	workErr := <-errCh

	printSummary(final, workErr)

	if runErr != nil && runErr != tea.ErrProgramKilled {
		return runErr
	}
	return workErr
}

// printSummary writes the post-exit plain-text recap to the restored
// terminal: the alt-screen wiped the interface, so the room code and join
// link (the things a host may still need) are reprinted, along with any
// session error.
func printSummary(final tea.Model, workErr error) {
	m, ok := final.(Model)
	if !ok {
		if workErr != nil {
			fmt.Fprintln(os.Stderr, workErr)
		}
		return
	}
	if m.info.RoomCode != "" {
		fmt.Printf("room code: %s\n", m.info.RoomCode)
		if m.info.JoinLink != "" {
			fmt.Printf("join link: %s\n", m.info.JoinLink)
		}
	}
	err := workErr
	if err == nil {
		err = m.err
	}
	if err != nil && err != context.Canceled {
		fmt.Fprintln(os.Stderr, err)
	}
}
