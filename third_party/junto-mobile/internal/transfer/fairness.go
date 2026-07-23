package transfer

import (
	"context"
	"sync"
	"time"
)

// Host upload fairness. When one node serves several streaming viewers,
// its uplink is a shared resource; without coordination the OS splits it
// evenly, so a viewer with 0.5s of buffer is served no faster than one
// with 10s. And because junto pauses the whole room whenever ANY viewer
// stalls, the room's smoothness is set by its worst-off viewer — so
// keeping the hungriest viewer fed raises the room's stall floor.
//
// Each viewer reports its own buffer depth (bytes downloaded ahead of its
// playhead) to whoever serves it, in "buf" data-channel frames. A sender
// that is comfortably buffered briefly throttles itself while another
// viewer of the same file is close to stalling, biasing the uplink toward
// the hungry one. It is a no-op with a single viewer, and a viewer that
// never reports a cushion (an older joiner) is neither throttled nor
// counted as hungry, so the behavior degrades to exactly the
// un-prioritized path.

var (
	// A sender only yields when it is comfortably buffered AND another
	// viewer of the same file is close to stalling. Byte thresholds (a few
	// seconds at typical streaming bitrates); vars for test-shrinkability.
	fairnessSatiatedBytes int64 = 16 << 20 // ≳6s at 20 Mbps: comfortable
	fairnessHungryBytes   int64 = 4 << 20  // ≲2s at 20 Mbps: close to stalling
	fairnessYieldTick           = 40 * time.Millisecond
	// fairnessMaxYield caps how long one pace() call throttles, so a
	// satiated sender still makes steady progress (never fully stalls
	// itself) even while another viewer stays hungry.
	fairnessMaxYield   = 200 * time.Millisecond
	fairnessStaleAfter = 5 * time.Second // a cushion report older than this is ignored
)

// cushionReport is one viewer's latest buffer depth for the file it's
// streaming.
type cushionReport struct {
	fileIdx int
	bytes   int64
	at      time.Time // zero until the first report
}

// UploadFairness coordinates a serving node's per-viewer senders so the
// uplink favors whoever is closest to stalling. One instance per serving
// node (the host, or a promoted / local-file joiner that serves others),
// shared across its ServeFile / ServeFromStore goroutines.
type UploadFairness struct {
	mu    sync.Mutex
	peers map[int]cushionReport
	next  int
}

func NewUploadFairness() *UploadFairness {
	return &UploadFairness{peers: make(map[int]cushionReport)}
}

// register enrolls a serve of file fileIdx and returns its id plus a
// release func to call when the serve ends. Safe on a nil receiver
// (returns a no-op), so callers needn't branch.
func (f *UploadFairness) register(fileIdx int) (id int, release func()) {
	if f == nil {
		return 0, func() {}
	}
	f.mu.Lock()
	id = f.next
	f.next++
	f.peers[id] = cushionReport{fileIdx: fileIdx}
	f.mu.Unlock()
	return id, func() {
		f.mu.Lock()
		delete(f.peers, id)
		f.mu.Unlock()
	}
}

// report records a viewer's latest buffer depth (bytes ahead of its
// playhead), from a "buf" frame.
func (f *UploadFairness) report(id int, cushionBytes int64) {
	if f == nil {
		return
	}
	f.mu.Lock()
	if r, ok := f.peers[id]; ok {
		r.bytes, r.at = cushionBytes, time.Now()
		f.peers[id] = r
	}
	f.mu.Unlock()
}

// shouldYield reports whether sender id should throttle right now: it is
// comfortably buffered and some OTHER viewer of the same file has a fresh
// report showing it's close to stalling.
func (f *UploadFairness) shouldYield(id int, now time.Time) bool {
	f.mu.Lock()
	defer f.mu.Unlock()
	me, ok := f.peers[id]
	if !ok || me.at.IsZero() || now.Sub(me.at) > fairnessStaleAfter || me.bytes < fairnessSatiatedBytes {
		return false // unknown / stale / not comfortably buffered: don't yield
	}
	for other, r := range f.peers {
		if other == id || r.fileIdx != me.fileIdx || r.at.IsZero() || now.Sub(r.at) > fairnessStaleAfter {
			continue
		}
		if r.bytes < fairnessHungryBytes {
			return true // a same-file viewer is starving and I have plenty
		}
	}
	return false
}

// pace throttles sender id before its next chunk, yielding uplink to a
// hungrier same-file viewer while one exists — but only up to
// fairnessMaxYield, so a satiated sender keeps making steady progress. A
// no-op on a nil receiver, when alone, or when nobody else is hungry.
func (f *UploadFairness) pace(ctx context.Context, id int) {
	if f == nil {
		return
	}
	deadline := time.Now().Add(fairnessMaxYield)
	for time.Now().Before(deadline) && f.shouldYield(id, time.Now()) {
		select {
		case <-ctx.Done():
			return
		case <-time.After(fairnessYieldTick):
		}
	}
}
