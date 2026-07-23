package tui

import (
	"strings"
	"testing"

	"github.com/swayam-mishra/junto/internal/syncer"
)

// TestStyleLineDifferentiation checks the log restyles the two message
// kinds correctly: system notices keep their "*" and content; chat lines
// are parsed into "<nick> body" with the engine's inline nick color
// stripped (so it can be re-styled uniformly). Assertions are on the
// ANSI-stripped output so they hold regardless of the test env's color
// profile.
func TestStyleLineDifferentiation(t *testing.T) {
	cases := []struct{ in, want string }{
		// System notice.
		{"* carol joined (3 watching)", "* carol joined (3 watching)"},
		// Chat with an engine-colored nick — the color must be stripped and
		// the "<nick> body" shape preserved.
		{"<\x1b[38;5;205malice\x1b[0m> the effects are unreal", "<alice> the effects are unreal"},
		// Bare chat, no embedded color.
		{"<bob> hi", "<bob> hi"},
		// Neither shape.
		{"just some text", "just some text"},
		// A chat body that itself contains "> " must split on the first one.
		{"<alice> 3 > 2 for sure", "<alice> 3 > 2 for sure"},
	}
	for _, c := range cases {
		if got := ansiStrip(styleLine(c.in)); got != c.want {
			t.Errorf("styleLine(%q) content = %q, want %q", c.in, got, c.want)
		}
	}
	// A blank scrollback pad line stays blank.
	if styleLine("") != "" {
		t.Error("blank line should render blank")
	}
}

func TestStatusGlyph(t *testing.T) {
	cases := []struct {
		name string
		p    syncer.PeerStatus
		want string
	}{
		{"connecting", syncer.PeerStatus{HasState: false}, "·"},
		{"buffering", syncer.PeerStatus{HasState: true, Buffering: true}, "○"},
		{"behind", syncer.PeerStatus{HasState: true, DriftKnown: true, Drift: -3}, "◐"},
		{"ready", syncer.PeerStatus{HasState: true, Ready: true}, "●"},
		{"in-sync default", syncer.PeerStatus{HasState: true}, "●"},
		// Buffering takes precedence over a drift reading.
		{"buffering beats behind", syncer.PeerStatus{HasState: true, Buffering: true, DriftKnown: true, Drift: -3}, "○"},
	}
	for _, c := range cases {
		if got := ansiStrip(statusGlyph(c.p)); got != c.want {
			t.Errorf("%s: glyph = %q, want %q", c.name, got, c.want)
		}
	}
}

func TestDriftNote(t *testing.T) {
	cases := []struct {
		p    syncer.PeerStatus
		want string
	}{
		{syncer.PeerStatus{DriftKnown: false}, ""},
		{syncer.PeerStatus{DriftKnown: true, Drift: 0}, ""},
		{syncer.PeerStatus{DriftKnown: true, Drift: -2}, "2s behind"},
		{syncer.PeerStatus{DriftKnown: true, Drift: 3}, "3s ahead"},
	}
	for _, c := range cases {
		if got := driftNote(c.p); got != c.want {
			t.Errorf("driftNote(%+v) = %q, want %q", c.p, got, c.want)
		}
	}
}

// TestSelfRowIsAccent guards that the self row renders the current user's
// name in the accent style so it's scannable — asserted structurally
// (self name present, glyph is the accent bullet) since the test env may
// strip colors.
func TestSelfRowIsAccent(t *testing.T) {
	m := sized(New())
	m = step(m, snapshotMsg(syncer.Snapshot{SelfNick: "swayam", SelfPos: 100, GateOpen: true, TotalCount: 1}))
	v := m.View()
	if !strings.Contains(v, "swayam") || !strings.Contains(v, "●") {
		t.Fatalf("self row missing name or bullet:\n%s", v)
	}
}
