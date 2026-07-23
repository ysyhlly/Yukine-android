package syncer

// Snapshot is a consistent, read-only view of the room's live state,
// built from the engine's single-writer peer state (see buildSnapshot).
// A TUI renders it as the live peer/room panel; it is also the single
// source of truth the text /peers output formats from. Emitted through
// the optional Deps.OnSnapshot hook at every point peer state changes.
type Snapshot struct {
	SelfNick   string
	SelfColor  uint8   // ANSI-256 nick color for this peer
	SelfPos    float64 // current local playback position (seconds); -1 if unknown
	Host       bool    // this peer is the room host
	GateOpen   bool    // playback has started (past the readiness gate)
	Paused     bool    // room is paused
	ReadyCount int     // peers reporting ready (including self), pre-start
	TotalCount int     // everyone in the room, including self
	Relayed    bool    // best-effort: transfer is going through a TURN relay
	Peers      []PeerStatus
}

// PeerStatus is one remote peer's live status, mirroring what the text
// /peers command shows plus the fields a panel badge needs.
type PeerStatus struct {
	Nick       string
	Color      uint8   // ANSI-256 nick color
	HasState   bool    // a heartbeat has been received (position/DL known)
	Pos        float64 // projected playback position (seconds), clock-adjusted
	Drift      float64 // signed seconds vs. local playback (+ ahead, - behind); 0 if unknown
	DriftKnown bool
	DL         int  // download progress percent (1–100); 0 = not downloading
	Buffering  bool // stalled fetching its current position
	Ready      bool // reported ready at the pre-start gate
	Ignored    bool // locally muted (/ignore or kicked)
}
