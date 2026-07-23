// Package debug writes structured JSON log lines to a timestamped file
// when the user passes -debug. All exported methods are no-ops on a nil
// *Logger so callers never need nil checks.
package debug

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// Logger writes one JSON object per line to a debug log file.
// A nil *Logger is safe to use — all methods are no-ops.
type Logger struct {
	mu sync.Mutex
	w  *bufio.Writer
	f  *os.File
}

// New creates the log file at cacheDir/debug-<RFC3339>.log and returns a
// Logger. Returns nil on any failure; the caller treats nil as "disabled".
func New(cacheDir string) *Logger {
	if err := os.MkdirAll(cacheDir, 0o755); err != nil {
		return nil
	}
	name := logFileName(time.Now(), os.Getpid())
	f, err := os.Create(filepath.Join(cacheDir, name))
	if err != nil {
		return nil
	}
	fmt.Fprintf(os.Stderr, "debug log: %s\n", f.Name())
	return &Logger{w: bufio.NewWriterSize(f, 32*1024), f: f}
}

// logFileName builds the log file's basename. The pid disambiguates two
// junto processes starting within the same second — the timestamp alone
// only has one-second resolution, so without it the second os.Create
// would silently truncate the first process's still-open log file.
func logFileName(now time.Time, pid int) string {
	return fmt.Sprintf("debug-%s-%d.log", now.UTC().Format("2006-01-02T15-04-05Z"), pid)
}

// Path returns the log file path, or "" if the Logger is nil.
func (l *Logger) Path() string {
	if l == nil {
		return ""
	}
	return l.f.Name()
}

// E writes a structured event. kv is alternating key, value pairs.
//
//	l.E("peer_join", "peer", "abc123de", "nick", "alice")
//
// Produces: {"t":"…","event":"peer_join","peer":"abc123de","nick":"alice"}
func (l *Logger) E(event string, kv ...any) {
	if l == nil {
		return
	}
	l.mu.Lock()
	defer l.mu.Unlock()

	fmt.Fprintf(l.w, `{"t":%s,"event":%s`, jsonString(time.Now().UTC().Format(time.RFC3339Nano)), jsonString(event))
	for i := 0; i+1 < len(kv); i += 2 {
		key := fmt.Sprintf("%v", kv[i])
		val := kv[i+1]
		switch v := val.(type) {
		case bool:
			fmt.Fprintf(l.w, `,%s:%v`, jsonString(key), v)
		case int, int8, int16, int32, int64,
			uint, uint8, uint16, uint32, uint64,
			float32, float64:
			fmt.Fprintf(l.w, `,%s:%v`, jsonString(key), v)
		default:
			fmt.Fprintf(l.w, `,%s:%s`, jsonString(key), jsonString(fmt.Sprintf("%v", val)))
		}
	}
	fmt.Fprintln(l.w, "}")
	// Flush after each event so a crash doesn't lose the tail of the log.
	l.w.Flush()
}

// jsonString encodes s as an RFC 8259 JSON string. Go's %q verb (the
// previous approach) produces Go-syntax-quoted strings, not JSON: it
// escapes some control characters as \a and \v, which aren't legal JSON
// escapes, so a logged value containing one of them silently produced a
// line that looked like JSON but wasn't. json.Marshal on a string value
// never errors (invalid UTF-8 is replaced, not rejected).
func jsonString(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}

// Close flushes and closes the log file. No-op on nil.
func (l *Logger) Close() error {
	if l == nil {
		return nil
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	l.w.Flush()
	return l.f.Close()
}
