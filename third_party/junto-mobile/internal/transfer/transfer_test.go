package transfer

import (
	"context"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"math"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/pion/webrtc/v4"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// chanSignaler wires two transfer endpoints together in-process.
type chanSignaler struct {
	self string
	out  map[string]*chanSignaler // peers by id
	in   chan AddressedSignal
}

func newSignalerPair(a, b string) (*chanSignaler, *chanSignaler) {
	sa := &chanSignaler{self: a, in: make(chan AddressedSignal, 8)}
	sb := &chanSignaler{self: b, in: make(chan AddressedSignal, 8)}
	sa.out = map[string]*chanSignaler{b: sb}
	sb.out = map[string]*chanSignaler{a: sa}
	return sa, sb
}

func (s *chanSignaler) SendSignal(ctx context.Context, to string, sig protocol.Signal) error {
	s.out[to].in <- AddressedSignal{From: s.self, Sig: sig}
	return nil
}

func (s *chanSignaler) Signals() <-chan AddressedSignal { return s.in }

// capturePrintf collects formatted log lines for assertions.
func capturePrintf() (func(string, ...any), func() []string) {
	var mu sync.Mutex
	var lines []string
	pf := func(f string, a ...any) {
		mu.Lock()
		lines = append(lines, fmt.Sprintf(f, a...))
		mu.Unlock()
	}
	get := func() []string {
		mu.Lock()
		defer mu.Unlock()
		return append([]string(nil), lines...)
	}
	return pf, get
}

func noICE() ICEConfig { return ICEConfig{} } // loopback host candidates need no STUN

func TestSanitizeName(t *testing.T) {
	cases := map[string]string{
		"movie.mkv":          "movie.mkv",
		"../../etc/passwd":   "passwd",
		`..\..\evil.exe`:     "evil.exe",
		"/abs/path/file.mp4": "file.mp4",
		"":                   "download",
		"..":                 "download",
		".":                  "download",
	}
	for in, want := range cases {
		if got := sanitizeName(in); got != want {
			t.Errorf("sanitizeName(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestFileTransferLoopback(t *testing.T) {
	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, 1<<20+12345) // >1 MiB, not chunk-aligned
	rand.New(rand.NewSource(1)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: int64(len(data)), SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	outDir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	serveErr := make(chan error, 1)
	go func() {
		serveErr <- ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0)
	}()

	path, err := FetchFile(ctx, joinSig, "host", meta, outDir, noICE(), t.Logf)
	if err != nil {
		t.Fatalf("FetchFile: %v", err)
	}
	if err := <-serveErr; err != nil {
		t.Fatalf("ServeFile: %v", err)
	}

	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("received file differs from source")
	}
	if filepath.Base(path) != "media.bin" {
		t.Errorf("unexpected file name %q", path)
	}
	if _, err := os.Stat(path + ".part"); !os.IsNotExist(err) {
		t.Error(".part file left behind")
	}
}

// TestServeFileReportsUploadRate checks ServeFile's reportRate hook — the
// upload-side mirror of the download side's throughput EMA — fires at
// least once during a real transfer. A light, deterministic throttle
// (wrapServeReader) guarantees the transfer spans at least one
// progressInterval window regardless of how fast real loopback happens to
// be on the machine running the test — an unthrottled small transfer can
// legitimately complete in well under progressInterval, which would make
// this test flaky rather than proving anything.
func TestServeFileReportsUploadRate(t *testing.T) {
	savedInterval := progressInterval
	savedWrap := wrapServeReader
	progressInterval = 5 * time.Millisecond
	wrapServeReader = func(f *os.File) io.ReadSeeker {
		return &slowReader{r: f, perByte: time.Microsecond, maxChunk: 4096} // ~1 MB/s
	}
	defer func() { progressInterval = savedInterval; wrapServeReader = savedWrap }()

	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, 512<<10) // 512 KiB, enough for several progressInterval ticks at ~1 MB/s
	rand.New(rand.NewSource(2)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: int64(len(data)), SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	outDir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	var mu sync.Mutex
	var rates []float64
	reportRate := func(r float64) {
		mu.Lock()
		rates = append(rates, r)
		mu.Unlock()
	}

	serveErr := make(chan error, 1)
	go func() {
		serveErr <- ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, reportRate, nil, nil, 0)
	}()
	if _, err := FetchFile(ctx, joinSig, "host", meta, outDir, noICE(), t.Logf); err != nil {
		t.Fatalf("FetchFile: %v", err)
	}
	if err := <-serveErr; err != nil {
		t.Fatalf("ServeFile: %v", err)
	}

	mu.Lock()
	defer mu.Unlock()
	if len(rates) == 0 {
		t.Fatal("expected at least one upload-rate report during the transfer")
	}
	for _, r := range rates {
		if r <= 0 {
			t.Errorf("reported upload rate should be positive, got %v", r)
		}
	}
}

// slowReader wraps a real io.ReadSeeker and throttles it to a fixed,
// reproducible rate — used to prove the upload EMA genuinely reacts to a
// slow upload, not just always reporting fast-loopback numbers.
type slowReader struct {
	r        io.ReadSeeker
	perByte  time.Duration
	maxChunk int
}

func (s *slowReader) Read(p []byte) (int, error) {
	if len(p) > s.maxChunk {
		p = p[:s.maxChunk]
	}
	n, err := s.r.Read(p)
	if n > 0 {
		time.Sleep(s.perByte * time.Duration(n))
	}
	return n, err
}

func (s *slowReader) Seek(offset int64, whence int) (int64, error) {
	return s.r.Seek(offset, whence)
}

// TestUploadRateEMAReflectsSlowThroughput forces a known, reproducible low
// upload rate (via wrapServeReader) and checks the reported EMA converges
// to something consistent with it — well under a fast-loopback sanity
// ceiling — proving it reacts to real speed rather than always reporting
// the host machine's real (fast) disk/loopback throughput.
func TestUploadRateEMAReflectsSlowThroughput(t *testing.T) {
	savedInterval := progressInterval
	savedWrap := wrapServeReader
	progressInterval = 15 * time.Millisecond
	defer func() { progressInterval = savedInterval; wrapServeReader = savedWrap }()

	const targetBytesPerSec = 200_000 // ~200 KB/s, deliberately slow
	wrapServeReader = func(f *os.File) io.ReadSeeker {
		return &slowReader{r: f, perByte: time.Second / targetBytesPerSec, maxChunk: 4096}
	}

	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, 100<<10) // 100 KiB -> ~0.5s at the throttled rate
	rand.New(rand.NewSource(3)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: int64(len(data)), SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	outDir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	var mu sync.Mutex
	var lastRate float64
	reportRate := func(r float64) {
		mu.Lock()
		lastRate = r
		mu.Unlock()
	}

	serveErr := make(chan error, 1)
	go func() {
		serveErr <- ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, reportRate, nil, nil, 0)
	}()
	if _, err := FetchFile(ctx, joinSig, "host", meta, outDir, noICE(), t.Logf); err != nil {
		t.Fatalf("FetchFile: %v", err)
	}
	if err := <-serveErr; err != nil {
		t.Fatalf("ServeFile: %v", err)
	}

	mu.Lock()
	got := lastRate
	mu.Unlock()
	if got <= 0 {
		t.Fatal("expected a positive reported upload rate")
	}
	// Generous tolerance: EMA smoothing and scheduling jitter mean an exact
	// match isn't realistic, but a genuinely throttled ~200 KB/s upload
	// must land far below an unthrottled loopback's speed.
	const fastLoopbackCeiling = 5_000_000 // 5 MB/s
	if got > fastLoopbackCeiling {
		t.Errorf("reported rate %v looks like unthrottled loopback speed, not the forced ~%v B/s", got, targetBytesPerSec)
	}
}

// TestFetchCorruptHashWarns: streaming means playback may already have
// happened by the time the hash is checked, so a mismatch warns loudly
// and keeps the file rather than failing.
func TestFetchCorruptHashWarns(t *testing.T) {
	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, 64*1024)
	rand.New(rand.NewSource(2)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	meta := protocol.FileMeta{Name: "media.bin", Size: int64(len(data)), SHA256: "00"} // wrong hash

	hostSig, joinSig := newSignalerPair("host", "joiner")
	outDir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()

	pf, lines := capturePrintf()
	path, err := FetchFile(ctx, joinSig, "host", meta, outDir, noICE(), pf)
	if err != nil {
		t.Fatalf("FetchFile should keep the file on mismatch, got error: %v", err)
	}
	if _, err := os.Stat(path); err != nil {
		t.Errorf("file should be kept after hash mismatch: %v", err)
	}
	warned := false
	for _, l := range lines() {
		if strings.Contains(l, "sha256") || strings.Contains(strings.ToLower(l), "corrupt") {
			warned = true
		}
	}
	if !warned {
		t.Errorf("expected a corruption warning, got: %v", lines())
	}
}

// TestResumeMidTransfer drops the connection partway through and checks
// the download resumes from the bytes already on disk and verifies.
func TestResumeMidTransfer(t *testing.T) {
	src := filepath.Join(t.TempDir(), "media.bin")
	const size = 2 << 20 // 2 MiB
	data := make([]byte, size)
	rand.New(rand.NewSource(3)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	outDir := t.TempDir()
	store, err := NewFileStore(outDir, []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()

	var attempts int
	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			attempts++
			a := attempts
			// The host spawns one persistent serve per (re)connect, exactly
			// as the real app does; byte ranges flow on the data channel.
			hctx, hcancel := context.WithCancel(context.Background())
			go func() {
				_ = ServeFile(hctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0)
			}()
			if a == 1 {
				// Drop the first attempt once a quarter has arrived.
				go func() {
					for store.files[0].covered() < size/4 {
						time.Sleep(time.Millisecond)
					}
					hcancel() // closes the host PC → joiner sees the drop
				}()
			} else {
				context.AfterFunc(c, hcancel)
			}
			return nil
		}, t.Logf)

	if err := dl.runFile(ctx, 0); err != nil {
		t.Fatalf("runFile: %v", err)
	}
	if attempts < 2 {
		t.Fatalf("expected a resume (>=2 attempts), got %d", attempts)
	}
	got, err := os.ReadFile(store.PathFor(0))
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("resumed file differs from source")
	}
}

// TestCrossSessionResume downloads partway, simulates the joiner
// quitting (ctx cancel), then builds a fresh FileStore over the same
// directory and checks it resumes from the .part + sidecar rather than
// starting over.
func TestCrossSessionResume(t *testing.T) {
	const size = 512 << 10
	data := make([]byte, size)
	rand.New(rand.NewSource(11)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}
	outDir := t.TempDir()

	// A host that serves any requested range, for either session.
	serve := func(hostSig *chanSignaler) func(context.Context, string, int, int64, int64) error {
		return func(c context.Context, _ string, i int, _, _ int64) error {
			go func() {
				_ = ServeFile(context.Background(), hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0)
			}()
			return nil
		}
	}

	// Session 1: download a quarter, then quit.
	hostSig1, joinSig1 := newSignalerPair("host", "joiner")
	store1, err := NewFileStore(outDir, []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	if store1.files[0].resumed {
		t.Fatal("a fresh download should not be marked resumed")
	}
	dlCtx, quit := context.WithCancel(context.Background())
	dl1 := NewDownloader(joinSig1, "host", store1, noICE(), serve(hostSig1), t.Logf)
	go func() {
		for store1.files[0].covered() < size/4 {
			time.Sleep(time.Millisecond)
		}
		quit() // simulate the user quitting mid-download
	}()
	if err := dl1.runFile(dlCtx, 0); err == nil {
		t.Fatal("expected runFile to stop on quit")
	}
	partial := store1.files[0].covered()
	if partial == 0 || partial == size {
		t.Fatalf("expected a partial download, got %d of %d", partial, size)
	}
	if _, err := os.Stat(outDir + "/media.bin.part.junto"); err != nil {
		t.Errorf("resume sidecar should be kept on quit: %v", err)
	}

	// Session 2: a fresh store over the same directory must resume.
	hostSig2, joinSig2 := newSignalerPair("host", "joiner")
	store2, err := NewFileStore(outDir, []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	if !store2.files[0].resumed {
		t.Fatal("second session should resume from the prior .part")
	}
	if got := store2.files[0].covered(); got != partial {
		t.Errorf("resumed coverage = %d, want the prior %d", got, partial)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	dl2 := NewDownloader(joinSig2, "host", store2, noICE(), serve(hostSig2), t.Logf)
	if err := dl2.runFile(ctx, 0); err != nil {
		t.Fatalf("resumed runFile: %v", err)
	}
	got, err := os.ReadFile(store2.PathFor(0))
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("resumed file differs from source")
	}
	if _, err := os.Stat(outDir + "/media.bin.part.junto"); !os.IsNotExist(err) {
		t.Error("sidecar should be removed once the download completes")
	}
}

// TestLoadProgressHandlesCorruptSidecar covers a `.part.junto` sidecar
// that's been damaged (truncated write, disk corruption, hand-edited) or
// crafted with out-of-range spans — it must never panic, and any
// sidecar loadProgress can't trust must be treated as absent (return
// false) rather than resuming from bogus data.
func TestLoadProgressHandlesCorruptSidecar(t *testing.T) {
	const size = 100
	meta := protocol.FileMeta{Size: size, SHA256: "deadbeef"}

	newEntry := func(t *testing.T) *fileEntry {
		dir := t.TempDir()
		e := &fileEntry{meta: meta, partPath: filepath.Join(dir, "f.part"), metaPath: filepath.Join(dir, "f.part.junto")}
		e.cond = sync.NewCond(&e.mu)
		if err := os.WriteFile(e.partPath, make([]byte, size), 0o644); err != nil {
			t.Fatal(err)
		}
		return e
	}

	cases := []struct {
		name    string
		sidecar string // written verbatim to metaPath; "" means no sidecar written
	}{
		{"missing sidecar", ""},
		{"empty sidecar", ""},
		{"truncated json", `{"size": 100, "sha256": "deadbeef", "spans": [[0, 5`},
		{"garbage bytes", "\x00\x01\xff not json at all"},
		{"wrong size", `{"size": 999, "sha256": "deadbeef", "spans": [[0, 100]]}`},
		{"wrong hash", `{"size": 100, "sha256": "wrong", "spans": [[0, 100]]}`},
		{"negative span start", `{"size": 100, "sha256": "deadbeef", "spans": [[-50, 100]]}`},
		{"span past file end", `{"size": 100, "sha256": "deadbeef", "spans": [[0, 999999]]}`},
		{"inverted span", `{"size": 100, "sha256": "deadbeef", "spans": [[50, 10]]}`},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			e := newEntry(t)
			if tc.name != "missing sidecar" {
				if err := os.WriteFile(e.metaPath, []byte(tc.sidecar), 0o644); err != nil {
					t.Fatal(err)
				}
			}

			resumed := e.loadProgress() // must not panic on any of the above

			if resumed {
				t.Errorf("a corrupt or invalid sidecar must never resume, got resumed=true with have=%+v", e.have)
			}
			if len(e.have) != 0 {
				t.Errorf("a corrupt or invalid sidecar must not populate any received spans, got %+v", e.have)
			}
		})
	}
}

// TestLoadProgressAcceptsValidSidecarWithSomeInvalidSpans checks that a
// sidecar mixing legitimate spans with out-of-range ones (e.g. from an
// older build with a since-fixed bounds bug) keeps the valid spans
// instead of discarding the whole resume on account of the bad ones.
func TestLoadProgressAcceptsValidSidecarWithSomeInvalidSpans(t *testing.T) {
	const size = 100
	dir := t.TempDir()
	meta := protocol.FileMeta{Size: size, SHA256: "deadbeef"}
	e := &fileEntry{meta: meta, partPath: filepath.Join(dir, "f.part"), metaPath: filepath.Join(dir, "f.part.junto")}
	e.cond = sync.NewCond(&e.mu)
	if err := os.WriteFile(e.partPath, make([]byte, size), 0o644); err != nil {
		t.Fatal(err)
	}
	sidecar := `{"size": 100, "sha256": "deadbeef", "spans": [[0, 10], [-5, 20], [50, 999999], [40, 60]]}`
	if err := os.WriteFile(e.metaPath, []byte(sidecar), 0o644); err != nil {
		t.Fatal(err)
	}

	if !e.loadProgress() {
		t.Fatal("expected the valid spans to be resumed")
	}
	if got := e.covered(); got != 30 { // [0,10) and [40,60) == 10 + 20
		t.Errorf("covered = %d, want 30 (only the two valid spans)", got)
	}
}

// TestTailPrefetch verifies the downloader fetches the file's tail
// (where media indexes live) before the body — observed through the
// progress feed, which flags the tail range — and reassembles a correct
// file. The persistent connection negotiates ranges on the data channel,
// so order is asserted via the IsTail progress flag rather than per-
// segment relay requests.
func TestTailPrefetch(t *testing.T) {
	saved, savedInt := tailPrefetch, progressInterval
	tailPrefetch = 64 << 10 // 64 KiB → forces a tail + body split
	progressInterval = 0    // observe every chunk
	defer func() { tailPrefetch, progressInterval = saved, savedInt }()

	const size = 256 << 10 // 256 KiB → tailStart = 192 KiB
	data := make([]byte, size)
	rand.New(rand.NewSource(4)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	var mu sync.Mutex
	var updates []Progress
	dl.OnProgress(func(p Progress) {
		mu.Lock()
		updates = append(updates, p)
		mu.Unlock()
	})

	if err := dl.runFile(ctx, 0); err != nil {
		t.Fatalf("runFile: %v", err)
	}

	mu.Lock()
	defer mu.Unlock()
	if len(updates) == 0 {
		t.Fatal("expected progress updates, got none")
	}
	// The tail is fetched first, so the first progress update is flagged
	// as the index/tail range, and the body (un-flagged) only follows it.
	if !updates[0].IsTail {
		t.Errorf("first progress update should be the tail prefetch, got IsTail=false")
	}
	firstBody := -1
	for i, p := range updates {
		if !p.IsTail {
			firstBody = i
			break
		}
	}
	if firstBody < 0 {
		t.Errorf("expected body (non-tail) progress updates to follow the tail")
	} else {
		for i := 0; i < firstBody; i++ {
			if !updates[i].IsTail {
				t.Errorf("update %d before the body should be a tail update", i)
			}
		}
	}
	got, err := os.ReadFile(store.PathFor(0))
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("reassembled file differs from source")
	}
}

// TestDownloadProgress checks the live progress feed: across the tail
// and body segments, Received climbs monotonically from above zero to
// exactly the file size, Total is always the size, and the tail segment
// is flagged.
func TestDownloadProgress(t *testing.T) {
	saved, savedInt := tailPrefetch, progressInterval
	tailPrefetch = 64 << 10 // 64 KiB → forces a tail + body split
	progressInterval = 0    // observe every chunk
	defer func() { tailPrefetch, progressInterval = saved, savedInt }()

	const size = 256 << 10
	data := make([]byte, size)
	rand.New(rand.NewSource(7)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	var mu sync.Mutex
	var updates []Progress
	dl.OnProgress(func(p Progress) {
		mu.Lock()
		updates = append(updates, p)
		mu.Unlock()
	})

	if err := dl.runFile(ctx, 0); err != nil {
		t.Fatalf("runFile: %v", err)
	}

	mu.Lock()
	defer mu.Unlock()
	if len(updates) == 0 {
		t.Fatal("expected progress updates, got none")
	}
	var last int64
	sawTail, sawBody := false, false
	for i, p := range updates {
		if p.Total != size {
			t.Errorf("update %d: Total = %d, want %d", i, p.Total, size)
		}
		if p.Received < last {
			t.Errorf("update %d: Received went backwards: %d after %d", i, p.Received, last)
		}
		if p.Received <= 0 || p.Received > size {
			t.Errorf("update %d: Received %d out of range (0, %d]", i, p.Received, size)
		}
		last = p.Received
		if p.IsTail {
			sawTail = true
		} else {
			sawBody = true
		}
	}
	if !sawTail || !sawBody {
		t.Errorf("expected both tail and body updates, got tail=%v body=%v", sawTail, sawBody)
	}
	if last != size {
		t.Errorf("final Received = %d, want %d", last, size)
	}
}

// TestSeekRedirectMidFlight exercises the live redirect path: a seek
// (Prioritize) lands while a range is actively streaming, so the
// in-flight range is preempted on both ends and the host restarts from
// the new offset — repeatedly — without the connection being torn down.
// The file must still reassemble correctly.
func TestSeekRedirectMidFlight(t *testing.T) {
	saved := tailPrefetch
	tailPrefetch = 1 << 62 // skip the index prefetch: body is one big gap
	defer func() { tailPrefetch = saved }()

	const size = 4 << 20 // large enough to stream over many chunks
	data := make([]byte, size)
	rand.New(rand.NewSource(21)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	done := make(chan error, 1)
	go func() { done <- dl.runFile(ctx, 0) }()

	// While bytes are flowing, jump the priority around a few times. Each
	// distinct offset redirects the active stream mid-range.
	offsets := []int64{3 << 20, 1 << 20, 2 << 20, 512 << 10}
	next := 0
	for {
		select {
		case err := <-done:
			if err != nil {
				t.Fatalf("runFile: %v", err)
			}
			got, rerr := os.ReadFile(store.PathFor(0))
			if rerr != nil {
				t.Fatal(rerr)
			}
			if string(got) != string(data) {
				t.Fatal("reassembled file differs from source after mid-flight seeks")
			}
			return
		default:
			if c := store.files[0].covered(); c > 0 && c < size && next < len(offsets) {
				dl.Prioritize(0, offsets[next])
				next++
			}
			time.Sleep(time.Millisecond)
		}
	}
}

// mp4box builds a 32-bit-size mp4 box: [size][type][payload].
func mp4box(typ string, payload []byte) []byte {
	b := make([]byte, 8+len(payload))
	binary.BigEndian.PutUint32(b[0:4], uint32(8+len(payload)))
	copy(b[4:8], typ)
	copy(b[8:], payload)
	return b
}

// mp4box64 builds a box using the 64-bit largesize form (size field 1).
func mp4box64(typ string, payload []byte) []byte {
	b := make([]byte, 16+len(payload))
	binary.BigEndian.PutUint32(b[0:4], 1)
	copy(b[4:8], typ)
	binary.BigEndian.PutUint64(b[8:16], uint64(16+len(payload)))
	copy(b[16:], payload)
	return b
}

// TestFindMoovBox checks the mp4 box walk pins the moov atom wherever it
// sits, handles the 64-bit size form, and declines (ok=false) on
// non-mp4, moov-less, and malformed inputs — all without a connection.
func TestFindMoovBox(t *testing.T) {
	ftyp := mp4box("ftyp", []byte("isom\x00\x00\x02\x00"))
	mdat := mp4box("mdat", make([]byte, 4096))
	moov := mp4box("moov", make([]byte, 512))

	join := func(parts ...[]byte) []byte {
		var out []byte
		for _, p := range parts {
			out = append(out, p...)
		}
		return out
	}
	reader := func(data []byte) func(off, n int64) ([]byte, error) {
		return func(off, n int64) ([]byte, error) { return data[off : off+n], nil }
	}

	t.Run("moov at end (non-faststart)", func(t *testing.T) {
		data := join(ftyp, mdat, moov)
		off, length, ok, err := findMoovBox(int64(len(data)), reader(data))
		if err != nil || !ok || off != int64(len(ftyp)+len(mdat)) || length != int64(len(moov)) {
			t.Fatalf("got off=%d len=%d ok=%v err=%v; want off=%d len=%d", off, length, ok, err, len(ftyp)+len(mdat), len(moov))
		}
	})
	t.Run("moov near front (faststart)", func(t *testing.T) {
		data := join(ftyp, moov, mdat)
		off, length, ok, _ := findMoovBox(int64(len(data)), reader(data))
		if !ok || off != int64(len(ftyp)) || length != int64(len(moov)) {
			t.Fatalf("got off=%d len=%d ok=%v; want off=%d len=%d", off, length, ok, len(ftyp), len(moov))
		}
	})
	t.Run("64-bit mdat largesize", func(t *testing.T) {
		bigMdat := mp4box64("mdat", make([]byte, 8192))
		data := join(ftyp, bigMdat, moov)
		off, length, ok, _ := findMoovBox(int64(len(data)), reader(data))
		if !ok || off != int64(len(ftyp)+len(bigMdat)) || length != int64(len(moov)) {
			t.Fatalf("got off=%d len=%d ok=%v; want off=%d len=%d", off, length, ok, len(ftyp)+len(bigMdat), len(moov))
		}
	})
	t.Run("not mp4 (no ftyp)", func(t *testing.T) {
		data := join(mp4box("free", make([]byte, 64)), moov)
		if _, _, ok, _ := findMoovBox(int64(len(data)), reader(data)); ok {
			t.Fatal("a non-ftyp first box should not be treated as mp4")
		}
	})
	t.Run("no moov", func(t *testing.T) {
		data := join(ftyp, mdat)
		if _, _, ok, _ := findMoovBox(int64(len(data)), reader(data)); ok {
			t.Fatal("should report no moov when none is present")
		}
	})
	t.Run("malformed oversized box", func(t *testing.T) {
		bad := mp4box("mdat", nil)
		binary.BigEndian.PutUint32(bad[0:4], 1<<30) // claims a size past EOF
		data := join(ftyp, bad)
		if _, _, ok, _ := findMoovBox(int64(len(data)), reader(data)); ok {
			t.Fatal("an oversized box should bail (ok=false), not panic")
		}
	})
	t.Run("hostile 64-bit largesize overflows pos+boxLen", func(t *testing.T) {
		// A largesize near math.MaxInt64 makes pos+boxLen wrap negative, so
		// a bounds check written as "pos+boxLen > size" would wrongly pass
		// and the walk would advance pos by a huge, wrapped amount instead
		// of bailing to the tail heuristic. bad+"size-pos" comparison must
		// catch this without ever overflowing.
		bad := mp4box64("mdat", nil)
		// pos (16, right after ftyp) + this boxLen overflows int64 and
		// wraps negative — a boxLen merely "large but not wrap-inducing"
		// wouldn't exercise the bug, since pos+boxLen would still exceed
		// size without overflowing.
		binary.BigEndian.PutUint64(bad[8:16], uint64(math.MaxInt64-10))
		data := join(ftyp, bad)
		if _, _, ok, _ := findMoovBox(int64(len(data)), reader(data)); ok {
			t.Fatal("a hostile 64-bit largesize should bail (ok=false), not overflow and misparse")
		}
	})
}

// FuzzFindMoovBox checks findMoovBox never panics on arbitrary
// remote-supplied box headers — the box walk's size arithmetic
// (largesize overflow, malformed lengths) runs on bytes read live over
// the data channel from whichever peer is hosting, so it must degrade to
// ok=false on garbage input, never crash the downloader reading it. The
// reader here rejects any out-of-range request instead of slicing (which
// would panic in the harness itself, not the code under test), so a
// regression that requests an invalid range surfaces as a normal error
// return.
func FuzzFindMoovBox(f *testing.F) {
	ftyp := mp4box("ftyp", []byte("isom\x00\x00\x02\x00"))
	mdat := mp4box("mdat", make([]byte, 64))
	moov := mp4box("moov", make([]byte, 32))
	f.Add(append(append(append([]byte{}, ftyp...), mdat...), moov...))
	f.Add([]byte{})
	f.Add([]byte("not an mp4 at all"))

	bigLen := mp4box("mdat", nil)
	binary.BigEndian.PutUint32(bigLen[0:4], 1<<30)
	f.Add(append(append([]byte{}, ftyp...), bigLen...))

	hostile := mp4box64("mdat", nil)
	binary.BigEndian.PutUint64(hostile[8:16], uint64(math.MaxInt64-10))
	f.Add(append(append([]byte{}, ftyp...), hostile...))

	f.Fuzz(func(t *testing.T, data []byte) {
		size := int64(len(data))
		readHeader := func(off, n int64) ([]byte, error) {
			if off < 0 || n < 0 || off > size-n {
				return nil, fmt.Errorf("out of range: off=%d n=%d size=%d", off, n, size)
			}
			return data[off : off+n], nil
		}
		_, _, _, _ = findMoovBox(size, readHeader)
	})
}

// TestMoovPrefetchDownload streams a synthetic non-faststart mp4 over the
// real persistent connection: the moov walk reads a few headers, the
// index prefetch pulls the moov, then the body fills in — and the file
// must reassemble byte-for-byte.
func TestMoovPrefetchDownload(t *testing.T) {
	saved := tailPrefetch
	tailPrefetch = 4 << 10 // small, so size > tailPrefetch triggers a prefetch
	defer func() { tailPrefetch = saved }()

	mdatPayload := make([]byte, 128<<10)
	moovPayload := make([]byte, 6<<10)
	rand.New(rand.NewSource(31)).Read(mdatPayload)
	rand.New(rand.NewSource(32)).Read(moovPayload)
	var data []byte
	data = append(data, mp4box("ftyp", []byte("isom\x00\x00\x02\x00"))...)
	data = append(data, mp4box("mdat", mdatPayload)...)
	data = append(data, mp4box("moov", moovPayload)...)

	src := filepath.Join(t.TempDir(), "movie.mp4")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "movie.mp4", Size: int64(len(data)), SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	if err := dl.runFile(ctx, 0); err != nil {
		t.Fatalf("runFile: %v", err)
	}
	got, err := os.ReadFile(store.PathFor(0))
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("reassembled mp4 differs from source")
	}
}

func TestICEConfigTURN(t *testing.T) {
	cfg := ICEConfig{}.WithTURN("turn:relay.example:3478", "user", "pass")
	if len(cfg.Servers) != 1 {
		t.Fatalf("want 1 server, got %d", len(cfg.Servers))
	}
	s := cfg.Servers[0]
	if s.URLs[0] != "turn:relay.example:3478" || s.Username != "user" || s.Credential != "pass" {
		t.Errorf("TURN server not configured correctly: %+v", s)
	}
	if n := len(ICEConfig{}.WithTURN("", "", "").Servers); n != 0 {
		t.Errorf("empty TURN url should add nothing, got %d servers", n)
	}
	// DefaultICEConfig snapshots the package STUN list.
	if len(DefaultICEConfig().Servers) != len(iceServers) {
		t.Error("DefaultICEConfig should mirror the default STUN list")
	}
}

// TestFallbackRelay covers the TURN-fallback plumbing: relay detection,
// fallback extension (empty by default, injectable when a built-in
// relay exists), and NAT-failure classification.
func TestFallbackRelay(t *testing.T) {
	if DefaultICEConfig().HasRelay() {
		t.Error("default (STUN-only) config should have no relay")
	}
	// Out of the box there is no built-in relay: the fallback is a no-op.
	if got := DefaultICEConfig().WithFallbackRelay(); got.HasRelay() {
		t.Error("with no FallbackRelays configured, WithFallbackRelay should add nothing")
	}
	// With a relay injected (how a future built-in relay slots in), the
	// fallback extends the config.
	saved := getFallbackRelays()
	SetFallbackRelays([]webrtc.ICEServer{{URLs: []string{"turn:relay.example:3478"}, Username: "u", Credential: "p"}})
	defer SetFallbackRelays(saved)
	if !DefaultICEConfig().WithFallbackRelay().HasRelay() {
		t.Error("WithFallbackRelay should append the configured fallback relays")
	}
	if !(ICEConfig{}).WithTURN("turn:relay.example:3478", "u", "p").HasRelay() {
		t.Error("an explicit -turn should count as a relay")
	}

	nat := natError{fmt.Errorf("hole punching failed")}
	if !isNATFailure(nat) {
		t.Error("natError should classify as NAT failure")
	}
	if !isNATFailure(fmt.Errorf("wrapped: %w", nat)) {
		t.Error("wrapped natError should still classify")
	}
	if isNATFailure(fmt.Errorf("some other error")) {
		t.Error("ordinary errors must not classify as NAT failure")
	}
	if isNATFailure(resumableError{fmt.Errorf("drop")}) {
		t.Error("resumable drops must not classify as NAT failure")
	}
}

// TestIntervalSet exercises the span set: disjoint inserts, gap
// bridging, overlap absorption, availability and gap queries.
func TestIntervalSet(t *testing.T) {
	e := &fileEntry{meta: protocol.FileMeta{Size: 100}}
	e.cond = sync.NewCond(&e.mu)
	add := func(lo, hi int64) { e.mu.Lock(); e.addLocked(lo, hi); e.mu.Unlock() }
	endOf := func(pos int64) int64 { e.mu.Lock(); defer e.mu.Unlock(); return e.endOfRunLocked(pos) }
	gap := func(from int64) (int64, int64, bool) { e.mu.Lock(); defer e.mu.Unlock(); return e.nextGapLocked(from) }

	add(0, 10)
	add(20, 30)
	if got := e.covered(); got != 20 {
		t.Fatalf("covered=%d want 20", got)
	}
	if end := endOf(5); end != 10 {
		t.Errorf("endOfRun(5)=%d want 10", end)
	}
	if end := endOf(15); end != -1 {
		t.Errorf("endOfRun(15)=%d want -1 (in the gap)", end)
	}
	if lo, hi, ok := gap(0); !ok || lo != 10 || hi != 20 {
		t.Errorf("nextGap(0)=%d,%d,%v want 10,20,true", lo, hi, ok)
	}

	add(10, 20) // bridge the gap → one span [0,30)
	if len(e.have) != 1 {
		t.Errorf("want 1 merged span, got %d: %+v", len(e.have), e.have)
	}
	if end := endOf(5); end != 30 {
		t.Errorf("merged run end=%d want 30", end)
	}

	add(25, 100) // overlaps [20,30), extends to 100
	if got := e.covered(); got != 100 {
		t.Errorf("covered=%d want 100", got)
	}
	if _, _, ok := gap(0); ok {
		t.Error("expected no gap once fully covered")
	}
}

// TestNewFileStoreDeduplicatesBasenames is the regression test for the
// path-collision bug: two playlist entries at different source paths that
// sanitize to the same basename (e.g. "dir1/movie.mp4" and
// "dir2/movie.mp4") used to be assigned the exact same .part/sidecar
// paths, so the two downloads would clobber each other's bytes and
// progress. It also covers the pathological case where an original name
// already claims the suffix a naive counter would assign to a duplicate.
func TestNewFileStoreDeduplicatesBasenames(t *testing.T) {
	metas := []protocol.FileMeta{
		{Name: "dir1/movie.mp4", Size: 10, SHA256: "a"},
		{Name: "dir2/movie.mp4", Size: 20, SHA256: "b"},
		{Name: "movie-1.mp4", Size: 30, SHA256: "c"}, // would collide with a naive "-1" suffix
		{Name: "dir3/movie.mp4", Size: 40, SHA256: "d"},
	}
	store, err := NewFileStore(t.TempDir(), metas)
	if err != nil {
		t.Fatal(err)
	}

	seen := make(map[string]int)
	for i, e := range store.files {
		if prior, dup := seen[e.finalPath]; dup {
			t.Fatalf("files %d and %d share finalPath %q", prior, i, e.finalPath)
		}
		seen[e.finalPath] = i
	}
	if len(seen) != len(metas) {
		t.Fatalf("want %d distinct paths, got %d: %v", len(metas), len(seen), seen)
	}
}

// TestPreBufferBytes checks the bitrate-aware pre-buffer cushion: unknown
// or implausible inputs fall back to the floor (the previous fixed 4 MB
// behavior), realistic bitrates land strictly between the floor and
// ceiling matching the bitrate × preBufferTargetSecs formula, and a
// pathologically high bitrate clamps to the ceiling rather than demanding
// an unreasonable cushion.
func TestPreBufferBytes(t *testing.T) {
	cases := []struct {
		name string
		meta protocol.FileMeta
		want func(got int64) error
	}{
		{"unknown duration", protocol.FileMeta{Size: 1 << 20, DurationSecs: 0}, func(got int64) error {
			if got != minPreBufferBytes {
				return fmt.Errorf("got %d, want floor %d", got, minPreBufferBytes)
			}
			return nil
		}},
		{"zero size", protocol.FileMeta{Size: 0, DurationSecs: 100}, func(got int64) error {
			if got != minPreBufferBytes {
				return fmt.Errorf("got %d, want floor %d", got, minPreBufferBytes)
			}
			return nil
		}},
		{"low bitrate (1.5 Mbps) stays at floor", protocol.FileMeta{Size: 187500, DurationSecs: 1}, func(got int64) error {
			if got != minPreBufferBytes {
				return fmt.Errorf("got %d, want floor %d", got, minPreBufferBytes)
			}
			return nil
		}},
		{"25 Mbps lands between floor and ceiling", protocol.FileMeta{Size: 3_125_000, DurationSecs: 1}, func(got int64) error {
			want := int64(3_125_000 * preBufferTargetSecs)
			if got != want || got <= minPreBufferBytes || got >= maxPreBufferBytes {
				return fmt.Errorf("got %d, want %d strictly between floor %d and ceiling %d", got, want, minPreBufferBytes, maxPreBufferBytes)
			}
			return nil
		}},
		{"40 Mbps lands between floor and ceiling", protocol.FileMeta{Size: 5_000_000, DurationSecs: 1}, func(got int64) error {
			want := int64(5_000_000 * preBufferTargetSecs)
			if got != want || got <= minPreBufferBytes || got >= maxPreBufferBytes {
				return fmt.Errorf("got %d, want %d strictly between floor %d and ceiling %d", got, want, minPreBufferBytes, maxPreBufferBytes)
			}
			return nil
		}},
		{"extreme bitrate clamps to ceiling", protocol.FileMeta{Size: 25_000_000, DurationSecs: 1}, func(got int64) error {
			if got != maxPreBufferBytes {
				return fmt.Errorf("got %d, want ceiling %d", got, maxPreBufferBytes)
			}
			return nil
		}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if err := c.want(PreBufferBytes(c.meta)); err != nil {
				t.Error(err)
			}
		})
	}
}

// TestWaitBufferedHonorsBitrateAwareCushion proves PreBufferBytes' output
// is what actually gates the readiness check (WaitBuffered), not just
// that the math function alone is correct: a file whose bitrate implies a
// cushion above the floor must still block just short of it, and return
// promptly once reached.
func TestWaitBufferedHonorsBitrateAwareCushion(t *testing.T) {
	meta := protocol.FileMeta{Name: "media.mp4", Size: 5_000_000, SHA256: "x", DurationSecs: 1} // 40 Mbps
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	want := PreBufferBytes(store.files[0].meta)
	if want <= minPreBufferBytes {
		t.Fatalf("test setup: want a cushion above the floor, got %d", want)
	}

	store.files[0].add(0, want-1024)
	shortCtx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()
	if err := store.WaitBuffered(shortCtx, 0, want); err == nil {
		t.Fatal("WaitBuffered should still be blocking just under the bitrate-aware cushion")
	}

	store.files[0].add(want-1024, want)
	longCtx, cancel2 := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel2()
	if err := store.WaitBuffered(longCtx, 0, want); err != nil {
		t.Fatalf("WaitBuffered should return promptly once the cushion is reached: %v", err)
	}
}

// TestBinaryFrameBeforeAtIsRejected is the regression test for the
// writePos-zero-value bug: a binary frame that arrives before any "at"
// frame has ever set writePos used to be silently written at offset 0 and
// marked received, corrupting span bookkeeping (and the file) instead of
// being rejected. This can happen with a confused or hostile host, or a
// stray leftover message on the data channel.
func TestBinaryFrameBeforeAtIsRejected(t *testing.T) {
	e := &fileEntry{meta: protocol.FileMeta{Size: 100}}
	e.cond = sync.NewCond(&e.mu)

	f, err := os.CreateTemp(t.TempDir(), "part")
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	if err := f.Truncate(100); err != nil {
		t.Fatal(err)
	}

	c := &fileConn{f: f, entry: e, errCh: make(chan error, 1), printf: t.Logf}
	c.onMessage(webrtc.DataChannelMessage{Data: []byte("corrupt")})

	select {
	case err := <-c.errCh:
		if err == nil {
			t.Fatal("expected a non-nil error on errCh")
		}
	default:
		t.Fatal("expected a stray pre-\"at\" binary frame to report an error, got none")
	}
	if len(e.have) != 0 {
		t.Errorf("stray binary frame must not be recorded as a received span, got %+v", e.have)
	}
	if c.got.Load() != 0 {
		t.Errorf("stray binary frame must not count toward received bytes, got %d", c.got.Load())
	}
}

// TestAtFrameOutOfBoundsOffsetIsRejected covers a hostile or buggy host
// sending an "at" frame with an offset outside the file: a negative
// offset, or one so large that at+len(data) overflows int64 and wraps
// past the "sent past end of file" upper-bound check. Neither the "at"
// handler nor onMessage's own bounds check catches these directly — the
// safety net is os.WriteAt itself, which the Go stdlib guarantees
// rejects a negative offset (and the OS rejects an offset beyond
// filesystem limits) — so this locks in that a hostile offset still
// surfaces as a clean error on errCh instead of silently corrupting span
// bookkeeping or the file.
func TestAtFrameOutOfBoundsOffsetIsRejected(t *testing.T) {
	cases := []struct {
		name string
		off  int64
	}{
		{"negative", -100},
		{"overflow past max int64", math.MaxInt64 - 2},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			e := &fileEntry{meta: protocol.FileMeta{Size: 100}}
			e.cond = sync.NewCond(&e.mu)

			f, err := os.CreateTemp(t.TempDir(), "part")
			if err != nil {
				t.Fatal(err)
			}
			defer f.Close()
			if err := f.Truncate(100); err != nil {
				t.Fatal(err)
			}

			c := &fileConn{f: f, entry: e, errCh: make(chan error, 1), printf: t.Logf}
			c.onMessage(webrtc.DataChannelMessage{IsString: true, Data: []byte(marshalCtrl(ctrl{T: "at", Off: tc.off}))})
			c.onMessage(webrtc.DataChannelMessage{Data: []byte("hello")})

			select {
			case err := <-c.errCh:
				if err == nil {
					t.Fatal("expected a non-nil error on errCh")
				}
			default:
				t.Fatal("expected an out-of-bounds \"at\" offset to report an error, got none")
			}
			if len(e.have) != 0 {
				t.Errorf("an out-of-bounds write must not be recorded as a received span, got %+v", e.have)
			}
			if c.got.Load() != 0 {
				t.Errorf("an out-of-bounds write must not count toward received bytes, got %d", c.got.Load())
			}
		})
	}
}

// TestNextFetchGapPriority verifies the scheduling decision a seek
// drives: with a priority set on a not-yet-received offset, the next gap
// to fetch starts there; once that region is received the scheduler
// falls back to filling the lowest remaining gap.
func TestNextFetchGapPriority(t *testing.T) {
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{{Name: "m.bin", Size: 100, SHA256: "x"}})
	if err != nil {
		t.Fatal(err)
	}
	dl := NewDownloader(nil, "host", store, noICE(), nil, t.Logf)

	dl.Prioritize(0, 50) // a seek to byte 50, nothing received yet
	if lo, hi, st := dl.nextFetchGap(0); st != gapFound || lo != 50 || hi != 100 {
		t.Errorf("nextFetchGap with priority=50 = %d,%d,%v; want 50,100,gapFound", lo, hi, st)
	}

	store.files[0].add(50, 100) // the prioritized region arrives
	dl.Prioritize(0, 50)        // same seek, now satisfied
	if lo, hi, st := dl.nextFetchGap(0); st != gapFound || lo != 0 || hi != 50 {
		t.Errorf("nextFetchGap after priority satisfied = %d,%d,%v; want 0,50,gapFound (lowest gap)", lo, hi, st)
	}

	// With a playhead window that doesn't cover the remaining gap, expect gapWaiting.
	dl.SetPlayheadFunc(func(i int) int64 { return 0 })
	// minWindowBytes is 32 MB but the file is only 100 bytes, so the window
	// covers everything — no waiting expected for a tiny file.
	if _, _, st := dl.nextFetchGap(0); st != gapFound {
		t.Errorf("nextFetchGap with window covering small file: got %v, want gapFound", st)
	}
	// A zero window end (playhead=0 and windowBytes clamped to minWindowBytes=32MB)
	// still covers a 100-byte file. Verify gapWaiting with an explicit tiny window.
	origMin := minWindowBytes
	minWindowBytes = 0
	dl.SetPlayheadFunc(func(i int) int64 { return 0 }) // playhead at 0, window=0 → end=0
	if lo, hi, st := dl.nextFetchGap(0); st != gapWaiting {
		t.Errorf("nextFetchGap with zero window: got %d,%d,%v, want gapWaiting", lo, hi, st)
	}
	minWindowBytes = origMin
}

// TestNextFetchGapPriorityClampsToWindow is the regression test for the
// unbounded seek-priority fetch bug: nextFetchGap's priority branch used
// to return the gap all the way to the next already-downloaded span (or
// EOF) with no upper bound, so a seek near the front of a large file with
// nothing downloaded past it would fetch the entire remainder of the file
// in one request instead of a bounded window's worth.
func TestNextFetchGapPriorityClampsToWindow(t *testing.T) {
	origMin := minWindowBytes
	minWindowBytes = 10
	defer func() { minWindowBytes = origMin }()

	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{{Name: "m.bin", Size: 1000, SHA256: "x"}})
	if err != nil {
		t.Fatal(err)
	}
	dl := NewDownloader(nil, "host", store, noICE(), nil, t.Logf)

	dl.Prioritize(0, 20) // a seek to byte 20, nothing received anywhere past it
	if lo, hi, st := dl.nextFetchGap(0); st != gapFound || lo != 20 || hi != 30 {
		t.Errorf("nextFetchGap with priority=20, window=10 = %d,%d,%v; want 20,30,gapFound (clamped to prioOff+window)", lo, hi, st)
	}
}

// TestPrioritizedFetch drives a real persistent connection with a
// priority (seek) set before the body fills, exercising the redirect
// path end-to-end, and checks the file still reassembles correctly.
func TestPrioritizedFetch(t *testing.T) {
	savedTail := tailPrefetch
	tailPrefetch = 32 << 10
	defer func() { tailPrefetch = savedTail }()

	const size = 256 << 10
	data := make([]byte, size)
	rand.New(rand.NewSource(9)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	dl.Prioritize(0, 128<<10) // a seek to the middle, before the body fills

	if err := dl.runFile(ctx, 0); err != nil {
		t.Fatalf("runFile: %v", err)
	}
	got, err := os.ReadFile(store.PathFor(0))
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("reassembled file differs from source")
	}
}

// TestPlaylistJumpDoesNotDeadlock is the regression test for the
// cross-file priority deadlock: a priority pointing at a *different* file
// than the one Run() is currently downloading was invisible to
// nextFetchGap, so once that file's playhead-follower window was
// satisfied — permanently frozen, since nothing reads it anymore after a
// playlist jump — the downloader looped gapWaiting on it forever. Run()
// never reached the file mpv actually jumped to, hanging the whole
// session with no error. nextFetchGap must rush the stale file to
// completion instead once a cross-file priority is set, so Run() can
// reach the prioritized file.
func TestPlaylistJumpDoesNotDeadlock(t *testing.T) {
	savedTail := tailPrefetch
	tailPrefetch = 1 << 62 // skip index prefetch: keep this a clean single-gap scenario
	defer func() { tailPrefetch = savedTail }()
	savedMin := minWindowBytes
	minWindowBytes = 0 // pins file 0's window exactly at its (frozen) playhead
	defer func() { minWindowBytes = savedMin }()

	data0 := make([]byte, 128<<10)
	data1 := make([]byte, 64<<10)
	rand.New(rand.NewSource(51)).Read(data0)
	rand.New(rand.NewSource(52)).Read(data1)
	src0 := filepath.Join(t.TempDir(), "a.mkv")
	src1 := filepath.Join(t.TempDir(), "b.mkv")
	if err := os.WriteFile(src0, data0, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(src1, data1, 0o644); err != nil {
		t.Fatal(err)
	}
	srcs := []string{src0, src1}
	sum0 := sha256.Sum256(data0)
	sum1 := sha256.Sum256(data1)
	metas := []protocol.FileMeta{
		{Name: "a.mkv", Size: int64(len(data0)), SHA256: hex.EncodeToString(sum0[:])},
		{Name: "b.mkv", Size: int64(len(data1)), SHA256: hex.EncodeToString(sum1[:])},
	}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), metas)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", srcs[i], noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	// File 0's playhead is frozen at 0 — nobody reads it anymore, exactly
	// like a joiner whose playlist jumped away from it mid-download.
	dl.SetPlayheadFunc(func(i int) int64 { return 0 })
	// The room jumped straight to file 1.
	dl.Prioritize(1, 0)

	done := make(chan error, 1)
	go func() { done <- dl.Run(ctx) }()

	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("Run: %v", err)
		}
	case <-time.After(8 * time.Second):
		t.Fatal("Run deadlocked on file 0's frozen window instead of rushing it to completion")
	}

	for i, want := range [][]byte{data0, data1} {
		got, err := os.ReadFile(store.PathFor(i))
		if err != nil {
			t.Fatalf("file %d: %v", i, err)
		}
		if string(got) != string(want) {
			t.Errorf("file %d: reassembled content differs from source", i)
		}
	}
}

// TestFetchDoesNotSwallowPriorPreemptSignal is the regression test for the
// seek-preempt race: a redirect (Prioritize) landing after a gap decision
// was already made — but before the fetch for that now-stale range
// starts — must not be silently discarded. Before the fix, fetch() drained
// the preempt channel as its very first statement, swallowing exactly this
// signal and letting the stale range run to completion; mpv would stay
// blocked on the wrong bytes until then instead of redirecting at once.
func TestFetchDoesNotSwallowPriorPreemptSignal(t *testing.T) {
	const size = 4 << 20
	data := make([]byte, size)
	rand.New(rand.NewSource(42)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	// connect() opens the .part file without creating it — normally
	// runFile pre-sizes it as a sparse file first; replicate that here
	// since this test calls connect() directly.
	pf, err := os.OpenFile(store.files[0].partPath, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0o644)
	if err != nil {
		t.Fatal(err)
	}
	if err := pf.Truncate(size); err != nil {
		t.Fatal(err)
	}
	pf.Close()

	conn, err := dl.connect(ctx, 0)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	defer conn.close()

	// The redirect lands here: a gap decision for the whole file (the
	// range fetched below) was already made, and now the priority moves —
	// exactly the window between nextFetchGap's snapshot and fetch's
	// start in the real downloadFile loop.
	dl.Prioritize(0, 2<<20)

	fetchCtx, fetchCancel := context.WithTimeout(ctx, 3*time.Second)
	defer fetchCancel()
	if err := dl.fetch(fetchCtx, conn, 0, size, false); err != errPreempted {
		t.Fatalf("fetch must immediately bail with errPreempted when a redirect landed just before it started, got %v", err)
	}
}

// stallingHost is a minimal host double standing in for ServeFile: it
// completes a real offer/answer/data-channel handshake — the joiner
// sees a genuine, connected WebRTC data channel, not a mock — answers
// exactly one "start" request with a partial chunk, and then goes
// silent forever without ever sending "eof" or closing anything. This
// is the shape of a real stall (a wedged goroutine, a deadlocked
// sender, a network black hole that hasn't yet failed ICE) as opposed
// to a torn-down connection, which fetch's connFailed path already
// covers.
func stallingHost(ctx context.Context, sig Signaler, peer string, chunk []byte) error {
	pc, err := noICE().newPeerConnection()
	if err != nil {
		return err
	}
	defer pc.Close()

	dc, err := pc.CreateDataChannel("file", nil)
	if err != nil {
		return err
	}
	opened := make(chan struct{})
	dc.OnOpen(func() { close(opened) })
	started := make(chan struct{}, 1)
	dc.OnMessage(func(msg webrtc.DataChannelMessage) {
		if !msg.IsString {
			return
		}
		var c ctrl
		if unmarshalCtrl(msg.Data, &c) == nil && c.T == "start" {
			select {
			case started <- struct{}{}:
			default:
			}
		}
	})

	offer, err := pc.CreateOffer(nil)
	if err != nil {
		return err
	}
	if err := pc.SetLocalDescription(offer); err != nil {
		return err
	}
	if err := awaitGathering(ctx, pc); err != nil {
		return err
	}
	if err := sig.SendSignal(ctx, peer, protocol.Signal{Kind: "offer", SDP: pc.LocalDescription().SDP}); err != nil {
		return err
	}
	answer, err := waitSignal(ctx, sig, peer, "answer")
	if err != nil {
		return err
	}
	if err := pc.SetRemoteDescription(webrtc.SessionDescription{Type: webrtc.SDPTypeAnswer, SDP: answer.SDP}); err != nil {
		return err
	}

	select {
	case <-opened:
	case <-ctx.Done():
		return ctx.Err()
	}
	select {
	case <-started:
	case <-ctx.Done():
		return ctx.Err()
	}
	if err := dc.SendText(marshalCtrl(ctrl{T: "at", Seq: 1, Off: 0})); err != nil {
		return err
	}
	if err := dc.Send(chunk); err != nil {
		return err
	}
	<-ctx.Done() // then go silent: no more bytes, no eof, connection stays up
	return ctx.Err()
}

// TestFetchStallWatchdogDetectsSilentHost is the regression/coverage
// test for fetch's stall watchdog: a host that opens the connection,
// sends a few bytes, and then goes completely silent (no eof, no error,
// no connection teardown) must eventually be treated as a resumable
// stall rather than hanging fetch forever. connectTimeout and
// stallCheckInterval are shrunk so the test doesn't wait out the real
// 30s budget.
func TestFetchStallWatchdogDetectsSilentHost(t *testing.T) {
	origTimeout, origInterval := connectTimeout, stallCheckInterval
	connectTimeout = 300 * time.Millisecond
	stallCheckInterval = 20 * time.Millisecond
	defer func() { connectTimeout, stallCheckInterval = origTimeout, origInterval }()

	const size = 64 << 10
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: "unused-not-verified-by-fetch"}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}

	hostCtx, hostCancel := context.WithCancel(context.Background())
	defer hostCancel()
	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = stallingHost(hostCtx, hostSig, "joiner", make([]byte, 1024)) }()
			return nil
		}, t.Logf)

	// connect() opens the .part file without creating it — runFile
	// normally pre-sizes it as a sparse file first; replicate that here
	// since this test calls connect()/fetch() directly, bypassing
	// runFile's resumable-retry loop (which would otherwise keep
	// reconnecting to a freshly stalling host and never surface the
	// watchdog's error to this test).
	pf, err := os.OpenFile(store.files[0].partPath, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0o644)
	if err != nil {
		t.Fatal(err)
	}
	if err := pf.Truncate(size); err != nil {
		t.Fatal(err)
	}
	pf.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	conn, err := dl.connect(ctx, 0)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	defer conn.close()

	start := time.Now()
	err = dl.fetch(ctx, conn, 0, size, false)
	elapsed := time.Since(start)

	if err == nil {
		t.Fatal("expected the stall watchdog to eventually fail fetch")
	}
	if ctx.Err() != nil {
		t.Fatalf("fetch hit the test's own timeout instead of the stall watchdog: %v (after %v)", err, elapsed)
	}
	if !isResumable(err) {
		t.Errorf("a stall should be resumable (bytes on disk are kept), got non-resumable error: %v", err)
	}
	if !strings.Contains(err.Error(), "stalled") {
		t.Errorf("expected a stall error, got: %v", err)
	}
	if elapsed > 3*time.Second {
		t.Errorf("stall detection took %v — connectTimeout/stallCheckInterval overrides aren't taking effect", elapsed)
	}
}

// TestDrainSignalsDiscardsQueued is the regression test for the
// stale-offer-poisons-reconnect bug: a signal left queued from a dead
// connection attempt (one that gave up before consuming it) must not be
// mistaken for a fresh attempt's signal — waitSignal has no way to tell
// the two apart otherwise, silently desyncing the offer/answer pairing
// until the attempt burns the full connectTimeout. connect() must drain
// anything queued before asking for (and waiting on) a new one.
func TestDrainSignalsDiscardsQueued(t *testing.T) {
	sigA, sigB := newSignalerPair("a", "b")
	if err := sigB.SendSignal(context.Background(), "a", protocol.Signal{Kind: "offer", SDP: "stale-from-a-dead-attempt"}); err != nil {
		t.Fatal(err)
	}

	drainSignals(sigA)

	select {
	case s := <-sigA.Signals():
		t.Fatalf("drainSignals should have discarded the queued signal, got %+v", s)
	default:
	}
}

// TestConnectDrainsStaleQueuedOffer checks that connect() itself (not
// just the drainSignals primitive in isolation) discards a signal left
// over from a dead attempt before waiting on a fresh one. Without the
// drain, waitSignal would dequeue the stale garbage "offer" injected
// below instead of the real one ServeFile sends moments later, and
// applying it as the remote description would fail connect() outright.
func TestConnectDrainsStaleQueuedOffer(t *testing.T) {
	const size = 64 << 10
	data := make([]byte, size)
	rand.New(rand.NewSource(62)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	pf, err := os.OpenFile(store.files[0].partPath, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0o644)
	if err != nil {
		t.Fatal(err)
	}
	if err := pf.Truncate(size); err != nil {
		t.Fatal(err)
	}
	pf.Close()

	// Simulate a stale offer left in the channel by a dead prior attempt.
	if err := hostSig.SendSignal(context.Background(), "joiner", protocol.Signal{Kind: "offer", SDP: "garbage from a dead attempt"}); err != nil {
		t.Fatal(err)
	}

	conn, err := dl.connect(ctx, 0)
	if err != nil {
		t.Fatalf("connect should drain the stale offer and succeed on the real one that follows, got: %v", err)
	}
	conn.close()
}

// TestHostFileShorterThanAnnouncedIsTerminalError is the regression test
// for the silent-livelock bug: if the host's on-disk file is shorter than
// the size it announced (FileMeta.Size) — stale metadata, a truncated
// file, whatever the cause — send.go's streamRange answers a valid-offset
// request with an immediate "eof" and zero bytes. downloadFile used to
// treat that as a completed range and loop forever re-requesting the
// same never-satisfiable gap. It must now be a terminal error instead.
func TestHostFileShorterThanAnnouncedIsTerminalError(t *testing.T) {
	const realSize = 64 << 10
	const announcedSize = 128 << 10 // the host's actual file is half this
	data := make([]byte, realSize)
	rand.New(rand.NewSource(61)).Read(data)
	src := filepath.Join(t.TempDir(), "media.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: announcedSize, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	dl := NewDownloader(joinSig, "host", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error {
			go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()
			return nil
		}, t.Logf)

	done := make(chan error, 1)
	go func() { done <- dl.runFile(ctx, 0) }()

	select {
	case err := <-done:
		if err == nil {
			t.Fatal("expected a terminal error when the host's file is shorter than announced")
		}
		if ctx.Err() != nil {
			t.Fatalf("runFile hit the test's timeout instead of erroring promptly — the livelock is back: %v", err)
		}
	case <-time.After(4 * time.Second):
		t.Fatal("runFile did not return — looping forever on the never-satisfiable gap")
	}
}

func TestSetHost(t *testing.T) {
	dl := NewDownloader(nil, "alice", nil, noICE(), nil, t.Logf)

	if dl.GetHost() != "alice" {
		t.Errorf("initial host: got %q, want %q", dl.GetHost(), "alice")
	}

	dl.SetHost("bob")

	if dl.GetHost() != "bob" {
		t.Errorf("after SetHost: got %q, want %q", dl.GetHost(), "bob")
	}

	// SetHost should nudge the preempt channel exactly once (buffered size 1).
	select {
	case <-dl.preempt:
		// good
	default:
		t.Error("SetHost should nudge the preempt channel")
	}

	// A second SetHost when preempt is already full should not block.
	dl.SetHost("carol")
	if dl.GetHost() != "carol" {
		t.Errorf("second SetHost: got %q, want %q", dl.GetHost(), "carol")
	}
}

// TestSetHostCancelsInFlightConnect is the regression test for the
// migration-during-reconnect bug: connect() used to snapshot the host
// pubkey once and then block in waitSignal for up to connectTimeout (30s)
// on that exact peer. If SetHost landed while an attempt against the old,
// now-dead host was already in flight — the likely case, since migration
// follows a host drop and a reconnect is often already underway — the new
// host's offer was ignored until the old attempt finally timed out. connect()
// must now abort within a moment instead of riding out the full timeout.
func TestSetHostCancelsInFlightConnect(t *testing.T) {
	const size = 64 << 10
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: "deadbeef"}

	// The "deadhost" side of this pair is never driven by a ServeFile — no
	// offer ever arrives — so a real attempt against it would otherwise
	// hang in waitSignal until connectTimeout.
	_, joinSig := newSignalerPair("deadhost", "joiner")
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}

	dl := NewDownloader(joinSig, "deadhost", store, noICE(),
		func(c context.Context, _ string, i int, _, _ int64) error { return nil }, t.Logf)

	pf, err := os.OpenFile(store.files[0].partPath, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0o644)
	if err != nil {
		t.Fatal(err)
	}
	if err := pf.Truncate(size); err != nil {
		t.Fatal(err)
	}
	pf.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	done := make(chan error, 1)
	go func() {
		_, err := dl.connect(ctx, 0)
		done <- err
	}()

	// Give connect() a moment to reach waitSignal against "deadhost" before
	// migrating — SetHost's cancellation only matters if an attempt is
	// actually in flight when it fires.
	time.Sleep(100 * time.Millisecond)
	dl.SetHost("newhost")

	select {
	case err := <-done:
		if err == nil {
			t.Fatal("expected connect() to fail once its attempt against the old host was canceled")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("connect() did not abort promptly after SetHost — it's riding out the full connectTimeout on a host that will never answer")
	}
}
