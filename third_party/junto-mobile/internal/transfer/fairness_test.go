package transfer

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"math/rand"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestUploadFairnessShouldYield(t *testing.T) {
	f := NewUploadFairness()
	me, _ := f.register(0)
	other, _ := f.register(0) // same file
	now := time.Now()

	// No reports yet: nothing to base a decision on.
	if f.shouldYield(me, now) {
		t.Fatal("no reports should not yield")
	}

	// Comfortably buffered, another same-file viewer starving → yield.
	f.report(me, fairnessSatiatedBytes+1)
	f.report(other, fairnessHungryBytes-1)
	if !f.shouldYield(me, time.Now()) {
		t.Fatal("a satiated sender should yield to a hungry same-file viewer")
	}

	// The other viewer catches up → no reason to yield.
	f.report(other, fairnessSatiatedBytes+1)
	if f.shouldYield(me, time.Now()) {
		t.Fatal("should not yield when nobody is hungry")
	}

	// I'm not comfortably buffered myself → never yield, even if another
	// viewer is hungry.
	f.report(me, fairnessHungryBytes)
	f.report(other, 0)
	if f.shouldYield(me, time.Now()) {
		t.Fatal("a not-comfortably-buffered sender must not yield")
	}
}

func TestUploadFairnessFileScoped(t *testing.T) {
	f := NewUploadFairness()
	me, _ := f.register(0)
	otherFile, _ := f.register(1) // a viewer of a DIFFERENT file
	f.report(me, fairnessSatiatedBytes+1)
	f.report(otherFile, 0) // hungry, but not for the file I'm serving
	if f.shouldYield(me, time.Now()) {
		t.Fatal("must not yield to a hungry viewer of a different file (e.g. a prefetch)")
	}
}

func TestUploadFairnessStaleReportIgnored(t *testing.T) {
	f := NewUploadFairness()
	me, _ := f.register(0)
	other, _ := f.register(0)
	f.report(me, fairnessSatiatedBytes+1)
	// A hungry report that's gone stale (the viewer went quiet).
	f.mu.Lock()
	f.peers[other] = cushionReport{fileIdx: 0, bytes: 0, at: time.Now().Add(-2 * fairnessStaleAfter)}
	f.mu.Unlock()
	if f.shouldYield(me, time.Now()) {
		t.Fatal("a stale hungry report must be ignored")
	}
	// And a stale report for ME means I don't yield (I can't vouch for my
	// own buffer anymore).
	f.mu.Lock()
	f.peers[me] = cushionReport{fileIdx: 0, bytes: fairnessSatiatedBytes + 1, at: time.Now().Add(-2 * fairnessStaleAfter)}
	f.peers[other] = cushionReport{fileIdx: 0, bytes: 0, at: time.Now()}
	f.mu.Unlock()
	if f.shouldYield(me, time.Now()) {
		t.Fatal("a sender with a stale self-report must not yield")
	}
}

func TestUploadFairnessSingleViewer(t *testing.T) {
	f := NewUploadFairness()
	me, _ := f.register(0)
	f.report(me, fairnessSatiatedBytes+1)
	if f.shouldYield(me, time.Now()) {
		t.Fatal("a lone viewer has nobody to yield to")
	}
}

func TestUploadFairnessReleaseStopsYield(t *testing.T) {
	f := NewUploadFairness()
	me, _ := f.register(0)
	other, relOther := f.register(0)
	f.report(me, fairnessSatiatedBytes+1)
	f.report(other, 0)
	if !f.shouldYield(me, time.Now()) {
		t.Fatal("precondition: should yield to the hungry viewer")
	}
	relOther() // the hungry viewer's serve ends
	if f.shouldYield(me, time.Now()) {
		t.Fatal("after the hungry viewer leaves, there's nothing to yield to")
	}
}

func TestUploadFairnessPaceBoundedYield(t *testing.T) {
	origMax, origTick := fairnessMaxYield, fairnessYieldTick
	fairnessMaxYield, fairnessYieldTick = 60*time.Millisecond, 10*time.Millisecond
	defer func() { fairnessMaxYield, fairnessYieldTick = origMax, origTick }()

	f := NewUploadFairness()
	me, _ := f.register(0)
	other, _ := f.register(0)
	f.report(me, fairnessSatiatedBytes+1)
	f.report(other, 0) // perpetually starving

	start := time.Now()
	f.pace(context.Background(), me)
	elapsed := time.Since(start)
	if elapsed < fairnessMaxYield/2 {
		t.Fatalf("pace should have throttled while a viewer starves, returned after %v", elapsed)
	}
	// Bounded: it must NOT throttle forever even while the peer stays hungry.
	if elapsed > fairnessMaxYield+3*fairnessYieldTick {
		t.Fatalf("pace throttled too long (%v); it must be capped so the sender keeps progressing", elapsed)
	}
}

func TestUploadFairnessPaceNoYieldIsImmediate(t *testing.T) {
	f := NewUploadFairness()
	me, _ := f.register(0)
	f.report(me, fairnessSatiatedBytes+1) // alone ⇒ nothing to yield to
	start := time.Now()
	f.pace(context.Background(), me)
	if time.Since(start) > 20*time.Millisecond {
		t.Fatal("pace must return immediately when there's no reason to yield")
	}
}

func TestUploadFairnessPaceContextCancel(t *testing.T) {
	origMax, origTick := fairnessMaxYield, fairnessYieldTick
	fairnessMaxYield, fairnessYieldTick = time.Hour, 10*time.Millisecond
	defer func() { fairnessMaxYield, fairnessYieldTick = origMax, origTick }()

	f := NewUploadFairness()
	me, _ := f.register(0)
	other, _ := f.register(0)
	f.report(me, fairnessSatiatedBytes+1)
	f.report(other, 0)

	ctx, cancel := context.WithCancel(context.Background())
	go func() { time.Sleep(30 * time.Millisecond); cancel() }()
	start := time.Now()
	f.pace(ctx, me) // would throttle for an hour; must bail on cancel
	if time.Since(start) > time.Second {
		t.Fatal("pace must return promptly when the context is canceled")
	}
}

func TestUploadFairnessNilSafe(t *testing.T) {
	var f *UploadFairness // a serve with fairness disabled
	id, release := f.register(3)
	f.report(id, 100)
	f.pace(context.Background(), id) // must not panic
	release()
}

// TestCushionAhead covers the joiner-side buffer-depth math the "buf"
// frame carries: contiguous downloaded bytes ahead of the playhead, 0
// when the playhead itself isn't downloaded, and "no report" when there's
// no playhead (a non-streaming bulk fetch).
func TestCushionAhead(t *testing.T) {
	const size = 1 << 20
	meta := protocol.FileMeta{Name: "m.bin", Size: size, SHA256: "x"}
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{meta})
	if err != nil {
		t.Fatal(err)
	}
	e := store.files[0]
	e.add(0, 300<<10) // contiguous [0, 300 KiB)
	e.add(500<<10, 600<<10)

	d := &Downloader{store: store}

	// No playhead registered (non-streaming): reports nothing.
	if _, ok := d.cushionAhead(0); ok {
		t.Fatal("a non-streaming fetch (no playhead) must not report a cushion")
	}

	var playhead int64
	d.SetPlayheadFunc(func(int) int64 { return playhead })

	// Playhead at 100 KiB, contiguous run ends at 300 KiB ⇒ 200 KiB ahead.
	playhead = 100 << 10
	if c, ok := d.cushionAhead(0); !ok || c != 200<<10 {
		t.Fatalf("cushion = %d,%v want 200KiB", c, ok)
	}
	// Playhead in the gap (not downloaded) ⇒ starving (0).
	playhead = 400 << 10
	if c, ok := d.cushionAhead(0); !ok || c != 0 {
		t.Fatalf("cushion in gap = %d,%v want 0 (hungry)", c, ok)
	}
}

// TestBufFrameReachesFairness drives the joiner→host cushion signal end
// to end over a real WebRTC connection: a viewer's "buf" frame must land
// in the serving node's fairness table for that serve. The serve is the
// first (and only) register on a fresh UploadFairness, so its handle id
// is 0.
func TestBufFrameReachesFairness(t *testing.T) {
	const size = 1 << 20
	data := make([]byte, size)
	rand.New(rand.NewSource(11)).Read(data)
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}
	store := storeWith(t, meta, data, [][2]int64{{0, size}})

	fair := NewUploadFairness()
	seederSig, joinSig := newSignalerPair("seeder", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	go func() { _ = ServeFromStore(ctx, seederSig, "joiner", store, 0, noICE(), t.Logf, nil, nil, fair) }()
	rj := newRawJoiner(ctx, t, joinSig, "seeder")

	const want = 5 << 20
	if err := rj.dc.SendText(marshalCtrl(ctrl{T: "buf", Off: want})); err != nil {
		t.Fatal(err)
	}

	deadline := time.After(10 * time.Second)
	for {
		fair.mu.Lock()
		r, ok := fair.peers[0]
		fair.mu.Unlock()
		if ok && r.bytes == want {
			return // the buf frame reached the fairness table
		}
		select {
		case <-deadline:
			t.Fatalf("buf frame never reached fairness (peers[0]=%+v)", r)
		case <-time.After(20 * time.Millisecond):
		}
	}
}
