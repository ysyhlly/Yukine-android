# Changelog

All notable changes to junto are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.4.0] - 2026-07-15

### Added

- **Host upload fairness** — when the host (or any peer) serves several
  streaming viewers at once, its upload now favors whoever is closest to
  running out of buffer instead of splitting evenly. Each viewer reports
  how much video it has buffered ahead; a well-buffered viewer briefly
  yields so the one about to stall gets served first. Because the room
  pauses whenever anyone stalls, keeping the worst-off viewer fed means
  fewer pauses for everyone. It only kicks in with multiple viewers and
  never throttles a viewer enough to stall it.
- **Interactive terminal UI** — on a real terminal, junto now runs a
  redrawn interface instead of plain scrolling text: a "JUNTO" banner, a
  room panel (code, join link, playlist, connection mode), a live party
  panel (each peer's playback position, drift, download progress, and a
  ready/buffering/behind badge), a chat/notices scrollback, and a bottom
  input line. It activates automatically when stdout is a terminal;
  piped/redirected output (scripts, CI) is byte-for-byte unchanged. Set
  `JUNTO_NO_TUI=1` to force the plain output, and `NO_COLOR` still
  strips colors while keeping the layout. Every command works exactly as
  before, including pressing Enter to start the room.
- **Swarm downloads** — streaming joiners now fetch byte ranges from any
  peer that has them (other joiners mid-download, local-file peers, a
  completed joiner), not just the host, so the host's upload is no longer
  the ceiling for the whole room. Peers advertise verified coverage on
  their regular heartbeats; the downloader keeps the host feeding the
  playhead window and adds helper sources that fill it alongside, then
  fetch rarest-first beyond it. Falls back to exactly the old single-host
  behavior when no other source is available.
- **Per-chunk verification (BLAKE3/Bao)** — every 256 KiB chunk group is
  verified against the host's announced hash as it arrives, before it
  touches disk or playback, so bytes can be accepted from untrusted peers
  and a corrupt source is caught immediately instead of after the whole
  file (the old end-of-download SHA-256 check only warned).
- **Completed streaming joiners can host** — a joiner whose download
  finished and verified now becomes host-eligible, so the room survives a
  host drop even when nobody joined with local files.
- **Local-file joiners seed immediately** — peers who joined with their
  own copy now serve matching files (and advertise them) from the start,
  instead of only after being promoted to host.

### Changed

- Old and new versions still watch together: rooms created by older
  binaries work unchanged (no swarm, end-of-download verification), and
  older joiners in new rooms download exactly as before.
- In a bigger room, peers fetching spare pieces from each other now spread
  out instead of all grabbing the same one, so a larger swarm shares more
  evenly.

## [1.3.1] - 2026-07-07

### Fixed

- **macOS Gatekeeper no longer blocks a fresh install on first launch** —
  junto's releases aren't Apple-notarized, so Gatekeeper refused to run a
  freshly downloaded binary ("Apple could not verify 'junto' is free of
  malware") on first launch. Both the `curl | sh` installer and the
  Homebrew cask now strip the quarantine flag themselves after install
  (the installer after the checksum verifies the download; the cask via a
  post-install `xattr` hook). A `.pkg` or manual-download install still
  needs the one-time `xattr -d com.apple.quarantine` step, documented in
  the README.
- **The documented one-line install command survives the http→https
  redirect** — it was written as `curl junto.watch/install | sh`, and with
  no scheme curl used `http`, didn't follow junto.watch's redirect to
  `https`, and piped the "Redirecting..." body into `sh`. It is now
  `curl -fsSL https://junto.watch/install | sh`.

## [1.3.0] - 2026-07-07

### Added

- **Disk-space preflight at join**: joining a streaming room now warns
  right away if the playlist's total size (videos plus subtitle
  sidecars) is bigger than the free space in your download directory,
  instead of only finding out via a write error deep into a multi-hour
  transfer. It's a warning, not a hard block, matching the join-time
  file-mismatch warning that already exists for local-file joiners.
- **Throughput tuning pass, measured**: added two permanent benchmarks
  that settle whether the data channel's 16 KiB chunk size and 8 MiB
  backpressure window were actually well-tuned. On real loopback WebRTC,
  chunk sizes from 16 KiB to 128 KiB show no measurable throughput
  difference. Using a simulated network (pion's `vnet`) to test 20/150/
  300ms round-trip times, the backpressure window shows no measurable
  difference either — the real ceiling at high latency turns out to be
  the underlying transport's own congestion control, a layer below where
  this window operates. Both defaults are confirmed correct; nothing
  about how junto transfers files changed.
- **Bitrate-aware pre-buffer, stall prediction, and host upload-capacity
  warnings**: for mp4/mov files, junto now reads the real bitrate (file
  size ÷ duration, from the same moov index it already parses) and sizes
  the streaming readiness cushion to a few seconds of that bitrate
  instead of a fixed 4 MB — a low-bitrate file keeps the old floor, a
  high-bitrate remux gets a real cushion. Once buffering, a joiner whose
  measured download speed looks slower than the file needs is warned up
  front instead of discovering it one stall at a time, and a host whose
  measured upload can't keep every concurrently-streaming viewer fed at
  the file's bitrate is warned too. Other containers (mkv, etc.) are
  unaffected — the fixed cushion and no prediction remain, as before.
- **Closed the remaining test-coverage gaps from the July 2026 audit**:
  `internal/mpv`'s command/response round trip (pause, speed, seeks,
  track and subtitle changes) is now tested against a fake mpv socket;
  the built-in TURN credentials fetch has coverage for both the happy
  path and its short startup timeout; four fuzz targets (the mp4 index
  parser, room-code decoding, protocol JSON, and the config parser) now
  run continuously in CI; a three-peer lossy-network harness
  (drop/duplicate/reorder) now exercises the sync engine alongside the
  existing two-peer lossless test; and the file-transfer layer has new
  tests for an out-of-range transfer offset, a corrupted resume sidecar,
  and a host that goes silent mid-transfer without disconnecting.
- **CI now catches release-config drift before a real release**: a new
  job runs a full goreleaser snapshot build — covering every declared
  OS/architecture, including linux/arm64 — on every push; the release
  workflow's tests now run with `-race`; and `govulncheck` is pinned to
  a fixed version instead of tracking `@latest`.

### Changed

- **Homebrew install command is now `brew install --cask
  swayam-mishra/tap/junto`** (was `brew install
  swayam-mishra/tap/junto`) — goreleaser's `brews` (Formula) config is
  deprecated upstream, so releases now publish a Homebrew cask instead.
  `brew upgrade` needs no flag change. Anyone on the old tap formula
  should reinstall with the cask command above once a release under the
  new config is published.

### Fixed

- **A stuck or malicious peer could freeze the whole room indefinitely**
  — the buffering hold that pauses everyone while one peer's download
  catches up had no time cap, so a peer that never cleared its
  buffering flag (whether genuinely stuck or deliberately misbehaving)
  was a denial-of-service on everyone else, with no automatic recovery
  — the only escape was a human running `/kick`. The room now
  automatically continues without a peer that's held it past 20
  seconds, and the host can also press Enter to skip the wait
  immediately. The left-behind peer isn't dropped — once their own
  buffering clears, they resync to the room's live position
  automatically instead of quietly drifting behind for the rest of the
  session.
- **Distribution and install-script hardening** — a `curl | sh` install
  truncated mid-download used to execute a partial script instead of
  failing cleanly; an unauthenticated GitHub API rate limit produced a
  confusing raw error instead of a clear explanation; a missing
  `/usr/local/bin` on a fresh machine failed the install instead of
  creating it; and the mpv package-manager fallback assumed `sudo` was
  available. The macOS `.pkg`'s postinstall script ran `brew install
  mpv` as root, which Homebrew refuses — the failure was silently
  swallowed, so mpv was never actually installed that way; it now runs
  as the logged-in console user, and the `.pkg` ships a published
  checksum.
- **`junto update`/`junto uninstall` could misidentify a Homebrew
  install** — installation-method detection only recognized Homebrew's
  Formula install path (`Cellar`), not its Cask install path
  (`Caskroom`); a Homebrew-cask install (see the `brew install --cask`
  change above) would have been treated as a standalone binary,
  attempting to self-replace or delete a file Homebrew manages instead
  of deferring to `brew`. Both paths are now recognized.
- **Stuck drift-correction nudge** — a rate-nudge applied to smooth over
  small drift was only cleared by a later winning remote state, but the
  largest-pubkey peer wins every tie and so never received one after
  correcting once: it would play up to 3% fast indefinitely, dragging the
  rest of the room into a permanent chase. Heartbeats now carry a computed
  deadline that force-clears an unexpired nudge as a backstop.
- **A transient mpv hiccup could hard-seek the whole room to 0:00** — when
  reading the current playback position failed right after a pause, seek, or
  track switch, the fabricated fallback position (0) was broadcast anyway
  with a fresh winning version, so every other peer snapped to the start of
  the file. That broadcast is now skipped on a read failure; the action
  already applied locally in mpv, and the next successful action re-syncs
  the room.
- **The adaptive download window never actually tracked playback** — the
  local streaming server recorded the playhead once when an mpv request
  started, not as bytes were served, but mpv opens one request and reads it
  for minutes. The tracked position was pinned at the request's starting
  offset for the whole stream, so the bandwidth-adaptive window guaranteed a
  stall once playback outran it — once per file, and again after every
  seek. The server now updates the tracked position continuously as it
  writes the response body.
- **A seek redirect could be silently lost mid-transfer** — the downloader
  discarded a pending redirect signal at the start of each range fetch,
  which is *after* it had already decided what to fetch next; a redirect
  landing in between was thrown away as if it were a stale leftover, so
  mpv stayed blocked until the (now wrong) in-flight range finished on its
  own instead of jumping to the new position right away. The discard now
  happens before that decision is made, not after.
- **A clock-skewed or malicious peer could freeze the room's controls** —
  action ordering is last-writer-wins by timestamp, and a peer with a fast
  or misconfigured clock could broadcast a timestamp far enough ahead that
  no one else's real-time action could ever outrank it, silently reverting
  everyone else's pauses/seeks/etc. for as long as the skew lasted (and a
  deliberately-sent max-value timestamp would never expire). Actions now
  ratchet strictly past any timestamp this peer has already adopted rather
  than trusting the wall clock outright, and incoming timestamps far
  enough outside any plausible clock skew are now rejected outright.
- **Telemetry never actually arrived, and a config typo could silently
  drop the opt-out** — the anonymous usage ping was posted from a
  goroutine spawned right before the process exits, so it was almost
  always killed before the request even left; telemetry has been dead in
  production. Fire now blocks (bounded by its existing 3 s timeout)
  instead of racing exit. Two config-parsing bugs compounded the privacy
  risk: a malformed config file was only a warning, so a typo elsewhere
  in the file could silently discard an explicit `telemetry = false`,
  and only the exact string `false` disabled it — `off`, `0`, or `False`
  silently left it enabled. A malformed config file is now a hard error
  for the commands that read it (`create`/`join`/`doctor`; `version`,
  `update`, etc. are unaffected), and only `true`/`false` are accepted
  for `telemetry`.
- **The pre-buffer gate could leak a premature "ready"** — a streaming
  joiner's readiness was correctly held back internally until its buffer
  cushion was met, but the room-wide "I'm ready" announcement fired every
  heartbeat regardless, so the host could see a joiner as ready and start
  the room before that cushion existed — the exact first-frame stall the
  gate shipped to prevent. The announcement now only goes out once the
  joiner is actually ready.
- **`junto update` could silently install an unverified binary** —
  checksum verification only ran when the release's `checksums.txt` was
  present, fetchable, and listed the asset; any failure of those three
  conditions (missing file, a network hiccup, a name mismatch) fell
  through to installing the download anyway with no warning, reopening
  the exact tampered/corrupt-download risk the checksum check exists to
  close. All three cases are now hard errors instead of a silent skip.
- **A playlist jump could hang the whole session with no error** —
  files download strictly in order, and once a jump moved playback to a
  later file, the still-downloading earlier file's playhead-following
  window froze (nothing reads it anymore) and reported "nothing to fetch
  yet" forever, so the session never reached the file the room actually
  jumped to. The downloader now rushes a stale file to completion as soon
  as a different file is prioritized, instead of waiting on a window that
  will never move again.
- **A captured relay message could be replayed** — the receive path never
  checked how old an incoming event was, and the underlying relay
  library's own duplicate-event cache only catches an exact replay for
  about a minute; after that, anyone who observed a relay (the room tag
  isn't encrypted) could re-publish a captured kick, WebRTC signal, or
  hello verbatim and have it accepted as new. Events older than a
  generous freshness window, implausibly future-dated, or older than the
  newest event already accepted from that sender are now rejected.
- **A dropped host could strand a joiner permanently** — when the
  original host disappeared and another peer promoted itself, that
  promotion was announced exactly once with nothing to confirm delivery;
  a joiner that missed it (a lost relay send, or simply never having
  exchanged hellos with the promoted peer before) kept waiting on the
  dead host forever and could never elect itself either, since it already
  saw the rightful new host as active. The promotion announcement now
  repeats for a few heartbeats, and is honored from a peer we've never
  seen before as long as the old host is already confirmed gone.
- **Six sync-engine hardening fixes from the July 2026 audit**:
  - A kicked peer could retaliate by kicking every other member in turn —
    chat and state from an ignored peer were already dropped, but a kick
    wasn't.
  - A sustained relay outage could freeze the whole engine loop (mpv
    events, drift correction, and user input all stalling for up to 10s
    per message) — queuing an outbound message never blocks now; the
    oldest queued one is dropped to make room instead.
  - A peer re-added after a brief network blip lost its measured clock
    offset and host-eligibility flag for good, which could cause a hard
    seek every heartbeat indefinitely or a split-brain host election —
    rediscovering a peer now redoes the clock-sync exchange, and host
    eligibility rides every heartbeat instead of only the initial hello.
  - A property (speed, subtitle/audio track, seek) that failed to apply
    still let the room's version advance, permanently diverging that
    property for the peer that wins every tie-break — the version is now
    only committed once every property actually applied.
  - `/peers` could show phantom drift for a peer with ordinary clock
    skew, since the sync engine's own clock-offset correction wasn't
    applied to that display.
  - A closed (not just unused) buffer-progress channel would have spun
    the engine loop at 100% CPU forever; it's now set to nil so the
    loop stops selecting it.
- **Five data-plane hardening fixes from the July 2026 audit**
  (`internal/transfer`):
  - A signal left queued from a dead connection attempt (one that gave
    up before consuming it) could be mistaken for a fresh attempt's
    offer, silently desyncing the handshake until it burned the full
    30s connect timeout — a fresh connection attempt now discards
    anything still queued before waiting on a new signal.
  - If the host's on-disk file was shorter than the size it announced,
    it would answer a valid-offset request with an immediate "end of
    file" and no data, and the downloader treated that as progress —
    looping forever, re-requesting the same never-satisfiable range.
    That's now a clear error instead.
  - A crash right after a resume checkpoint could leave the resume
    sidecar claiming bytes were safely on disk when the OS hadn't
    actually flushed them yet; the in-progress file is now synced
    before each checkpoint is written.
  - Built-in TURN relay credentials were fetched once per process and
    cached forever: a slow network at startup could permanently
    disable the fallback relay for the whole session, and credentials
    that outlived their server-declared lifetime would be reused past
    expiry and rejected. Credentials are now fetched (or refetched) on
    demand when needed and past their lifetime, not just once.
  - A brief, self-healing WebRTC connectivity flap (common in
    practice) was treated exactly like a real connection failure,
    tearing down and fully re-negotiating the connection over the
    relays. It now gets a short grace period to recover on its own
    first; only a connection that's genuinely failed or closed is
    immediate.
- **Four more data-plane hardening fixes from the July 2026 audit**
  (`internal/transfer`, `internal/streamserver`):
  - A handler could briefly see a file as still downloading right as it
    finished, try to open the now-renamed-away in-progress path, and
    fail — silently truncating playback at exactly the moment a file
    completed. The file's completion state and its on-disk path now
    change together, and the streaming server no longer reopens the
    file on every read.
  - Migrating to a new host while a connection attempt was already
    reconnecting to the old one used to wait out the full 30-second
    connection timeout before noticing — it now aborts that attempt
    immediately.
  - Seeking near the start of a large file could trigger downloading
    the entire rest of the file in one uninterruptible burst instead of
    a bounded amount ahead of the seek.
  - Four smaller ones: a hostile file size could defeat a safety check
    in the mp4 index parser; a malformed or adversarial transfer could
    corrupt a file's downloaded-byte bookkeeping; two files in the same
    room with the same name could overwrite each other's downloads; and
    closing the local streaming server could leave background goroutines
    running indefinitely if they were waiting on data that would never
    arrive.
- **Seven control-plane hardening fixes from the July 2026 audit**
  (`internal/nostrx`, `internal/protocol`, `internal/mpv`):
  - A control message (kick, hello, a WebRTC signal, a file request)
    could durably land on only one relay and never reach a peer
    subscribed elsewhere — sending now waits for a second relay to
    confirm, up to a short grace period, instead of stopping at the
    first.
  - A burst of mpv property changes (e.g. every property resetting at
    once when a file loads) could silently drop an update once an
    internal buffer filled, desyncing that property for the room.
    Updates to the same property now collapse to the latest value
    instead of ever being dropped.
  - Room messages had no bound on a WebRTC signal's size, a playback
    position, or an announced file size, so a corrupted or hostile value
    in any of them could reach downstream parsing unchecked. All three
    are now rejected at the point a message is decoded.
  - Receiving room messages had no rate limit and didn't count failed
    decryptions, leaving an attack that floods the room's public tag
    with garbage invisible and uncapped.
  - A stalled relay subscription used to look exactly like a quiet
    room. The app now notices when nothing has been received from any
    relay for an unusual stretch and lets you know, recovering silently
    once it resumes.
  - An oversized line from mpv's IPC socket used to be mistaken for mpv
    crashing, ending the whole session; it's now skipped and the
    connection keeps going.
  - The security model documentation now states plainly that a message
    addressed to one person is still readable by every room member —
    addressing is routing, not privacy.
- **Nine app-shell hardening fixes from the July 2026 audit** (`cmd/junto`,
  `internal/app`, `internal/config`, `internal/ytdlp`, `internal/doctor`,
  `internal/debug`, `internal/selfupdate`):
  - Ctrl-C during a `junto create <url>` download could leave ffmpeg
    running in the background, still writing to the cache and blocking
    junto from exiting cleanly; cancelling now stops the whole download,
    not just its immediate process. Cleanup of a cancelled download's
    leftover files no longer touches other downloads happening at the
    same time.
  - Two files in the same room whose names shared a prefix (e.g. a movie
    and an "extended cut" of it) could cause a subtitle file meant for
    one to attach to both, aborting `create` with a confusing duplicate
    file error.
  - `junto create <url>` now checks that mpv is installed before
    spending time downloading the video — previously a missing mpv was
    only discovered after a potentially large download finished.
  - The config file silently produced the wrong value for a line with a
    trailing comment or an escaped quote, and a key listed twice
    silently kept only the last one; all three are now reported as
    errors instead of guessed at.
  - Pressing Ctrl-C while waiting to find the host used to print a raw
    technical error and count as a failure in anonymous usage stats;
    it's now treated as the ordinary, no-error exit it is.
  - Fetching a video's info from a slow or unresponsive site used to
    hang `create` indefinitely with no feedback; it now gives up after
    30 seconds with a clear message.
  - If a joiner's background download failed outright partway through a
    session, that joiner could get stuck showing as "not ready" for the
    rest of the watch party; it now clears correctly either way.
  - `junto doctor` used to show nothing at all until every check
    finished, which for a slow network could take the better part of a
    minute; results now appear one by one as each check completes.
  - Small ones: the `-debug` log could contain lines that weren't valid
    JSON, and two junto processes started in the same second could
    overwrite each other's debug log; `junto update` now flushes the
    downloaded binary to disk before installing it, closing a rare
    window where a crash mid-update could leave junto broken; and a
    long download status line could occasionally show a garbled
    character at the cutoff point.
- **Three CI-only test flakes surfaced by GitHub's macOS runner migrating
  to macOS 26** — a test that spawned a background file-serving goroutine
  with an unbounded context could still be running when the next test
  mutated a shared timing variable, tripping the race detector; a
  burst-delivery test slept a fixed guess at how long event coalescing
  needed to catch up instead of waiting for the actual result; and an
  orphaned-child-process test raced its own assertion window against a
  production backstop timer of the exact same duration, leaving no
  margin on a loaded machine. All three were pre-existing test hygiene
  issues, not regressions in the code under test.

## [1.2.0] - 2026-06-16

### Security

- **Sanitized all peer-supplied display text** — nicknames, chat messages,
  and host-announced file names are stripped of control characters at the
  decode boundary, so a malicious peer can no longer inject terminal escape
  sequences (clearing your screen, moving the cursor) or forge extra output
  lines via your terminal or the mpv OSD. These fields are also length-capped.
- **Fixed a data race on the built-in relay list** — it was written by the
  background credential fetch and read by the downloader with no
  synchronization; access now goes through a mutex.
- **Bounded untrusted input** — a host can announce at most 1024 playlist
  entries (preventing unbounded allocation), and ICE candidate gathering now
  has a timeout so a stalled STUN server can't hang a transfer indefinitely.
- **Debug logs no longer record file names or absolute paths** — the
  `-debug` log is meant to be shareable, so it now logs playlist indices and
  sizes instead of names, and never the room secret or relay credentials.
- **Hardened the streaming server and yt-dlp invocation** — the local HTTP
  server (already loopback-only) gained a header-read timeout, and URL
  arguments to yt-dlp are passed after `--` so a URL can't be smuggled in as
  a flag. CI now runs `govulncheck`.

### Added

- **Graceful host migration** — when the host quits or drops offline
  mid-session, the room keeps going. A joiner who has the files locally is
  elected the new host (the largest-pubkey eligible peer, decided the same
  way by everyone with no coordination round), re-announces the playlist so
  late joiners still connect, and streaming joiners automatically redirect
  their in-progress download to the new host. Every byte stays SHA-256
  verified across the handover.
- **Stale host detection** — if the host closes mpv or loses its
  connection, you no longer stare at a frozen window: junto prints "host
  appears to have left — waiting…" after one missed heartbeat cycle and
  escalates to "host has been gone for X minutes — use /quit to exit" after
  a longer gap, in the terminal and the mpv OSD. The notice clears itself if
  a replacement host takes over.
- **Smart pre-buffer gate** — a streaming joiner now waits until a real
  cushion of video is buffered before it reports "ready", so when the host
  presses play everyone actually plays instead of one person stalling on the
  first frame.
- **File-match check** — joining with your own local copy now warns you up
  front if it doesn't match the room (different file count, a name that
  doesn't line up with the room's playlist, or a different size — "likely a
  different cut"), heading off a confusing silent desync. Name and size are
  checked instantly; no full re-hash on join.
- **One-command install** — `curl junto.watch/install | sh` detects your
  platform, downloads the verified binary (SHA-256 against the release
  checksums), and installs mpv via your package manager. On macOS it uses
  the Homebrew tap when present. A double-click macOS `.pkg` installer is
  now produced on each release too. No more "install Homebrew first, then
  the tarball, then mpv separately."
- **Audio-track sync** — the active audio track is now mirrored across the
  room, just like the subtitle track. On multi-audio files (anime dubs,
  multi-language releases) one person switching to the English dub no
  longer leaves everyone else silently on the original.
- **Built-in TURN relay fallback** — when direct hole-punching fails,
  junto now fetches short-lived credentials for a built-in TURN relay and
  retries through it automatically, with no flags — so symmetric-NAT pairs
  recover transparently instead of failing the transfer. `-turn` still
  works as an override. Falls back to direct-only if the relay is
  unreachable. `junto doctor` shows the built-in relay's status.
- **Opt-in telemetry** — a single anonymous ping on `create`/`join`
  reports junto version, OS/arch, outcome, relay latency, and (for
  streaming joiners) time-to-first-frame and transfer success — no PII, no
  room secret, no file names. First run prints exactly what's collected;
  opt out with `--no-telemetry` or `telemetry = false` in config.toml. It's
  a fire-and-forget call with a 3 s timeout that can never slow you down.
- **`--debug` flag** — writes a timestamped, structured (JSON) log of relay
  health, ICE negotiation, downloads, and sync decisions to
  `~/.cache/junto/debug-<timestamp>.log`. Off by default, so normal runs
  stay silent; invaluable when something misbehaves.

### Changed

- **Smooth drift correction** — when a peer drifts slightly out of sync
  (roughly 0.05–0.4 s), junto now eases it back by nudging mpv's playback
  rate up to 3 % for a few seconds instead of performing a visible hard
  seek; hard seeks are kept for larger gaps. The nudge is local-only — it's
  never broadcast — so each peer corrects its own drift without dragging the
  room's speed around. Routine corrections are now invisible.
- **Guided failure & preflight** — junto now checks that mpv is installed
  *before* connecting to relays, so a missing player fails instantly with
  install instructions instead of after a ~10 s wait. Connection, relay,
  and NAT errors now suggest running `junto doctor`, and the old "NAT
  traversal failed" jargon is replaced with plain English ("your network or
  the host's may be blocking peer-to-peer connections"). `junto doctor`
  always ends with a one-line verdict ("safe to stream" / "not ready to
  stream") and lists a `telemetry` status row.
- **In-player notices (mpv OSD)** — chat, joins and leaves, buffering,
  pause/resume/seek, kicks, relay reconnects, and the "press Enter to
  start" prompt now appear as OSD overlays inside the mpv window, mirrored
  alongside the terminal output, so notices land where you're actually
  looking. The start-prompt stays pinned while the room waits, and OSD
  writes are dispatched off the sync loop so a stalled mpv IPC can't freeze
  playback.

- **Buffer-free playback** — the room now pauses only when mpv truly runs
  out of buffered data (`paused-for-cache`), not on speculative read-ahead,
  so a generous mpv jitter buffer (read-ahead raised to 120 s / 128 MiB)
  and an instant start finally coexist — fixing the "minutes cached but
  still frozen" stall. The background download follows the playhead in a
  bandwidth-adaptive window sized to measured throughput (32 MB–512 MB)
  instead of always filling the file front-to-back; index/tail prefetch and
  seeks bypass the window so startup and seeking stay instant.
- **Sharper sync via clock-offset estimation** — peers now measure their
  clock skew NTP-style during the hello handshake and compensate every
  projected position, so a peer whose system clock is off no longer reads
  as drift. With skew removed the hard-seek drift threshold tightened from
  1.0 s to 0.5 s, while the seek-suppression tolerance stays at 1.0 s so a
  keyframe-rounded landing isn't re-broadcast as a fake user seek. Jittery
  (implausible round-trip) measurements are discarded.
- **Relay health scoring** — sends are routed to reachable relays
  fastest-first by measured publish latency, and a relay that fails
  repeatedly is benched for a cooldown (falling back to the full set if all
  are down), so play/pause/seek take the snappiest path. `junto doctor` now
  shows each relay's round-trip latency.

## [1.1.0] - 2026-06-14

> **Upgrade note:** v1.1.0 reworked the peer-to-peer transfer, so everyone in a
> room must be on the **same version** — a 1.1.0 peer can't transfer files with a
> 1.0.0 peer. Update together (`junto update`, or `brew upgrade`).

### Added

- **Config file** — `~/.config/junto/config.toml` (or `$XDG_CONFIG_HOME`)
  persists `nick`, `relays`, `mpv-path`, and `turn`/`turn-user`/`turn-pass`
  so they don't need retyping; explicit flags still override it.
- **Subtitle file sharing** — the host automatically offers sidecar
  `.srt`/`.ass`/`.ssa`/`.vtt` files that match a video's name; streaming
  joiners download them first and load them into mpv for the right
  playlist item, so subtitle track and delay sync work without anyone
  sourcing subtitles separately.
- **Cross-session resumable downloads** — quitting mid-download now keeps
  the `.part` and a small sidecar recording which byte ranges arrived, so
  rejoining resumes instead of starting over. A stale sidecar (the file
  changed under the same name) is discarded automatically.
- **`/ignore <nick>`** — locally suppress a peer's chat and stop their
  playback commands and buffering from affecting you. `/unignore` undoes it.
- **`/kick <nick>`** — ask a peer to leave; everyone else auto-ignores
  them. Cooperative (junto has no authority over a public relay), so a
  misbehaving client can decline — documented as such.
- **`/sync`** — force an immediate re-adoption of the room's current
  state when something feels off, instead of quitting and rejoining.
- **Richer `/peers`** — shows each peer's position, drift from your
  playback, download progress, and buffering/ignored state.
- **yt-dlp URL sources** — a `junto create` argument that's an http(s)
  URL is downloaded via yt-dlp and hosted like a local file, so you can
  watch a YouTube (or any yt-dlp-supported) video in sync. Capped at
  1080p by default (`-quality`), cached in `~/.cache/junto` (keyed by
  video id, so re-hosting is instant), live URLs rejected up front.
  `junto doctor` now also checks for yt-dlp and ffmpeg.
- **`junto update`** — updates to the latest GitHub release: a standalone
  binary downloads and swaps itself (checksum-verified); Homebrew and
  `go install` builds are pointed at the right upgrade command.
- **`junto uninstall`** — removes the junto binary (Homebrew installs are
  deferred to `brew uninstall`), optionally with config and cache
  (`-purge`).

### Changed

- **Much faster streaming** — a file now transfers over a single
  persistent WebRTC connection, with byte ranges negotiated on the data
  channel. The previous model rebuilt the connection (ICE + SDP over the
  relays) every 4 MiB, so handshake latency throttled throughput below HD
  bitrates and caused frequent stalls. A seek now redirects the live
  stream mid-flight instead of reconnecting, and the backpressure window
  grew from 1 MiB to 8 MiB so fast links aren't capped by round-trip
  latency. Resume, seek-ahead buffering, and the TURN fallback are
  unchanged.
- **Precise mp4 index prefetch** — for mp4/mov, junto walks the box
  structure to fetch the `moov` atom exactly, wherever it sits and
  whatever its size, instead of guessing a fixed 16 MiB tail (which
  missed the larger `moov` of long/4K files and stalled startup). Other
  containers keep the tail prefetch.
- **Tuned mpv read-ahead for streaming** — streaming joiners run mpv with
  a modest demuxer read-ahead (10s) on top of the bytes already
  downloaded ahead of the playhead. The real jitter cushion is the
  on-disk download lead, so a big mpv read-ahead only made it pull bytes
  past the download frontier and freeze the room at startup; a small one
  starts fast and still plays smoothly.

## [1.0.0] - 2026-06-12

The first release: watch media in sync with friends — no server, no
account — installable with one command on macOS and Linux.

### Synchronized playback

- **Leaderless sync** — play, pause, seek, playback speed, subtitle
  track and delay, and playlist position are mirrored to everyone in
  the room within about a second, driven straight from the mpv window.
  Last-writer-wins with echo suppression and one-way drift correction,
  so there are no seek wars and no single point of failure.
- **Readiness gate** — the host holds at a start screen showing who's
  loaded and ready ("2/3 ready") and starts playback for the whole room
  with one keypress. Late joiners skip the gate and land at the room's
  current position, speed, and subtitle state in one round-trip.
- **Seek-ahead buffering** — seeking past what a streaming joiner has
  downloaded steers their download to fetch that region first and
  pauses the whole room with a "buffering…" notice until everyone has
  caught up, then resumes together.

### File transfer & streaming

- **P2P file transfer** — the host's files travel directly to joiners
  over WebRTC data channels with backpressure and SHA-256 verification;
  no third party ever holds the media.
- **Streaming playback** — joiners start watching within seconds: a
  local HTTP range server feeds mpv while the download continues in the
  background, across the whole playlist. The last 16 MiB of each file
  is fetched first so formats with end-of-file indexes (non-faststart
  mp4, end-Cues mkv) also start immediately.
- **Resumable transfers** — a dropped connection resumes from the last
  received byte instead of restarting.
- **TURN relay support** — `-turn`/`-turn-user`/`-turn-pass` route
  transfers through a relay when direct hole-punching fails; failure
  messages say exactly what to do. (The auto-fallback plumbing is wired
  for a future built-in relay.)
- **Live progress bar** — percent, bytes, smoothed speed, and ETA,
  pinned to the bottom row so chat scrolls above it.

### Rooms & transport

- **Room codes** — a room is a random 128-bit secret encoded as a
  shareable `jun1…` code; `junto create` puts the ready-to-paste
  `junto join` command on your clipboard.
- **Encrypted coordination over public nostr relays** — NIP-44-encrypted
  ephemeral events, a throwaway keypair per session, and an unlinkable
  room tag; relays see only ciphertext. Dropped relays are re-dialed
  silently, with a brief "reconnecting…" notice at worst.
- **Protocol versioning** — every message carries a wire version so
  future releases can detect mismatched peers instead of silently
  misbehaving.

### Interface

- **Line-based CLI** — `create` / `join`, in-session chat with
  per-peer colors, `/pause`, `/play`, `/seek`, `/speed`, `/peers`,
  `/quit`. Flags work anywhere on the command line. Plain-English
  errors throughout ("nobody answered on that room code — check the
  host is running and the code is exactly right").
- **`junto doctor`** — preflight checks for everything a watch party
  needs: mpv, relay reachability, NAT traversal (STUN), the TURN relay
  if configured, and disk space — with a fix printed for whatever fails.
- **`junto version`** — prints the version, commit, and build date.
- **Binary releases** — macOS (universal) and Linux (amd64/arm64)
  binaries on every release; the Homebrew formula installs mpv as a
  dependency, so `brew install` is genuinely one command.

[Unreleased]: https://github.com/swayam-mishra/junto/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/swayam-mishra/junto/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/swayam-mishra/junto/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/swayam-mishra/junto/releases/tag/v1.0.0
