package mpv

import (
	"bufio"
	"encoding/json"
	"net"
	"strings"
	"testing"
	"time"
)

// TestEventQueueCoalescesSamePropertyBurst is the regression test for the
// burst-drop bug: a plain fixed-size buffered channel with a non-blocking
// send silently dropped a property-change event once it filled up (e.g.
// when a new file load resets every observed property at once), which
// could permanently desync whichever property's update was lost. Pushing
// several updates to the same property ID before the consumer drains any
// of them must coalesce to just the latest value, not drop any or queue
// duplicates.
func TestEventQueueCoalescesSamePropertyBurst(t *testing.T) {
	q := newEventQueue()
	for _, v := range []bool{true, false, true} {
		data, _ := json.Marshal(v)
		q.push(Event{Event: "property-change", ID: 1, Name: "pause", Data: data})
	}
	// A different property must not be coalesced together with obsPause.
	data, _ := json.Marshal(2.0)
	q.push(Event{Event: "property-change", ID: 2, Name: "speed", Data: data})

	ev, ok := q.next()
	if !ok || ev.ID != 1 {
		t.Fatalf("first delivered event = %+v, ok=%v; want property 1", ev, ok)
	}
	var got bool
	if err := json.Unmarshal(ev.Data, &got); err != nil || got != true {
		t.Fatalf("coalesced property 1 value = %v, err=%v; want the latest push (true)", got, err)
	}

	ev, ok = q.next()
	if !ok || ev.ID != 2 {
		t.Fatalf("second delivered event = %+v, ok=%v; want property 2", ev, ok)
	}

	// Nothing else queued: a third call must block, not return a stale
	// duplicate of an already-delivered coalesced event.
	done := make(chan struct{})
	go func() {
		q.next()
		close(done)
	}()
	select {
	case <-done:
		t.Fatal("next() returned a third event — the burst should have coalesced to exactly two")
	case <-time.After(50 * time.Millisecond):
	}
	q.close()
	<-done
}

// TestEventQueueNeverCoalescesDifferentEventKinds ensures non-property
// events (seek, playback-restart, file-loaded, end-file, ...) — which
// carry no reusable "current value" to coalesce onto — are each kept and
// delivered in order, even when several of the same kind arrive before
// the consumer drains any of them.
func TestEventQueueNeverCoalescesDifferentEventKinds(t *testing.T) {
	q := newEventQueue()
	q.push(Event{Event: "seek"})
	q.push(Event{Event: "seek"})
	q.push(Event{Event: "playback-restart"})

	var got []string
	for range 3 {
		ev, ok := q.next()
		if !ok {
			t.Fatalf("next() closed early after %d events", len(got))
		}
		got = append(got, ev.Event)
	}
	want := []string{"seek", "seek", "playback-restart"}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("got %v, want %v", got, want)
		}
	}
}

// TestEventQueueNextBlocksUntilPush checks next() doesn't busy-spin or
// return a zero value before anything has been pushed.
func TestEventQueueNextBlocksUntilPush(t *testing.T) {
	q := newEventQueue()
	done := make(chan Event)
	go func() {
		ev, _ := q.next()
		done <- ev
	}()

	select {
	case <-done:
		t.Fatal("next() returned before any event was pushed")
	case <-time.After(50 * time.Millisecond):
	}

	q.push(Event{Event: "seek"})
	select {
	case ev := <-done:
		if ev.Event != "seek" {
			t.Fatalf("got %+v, want the pushed seek event", ev)
		}
	case <-time.After(time.Second):
		t.Fatal("next() never returned after push")
	}
}

// TestEventQueueCloseUnblocksNext checks a consumer blocked in next()
// wakes up with ok=false once the queue is closed and drained, instead
// of blocking forever.
func TestEventQueueCloseUnblocksNext(t *testing.T) {
	q := newEventQueue()
	done := make(chan bool)
	go func() {
		_, ok := q.next()
		done <- ok
	}()

	select {
	case <-done:
		t.Fatal("next() returned before close")
	case <-time.After(50 * time.Millisecond):
	}

	q.close()
	select {
	case ok := <-done:
		if ok {
			t.Fatal("next() reported ok=true after close with nothing queued")
		}
	case <-time.After(time.Second):
		t.Fatal("next() never unblocked after close")
	}
}

// TestClientSurvivesBurstWithoutLosingLatestValue drives a real Client
// against a fake mpv socket (net.Pipe, same pattern the mpv package's
// other coverage gaps call for). It floods far more property-change
// updates for one property than the old fixed-size event buffer (64) held,
// with nothing draining Events() in the meantime — exactly the burst the
// old drop-when-full channel handled by silently discarding updates past
// the 64th. The client must still deliver the true latest value once
// drained, not a stale one truncated at the old buffer's capacity.
func TestClientSurvivesBurstWithoutLosingLatestValue(t *testing.T) {
	server, client := net.Pipe()
	defer server.Close()

	c := &Client{
		conn:    client,
		eq:      newEventQueue(),
		events:  make(chan Event, 64),
		pending: make(map[int64]chan response),
	}
	go c.readLoop()
	go c.feedEvents()

	const n = 200 // more than the old 64-slot buffer
	writeDone := make(chan struct{})
	go func() {
		defer close(writeDone)
		enc := json.NewEncoder(server)
		for i := range n {
			data, _ := json.Marshal(i)
			_ = enc.Encode(Event{Event: "property-change", ID: 1, Name: "pause", Data: data})
		}
	}()

	select {
	case <-writeDone:
	case <-time.After(2 * time.Second):
		t.Fatal("writer never finished sending the burst")
	}

	// Drain until the true latest value (n-1) actually arrives rather than
	// sleeping a fixed guess at how long feedEvents needs to catch up: the
	// writer finishing only means the bytes crossed the pipe, not that
	// readLoop has parsed and pushed every buffered line yet, and how long
	// that takes is machine-load-dependent. Polling with a generous overall
	// timeout converges correctly regardless of speed, while still failing
	// (on timeout, never seeing n-1) if the real burst-drop bug reappears.
	var delivered []int
	timeout := time.After(3 * time.Second)
drain:
	for {
		select {
		case ev := <-c.events:
			var v int
			if err := json.Unmarshal(ev.Data, &v); err != nil {
				t.Fatalf("bad event data: %v", err)
			}
			delivered = append(delivered, v)
			if v == n-1 {
				break drain // the true latest value arrived; nothing more can follow
			}
		case <-timeout:
			break drain
		}
	}

	if len(delivered) == 0 {
		t.Fatal("no events delivered at all")
	}
	if len(delivered) >= n {
		t.Errorf("delivered %d events for %d pushes to the same property — expected coalescing to reduce this well below the raw count", len(delivered), n)
	}
	if last := delivered[len(delivered)-1]; last != n-1 {
		t.Errorf("last delivered value = %d, want %d (the true latest push) — a stale/truncated value means the burst-drop bug is back", last, n-1)
	}
}

func TestReadIPCLineNormalLine(t *testing.T) {
	r := bufio.NewReaderSize(strings.NewReader("hello\nworld\n"), 64)
	line, err := readIPCLine(r)
	if err != nil || string(line) != "hello\n" {
		t.Fatalf("got %q, %v; want \"hello\\n\", nil", line, err)
	}
}

// TestReadIPCLineDiscardsOversizedAndResyncs is the regression test for
// the connection-teardown bug: a line longer than the reader's buffer
// used to surface as a fatal read error (bufio.ErrTooLong via Scanner),
// indistinguishable from the connection actually closing, tearing down
// the whole client over one pathological line from a still-healthy mpv
// process. readIPCLine must instead discard exactly the oversized line
// and resync cleanly at the start of the next one.
func TestReadIPCLineDiscardsOversizedAndResyncs(t *testing.T) {
	oversized := strings.Repeat("x", 100) // far more than the 16-byte buffer below
	r := bufio.NewReaderSize(strings.NewReader(oversized+"\n"+"ok\n"), 16)

	line, err := readIPCLine(r)
	if err != nil {
		t.Fatalf("unexpected error on an oversized line: %v", err)
	}
	if line != nil {
		t.Fatalf("expected nil (skipped) for an oversized line, got %q", line)
	}

	line, err = readIPCLine(r)
	if err != nil || string(line) != "ok\n" {
		t.Fatalf("expected to resync onto the next line \"ok\\n\", got %q, %v", line, err)
	}
}

// TestReadIPCLineOversizedAtEOF checks the one case that must still
// surface as a real error: the stream ends mid-oversized-line with no
// terminating newline ever arriving, which is a genuinely dead
// connection, not a skippable pathological line.
func TestReadIPCLineOversizedAtEOF(t *testing.T) {
	oversized := strings.Repeat("x", 100)
	r := bufio.NewReaderSize(strings.NewReader(oversized), 16)
	if _, err := readIPCLine(r); err == nil {
		t.Fatal("expected an error when the stream ends mid-oversized-line")
	}
}

// TestClientResyncsAfterOversizedLine drives a real Client against a
// fake mpv socket and checks it survives a pathological oversized line
// (bigger than maxIPCLineLen) without tearing down: a subsequent,
// well-formed event must still be delivered, and the client must not be
// left marked closed.
func TestClientResyncsAfterOversizedLine(t *testing.T) {
	server, client := net.Pipe()
	defer server.Close()

	c := &Client{
		conn:    client,
		eq:      newEventQueue(),
		events:  make(chan Event, 64),
		pending: make(map[int64]chan response),
	}
	go c.readLoop()
	go c.feedEvents()

	go func() {
		_, _ = server.Write([]byte(strings.Repeat("x", maxIPCLineLen+1000) + "\n"))
		data, _ := json.Marshal(true)
		_ = json.NewEncoder(server).Encode(Event{Event: "property-change", ID: 1, Name: "pause", Data: data})
	}()

	select {
	case ev := <-c.events:
		if ev.ID != 1 {
			t.Fatalf("got %+v, want property 1", ev)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("no event delivered after the oversized line — the read loop appears to have torn down instead of resyncing")
	}

	c.mu.Lock()
	closed := c.closed
	c.mu.Unlock()
	if closed {
		t.Error("client marked closed after an oversized line from a still-healthy connection")
	}
}
