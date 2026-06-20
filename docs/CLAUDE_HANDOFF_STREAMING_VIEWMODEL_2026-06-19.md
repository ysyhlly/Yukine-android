# Claude Handoff: StreamingViewModel Facade Removal

Date: 2026-06-19
Owner: Codex

## Summary

Continued `docs/STREAMING_VIEWMODEL_SPLIT_PLAN.md` and completed phases 6/7. `MainActivityViewModel` is no longer a streaming facade. Streaming behavior now routes through `StreamingViewModel` directly.

## Completed

- Removed the `MainActivityViewModel` internal `StreamingViewModel` placeholder, `streaming` forwarding property, `bindStreamingViewModel`, and the old `return streamingViewModel.xxx(...)` streaming/heartbeat/manual-cookie/playlist proxy methods.
- Updated `MainActivity` remaining streaming call sites to direct `StreamingViewModel` access, including playback start heartbeat stop, current queue resolve, provider track id, heartbeat seed, manual cookie, playlist import/sync/login, and streaming status text.
- Removed `MainActivityViewModel` constructor dependencies from:
  - `StreamingManualCookieController`
  - `StreamingSearchActionHandlerBindings`
  - `StreamingAuthCallbackBindings`
- Deleted the old `MainActivityViewModelStreamingTest.kt`.
- Migrated key coverage into `StreamingViewModelTest`, including remote playlist fetch fallback, empty remote import skip, liked import link behavior, sync-specific remote playlist, and login playlist delegation.
- Added shared test coroutine rule: `app/src/test/java/app/yukine/MainDispatcherRule.kt`.
- Updated `MainActivityArchitectureContractTest` so the contract now asserts that streaming behavior belongs to `StreamingViewModel`, while `MainActivityViewModel.kt` only temporarily hosts shared top-level data classes/interfaces.
- Updated `docs/STREAMING_VIEWMODEL_SPLIT_PLAN.md` with this completion note.

## Verification

- Residual scan passed: production code no longer has old `viewModel.*Streaming*`, `viewModel.*Heartbeat*`, `viewModel.getStreaming()`, or `bindStreamingViewModel` paths.
- Focused tests passed:
  - `StreamingViewModelTest`
  - `StreamingSearchActionHandlerBindingsTest`
  - `StreamingAuthCallbackBindingsTest`
  - `StreamingManualCookieBindingsTest`
  - `DailyRecommendationControllerTest`
  - `HeartbeatRecommendationControllerTest`
  - `StreamingPlaybackControllerTest`
  - `StreamingPlaylistControllerTest`
- Full verification passed:
  - `.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug --console=plain`

## APK

- Path: `E:\ECHO andriod\echo-android\app\build\outputs\apk\debug\app-debug.apk`
- Size: `17,881,413` bytes
- Timestamp: `2026/6/19 07:20:15`

## Suggested Next Work

- Move the remaining streaming top-level data classes/interfaces out of `MainActivityViewModel.kt` into clearer files such as `StreamingContracts.kt` or `StreamingModels.kt`.
- `StreamingViewModel` still keeps several Activity-bound `bindXxx` seams for playback coordinator, local playlist operations, and track match store. If continuing Hilt cleanup, migrate those one dependency source at a time.
