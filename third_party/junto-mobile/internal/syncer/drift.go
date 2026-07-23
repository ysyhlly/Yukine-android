package syncer

import "time"

// driftThreshold is how far apart two clients may be before we
// hard-seek to converge. Clock offset is now compensated (NTP-style),
// so the remaining drift is real position divergence.
const driftThreshold = 0.5 // seconds (was 1.0 before clock-offset estimation)

// seekSuppressTolerance is how far mpv may land from a seek target we
// applied before we stop recognizing the resulting playback-restart as
// our own. It is deliberately looser than driftThreshold: with
// keyframe seeking mpv can settle most of a second from the requested
// position, and if we don't claim that as our own action we'd
// re-broadcast it as a fake "you seeked" — a visible jolt and a
// potential seek-war. Detection (needsSeek) wants to be tight;
// suppression wants to be forgiving, so they are separate.
const seekSuppressTolerance = 1.0 // seconds

// expectedPosition projects where a sender is *now*, given the
// position it reported at sentAtMillis. Paused senders don't advance;
// playing senders advance at their playback speed.
func expectedPosition(pos float64, paused bool, speed float64, sentAtMillis, nowMillis int64) float64 {
	if paused {
		return pos
	}
	return pos + float64(nowMillis-sentAtMillis)/1000.0*speed
}

// needsSeek reports whether localPos is far enough from target to
// warrant a corrective seek.
func needsSeek(localPos, target float64) bool {
	d := localPos - target
	if d < 0 {
		d = -d
	}
	return d > driftThreshold
}

// Rate-nudge thresholds for smooth drift correction. When drift is between
// nudgeDriftMin and nudgeDriftMax, we briefly adjust playback rate instead
// of hard-seeking so the correction is invisible to the viewer.
const (
	nudgeDriftMin = 0.05 // below this, drift is noise — ignore
	nudgeDriftMax = 0.40 // at or above this, use a hard seek instead
	maxRateNudge  = 0.03 // ±3 % maximum rate adjustment
)

// nudgeRequired reports whether drift is in the glide zone where a rate
// nudge is appropriate (i.e. not noise, not large enough for a hard seek).
func nudgeRequired(drift float64) bool {
	d := drift
	if d < 0 {
		d = -d
	}
	return d >= nudgeDriftMin && d < nudgeDriftMax
}

// nudgeSpeed returns the playback rate that corrects drift. base is the
// current user-intended speed (typically 1.0). The adjustment scales with
// |drift| and is capped at maxRateNudge.
func nudgeSpeed(drift, base float64) float64 {
	d := drift
	if d < 0 {
		d = -d
	}
	scale := d / nudgeDriftMax
	if scale > 1.0 {
		scale = 1.0
	}
	delta := scale * maxRateNudge
	if drift > 0 { // we're behind — speed up
		return base * (1.0 + delta)
	}
	return base * (1.0 - delta) // we're ahead — slow down
}

// nudgeDeadline bounds how long a rate nudge may run unattended: an
// estimate of the real time needed to close the given drift at the
// nudge's fixed rate, plus a safety margin. It's a backstop for the case
// where no further remote state arrives to naturally clear the nudge —
// e.g. this peer's pubkey wins every future tie-break against the
// nudge's originator, so applyRemoteState never re-evaluates the drift
// (see remoteWins) and the nudge would otherwise run forever.
func nudgeDeadline(drift, base, nudgeSpeed float64) time.Duration {
	rate := nudgeSpeed - base
	if rate < 0 {
		rate = -rate
	}
	if rate < 1e-6 {
		return 2 * time.Second // shouldn't happen; fall back to one heartbeat
	}
	d := drift
	if d < 0 {
		d = -d
	}
	seconds := d/rate*1.25 + 1.0 // 25% margin plus a flat 1s for timing jitter
	return time.Duration(seconds * float64(time.Second))
}

// remoteWins decides last-writer-wins between a remote action version
// and our local one; ties break toward the larger pubkey so all peers
// agree.
func remoteWins(remoteVer, localVer int64, remotePub, selfPub string) bool {
	if remoteVer != localVer {
		return remoteVer > localVer
	}
	return remotePub > selfPub
}

const suppressWindow = 1500 * time.Millisecond

// expectation remembers one state change we caused by applying a
// remote command to mpv, so the resulting mpv event is not rebroadcast
// as if the local user had acted. The deadline keeps a lost or
// coalesced mpv event from suppressing a real user action forever.
type expectation[T any] struct {
	val   *T
	until time.Time
	eq    func(a, b T) bool
}

func (e *expectation[T]) set(v T, now time.Time) {
	e.val = &v
	e.until = now.Add(suppressWindow)
}

// claim consumes a matching unexpired expectation. An expired one is
// cleared and never matches; a value mismatch leaves the expectation
// in place (the real event may still be coming).
func (e *expectation[T]) claim(observed T, now time.Time) bool {
	if e.val == nil || now.After(e.until) {
		e.val = nil
		return false
	}
	if !e.eq(*e.val, observed) {
		return false
	}
	e.val = nil
	return true
}

func eqExact[T comparable](a, b T) bool { return a == b }

func eqWithin(tol float64) func(a, b float64) bool {
	return func(a, b float64) bool {
		d := a - b
		if d < 0 {
			d = -d
		}
		return d <= tol
	}
}

// suppression tracks expectations for every synced mpv property.
type suppression struct {
	pause    expectation[bool]
	seek     expectation[float64]
	speed    expectation[float64]
	subDelay expectation[float64]
	sid      expectation[int]
	aid      expectation[int]
	index    expectation[int]
}

func newSuppression() suppression {
	return suppression{
		pause:    expectation[bool]{eq: eqExact[bool]},
		seek:     expectation[float64]{eq: eqWithin(seekSuppressTolerance)},
		speed:    expectation[float64]{eq: eqWithin(0.01)},
		subDelay: expectation[float64]{eq: eqWithin(0.01)},
		sid:      expectation[int]{eq: eqExact[int]},
		aid:      expectation[int]{eq: eqExact[int]},
		index:    expectation[int]{eq: eqExact[int]},
	}
}

// floatEq is the "did this property actually change" comparison used
// for mpv float properties, which can carry representation noise.
func floatEq(a, b float64) bool {
	d := a - b
	if d < 0 {
		d = -d
	}
	return d <= 0.001
}
