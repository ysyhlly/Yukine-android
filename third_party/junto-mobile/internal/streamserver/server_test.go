package streamserver

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// fakeStore is a controllable Store backed by a temp file whose
// available byte count the test advances over time. path/finalPath and
// the done-gated rename in setDone mirror transfer.FileStore's real
// .part-to-final transition, so tests can exercise the same completion
// race OpenFor exists to close.
type fakeStore struct {
	path      string // backing file while downloading (the ".part")
	finalPath string // backing file once done (post-rename)
	meta      protocol.FileMeta
	mu        sync.Mutex
	cond      *sync.Cond
	avail     int64
	done      bool
	err       error
}

func newFakeStore(t *testing.T, data []byte) *fakeStore {
	t.Helper()
	dir := t.TempDir()
	path := filepath.Join(dir, "f.bin.part")
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	fs := &fakeStore{
		path:      path,
		finalPath: filepath.Join(dir, "f.bin"),
		meta:      protocol.FileMeta{Name: "f.bin", Size: int64(len(data)), SHA256: hex.EncodeToString(sum[:])},
	}
	fs.cond = sync.NewCond(&fs.mu)
	return fs
}

func (s *fakeStore) setAvail(n int64) { s.mu.Lock(); s.avail = n; s.cond.Broadcast(); s.mu.Unlock() }
func (s *fakeStore) setErr(e error)   { s.mu.Lock(); s.err = e; s.cond.Broadcast(); s.mu.Unlock() }

// setDone renames path to finalPath and marks the store done atomically,
// mirroring transfer.fileEntry.finishRename — the real completion race
// this fake exists to reproduce is between exactly these two steps.
func (s *fakeStore) setDone() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := os.Rename(s.path, s.finalPath); err != nil {
		panic(err) // test setup issue, not a path this fake expects to hit
	}
	s.done = true
	s.cond.Broadcast()
}

func (s *fakeStore) Len() int                     { return 1 }
func (s *fakeStore) Meta(i int) protocol.FileMeta { return s.meta }

// racyPathFor mimics the shape of the OLD, pre-OpenFor pattern: read
// which path is current, as a separate, non-atomic step from actually
// opening it. Not part of the Store interface — it exists only so
// TestStalePathRacesRename can demonstrate exactly why that pattern is
// unsafe and OpenFor isn't.
func (s *fakeStore) racyPathFor(i int) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.done {
		return s.finalPath
	}
	return s.path
}

// OpenFor mirrors transfer.FileStore.OpenFor: the done-check and the
// open happen under the same lock setDone uses for its rename+done flip.
func (s *fakeStore) OpenFor(i int) (*os.File, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.done {
		return os.Open(s.finalPath)
	}
	return os.Open(s.path)
}

// Available is the non-blocking probe matching WaitForByte's
// front-growing region.
func (s *fakeStore) Available(i int, pos int64) (int64, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.done {
		return s.meta.Size, true
	}
	if pos < s.avail {
		return s.avail, true
	}
	return -1, false
}

// WaitForByte blocks until byte pos is available (this fake models a
// single front-growing region) and returns the contiguous end.
func (s *fakeStore) WaitForByte(ctx context.Context, i int, pos int64) (int64, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	stop := context.AfterFunc(ctx, func() { s.mu.Lock(); s.cond.Broadcast(); s.mu.Unlock() })
	defer stop()
	for {
		if s.err != nil {
			return 0, s.done, s.err
		}
		if s.done {
			return s.meta.Size, true, nil
		}
		if pos < s.avail {
			return s.avail, false, nil
		}
		if ctx.Err() != nil {
			return 0, s.done, ctx.Err()
		}
		s.cond.Wait()
	}
}

func startServer(t *testing.T, store Store) *Server {
	t.Helper()
	s, err := New(store)
	if err != nil {
		t.Fatal(err)
	}
	s.Start()
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		_ = s.Close(ctx)
	})
	return s
}

func TestRangeBlockingGrowingFile(t *testing.T) {
	data := make([]byte, 512*1024)
	rand.New(rand.NewSource(1)).Read(data)
	fs := newFakeStore(t, data)
	srv := startServer(t, fs)

	// Reveal the file in chunks while the request is in flight.
	go func() {
		for n := int64(0); n < int64(len(data)); n += 64 * 1024 {
			time.Sleep(20 * time.Millisecond)
			fs.setAvail(min(n+64*1024, int64(len(data))))
		}
		fs.setDone()
	}()

	resp, err := http.Get(srv.URLs()[0])
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	got, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatalf("body mismatch: got %d bytes, want %d", len(got), len(data))
	}
}

// TestStalePathRacesRename is the deterministic regression test for the
// completion race's root cause: reading which path is current and then
// opening it are two separate steps (the old server.go pattern, and what
// racyPathFor mimics here). A rename landing in between — trivial to
// force deterministically, unlike hitting the same window through a real
// concurrent HTTP request — turns a moment-ago-valid path into ENOENT.
// OpenFor collapses the decision and the open into one locked step, so
// the exact same sequence can never observe a stale path.
func TestStalePathRacesRename(t *testing.T) {
	fs := newFakeStore(t, []byte("hello"))

	stalePath := fs.racyPathFor(0) // snapshot while still "not done"
	fs.setDone()                   // the rename lands right in the gap
	if _, err := os.Open(stalePath); err == nil {
		t.Fatal("expected the snapshotted .part path to be gone after the rename — this is the race OpenFor exists to close")
	}

	// OpenFor doesn't have a "snapshot, then act later" step to race:
	// the exact same sequence of events never produces a stale open.
	f, err := fs.OpenFor(0)
	if err != nil {
		t.Fatalf("OpenFor must never race the rename, got: %v", err)
	}
	f.Close()
}

// TestCompletionDoesNotRaceOpenFor is a behavioral (if non-deterministic)
// companion to TestStalePathRacesRename: the .part→final rename and the
// done flag used to flip in two separate, unsynchronized steps, and the
// old handler reopened the file by path on every availability run — so a
// handler resuming right as the rename landed could read a stale "not
// done", decide to open the now-renamed-away .part path, and get ENOENT,
// silently truncating the response right at the moment the file
// completes. This stalls many concurrent requests on the very last byte,
// then completes the file (triggering the rename) while all of them are
// still blocked, and asserts every single one finishes with the full,
// correct content.
func TestCompletionDoesNotRaceOpenFor(t *testing.T) {
	data := make([]byte, 256*1024)
	rand.New(rand.NewSource(7)).Read(data)
	fs := newFakeStore(t, data)
	fs.setAvail(int64(len(data)) - 1) // one byte short: every request stalls right at the end
	srv := startServer(t, fs)

	const n = 20
	var wg sync.WaitGroup
	results := make([]error, n)
	for i := range n {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			resp, err := http.Get(srv.URLs()[0])
			if err != nil {
				results[i] = err
				return
			}
			defer resp.Body.Close()
			got, err := io.ReadAll(resp.Body)
			if err != nil {
				results[i] = err
				return
			}
			if string(got) != string(data) {
				results[i] = fmt.Errorf("got %d bytes, want %d", len(got), len(data))
			}
		}(i)
	}

	time.Sleep(50 * time.Millisecond) // let every request stall on the last byte
	fs.setAvail(int64(len(data)))
	fs.setDone() // the rename lands while every request is blocked right at completion

	wg.Wait()
	for i, err := range results {
		if err != nil {
			t.Errorf("request %d: %v", i, err)
		}
	}
}

// TestCurrentReadPosTracksLongLivedResponse is the regression test for the
// frozen-playhead bug: mpv issues one long-lived "bytes=X-" request and
// reads it for minutes, so CurrentReadPos must advance as the response body
// is written, not just once when the request starts. Without that, the
// downloader's adaptive window pins at the request's start offset and
// playback guarantees a stall once it outgrows the window.
func TestCurrentReadPosTracksLongLivedResponse(t *testing.T) {
	data := make([]byte, 512*1024)
	rand.New(rand.NewSource(4)).Read(data)
	fs := newFakeStore(t, data)
	srv := startServer(t, fs)

	go func() {
		for n := int64(0); n < int64(len(data)); n += 64 * 1024 {
			time.Sleep(20 * time.Millisecond)
			fs.setAvail(min(n+64*1024, int64(len(data))))
		}
		fs.setDone()
	}()

	resp, err := http.Get(srv.URLs()[0])
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	// Poll CurrentReadPos while the single request is still streaming.
	// It must climb well past 0 before the body finishes — a frozen
	// implementation would report 0 (the request's start offset) the
	// whole time.
	var sawProgress bool
	done := make(chan struct{})
	go func() {
		defer close(done)
		if _, err := io.ReadAll(resp.Body); err != nil {
			t.Errorf("read: %v", err)
		}
	}()
	deadline := time.After(2 * time.Second)
poll:
	for {
		select {
		case <-done:
			break poll
		case <-deadline:
			break poll
		default:
			if srv.CurrentReadPos(0) > 128*1024 {
				sawProgress = true
			}
			time.Sleep(5 * time.Millisecond)
		}
	}
	<-done
	if !sawProgress {
		t.Errorf("CurrentReadPos never advanced past the request's start offset during a long-lived read")
	}
	if final := srv.CurrentReadPos(0); final != int64(len(data)) {
		t.Errorf("CurrentReadPos after completion = %d, want %d", final, len(data))
	}
}

func TestRangeWindow(t *testing.T) {
	data := make([]byte, 100*1024)
	rand.New(rand.NewSource(2)).Read(data)
	fs := newFakeStore(t, data)
	fs.setAvail(50 * 1024) // only first half available initially
	srv := startServer(t, fs)

	go func() {
		time.Sleep(50 * time.Millisecond)
		fs.setAvail(int64(len(data)))
		fs.setDone()
	}()

	req, _ := http.NewRequest("GET", srv.URLs()[0], nil)
	req.Header.Set("Range", "bytes=70000-70099") // spans into the not-yet-available half
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusPartialContent {
		t.Fatalf("status = %d, want 206", resp.StatusCode)
	}
	if cr := resp.Header.Get("Content-Range"); cr != fmt.Sprintf("bytes 70000-70099/%d", len(data)) {
		t.Errorf("Content-Range = %q", cr)
	}
	got, _ := io.ReadAll(resp.Body)
	if len(got) != 100 || string(got) != string(data[70000:70100]) {
		t.Errorf("window mismatch: %d bytes", len(got))
	}
}

func TestRangeErrorSurfaces(t *testing.T) {
	data := make([]byte, 64*1024)
	fs := newFakeStore(t, data)
	srv := startServer(t, fs)

	go func() {
		time.Sleep(30 * time.Millisecond)
		fs.setErr(fmt.Errorf("download failed"))
	}()

	resp, err := http.Get(srv.URLs()[0])
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusBadGateway {
		t.Fatalf("status = %d, want 502", resp.StatusCode)
	}
}

func TestConcurrentRangeSameFile(t *testing.T) {
	data := make([]byte, 256*1024)
	rand.New(rand.NewSource(3)).Read(data)
	fs := newFakeStore(t, data)
	fs.setAvail(int64(len(data)))
	fs.setDone()
	srv := startServer(t, fs)

	var wg sync.WaitGroup
	for _, rng := range []string{"", "bytes=0-1023", "bytes=200000-"} {
		wg.Add(1)
		go func(rng string) {
			defer wg.Done()
			req, _ := http.NewRequest("GET", srv.URLs()[0], nil)
			if rng != "" {
				req.Header.Set("Range", rng)
			}
			resp, err := http.DefaultClient.Do(req)
			if err != nil {
				t.Errorf("%s: %v", rng, err)
				return
			}
			defer resp.Body.Close()
			if _, err := io.ReadAll(resp.Body); err != nil {
				t.Errorf("%s: read: %v", rng, err)
			}
		}(rng)
	}
	wg.Wait()
}

// TestCloseWakesBlockedHandlers is the regression test for the
// goroutine-leak bug: Close used to call only http.Server.Shutdown, which
// waits for in-flight handlers to return on their own but never cancels
// their request contexts. A handler blocked in WaitByte on bytes that
// will never arrive (a download that stalled or died) would then block
// Shutdown forever and leak until process exit. Close must wake such
// handlers itself.
func TestCloseWakesBlockedHandlers(t *testing.T) {
	data := make([]byte, 1024)
	fs := newFakeStore(t, data) // avail=0 and never advanced: byte 0 blocks forever

	srv, err := New(fs)
	if err != nil {
		t.Fatal(err)
	}
	srv.Start()

	clientDone := make(chan error, 1)
	go func() {
		resp, err := http.Get(srv.URLs()[0])
		if err != nil {
			clientDone <- err
			return
		}
		defer resp.Body.Close()
		_, err = io.ReadAll(resp.Body)
		clientDone <- err
	}()
	time.Sleep(100 * time.Millisecond) // let the handler actually block in WaitByte

	closeDone := make(chan error, 1)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		defer cancel()
		closeDone <- srv.Close(ctx)
	}()

	select {
	case err := <-closeDone:
		if err != nil {
			t.Errorf("Close returned an error: %v", err)
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("Close did not return promptly — a handler blocked in WaitByte is stuck until its 3s deadline (or forever with an unbounded ctx), leaking its goroutine")
	}

	select {
	case <-clientDone:
	case <-time.After(time.Second):
		t.Fatal("client request never completed after Close returned — the handler goroutine appears to be leaked")
	}
}

// TestStallReporter checks that reading past the downloaded bytes fires
// onStall(true) and prioritize(offset), then onStall(false) once the
// bytes arrive.
func TestStallReporter(t *testing.T) {
	data := make([]byte, 200*1024)
	rand.New(rand.NewSource(5)).Read(data)
	fs := newFakeStore(t, data) // avail=0 → reading byte 0 stalls immediately

	srv, err := New(fs)
	if err != nil {
		t.Fatal(err)
	}
	var mu sync.Mutex
	var stalls []bool
	prioOff := int64(-1)
	srv.SetStallReporter(
		func(s bool) { mu.Lock(); stalls = append(stalls, s); mu.Unlock() },
		func(i int, pos int64) { mu.Lock(); prioOff = pos; mu.Unlock() },
	)
	srv.Start()
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		_ = srv.Close(ctx)
	})

	go func() {
		time.Sleep(80 * time.Millisecond) // let the read stall first
		fs.setAvail(int64(len(data)))
		fs.setDone()
	}()

	resp, err := http.Get(srv.URLs()[0])
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if got, _ := io.ReadAll(resp.Body); len(got) != len(data) {
		t.Fatalf("got %d bytes, want %d", len(got), len(data))
	}

	mu.Lock()
	defer mu.Unlock()
	if len(stalls) < 2 || !stalls[0] || stalls[len(stalls)-1] {
		t.Errorf("want a stall (true) then a clear (false), got %v", stalls)
	}
	if prioOff != 0 {
		t.Errorf("prioritize should get the blocked offset 0, got %d", prioOff)
	}
}
