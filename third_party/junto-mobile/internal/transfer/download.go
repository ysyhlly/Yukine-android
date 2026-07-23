package transfer

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/pion/webrtc/v4"

	"github.com/swayam-mishra/junto/internal/debug"
	"github.com/swayam-mishra/junto/internal/human"
	"github.com/swayam-mishra/junto/internal/protocol"
)

const maxResumeRetries = 5

// gapStatus distinguishes the three outcomes of nextFetchGap.
type gapStatus int

const (
	gapFound   gapStatus = iota // a real gap in [gapLo, gapHi) to fetch
	gapWaiting                  // window is satisfied; wait for playhead to advance
	gapDone                     // file is fully received
)

// Adaptive download-window parameters. The window ahead of the playhead is
// clamped between min and max and sized to windowTargetSecs of throughput.
// min/max are vars so tests can override them.
var (
	minWindowBytes int64 = 32 << 20  // 32 MB floor
	maxWindowBytes int64 = 512 << 20 // 512 MB ceiling
)

const windowTargetSecs = 30 // target seconds of download headroom

// tailPrefetch is how many bytes are fetched from the END of a file
// before the body. Media indexes (mp4 moov, mkv Cues) often live at the
// end; players seek there on open, so having the tail ready first lets
// playback start without waiting for the whole download. It's a var so
// tests can shrink it. Files at or below this size skip prefetch.
var tailPrefetch int64 = 16 << 20

// progressInterval throttles how often progress is reported during a
// transfer. A var so tests can drop it to observe every chunk.
var progressInterval = 500 * time.Millisecond

// resumableError marks a mid-transfer connection drop — the bytes
// already on disk are kept and the download retries from there. A
// terminal error (host error, disk error, host unreachable) is not
// wrapped and stops the download.
type resumableError struct{ err error }

func (e resumableError) Error() string { return e.err.Error() }
func (e resumableError) Unwrap() error { return e.err }

func isResumable(err error) bool {
	var r resumableError
	return errors.As(err, &r)
}

// span is a half-open byte range [lo, hi) that has been received.
type span struct{ lo, hi int64 }

// fileEntry tracks one file's download as a set of received byte ranges
// (have) over a sparse file. A byte is available once it falls inside a
// span. Ranges may be filled in any order — sequentially from the front,
// the prefetched tail, or a region a seek jumped to — and adjacent spans
// coalesce. Readers (the stream server) wait on cond.
type fileEntry struct {
	meta      protocol.FileMeta
	partPath  string
	finalPath string
	metaPath  string     // sidecar recording received spans, for cross-session resume
	obPath    string     // sidecar holding the bao outboard tree (bao rooms only)
	bao       *baoParams // per-chunk verification params; nil in a pre-swarm room

	mu       sync.Mutex
	cond     *sync.Cond
	have     []span // sorted, merged, non-overlapping received ranges
	done     bool   // fully received + verified + renamed
	resumed  bool   // started from a prior session's .part + sidecar
	err      error
	outboard []byte // verified bao outboard tree; nil until fetched or loaded

	// Multi-source coordination. claims are ranges some source is
	// currently fetching, so concurrent sources never duplicate work:
	// selectors skip have ∪ claims, and a claim is released when its
	// fetch ends (however it ends — the bytes that landed are in have,
	// the rest re-enters selection). writers are every open handle
	// currently writing this file; saveProgress syncs them all before
	// snapshotting have, preserving the fsync-before-sidecar crash
	// invariant across concurrent writers.
	claims  []claimSpan
	writers map[*os.File]struct{}
}

// claimSpan is a half-open range a named source is fetching right now.
type claimSpan struct {
	lo, hi int64
	owner  string
}

// tryClaim registers [lo, hi) for owner unless it overlaps an existing
// claim (two selectors can race to the same gap; exactly one wins).
func (e *fileEntry) tryClaim(lo, hi int64, owner string) bool {
	e.mu.Lock()
	defer e.mu.Unlock()
	for _, c := range e.claims {
		if lo < c.hi && c.lo < hi {
			return false
		}
	}
	e.claims = append(e.claims, claimSpan{lo: lo, hi: hi, owner: owner})
	return true
}

// releaseClaims drops every claim held by owner.
func (e *fileEntry) releaseClaims(owner string) {
	e.mu.Lock()
	defer e.mu.Unlock()
	kept := e.claims[:0]
	for _, c := range e.claims {
		if c.owner != owner {
			kept = append(kept, c)
		}
	}
	e.claims = kept
}

// revokeClaimsOverlapping removes other owners' claims that overlap
// [lo, hi) and returns their owners, so the caller can nudge those
// sources to re-plan. Used when a seek makes a region urgent: the
// primary must not have to wait out a slower source's claim on the exact
// bytes mpv is blocked on.
func (e *fileEntry) revokeClaimsOverlapping(lo, hi int64, except string) []string {
	e.mu.Lock()
	defer e.mu.Unlock()
	var owners []string
	kept := e.claims[:0]
	for _, c := range e.claims {
		if c.owner != except && lo < c.hi && c.lo < hi {
			owners = append(owners, c.owner)
			continue
		}
		kept = append(kept, c)
	}
	e.claims = kept
	return owners
}

// nextUnclaimedGapLocked is nextGapLocked filtered by the claim table:
// the first missing-and-unclaimed range at or after from. mu must be
// held. With no claims (the single-source path) it is exactly
// nextGapLocked.
func (e *fileEntry) nextUnclaimedGapLocked(from int64) (gapLo, gapHi int64, ok bool) {
	for {
		gLo, gHi, found := e.nextGapLocked(from)
		if !found {
			return 0, 0, false
		}
		lo := gLo
		for advanced := true; advanced; {
			advanced = false
			for _, c := range e.claims {
				if lo >= c.lo && lo < c.hi {
					lo, advanced = c.hi, true
				}
			}
		}
		if lo >= gHi {
			from = gHi // the whole gap is claimed; look past it
			continue
		}
		hi := gHi
		for _, c := range e.claims {
			if c.lo > lo && c.lo < hi {
				hi = c.lo
			}
		}
		return lo, hi, true
	}
}

// nextUnclaimedGapWithinLocked is nextUnclaimedGapLocked additionally
// restricted to the given byte ranges (a source's advertised coverage,
// sorted): the first missing-and-unclaimed byte run at or after from that
// the source can actually serve. mu must be held.
func (e *fileEntry) nextUnclaimedGapWithinLocked(from int64, within [][2]int64) (gapLo, gapHi int64, ok bool) {
	for _, sp := range within {
		start := sp[0]
		if from > start {
			start = from
		}
		if start >= sp[1] {
			continue
		}
		lo, hi, found := e.nextUnclaimedGapLocked(start)
		if !found || lo >= sp[1] {
			continue
		}
		if hi > sp[1] {
			hi = sp[1]
		}
		return lo, hi, true
	}
	return 0, 0, false
}

// registerWriter/unregisterWriter track the open handles writing this
// file so checkpoints can sync all of them.
func (e *fileEntry) registerWriter(f *os.File) {
	e.mu.Lock()
	if e.writers == nil {
		e.writers = make(map[*os.File]struct{})
	}
	e.writers[f] = struct{}{}
	e.mu.Unlock()
}

func (e *fileEntry) unregisterWriter(f *os.File) {
	e.mu.Lock()
	delete(e.writers, f)
	e.mu.Unlock()
}

// getOutboard returns the verified outboard tree, or nil if it hasn't
// been obtained yet. setOutboard stores a just-verified tree and persists
// it as a sidecar so later sessions (and serving peers) don't refetch it.
func (e *fileEntry) getOutboard() []byte {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.outboard
}

func (e *fileEntry) setOutboard(ob []byte) {
	e.mu.Lock()
	e.outboard = ob
	e.mu.Unlock()
	// Write-then-rename, same crash discipline as the resume sidecar.
	tmp := e.obPath + ".tmp"
	if os.WriteFile(tmp, ob, 0o644) == nil {
		_ = os.Rename(tmp, e.obPath)
	}
}

// progressFile is the on-disk sidecar (next to the .part) that lets a
// download resume across sessions. Spans are stored as [lo,hi] pairs
// since span's fields are unexported.
type progressFile struct {
	Size   int64      `json:"size"`
	SHA256 string     `json:"sha256"`
	Spans  [][2]int64 `json:"spans"`
	// Bao records the room's announced bao root at the time the spans were
	// written. In a bao room every span in the sidecar is per-chunk
	// verified; a sidecar with a different (or no) root may hold bytes
	// that were never verified, so it can't be trusted for resume.
	Bao string `json:"bao,omitempty"`
}

// saveProgress flushes the received spans to the sidecar so a later
// session can resume. No-op once the file is complete or empty. f is the
// .part file's open handle, synced before the sidecar is written so a
// crash can never leave a sidecar claiming bytes are on disk that the OS
// hadn't actually flushed yet (a resumed download would then treat
// still-zero bytes as verified). f may be nil when there's nothing new
// to flush (e.g. a connect attempt failed before any bytes could be
// written this round) — the sync step is skipped in that case.
func (e *fileEntry) saveProgress(f *os.File) {
	if f != nil {
		_ = f.Sync() // best-effort: the sidecar write below is what changes correctness
	}
	// Concurrent swarm sources write through their own handles; their
	// bytes are in e.have too, so they must hit disk before the snapshot
	// below claims them. Collect under the lock, sync outside it.
	e.mu.Lock()
	others := make([]*os.File, 0, len(e.writers))
	for w := range e.writers {
		if w != f {
			others = append(others, w)
		}
	}
	e.mu.Unlock()
	for _, w := range others {
		_ = w.Sync()
	}
	e.mu.Lock()
	if e.done || len(e.have) == 0 {
		e.mu.Unlock()
		return
	}
	pf := progressFile{Size: e.meta.Size, SHA256: e.meta.SHA256, Bao: e.meta.Bao}
	for _, s := range e.have {
		pf.Spans = append(pf.Spans, [2]int64{s.lo, s.hi})
	}
	e.mu.Unlock()
	b, err := json.Marshal(pf)
	if err != nil {
		return
	}
	// Write-then-rename so a crash never leaves a half-written sidecar.
	tmp := e.metaPath + ".tmp"
	if os.WriteFile(tmp, b, 0o644) == nil {
		_ = os.Rename(tmp, e.metaPath)
	}
}

// loadProgress restores spans from the sidecar when it matches this
// file's size and hash and the .part is present at the right size.
// Returns true when the download can resume; otherwise it clears any
// stale sidecar.
func (e *fileEntry) loadProgress() bool {
	b, err := os.ReadFile(e.metaPath)
	if err != nil {
		return false
	}
	var pf progressFile
	if json.Unmarshal(b, &pf) != nil || pf.Size != e.meta.Size || pf.SHA256 != e.meta.SHA256 {
		os.Remove(e.metaPath) // stale (different file under the same name)
		return false
	}
	if pf.Bao != e.meta.Bao {
		// A sidecar written under a different verification regime (most
		// likely a pre-swarm session with no per-chunk verification) may
		// claim spans that were never verified against this room's root —
		// and would also break the invariant that spans are group-aligned.
		os.Remove(e.metaPath)
		return false
	}
	if fi, err := os.Stat(e.partPath); err != nil || fi.Size() != e.meta.Size {
		os.Remove(e.metaPath)
		return false
	}
	e.mu.Lock()
	for _, s := range pf.Spans {
		if s[0] >= 0 && s[1] <= e.meta.Size && s[0] < s[1] {
			e.addLocked(s[0], s[1])
		}
	}
	resumed := len(e.have) > 0
	e.mu.Unlock()
	return resumed
}

// addLocked merges [lo, hi) into the received set, coalescing touching
// and overlapping spans, and wakes blocked readers. mu must be held.
func (e *fileEntry) addLocked(lo, hi int64) {
	if hi <= lo {
		return
	}
	merged := make([]span, 0, len(e.have)+1)
	inserted := false
	for _, s := range e.have {
		switch {
		case s.hi < lo: // wholly before, with a gap
			merged = append(merged, s)
		case s.lo > hi: // wholly after, with a gap
			if !inserted {
				merged = append(merged, span{lo, hi})
				inserted = true
			}
			merged = append(merged, s)
		default: // overlaps or touches: absorb into [lo,hi)
			lo = min(lo, s.lo)
			hi = max(hi, s.hi)
		}
	}
	if !inserted {
		merged = append(merged, span{lo, hi})
	}
	e.have = merged
	e.cond.Broadcast()
}

func (e *fileEntry) add(lo, hi int64) {
	e.mu.Lock()
	e.addLocked(lo, hi)
	e.mu.Unlock()
}

// endOfRunLocked returns the end offset of the received run containing
// pos, or -1 if pos hasn't been received yet. mu must be held.
func (e *fileEntry) endOfRunLocked(pos int64) int64 {
	if e.done || pos >= e.meta.Size {
		return e.meta.Size
	}
	for _, s := range e.have {
		if pos >= s.lo && pos < s.hi {
			return s.hi
		}
	}
	return -1
}

// coveredLocked is the total number of received bytes. mu must be held.
func (e *fileEntry) coveredLocked() int64 {
	var n int64
	for _, s := range e.have {
		n += s.hi - s.lo
	}
	return n
}

func (e *fileEntry) covered() int64 {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.coveredLocked()
}

// nextGapLocked returns the first not-yet-received range at or after
// `from`: its start, the end of the gap (the next received byte, or
// size), and whether any gap remains. mu must be held.
func (e *fileEntry) nextGapLocked(from int64) (gapLo, gapHi int64, ok bool) {
	size := e.meta.Size
	pos := from
	if pos < 0 {
		pos = 0
	}
	for _, s := range e.have {
		if s.hi <= pos {
			continue // span is behind pos
		}
		if s.lo <= pos {
			pos = s.hi // pos sits inside this span; skip past it
			continue
		}
		return pos, s.lo, pos < size // gap is [pos, s.lo)
	}
	return pos, size, pos < size
}

// finishRename renames the .part file to its final path and marks the
// entry done in one atomic step (same lock as OpenFor). Renaming first
// and separately flipping done after would leave a window where the
// file has already moved but done still reads false — a concurrent
// OpenFor reading that stale false would try to open the now-nonexistent
// .part path and fail with ENOENT.
func (e *fileEntry) finishRename() error {
	e.mu.Lock()
	defer e.mu.Unlock()
	if err := os.Rename(e.partPath, e.finalPath); err != nil {
		return err
	}
	e.done = true
	e.cond.Broadcast()
	return nil
}

func (e *fileEntry) setErr(err error) {
	e.mu.Lock()
	if e.err == nil {
		e.err = err
	}
	e.cond.Broadcast()
	e.mu.Unlock()
}

// waitForByte blocks until byte pos is available, the file is done, or
// it errors (or ctx is canceled). It returns the end of the received run
// containing pos so the caller can serve it in one go.
func (e *fileEntry) waitForByte(ctx context.Context, pos int64) (end int64, done bool, err error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	stop := context.AfterFunc(ctx, func() {
		e.mu.Lock()
		e.cond.Broadcast()
		e.mu.Unlock()
	})
	defer stop()
	for {
		if e.err != nil {
			return 0, e.done, e.err
		}
		if ce := e.endOfRunLocked(pos); ce >= 0 {
			return ce, e.done, nil
		}
		if ctx.Err() != nil {
			return 0, e.done, ctx.Err()
		}
		e.cond.Wait()
	}
}

// FileStore holds the per-file download state for one room's playlist.
type FileStore struct {
	files []*fileEntry
}

func NewFileStore(dir string, metas []protocol.FileMeta) (*FileStore, error) {
	s := &FileStore{}
	used := make(map[string]bool)
	for _, m := range metas {
		if m.Size <= 0 {
			return nil, fmt.Errorf("host announced an empty file %q", m.Name)
		}
		// Two playlist entries can sanitize to the same basename (distinct
		// source paths that share a leaf name, or an adversarial host).
		// Without de-duplication they'd share one .part/sidecar pair and
		// clobber each other's downloads. The loop (rather than a simple
		// per-base counter) also guards against a colliding original name
		// like "movie-1.mp4" already claiming the suffix a duplicate would
		// otherwise be assigned.
		base := sanitizeName(m.Name)
		name := base
		for i := 1; used[name]; i++ {
			ext := filepath.Ext(base)
			name = fmt.Sprintf("%s-%d%s", strings.TrimSuffix(base, ext), i, ext)
		}
		used[name] = true
		final := filepath.Join(dir, name)
		e := &fileEntry{meta: m, partPath: final + ".part", finalPath: final, metaPath: final + ".part.junto"}
		e.cond = sync.NewCond(&e.mu)
		if m.Bao != "" {
			// validate() already bounds these fields at the decode boundary;
			// failing here anyway keeps a bad meta from silently downgrading
			// the room to unverified transfers.
			p, err := newBaoParams(m.Bao, m.BaoGroup, m.BaoOb, m.Size)
			if err != nil {
				return nil, fmt.Errorf("file %q announces unusable verification metadata: %w", m.Name, err)
			}
			e.bao = &p
			e.obPath = final + ".bao"
			// A prior session's outboard sidecar saves refetching ~1/4096 of
			// the file; it's only trusted if it still matches the announced
			// hash (the room may have been recreated around a different file).
			if b, err := os.ReadFile(e.obPath); err == nil {
				if verifyOutboard(b, p) {
					e.outboard = b
				} else {
					os.Remove(e.obPath)
				}
			}
		}
		e.resumed = e.loadProgress() // pick up an interrupted prior session
		s.files = append(s.files, e)
	}
	return s, nil
}

func (s *FileStore) Len() int                     { return len(s.files) }
func (s *FileStore) Meta(i int) protocol.FileMeta { return s.files[i].meta }

// Progress is overall download completion across every file (0–1), for
// the /peers download indicator.
func (s *FileStore) Progress() float64 {
	var have, total int64
	for _, e := range s.files {
		have += e.covered()
		total += e.meta.Size
	}
	if total == 0 {
		return 1
	}
	return float64(have) / float64(total)
}

// WaitForByte blocks until byte pos of file i is available, returning
// the contiguous end of the region it lands in.
func (s *FileStore) WaitForByte(ctx context.Context, i int, pos int64) (int64, bool, error) {
	return s.files[i].waitForByte(ctx, pos)
}

// Available reports, without blocking, whether byte pos of file i is
// already on disk, and the end of the received run it lands in. The
// stream server uses it to tell a stall (a seek past the download) from
// a hit.
func (s *FileStore) Available(i int, pos int64) (int64, bool) {
	e := s.files[i]
	e.mu.Lock()
	defer e.mu.Unlock()
	end := e.endOfRunLocked(pos)
	return end, end >= 0
}

// WaitBuffered blocks until at least minBytes of contiguous data exist
// starting from byte 0 of file i (or the file completes), or ctx is
// cancelled. Used by streaming joiners to hold the readiness gate until a
// real playback cushion exists. Mirrors waitForByte's ctx-aware cond
// pattern: an AfterFunc broadcasts on cancellation to wake the waiter,
// which then observes ctx.Err().
func (s *FileStore) WaitBuffered(ctx context.Context, i int, minBytes int64) error {
	e := s.files[i]
	e.mu.Lock()
	defer e.mu.Unlock()
	stop := context.AfterFunc(ctx, func() {
		e.mu.Lock()
		e.cond.Broadcast()
		e.mu.Unlock()
	})
	defer stop()
	for {
		if e.err != nil {
			return e.err
		}
		if e.done || e.endOfRunLocked(0) >= minBytes {
			return nil
		}
		if ctx.Err() != nil {
			return ctx.Err()
		}
		e.cond.Wait()
	}
}

// AllDone reports whether every file in the store — videos and subtitle
// sidecars alike — is fully downloaded, verified, and renamed into place.
// A streaming joiner becomes host-eligible the moment this flips true.
func (s *FileStore) AllDone() bool {
	for _, e := range s.files {
		e.mu.Lock()
		done := e.done
		e.mu.Unlock()
		if !done {
			return false
		}
	}
	return len(s.files) > 0
}

// OutboardFor returns file i's verified bao outboard tree for serving to
// other peers, or nil when it hasn't been fetched (or the room predates
// per-chunk verification).
func (s *FileStore) OutboardFor(i int) []byte { return s.files[i].getOutboard() }

// PathFor returns the file to read from: the final path once complete,
// otherwise the in-progress .part. Safe for logging/display, but not for
// opening the file yourself — see OpenFor.
func (s *FileStore) PathFor(i int) string {
	e := s.files[i]
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.done {
		return e.finalPath
	}
	return e.partPath
}

// Done reports whether file i has completed whole-file/Bao verification and the atomic
// .part-to-final rename. It is used by non-mpv frontends before exposing "save" actions.
func (s *FileStore) Done(i int) bool {
	if i < 0 || i >= len(s.files) {
		return false
	}
	e := s.files[i]
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.done
}

// ImportVerified installs a caller-provided local file only when its complete SHA-256 and Bao
// metadata match the host announcement. It runs before Downloader.Run, so a successful import is
// immediately available to the localhost stream and safe to advertise to swarm peers.
func (s *FileStore) ImportVerified(i int, sourcePath string) (bool, error) {
	if i < 0 || i >= len(s.files) {
		return false, fmt.Errorf("file index %d is out of range", i)
	}
	e := s.files[i]
	e.mu.Lock()
	alreadyDone := e.done
	e.mu.Unlock()
	if alreadyDone {
		return true, nil
	}
	info, err := os.Stat(sourcePath)
	if err != nil || !info.Mode().IsRegular() || info.Size() != e.meta.Size {
		return false, nil
	}
	hashes, err := HashForServe(sourcePath)
	if err != nil {
		return false, err
	}
	if hashes.SHA256 != e.meta.SHA256 ||
		hashes.BaoRoot != e.meta.Bao ||
		hashes.BaoGroup != e.meta.BaoGroup ||
		hashes.BaoObSHA != e.meta.BaoOb {
		return false, nil
	}

	if err := os.MkdirAll(filepath.Dir(e.partPath), 0o755); err != nil {
		return false, err
	}
	tempPath := e.partPath + ".import"
	source, err := os.Open(sourcePath)
	if err != nil {
		return false, err
	}
	temp, err := os.Create(tempPath)
	if err != nil {
		source.Close()
		return false, err
	}
	_, copyErr := io.Copy(temp, source)
	sourceErr := source.Close()
	syncErr := temp.Sync()
	closeErr := temp.Close()
	if copyErr != nil {
		os.Remove(tempPath)
		return false, copyErr
	}
	if sourceErr != nil {
		os.Remove(tempPath)
		return false, sourceErr
	}
	if syncErr != nil {
		os.Remove(tempPath)
		return false, syncErr
	}
	if closeErr != nil {
		os.Remove(tempPath)
		return false, closeErr
	}
	_ = os.Remove(e.partPath)
	if err := os.Rename(tempPath, e.partPath); err != nil {
		os.Remove(tempPath)
		return false, err
	}
	e.mu.Lock()
	e.have = []span{{lo: 0, hi: e.meta.Size}}
	e.outboard = append([]byte(nil), hashes.Outboard...)
	e.mu.Unlock()
	if e.obPath != "" {
		if err := os.WriteFile(e.obPath, hashes.Outboard, 0o600); err != nil {
			return false, err
		}
	}
	if err := e.finishRename(); err != nil {
		return false, err
	}
	os.Remove(e.metaPath)
	return true, nil
}

// OpenFor opens the current backing file for index i. Unlike calling
// PathFor and then os.Open separately, the done-check and the open
// happen under the same lock finishRename uses for the rename+done flip
// — so a caller can never be caught in between (reading "not done",
// deciding to open the .part path, and then actually opening it only
// after finishRename has already moved it to the final path, which
// would otherwise surface as a plain ENOENT).
func (s *FileStore) OpenFor(i int) (*os.File, error) {
	e := s.files[i]
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.done {
		return os.Open(e.finalPath)
	}
	return os.Open(e.partPath)
}

// Progress reports the live state of one file's download so the UI can
// render a bar. Received counts bytes of this file present on disk so
// far (0 → Total monotonically, across the tail and body segments);
// IsTail marks the initial index/tail prefetch. ETA is 0 when not yet
// estimable.
type Progress struct {
	Name        string
	IsTail      bool
	Received    int64
	Total       int64
	BytesPerSec float64
	ETA         time.Duration
}

// Downloader fetches a playlist's files from the host over WebRTC. It
// holds one persistent connection per file, streaming bytes continuously
// (index/tail region first so streaming can start before the body
// arrives) and redirecting that stream — without reconnecting — when a
// seek asks for a different region. A dropped connection resumes from
// the bytes already on disk.
type Downloader struct {
	sig      Signaler
	host     string
	ice      ICEConfig
	store    *FileStore
	sendReq  func(ctx context.Context, peer string, index int, offset, length int64) error
	printf   func(string, ...any)
	log      *debug.Logger
	progress func(Progress)
	onDone   func(index int) // fired after a file fully downloads + verifies

	seq atomic.Int64 // monotonic request sequence, stamped on each "start"

	// hostMu guards host and hostChangeCancel; SetHost may be called from
	// a different goroutine (the engine loop) while connect() runs in
	// Run's goroutine. hostChangeCancel cancels the in-flight connect()
	// attempt (if any) so a migration lands within a moment instead of
	// being discovered only after that attempt's offer/answer wait times
	// out — up to connectTimeout (30s) later, since connect() would
	// otherwise keep waiting on the old host's offer. Only one connect()
	// attempt is ever in flight at a time (Run processes files strictly
	// one at a time), so there's no need to track which attempt owns it.
	hostMu           sync.Mutex
	hostChangeCancel context.CancelFunc

	// Priority cursor: when set, the scheduler fetches around prioOff of
	// prioFile next (a seek the stream server jumped to). Written from
	// the stream server's goroutine via Prioritize, read by the fetch
	// loop. preempt nudges the in-flight range to redirect there at once.
	prioMu   sync.Mutex
	prioFile int
	prioOff  int64
	preempt  chan struct{}

	// Bandwidth-adaptive window. playheadFn returns the latest byte
	// served to mpv for the given file index (proxy for playback position).
	// rateEMA stores the smoothed download throughput in bytes/sec as
	// float64 bits so it can be read without a lock — under a swarm it is
	// the aggregate across sources, and primaryRate holds the primary
	// connection's own rate (which sizes its stride cap).
	playheadFn  func(i int) int64
	rateEMA     atomic.Uint64
	primaryRate atomic.Uint64

	// cov tracks every peer's advertised swarm coverage, fed from their
	// heartbeats via UpdatePeerHave/PeerGone (see swarm.go). sigFor and
	// swarm are set together by EnableSwarm; both nil means single-source
	// (the pre-swarm behavior, byte for byte).
	cov    peerCoverage
	sigFor func(peer string) (Signaler, func())
	swarm  *swarmState
}

func NewDownloader(sig Signaler, host string, store *FileStore, ice ICEConfig, sendReq func(context.Context, string, int, int64, int64) error, printf func(string, ...any)) *Downloader {
	return &Downloader{sig: sig, host: host, ice: ice, store: store, sendReq: sendReq, printf: printf, prioFile: -1, prioOff: -1, preempt: make(chan struct{}, 1)}
}

// EnableSwarm turns on multi-source fetching: sigFor opens (and later
// releases) a per-peer signal channel so aux sources can be dialed
// alongside the primary. Without it the downloader is single-source and
// behaves exactly as before the swarm existed.
func (d *Downloader) EnableSwarm(sigFor func(peer string) (Signaler, func())) {
	d.sigFor = sigFor
	d.swarm = &swarmState{sources: make(map[string]*sourceState)}
}

// SetLogger attaches a debug logger. Call once after NewDownloader.
func (d *Downloader) SetLogger(l *debug.Logger) { d.log = l }

// OnProgress registers a callback for live transfer progress. When set,
// per-byte progress flows to it (throttled) instead of being printed as
// text lines; the caller renders the bar. Safe to leave unset.
func (d *Downloader) OnProgress(fn func(Progress)) { d.progress = fn }

// OnFileDone registers a callback fired (from Run's goroutine) after
// each file finishes downloading and verifying — used to load shared
// subtitle files once they're complete on disk.
func (d *Downloader) OnFileDone(fn func(index int)) { d.onDone = fn }

// SetPlayheadFunc registers a function that returns the most recently
// served byte offset for file i (typically ss.CurrentReadPos). When set,
// the downloader limits its forward fetch to a sliding window ahead of
// the playhead sized to measured throughput.
func (d *Downloader) SetPlayheadFunc(fn func(i int) int64) { d.playheadFn = fn }

// GetHost returns the current host pubkey. Safe to call from any goroutine.
func (d *Downloader) GetHost() string {
	d.hostMu.Lock()
	defer d.hostMu.Unlock()
	return d.host
}

// SetHost redirects future connections to newPub. Safe to call concurrently
// with Run. The preempt nudge causes any in-flight range fetch to exit early
// and re-enter connect(), where it will pick up the new host pubkey. If a
// connect() attempt (offer/answer negotiation, not yet an established
// fetch loop) is in flight against the old host, it's also canceled
// directly — the likely case, since migration follows a host drop and a
// reconnect attempt is often already underway — so it fails fast and
// retries against newPub within a moment instead of only after that
// attempt's up-to-30s offer wait times out on a host that will never answer.
func (d *Downloader) SetHost(newPub string) {
	d.hostMu.Lock()
	d.host = newPub
	cancel := d.hostChangeCancel
	d.hostMu.Unlock()
	if cancel != nil {
		d.log.E("host_change_canceled_connect")
		cancel()
	}
	select {
	case d.preempt <- struct{}{}:
	default:
	}
	// Aux sources re-plan as well — one of them may be the new host (its
	// loop bows out so the primary connection takes over that peer).
	d.nudgeAux(-1, -1)
}

// armHostChangeCancel registers cancel as the func that aborts the
// caller's in-flight connect() attempt if SetHost changes the host
// before it finishes, and returns the host to connect to — both read
// under the same lock, so the attempt starts against a host that can't
// change out from under it without also canceling this exact attempt.
func (d *Downloader) armHostChangeCancel(cancel context.CancelFunc) string {
	d.hostMu.Lock()
	defer d.hostMu.Unlock()
	d.hostChangeCancel = cancel
	return d.host
}

// disarmHostChangeCancel clears the registered cancel func once a
// connect() attempt is over (however it ended), so a later SetHost has
// nothing stale to call.
func (d *Downloader) disarmHostChangeCancel() {
	d.hostMu.Lock()
	d.hostChangeCancel = nil
	d.hostMu.Unlock()
}

// currentRate returns the latest EMA throughput in bytes/sec.
func (d *Downloader) currentRate() float64 {
	return math.Float64frombits(d.rateEMA.Load())
}

// CurrentRate exports the EMA download throughput (bytes/sec) for the
// sync engine's stall-prediction check (syncer.Deps.CurrentRate).
func (d *Downloader) CurrentRate() float64 { return d.currentRate() }

// windowBytes returns the adaptive download window size: throughput ×
// target seconds, clamped between a floor and ceiling.
func (d *Downloader) windowBytes() int64 {
	w := int64(d.currentRate()) * windowTargetSecs
	if w < minWindowBytes {
		return minWindowBytes
	}
	if w > maxWindowBytes {
		return maxWindowBytes
	}
	return w
}

// Pre-buffer cushion parameters: the readiness gate holds until roughly
// preBufferTargetSecs of playback sits ahead of the start of the first
// file, instead of a fixed byte count — a low-bitrate file isn't forced
// to over-buffer, and a high-bitrate remux gets a real cushion instead of
// a blink's worth of bytes. minPreBufferBytes matches the previous fixed
// value (also the answer when bitrate is unknown — non-mp4, or an mp4
// whose moov/mvhd couldn't be parsed). maxPreBufferBytes bounds the worst
// case (a pathologically high bitrate) so buffering can't demand an
// unreasonable amount even then. vars per this package's
// test-shrinkability convention.
var (
	minPreBufferBytes int64 = 4 << 20  // 4 MB — previous fixed floor
	maxPreBufferBytes int64 = 64 << 20 // 64 MB — binds only above ~170 Mbps at the 3s target
)

const preBufferTargetSecs = 3 // seconds of playback the cushion targets

// PreBufferBytes returns the readiness-gate cushion for m: bitrate
// (Size/DurationSecs) × preBufferTargetSecs, clamped to
// [minPreBufferBytes, maxPreBufferBytes]. DurationSecs <= 0 means the
// bitrate isn't known — callers get the floor, i.e. the previous fixed
// 4 MB behavior, unchanged.
func PreBufferBytes(m protocol.FileMeta) int64 {
	if m.DurationSecs <= 0 || m.Size <= 0 {
		return minPreBufferBytes
	}
	bitrate := float64(m.Size) / m.DurationSecs
	w := int64(bitrate * preBufferTargetSecs)
	switch {
	case w < minPreBufferBytes:
		return minPreBufferBytes
	case w > maxPreBufferBytes:
		return maxPreBufferBytes
	default:
		return w
	}
}

// Prioritize asks the scheduler to fetch the region of file i around
// byte pos before continuing to fill earlier gaps — used when a
// streaming reader seeks past what's downloaded. It redirects the
// in-flight stream immediately. Cheap and lock-guarded; safe to call
// from any goroutine. The preempt nudge only fires when the target
// actually moves, so repeated stalls at the same offset don't churn.
func (d *Downloader) Prioritize(i int, pos int64) {
	d.prioMu.Lock()
	changed := d.prioFile != i || d.prioOff != pos
	d.prioFile, d.prioOff = i, pos
	d.prioMu.Unlock()
	if changed {
		select {
		case d.preempt <- struct{}{}:
		default:
		}
		// Aux sources re-plan too: claims overlapping the now-urgent
		// region are revoked so the primary isn't stuck waiting out a
		// slower source's claim on the exact bytes mpv is blocked on.
		d.nudgeAux(i, pos)
	}
}

func (d *Downloader) Store() *FileStore { return d.store }

// Run downloads every file in order. It records the first terminal
// error on the failing entry (so stream-server readers unblock) and
// returns it.
func (d *Downloader) Run(ctx context.Context) error {
	for i := range d.store.files {
		if err := d.runFile(ctx, i); err != nil {
			return err
		}
		if d.onDone != nil {
			d.onDone(i)
		}
	}
	return nil
}

func (d *Downloader) runFile(ctx context.Context, i int) error {
	e := d.store.files[i]
	size := e.meta.Size

	// Pre-size the .part as a sparse file so ranges can be written at
	// absolute offsets. When resuming a prior session we must NOT
	// truncate — the bytes already on disk are what we're resuming from.
	flag := os.O_RDWR | os.O_CREATE
	if !e.resumed {
		flag |= os.O_TRUNC
	}
	f, err := os.OpenFile(e.partPath, flag, 0o644)
	if err != nil {
		e.setErr(err)
		return fmt.Errorf("creating %s: %w", e.partPath, err)
	}
	if err := f.Truncate(size); err != nil { // idempotent when already `size`
		f.Close()
		e.setErr(err)
		return fmt.Errorf("sizing %s: %w", e.partPath, err)
	}
	f.Close()

	// fail discards a download that can't be trusted (terminal/disk/host
	// error), sidecar included.
	fail := func(err error) error {
		e.setErr(err)
		os.Remove(e.partPath)
		os.Remove(e.metaPath)
		return fmt.Errorf("downloading %s: %w", e.meta.Name, err)
	}

	// (Re)connect and download until every byte is received. One
	// connection serves the whole file and is reused across ranges and
	// seeks; only a drop, a stall, or a NAT-traversal failure on connect
	// starts a fresh attempt. The retry budget resets whenever progress
	// is made, so a long download survives several transient drops.
	// SECURITY: log the playlist index, not e.meta.Name — file names can
	// reveal what's being watched and the debug log is meant to be shared.
	d.log.E("download_start", "idx", i, "size", e.meta.Size, "resumed", e.resumed)
	// Swarm: fetch from advertising peers alongside the host. Stopped —
	// with a wait, so nothing still writes the file — before the final
	// hash and rename below, and on every error return.
	var stopAux func()
	if d.swarm != nil && e.bao != nil {
		stopAux = d.startAuxManager(ctx, i)
		defer stopAux()
	}
	dlStart := time.Now()
	backoff := 500 * time.Millisecond
	retries := 0
	verifyStrikes := 0
	lastCovered := e.covered()
	// strike records a verification failure attributable to the serving
	// peer. One strike gets a fresh connection (a transient corruption, or
	// a mid-write race on the host's file, deserves a second chance); a
	// second is terminal — the host's file simply doesn't match the hash
	// it announced, and every retry would fail the same way.
	strike := func(err error) (terminal error) {
		verifyStrikes++
		d.log.E("verify_strike", "idx", i, "count", verifyStrikes)
		if verifyStrikes >= 2 {
			return fail(fmt.Errorf("the host's data doesn't match the hash it announced: %w", err))
		}
		d.printf("* %s failed verification — reconnecting to retry...", e.meta.Name)
		return nil
	}
	for {
		conn, cerr := d.connect(ctx, i)
		if cerr != nil {
			var vErr verifyError
			if errors.As(cerr, &vErr) {
				// A forged or wrong outboard tree from the host counts as a
				// verification strike, not an ordinary connect failure.
				if terr := strike(cerr); terr != nil {
					return terr
				}
				if !sleepBackoff(ctx, &backoff) {
					return ctx.Err()
				}
				continue
			}
			if ctx.Err() != nil {
				// No conn (and so no new bytes) this attempt; nothing to
				// sync beyond what the last successful checkpoint already
				// flushed.
				e.saveProgress(nil)
				return ctx.Err()
			}
			if isNATFailure(cerr) {
				// Fetch (or refetch, if stale/never-succeeded) fallback
				// creds now — direct hole-punching just failed, so there's
				// a little time to spare before giving up on this
				// connection entirely.
				if !d.ice.HasRelay() && EnsureFallbackRelayFresh(ctx) {
					d.ice = d.ice.WithFallbackRelay()
					d.log.E("ice_fallback", "peer", d.GetHost()[:min(8, len(d.GetHost()))], "file", i)
					d.printf("* direct connection failed — retrying via relay…")
					continue
				}
				if d.ice.HasRelay() {
					return fail(fmt.Errorf("%w — even the TURN relay couldn't get through; check the -turn address and credentials, or get the files another way and re-run with them", cerr))
				}
				return fail(fmt.Errorf("%w — add -turn <relay> to route around it, or have everyone source the file locally and run: junto join <code> <file>", cerr))
			}
			if retries >= maxResumeRetries {
				return fail(cerr)
			}
			retries++
			if !sleepBackoff(ctx, &backoff) {
				return ctx.Err()
			}
			continue
		}

		derr := d.downloadFile(ctx, i, conn)
		if derr == nil {
			conn.finish() // tell the host it can stop serving
			conn.close()
			break // fully received
		}
		// Stop receiving before snapshotting e.have for the checkpoint —
		// otherwise an onMessage callback still in flight could add bytes
		// to e.have right after saveProgress reads it, so the sidecar
		// under-reports what a concurrent covered() call (e.g. the
		// caller's own bookkeeping) sees moments later. conn.f stays open
		// a little longer so saveProgress can sync it before the sidecar
		// claims those bytes are safely on disk; close it after.
		conn.stopReceiving()
		if ctx.Err() != nil {
			e.saveProgress(conn.f) // keep the .part + sidecar so a rejoin resumes
			conn.f.Close()
			return ctx.Err()
		}
		var vErr verifyError
		if errors.As(derr, &vErr) {
			// Only verified bytes ever enter e.have, so the checkpoint
			// below stays trustworthy even though this peer just sent
			// garbage.
			e.saveProgress(conn.f)
			conn.f.Close()
			if terr := strike(derr); terr != nil {
				return terr
			}
			if !sleepBackoff(ctx, &backoff) {
				return ctx.Err()
			}
			continue
		}
		if !isResumable(derr) {
			conn.f.Close()
			return fail(derr)
		}
		e.saveProgress(conn.f)
		conn.f.Close()
		if cur := e.covered(); cur > lastCovered { // progress made: refresh the budget
			retries, backoff, lastCovered = 0, 500*time.Millisecond, cur
		}
		if retries >= maxResumeRetries {
			return fail(derr)
		}
		retries++
		d.printf("* %s dropped at %s — resuming (attempt %d)...", e.meta.Name, human.Bytes(e.covered()), retries)
		if !sleepBackoff(ctx, &backoff) {
			return ctx.Err()
		}
	}

	// Quiesce the swarm before hashing/renaming: stopAux waits for every
	// aux goroutine, so no handle still writes the file.
	if stopAux != nil {
		stopAux()
	}
	// Verify the whole file from disk (ranges arrive out of order, so a
	// streaming hash isn't possible; one sequential read is cheap next to
	// the download that just finished).
	sum, err := hashFileOnDisk(e.partPath)
	if err != nil {
		return fail(err)
	}
	if err := e.finishRename(); err != nil {
		return fail(fmt.Errorf("renaming download: %w", err))
	}
	os.Remove(e.metaPath) // resume sidecar no longer needed
	ms := time.Since(dlStart).Milliseconds()
	var bps int64
	if ms > 0 {
		bps = e.meta.Size * 1000 / ms
	}
	d.log.E("download_done", "idx", i, "ms", ms, "bps", bps)
	if sum != e.meta.SHA256 {
		d.printf("* WARNING: %s failed its sha256 check — it may be corrupted", e.meta.Name)
	} else {
		d.printf("* %s complete, sha256 verified", e.meta.Name)
	}
	return nil
}

// sleepBackoff waits out the current backoff, doubling it (capped at 4s)
// for next time. It returns false if ctx is canceled during the wait.
func sleepBackoff(ctx context.Context, backoff *time.Duration) bool {
	select {
	case <-ctx.Done():
		return false
	case <-time.After(*backoff):
	}
	if *backoff < 4*time.Second {
		*backoff *= 2
	}
	return true
}

// downloadFile streams every missing byte of file i over conn: the
// index/tail region first (so a streaming player can start), then the
// remaining gaps lowest-first, honoring seek redirects. It returns nil
// once the file is fully received, a resumableError on a dropped or
// stalled connection, ctx.Err() on cancel, or a terminal host error.
func (d *Downloader) downloadFile(ctx context.Context, i int, conn *fileConn) error {
	e := d.store.files[i]

	// Index prefetch: fetch the region holding the media index before the
	// body so a streaming player can start. A seek mid-prefetch
	// (errPreempted) just drops us into the gap loop, which honors the new
	// priority.
	if err := d.prefetchIndex(ctx, i, conn); err != nil && err != errPreempted {
		return err
	}

	for {
		// Drain right before consulting priority state, not after: a
		// redirect that lands between this drain and the fetch call below
		// is either seen fresh by nextFetchGap (correct gap picked first
		// try) or still sits in the channel for fetch's own select to
		// catch immediately (errPreempted, re-evaluated next iteration).
		// Draining any later than this (e.g. at the top of fetch, as it
		// used to) can swallow a redirect that arrived in the gap between
		// the snapshot and the drain, silently losing the seek until
		// whatever stale range is already in flight finishes on its own.
		d.drainPreempt()
		gapLo, gapHi, st := d.nextFetchGap(i)
		switch st {
		case gapDone:
			return nil // fully received
		case gapWaiting:
			// Window ahead of playhead is satisfied. Keep reporting our
			// (now healthy) buffer depth so the source knows we're
			// well-cushioned and can favor hungrier viewers, then block
			// until a seek redirect (preempt) arrives or the playhead
			// advances.
			d.reportCushion(conn)
			select {
			case <-d.preempt:
			case <-time.After(500 * time.Millisecond):
			case <-ctx.Done():
				return ctx.Err()
			}
			continue
		}
		// Under a swarm, claim the range so aux sources skip it; losing
		// the race (an aux claimed overlapping bytes between selection and
		// here) just means re-planning.
		claimed := false
		if d.swarm != nil {
			if !e.tryClaim(gapLo, gapHi, "primary") {
				continue
			}
			claimed = true
		}
		err := d.fetch(ctx, conn, gapLo, gapHi-gapLo, false)
		if claimed {
			e.releaseClaims("primary")
		}
		if err == errPreempted {
			continue // a seek moved the priority; re-evaluate the next gap
		}
		if err != nil {
			return err
		}
		e.saveProgress(conn.f) // checkpoint between completed ranges (crash safety)
	}
}

// prefetchIndex fetches the region holding the media index before the
// body, so a streaming player can parse it and start. For an mp4-family
// file it locates the moov box exactly (any size, wherever it sits);
// for everything else — and whenever the mp4 walk can't pin it down — it
// falls back to the file's tail, where mkv Cues and non-faststart moov
// atoms live. Files small enough that the body covers the index quickly
// skip a separate prefetch.
func (d *Downloader) prefetchIndex(ctx context.Context, i int, conn *fileConn) error {
	e := d.store.files[i]
	size := e.meta.Size
	if size <= tailPrefetch {
		return nil
	}
	off, length, ok, err := d.locateMoov(ctx, i, conn)
	if err != nil {
		return err // errPreempted (a seek took over) or resumable/ctx — caller handles
	}
	if !ok {
		off, length = size-tailPrefetch, tailPrefetch
	}
	return d.fetchIfMissing(ctx, i, conn, off, length)
}

// fetchIfMissing streams [off, off+length) unless it's already on disk.
// A seek that redirects mid-fetch (errPreempted) is not an error: the
// body gap loop will honor the new priority and fill this region later.
func (d *Downloader) fetchIfMissing(ctx context.Context, i int, conn *fileConn, off, length int64) error {
	e := d.store.files[i]
	e.mu.Lock()
	have := e.endOfRunLocked(off) >= off+length
	e.mu.Unlock()
	if have {
		return nil
	}
	d.drainPreempt() // discard a stale nudge from an unrelated, already-handled redirect
	if err := d.fetch(ctx, conn, off, length, true); err != nil && err != errPreempted {
		return err
	}
	return nil
}

// moovWalkLimit bounds how many top-level boxes locateMoov inspects
// before giving up and falling back to the tail, so a malformed or
// adversarial file can't drive an unbounded number of header fetches.
const moovWalkLimit = 64

// locateMoov finds the moov box's exact offset and length by walking an
// mp4 file's top-level boxes, reading each box header over the connection
// (skipping mdat by its size). See findMoovBox for the parsing rules and
// the meaning of ok; a connection error or seek redirect surfaces as err.
func (d *Downloader) locateMoov(ctx context.Context, i int, conn *fileConn) (off, length int64, ok bool, err error) {
	size := d.store.files[i].meta.Size
	return findMoovBox(size, func(at, n int64) ([]byte, error) {
		return d.readRange(ctx, i, conn, at, n)
	})
}

// findMoovBox walks the top-level boxes of an mp4 file of the given size,
// using readHeader to fetch each box's header bytes, and returns the
// moov box's [off, off+length). ok is false (with nil err) when the file
// isn't mp4-family (first box isn't ftyp), the structure is malformed, or
// no moov appears within moovWalkLimit boxes — the caller then falls back
// to the tail. readHeader's error (e.g. a dropped connection or a seek
// redirect) is propagated as err.
func findMoovBox(size int64, readHeader func(off, n int64) ([]byte, error)) (off, length int64, ok bool, err error) {
	var pos int64
	for box := 0; box < moovWalkLimit && pos+8 <= size; box++ {
		boxLen, typ, hok, herr := parseBoxHeader(pos, size, readHeader)
		if herr != nil {
			return 0, 0, false, herr
		}
		if !hok {
			return 0, 0, false, nil // malformed: bail to the tail heuristic
		}
		if box == 0 && typ != "ftyp" {
			return 0, 0, false, nil // not an mp4-family container; fall back to tail
		}
		if typ == "moov" {
			return pos, boxLen, true, nil
		}
		pos += boxLen
	}
	return 0, 0, false, nil
}

// readRange fetches [off, off+n) and returns those bytes read back from
// the .part. Used to read mp4 box headers during the moov walk; the
// bytes are real file content, so they also count toward the download.
func (d *Downloader) readRange(ctx context.Context, i int, conn *fileConn, off, n int64) ([]byte, error) {
	d.drainPreempt() // discard a stale nudge from an unrelated, already-handled redirect
	if err := d.fetch(ctx, conn, off, n, true); err != nil {
		return nil, err
	}
	f, err := os.Open(d.store.files[i].partPath)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	buf := make([]byte, n)
	if _, err := f.ReadAt(buf, off); err != nil {
		return nil, err
	}
	return buf, nil
}

// nextFetchGap picks the next range to fetch for file i. It honours a
// pending seek priority, then falls back to filling the lowest remaining
// gap within the adaptive download window. Returns gapDone when the file
// is fully received, gapWaiting when the window ahead of the playhead is
// already satisfied (caller should idle until the playhead advances or a
// seek redirect arrives), and gapFound with [gapLo, gapHi) to fetch.
func (d *Downloader) nextFetchGap(i int) (gapLo, gapHi int64, s gapStatus) {
	e := d.store.files[i]
	d.prioMu.Lock()
	prioFile, prioOff := d.prioFile, d.prioOff
	d.prioMu.Unlock()

	e.mu.Lock()
	defer e.mu.Unlock()

	// With swarm sources available, cap each primary fetch at a few
	// seconds of its own throughput so aux claims can land inside the
	// window instead of behind one giant fetch. No candidates ⇒ no cap ⇒
	// selection identical to the single-source downloader (claims are
	// then empty, so the claim-aware selectors reduce to the plain ones).
	stride := int64(-1)
	if d.strideCapActive(i) {
		stride = d.primaryStride()
	}

	// A pending seek priority always wins — fetch around it regardless of
	// the playhead window (mpv needs those bytes right now) — but it's
	// still clamped to one window's worth starting at the seek: the gap
	// selector returns the gap up to the next already-downloaded span or
	// EOF, whichever comes first, which for a large file with nothing
	// downloaded past the seek is the rest of the file. Without this
	// clamp, one seek near the front of a large file fetches the entire
	// remainder in a single unbounded request instead of a bounded window.
	if prioFile == i && prioOff >= 0 && e.endOfRunLocked(prioOff) < 0 {
		lo, hi, ok := e.nextUnclaimedGapLocked(prioOff)
		if !ok {
			// Everything urgent is claimed by another source (transient: a
			// seek revokes overlapping aux claims and nudges their owners)
			// — idle briefly rather than mis-reporting done.
			return 0, 0, gapWaiting
		}
		if limit := prioOff + d.windowBytes(); hi > limit {
			hi = limit
		}
		if stride > 0 && hi > lo+stride {
			hi = lo + stride
		}
		// hi may degenerate to lo when the window is zero (rate still
		// unmeasured with the floor shrunk in tests); fetch treats a
		// zero-length range as to-EOF, matching pre-claim behavior.
		return lo, hi, gapFound
	}

	// Compute the window end if a playhead function is registered.
	windowEnd := int64(-1) // -1 = no limit
	if d.playheadFn != nil {
		playhead := d.playheadFn(i)
		windowEnd = playhead + d.windowBytes()
	}
	// A priority pointing at a *different* file means the playhead moved
	// on (a playlist jump) — nothing reads this file anymore, so its
	// playhead-follower window is frozen and would never grow again;
	// honoring it would report gapWaiting forever and hang Run() on this
	// file while mpv blocks waiting for the file the room actually
	// jumped to. Rush this file to completion instead (unclamped) so
	// Run() can reach the prioritized file; once i==prioFile, the branch
	// above takes over and fetches exactly what mpv is waiting on first.
	if prioFile >= 0 && prioFile != i {
		windowEnd = -1
	}

	lo, hi, ok := e.nextUnclaimedGapLocked(0)
	if !ok {
		// No unclaimed gap left: the file is done if nothing at all is
		// missing; otherwise other sources hold claims on the remainder —
		// gapDone must never fire while bytes are merely claimed.
		if _, _, missing := e.nextGapLocked(0); !missing {
			return 0, 0, gapDone
		}
		return 0, 0, gapWaiting
	}
	if windowEnd >= 0 && lo >= windowEnd {
		return 0, 0, gapWaiting // window satisfied; nothing to do yet
	}
	// Clamp the fetch to the window boundary so we don't overshoot.
	if windowEnd >= 0 && hi > windowEnd {
		hi = windowEnd
	}
	if stride > 0 && hi > lo+stride {
		hi = lo + stride
	}
	return lo, hi, gapFound
}

// drainPreempt clears a pending redirect nudge without blocking. Callers
// use it to discard a stale signal immediately before making a decision
// that will itself account for the current priority state (nextFetchGap)
// or before starting a fetch that isn't subject to one (prefetch/index
// reads) — so a leftover nudge from an unrelated, already-handled redirect
// doesn't cut the next fetch short before it even gets going. It must run
// *before* that decision, not after (inside fetch itself): draining after
// the decision was made can swallow a redirect that arrived in between,
// silently losing the seek until the in-flight range finishes on its own.
func (d *Downloader) drainPreempt() {
	select {
	case <-d.preempt:
	default:
	}
}

// stallCheckInterval is how often fetch's watchdog polls for byte-count
// progress. A var (not const) so tests can shrink it instead of waiting
// out the real interval.
var stallCheckInterval = 2 * time.Second

// fetch streams [off, off+length) of conn's file (length 0 ⇒ to EOF) by
// asking the host with a "start" frame and consuming bytes until the
// matching "eof". It returns errPreempted the instant a seek redirects
// elsewhere (Prioritize), nil on completion, a resumableError on a drop
// or stall, ctx.Err() on cancel, or a host-side error. isTail only
// labels progress. Callers are responsible for draining a stale preempt
// nudge (via drainPreempt) before invoking fetch, at whatever point they
// last consult priority state — see drainPreempt's comment.
func (d *Downloader) fetch(ctx context.Context, conn *fileConn, off, length int64, isTail bool) error {
	return d.fetchOn(ctx, conn, d.preempt, off, length, isTail)
}

// reportCushion tells the peer serving conn how much buffer this joiner
// has ahead of its playhead (contiguous downloaded bytes), so the peer can
// serve the hungriest of its viewers first (upload fairness). It's a
// "buf" frame, sent from the run goroutine so it never races the fetch
// loop's own sends. No-op for a non-streaming bulk fetch (no playhead):
// such a download isn't playing, so it must not preempt streaming viewers.
func (d *Downloader) reportCushion(conn *fileConn) {
	if conn == nil || conn.dc == nil {
		return
	}
	cushion, ok := d.cushionAhead(conn.idx)
	if !ok {
		return
	}
	_ = conn.dc.SendText(marshalCtrl(ctrl{T: "buf", Off: cushion}))
}

// cushionAhead is the joiner's buffer depth for file idx: contiguous
// downloaded bytes ahead of the playhead (0 when the playhead byte isn't
// downloaded yet). ok is false for a non-streaming bulk fetch (no
// playhead registered), which reports nothing so it never preempts
// streaming viewers.
func (d *Downloader) cushionAhead(idx int) (int64, bool) {
	if d.playheadFn == nil {
		return 0, false
	}
	playhead := d.playheadFn(idx)
	e := d.store.files[idx]
	e.mu.Lock()
	end := e.endOfRunLocked(playhead)
	e.mu.Unlock()
	if end < 0 {
		return 0, true // playhead not downloaded yet → maximally hungry
	}
	return end - playhead, true
}

// fetchOn is fetch parameterized by the preempt channel, so each swarm
// source can be redirected independently: the primary listens on the
// downloader-wide d.preempt, aux sources on their own per-source nudge.
func (d *Downloader) fetchOn(ctx context.Context, conn *fileConn, preempt <-chan struct{}, off, length int64, isTail bool) error {
	if e := conn.entry; e.bao != nil {
		// Requests must land on chunk-group boundaries so every received
		// group is verifiable (bao verifies whole groups; only the file's
		// final group may be short). Expanding the range re-fetches at most
		// one partial group per side; the overlap coalesces harmlessly in
		// e.have. Gap boundaries are usually aligned already (verified
		// spans hold whole groups) — this rounds the rest: seek offsets,
		// the tail-prefetch start, and mp4 box-walk reads.
		g := e.bao.groupBytes
		alo := alignDown(off, g)
		if length > 0 {
			length = alignUp(off+length, g, e.meta.Size) - alo
		}
		off = alo
	}
	seq := d.seq.Add(1)
	conn.isTail.Store(isTail)
	startGot := conn.got.Load()
	startVGot := conn.vgot.Load()
	if err := conn.dc.SendText(marshalCtrl(ctrl{T: "start", Seq: seq, Off: off, Len: length})); err != nil {
		return resumableError{fmt.Errorf("requesting range: %w", err)}
	}

	watchdog := time.NewTicker(stallCheckInterval)
	defer watchdog.Stop()
	lastGot, lastChange := conn.got.Load(), time.Now()
	d.reportCushion(conn) // tell the source our buffer depth as this range begins
	for {
		select {
		case s := <-conn.eofCh:
			if s == seq {
				if length > 0 && conn.got.Load() == startGot {
					// The host answered a real gap with eof and zero
					// bytes: its file is shorter than the size it
					// announced (FileMeta.Size), or has changed since.
					// Retrying would just re-request the same gap and
					// get the same empty answer forever — this is not
					// resumable, unlike a genuine drop.
					return fmt.Errorf("host's file is shorter than announced, or has changed (range %d+%d)", off, length)
				}
				if conn.entry.bao != nil && length > 0 && conn.vgot.Load() == startVGot {
					// Bytes arrived but not one chunk group completed
					// verification — nothing entered e.have, so the gap
					// loop would re-request the exact same range forever
					// against a host that keeps under-delivering. Surface
					// it as a drop so the retry budget bounds it.
					return resumableError{fmt.Errorf("range ended before any chunk could be verified")}
				}
				return nil
			}
			// stale eof from a range a redirect superseded — ignore it.
		case err := <-conn.errCh:
			return err
		case <-conn.connFailed:
			return resumableError{fmt.Errorf("connection lost")}
		case <-preempt:
			return errPreempted
		case <-ctx.Done():
			return ctx.Err()
		case <-watchdog.C:
			d.reportCushion(conn) // refresh our buffer depth for the source
			if g := conn.got.Load(); g != lastGot {
				lastGot, lastChange = g, time.Now()
			} else if time.Since(lastChange) > connectTimeout {
				return resumableError{fmt.Errorf("transfer stalled")}
			}
		}
	}
}

// connect establishes one persistent WebRTC connection to the host for
// file i: it asks the host to start serving (a single relay message),
// answers the host's offer, and waits for the data channel to open. A
// failure to connect at all is classified as a NAT error so the caller
// can fall back to a relay.
func (d *Downloader) connect(ctx context.Context, i int) (*fileConn, error) {
	// hostCtx — not ctx itself — bounds only the two waits below that can
	// genuinely block for a long time on the old host after a migration
	// (waitSignal's offer wait, and the final "did the channel open"
	// wait). ctx itself is left untouched for sendReq/SendSignal/
	// awaitGathering: those are quick, one-shot, or already
	// self-bounded, and at least one real caller (a relay send tied to a
	// longer-lived request) depends on ctx's cancellation meaning
	// something broader than "this one connect() attempt" — canceling it
	// the instant connect() returns would cut that short.
	hostCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	host := d.armHostChangeCancel(cancel)
	defer d.disarmHostChangeCancel()

	d.log.E("ice_start", "peer", host[:min(8, len(host))], "file", i, "has_relay", d.ice.HasRelay())
	iceStart := time.Now()
	e := d.store.files[i]
	f, err := os.OpenFile(e.partPath, os.O_RDWR, 0o644)
	if err != nil {
		return nil, fmt.Errorf("opening %s: %w", e.partPath, err)
	}

	// Discard any signal still queued from a previous attempt (this file's
	// own retry, or the tail of the last file's negotiation) before asking
	// for a fresh one — otherwise waitSignal below could pair this
	// attempt's answer with a dead attempt's stale offer, silently
	// desyncing the handshake until it burns the full connectTimeout.
	drainSignals(d.sig)

	if d.sendReq != nil {
		// Offset/length are unused now — byte ranges flow on the channel —
		// but the signature is kept so the host's request handler is one
		// spawn per file rather than per range.
		if err := d.sendReq(ctx, host, i, 0, 0); err != nil {
			f.Close()
			return nil, err
		}
	}

	pc, err := d.ice.newPeerConnection()
	if err != nil {
		f.Close()
		return nil, fmt.Errorf("creating peer connection: %w", err)
	}
	connFailed := watchConnection(pc)
	conn := &fileConn{
		pc: pc, f: f, idx: i, entry: e, connFailed: connFailed, log: d.log,
		eofCh: make(chan int64, 4), errCh: make(chan error, 1),
		emit: d.progress, printf: d.printf, lastPrint: time.Now(),
		reportRate: d.reportPrimaryRate,
	}
	if e.bao != nil {
		if ob := e.getOutboard(); ob != nil {
			conn.verifier.Store(newGroupVerifier(*e.bao, ob))
		} else {
			// Arm the outboard fetch before the channel can deliver
			// anything, so OnMessage never sees these fields change under
			// it. The placeholder verifier has no tree yet, so any file
			// bytes a hostile host pushes before the fetch completes fail
			// verification instead of slipping through unverified.
			conn.obWant.Store(int64(e.bao.obLen))
			conn.obCh = make(chan obFetch, 1)
			conn.verifier.Store(newGroupVerifier(*e.bao, nil))
		}
	}

	opened := make(chan struct{})
	var once sync.Once
	pc.OnDataChannel(func(dc *webrtc.DataChannel) {
		conn.dc = dc
		dc.OnOpen(func() { once.Do(func() { close(opened) }) })
		dc.OnMessage(conn.onMessage)
	})

	offer, err := waitSignal(hostCtx, d.sig, host, "offer")
	if err != nil {
		conn.close()
		return nil, err
	}
	if err := pc.SetRemoteDescription(webrtc.SessionDescription{Type: webrtc.SDPTypeOffer, SDP: offer.SDP}); err != nil {
		conn.close()
		return nil, fmt.Errorf("applying offer: %w", err)
	}
	answer, err := pc.CreateAnswer(nil)
	if err != nil {
		conn.close()
		return nil, fmt.Errorf("creating answer: %w", err)
	}
	if err := pc.SetLocalDescription(answer); err != nil {
		conn.close()
		return nil, fmt.Errorf("setting local description: %w", err)
	}
	if err := awaitGathering(ctx, pc); err != nil {
		conn.close()
		return nil, fmt.Errorf("gathering ICE candidates: %w", err)
	}
	if err := d.sig.SendSignal(ctx, host, protocol.Signal{Kind: "answer", SDP: pc.LocalDescription().SDP}); err != nil {
		conn.close()
		return nil, fmt.Errorf("sending answer: %w", err)
	}

	select {
	case <-opened:
		d.log.E("ice_open", "peer", host[:min(8, len(host))], "file", i, "ms", time.Since(iceStart).Milliseconds())
		if e.bao != nil {
			if err := d.ensureOutboard(ctx, conn, e); err != nil {
				conn.close()
				return nil, err
			}
			conn.verifier.Store(newGroupVerifier(*e.bao, e.getOutboard()))
		}
		return conn, nil
	case <-connFailed:
		conn.close()
		d.log.E("ice_failed", "peer", host[:min(8, len(host))], "file", i, "reason", "nat", "ms", time.Since(iceStart).Milliseconds())
		return nil, natError{fmt.Errorf("couldn't connect directly to the host — your network or the host's may be blocking peer-to-peer connections")}
	case <-time.After(connectTimeout):
		conn.close()
		d.log.E("ice_failed", "peer", host[:min(8, len(host))], "file", i, "reason", "timeout", "ms", time.Since(iceStart).Milliseconds())
		return nil, natError{fmt.Errorf("timed out waiting for a direct connection — your network or the host's may be blocking peer-to-peer connections")}
	case <-hostCtx.Done():
		conn.close()
		return nil, hostCtx.Err()
	}
}

// ensureOutboard makes sure e's bao outboard tree is available before any
// range is requested on conn, fetching it over the channel (ob_req) when
// no prior session left a valid sidecar. It then belongs to the entry for
// the file's whole lifetime, shared by every later connection. The wait
// uses fetch's progress-based patience rather than a fixed deadline: a
// promoted host may legitimately spend a while computing the tree from
// its local file before the first byte arrives.
func (d *Downloader) ensureOutboard(ctx context.Context, conn *fileConn, e *fileEntry) error {
	if e.getOutboard() != nil {
		// Another connection won the race to fetch the tree between this
		// conn's construction and now (the primary and an aux both start
		// armed when no sidecar exists). Disarm this conn's ob mode — an
		// armed conn diverts every binary frame into the tree buffer,
		// which would silently swallow the file bytes themselves. No
		// "start" has been sent yet, so nothing file-bound is in flight.
		conn.obWant.Store(0)
		return nil
	}
	if err := conn.dc.SendText(marshalCtrl(ctrl{T: "ob_req", Seq: d.seq.Add(1)})); err != nil {
		return resumableError{fmt.Errorf("requesting verification tree: %w", err)}
	}
	watchdog := time.NewTicker(stallCheckInterval)
	defer watchdog.Stop()
	lastGot, lastChange := int64(0), time.Now()
	for {
		select {
		case res := <-conn.obCh:
			if res.err != nil {
				return resumableError{fmt.Errorf("fetching verification tree: %w", res.err)}
			}
			if !verifyOutboard(res.ob, *e.bao) {
				return verifyError{"the verification tree doesn't match the announced hash"}
			}
			e.setOutboard(res.ob)
			d.log.E("ob_fetched", "bytes", len(res.ob))
			return nil
		case err := <-conn.errCh:
			return err
		case <-conn.connFailed:
			return resumableError{fmt.Errorf("connection lost")}
		case <-ctx.Done():
			return ctx.Err()
		case <-watchdog.C:
			if g := conn.obGot.Load(); g != lastGot {
				lastGot, lastChange = g, time.Now()
			} else if time.Since(lastChange) > connectTimeout {
				return resumableError{fmt.Errorf("timed out fetching the verification tree")}
			}
		}
	}
}

// fileConn is one persistent connection serving a single file. Its
// receive state (curSeq, writePos) is touched only from pion's OnMessage
// goroutine, which is never concurrent with itself; got is atomic
// because the fetch watchdog reads it, and isTail because fetch sets it.
type fileConn struct {
	pc *webrtc.PeerConnection
	dc *webrtc.DataChannel
	f  *os.File

	idx        int // playlist index this connection is downloading
	entry      *fileEntry
	connFailed <-chan struct{}
	eofCh      chan int64 // completed range seqs, from the host's "eof"
	errCh      chan error // host-side error frames
	log        *debug.Logger

	curSeq   int64 // seq of the range the current bytes belong to (unused beyond tracing)
	writePos int64 // absolute offset the next binary chunk lands at
	sawAt    bool  // an "at" frame has set writePos at least once
	isTail   atomic.Bool
	got      atomic.Int64

	// Per-chunk verification (bao rooms only; all nil/zero otherwise).
	// verifier gates every binary frame: bytes reach the file and e.have
	// only after their chunk group verifies against the announced root.
	// It's an atomic pointer because connect() swaps in the tree-bearing
	// verifier after the outboard fetch while pion's OnMessage goroutine
	// reads it; until then a placeholder with no tree fails everything
	// closed. vgot counts verified bytes so fetch can tell "peer sent
	// something" from "peer sent something usable". The ob* fields drive
	// the one-off outboard fetch that precedes any range request: while
	// obWant > 0, binary frames accumulate into obBuf instead of the file,
	// and the finished blob (or failure) is handed to the waiting connect
	// goroutine via obCh — after which OnMessage never touches the buffer
	// again, so there is no shared ob state post-handoff. obWant is
	// atomic because ensureOutboard must be able to disarm it when
	// another connection won the race to fetch the tree first (an armed
	// conn would otherwise divert every file byte into obBuf forever);
	// obBuf is only ever touched by OnMessage while obWant is armed.
	// obGot is atomic for the waiter's stall watchdog.
	verifier atomic.Pointer[groupVerifier]
	vgot     atomic.Int64
	obWant   atomic.Int64
	obBuf    []byte
	obGot    atomic.Int64
	obCh     chan obFetch

	// progress reporting (throttled), mirroring the old segReceiver.
	emit       func(Progress)
	reportRate func(float64) // called each tick with the updated EMA rate
	printf     func(string, ...any)
	lastPrint  time.Time
	lastBytes  int64
	rate       float64 // smoothed bytes/sec (EMA)
}

func (c *fileConn) close() {
	c.stopReceiving()
	c.f.Close()
}

// stopReceiving closes the peer connection without closing the file
// handle, so no further onMessage callback can land a write after this
// point. Used when a caller needs e.have to be quiesced before
// snapshotting it (a resume checkpoint) but still needs the file open a
// little longer (to sync it) before closing it too.
func (c *fileConn) stopReceiving() {
	c.pc.Close()
}

// finish tells the host the whole file is received so it can stop
// serving, then briefly lets the frame flush before the caller closes.
func (c *fileConn) finish() {
	if c.dc == nil {
		return
	}
	_ = c.dc.SendText(marshalCtrl(ctrl{T: "done"}))
	deadline := time.Now().Add(time.Second)
	for c.dc.BufferedAmount() > 0 && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	time.Sleep(100 * time.Millisecond)
}

func (c *fileConn) onMessage(msg webrtc.DataChannelMessage) {
	if msg.IsString {
		var ct ctrl
		if err := unmarshalCtrl(msg.Data, &ct); err != nil {
			return
		}
		switch ct.T {
		case "at": // subsequent bytes begin at this absolute offset
			c.curSeq, c.writePos = ct.Seq, ct.Off
			c.sawAt = true
			if v := c.verifier.Load(); v != nil {
				discarded, err := v.reset(ct.Off)
				if discarded > 0 {
					c.log.E("verify_discard_partial", "bytes", discarded)
				}
				if err != nil {
					c.failErr(err)
				}
			}
		case "eof":
			select {
			case c.eofCh <- ct.Seq:
			default:
			}
		case "ob":
			if w := c.obWant.Load(); w > 0 && ct.Len != w {
				c.obResult(nil, fmt.Errorf("peer's outboard is %d bytes, expected %d", ct.Len, w))
			}
		case "ob_eof":
			if c.obWant.Load() > 0 {
				ob := c.obBuf
				c.obBuf = nil
				c.obWant.Store(0)
				c.obResult(ob, nil) // completeness + hash checked by the waiter
			}
		case "nak":
			if c.obWant.Load() > 0 {
				c.obResult(nil, errObUnavailable)
				return
			}
			// A range nak: the peer doesn't have the requested bytes (a
			// stale advertisement, or coverage that shrank across a resume).
			// Resumable — another attempt (or, later, another source) can
			// still supply them.
			c.failErr(resumableError{fmt.Errorf("peer can't serve the requested range")})
		case "err":
			c.failErr(fmt.Errorf("host: %s", ct.Msg))
		}
		return
	}
	if w := c.obWant.Load(); w > 0 {
		// The one-off outboard fetch that precedes any range request:
		// binary frames are tree bytes, not file bytes.
		if int64(len(c.obBuf)+len(msg.Data)) > w {
			c.obResult(nil, fmt.Errorf("peer sent more outboard bytes than announced"))
			return
		}
		c.obBuf = append(c.obBuf, msg.Data...)
		c.obGot.Add(int64(len(msg.Data)))
		return
	}
	if !c.sawAt {
		// Without a prior "at" frame, writePos still holds its zero value —
		// writing here would silently misattribute these bytes to offset 0
		// and mark it received, corrupting span bookkeeping instead of
		// failing loudly.
		c.log.E("binary_before_at", "bytes", len(msg.Data))
		c.failErr(fmt.Errorf("host sent data before announcing an offset"))
		return
	}
	at := c.writePos
	if at+int64(len(msg.Data)) > c.entry.meta.Size {
		c.failErr(fmt.Errorf("host sent past end of file"))
		return
	}
	n := int64(len(msg.Data))
	if v := c.verifier.Load(); v != nil {
		// Verified path: bytes reach the file and the received set only
		// once their whole chunk group checks out against the root, so the
		// stream server can never serve unverified data. Raw arrivals still
		// count toward got (liveness for the stall watchdog and the rate
		// EMA); e.have — what readers actually see — grows only via emit.
		err := v.feed(msg.Data, func(off int64, data []byte) error {
			if _, werr := c.f.WriteAt(data, off); werr != nil {
				return fmt.Errorf("writing file: %w", werr)
			}
			c.vgot.Add(int64(len(data)))
			c.entry.add(off, off+int64(len(data)))
			return nil
		})
		if err != nil {
			c.log.E("verify_fail", "at", at, "err", err.Error())
			c.failErr(err)
			return
		}
		c.writePos += n
		c.got.Add(n)
		c.progress()
		return
	}
	if _, err := c.f.WriteAt(msg.Data, at); err != nil {
		c.failErr(fmt.Errorf("writing file: %w", err))
		return
	}
	c.writePos += n
	c.got.Add(n)
	// Mark the received range available; chunks within a range arrive in
	// order, so these coalesce into one growing span.
	c.entry.add(at, at+n)
	c.progress()
}

// failErr surfaces a connection-fatal error to the fetch loop without
// blocking pion's OnMessage goroutine (only the first error matters).
func (c *fileConn) failErr(err error) {
	select {
	case c.errCh <- err:
	default:
	}
}

// obFetch is the outboard fetch's outcome, handed from the OnMessage
// goroutine to the connect goroutine waiting on obCh.
type obFetch struct {
	ob  []byte
	err error
}

// obResult delivers the outboard fetch's outcome once; a second outcome
// (e.g. an overflow error after ob_eof already delivered) is dropped.
func (c *fileConn) obResult(ob []byte, err error) {
	select {
	case c.obCh <- obFetch{ob: ob, err: err}:
	default:
	}
}

func (c *fileConn) progress() {
	now := time.Now()
	if now.Sub(c.lastPrint) < progressInterval {
		return
	}
	got := c.got.Load()
	size := c.entry.meta.Size
	inst := float64(got-c.lastBytes) / now.Sub(c.lastPrint).Seconds()
	if c.rate == 0 {
		c.rate = inst
	} else {
		c.rate = 0.6*c.rate + 0.4*inst // EMA smooths the bursty data channel
	}
	if c.reportRate != nil {
		c.reportRate(c.rate)
	}
	c.lastPrint, c.lastBytes = now, got
	// Bytes of the whole file present so far, across every received span
	// (front, tail, and any seeked-to region).
	received := c.entry.covered()
	isTail := c.isTail.Load()

	if c.emit != nil {
		var eta time.Duration
		if c.rate > 0 && received < size {
			eta = time.Duration(float64(size-received) / c.rate * float64(time.Second))
		}
		c.emit(Progress{Name: c.entry.meta.Name, IsTail: isTail, Received: received, Total: size, BytesPerSec: c.rate, ETA: eta})
		return
	}

	label := c.entry.meta.Name
	if isTail {
		label += " (index)"
	}
	c.printf("* %s  %3d%%  %s / %s  %s/s", label, received*100/size, human.Bytes(received), human.Bytes(size), human.Bytes(int64(c.rate)))
}

func hashFileOnDisk(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// FetchFile downloads a single file (no streaming server) and returns
// its final path, resuming across drops. A thin wrapper over the
// Downloader for the non-streaming local-fetch path.
func FetchFile(ctx context.Context, sig Signaler, host string, meta protocol.FileMeta, outDir string, ice ICEConfig, printf func(string, ...any)) (string, error) {
	store, err := NewFileStore(outDir, []protocol.FileMeta{meta})
	if err != nil {
		return "", err
	}
	dl := NewDownloader(sig, host, store, ice, nil, printf)
	if err := dl.runFile(ctx, 0); err != nil {
		return "", err
	}
	return store.PathFor(0), nil
}
