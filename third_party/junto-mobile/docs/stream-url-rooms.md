# Stream URL rooms (live P2P relay) — parked design

> **Status: parked.** This is a different product direction from junto's north star
> (a seamless watch party around a *file*). It's preserved here in full so it can be
> picked up later, but it is **not** on the active roadmap — see [ROADMAP.md](../ROADMAP.md).

The host pipes a live encoder feed into junto (`ffmpeg … -f mpegts - | junto create -stream -`) and junto relays that byte stream over its existing WebRTC mesh to every joiner, who watches it live in mpv. No file transfer, no local copy, no seeking. Distinct from **HTTP URL rooms** (a public file everyone fetches independently) and **On-demand streaming** (follow-the-playhead for a finite file).

**Why it's a Larger Project.** junto everywhere assumes a finite, seekable, known-size file (`FileMeta.Size`/`SHA256`, `streamserver` byte-ranges + `Content-Length`, the syncer's absolute `Position` / drift-seek, playlist-pos EOF advance, SHA verify). A live stream has none of these, so it needs a parallel "live" path rather than changes to the file path.

**Recommended architecture**
- **Input:** the host provides a local source as an `io.Reader` — primarily **stdin** (`-stream -`); a loopback HTTP URL (`-stream http://127.0.0.1:…`) is an easy follow-on. junto never launches the encoder; it's encoder-agnostic.
- **Container = MPEG-TS (mandatory for v1).** Mid-stream join only works in a self-resyncing container: TS has a `0x47` sync byte every 188 bytes and repeating PAT/PMT, so a late joiner starting at the live edge resyncs within a fraction of a second. Plain mp4 (single `moov`) cannot be joined mid-stream; Matroska/WebM-over-pipe could, but only by replaying a cached init segment to each new joiner — deferred. `StreamMeta.Container` is the late-join contract.
- **Transport = P2P fan-out.** One host goroutine reads the source into fixed chunks and fans them out to every connected joiner; late joiners start at the current live edge. Reuse the WebRTC connect/offer-answer/NAT-fallback plumbing from `transfer` (`ICEConfig`, `watchConnection`, `waitSignal`, the non-trickle gather pattern) but **not** the range/`start`/`at`/`eof` protocol — live is continuous push: binary chunks, plus one `{"t":"end"}` text frame at source EOF.
- **Backpressure = per-joiner ring + drop-to-live.** Never block the single source reader on a slow peer (that would stall everyone / back-pressure ffmpeg). Each joiner has a bounded ring; when it overflows, drop oldest and skip that peer to the live edge (safe in TS, which resyncs).
- **Joiner playback:** a live HTTP server (loopback, unbounded **chunked** response, no `Content-Length`, no range — a separate type from the range-based `streamserver.Server`) feeds mpv from a ring fed by the WebRTC receiver.
- **Sync = pause-only, live-edge.** No position sync, no drift-seek, no playlist/EOF advance, no SHA verify. Heartbeats carry only `Paused`. On resume, peers re-sync to the live edge (the ring drops paused-through backlog; keep mpv's demuxer buffer small and flush/seek-to-tail on resume). `/seek` and `/speed` are disabled in a live room.
- **Host watches too:** the host's own mpv reads a local live HTTP server fed by a host-local subscriber of the same broadcaster — so there's exactly one reader of the source and the host is a normal participant. (Resolves the "stdin is one-shot, can't tee to mpv + fan-out" conflict.)

**Components**
- *Protocol* (`internal/protocol/protocol.go`): add `StreamMeta{Title, Container}`, an optional `Message.Stream *StreamMeta`, a `MsgStreamReq` type, bump `Version` to 2, extend `validate`. (Use a dedicated field, **not** a `FileMeta.Size=-1` sentinel — keeps every `len(Files)>0` file-transfer path cleanly false for a live room.)
- *New* `internal/livestream/`: `OpenSource` (stdin / loopback URL, TS sniff) + `Broadcaster`/`Sub` fan-out (pure, unit-testable).
- *New* `internal/transfer/live.go`: `ServeLive` / `ReceiveLive` + a shared `dialServe` handshake helper factored out of `ServeFile`.
- *New* `internal/streamserver/live.go`: `LiveServer` (chunked, no range).
- *Change* `internal/syncer/engine.go` + `Deps`: `Live bool`, `OnStreamReq`, `OnResume`; gate out seek/position/index/EOF; pause-only adoption; bounded buffering-hold in live mode.
- *Change* `internal/mpv/client.go`: `Options.Live` → small-buffer low-latency live flags (`--cache=yes --demuxer-max-bytes=32MiB --demuxer-readahead-secs=5`, keep `--keep-open` / `--force-window`).
- *Change* `internal/app/app.go`: `Config.StreamSource`, `CreateStream`, `joinLiveStream`, `waitForHost` returns the `Stream` meta; reuse `signalRouter`/`transportSignaler` as-is.
- *Change* `cmd/junto/main.go`: `-stream` flag on `create` (mutually exclusive with files / `-no-transfer`).

**Milestones**
- **M1 (minimal usable):** stdin TS source, fan-out to joiners, live HTTP server, pause-only sync, host-watches-via-same-feed, direct connection. End-to-end: `ffmpeg … -f mpegts - | junto create -stream -` → friends `junto join <code>` → synced play/pause on a live feed.
- **M2 (robust):** loopback-HTTP-URL input; reconnect-on-drop (re-subscribe at live edge); NAT/TURN fallback wired through; bounded buffering-hold so one slow peer can't freeze the room; explicit `OnResume` cache-flush + seek-to-tail; clean `MsgStreamEnd` / "stream ended" UX; `/peers` shows per-peer "behind live / live" instead of download %.
- **M3 (later):** Matroska/WebM-over-pipe with cached init-segment replay; multi-stream/playlist; speed sync (likely never).
- **Deferred out of v1:** seeking, position/drift sync, EOF/playlist advance, SHA verify, disk buffering/resume, mp4 source, non-loopback source URLs.

**Open decisions** (recommended defaults in bold)
- Input form: **stdin pipe (`-stream -`)** vs loopback HTTP URL vs junto-launches-ffmpeg.
- Scope of first pass: **M1 minimal** vs full robust (M1+M2).

**Hardest problems → handling**
- Slow joiner: per-Sub ring drop-oldest + skip-to-live (don't block the source).
- Mid-stream join: mandate MPEG-TS; the broadcaster passes raw bytes and mpv resyncs.
- Pause/resume: ring drops backlog during pause; small mpv buffer + flush/seek-to-tail on resume.
- Source ends: broadcaster closes → `Sub.Next` drains then `io.EOF` → `ServeLive` sends `{"t":"end"}` → joiner closes its live buffer → mpv EOF; host broadcasts a "stream ended" so the room ends cleanly.

**Verification**
- Unit: `livestream` broadcaster (two-from-start identical; late sub starts near live edge; slow sub drops-to-live without blocking; EOF propagates). Syncer live-gate test (remote `Position`/index → no `SeekAbsolute`/`SetPlaylistPos`; `Paused` still adopted; `/seek` rejected). Protocol round-trip with `Stream` set / `Files` nil, `Version==2`.
- E2E loopback (`transfer/live_test.go`, modeled on `TestFileTransferLoopback` with `newSignalerPair` + `noICE()`): broadcaster over an in-memory reader → `ServeLive` → `ReceiveLive` into a buffer; assert received bytes are a contiguous suffix from the join point + clean EOF.
- Manual smoke: `ffmpeg -re -f lavfi -i testsrc2=size=640x360:rate=30 -f lavfi -i sine=frequency=440 -c:v libx264 -preset ultrafast -tune zerolatency -c:a aac -f mpegts - | junto create -stream -`, then `junto join <code>`; verify live playback, synced `/pause` `/play` (resume at live edge), clean teardown when the host's ffmpeg stops.
