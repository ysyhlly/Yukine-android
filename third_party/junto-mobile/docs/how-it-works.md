# How junto works

junto is a leaderless watch-party coordinator: two roles (host and joiner),
no server in the middle. This is the technical breakdown of the pieces —
for install/usage, see the [README](../README.md).

- **Rooms.** A room is a random 128-bit secret, encoded as the `jun1...`
  code. From it both sides derive (domain-separated SHA-256) a public
  room ID used for relay filtering, and a NIP-44 key that encrypts every
  message. Relays and lurkers see only ciphertext under an unlinkable
  room tag. Each participant uses a throwaway nostr keypair per session.
- **Transport.** Messages are ephemeral nostr events (kind 29888 —
  relays forward them but don't store them), published redundantly to
  several free relays (`relay.damus.io`, `nos.lol`, `relay.primal.net`,
  `nostr.mom` by default). junto scores each relay by measured publish
  latency and sends to the reachable ones fastest-first, benching a relay
  that fails repeatedly for a short cooldown (and falling back to the full
  set if all are down), so play/pause/seek take the snappiest path.
- **Sync.** Leaderless last-writer-wins: explicit actions (pause, seek,
  speed, subtitle, playlist changes) carry a version timestamp and win
  everywhere; 2-second heartbeats double as presence and drift beacons.
  Peers measure their clock skew NTP-style on join (round-trip timestamps
  echoed both ways) and compensate for it, so a peer whose system clock is
  off doesn't read as drift. Small divergence (about 0.05–0.4 s) is eased
  out by briefly nudging mpv's playback rate up to 3 % — invisible, and
  local-only so it never drags the room's speed — while a larger gap still
  triggers a one-way hard seek (so clients can't fight). Remote commands
  applied to mpv are echo-suppressed so they aren't rebroadcast.
- **File transfer.** The joiner opens one persistent WebRTC connection
  per file: a single SDP offer/answer pair (non-trickle ICE) travels
  through the room's encrypted channel, then byte ranges are negotiated
  *on the data channel itself* — so the whole file streams over one
  ordered reliable channel in 16 KiB chunks with backpressure, and a
  seek redirects that stream to a new offset without tearing the
  connection down and rebuilding it. Every 256 KiB chunk group is
  verified against the host's announced BLAKE3/Bao root *as it arrives*
  (the host computes the small verification tree when creating the room;
  joiners fetch and hash-check it once), so bad bytes are rejected before
  they ever reach disk or playback; a dropped connection resumes from the
  verified bytes already on disk rather than restarting. STUN
  hole-punching is used for the direct connection; when it fails, the
  error says exactly what to do, and `-turn` routes the transfer through
  a TURN relay you supply.
- **Swarm.** The host isn't the only source: peers advertise which
  verified pieces they hold on their regular heartbeats, and a streaming
  joiner fetches from up to two additional sources alongside the host —
  other joiners mid-download, local-file peers, or a joiner that already
  finished. Helper sources fill the playhead window right behind the
  host's stride, then fetch the rarest pieces beyond it, so the room
  spreads bytes instead of everyone queueing on one upload. Because every
  chunk is verified against the host's announced hash, an extra source
  can never inject corrupt data — a source that tries is dropped
  permanently, and a pair that simply can't connect just leaves the
  download exactly as fast as it was.
- **Upload fairness.** When one node serves several streaming viewers at
  once, its uplink is shared. Each viewer reports how much video it has
  buffered ahead (a small `buf` frame on the data channel), and a viewer
  who's comfortably buffered briefly yields its share so the one about to
  run dry is served first. Since the room pauses whenever *anyone* stalls,
  feeding the worst-off viewer first is what raises the whole room's
  floor. It's a no-op with one viewer and never throttles a viewer enough
  to stall it.
- **Streaming.** A joiner that fetches from the host doesn't wait for
  the download — junto runs a tiny local HTTP server and points mpv at
  it, so playback starts within seconds while bytes arrive in the
  background (across the whole playlist). mpv gets a generous jitter buffer
  (read-ahead up to ~120 s) on top of the bytes already on disk ahead of
  the playhead, and the room pauses *only* when mpv actually runs dry
  (`paused-for-cache`) — never on speculative read-ahead — so a large
  buffer and an instant start coexist instead of trading off. The download
  follows the playhead in a bandwidth-adaptive window sized to measured
  throughput, rather than racing to fill the whole file front-to-back.
  Because media indexes are read on open, junto fetches the index region
  first (bypassing the window): for mp4/mov it walks the box structure to
  pull the `moov` atom *exactly* (any size, wherever it sits — front or
  end), and for other containers (e.g. mkv Cues) it grabs the last 16 MiB.
  Either way, files whose index is at the end start quickly instead of
  forcing a full download. A live progress bar (percent, bytes, speed,
  ETA) tracks the background download, pinned to the bottom row so chat
  scrolls above it. A streaming joiner also holds its "ready" signal at the
  start gate until a real cushion of video is buffered ahead of the start,
  so pressing play leads to playback rather than an instant stall — for
  mp4/mov the cushion scales to a few seconds of the file's real bitrate
  (read from its own index), falling back to a fixed few-MB floor for
  other containers. The trade-off of starting fast: smoothness then rides
  on the download staying ahead of playback, so a high-bitrate file over a
  link that can't keep up will pause to buffer (see the README's
  Limitations section).
- **Seek-ahead buffering.** Seek past what's downloaded and junto steers
  the download to fetch that region next (random access, not just
  front-to-back) and pauses the whole room with a "buffering…" notice
  until everyone has the bytes, then resumes together — instead of one
  peer silently stalling while the rest play on. If one peer holds the
  room past 20 seconds, the room continues without them automatically
  (the host can also press Enter to skip the wait immediately); the
  left-behind peer isn't dropped — they resync to the room's live
  position on their own once their download catches up.
- **Relay resilience.** Dropped relay connections are re-dialed silently
  in the background; if every relay is briefly unreachable you see a
  "reconnecting…" notice and then "reconnected", with the room resyncing
  on the next heartbeat — never a permanent freeze.
- **Host resilience.** If the host closes mpv or drops offline, joiners
  are told ("host appears to have left — waiting…", escalating to a
  "gone for X minutes — use /quit" notice) instead of hanging silently.
  And if a still-present member can serve the files — someone with local
  copies, or a streaming joiner whose download already completed and
  verified — the room elects a new host automatically (the largest-pubkey
  eligible peer, agreed the same way by everyone with no coordination
  round): it re-announces the playlist so late joiners still connect, and
  streaming joiners redirect their download to it — verification
  continues across the switch. Even with *no* promotable member, peers
  mid-download keep fetching what the room collectively holds from each
  other via the swarm.
- **In-player notices.** Chat, joins and leaves, buffering, pause/resume/
  seek, kicks, relay reconnects, and the "press Enter to start" prompt show
  as OSD overlays inside the mpv window — where you're actually looking —
  mirrored alongside the terminal output. They're dispatched off the sync
  loop, so a slow mpv socket can't stall playback.
- **Security model.** Coordination is encrypted (NIP-44) under the room
  secret *and* every event is BIP-340 signature-verified, so only people you
  share the code with can read or send room messages — and a relay can't
  forge who said what. A message addressed to one peer (e.g. a WebRTC
  signal) is still decryptable by every room member, not just its
  recipient: addressing is routing, not privacy — the room secret is the
  actual privacy boundary, and filtering by recipient happens client-side
  after decryption. Transferred bytes are verified in 256 KiB chunks
  against the host's announced BLAKE3/Bao root before use (whole-file
  SHA-256 in rooms created by older versions). Peer-supplied
  text (nicks, chat, file names) is stripped of control characters before
  display so a malicious peer can't hijack your terminal, and host-announced
  file names are reduced to a safe basename before any local write (no path
  traversal). The local streaming server binds to `127.0.0.1` only. The room
  is **cooperative**, though: anyone with the code is trusted inside it —
  there's no defense against a member who deliberately misbehaves (e.g.
  holding the room in "buffering", or seizing the serving role via host
  migration to redirect a download to themselves). Even then, every
  transferred byte is hash-verified — per chunk, from whichever peer it
  came — so a misbehaving peer can stall or redirect but never feed you
  corrupt data. (See the README's Limitations section and
  [ROADMAP.md](../ROADMAP.md).)

See also [docs/junto-architecture.excalidraw](junto-architecture.excalidraw)
for a diagram breakdown of the same system, and [ROADMAP.md](../ROADMAP.md)
for what's shipped and what's next.
