// Package mpv launches mpv as a subprocess and speaks its JSON IPC
// protocol over a local socket: newline-delimited JSON, requests
// matched to responses by request_id, asynchronous events in between.
package mpv

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"github.com/swayam-mishra/junto/internal/debug"
)

const (
	dialTimeout = 10 * time.Second
	// maxIPCLineLen bounds one line from mpv's IPC socket. mpv is a
	// trusted local subprocess, not adversarial input, but a pathological
	// property dump (e.g. an enormous track-list) must not be mistaken
	// for the connection dying — see readIPCLine.
	maxIPCLineLen = 4 << 20 // 4 MiB, well above any real IPC line
)

// requestTimeout bounds how long Command waits for mpv's response. A
// var so tests can shrink it instead of waiting out the real timeout.
var requestTimeout = 2 * time.Second

// Event is an asynchronous mpv event, e.g. property-change, seek,
// playback-restart, file-loaded, end-file.
type Event struct {
	Event string          `json:"event"`
	ID    int             `json:"id,omitempty"`   // observe id for property-change
	Name  string          `json:"name,omitempty"` // property name
	Data  json.RawMessage `json:"data,omitempty"`
}

type response struct {
	Error     string          `json:"error"`
	Data      json.RawMessage `json:"data"`
	RequestID int64           `json:"request_id"`
}

type Options struct {
	MpvPath     string   // binary name or path; default "mpv"
	MediaPaths  []string // playlist of file paths or URLs, in order
	SocketPath  string   // default: per-PID path in os.TempDir()
	StartPaused bool
	// NetworkCache enlarges mpv's demuxer buffer for streaming joiners,
	// who read from junto's local HTTP server as bytes trickle in over
	// the P2P transfer. A big forward buffer lets mpv read far ahead of
	// the playhead while the download is keeping up, so a transient dip
	// doesn't immediately stall the room; a back buffer keeps recently
	// played bytes so small backward seeks don't re-download.
	NetworkCache bool
	ExtraArgs    []string
}

// networkCacheArgs tune mpv's cache/demuxer for a streaming source.
// The room now pauses only when mpv fires paused-for-cache (truly empty
// buffer), not on speculative read-ahead stalls, so a generous readahead
// is safe: mpv builds a large jitter cushion without freezing the room
// while filling it. cache=yes forces caching even though the source is a
// loopback http:// URL mpv might otherwise treat as instant.
var networkCacheArgs = []string{
	"--cache=yes",
	"--demuxer-max-bytes=128MiB",
	"--demuxer-max-back-bytes=32MiB",
	"--demuxer-readahead-secs=120",
}

type Client struct {
	cmd    *exec.Cmd
	conn   net.Conn
	eq     *eventQueue
	events chan Event
	done   chan error
	log    *debug.Logger

	reqID   atomic.Int64
	mu      sync.Mutex
	pending map[int64]chan response
	closed  bool
}

// SetLogger attaches a debug logger. Safe to call any time after Launch.
func (c *Client) SetLogger(l *debug.Logger) { c.log = l }

// eventQueue buffers mpv events for delivery to a single consumer
// without ever blocking the socket read loop. property-change events
// are coalesced by property ID: a burst of rapid updates to the same
// property (e.g. every observed property resetting at once when a new
// file loads) collapses to just the latest value instead of piling up
// or — as a plain fixed-size buffered channel with a non-blocking send
// used to do — silently dropping an update once the buffer filled,
// desyncing whichever peer missed it. Every other event kind (seek,
// playback-restart, file-loaded, end-file) has no "current value" to
// coalesce onto, so each instance gets its own slot and is delivered in
// order; none are ever dropped.
type eventQueue struct {
	mu     sync.Mutex
	order  []string
	byKey  map[string]Event
	seq    int64
	wake   chan struct{}
	closed bool
}

func newEventQueue() *eventQueue {
	return &eventQueue{byKey: make(map[string]Event), wake: make(chan struct{}, 1)}
}

func (q *eventQueue) push(ev Event) {
	q.mu.Lock()
	key := ev.Event
	if ev.Event == "property-change" {
		key = fmt.Sprintf("property-change:%d", ev.ID)
	} else {
		q.seq++
		key = fmt.Sprintf("%s:%d", ev.Event, q.seq)
	}
	if _, exists := q.byKey[key]; !exists {
		q.order = append(q.order, key)
	}
	q.byKey[key] = ev
	q.mu.Unlock()
	select {
	case q.wake <- struct{}{}:
	default:
	}
}

// next blocks until an event is available or the queue is closed and
// drained, returning ok=false only in the latter case.
func (q *eventQueue) next() (Event, bool) {
	for {
		q.mu.Lock()
		if len(q.order) > 0 {
			key := q.order[0]
			q.order = q.order[1:]
			ev := q.byKey[key]
			delete(q.byKey, key)
			q.mu.Unlock()
			return ev, true
		}
		if q.closed {
			q.mu.Unlock()
			return Event{}, false
		}
		q.mu.Unlock()
		<-q.wake
	}
}

func (q *eventQueue) close() {
	q.mu.Lock()
	q.closed = true
	q.mu.Unlock()
	select {
	case q.wake <- struct{}{}:
	default:
	}
}

// DefaultSocketPath returns a per-process IPC path so two instances on
// one machine never collide.
func DefaultSocketPath() string {
	return ipcPath(fmt.Sprintf("junto-mpv-%d", os.Getpid()))
}

// Launch starts mpv with opts.MediaPaths as its playlist and connects
// to its IPC socket, retrying until mpv creates it.
func Launch(ctx context.Context, opts Options) (*Client, error) {
	if len(opts.MediaPaths) == 0 {
		return nil, errors.New("no media files to play")
	}
	if opts.MpvPath == "" {
		opts.MpvPath = "mpv"
	}
	if opts.SocketPath == "" {
		opts.SocketPath = DefaultSocketPath()
	}
	bin, err := exec.LookPath(opts.MpvPath)
	if err != nil {
		return nil, fmt.Errorf("mpv not found on your PATH — install it (e.g. `brew install mpv`, or your distro's package manager), or point to it with -mpv-path")
	}

	args := []string{
		"--input-ipc-server=" + opts.SocketPath,
		"--force-window",
		"--keep-open=yes", // reaching EOF must not kill the session
	}
	if opts.StartPaused {
		args = append(args, "--pause")
	}
	if opts.NetworkCache {
		args = append(args, networkCacheArgs...)
	}
	args = append(args, opts.ExtraArgs...)
	args = append(args, "--")
	args = append(args, opts.MediaPaths...)

	cmd := exec.Command(bin, args...)
	cmd.Stdout = nil
	cmd.Stderr = nil
	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("starting mpv: %w", err)
	}

	conn, err := dialWithRetry(ctx, opts.SocketPath, cmd)
	if err != nil {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		return nil, err
	}

	c := &Client{
		cmd:     cmd,
		conn:    conn,
		eq:      newEventQueue(),
		events:  make(chan Event, 64),
		done:    make(chan error, 1),
		pending: make(map[int64]chan response),
	}
	go c.readLoop()
	go c.feedEvents()
	go func() {
		err := cmd.Wait()
		c.conn.Close()
		c.done <- err
		close(c.done)
		os.Remove(opts.SocketPath)
	}()
	return c, nil
}

func dialWithRetry(ctx context.Context, path string, cmd *exec.Cmd) (net.Conn, error) {
	deadline := time.Now().Add(dialTimeout)
	for {
		conn, err := dialIPC(path)
		if err == nil {
			return conn, nil
		}
		if cmd.ProcessState != nil {
			return nil, fmt.Errorf("mpv quit while starting up — try running mpv on its own to see why")
		}
		if time.Now().After(deadline) {
			return nil, fmt.Errorf("timed out connecting to mpv IPC socket %s: %w", path, err)
		}
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-time.After(100 * time.Millisecond):
		}
	}
}

// readIPCLine reads one newline-terminated line from r, capped at r's
// buffer size (maxIPCLineLen). A line at or under the cap is returned
// normally. A line over the cap — bufio.ErrBufferFull, meaning no
// newline appeared within a full buffer — is discarded by reading (and
// dropping) further chunks until the newline that ends it is found,
// without ever buffering more than one chunk of it at a time; (nil, nil)
// then tells the caller to resume at the next line instead of mistaking
// an oversized line for the connection dying.
func readIPCLine(r *bufio.Reader) ([]byte, error) {
	line, err := r.ReadSlice('\n')
	if err == nil {
		return append([]byte(nil), line...), nil // ReadSlice's buffer is reused; copy out
	}
	if err != bufio.ErrBufferFull {
		return nil, err // real read error (EOF, connection closed, ...)
	}
	for err == bufio.ErrBufferFull {
		_, err = r.ReadSlice('\n')
	}
	if err != nil {
		return nil, err // stream ended before the oversized line's end
	}
	return nil, nil // skipped; caller should read the next line
}

func (c *Client) readLoop() {
	reader := bufio.NewReaderSize(c.conn, maxIPCLineLen)
	for {
		line, err := readIPCLine(reader)
		if err != nil {
			break
		}
		if line == nil {
			c.log.E("mpv_line_too_long", "max_bytes", maxIPCLineLen)
			continue
		}
		// Responses carry request_id; everything else is an event.
		var probe struct {
			RequestID *int64 `json:"request_id"`
			Event     string `json:"event"`
		}
		if err := json.Unmarshal(line, &probe); err != nil {
			continue
		}
		switch {
		case probe.RequestID != nil:
			var resp response
			if err := json.Unmarshal(line, &resp); err != nil {
				continue
			}
			c.mu.Lock()
			ch := c.pending[resp.RequestID]
			delete(c.pending, resp.RequestID)
			c.mu.Unlock()
			if ch != nil {
				ch <- resp
			}
		case probe.Event != "":
			var ev Event
			if err := json.Unmarshal(line, &ev); err != nil {
				continue
			}
			c.eq.push(ev)
		}
	}
	// Socket closed: fail all in-flight requests.
	c.mu.Lock()
	c.closed = true
	for id, ch := range c.pending {
		close(ch)
		delete(c.pending, id)
	}
	c.mu.Unlock()
	c.eq.close()
}

// feedEvents relays coalesced events from eq to the public Events()
// channel, one at a time, blocking on the channel send as needed — this
// is safe here (unlike in readLoop) because it's decoupled from the mpv
// socket read loop, so a slow consumer only delays delivery instead of
// stalling IPC reads.
func (c *Client) feedEvents() {
	defer close(c.events)
	for {
		ev, ok := c.eq.next()
		if !ok {
			return
		}
		c.events <- ev
	}
}

// Command sends an mpv command and waits for its response data.
func (c *Client) Command(ctx context.Context, args ...any) (json.RawMessage, error) {
	id := c.reqID.Add(1)
	req, err := json.Marshal(map[string]any{"command": args, "request_id": id})
	if err != nil {
		return nil, err
	}

	ch := make(chan response, 1)
	c.mu.Lock()
	if c.closed {
		c.mu.Unlock()
		return nil, errors.New("mpv connection closed")
	}
	c.pending[id] = ch
	c.mu.Unlock()

	if _, err := c.conn.Write(append(req, '\n')); err != nil {
		c.mu.Lock()
		delete(c.pending, id)
		c.mu.Unlock()
		return nil, fmt.Errorf("writing to mpv: %w", err)
	}

	ctx, cancel := context.WithTimeout(ctx, requestTimeout)
	defer cancel()
	select {
	case resp, ok := <-ch:
		if !ok {
			return nil, errors.New("mpv connection closed")
		}
		if resp.Error != "success" {
			return nil, fmt.Errorf("mpv: %s (command %v)", resp.Error, args)
		}
		return resp.Data, nil
	case <-ctx.Done():
		c.mu.Lock()
		delete(c.pending, id)
		c.mu.Unlock()
		return nil, fmt.Errorf("mpv command %v: %w", args, ctx.Err())
	}
}

func (c *Client) SetPause(ctx context.Context, paused bool) error {
	_, err := c.Command(ctx, "set_property", "pause", paused)
	return err
}

func (c *Client) SeekAbsolute(ctx context.Context, secs float64) error {
	_, err := c.Command(ctx, "seek", secs, "absolute")
	return err
}

func (c *Client) SetSpeed(ctx context.Context, speed float64) error {
	_, err := c.Command(ctx, "set_property", "speed", speed)
	return err
}

func (c *Client) SetSubDelay(ctx context.Context, secs float64) error {
	_, err := c.Command(ctx, "set_property", "sub-delay", secs)
	return err
}

// SetSid selects subtitle track sid (1-based); sid <= 0 disables
// subtitles. mpv documents the string "no" for disabling — writing 0
// would be rejected as "selects nothing" isn't a valid numeric choice.
// AddSubtitle loads an external subtitle file (or URL) as a selectable
// track. The "auto" flag selects it only if no subtitle is active, so
// it doesn't fight the room's synced track selection.
func (c *Client) AddSubtitle(ctx context.Context, path string) error {
	_, err := c.Command(ctx, "sub-add", path, "auto")
	return err
}

func (c *Client) SetSid(ctx context.Context, sid int) error {
	var v any = sid
	if sid <= 0 {
		v = "no"
	}
	_, err := c.Command(ctx, "set_property", "sid", v)
	return err
}

// SetAid selects audio track aid (1-based); aid <= 0 disables audio.
// Mirrors SetSid — mpv uses "no" to disable, not 0.
func (c *Client) SetAid(ctx context.Context, aid int) error {
	var v any = aid
	if aid <= 0 {
		v = "no"
	}
	_, err := c.Command(ctx, "set_property", "aid", v)
	return err
}

// SetPlaylistPos jumps to playlist entry idx (0-based). Callers must
// bounds-check: mpv treats an out-of-range write as -1, which idles or
// exits the player.
func (c *Client) SetPlaylistPos(ctx context.Context, idx int) error {
	_, err := c.Command(ctx, "set_property", "playlist-pos", idx)
	return err
}

func (c *Client) GetPlaybackTime(ctx context.Context) (float64, error) {
	data, err := c.Command(ctx, "get_property", "playback-time")
	if err != nil {
		return 0, err
	}
	var t float64
	if err := json.Unmarshal(data, &t); err != nil {
		return 0, fmt.Errorf("parsing playback-time %q: %w", data, err)
	}
	return t, nil
}

func (c *Client) GetPause(ctx context.Context) (bool, error) {
	data, err := c.Command(ctx, "get_property", "pause")
	if err != nil {
		return false, err
	}
	var p bool
	if err := json.Unmarshal(data, &p); err != nil {
		return false, fmt.Errorf("parsing pause %q: %w", data, err)
	}
	return p, nil
}

func (c *Client) ObserveProperty(ctx context.Context, id int, name string) error {
	_, err := c.Command(ctx, "observe_property", id, name)
	return err
}

// ShowText displays text in mpv's OSD for durationMs milliseconds.
// durationMs 0 uses mpv's default osd-duration.
func (c *Client) ShowText(ctx context.Context, text string, durationMs int) error {
	_, err := c.Command(ctx, "show-text", text, durationMs)
	return err
}

// Events delivers asynchronous mpv events; closed when mpv exits.
func (c *Client) Events() <-chan Event { return c.events }

// Done yields mpv's exit error (possibly nil) when the process ends.
func (c *Client) Done() <-chan error { return c.done }

// Close asks mpv to quit, escalating to SIGKILL if it lingers.
func (c *Client) Close() error {
	ctx, cancel := context.WithTimeout(context.Background(), requestTimeout)
	defer cancel()
	_, _ = c.Command(ctx, "quit")
	select {
	case <-c.done:
		return nil
	case <-time.After(2 * time.Second):
		_ = c.cmd.Process.Kill()
		<-c.done
		return nil
	}
}

func tempIPCName(name string) string {
	return filepath.Join(os.TempDir(), name+".sock")
}
