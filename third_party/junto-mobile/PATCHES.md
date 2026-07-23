# ECHO patch inventory

The mobile integration intentionally keeps the junto v1 wire protocol intact.

1. `mobile/mobile.go`, `mobile/player.go`
   - Adds the gomobile facade.
   - Replaces mpv/TUI/stdin with a primitive JSON callback and Media3-facing player adapter.
   - Reuses upstream `room`, `protocol`, `nostrx`, `syncer`, `transfer`, `streamserver`, WebRTC and
     Bao verification packages.
   - Wires upstream swarm coverage, verified peer serving and host-migration hooks for streaming
     Android joiners, exposes room-file preview/relay testing, and rejects video/subtitle rooms
     before transfer.
   - Does not initialize telemetry, self-update, yt-dlp or the desktop TUI.
2. `internal/transfer/download.go`
   - Adds the read-only `FileStore.Done(index)` query so Android exposes save only after verification
     and the atomic final rename.
   - Adds `FileStore.ImportVerified(index, path)` for explicit Android local matches; it trusts the
     file only after the complete announced SHA-256 and Bao metadata match.
3. `go.mod`, `go.sum`, `tools/build_junto_mobile.ps1`
   - Pins `gobind` as a Go tool dependency, as required by the selected Go/x-mobile versions.
   - Passes anet's documented `-checklinkname=0` linker compatibility flag for Android.
   - Fails immediately when any native tool step fails, then checks both ABI entries and records the
     generated AAR SHA-256.

No protocol field, encryption rule, room-code rule or chunk verification algorithm is modified.
