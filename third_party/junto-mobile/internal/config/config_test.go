package config

import (
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

func TestCacheDir(t *testing.T) {
	t.Setenv("XDG_CACHE_HOME", "/custom/cache")
	if got, want := CacheDir(), filepath.Join("/custom/cache", "junto"); got != want {
		t.Errorf("CacheDir with XDG_CACHE_HOME = %q, want %q", got, want)
	}
	t.Setenv("XDG_CACHE_HOME", "")
	t.Setenv("HOME", "/home/tester")
	if got, want := CacheDir(), filepath.Join("/home/tester", ".cache", "junto"); got != want {
		t.Errorf("CacheDir fallback = %q, want %q", got, want)
	}
}

func TestParse(t *testing.T) {
	in := `
# junto config
nick = "alice"
relays = ["wss://a.example", "wss://b.example"]
mpv-path = "/opt/mpv"
turn = "turn:relay.example:3478"
turn-user = 'user'
turn-pass = "pass"
`
	got, err := parse(strings.NewReader(in), "test")
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	want := Config{
		Nick:     "alice",
		Relays:   []string{"wss://a.example", "wss://b.example"},
		MpvPath:  "/opt/mpv",
		Turn:     "turn:relay.example:3478",
		TurnUser: "user",
		TurnPass: "pass",
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("parse = %+v, want %+v", got, want)
	}
}

func TestParseEmptyAndComments(t *testing.T) {
	got, err := parse(strings.NewReader("\n# just a comment\n\n"), "test")
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if !reflect.DeepEqual(got, Config{}) {
		t.Errorf("expected zero config, got %+v", got)
	}
}

func TestParseErrors(t *testing.T) {
	for _, in := range []string{
		"nick \"alice\"",  // no '='
		"unknown = \"x\"", // unknown key
	} {
		if _, err := parse(strings.NewReader(in), "test"); err == nil {
			t.Errorf("parse(%q): expected error", in)
		}
	}
}

// TestTelemetryBoolean is the regression test for the silently-ignored
// opt-out bug: only the exact string "false" used to disable telemetry,
// so "off"/"0"/"False"/anything else silently left it enabled — a
// privacy-affecting typo class the unknown-key hard error exists to
// prevent everywhere else. Only true/false (bare or quoted) may parse;
// anything else must be a hard error, never a silent fallback to enabled.
func TestTelemetryBoolean(t *testing.T) {
	cases := []struct {
		val         string
		wantErr     bool
		noTelemetry bool
	}{
		{`telemetry = "true"`, false, false},
		{`telemetry = "false"`, false, true},
		{`telemetry = true`, false, false},
		{`telemetry = false`, false, true},
		{`telemetry = "off"`, true, false},
		{`telemetry = "0"`, true, false},
		{`telemetry = "False"`, true, false},
		{`telemetry = no`, true, false},
	}
	for _, c := range cases {
		got, err := parse(strings.NewReader(c.val), "test")
		if c.wantErr {
			if err == nil {
				t.Errorf("parse(%q): expected error for a non-boolean value, got NoTelemetry=%v", c.val, got.NoTelemetry)
			}
			continue
		}
		if err != nil {
			t.Errorf("parse(%q): unexpected error: %v", c.val, err)
			continue
		}
		if got.NoTelemetry != c.noTelemetry {
			t.Errorf("parse(%q): NoTelemetry = %v, want %v", c.val, got.NoTelemetry, c.noTelemetry)
		}
	}
}

func TestParseStringAndList(t *testing.T) {
	if s, err := parseString(`"x"`); err != nil || s != "x" {
		t.Errorf("parseString(%q) = %q, %v; want \"x\", nil", `"x"`, s, err)
	}
	if s, err := parseString(`'y'`); err != nil || s != "y" {
		t.Errorf("parseString(%q) = %q, %v; want \"y\", nil", `'y'`, s, err)
	}
	if _, err := parseString("bare"); err == nil {
		t.Error("an unquoted bare value must be a hard error, not silently accepted")
	}

	got, err := parseStringList(`["a", "b", "c"]`)
	if err != nil {
		t.Fatalf("parseStringList: %v", err)
	}
	if !reflect.DeepEqual(got, []string{"a", "b", "c"}) {
		t.Errorf("parseStringList = %v, want [a b c]", got)
	}
	// A trailing comma is tolerated (the blank split segment it produces
	// is skipped), but a genuinely malformed element is still an error.
	got, err = parseStringList(`["a", "b",]`)
	if err != nil || !reflect.DeepEqual(got, []string{"a", "b"}) {
		t.Errorf("parseStringList with trailing comma = %v, %v; want [a b], nil", got, err)
	}
	if _, err := parseStringList(`["a", bare, "c"]`); err == nil {
		t.Error("an unquoted list element must be a hard error, not silently dropped")
	}
}

// TestParseRejectsInlineComment is the regression test for the
// silent-corruption bug: an inline "# ..." comment after a value used
// to be kept as literal trailing text instead of being stripped or
// rejected, since unquote only checked that the value *started* and
// *ended* with a quote — "alice" # nickname doesn't end with a quote,
// so the whole thing (comment included) became the Nick. It must now be
// a hard error, matching this parser's documented whole-line-only
// comment support.
func TestParseRejectsInlineComment(t *testing.T) {
	if _, err := parse(strings.NewReader(`nick = "alice" # nickname`), "test"); err == nil {
		t.Error("an inline comment after a quoted value should be a hard error")
	}
}

// TestParseRejectsDuplicateKey is the regression test for the
// last-wins bug: repeating a key silently kept only the final value
// with no indication the earlier one was discarded — easy to hit via a
// copy-paste mistake and hard to notice since the file still "works".
func TestParseRejectsDuplicateKey(t *testing.T) {
	in := "nick = \"a\"\nnick = \"b\"\n"
	if _, err := parse(strings.NewReader(in), "test"); err == nil {
		t.Error("a duplicate key should be a hard error, not silently last-wins")
	}
}

// TestParseHandlesEscapedQuote is the regression test for the
// mangled-escape bug: unquote stripped only the first and last
// characters with no escape processing, so a value with an escaped
// quote inside it (e.g. a nick containing a literal ") kept the stray
// backslash instead of producing the intended unescaped string.
func TestParseHandlesEscapedQuote(t *testing.T) {
	got, err := parse(strings.NewReader(`nick = "O\"Brien"`), "test")
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if got.Nick != `O"Brien` {
		t.Errorf("Nick = %q, want %q", got.Nick, `O"Brien`)
	}
}

// FuzzParse checks the config parser never panics on arbitrary file
// content — it runs on a user-edited text file, so a stray character or
// a half-finished edit must produce a clean error, never crash junto
// on startup.
func FuzzParse(f *testing.F) {
	seeds := []string{
		"",
		"# just a comment\n",
		`nick = "alice"` + "\n",
		`relays = ["wss://a.example", "wss://b.example"]` + "\n",
		`telemetry = false` + "\n",
		`telemetry = "true"` + "\n",
		"nick \"alice\"",
		"unknown = \"x\"",
		`nick = "alice" # comment`,
		"nick = \"a\"\nnick = \"b\"\n",
		`nick = "O\"Brien"`,
		"nick = \x00\x01\x02",
		"relays = [\"a\", , \"c\"]",
	}
	for _, s := range seeds {
		f.Add(s)
	}
	f.Fuzz(func(t *testing.T, in string) {
		_, _ = parse(strings.NewReader(in), "fuzz")
	})
}
