package ytdlp

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"
)

func TestFormatArg(t *testing.T) {
	cases := map[string]string{
		"":      "bv*[height<=1080]+ba/b[height<=1080]/b",
		"1080p": "bv*[height<=1080]+ba/b[height<=1080]/b",
		"720p":  "bv*[height<=720]+ba/b[height<=720]/b",
		"480p":  "bv*[height<=480]+ba/b[height<=480]/b",
		"best":  "bv*+ba/b",
		"audio": "ba/b",
		"137":   "137", // unknown → raw passthrough
	}
	for in, want := range cases {
		if got := formatArg(in); got != want {
			t.Errorf("formatArg(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestBuildArgs(t *testing.T) {
	args := buildArgs("https://youtu.be/x", Options{Quality: "720p", DestDir: "/tmp/cache"})
	joined := strings.Join(args, "\x00")
	mustHave := []string{
		"--no-playlist", "--restrict-filenames", "--no-simulate",
		"--merge-output-format", "mp4",
		"-o", "%(title).80s [%(id)s].%(ext)s",
		"-P", "/tmp/cache",
		"-f", "bv*[height<=720]+ba/b[height<=720]/b",
	}
	for _, m := range mustHave {
		if !strings.Contains(joined, m) {
			t.Errorf("buildArgs missing %q in %v", m, args)
		}
	}
	if args[len(args)-1] != "https://youtu.be/x" {
		t.Errorf("URL should be the last arg, got %q", args[len(args)-1])
	}
	if !strings.Contains(joined, "download:JUNTO ") {
		t.Errorf("buildArgs missing the progress template: %v", args)
	}
}

func TestParseProgress(t *testing.T) {
	p, ok := parseProgress("JUNTO 1024 4096 512.0 6")
	if !ok || p.Downloaded != 1024 || p.Total != 4096 || p.BytesPerSec != 512 || p.ETA != 6*time.Second {
		t.Errorf("full line parsed wrong: %+v ok=%v", p, ok)
	}
	// NA total/speed/eta tolerated; downloaded still required.
	p, ok = parseProgress("JUNTO 1024 NA NA NA")
	if !ok || p.Downloaded != 1024 || p.Total != 0 || p.BytesPerSec != 0 || p.ETA != 0 {
		t.Errorf("NA line parsed wrong: %+v ok=%v", p, ok)
	}
	for _, bad := range []string{
		"[download]  50% of 4MiB", // not ours
		"/abs/path/file.mp4",      // a filepath line
		"JUNTO 1024 4096 512",     // too few fields
		"JUNTO NA 4096 1 1",       // downloaded NA → unusable
		"",
	} {
		if _, ok := parseProgress(bad); ok {
			t.Errorf("parseProgress(%q) should be ok=false", bad)
		}
	}
}

func TestClassifyErr(t *testing.T) {
	if err := classifyErr("u", "ERROR: ffmpeg is not installed. Please install", nil); !strings.Contains(err.Error(), "ffmpeg") {
		t.Errorf("ffmpeg error not recognized: %v", err)
	}
	if err := classifyErr("u", "ERROR: Private video. Sign in if you've been granted access", nil); !strings.Contains(err.Error(), "logged-in") {
		t.Errorf("private-video error not recognized: %v", err)
	}
}

// TestFetchStub drives Fetch against a fake yt-dlp script: a preflight
// call (sees --simulate) prints is_live + title; the download call prints
// a progress line then the output path. Verifies the wiring end-to-end
// without a real yt-dlp or network.
func TestFetchStub(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("stub uses a /bin/sh script")
	}
	defer func(old time.Duration) { progressInterval = old }(progressInterval)
	progressInterval = 0

	dir := t.TempDir()
	out := filepath.Join(dir, "My_Video [abc123].mp4")
	if err := os.WriteFile(out, []byte("video"), 0o644); err != nil {
		t.Fatal(err)
	}
	stub := filepath.Join(dir, "fake-yt-dlp")
	script := "#!/bin/sh\ncase \"$*\" in\n  *--simulate*) printf 'False\\nMy Video\\n' ;;\n  *) printf 'JUNTO 1024 2048 512.0 2\\n'; printf '%s\\n' \"" + out + "\" ;;\nesac\n"
	if err := os.WriteFile(stub, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}

	var gotProgress bool
	path, title, err := Fetch(context.Background(), "https://youtu.be/abc123",
		Options{BinPath: stub, DestDir: dir},
		func(p Progress) { gotProgress = true })
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if path != out {
		t.Errorf("path = %q, want %q", path, out)
	}
	if title != "My Video" {
		t.Errorf("title = %q, want %q", title, "My Video")
	}
	if !gotProgress {
		t.Error("expected the progress callback to fire")
	}
}

// TestFetchStubLiveRejected: a preflight reporting is_live=True is refused
// before any download.
func TestFetchStubLiveRejected(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("stub uses a /bin/sh script")
	}
	dir := t.TempDir()
	stub := filepath.Join(dir, "fake-yt-dlp")
	script := "#!/bin/sh\ncase \"$*\" in\n  *--simulate*) printf 'True\\nLive Now\\n' ;;\n  *) echo should-not-run; exit 1 ;;\nesac\n"
	if err := os.WriteFile(stub, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	_, _, err := Fetch(context.Background(), "https://youtu.be/live", Options{BinPath: stub, DestDir: dir}, nil)
	if err == nil || !strings.Contains(err.Error(), "live stream") {
		t.Fatalf("expected a live-stream rejection, got %v", err)
	}
}

func TestFetchMissingBinary(t *testing.T) {
	_, _, err := Fetch(context.Background(), "https://youtu.be/x", Options{BinPath: "definitely-not-a-real-binary-xyz"}, nil)
	if err == nil || !strings.Contains(err.Error(), "not found on your PATH") {
		t.Fatalf("expected a not-found error, got %v", err)
	}
}

// TestCleanPartialsScopedToID is the regression test for the shared-
// cache-directory bug: DestDir is one cache dir shared by every
// concurrent `junto create <url>`, so an unscoped glob for "*.part" etc.
// deleted a cancelled download's fragments along with any other
// session's still-in-progress ones. cleanPartials must only ever touch
// files carrying this download's own id (embedded as "[id]" by
// buildArgs' -o template).
func TestCleanPartialsScopedToID(t *testing.T) {
	dir := t.TempDir()
	mine := []string{
		"My Video [abc123].mp4.part",
		"My Video [abc123].ytdl",
		"My Video [abc123].f137.mp4.part-Frag3",
	}
	others := []string{
		"Other Video [xyz789].mp4.part",
		"Other Video [xyz789].ytdl",
	}
	for _, f := range append(append([]string{}, mine...), others...) {
		if err := os.WriteFile(filepath.Join(dir, f), []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
	}

	cleanPartials(dir, "abc123")

	for _, f := range mine {
		if _, err := os.Stat(filepath.Join(dir, f)); !os.IsNotExist(err) {
			t.Errorf("expected %q to be removed, stat err = %v", f, err)
		}
	}
	for _, f := range others {
		if _, err := os.Stat(filepath.Join(dir, f)); err != nil {
			t.Errorf("cleanPartials scoped to abc123 deleted an unrelated session's file %q: %v", f, err)
		}
	}
}

// TestFetchKillsOrphanedChildOnCancel is the regression test for the
// no-process-group bug: on cancellation, the default exec.CommandContext
// behavior kills only the direct yt-dlp child, leaving any subprocess it
// spawned (ffmpeg, standing in here for a detached background child that
// inherits the same stdout pipe) running — the read side then never
// sees EOF and Fetch hangs well past cancellation instead of returning
// promptly.
func TestFetchKillsOrphanedChildOnCancel(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("stub uses a /bin/sh script and POSIX process groups")
	}
	dir := t.TempDir()
	stub := filepath.Join(dir, "fake-yt-dlp")
	// The background child touches a marker file right before it starts
	// holding the pipe open, so the test can wait deterministically for
	// it to actually exist rather than racing a fixed sleep against the
	// fork — too short a sleep lets the test pass by luck (cancel lands
	// before the child even forks) regardless of whether the fix works.
	marker := filepath.Join(dir, "child-started")
	script := "#!/bin/sh\ncase \"$*\" in\n" +
		"  *--simulate*) printf 'False\\nMy Video\\nvid123\\n' ;;\n" +
		"  *)\n" +
		"    ( touch \"" + marker + "\"; sleep 30 ) &\n" + // a detached child inheriting our stdout pipe
		"    sleep 30\n" + // yt-dlp itself, still "running" until killed
		"    ;;\n" +
		"esac\n"
	if err := os.WriteFile(stub, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}

	// Shrunk so the wait below has real margin over it instead of racing
	// the exact same duration — on a loaded machine that leaves zero slack
	// for the group-kill and pipe teardown to actually finish in time.
	origWaitDelay := killWaitDelay
	killWaitDelay = 300 * time.Millisecond
	defer func() { killWaitDelay = origWaitDelay }()

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() {
		_, _, err := Fetch(ctx, "https://youtu.be/vid123", Options{BinPath: stub, DestDir: dir}, nil)
		done <- err
	}()

	deadline := time.Now().Add(5 * time.Second)
	for {
		if _, err := os.Stat(marker); err == nil {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("background child never started (marker file never appeared)")
		}
		time.Sleep(10 * time.Millisecond)
	}
	cancel()

	select {
	case err := <-done:
		if !errors.Is(err, context.Canceled) {
			t.Errorf("expected context.Canceled, got %v", err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("Fetch did not return after cancellation — an orphaned child is likely still holding the stdout pipe open")
	}
}

// TestPreflightTimesOut is the regression test for the silent-hang bug:
// the metadata-only probe used the caller's context unbounded, so a
// stalled extractor (a slow site, a hung network call, yt-dlp waiting on
// an interactive prompt) hung `create` indefinitely with no feedback and
// no way out short of Ctrl-C. preflight must bound its own wait and
// return an explanatory error well before that.
func TestPreflightTimesOut(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("stub uses a /bin/sh script")
	}
	saved := preflightTimeout
	preflightTimeout = 200 * time.Millisecond
	defer func() { preflightTimeout = saved }()

	dir := t.TempDir()
	stub := filepath.Join(dir, "fake-yt-dlp")
	// exec (not a plain "sleep 5") replaces the shell with sleep directly
	// instead of forking a child — otherwise killing the shell on timeout
	// orphans the sleep, which keeps the stdout pipe open for its own
	// remaining lifetime and defeats the very timeout under test.
	if err := os.WriteFile(stub, []byte("#!/bin/sh\nexec sleep 5\n"), 0o755); err != nil {
		t.Fatal(err)
	}

	start := time.Now()
	_, _, _, err := preflight(context.Background(), stub, "https://example.com/x")
	elapsed := time.Since(start)

	if err == nil {
		t.Fatal("expected a timeout error, got none")
	}
	if elapsed > 2*time.Second {
		t.Fatalf("preflight took %v to give up, want well under the stub's 5s sleep", elapsed)
	}
}
