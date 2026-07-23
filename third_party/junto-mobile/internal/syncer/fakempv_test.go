package syncer

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"time"

	"github.com/swayam-mishra/junto/internal/mpv"
)

// fakeMpv is a test double for the Player interface used by the end-to-end
// loopback test. It models a playback clock (position advances in real time
// while unpaused) and, like real mpv, emits a property-change event after
// every state change so the engine's echo-suppression paths run.
//
// Events are delivered on a buffered channel with best-effort (non-blocking)
// sends. This mirrors mpv's lossy event channel and — crucially — never
// blocks: the engine's Run goroutine both calls these setters and drains
// Events(), so a synchronous send would deadlock.
//
// All state is guarded by mu so the test goroutine can read it (via the
// getters below) while the engine goroutine drives it — race-free.
type fakeMpv struct {
	mu       sync.Mutex
	pos      float64
	speed    float64
	paused   bool
	sid      int
	aid      int
	subDelay float64
	plPos    int
	lastTick time.Time

	// Call counters let a test assert quiescence (no rebroadcast storm).
	setPauseN int

	events chan mpv.Event
	done   chan error
}

func newFakeMpv() *fakeMpv {
	return &fakeMpv{
		speed:    1.0,
		paused:   true, // the engine launches mpv with --pause
		lastTick: time.Now(),
		events:   make(chan mpv.Event, 256),
		done:     make(chan error, 1),
	}
}

func mustJSON(v any) json.RawMessage {
	b, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return b
}

// emitProp queues a property-change event, best-effort (never blocks). Caller
// holds mu (or doesn't need to — the channel op is independent of mu).
func (f *fakeMpv) emitProp(id int, v any) {
	select {
	case f.events <- mpv.Event{Event: "property-change", ID: id, Data: mustJSON(v)}:
	default:
	}
}

func (f *fakeMpv) emitEvent(name string) {
	select {
	case f.events <- mpv.Event{Event: name}:
	default:
	}
}

// advanceLocked moves the playback clock forward to now. mu must be held.
func (f *fakeMpv) advanceLocked() {
	now := time.Now()
	if !f.paused {
		f.pos += now.Sub(f.lastTick).Seconds() * f.speed
	}
	f.lastTick = now
}

// --- Player interface (driven by the engine) -------------------------

func (f *fakeMpv) ObserveProperty(_ context.Context, id int, _ string) error {
	// Emit one initial echo per observed property so seenProps fills to 7 and
	// propsReady flips via the count path (not the 3 s safety valve) — the
	// regression hook for the readiness-gate magic number.
	f.mu.Lock()
	defer f.mu.Unlock()
	switch id {
	case obsPause:
		f.emitProp(id, f.paused)
	case obsSpeed:
		f.emitProp(id, f.speed)
	case obsSubDelay:
		f.emitProp(id, f.subDelay)
	case obsSid:
		f.emitProp(id, f.sid)
	case obsAid:
		f.emitProp(id, f.aid)
	case obsPlaylistPos:
		f.emitProp(id, f.plPos)
	case obsPausedForCache:
		f.emitProp(id, false)
	}
	return nil
}

func (f *fakeMpv) SetPause(_ context.Context, p bool) error {
	f.mu.Lock()
	f.advanceLocked()
	f.paused = p
	f.setPauseN++
	f.emitProp(obsPause, p)
	f.mu.Unlock()
	return nil
}

func (f *fakeMpv) SetSpeed(_ context.Context, s float64) error {
	f.mu.Lock()
	f.advanceLocked()
	f.speed = s
	f.emitProp(obsSpeed, s)
	f.mu.Unlock()
	return nil
}

func (f *fakeMpv) SeekAbsolute(_ context.Context, secs float64) error {
	f.mu.Lock()
	f.pos = secs
	f.lastTick = time.Now()
	f.mu.Unlock()
	// Mirror mpv: a seek emits "seek" then "playback-restart". The engine set
	// a seek suppression before calling, so it claims this and won't rebroadcast.
	f.emitEvent("seek")
	f.emitEvent("playback-restart")
	return nil
}

func (f *fakeMpv) SetSubDelay(_ context.Context, secs float64) error {
	f.mu.Lock()
	f.subDelay = secs
	f.emitProp(obsSubDelay, secs)
	f.mu.Unlock()
	return nil
}

func (f *fakeMpv) SetSid(_ context.Context, sid int) error {
	f.mu.Lock()
	f.sid = sid
	f.emitProp(obsSid, sid)
	f.mu.Unlock()
	return nil
}

func (f *fakeMpv) SetAid(_ context.Context, aid int) error {
	f.mu.Lock()
	f.aid = aid
	f.emitProp(obsAid, aid)
	f.mu.Unlock()
	return nil
}

func (f *fakeMpv) SetPlaylistPos(_ context.Context, idx int) error {
	f.mu.Lock()
	f.plPos = idx
	f.pos = 0
	f.lastTick = time.Now()
	f.emitProp(obsPlaylistPos, idx)
	f.mu.Unlock()
	f.emitEvent("playback-restart") // a new file loaded
	return nil
}

func (f *fakeMpv) AddSubtitle(_ context.Context, _ string) error { return nil }

func (f *fakeMpv) GetPlaybackTime(_ context.Context) (float64, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.advanceLocked()
	return f.pos, nil
}

func (f *fakeMpv) ShowText(_ context.Context, _ string, _ int) error { return nil }
func (f *fakeMpv) Events() <-chan mpv.Event                          { return f.events }
func (f *fakeMpv) Done() <-chan error                                { return f.done }
func (f *fakeMpv) Close() error                                      { return nil }

// errPlaybackTimeMpv wraps a fakeMpv but forces GetPlaybackTime to fail once
// (then behave normally), simulating a transient mpv IPC hiccup.
type errPlaybackTimeMpv struct {
	*fakeMpv
	fail bool
}

func (f *errPlaybackTimeMpv) GetPlaybackTime(ctx context.Context) (float64, error) {
	if f.fail {
		f.fail = false
		return 0, errors.New("mpv ipc timeout")
	}
	return f.fakeMpv.GetPlaybackTime(ctx)
}

// errSetSpeedMpv wraps a fakeMpv but makes every SetSpeed call fail,
// simulating a property apply that never succeeds (e.g. mpv rejecting an
// out-of-range value or a wedged IPC socket).
type errSetSpeedMpv struct {
	*fakeMpv
}

func (f *errSetSpeedMpv) SetSpeed(_ context.Context, _ float64) error {
	return errors.New("mpv rejected the speed change")
}

// --- test helpers: simulate a human acting in the mpv window ---------
//
// These emit the same events the real player would, but the engine did NOT
// set a suppression for them, so it treats them as user actions and
// broadcasts them — exactly like a person pressing a key in mpv.

// start simulates the initial file load completing, which mpv signals with a
// playback-restart. This clears the engine's `loading` flag so a later user
// seek isn't mistaken for an internal load seek and swallowed.
func (f *fakeMpv) start() { f.emitEvent("playback-restart") }

func (f *fakeMpv) userPause(p bool) {
	f.mu.Lock()
	f.advanceLocked()
	f.paused = p
	f.emitProp(obsPause, p)
	f.mu.Unlock()
}

func (f *fakeMpv) userSpeed(s float64) {
	f.mu.Lock()
	f.speed = s
	f.emitProp(obsSpeed, s)
	f.mu.Unlock()
}

func (f *fakeMpv) userSeek(secs float64) {
	f.mu.Lock()
	f.pos = secs
	f.lastTick = time.Now()
	f.mu.Unlock()
	f.emitEvent("seek")
	f.emitEvent("playback-restart")
}

func (f *fakeMpv) userPlaylistPos(idx int) {
	f.mu.Lock()
	f.plPos = idx
	f.pos = 0
	f.lastTick = time.Now()
	f.emitProp(obsPlaylistPos, idx)
	f.mu.Unlock()
	f.emitEvent("playback-restart")
}

// --- race-free getters for assertions --------------------------------

func (f *fakeMpv) isPaused() bool { f.mu.Lock(); defer f.mu.Unlock(); return f.paused }
func (f *fakeMpv) getSpeed() float64 {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.speed
}
func (f *fakeMpv) position() float64 {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.advanceLocked()
	return f.pos
}
func (f *fakeMpv) playlistPos() int { f.mu.Lock(); defer f.mu.Unlock(); return f.plPos }
func (f *fakeMpv) pauseCalls() int  { f.mu.Lock(); defer f.mu.Unlock(); return f.setPauseN }
