# junto mobile upstream

- Project: `swayam-mishra/junto`
- License: MIT (`LICENSE`)
- Pinned release: `v1.4.0`
- Pinned commit: `0141c375a6a37d417c3215dc6caf62715ca55b38`
- Go toolchain: `go1.26.4`
- `golang.org/x/mobile`: commit `6129f5b`
- Android API: 23
- Android ABIs: `arm64-v8a`, `x86_64`
- Generated Java package: `app.yukine.junto.mobile`

The vendored tree is the complete v1.4.0 source archive. ECHO-specific changes are listed in
`PATCHES.md`. Protocol message fields, NIP-44 encryption, room-code derivation and validation rules
remain unchanged.

## Upgrade procedure

1. Replace the vendored tree with the complete source archive for the chosen signed release.
2. Restore `mobile/`, reapply only the patches listed in `PATCHES.md`, and update this file.
3. Run `go test ./...` in this directory.
4. Run `tools/build_junto_mobile.ps1`.
5. Verify the AAR SHA-256 and both ABI entries.
6. Complete the desktop/Android interoperability matrix before changing the default feature flag.
