package tui

import (
	"fmt"
	"strings"
	"time"

	"github.com/charmbracelet/lipgloss"

	"github.com/swayam-mishra/junto/internal/human"
	"github.com/swayam-mishra/junto/internal/syncer"
)

// Minimum usable terminal size; below this View renders a hint rather
// than a garbled layout.
const (
	minWidth  = 44
	minHeight = 12
)

func (m Model) View() string {
	if m.quitting {
		return ""
	}
	if m.width < minWidth || m.height < minHeight {
		return dimStyle.Render("terminal too small — enlarge the window to use junto")
	}

	banner := renderBanner(bannerSubtitle(m.info))
	panels := m.renderPanels()
	input := m.renderInput()
	status := m.status

	// Scrollback fills whatever vertical space is left between the fixed
	// top (banner + panels) and bottom (status + input) blocks.
	topH := lipgloss.Height(banner) + lipgloss.Height(panels)
	botH := lipgloss.Height(input)
	if status != "" {
		botH += lipgloss.Height(status)
	}
	scrollH := m.height - topH - botH - 2 // 2 = spacing rows
	if scrollH < 1 {
		scrollH = 1
	}
	scroll := m.renderScrollback(scrollH)

	parts := []string{banner, panels, scroll}
	if status != "" {
		parts = append(parts, status)
	}
	parts = append(parts, input)
	return strings.Join(parts, "\n")
}

func bannerSubtitle(info SessionInfo) string {
	role := "watch together, in sync"
	if info.Host {
		role = "hosting · watch together, in sync"
	} else if info.RoomCode != "" {
		role = "joined · watch together, in sync"
	}
	return role
}

// renderPanels lays the session-info box and the live peer/room box
// side-by-side when the terminal is wide enough, stacked when narrow.
// panel(width) renders a box of exactly that total width, so the body is
// built at width-4 (rounded border + 1-col padding on each side).
const panelChrome = 4 // border(2) + padding(2)

func (m Model) renderPanels() string {
	if m.width >= 88 {
		colW := (m.width - 2) / 2 // 2 = the gap between the two panels
		bodyW := colW - panelChrome
		infoBody, liveBody := equalizeHeight(m.infoBody(bodyW), m.liveBody(bodyW))
		info := panel("room", infoBody, colW)
		live := panel("party", liveBody, colW)
		return lipgloss.JoinHorizontal(lipgloss.Top, info, "  ", live)
	}
	bodyW := m.width - panelChrome
	info := panel("room", m.infoBody(bodyW), m.width)
	live := panel("party", m.liveBody(bodyW), m.width)
	return lipgloss.JoinVertical(lipgloss.Left, info, live)
}

func (m Model) infoBody(w int) string {
	var b strings.Builder
	if m.info.RoomCode != "" {
		b.WriteString(dimStyle.Render("code  "))
		b.WriteString(roomStyle.Render(m.info.RoomCode))
		b.WriteByte('\n')
	} else {
		b.WriteString(m.sp.View())
		b.WriteString(dimStyle.Render(" starting up…"))
		b.WriteByte('\n')
	}
	if m.info.JoinLink != "" {
		b.WriteString(dimStyle.Render("link  "))
		b.WriteString(accentStyle.Render(clip(m.info.JoinLink, w-6)))
		b.WriteByte('\n')
	}
	if m.info.Nick != "" {
		b.WriteString(dimStyle.Render("you   "))
		b.WriteString(accentStyle.Render(m.info.Nick))
		role := " (joiner)"
		if m.info.Host {
			role = " (host)"
		}
		b.WriteString(dimStyle.Render(role))
		b.WriteByte('\n')
	}
	if len(m.info.Playlist) > 0 {
		b.WriteString(dimStyle.Render("play  "))
		b.WriteString(textStyle.Render(clip(m.info.Playlist[0], w-6)))
		if n := len(m.info.Playlist) - 1; n > 0 {
			b.WriteString(dimStyle.Render(fmt.Sprintf("  +%d more", n)))
		}
		b.WriteByte('\n')
	}
	mode := "direct"
	if m.relayed {
		mode = "relayed"
	}
	b.WriteString(dimStyle.Render("mode  "))
	b.WriteString(textStyle.Render(mode))
	return strings.TrimRight(b.String(), "\n")
}

func (m Model) liveBody(w int) string {
	if !m.haveSnap {
		return m.sp.View() + dimStyle.Render(" connecting to the room…")
	}
	s := m.snap
	var b strings.Builder

	// Pre-start gate callout.
	if !s.GateOpen && s.TotalCount > 0 {
		gate := fmt.Sprintf("%d/%d ready", s.ReadyCount, s.TotalCount)
		if s.Host {
			gate += " — press Enter to start"
		} else {
			gate += " — waiting for host"
		}
		b.WriteString(accentStyle.Render(gate))
		b.WriteString("\n\n")
	}

	// A single name-column width shared by the self and peer rows, so the
	// numeric columns (position, drift) form aligned vertical columns. Any
	// streaming peer reserves the download-bar column so names don't shift.
	anyDL := false
	for _, p := range s.Peers {
		if p.DL > 0 {
			anyDL = true
		}
	}
	nameW := nameColWidth(w, anyDL)

	// Self row first: "you"/nick always in accent so it's instantly
	// scannable in the list.
	selfPos := ""
	if s.SelfPos >= 0 {
		selfPos = formatPos(s.SelfPos)
	}
	b.WriteString(rowLine(accentStyle.Render("●"), orDefault(s.SelfNick, "you"), accentBold, selfPos, "", "", nameW))
	b.WriteByte('\n')

	for _, p := range s.Peers {
		b.WriteString(m.peerRow(p, nameW))
		b.WriteByte('\n')
	}
	return strings.TrimRight(b.String(), "\n")
}

// Column widths for a peer/self row. glyph + name(flex) + pos + drift,
// with an optional trailing download-bar column reserved when streaming.
const (
	glyphColW = 2
	posColW   = 8
	driftColW = 11
	barColW   = 16 // "  " gap + "[██████] 55%"
)

func nameColWidth(panelW int, reserveBar bool) int {
	w := panelW - glyphColW - posColW - driftColW
	if reserveBar {
		w -= barColW
	}
	if w < 6 {
		return 6
	}
	return w
}

// rowLine lays one party row as aligned columns: a status glyph, the
// name (styled by nameStyle), then right-aligned position and drift
// columns, then an optional download bar. JoinHorizontal keeps the
// numeric columns in a scannable vertical line across every row.
func rowLine(glyph, name string, nameStyle lipgloss.Style, pos, drift, dl string, nameW int) string {
	g := lipgloss.NewStyle().Width(glyphColW).Render(glyph)
	n := nameStyle.Width(nameW).Render(clip(name, nameW))
	p := dimStyle.Width(posColW).Align(lipgloss.Right).Render(pos)
	d := dimStyle.Width(driftColW).Align(lipgloss.Right).Render(drift)
	row := lipgloss.JoinHorizontal(lipgloss.Top, g, n, p, d)
	if dl != "" {
		row = lipgloss.JoinHorizontal(lipgloss.Top, row, "  ", dl)
	}
	return row
}

func (m Model) peerRow(p syncer.PeerStatus, nameW int) string {
	pos := ""
	if p.HasState {
		pos = formatPos(p.Pos)
	}
	dl := ""
	if p.DL > 0 {
		dl = miniBar(p.DL)
	}
	row := rowLine(statusGlyph(p), p.Nick, nickStyle(p.Color, true), pos, driftNote(p), dl, nameW)
	if p.Ignored {
		row += dimStyle.Render("  (ignored)")
	}
	return row
}

// statusGlyph maps a peer's real status to a single colored glyph:
// ● good (ready/in-sync), ◐ warn (behind), ○ warn (buffering), · dim
// (connecting / no heartbeat yet). The ✕/colorBad error glyph is reserved
// for a real per-peer disconnect state, which doesn't exist yet.
func statusGlyph(p syncer.PeerStatus) string {
	switch {
	case !p.HasState:
		return dimStyle.Render("·")
	case p.Buffering:
		return warnStyle.Render("○")
	case p.DriftKnown && p.Drift <= -1:
		return warnStyle.Render("◐")
	default:
		return goodStyle.Render("●")
	}
}

// driftNote is the short right-aligned drift text ("2s behind" / "3s
// ahead"), empty when in sync or unknown.
func driftNote(p syncer.PeerStatus) string {
	if !p.DriftKnown {
		return ""
	}
	if p.Drift <= -1 {
		return fmt.Sprintf("%.0fs behind", -p.Drift)
	}
	if p.Drift >= 1 {
		return fmt.Sprintf("%.0fs ahead", p.Drift)
	}
	return ""
}

// miniBar renders a compact download-progress bar for a streaming peer:
// solid █ (accent) filled, ░ (dim) empty, with a right-aligned percent.
func miniBar(pct int) string {
	const w = 6
	if pct > 100 {
		pct = 100
	}
	if pct < 0 {
		pct = 0
	}
	filled := pct * w / 100
	bar := accentStyle.Render(strings.Repeat("█", filled)) + dimStyle.Render(strings.Repeat("░", w-filled))
	pctCol := dimStyle.Width(4).Align(lipgloss.Right).Render(fmt.Sprintf("%d%%", pct))
	return dimStyle.Render("[") + bar + dimStyle.Render("]") + " " + pctCol
}

func (m Model) renderScrollback(h int) string {
	lines := m.log
	if len(lines) > h {
		lines = lines[len(lines)-h:]
	}
	styled := make([]string, len(lines))
	for i, ln := range lines {
		styled[i] = styleLine(ln)
	}
	// Pad up to h so the input line stays pinned to the bottom.
	for len(styled) < h {
		styled = append([]string{""}, styled...)
	}
	return strings.Join(styled, "\n")
}

// styleLine differentiates message types in the log. System notices
// (lines the engine prefixes with "*") recede in dim italic; chat lines
// ("<nick> body") read as primary content with an accent-bold username
// and body in near-white. The engine may have ANSI-colored the nick
// already, so strip that first and re-style uniformly.
func styleLine(s string) string {
	plain := ansiStrip(s)
	switch {
	case plain == "":
		return ""
	case strings.HasPrefix(plain, "*"):
		return noticeStyle.Render(plain)
	case strings.HasPrefix(plain, "<"):
		if i := strings.Index(plain, "> "); i > 0 {
			nick := plain[1:i]
			body := plain[i+2:]
			return accentBold.Render("<"+nick+">") + " " + textStyle.Render(body)
		}
		return textStyle.Render(plain)
	default:
		return textStyle.Render(plain)
	}
}

func (m Model) renderInput() string {
	line := m.ti.View()
	// Ghost hint for the load-bearing bare-Enter start action, only when
	// the gate is open to us as host and the input is empty.
	if m.haveSnap && m.info.Host && !m.snap.GateOpen && m.ti.Value() == "" {
		return line + "\n" + ghostStyle.Render("  press Enter to start the room")
	}
	return line
}

// renderProgressStyled builds the styled download bar (gradient fill,
// bold %, dim ETA) from a progress update.
func renderProgressStyled(width int, p progressMsg) string {
	const barW = 24
	var frac float64
	if p.Total > 0 {
		frac = float64(p.Received) / float64(p.Total)
	}
	if frac > 1 {
		frac = 1
	}
	filled := int(frac * barW)
	bar := accentStyle.Render(strings.Repeat("█", filled)) + dimStyle.Render(strings.Repeat("░", barW-filled))
	label := p.Name
	if p.IsTail {
		label += " (index)"
	}
	eta := "--"
	if p.ETA > 0 {
		eta = etaStr(p.ETA)
	}
	pct := lipgloss.NewStyle().Foreground(colorAccent).Bold(true).Width(4).Align(lipgloss.Right).Render(fmt.Sprintf("%.0f%%", frac*100))
	return fmt.Sprintf("%s %s [%s] %s  %s/%s  %s/s  %s",
		accentStyle.Render("⬇"), textStyle.Render(clip(label, 28)), bar, pct,
		human.Bytes(p.Received), human.Bytes(p.Total), human.Bytes(int64(p.BytesPerSec)),
		dimStyle.Render("ETA "+eta))
}

func etaStr(d time.Duration) string {
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

// formatPos renders a playback position as H:MM:SS or M:SS.
func formatPos(secs float64) string {
	if secs < 0 {
		secs = 0
	}
	s := int(secs)
	h, m, sec := s/3600, (s%3600)/60, s%60
	if h > 0 {
		return fmt.Sprintf("%d:%02d:%02d", h, m, sec)
	}
	return fmt.Sprintf("%d:%02d", m, sec)
}

func clip(s string, w int) string {
	if w < 1 {
		w = 1
	}
	if lipgloss.Width(s) <= w {
		return s
	}
	r := []rune(s)
	if w <= 1 || len(r) <= 1 {
		return string(r[:1])
	}
	return string(r[:w-1]) + "…"
}

func orDefault(s, d string) string {
	if s == "" {
		return d
	}
	return s
}
