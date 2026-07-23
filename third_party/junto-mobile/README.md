# junto

Watch media in sync with friends — no server, no account. Playback
coordination travels encrypted over free public [nostr](https://nostr.com)
relays; the media files themselves can be sent directly peer-to-peer over
a WebRTC data channel. Playback happens in [mpv](https://mpv.io), which
this program launches and remote-controls.

Synced across the room: play/pause, seeking, playback speed, audio track,
subtitle track and delay, and playlist position (queue several files; when
one ends, everyone advances together).

## Install

**macOS or Linux — one command:**

```sh
curl -fsSL https://junto.watch/install | sh
```

Detects your platform, downloads the binary, and installs mpv. If Homebrew
is already installed on macOS, it uses `brew install --cask swayam-mishra/tap/junto`
(which also keeps junto updated via `brew upgrade`).

**Other ways to install:**

| Method | Command |
|---|---|
| macOS Homebrew | `brew install --cask swayam-mishra/tap/junto` |
| macOS installer | Download `junto_*.pkg` from the [releases page](https://github.com/swayam-mishra/junto/releases/latest) |
| Linux manual | `tar xzf junto_*_linux_*.tar.gz && sudo install junto /usr/local/bin/` |
| From source | `go install github.com/swayam-mishra/junto/cmd/junto@latest` |

All methods except Homebrew require mpv to be installed separately
(`brew install mpv` on macOS; `apt/dnf/pacman install mpv` on Linux) — the
macOS `.pkg` installer will do this for you automatically if Homebrew is
already present, and tells you how to otherwise.

After installing, run `junto doctor` to verify everything is working.

junto's releases aren't Apple-notarized yet, so macOS Gatekeeper may
refuse to run the binary the first time ("Apple could not verify 'junto'
is free of malware"). The `curl | sh` installer and the Homebrew cask
both clear this automatically; if you hit it via the `.pkg` or a manual
download, clear it yourself:

```sh
xattr -d com.apple.quarantine $(which junto)
```

Windows isn't supported yet ([roadmap](ROADMAP.md)).

## Use

**Host** a room around a file (or several — they become a shared playlist):

```sh
junto create movie.mkv
junto create episode1.mkv episode2.mkv episode3.mkv
# room code: jun1nve3pbfqpfj5qtwkayld3365ui
# (the join command is copied to your clipboard)
```

A `create` argument that's an http(s) URL is fetched with
[yt-dlp](https://github.com/yt-dlp/yt-dlp) and hosted like a local file —
so you can watch a YouTube (or any yt-dlp-supported) video in sync:

```sh
junto create "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
junto create intro.mkv "https://youtu.be/aqz-KE-bpKQ"   # mixed playlist
```

The download is capped at 1080p by default (`-quality 720p|480p|best|audio`
to change) and cached in `~/.cache/junto`, so re-hosting the same URL is
instant. URL sources need `yt-dlp` installed (and `ffmpeg` to merge HD
video+audio); `junto doctor` checks for both.

Share the room code — it's already on your clipboard as a ready-to-paste
`junto join …` command. **Friends** join with it:

```sh
# stream the files directly from the host (P2P) — playback starts
# right away while they download in the background:
junto join jun1nve3pbf... 

# or, if you already have the files, skip the transfer:
junto join jun1nve3pbf... episode1.mkv episode2.mkv episode3.mkv
```

mpv opens paused for everyone. The host holds at a start screen that
shows who's loaded and ready (`2/3 ready`); pressing **Enter** starts
playback for the whole room at once, so nobody has to ask "are you at
the beginning?". Joiners just wait for the host to start. (Late joiners
who arrive after playback has begun skip the gate and land at the
room's current position immediately.)

From then on, anything anyone does in their mpv window — pause, play,
seek, change speed (`[`/`]`), switch subtitle track (`j`), adjust
subtitle delay (`z`/`x`), skip playlist items (`<`/`>`) — happens for
everyone, within about a second.

Subtitle files (`.srt`/`.ass`) sitting next to a shared video are sent
along automatically and loaded into mpv, so subtitle track and delay
sync work without anyone sourcing subtitles separately. If a streaming
joiner quits mid-download, rejoining resumes where it left off.

If one peer's download can't keep up, the room pauses and waits for
them — but only for up to 20 seconds; after that it continues without
them automatically, and they resync once they catch up. The host can
also press **Enter** during playback to skip that wait immediately.

On a real terminal, junto draws an interactive interface — a banner, a
room panel (code, join link, playlist), a live party panel (each peer's
position, drift, download progress, and a ready/buffering/behind badge),
a chat log, and an input line. Piped or redirected output (scripts, CI)
stays plain; `JUNTO_NO_TUI=1` forces plain output and `NO_COLOR` strips
colors while keeping the layout.

Lines typed into the terminal are chat (each person gets a consistent
color); there are also `/pause`, `/play`, `/seek <mm:ss>`,
`/speed <rate>`, `/peers` (positions, drift, download progress),
`/sync` (force a resync), `/ignore <nick>`, `/kick <nick>` and `/quit`.

If anything misbehaves, `junto doctor` checks mpv, relay reachability,
NAT traversal, your TURN relay (built-in and any you supply), disk space,
yt-dlp/ffmpeg, and your telemetry setting — and prints the fix for
whatever fails. For a deeper trace, add `-debug` to `create`/`join` to
write a structured JSON log to `~/.cache/junto/debug-<timestamp>.log`
(off by default).

`junto update` upgrades to the latest release (a standalone binary
updates itself; a Homebrew or `go install` build is pointed at the right
command). `junto uninstall` removes junto, optionally with its config
and cache (`-purge`).

Flags (after the subcommand): `-nick name`, `-relays wss://a,wss://b`,
`-mpv-path /path/to/mpv`, `-turn turn:host:3478` `-turn-user` `-turn-pass`
(relay for when direct connection fails), `-out dir` (join: download
directory), `-no-transfer` (create: don't offer the files),
`-quality`/`-yt-dlp-path` (create: for URL sources), `-debug` (write a
structured log file), `-no-telemetry` (disable the anonymous usage ping).

### Telemetry

junto sends a single anonymous ping when you `create` or `join`: the
event type, junto version, OS/arch, outcome, relay latency, and — for
streaming joiners — time-to-first-frame and whether the transfer
succeeded. **No personal data, no room secret, no file names.** The first
run prints exactly this. Opt out any time with `--no-telemetry` or by
adding `telemetry = false` to your config file.

### Config file

To avoid retyping flags, put defaults in `~/.config/junto/config.toml`
(explicit flags still win):

```toml
nick = "alice"
relays = ["wss://relay.damus.io", "wss://nos.lol"]
mpv-path = "/usr/local/bin/mpv"
# turn = "turn:relay.example:3478"
# turn-user = "user"
# turn-pass = "pass"
# telemetry = false   # disable the anonymous usage ping
```

## How it works

No server in the middle: playback coordination (pause/seek/speed/etc.) is
encrypted and relayed over public nostr relays, while every media byte
flows directly peer-to-peer over WebRTC. For the full technical
breakdown — rooms and encryption, sync, file transfer, streaming, host
and relay resilience, and the security model — see
[docs/how-it-works.md](docs/how-it-works.md).

## Limitations

- NAT traversal is direct (STUN) out of the box; symmetric-NAT pairs
  need a TURN relay supplied with `-turn` (there is no reliable free
  public TURN service to bake in). The manual fallback always works:
  get the files any other way and `join <code> <file>...`. Run
  `junto doctor` to see what your network supports.
- Streaming smoothness depends on bandwidth vs. bitrate. junto starts
  playback fast and keeps a small read-ahead, relying on the download
  staying ahead of the playhead. Joiners fetch from every peer that has
  the bytes (other joiners mid-download included), not just the host, so
  the host's upload alone isn't the ceiling — but if the room's combined
  upload toward a peer or that **peer's download** can't keep up with the
  content's **bitrate** — a high-quality remux (Blu-ray, 4K) is tens of
  Mbit/s and spikes higher in busy scenes —
  the room will pause to buffer until it catches up. So the bigger and
  higher-quality the file, and the slower the link, the more buffering you
  trade for that quality. To smooth it out: let the download run ahead
  before starting, use a lower-bitrate copy, or — guaranteed smooth — have
  each watcher bring the file locally (`junto join <code> <file>...`),
  which skips the transfer entirely. For mp4/mov files, junto compares
  your measured download speed to the file's real bitrate once buffering
  starts and warns up front if it looks too slow to keep up, and a host
  serving several streaming viewers is warned if their measured upload
  can't feed all of them at that bitrate — not available yet for other
  containers.
- Streamed bytes are verified in 256 KiB chunks against the host's
  announced hash before they're played or stored, from whichever peer
  they came. (In a room created by an older junto, verification falls
  back to a whole-file check after the download, which only warns.)
- Clock skew is measured once on join and compensated, so participants
  whose clocks are off by a fixed amount sync fine; a clock that drifts
  substantially *during* a long session isn't re-measured and could
  reintroduce apparent drift.
- Free relays may rate-limit; pass your own with `-relays`.
- Everyone should use the same media files (same order for playlists)
  for positions and subtitle tracks to line up.

## Tests

```sh
go test ./...
```

The transfer test runs a real WebRTC connection over loopback, and a
two-peer in-process test drives two full sync engines against each other
to assert playback converges; the rest are pure unit tests (protocol, room
codes, drift math, clock-offset estimation, echo suppression, relay health
scoring).

## Contributing

junto's core is under active daily development, so **code contributions are
paused** while we build a stable foundation — but **bug reports and ideas
are very welcome**. Please [open an issue](https://github.com/swayam-mishra/junto/issues).
See [CONTRIBUTING.md](CONTRIBUTING.md) for what to include and how to report
security issues.
