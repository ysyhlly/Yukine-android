package tui

import (
	"strconv"

	"github.com/charmbracelet/lipgloss"
)

// Centralized palette. Fixed lipgloss.Color values (tuned for dark
// terminals); lipgloss strips them under NO_COLOR / a non-color profile,
// so the layout stays intact in monochrome.
const (
	colorAccent = lipgloss.Color("#7DD3FC") // cyan: you / active / links
	colorGood   = lipgloss.Color("#86EFAC") // green: ready / synced / connected
	colorWarn   = lipgloss.Color("#FDE68A") // amber: behind / buffering / pending
	colorBad    = lipgloss.Color("#FCA5A5") // red: errors / disconnects / failures
	colorDim    = lipgloss.Color("#6B7280") // muted gray: borders, labels, chrome
	colorText   = lipgloss.Color("#E5E7EB") // near-white: primary body text
)

var (
	dimStyle    = lipgloss.NewStyle().Foreground(colorDim)
	textStyle   = lipgloss.NewStyle().Foreground(colorText)
	accentStyle = lipgloss.NewStyle().Foreground(colorAccent)
	accentBold  = lipgloss.NewStyle().Foreground(colorAccent).Bold(true)
	goodStyle   = lipgloss.NewStyle().Foreground(colorGood)
	warnStyle   = lipgloss.NewStyle().Foreground(colorWarn)
	badStyle    = lipgloss.NewStyle().Foreground(colorBad) // reserved for a real error/disconnect state
	noticeStyle = lipgloss.NewStyle().Foreground(colorDim).Italic(true)
	roomStyle   = lipgloss.NewStyle().Foreground(colorAccent).Bold(true)
	promptStyle = lipgloss.NewStyle().Foreground(colorAccent).Bold(true)
	ghostStyle  = lipgloss.NewStyle().Foreground(colorDim).Italic(true)
)

// panelStyle boxes a section with a rounded dim border; the title is
// rendered as an inset tab on the top border by panel().
var panelStyle = lipgloss.NewStyle().
	Border(lipgloss.RoundedBorder()).
	BorderForeground(colorDim).
	Padding(0, 1)

// panel wraps body in a rounded border with an inset title tab
// ("─ title ─") on the top edge, at the given content width. The label is
// dim, staying part of the chrome rather than drawing the eye.
func panel(title, body string, width int) string {
	box := panelStyle.Width(width).Render(body)
	if title == "" {
		return box
	}
	// Overlay the title onto the top border line: replace a slice of the
	// top border's dashes with " title ".
	lines := splitLines(box)
	if len(lines) == 0 {
		return box
	}
	tab := dimStyle.Render("─ " + title + " ─")
	lines[0] = spliceTitle(lines[0], tab, 2)
	return joinLines(lines)
}

// nickStyle renders a nick in its deterministic ANSI-256 color (the same
// value syncer's colorFor computes, carried on the snapshot). A zero code
// or colors-off falls back to primary body text.
func nickStyle(code uint8, colors bool) lipgloss.Style {
	if !colors || code == 0 {
		return textStyle
	}
	return lipgloss.NewStyle().Foreground(lipgloss.Color(strconv.Itoa(int(code))))
}
