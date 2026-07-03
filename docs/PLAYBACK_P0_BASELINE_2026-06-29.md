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

## P1 Delta - Queue Advance Predicates Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.hasMultipleTracks()` now owns the queue-length
  predicate used by automatic fade-out advancement.
- `PlaybackQueueManager.isAtEndOfQueue()` now owns the current-index versus
  queue-size predicate used by repeat-off completion and fade-out advancement.
- `EchoPlaybackService` no longer combines `queueSize() < 2` or
  `currentIndex() >= queueSize() - 1` directly for queue advance policy.
- Current `EchoPlaybackService.java`: 2182 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest.queueAdvancePredicatesAreOwnedByQueueManager`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Error Recovery Uses Queue Predicate Instead Of Raw Size

After the next queue ownership slice:

- `PlaybackErrorRecoveryManager.Actions` now asks for
  `hasMultipleQueueTracks()` instead of raw `queueSize()`.
- `EchoPlaybackService` wires that predicate to the existing queue owner
  delegate, `PlaybackQueueManager.hasMultipleTracks()`.
- Error recovery still owns retry/skip policy, but it no longer interprets
  queue size directly.
- Current `EchoPlaybackService.java`: 2182 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guards:
  `PlaybackErrorRecoveryManagerTest.retriesStreamingTrackOnceBeforeSkipping`
  and
  `PlaybackErrorRecoveryManagerTest.singleTrackErrorDoesNotSkipToNext`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackErrorRecoveryIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Remove Unused Queue Forwarding Wrappers

After the next queue ownership cleanup:

- Removed unused `EchoPlaybackService.advanceQueueIndexToNext()` and
  `EchoPlaybackService.clampedCurrentIndex()` wrappers.
- `EchoPlaybackService` no longer exposes a forwarding-only entry point for
  queue index advancement.
- Current `EchoPlaybackService.java`: 2167 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Mirrored Transition Index Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.applyMirroredTransitionIndex(nextIndex, automaticAdvance)`
  now owns validation of player-reported mirrored queue indexes.
- Repeat-off automatic Media3 transitions now return a queue-manager result
  telling the service to stop at the completed index; normal transitions update
  the current index inside the queue owner.
- `EchoPlaybackService` no longer checks `nextIndex < 0`,
  `nextIndex >= queueSize()`, or `nextIndex == currentIndex()` directly, and
  the unused `setCurrentIndex(int)` service wrapper was removed.
- Current `EchoPlaybackService.java`: 2177 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest.mirroredTransitionIndexValidationAndRepeatStopAreOwnedByQueueManager`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Queue Index Clamping Size Hidden Inside Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.clampCurrentIndex()` and
  `PlaybackQueueManager.setClampedCurrentIndex(index)` now derive queue size
  from the queue owner instead of accepting size from `EchoPlaybackService`.
- `EchoPlaybackService` no longer passes `queueSize()` back into queue index
  normalization.
- Current `EchoPlaybackService.java`: 2182 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest.currentIndexStateIsOwnedByQueueManager`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueRuntimeStateIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
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

## P1 Delta - Queue Clear And Persist State Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.clearQueueState()` now owns clearing the mutable queue
  and resetting the current index.
- `PlaybackQueueManager.persistQueueState()` now owns explicit queue snapshot
  persistence for service lifecycle and player transition callbacks.
- `EchoPlaybackService.clearQueueState()` and `persistPlaybackQueue()` are
  reduced to boundary delegates and no longer call `queue.clear()` or
  `queueStore().save(new ArrayList<>(queue), currentIndex())` directly.
- Current `EchoPlaybackService.java`: 2174 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guards:
  `PlaybackQueueManagerTest.clearQueueStateClearsQueueAndCurrentIndexWithoutPublishing`
  and
  `PlaybackQueueManagerTest.persistQueueStateSavesCurrentSnapshotThroughQueueStore`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Mirrored Queue Traversal Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.matchesMirroredQueue(...)` now owns queue-side mirrored
  player traversal and item-count matching.
- `EchoPlaybackService.mirroredQueueMatchesCurrentPlayer()` still owns the
  Android/Media3 boundary: checking player state, reading `MediaItem`s, and
  asking `PlaybackMediaSourceProvider` whether each player item matches a
  queue track.
- `EchoPlaybackService` no longer loops over `queue.size()` or reads
  `queue.get(i)` for mirrored queue comparison.
- Current `EchoPlaybackService.java`: 2171 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest.matchesMirroredQueueChecksItemCountAndDelegatesTrackIdentity`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Current Queue Track Replacement Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.replaceCurrentQueueTrack(...)` now owns the
  current-index queue slot replacement used after streaming header restoration.
- `EchoPlaybackService.replaceCurrentQueueTrack(...)` is reduced to a boundary
  delegate and no longer calls `queue.set(currentIndex(), track)` or persists
  that replacement directly.
- Current `EchoPlaybackService.java`: 2168 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guards:
  `PlaybackQueueManagerTest.replaceCurrentQueueTrackPersistsCurrentSlotOnly`
  and
  `PlaybackQueueManagerTest.replaceCurrentQueueTrackRejectsMissingCurrentSlot`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Queue Empty Size And Safe Index Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager` now exposes `isQueueEmpty()`, `queueSize()`, and
  `safeCurrentIndex()` for service boundary code that only needs derived queue
  state.
- `EchoPlaybackService` no longer uses `queue.isEmpty()` or simple
  `queue.size()` checks for skip, restore, snapshot, notification-worthiness,
  mirrored queue preparation entry checks, or safe current index selection.
- A stale service-local `indexOfTrackOccurrence(...)` helper was removed after
  confirming it had no callers; the active implementation already lives inside
  `PlaybackQueueManager`.
- The remaining direct queue traversal in service is limited to mirrored player
  queue comparison and current-track replacement, which are separate follow-up
  slices.
- Current `EchoPlaybackService.java`: 2170 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest.currentIndexStateIsOwnedByQueueManager`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Queue Snapshot Derivation Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.queueSnapshot()` now owns the defensive queue snapshot
  used by UI/service state readers.
- `EchoPlaybackService.queueSnapshot()` delegates to the queue manager, and the
  precache state provider now reuses that service snapshot instead of copying
  the mutable service queue directly.
- Current `EchoPlaybackService.java`: 2186 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest.queueSnapshotIsOwnedByQueueManagerAndDefensive`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Delta - Current Track Derivation Owned By Queue Manager

After the next queue ownership slice:

- `PlaybackQueueManager.currentTrack()` is now the owner for deriving the
  current track from the queue and current index.
- `EchoPlaybackService.currentTrack()` delegates to the queue manager instead
  of reading `queue.get(currentIndex())` directly. The service still keeps the
  Android/Media3 boundary calls that need the current track.
- Current `EchoPlaybackService.java`: 2186 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guard:
  `PlaybackQueueManagerTest.currentTrackStateIsOwnedByQueueManager`.
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

## P4 Delta - Notification Foreground Boundary Split

After the next P4 notification bridge slice:

- `PlaybackNotificationManager.ForegroundPresenter` was split into
  `ForegroundController` and `StateProvider`.
- `ForegroundController` now owns Android notification boundary actions:
  activity pending intent, service action pending intents, and starting
  foreground playback.
- `StateProvider` now owns playback/session state reads used to build the
  notification: notification-worthy state, playing, preparing, current track,
  and MediaSession platform token.
- Notification construction and action mapping remain inside
  `PlaybackNotificationManager`; `EchoPlaybackService` only supplies the
  Android/Media3 boundary callbacks and state reads.
- Current `EchoPlaybackService.java`: 2084 lines.
- Current `PlaybackNotificationManager.ForegroundController`: 3 methods.
- Current `PlaybackNotificationManager.StateProvider`: 5 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.liveLyricsNotificationServiceKeepsOppoFluidCloudContract`.
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

## P2 Delta - Playable URI Validation Owner

After the next P2 URI/MediaItem ownership slice:

- Empty playback URI validation moved from `EchoPlaybackService.prepareCurrent()`
  to the existing `PlaybackMediaSourceProvider` owner. The service now asks for
  an unplayable-track message and only applies runtime state/logging at the
  Android/Media3 boundary.
- Streaming placeholder detection now uses `StreamingDataPathMetadata` inside
  `PlaybackMediaSourceProvider`; `EchoPlaybackService` no longer carries the
  private `isStreamingPlaceholder(...)` helper or hard-coded
  `track.dataPath.startsWith("streaming:")` check for prepare validation.
- The first P2 slice remains a small extension of the existing media-source
  owner, not a new resolver/facade.
- Current `EchoPlaybackService.java`: 2160 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guards:
  `PlaybackMediaSourceProviderTest.resolvedTrackIsPlayable`,
  `PlaybackMediaSourceProviderTest.emptyLocalUriReturnsGenericOpenError`, and
  `PlaybackMediaSourceProviderTest.emptyStreamingPlaceholderReturnsResolutionError`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackMediaItemReuseIdentityIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest --console=plain
```

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P2 Delta - Streaming Header Restore Owner

After the next P2 URI/MediaItem ownership slice:

- Prepare-time streaming playback header restore now flows through
  `PlaybackMediaSourceProvider`. `EchoPlaybackService` no longer directly calls
  `streamingPlaybackHeaderStore.restoredTrackFor(track)` or
  `streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath)` in
  transition, mirrored-queue prepare, or single-track prepare paths.
- The queue-restore bridge also delegates its `StreamingRestoreProvider`
  callbacks to `PlaybackMediaSourceProvider`, keeping restored streaming track
  resolution next to headers, media item, URI validation, and cache-key policy.
- `StreamingPlaybackHeaderStore` is still injected into the service and passed
  to the media-source owner; this is a dependency wiring boundary, not service
  ownership of URI/header restore policy.
- Current `EchoPlaybackService.java`: 2160 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Behavior guards:
  `PlaybackMediaSourceProviderTest.restoredTrackForPreparationDelegatesToHeaderStore`,
  `PlaybackMediaSourceProviderTest.restoreHeadersForTrackDelegatesDataPathToHeaderStore`,
  and
  `PlaybackMediaSourceProviderTest.restoreHeadersForDataPathDelegatesToHeaderStore`.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackServiceUsesInjectableStreamingHeaderStore`
  and
  `MainActivityArchitectureContractTest.playbackMediaItemReuseIdentityIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest --console=plain
```

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3 Delta - Precache Cache-Key Policy Owner

After the first P3 cache/precache ownership slice:

- `PlaybackPrecacheManager.StateProvider` no longer exposes
  `isHttpUri(Uri)` or `cacheKeyForTrack(Track)`. Current-track, upcoming-track,
  and segmented precache now ask the injected `PlaybackMediaSourceProvider`
  directly for HTTP eligibility and playback cache keys.
- `EchoPlaybackService` no longer supplies cache-key or HTTP URI callbacks to
  `PlaybackPrecacheManager`; it still supplies live player state through
  `currentPlayerLoadsCacheKey(...)`, which remains a Media3 boundary concern.
- Existing delayed-precache cancellation tests now construct the same
  `PlaybackMediaSourceProvider` dependency shape used by production instead of
  keeping cache policy in a fake service state provider.
- Current `EchoPlaybackService.java`: 2150 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Current `PlaybackPrecacheManager.StateProvider`: 6 methods.
- Behavior guards:
  `PlaybackPrecacheManagerTest.releaseCancelsDelayedPrecacheCallbacksOwnedByManager`
  and
  `PlaybackPrecacheManagerTest.replacingCurrentPrecacheCancelsPreviousDelayedCallbacks`.
- Architecture guard:
  `MainActivityArchitectureContractTest.streamingPlaybackCacheUsesSegmentedConcurrentPrecache`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3 Delta - Visualization Cache Policy Owner

After the next P3 cache/precache ownership slice:

- `PlaybackVisualizationCacheManager` now holds the existing
  `PlaybackMediaSourceProvider` directly for HTTP eligibility, playback cache
  keys, continuous cached bytes, and cache data sources.
- `PlaybackVisualizationCacheManager.StateProvider` no longer exposes
  `isHttpUri(Uri)`, `cacheKeyForTrack(Track)`,
  `continuousCachedBytes(String)`, or `cacheDependencies()`. It now only
  receives runtime scheduling/current-track boundaries from the service.
- `EchoPlaybackService` no longer wires visualization cache-specific cache
  strategy callbacks or anonymous `PlaybackCacheDependencies`; those decisions
  live with the cache/media-source owner.
- Current `EchoPlaybackService.java`: 2126 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Current `PlaybackPrecacheManager.StateProvider`: 6 methods.
- Current `PlaybackVisualizationCacheManager.StateProvider`: 3 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackStartDefersHeavyVisualizationWork`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3 Delta - Visualization Analyzer Cache Policy Owner

After the next P3 cache/precache ownership slice:

- `PlaybackVisualizationAnalyzer` now holds the existing
  `PlaybackMediaSourceProvider` directly for streaming HTTP eligibility,
  playback cache keys, continuous cached bytes, cache data sources, and cached
  content length.
- `PlaybackVisualizationAnalyzer.StateProvider` no longer exposes
  `isHttpUri(Uri)`, `cacheDataSourceForTrack(Track)`,
  `mediaCacheKeyForTrack(Track)`, `continuousCachedBytes(String)`, or
  `contentLengthForCacheKey(String)`. It now only receives runtime visibility,
  buffered playback progress, and state publication callbacks from the service.
- `EchoPlaybackService` no longer wires visualization analyzer-specific cache
  strategy callbacks; waveform and spectrum generation ask the cache/media
  source owner for cache policy directly.
- Current `EchoPlaybackService.java`: 2101 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Current `PlaybackPrecacheManager.StateProvider`: 6 methods.
- Current `PlaybackVisualizationCacheManager.StateProvider`: 3 methods.
- Current `PlaybackVisualizationAnalyzer.StateProvider`: 3 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackStartDefersHeavyVisualizationWork`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3 Delta - Streaming URI Policy Owner

After the next P3 cache/streaming ownership slice:

- `PlaybackErrorRecoveryManager` now holds the existing
  `PlaybackMediaSourceProvider` directly for streaming HTTP eligibility before
  deciding whether a playback error should retry the current track.
- `PlaybackWifiLockManager` now holds the same media-source owner for streaming
  HTTP eligibility before acquiring the Wi-Fi lock.
- `EchoPlaybackService` no longer exposes private `isHttpUri(...)`,
  `cacheKeyForTrack(...)`, or `headersForTrack(...)` wrappers. The remaining
  mirrored Media3 reuse check asks `PlaybackMediaSourceProvider` directly for
  the playback cache key.
- Current `EchoPlaybackService.java`: 2079 lines.
- Current `PlaybackQueueManager.QueueProvider`: 1 method.
- Current `PlaybackPrecacheManager.StateProvider`: 6 methods.
- Current `PlaybackErrorRecoveryManager.Actions`: 8 methods.
- Current `PlaybackWifiLockManager.StreamingTrackProvider`: 1 method.
- Behavior guards:
  `PlaybackErrorRecoveryManagerTest` and `PlaybackWifiLockManagerTest`.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackErrorRecoveryIsOwnedOutsideEchoPlaybackService`,
  `MainActivityArchitectureContractTest.playbackWifiLockIsOwnedOutsideEchoPlaybackService`,
  and
  `MainActivityArchitectureContractTest.streamingPlaybackCacheUsesSegmentedConcurrentPrecache`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest --tests app.yukine.playback.PlaybackWifiLockManagerTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P4 Delta - Lyrics Notification Bridge Split

After the first P4 notification/lyrics bridge slice:

- `PlaybackLyricsManager.StateProvider` now only exposes playback/visibility
  state needed for lyrics decisions: notification-worthy state, app visibility,
  current track, playing, and preparing.
- Notification refresh actions moved to a dedicated
  `PlaybackLyricsManager.NotificationBridge`, separating lyrics state reads
  from notification/session side effects while preserving the existing service
  boundary callbacks.
- `EchoPlaybackService.setStatusBarLyricsEnabled(...)` still delegates only to
  the lyrics owner; session refresh and forced notification update remain owned
  by `PlaybackLyricsManager`.
- Current `EchoPlaybackService.java`: 2082 lines.
- Current `PlaybackLyricsManager.StateProvider`: 5 methods.
- Current `PlaybackLyricsManager.NotificationBridge`: 2 methods.
- Behavior guard:
  `PlaybackLyricsManagerTest.statusBarLyricsSettingChangeRefreshesSessionAndNotificationFromLyricsOwner`.
- Architecture guard:
  `MainActivityArchitectureContractTest.liveLyricsNotificationServiceKeepsOppoFluidCloudContract`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackLyricsManagerTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P4 Delta - Notification Artwork Bridge Split

After the next P4 notification bridge slice:

- `PlaybackNotificationArtworkManager.StateProvider` now only exposes the
  current track needed to decide whether a decoded artwork result still belongs
  to the active notification/session.
- Notification and MediaSession refresh actions moved to
  `PlaybackNotificationArtworkManager.NotificationBridge`, separating artwork
  state reads from notification/session side effects while preserving the
  existing service boundary callbacks.
- `EchoPlaybackService` still supplies the Android/Media3 boundary actions,
  but no longer exposes them through the artwork state provider.
- Current `EchoPlaybackService.java`: 2083 lines.
- Current `PlaybackNotificationArtworkManager.StateProvider`: 1 method.
- Current `PlaybackNotificationArtworkManager.NotificationBridge`: 2 methods.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackStartDefersHeavyVisualizationWork`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Shutdown Resource Boundary Split

After the first P5 concurrency/shutdown audit slice:

- `PlaybackShutdownCoordinator` no longer accepts eight raw `Runnable`
  constructor arguments. Its inputs are now split into:
  `PlaybackResources` for lyrics, Wi-Fi lock, and player release, and
  `ServiceResources` for noisy receiver unregister, scheduler shutdown,
  main-handler callback clearing, notification artwork release, and precache
  release.
- The shutdown order is still owned by `PlaybackShutdownCoordinator`:
  playback-only release runs lyrics, Wi-Fi lock, then player; full service
  teardown runs lyrics, noisy receiver unregister, task scheduler shutdown,
  main callback clearing, artwork release, precache release, Wi-Fi lock
  release, then player release.
- `EchoPlaybackService` still supplies Android/Media3 boundary actions, but it
  no longer describes the shutdown sequence through a long positional list of
  anonymous `Runnable`s.
- Current `EchoPlaybackService.java`: 2078 lines.
- Current `PlaybackShutdownCoordinator.PlaybackResources`: 3 methods.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 5 methods.
- Behavior guard:
  `PlaybackShutdownCoordinatorTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Notification Artwork Async Release Guard

After the next P5 concurrency/shutdown audit slice:

- `PlaybackNotificationArtworkManager` now owns invalidation of queued artwork
  decode results. `release()` increments an artwork generation before clearing
  caches and misses; queued async work checks the generation before decode,
  after decode, and before notification/session refresh.
- A queued artwork task from before service teardown can no longer repopulate
  `artworkCache` / `artworkDataCache` or call notification/session bridge
  callbacks after the manager has been released.
- The production path still uses the existing `Context.getMainExecutor()` and
  decode/encode logic. A package-private constructor exposes only executor,
  loader, and encoder seams for focused shutdown tests.
- Current `EchoPlaybackService.java`: 2078 lines.
- Current `PlaybackNotificationArtworkManager.java`: 247 lines.
- Behavior guard:
  `PlaybackNotificationArtworkManagerTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackStartDefersHeavyVisualizationWork`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationArtworkManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Visualization Cache Writer Shutdown

After the next P5 concurrency/shutdown audit slice:

- `PlaybackVisualizationCacheManager` now owns shutdown invalidation for queued
  and active visualization cache work. `release()` increments a cache
  generation and cancels every active visualization `CacheWriter` wrapper.
- Main-handler warmup callbacks and scheduled visualization cache tasks carry
  the generation they were created under; tasks from before release no longer
  create cache writers or continue cache work after service teardown.
- `PlaybackVisualizationCacheManager.StateProvider` no longer exposes the
  concrete `PlaybackTaskScheduler`; it receives a single
  `scheduleVisualizationCacheTask(Runnable)` boundary from the service.
- `PlaybackShutdownCoordinator.ServiceResources` now includes
  `releaseVisualizationCache()` after scheduler shutdown and main callback
  clearing, before artwork/precache release.
- Current `EchoPlaybackService.java`: 2085 lines.
- Current `PlaybackVisualizationCacheManager.java`: 151 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 6 methods.
- Behavior guards:
  `PlaybackVisualizationCacheManagerTest` and
  `PlaybackShutdownCoordinatorTest`.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackStartDefersHeavyVisualizationWork`
  and
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackVisualizationCacheManagerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Playback Task Scheduler Shutdown Baseline

After the next P5 concurrency/shutdown audit slice:

- `PlaybackTaskScheduler` now keeps its worker alive when one scheduled
  playback/cache/resolve task throws `RuntimeException`; later queued playback
  work can still run instead of silently losing the scheduler thread.
- Focused scheduler tests now pin the existing shutdown contract:
  `shutdownNow()` clears queued tasks, interrupts the worker, and ignores tasks
  scheduled after shutdown.
- This is a scheduler-owner hardening slice. `EchoPlaybackService` still only
  calls `shutdownNow()` through `PlaybackShutdownCoordinator.ServiceResources`.
- Current `EchoPlaybackService.java`: 2085 lines.
- Current `PlaybackTaskScheduler.java`: 102 lines.
- Behavior guard:
  `PlaybackTaskSchedulerTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackTaskSchedulerTest --tests app.yukine.playback.PlaybackVisualizationCacheManagerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Error Recovery Retry Cancellation

After the next P5 concurrency/shutdown audit slice:

- `PlaybackErrorRecoveryManager` now owns cancellation of its delayed streaming
  retry callback. `RetryScheduler` exposes `removeCallbacks`, and pending retry
  work is canceled and invalidated when playback becomes ready or when the
  manager is released.
- `PlaybackShutdownCoordinator.ServiceResources` now includes
  `releaseErrorRecovery()` after scheduler shutdown and before global main
  callback clearing, so the retry owner explicitly tears down its own callback
  instead of relying only on `mainHandler.removeCallbacksAndMessages(null)`.
- Current `EchoPlaybackService.java`: 2097 lines.
- Current `PlaybackErrorRecoveryManager.kt`: 83 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 7 methods.
- Behavior guards:
  `PlaybackErrorRecoveryManagerTest` and `PlaybackShutdownCoordinatorTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Progress Update Callback Release

After the next P5 concurrency/shutdown audit slice:

- `PlaybackProgressUpdateManager` already owns progress tick scheduling through
  `startIfNeeded()` and `stop()`. Service teardown now calls that owner
  explicitly instead of relying only on
  `mainHandler.removeCallbacksAndMessages(null)`.
- `PlaybackShutdownCoordinator.ServiceResources` now includes
  `releaseProgressUpdates()` after error recovery release and before global
  main callback clearing. Owner-specific callback cancellation stays ahead of
  the broad handler cleanup.
- Current `EchoPlaybackService.java`: 2102 lines.
- Current `PlaybackProgressUpdateManager.kt`: 50 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 8 methods.
- Behavior guards:
  `PlaybackProgressUpdateManagerTest` and
  `PlaybackShutdownCoordinatorTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackProgressUpdateManagerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Sleep Timer Callback Release

After the next P5 concurrency/shutdown audit slice:

- `PlaybackSleepTimerManager` already owns sleep timer scheduling through
  `startMinutes()` and `cancel(publish)`. Service teardown now cancels it
  explicitly with `cancel(false)`, preserving the existing silent shutdown
  behavior used by `stopAndClear()`.
- `PlaybackShutdownCoordinator.ServiceResources` now includes
  `releaseSleepTimer()` after progress update release and before global main
  callback clearing. Sleep timer callbacks are canceled by their owner before
  the service-wide handler cleanup runs.
- Current `EchoPlaybackService.java`: 2109 lines.
- Current `PlaybackSleepTimerManager.kt`: 76 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 9 methods.
- Behavior guards:
  `PlaybackSleepTimerManagerTest` and `PlaybackShutdownCoordinatorTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackSleepTimerManagerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Precache Release Late Task Guard

After the next P5 concurrency/shutdown audit slice:

- `PlaybackPrecacheManager.release()` now marks the owner as released before
  invalidating generation, clearing callbacks, canceling active cache writers,
  and shutting down its executor.
- Late `precacheTrack()`, delayed callback registration, executor submission,
  and generation checks now all stop when the precache owner has been released.
  This prevents service-destroy races from writing new streaming diagnostics or
  queueing callbacks after teardown.
- Current `EchoPlaybackService.java`: 2109 lines.
- Current `PlaybackPrecacheManager.java`: 679 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 9 methods.
- Behavior guard:
  `PlaybackPrecacheManagerTest.releasePreventsLatePrecacheDiagnosticsAndCallbacks`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - State Publisher Listener Release

After the next P5 concurrency/shutdown audit slice:

- `PlaybackStatePublisher` now owns listener teardown through `release()`.
  Releasing the publisher clears registered `PlaybackStateListener` instances
  and makes later state, notification, buffering, and registration calls no-op.
- `PlaybackShutdownCoordinator.ServiceResources` now includes
  `releaseStatePublisher()` after precache release and before wifi/player
  release. Service destroy no longer relies only on connection-side unregister
  to drop UI/state listeners.
- Current `EchoPlaybackService.java`: 2116 lines.
- Current `PlaybackStatePublisher.kt`: 84 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 10 methods.
- Behavior guard:
  `PlaybackStatePublisherTest.releaseClearsListenersAndStopsFutureCallbacks`.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackStateBroadcastsAreOwnedOutsideEchoPlaybackService`
  and
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackStatePublisherTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Warmup Fanout Release

After the next P5 concurrency/shutdown audit slice:

- `PlaybackWarmupCoordinator` now owns shutdown gating for playback warmup
  fanout. `release()` stops future warmup calls from dispatching precache or
  visualization cache work.
- `PlaybackShutdownCoordinator.ServiceResources` now includes
  `releaseWarmup()` after noisy receiver unregister and before scheduler
  shutdown. This stops the fanout entry point before downstream precache and
  visualization cache owners are released.
- Current `EchoPlaybackService.java`: 2123 lines.
- Current `PlaybackWarmupCoordinator.kt`: 23 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 11 methods.
- Behavior guards:
  `PlaybackWarmupCoordinatorTest.releaseStopsFutureWarmupFanout` and
  `PlaybackShutdownCoordinatorTest`.
- Architecture guard:
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackWarmupCoordinatorTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Visualization Analyzer Task Release

After the next P5 concurrency/shutdown audit slice:

- `PlaybackVisualizationAnalyzer` now owns shutdown gating for waveform and
  spectrum analysis. `release()` stops future snapshot generation, clears
  active generation keys, and prevents queued visualization tasks from
  publishing playback state after service teardown.
- `PlaybackShutdownCoordinator.ServiceResources` now includes
  `releaseVisualizationAnalyzer()` after warmup release and before scheduler
  shutdown. This invalidates analyzer task callbacks before the shared
  visualization scheduler is stopped.
- Current `EchoPlaybackService.java`: 2130 lines.
- Current `PlaybackVisualizationAnalyzer.kt`: 358 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 12 methods.
- Behavior guards:
  `PlaybackVisualizationAnalyzerTest.releaseStopsFutureSpectrumTaskScheduling`,
  `PlaybackVisualizationAnalyzerTest.releaseBeforeScheduledTaskPreventsPublishState`,
  and `PlaybackShutdownCoordinatorTest`.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackStartDefersHeavyVisualizationWork`
  and
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackVisualizationAnalyzerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Lyrics Owner Release Guard

After the next P5 concurrency/shutdown audit slice:

- `PlaybackLyricsManager.release()` now marks the lyrics owner as released
  before removing the floating lyrics listener and stopping the live lyrics
  notification service.
- Released lyrics owners no-op future status-bar lyrics setting changes,
  floating lyrics playback-state sync, listener callbacks, and notification
  lyric reads. This prevents service teardown races from restarting live lyrics
  notification work or rewriting floating lyrics state after destroy.
- Shutdown order is unchanged: `PlaybackShutdownCoordinator` still releases
  lyrics first through `PlaybackResources.releaseLyrics()`.
- Current `EchoPlaybackService.java`: 2130 lines.
- Current `PlaybackLyricsManager.kt`: 164 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 12 methods.
- Behavior guards:
  `PlaybackLyricsManagerTest.releaseStopsFutureStatusBarSettingRefreshes` and
  `PlaybackLyricsManagerTest.releaseStopsFutureFloatingLyricsSync`.
- Architecture guards:
  `MainActivityArchitectureContractTest.liveLyricsNotificationServiceKeepsOppoFluidCloudContract`
  and
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackLyricsManagerTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Crossfade Advance Release Owner

After the next P5 concurrency/shutdown audit slice:

- `PlaybackCrossfadeAdvanceManager` now owns fade-out advance scheduling,
  active runnable cancellation, fade-out transition flag updates, volume
  stepping, and final next-track handoff.
- `EchoPlaybackService` no longer keeps the crossfade `Handler` loop or
  crossfade timing constants. It delegates skip-to-next fade-out to the owner
  and only supplies Android/Media3 boundary actions such as player volume and
  queue advancement.
- `stopAndClear()` cancels an active crossfade without permanently releasing
  the owner. Service destroy releases the owner through
  `PlaybackShutdownCoordinator.ServiceResources.releaseCrossfade()` after the
  sleep timer and before global main-handler callback clearing.
- Current `EchoPlaybackService.java`: 2179 lines.
- Current `PlaybackCrossfadeAdvanceManager.kt`: 113 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 13 methods.
- Behavior guards:
  `PlaybackCrossfadeAdvanceManagerTest.fadeOutCompletesBySkippingNextAndRestoringVolume`,
  `PlaybackCrossfadeAdvanceManagerTest.releaseCancelsPendingFadeAndStopsFuturePlayerWrites`,
  and `PlaybackShutdownCoordinatorTest`.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackTransitionStateIsOwnedOutsideEchoPlaybackService`
  and
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackCrossfadeAdvanceManagerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P5 Delta - Current Playback Recovery Scheduler Owner

After the next P5 concurrency/shutdown audit slice:

- `PlaybackRecoveryScheduler` now owns current-playback recovery scheduling:
  background queue handoff, main-thread post, pending main callback removal,
  generation invalidation, and release guard.
- `EchoPlaybackService.replaceCurrentTrackAndResume()` still records streaming
  diagnostics and receives the recovery decision from `PlaybackQueueManager`,
  but no longer builds the `playbackTaskScheduler -> mainHandler.post ->
  prepareCurrent()` callback chain inline.
- Service destroy releases `PlaybackRecoveryScheduler` before shutting down
  the shared playback/visualization task schedulers, so queued recovery work
  cannot prepare the player after teardown.
- Current `EchoPlaybackService.java`: 2205 lines.
- Current `PlaybackRecoveryScheduler.kt`: 56 lines.
- Current `PlaybackShutdownCoordinator.ServiceResources`: 14 methods.
- Behavior guards:
  `PlaybackRecoverySchedulerTest.recoveryPostsPrepareToMainThread`,
  `PlaybackRecoverySchedulerTest.releaseBeforeBackgroundTaskRunsPreventsPreparePost`,
  `PlaybackRecoverySchedulerTest.releaseBeforeMainTaskRunsRemovesAndSuppressesPrepare`,
  and `PlaybackShutdownCoordinatorTest`.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackQueueCurrentTrackReplacementIsOwnedByQueueManager`
  and
  `MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRecoverySchedulerTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Settings Runtime Playback Controls Boundary

After the first P6 structure-dependency slice:

- `SettingsRuntimeApplier` no longer imports or accepts concrete
  `EchoPlaybackService`. Its factory now receives the existing
  `SettingsPlaybackServiceControlsProvider`, keeping runtime settings on the
  semantic controls interface.
- `MainSettingsPlaybackServiceControls` moved to
  `SettingsPlaybackServiceControlsAdapter.kt`, where the concrete service
  calls remain at the app/service boundary.
- `MainActivityBase` still owns the bound-service lookup, but it now adapts
  that lookup to `SettingsPlaybackServiceControls` before constructing the
  runtime applier.
- Current `SettingsRuntimeApplier.kt`: 187 lines.
- Current `SettingsPlaybackServiceControlsAdapter.kt`: 36 lines.
- Architecture guard:
  `MainActivityArchitectureContractTest` asserts the runtime applier does not
  mention `EchoPlaybackService` or own `MainSettingsPlaybackServiceControls`,
  while the boundary adapter owns the concrete service calls.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.SettingsRuntimeApplierTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - UI/ViewModel Playback Service Dependency Guard

After the next P6 structure-dependency slice:

- `MainActivityArchitectureContractTest.uiAndViewModelsDoNotDependOnConcretePlaybackService`
  now scans app `*ViewModel*` files, app UI/navigation sources, feature
  UI-common sources, and feature navigation sources for concrete
  `EchoPlaybackService` references.
- UI progress KDoc no longer names the concrete playback service class. It
  describes the dependency as the playback service boundary, matching the
  target `UI/ViewModel -> semantic command owner -> service boundary` shape.
- Concrete service references remain allowed in explicit boundary adapters
  such as `NowPlayingPlaybackGatewayAdapter` and
  `SettingsPlaybackServiceControlsAdapter`.
- Architecture guard:
  `MainActivityArchitectureContractTest.uiAndViewModelsDoNotDependOnConcretePlaybackService`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Runtime Player Settings Owner

After the next playback-owner slice:

- `PlaybackRuntimeStateManager` now owns the combined application of playback
  parameters and playback mode through
  `applyPlaybackModeAndParametersToPlayer()`.
- `EchoPlaybackService` still performs the Media3 boundary calls through its
  runtime-state owner, but no longer sequences the paired speed/volume and
  shuffle/repeat application inline when reusing a mirrored queue or creating
  the player.
- `PlaybackRuntimeStateManagerTest` uses a JVM `ExoPlayer` proxy to verify the
  owner applies speed, volume, shuffle, and repeat as one runtime snapshot.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Current Track Volume Owner

After the next runtime/player owner slice:

- `PlaybackRuntimeStateManager` now owns the effective current-track volume
  calculation through `currentTrackVolume()`, including app volume,
  replay-gain multiplier, and final normalization.
- `EchoPlaybackService` no longer computes
  `appVolume * replayGainMultiplierForTrack(currentTrack())` inside the
  crossfade wiring. The service asks the runtime owner for the base volume and
  triggers `applyCurrentTrackVolumeToPlayer()` on track transitions or
  crossfade restore.
- `PlaybackRuntimeStateManagerTest` covers replay-gain volume calculation and
  the player volume application path with the existing JVM `ExoPlayer` proxy.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Crossfade Queue-End Policy Owner

After the next crossfade owner slice:

- `PlaybackCrossfadeAdvanceManager` now owns the `REPEAT_OFF` plus
  end-of-queue decision that suppresses fade-out advance.
- `EchoPlaybackService` no longer combines repeat mode and queue position in
  the crossfade wiring. It only supplies `repeatMode()` and
  `isAtEndOfQueue()` snapshots to the crossfade owner.
- `PlaybackCrossfadeAdvanceManagerTest` covers the queue-end suppression path
  so future crossfade changes do not reintroduce the policy into the service.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackCrossfadeAdvanceManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Crossfade Fade Volume Owner

After the next crossfade owner slice:

- `PlaybackCrossfadeAdvanceManager` now clamps fade-out volume internally
  before writing it to the player.
- `EchoPlaybackService` no longer provides a `normalizedVolume()` callback for
  crossfade. It supplies the already-normalized base volume snapshot, while
  the crossfade owner owns fade-step volume safety.
- `PlaybackCrossfadeAdvanceManagerTest.fadeOutVolumeIsClampedByOwner` covers
  the clamp path.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackCrossfadeAdvanceManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Playback Completion Queue Action Owner

After the next queue-owner slice:

- `PlaybackQueueManager` now owns the post-completion queue decision through
  `playbackCompletionAction()`, returning whether completion should clear the
  queue, repeat the current track, stop at the end, or advance to the next
  track.
- `EchoPlaybackService.playAfterCompletion()` no longer combines queue
  emptiness, repeat mode, and end-of-queue state directly. It still performs
  the Android/Media3 boundary actions such as preparing, stopping, and
  advancing playback.
- `PlaybackQueueManagerTest` covers all four completion actions:
  `STOP_AND_CLEAR`, `REPEAT_CURRENT`, `STOP_AT_END`, and `ADVANCE_TO_NEXT`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Mirrored Transition Playback State Owner

After the next queue-position owner slice:

- `PlaybackQueueManager` now owns the playback state preparation after a
  mirrored MediaItem transition through `prepareMirroredTransitionPlaybackState()`.
- `EchoPlaybackService.onMediaItemTransition()` no longer directly persists the
  transition position, clears runtime/transition/restored state, resets the
  new current track position, or persists the queue on the normal transition
  path. It still owns the Android/Media3 boundary work: reading the player
  transition index, waveform/header updates, volume application, state publish,
  and progress updates.
- `PlaybackQueueManagerTest.prepareMirroredTransitionPlaybackStatePersistsAndResetsNewCurrentTrack`
  covers the owner-side ordering and cleanup contract.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Current Playback Position Persistence Owner

After the next queue-position owner slice:

- `PlaybackQueueManager` now exposes
  `persistCurrentPlaybackPosition(force)` as the service-facing entry point for
  saving the current track position.
- `EchoPlaybackService.persistPlaybackPositionThrottled()` no longer asks
  `PlaybackPositionManager` directly on the normal path. It delegates through
  the queue owner while preserving the fallback path for early construction.
- `PlaybackQueueManagerTest.persistCurrentPlaybackPositionUsesPositionOwnerThrottleAndForce`
  covers the owner-side throttle and force behavior.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Restored Position Query Owner

After the next queue-position owner slice:

- `PlaybackQueueManager` now exposes `restoredPositionFor(track)` as the
  service-facing restored-position query.
- `EchoPlaybackService.prepareCurrent()` still decides when to prepare the
  current item, but its normal path no longer asks `PlaybackPositionManager`
  directly for the start position.
- `PlaybackQueueManagerTest.restoredPositionForDelegatesThroughQueueOwner`
  covers the owner-side query contract, including duration clamping and the
  streaming implicit-restore suppression rule.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Restored Position Consumption Owner

After the next queue-position owner slice:

- `PlaybackQueueManager` now owns the post-prepare restored-position
  consumption rule through `consumeRestoredPositionAfterPrepare(startPositionMs)`.
- `EchoPlaybackService.prepareMirroredQueue()` and
  `EchoPlaybackService.prepareSingleTrack()` no longer directly decide when
  restored position should be cleared after a successful prepare. They still
  own the Android/Media3 player prepare, seek, warmup, publish, and
  notification boundary work.
- `PlaybackQueueManagerTest.consumeRestoredPositionAfterPrepareClearsOnlyWhenStartPositionWasUsed`
  covers the rule that `0L` keeps the restored position and a positive consumed
  start position clears it.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Stop-And-Clear Playback State Owner

After the next queue-owner slice:

- `PlaybackQueueManager` now owns the playback state cleanup for
  stop-and-clear through `prepareStopAndClearPlaybackState()`: playback
  position reset, runtime preparing/error reset, transition reset, queue clear
  and persistence, and resume-request clearing.
- `EchoPlaybackService.stopAndClear()` no longer directly mutates those queue
  and playback state owners on the normal path. It still owns Android/service
  boundary work: crossfade/sleep cancellation, playback resource release,
  progress updates, foreground state, publish, and `stopSelf()`.
- `PlaybackQueueManagerTest.prepareStopAndClearPlaybackStateClearsQueuePositionRuntimeAndResumeState`
  covers the owner-side cleanup contract.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Playback Completion State Preparation Owner

After the next queue-owner slice:

- `PlaybackQueueManager` now owns the state preparation that happens after a
  track completes but before the service performs the repeat, stop-at-end, or
  advance boundary action through `preparePlaybackCompletion(action)`.
- `EchoPlaybackService.playAfterCompletion()` no longer directly reads the
  completed track, saves its playback position at `0L`, or clears restored
  position for repeat-current on the normal path.
- `PlaybackQueueManagerTest` covers both completion preparation branches:
  repeat-current clears restored position, while advance only resets the
  completed track position.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Automatic Advance Stop Queue Position Owner

After the next queue-owner slice:

- `PlaybackQueueManager` now owns the queue-position cleanup for mirrored
  automatic advance that must stop at the end of the queue through
  `prepareStopAfterAutomaticAdvance(completedIndex)`.
- `EchoPlaybackService.stopAfterAutomaticAdvance()` no longer directly
  persists the current playback position, clamps the current index, or saves
  the completed track at `0L` on the normal path. It still enters
  `stopAtEndOfQueue()` for the Android/Media3 stop boundary.
- `PlaybackQueueManagerTest.prepareStopAfterAutomaticAdvancePersistsAndResetsCompletedTrackThroughOwner`
  covers the owner-side ordering: persist the completed track position first,
  then reset that completed track to `0L`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Stop-At-End Queue State Owner

After the next queue-owner slice:

- `PlaybackQueueManager` now owns the queue playback state cleanup needed when
  playback stops at the end of the queue through `prepareStopAtEndOfQueue()`.
- `EchoPlaybackService.stopAtEndOfQueue()` no longer directly clears restored
  position, runtime preparing/error state, transition marker, or resume
  request on the normal path. It still owns the Android/Media3 boundary work:
  progress updates, player pause/seek/create fallback, and state publication.
- `PlaybackQueueManagerTest.prepareStopAtEndOfQueueClearsQueuePlaybackStateThroughOwners`
  covers the owner-side cleanup contract.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Activity Reverse Dependency And Facade Guard

After the next P6 structure-dependency slice:

- `MainActivityArchitectureContractTest.playbackServiceAndOwnersDoNotDependOnActivityClasses`
  now guards `EchoPlaybackService` plus feature playback owners from depending
  on `MainActivity` or `MainActivityBase`.
- The widget provider remains an app/playback Android boundary and can open
  the launcher activity; the guard is scoped to the service and feature
  playback owners.
- `MainActivityArchitectureContractTest.rootPackageDoesNotAddPlaybackFacadeOrBroadCoordinatorFiles`
  now prevents root-package `*Facade*` files and keeps root-package
  `*Coordinator*` files limited to the existing `NetworkRenderCoordinator`.
  Playback-specific service facade/coordinator names are explicitly blocked.
- Architecture guards:
  `MainActivityArchitectureContractTest.playbackServiceDoesNotDependOnMainActivityClass`,
  `MainActivityArchitectureContractTest.playbackServiceAndOwnersDoNotDependOnActivityClasses`,
  and
  `MainActivityArchitectureContractTest.rootPackageDoesNotAddPlaybackFacadeOrBroadCoordinatorFiles`.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P6 Delta - Obsolete Service Position Helper Cleanup

After the next queue-owner cleanup slice:

- `EchoPlaybackService.resetCurrentPlaybackPosition()` was removed after all
  normal-path current-position reset calls moved behind `PlaybackQueueManager`.
- `PlaybackQueueManager` remains the only playback owner that directly calls
  `PlaybackPositionManager.resetCurrentPlaybackPosition()` for queue state
  transitions.
- `EchoPlaybackService.playAfterCompletion()` also dropped its unreachable
  null-manager save/clear fallback branch; when the queue owner is missing,
  completion already resolves to `STOP_AND_CLEAR` before owner preparation.
- The service still keeps fallback position helpers for manager construction
  or degraded paths, but no longer exposes a direct reset helper.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P2 Delta - Mirrored Queue MediaItem Match Owner

After the next URI/media-item owner slice:

- `PlaybackMediaSourceProvider` now owns MediaItem-to-Track reuse matching for
  mirrored queue checks through `mediaItemMatchesTrackForReuse(mediaItem,
  track)`.
- `EchoPlaybackService.mirroredQueueMatchesCurrentPlayer()` no longer computes
  the track cache key or passes URI/cache-key identity pieces directly; it only
  passes the current player item and queue track to the media source owner.
- `PlaybackMediaSourceProviderTest.providerMatchesMediaItemForTrackUsingOwnedCacheKeyRule`
  covers that the owner applies the streaming cache-key rule when matching.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3 Delta - Precache Current Player Match Owner

After the next cache/precache owner slice:

- `PlaybackPrecacheManager` no longer asks the service whether the current
  player already loads a specific cache key. Its `StateProvider` now exposes
  only the current `MediaItem` snapshot from the Android/Media3 boundary.
- `PlaybackPrecacheManager.currentPlayerLoadsTrack(track)` delegates
  MediaItem-to-Track matching to `PlaybackMediaSourceProvider`, keeping the
  URI/cache-key comparison rule with the media source owner.
- `EchoPlaybackService` no longer computes cache-key match policy for current
  leading-range precache suppression.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P3 Delta - Precache Upcoming Queue Owner

After the next cache/queue owner slice:

- `PlaybackQueueManager` now owns upcoming-track selection for precache through
  `upcomingTracksForPrecache(maxCount)`, including the repeat-off rule that
  stops at the queue end and the repeat-all wraparound rule that skips the
  current track.
- `PlaybackPrecacheManager` no longer computes upcoming queue indices from a
  service-provided queue snapshot, current index, and repeat mode. It asks the
  queue owner for candidate tracks, then keeps cache-specific URI/cache-key
  filtering locally.
- `EchoPlaybackService` no longer forwards queue snapshot, current index, or
  repeat mode into `PlaybackPrecacheManager.StateProvider`; the adapter only
  exposes the current track, current MediaItem boundary snapshot, and
  diagnostics.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P4 Delta - Notification Worthy State Owner

After the next notification-owner slice:

- `PlaybackNotificationManager` now owns the notification-worthy state rule
  through `hasNotificationWorthyState()`.
- `PlaybackNotificationManager.StateProvider` no longer asks the service for a
  precomputed notification policy result. It receives raw playback state:
  current track, queue empty, preparing, playing, and session token.
- `EchoPlaybackService` still owns lifecycle trigger points that decide when to
  request notification publication, but the notification manager now decides
  whether its own update should produce a foreground notification.
- `PlaybackNotificationManagerTest.notificationWorthyStateIsOwnedByNotificationManager`
  covers empty-state suppression and queue-backed notification publication.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Audit - QueueProvider Removed From Production Source

Current audit date: 2026-07-01.

- `PlaybackQueueManager.QueueProvider` is no longer present in production
  source. The previous service-state provider interface has been replaced by
  explicit constructor dependencies on `PlaybackQueueStore`, queue state,
  `PlaybackQueueManager.QueuePlaybackActions`, `PlaybackPositionManager`,
  `PlaybackQueueManager.StreamingRestoreProvider`,
  `PlaybackQueueManager.MirroredQueuePlayer`, runtime state, and transition
  state.
- Source scan result: `QueueProvider`, `PlaybackQueueManager.QueueProvider`,
  and `queueProvider.` appear only in historical docs and architecture
  contract assertions, not in `app/src/main/java/app/yukine/playback` or
  `feature/playback/src/main/java`.
- The active P1 contract is now
  `MainActivityArchitectureContractTest.playbackQueueManagerPublicApiStaysExplicitlyAudited`.
  It scans playback production sources to prevent the old provider from
  returning and pins the current `PlaybackQueueManager` class-level public API
  into four groups:
  queue mutation, queue restore/persistence, mirrored queue operations, and
  derived read APIs.
- Remaining P1 work should reduce or split the pinned `PlaybackQueueManager`
  public API by small behavior-preserving slices. Do not reintroduce a generic
  queue provider, and do not move queue policy back into `EchoPlaybackService`.

## P5 Delta - Service Lifecycle Shutdown Boundary

After the first shutdown/concurrency slice:

- `PlaybackShutdownCoordinator` now owns the task-removed lifecycle policy:
  persist playback position, persist queue, save the resume-requested flag from
  playing/preparing state, and publish a media notification only when the
  notification owner reports a worthy state.
- `PlaybackShutdownCoordinator` also owns the service-destroyed policy: persist
  the current playback position before running full service resource teardown.
- `EchoPlaybackService.onTaskRemoved()` is reduced to the Android callback
  boundary plus an early-init fallback, and `onDestroy()` now delegates the
  normal teardown path to the shutdown coordinator.
- `PlaybackShutdownCoordinatorTest.handleTaskRemovedPersistsResumeRequestFromPlaybackState`
  and `handleTaskRemovedPublishesNotificationOnlyWhenWorthy` cover the
  task-removed behavior.
- `PlaybackShutdownCoordinatorTest.handleServiceDestroyedPersistsPositionBeforeFullServiceTeardown`
  covers the destroy-time persist-before-release ordering.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P2/P3 Delta - Precache Track Identity Resolver

After the next resolver/cache-owner slice:

- `PlaybackMediaSourceProvider` now owns the resolved-URI track reuse rule
  through `tracksShareResolvedUriForReuse(current, candidate)`.
- `PlaybackPrecacheManager` no longer performs direct `contentUri.equals(...)`
  checks when deciding whether a requested precache target still matches the
  current track. It asks the media source provider for that identity decision.
- The slice preserves the existing resolved-URI behavior instead of changing
  precache identity to a stricter id/cache-key rule.
- `PlaybackMediaSourceProviderTest.providerMatchesTracksForReuseUsingResolvedUriRule`
  covers the owner-side rule.
- `PlaybackPrecacheManagerTest.resolvedUriMatchUsesCurrentTrackPrecachePath`
  covers the current-track precache path through the resolver-owned rule.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P2/P3 Delta - Visualization Cache Media Identity Resolver

After the next resolver/cache-owner slice:

- `PlaybackMediaSourceProvider` now owns the stricter track media-identity rule
  through `tracksShareMediaIdentityForReuse(current, candidate)`, which requires
  both track id and resolved URI to match.
- `PlaybackVisualizationCacheManager` no longer compares track id and
  `contentUri` directly before scheduling waveform/visualization cache work.
  It asks the media source provider whether the pending visualization target
  still matches the active track identity.
- Existing behavior is preserved: a shared resolved URI with a different track
  id is not enough to schedule visualization cache work.
- `PlaybackMediaSourceProviderTest.providerMatchesTrackMediaIdentityUsingIdAndResolvedUri`
  covers the resolver-owned rule.
- `PlaybackVisualizationCacheManagerTest.sameResolvedUriWithDifferentTrackIdDoesNotScheduleVisualizationCache`
  covers the manager-side scheduling guard.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackVisualizationCacheManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P2 Delta - MediaLibrary Playable URI Rule Owner

After the next MediaLibrary resolver slice:

- `PlaybackMediaSourceProvider` now owns the "track has a playable media URI"
  rule through `hasPlayableMediaUri(track)`.
- `PlaybackMediaLibraryCallback` no longer checks `track.contentUri` and
  `Uri.EMPTY` directly when exposing auto library items. It asks the media
  source provider whether the track can be represented as a playable media
  item.
- `PlaybackMediaSourceProviderTest` covers playable and empty-URI behavior
  through the resolver-owned rule.
- `PlaybackMediaLibraryCallbackTest.autoItemsForTracksUsesMediaSourcePlayableUriRule`
  covers the MediaLibrary auto list filtering path.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.manager.PlaybackMediaLibraryCallbackTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P2/P3 Delta - Visualization Analyzer Playable URI Rule

After the next visualization resolver slice:

- `PlaybackVisualizationAnalyzer` now asks
  `PlaybackMediaSourceProvider.hasPlayableMediaUri(track)` before scheduling
  spectrum generation for local or streaming tracks.
- The analyzer no longer owns the `contentUri == null` / `Uri.EMPTY` playable
  check for spectrum snapshots; that URI rule stays with the media source
  provider.
- `PlaybackVisualizationAnalyzerTest.emptyUriTrackDoesNotScheduleSpectrumTask`
  covers the analyzer-side scheduling guard.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackVisualizationAnalyzerTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P4 Delta - Lyrics Notification Worthy State Owner

After the next lyrics-bridge slice:

- `PlaybackLyricsManager` now owns the notification-worthy state rule for
  floating-lyrics-driven media notification refreshes through
  `hasNotificationWorthyState()`.
- `PlaybackLyricsManager.StateProvider` no longer asks the service for a
  precomputed notification policy result. It receives raw playback state:
  current track, queue empty, preparing, playing, and app visibility.
- `EchoPlaybackService` still owns lifecycle trigger points that decide when to
  request notification publication, but the lyrics bridge now decides whether a
  lyric update should request a media notification refresh.
- `PlaybackLyricsManagerTest.notificationWorthyStateIsOwnedByLyricsManager`
  covers the owner-side rule for an empty current track with a non-empty queue.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackLyricsManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P4 Delta - Service Notification Worthy Delegation

After the next notification boundary slice:

- `EchoPlaybackService.hasNotificationWorthyState()` now delegates to
  `PlaybackNotificationManager.hasNotificationWorthyState()` on the normal
  path, keeping only an early-initialization fallback.
- Service lifecycle trigger points (`onCreate`, `onStartCommand`,
  `onTaskRemoved`, app visibility) still decide when to request notification
  publication, but the notification-worthy policy is owned by the notification
  manager.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationManagerTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Audit - Queue Completion Boundary Narrowing

Current audit date: 2026-07-03.

- The stop/clear queue preparation merge candidate is complete; there is still
  no `PlaybackQueueStopClearOwner` production or test source.
- `PlaybackQueueCompletionOwner` no longer receives
  `PlaybackQueueManager.QueuePlaybackActions` from `EchoPlaybackService`.
  Repeat-current completion is handled inside `PlaybackQueueManager`; the
  completion owner only routes service-boundary actions: stop/clear,
  stop-at-end, and skip-next.
- `PlaybackQueueManager.PlaybackCompletionAction` now represents only actions
  that need service-boundary handling. Repeat-current remains an internal
  queue decision and returns no boundary action after preparing current
  playback.
- Batch metrics after this audit: `EchoPlaybackService.java` is 1422 lines,
  `private Playback*` field count is 47, `fromPlaybackQueueManager` count is
  0, `queueStateSnapshot` references in the service are 3, and
  `Playback*Owner` production file count is 43.
- Real reductions in this batch: one fewer
  `PlaybackQueueCompletionOwner` constructor parameter, one fewer
  `EchoPlaybackService` owner-wiring argument, and one fewer completion-owner
  strategy branch.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCompletionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Audit - Restored Position Boundary Narrowing

Current audit date: 2026-07-03.

- `PlaybackQueueManager` no longer exposes
  `consumeRestoredPositionAfterPrepare(startPositionMs)`. The method only
  consumed restored playback position after a successful player prepare, so it
  now belongs to `PlaybackPositionManager`.
- `PlaybackCurrentTrackPreparationQueueOwner` no longer forwards restored
  position cleanup through the queue manager. `EchoPlaybackService` still owns
  the Android/Media3 prepare success trigger, but delegates the restored
  position cleanup policy to the position owner.
- No new owner was added for this slice. The real reduction is one fewer
  queue-manager public method and one fewer queue-owner forwarding method.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1423 lines,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackPositionManagerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackCurrentTrackPreparationQueueOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Audit - Playback Position Persistence Boundary Narrowing

Current audit date: 2026-07-03.

- `PlaybackQueueManager` no longer exposes
  `persistCurrentPlaybackPosition(force)`. Service-bound current-position
  persistence now goes directly to `PlaybackPositionManager.persistCurrentPosition(force)`.
- `PlaybackQueuePersistenceOwner` no longer forwards current-position
  persistence through the queue manager. It remains responsible for queue state
  persistence and playback resume flags.
- No new owner or constructor parameter was added for this slice. The real
  reductions are one fewer queue-manager public method, one fewer
  queue-persistence owner method, and the shorter service path:
  `EchoPlaybackService -> PlaybackPositionManager`.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackPositionManagerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueuePersistenceOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1/P4 Boundary Note - Lyrics Current Track Source

Current audit date: 2026-07-03.

- `PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState(...)` no
  longer accepts a generic `Supplier<Track>` for current-track state. It now
  reads the current track from the existing `PlaybackQueueStateOwner`.
- This is not a P4 runtime lyrics/notification migration. The notification and
  lyrics trigger behavior is unchanged; the only boundary change is that the
  lyrics state owner uses the queue-state owner as the current-track source.
- No owner was added. The real reduction is one fewer generic current-track
  supplier chain entering lyrics state.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackLyricsStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Service Current Track Supplier Alias

Current audit date: 2026-07-03.

- `EchoPlaybackService` no longer creates a local
  `final Supplier<Track> currentTrackSupplier` alias during playback owner
  wiring.
- The remaining owners that still require a `Supplier<Track>` receive
  `playbackQueueStateOwner::currentTrack` directly. This avoids a service-local
  forwarding variable without changing notification artwork or wifi-lock
  behavior.
- No owner was added or removed. The real reduction is one fewer service wiring
  supplier alias.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1424 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Play Command Current Track Read

Current audit date: 2026-07-03.

- `EchoPlaybackService.play()` no longer reads
  `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` to decide
  whether to prepare the current queue track.
- The existing `PlaybackQueueCommandOwner` now owns the queue-current check for
  play preparation through owner-local `prepareCurrentIfAvailable(...)` and
  `hasCurrentTrack()` methods. `PlaybackQueueManager.QueuePlaybackActions`
  remains unchanged, so this does not widen the queue manager interface.
- No owner was added. The real reduction is one fewer direct Service
  queue-current-track read and one fewer Service branch that passes a raw
  `Track` into `prepareCurrent(...)` from `play()`.
- `PlaybackQueueCommandOwnerTest` now covers the true/false return paths for
  preparing the current queue track and the missing-current-track no-op path.
- Current metrics: `EchoPlaybackService.java` is 1422 lines,
  `private (final )?Playback` count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` production count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()`
  calls in the service are 2, direct `playbackQueueStateOwner.currentTrack()`
  calls in the service are 0, direct `playbackQueueStateOwner::currentTrack`
  references in the service are 2, and `Playback*Owner` production file count
  is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P2 Boundary Test - Media Library Item Factory Uses Provider

Current audit date: 2026-07-03.

- `PlaybackMediaLibraryDataSourceTest` now covers the real
  `PlaybackMediaLibraryDataSource.fromRepository(...) -> PlaybackMediaSourceProvider.mediaItemForTrack(...)`
  path for MediaLibrary media item creation.
- The test verifies that a streaming track keeps the provider-owned media id,
  resolved URI, streaming cache key, and caller metadata in the produced
  `MediaItem`.
- No owner, resolver facade, cache policy facade, or production wiring was
  added. The real gain is stronger behavior coverage for the app-layer
  MediaLibrary boundary that could otherwise bypass `PlaybackMediaSourceProvider`.
- Current metrics: `EchoPlaybackService.java` is 1422 lines,
  `private (final )?Playback` count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` production count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()`
  calls in the service are 2, direct `playbackQueueStateOwner.currentTrack()`
  calls in the service are 0, direct `playbackQueueStateOwner::currentTrack`
  references in the service are 2, and `Playback*Owner` production file count
  is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaLibraryDataSourceTest --console=plain
```

## P2/P3 Boundary Test - Media Cache Operations

Current audit date: 2026-07-03.

- Read-only boundary check found no `PlaybackMediaSourceResolutionOwner`
  production or test class, and architecture contracts already keep that
  resolution facade from returning.
- URI/media item resolution remains in `PlaybackMediaSourceProvider`.
  `PlaybackPrecacheManager` and `PlaybackVisualizationCacheManager` depend on
  `PlaybackMediaCacheOperations`, which adapts only cache-relevant provider
  operations.
- `PlaybackMediaCacheOperationsTest` now covers the provider-backed cache
  boundary in `feature:playback`: HTTP/streaming tracks produce the owned
  streaming cache key and headers, while local/content tracks return `null`
  for precache cache key.
- No production owner was added or removed. The real gain is stronger focused
  behavior coverage for the resolver/cache boundary, reducing the chance that
  cache policy drifts into `EchoPlaybackService` or a broad resolution owner.
- Current batch metrics: `EchoPlaybackService.java` is 1428 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, and `Playback*Owner` production file count is 44.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaCacheOperationsTest --console=plain
```

- T1 verification passed for the current playback batch:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaCacheOperationsTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## Batch Verification - Queue State And Cache Boundary Slices

Current audit date: 2026-07-03.

- This checkpoint covers the recent small slices:
  `EchoPlaybackService.play()` current-track reuse,
  `PlaybackQueueMirroredTransitionOwner` queue-state source, and
  `PlaybackMediaCacheOperations` provider-backed cache boundary coverage.
- Batch gains:
  - One fewer repeated service current-track snapshot read during a single
    `play()` command.
  - One fewer app owner directly reading
    `PlaybackQueueManager.queueStateSnapshot()`.
  - Stronger focused behavior coverage for HTTP/streaming versus local/content
    precache cache-key policy.
- Batch metrics: `EchoPlaybackService.java` is 1428 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  4, and `Playback*Owner` production file count is 44.
- Focused and T1 verification already passed for this batch:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMirroredTransitionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaCacheOperationsTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaCacheOperationsTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

- T2 verification passed:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

- Next safe candidates remain queue/state/interface focused: reduce the
  remaining service queue snapshot supplier only outside notification scope, or
  continue `PlaybackQueueManager` public API grouping for real migration
  leftovers. P4/P5 notification, lyrics, shutdown, and background playback stay
  deferred until smoke evidence is stable.

## P1 Wiring Note - Queue Mutation Empty State Source

Current audit date: 2026-07-03.

- `PlaybackQueueMutationOwner` still uses `PlaybackQueueManager` for queue
  mutation commands such as play, append, remove, retain, move, and replace.
- `clearQueue()` now reads empty-queue state through the existing
  `PlaybackQueueStateOwner` instead of directly calling
  `PlaybackQueueManager.queueStateSnapshot()`.
- No owner was added. This adds one constructor argument to an existing owner,
  so it should not be counted as service wiring reduction. The real reduction
  is one fewer app owner directly reading
  `PlaybackQueueManager.queueStateSnapshot()`.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1430 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  4, and direct `playbackQueueManager.queueStateSnapshot()` calls inside
  `PlaybackQueueMutationOwner` are 0.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Mirrored Transition Queue State Source

Current audit date: 2026-07-03.

- `PlaybackQueueManager` public API audit split the current surface into:
  command methods such as `playQueue`, `appendToQueue`, navigation, mutation,
  completion, and mirrored-transition commands; restore/persistence methods
  such as `restorePlaybackQueue`, `restoreLastPlayback`, and
  `persistQueueState`; and state reads such as `queueSnapshot`,
  `queueStateSnapshot`, and `upcomingTracksForPrecache`.
- `PlaybackQueueMirroredTransitionOwner` still uses `PlaybackQueueManager` for
  the mirrored-transition command `applyMirroredTransitionIndex(...)`, but its
  empty-queue state read now goes through the existing
  `PlaybackQueueStateOwner`.
- No owner was added. This does add one constructor argument to an existing
  owner, so it should not be counted as service wiring reduction. The real
  reduction is one fewer app owner directly reading
  `PlaybackQueueManager.queueStateSnapshot()`.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1428 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, and direct `playbackQueueManager.queueStateSnapshot()` calls inside
  `PlaybackQueueMirroredTransitionOwner` are 0.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMirroredTransitionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Interface Audit - Queue Manager Inputs

Current audit date: 2026-07-03.

- This is an interface-only audit checkpoint after the recent queue snapshot
  and current-track wiring slices. No production owner was added or deleted.
- `PlaybackQueueManager.QueueProvider` remains absent from production code, and
  production `fromPlaybackQueueManager(...)` factories remain at 0.
- `MainActivityArchitectureContractTest` now treats the remaining
  `PlaybackQueueManager` nested interfaces as an audited external-input set:
  `QueuePlaybackActions`, `StreamingRestoreProvider`, and
  `MirroredQueuePlayer`.
- The contract fixes each interface width:
  `QueuePlaybackActions` can only prepare current playback and publish state,
  `StreamingRestoreProvider` can only restore a track for playback, and
  `MirroredQueuePlayer` can only match/seek the mirrored queue.
- This closes the P1 audit gap where Service direct queue calls had shrunk but
  interface width could still grow by moving calls behind new provider-style
  methods.
- Current metrics: `EchoPlaybackService.java` is 1425 lines,
  `private (final )?Playback` count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` production count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  0, direct `playbackQueueStateOwner::currentTrack` references in the service
  are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Runtime And Position Current Track Suppliers

Current audit date: 2026-07-03.

- `EchoPlaybackService` no longer wires `playbackQueueStateOwner::currentTrack`
  into `PlaybackRuntimeStateManager.stateProviderFromPlaybackState(...)` or
  `PlaybackPositionManager.stateProviderFromPlaybackState(...)`.
- Both suppliers now explicitly read
  `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()`, keeping the
  source state path visible as `PlaybackQueueStateOwner -> PlaybackQueueManager`
  instead of another derivable current-track method reference.
- No owner was added or deleted. The real reduction is two fewer
  current-track forwarding method references in Service wiring, without
  touching notification, lyrics, shutdown, or background playback behavior.
- The remaining `playbackQueueStateOwner::currentTrack` references in
  `EchoPlaybackService` are notification artwork and Wi-Fi lock wiring. They
  stay deferred because they are P4/P5-adjacent runtime boundaries.
- `MainActivityArchitectureContractTest` now guards the runtime and position
  wiring against reintroducing the old method references, and keeps the
  remaining Service method-reference count at 2.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private (final )?Playback` count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  0, direct `playbackQueueStateOwner::currentTrack` references in the service
  are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackPositionManagerTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Interface Note - Queue State Snapshot Current Track Reads

Current audit date: 2026-07-03.

- `PlaybackFavoriteCommandOwner`, `PlaybackQueueCommandOwner`,
  `PlaybackPlayHistoryRecorder`, and `PlaybackSessionCommandOwner` now read
  the current track from `PlaybackQueueStateOwner.queueStateSnapshot()` instead
  of the derivable `PlaybackQueueStateOwner.currentTrack()` convenience method.
- No owner was added. The real reduction is four fewer non-P4 command/history
  paths depending on the derivable current-track shortcut, which keeps P1
  moving toward a narrower queue state contract rather than just moving Service
  calls.
- `MainActivityArchitectureContractTest` now guards those owners against
  returning to `queueStateOwner.currentTrack()`.
- Remaining `queueStateOwner.currentTrack()` production calls are deliberately
  left in P3/P4-adjacent cache, visualization, lyrics, and mirrored-player
  paths. The remaining direct Service `playbackQueueStateOwner.currentTrack()`
  call is in `play()`, which still owns Media3 player, prepare, resume, Wi-Fi
  lock, state publish, and progress-update boundary behavior; moving it now
  risks creating a broad play facade.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  1, and recursive `Playback*Owner` production file count is 43 by the current
  `Get-ChildItem` metric.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackFavoriteCommandOwnerTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.playback.PlaybackPlayHistoryRecorderTest --tests app.yukine.playback.PlaybackSessionCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Interface Note - Mirrored Player Current Track Snapshot

Current audit date: 2026-07-03.

- `PlaybackQueueMirroredPlayerOwner` now reads the current track from
  `PlaybackQueueStateOwner.queueStateSnapshot()` instead of the derivable
  `PlaybackQueueStateOwner.currentTrack()` convenience method before resetting
  waveform state during mirrored queue seek reuse.
- No owner was added and no Service wiring changed. The real reduction is one
  fewer P1 mirrored-queue owner path depending on the current-track shortcut.
- `MainActivityArchitectureContractTest` now guards
  `PlaybackQueueMirroredPlayerOwner` against returning to
  `queueStateOwner.currentTrack()`.
- Remaining `queueStateOwner.currentTrack()` production calls are in
  `PlaybackLyricsStateOwner`, `PlaybackPrecacheStateOwner`, and
  `PlaybackVisualizationCacheStateOwner`, which are P3/P4-adjacent and should
  be handled only with their focused smoke/test coverage.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  1, and recursive `Playback*Owner` production file count is 43 by the current
  `Get-ChildItem` metric.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMirroredPlayerOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P3 Interface Note - Cache State Current Track Snapshots

Current audit date: 2026-07-03.

- `PlaybackPrecacheStateOwner` and `PlaybackVisualizationCacheStateOwner` now
  read current track state from `PlaybackQueueStateOwner.queueStateSnapshot()`
  instead of the derivable `PlaybackQueueStateOwner.currentTrack()`
  convenience method.
- No owner, supplier, resolver, or cache policy was added. The real reduction
  is two fewer cache-adjacent state-provider paths depending on the
  current-track shortcut while keeping URI/MediaItem resolution in
  `PlaybackMediaSourceProvider` and cache policy in `PlaybackPrecacheManager`
  / `PlaybackVisualizationCacheManager`.
- `MainActivityArchitectureContractTest` now guards both state owners against
  returning to `queueStateOwner.currentTrack()`.
- Remaining production `queueStateOwner.currentTrack()` use is only in
  `PlaybackLyricsStateOwner`, which is P4-adjacent and should wait for lyrics
  smoke coverage. The remaining direct Service
  `playbackQueueStateOwner.currentTrack()` call is still `play()` and remains
  deferred to avoid creating a broad play facade around Media3/player
  boundary behavior.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  1, and recursive `Playback*Owner` production file count is 43 by the current
  `Get-ChildItem` metric.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheStateOwnerTest --tests app.yukine.playback.PlaybackVisualizationCacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1/P3 Batch Audit - Snapshot Reads And Resolver Cache Boundary

Current audit date: 2026-07-03.

- Batch covered the mirrored-player and cache-adjacent current-track read
  slices:
  - `PlaybackQueueMirroredPlayerOwner` now reads current track state through
    `PlaybackQueueStateOwner.queueStateSnapshot()`.
  - `PlaybackPrecacheStateOwner` and
    `PlaybackVisualizationCacheStateOwner` now read current track state through
    `PlaybackQueueStateOwner.queueStateSnapshot()`.
- Batch reductions:
  - Three fewer production owner paths depend on the derivable
    `PlaybackQueueStateOwner.currentTrack()` shortcut.
  - No owner, facade, supplier chain, Service field, or Service strategy branch
    was added.
- Resolver/cache boundary audit:
  - `MainActivityArchitectureContractTest` already rejects
    `PlaybackMediaSourceResolutionOwner` files and Service references.
  - `PlaybackMediaSourceProvider` remains the URI / MediaItem owner.
  - `PlaybackPrecacheManager` and `PlaybackVisualizationCacheManager` receive
    `PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider)`
    and do not store `PlaybackMediaSourceProvider` fields directly.
  - Service still enters cache policy through
    `PlaybackPrecacheManager.fromMediaSourceProvider(...)`; it does not call
    `PlaybackMediaCacheOperations.fromMediaSourceProvider(...)` directly.
- Remaining production `queueStateOwner.currentTrack()` use is only in
  `PlaybackLyricsStateOwner`, which stays P4-adjacent. The remaining direct
  Service `playbackQueueStateOwner.currentTrack()` read remains in `play()`,
  still deferred to avoid creating a broad play facade around Media3/player
  boundary behavior.
- Batch metrics after this audit: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  1, and recursive `Playback*Owner` production file count is 43 by the current
  `Get-ChildItem` metric.
- T1 verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMirroredPlayerOwnerTest --tests app.yukine.playback.PlaybackPrecacheStateOwnerTest --tests app.yukine.playback.PlaybackVisualizationCacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Interface Note - Service Play Current Track Snapshot

Current audit date: 2026-07-03.

- `EchoPlaybackService.play()` now reads the current track through
  `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` instead of
  the derivable `playbackQueueStateOwner.currentTrack()` shortcut.
- No play owner, facade, Service field, or playback strategy branch was added.
  The Service still owns the Android/Media3 boundary work in `play()`:
  player existence, preparation, player resume, Wi-Fi lock, state publish, and
  progress updates.
- Stop/clear queue preparation was re-audited before this slice. It is already
  owned by `PlaybackQueueCompletionOwner` and `PlaybackQueueManager`, covered
  by `PlaybackQueueCompletionOwnerTest`, and guarded by
  `MainActivityArchitectureContractTest` against
  `PlaybackQueueStopClearOwner` and `fromPlaybackQueueManager(...)` factories
  returning.
- The real reduction is the final direct Service
  `playbackQueueStateOwner.currentTrack()` call dropping from 1 to 0.
  Remaining production `queueStateOwner.currentTrack()` use is only in
  `PlaybackLyricsStateOwner`, which remains P4-adjacent.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  0, and recursive `Playback*Owner` production file count is 43 by the current
  `Get-ChildItem` metric.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Play Command Current Track Reuse

Current audit date: 2026-07-03.

- The `PlaybackQueueStopClearOwner` candidate was not continued: the class no
  longer exists, `fromPlaybackQueueManager` is already 0, and stop/clear
  playback-state preparation is already owned by `PlaybackQueueCompletionOwner`
  and `PlaybackQueueManager`.
- `EchoPlaybackService.play()` now reads
  `playbackQueueStateOwner.currentTrack()` once and passes the resolved track
  into a private `prepareCurrent(track, playWhenReady)` overload for the
  immediate prepare paths.
- No owner was added and no public playback command changed. The real reduction
  is one fewer repeated service current-track snapshot read during a single
  play command.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1427 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, and direct `playbackQueueStateOwner.currentTrack()` calls in the service
  are 4.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## Batch Verification - P1 Command And Queue State Slices

Current audit date: 2026-07-03.

- This checkpoint covers the recent queue/current-track wiring and command-owner
  slices: `PlaybackStateSnapshotOwner` queue-state source, service queue
  snapshot alias removal, `PlaybackPositionManager` current-track adapter,
  `PlaybackRuntimeStateManager` current-track adapter,
  `PlaybackQueueManager` API audit, mutation/resolver-cache boundary delta,
  empty queue clear no-op coverage, and `PlaybackFavoriteCommandOwner`.
- Batch metrics: `EchoPlaybackService.java` is 1422 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  5, and `Playback*Owner` production file count is 44.
- Focused tests run in this batch:
  - `PlaybackStateSnapshotOwnerTest` plus
    `MainActivityArchitectureContractTest`.
  - `PlaybackPositionManagerTest` plus
    `MainActivityArchitectureContractTest`.
  - `PlaybackRuntimeStateManagerTest` plus
    `MainActivityArchitectureContractTest`.
  - `PlaybackQueueMutationOwnerTest`.
  - `PlaybackFavoriteCommandOwnerTest` plus
    `MainActivityArchitectureContractTest`.
- T2 verification passed:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

- Next audit constraints:
  - Do not treat a new owner as progress unless it removes a state source,
    interface method, forwarding chain, service policy branch, or adds focused
    behavior coverage.
  - Keep auditing `PlaybackQueueManager` and queue snapshot APIs for real
    external inputs, derivable values, and migration leftovers so queue work
    does not become call-site relocation only.
  - Track `EchoPlaybackService` wiring pressure, especially field count,
    initialization length, and supplier/factory count, so line-count reduction
    does not become manager/owner accumulation.

## P1 Owner/Interface Audit - PlaybackQueueManager API

Current audit date: 2026-07-03.

- Stop/clear preparation merge status: complete before this audit. There is no
  `PlaybackQueueStopClearOwner` production or test file, and
  `EchoPlaybackService.stopAndClear()` routes preparation through
  `withPlaybackQueueCompletionOwner(PlaybackQueueCompletionOwner::prepareStopAndClearPlaybackState)`.
- True queue commands still owned by `PlaybackQueueManager`: `playQueue`,
  `appendToQueue`, `playFirstQueuedTrack`, `skipToNextImmediately`,
  `skipToPrevious`, `moveQueueTrack`, `removeTracksById`, `retainTracksById`,
  `replaceQueuedTrackById`, `replaceCurrentTrackAndResume`,
  `replaceCurrentQueueTrack`, `reuseMirroredQueueIfAvailable`, and
  `applyMirroredTransitionIndex`.
- Completion and stop-state commands: `preparePlaybackCompletionAction`,
  `prepareStopAtEndOfQueue`, `prepareStopAfterAutomaticAdvance`, and
  `prepareStopAndClearPlaybackState`.
- Persistence/restore inputs: `setPlaybackRestoreEnabled`,
  `persistQueueState`, `restorePlaybackQueue`, and `restoreLastPlayback`.
- Read/snapshot outputs: `queuePreparationForNewPlayer`, `queueSnapshot`,
  `queueStateSnapshot`, and `upcomingTracksForPrecache`.
- Derived state should continue to come from `QueueStateSnapshot` rather than
  new public booleans. Current snapshot source values remain `currentTrack`,
  `currentIndex`, and `queueSize`.
- Next low-risk candidate: audit `PlaybackQueueMutationOwner.clearQueue()`,
  which currently reads `playbackQueueManager.queueStateSnapshot().isQueueEmpty()`
  only to avoid `stopAndClear()` on an empty queue. Do not add a new
  `QueueManager` boolean just for this; either preserve the behavior with a
  narrower existing state owner or leave it until a mutation-owner slice can
  prove the stop/clear behavior with focused tests.
- Boundary risk left for later: notification state remains the only direct
  service wiring consumer of `playbackQueueStateOwner::queueStateSnapshot`;
  avoid changing it until a notification-focused smoke slice is justified.
- Verification for this audit was read-only evidence plus diff hygiene:

```powershell
codegraph explore "PlaybackQueueManager public methods QueueStateSnapshot QueuePreparation RestorePlaybackResult PlaybackCompletionAction callers tests API audit"
rg -n "^    fun |^    (data class|enum class|interface) " feature/playback/src/main/java/app/yukine/playback/manager/PlaybackQueueManager.kt
rg -n "playbackQueueManager\.([A-Za-z0-9_]+)\(" app/src/main/java/app/yukine/playback app/src/test/java/app/yukine/playback feature/playback/src/test/java/app/yukine/playback
git diff --check
```

## P1 Wiring Note - Runtime Current Track Adapter

Current audit date: 2026-07-03.

- `PlaybackRuntimeStateManager.stateProviderFromPlaybackState(...)` no longer
  accepts a `Supplier<PlaybackQueueManager.QueueStateSnapshot?>`.
- It now accepts a `Supplier<Track?>`, matching the only queue value runtime
  state needs for replay-gain volume calculation.
- `EchoPlaybackService` now passes `playbackQueueStateOwner::currentTrack` to
  runtime state wiring. Notification state is now the only direct service
  consumer of `playbackQueueStateOwner::queueStateSnapshot`.
- No owner was added. The real reduction is one narrower feature adapter input
  and one fewer direct service queue snapshot method reference.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1421 lines,
  `private (final )?Playback` count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `playbackQueueStateOwner::queueStateSnapshot`
  references in the service are 1, service `queueStateSupplier` alias
  references are 0, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Position Current Track Adapter

Current audit date: 2026-07-03.

- `PlaybackPositionManager.stateProviderFromPlaybackState(...)` no longer
  accepts a `Supplier<PlaybackQueueManager.QueueStateSnapshot?>`.
- It now accepts a `Supplier<Track?>`, matching the only queue value the
  position manager needs to persist or reset playback position.
- `EchoPlaybackService` now passes `playbackQueueStateOwner::currentTrack` to
  position state wiring. Runtime state and notification remain the only direct
  service consumers of `playbackQueueStateOwner::queueStateSnapshot`.
- No owner was added. The real reduction is one narrower feature adapter input
  and one fewer direct service queue snapshot method reference.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1421 lines,
  `private (final )?Playback` count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `playbackQueueStateOwner::queueStateSnapshot`
  references in the service are 2, service `queueStateSupplier` alias
  references are 0, `playbackQueueStateOwner::currentTrack` method-reference
  count in the service is 3, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackPositionManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P2/P3 Boundary Audit - Resolver And Precache Owners

Current audit date: 2026-07-03.

- This is a read-only boundary checkpoint after the last playback wiring
  slices. No playback behavior or production code changed in this checkpoint.
- `PlaybackMediaSourceProvider` remains the owner for playable URI and
  MediaItem resolution, restored streaming headers, MediaItem-to-track reuse,
  resolved URI track reuse, media identity reuse, and media/cache key
  derivation. Do not move these rules into a generic
  `PlaybackMediaSourceResolutionOwner`.
- No production `PlaybackMediaSourceResolutionOwner` exists. Existing
  `PlaybackWarmupCoordinator` and `PlaybackShutdownCoordinator` are outside
  the resolver/cache boundary and should be audited separately before any
  rename, merge, or deletion.
- `PlaybackPrecacheManager` remains the owner for cache scheduling and cache
  policy: delayed current-track precache, upcoming-track precache, segmented
  precache probing, generation checks, executor shutdown, active writer
  cancellation, and "keep existing precache" decisions.
- `PlaybackMediaCacheOperations` is intentionally narrow. It exposes cache
  operations needed by precache and visualization cache users:
  resolved-URI reuse, content length, precache cache key, request headers,
  cached byte ranges, and cache data sources. It should not grow into a
  MediaItem/URI resolver facade.
- Current `PlaybackPrecacheManager.StateProvider`: 4 methods
  (`currentTrack`, `currentPlayerMediaItem`, `upcomingTracksForPrecache`, and
  `streamingDiagnostics`). Its remaining width is playback state, not cache
  policy.
- Current risk to watch: `PlaybackPrecacheManager.fromMediaSourceProvider(...)`
  still injects `PlaybackMediaSourceProvider.mediaItemMatchesTrackForReuse(...)`
  and `releaseAudioCache()` through constructor callbacks. A future slice may
  reduce that wiring only if it removes a real supplier/constructor chain
  without moving MediaItem identity rules into `PlaybackMediaCacheOperations`.
- `PlaybackQueueManager.QueueProvider` remains absent in production playback
  code. The next P1 audit should continue from queue snapshot/service wiring
  and classify remaining queue-derived values as source input, snapshot-derived
  value, or migration residue.
- Batch metrics after this audit: `EchoPlaybackService.java` is 1424 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, and `Playback*Owner` production file count is 43.
- Focused tests already protecting this boundary include
  `PlaybackMediaSourceProviderTest`, `PlaybackPrecacheManagerTest`,
  `PlaybackVisualizationCacheManagerTest`, `PlaybackMediaCacheOperationsTest`,
  and `PlaybackMediaLibraryCallbackTest`.
- Verification for this checkpoint was read-only audit plus doc validation:

```powershell
codegraph explore "PlaybackMediaSourceProvider PlaybackPrecacheManager PlaybackMediaCacheOperations PlaybackMediaSourceResolutionOwner resolver cache owner boundary"
rg -n "PlaybackMediaSourceResolutionOwner|ResolutionOwner|Facade|Coordinator" app/src/main/java feature/playback/src/main/java
rg -n "interface QueueProvider|class QueueProvider|QueueProvider" app/src/main/java feature/playback/src/main/java feature/playback/src/test app/src/test/java/app/yukine/playback
git diff --check
```

## P1/P2 Boundary Delta - Mutation And Resolver Cache

Current audit date: 2026-07-03.

- `PlaybackQueueMutationOwner.clearQueue()` remains intentionally unchanged in
  this checkpoint. It reads `playbackQueueManager.queueStateSnapshot().isQueueEmpty()`
  only to avoid running `stopAndClear()` for an already-empty queue.
- Do not add a public `PlaybackQueueManager` boolean such as `hasQueueItems()`
  just to hide this read; that would grow the queue API and move derived state
  instead of narrowing it. Also do not add `PlaybackQueueStateOwner` to
  `PlaybackQueueMutationOwner` unless a focused mutation slice proves the
  added constructor wiring is offset by a real behavior or interface reduction.
- Current low-risk path for this area is test-first: strengthen
  `PlaybackQueueMutationOwnerTest` around empty-queue clear behavior before
  changing the owner. If the behavior should remain a no-op, leave the read in
  place until a better command-level owner shape exists.
- Resolver/cache boundary remains stable after the runtime/position adapter
  slices. Production code still has no `PlaybackMediaSourceResolutionOwner` or
  `Playback*ResolutionOwner` file, and architecture contracts assert that these
  files stay absent.
- `PlaybackMediaSourceProvider` is still the URI/MediaItem/cache-key owner.
  `PlaybackPrecacheManager` still owns cache scheduling and cache policy.
  `PlaybackMediaCacheOperations` remains a cache operations adapter and should
  not grow MediaItem or URI resolution methods.
- Current focused coverage for this boundary includes
  `PlaybackMediaSourceProviderTest`, `PlaybackPrecacheManagerTest`,
  `PlaybackMediaCacheOperationsTest`, `PlaybackVisualizationCacheManagerTest`,
  and `PlaybackPrecacheStateOwnerTest`.
- Current metrics after the latest wiring slices: `EchoPlaybackService.java`
  is 1421 lines, `private (final )?Playback` count is 55,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, and `Playback*Owner` production file count is 43.
- Verification for this checkpoint was read-only evidence plus diff hygiene:

```powershell
codegraph explore "PlaybackQueueMutationOwner clearQueue PlaybackQueueMutationOwnerTest EchoPlaybackService clearQueue queueStateSnapshot isQueueEmpty stopAndClearAction"
codegraph explore "PlaybackMediaSourceProvider PlaybackMediaSourceResolutionOwner PlaybackPrecacheManager PlaybackPrecacheStateOwner cache policy media item uri resolver EchoPlaybackService tests"
rg -n "PlaybackMediaSourceResolutionOwner|ResolutionOwner|MediaSourceResolution" app/src/main/java feature/playback/src/main/java app/src/test/java feature/playback/src/test/java docs
git diff --check
```

## P1 Command Note - Favorite Toggle Owner

Current audit date: 2026-07-03.

- `EchoPlaybackService.toggleCurrentFavorite()` no longer reads the current
  track and directly decides whether to call `ToggleFavoriteUseCase.toggle(...)`
  and `publishState()`.
- `PlaybackFavoriteCommandOwner` now owns that small command behavior:
  current queue track lookup, favorite toggle delegation, and state publish on
  a handled toggle.
- This intentionally adds one `Playback*Owner` production file, but it does
  not add an `EchoPlaybackService` field. The owner is not pure forwarding; it
  removes one Service strategy branch and has focused behavior tests for
  current-track, missing-track, and missing-use-case paths.
- Notification favorite state remains unchanged: `PlaybackNotificationStateOwner`
  still asks the injected `ToggleFavoriteUseCase` whether the supplied track is
  favorite.
- Metrics after this slice: `EchoPlaybackService.java` is 1422 lines,
  `private (final )?Playback` count is 55, `fromPlaybackQueueManager` count is
  0, direct `playbackQueueStateOwner::queueStateSnapshot` references in the
  service are 1, direct `playbackQueueStateOwner.currentTrack()` calls in the
  service are 5, and `Playback*Owner` production file count is 44.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackFavoriteCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Error Recovery Queue State Source

Current audit date: 2026-07-03.

- `PlaybackErrorRecoveryCommandOwner` no longer accepts a generic
  `Supplier<PlaybackQueueManager.QueueStateSnapshot>`. It now reads queue state
  through the existing `PlaybackQueueStateOwner`.
- `EchoPlaybackService` no longer passes the local `queueStateSupplier` into
  error recovery wiring. The remaining `queueStateSupplier` uses in the service
  still belong to position, crossfade, notification state, and playback state
  snapshot owners.
- No owner was added. The real reduction is one fewer generic queue snapshot
  supplier chain entering error recovery.
- `PlaybackErrorRecoveryCommandOwnerTest` now exercises the real
  `PlaybackQueueStateOwner -> PlaybackQueueManager` path for current-track and
  multiple-track skip decisions instead of a hand-written snapshot supplier.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1424 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Crossfade Queue State Source

Current audit date: 2026-07-03.

- `PlaybackCrossfadeStateOwner` no longer accepts a generic
  `Supplier<PlaybackQueueManager.QueueStateSnapshot>`. It now reads queue state
  through the existing `PlaybackQueueStateOwner`.
- `EchoPlaybackService` no longer passes the local `queueStateSupplier` into
  crossfade state wiring. The remaining `queueStateSupplier` uses in the
  service belong to position, notification state, and playback state snapshot
  owners.
- No owner was added. The real reduction is one fewer generic queue snapshot
  supplier chain entering crossfade.
- `PlaybackCrossfadeStateOwnerTest` now exercises the real
  `PlaybackQueueStateOwner -> PlaybackQueueManager` path for multiple-track and
  end-of-queue crossfade decisions instead of hand-written queue snapshots.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1424 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackCrossfadeStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Audit - Queue Snapshot Supplier Batch

Current audit date: 2026-07-03.

- This is a read-only audit after the error-recovery and crossfade queue-state
  source slices. No production code changed in this checkpoint.
- Batch reductions:
  - `PlaybackErrorRecoveryCommandOwner` now reads queue state from
    `PlaybackQueueStateOwner` instead of a generic
    `Supplier<PlaybackQueueManager.QueueStateSnapshot>`.
  - `PlaybackCrossfadeStateOwner` now reads queue state from
    `PlaybackQueueStateOwner` instead of a generic
    `Supplier<PlaybackQueueManager.QueueStateSnapshot>`.
  - Both focused tests now exercise the real
    `PlaybackQueueStateOwner -> PlaybackQueueManager` path instead of
    hand-written queue snapshot suppliers.
- Remaining `queueStateSupplier` arguments in `EchoPlaybackService`: 3.
  They currently feed `PlaybackPositionManager.stateProviderFromPlaybackState`,
  `PlaybackNotificationStateOwner`, and `PlaybackStateSnapshotOwner`.
- Boundary assessment:
  - `PlaybackPositionManager` is a low-risk next candidate if the feature
    manager can accept a queue-state owner without creating an app-module
    dependency cycle.
  - `PlaybackStateSnapshotOwner` is the public playback snapshot aggregation
    owner; it can be narrowed later, but it should keep one stable snapshot
    read instead of exposing separate derived queue booleans.
  - `PlaybackNotificationStateOwner` remains a P4-adjacent runtime surface.
    Do not touch notification queue-state wiring until the smoke table is
    stable or the slice is explicitly notification-focused.
- Current metrics: `EchoPlaybackService.java` is 1424 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, service `queueStateSupplier` argument references are 3,
  and `Playback*Owner` production file count is 43.
- Verification for this audit was read-only evidence plus doc validation:

```powershell
codegraph explore "EchoPlaybackService queueStateSupplier PlaybackPositionManager PlaybackNotificationStateOwner PlaybackStateSnapshotOwner PlaybackQueueStateOwner supplier wiring owner interface audit"
rg -n "queueStateSupplier|new PlaybackNotificationStateOwner|new PlaybackStateSnapshotOwner|PlaybackPositionManager\.stateProviderFromPlaybackState|new PlaybackCrossfadeStateOwner|new PlaybackErrorRecoveryCommandOwner" app/src/main/java/app/yukine/playback/EchoPlaybackService.java app/src/main/java/app/yukine/playback feature/playback/src/main/java app/src/test/java/app/yukine/playback feature/playback/src/test/java/app/yukine/playback
git diff --check
```

## P1 Wiring Note - Playback Snapshot Queue State Source

Current audit date: 2026-07-03.

- `PlaybackStateSnapshotOwner` no longer accepts a generic
  `Supplier<PlaybackQueueManager.QueueStateSnapshot>`. It now reads the stable
  queue snapshot through the existing `PlaybackQueueStateOwner`.
- `EchoPlaybackService` no longer passes the local `queueStateSupplier` into
  playback state snapshot wiring. The remaining `queueStateSupplier` uses in
  the service are `PlaybackPositionManager.stateProviderFromPlaybackState(...)`
  and `PlaybackNotificationStateOwner`.
- No owner was added. The real reduction is one fewer generic queue snapshot
  supplier chain entering playback state snapshot aggregation.
- `PlaybackStateSnapshotOwnerTest` now exercises the real
  `PlaybackQueueStateOwner -> PlaybackQueueManager` path for current track,
  current index, and queue size instead of a hand-written queue snapshot
  supplier.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1424 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 2, service `queueStateSupplier` argument references are 2,
  and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackStateSnapshotOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Service Queue Snapshot Supplier Alias

Current audit date: 2026-07-03.

- `EchoPlaybackService` no longer creates a local
  `final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier`
  alias during playback owner wiring.
- The remaining consumers receive the existing
  `playbackQueueStateOwner::queueStateSnapshot` method reference directly:
  `PlaybackPositionManager.stateProviderFromPlaybackState(...)` and
  `PlaybackNotificationStateOwner`.
- This does not change notification behavior and does not move notification
  policy. It only removes the service-local forwarding alias.
- No owner was added. The real reduction is one fewer service wiring supplier
  alias. The direct `queueStateSnapshot` method-reference count in the service
  is now 3, so this slice should not be counted as reducing repeated method
  references.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1421 lines,
  `private Playback*` field count is 55 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, `queueStateSnapshot` references in
  the service are 3, service `queueStateSupplier` alias references are 0, and
  `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Error Recovery Current Track Debug

Current audit date: 2026-07-03.

- `EchoPlaybackService` no longer reads
  `playbackQueueStateOwner.currentTrack()` just to format the fallback player
  error log. The existing `PlaybackErrorRecoveryCommandOwner` now exposes
  `debugCurrentTrack()` and reads the current track through its existing
  `PlaybackQueueStateOwner`.
- No owner was added and no constructor wiring changed. The real reduction is
  one fewer direct Service queue-current-track read in the player error path.
- This slice deliberately does not touch notification, lyrics, shutdown, or
  background playback behavior.
- `PlaybackErrorRecoveryCommandOwnerTest` now covers current-track debug
  formatting for populated and missing queue state.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1430 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  3, and `Playback*Owner` production file count is 44.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P2 Boundary Note - Media Cache Key Owner Rules

Current audit date: 2026-07-03.

- No production code changed in this slice. The purpose is to lock the
  resolver/cache boundary with focused behavior coverage before more migration.
- `PlaybackMediaSourceProviderTest` now directly covers the static
  `PlaybackMediaSourceProvider.mediaCacheKey(dataPath, uri)` rules for:
  streaming placeholders without a resolved URL, WebDAV cache keys, empty
  data paths, and local file paths.
- This keeps URI / MediaItem / cache key identity behavior owned by
  `PlaybackMediaSourceProvider`, while cache operations remain consumed through
  `PlaybackMediaCacheOperations` by `PlaybackPrecacheManager` and visualization
  cache code.
- `PlaybackMediaSourceResolutionOwner` remains absent; no resolver facade or
  cache policy owner was added.
- Audit metrics are unchanged from the previous production slice:
  `EchoPlaybackService.java` is 1430 lines, `private Playback*` field count is
  43 by the current `rg` metric, `fromPlaybackQueueManager` count is 0, direct
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  3, and `Playback*Owner` production file count is 44.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain
```

## P1/P2 Batch Audit - Queue State And Resolver Cache Boundaries

Current audit date: 2026-07-03.

- This is the audit checkpoint after three small playback slices:
  queue mutation empty-state routing, error-recovery current-track debug
  routing, and media cache key owner-rule coverage.
- Real reductions in this batch:
  - `PlaybackQueueMutationOwner.clearQueue()` reads empty-queue state through
    `PlaybackQueueStateOwner`, not directly from
    `PlaybackQueueManager.queueStateSnapshot()`.
  - fallback player-error debug logging reads the current track through the
    existing `PlaybackErrorRecoveryCommandOwner`, reducing direct
    `EchoPlaybackService` queue-current-track reads from 4 to 3.
  - `PlaybackMediaSourceProviderTest` now protects cache-key ownership for
    streaming, WebDAV, empty data path, and local-path rules.
- Candidate disposition:
  - `PlaybackQueueStopClearOwner` remains abandoned. The production and test
    files are absent, architecture contracts already guard that absence, and
    `stopAndClear()` reaches
    `PlaybackQueueCompletionOwner.prepareStopAndClearPlaybackState`.
  - The remaining service
    `playbackQueueStateOwner::queueStateSnapshot` consumer is
    `PlaybackNotificationStateOwner`, which is P4-adjacent and should not be
    moved until notification smoke is stable or the slice is explicitly scoped
    to notification.
  - No production `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`,
    resolver facade, or cache policy facade exists. URI and MediaItem
    decisions remain in `PlaybackMediaSourceProvider`; precache policy remains
    in `PlaybackPrecacheManager` through `PlaybackMediaCacheOperations`.
- Current metrics: `EchoPlaybackService.java` is 1430 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  3, and `Playback*Owner` production file count is 44.
- Focused tests covering this batch:
  `PlaybackQueueMutationOwnerTest`, `PlaybackErrorRecoveryCommandOwnerTest`,
  `PlaybackMediaSourceProviderTest`, and
  `MainActivityArchitectureContractTest`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Prepare Current Command Owner

Current audit date: 2026-07-03.

- `PlaybackQueueCommandOwner.prepareCurrent(boolean)` now resolves the current
  track through the existing `PlaybackQueueStateOwner` before invoking the
  Track-aware Service preparation boundary.
- `EchoPlaybackService` no longer has the boolean-only `prepareCurrent(boolean)`
  helper that read `playbackQueueStateOwner.currentTrack()` and then forwarded
  to `prepareCurrent(Track, boolean)`.
- `PlaybackErrorRecoveryCommandOwner` now receives
  `playbackQueueCommandOwner::prepareCurrent`, so error recovery also uses the
  semantic playback command owner instead of re-entering the Service to read
  queue state.
- `PlaybackRecoveryScheduler` also receives
  `playbackQueueCommandOwner::prepareCurrent`, keeping scheduled playback
  recovery on the same semantic command path.
- No owner was added. The real reductions are one fewer Service queue-current
  read and one fewer boolean-only Service forwarding helper.
- `PlaybackQueueCommandOwnerTest` now covers the real
  `PlaybackQueueStateOwner -> PlaybackQueueManager` current-track path and the
  missing-current-track no-op path.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  2, and `Playback*Owner` production file count is 44.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1 Wiring Note - Mirrored Transition Current Track Result

Current audit date: 2026-07-03.

- `PlaybackQueueMirroredTransitionOwner.applyMirroredTransitionReason(...)`
  now returns a narrow owner-local `Transition` result with the completed
  index, stop-after-auto-advance flag, and the transition-current track.
- `EchoPlaybackService.onMediaItemTransition(...)` no longer reads
  `playbackQueueStateOwner.currentTrack()` after applying a mirrored queue
  transition; it consumes `transition.currentTrack()` for waveform reset.
- No owner was added. The real reduction is one fewer direct Service
  queue-current-track read while keeping the queue state source inside the
  existing mirrored transition owner.
- `PlaybackQueueMirroredTransitionOwnerTest` now covers current-track values
  returned for successful mirrored transitions and null current-track for the
  stop-after-automatic-advance branch.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1425 lines,
  `private Playback*` field count is 43 by the current `rg` metric,
  `fromPlaybackQueueManager` count is 0,
  `playbackQueueStateOwner::queueStateSnapshot` references in the service are
  1, direct `playbackQueueStateOwner.currentTrack()` calls in the service are
  1, and `Playback*Owner` production file count is 44.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMirroredTransitionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

## P1/P2 Checkpoint - Owner Boundary And Interface Drift

Current audit date: 2026-07-03.

- This checkpoint follows the queue command and media library data-source
  slices:
  - `EchoPlaybackService.play()` no longer reads the current track directly
    before preparing playback; it asks `PlaybackQueueCommandOwner` to prepare
    the current queue item when one exists.
  - `PlaybackMediaLibraryDataSourceTest` now covers the real streaming item
    path from `PlaybackMediaLibraryDataSource.fromRepository(...)` to
    `PlaybackMediaSourceProvider.mediaItemForTrack(...)`, including media item
    id, resolved URI, custom cache key, and metadata.
- Real reductions in this checkpoint:
  - no new owner was added;
  - one Service queue-current-track decision moved behind the existing
    semantic queue command owner;
  - no resolver/cache facade was introduced for the media library path;
  - the architecture contract still blocks `PlaybackQueueManager.QueueProvider`
    from returning and keeps queue manager nested interfaces narrowed to
    `QueuePlaybackActions`, `StreamingRestoreProvider`, and
    `MirroredQueuePlayer`.
- Current metrics:
  - `EchoPlaybackService.java` is 1327 lines.
  - `private Playback*` field count is 43 by the existing non-final field
    metric; counting `private final Playback*` as well makes the wiring risk
    count 55.
  - `fromPlaybackQueueManager` production count is 0.
  - service `playbackQueueStateOwner` queue/current references are:
    `playbackQueueStateOwner::queueStateSnapshot` = 1,
    direct `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` =
    2, `playbackQueueStateOwner::currentTrack` = 2, and direct
    `playbackQueueStateOwner.currentTrack()` = 0.
  - `Playback*Owner` production file count is 43.
- Resolver/cache boundary audit:
  - production code has no `PlaybackMediaSourceResolutionOwner`,
    `PlaybackItemResolver`, resolver facade, or cache policy facade.
  - URI and MediaItem resolution remain in `PlaybackMediaSourceProvider`.
  - cache operations for precache still enter through
    `PlaybackPrecacheManager.fromMediaSourceProvider(...)` and
    `PlaybackMediaCacheOperations.fromMediaSourceProvider(...)`.
- Deferred risk:
  - the remaining `playbackQueueStateOwner::queueStateSnapshot` service
    supplier feeds notification state and is P4-adjacent;
  - the remaining `playbackQueueStateOwner::currentTrack` suppliers feed
    notification artwork and Wi-Fi/background handling, so they should not be
    collapsed without the notification/background smoke table;
  - the two direct snapshot current-track reads are runtime/position wiring and
    should only move if the next slice removes a real supplier chain or Service
    strategy decision.
- Focused tests covering this checkpoint:
  `PlaybackQueueCommandOwnerTest`, `PlaybackMediaLibraryDataSourceTest`,
  `PlaybackMediaSourceProviderTest`, and
  `MainActivityArchitectureContractTest`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.playback.PlaybackMediaLibraryDataSourceTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Wiring Note - Runtime And Position Current Track Suppliers

Current audit date: 2026-07-03.

- `EchoPlaybackService` no longer wires runtime state or playback-position
  state with handwritten
  `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` suppliers.
- Both state providers now use the existing
  `playbackQueueStateOwner::currentTrack` entry point, keeping the queue
  snapshot read behind `PlaybackQueueStateOwner`.
- No owner was added. The real reduction is two fewer Service-local
  snapshot-to-current-track supplier lambdas.
- `MainActivityArchitectureContractTest` now blocks this direct Service read
  from returning.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1327 lines,
  `private Playback*` field count is 43 by the existing non-final field
  metric, `playbackQueueStateOwner::queueStateSnapshot` references in the
  service are 1, direct
  `playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` references
  in the service are 0, and `playbackQueueStateOwner::currentTrack` references
  in the service are 4.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest --tests app.yukine.playback.PlaybackPositionManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Interface Audit - Queue State Snapshot Source Fields

Current audit date: 2026-07-03.

- `MainActivityArchitectureContractTest` now audits
  `PlaybackQueueManager.QueueStateSnapshot` constructor fields separately from
  derived reads.
- The snapshot source state is fixed to `currentTrack`, `currentIndex`, and
  `queueSize`.
- `isQueueEmpty`, `hasCurrentTrack`, `hasMultipleTracks`, and
  `isAtEndOfQueue` must remain derived getters. This prevents queue snapshot
  widening from reintroducing extra state sources under a narrower-looking
  interface.
- No production code changed and no owner was added. The real gain is stronger
  interface drift coverage for the queue authority boundary.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1327 lines,
  `private Playback*` field count is 43 by the existing non-final field
  metric, and `Playback*Owner` production file count is 43.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain
```

## P2/P3 Boundary Test - Null Media Source Provider Precache

Current audit date: 2026-07-03.

- `PlaybackPrecacheManagerTest` now covers
  `PlaybackPrecacheManager.fromMediaSourceProvider(...)` when the media source
  provider is missing.
- The expected behavior is a safe no-op: no precache diagnostics, no delayed
  callbacks, and safe `releaseAudioCache()` / `release()` calls.
- No production code changed and no owner was added. The real gain is behavior
  coverage for the cache boundary staying inside `PlaybackPrecacheManager` and
  `PlaybackMediaCacheOperations`, instead of pushing null-provider fallback
  policy back into `EchoPlaybackService` or a new resolver/cache facade.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1327 lines,
  `private Playback*` field count is 43 by the existing non-final field
  metric, `Playback*Owner` production file count is 43, and Service
  `queueStateSnapshot()` supplier count is 1.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain
```

## P1 Wiring Note - Current Track Missing Fallback Command

Current audit date: 2026-07-03.

- `EchoPlaybackService.play()` no longer calls
  `playbackQueueCommandOwner.hasCurrentTrack()` and interprets the boolean
  current-track availability result itself.
- `PlaybackQueueCommandOwner.runIfCurrentTrackMissing(...)` now owns the
  current-track-missing check and runs the Service-provided fallback command.
- No owner was added. The real reduction is one fewer Service queue-state
  strategy decision; the Service still supplies the Android/Media3 boundary
  action that plays the first queued track.
- `PlaybackQueueCommandOwnerTest` covers both paths: fallback is skipped when a
  current track exists and is run when the current track is missing.
- `MainActivityArchitectureContractTest` blocks the old
  `playbackQueueCommandOwner.hasCurrentTrack()` call from returning.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1328 lines,
  `private Playback*` field count is 43 by the existing non-final field
  metric, `Playback*Owner` production file count is 43, and Service
  `queueStateSnapshot()` supplier count is 1.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1/P2/P3 Batch Audit - Queue Contract And Cache Boundary

Current audit date: 2026-07-03.

- This checkpoint follows three small slices:
  - queue state snapshot source-field contract;
  - null media source provider precache boundary coverage;
  - current-track-missing fallback routed through `PlaybackQueueCommandOwner`.
- Real reductions and protections in this batch:
  - `PlaybackQueueManager.QueueStateSnapshot` is locked to three source fields:
    `currentTrack`, `currentIndex`, and `queueSize`; queue booleans must remain
    derived getters.
  - `PlaybackPrecacheManager.fromMediaSourceProvider(...)` now has focused
    coverage for missing provider behavior, so null-provider cache fallback
    policy does not need to move into `EchoPlaybackService` or a new facade.
  - `EchoPlaybackService.play()` no longer interprets
    `playbackQueueCommandOwner.hasCurrentTrack()`; missing-current fallback is
    owned by `PlaybackQueueCommandOwner.runIfCurrentTrackMissing(...)`.
- Current metrics:
  - `EchoPlaybackService.java` is 1328 lines.
  - `private Playback*` field count is 43 by the existing non-final field
    metric; counting `private final Playback*` as well gives 55.
  - `fromPlaybackQueueManager` production count is 0.
  - Service `queueStateSnapshot()` supplier count is 1.
  - `Playback*Owner` production file count is 43.
- Boundary disposition:
  - no owner was added in this batch;
  - no production `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`,
    resolver facade, or cache policy facade was added;
  - URI / MediaItem resolution remains in `PlaybackMediaSourceProvider`;
  - cache policy remains in `PlaybackPrecacheManager` /
    `PlaybackMediaCacheOperations`.
- Deferred risk remains unchanged: the single Service
  `playbackQueueStateOwner::queueStateSnapshot` supplier is notification-state
  wiring and should stay deferred until a P4 notification smoke slice is
  explicitly scoped.
- Focused tests covering this batch:
  `PlaybackQueueManagerTest`, `PlaybackMediaSourceProviderTest`,
  `PlaybackPrecacheManagerTest`, `PlaybackQueueCommandOwnerTest`, and
  `MainActivityArchitectureContractTest`.
- Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Wiring Note - Prepare Current Or Fallback Command

Current audit date: 2026-07-03.

- `EchoPlaybackService.play()` no longer calls
  `playbackQueueCommandOwner.prepareCurrentIfAvailable(true)` and branches on
  the boolean prepare result in the player-null path.
- `PlaybackQueueCommandOwner.prepareCurrentOrRunFallback(...)` now owns the
  prepare-or-fallback decision and runs the Service-provided queue fallback
  command when the current item cannot be prepared.
- `prepareCurrentIfAvailable(...)` is now private to
  `PlaybackQueueCommandOwner`; the Service sees a semantic command instead of a
  queue-state probe.
- No owner was added. The real reduction is one fewer Service prepare fallback
  strategy decision.
- `PlaybackQueueCommandOwnerTest` covers both prepare success and fallback
  paths, and `MainActivityArchitectureContractTest` blocks the old Service
  call from returning.
- Audit metrics after this slice: `EchoPlaybackService.java` is 1329 lines,
  `private Playback*` field count is 43 by the existing non-final field
  metric, `Playback*Owner` production file count is 43, and Service
  `queueStateSnapshot()` supplier count is 1.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

## P1 Wiring Note - Queue Command Current Track Source

Current audit date: 2026-07-03.

- `PlaybackQueueCommandOwner` no longer directly unwraps
  `queueStateOwner.queueStateSnapshot().getCurrentTrack()` when preparing the
  current item or checking the missing-current fallback.
- The command owner now reads the current track through the existing
  `PlaybackQueueStateOwner.currentTrack()` semantic method.
- No owner was added. The real reduction is two fewer direct snapshot-current
  dereference chains inside a command owner while keeping
  `PlaybackQueueStateOwner` as the narrow queue-state reader.
- `MainActivityArchitectureContractTest` now blocks
  `PlaybackQueueCommandOwner` from returning to direct
  `queueStateOwner.queueStateSnapshot().getCurrentTrack()` reads.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```
