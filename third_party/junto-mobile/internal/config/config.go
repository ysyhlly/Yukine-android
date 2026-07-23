// Package config loads persisted defaults from
// ~/.config/junto/config.toml so common flags (nick, relays, TURN
// credentials, mpv path) don't have to be retyped every session.
// Explicit command-line flags always override the file.
//
// It parses a deliberately small TOML subset — flat `key = value` lines
// where the value is a quoted string or an array of quoted strings,
// plus whole-line `#` comments — which covers every junto setting
// without pulling in a TOML dependency.
package config

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// Config holds the persisted defaults. Zero values mean "unset" — the
// caller falls back to its built-in default.
type Config struct {
	Nick        string
	Relays      []string
	MpvPath     string
	Turn        string
	TurnUser    string
	TurnPass    string
	NoTelemetry bool // set by: telemetry = false
}

// Path returns the config file location: $XDG_CONFIG_HOME/junto/config.toml
// if XDG_CONFIG_HOME is set, else ~/.config/junto/config.toml.
func Path() string {
	dir := os.Getenv("XDG_CONFIG_HOME")
	if dir == "" {
		home, err := os.UserHomeDir()
		if err != nil {
			return ""
		}
		dir = filepath.Join(home, ".config")
	}
	return filepath.Join(dir, "junto", "config.toml")
}

// CacheDir returns junto's cache directory: $XDG_CACHE_HOME/junto if
// XDG_CACHE_HOME is set, else ~/.cache/junto. It's where yt-dlp-fetched
// URL videos are stored (keyed by video id) so re-hosting the same URL
// is instant. Returns "" if the home directory can't be determined.
func CacheDir() string {
	dir := os.Getenv("XDG_CACHE_HOME")
	if dir == "" {
		home, err := os.UserHomeDir()
		if err != nil {
			return ""
		}
		dir = filepath.Join(home, ".cache")
	}
	return filepath.Join(dir, "junto")
}

// Load reads and parses the config file. A missing file is not an error
// — it returns the zero Config. A malformed file returns an error so the
// user learns their settings were ignored rather than silently dropped.
func Load() (Config, error) {
	path := Path()
	if path == "" {
		return Config{}, nil
	}
	f, err := os.Open(path)
	if os.IsNotExist(err) {
		return Config{}, nil
	}
	if err != nil {
		return Config{}, fmt.Errorf("opening config %s: %w", path, err)
	}
	defer f.Close()
	return parse(f, path)
}

func parse(r io.Reader, path string) (Config, error) {
	var c Config
	sc := bufio.NewScanner(r)
	line := 0
	seenAt := make(map[string]int) // key -> line it was first set on
	for sc.Scan() {
		line++
		raw := strings.TrimSpace(sc.Text())
		if raw == "" || strings.HasPrefix(raw, "#") {
			continue
		}
		key, val, ok := strings.Cut(raw, "=")
		if !ok {
			return Config{}, fmt.Errorf("%s:%d: expected key = value", path, line)
		}
		key = strings.TrimSpace(key)
		val = strings.TrimSpace(val)

		if first, dup := seenAt[key]; dup {
			return Config{}, fmt.Errorf("%s:%d: duplicate key %q (first set on line %d) — last-wins would silently ignore one of them", path, line, key, first)
		}
		seenAt[key] = line

		switch key {
		case "nick", "mpv-path", "turn", "turn-user", "turn-pass":
			s, err := parseString(val)
			if err != nil {
				return Config{}, fmt.Errorf("%s:%d: %s: %w", path, line, key, err)
			}
			switch key {
			case "nick":
				c.Nick = s
			case "mpv-path":
				c.MpvPath = s
			case "turn":
				c.Turn = s
			case "turn-user":
				c.TurnUser = s
			case "turn-pass":
				c.TurnPass = s
			}
		case "relays":
			list, err := parseStringList(val)
			if err != nil {
				return Config{}, fmt.Errorf("%s:%d: relays: %w", path, line, err)
			}
			c.Relays = list
		case "telemetry":
			b, err := parseBool(val)
			if err != nil {
				// A privacy-affecting setting: fail loudly rather than
				// silently keeping telemetry on for an unrecognized value
				// (e.g. "off", "0", "False").
				return Config{}, fmt.Errorf("%s:%d: telemetry must be true or false, got %q", path, line, val)
			}
			c.NoTelemetry = !b
		default:
			return Config{}, fmt.Errorf("%s:%d: unknown setting %q", path, line, key)
		}
	}
	if err := sc.Err(); err != nil {
		return Config{}, fmt.Errorf("reading config %s: %w", path, err)
	}
	return c, nil
}

// parseString parses a single quoted TOML-style string value: exactly
// one pair of matching ' or " quotes, with \\ and \<quote-char> escapes
// recognized inside, and nothing but the quoted string on the line — no
// trailing content (an inline "# comment", say) and no bare unquoted
// value. Requiring this closes two failure modes that used to corrupt a
// value silently instead of erroring: an inline comment after the value
// (previously kept as literal trailing text, e.g. `nick = "alice" #
// hi"` set Nick to `"alice" # hi`) and an escaped quote inside the
// string (previously left as a literal backslash instead of being
// unescaped).
func parseString(val string) (string, error) {
	if len(val) < 2 {
		return "", fmt.Errorf("expected a quoted string, got %q", val)
	}
	quote := val[0]
	if quote != '"' && quote != '\'' {
		return "", fmt.Errorf("expected a quoted string, got %q", val)
	}
	var b strings.Builder
	for i := 1; i < len(val); i++ {
		c := val[i]
		if c == '\\' && i+1 < len(val) && (val[i+1] == quote || val[i+1] == '\\') {
			b.WriteByte(val[i+1])
			i++
			continue
		}
		if c == quote {
			if rest := strings.TrimSpace(val[i+1:]); rest != "" {
				return "", fmt.Errorf("unexpected text after closing quote: %q", rest)
			}
			return b.String(), nil
		}
		b.WriteByte(c)
	}
	return "", fmt.Errorf("unterminated quoted string: %q", val)
}

// parseStringList parses a TOML-style array of quoted strings, e.g.
// ["a", "b"]. Each element uses the same escape rules as parseString; a
// malformed element is a hard error rather than being silently dropped
// or left mangled.
func parseStringList(val string) ([]string, error) {
	if !strings.HasPrefix(val, "[") || !strings.HasSuffix(val, "]") {
		return nil, fmt.Errorf("expected a [...] list, got %q", val)
	}
	inner := strings.TrimSpace(val[1 : len(val)-1])
	if inner == "" {
		return nil, nil
	}
	var out []string
	for _, part := range strings.Split(inner, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue // tolerate a trailing comma, e.g. ["a", "b",]
		}
		s, err := parseString(part)
		if err != nil {
			return nil, fmt.Errorf("list element %q: %w", part, err)
		}
		out = append(out, s)
	}
	return out, nil
}

// parseBool accepts a bare true/false or a quoted "true"/"false" — the
// telemetry key's only two valid literal forms — and rejects near-misses
// like "off"/"0"/"False" instead of silently falling back to a default
// that would leave a privacy-affecting setting on.
func parseBool(val string) (bool, error) {
	switch val {
	case "true":
		return true, nil
	case "false":
		return false, nil
	}
	if s, err := parseString(val); err == nil {
		switch s {
		case "true":
			return true, nil
		case "false":
			return false, nil
		}
	}
	return false, fmt.Errorf("not true or false: %q", val)
}
