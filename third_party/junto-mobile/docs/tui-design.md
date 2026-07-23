# Interactive terminal UI — design

> **Status: built** (`internal/tui`). This was the implementation plan for
> replacing junto's plain scrolling terminal output with a real, redrawn TUI
> (banner, session-info panel, live peer/room panel, chat scrollback, input
> line); it's kept as the design record for the shipped feature. See the
> "Interactive terminal UI" entry under Shipped in [ROADMAP.md](../ROADMAP.md).

## Why

junto's terminal output today is plain scrolling text: every chat line,
notice, and status update goes through a single `printLine`/`console` writer
(`internal/app/console.go`), with exactly one "pinned" row (the download
progress bar) achieved via raw `\r\x1b[K` cursor tricks. There's no banner, no
persistent session/peer panel, and command/chat input is a raw
`bufio.Scanner` on stdin. The goal is a much more legible interface: an ASCII
banner, a boxed session-info panel, a live-updating panel of room/peer state,
a scrolling chat/notices log, and a bottom input line — rendered as a real
TUI, not plain scrolling text.

This is a materially bigger change than a one-shot banner: it needs a real
TUI framework (nothing like this exists in `go.mod` today), and it changes
how `internal/app` and `internal/syncer` get lines onto the screen.

**Decisions already made** (not open questions):
- **Full interactive TUI**, not just a one-shot banner or a persistent status
  bar bolted onto the existing single-row trick.
- **Auto-activation**: TUI when stdout is a real TTY; today's exact plain
  line-per-message behavior otherwise (scripts, CI, piped output, automation
  must be byte-for-byte unaffected) — mirrors the existing `detectColors()`
  TTY-gate pattern (`internal/app/app.go:701-706`).
- **Connection mode (P2P direct vs. TURN relay) in the info panel**: ship a
  cheap best-effort approximation (pattern-match the two existing "retrying
  via relay" log strings into a single `relayed bool`), accepting it can be
  inaccurate under swarm mode or once a connection recovers. Real per-peer
  `OnConnectionStateChange` plumbing is a documented fast-follow, not part of
  this change.

This directly reverses the "Parked" note that used to sit in `ROADMAP.md`
("a fancier terminal UI pulls attention the wrong way ... supporting other
players is reach, not the core feel") — the video surface is still mpv; this
is about making junto's own control/chat surface legible, not about
rendering video in the terminal.

## Library choice

`github.com/charmbracelet/bubbletea` + `github.com/charmbracelet/lipgloss` +
`github.com/charmbracelet/bubbles` (for `textinput`/`viewport`). MIT
licensed, pure Go (no cgo — compatible with `CGO_ENABLED=0`
cross-compilation), the de facto standard Go TUI stack, and zero conflicts
with the current dependency set (no terminal/rendering library exists in
`go.mod` today). Windows is already out of scope (separately parked in
`ROADMAP.md`), so bubbletea's Windows raw-mode path doesn't need validating.

## Architecture: the core problem and its fix

`Deps.Printf func(format string, a ...any)` (`internal/syncer/engine.go:103`)
is called synchronously from ~70 sites inside `engine.go` and ~20 in
`app.go`, from **several different goroutines** (the engine's own `Run`
loop, its `senderLoop`, the downloader's progress-reporting goroutine via
`dl.OnProgress` in `app.go:377`, and — since swarm downloads landed — the
downloader's per-file goroutines printing verification/reconnect notices
through the same injected `printf`, e.g. `download.go:989/1022/1087`). None
of that changes. Only
what's *behind* `printLine`/`showProgress` changes, by constructing different
functions with the exact same signatures and handing those to `Deps` instead:

- **Plain path (non-TTY, unchanged)**: `printLine`/`showProgress` package
  functions exactly as today, calling into `console.go`.
- **TUI path (new)**: `tui.NewPrintf(p)` / `tui.NewProgressFn(p)` — thin
  wrappers that call `tea.Program.Send(...)` (documented safe from any
  goroutine) with a typed message the TUI model appends to its scrollback /
  status area.

### Live peer/room panel

The live peer/room panel needs richer, structured data that scrolling text
can't provide. `engine.go`'s peer state (`e.peers`, `ready`, `buffering`,
`gateOpen`) is private and single-writer (only `Run`'s own goroutine touches
it), and the existing `printPeers` method
(`engine.go:1950`) already walks exactly this state to build formatted
lines — per-peer position, drift vs. local, download percent (`p.state.DL`,
advertised on heartbeats), buffering, and ignored state. Rather than expose
a thread-safe accessor (would mean adding locking
across ~15 read/write sites), add one new optional, nil-safe `Deps` hook —
`OnSnapshot func(Snapshot)` — and refactor `printPeers` to build a
`Snapshot`/`[]PeerStatus` struct first, then format *that* into lines
(single source of truth for both the old text output and the new structured
panel). Call `OnSnapshot` from the same points peer state already changes:
end of `heartbeat()` (`engine.go:1252`), the `MsgHello`/`MsgLeave` branches
in `handleMessage` (`engine.go:865/910`), and `refreshGatePrompt`
(`engine.go:1580`). Small, additive change to `engine.go` — new struct, new
optional func field, 3-4 call sites — no existing method signature changes.

**Swarm note** (post-`61af7a5`): peers now also advertise verified piece
coverage on heartbeats (`Have`/`HaveDone` on `MsgState`), and a streaming
joiner may be fetching from several sources at once. The MVP `PeerStatus`
mirrors exactly what `printPeers` shows today; a later iteration can extend
`Snapshot` with swarm roles (seeding / helper source) once that's worth
surfacing — the hook design doesn't change either way.

### Input

No engine change needed at all. `Deps.Lines <-chan string`
(`engine.go:102`) is fed today by an unbuffered `lines := make(chan string)`
in `runSession` (`internal/app/app.go:645`), populated by a raw
`bufio.Scanner` goroutine on `os.Stdin`. The TUI path replaces only that
goroutine with a `bubbles/textinput` component whose `Enter` handler pushes
the submitted string onto the *same* channel — `runSession`'s
`syncer.Deps{Lines: lines, ...}` wiring is untouched.

**Plumbing detail (easy to miss)**: once bubbletea runs, *it* owns stdin —
the inner session code can't scan stdin itself, and the `lines` channel is
created inside `runSession` today, out of the TUI's reach. So `RunSession`
must create the channel and thread it through: the model's Enter handler
sends into it, and the `work` closure receives it as a parameter
(`lines <-chan string`), which `runSession`'s parameterized inner form uses
*instead of* spawning the stdin-scanner goroutine (nil ⇒ spawn the scanner
exactly as today, preserving the plain path). The channel should be lightly
buffered (typing is human-slow; a small buffer keeps `Update` from ever
blocking on a busy engine loop).

**Load-bearing detail**: `handleLine`'s first branch treats a bare
empty-string submission specially (it's the pre-start "press Enter to
start"/mid-session buffering-override gate — `engine.go:1866` on). The TUI's
textinput handling must submit an empty string on a bare Enter, not swallow
it as a no-op.

### Session info panel

Session info (room code, join link, nick, playlist, host/joiner) becomes
known partway through `Create`/`Join`/`joinStreaming`, before `runSession` is
reached (`Create` alone emits ~8 `printLine` calls first). A
`sessionInfoMsg` is sent at the point each becomes available: right after
`rm.New()` in `Create` (`app.go:144-147`, before today's first
`printLine("room code: ...")` at line 148), at the top of `Join` and
`joinStreaming` (room code already known there), with a follow-up message
once `waitForHost` resolves the file list for streaming joiners.

### Ctrl-C

bubbletea puts the terminal in raw mode, which disables the `ISIG`
translation `signal.NotifyContext(ctx, os.Interrupt, ...)`
(`cmd/junto/main.go:66`) relies on for Ctrl-C → SIGINT. Once the TUI owns the
terminal, typed Ctrl-C stops generating SIGINT and arrives instead as
`tea.KeyMsg{Type: tea.KeyCtrlC}`. The TUI's `Update` must explicitly cancel
the session context on `KeyCtrlC` (`tea.Quit`, then the session's own
`cancel()` in `tui.RunSession` — see below), which flows into the exact same
`ctx.Done()`-driven cleanup (`engine.Run`, `mpvc.Close()`, transport close)
that already exists. `SIGTERM` (external `kill`) is untouched — not
tty-mediated.

### Blocking control-flow inversion

bubbletea needs `p.Run()` to be the top-level blocking call on its own
goroutine, but today `Create`/`Join` already run synchronously through to
`runSession`/`engine.Run`. Fix: extract each entry point's existing body into
an `*Inner` function parameterized on `printf`/`showProgress`/`onSnapshot`
(same functions, just passed as parameters instead of referenced as package
vars), called either directly (plain path — **zero behavioral change**) or
from a goroutine that `tui.RunSession` manages while `p.Run()` blocks on the
calling goroutine:

```go
// internal/tui/session.go

// Hooks carries everything the parameterized session body needs from the
// TUI (or, on the plain path, from the existing package-level functions).
type Hooks struct {
    Printf       func(string, ...any)
    ShowProgress func(transfer.Progress)
    OnSnapshot   func(syncer.Snapshot)
    Lines        <-chan string // nil ⇒ caller spawns its own stdin scanner
}

func RunSession(ctx context.Context, work func(ctx context.Context, h Hooks) error) error {
    ctx, cancel := context.WithCancel(ctx)
    defer cancel()
    lines := make(chan string, 4) // fed by the model's Enter handler
    m := newModel(lines)
    p := tea.NewProgram(m, tea.WithAltScreen())
    errCh := make(chan error, 1)
    go func() {
        errCh <- work(ctx, Hooks{
            Printf:       NewPrintf(p),
            ShowProgress: NewProgressFn(p),
            OnSnapshot:   NewSnapshotFn(p),
            Lines:        lines,
        })
        p.Send(sessionDoneMsg{}) // Update must answer this with tea.Quit,
        // or the TUI outlives the session it exists to display
    }()
    _, runErr := p.Run()
    cancel() // covers Ctrl-C-triggered exit before work() finished
    workErr := <-errCh
    if runErr != nil {
        return runErr
    }
    return workErr
}
```

**Post-exit summary**: `tea.WithAltScreen()` means the whole interface —
including the room code and join link — vanishes when the program exits.
After `p.Run()` returns, print a short plain-text summary to the restored
terminal (room code, join link, and the session error if any), so the one
piece of information a host may still need isn't destroyed by quitting.
The final model returned by `p.Run()` already holds the session info to
print.

**mpv can't corrupt the TUI** (verified, no work needed): `mpv.Launch` sets
`cmd.Stdout = nil` / `cmd.Stderr = nil` (`internal/mpv/client.go:210-212`),
so the external mpv process never writes to the terminal bubbletea owns.

Gate placement — **at the top of `app.Create` and `app.Join`**, not in
`main.go`: `checkMpvEarly`, yt-dlp resolution, and the telemetry first-run
notice all run in `main.go` before `Create`/`Join` via plain `fmt.Printf`
(untouched either way). The binary branch:

```go
if tui.UseTUI() {
    return tui.RunSession(ctx, func(ctx context.Context, h tui.Hooks) error {
        return createInner(ctx, mediaPaths, cfg, h)
    })
}
return createInner(ctx, mediaPaths, cfg, tui.Hooks{Printf: printLine, ShowProgress: showProgress})
```

One copy of the session logic, not a forked duplicate.

## New package: `internal/tui/`

```
internal/tui/
  model.go     // Model struct, Init(), New()
  update.go    // Update() — switch over message types + tea.KeyMsg
  view.go      // View() — banner + info box + live panel + scrollback + input
  banner.go    // ASCII "junto" banner as a Go string const
  bridge.go    // NewPrintf/NewProgressFn/NewSnapshotFn, message type defs
  session.go   // RunSession (the blocking entry point described above)
  fallback.go  // UseTUI() — the TTY-detection gate
  model_test.go
  view_test.go
```

Reuse, don't duplicate:
- `internal/app/console.go`'s `renderProgress`/`fmtETA`/`progressBarWidth`
  (pure functions, no `console` dependency) — move into `internal/tui` and
  call from `NewProgressFn`; `console.go` keeps its own copy only if still
  needed there, otherwise this is a genuine move, not a fork.
- `internal/syncer/colors.go`'s `nickPalette`/`colorFor` — the existing
  ANSI-256 values plug directly into `lipgloss.Color(strconv.Itoa(n))`, no
  new palette needed.
- `app.isTTY` / `detectColors()`'s pattern for `tui.UseTUI()`'s gate (plus a
  `JUNTO_NO_TUI` env var escape hatch).

## Visual design

The bar: someone screenshots their terminal mid-watch-party and it looks
*designed*, not assembled. Concrete choices, all achievable with lipgloss
alone (no images, no custom fonts, degrades cleanly):

- **Banner**: hand-drawn block-letter "JUNTO" (like the Hermes reference —
  chunky outlined block glyphs, ~6 rows tall), with a **horizontal color
  gradient** applied per-column via lipgloss (interpolate between two brand
  hues across the banner's width; ANSI-256 fallback picks the nearest palette
  entries). One string constant + a small gradient function, not a figlet
  dependency. Under it, one dim line: version + "watch together, in sync".
- **Adaptive light/dark**: every style uses `lipgloss.AdaptiveColor{Light:
  ..., Dark: ...}` so the same UI reads correctly on white and black
  terminals — the existing `nickPalette` was already chosen for both, reuse
  its values for nicks.
- **Panels**: `lipgloss.RoundedBorder()` boxes with a dim border color and a
  colored title tab (e.g. `─ room ─`, `─ party ─`) inset into the top border.
  Info box left, live panel right, side-by-side when the terminal is wide
  enough (`lipgloss.JoinHorizontal`), stacked when narrow.
- **Room code**: rendered prominent (bold, brand color, copy-ready on its own
  line) — it's the single thing a host exists to share.
- **Live panel rows**: one row per peer — `colorFor`-colored nick, a status
  badge (`● ready` green / `◌ buffering` yellow / `✗ behind` red / `⋯
  connecting` dim), drift as a small signed number, and a mini per-peer
  download bar for streaming joiners. Self row pinned first.
- **Session states as moments, not just text**: a spinner (`bubbles/spinner`)
  while connecting to relays / waiting for the host; the pre-start readiness
  gate rendered as its own centered callout ("2/3 ready — press Enter to
  start") rather than a scrollback line; a one-frame flash of the border
  color on buffering→playing transitions is *out* (no animation loops beyond
  the spinner — terminals are not for particle effects, and every timer tick
  costs a redraw).
- **Download progress**: keep `renderProgress`'s bar format but style it —
  filled portion in the brand gradient, percentage bold, ETA dim.
- **Chat scrollback**: nicks in their existing deterministic colors, `*`
  notices dim italic, own messages prefixed distinctly. Timestamps omitted
  (the room is live; scrollback is ambience, not a log).
- **Input line**: a `>` prompt in brand color; when the input is empty and
  the gate is open, ghost text hints the load-bearing bare-Enter action
  ("press Enter to start the room").
- **Restraint rules**: two brand hues + the nick palette + green/yellow/red
  status — nothing else. No emoji beyond the status glyphs. Everything must
  degrade to readable monochrome under `NO_COLOR` (layout identical, colors
  stripped — the two gates stay independent).

## Failure modes (kept pragmatic)

- Panic inside `Update`/`View`: bubbletea already recovers and restores
  cooked terminal mode before propagating — no new handling needed beyond
  the existing `main.go` panic-recovery banner (`main.go:44-58`) catching it
  from there.
- Terminal resized below a usable floor (e.g. 20x8 via `tea.WindowSizeMsg`):
  `View()` renders a one-line "terminal too small" message instead of a
  garbled layout. Not a fallback to plain-print mid-session — deliberately
  out of scope.
- No mid-session TUI→plain fallback; the TTY/`JUNTO_NO_TUI` decision is made
  once at startup only.

## Phased build order

1. Static shell with fake/synthetic data (banner, info box, empty panel,
   empty scrollback, input line) — validates layout and color mapping before
   touching `app`/`syncer` at all.
2. Wire `bridge.go`/`fallback.go` + the `if tui.UseTUI()` gate and the
   `*Inner`-parameterization refactor. Real chat/notices/progress now flow
   into the TUI; live panel still empty; input still raw stdin (both paths
   coexist safely, hard branch).
3. Wire `sessionInfoMsg` at the send points identified above.
4. `engine.go`: add `Snapshot`/`PeerStatus`, `Deps.OnSnapshot`, refactor
   `printPeers` to share `buildSnapshot()`, call it from the 3-4 identified
   sites. New `engine` unit tests.
5. Replace the stdin scanner with `bubbles/textinput` feeding the same
   `lines` channel; verify `/pause /play /seek /peers /kick` and bare-Enter
   still reach `handleLine` unchanged.
6. Ctrl-C/lifecycle: explicit `KeyCtrlC` → cancel path; verify SIGTERM is
   untouched; verify mpv/relay cleanup on a forced Ctrl-C mid-session matches
   the plain path.
7. Polish: resize floor, `JUNTO_NO_TUI`, the connection-mode approximation,
   docs (CHANGELOG/README/ROADMAP — including the parked-line rewrite).

## Verification plan

1. `go build -o junto ./cmd/junto && go vet ./... && gofmt -l . && go test -race ./...`
   — the standing gate for every change in this repo.
2. `Update()`-driven unit tests with zero real terminal (matches this repo's
   `Deps{Printf: pf, ...}` fake-injection style already used in
   `gate_test.go`/`canhost_dynamic_test.go`): log-line append, scrollback
   cap, Ctrl-C → quit, bare-Enter passthrough. `View()` assertions via
   `strings.Contains` (matches `console_test.go`'s existing
   `TestRenderProgress` style) — no golden-terminal/`teatest` dependency
   needed for the MVP.
3. New `internal/syncer` tests for `buildSnapshot()`/`OnSnapshot`,
   table-driven, matching `canhost_dynamic_test.go`'s shape.
4. Manual: run a real two-terminal `create`/`join` session and confirm the
   banner, info box, live peer panel (ready/buffering/drift), chat, and
   `/peers`/`/pause`/`/seek`/bare-Enter-to-start all behave identically to
   today's plain-mode session, just rendered differently. Confirm Ctrl-C
   cleans up mpv and the relay connection. Confirm piping output
   (`junto create x.mkv | cat`) falls back to today's exact plain text with
   no TUI escape codes leaking into the stream.
5. Confirm `NO_COLOR=1` still yields a monochrome (but full-layout) TUI, not
   a TUI/plain-text toggle — the two gates are independent by design.
