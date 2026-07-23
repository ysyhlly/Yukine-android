package syncer

import (
	"testing"
	"time"
)

func TestExpectedPosition(t *testing.T) {
	cases := []struct {
		name        string
		pos         float64
		paused      bool
		speed       float64
		sentAt, now int64
		want        float64
	}{
		{"paused holds", 30, true, 1.0, 1000, 5000, 30},
		{"paused ignores speed", 30, true, 2.0, 1000, 5000, 30},
		{"playing advances", 30, false, 1.0, 1000, 3500, 32.5},
		{"double speed advances twice", 30, false, 2.0, 1000, 3500, 35},
		{"half speed advances half", 30, false, 0.5, 1000, 3000, 31},
		{"no elapsed", 30, false, 1.0, 1000, 1000, 30},
	}
	for _, c := range cases {
		if got := expectedPosition(c.pos, c.paused, c.speed, c.sentAt, c.now); got != c.want {
			t.Errorf("%s: got %v want %v", c.name, got, c.want)
		}
	}
}

func TestNeedsSeek(t *testing.T) {
	if needsSeek(10.0, 10.4) {
		t.Error("0.4s drift should not seek")
	}
	if !needsSeek(10.0, 10.6) {
		t.Error("0.6s drift should seek")
	}
	if !needsSeek(10.6, 10.0) {
		t.Error("drift is symmetric")
	}
	if needsSeek(10.0, 10.5) {
		t.Error("exactly at threshold should not seek")
	}
}

func TestRemoteWins(t *testing.T) {
	if !remoteWins(200, 100, "aa", "bb") {
		t.Error("newer remote version must win")
	}
	if remoteWins(100, 200, "ff", "aa") {
		t.Error("older remote version must lose")
	}
	// Ties break by pubkey, consistently on both sides.
	if !remoteWins(100, 100, "ff", "aa") {
		t.Error("tie: larger remote pubkey wins")
	}
	if remoteWins(100, 100, "aa", "ff") {
		t.Error("tie: smaller remote pubkey loses")
	}
}

func TestSuppressionPause(t *testing.T) {
	now := time.Now()
	s := newSuppression()
	if s.pause.claim(true, now) {
		t.Error("nothing expected: claim must fail")
	}
	s.pause.set(true, now)
	if s.pause.claim(false, now) {
		t.Error("wrong value must not be claimed")
	}
	if !s.pause.claim(true, now.Add(time.Second)) {
		t.Error("matching value within window must be claimed")
	}
	if s.pause.claim(true, now) {
		t.Error("expectation must be consumed by claim")
	}
	s.pause.set(false, now)
	if s.pause.claim(false, now.Add(2*time.Second)) {
		t.Error("expired expectation must not be claimed")
	}
}

func TestSuppressionSeek(t *testing.T) {
	now := time.Now()
	s := newSuppression()
	s.seek.set(120, now)
	if s.seek.claim(50, now) {
		t.Error("far-off position must not be claimed")
	}
	if !s.seek.claim(120.4, now.Add(time.Second)) {
		t.Error("near-target position within window must be claimed")
	}
	if s.seek.claim(120.4, now) {
		t.Error("expectation must be consumed")
	}
	s.seek.set(120, now)
	if s.seek.claim(120, now.Add(2*time.Second)) {
		t.Error("expired seek expectation must not be claimed")
	}
}

// TestSeekSuppressionDecoupledFromDrift guards the decoupling: seek
// suppression must stay forgiving (seekSuppressTolerance, ~1.0s) even
// though drift *detection* (driftThreshold) is tight (0.5s). With
// keyframe seeking, mpv can land most of a second from the requested
// target; if that landing weren't claimed as our own it would be
// re-broadcast as a fake "you seeked".
func TestSeekSuppressionDecoupledFromDrift(t *testing.T) {
	now := time.Now()
	s := newSuppression()
	// 0.7s off: beyond driftThreshold (0.5) but within seekSuppressTolerance (1.0).
	if !needsSeek(120.0, 120.7) {
		t.Fatal("precondition: 0.7s should exceed the drift-detection threshold")
	}
	s.seek.set(120, now)
	if !s.seek.claim(120.7, now) {
		t.Error("a seek landing 0.7s off target must still be claimed as our own")
	}
	// Beyond the suppression tolerance, it is genuinely not our seek.
	s.seek.set(120, now)
	if s.seek.claim(121.2, now) {
		t.Error("a landing 1.2s off (past tolerance) must not be claimed")
	}
}

func TestSuppressionMismatchKeepsExpectation(t *testing.T) {
	// A mismatched event must not clear the expectation: the event we
	// actually caused may still be in flight behind it.
	now := time.Now()
	s := newSuppression()
	s.sid.set(3, now)
	if s.sid.claim(1, now) {
		t.Error("mismatch must not claim")
	}
	if !s.sid.claim(3, now.Add(time.Second)) {
		t.Error("the real event must still be claimable after a mismatch")
	}
}

func TestSuppressionTolerances(t *testing.T) {
	now := time.Now()
	s := newSuppression()
	s.speed.set(1.5, now)
	if !s.speed.claim(1.505, now) {
		t.Error("speed within tolerance must be claimed")
	}
	s.speed.set(1.5, now)
	if s.speed.claim(1.6, now) {
		t.Error("speed outside tolerance must not be claimed")
	}
	s.index.set(2, now)
	if s.index.claim(1, now) {
		t.Error("index requires exact match")
	}
	if !s.index.claim(2, now) {
		t.Error("exact index must be claimed")
	}
}

func TestFloatEq(t *testing.T) {
	if !floatEq(1.5, 1.5005) {
		t.Error("representation noise should compare equal")
	}
	if floatEq(1.5, 1.51) {
		t.Error("a real change should compare unequal")
	}
}

// --- nudge helpers ---

func TestNudgeRequired(t *testing.T) {
	cases := []struct {
		drift float64
		want  bool
	}{
		{0.0, false},                   // zero — no correction
		{0.01, false},                  // noise floor
		{nudgeDriftMin - 0.001, false}, // just below min
		{nudgeDriftMin, true},          // at min
		{0.20, true},                   // middle of range
		{nudgeDriftMax - 0.001, true},  // just below max
		{nudgeDriftMax, false},         // at max → hard seek territory
		{0.5, false},                   // well into seek territory
		{-nudgeDriftMin - 0.01, true},  // negative drift (ahead)
		{-nudgeDriftMax, false},        // too far ahead → seek
	}
	for _, c := range cases {
		if got := nudgeRequired(c.drift); got != c.want {
			t.Errorf("nudgeRequired(%v) = %v, want %v", c.drift, got, c.want)
		}
	}
}

func TestNudgeSpeedDirection(t *testing.T) {
	// Positive drift (we're behind) → speed up.
	ns := nudgeSpeed(0.1, 1.0)
	if ns <= 1.0 {
		t.Errorf("behind: expected speed > 1.0, got %v", ns)
	}
	// Negative drift (we're ahead) → slow down.
	ns = nudgeSpeed(-0.1, 1.0)
	if ns >= 1.0 {
		t.Errorf("ahead: expected speed < 1.0, got %v", ns)
	}
}

func TestNudgeSpeedCapped(t *testing.T) {
	// Maximum nudge should not exceed base * (1 + maxRateNudge).
	max := nudgeSpeed(nudgeDriftMax*10, 1.0) // huge drift
	if max > 1.0+maxRateNudge+0.001 {
		t.Errorf("nudge speed exceeded cap: got %v, want <= %v", max, 1.0+maxRateNudge)
	}
}

func TestNudgeSpeedSymmetric(t *testing.T) {
	pos := nudgeSpeed(0.2, 1.0)
	neg := nudgeSpeed(-0.2, 1.0)
	// |pos-1| should equal |neg-1| within floating-point noise.
	if !floatEq(pos-1.0, 1.0-neg) {
		t.Errorf("nudge not symmetric: +%v vs -%v", pos-1.0, 1.0-neg)
	}
}

func TestNudgeDeadlinePositive(t *testing.T) {
	d := nudgeDeadline(0.2, 1.0, nudgeSpeed(0.2, 1.0))
	if d <= 0 {
		t.Errorf("deadline must be positive, got %v", d)
	}
}

func TestNudgeDeadlineFallsBackWhenRateIsZero(t *testing.T) {
	// nudgeSpeed == base means no correction is actually happening;
	// the deadline must still be finite and positive rather than dividing
	// by zero or blocking forever.
	d := nudgeDeadline(0.2, 1.0, 1.0)
	if d <= 0 {
		t.Errorf("zero-rate deadline must still be positive, got %v", d)
	}
}

func TestNudgeSpeedWithNonUnitBase(t *testing.T) {
	// Nudge at 2× base speed.
	ns := nudgeSpeed(0.1, 2.0)
	if ns <= 2.0 {
		t.Errorf("expected nudge > base speed 2.0, got %v", ns)
	}
	if ns > 2.0*(1.0+maxRateNudge)+0.001 {
		t.Errorf("nudge exceeded cap relative to base: %v", ns)
	}
}
