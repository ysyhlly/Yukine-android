package mpv

import (
	"bufio"
	"context"
	"encoding/json"
	"net"
	"testing"
	"time"
)

// fakeMpvServer speaks the server side of mpv's JSON IPC protocol over a
// net.Conn, so Client's request/response methods (Command and everything
// built on it — SetPause, SetSpeed, GetPlaybackTime, ...) can be tested
// end to end without a real mpv process. handle is invoked with the
// decoded "command" array for each request and returns the response's
// data (nil for none) and error string ("" means "success").
type fakeMpvServer struct {
	calls chan []any
}

func startFakeMpvServer(t *testing.T, conn net.Conn, handle func(cmd []any) (data any, errStr string)) *fakeMpvServer {
	t.Helper()
	s := &fakeMpvServer{calls: make(chan []any, 32)}
	go func() {
		sc := bufio.NewScanner(conn)
		for sc.Scan() {
			var req struct {
				Command   []any `json:"command"`
				RequestID int64 `json:"request_id"`
			}
			if err := json.Unmarshal(sc.Bytes(), &req); err != nil {
				continue
			}
			select {
			case s.calls <- req.Command:
			default:
			}

			var data any
			errStr := "success"
			if handle != nil {
				d, e := handle(req.Command)
				data = d
				if e != "" {
					errStr = e
				}
			}
			resp := map[string]any{"error": errStr, "request_id": req.RequestID}
			if data != nil {
				resp["data"] = data
			}
			b, err := json.Marshal(resp)
			if err != nil {
				continue
			}
			if _, err := conn.Write(append(b, '\n')); err != nil {
				return
			}
		}
	}()
	return s
}

// lastCall waits (briefly) for the most recent command the fake server
// received and returns it, failing the test if none arrived in time.
func (s *fakeMpvServer) lastCall(t *testing.T) []any {
	t.Helper()
	select {
	case cmd := <-s.calls:
		return cmd
	case <-time.After(2 * time.Second):
		t.Fatal("fake mpv server never received a command")
		return nil
	}
}

// newTestClient builds a Client wired to one end of a net.Pipe, with the
// other end handed to the caller to drive as a fake mpv server.
func newTestClient() (*Client, net.Conn) {
	server, client := net.Pipe()
	c := &Client{
		conn:    client,
		eq:      newEventQueue(),
		events:  make(chan Event, 64),
		pending: make(map[int64]chan response),
	}
	go c.readLoop()
	go c.feedEvents()
	return c, server
}

func TestSetPauseSendsCorrectCommand(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.SetPause(context.Background(), true); err != nil {
		t.Fatalf("SetPause: %v", err)
	}
}

func TestSetSpeedSendsCorrectCommand(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	got := make(chan []any, 1)
	startFakeMpvServer(t, server, func(cmd []any) (any, string) {
		got <- cmd
		return nil, ""
	})

	if err := c.SetSpeed(context.Background(), 1.5); err != nil {
		t.Fatalf("SetSpeed: %v", err)
	}
	cmd := <-got
	want := []any{"set_property", "speed", 1.5}
	if len(cmd) != len(want) || cmd[1] != want[1] || cmd[2] != want[2] {
		t.Errorf("command = %v, want %v", cmd, want)
	}
}

func TestSeekAbsoluteSendsCorrectCommand(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	fs := startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.SeekAbsolute(context.Background(), 42.5); err != nil {
		t.Fatalf("SeekAbsolute: %v", err)
	}
	cmd := fs.lastCall(t)
	if cmd[0] != "seek" || cmd[1] != 42.5 || cmd[2] != "absolute" {
		t.Errorf("command = %v, want [seek 42.5 absolute]", cmd)
	}
}

func TestSetSidPositiveAndDisable(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	fs := startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.SetSid(context.Background(), 2); err != nil {
		t.Fatalf("SetSid(2): %v", err)
	}
	cmd := fs.lastCall(t)
	if cmd[2] != float64(2) { // JSON numbers decode as float64
		t.Errorf("SetSid(2) sent %v, want 2", cmd[2])
	}

	if err := c.SetSid(context.Background(), 0); err != nil {
		t.Fatalf("SetSid(0): %v", err)
	}
	cmd = fs.lastCall(t)
	if cmd[2] != "no" {
		t.Errorf(`SetSid(0) sent %v, want "no"`, cmd[2])
	}
}

func TestSetAidPositiveAndDisable(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	fs := startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.SetAid(context.Background(), 1); err != nil {
		t.Fatalf("SetAid(1): %v", err)
	}
	cmd := fs.lastCall(t)
	if cmd[2] != float64(1) {
		t.Errorf("SetAid(1) sent %v, want 1", cmd[2])
	}

	if err := c.SetAid(context.Background(), -1); err != nil {
		t.Fatalf("SetAid(-1): %v", err)
	}
	cmd = fs.lastCall(t)
	if cmd[2] != "no" {
		t.Errorf(`SetAid(-1) sent %v, want "no"`, cmd[2])
	}
}

func TestSetSubDelaySendsCorrectCommand(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	fs := startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.SetSubDelay(context.Background(), -0.25); err != nil {
		t.Fatalf("SetSubDelay: %v", err)
	}
	cmd := fs.lastCall(t)
	if cmd[1] != "sub-delay" || cmd[2] != -0.25 {
		t.Errorf("command = %v, want [set_property sub-delay -0.25]", cmd)
	}
}

func TestSetPlaylistPosSendsCorrectCommand(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	fs := startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.SetPlaylistPos(context.Background(), 3); err != nil {
		t.Fatalf("SetPlaylistPos: %v", err)
	}
	cmd := fs.lastCall(t)
	if cmd[1] != "playlist-pos" || cmd[2] != float64(3) {
		t.Errorf("command = %v, want [set_property playlist-pos 3]", cmd)
	}
}

func TestGetPlaybackTimeParsesResponse(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	startFakeMpvServer(t, server, func(cmd []any) (any, string) {
		if cmd[0] == "get_property" && cmd[1] == "playback-time" {
			return 123.456, ""
		}
		return nil, "unsupported"
	})

	got, err := c.GetPlaybackTime(context.Background())
	if err != nil {
		t.Fatalf("GetPlaybackTime: %v", err)
	}
	if got != 123.456 {
		t.Errorf("GetPlaybackTime = %v, want 123.456", got)
	}
}

func TestGetPauseParsesResponse(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	startFakeMpvServer(t, server, func(cmd []any) (any, string) {
		if cmd[0] == "get_property" && cmd[1] == "pause" {
			return true, ""
		}
		return nil, "unsupported"
	})

	got, err := c.GetPause(context.Background())
	if err != nil {
		t.Fatalf("GetPause: %v", err)
	}
	if !got {
		t.Error("GetPause = false, want true")
	}
}

func TestObservePropertySendsCorrectCommand(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	fs := startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.ObserveProperty(context.Background(), 5, "pause"); err != nil {
		t.Fatalf("ObserveProperty: %v", err)
	}
	cmd := fs.lastCall(t)
	if cmd[0] != "observe_property" || cmd[1] != float64(5) || cmd[2] != "pause" {
		t.Errorf("command = %v, want [observe_property 5 pause]", cmd)
	}
}

func TestShowTextSendsCorrectCommand(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	fs := startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "" })

	if err := c.ShowText(context.Background(), "hello", 2000); err != nil {
		t.Fatalf("ShowText: %v", err)
	}
	cmd := fs.lastCall(t)
	if cmd[0] != "show-text" || cmd[1] != "hello" || cmd[2] != float64(2000) {
		t.Errorf("command = %v, want [show-text hello 2000]", cmd)
	}
}

// TestCommandReturnsMpvError checks a non-"success" response surfaces as
// a Go error naming both mpv's message and the failing command.
func TestCommandReturnsMpvError(t *testing.T) {
	c, server := newTestClient()
	defer server.Close()
	startFakeMpvServer(t, server, func([]any) (any, string) { return nil, "property not found" })

	err := c.SetPause(context.Background(), true)
	if err == nil {
		t.Fatal("expected an error when mpv reports failure")
	}
}

// TestCommandTimesOutWhenMpvNeverResponds is the regression-guarding
// test for Command's timeout path: if mpv never answers a request (a
// wedged IPC connection), Command must give up after requestTimeout
// instead of hanging forever.
func TestCommandTimesOutWhenMpvNeverResponds(t *testing.T) {
	saved := requestTimeout
	requestTimeout = 200 * time.Millisecond
	defer func() { requestTimeout = saved }()

	c, server := newTestClient()
	defer server.Close()
	// Drain requests without ever responding — net.Pipe's Write blocks
	// until the peer reads, so leaving the server side completely unread
	// would hang Command's Write itself rather than exercising the
	// response-wait timeout this test is actually about.
	go func() {
		sc := bufio.NewScanner(server)
		for sc.Scan() {
		}
	}()

	start := time.Now()
	_, err := c.Command(context.Background(), "get_property", "pause")
	elapsed := time.Since(start)

	if err == nil {
		t.Fatal("expected a timeout error")
	}
	if elapsed > 2*time.Second {
		t.Fatalf("Command took %v to give up, want well under 2s", elapsed)
	}
}

// TestCommandFailsAfterClose checks a command issued after the
// connection is known closed fails immediately with a clear error,
// rather than hanging or panicking.
func TestCommandFailsAfterClose(t *testing.T) {
	c, server := newTestClient()
	server.Close()
	c.conn.Close()
	// Let readLoop observe the close and mark the client closed.
	deadline := time.Now().Add(2 * time.Second)
	for {
		c.mu.Lock()
		closed := c.closed
		c.mu.Unlock()
		if closed || time.Now().After(deadline) {
			break
		}
		time.Sleep(5 * time.Millisecond)
	}

	if _, err := c.Command(context.Background(), "get_property", "pause"); err == nil {
		t.Fatal("expected an error calling Command on a closed connection")
	}
}
