package debug

import (
	"bufio"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// readLines opens path and returns its lines (the log file is flushed
// after every event, so this always reflects everything written so far).
func readLines(t *testing.T, path string) []string {
	t.Helper()
	f, err := os.Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	var lines []string
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		lines = append(lines, sc.Text())
	}
	if err := sc.Err(); err != nil {
		t.Fatal(err)
	}
	return lines
}

// TestEProducesValidJSONForControlCharacters is the regression test for
// the invalid-JSON bug: %q (Go-syntax quoting) escapes some control
// characters (e.g. BEL as \a, vertical tab as \v) that aren't legal JSON
// escapes, so a logged value containing one produced a line that looked
// like JSON but a real JSON parser would reject. Every line E writes
// must parse as valid JSON regardless of what's in the value.
func TestEProducesValidJSONForControlCharacters(t *testing.T) {
	l := New(t.TempDir())
	if l == nil {
		t.Fatal("New returned nil")
	}
	defer l.Close()

	l.E("test_event", "bel", "a\ab", "vtab", "a\vb", "quote", `a"b`, "backslash", `a\b`)

	lines := readLines(t, l.Path())
	if len(lines) != 1 {
		t.Fatalf("expected 1 line, got %d: %v", len(lines), lines)
	}
	var decoded map[string]any
	if err := json.Unmarshal([]byte(lines[0]), &decoded); err != nil {
		t.Fatalf("line is not valid JSON: %v\nline: %s", err, lines[0])
	}
	if decoded["bel"] != "a\ab" {
		t.Errorf("bel value round-tripped wrong: got %q", decoded["bel"])
	}
	if decoded["vtab"] != "a\vb" {
		t.Errorf("vtab value round-tripped wrong: got %q", decoded["vtab"])
	}
	if decoded["quote"] != `a"b` {
		t.Errorf("quote value round-tripped wrong: got %q", decoded["quote"])
	}
}

// TestEProducesValidJSONForEventAndKeyNames checks the event name and
// key names (also passed through %q previously) are equally safe.
func TestEProducesValidJSONForEventAndKeyNames(t *testing.T) {
	l := New(t.TempDir())
	if l == nil {
		t.Fatal("New returned nil")
	}
	defer l.Close()

	l.E("weird\aevent", "weird\vkey", "value")

	lines := readLines(t, l.Path())
	if len(lines) != 1 {
		t.Fatalf("expected 1 line, got %d: %v", len(lines), lines)
	}
	var decoded map[string]any
	if err := json.Unmarshal([]byte(lines[0]), &decoded); err != nil {
		t.Fatalf("line is not valid JSON: %v\nline: %s", err, lines[0])
	}
	if decoded["event"] != "weird\aevent" {
		t.Errorf("event round-tripped wrong: got %q", decoded["event"])
	}
}

// TestLogFileNameDistinguishesPIDsAtSameTimestamp is the regression test
// for the truncated-log bug: the log filename had only one-second
// resolution (no pid), so two junto processes starting within the same
// second computed the identical path — the second os.Create silently
// truncated the first, still-open process's log. Two different pids at
// the exact same timestamp must now produce different filenames. (pid
// is the right axis to test: within a single process — and hence a
// single test binary's pid — New is only ever called once at startup,
// so a same-process collision isn't the scenario this fix addresses;
// two separate junto processes are.)
func TestLogFileNameDistinguishesPIDsAtSameTimestamp(t *testing.T) {
	now := time.Now()
	a := logFileName(now, 111)
	b := logFileName(now, 222)
	if a == b {
		t.Errorf("two different pids at the identical timestamp produced the same filename: %q", a)
	}
}

func TestPathNilLogger(t *testing.T) {
	var l *Logger
	if l.Path() != "" {
		t.Error("a nil Logger's Path should be empty")
	}
	l.E("should not panic") // nil receiver must be a safe no-op
}

func TestJSONStringLogFileIsInDir(t *testing.T) {
	dir := t.TempDir()
	l := New(dir)
	if l == nil {
		t.Fatal("New returned nil")
	}
	defer l.Close()
	if filepath.Dir(l.Path()) != dir {
		t.Errorf("log file %q not created in %q", l.Path(), dir)
	}
}
