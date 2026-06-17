# Yukine Android

Yukine is a modern Android music player for local libraries, playlist workflows, lyric browsing, and streaming source integration.

Its product direction and interface rhythm take inspiration from Echo Next: soft visual layers, music-first navigation, quick access to lyrics and queue controls, and a calm player experience designed for long listening sessions.

It focuses on a clean listening flow:
- local music scan and library browsing
- playlists, queue, favorites, and recent playback
- synchronized lyrics with line highlighting
- streaming source search, import, and playback resolution
- persistent playback service and background control

## Highlights

- Compose UI with a lightweight, mobile-first player layout
- Media3 playback with background service support
- local library management backed by SQLite
- lyrics loading, offset control, and copy-friendly lyric display
- streaming gateway support for external sources and custom adapters
- queue management, playback recovery, and recommendation workflows

## Build

```powershell
.\gradlew.bat assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Test

```powershell
.\gradlew.bat testDebugUnitTest
```

## Notes

- The project package remains `app.yukine` during migration.
- Activity-facing playback, streaming, library, and settings gateway glue is being moved into small Kotlin bindings as part of the MVVM migration.
- Existing internal architecture names may still use `echo` until the refactor is complete.
- Playback service release checks are tracked in [docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md](docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md).
