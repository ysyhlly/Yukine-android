// Package streamserver exposes a downloading playlist over HTTP so mpv
// can start playing immediately. It serves each file at /{index} with
// byte-range support; a request for bytes that haven't been downloaded
// yet blocks until they arrive instead of returning EOF, so mpv treats
// it as a slow source rather than a truncated file. A blocking read is
// reported via the stall hooks (SetStallReporter) so the downloader can
// prioritize the seeked-to region and the room can pause to buffer.
package streamserver

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// readChunk bounds how many bytes one ReadAt pulls before flushing, so
// the client sees data promptly while a file is still downloading.
const readChunk = 64 * 1024

// Store is the slice of *transfer.FileStore the server needs: playlist
// shape, per-file metadata, the blocking byte-availability wait, and a
// way to open the file for reading. Kept as an interface so the server
// is testable without a live download.
type Store interface {
	Len() int
	Meta(i int) protocol.FileMeta
	// WaitForByte blocks until byte pos of file i is downloaded, then
	// returns the end of the contiguous available region containing it.
	WaitForByte(ctx context.Context, i int, pos int64) (end int64, done bool, err error)
	// Available reports, without blocking, whether byte pos of file i is
	// already on disk (and the end of its run). Used to tell a stall from
	// a hit before committing to the blocking WaitForByte.
	Available(i int, pos int64) (end int64, ok bool)
	// OpenFor opens the current backing file for index i. Deciding which
	// path to open and actually opening it must happen atomically with
	// respect to the file's .part-to-final rename on completion — a
	// separate "get the path" then "open it" (as this used to be, via a
	// PathFor(i) string method) can be caught in between and get ENOENT.
	OpenFor(i int) (*os.File, error)
}

type Server struct {
	store    Store
	ln       net.Listener
	srv      *http.Server
	playable []int // store indices exposed as playlist URLs; nil = all

	// cancel aborts baseCtx, the root every request's context derives from
	// (wired via http.Server.BaseContext). A handler blocked in WaitByte
	// waits on its own request's context, but that context is only ever
	// canceled when the client disconnects or the handler returns — an
	// http.Server.Shutdown (graceful, as Close uses) does neither, so a
	// handler stalled waiting for not-yet-downloaded bytes would otherwise
	// block Shutdown forever and leak its goroutine. Canceling baseCtx
	// propagates to every in-flight request's context immediately,
	// unblocking WaitByte so the handler returns and Shutdown completes.
	cancel context.CancelFunc

	// Seek-ahead buffering hooks (optional). onStall fires true when the
	// first read stalls waiting for not-yet-downloaded bytes and false
	// when no read is stalled anymore; prioritize asks the downloader to
	// fetch the stalled offset next. stallDepth counts concurrently
	// blocked handler goroutines (mpv opens several connections).
	onStall    func(bool)
	prioritize func(i int, pos int64)
	stallMu    sync.Mutex
	stallDepth int

	// lastServed tracks the most recent byte offset served per store
	// index. Used by the downloader to follow the playhead.
	lastServed []atomic.Int64
}

// New binds an HTTP server to an ephemeral loopback port backed by store.
func New(store Store) (*Server, error) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, fmt.Errorf("binding stream server: %w", err)
	}
	baseCtx, cancel := context.WithCancel(context.Background())
	s := &Server{store: store, ln: ln, cancel: cancel, lastServed: make([]atomic.Int64, store.Len())}
	mux := http.NewServeMux()
	mux.HandleFunc("/", s.handle)
	// ReadHeaderTimeout bounds how long a client may dribble request
	// headers (Slowloris). It's loopback-only so the exposure is small, but
	// the cap is free. No ReadTimeout/WriteTimeout: range responses are
	// deliberately long-lived (they block on not-yet-downloaded bytes), and
	// a write deadline would kill a legitimately slow stream.
	s.srv = &http.Server{Handler: mux, ReadHeaderTimeout: 10 * time.Second, BaseContext: func(net.Listener) context.Context { return baseCtx }}
	return s, nil
}

// SetStallReporter wires the seek-ahead buffering hooks. onStall is
// called with true when a read first blocks on undownloaded bytes and
// false once nothing is blocked; prioritize tells the downloader which
// offset to fetch next. Call before Start; safe to leave unset.
func (s *Server) SetStallReporter(onStall func(bool), prioritize func(i int, pos int64)) {
	s.onStall = onStall
	s.prioritize = prioritize
}

func (s *Server) Start() { go s.srv.Serve(s.ln) }

// waitByte returns the end of the received run containing pos, blocking
// if pos isn't downloaded yet. A blocking wait is a stall: it asks the
// downloader to prioritize pos and brackets the wait with stall
// accounting (paired via defer, so the error path reports correctly).
func (s *Server) waitByte(ctx context.Context, idx int, pos int64) (int64, error) {
	if end, ok := s.store.Available(idx, pos); ok {
		return end, nil
	}
	if s.prioritize != nil {
		s.prioritize(idx, pos)
	}
	s.enterStall()
	defer s.leaveStall()
	end, _, err := s.store.WaitForByte(ctx, idx, pos)
	return end, err
}

func (s *Server) enterStall() {
	s.stallMu.Lock()
	s.stallDepth++
	fire := s.stallDepth == 1 && s.onStall != nil
	s.stallMu.Unlock()
	if fire { // outside the lock: a slow reporter mustn't serialize stalls
		s.onStall(true)
	}
}

func (s *Server) leaveStall() {
	s.stallMu.Lock()
	s.stallDepth--
	fire := s.stallDepth == 0 && s.onStall != nil
	s.stallMu.Unlock()
	if fire {
		s.onStall(false)
	}
}

// URLs returns one playable URL per playlist entry, index-aligned.
// SetPlayable restricts which store indices are exposed as playlist
// URLs — used to keep subtitle sidecar files out of the mpv playlist
// while still serving them on request. Call before URLs; nil means all.
func (s *Server) SetPlayable(indices []int) { s.playable = indices }

func (s *Server) URLs() []string {
	port := s.ln.Addr().(*net.TCPAddr).Port
	idxs := s.playable
	if idxs == nil {
		idxs = make([]int, s.store.Len())
		for i := range idxs {
			idxs[i] = i
		}
	}
	urls := make([]string, len(idxs))
	for i, storeIdx := range idxs {
		urls[i] = fmt.Sprintf("http://127.0.0.1:%d/%d", port, storeIdx)
	}
	return urls
}

// Close shuts down the server. It cancels every in-flight request's
// context first so a handler blocked in WaitByte on not-yet-downloaded
// bytes wakes up and returns instead of leaking its goroutine and
// blocking Shutdown indefinitely.
func (s *Server) Close(ctx context.Context) error {
	s.cancel()
	return s.srv.Shutdown(ctx)
}

// CurrentReadPos returns the most recently served byte offset for store
// index idx. The downloader uses this as a proxy for the playhead position
// when sizing its adaptive download window.
func (s *Server) CurrentReadPos(idx int) int64 {
	if idx < 0 || idx >= len(s.lastServed) {
		return 0
	}
	return s.lastServed[idx].Load()
}

func (s *Server) handle(w http.ResponseWriter, r *http.Request) {
	idx, err := strconv.Atoi(strings.TrimPrefix(r.URL.Path, "/"))
	if err != nil || idx < 0 || idx >= s.store.Len() {
		http.NotFound(w, r)
		return
	}
	meta := s.store.Meta(idx)
	total := meta.Size

	start, end, partial, ok := parseRange(r.Header.Get("Range"), total)
	if !ok {
		w.Header().Set("Content-Range", fmt.Sprintf("bytes */%d", total))
		http.Error(w, "invalid range", http.StatusRequestedRangeNotSatisfiable)
		return
	}

	w.Header().Set("Accept-Ranges", "bytes")
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", strconv.FormatInt(end-start+1, 10))
	if partial {
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, end, total))
		w.WriteHeader(http.StatusPartialContent)
	}
	if r.Method == http.MethodHead {
		return // a probe, not a real read; don't move the tracked playhead
	}

	// mpv typically issues one long-lived "bytes=X-" request and reads it
	// for minutes, so this must be kept current as bytes are written below
	// (not just set once here) — otherwise the adaptive download window
	// pins at the request's start offset and playback guarantees a stall
	// once it outgrows the window.
	if idx < len(s.lastServed) {
		s.lastServed[idx].Store(start)
	}

	flusher, _ := w.(http.Flusher)
	ctx := r.Context()
	buf := make([]byte, readChunk)
	pos := start

	// Opened once, atomically with respect to the .part-to-final rename
	// (see OpenFor) — not once per availability run. A POSIX file
	// descriptor stays valid and keeps reading the same underlying data
	// even if the file is later renamed elsewhere, so there's no need to
	// reopen once the download completes: the same handle keeps working.
	f, err := s.store.OpenFor(idx)
	if err != nil {
		if !partial {
			http.Error(w, "download failed", http.StatusBadGateway)
		}
		return
	}
	defer f.Close()

	for pos <= end {
		avail, derr := s.waitByte(ctx, idx, pos)
		if derr != nil {
			// Nothing we can do mid-body but stop; if we've written
			// nothing yet and headers allow it, signal an error.
			if pos == start && !partial {
				http.Error(w, "download failed", http.StatusBadGateway)
			}
			return
		}
		readEnd := end + 1
		if avail < readEnd {
			readEnd = avail
		}
		for pos < readEnd {
			n := int64(len(buf))
			if readEnd-pos < n {
				n = readEnd - pos
			}
			m, rerr := f.ReadAt(buf[:n], pos)
			if m > 0 {
				if _, werr := w.Write(buf[:m]); werr != nil {
					return
				}
				pos += int64(m)
				if idx < len(s.lastServed) {
					s.lastServed[idx].Store(pos)
				}
				if flusher != nil {
					flusher.Flush()
				}
			}
			if rerr != nil {
				if errors.Is(rerr, io.EOF) {
					break // wait for more bytes
				}
				return
			}
		}
	}
}

// parseRange handles a single "bytes=start-[end]" range against a known
// total size. With no header it returns the whole file (partial=false).
func parseRange(header string, total int64) (start, end int64, partial, ok bool) {
	if header == "" {
		return 0, total - 1, false, true
	}
	spec, found := strings.CutPrefix(header, "bytes=")
	if !found || strings.Contains(spec, ",") {
		return 0, 0, false, false // multi-range unsupported
	}
	lo, hi, found := strings.Cut(spec, "-")
	if !found {
		return 0, 0, false, false
	}
	if lo == "" {
		// suffix range: last N bytes
		n, err := strconv.ParseInt(hi, 10, 64)
		if err != nil || n <= 0 {
			return 0, 0, false, false
		}
		if n > total {
			n = total
		}
		return total - n, total - 1, true, true
	}
	start, err := strconv.ParseInt(lo, 10, 64)
	if err != nil || start < 0 || start >= total {
		return 0, 0, false, false
	}
	end = total - 1
	if hi != "" {
		end, err = strconv.ParseInt(hi, 10, 64)
		if err != nil || end < start {
			return 0, 0, false, false
		}
		if end >= total {
			end = total - 1
		}
	}
	return start, end, true, true
}
