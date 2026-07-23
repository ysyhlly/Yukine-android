package tui

import (
	"regexp"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

func splitLines(s string) []string { return strings.Split(s, "\n") }
func joinLines(l []string) string  { return strings.Join(l, "\n") }

// ansiRe matches SGR color/style escape sequences.
var ansiRe = regexp.MustCompile("\x1b\\[[0-9;]*m")

// ansiStrip removes SGR escape sequences so a pre-colored line can be
// re-styled uniformly.
func ansiStrip(s string) string { return ansiRe.ReplaceAllString(s, "") }

// equalizeHeight pads both bodies with blank lines to the taller one's
// height, so side-by-side panels get equal-height borders.
func equalizeHeight(a, b string) (string, string) {
	ha, hb := lipgloss.Height(a), lipgloss.Height(b)
	max := ha
	if hb > max {
		max = hb
	}
	return padLines(a, max-ha), padLines(b, max-hb)
}

func padLines(s string, n int) string {
	if n <= 0 {
		return s
	}
	return s + strings.Repeat("\n", n)
}

// spliceTitle overlays tab onto line starting at visible column `at`,
// preserving the surrounding border. Both line and tab may contain ANSI;
// widths are measured visually. If the line is too short the tab is
// dropped (returns line unchanged).
func spliceTitle(line, tab string, at int) string {
	lineW := lipgloss.Width(line)
	tabW := lipgloss.Width(tab)
	if at+tabW >= lineW {
		return line
	}
	left := truncVisible(line, at)
	right := dropVisible(line, at+tabW)
	return left + tab + right
}

// truncVisible returns the prefix of s spanning the first n visible
// columns (ANSI-aware, best-effort: assumes single-width border runes,
// which the box-drawing characters are).
func truncVisible(s string, n int) string {
	if n <= 0 {
		return ""
	}
	var b strings.Builder
	w := 0
	inEsc := false
	for _, r := range s {
		if r == '\x1b' {
			inEsc = true
			b.WriteRune(r)
			continue
		}
		if inEsc {
			b.WriteRune(r)
			if r == 'm' {
				inEsc = false
			}
			continue
		}
		if w >= n {
			break
		}
		b.WriteRune(r)
		w++
	}
	return b.String()
}

// dropVisible returns s with its first n visible columns removed
// (ANSI-aware).
func dropVisible(s string, n int) string {
	var b strings.Builder
	w := 0
	inEsc := false
	for _, r := range s {
		if r == '\x1b' {
			inEsc = true
			b.WriteRune(r)
			continue
		}
		if inEsc {
			b.WriteRune(r)
			if r == 'm' {
				inEsc = false
			}
			continue
		}
		if w < n {
			w++
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}
