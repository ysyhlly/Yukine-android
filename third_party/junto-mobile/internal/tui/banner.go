package tui

import (
	"strings"

	"github.com/charmbracelet/lipgloss"
)

// bannerArt is a hand-drawn block-letter "JUNTO" (5 rows). Kept as a
// plain string const so there's no figlet dependency; the gradient is
// applied per column at render time.
const bannerArt = `      ██  ██    ██  ███    ██  ████████   ██████
      ██  ██    ██  ████   ██     ██     ██    ██
      ██  ██    ██  ██ ██  ██     ██     ██    ██
██    ██  ██    ██  ██  ██ ██     ██     ██    ██
 ██████    ██████   ██   ████     ██      ██████ `

// gradientStops are the two palette hues the banner interpolates between,
// left to right (accent → good). Truecolor terminals get the smooth
// blend; lipgloss downsamples to the nearest ANSI-256 entry elsewhere,
// and everything is stripped under NO_COLOR / a non-color profile.
var gradientStops = [2]string{string(colorAccent), string(colorGood)}

// renderBanner returns the block-letter banner with a horizontal color
// gradient applied per column, then a dim subtitle line (version +
// tagline) beneath it. width bounds nothing here (the art is fixed
// width); callers center or pad as needed.
func renderBanner(subtitle string) string {
	lines := strings.Split(bannerArt, "\n")
	maxw := 0
	for _, ln := range lines {
		if w := lipgloss.Width(ln); w > maxw {
			maxw = w
		}
	}
	var b strings.Builder
	for _, ln := range lines {
		runes := []rune(ln)
		for i, r := range runes {
			if r == ' ' {
				b.WriteRune(' ')
				continue
			}
			frac := 0.0
			if maxw > 1 {
				frac = float64(i) / float64(maxw-1)
			}
			style := lipgloss.NewStyle().Foreground(lipgloss.Color(lerpHex(gradientStops[0], gradientStops[1], frac)))
			b.WriteString(style.Render(string(r)))
		}
		b.WriteByte('\n')
	}
	if subtitle != "" {
		b.WriteString(dimStyle.Render(subtitle))
	}
	return b.String()
}

// lerpHex linearly interpolates between two "#rrggbb" colors at t∈[0,1]
// and returns the result as a "#rrggbb" string.
func lerpHex(a, c string, t float64) string {
	ar, ag, ab := hexToRGB(a)
	cr, cg, cb := hexToRGB(c)
	if t < 0 {
		t = 0
	}
	if t > 1 {
		t = 1
	}
	r := int(float64(ar) + (float64(cr)-float64(ar))*t)
	g := int(float64(ag) + (float64(cg)-float64(ag))*t)
	bl := int(float64(ab) + (float64(cb)-float64(ab))*t)
	return "#" + hex2(r) + hex2(g) + hex2(bl)
}

func hexToRGB(s string) (int, int, int) {
	s = strings.TrimPrefix(s, "#")
	if len(s) != 6 {
		return 255, 255, 255
	}
	return hexByte(s[0:2]), hexByte(s[2:4]), hexByte(s[4:6])
}

func hexByte(s string) int {
	n := 0
	for _, c := range s {
		n <<= 4
		switch {
		case c >= '0' && c <= '9':
			n |= int(c - '0')
		case c >= 'a' && c <= 'f':
			n |= int(c-'a') + 10
		case c >= 'A' && c <= 'F':
			n |= int(c-'A') + 10
		}
	}
	return n
}

func hex2(n int) string {
	if n < 0 {
		n = 0
	}
	if n > 255 {
		n = 255
	}
	const digits = "0123456789abcdef"
	return string([]byte{digits[n>>4], digits[n&0xf]})
}
