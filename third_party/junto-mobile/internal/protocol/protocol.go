// Package protocol defines the wire messages exchanged between room
// members. Messages travel as JSON inside NIP-44-encrypted ephemeral
// nostr events; this package has no dependency on nostr itself.
package protocol

import (
	"encoding/json"
	"fmt"
	"strings"
	"unicode"
	"unicode/utf8"
)

// Display-string limits on peer-supplied fields. Caps bound how much a
// peer can splatter across another watcher's terminal / mpv OSD; the
// control-character stripping (SafeText) prevents ANSI-escape injection.
const (
	maxNickLen = 64
	maxChatLen = 2000
	maxNameLen = 255  // typical filesystem component limit
	maxFiles   = 1024 // playlist entries a host may announce
	// maxSDPLen bounds Signal.SDP. A real WebRTC offer/answer runs at most
	// a few KB even with many ICE candidates; this exists to reject a
	// hostile or corrupted payload before it reaches pion's SDP parser.
	maxSDPLen = 64 << 10
)

// maxPlausiblePosition bounds PlayState.Position (seconds). Real playback
// position is never negative and comes nowhere close to this — it exists
// only to reject an overtly implausible or malicious value (say, ±1e18)
// before it flows into drift-projection math and an absolute mpv seek.
const maxPlausiblePosition = 1e7 // ~115 days

// maxPlausibleFileSize bounds FileMeta.Size. It exists to reject a
// negative or overtly implausible size (a hostile MaxInt64, say) before
// it flows into sparse-file creation and disk-space accounting — real
// media files, even multi-hour 4K remuxes, are nowhere close to this.
const maxPlausibleFileSize = 10 << 40 // 10 TiB

// maxPlausibleDurationSecs bounds FileMeta.DurationSecs, mirroring
// maxPlausiblePosition's order of magnitude: a real file's duration is
// nowhere close to this, and rejecting an absurd value here keeps a
// corrupt or malicious duration from producing a nonsensical bitrate
// downstream (pre-buffer sizing, stall prediction, the host's own
// upload-capacity warning).
const maxPlausibleDurationSecs = 1e7 // ~115 days

// Swarm advertisement caps. Have/HaveDone ride every 2-second heartbeat,
// so they must stay small on the wire and cheap to validate; a peer with
// more fragmented coverage than fits must coarsen (drop spans), never
// exceed — senders build against these same exported caps. Piece indices
// are bounded far above any real file (a 10 TiB file at 4 MiB pieces is
// ~2.6M pieces, well under 1<<31).
const (
	MaxHaveFiles  = 4       // files a single advertisement may cover
	MaxHavePieces = 64      // piece spans per advertised file
	MaxHaveDone   = 16      // completed-file index ranges
	maxPieceIndex = 1 << 31 // upper bound on any advertised piece index
)

// maxPlausibleActionVersion bounds PlayState.Version — a wall-clock-ms
// timestamp stamped on every explicit user action, compared last-writer-
// wins across peers. It exists to reject overtly implausible or malicious
// values (a garbage or attacker-supplied MaxInt64, say) that would
// otherwise become an unbeatable version and permanently freeze the
// room's controls, since no legitimate future action could ever exceed
// it. It is deliberately far beyond any real clock skew — an ordinary
// misconfigured clock a few hours or even years off must still be
// accepted (last-writer-wins tolerates that; only the unbeatable-forever
// case needs rejecting here).
const maxPlausibleActionVersion = 4102444800000 // 2100-01-01T00:00:00Z, unix ms

// SafeText neutralizes a peer-supplied string for display: it drops every
// Unicode control character (ESC, CR/LF, etc.) so a malicious nick, chat
// line, or file name cannot inject terminal escape sequences (cursor
// moves, screen clears) or forge extra output lines, and it truncates to
// max runes. Tabs become spaces. Normal printable Unicode is preserved.
// SECURITY: peer data reaches the terminal and mpv OSD; sanitize at the
// decode boundary so no print site can forget to.
func SafeText(s string, max int) string {
	s = strings.Map(func(r rune) rune {
		switch {
		case r == '\t':
			return ' '
		case unicode.IsControl(r):
			return -1
		default:
			return r
		}
	}, s)
	if utf8.RuneCountInString(s) > max {
		// Truncate on a rune boundary.
		i, n := 0, 0
		for i = range s {
			if n == max {
				return s[:i]
			}
			n++
		}
	}
	return s
}

// Version is the wire protocol version, stamped on every encoded
// message. Bump it on breaking changes to the message schema or room
// semantics, so mismatched peers can detect each other instead of
// silently misbehaving.
const Version = 1

type MsgType string

const (
	MsgHello   MsgType = "hello"    // announce presence + nick; host includes Files
	MsgLeave   MsgType = "leave"    // clean departure
	MsgState   MsgType = "state"    // heartbeat and explicit user actions
	MsgChat    MsgType = "chat"     // chat line
	MsgFileReq MsgType = "file-req" // joiner -> host: please send file FileIndex
	MsgSignal  MsgType = "signal"   // WebRTC SDP offer/answer, addressed
	MsgReady   MsgType = "ready"    // loaded and ready for the host to start (readiness gate)
	MsgKick    MsgType = "kick"     // ask a peer to leave; others auto-ignore them (cooperative)
)

// Message is the envelope for everything sent in a room. To is a peer
// pubkey for addressed messages; empty means broadcast. From is never
// serialized — the transport fills it from the signed event's pubkey,
// so peers cannot spoof it inside the payload.
type Message struct {
	Type      MsgType    `json:"type"`
	V         int        `json:"v,omitempty"` // protocol version; Encode stamps it
	From      string     `json:"-"`
	To        string     `json:"to,omitempty"`
	Nick      string     `json:"nick,omitempty"`
	State     *PlayState `json:"state,omitempty"`
	Text      string     `json:"text,omitempty"`
	Files     []FileMeta `json:"files,omitempty"`    // hello: the host's playlist
	CanHost   bool       `json:"can_host,omitempty"` // hello: sender has local files and can become host
	FileIndex int        `json:"fidx,omitempty"`     // file-req: which playlist entry
	Offset    int64      `json:"off,omitempty"`      // file-req: start at this byte
	Length    int64      `json:"len,omitempty"`      // file-req: send at most this many bytes (0 = to EOF)
	Signal    *Signal    `json:"signal,omitempty"`
	Kicked    string     `json:"kicked,omitempty"` // kick: pubkey of the peer being kicked

	// Swarm coverage advertisement, piggybacked on state heartbeats (a new
	// message type would be rejected by older binaries' validate; unknown
	// fields are ignored, so these are backward-compatible). Have lists
	// verified piece coverage for files still downloading; HaveDone lists
	// [lo,hi) flat playlist index ranges this peer has completely. Only
	// fully verified pieces may be advertised — under-advertising is safe,
	// over-advertising just gets the peer deprioritized by requesters.
	Have     []HaveEntry `json:"hv,omitempty"`
	HaveDone [][2]int    `json:"hvd,omitempty"`

	// Clock-sync fields (NTP-style RTT measurement).
	// SentAt is the sender's wall clock at send time (ms). EchoAt and
	// EchoRcvd let the original sender recover RTT and compute the clock
	// offset: EchoAt echoes back the SentAt from the message being
	// acknowledged, EchoRcvd is when the echoing peer received it.
	SentAt   int64 `json:"sat,omitempty"`
	EchoAt   int64 `json:"eat,omitempty"`
	EchoRcvd int64 `json:"ert,omitempty"`
}

// PlayState describes the sender's playback at SentAt. Version is the
// unix-millis timestamp of the sender's last explicit user action and
// orders actions last-writer-wins; heartbeats carry it unchanged.
type PlayState struct {
	Paused   bool    `json:"paused"`
	Position float64 `json:"pos"`
	SentAt   int64   `json:"at"`
	Version  int64   `json:"ver"`
	Speed    float64 `json:"speed,omitempty"` // playback rate; decode normalizes 0 to 1.0
	Index    int     `json:"idx,omitempty"`   // 0-based playlist position
	Sid      int     `json:"sid,omitempty"`   // subtitle track; 0 = off (mpv ids are 1-based)
	Aid      int     `json:"aid,omitempty"`   // audio track; 0 = off (mpv ids are 1-based)
	SubDelay float64 `json:"subd,omitempty"`  // subtitle delay in seconds, may be negative
	// Buffering is true while this peer is stalled waiting for its
	// streaming download to reach the current position. The room pauses
	// while any peer is buffering (seek-ahead buffering).
	Buffering bool `json:"buf,omitempty"`
	// DL is this peer's download progress as a percent (1–100) while
	// streaming; 0/omitted means not downloading (host, or a joiner with
	// a local copy). Shown in /peers so the host can see who's behind.
	DL int `json:"dl,omitempty"`
}

// HaveEntry advertises one downloading file's verified coverage as
// [lo,hi) piece-index spans (pieces are a fixed multiple of the file's
// bao chunk group — see internal/transfer). Spans must be sorted,
// non-overlapping, and cover only fully verified pieces.
type HaveEntry struct {
	File   int      `json:"f"`
	Pieces [][2]int `json:"p"`
}

type FileMeta struct {
	Name   string `json:"name"`
	Size   int64  `json:"size"`
	SHA256 string `json:"sha256"`
	// Bao/BaoGroup/BaoOb enable per-chunk verification so bytes can be
	// fetched from any peer, not just the host: Bao is the hex 32-byte
	// BLAKE3/Bao root, BaoGroup the log2 chunks-per-group it was encoded
	// with, and BaoOb the hex sha256 of the outboard tree blob (fetched
	// over the data channel, verifiable against this hash from any peer).
	// All three are set together or not at all; absence means a pre-swarm
	// host, and joiners fall back to host-only whole-file verification.
	Bao      string `json:"bao,omitempty"`
	BaoGroup int    `json:"baog,omitempty"`
	BaoOb    string `json:"baoob,omitempty"`
	// DurationSecs is the host-computed playback duration, used to derive
	// a bitrate (Size/DurationSecs) for the pre-buffer cushion and stall
	// prediction — see transfer.ProbeDuration. Currently populated for
	// mp4/mov only (parsed from the local moov/mvhd box); 0 means unknown,
	// omitted on the wire so old/new binaries interoperate without a
	// version bump.
	DurationSecs float64 `json:"dur,omitempty"`
	// Subs are subtitle sidecar files (.srt/.ass) that belong to this
	// video; joiners fetch them alongside it and load them into mpv.
	Subs []FileMeta `json:"subs,omitempty"`
}

type Signal struct {
	Kind string `json:"kind"` // "offer" | "answer"
	SDP  string `json:"sdp"`
}

func Encode(m Message) ([]byte, error) {
	if err := m.validate(); err != nil {
		return nil, err
	}
	m.V = Version
	return json.Marshal(m)
}

func Decode(data []byte) (Message, error) {
	var m Message
	if err := json.Unmarshal(data, &m); err != nil {
		return Message{}, err
	}
	if m.State != nil && m.State.Speed == 0 {
		m.State.Speed = 1.0 // omitted on the wire at the default rate
	}
	// Sanitize every peer-supplied display string at the boundary so no
	// downstream print site (terminal or mpv OSD) can be tricked into
	// emitting attacker-controlled terminal escape sequences. Names are
	// also fed to the filesystem, but sanitizeName re-checks there.
	m.Nick = SafeText(m.Nick, maxNickLen)
	m.Text = SafeText(m.Text, maxChatLen)
	for i := range m.Files {
		m.Files[i].Name = SafeText(m.Files[i].Name, maxNameLen)
		for j := range m.Files[i].Subs {
			m.Files[i].Subs[j].Name = SafeText(m.Files[i].Subs[j].Name, maxNameLen)
		}
	}
	if err := m.validate(); err != nil {
		return Message{}, err
	}
	return m, nil
}

func (m *Message) validate() error {
	switch m.Type {
	case MsgHello:
		// A hello carries the host's playlist; bound it so a malicious host
		// can't drive unbounded allocation / filesystem churn on joiners
		// (each entry becomes a sparse .part file).
		total := len(m.Files)
		for _, f := range m.Files {
			total += len(f.Subs)
		}
		if total > maxFiles {
			return fmt.Errorf("hello announces %d files (max %d)", total, maxFiles)
		}
		for _, f := range m.Files {
			if err := validateFileMeta(f); err != nil {
				return err
			}
			for _, sub := range f.Subs {
				if err := validateFileMeta(sub); err != nil {
					return err
				}
			}
		}
	case MsgLeave, MsgChat, MsgReady:
	case MsgKick:
		if m.Kicked == "" {
			return fmt.Errorf("kick message without a target")
		}
	case MsgFileReq:
		if m.FileIndex < 0 {
			return fmt.Errorf("file-req with negative index")
		}
		if m.Offset < 0 || m.Length < 0 {
			return fmt.Errorf("file-req with negative offset or length")
		}
	case MsgState:
		s := m.State
		if s == nil {
			return fmt.Errorf("state message without state payload")
		}
		if s.Speed != 0 && (s.Speed < 0.01 || s.Speed > 100) {
			return fmt.Errorf("speed %v out of range", s.Speed)
		}
		if s.Index < 0 || s.Sid < 0 || s.Aid < 0 {
			return fmt.Errorf("negative playlist index, subtitle id, or audio id")
		}
		if s.Version < 0 || s.Version > maxPlausibleActionVersion {
			return fmt.Errorf("implausible version %d", s.Version)
		}
		if s.Position < 0 || s.Position > maxPlausiblePosition {
			return fmt.Errorf("implausible position %v", s.Position)
		}
	case MsgSignal:
		if m.Signal == nil {
			return fmt.Errorf("signal message without signal payload")
		}
		if m.Signal.Kind != "offer" && m.Signal.Kind != "answer" {
			return fmt.Errorf("unknown signal kind %q", m.Signal.Kind)
		}
		if len(m.Signal.SDP) > maxSDPLen {
			return fmt.Errorf("signal SDP too large (%d bytes, max %d)", len(m.Signal.SDP), maxSDPLen)
		}
	default:
		return fmt.Errorf("unknown message type %q", m.Type)
	}
	// Coverage advertisements normally ride MsgState heartbeats, but the
	// bounds hold wherever the fields appear.
	return validateHave(m.Have, m.HaveDone)
}

// validateFileMeta rejects a negative or overtly implausible file size or
// duration before either flows into sparse-file creation, disk-space
// accounting, or bitrate math (pre-buffer sizing, stall prediction).
func validateFileMeta(f FileMeta) error {
	if f.Size < 0 || f.Size > maxPlausibleFileSize {
		return fmt.Errorf("file %q has an implausible size %d", f.Name, f.Size)
	}
	if f.DurationSecs < 0 || f.DurationSecs > maxPlausibleDurationSecs {
		return fmt.Errorf("file %q has an implausible duration %v", f.Name, f.DurationSecs)
	}
	// The bao fields come as a unit: a root without a fetchable-and-
	// checkable outboard (or vice versa) is unusable, and catching the
	// inconsistency here beats a confusing verification failure later.
	if f.Bao == "" {
		if f.BaoGroup != 0 || f.BaoOb != "" {
			return fmt.Errorf("file %q announces partial verification metadata", f.Name)
		}
		return nil
	}
	if !isHex(f.Bao, 64) {
		return fmt.Errorf("file %q has a malformed bao root", f.Name)
	}
	if !isHex(f.BaoOb, 64) {
		return fmt.Errorf("file %q has a malformed outboard hash", f.Name)
	}
	if f.BaoGroup < 0 || f.BaoGroup > 10 {
		return fmt.Errorf("file %q has bao group %d out of range", f.Name, f.BaoGroup)
	}
	return nil
}

// isHex reports whether s is exactly n lowercase-or-uppercase hex chars.
func isHex(s string, n int) bool {
	if len(s) != n {
		return false
	}
	for _, c := range s {
		switch {
		case c >= '0' && c <= '9', c >= 'a' && c <= 'f', c >= 'A' && c <= 'F':
		default:
			return false
		}
	}
	return true
}

// validateHave bounds a swarm coverage advertisement. Sorted, non-
// overlapping spans keep both the wire size and every consumer's merge
// logic bounded; the caps reject a hostile blow-up before it allocates.
func validateHave(have []HaveEntry, haveDone [][2]int) error {
	if len(have) > MaxHaveFiles {
		return fmt.Errorf("advertisement covers %d files (max %d)", len(have), MaxHaveFiles)
	}
	for _, h := range have {
		if h.File < 0 || h.File >= maxFiles {
			return fmt.Errorf("advertisement for out-of-range file %d", h.File)
		}
		if len(h.Pieces) > MaxHavePieces {
			return fmt.Errorf("advertisement with %d piece spans (max %d)", len(h.Pieces), MaxHavePieces)
		}
		prev := -1
		for _, p := range h.Pieces {
			if p[0] < 0 || p[1] <= p[0] || p[1] > maxPieceIndex {
				return fmt.Errorf("advertisement with malformed piece span [%d,%d)", p[0], p[1])
			}
			if p[0] < prev {
				return fmt.Errorf("advertisement with unsorted or overlapping piece spans")
			}
			prev = p[1]
		}
	}
	if len(haveDone) > MaxHaveDone {
		return fmt.Errorf("advertisement with %d done ranges (max %d)", len(haveDone), MaxHaveDone)
	}
	prev := -1
	for _, r := range haveDone {
		if r[0] < 0 || r[1] <= r[0] || r[1] > maxFiles {
			return fmt.Errorf("advertisement with malformed done range [%d,%d)", r[0], r[1])
		}
		if r[0] < prev {
			return fmt.Errorf("advertisement with unsorted or overlapping done ranges")
		}
		prev = r[1]
	}
	return nil
}
