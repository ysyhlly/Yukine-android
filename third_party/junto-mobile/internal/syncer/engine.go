// Package syncer keeps every room member's mpv at the same position.
// Design: leaderless last-writer-wins. Explicit user actions (pause,
// play, seek, speed, subtitles, playlist jumps) carry a version (their
// wall-clock millis) and beat older state everywhere. Between actions,
// heartbeats double as drift beacons: on equal versions, ties break
// toward the larger pubkey, so corrections flow one way and the
// largest active pubkey is the de-facto drift reference — no seek
// wars, automatic handoff when it leaves.
package syncer

import (
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/swayam-mishra/junto/internal/debug"
	"github.com/swayam-mishra/junto/internal/human"
	"github.com/swayam-mishra/junto/internal/mpv"
	"github.com/swayam-mishra/junto/internal/protocol"
)

// heartbeatEvery is the interval between state heartbeats. A var (not a
// const) only so the end-to-end loopback test can shrink it; production
// never changes it.
var heartbeatEvery = 2 * time.Second

// maxDesyncWindow bounds how long the room holds paused for one peer's
// buffering flag before continuing without them (slow-peer recovery).
// Deliberately much longer than heartbeatEvery/peerTimeout: an ordinary
// network hiccup recovering within a handful of seconds is exactly the
// case the buffering hold exists to smooth over, so the window has to
// tolerate that — but it must still be finite. A var so tests can shrink
// it; production never changes it.
var maxDesyncWindow = 20 * time.Second

const (
	peerTimeout = 7 * time.Second

	// Host-absence thresholds for joiners.
	hostWarnAfter     = 4 * time.Second // first "host appears to have left" warning (~one missed beat)
	hostEscalateAfter = 2 * time.Minute // switch to "X minutes" message after this long
	hostEscalateEvery = 1 * time.Minute // repeat the "X minutes" escalation at this interval

	// hostAnnounceRepeats is how many extra heartbeats a freshly promoted
	// host re-broadcasts its Files-hello for, on top of the one sent at
	// promotion time — at heartbeatEvery (2s) this spans the peerTimeout
	// (7s) eviction window at least once, so a peer that missed the first
	// announcement (lost relay delivery, or mid-eviction) still catches a
	// later one.
	hostAnnounceRepeats = 4

	// maxClockSyncRTT bounds the round-trip delay (ms) of a clock-offset
	// measurement we'll trust. The NTP offset estimate assumes a symmetric
	// path; a round slower than this is too jittery to believe, so we keep
	// the previous offset rather than poison it with a bad sample.
	maxClockSyncRTT = 4000

	// OSD display durations (milliseconds). Gate prompts are re-sent each
	// heartbeat with a duration slightly longer than the interval, so they
	// appear permanently on screen while the condition is active.
	osdChat  = 6000 // chat messages linger longer to be read
	osdEvent = 4000 // join/leave/action/kick
	osdBuf   = 3000 // buffering notices (re-sent on state change)
	osdGate  = 3000 // ready-gate prompts (re-sent each heartbeat)

	obsPause          = 1
	obsSpeed          = 2
	obsSubDelay       = 3
	obsSid            = 4
	obsPlaylistPos    = 5
	obsPausedForCache = 6
	obsAid            = 7
)

// Player is the subset of *mpv.Client the engine drives. Extracted so the
// full session loop can run against a fake in tests; *mpv.Client satisfies it.
type Player interface {
	ObserveProperty(ctx context.Context, id int, name string) error
	SetPause(ctx context.Context, paused bool) error
	SeekAbsolute(ctx context.Context, secs float64) error
	SetSpeed(ctx context.Context, speed float64) error
	SetSubDelay(ctx context.Context, secs float64) error
	SetSid(ctx context.Context, sid int) error
	SetAid(ctx context.Context, aid int) error
	SetPlaylistPos(ctx context.Context, idx int) error
	AddSubtitle(ctx context.Context, path string) error
	GetPlaybackTime(ctx context.Context) (float64, error)
	ShowText(ctx context.Context, text string, durationMs int) error
	Events() <-chan mpv.Event
	Done() <-chan error
	Close() error
}

type Deps struct {
	Mpv     Player
	Send    func(context.Context, protocol.Message) error
	Inbox   <-chan protocol.Message
	Lines   <-chan string // user-typed lines (chat and /commands)
	Printf  func(format string, a ...any)
	SelfPub string
	Nick    string
	// Host marks the room creator. The host drives the readiness gate:
	// it holds at the start screen showing who's ready and starts
	// everyone with one keypress. Joiners wait for the host to start.
	Host  bool
	Files []protocol.FileMeta // set on the host; included in hellos
	// PlaylistLen is how many entries the local mpv playlist has; used
	// to bounds-check remote indices (an out-of-range playlist-pos
	// write idles or exits mpv).
	PlaylistLen int
	Colors      bool

	// Host-side hooks, invoked from the engine loop; must not block.
	OnFileReq func(from string, index int, offset, length int64)
	OnSignal  func(from string, sig protocol.Signal)

	// CanHost is true if this peer has local file copies and can step up
	// as host if the original host drops.
	CanHost bool

	// CanHostDynamic, if non-nil, supplements CanHost with an eligibility
	// that can change mid-session: a streaming joiner becomes able to host
	// once its download completes and verifies (FileStore.AllDone). Read
	// each time eligibility is advertised or an election is evaluated;
	// must not block.
	CanHostDynamic func() bool

	// OnBecomeHost is called once when this peer wins the host election.
	// It returns the OnFileReq and OnSignal hooks needed for file serving.
	// Nil for streaming joiners (they cannot serve files).
	OnBecomeHost func(files []protocol.FileMeta) (
		onFileReq func(from string, index int, offset, length int64),
		onSignal func(from string, sig protocol.Signal),
	)

	// OnHostChange is called when hostPub changes due to migration.
	// Streaming joiners use this to redirect their downloader to the new host.
	// Must not block.
	OnHostChange func(newPub string)

	// OnSnapshot, if non-nil, is called with a consistent view of room/peer
	// state whenever that state changes (heartbeat, join/leave, gate). The
	// TUI renders it as the live panel. Fired only from the Run goroutine's
	// synchronous call chain, so it observes race-free peer state. Nil on
	// the plain path.
	OnSnapshot func(Snapshot)

	// Swarm coverage plumbing (streaming joiners; all nil otherwise).
	// HaveSnapshot returns this peer's verified-coverage advertisement to
	// attach to each heartbeat (FileStore.AdvertSnapshot). OnPeerHave
	// receives another peer's advertisement from its heartbeat, and
	// OnPeerGone fires when a peer leaves or is evicted so it stops being
	// considered a source. None may block.
	HaveSnapshot func() ([]protocol.HaveEntry, [][2]int)
	OnPeerHave   func(peer string, have []protocol.HaveEntry, done [][2]int)
	OnPeerGone   func(peer string)

	// OnHostFiles is called once when the remote host's file list is first
	// received. Local-file joiners use it to validate their local copies
	// against the announced name / size. Must not block.
	OnHostFiles func(files []protocol.FileMeta)

	// ReadyCh, if non-nil, is waited on (non-blocking select) each heartbeat
	// before this peer declares itself ready to the room. Streaming joiners
	// close it once a minimum pre-buffer cushion is available. Nil means
	// "ready immediately" (host, local-file joiners).
	ReadyCh <-chan struct{}

	// Buffer signals local seek-ahead buffering on/off (streaming joiners
	// only). Nil for host/local sessions — a nil channel never selects.
	Buffer <-chan bool

	// DownloadProgress reports our own streaming download as a fraction
	// (0–1); nil for the host and local-file joiners. Surfaced to peers
	// in heartbeats and shown in /peers.
	DownloadProgress func() float64

	// Bitrate is the known bitrate (bytes/sec) of the file gating the
	// pre-buffer readiness check, for the stall-prediction warning; 0
	// means unknown (non-mp4, or an mp4 whose duration couldn't be
	// parsed).
	Bitrate float64

	// CurrentRate reports the downloader's measured throughput
	// (bytes/sec); nil for the host and local-file joiners (nothing is
	// being downloaded).
	CurrentRate func() float64

	// Subs delivers downloaded subtitle sidecar files to load into mpv,
	// keyed by the playlist index they belong to. Nil unless streaming a
	// room whose host shared subtitles.
	Subs <-chan SubReady

	// Receiving reports whether the transport has received anything from
	// any relay recently (including our own heartbeats echoed back, which
	// keep arriving even in an otherwise-empty room) — a health signal for
	// the receive side, distinct from whether Send is succeeding. A dead
	// subscription can otherwise look identical to a quiet room: sends
	// keep working while go-nostr backs off reconnecting the read side
	// for up to 5 minutes. Nil is treated as always healthy (e.g. tests).
	Receiving func() bool

	Log *debug.Logger
}

// SubReady announces that subtitle file Path (for playlist item Index)
// has finished downloading and can be loaded into mpv.
type SubReady struct {
	Index int
	Path  string
}

type peerInfo struct {
	nick      string
	lastSeen  time.Time
	buffering bool               // peer is stalled fetching its current position
	state     protocol.PlayState // last heartbeat, for /peers position/drift
	hasState  bool
	canHost   bool // peer advertised CanHost:true in their hello

	// Slow-peer recovery: bufferingSince marks when this buffering episode
	// started (set on the false->true transition); leftBehind is set once
	// it's run past maxDesyncWindow (or the host overrides), at which
	// point anyPeerBuffering stops honoring this peer's hold. Reset when
	// their buffering clears, so the next episode gets a fresh window.
	bufferingSince time.Time
	leftBehind     bool

	// Clock-sync state (NTP-style, measured on hello exchange).
	lastRcvdSentAt int64 // peer's SentAt from their most recent message
	lastRcvdAt     int64 // our wall clock when we received that message
	clockOffset    int64 // EMA of (peer_clock − our_clock) in ms
}

// ema64 applies an exponential moving average (α=0.3) to an int64 sample.
// When old is 0 the sample is taken as-is (first measurement bootstrap).
func ema64(old, sample int64) int64 {
	if old == 0 {
		return sample
	}
	return int64(float64(old)*0.7 + float64(sample)*0.3)
}

// updateClockSync records the latest SentAt from p's message and, when
// the message carries an echo of something we sent, applies the NTP
// formula to update p.clockOffset (peer_clock − our_clock in ms).
func (e *Engine) updateClockSync(p *peerInfo, m protocol.Message, now time.Time) {
	sentAt := m.SentAt
	if sentAt == 0 && m.State != nil {
		sentAt = m.State.SentAt
	}
	if sentAt > 0 {
		p.lastRcvdSentAt = sentAt
		p.lastRcvdAt = now.UnixMilli()
	}
	// NTP offset: T1=EchoAt (our original send), T2=EchoRcvd (peer's
	// receive time), T3=m.SentAt (peer's send time), T4=now.
	if m.EchoAt != 0 && m.EchoRcvd != 0 && m.SentAt != 0 {
		t1, t2, t3, t4 := m.EchoAt, m.EchoRcvd, m.SentAt, now.UnixMilli()
		// Round-trip delay; the offset estimate assumes a symmetric path,
		// so a slow or jittery relay round makes it unreliable. Trust the
		// sample only when the delay is sane — a bad offset would make
		// sync worse than none.
		rtt := (t4 - t1) - (t3 - t2)
		if rtt >= 0 && rtt <= maxClockSyncRTT {
			sample := ((t2 - t1) + (t3 - t4)) / 2
			p.clockOffset = ema64(p.clockOffset, sample)
		}
	}
}

// greet sends a fresh addressed hello (clock-sync echo plus our files and
// host status) and a state snapshot to a peer we've just added to
// e.peers. Used both for a genuine first-ever join and for rediscovering
// a peer after a >peerTimeout eviction: an evicted-then-re-added peer
// gets a brand new zero-valued peerInfo (see the MsgState/MsgReady
// unknown branches), losing clockOffset and canHost with no other path
// to re-establish them — this restarts the same hello round trip that
// established them the first time. Caller must have already applied
// updateClockSync to the triggering message so p.lastRcvdSentAt/
// lastRcvdAt are current.
func (e *Engine) greet(ctx context.Context, to string, p *peerInfo, now time.Time) {
	// Addressed so the exchange doesn't echo forever.
	e.broadcast(protocol.Message{
		Type:     protocol.MsgHello,
		To:       to,
		Nick:     e.d.Nick,
		Files:    e.d.Files,
		CanHost:  e.canHostNow(),
		SentAt:   now.UnixMilli(),
		EchoAt:   p.lastRcvdSentAt,
		EchoRcvd: p.lastRcvdAt,
	})
	// Send our state snapshot too, so the peer lands at the room's
	// position in one relay round-trip instead of waiting out a heartbeat.
	if e.d.Mpv != nil {
		if pos, err := e.d.Mpv.GetPlaybackTime(ctx); err == nil {
			snap := e.snapshot(pos)
			e.broadcast(protocol.Message{
				Type:    protocol.MsgState,
				To:      to,
				Nick:    e.d.Nick,
				CanHost: e.canHostNow(),
				State:   snap,
				SentAt:  snap.SentAt,
			})
		}
	}
}

type Engine struct {
	d Deps

	paused       bool
	speed        float64
	subDelay     float64
	sid          int
	aid          int
	index        int
	localVersion int64
	peers        map[string]*peerInfo
	suppress     suppression
	seekPending  bool
	// sidInit / aidInit absorb mpv's automatic track selections, which
	// fire uncaused property-changes on startup and on every playlist advance.
	sidInit bool
	aidInit bool
	// loading is true between a playlist-pos change and the next
	// playback-restart; internal seeks during that window (and the
	// restart itself) are not user actions.
	loading bool
	// warnedIndex rate-limits the "you don't have that item" warning,
	// which would otherwise repeat with every heartbeat.
	warnedIndex int
	// seenProps tracks which observed properties have delivered their
	// initial echo. Remote states must not be applied before then: the
	// echoes carry pre-command values, and applying a command first
	// makes its own echo look like a local user action (the inbox can
	// be full of buffered heartbeats after a long file fetch).
	seenProps  map[int]bool
	propsReady bool
	started    time.Time
	outbox     chan protocol.Message

	// Readiness gate. selfReady becomes true once our mpv has loaded the
	// file; ready tracks which peers have signaled the same. gateOpen
	// flips once playback has begun (we start it as host, or we see a
	// peer playing) — after that the gate is irrelevant and late joiners
	// just adopt the current position.
	host      bool
	selfReady bool
	ready     map[string]bool
	gateOpen  bool

	// Seek-ahead buffering. `paused` above is the room's *user* pause
	// intent (synced last-writer-wins); `buffering` is whether we're
	// stalled fetching the current position. mpv's actual pause
	// (`mpvPaused`) is the OR of user intent and anyone buffering, so the
	// room holds together while any peer catches up, then falls back to
	// user intent. Reconciled only through applyDesiredPause. bufferingSince
	// marks when our own current episode started, for slow-peer recovery's
	// resync-on-catch-up (setBuffering): mirrors peerInfo.bufferingSince
	// but for ourselves.
	buffering      bool
	bufferingSince time.Time
	mpvPaused      bool

	// warnedNewer rate-limits the "peer runs a newer junto" notice to
	// once per peer.
	warnedNewer map[string]bool

	// receiveWarned tracks whether the "not receiving from relays" notice
	// is currently showing, so heartbeat (every 2s) doesn't reprint it and
	// a later recovery can print a matching "receiving again" exactly once.
	receiveWarned bool

	// wasHostEligible tracks the last advertised host-eligibility so the
	// dynamic flip (a streaming joiner's download completing) is logged
	// exactly once, not on every heartbeat.
	wasHostEligible bool

	// ignored holds pubkeys whose chat and playback commands we suppress
	// locally (/ignore, and anyone /kick'd).
	ignored map[string]bool
	// quitRequested is set when a kick targets us; Run exits after the
	// current message.
	quitRequested bool
	// lastState is the most recent remote heartbeat we've seen, kept so
	// /sync can immediately re-adopt the room's state.
	lastState     protocol.PlayState
	lastStateFrom string
	haveLast      bool

	// pendingSubs holds downloaded subtitle paths keyed by playlist
	// index, loaded into mpv when that item is current; addedSubs guards
	// against loading the same file twice.
	pendingSubs map[int][]string
	addedSubs   map[string]bool

	// lastGateMsg is the most recently printed gate-prompt text; used to
	// suppress duplicate terminal lines when the prompt refreshes every
	// heartbeat for OSD persistence.
	lastGateMsg string

	// Host-absence tracking (joiner-only). hostPub is set on the first hello
	// we receive that carries Files (only the host sends those). hostGoneAt is
	// zero while the host is reachable; set when the first warning is printed.
	hostPub            string
	hostGoneAt         time.Time
	hostLastEscalation time.Time

	// Host-migration state. peerFiles is a copy of Files from the original
	// host's hello, reused when this peer elects itself as the new host.
	// promoted is set after selfPromote runs so its one-time state
	// transition (file-serving wiring, "you are now host" message) only
	// fires once. hostAnnounceRetries separately counts down extra
	// Files-hello re-broadcasts after promotion: unlike a joiner's hello
	// (which gets an addressed reply), the promotion announcement is a
	// single best-effort relay send with no reply to confirm delivery, so
	// repeating it a few heartbeats closes the gap where the one-shot
	// version is lost and a peer is stuck on the dead host forever.
	peerFiles           []protocol.FileMeta
	promoted            bool
	hostAnnounceRetries int

	// nudging is true while a rate-nudge drift correction is applied to
	// mpv. The nudge changes mpv's actual playback rate but never e.speed
	// (the user-intended speed we broadcast and LWW-sync), so a local
	// drift correction stays local and isn't adopted by peers. When the
	// drift clears we restore mpv to e.speed and unset this.
	nudging bool
	// nudgeUntil is a backstop deadline checked every heartbeat: a
	// same-version heartbeat from the nudge's originator never re-enters
	// applyRemoteState's tie-break on a peer that wins every tie (see
	// remoteWins), so without this the nudge would never be re-evaluated
	// and would run forever. Zero while not nudging.
	nudgeUntil time.Time
}

func New(d Deps) *Engine {
	return &Engine{
		d:           d,
		paused:      true, // mpv is launched with --pause
		mpvPaused:   true, // matches the --pause launch
		speed:       1.0,
		sidInit:     true,
		aidInit:     true,
		loading:     true,
		peers:       make(map[string]*peerInfo),
		suppress:    newSuppression(),
		seenProps:   make(map[int]bool),
		started:     time.Now(),
		outbox:      make(chan protocol.Message, 64),
		host:        d.Host,
		ready:       make(map[string]bool),
		warnedNewer: make(map[string]bool),
		ignored:     make(map[string]bool),
		pendingSubs: make(map[int][]string),
		addedSubs:   make(map[string]bool),
	}
}

// Run drives the session until the user quits, mpv exits, or ctx is
// canceled. It is the only goroutine touching engine state.
func (e *Engine) Run(ctx context.Context) error {
	role := "joiner"
	if e.d.Host {
		role = "host"
	}
	e.d.Log.E("session_start", "role", role, "nick", e.d.Nick, "self", e.d.SelfPub[:min(8, len(e.d.SelfPub))])
	go e.senderLoop(ctx)
	defer e.sendLeave()

	for id, prop := range map[int]string{
		obsPause: "pause", obsSpeed: "speed", obsSubDelay: "sub-delay",
		obsSid: "sid", obsAid: "aid", obsPlaylistPos: "playlist-pos",
		obsPausedForCache: "paused-for-cache",
	} {
		if err := e.d.Mpv.ObserveProperty(ctx, id, prop); err != nil {
			return fmt.Errorf("observing %s: %w", prop, err)
		}
	}
	// SentAt lets whoever replies echo it back, so clock-offset measurement
	// completes on the first reply round in BOTH directions (without it, only
	// the replier learns the offset and the joiner stays uncompensated).
	e.broadcast(protocol.Message{Type: protocol.MsgHello, Nick: e.d.Nick, Files: e.d.Files, CanHost: e.canHostNow(), SentAt: time.Now().UnixMilli()})

	heartbeat := time.NewTicker(heartbeatEvery)
	defer heartbeat.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case err := <-e.d.Mpv.Done():
			e.d.Printf("* mpv closed — ending the session")
			_ = err
			return nil
		case ev, ok := <-e.d.Mpv.Events():
			if !ok {
				return nil
			}
			e.handleMpvEvent(ctx, ev)
		case m, ok := <-e.d.Inbox:
			if !ok {
				e.d.Printf("* lost connection to all relays")
				return nil
			}
			e.handleMessage(ctx, m)
			if e.quitRequested { // a kick targeted us
				return nil
			}
		case <-heartbeat.C:
			e.heartbeat(ctx)
		case b, ok := <-e.d.Buffer:
			if !ok {
				// A nil channel blocks forever in a select, unlike a
				// closed one (always ready with ok=false) — without this,
				// a closed Buffer would spin this loop at 100% CPU forever.
				e.d.Buffer = nil
			} else {
				e.setBuffering(ctx, b)
			}
		case sr, ok := <-e.d.Subs:
			if !ok {
				e.d.Subs = nil
			} else {
				e.onSubReady(ctx, sr)
			}
		case line, ok := <-e.d.Lines:
			if !ok {
				return nil
			}
			if quit := e.handleLine(ctx, line); quit {
				return nil
			}
		}
	}
}

// senderLoop publishes queued messages and, since heartbeats flow
// through here every 2s, doubles as a relay-health monitor: go-nostr
// silently re-dials dropped relays underneath us, so all we owe the
// user is a "reconnecting…" / "reconnected" notice when every relay is
// briefly unreachable. State is local to this goroutine (no races).
func (e *Engine) senderLoop(ctx context.Context) {
	connected := true
	fails := 0
	for {
		select {
		case <-ctx.Done():
			return
		case m := <-e.outbox:
			if err := e.d.Send(ctx, m); err != nil {
				fails++
				if connected && fails >= 2 { // 2 consecutive send failures (≤~4s of heartbeats)
					connected = false
					e.d.Log.E("relay_lost", "fails", fails)
					msg := "* lost the relays — reconnecting…"
					e.d.Printf("%s", msg)
					e.osd(ctx, msg, osdEvent)
				}
			} else {
				fails = 0
				if !connected {
					connected = true
					e.d.Log.E("relay_alive")
					msg := "* reconnected to relays"
					e.d.Printf("%s", msg)
					e.osd(ctx, msg, osdEvent)
				}
			}
		}
	}
}

// broadcast queues a message without ever blocking the caller — the
// engine loop is the only writer here, and it also owns mpv event
// handling, drift correction, and user input, so a blocked send would
// stall all of that too. If the outbox is saturated (e.g. every relay is
// down and senderLoop is stuck retrying at up to ~10s per attempt), the
// oldest queued message is dropped to make room for the new one: relay
// delivery is already best-effort, and a stalled engine loop is worse
// than losing one queued send during a sustained outage.
func (e *Engine) broadcast(m protocol.Message) {
	select {
	case e.outbox <- m:
		return
	default:
	}
	select {
	case <-e.outbox: // drop the oldest to make room
	default:
	}
	select {
	case e.outbox <- m:
	default:
		// Lost a race with senderLoop draining a slot; give up rather
		// than block or spin — the next broadcast tries again.
	}
}

func (e *Engine) sendLeave() {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	_ = e.d.Send(ctx, protocol.Message{Type: protocol.MsgLeave, Nick: e.d.Nick})
}

// --- mpv side ---------------------------------------------------------

func (e *Engine) handleMpvEvent(ctx context.Context, ev mpv.Event) {
	switch ev.Event {
	case "property-change":
		if !e.propsReady {
			e.seenProps[ev.ID] = true
			e.propsReady = len(e.seenProps) >= 7
		}
		e.handlePropertyChange(ctx, ev)
	case "seek":
		if e.loading {
			return // internal seek during a file transition
		}
		e.seekPending = true
	case "playback-restart":
		e.loading = false
		// A file just loaded (or a seek completed): attach any shared
		// subtitles already downloaded for the current playlist item.
		e.loadSubsFor(ctx, e.index)
		if !e.seekPending {
			return // file load, not a seek
		}
		e.seekPending = false
		pos, err := e.d.Mpv.GetPlaybackTime(ctx)
		if err != nil {
			return
		}
		if e.suppress.seek.claim(pos, time.Now()) {
			return
		}
		e.userAction(ctx, "seeked to "+formatPos(pos))
	}
}

func (e *Engine) handlePropertyChange(ctx context.Context, ev mpv.Event) {
	now := time.Now()
	switch ev.ID {
	case obsPause:
		var v bool
		if err := json.Unmarshal(ev.Data, &v); err != nil {
			return // null before file load
		}
		if v == e.mpvPaused {
			return // matches what we last set mpv to (our own echo / dup)
		}
		e.mpvPaused = v
		if e.suppress.pause.claim(v, now) {
			return
		}
		// Unsuppressed change = a manual pause/play in the mpv window, i.e.
		// the user's intent. Record it and broadcast; then re-assert a
		// buffering hold if one is active (e.g. user hit play mid-buffer).
		e.paused = v
		if !v {
			e.openGate() // playback has begun; the readiness gate is moot
		}
		pos, _ := e.d.Mpv.GetPlaybackTime(ctx)
		e.userAction(ctx, map[bool]string{true: "paused at ", false: "resumed at "}[v]+formatPos(pos))
		e.applyDesiredPause(ctx)
	case obsSpeed:
		var v float64
		if err := json.Unmarshal(ev.Data, &v); err != nil {
			return
		}
		if floatEq(v, e.speed) {
			return // initial echo (default 1.0) or duplicate
		}
		// Claim before adopting: a suppressed change (a remote speed sync we
		// applied, or a local drift-correction nudge) must not be recorded as
		// the user's intended speed, or the nudge would leak into heartbeats.
		if e.suppress.speed.claim(v, now) {
			return
		}
		// A genuine local speed change supersedes any active nudge.
		e.nudging = false
		e.nudgeUntil = time.Time{}
		e.speed = v
		e.userAction(ctx, fmt.Sprintf("set speed to %.2fx", v))
	case obsSubDelay:
		var v float64
		if err := json.Unmarshal(ev.Data, &v); err != nil {
			return
		}
		if floatEq(v, e.subDelay) {
			return // initial echo (default 0) or duplicate
		}
		e.subDelay = v
		if e.suppress.subDelay.claim(v, now) {
			return
		}
		e.userAction(ctx, fmt.Sprintf("set subtitle delay to %+.2fs", v))
	case obsSid:
		v, ok := parseSid(ev.Data)
		if !ok {
			return
		}
		if e.sidInit {
			// mpv auto-selected a track for this file; not a user action.
			e.sid = v
			e.sidInit = false
			return
		}
		if v == e.sid {
			return
		}
		e.sid = v
		if e.suppress.sid.claim(v, now) {
			return
		}
		if v == 0 {
			e.userAction(ctx, "turned subtitles off")
		} else {
			e.userAction(ctx, fmt.Sprintf("switched to subtitle track %d", v))
		}
	case obsAid:
		v, ok := parseSid(ev.Data) // same loose-typing as sid: int | false | "no"
		if !ok {
			return
		}
		if e.aidInit {
			e.aid = v
			e.aidInit = false
			return
		}
		if v == e.aid {
			return
		}
		e.aid = v
		if e.suppress.aid.claim(v, now) {
			return
		}
		if v == 0 {
			e.userAction(ctx, "turned audio off")
		} else {
			e.userAction(ctx, fmt.Sprintf("switched to audio track %d", v))
		}
	case obsPlaylistPos:
		var v int
		if err := json.Unmarshal(ev.Data, &v); err != nil || v < 0 {
			return // -1 means nothing current
		}
		if v == e.index {
			return // initial echo or duplicate
		}
		e.index = v
		e.loading = true
		e.sidInit = true
		e.aidInit = true
		if e.suppress.index.claim(v, now) {
			return
		}
		// User skipped, or mpv auto-advanced at EOF. Everyone reaching
		// EOF broadcasts the same index; adoption is then a no-op.
		e.userAction(ctx, fmt.Sprintf("moved to item %d/%d%s", v+1, e.d.PlaylistLen, e.itemName(v)))
	case obsPausedForCache:
		var v bool
		if err := json.Unmarshal(ev.Data, &v); err != nil {
			return // null before first file load
		}
		e.setBuffering(ctx, v)
	}
}

// parseSid handles mpv's loosely-typed sid values: an int track id,
// false / "no" for disabled, "auto" or null for not-yet-decided.
func parseSid(data json.RawMessage) (int, bool) {
	var n int
	if err := json.Unmarshal(data, &n); err == nil {
		return n, true
	}
	var b bool
	if err := json.Unmarshal(data, &b); err == nil && !b {
		return 0, true
	}
	var s string
	if err := json.Unmarshal(data, &s); err == nil && s == "no" {
		return 0, true
	}
	return 0, false // "auto", null, or garbage: no effective track yet
}

func (e *Engine) itemName(idx int) string {
	if idx < len(e.d.Files) {
		return ": " + e.d.Files[idx].Name
	}
	return ""
}

// snapshot builds the full current PlayState. pos must come from a
// successful GetPlaybackTime read.
func (e *Engine) snapshot(pos float64) *protocol.PlayState {
	s := &protocol.PlayState{
		Paused: e.paused, Position: pos, SentAt: time.Now().UnixMilli(), Version: e.localVersion,
		Speed: e.speed, Index: e.index, Sid: e.sid, Aid: e.aid, SubDelay: e.subDelay, Buffering: e.buffering,
	}
	if e.d.DownloadProgress != nil {
		if pct := int(e.d.DownloadProgress() * 100); pct < 100 {
			s.DL = max(pct, 1) // 1–99 while downloading; 0/omitted once complete
		}
	}
	return s
}

// userAction broadcasts the local user's explicit action with a fresh
// version so it wins everywhere.
func (e *Engine) userAction(ctx context.Context, what string) {
	e.d.Printf("* you %s", what)
	pos, err := e.d.Mpv.GetPlaybackTime(ctx)
	if err != nil {
		// A transient mpv IPC hiccup must never broadcast a fabricated
		// position — a fresh winning version carrying Position: 0 would
		// hard-seek every peer in the room to the start. The action already
		// took effect locally in mpv; skip the broadcast and let the next
		// successful action (or heartbeat, once mpv answers again) re-sync
		// the room instead.
		e.d.Log.E("playback_time_unavailable", "err", err.Error())
		return
	}
	// Ratchet rather than stamp raw wall-clock time: if we've already
	// adopted a remote version ahead of "now" (a peer with a fast clock,
	// or simply one who acted a moment from our future due to ordinary
	// skew), a plain time.Now() would broadcast a *lower* version that
	// loses every tie-break against it — freezing out this and every
	// other peer's controls until real time catches up to the skew. The
	// wire-level implausible-version check (protocol.validate) keeps this
	// ratchet itself from ever needing to jump further than a genuine
	// clock could plausibly explain.
	e.localVersion = max(time.Now().UnixMilli(), e.localVersion+1)
	s := e.snapshot(pos)
	s.Version = e.localVersion
	e.broadcast(protocol.Message{Type: protocol.MsgState, Nick: e.d.Nick, CanHost: e.canHostNow(), State: s})
}

// --- remote side ------------------------------------------------------

func (e *Engine) handleMessage(ctx context.Context, m protocol.Message) {
	// Ignore our own broadcast echoes that come back from the relay.
	if m.From == e.d.SelfPub {
		return
	}
	// Any inbound message may change what the live panel shows (a join,
	// leave, ready, position/DL heartbeat, kick); refresh it on the way
	// out. No-op with no mpv IPC on the plain path.
	defer e.emitSnapshot(ctx)
	if m.V > protocol.Version && !e.warnedNewer[m.From] {
		e.warnedNewer[m.From] = true
		e.d.Printf("* %s is running a newer junto (protocol v%d, yours v%d) — upgrade if things misbehave", e.peerName(m.From), m.V, protocol.Version)
	}
	now := time.Now()
	if p, known := e.peers[m.From]; known {
		p.lastSeen = now
		if m.Nick != "" {
			p.nick = m.Nick
		}
		e.updateClockSync(p, m, now)
	}
	// If the remote host just sent anything, clear any host-absence warning.
	if !e.d.Host && m.From == e.hostPub && !e.hostGoneAt.IsZero() {
		e.hostGoneAt = time.Time{}
		e.hostLastEscalation = time.Time{}
	}
	switch m.Type {
	case protocol.MsgHello:
		if _, known := e.peers[m.From]; !known {
			p := &peerInfo{nick: m.Nick, lastSeen: now, canHost: m.CanHost}
			e.peers[m.From] = p
			// Record the peer's timestamp before replying so the echo
			// fields in our hello response carry the right values.
			e.updateClockSync(p, m, now)
			// First hello that carries Files identifies the remote host —
			// or, if the current host is confirmed gone, this unknown
			// peer's Files-hello is honored as the migration announcement
			// too. Without this, a promotion hello reaching us from a
			// sender we'd never exchanged hellos with (e.g. it arrived
			// while we'd evicted them during the outage window) would be
			// silently dropped, leaving hostPub pointing at the dead host
			// forever — see adoptHost.
			if e.hostPub == "" || !e.hostGoneAt.IsZero() {
				e.adoptHost(ctx, m.From, m.Files)
			}
			msg := fmt.Sprintf("* %s joined (%d watching)", e.peerName(m.From), len(e.peers)+1)
			e.d.Log.E("peer_join", "peer", m.From[:min(8, len(m.From))], "nick", m.Nick)
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdEvent)
			e.greet(ctx, m.From, p, now)
		} else {
			// Re-hello from a known peer. Update canHost and detect host
			// migration: a peer that wasn't previously serving files is now
			// announcing Files.
			//
			// SECURITY: this accepts a new-host announcement from any room
			// member, even while the original host is still alive — a
			// member can thus seize the serving role and redirect
			// streaming joiners to itself. This is a deliberate
			// cooperative-room tradeoff (membership already requires the
			// shared room secret): a hijacker can only redirect the
			// source, not inject bad data, because every downloaded byte
			// is SHA-256 verified (transfer.fileEntry verify on
			// completion). Gating on "host confirmed gone" was rejected
			// because a joiner that misses the one-shot promotion hello
			// would be stuck on a dead host. Under a network partition
			// this can also redirect a joiner that still hears the real
			// host; it degrades to the same verified content from another
			// peer.
			e.peers[m.From].canHost = m.CanHost
			e.adoptHost(ctx, m.From, m.Files)
		}
	case protocol.MsgLeave:
		if _, known := e.peers[m.From]; known {
			delete(e.peers, m.From)
			delete(e.ready, m.From)
			e.peerGone(m.From)
			e.d.Log.E("peer_leave", "peer", m.From[:min(8, len(m.From))], "nick", m.Nick, "reason", "explicit")
			msg := fmt.Sprintf("* %s left", e.nameOf(m.Nick, m.From))
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdEvent)
			e.applyDesiredPause(ctx) // a buffering peer leaving can unblock us
		}
	case protocol.MsgChat:
		if e.ignored[m.From] {
			return
		}
		msg := fmt.Sprintf("<%s> %s", e.peerName(m.From), m.Text)
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdChat)
	case protocol.MsgKick:
		if e.ignored[m.From] {
			return // an ignored (already-kicked) peer can't kick anyone else
		}
		e.handleKick(ctx, m)
	case protocol.MsgState:
		if p, known := e.peers[m.From]; !known {
			// A heartbeat can beat the hello here — or this is a peer
			// rediscovered after a >peerTimeout eviction, which otherwise
			// loses clockOffset/canHost for good (a fresh zero-valued
			// peerInfo, and nothing else re-triggers the hello exchange
			// that established them). Either way, treat it as a join and
			// redo that exchange: greet also seeds canHost from this
			// message via updateClockSync's caller-visible p update below.
			p = &peerInfo{nick: m.Nick, lastSeen: now, canHost: m.CanHost}
			e.peers[m.From] = p
			e.updateClockSync(p, m, now)
			msg := fmt.Sprintf("* %s joined (%d watching)", e.peerName(m.From), len(e.peers)+1)
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdEvent)
			e.greet(ctx, m.From, p, now)
		} else {
			// CanHost rides every heartbeat (not just hello) so a peer
			// re-added after eviction has it correct on the very next
			// beat, without waiting on the hello round trip above.
			p.canHost = m.CanHost
		}
		// Record the state for /peers (position, drift, download %) even
		// for ignored peers — display is harmless; only their commands are.
		if m.State != nil {
			if p := e.peers[m.From]; p != nil {
				p.state, p.hasState = *m.State, true
			}
		}
		// Hand a swarm coverage advertisement to the downloader (bounds
		// were enforced at decode). Ignored peers are excluded — we won't
		// fetch from a peer the room kicked.
		if e.d.OnPeerHave != nil && !e.ignored[m.From] && (len(m.Have) > 0 || len(m.HaveDone) > 0) {
			e.d.OnPeerHave(m.From, m.Have, m.HaveDone)
		}
		if e.ignored[m.From] {
			return // suppress an ignored peer's playback/buffering influence
		}
		// Keep the latest remote state so /sync can re-adopt it instantly.
		if m.State != nil {
			e.lastState, e.lastStateFrom, e.haveLast = *m.State, m.From, true
		}
		// Seeing anyone already playing means the session has started, so
		// drop the gate before applying — a late joiner shouldn't flash a
		// "waiting for the host" message it's about to contradict.
		if m.State != nil && !m.State.Paused {
			e.openGate()
		}
		// Track the peer's buffering flag independent of the version gate
		// (a same-version heartbeat may flip only Buffering), then
		// reconcile our own pause so the room holds while anyone buffers.
		if m.State != nil {
			if p := e.peers[m.From]; p != nil && p.buffering != m.State.Buffering {
				p.buffering = m.State.Buffering
				if m.State.Buffering {
					p.bufferingSince = now // fresh episode: a full window before slow-peer recovery kicks in
				} else {
					p.leftBehind = false // caught up on their own; next episode starts clean
				}
				if e.gateOpen {
					var msg string
					switch {
					case m.State.Buffering && e.host:
						// Only the host can invoke the override, so only
						// the host is told about it — matching the
						// pre-start gate's "press Enter to start anyway".
						msg = fmt.Sprintf("* waiting for %s to buffer… (press Enter to continue without them)", e.peerName(m.From))
					case m.State.Buffering:
						msg = fmt.Sprintf("* waiting for %s to buffer…", e.peerName(m.From))
					default:
						msg = fmt.Sprintf("* %s caught up", e.peerName(m.From))
					}
					e.d.Printf("%s", msg)
					e.osd(ctx, msg, osdBuf)
				}
			}
		}
		e.applyRemoteState(ctx, m)
		e.applyDesiredPause(ctx)
	case protocol.MsgReady:
		if _, known := e.peers[m.From]; !known {
			// See the MsgState unknown branch: this may be a peer
			// rediscovered after eviction, so redo the hello exchange to
			// re-establish clockOffset (canHost isn't carried by MsgReady;
			// their next heartbeat corrects it).
			p := &peerInfo{nick: m.Nick, lastSeen: now}
			e.peers[m.From] = p
			e.updateClockSync(p, m, now)
			msg := fmt.Sprintf("* %s joined (%d watching)", e.peerName(m.From), len(e.peers)+1)
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdEvent)
			e.greet(ctx, m.From, p, now)
		}
		if !e.ready[m.From] {
			e.ready[m.From] = true
			if e.host && !e.gateOpen {
				e.refreshGatePrompt(ctx)
			}
		}
	case protocol.MsgFileReq:
		if e.d.OnFileReq != nil {
			e.d.OnFileReq(m.From, m.FileIndex, m.Offset, m.Length)
		}
	case protocol.MsgSignal:
		if e.d.OnSignal != nil {
			e.d.OnSignal(m.From, *m.Signal)
		}
	}
}

func (e *Engine) applyRemoteState(ctx context.Context, m protocol.Message) {
	if !e.propsReady {
		return // initial echoes pending; heartbeats will retry
	}
	s := m.State
	if !remoteWins(s.Version, e.localVersion, m.From, e.d.SelfPub) {
		return
	}
	// Probe mpv first: if nothing is loaded yet we must NOT adopt the
	// version, or the action would be swallowed — returning here lets
	// the sender's next heartbeat retry until mpv is ready.
	if e.d.Mpv == nil {
		return
	}
	local, err := e.d.Mpv.GetPlaybackTime(ctx)
	if err != nil {
		return
	}
	isAction := s.Version > e.localVersion
	now := time.Now()
	who := e.peerName(m.From)

	// Playlist index first: if it changes, the new file isn't loaded
	// yet, so subtitle and position corrections are skipped this round.
	// The version is then NOT committed, so the sender's next heartbeat
	// (same version, still winning) replays them against the loaded
	// file. Only a completed round commits.
	indexChanged := false
	if s.Index != e.index {
		if s.Index >= e.d.PlaylistLen {
			if e.warnedIndex != s.Index {
				e.warnedIndex = s.Index
				e.d.Printf("* %s is on playlist item %d which you don't have", who, s.Index+1)
			}
			return
		}
		e.suppress.index.set(s.Index, now)
		if err := e.d.Mpv.SetPlaylistPos(ctx, s.Index); err != nil {
			e.d.Printf("* failed to switch playlist item: %v", err)
			return // retry on the next heartbeat
		}
		e.index = s.Index
		e.loading = true
		e.sidInit = true
		e.aidInit = true
		indexChanged = true
		if isAction {
			e.d.Printf("* %s moved to item %d/%d%s", who, s.Index+1, e.d.PlaylistLen, e.itemName(s.Index))
		}
	}

	// Adopt the remote *user* pause intent; the actual mpv pause is then
	// reconciled (with any buffering) by applyDesiredPause after this
	// returns. Updating e.paused without calling SetPause here keeps the
	// buffering OR-state authoritative.
	if s.Paused != e.paused {
		e.paused = s.Paused
		if isAction {
			verb := map[bool]string{true: "paused", false: "resumed"}[s.Paused]
			msg := fmt.Sprintf("* %s %s at %s", who, verb, formatPos(s.Position))
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdEvent)
		}
	}

	// failed tracks whether any property below fails to apply, so the
	// version isn't committed on a partial round — same principle as
	// indexChanged above. Without this, a single failed SetSpeed/SetSid/
	// SetAid/SetSubDelay/SeekAbsolute call would still let e.localVersion
	// advance to s.Version, and since ties break on pubkey, the peer that
	// wins every tie would never see this version "lose" again to
	// re-trigger a retry — the property stays diverged until a genuinely
	// new action arrives.
	failed := false

	if !floatEq(s.Speed, e.speed) {
		speed := min(max(s.Speed, 0.01), 100)
		e.suppress.speed.set(speed, now)
		if err := e.d.Mpv.SetSpeed(ctx, speed); err != nil {
			e.d.Printf("* failed to apply remote speed: %v", err)
			failed = true
		} else {
			// The room's base speed changed: mpv is now at the new base,
			// not a nudge offset, so any active nudge is resolved.
			e.nudging = false
			e.nudgeUntil = time.Time{}
			e.speed = speed
			if isAction {
				e.d.Printf("* %s set speed to %.2fx", who, speed)
			}
		}
	}

	if !floatEq(s.SubDelay, e.subDelay) {
		e.suppress.subDelay.set(s.SubDelay, now)
		if err := e.d.Mpv.SetSubDelay(ctx, s.SubDelay); err != nil {
			e.d.Printf("* failed to apply remote subtitle delay: %v", err)
			failed = true
		} else {
			e.subDelay = s.SubDelay
			if isAction {
				e.d.Printf("* %s set subtitle delay to %+.2fs", who, s.SubDelay)
			}
		}
	}

	if indexChanged {
		return // version not committed; next heartbeat finishes sid + position
	}

	if s.Sid != e.sid {
		e.suppress.sid.set(s.Sid, now)
		if err := e.d.Mpv.SetSid(ctx, s.Sid); err != nil {
			e.d.Printf("* failed to apply remote subtitle track: %v", err)
			failed = true
		} else {
			e.sid = s.Sid
			if isAction {
				if s.Sid == 0 {
					e.d.Printf("* %s turned subtitles off", who)
				} else {
					e.d.Printf("* %s switched to subtitle track %d", who, s.Sid)
				}
			}
		}
	}

	if s.Aid != e.aid {
		e.suppress.aid.set(s.Aid, now)
		if err := e.d.Mpv.SetAid(ctx, s.Aid); err != nil {
			e.d.Printf("* failed to apply remote audio track: %v", err)
			failed = true
		} else {
			e.aid = s.Aid
			if isAction {
				if s.Aid == 0 {
					e.d.Printf("* %s turned audio off", who)
				} else {
					e.d.Printf("* %s switched to audio track %d", who, s.Aid)
				}
			}
		}
	}

	// A buffering peer is paused at its position even though its user
	// pause intent may be "play", so don't project its position forward.
	// Adjust SentAt into our clock frame using the measured offset so
	// clock skew between machines doesn't look like drift.
	adjustedSentAt := s.SentAt
	if peer := e.peers[m.From]; peer != nil && peer.clockOffset != 0 {
		adjustedSentAt -= peer.clockOffset
	}
	target := expectedPosition(s.Position, s.Paused || s.Buffering, s.Speed, adjustedSentAt, now.UnixMilli())
	drift := target - local
	e.d.Log.E("state_applied", "peer", m.From[:min(8, len(m.From))], "version", s.Version, "pos", s.Position, "paused", s.Paused, "index", s.Index)

	switch {
	case needsSeek(local, target):
		// Large gap — hard seek; restore base speed if a nudge was active.
		e.d.Log.E("drift_corrected", "peer", m.From[:min(8, len(m.From))], "drift_ms", drift, "target", target, "method", "seek")
		e.clearNudge(ctx, now)
		e.suppress.seek.set(target, now)
		if err := e.d.Mpv.SeekAbsolute(ctx, target); err != nil {
			e.d.Printf("* failed to apply remote seek: %v", err)
			failed = true
		} else if isAction {
			msg := fmt.Sprintf("* %s seeked to %s", who, formatPos(target))
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdEvent)
		}
	case nudgeRequired(drift) && !s.Paused && !e.paused:
		// Small gap while playing — glide back by nudging mpv's playback
		// rate around e.speed. e.speed itself is NOT changed, so the nudge
		// never enters our heartbeats or the LWW speed channel: each peer
		// corrects its own drift locally rather than dragging the room's
		// speed along with it.
		ns := nudgeSpeed(drift, e.speed)
		e.d.Log.E("drift_corrected", "peer", m.From[:min(8, len(m.From))], "drift_ms", drift, "nudge_speed", ns, "method", "nudge")
		e.suppress.speed.set(ns, now)
		if err := e.d.Mpv.SetSpeed(ctx, ns); err == nil {
			e.nudging = true
			e.nudgeUntil = now.Add(nudgeDeadline(drift, e.speed, ns))
		}
	default:
		// Drift in the noise zone — restore base speed if we were nudging.
		e.clearNudge(ctx, now)
	}

	if failed {
		return // a property failed to apply; retry against the same version next heartbeat
	}
	e.localVersion = s.Version
}

// clearNudge restores mpv's playback rate to the user-intended speed
// (e.speed) if a drift-correction nudge is active. No-op otherwise.
func (e *Engine) clearNudge(ctx context.Context, now time.Time) {
	if !e.nudging {
		return
	}
	e.d.Log.E("nudge_complete", "restored_speed", e.speed)
	e.suppress.speed.set(e.speed, now)
	_ = e.d.Mpv.SetSpeed(ctx, e.speed)
	e.nudging = false
	e.nudgeUntil = time.Time{}
}

// --- ticking ----------------------------------------------------------

func (e *Engine) heartbeat(ctx context.Context) {
	// Safety valve: if an initial echo was lost (saturated event
	// channel), don't stay gated forever.
	if !e.propsReady && time.Since(e.started) > 3*time.Second {
		e.propsReady = true
	}
	cutoff := time.Now().Add(-peerTimeout)
	for pub, p := range e.peers {
		if p.lastSeen.Before(cutoff) {
			wasBuffering := p.buffering
			delete(e.peers, pub)
			delete(e.ready, pub)
			e.peerGone(pub)
			e.d.Log.E("peer_leave", "peer", pub[:min(8, len(pub))], "nick", p.nick, "reason", "timeout")
			// Suppress the generic "left (timeout)" for the identified host
			// when we've already printed the "host appears to have left" warning —
			// the escalation path handles all subsequent messaging.
			if pub != e.hostPub || e.hostGoneAt.IsZero() {
				msg := fmt.Sprintf("* %s left (timeout)", e.nameOf(p.nick, pub))
				e.d.Printf("%s", msg)
				e.osd(ctx, msg, osdEvent)
			}
			if e.host && !e.gateOpen {
				e.refreshGatePrompt(ctx)
			}
			if wasBuffering {
				e.applyDesiredPause(ctx) // don't stay paused for a gone peer
			}
		}
	}
	e.checkDesyncTimeouts(ctx, time.Now())
	e.checkReceiveHealth(ctx)
	if !e.d.Host && e.hostPub != "" {
		e.checkHostPresence(ctx)
	}
	if e.hostAnnounceRetries > 0 {
		e.hostAnnounceRetries--
		e.announceAsHost()
	}
	pos, err := e.d.Mpv.GetPlaybackTime(ctx)
	if err != nil {
		return // not loaded yet; skip this beat
	}
	// Backstop: a nudge past its computed deadline never got a chance to
	// naturally clear (see nudgeUntil), so force it now rather than let
	// it drift the room's playback rate indefinitely.
	if e.nudging && !e.nudgeUntil.IsZero() && time.Now().After(e.nudgeUntil) {
		e.clearNudge(ctx, time.Now())
	}
	if !e.gateOpen {
		// Our file is loaded: announce readiness (re-sent each beat while
		// gated, since relays are best-effort and the host may join late).
		// Gated on selfReady — which markSelfReady itself holds false until
		// a streaming joiner's pre-buffer ReadyCh closes — so the room can
		// never be told "ready to start" before the buffer cushion the gate
		// exists to guarantee is actually there.
		e.markSelfReady(ctx)
		if e.selfReady {
			e.broadcast(protocol.Message{Type: protocol.MsgReady, Nick: e.d.Nick})
			// Keep the gate prompt visible in mpv's OSD throughout the wait.
			if e.host {
				e.refreshGatePrompt(ctx) // terminal-suppressed by lastGateMsg guard; OSD always fires
			} else {
				e.osd(ctx, "* loaded and ready — waiting for the host to start…", osdGate)
			}
		}
	}
	if eligible := e.canHostNow(); eligible != e.wasHostEligible {
		e.wasHostEligible = eligible
		if eligible && !e.d.CanHost {
			e.d.Log.E("host_eligible") // dynamic flip: download completed + verified
		}
	}
	hb := protocol.Message{Type: protocol.MsgState, Nick: e.d.Nick, CanHost: e.canHostNow(), State: e.snapshot(pos)}
	if e.d.HaveSnapshot != nil {
		// Advertise verified swarm coverage on the regular heartbeat — no
		// extra event volume, and 2s staleness is fine for a table that's
		// only ever consulted conservatively.
		hb.Have, hb.HaveDone = e.d.HaveSnapshot()
	}
	e.broadcast(hb)
	e.doEmitSnapshot(pos, true) // refresh the TUI live panel each beat
}

// checkReceiveHealth is called each heartbeat to surface a stalled relay
// subscription — sends can keep succeeding while go-nostr's read side
// silently backs off reconnecting for up to 5 minutes, which would
// otherwise just look like an unusually quiet room.
func (e *Engine) checkReceiveHealth(ctx context.Context) {
	if e.d.Receiving == nil || e.d.Receiving() {
		if e.receiveWarned {
			e.receiveWarned = false
			e.d.Log.E("relay_receive_alive")
			const msg = "* receiving from relays again"
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdEvent)
		}
		return
	}
	if !e.receiveWarned {
		e.receiveWarned = true
		e.d.Log.E("relay_receive_stalled")
		const msg = "* not receiving anything from relays — possible connection issue"
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdEvent)
	}
}

// checkHostPresence is called each heartbeat on joiners that have identified
// the remote host. It warns after one missed heartbeat cycle, tries to elect
// this peer as the new host on every tick while the host is absent, and
// escalates to a timed message if no eligible peer can step up.
func (e *Engine) checkHostPresence(ctx context.Context) {
	now := time.Now()
	p := e.peers[e.hostPub]
	hostAlive := p != nil && now.Sub(p.lastSeen) < hostWarnAfter
	if hostAlive {
		return
	}
	if e.hostGoneAt.IsZero() {
		e.hostGoneAt = now
		e.d.Log.E("host_gone", "host", e.hostPub[:min(8, len(e.hostPub))])
		const msg = "* host appears to have left — waiting..."
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdEvent)
		return
	}
	// Try to self-elect on every tick while not yet promoted.
	if !e.promoted && e.shouldElectSelf() {
		e.selfPromote(ctx)
		return
	}
	goneFor := now.Sub(e.hostGoneAt)
	if goneFor < hostEscalateAfter || now.Sub(e.hostLastEscalation) < hostEscalateEvery {
		return
	}
	e.hostLastEscalation = now
	e.d.Log.E("host_gone_escalated", "host", e.hostPub[:min(8, len(e.hostPub))], "gone_ms", goneFor.Milliseconds())
	mins := int(goneFor.Round(time.Minute).Minutes())
	if mins < 1 {
		mins = 1
	}
	suffix := "s"
	if mins == 1 {
		suffix = ""
	}
	msg := fmt.Sprintf("* host has been gone for %d minute%s — use /quit to exit", mins, suffix)
	e.d.Printf("%s", msg)
	e.osd(ctx, msg, osdEvent)
}

// adoptHost records from as our host — either the first-ever discovery
// (hostPub was empty) or a migration (hostPub pointed elsewhere) — when a
// hello carries Files. No-op if we're the host ourselves, the hello
// carries no Files, or from is already our recorded host. Shared by both
// the unknown- and known-peer MsgHello branches so a promotion is honored
// the same way whether or not we'd already exchanged hellos with the
// promoted peer; callers gate eligibility (see the unknown-peer branch's
// hostGoneAt check and the known-peer branch's SECURITY comment).
func (e *Engine) adoptHost(ctx context.Context, from string, files []protocol.FileMeta) {
	if e.d.Host || len(files) == 0 || from == e.hostPub {
		return
	}
	old := e.hostPub
	e.hostPub = from
	e.hostGoneAt = time.Time{}
	e.hostLastEscalation = time.Time{}
	if len(e.peerFiles) == 0 {
		e.peerFiles = append([]protocol.FileMeta(nil), files...)
	}
	if old == "" {
		if e.d.OnHostFiles != nil {
			e.d.OnHostFiles(e.peerFiles)
		}
		return
	}
	e.d.Log.E("host_changed", "old", old[:min(8, len(old))], "new", from[:min(8, len(from))])
	if e.d.OnHostChange != nil {
		e.d.OnHostChange(from)
	}
	msg := fmt.Sprintf("* %s is now serving files for the room", e.peerName(from))
	e.d.Printf("%s", msg)
	e.osd(ctx, msg, osdEvent)
}

// peerGone tells the downloader a peer is no longer part of the room
// (left, timed out, or kicked) so its advertised swarm coverage is
// forgotten and it stops being considered a source.
func (e *Engine) peerGone(pub string) {
	if e.d.OnPeerGone != nil {
		e.d.OnPeerGone(pub)
	}
}

// canHostNow reports this peer's current host-eligibility: the static
// CanHost flag (a local-file joiner, known at join time), or the dynamic
// hook when wired (a streaming joiner whose download has fully completed
// and verified). Consulted live at every advertisement and election so a
// mid-session flip propagates on the next heartbeat.
func (e *Engine) canHostNow() bool {
	if e.d.CanHost {
		return true
	}
	return e.d.CanHostDynamic != nil && e.d.CanHostDynamic()
}

// shouldElectSelf reports whether this peer should promote itself as the new
// host. Returns true only when it's host-eligible (canHostNow), peerFiles are
// known, and no active peer with a larger pubkey also has CanHost set.
func (e *Engine) shouldElectSelf() bool {
	if !e.canHostNow() || e.d.OnBecomeHost == nil || len(e.peerFiles) == 0 {
		return false
	}
	cutoff := time.Now().Add(-peerTimeout)
	for pub, p := range e.peers {
		if p.canHost && p.lastSeen.After(cutoff) && pub > e.d.SelfPub {
			return false // a larger-pubkey eligible peer is still active
		}
	}
	return true
}

// selfPromote makes this peer the new room host. It wires up file-serving
// callbacks, broadcasts a new hello with Files, and prints a notification.
// Guarded by e.promoted so it only runs once.
func (e *Engine) selfPromote(ctx context.Context) {
	if e.promoted || e.d.OnBecomeHost == nil || len(e.peerFiles) == 0 {
		return
	}
	onFileReq, onSignal := e.d.OnBecomeHost(e.peerFiles)
	e.d.OnFileReq = onFileReq
	e.d.OnSignal = onSignal
	e.d.Files = e.peerFiles
	e.d.Host = true
	e.host = true
	e.hostPub = ""
	e.hostGoneAt = time.Time{}
	e.hostLastEscalation = time.Time{}
	e.promoted = true
	e.hostAnnounceRetries = hostAnnounceRepeats
	e.d.Log.E("host_promoted", "self", e.d.SelfPub[:min(8, len(e.d.SelfPub))])
	msg := "* you are now the host — serving files for the room"
	e.d.Printf("%s", msg)
	e.osd(ctx, msg, osdEvent)
	e.announceAsHost()
}

// announceAsHost broadcasts the Files-hello that tells the room this peer
// now serves the playlist. Called once at promotion and then repeated a
// few heartbeats (hostAnnounceRetries, ticked down in heartbeat) since,
// unlike a joiner's hello, nothing replies to confirm delivery — a single
// best-effort relay send can simply be lost.
func (e *Engine) announceAsHost() {
	e.broadcast(protocol.Message{
		Type:    protocol.MsgHello,
		Nick:    e.d.Nick,
		Files:   e.peerFiles,
		CanHost: true,
		SentAt:  time.Now().UnixMilli(),
	})
}

// --- readiness gate ---------------------------------------------------

// markSelfReady records that our mpv has loaded and, the first time,
// surfaces the waiting state: the host gets the roster + start prompt,
// a joiner is told the host hasn't started yet.
func (e *Engine) markSelfReady(ctx context.Context) {
	if e.selfReady {
		return
	}
	// If a pre-buffer channel is wired (streaming joiners), don't declare
	// ready until it's closed. Check non-blocking so heartbeat never stalls.
	if e.d.ReadyCh != nil {
		select {
		case <-e.d.ReadyCh:
			// buffer threshold met — fall through
			e.d.Log.E("prebuffer_ready")
		default:
			return // check again next heartbeat
		}
	}
	e.selfReady = true
	e.checkStallRisk(ctx)
	if e.host {
		e.refreshGatePrompt(ctx)
	} else {
		msg := "* loaded and ready — waiting for the host to start…"
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdGate)
	}
}

// stallWarnMargin: measured throughput must be at least this fraction of
// the file's bitrate to avoid the stall-risk warning — some slack below
// exact parity so ordinary jitter doesn't trip it, while a genuinely
// under-provisioned link still gets flagged. A var so tests can tune it.
var stallWarnMargin = 0.9

// checkStallRisk runs once, right after the pre-buffer gate opens (called
// from markSelfReady, which itself only runs its body once — see
// e.selfReady), comparing the downloader's already-measured throughput
// (warmed up while filling the cushion) against the file's known bitrate.
// It's a one-time check, not a running monitor: a warning here says
// nothing about a later network blip, and repeating it would just be
// noise. CurrentRate == nil means this isn't a streaming-joiner session at
// all (host, local-file joiner) — skip entirely, including the log line,
// so those sessions' debug logs aren't cluttered with an inapplicable
// check.
func (e *Engine) checkStallRisk(ctx context.Context) {
	if e.d.CurrentRate == nil {
		return
	}
	rate := e.d.CurrentRate()
	predicted := e.d.Bitrate > 0 && rate > 0 && rate < e.d.Bitrate*stallWarnMargin
	e.d.Log.E("stall_check", "bitrate_bps", int64(e.d.Bitrate), "measured_bps", int64(rate), "predicted", predicted)
	if !predicted {
		return
	}
	msg := fmt.Sprintf("* your connection (~%s/s) looks slower than this file needs (~%s/s) — expect pauses while it buffers",
		human.Bytes(int64(rate)), human.Bytes(int64(e.d.Bitrate)))
	e.d.Printf("%s", msg)
	e.osd(ctx, msg, osdEvent)
}

// refreshGatePrompt (host, pre-start only) reprints who's ready and how
// to begin. Called whenever the ready set changes and from the heartbeat
// loop (for OSD persistence). Terminal print is suppressed when the
// message hasn't changed to avoid spam; OSD is always refreshed.
func (e *Engine) refreshGatePrompt(ctx context.Context) {
	total := len(e.peers) + 1
	ready := 0
	if e.selfReady {
		ready++
	}
	for pub := range e.peers {
		if e.ready[pub] {
			ready++
		}
	}
	var msg string
	switch {
	case total == 1:
		msg = "* no one else here yet — press Enter to start solo, or wait for friends to join"
	case ready >= total:
		msg = fmt.Sprintf("* everyone's ready (%d/%d) — press Enter to start", ready, total)
	default:
		msg = fmt.Sprintf("* waiting for peers… (%d/%d ready) — press Enter to start anyway", ready, total)
	}
	if msg != e.lastGateMsg {
		e.d.Printf("%s", msg)
		e.lastGateMsg = msg
	}
	e.osd(ctx, msg, osdGate)
	e.emitSnapshot(ctx) // refresh the readiness callout in the live panel
}

// openGate marks playback as begun, so the waiting messages and ready
// re-broadcasts stop. Idempotent.
func (e *Engine) openGate() {
	if !e.gateOpen {
		e.d.Log.E("gate_open")
	}
	e.gateOpen = true
}

// osd shows text in mpv's OSD overlay. Nil-safe and error-ignored — OSD
// is a best-effort layer on top of the terminal output. It is dispatched
// on its own goroutine so a stalled mpv IPC socket can never block the
// engine's single event loop (which would freeze sync for the room).
// ShowText goes through the mpv client's mutex-guarded Command, so it is
// safe to call concurrently; show-text just replaces the current overlay,
// so the occasional reordering of two near-simultaneous notices is fine.
func (e *Engine) osd(_ context.Context, text string, ms int) {
	if e.d.Mpv == nil {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = e.d.Mpv.ShowText(ctx, text, ms)
	}()
}

// --- seek-ahead buffering ---------------------------------------------

// anyPeerBuffering reports whether the room should stay paused for a
// peer's buffering hold. A peer marked leftBehind (slow-peer recovery:
// held the room past maxDesyncWindow, or the host overrode it) no longer
// counts — this is also what closes the SECURITY concern this function
// used to be flagged with: a peer that never clears its Buffering flag
// (malicious or just broken) can no longer freeze the room indefinitely,
// since checkDesyncTimeouts marks it leftBehind on a bounded schedule
// regardless of what that peer does.
func (e *Engine) anyPeerBuffering() bool {
	for _, p := range e.peers {
		if p.buffering && !p.leftBehind {
			return true
		}
	}
	return false
}

// checkDesyncTimeouts is slow-peer recovery's automatic half: any peer
// that's been buffering longer than maxDesyncWindow is marked leftBehind,
// so anyPeerBuffering stops honoring their hold and the room continues
// without them. Called once per heartbeat — a few seconds of overshoot
// past the exact window is fine given the window itself is tens of
// seconds. The peer isn't dropped from the room, just no longer allowed
// to hold it; they resync automatically once their own buffering clears
// (setBuffering).
func (e *Engine) checkDesyncTimeouts(ctx context.Context, now time.Time) {
	changed := false
	for pub, p := range e.peers {
		if p.buffering && !p.leftBehind && now.Sub(p.bufferingSince) > maxDesyncWindow {
			p.leftBehind = true
			changed = true
			e.d.Log.E("peer_left_behind", "peer", pub[:min(8, len(pub))], "forced", false)
			msg := fmt.Sprintf("* %s has been buffering too long — continuing without them; they'll resync once they catch up", e.peerName(pub))
			e.d.Printf("%s", msg)
			e.osd(ctx, msg, osdBuf)
		}
	}
	if changed {
		e.applyDesiredPause(ctx)
	}
}

// overridePeerBuffering is slow-peer recovery's manual half: the host
// pressing Enter mid-session (the gate is already open) skips the
// automatic maxDesyncWindow immediately for whoever's currently holding
// the room, instead of waiting it out.
func (e *Engine) overridePeerBuffering(ctx context.Context) {
	changed := false
	for pub, p := range e.peers {
		if p.buffering && !p.leftBehind {
			p.leftBehind = true
			changed = true
			e.d.Log.E("peer_left_behind", "peer", pub[:min(8, len(pub))], "forced", true)
		}
	}
	if !changed {
		return
	}
	e.d.Printf("* continuing without whoever's still buffering — they'll resync once they catch up")
	e.applyDesiredPause(ctx)
}

// applyDesiredPause reconciles mpv's actual pause state with the room's
// intent: paused if the user paused OR anyone (us or a peer) is
// buffering. The resulting echo matches mpvPaused, so obsPause treats it
// as our own and won't re-broadcast it as a user action.
func (e *Engine) applyDesiredPause(ctx context.Context) {
	want := e.paused || e.buffering || e.anyPeerBuffering()
	if want == e.mpvPaused {
		return
	}
	e.suppress.pause.set(want, time.Now())
	if err := e.d.Mpv.SetPause(ctx, want); err != nil {
		e.d.Printf("* failed to apply pause: %v", err)
		return
	}
	e.mpvPaused = want
	if !want {
		e.openGate()
	}
}

// setBuffering records our local buffering state (driven by mpv's
// paused-for-cache signal — true only when mpv's buffer is genuinely
// empty and playback cannot continue), pauses the room while we catch
// up, and announces it so peers hold with us.
func (e *Engine) setBuffering(ctx context.Context, on bool) {
	if on == e.buffering {
		return
	}
	e.buffering = on
	if on {
		e.bufferingSince = time.Now()
	}
	e.d.Log.E("buffering", "on", on)
	if e.gateOpen { // pre-start, the readiness screen already explains the wait
		var msg string
		if on {
			msg = "* buffering — waiting for the download to reach this point…"
		} else {
			msg = "* resuming"
		}
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdBuf)
	}
	if !on && time.Since(e.bufferingSince) > maxDesyncWindow {
		// Slow-peer recovery: we were stuck long enough that the rest of
		// the room would have (or did) continue without us, so resync to
		// its live position instead of quietly resuming from wherever we
		// paused and drifting behind for the rest of the session. An
		// ordinary short stall never reaches here — the room held for us
		// the whole time, so there's nothing to catch up on.
		e.resync(ctx)
	} else {
		e.applyDesiredPause(ctx)
	}
	// Tell the room now (heartbeats also carry Buffering within ~2s).
	if pos, err := e.d.Mpv.GetPlaybackTime(ctx); err == nil {
		e.broadcast(protocol.Message{Type: protocol.MsgState, Nick: e.d.Nick, CanHost: e.canHostNow(), State: e.snapshot(pos)})
	}
}

// --- shared subtitles -------------------------------------------------

// onSubReady records a downloaded subtitle for its playlist item and
// loads it immediately if that item is playing now.
func (e *Engine) onSubReady(ctx context.Context, sr SubReady) {
	for _, p := range e.pendingSubs[sr.Index] {
		if p == sr.Path {
			return // already known
		}
	}
	e.pendingSubs[sr.Index] = append(e.pendingSubs[sr.Index], sr.Path)
	if sr.Index == e.index {
		e.loadSubsFor(ctx, e.index)
	}
}

// loadSubsFor adds any not-yet-loaded subtitle files for playlist item i
// into mpv. Called when a sub arrives for the current item and whenever
// playback (re)starts on an item.
func (e *Engine) loadSubsFor(ctx context.Context, i int) {
	for _, path := range e.pendingSubs[i] {
		if e.addedSubs[path] {
			continue
		}
		if err := e.d.Mpv.AddSubtitle(ctx, path); err != nil {
			continue // try again on the next trigger
		}
		e.addedSubs[path] = true
		e.d.Printf("* loaded shared subtitles: %s", filepath.Base(path))
	}
}

// --- ignore / kick / sync ---------------------------------------------

// resolvePeer matches a /command argument against connected peers by
// nick (case-insensitive) or pubkey prefix. ambiguous is true when more
// than one peer matches.
func (e *Engine) resolvePeer(name string) (pub string, ok, ambiguous bool) {
	name = strings.ToLower(strings.TrimSpace(name))
	if name == "" {
		return "", false, false
	}
	for p := range e.peers {
		info := e.peers[p]
		if strings.ToLower(info.nick) == name || strings.HasPrefix(p, name) {
			if ok {
				return "", true, true // a second match
			}
			pub, ok = p, true
		}
	}
	return pub, ok, false
}

// ignorePeer suppresses a peer's chat and playback locally and stops the
// room from holding for their buffering.
func (e *Engine) ignorePeer(ctx context.Context, pub string) {
	e.ignored[pub] = true
	if p := e.peers[pub]; p != nil {
		p.buffering = false // no longer let them pause us
	}
	if e.lastStateFrom == pub {
		e.haveLast = false // don't /sync to an ignored peer
	}
	e.peerGone(pub) // and never fetch from them again
	e.applyDesiredPause(ctx)
}

// handleKick processes an incoming kick: if it targets us we leave;
// otherwise we auto-ignore the target so the room converges on shunning
// them. Kicks are cooperative — junto has no authority to force a peer
// off a public relay; a misbehaving client can ignore the kick.
func (e *Engine) handleKick(ctx context.Context, m protocol.Message) {
	if m.Kicked == e.d.SelfPub {
		msg := fmt.Sprintf("* you were kicked from the room by %s", e.peerName(m.From))
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdEvent)
		e.quitRequested = true
		return
	}
	if !e.ignored[m.Kicked] {
		who := e.nameOf("", m.Kicked)
		if p := e.peers[m.Kicked]; p != nil {
			who = e.nameOf(p.nick, m.Kicked)
		}
		msg := fmt.Sprintf("* %s kicked %s — ignoring them", e.peerName(m.From), who)
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdEvent)
		e.ignorePeer(ctx, m.Kicked)
	}
}

// resync forces immediate adoption of the room's latest known state,
// for /sync. Zeroing localVersion makes any peer's state win, and
// replaying the last heartbeat snaps us into line without waiting.
func (e *Engine) resync(ctx context.Context) {
	if !e.haveLast {
		e.d.Printf("* nothing to sync to yet — you're the only one here")
		return
	}
	e.localVersion = 0
	e.applyRemoteState(ctx, protocol.Message{Type: protocol.MsgState, From: e.lastStateFrom, State: &e.lastState})
	e.applyDesiredPause(ctx)
	e.d.Printf("* resynced to the room")
}

// --- user input -------------------------------------------------------

func (e *Engine) handleLine(ctx context.Context, line string) (quit bool) {
	// A command may open the gate or change pause/seek state; refresh the
	// live panel afterward. No-op on the plain path.
	defer e.emitSnapshot(ctx)
	line = strings.TrimSpace(line)
	if line == "" {
		// One keypress starts the room: the host pressing Enter at the
		// readiness gate begins playback for everyone.
		if e.host && !e.gateOpen {
			e.openGate()
			if err := e.d.Mpv.SetPause(ctx, false); err != nil {
				e.d.Printf("* %v", err)
			}
			return false
		}
		// Mid-session, the same keypress is slow-peer recovery's manual
		// override: the host can continue past a peer holding the room
		// buffering right away, instead of waiting out maxDesyncWindow.
		if e.host && e.gateOpen && e.anyPeerBuffering() {
			e.overridePeerBuffering(ctx)
		}
		return false
	}
	if !strings.HasPrefix(line, "/") {
		e.broadcast(protocol.Message{Type: protocol.MsgChat, Nick: e.d.Nick, Text: line})
		msg := fmt.Sprintf("<%s> %s", colorize("you", e.d.SelfPub, e.d.Colors), line)
		e.d.Printf("%s", msg)
		e.osd(ctx, msg, osdChat)
		return false
	}
	cmd, arg, _ := strings.Cut(line, " ")
	switch cmd {
	case "/quit":
		return true
	case "/pause":
		// Unsuppressed: mpv's property-change comes back through the
		// user-action path, which broadcasts it.
		if err := e.d.Mpv.SetPause(ctx, true); err != nil {
			e.d.Printf("* %v", err)
		}
	case "/play":
		if err := e.d.Mpv.SetPause(ctx, false); err != nil {
			e.d.Printf("* %v", err)
		}
	case "/seek":
		secs, err := parsePos(arg)
		if err != nil {
			e.d.Printf("* usage: /seek <secs|mm:ss|hh:mm:ss>")
			return false
		}
		if err := e.d.Mpv.SeekAbsolute(ctx, secs); err != nil {
			e.d.Printf("* %v", err)
		}
	case "/speed":
		rate, err := strconv.ParseFloat(strings.TrimSpace(arg), 64)
		if err != nil || rate < 0.01 || rate > 100 {
			e.d.Printf("* usage: /speed <rate>   (0.01–100, e.g. 1.5)")
			return false
		}
		if err := e.d.Mpv.SetSpeed(ctx, rate); err != nil {
			e.d.Printf("* %v", err)
		}
	case "/peers":
		e.printPeers(ctx)
	case "/ignore":
		e.cmdIgnore(ctx, arg)
	case "/unignore":
		if pub, ok, amb := e.resolvePeer(arg); !ok || amb {
			e.d.Printf("* usage: /unignore <nick> — no single match for %q", arg)
		} else if e.ignored[pub] {
			delete(e.ignored, pub)
			e.d.Printf("* no longer ignoring %s", e.peerName(pub))
		} else {
			e.d.Printf("* %s wasn't ignored", e.peerName(pub))
		}
	case "/kick":
		e.cmdKick(ctx, arg)
	case "/sync":
		e.resync(ctx)
	default:
		e.d.Printf("* commands: /pause /play /seek <time> /speed <rate> /peers /sync /ignore <nick> /kick <nick> /quit — anything else is chat")
	}
	return false
}

// printPeers lists everyone with their projected position, drift from
// our own playback, download progress, and buffering/ignored state.
// buildSnapshot assembles a consistent view of room/peer state from the
// engine's single-writer fields, adjusting each peer's position into our
// clock frame and computing drift vs. localPos. It is the single source
// of truth for both the text /peers output and the TUI live panel.
// hasLocal false means our own playback position isn't known (mpv not
// ready), so drift can't be computed. Must be called from the Run
// goroutine.
func (e *Engine) buildSnapshot(localPos float64, hasLocal bool) Snapshot {
	s := Snapshot{
		SelfNick:   e.d.Nick,
		SelfColor:  colorFor(e.d.SelfPub),
		SelfPos:    -1,
		Host:       e.host,
		GateOpen:   e.gateOpen,
		Paused:     e.paused,
		TotalCount: len(e.peers) + 1,
	}
	if hasLocal {
		s.SelfPos = localPos
	}
	if e.selfReady {
		s.ReadyCount++
	}
	now := time.Now().UnixMilli()
	for pub := range e.peers {
		p := e.peers[pub]
		ps := PeerStatus{
			Nick:      nickOr(p.nick, pub),
			Color:     colorFor(pub),
			HasState:  p.hasState,
			Buffering: p.hasState && p.state.Buffering,
			DL:        0,
			Ready:     e.ready[pub],
			Ignored:   e.ignored[pub],
		}
		if e.ready[pub] {
			s.ReadyCount++
		}
		if p.hasState {
			// Adjust SentAt into our clock frame using the measured offset,
			// same as applyRemoteState — otherwise this reports phantom
			// ahead/behind drift for any peer with real clock skew, when
			// the engine itself already knows how to correct for it.
			adjustedSentAt := p.state.SentAt
			if p.clockOffset != 0 {
				adjustedSentAt -= p.clockOffset
			}
			ps.Pos = expectedPosition(p.state.Position, p.state.Paused || p.state.Buffering, p.state.Speed, adjustedSentAt, now)
			if hasLocal {
				ps.Drift = ps.Pos - localPos
				ps.DriftKnown = true
			}
			ps.DL = p.state.DL
		}
		s.Peers = append(s.Peers, ps)
	}
	return s
}

// emitSnapshot pushes the current room state to the TUI, if a snapshot
// hook is wired. No-op (and no mpv IPC) on the plain path.
func (e *Engine) emitSnapshot(ctx context.Context) {
	if e.d.OnSnapshot == nil {
		return
	}
	local, err := e.d.Mpv.GetPlaybackTime(ctx)
	e.doEmitSnapshot(local, err == nil)
}

// doEmitSnapshot emits with an already-known local position, so a caller
// that just fetched it (heartbeat) doesn't pay for a second mpv IPC.
func (e *Engine) doEmitSnapshot(localPos float64, hasLocal bool) {
	if e.d.OnSnapshot == nil {
		return
	}
	e.d.OnSnapshot(e.buildSnapshot(localPos, hasLocal))
}

func (e *Engine) printPeers(ctx context.Context) {
	local, err := e.d.Mpv.GetPlaybackTime(ctx)
	s := e.buildSnapshot(local, err == nil)
	e.d.Printf("* %d watching:", s.TotalCount)
	self := colorize("you", e.d.SelfPub, e.d.Colors) + " (" + e.d.Nick + ")"
	if s.SelfPos >= 0 {
		e.d.Printf("*   %s — %s", self, formatPos(s.SelfPos))
	} else {
		e.d.Printf("*   %s", self)
	}
	for _, p := range s.Peers {
		line := "*   " + colorizeCode(p.Nick, p.Color, e.d.Colors)
		if p.HasState {
			line += " — " + formatPos(p.Pos)
			if p.DriftKnown && (p.Drift <= -1 || p.Drift >= 1) {
				verb := "behind"
				if p.Drift > 0 {
					verb = "ahead"
				}
				line += fmt.Sprintf(" (%s %s)", formatPos(absf(p.Drift)), verb)
			}
			if p.DL > 0 {
				line += fmt.Sprintf("  ↓%d%%", p.DL)
			}
			if p.Buffering {
				line += "  buffering"
			}
		}
		if p.Ignored {
			line += "  (ignored)"
		}
		e.d.Printf("%s", line)
	}
}

func (e *Engine) cmdIgnore(ctx context.Context, arg string) {
	pub, ok, amb := e.resolvePeer(arg)
	switch {
	case amb:
		e.d.Printf("* more than one peer matches %q — use a longer nick or a pubkey prefix", arg)
	case !ok:
		e.d.Printf("* usage: /ignore <nick> — no peer named %q", arg)
	case pub == e.d.SelfPub:
		e.d.Printf("* you can't ignore yourself")
	default:
		e.ignorePeer(ctx, pub)
		e.d.Printf("* ignoring %s — their chat and playback no longer affect you", e.peerName(pub))
	}
}

func (e *Engine) cmdKick(ctx context.Context, arg string) {
	pub, ok, amb := e.resolvePeer(arg)
	switch {
	case amb:
		e.d.Printf("* more than one peer matches %q — use a longer nick or a pubkey prefix", arg)
	case !ok:
		e.d.Printf("* usage: /kick <nick> — no peer named %q", arg)
	default:
		who := e.peerName(pub)
		e.broadcast(protocol.Message{Type: protocol.MsgKick, Nick: e.d.Nick, Kicked: pub})
		e.ignorePeer(ctx, pub)
		e.d.Printf("* kicked %s — asked them to leave; the room will ignore them", who)
	}
}

// absf is |x| for float64.
func absf(x float64) float64 {
	if x < 0 {
		return -x
	}
	return x
}

// --- helpers ----------------------------------------------------------

func (e *Engine) peerName(pub string) string {
	if p, ok := e.peers[pub]; ok {
		return e.nameOf(p.nick, pub)
	}
	return e.nameOf("", pub)
}

func (e *Engine) nameOf(nick, pub string) string {
	return colorize(nickOr(nick, pub), pub, e.d.Colors)
}

func nickOr(nick, pub string) string {
	if nick != "" {
		return nick
	}
	if len(pub) > 8 {
		return pub[:8]
	}
	return pub
}

func formatPos(secs float64) string {
	if secs < 0 {
		secs = 0
	}
	t := int(secs)
	if t >= 3600 {
		return fmt.Sprintf("%d:%02d:%02d", t/3600, t/60%60, t%60)
	}
	return fmt.Sprintf("%d:%02d", t/60, t%60)
}

func parsePos(s string) (float64, error) {
	parts := strings.Split(strings.TrimSpace(s), ":")
	if len(parts) > 3 || s == "" {
		return 0, fmt.Errorf("bad time %q", s)
	}
	var secs float64
	for _, p := range parts {
		v, err := strconv.ParseFloat(p, 64)
		if err != nil || v < 0 {
			return 0, fmt.Errorf("bad time %q", s)
		}
		secs = secs*60 + v
	}
	return secs, nil
}
