package syncer

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

var testFiles = []protocol.FileMeta{{Name: "movie.mp4", Size: 1000, SHA256: "abc"}}

// newMigrationEngine creates a minimal joiner engine configured for
// host-migration tests. selfPub is this peer's pubkey.
func newMigrationEngine(selfPub string, canHost bool, onBecomeHost func([]protocol.FileMeta) (func(string, int, int64, int64), func(string, protocol.Signal))) *Engine {
	pf, _ := capture()
	return New(Deps{
		SelfPub:      selfPub,
		Printf:       pf,
		CanHost:      canHost,
		OnBecomeHost: onBecomeHost,
	})
}

// --- shouldElectSelf ---

func TestShouldElectSelf_Sole(t *testing.T) {
	e := newMigrationEngine("zzz", true, stubOnBecomeHost())
	e.peerFiles = testFiles
	if !e.shouldElectSelf() {
		t.Error("sole eligible peer should elect itself")
	}
}

func TestShouldElectSelf_LargestPub(t *testing.T) {
	e := newMigrationEngine("zzz", true, stubOnBecomeHost())
	e.peerFiles = testFiles
	e.peers["aaa"] = &peerInfo{canHost: true, lastSeen: time.Now()}
	e.peers["mmm"] = &peerInfo{canHost: true, lastSeen: time.Now()}
	if !e.shouldElectSelf() {
		t.Error("largest pubkey peer should elect itself")
	}
}

func TestShouldElectSelf_NotLargest(t *testing.T) {
	e := newMigrationEngine("aaa", true, stubOnBecomeHost())
	e.peerFiles = testFiles
	e.peers["zzz"] = &peerInfo{canHost: true, lastSeen: time.Now()}
	if e.shouldElectSelf() {
		t.Error("smaller pubkey peer should defer to larger")
	}
}

func TestShouldElectSelf_NotCanHost(t *testing.T) {
	e := newMigrationEngine("zzz", false, nil)
	e.peerFiles = testFiles
	if e.shouldElectSelf() {
		t.Error("peer without CanHost should not elect itself")
	}
}

func TestShouldElectSelf_NilOnBecomeHost(t *testing.T) {
	e := newMigrationEngine("zzz", true, nil) // CanHost but no callback
	e.peerFiles = testFiles
	if e.shouldElectSelf() {
		t.Error("peer with nil OnBecomeHost should not elect itself")
	}
}

func TestShouldElectSelf_PeerCanHostFalse(t *testing.T) {
	// Larger pubkey peer exists but canHost=false — we should still win.
	e := newMigrationEngine("aaa", true, stubOnBecomeHost())
	e.peerFiles = testFiles
	e.peers["zzz"] = &peerInfo{canHost: false, lastSeen: time.Now()}
	if !e.shouldElectSelf() {
		t.Error("should elect self when no other canHost peer is active")
	}
}

func TestShouldElectSelf_LargerPeerTimedOut(t *testing.T) {
	// A peer with canHost and larger pubkey but stale lastSeen should not block election.
	e := newMigrationEngine("aaa", true, stubOnBecomeHost())
	e.peerFiles = testFiles
	e.peers["zzz"] = &peerInfo{canHost: true, lastSeen: time.Now().Add(-peerTimeout - time.Second)}
	if !e.shouldElectSelf() {
		t.Error("timed-out larger peer should not block election")
	}
}

func TestShouldElectSelf_NoPeerFiles(t *testing.T) {
	e := newMigrationEngine("zzz", true, stubOnBecomeHost())
	// peerFiles is nil — original host's hello never stored
	if e.shouldElectSelf() {
		t.Error("should not elect without stored peer files")
	}
}

// --- selfPromote ---

func TestSelfPromote(t *testing.T) {
	pf, lines := capture()
	promoted := false
	e := New(Deps{
		SelfPub: "zzz",
		Printf:  pf,
		CanHost: true,
		OnBecomeHost: func(files []protocol.FileMeta) (func(string, int, int64, int64), func(string, protocol.Signal)) {
			promoted = true
			return func(string, int, int64, int64) {}, func(string, protocol.Signal) {}
		},
	})
	e.peerFiles = testFiles
	e.hostPub = "old-host"
	e.hostGoneAt = time.Now().Add(-10 * time.Second)

	e.selfPromote(context.Background())

	if !promoted {
		t.Error("OnBecomeHost should have been called")
	}
	if !e.promoted {
		t.Error("e.promoted should be set")
	}
	if !e.host {
		t.Error("e.host should be true after promotion")
	}
	if !e.d.Host {
		t.Error("e.d.Host should be true after promotion")
	}
	if e.d.OnFileReq == nil {
		t.Error("OnFileReq should be set after promotion")
	}
	if e.d.OnSignal == nil {
		t.Error("OnSignal should be set after promotion")
	}
	if !e.hostGoneAt.IsZero() {
		t.Error("hostGoneAt should be cleared after promotion")
	}
	if e.hostPub != "" {
		t.Errorf("hostPub should be empty after promotion, got %q", e.hostPub)
	}
	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "now the host") {
		t.Errorf("expected promotion message, got: %q", out)
	}
}

func TestSelfPromote_DoubleCallIsNoop(t *testing.T) {
	calls := 0
	e := New(Deps{
		SelfPub: "zzz",
		Printf:  func(string, ...any) {},
		CanHost: true,
		OnBecomeHost: func([]protocol.FileMeta) (func(string, int, int64, int64), func(string, protocol.Signal)) {
			calls++
			return func(string, int, int64, int64) {}, func(string, protocol.Signal) {}
		},
	})
	e.peerFiles = testFiles

	e.selfPromote(context.Background())
	e.selfPromote(context.Background())

	if calls != 1 {
		t.Errorf("OnBecomeHost should be called exactly once, got %d", calls)
	}
}

func TestSelfPromote_GuardsNilOnBecomeHost(t *testing.T) {
	e := newMigrationEngine("zzz", true, nil)
	e.peerFiles = testFiles
	// Should not panic.
	e.selfPromote(context.Background())
	if e.promoted {
		t.Error("should not promote with nil OnBecomeHost")
	}
}

// --- checkHostPresence ---

func TestCheckHostPresence_PromotesWhenEligible(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{
		SelfPub: "zzz",
		Printf:  pf,
		CanHost: true,
		OnBecomeHost: func([]protocol.FileMeta) (func(string, int, int64, int64), func(string, protocol.Signal)) {
			return func(string, int, int64, int64) {}, func(string, protocol.Signal) {}
		},
	})
	e.peerFiles = testFiles
	e.hostPub = "dead-host"
	// No entry in e.peers for dead-host → hostAlive = false.
	e.hostGoneAt = time.Now().Add(-2 * time.Second) // already warned once

	e.checkHostPresence(context.Background())

	if !e.promoted {
		t.Error("eligible peer should promote itself")
	}
	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "now the host") {
		t.Errorf("expected promotion message, got: %q", out)
	}
}

func TestCheckHostPresence_NoPromoteWhenNotCanHost(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{SelfPub: "zzz", Printf: pf})
	e.peerFiles = testFiles
	e.hostPub = "dead-host"
	e.hostGoneAt = time.Now().Add(-2 * time.Second)

	e.checkHostPresence(context.Background())

	if e.promoted {
		t.Error("peer without CanHost should not promote")
	}
}

func TestCheckHostPresence_FirstDetectionSetsGoneAt(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{SelfPub: "zzz", Printf: pf})
	e.hostPub = "dead-host"
	// hostGoneAt is zero → first detection.

	e.checkHostPresence(context.Background())

	if e.hostGoneAt.IsZero() {
		t.Error("hostGoneAt should be set on first detection")
	}
	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "appears to have left") {
		t.Errorf("expected warning message, got: %q", out)
	}
}

// --- handleMessage: self-message guard ---

func TestHandleMessageHello_SelfGuard(t *testing.T) {
	e := newMigrationEngine("mykey", false, nil)

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "mykey", Nick: "me",
	})

	if _, ok := e.peers["mykey"]; ok {
		t.Error("engine should not add itself to peers")
	}
}

// --- handleMessage: canHost stored in peerInfo ---

func TestHandleMessageHello_StoresCanHost(t *testing.T) {
	e := newMigrationEngine("self", false, nil)

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "peer1", Nick: "alice", CanHost: true,
	})

	p, ok := e.peers["peer1"]
	if !ok {
		t.Fatal("peer should be added")
	}
	if !p.canHost {
		t.Error("peerInfo.canHost should reflect the hello's CanHost field")
	}
}

// --- handleMessage: peerFiles stored from original host's hello ---

func TestHandleMessageHello_StoresPeerFiles(t *testing.T) {
	e := newMigrationEngine("self", false, nil)

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "host1", Nick: "host",
		Files: testFiles,
	})

	if len(e.peerFiles) == 0 {
		t.Error("peerFiles should be stored from host's hello")
	}
	if e.peerFiles[0].Name != testFiles[0].Name {
		t.Errorf("got %q, want %q", e.peerFiles[0].Name, testFiles[0].Name)
	}
	if e.hostPub != "host1" {
		t.Errorf("hostPub should be set to host1, got %q", e.hostPub)
	}
}

// --- handleMessage: host migration on re-hello ---

func TestHandleMessageHello_HostMigration(t *testing.T) {
	pf, lines := capture()
	hostChangeCalled := false
	e := New(Deps{
		SelfPub:      "self",
		Printf:       pf,
		OnHostChange: func(newPub string) { hostChangeCalled = true },
	})
	// Simulate original host gone.
	e.hostPub = "old-host"
	e.hostGoneAt = time.Now().Add(-5 * time.Second)
	// New host is already a known peer (re-hello from known peer).
	e.peers["new-host"] = &peerInfo{nick: "newhost", lastSeen: time.Now()}

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "new-host", Nick: "newhost",
		Files: testFiles,
	})

	if e.hostPub != "new-host" {
		t.Errorf("hostPub should be updated to new-host, got %q", e.hostPub)
	}
	if !e.hostGoneAt.IsZero() {
		t.Error("hostGoneAt should be cleared when new host announces")
	}
	if !hostChangeCalled {
		t.Error("OnHostChange should be called on host migration")
	}
	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "serving files") {
		t.Errorf("expected serving-files message, got: %q", out)
	}
}

// TestHandleMessageHello_MigrationFromUnknownPeer is the regression test
// for the stranded-joiner bug: the promoted host's announcement used to be
// honored only from an *already-known* peer, so if it arrived from a
// sender we'd never exchanged hellos with (e.g. evicted during the 7s
// outage window, or simply never crossed hellos with them before), hostPub
// stayed pointed at the dead host forever. It must now be accepted from an
// unknown peer too, as long as we've already flagged the host as gone.
func TestHandleMessageHello_MigrationFromUnknownPeer(t *testing.T) {
	pf, lines := capture()
	hostChangeCalled := false
	e := New(Deps{
		SelfPub:      "self",
		Printf:       pf,
		OnHostChange: func(newPub string) { hostChangeCalled = true },
	})
	e.hostPub = "old-host"
	e.hostGoneAt = time.Now().Add(-5 * time.Second)
	// "new-host" is NOT in e.peers — this is its first hello we've ever seen.

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "new-host", Nick: "newhost",
		Files: testFiles,
	})

	if e.hostPub != "new-host" {
		t.Errorf("hostPub should be updated to new-host, got %q", e.hostPub)
	}
	if !e.hostGoneAt.IsZero() {
		t.Error("hostGoneAt should be cleared when the new host announces")
	}
	if !hostChangeCalled {
		t.Error("OnHostChange should be called on migration from an unknown peer")
	}
	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "serving files") {
		t.Errorf("expected serving-files message, got: %q", out)
	}
}

// TestHandleMessageHello_UnknownPeerNotAdoptedWhileHostAlive guards the
// narrower scope of the fix above: an unknown peer's Files-hello must
// NOT be treated as a takeover while the current host is still believed
// alive (hostGoneAt zero) — only the already-known-peer path is allowed
// that unconditional takeover privilege (see adoptHost's SECURITY note at
// its known-peer call site).
func TestHandleMessageHello_UnknownPeerNotAdoptedWhileHostAlive(t *testing.T) {
	hostChangeCalled := false
	e := New(Deps{
		SelfPub:      "self",
		Printf:       func(string, ...any) {},
		OnHostChange: func(string) { hostChangeCalled = true },
	})
	e.hostPub = "live-host"
	// hostGoneAt stays zero: the host is not believed gone.

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "stranger", Nick: "stranger",
		Files: testFiles,
	})

	if e.hostPub != "live-host" {
		t.Errorf("hostPub should not change while the host is believed alive, got %q", e.hostPub)
	}
	if hostChangeCalled {
		t.Error("OnHostChange should not fire for an unknown peer while the host is alive")
	}
}

// TestSelfPromoteReannouncesAcrossHeartbeats is the regression test for
// the lost-promotion-hello bug: selfPromote's Files-hello is a single
// best-effort relay send with nothing to confirm delivery, unlike a
// joiner's hello which gets an addressed reply — if that one message is
// lost, a peer that missed it is stuck on the dead host forever.
// heartbeat() must repeat the announcement for hostAnnounceRepeats extra
// beats, then stop.
func TestSelfPromoteReannouncesAcrossHeartbeats(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{
		SelfPub:      "zzz",
		Printf:       pf,
		Mpv:          m,
		CanHost:      true,
		OnBecomeHost: stubOnBecomeHost(),
	})
	e.peerFiles = testFiles
	e.hostPub = "old-host"
	e.hostGoneAt = time.Now().Add(-10 * time.Second)
	e.propsReady = true
	e.gateOpen = true

	e.selfPromote(context.Background())

	countHellos := func() int {
		n := 0
		for {
			select {
			case msg := <-e.outbox:
				if msg.Type == protocol.MsgHello {
					n++
				}
			default:
				return n
			}
		}
	}

	if n := countHellos(); n != 1 {
		t.Fatalf("expected 1 hello from selfPromote itself, got %d", n)
	}

	for i := 0; i < hostAnnounceRepeats; i++ {
		e.heartbeat(context.Background())
		if n := countHellos(); n != 1 {
			t.Fatalf("heartbeat %d: expected exactly 1 re-announcement hello, got %d", i, n)
		}
	}

	e.heartbeat(context.Background())
	if n := countHellos(); n != 0 {
		t.Fatalf("expected no more re-announcements after %d heartbeats, got %d", hostAnnounceRepeats, n)
	}
}

// stubOnBecomeHost returns a minimal OnBecomeHost callback for tests that
// need one but don't care about the returned hooks.
func stubOnBecomeHost() func([]protocol.FileMeta) (func(string, int, int64, int64), func(string, protocol.Signal)) {
	return func([]protocol.FileMeta) (func(string, int, int64, int64), func(string, protocol.Signal)) {
		return func(string, int, int64, int64) {}, func(string, protocol.Signal) {}
	}
}

// --- OnHostFiles callback ---

func TestOnHostFilesCalled(t *testing.T) {
	var got []protocol.FileMeta
	e := New(Deps{
		SelfPub:     "self",
		Printf:      func(string, ...any) {},
		OnHostFiles: func(files []protocol.FileMeta) { got = files },
	})

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "host1", Nick: "host",
		Files: testFiles,
	})

	if len(got) == 0 {
		t.Fatal("OnHostFiles was not called")
	}
	if got[0].Name != testFiles[0].Name {
		t.Errorf("got name %q, want %q", got[0].Name, testFiles[0].Name)
	}
}

func TestOnHostFilesNotCalledOnReHello(t *testing.T) {
	calls := 0
	e := New(Deps{
		SelfPub:     "self",
		Printf:      func(string, ...any) {},
		OnHostFiles: func([]protocol.FileMeta) { calls++ },
	})
	// First hello sets hostPub.
	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "host1", Files: testFiles,
	})
	// Re-hello from same peer (already known) should not fire OnHostFiles again.
	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgHello, From: "host1", Files: testFiles,
	})
	if calls != 1 {
		t.Errorf("OnHostFiles should fire exactly once on first identification, got %d calls", calls)
	}
}

// --- smooth drift correction ---

// TestClearNudgeNoopWhenNotNudging verifies clearNudge does nothing (and
// never touches mpv) when no nudge is active — the nil-mpv path must be safe.
func TestClearNudgeNoopWhenNotNudging(t *testing.T) {
	e := New(Deps{SelfPub: "self", Printf: func(string, ...any) {}})
	e.speed = 1.0
	e.nudging = false
	// mpv is nil here; clearNudge must not dereference it.
	e.clearNudge(context.Background(), time.Now())
	if e.nudging {
		t.Error("nudging should remain false")
	}
	if e.speed != 1.0 {
		t.Errorf("clearNudge must not change e.speed, got %v", e.speed)
	}
}

// TestHeartbeatClearsExpiredNudge is the regression test for the stuck-nudge
// bug: a same-version heartbeat from the nudge's originator never re-enters
// applyRemoteState's tie-break on a peer that wins every tie (remoteWins),
// so without a deadline backstop the nudge — and the ±3% speed skew it
// applies to mpv — would never clear and the room would creep forever.
// heartbeat() must force-clear it once nudgeUntil has passed.
func TestHeartbeatClearsExpiredNudge(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{SelfPub: "self", Printf: pf, Mpv: m, Host: true})
	e.propsReady = true
	e.gateOpen = true
	e.speed = 1.0
	m.speed = 1.03 // as if a rate-nudge is actively applied
	e.nudging = true
	e.nudgeUntil = time.Now().Add(-time.Second) // already past deadline

	e.heartbeat(context.Background())

	if e.nudging {
		t.Error("heartbeat must clear a nudge past its deadline")
	}
	if got := m.getSpeed(); got != 1.0 {
		t.Errorf("mpv speed must be restored to base 1.0, got %v", got)
	}
}

// TestHeartbeatKeepsUnexpiredNudge guards the flip side: a nudge that is
// still converging must survive a heartbeat tick, or drift correction
// would be cut short before it's had time to work.
func TestHeartbeatKeepsUnexpiredNudge(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{SelfPub: "self", Printf: pf, Mpv: m, Host: true})
	e.propsReady = true
	e.gateOpen = true
	e.speed = 1.0
	m.speed = 1.03
	e.nudging = true
	e.nudgeUntil = time.Now().Add(time.Minute) // well before its deadline

	e.heartbeat(context.Background())

	if !e.nudging {
		t.Error("heartbeat must not clear a nudge before its deadline")
	}
	if got := m.getSpeed(); got != 1.03 {
		t.Errorf("mpv speed must remain at the nudge rate, got %v", got)
	}
}

// --- userAction / version ratchet ---

// TestUserActionRatchetsPastAdoptedSkew is the regression test for the
// version-freeze bug: once this peer has adopted a remote action's version
// that sits ahead of its own wall clock (applyRemoteState's
// e.localVersion = s.Version commit — e.g. a peer with a fast or
// misconfigured clock), a fresh local action stamped with a raw
// time.Now() would broadcast a *lower* version than what's already been
// adopted, losing every tie-break against it — the stale skewed state
// would come right back on the sender's next heartbeat, silently undoing
// the local action. userAction must instead ratchet strictly past
// whatever version this peer has already adopted.
func TestUserActionRatchetsPastAdoptedSkew(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{SelfPub: "self", Printf: pf, Mpv: m})
	future := time.Now().Add(30 * time.Minute).UnixMilli()
	e.localVersion = future // simulates having adopted a fast-clocked peer's action

	e.userAction(context.Background(), "paused at 0:00")

	if e.localVersion <= future {
		t.Fatalf("localVersion must ratchet past the previously adopted version, got %d (was %d)", e.localVersion, future)
	}
	select {
	case msg := <-e.outbox:
		if msg.State == nil || msg.State.Version != e.localVersion {
			t.Fatalf("broadcast version must match the ratcheted localVersion, got %+v", msg.State)
		}
	default:
		t.Fatal("expected a broadcast")
	}
}

// --- userAction / playback-time hiccup ---

// TestUserActionSkipsBroadcastOnPlaybackTimeError is the regression test for
// the hard-seek-to-0:00 bug: userAction used to fall back to Position: 0
// when mpv's IPC hiccuped, then broadcast that fabricated position with a
// fresh winning version — a transient timeout during a pause/track-switch
// would hard-seek every other peer in the room to the start. It must
// instead skip the broadcast and leave localVersion untouched.
func TestUserActionSkipsBroadcastOnPlaybackTimeError(t *testing.T) {
	pf, get := capture()
	m := &errPlaybackTimeMpv{fakeMpv: newFakeMpv(), fail: true}
	e := New(Deps{SelfPub: "self", Printf: pf, Mpv: m})
	e.localVersion = 1000

	e.userAction(context.Background(), "seeked to 1:23")

	select {
	case msg := <-e.outbox:
		t.Fatalf("must not broadcast on a playback-time read failure, got %+v", msg)
	default:
	}
	if e.localVersion != 1000 {
		t.Errorf("localVersion must not advance when the broadcast is skipped, got %d", e.localVersion)
	}
	lines := get()
	if len(lines) != 1 || lines[0] != "* you seeked to 1:23" {
		t.Errorf("local confirmation must still print, got %v", lines)
	}
}

// TestUserActionBroadcastsAfterTransientRecovery checks the self-healing
// side: once mpv answers again, the very next action broadcasts normally.
func TestUserActionBroadcastsAfterTransientRecovery(t *testing.T) {
	pf, _ := capture()
	m := &errPlaybackTimeMpv{fakeMpv: newFakeMpv(), fail: true}
	e := New(Deps{SelfPub: "self", Printf: pf, Mpv: m})

	e.userAction(context.Background(), "paused at 0:05")  // fails, skipped
	e.userAction(context.Background(), "resumed at 0:05") // mpv recovered

	select {
	case msg := <-e.outbox:
		if msg.State == nil {
			t.Fatal("expected a state broadcast")
		}
	default:
		t.Fatal("expected the recovered action to broadcast")
	}
}

// --- ignored peers can't kick ---

// TestIgnoredPeerCannotKick is the regression test for the
// kick-the-room-empty griefing bug: chat and state from an ignored peer
// are already dropped, but MsgKick wasn't — so a peer everyone had just
// kicked (and therefore auto-ignored) could retaliate by kicking every
// other member in turn, since nothing stopped its own kick messages from
// being honored.
func TestIgnoredPeerCannotKick(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{SelfPub: "self", Printf: pf})
	e.ignored["griefer"] = true

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgKick, From: "griefer", Kicked: "victim",
	})

	if e.ignored["victim"] {
		t.Error("a kick from an already-ignored peer must not be honored")
	}
}

// --- deferred version commit on a failed property apply ---

// TestApplyRemoteStateDefersCommitOnFailedProperty is the regression test
// for the diverge-forever bug: a failed SetSpeed/SetSid/SetAid/
// SetSubDelay/SeekAbsolute used to still let localVersion advance to the
// remote version — since ties break on pubkey, the peer that wins every
// tie would never see that version "lose" again to retry it, so the
// property stayed diverged until a genuinely new action arrived. A failed
// property apply must leave the version uncommitted, mirroring how a
// playlist-index change already defers the commit.
func TestApplyRemoteStateDefersCommitOnFailedProperty(t *testing.T) {
	pf, _ := capture()
	m := &errSetSpeedMpv{fakeMpv: newFakeMpv()}
	e := New(Deps{SelfPub: "zzz", Printf: pf, Mpv: m}) // largest pubkey: wins every tie
	e.propsReady = true
	e.speed = 1.0
	e.localVersion = 100

	e.applyRemoteState(context.Background(), protocol.Message{
		Type: protocol.MsgState, From: "aaa",
		State: &protocol.PlayState{Version: 200, Speed: 2.0, SentAt: time.Now().UnixMilli()},
	})

	if e.localVersion != 100 {
		t.Errorf("localVersion must not commit when a property fails to apply, got %d", e.localVersion)
	}
}

// --- /peers drift display ---

// TestPrintPeersAdjustsForClockOffset is the regression test for the
// phantom-drift display bug: the drift shown by /peers used the peer's
// raw SentAt with no clock-offset correction, unlike the sync path
// (applyRemoteState) which already knows how to correct for it — so a
// peer with real, benign clock skew showed as constantly "ahead"/"behind"
// even though the engine itself knew that wasn't true drift.
func TestPrintPeersAdjustsForClockOffset(t *testing.T) {
	pf, lines := capture()
	m := newFakeMpv() // paused at position 0 — matches the peer's true position below
	e := New(Deps{SelfPub: "self", Printf: pf, Mpv: m})
	now := time.Now()

	// Peer is truly at the same position 0 as us right now, but its clock
	// reads 5s ahead of ours, so it stamped SentAt 5s into what looks (to
	// us, uncorrected) like the future. Projected without correction that
	// reads as 5s of drift; corrected, there's none.
	e.peers["p"] = &peerInfo{
		nick:        "peer",
		clockOffset: 5000, // peer_clock - our_clock, ms
		hasState:    true,
		state: protocol.PlayState{
			Position: 0, Paused: false, Speed: 1.0,
			SentAt: now.UnixMilli() + 5000, // stamped on the peer's fast clock
		},
	}

	e.printPeers(context.Background())

	out := strings.Join(lines(), "\n")
	if strings.Contains(out, "ahead") || strings.Contains(out, "behind") {
		t.Errorf("clock skew must not be reported as drift once corrected, got:\n%s", out)
	}
}

// --- peer eviction re-add: clock offset + canHost ---

// TestMsgStateRediscoveryRedoesHelloExchange is the regression test for
// the eviction-loses-clock-offset bug: a >peerTimeout blip evicts a peer
// entirely, and its next heartbeat used to just re-add a bare, zero-
// valued peerInfo with no way to ever re-establish clockOffset (nothing
// else triggers the NTP hello exchange). Rediscovering a peer via
// MsgState must now redo that exchange (an addressed hello reply) and
// pick up CanHost from the message immediately.
func TestMsgStateRediscoveryRedoesHelloExchange(t *testing.T) {
	pf, _ := capture()
	m := newFakeMpv()
	e := New(Deps{SelfPub: "self", Printf: pf, Mpv: m})
	// No prior peerInfo for "evicted-peer" — as if peerTimeout just fired
	// and heartbeat's eviction loop deleted it.

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgState, From: "evicted-peer", Nick: "returner", CanHost: true,
		State: &protocol.PlayState{Paused: true, SentAt: time.Now().UnixMilli()},
	})

	p := e.peers["evicted-peer"]
	if p == nil {
		t.Fatal("peer should be re-added")
	}
	if !p.canHost {
		t.Error("canHost should be picked up from the rediscovery message immediately, not left at its zero value")
	}

	sawAddressedHello := false
drain:
	for {
		select {
		case msg := <-e.outbox:
			if msg.Type == protocol.MsgHello && msg.To == "evicted-peer" {
				sawAddressedHello = true
			}
		default:
			break drain
		}
	}
	if !sawAddressedHello {
		t.Error("rediscovering a peer via MsgState should redo the addressed hello exchange to re-establish clock sync")
	}
}

// TestMsgStateCarriesCanHostForKnownPeer checks the heartbeat-level
// safety net: even without any hello round trip, a known peer's canHost
// status stays current because every MsgState (not just MsgHello) now
// carries it.
func TestMsgStateCarriesCanHostForKnownPeer(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{SelfPub: "self", Printf: pf})
	e.peers["p"] = &peerInfo{nick: "peer", lastSeen: time.Now(), canHost: false}

	e.handleMessage(context.Background(), protocol.Message{
		Type: protocol.MsgState, From: "p", CanHost: true,
		State: &protocol.PlayState{Paused: true, Version: 1},
	})

	if !e.peers["p"].canHost {
		t.Error("canHost should update from a known peer's heartbeat, not just from hello")
	}
}

// TestCheckReceiveHealthWarnsWhenNotReceiving is the regression test for
// the invisible-dead-subscription bug: a stalled receive side (go-nostr's
// own reconnect backoff for reads can run up to 5 minutes) used to look
// exactly like a quiet room, with nothing telling the user anything was
// wrong. checkReceiveHealth must print a notice once Receiving reports
// false, and not repeat it on every subsequent heartbeat.
func TestCheckReceiveHealthWarnsWhenNotReceiving(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Printf: pf, Receiving: func() bool { return false }})

	e.checkReceiveHealth(context.Background())
	if !e.receiveWarned {
		t.Error("receiveWarned should be set once Receiving reports false")
	}
	warnCount := 0
	for _, l := range lines() {
		if strings.Contains(l, "not receiving") {
			warnCount++
		}
	}
	if warnCount != 1 {
		t.Fatalf("expected exactly one warning line, got %d: %v", warnCount, lines())
	}

	e.checkReceiveHealth(context.Background()) // a second beat, still not receiving
	warnCount = 0
	for _, l := range lines() {
		if strings.Contains(l, "not receiving") {
			warnCount++
		}
	}
	if warnCount != 1 {
		t.Errorf("warning should not repeat every heartbeat, got %d occurrences: %v", warnCount, lines())
	}
}

// TestCheckReceiveHealthRecoversPrintsNotice checks the matching
// recovery notice fires exactly once when Receiving flips back to true.
func TestCheckReceiveHealthRecoversPrintsNotice(t *testing.T) {
	pf, lines := capture()
	receiving := false
	e := New(Deps{Printf: pf, Receiving: func() bool { return receiving }})

	e.checkReceiveHealth(context.Background())
	if !e.receiveWarned {
		t.Fatal("precondition: should have warned")
	}

	receiving = true
	e.checkReceiveHealth(context.Background())
	if e.receiveWarned {
		t.Error("receiveWarned should clear once Receiving reports true again")
	}
	var sawRecovery bool
	for _, l := range lines() {
		if strings.Contains(l, "receiving from relays again") {
			sawRecovery = true
		}
	}
	if !sawRecovery {
		t.Errorf("expected a recovery notice, got %v", lines())
	}
}

// TestCheckReceiveHealthNilReceivingTreatedHealthy guards the "not
// wired up" case (older test helpers, or a future caller that doesn't
// set Receiving) — nil must never be mistaken for "always failing".
func TestCheckReceiveHealthNilReceivingTreatedHealthy(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{Printf: pf})

	e.checkReceiveHealth(context.Background())
	if e.receiveWarned {
		t.Error("a nil Receiving hook must never trigger the stalled-subscription warning")
	}
	if len(lines()) != 0 {
		t.Errorf("expected no output, got %v", lines())
	}
}
