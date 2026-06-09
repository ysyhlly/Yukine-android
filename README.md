# ECHO NEXT Android

Native Android port of ECHO NEXT.

This implementation is a native Android music player built around Kotlin/Compose UI surfaces, Java Android lifecycle orchestration, AndroidX Media3 playback, and SQLite persistence. It implements the Android core that ECHO NEXT needs for the current MVP:

- Local audio discovery through `MediaStore`.
- System file picker import for selected audio files and folders, with persisted URI access.
- Local M3U/M3U8 playlist import for stream URLs, playlist import/merge, and playlist M3U8 export.
- WebDAV remote source setup, sync, and playback through cached remote tracks.
- Local SQLite library cache for tracks, document imports, streams, WebDAV tracks, favorites, play history, playlists, queue state, playback position, and settings.
- Playback through AndroidX Media3 `ExoPlayer`.
- Background playback through AndroidX Media3 `MediaSessionService`.
- Notification, lock-screen, headset, and Bluetooth media controls through Android media sessions.
- Automatic pause when wired or Bluetooth output disconnects.
- Compose-backed library, search, queue, now-playing, network sources, settings, and collection UI.
- Library grouping for songs, albums, artists, and folders.
- SQLite-backed favorites, recent plays, most-played records, playlists, and playlist membership.
- Playlist create, rename, delete, track reorder, remove-track, selected playlist playback, add-to-playlist, selected/current playlist M3U/M3U8 import/merge, and current playlist M3U8 export through the system file creator.
- Persistent queue, shuffle, repeat off/all/one, playback speed, app volume, and sleep timer.
- Stream URL normalization/dedupe and stream edit migration for favorites, history, playlists, queue, and playback position.
- Local sidecar `.lrc` lyrics loading with now-playing active-line display.
- Optional LRCLIB online lyrics fallback and configurable lyrics timing offset.
- Theme and accent settings, including system/dark/light, AMOLED, high-contrast, and named palette modes.

Desktop-only integrations such as Windows SMTC, tray behavior, global desktop shortcuts, Windows audio hosts, bundled downloader/converter binaries, Electron plugins, Discord presence, and AirPlay helper binaries are intentionally outside the Android MVP. See the root-level Android planning documents for the parity boundary and release checklist.

## Current Environment Notes

The Gradle wrapper is available and command-line verification currently works:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
.\gradlew.bat lintRelease
```

These commands passed during the local Androidization verification. On 2026-06-05, `assembleDebugAndroidTest`, `assembleDebug`, `lintDebug`, `assembleRelease`, `bundleRelease`, and `lintRelease` passed after playback queue index hardening, restored-queue play behavior fixes, persisted queue cleanup after track deletion, and media-session lifecycle hardening. `connectedDebugAndroidTest` passed 32 instrumentation tests on the Android 13/API 33 `EchoApi33` emulator, and the scripted single-device `-Connected -DeviceSerial emulator-5554` path passed the same 32 tests on that selected emulator while other devices were attached. The user-designated MuMu-MUSIC Android 12/API 32 instance passed 32 manual single-device instrumentation tests and signed release launch smoke as compatibility confidence. After the icon-first Compose UI pass, the latest debug build installed and launched on MuMu-MUSIC with screenshot evidence at `app/build/tmp/echo-ui-icon-first-mumu-music-20260605-pulled.png`, and the scripted single-device connected path reran 32 instrumentation tests successfully on that same selected device. A temporary smoke key produced signed release APK/AAB artifacts, and the signed release APK installed and launched on the API 33 emulator with `app.echo.next/.MainActivity` resumed; the API 33 release smoke was rerun successfully through `-ReleaseSmoke -DeviceSerial emulator-5554`. On 2026-06-04, the same debug and lint checks passed after M3U/M3U8 parser/export and stream-edit reference migration hardening. Debug APKs are generated under `app/build/outputs/apk/debug/`; release APK/AAB outputs are generated under `app/build/outputs/apk/release/` and `app/build/outputs/bundle/release/`.

The repeatable local verification entry point is:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
```

Add `-Connected` for attached-device instrumentation tests, or `-ReleaseSmoke -CreateSmokeKeystore` for a temporary-key signed release smoke on an attached Android 13+/API 33 or newer emulator/device. If multiple targets are attached, pass `-DeviceSerial <serial>`; for connected instrumentation, the script installs the debug APKs and runs `adb shell am instrument` on that selected target only. Use `-AllowPreAndroid13Smoke` only for non-release historical confidence checks.

Local emulator/smoke scratch files such as `screen-*.png`, `window-*.xml`, `echo_next_*.db`, and `tmp-m3u/` are ignored by `.gitignore`.

Public release still requires a maintainer-owned production keystore and a physical-device media-control acceptance pass on Android 13+.
