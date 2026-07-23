package app

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// capture returns a Printf sink and an accessor for what it collected,
// mirroring the same helper in internal/syncer and internal/transfer.
func capture() (func(string, ...any), func() []string) {
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

// TestHostWaitErrorTreatsCancellationCleanly is the regression test for
// the Ctrl-C-while-waiting bug: a cancellation used to be wrapped with
// an irrelevant "get the files another way" retry hint, and the
// resulting message ("context canceled\nget the files another way...")
// looked like a real failure instead of the clean, user-initiated exit
// it actually was. It must stay recognizable via errors.Is and must not
// carry the retry hint.
func TestHostWaitErrorTreatsCancellationCleanly(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	err := hostWaitError(ctx, context.Canceled, "jun1abc")
	if !errors.Is(err, context.Canceled) {
		t.Errorf("expected an error matching context.Canceled, got %v", err)
	}
	if strings.Contains(err.Error(), "get the files another way") {
		t.Errorf("a cancellation should not carry the irrelevant retry hint, got: %v", err)
	}
}

// TestHostWaitErrorAddsHintForRealFailures checks the fix didn't throw
// out the retry hint for a genuine failure (nobody answered, etc.) —
// only cancellation should skip it.
func TestHostWaitErrorAddsHintForRealFailures(t *testing.T) {
	err := hostWaitError(context.Background(), fmt.Errorf("nobody answered on that room code"), "jun1abc")
	if !strings.Contains(err.Error(), "get the files another way") {
		t.Errorf("a real failure should still carry the retry hint, got: %v", err)
	}
	if !strings.Contains(err.Error(), "nobody answered") {
		t.Errorf("the original error should still be present, got: %v", err)
	}
}

// TestRunSessionPropagatesMpvLaunchFailure covers the one runSession
// codepath testable without a real mpv binary or a live *nostrx.Transport
// (runSession's composition-root wiring is otherwise 0% covered): mpv.Launch
// is the very first thing it does, and a bad MpvPath fails there via
// exec.LookPath before t (nil here) or any of the syncer.Deps wiring is ever
// touched, so the error must propagate straight back to the caller instead
// of panicking on the nil transport or hanging.
func TestRunSessionPropagatesMpvLaunchFailure(t *testing.T) {
	cfg := Config{MpvPath: "/nonexistent/junto-test-mpv-binary"}
	err := runSession(context.Background(), nil, []string{"/tmp/junto-test-media.mkv"}, nil, false, cfg, nil, nil, nil, nil, nil, sessionExtras{}, plainHooks())
	if err == nil {
		t.Fatal("expected an error when mpv fails to launch")
	}
	if !strings.Contains(err.Error(), "mpv") {
		t.Errorf("expected an mpv-related error, got: %v", err)
	}
}

// TestUploadTrackerWarnsOnSecondViewerWhenUndersupplied checks the core
// oversubscription trigger: once a second concurrent viewer of the same
// file joins and the best rate ever observed from a single viewer can't
// cover both at the file's bitrate, a one-time warning fires.
func TestUploadTrackerWarnsOnSecondViewerWhenUndersupplied(t *testing.T) {
	pf, lines := capture()
	meta := protocol.FileMeta{Name: "movie.mp4", Size: 10_000_000, DurationSecs: 1} // 10 MB/s bitrate
	tr := newUploadTracker([]protocol.FileMeta{meta}, pf, nil)

	stop1 := tr.start(0)
	tr.report(0, 6_000_000) // solo rate well under 2x bitrate (20 MB/s)
	stop2 := tr.start(0)    // second concurrent viewer
	defer stop1()
	defer stop2()

	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "supports about") {
		t.Errorf("expected an oversubscription warning, got:\n%s", out)
	}
}

// TestUploadTrackerSilentWithSufficientRate checks that ample measured
// upload throughput (comfortably covering every concurrent viewer at the
// file's bitrate) never warns.
func TestUploadTrackerSilentWithSufficientRate(t *testing.T) {
	pf, lines := capture()
	meta := protocol.FileMeta{Name: "movie.mp4", Size: 10_000_000, DurationSecs: 1} // 10 MB/s bitrate
	tr := newUploadTracker([]protocol.FileMeta{meta}, pf, nil)

	stop1 := tr.start(0)
	tr.report(0, 25_000_000) // solo rate comfortably above 2x bitrate (20 MB/s)
	stop2 := tr.start(0)
	defer stop1()
	defer stop2()

	if out := strings.Join(lines(), "\n"); strings.Contains(out, "supports about") {
		t.Errorf("should not warn when the measured rate comfortably covers every viewer, got:\n%s", out)
	}
}

// TestUploadTrackerSilentForUnknownBitrate checks the non-mp4 case (no
// DurationSecs): no bitrate means no prediction is possible, so start/
// report must be no-ops rather than warning about nothing or panicking.
func TestUploadTrackerSilentForUnknownBitrate(t *testing.T) {
	pf, lines := capture()
	meta := protocol.FileMeta{Name: "movie.mkv", Size: 10_000_000, DurationSecs: 0}
	tr := newUploadTracker([]protocol.FileMeta{meta}, pf, nil)

	stop1 := tr.start(0)
	tr.report(0, 1) // deliberately tiny — would trip the warning if bitrate were known
	stop2 := tr.start(0)
	defer stop1()
	defer stop2()

	if out := strings.Join(lines(), "\n"); len(out) != 0 {
		t.Errorf("expected no output for a file with an unknown bitrate, got:\n%s", out)
	}
}

// TestUploadTrackerWarnsOnlyOnce checks a third (and further) viewer
// joining an already-undersupplied file doesn't repeat the warning.
func TestUploadTrackerWarnsOnlyOnce(t *testing.T) {
	pf, lines := capture()
	meta := protocol.FileMeta{Name: "movie.mp4", Size: 10_000_000, DurationSecs: 1}
	tr := newUploadTracker([]protocol.FileMeta{meta}, pf, nil)

	stop1 := tr.start(0)
	tr.report(0, 6_000_000)
	stop2 := tr.start(0) // triggers the warning
	stop3 := tr.start(0) // a third viewer must not re-trigger it
	defer stop1()
	defer stop2()
	defer stop3()

	if got := strings.Count(strings.Join(lines(), "\n"), "supports about"); got != 1 {
		t.Errorf("expected the oversubscription warning exactly once, got %d occurrences", got)
	}
}

// TestCheckDiskSpaceWarnsWhenPlaylistExceedsFree is the regression test
// for the disk-space preflight: a streaming joiner used to only find out
// the playlist wouldn't fit via a write error deep into a multi-hour
// transfer. It must now be told up front, with both numbers in the
// message.
func TestCheckDiskSpaceWarnsWhenPlaylistExceedsFree(t *testing.T) {
	saved := diskFreeFn
	defer func() { diskFreeFn = saved }()
	diskFreeFn = func(string) (uint64, error) { return 8 << 30, nil } // 8 GiB free

	pf, lines := capture()
	videos := []protocol.FileMeta{
		{Name: "a.mkv", Size: 12 << 30},
		{Name: "b.mkv", Size: 9 << 30},
	} // 21 GiB total
	checkDiskSpace(pf, "/tmp/out", videos)

	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "heads up") {
		t.Fatalf("expected a disk-space warning, got:\n%s", out)
	}
	if !strings.Contains(out, "21.0 GiB") || !strings.Contains(out, "8.0 GiB") {
		t.Errorf("expected both the needed and free sizes in the message, got:\n%s", out)
	}
}

// TestCheckDiskSpaceSilentWhenEnoughFree checks the common case (plenty
// of room) never warns.
func TestCheckDiskSpaceSilentWhenEnoughFree(t *testing.T) {
	saved := diskFreeFn
	defer func() { diskFreeFn = saved }()
	diskFreeFn = func(string) (uint64, error) { return 500 << 30, nil } // 500 GiB free

	pf, lines := capture()
	videos := []protocol.FileMeta{{Name: "a.mkv", Size: 20 << 30}}
	checkDiskSpace(pf, "/tmp/out", videos)

	if out := strings.Join(lines(), "\n"); out != "" {
		t.Errorf("should not warn when there's plenty of free space, got:\n%s", out)
	}
}

// TestCheckDiskSpaceSilentOnLookupFailure checks that a disk-free lookup
// failure (e.g. the download directory doesn't exist yet) is silently
// skipped rather than warning about nothing or panicking — that's a
// separate problem the download itself will surface clearly.
func TestCheckDiskSpaceSilentOnLookupFailure(t *testing.T) {
	saved := diskFreeFn
	defer func() { diskFreeFn = saved }()
	diskFreeFn = func(string) (uint64, error) { return 0, fmt.Errorf("no such file or directory") }

	pf, lines := capture()
	videos := []protocol.FileMeta{{Name: "a.mkv", Size: 20 << 30}}
	checkDiskSpace(pf, "/nonexistent", videos)

	if out := strings.Join(lines(), "\n"); out != "" {
		t.Errorf("a disk-free lookup failure must not itself produce a warning, got:\n%s", out)
	}
}

// TestCheckDiskSpaceCountsSubtitleSizes checks that subtitle sidecars —
// downloaded into the same directory as the videos — count toward the
// total, not just the video files themselves.
func TestCheckDiskSpaceCountsSubtitleSizes(t *testing.T) {
	saved := diskFreeFn
	defer func() { diskFreeFn = saved }()
	const free = 10 << 30 // 10 GiB
	diskFreeFn = func(string) (uint64, error) { return free, nil }

	pf, lines := capture()
	videos := []protocol.FileMeta{
		{Name: "a.mkv", Size: 9 << 30, Subs: []protocol.FileMeta{
			{Name: "a.srt", Size: 2 << 30}, // pushes the total past free space on its own
		}},
	}
	checkDiskSpace(pf, "/tmp/out", videos)

	if out := strings.Join(lines(), "\n"); !strings.Contains(out, "heads up") {
		t.Errorf("subtitle sizes should count toward the total, expected a warning, got:\n%s", out)
	}
}

// TestSignalReadyOrFailClosesOnSuccess is the baseline: reaching the
// buffer target closes readyCh as before.
func TestSignalReadyOrFailClosesOnSuccess(t *testing.T) {
	readyCh := make(chan struct{})
	signalReadyOrFail(context.Background(), readyCh, func(context.Context) error { return nil })
	select {
	case <-readyCh:
	default:
		t.Error("readyCh should be closed once WaitBuffered succeeds")
	}
}

// TestSignalReadyOrFailClosesOnGenuineDownloadFailure is the regression
// test for the stuck-forever bug: a real download failure (host gone,
// disk full, unrecoverable corruption) while the session is still live
// used to leave readyCh open forever, since only the success path
// closed it — the joiner stayed "not ready" for the rest of the
// session with no way to recover.
func TestSignalReadyOrFailClosesOnGenuineDownloadFailure(t *testing.T) {
	readyCh := make(chan struct{})
	// dlCtx (context.Background here) is NOT done — this models a real
	// download error while the session keeps running, not the session
	// itself ending.
	signalReadyOrFail(context.Background(), readyCh, func(context.Context) error {
		return fmt.Errorf("host disconnected")
	})
	select {
	case <-readyCh:
	default:
		t.Error("readyCh must still close on a genuine download failure — otherwise the peer is stuck \"not ready\" forever")
	}
}

// TestSignalReadyOrFailLeavesOpenWhenSessionEnds checks the fix didn't
// overcorrect: when the failure is because the session itself is
// ending (dlCtx done), readyCh should stay open — nothing is listening
// on it anymore, so closing it would be a no-op at best.
func TestSignalReadyOrFailLeavesOpenWhenSessionEnds(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	readyCh := make(chan struct{})
	signalReadyOrFail(ctx, readyCh, func(context.Context) error { return ctx.Err() })
	select {
	case <-readyCh:
		t.Error("readyCh should stay open when the session itself is ending")
	case <-time.After(50 * time.Millisecond):
	}
}
