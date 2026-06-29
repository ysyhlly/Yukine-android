# Playback P0 Baseline Freeze - 2026-06-29

This checkpoint freezes the playback migration baseline before any further
`EchoPlaybackService` queue, URI, cache, notification, lyrics, or shutdown
refactor. It is evidence only; no playback production code was changed for
this checkpoint.

## Dirty Worktree At Capture

`git status --short` before this baseline documentation showed the following
migration-site changes:

```text
 M app/src/main/java/app/yukine/LibraryModule.kt
 M app/src/main/java/app/yukine/MainActivityBase.java
 M app/src/test/java/app/yukine/MainActivityArchitectureContractTest.java
 M docs/ARCHITECTURE_REMEDIATION_PLAN_2026-06-26.md
 M docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md
 M docs/MVVM_MIGRATION_HANDOFF.md
?? app/src/main/java/app/yukine/MainCollectionsRenderListener.kt
?? app/src/main/java/app/yukine/MainLibraryGroupsRenderListener.kt
?? app/src/test/java/app/yukine/MainCollectionsRenderListenerTest.kt
?? app/src/test/java/app/yukine/MainLibraryGroupsRenderListenerTest.kt
```

Treat those entries as existing migration work. Do not revert them as part of
the playback service migration unless the user explicitly asks for that.

## Service And Queue Surface

- `app/src/main/java/app/yukine/playback/EchoPlaybackService.java`: 2469 lines.
- `PlaybackQueueManager.QueueProvider`: 33 methods.
- CodeGraph blast-radius note: `QueueProvider` is implemented through
  `EchoPlaybackService` and covered by `PlaybackQueueManagerTest` fakes, but
  it remains a large service-state interface. The next queue slice should
  delete or derive methods, not add more.

P0-captured `QueueProvider` method list:

```text
queue(): MutableList<Track>
currentIndex(): Int
repeatMode(): Int
shuffleEnabled(): Boolean
isPlaying(): Boolean
preparing(): Boolean
clearRestoredPosition()
resetCurrentPlaybackPosition()
savePlaybackResumeRequested(requested: Boolean)
prepareCurrent(playWhenReady: Boolean)
publishState()
stopAndClear()
seekMirroredQueueToCurrentIndex(playWhenReady: Boolean): Boolean
playbackPositionMs(): Long
restoredTrackFor(track: Track): Track?
restoreForDataPath(dataPath: String?)
isRestorableQueueTrack(track: Track): Boolean
setRestoredPosition(trackId: Long, positionMs: Long, explicit: Boolean)
setCurrentIndex(index: Int)
setErrorMessage(message: String)
setPreparing(preparing: Boolean)
setLastMarkedTrack(track: Track?)
setExplicitRestoredPosition(track: Track?, positionMs: Long): Long
recordStreamingRecovery(track: Track, restoredPositionMs: Long)
schedulePrepareCurrent(playWhenReady: Boolean)
mirroredQueueMatchesCurrentPlayer(): Boolean
resetWaveformIfTrackChanged(track: Track)
applyPlaybackParametersToPlayer()
applyPlaybackModeToPlayer()
seekMirroredQueueTo(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean
setPlayerMirrorsQueue(enabled: Boolean)
acquireWifiLockIfStreaming()
startProgressUpdates()
```

## Playback Test Inventory

Focused playback-adjacent unit tests currently found under `app/src/test` and
`feature/playback/src/test`:

```text
app/src/test/java/app/yukine/LoadLyricsSettingsUseCaseTest.kt
app/src/test/java/app/yukine/LoadTrackLyricsUseCaseTest.kt
app/src/test/java/app/yukine/LyricsStateRefreshListenerTest.kt
app/src/test/java/app/yukine/LyricsViewModelTest.kt
app/src/test/java/app/yukine/MainNowPlayingGatewayTest.kt
app/src/test/java/app/yukine/MainNowPlayingStateListenerTest.kt
app/src/test/java/app/yukine/MainPlaybackActionListenerTest.kt
app/src/test/java/app/yukine/MainPlaybackServiceHostTest.kt
app/src/test/java/app/yukine/MainPlaybackStartListenerTest.kt
app/src/test/java/app/yukine/MainPlaybackStateEventListenerTest.kt
app/src/test/java/app/yukine/MainQueueActionListenerTest.kt
app/src/test/java/app/yukine/MainQueueRenderListenerTest.kt
app/src/test/java/app/yukine/MainStreamingPlaybackListenerTest.kt
app/src/test/java/app/yukine/NetworkMenuEventControllerPlaybackTest.kt
app/src/test/java/app/yukine/NowPlayingPlaybackGatewayAdapterTest.kt
app/src/test/java/app/yukine/NowPlayingStateControllerTest.kt
app/src/test/java/app/yukine/NowPlayingStateFactoryTest.kt
app/src/test/java/app/yukine/NowPlayingViewModelTest.kt
app/src/test/java/app/yukine/PlaybackActionControllerTest.kt
app/src/test/java/app/yukine/PlaybackStartControllerTest.kt
app/src/test/java/app/yukine/PlaybackStateUpdateControllerTest.kt
app/src/test/java/app/yukine/PlaybackViewModelTest.kt
app/src/test/java/app/yukine/QueueActionControllerTest.kt
app/src/test/java/app/yukine/ResolveStreamingPlaybackUseCaseTest.kt
app/src/test/java/app/yukine/StreamingPlaybackTaskSchedulerTest.java
app/src/test/java/app/yukine/data/LyricsRepositoryTest.java
app/src/test/java/app/yukine/playback/AudioEffectSettingsTest.java
app/src/test/java/app/yukine/playback/EchoPlaybackServiceTest.java
app/src/test/java/app/yukine/playback/PlaybackStreamingDiagnosticsTest.java
app/src/test/java/app/yukine/playback/PlaybackWaveformMergePolicyTest.java
app/src/test/java/app/yukine/playback/StreamingWaveformGeneratorTest.java
app/src/test/java/app/yukine/queue/QueueDestinationTest.kt
app/src/test/java/app/yukine/queue/QueueViewModelTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackErrorRecoveryManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackNoisyReceiverManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackPositionManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackProgressUpdateManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackQueueManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackQueueRuntimeStateManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackRuntimeStateManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackShutdownCoordinatorTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackSleepTimerManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackStatePublisherTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackTransitionStateManagerTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackWarmupCoordinatorTest.kt
feature/playback/src/test/java/app/yukine/playback/PlaybackWifiLockManagerTest.kt
```

## Verification Results

Passed with default Gradle daemon/workers:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests "app.yukine.playback.*" --tests "app.yukine.*Playback*" --tests "app.yukine.*Queue*" --tests "app.yukine.*NowPlaying*" --tests "app.yukine.*Lyrics*" --tests "app.yukine.*Media*" --tests "app.yukine.*Notification*" --console=plain
```

No daemon, KSP, or file-lock issue reproduced during this checkpoint, so
`--no-daemon` and `--max-workers=1` were not used.

## Smoke Table

Device smoke was not executed during this documentation checkpoint. These
rows are the required evidence table before a risky playback P1/P2 slice is
treated as fully smoke-covered.

| Scenario | Required Evidence | 2026-06-29 Status |
| --- | --- | --- |
| Local playback | Playback screen, NowBar, and notification stay in sync for a local queue. | Not run |
| Background playback | Playback continues after Home and returns with synchronized UI/notification state. | Not run |
| Notification controls | Notification pause/play/previous/next actions affect the active queue immediately. | Not run |
| Queue restore | Cold restart restores queue/current track/position or gives an explicit recoverable state. | Not run |
| Lyrics | Lyrics state follows play/pause/seek and track changes. | Not run |
| Streaming playback | Streaming URL resolution, playback start, cache fallback, and failure messaging behave without blocking local playback. | Not run |

## P0 Exit State

Automated baseline status is known: compile passed and the focused
playback-adjacent test slice passed. Device smoke status is also known: it
has not been run for this checkpoint. Do not start a broad service split from
this baseline; the next implementation slice should be a small
`PlaybackQueueManager` authority-source change with focused tests first and
device smoke recorded before claiming runtime coverage.

## P1 Delta - Queue Authority Slices

After the first P1 queue-authority slices:

- `PlaybackQueueManager` owns restored track eligibility directly, and
  `PlaybackQueueManager.QueueProvider` no longer exposes
  `isRestorableQueueTrack(track: Track): Boolean`.
- `PlaybackQueueManager` owns current-index state and clamping directly, and
  `PlaybackQueueManager.QueueProvider` no longer exposes `currentIndex()` or
  `setCurrentIndex(index)`.
- `PlaybackQueueRuntimeStateManager` now keeps only MediaSession mirror state
  for this area; it no longer owns current-index state.
- Queue-driven resume-request writes go through
  `PlaybackQueueManager -> PlaybackQueueStore.saveResumeRequested(...)`, and
  `PlaybackQueueManager.QueueProvider` no longer exposes
  `savePlaybackResumeRequested(requested)`.
- MediaSession mirror eligibility now flows through
  `PlaybackQueueManager.mirroredQueueTracksForPreparation()`. The service no
  longer keeps a separate `canMirrorQueueToPlayer()` precheck; it receives a
  queue snapshot from the queue owner and falls back to single-track
  preparation when the owner rejects the queue.
- Queue skip/previous mirrored-player reuse now goes through
  `PlaybackQueueManager.reuseMirroredQueueAtCurrentIndex(...)`. The
  `PlaybackQueueManager.QueueProvider` no longer exposes
  `seekMirroredQueueToCurrentIndex(playWhenReady)`.
- Current playback position reads for queue persistence and streaming
  replacement resume now go through `PlaybackPositionManager.positionMs()`.
  `PlaybackQueueManager.QueueProvider` no longer exposes
  `playbackPositionMs()`.
- Current `EchoPlaybackService.java`: 2344 lines.
- Current `PlaybackQueueManager.QueueProvider`: 23 methods.
- Behavior guards:
  `PlaybackQueueManagerTest.restorePlaybackQueueFiltersInvalidTracksInsideManager`,
  `PlaybackQueueManagerTest.currentIndexStateIsOwnedByQueueManager`, and the
  existing previous/replace-current/mirrored-queue tests, plus
  `PlaybackQueueManagerTest.queuePlaybackStartPersistsResumeRequestThroughStore`
  and
  `PlaybackQueueManagerTest.mirroredQueueTracksForPreparationRejectsEmptyUriWithoutPartialRestore`
  and
  `PlaybackQueueManagerTest.skipToNextReusesMirroredQueueWithoutPreparingNewMediaSources`,
  plus
  `PlaybackPositionManagerTest.positionMsReadsCurrentPlaybackPositionFromStateProvider`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`
  plus `MainActivityArchitectureContractTest.playbackQueueRuntimeStateIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Queue Data Source Split From Playback Actions

After the next queue ownership slice:

- `PlaybackQueueManager.QueueProvider` is now a queue data source only. It
  exposes `queue()` and no longer carries service playback actions.
- Service callbacks for `isPlaying()`, `prepareCurrent(...)`, `publishState()`,
  and `stopAndClear()` moved to the narrow
  `PlaybackQueueManager.QueuePlaybackActions` port.
- `EchoPlaybackService` still supplies the Android/Media3 boundary behavior,
  but queue mutation policy now calls `queuePlaybackActions` for playback
  effects and `queueProvider.queue()` only for queue data.
- Current `EchoPlaybackService.java`: 2189 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - NowPlaying ViewModel Avoids Concrete Service Constants

After the first P6 structure-dependency slice:

- `NowPlayingViewModel` no longer imports `EchoPlaybackService` for transport
  action strings or repeat-mode constants.
- Offline previous/next service startup now uses the existing
  `PlaybackServiceActions` owner.
- Bottom playback-mode cycling and repeat-mode UI mapping now use
  `PlaybackRepeatMode`.
- The concrete service dependency remains confined to
  `NowPlayingPlaybackGatewayAdapter`, which is the current Android service
  boundary adapter for `NowPlayingPlaybackGateway`.
- Current `EchoPlaybackService.java`: unchanged by this slice.
- Current `PlaybackQueueManager.QueueProvider`: unchanged at 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: unchanged at 8 methods.
- Behavior guard: `NowPlayingViewModelTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackStateListenerStaysOutOfMainActivity`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.NowPlayingViewModelTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.PlaybackServiceActionsTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - External Playback Actions Use Action Owner

After the second P6 structure-dependency slice:

- Live-lyrics notification controls, playback widget controls, and boot
  restore now use `PlaybackServiceActions` for playback action strings.
- `EchoPlaybackService` remains the Android service `Intent` target and keeps
  `ACTION_*` aliases only as compatibility aliases guarded by
  `PlaybackServiceActionsTest`.
- `NowBarStateFactory` no longer imports `EchoPlaybackService`; now-playing
  state tests use `PlaybackRepeatMode` instead of concrete service repeat
  aliases.
- Production code no longer contains `EchoPlaybackService.ACTION_*` callers
  outside the service alias definitions.
- Current `EchoPlaybackService.java`: unchanged by this slice at 2046 lines.
- Current `PlaybackQueueManager.QueueProvider`: unchanged at 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: unchanged at 8 methods.
- Behavior guards:
  `NowPlayingPlaybackGatewayAdapterTest`, `NowPlayingStateFactoryTest`,
  `NowPlayingStateControllerTest`, and `PlaybackServiceActionsTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.liveLyricsNotificationServiceKeepsOppoFluidCloudContract`
  and `MainActivityArchitectureContractTest.playbackStateListenerStaysOutOfMainActivity`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.NowPlayingPlaybackGatewayAdapterTest --tests app.yukine.NowPlayingStateFactoryTest --tests app.yukine.NowPlayingStateControllerTest --tests app.yukine.playback.PlaybackServiceActionsTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3/P5 Delta - Visualization Cache Policy Stays In Analyzer

After the visualization cleanup slice:

- `EchoPlaybackService` no longer keeps stale private
  `maybeGenerateSpectrum(...)`, `maybeGenerateStreamingWaveform(...)`, or
  `visualizationCachedProgress(...)` helpers. Streaming waveform/spectrum
  scheduling and cached-progress calculation remain in
  `PlaybackVisualizationAnalyzer`.
- Unused `waveformKey(...)` and `audioCache()` helpers were removed from the
  service.
- Visualization state providers now call `PlaybackMediaSourceProvider`
  directly for cache data sources, cache keys, continuous cached bytes, and
  content length instead of routing through service-private cache proxy
  methods. `bufferedProgress(...)` remains in the service because it reads the
  active Media3 player state.
- Current `EchoPlaybackService.java`: 2002 lines.
- Current `PlaybackQueueManager.QueueProvider`: unchanged at 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: unchanged at 8 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackStartDefersHeavyVisualizationWork`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P5 Delta - QueueProvider No Longer Carries Wi-Fi Lock Actions

After the queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `acquireWifiLockIfStreaming()`. Queue code reports whether mirrored queue
  reuse succeeded; `EchoPlaybackService`, as the Android boundary, calls the
  existing `PlaybackWifiLockManager` when playback is being resumed through a
  reused mirrored queue.
- `PlaybackQueueManager.skipToNextImmediately()` and
  `PlaybackQueueManager.skipToPrevious()` now return a boolean reuse result
  instead of triggering platform lock side effects through the queue provider.
- `PlaybackWifiLockManager` remains the Wi-Fi lock owner. The service still
  binds lock lifetime to `onIsPlayingChanged(...)` as the broad safety path.
- Current `EchoPlaybackService.java`: 2004 lines.
- Current `PlaybackQueueManager.QueueProvider`: 22 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Playback Store Publishes Queue Snapshots, Not Services

After the playback state dependency cleanup:

- `MainPlaybackStore.publish(...)` now accepts a `List<Track>` queue snapshot
  instead of an `EchoPlaybackService?`. The store no longer imports or depends
  on the concrete playback service.
- `PlaybackStateEventController.QueueSnapshotSource` now supplies queue
  snapshots directly. The controller no longer imports `EchoPlaybackService`
  just to refresh the view-model queue state after playback state events.
- `MainActivityBase`, which already owns the concrete bound service boundary,
  converts `playbackService.queueSnapshot()` through a private
  `playbackQueueSnapshot()` helper and calls `publishPlaybackStore()` at the
  existing state publish sites.
- Current `EchoPlaybackService.java`: 2004 lines.
- Current `PlaybackQueueManager.QueueProvider`: 22 methods.
- Architecture guards:
  `MainActivityArchitectureContractTest.navigationStateMovesOutOfMainActivity`
  and `MainActivityArchitectureContractTest.mainPlaybackStoreIsKotlinStateHolder`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P5 Delta - QueueProvider No Longer Starts Progress Updates

After the second queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `startProgressUpdates()`. Mirrored queue reuse remains a queue decision, but
  progress update scheduling is now triggered from `EchoPlaybackService` after
  the queue owner reports successful reuse.
- `EchoPlaybackService.onMirroredQueueReused(...)` now centralizes the service
  side effects that follow mirrored queue reuse: Wi-Fi lock acquisition when
  playback is requested, plus progress update scheduling.
- Current `EchoPlaybackService.java`: 2002 lines.
- Current `PlaybackQueueManager.QueueProvider`: 21 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P2 Delta - Mirrored Queue Seek Owns Player/Visualization Side Effects

After the third queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `resetWaveformIfTrackChanged(...)`, `applyPlaybackParametersToPlayer()`, or
  `applyPlaybackModeToPlayer()`.
- Mirrored queue reuse remains a queue decision, but the actual Media3 seek
  boundary in `EchoPlaybackService.seekMirroredQueueTo(...)` now applies
  playback parameters, playback mode, and waveform reset before seeking the
  mirrored player queue.
- `restoreForDataPath(...)` stays on the provider for now because it is used by
  both mirrored queue reuse and queue restoration/preparation loops.
- Current `EchoPlaybackService.java`: 1996 lines.
- Current `PlaybackQueueManager.QueueProvider`: 18 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P5 Delta - Mirrored Queue Runtime Flags Stay At Service Boundary

After the fourth queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `setPreparing(...)` or `setPlayerMirrorsQueue(...)`.
- The Media3 seek boundary in `EchoPlaybackService.seekMirroredQueueTo(...)`
  now clears `preparing` before reusing the mirrored player queue and clears the
  mirrored-queue runtime flag when the player seek fails.
- `PlaybackRuntimeStateManager` and `PlaybackQueueRuntimeStateManager` remain
  the runtime state owners; this change only removes those writes from the queue
  provider bridge.
- Current `EchoPlaybackService.java`: 1994 lines.
- Current `PlaybackQueueManager.QueueProvider`: 16 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P5 Delta - Streaming Recovery Scheduling Stays At Service Boundary

After the fifth queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `recordStreamingRecovery(...)` or `schedulePrepareCurrent(...)`.
- `PlaybackQueueManager.replaceCurrentTrackAndResume(...)` still owns the
  queue replacement and restored-position write, then returns a narrow
  `CurrentTrackReplacementRecovery` request when a new playable item requires
  service-bound recovery work.
- `EchoPlaybackService.replaceCurrentTrackAndResume(...)`, as the Android
  boundary, records streaming diagnostics and schedules the main-thread
  `prepareCurrent(...)` recovery task from that request.
- Current `EchoPlaybackService.java`: 2208 lines.
- Current `PlaybackQueueManager.QueueProvider`: 14 methods.
- Behavior guard:
  `PlaybackQueueManagerTest.replaceCurrentTrackAndResumeSchedulesRecoveryForNewUri`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P2 Delta - Streaming Header Restore Uses A Narrow Queue Port

After the sixth queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `restoredTrackFor(...)` or `restoreForDataPath(...)`.
- Streaming playback header restoration for queue restore and mirrored queue
  preparation now goes through the narrow
  `PlaybackQueueManager.StreamingRestoreProvider` port. `EchoPlaybackService`
  adapts that port to the existing `StreamingPlaybackHeaderStore`.
- `PlaybackQueueManager` still owns the queue mutation and mirrored queue
  eligibility decisions; service-local `prepareCurrent(...)` and Media3
  preparation still keep their direct Android/Media3 boundary calls.
- Current `EchoPlaybackService.java`: 2209 lines.
- Current `PlaybackQueueManager.QueueProvider`: 12 methods.
- Behavior guards:
  `PlaybackQueueManagerTest.restorePlaybackQueueFiltersInvalidTracksInsideManager`,
  `PlaybackQueueManagerTest.skipToNextReusesMirroredQueueWithoutPreparingNewMediaSources`,
  and the mirrored queue preparation tests.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Queue Runtime State Writes Use Existing Owners

After the seventh queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `setErrorMessage(...)` or `setLastMarkedTrack(...)`.
- Queue mutations that clear stale playback errors now call the existing
  `PlaybackRuntimeStateManager` owner through a local
  `clearErrorMessage()` helper.
- Queue mutations that clear transition marker state now call the existing
  `PlaybackTransitionStateManager` owner through a local
  `clearLastMarkedTrack()` helper.
- `EchoPlaybackService` no longer bridges these state writes through the
  queue provider; it only supplies the existing state owners to
  `PlaybackQueueManager`.
- Current `EchoPlaybackService.java`: 2201 lines.
- Current `PlaybackQueueManager.QueueProvider`: 10 methods.
- Behavior guard:
  `PlaybackQueueManagerTest.queuePlaybackStartClearsRuntimeErrorAndTransitionMarkerThroughOwners`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Queue Playback Mode Reads Use Runtime State Owner

After the eighth queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes `repeatMode()` or
  `shuffleEnabled()`.
- Queue next-index calculation now reads playback mode from the existing
  `PlaybackRuntimeStateManager` owner. The fallback behavior remains
  `REPEAT_ALL` and shuffle disabled when no runtime owner is supplied.
- `EchoPlaybackService` no longer bridges repeat/shuffle reads through the
  queue provider; it supplies the runtime state owner to
  `PlaybackQueueManager`.
- Current `EchoPlaybackService.java`: 2191 lines.
- Current `PlaybackQueueManager.QueueProvider`: 8 methods.
- Behavior guard:
  `PlaybackQueueManagerTest.advanceQueueIndexToNextReadsRepeatModeFromRuntimeStateOwner`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P2 Delta - Mirrored Queue Player Boundary Uses A Narrow Port

After the ninth queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes
  `mirroredQueueMatchesCurrentPlayer()` or
  `seekMirroredQueueTo(...)`.
- Mirrored queue reuse still remains a queue decision inside
  `PlaybackQueueManager`, but Media3 player identity checks and player seeks
  now go through the narrow `PlaybackQueueManager.MirroredQueuePlayer` port.
- `EchoPlaybackService` adapts that port to the existing Android/Media3
  boundary code that checks player queue identity, resets waveform state,
  reapplies playback parameters/mode, seeks the player, and clears mirror state
  on seek failure.
- Current `EchoPlaybackService.java`: 2193 lines.
- Current `PlaybackQueueManager.QueueProvider`: 6 methods.
- Behavior guards:
  `PlaybackQueueManagerTest.skipToNextReusesMirroredQueueWithoutPreparingNewMediaSources`,
  `PlaybackQueueManagerTest.reuseMirroredQueueAppliesQueueStateWithoutPreparingNewMediaSources`,
  and
  `PlaybackQueueManagerTest.reuseMirroredQueueFallsBackWhenPlayerSeekFails`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Queue Preparing Reads Use Runtime State Owner

After the tenth queue/provider boundary cleanup:

- `PlaybackQueueManager.QueueProvider` no longer exposes `preparing()`.
- Queue replacement decisions that need to preserve active preparation now read
  the existing `PlaybackRuntimeStateManager` owner through a local
  `preparing()` helper.
- `EchoPlaybackService` no longer bridges preparing state through the queue
  provider; it still owns direct service/runtime uses of preparing state for
  seek, snapshot, notification worthiness, and lifecycle persistence.
- Current `EchoPlaybackService.java`: 2188 lines.
- Current `PlaybackQueueManager.QueueProvider`: 5 methods.
- Behavior guard:
  `PlaybackQueueManagerTest.replaceQueuedTrackContinuesPreparingPlaybackFromRuntimeStateOwner`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3 Prep Delta - Precache Cache Owner Surface

After the first P3-prep cache/precache ownership slice:

- `PlaybackPrecacheManager` now receives the existing
  `PlaybackMediaSourceProvider` directly for cache operations. Cache headers,
  audio cache access, cache data source creation, and content-length metadata
  no longer pass through `EchoPlaybackService` via
  `PlaybackPrecacheManager.StateProvider`.
- `PlaybackPrecacheManager.StateProvider` is reduced to playback/queue state,
  active-player reuse checks, diagnostics, and main-thread scheduling. Its
  method count is now 9.
- `EchoPlaybackService` still owns the Android/Media3 player boundary for
  `currentPlayerLoadsCacheKey(...)`, but it no longer proxies precache cache
  storage operations.
- Current `EchoPlaybackService.java`: 2273 lines.
- Current `PlaybackQueueManager.QueueProvider`: 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: 9 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.streamingPlaybackCacheUsesSegmentedConcurrentPrecache`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.EchoPlaybackServiceTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P4 Prep Delta - Notification Action Vocabulary

After the first P4-prep notification/action ownership slice:

- Playback service action strings moved into `PlaybackServiceActions`.
  `EchoPlaybackService.ACTION_*` remain as compatibility aliases while active
  notification, widget, restore, and UI callers move to the action owner.
- `PlaybackNotificationManager` no longer duplicates raw playback action
  strings in notification button construction. It maps notification buttons to
  `PlaybackServiceActions` and still receives only Android `PendingIntent`
  construction from the service boundary.
- `EchoPlaybackService.isPlaybackServiceAction(...)` now delegates action
  recognition to `PlaybackServiceActions`.
- Current `EchoPlaybackService.java`: 2266 lines.
- Current `PlaybackQueueManager.QueueProvider`: 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: 9 methods.
- Behavior guards:
  `PlaybackServiceActionsTest.serviceConstantsRemainCompatibilityAliasesForSharedActions`
  and
  `PlaybackServiceActionsTest.actionOwnerRecognizesPlaybackServiceActions`.
- Architecture guard:
  `MainActivityArchitectureContractTest` notification ownership assertions for
  `PlaybackServiceActions`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackServiceActionsTest --tests app.yukine.playback.EchoPlaybackServiceTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P4 Prep Delta - Lyrics Setting Bridge

After the second P4-prep notification/lyrics ownership slice:

- `PlaybackLyricsManager` now owns the status-bar lyrics setting side effects:
  live-lyrics notification service sync, playback session refresh, and forced
  media notification refresh.
- `EchoPlaybackService.setStatusBarLyricsEnabled(...)` is reduced to delegating
  the setting change to `PlaybackLyricsManager`; the service still supplies the
  Android/Media3 boundary callbacks through `PlaybackLyricsManager.StateProvider`.
- Current `EchoPlaybackService.java`: 2269 lines.
- Current `PlaybackQueueManager.QueueProvider`: 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: 9 methods.
- Behavior guard:
  `PlaybackLyricsManagerTest.statusBarLyricsSettingChangeRefreshesSessionAndNotificationFromLyricsOwner`.
- Architecture guard:
  `MainActivityArchitectureContractTest` live-lyrics notification assertions for
  `PlaybackLyricsManager` ownership.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackLyricsManagerTest --tests app.yukine.playback.PlaybackServiceActionsTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Prep Delta - Service Callback Shutdown Owner

After the first P5-prep shutdown/concurrency ownership slice:

- Full service teardown now routes main-thread callback clearing through
  `PlaybackShutdownCoordinator.releaseServiceResources()`. `EchoPlaybackService`
  persists the final playback position and delegates teardown instead of
  directly clearing `mainHandler` callbacks in `onDestroy()`.
- `releasePlaybackResources()` still keeps service-level callbacks and
  schedulers alive. Full service teardown unregisters receivers, shuts down task
  schedulers before clearing main-thread callbacks, then releases artwork,
  precache work, the Wi-Fi lock, and the player.
- Current `EchoPlaybackService.java`: 2274 lines.
- Current `PlaybackQueueManager.QueueProvider`: 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: 9 methods.
- Behavior guard:
  `PlaybackShutdownCoordinatorTest.releaseServiceResourcesRunsFullServiceTeardown`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Prep Delta - Precache Callback Cancellation Owner

After the second P5-prep shutdown/concurrency ownership slice:

- `PlaybackPrecacheManager` now owns delayed precache callback scheduling and
  cancellation through a narrow `CallbackScheduler`. It tracks pending delayed
  precache callbacks, cancels stale callbacks when the current precache
  generation changes, and cancels remaining callbacks during `release()`.
- `PlaybackPrecacheManager.StateProvider` no longer exposes Android `Handler`;
  the broad state provider method count is now 8. `EchoPlaybackService` still
  provides the Android main-thread boundary through the scheduler adapter.
- Current `EchoPlaybackService.java`: 2279 lines.
- Current `PlaybackQueueManager.QueueProvider`: 23 methods.
- Current `PlaybackPrecacheManager.StateProvider`: 8 methods.
- Behavior guards:
  `PlaybackPrecacheManagerTest.releaseCancelsDelayedPrecacheCallbacksOwnedByManager`
  and
  `PlaybackPrecacheManagerTest.replacingCurrentPrecacheCancelsPreviousDelayedCallbacks`.
- Architecture guard:
  `MainActivityArchitectureContractTest.streamingPlaybackCacheUsesSegmentedConcurrentPrecache`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.playback.EchoPlaybackServiceTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P2 Prep Delta - MediaItem Reuse Identity Owner

After the first P2-prep URI/MediaItem ownership slice:

- MediaItem reuse identity checks moved from `EchoPlaybackService` to the
  existing `PlaybackMediaSourceProvider` owner. This keeps the resolved URI,
  media item id, and custom cache key comparison next to media item/cache-key
  construction instead of adding a new resolver facade.
- Playback `MediaItem` construction now also flows through
  `PlaybackMediaSourceProvider.mediaItemForTrack(...)`. The service supplies
  track metadata and receives the playable item; it no longer builds playback
  `MediaItem`s inline or asks `PlaybackMediaLibraryCallback` to build playback
  items for player preparation.
- `PlaybackMediaLibraryCallback.DataSource` now receives a ready
  `mediaItemForTrack(track)` for playable children instead of a playback
  cache-key callback. The callback still asks for metadata separately for
  browsable Android Auto items.
- `EchoPlaybackService` still performs the Android/Media3 boundary checks for
  the active player, but it now calls
  `PlaybackMediaSourceProvider.mediaItemMatchesTrackForReuse(...)` for both
  current-track precache reuse and mirrored queue reuse.
- `EchoPlaybackService` no longer defines
  `mediaItemMatchesTrackForReuse(...)`,
  `mediaItemIdentityMatchesForReuse(...)`, or
  `cacheKeyMatchesForReuse(...)`.
- Streaming/local media cache-key policy is tested through
  `PlaybackMediaSourceProvider`; `EchoPlaybackService` no longer exposes
  static `mediaCacheKey(...)` helpers for tests or callers.
- Current `EchoPlaybackService.java`: 2293 lines.
- Current `PlaybackQueueManager.QueueProvider`: 23 methods.
- Behavior guards:
  `PlaybackMediaSourceProviderTest.playbackMediaItemForLocalTrackPreservesUriAndMetadataWithoutCacheKey`,
  `PlaybackMediaSourceProviderTest.playbackMediaItemForStreamingTrackPreservesResolvedUriAndStreamingCacheKeyRule`,
  `PlaybackMediaSourceProviderTest.streamingCacheKeyIncludesResolvedUrl`,
  `PlaybackMediaSourceProviderTest.mirroredQueueReuseRequiresResolvedUriToMatch`
  and
  `PlaybackMediaSourceProviderTest.mirroredQueueReuseAllowsSameResolvedMediaItem`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackMediaItemReuseIdentityIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.playback.EchoPlaybackServiceTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.playback.EchoPlaybackServiceTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackQueueRuntimeStateManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```
