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

2026-07-07 continuation verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
# BUILD SUCCESSFUL
# Covers assembleDebugAndroidTest, assembleDebug, lintDebug, assembleRelease, bundleRelease, lintRelease.

.\gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

adb devices
# List of devices attached
# <empty>
```

This proves the non-device release gates and P0 unit-test guards on the
current workstation. It does not prove the device-only playback stability
matrix rows; those still require a connected Android device and recorded
screens/logcat.

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

## 2026-07-07 P0 Automation And Device-Gate Update

Scope for this checkpoint:

- Kept the `StreamingViewModelTest` unit-test guard green after the thread-safe fake provider fix.
- Added the first `EchoDatabaseHelper` Robolectric baseline covering schema upgrade, transaction rollback, and concurrent writes before any new schema work.
- Re-ran the playback matrix pre/post automated gates available on this workstation.

Commands and results:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
# First run: LibraryGroupsRenderControllerTest.ignoresStaleArtistInfoWhenAnotherArtistIsOpened failed once.
# Focused rerun of that test: BUILD SUCCESSFUL.
# Second full rerun: BUILD SUCCESSFUL.
```

Device-gated playback matrix status:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
# List of devices attached
# <empty>
```

No Android device was connected on this workstation, so the manual/half-automated rows in
`docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md` remain **not executed** for this checkpoint, including:

- Local song playback/pause/skip/seek on hardware.
- Background playback, lock-screen controls, and notification controls.
- Cold-start queue restore and process-kill restore.
- Headset disconnect, Bluetooth switching, and call interruption.

Do not claim runtime playback-service acceptance from this checkpoint alone. The next device run should use
`app/build/outputs/apk/debug/app-debug.apk`, capture `adb logcat` for `EchoPlaybackService`, `MediaSession`,
`ExoPlayer`, and `AudioManager`, and fill the execution record in `PLAYBACK_SERVICE_STABILITY_MATRIX.md`.

## 2026-07-07 Playback Queue Restore Transaction Guard

Additional non-device P0 guard added while no ADB device was available for the full playback matrix:

- `EchoDatabaseHelperTest.savePlaybackQueueRollsBackOldQueueWhenReplacementBatchFails`
  - Seeds a two-track playback queue at index 1.
  - Attempts to replace it with a malformed queue batch that fails after the table delete.
  - Verifies the previous queue rows and saved index are still available for cold-start restore.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL
```

This does not replace device smoke for cold-start/process-kill restore, but it protects the SQLite transaction
that runtime restore depends on.

## 2026-07-07 Hardware Interruption Guard

Added a focused non-device guard for the headset/Bluetooth disconnect row while no ADB device was available:

- `PlaybackNoisyReceiverManagerTest.audioBecomingNoisyBroadcastPausesOnlyActivePlayback`
  - Dispatches `AudioManager.ACTION_AUDIO_BECOMING_NOISY` through the registered receiver.
  - Verifies active playback pauses exactly once.
  - Verifies unrelated broadcasts do not pause and inactive playback is not paused.

This does not replace the hardware row in `PLAYBACK_SERVICE_STABILITY_MATRIX.md`, but it protects the core
no-sudden-speaker behavior behind that row until the real device pass can be recorded.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackNoisyReceiverManagerTest --console=plain
# BUILD SUCCESSFUL
```

## 2026-07-07 Task-Removed Resume Flag Guard

Added another non-device guard for cold-start/process-kill restore behavior:

- `PlaybackShutdownCoordinatorTest.handleTaskRemovedClearsResumeRequestWhenPlaybackIsInactive`
  - Verifies `handleTaskRemoved()` persists current position and queue before writing the resume flag.
  - Verifies the resume flag is explicitly saved as `false` when playback is neither playing nor preparing.

This complements the existing positive `resume:true` cases and prevents stale resume requests from causing an
unexpected auto-resume after task removal or process recreation.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest --console=plain
# BUILD SUCCESSFUL
```

## 2026-07-07 Notification Control Action Guard

Added a focused non-device guard for the notification-control row:

- `PlaybackNotificationManagerTest.notificationActionsMapToPlaybackServiceControls`
  - Builds the playback notification with a paused track and verifies action pending-intents map to
    `PREVIOUS`, `RESTORE_AND_PLAY`, `NEXT`, and `TOGGLE_FAVORITE`.
  - Rebuilds while playing and verifies the compact middle action switches to `PAUSE`.

This does not replace lock-screen/notification tray hardware interaction, but it protects the notification action
mapping that those controls dispatch into `EchoPlaybackService.onStartCommand()`.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationManagerTest --console=plain
# BUILD SUCCESSFUL
```

## 2026-07-07 MediaSession / Lock-Screen Command Guard

Added a focused non-device guard for lock-screen / MediaSession transport controls:

- `PlaybackSessionPlayerTest.lockScreenTransportCommandsDelegateToPlaybackOwner`
  - Verifies `play`, `pause`, `setPlayWhenReady`, seek, previous/next, repeat-mode, and stop calls are routed
    to the playback delegate instead of only mutating the wrapped Media3 player.
- `PlaybackSessionPlayerTest.lockScreenQueueNavigationAndRepeatCommandsAreAdvertised`
  - Runs under Robolectric so Media3 `Player.Commands` uses Android collection semantics.
  - Verifies previous/next queue navigation and repeat controls are available both through
    `isCommandAvailable()` and `availableCommands`.
  - Verifies an unrelated base seek command still remains unavailable when the wrapped player does not advertise it.
- `PlaybackSessionPlayerTest.controllerMediaItemCommandsRouteThroughDelegateBeforeFallback`
  - Verifies controller-provided media items are offered to the app queue delegate before any fallback path.

This does not replace the physical lock-screen row in `PLAYBACK_SERVICE_STABILITY_MATRIX.md`, but it protects the
MediaSession command bridge that lock-screen controls use.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionPlayerTest --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Audio Focus Handling Guard

Added a focused non-device guard for the call-interruption/audio-focus side of the hardware interruption row:

- `PlaybackRuntimeStateManagerTest.concurrentPlaybackSetterAppliesAudioFocusHandling`
  - Verifies disabling concurrent playback applies Media3 audio attributes with `USAGE_MEDIA` and
    `AUDIO_CONTENT_TYPE_MUSIC`, and enables audio-focus handling.
  - Verifies enabling concurrent playback keeps the same media/music attributes but disables audio-focus handling,
    preserving the intentional "play alongside other apps" setting.

This does not replace an actual incoming-call interruption pass on a device, but it protects the audio-focus
configuration that determines whether Android will pause/duck Yukine during competing audio focus events.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest.concurrentPlaybackSetterAppliesAudioFocusHandling --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Queue Restore Filtered-Index Guard

Fixed and guarded a cold-start restore edge case where persisted queue indexes could point at a row that is later
filtered out as non-restorable, or beyond the filtered queue length:

- `PlaybackQueueManager.restorePlaybackQueue()` now maps the persisted current index through the filtered,
  restorable queue before publishing/persisting the restored queue.
- A persisted `-1` still preserves the existing "queue exists but no current track" state.
- Non-negative indexes that survive filtering keep the intended current track; non-negative indexes that are out of
  range are clamped to a valid restored track instead of leaving an invalid current index.

Regression coverage:

- `PlaybackQueueManagerTest.restorePlaybackQueueKeepsCurrentTrackAfterFilteringInvalidRows`
- `PlaybackQueueManagerTest.restorePlaybackQueueClampsOutOfRangePersistedIndexAfterFilteringInvalidRows`
- Existing `restoreLastPlaybackPreservesCreateWithoutPrepareForMissingCurrentTrack` remains green to protect the
  `currentIndex == -1` semantics.

This does not replace device cold-start/process-kill recovery smoke, but it protects the queue/index restoration
logic those rows depend on after invalid local rows or missing URI rows are dropped.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Invalid Local Track Recovery Guard

Added a focused non-device guard for the `无效本地 URI` / local playback error row:

- `PlaybackErrorRecoveryManagerTest.invalidLocalTrackSkipsToNextWhenQueueCanContinue`
  - Simulates a failed local `content://` track.
  - Verifies the error recovery owner does not schedule a streaming retry for local media.
  - Verifies recoverable queue failures clear the visible error state and skip to the next track.
  - Verifies no delayed retry remains pending after the local failure path.

This does not replace device playback of a truly missing media-store row, but it protects the service-side failure
policy that prevents an unplayable local item from crashing playback or trapping the queue when another track can be
played.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Seek / Progress Drag Boundary Guard

Added a focused non-device guard for the `进度拖动` row:

- `PlaybackSessionPlayer.seekTo(...)` now clamps negative positions to `0` before delegating to the playback owner.
- `PlaybackSessionPlayerTest.seekCommandsClampNegativePositionsBeforeDelegating`
  - Verifies direct MediaSession seek and indexed seek both clamp negative positions to the track start.
  - Verifies valid positive seek positions still pass through unchanged.

This does not replace device drag/lyrics/waveform synchronization smoke, but it prevents malformed slider/session input
from propagating a negative seek position into the playback service path.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionPlayerTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Queue Restore Filtered-Current Fallback Guard

Tightened the cold-start/process-kill queue restore guard for the case where the persisted current row is itself
filtered out as non-restorable:

- `PlaybackQueueManager.restorePlaybackQueue()` now prefers the first restorable row after the removed persisted-current
  row, falling back to the last restorable row before it when there is no later item.
- `PlaybackQueueManagerTest.restorePlaybackQueueFallsForwardWhenPersistedCurrentTrackIsFilteredOut`
  - Persists a queue where index `1` points at a missing local URI.
  - Verifies restore filters that row but resumes at the next playable local item instead of incorrectly clamping to the
    previous track.
  - Verifies the remapped index is persisted back through the queue store.

This does not replace the device cold-start/process-kill recovery matrix rows, but it protects the deterministic queue
selection rule used when stale media-store rows disappear between launches.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 MediaSession / Android Auto Queue Start Index Guard

Added a focused non-device guard for external controller queue requests used by MediaSession, lock-screen/headset flows,
and Android Auto browse/playback:

- `PlaybackMediaLibraryCallback.controllerQueueForMediaItems()` now remaps the controller-provided `startIndex` after
  unresolved media items are filtered from the requested queue.
- If the requested item survives filtering, playback starts from that same logical item in the filtered queue.
- If the requested item is filtered out, restore/playback falls forward to the next resolved item, falls back to the last
  resolved item when there is no later item, and clamps negative indexes to the first resolved item.
- `onSetMediaItems()` uses the same remapping rule so platform controller queue replacement and app-side queue playback
  stay aligned.

Regression coverage:

- `PlaybackMediaLibraryCallbackTest.controllerQueueForMediaItemsRemapsStartIndexAfterFilteringUnresolvedItems`
- `PlaybackMediaLibraryCallbackTest.controllerQueueForMediaItemsFallsForwardOrBackWhenRequestedStartItemIsFiltered`

This does not replace Android Auto DHU or real Bluetooth/headset validation, but it protects the queue selection rule
that those external controller paths depend on when stale/missing media IDs are present.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaLibraryCallbackTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Controller Queue Start Position Guard

Added explicit regression coverage for external controller queue requests that provide a start position:

- `PlaybackQueueManagerTest.playQueueClampsExplicitStartPositionForImmediateRestore`
  - Verifies an over-large controller start position is clamped to the last safe resume point for the selected track.
  - Verifies a negative controller start position does not create a negative restored position and resolves to track start.

This protects the `本地歌曲播放 / 进度拖动 / 外部控制器队列播放` rows from malformed MediaSession/Android Auto start
positions. It does not replace physical progress-drag validation on a device, but it confirms the queue owner never
persists an unsafe immediate-restore position from controller input.

Verification:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.playQueueClampsExplicitStartPositionForImmediateRestore --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.manager.PlaybackMediaLibraryCallbackTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Notification Stop Action Mapping Guard

Tightened the non-device notification control guard:

- `PlaybackNotificationManagerTest.notificationActionsMapToPlaybackServiceControls` now also verifies the fifth
  notification action maps to `PlaybackServiceActions.STOP` in both paused and playing notification states.
- This protects the notification stop/clear entry point from action-order or PendingIntent mapping regressions while the
  real notification-control matrix row still needs device validation.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationManagerTest.notificationActionsMapToPlaybackServiceControls --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Database Same-ID Concurrent Upsert Guard

Expanded the `EchoDatabaseHelper` concurrency baseline before future schema changes:

- `EchoDatabaseHelperTest.concurrentUpsertSameTrackIdKeepsSingleCompleteRow`
  - Starts multiple writers that concurrently upsert the same track primary key.
  - Verifies the database keeps a single row for that id instead of duplicating or corrupting the record.
  - Verifies the surviving row is a complete track row with the expected stable fields.

This complements the existing multi-id concurrent write test and protects the library/cache update path where repeated
scans or streaming metadata refreshes can race on the same track id.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.concurrentUpsertSameTrackIdKeepsSingleCompleteRow --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```


## 2026-07-07 Database Partial Playback Queue Migration Guard

Expanded the `EchoDatabaseHelper` migration baseline for users upgrading from an intermediate schema that already has a
`playback_queue` table but lacks the newer audio-spec and replay-gain columns:

- `EchoDatabaseHelperTest.upgradeFromPartialPlaybackQueueSchemaAddsAudioColumnsAndPreservesQueueRows`
  - Builds a version-10 style database with an older `playback_queue` shape.
  - Verifies upgrade adds `codec`, `bitrate_kbps`, `sample_rate_hz`, `bits_per_sample`, `channel_count`,
    `replay_gain_track_db`, and `replay_gain_album_db`.
  - Verifies the saved playback queue row and queue index survive the upgrade with safe default audio metadata values.

This complements the version-1 legacy migration test by covering idempotent `ALTER TABLE` behavior for partial schemas,
which is the path most likely to be hit by users who have upgraded through several app versions.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.upgradeFromPartialPlaybackQueueSchemaAddsAudioColumnsAndPreservesQueueRows --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL
```

## 2026-07-07 Sleep Timer Cancel Race Guard

- 覆盖矩阵：基础状态矩阵 / 睡眠定时。
- 新增自动化护栏：`PlaybackSleepTimerManagerTest.cancelPreventsAlreadyDequeuedExpiryTickFromPausingPlayback`。
- 验证点：睡眠定时倒计时回调已经从主线程队列取出后，如果用户先取消定时器，该旧回调即使随后执行也不能再次暂停播放或发布过期状态，避免“取消后仍到时暂停”的 P1/P0 体验问题。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackSleepTimerManagerTest.cancelPreventsAlreadyDequeuedExpiryTickFromPausingPlayback --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：当前 `adb devices` 未发现连接设备；真机睡眠定时录屏仍需在播放服务稳定性矩阵中补证据。

## 2026-07-07 Verification Refresh

- `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- `./gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
- `adb devices` — 未发现连接设备；`PLAYBACK_SERVICE_STABILITY_MATRIX.md` 中本地播放、后台/锁屏/通知、冷启动/杀进程恢复、耳机/蓝牙/来电等真机行仍需补录屏和 logcat 证据。

## 2026-07-07 Deleted Current Track / Empty Retain Guard

- 覆盖矩阵：恢复与异常矩阵 / 删除当前曲目、空队列控制。
- 修复点：`PlaybackQueueMutationOwner.retainTracksById(...)` 不再把空集合当作 no-op；当曲库同步结果表示没有任何可保留曲目时，转发给 `PlaybackQueueManager`。
- 修复点：`PlaybackQueueManager.retainTracksById(emptySet())` 现在会走既有 `clearQueue()`/`stopAndClear` 边界，而不是保留已从曲库删除的队列项。
- 新增自动化护栏：
  - `PlaybackQueueManagerTest.retainTracksWithEmptyKeepSetClearsQueueAndStopsPlayback`
  - `PlaybackQueueMutationOwnerTest.retainEmptyTrackSetClearsExistingQueueThroughManager`
- 验证点：当曲库删除/同步后当前队列没有任何可保留 track id，播放队列会停止并清空，随后 `prepareStopAndClearPlaybackState()` 持久化空队列、`currentIndex = -1` 和 `resumeRequested = false`，避免冷启动恢复已删除曲目。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.retainTracksWithEmptyKeepSetClearsQueueAndStopsPlayback :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest.retainEmptyTrackSetClearsExistingQueueThroughManager --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：当前仍未发现 ADB 设备；真实“播放中删除当前曲目/移出曲库”录屏仍需在播放服务稳定性矩阵中补证据。

## 2026-07-07 Streaming Retry Stale Callback Guard

- 覆盖矩阵：流媒体播放矩阵 / 播放中断网、URL 过期恢复。
- 修复点：`PlaybackErrorRecoveryManager` 在决定跳过失败曲目或发布不可播放错误前，会取消仍挂起的流媒体延迟重试，递增 retry generation 并移除旧 callback。
- 新增自动化护栏：`PlaybackErrorRecoveryManagerTest.repeatedStreamingErrorBeforeRetryCancelsStaleRetryBeforeSkipping`。
- 验证点：同一流媒体 track 第一次错误会安排一次 URL/播放重试；如果重试执行前又收到同 track 错误并进入 skip 分支，旧 retry 不会在 skip 后再次 `prepareCurrent(true)`，避免跳歌后旧回调把过期 URL/失败曲目重新拉起。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest.repeatedStreamingErrorBeforeRetryCancelsStaleRetryBeforeSkipping --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryManagerTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：仍需在真实网络切换/URL 过期场景下补 `PLAYBACK_SERVICE_STABILITY_MATRIX.md` 录屏和 logcat 证据。

## 2026-07-07 Current Track Removal Queue Guard

- 覆盖矩阵：恢复与异常矩阵 / 删除当前曲目。
- 新增自动化护栏：`PlaybackQueueManagerTest.removeCurrentTrackKeepsQueueAtNextTrackAndPreparesPausedPlayback`。
- 验证点：播放队列中删除当前曲目时，队列会保留剩余曲目并把 current index 指向下一首；持久化队列/index 同步更新；播放准备以 paused 状态触发；新当前曲目的恢复位置重置为 0，避免恢复已删除曲目或继承旧曲目进度。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.removeCurrentTrackKeepsQueueAtNextTrackAndPreparesPausedPlayback --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：当前仍需在真机播放中删除当前曲目/移出曲库场景补录屏与 logcat 证据。

## 2026-07-07 All-Invalid Restore Cleanup Guard

- 覆盖矩阵：恢复与异常矩阵 / 冷启动恢复、无效本地 URI。
- 修复点：`PlaybackQueueManager.restorePlaybackQueue()` 在持久化队列存在但所有行都被 `PlaybackMediaSourceProvider.isRestorableQueueTrack(...)` 过滤掉时，现在会持久化空队列、`currentIndex = -1`，并清理 stale `resumeRequested`，而不是只返回内存空状态。
- 新增自动化护栏：`PlaybackQueueManagerTest.restorePlaybackQueueClearsPersistedStateWhenAllRowsAreFilteredOut`。
- 验证点：冷启动遇到全坏队列时，不会在下一次启动继续反复尝试同一批不可恢复行；持久化恢复状态被清理为明确空队列，且不会因旧 resume 标记立即恢复播放。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.restorePlaybackQueueClearsPersistedStateWhenAllRowsAreFilteredOut --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：当前仍需在真机冷启动/杀进程恢复和缺失文件场景补录屏与 logcat 证据。

## 2026-07-07 Verification Refresh - Resume Cleanup

- `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.restorePlaybackQueueClearsPersistedStateWhenAllRowsAreFilteredOut --console=plain` — 通过（BUILD SUCCESSFUL）。
- `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- `./gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
- `adb devices` — 未发现连接设备；播放服务稳定性矩阵里的本地播放、后台/锁屏/通知、冷启动/杀进程恢复、耳机/蓝牙/来电仍需真机证据。

## 2026-07-07 Repeat-Off Queue-End Skip Guard

- 覆盖矩阵：基础状态矩阵 / 切歌、通知控制、锁屏控制。
- 修复点：`PlaybackQueueManager.skipToNextImmediately()` 现在会让 `advanceQueueIndexToNext()` 明确返回是否真的移动到下一首；当队列已经在末尾且 repeat mode 为 `REPEAT_OFF` 时，只持久化/发布当前状态，不再继续 `prepareCurrent(true)` 重新播放当前曲。
- 新增自动化护栏：`PlaybackQueueManagerTest.skipToNextAtQueueEndWithRepeatOffDoesNotRestartCurrentTrack`。
- 验证点：用户在队尾点“下一首”（含通知/锁屏委托到同一 queue owner 的路径）不会把最后一首从当前位置重启，也不会写入新的 `resumeRequested=true`；状态只发布一次并保留当前 index。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.skipToNextAtQueueEndWithRepeatOffDoesNotRestartCurrentTrack --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：仍需真机录屏确认播放页、通知、锁屏三处“下一首”在队尾 repeat-off 下的可感知行为一致。

## 2026-07-07 Play-First Resume Persistence Guard

- 覆盖矩阵：恢复与异常矩阵 / 冷启动恢复；基础状态矩阵 / 本地歌曲首次播放。
- 修复点：`PlaybackQueueManager.playFirstQueuedTrack()` 作为空播放器或无当前曲目时的播放入口，现在会与 `playQueue(...)`、`appendToQueue(...)`、手动切歌路径一致写入 `resumeRequested = true`。
- 新增自动化护栏：`PlaybackQueueManagerTest.playFirstQueuedTrackPersistsResumeRequestForColdStartRestore`。
- 验证点：当已有恢复队列但 `currentIndex = -1`，用户点击播放第一首后会持久化 `currentIndex = 0`、重置当前曲位置并写入 resume 标记，避免后续进程重建只恢复队列但不恢复“用户请求继续播放”的状态。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.playFirstQueuedTrackPersistsResumeRequestForColdStartRestore --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：仍需真机冷启动/杀进程恢复录屏确认播放页、服务和通知表现一致。

## 2026-07-07 Play History Event Transaction Guard

- 覆盖矩阵：恢复与异常矩阵 / 数据一致性；成熟度路线图 / 智能歌单数据基线。
- 修复点：`EchoDatabaseHelper.markPlayed(...)` 写入 `play_events` 现在使用 `insertOrThrow(...)`；如果事件表写入失败，会抛出异常并让同一事务回滚，而不是在 `play_history` 中留下半更新的 `play_count`。
- 新增自动化护栏：`EchoDatabaseHelperTest.markPlayedRollsBackHistoryWhenPlayEventInsertFails`。
- 验证点：先记录一次播放历史，再模拟 `play_events` 写入失败；第二次 `markPlayed(...)` 不允许把 `play_history.play_count` 从 1 半更新到 2，保护最近播放/最常播放/智能歌单依赖的数据一致性。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.markPlayedRollsBackHistoryWhenPlayEventInsertFails --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：该项为 SQLite 事务护栏；仍需真机播放历史/最近播放 UI 冒烟作为发布验收补充。

## 2026-07-07 Track Reference Migration Baseline Guard

- 覆盖矩阵：恢复与异常矩阵 / 数据一致性；EchoDatabaseHelper schema 前事务/引用基线。
- 新增自动化护栏：`EchoDatabaseHelperTest.replaceTrackAndMigrateReferencesMovesAllUserDataToReplacementTrack`。
- 验证点：`replaceTrackAndMigrateReferences(...)` 将旧 track id 迁移到 replacement track 时，收藏、播放历史聚合、歌单项、播放队列、播放队列 index、播放位置 track id/position 都一起迁移，旧 track 从曲库移除，避免本地/流媒体解析替换后用户数据丢失或队列恢复指向孤儿 track。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.replaceTrackAndMigrateReferencesMovesAllUserDataToReplacementTrack --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：该项为 SQLite 引用迁移护栏；仍需真机播放队列恢复、最近播放/收藏/歌单 UI 冒烟补充证据。

## 2026-07-07 Track Delete Reference Cleanup Guard

- 覆盖矩阵：恢复与异常矩阵 / 删除当前曲目；数据库基线 / CRUD 事务边界。
- 修复点：`EchoDatabaseHelper.deleteTracksWhere(...)` 现在在删除曲目时同步清理 `play_events`，与 favorites、play_history、playlist_tracks、playback_queue 一起保持引用一致，避免曲目删除后周/月播放统计继续残留孤儿事件。
- 新增自动化护栏：`EchoDatabaseHelperTest.deleteTrackRemovesReferencesEventsAndReconcilesPlaybackState`。
- 验证点：删除曲目会清理收藏、最近播放聚合、play_events、歌单引用、播放队列引用；如果删除的是保存的播放位置曲目，会重置恢复位置；如果删除的是当前队列前方曲目，会压缩队列并重映射 current index。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteTrackRemovesReferencesEventsAndReconcilesPlaybackState --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：真机播放中删除当前曲目/移出曲库仍需补录屏与 logcat；当前仅证明 SQLite 引用清理和恢复状态重映射的自动化基线。

## 2026-07-07 Missing Playlist Delete Transaction Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界。
- 修复点：`EchoDatabaseHelper.deletePlaylist(...)` 现在会先确认 playlist 行存在；当 playlist 不存在时直接返回 `false`，不会先删除同 id 的 `playlist_tracks` 残留行，避免“删除失败但已改变数据库”的半提交语义。
- 新增自动化护栏：`EchoDatabaseHelperTest.deleteMissingPlaylistDoesNotMutateDanglingPlaylistRows`。
- 验证点：在无外键约束或迁移遗留导致存在 dangling playlist_tracks 的情况下，删除不存在的 playlist 不会清掉这些行，也不会误删曲库曲目；真实删除路径仍由已有 playlist 删除测试覆盖。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteMissingPlaylistDoesNotMutateDanglingPlaylistRows --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机播放服务矩阵。

## 2026-07-07 Playlist Membership Missing Row Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 大歌单流媒体队列（同步歌单会清空并重建成员）。
- 修复点：`EchoDatabaseHelper.removeTrackFromPlaylist(...)` 与 `clearPlaylistTracks(...)` 现在会先确认 playlist 行存在；当迁移遗留或异常状态只剩 dangling `playlist_tracks` 时，不会误删除/清空这些成员行。
- 新增自动化护栏：`EchoDatabaseHelperTest.removeTrackFromMissingPlaylistLeavesMembershipUntouched`、`EchoDatabaseHelperTest.clearMissingPlaylistLeavesMembershipUntouched`。
- 验证点：缺失 playlist 行时，移出/清空歌单成员入口不改变 dangling membership，避免“目标 playlist 不存在但成员行被改动”的半提交语义；正常存在 playlist 的移出/清空路径仍由同一 EchoDatabaseHelper 测试集覆盖。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.removeTrackFromMissingPlaylistLeavesMembershipUntouched --tests app.yukine.data.EchoDatabaseHelperTest.clearMissingPlaylistLeavesMembershipUntouched --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机播放服务矩阵中的流媒体大歌单同步/连续切歌录屏与 logcat。

## 2026-07-07 Clear Play History Transaction Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 播放历史与数据一致性。
- 新增自动化护栏：`EchoDatabaseHelperTest.clearPlayHistoryRollsBackHistoryWhenEventDeleteFails`。
- 验证点：`EchoDatabaseHelper.clearPlayHistory()` 已在单个 SQLite transaction 内同时删除 `play_history` 与 `play_events`；当 `play_events` 删除失败时，`play_history` 删除会回滚，避免最近播放 UI 清空但事件统计仍失败/不一致的半提交状态。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.clearPlayHistoryRollsBackHistoryWhenEventDeleteFails --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机最近播放/播放历史 UI 冒烟。

## 2026-07-07 WebDAV Source Edit Transaction Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 后台被杀、冷启动恢复中的远程源缓存数据一致性。
- 修复点：编辑 WebDAV 源时，旧 `webdav:<sourceId>:` 曲目清理现在下沉到 `EchoDatabaseHelper.saveRemoteSource(...)`，与 `remote_sources` 更新处于同一个 SQLite transaction；`MusicLibraryRepository.saveWebDavSource(...)` 不再在保存前先独立删除旧曲目。
- 新增自动化护栏：`EchoDatabaseHelperTest.saveRemoteSourceRollsBackTrackDeleteWhenSourceUpdateFails`。
- 验证点：当远程源更新失败时，旧远程缓存曲目删除会回滚，避免“源未更新但缓存曲目已丢失”的数据丢失状态；成功编辑仍会在同一 DB owner 内清理旧源曲目并保存新源信息。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.saveRemoteSourceRollsBackTrackDeleteWhenSourceUpdateFails --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机 WebDAV 源编辑/同步和播放恢复录屏与 logcat。

## 2026-07-07 WebDAV Source Edit Success Cleanup Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 远程源缓存数据一致性。
- 新增自动化护栏：`EchoDatabaseHelperTest.saveRemoteSourceUpdateDeletesOldCachedTracksAndKeepsSource`。
- 验证点：编辑已有 WebDAV 源成功时，会保留同一个 source id 并更新 `remote_sources` 信息；旧 `webdav:<sourceId>:` 缓存曲目会被清理；其他远程源曲目和本地曲目不会被误删。该成功路径与 `saveRemoteSourceRollsBackTrackDeleteWhenSourceUpdateFails` 一起覆盖编辑远程源的提交/回滚两侧。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.saveRemoteSourceUpdateDeletesOldCachedTracksAndKeepsSource --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机 WebDAV 源编辑/同步和播放恢复录屏与 logcat。

## 2026-07-07 Playback Position Settings Transaction Guard

- 覆盖矩阵：恢复与异常矩阵 / 冷启动恢复、后台被杀；数据库基线 / CRUD 操作事务边界。
- 新增自动化护栏：`EchoDatabaseHelperTest.savePlaybackPositionRollsBackTrackIdWhenPositionWriteFails`。
- 验证点：`EchoDatabaseHelper.savePlaybackPosition(...)` 会把 `playback_position_track_id` 与 `playback_position_ms` 放在同一个 SQLite transaction 内；当位置毫秒写入失败时，track id 写入也会回滚，避免冷启动恢复时出现“曲目 id 是新值但位置仍是旧值”的错配恢复状态。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.savePlaybackPositionRollsBackTrackIdWhenPositionWriteFails --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机冷启动/后台被杀恢复录屏与 logcat。

## 2026-07-07 Audio Specs Queue Mirror Transaction Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 本地歌曲播放、冷启动恢复（队列快照音频规格一致性）。
- 新增自动化护栏：`EchoDatabaseHelperTest.updateAudioSpecsRollsBackTrackUpdateWhenQueueMirrorFails`。
- 验证点：`EchoDatabaseHelper.updateAudioSpecs(...)` 更新 `tracks` 音频规格与 `playback_queue` 镜像处于同一 transaction；当队列镜像写入失败时，`tracks` 表音频规格更新回滚，避免曲库与冷启动队列快照 codec/bitrate 不一致。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.updateAudioSpecsRollsBackTrackUpdateWhenQueueMirrorFails --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：Robolectric/SQLite 层基线，不替代真机本地播放/队列恢复录屏与 logcat。

## 2026-07-07 Playlist Add Missing Row Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 播放历史与数据一致性、流媒体歌单同步后的本地歌单成员一致性。
- 修复点：`EchoDatabaseHelper.addTrackToPlaylist(...)` 现在会先确认 playlist 行存在；当 playlist 已缺失时返回 `false`，不会创建新的 dangling `playlist_tracks` 行。
- 新增自动化护栏：`EchoDatabaseHelperTest.addTrackToPlaylistMissingPlaylistReturnsFalseWithoutDanglingMembership`。
- 验证点：在迁移遗留或异常状态下 playlist 行已不存在时，添加歌单成员不会留下“playlist 不存在但成员已添加”的半提交状态；该路径与移出/清空/移动歌单成员缺失行护栏一起覆盖歌单 CRUD 的成员变更原子性。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.addTrackToPlaylistMissingPlaylistReturnsFalseWithoutDanglingMembership --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机本地歌单添加/流媒体歌单同步录屏与 logcat。

## 2026-07-07 Playlist Move Transaction Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 歌单成员顺序一致性、流媒体歌单同步后的本地歌单手动重排。
- 新增自动化护栏：`EchoDatabaseHelperTest.movePlaylistTrackAtSwapsByVisibleIndex`、`EchoDatabaseHelperTest.movePlaylistTrackAtMissingPlaylistReturnsFalseWithoutReorder`。
- 验证点：`EchoDatabaseHelper.movePlaylistTrackAt(...)` 按 UI 可见 index 与相邻成员交换 position；当 playlist 行缺失时，移动返回 `false` 且不重排 dangling membership，避免目标 playlist 不存在但本地歌单顺序仍被改动的半提交状态。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.movePlaylistTrackAtSwapsByVisibleIndex --tests app.yukine.data.EchoDatabaseHelperTest.movePlaylistTrackAtMissingPlaylistReturnsFalseWithoutReorder --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机本地歌单拖动/上下移动和播放队列联动录屏与 logcat。

## 2026-07-07 Missing Playlist Membership Mutation Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 歌单成员一致性、流媒体歌单同步后的本地歌单成员保护。
- 修复点：`EchoDatabaseHelper.addTrackToPlaylist(...)`、`removeTrackFromPlaylist(...)`、`clearPlaylistTracks(...)`、`movePlaylistTrackAt(...)` 现在会先确认 playlist 行存在；当 playlist 已被删除或迁移遗留只剩 dangling `playlist_tracks` 时，不会再新增、删除、清空或重排这些成员行。
- 新增/更新自动化护栏：`EchoDatabaseHelperTest.addTrackToPlaylistMissingPlaylistReturnsFalseWithoutDanglingMembership`、`removeTrackFromMissingPlaylistLeavesMembershipUntouched`、`clearMissingPlaylistLeavesMembershipUntouched`、`movePlaylistTrackAtMissingPlaylistReturnsFalseWithoutReorder`。
- 验证点：缺失 playlist 行时，添加返回 `false` 且不创建 dangling membership；移出/清空保持遗留 membership 不变；按 index 移动返回 `false` 且不重排，避免“操作失败但数据库成员已改变”的半提交语义。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.addTrackToPlaylistMissingPlaylistReturnsFalseWithoutDanglingMembership --tests app.yukine.data.EchoDatabaseHelperTest.removeTrackFromMissingPlaylistLeavesMembershipUntouched --tests app.yukine.data.EchoDatabaseHelperTest.clearMissingPlaylistLeavesMembershipUntouched --tests app.yukine.data.EchoDatabaseHelperTest.movePlaylistTrackAtMissingPlaylistReturnsFalseWithoutReorder --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机本地歌单添加/删除/清空/重排和流媒体歌单同步录屏与 logcat。

## 2026-07-07 WebDAV Source Delete Transaction Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 远程源缓存数据一致性、冷启动恢复中的 WebDAV 队列引用保护。
- 新增自动化护栏：`EchoDatabaseHelperTest.deleteRemoteSourceRollsBackTrackDeleteWhenSourceDeleteFails`。
- 验证点：`EchoDatabaseHelper.deleteRemoteSource(...)` 删除 `remote_sources` 行与清理对应 `webdav:<sourceId>:` 缓存曲目处于同一个 SQLite transaction；当远程源行删除失败时，缓存曲目删除会回滚，避免“源仍存在但缓存曲目已丢失”的数据丢失状态。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteRemoteSourceRollsBackTrackDeleteWhenSourceDeleteFails --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机 WebDAV 源删除、播放队列恢复和 logcat 验收。

## 2026-07-07 Stream Track Delete Reference Rollback Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 流媒体曲目删除、播放历史与队列引用一致性。
- 新增自动化护栏：`EchoDatabaseHelperTest.deleteStreamTracksRollsBackTrackDeleteWhenReferenceCleanupFails`。
- 验证点：`EchoDatabaseHelper.deleteStreamTracks()` 通过 `deleteTracksWhere(...)` 在同一个 SQLite transaction 内清理 stream 曲目、收藏、最近播放、play_events、歌单项和播放队列引用；当引用清理失败时，stream 曲目删除会回滚，避免“引用仍在但曲目已丢失”的孤儿数据状态。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteStreamTracksRollsBackTrackDeleteWhenReferenceCleanupFails --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机删除流媒体曲目/清空全部流媒体曲目后的播放队列、最近播放 UI 和 logcat 验收。

## 2026-07-07 WebDAV Cache-Only Delete Reference Rollback Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / WebDAV 同步刷新缓存、远程源缓存数据一致性。
- 新增自动化护栏：`EchoDatabaseHelperTest.deleteRemoteSourceTracksRollsBackWhenReferenceCleanupFails`。
- 验证点：`EchoDatabaseHelper.deleteRemoteSourceTracks(...)` 只清理指定 WebDAV source 的 `webdav:<sourceId>:` 缓存曲目，不删除 `remote_sources` 行；当 play_history 等引用清理失败时，缓存曲目删除会回滚，避免 WebDAV 同步/刷新缓存时出现“源仍存在但缓存曲目或历史引用半丢失”的状态。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteRemoteSourceTracksRollsBackWhenReferenceCleanupFails --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 Robolectric/SQLite 层基线，不替代真机 WebDAV 同步、播放队列恢复和最近播放 UI 验收。

## 2026-07-07 Playback/App Broad Unit Regression Gate

- 覆盖矩阵：P0 单元测试护栏；播放服务矩阵 / 队列恢复、通知控制、耳机断开、错误恢复、睡眠定时、MediaLibrary 过滤、Session seek 边界。
- 验证目的：在继续真机矩阵前，先确认本轮 `EchoPlaybackService` owner 收敛、`PlaybackQueueManager` 恢复规则、`PlaybackSessionPlayer` seek 边界和数据库事务护栏没有破坏现有 Robolectric/JVM 基线。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.* --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：`adb devices` 仍未列出设备；这些自动化结果只证明单元层和 Robolectric 层回归通过，不替代 `PLAYBACK_SERVICE_STABILITY_MATRIX.md` 中后台播放、锁屏/通知控制、蓝牙/耳机/来电中断和杀进程恢复的真机证据。

## 2026-07-07 Queued Streaming Placeholder Playlist Delete Guard

- 覆盖矩阵：数据库基线 / CRUD 操作事务边界；播放服务矩阵 / 冷启动队列恢复、流媒体队列恢复。
- 修复点：`EchoDatabaseHelper.deletePlaylist(...)` 中 streaming orphan 删除路径复用现有 `deleteTracksByIds(...)` 引用清理 owner，并移除重复的 `deleteStreamingTracksByIds(...)` 纯转发/重复实现，避免两套引用清理规则分叉。
- 新增自动化护栏：`EchoDatabaseHelperTest.deletePlaylistKeepsQueuedStreamingPlaceholderForPlaybackRestore`。
- 验证点：删除只包含某个 streaming placeholder 的 playlist 时，如果该 placeholder 仍在 playback queue 中，曲目和队列快照必须保留，当前队列索引不能被重写；这样冷启动/杀进程恢复仍能看到原队列，而 playlist membership 已被删除。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deletePlaylistKeepsQueuedStreamingPlaceholderForPlaybackRestore --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 SQLite/Robolectric 层队列恢复基线，不替代真机冷启动、杀进程恢复和流媒体队列播放录屏/logcat。

## 2026-07-07 Streaming Guard And Debug APK Build Gate

- 覆盖矩阵：P0 单元测试护栏；播放服务矩阵 / 真机验收前 APK 可安装性前置门禁。
- 验证目的：在当前大量播放/数据库稳定性改动之后，重新确认 `StreamingViewModelTest` 仍保持绿色，并且 `:app:assembleDebug` 能产出 debug APK，避免进入设备矩阵前才发现核心编译或打包失败。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
- 产物入口：`app/build/outputs/apk/debug/app-debug.apk`。
- 设备状态：本门禁只证明核心 streaming 单测与 debug APK 构建通过；后台播放、锁屏/通知、蓝牙/耳机/来电、冷启动/杀进程恢复仍需连接设备后按 `PLAYBACK_SERVICE_STABILITY_MATRIX.md` 录屏和采集 logcat。

## 2026-07-07 Playback Queue Restore Owner Collapse

- 覆盖矩阵：P1 收敛已有抽象层；播放服务矩阵 / 冷启动队列恢复、杀进程恢复的非真机 owner 边界。
- 修复点：删除纯转发 `PlaybackQueueRestoreOwner` 与对应孤立转发测试；`EchoPlaybackService` 直接调用现有
  `PlaybackQueueManager.restorePlaybackQueue()`、`restoreLastPlayback(...)`、`setPlaybackRestoreEnabled(...)`。
- 边界说明：队列恢复、resume 标记、持久化读写和 enablement 状态仍由 `PlaybackQueueManager` / `PlaybackQueueStore`
  拥有；Service 只把 `RestorePlaybackResult` 映射到已有生命周期动作（创建 player、prepare、publish state），没有新增
  Manager/Coordinator/Controller。
- 架构护栏：`MainActivityArchitectureContractTest` 现在明确禁止 `PlaybackQueueRestoreOwner` 回归，同时继续禁止 Service 直接读取
  playback queue/position/resume repository 状态。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1` — 通过（BUILD SUCCESSFUL）。
  - `git diff --check` — 通过；仅有 Windows CRLF 工作区提示。
  - `adb devices` — 未发现设备。
- 设备状态：这是架构收敛与非真机回归门禁，不替代冷启动/杀进程恢复的真机录屏与 logcat。

## 2026-07-07 Repository-Level Unit Gate Flake Fix

- 覆盖矩阵：P0 单元测试护栏；播放服务矩阵 / 人工矩阵前后仓库级 `testDebugUnitTest` 门禁。
- 发现问题：`./gradlew.bat testDebugUnitTest --console=plain` 首次运行暴露 `LibraryGroupsRenderControllerTest.ignoresStaleArtistInfoWhenAnotherArtistIsOpened` 偶发失败；focused rerun 可过，说明测试只等待 pending UI 回调数量，可能只执行到预览/旧请求回调而未等到最终 LiSA 在线简介渲染。
- 修复点：该测试现在同步收集 pending UI 回调，并在 `waitUntil` 中持续 drain 回调直到当前 track list 是 LiSA 且 artist intro 包含 `LiSA online`，再断言旧 Aimer 简介未污染当前页面。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.LibraryGroupsRenderControllerTest.ignoresStaleArtistInfoWhenAnotherArtistIsOpened --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat testDebugUnitTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 JVM/Robolectric 层门禁恢复，不替代真机播放服务矩阵。

## 2026-07-07 Streaming Restore Owner Collapse

- 覆盖矩阵：P1 收敛已有抽象层；播放服务矩阵 / 冷启动队列恢复、杀进程恢复、流媒体队列恢复的非真机 owner 边界。
- 修复点：删除纯转发 `PlaybackQueueStreamingRestoreOwner` 与对应孤立转发测试；`PlaybackMediaSourceProvider` 直接实现
  `PlaybackQueueManager.StreamingRestoreProvider`，让 restored track lookup 与 streaming header restore 留在既有 URI/media-item/cache-key owner 内。
- 边界说明：`EchoPlaybackService` 只把既有 `mediaSourceProvider` 传给 `PlaybackQueueManager`；Service 不直接读取
  `StreamingPlaybackHeaderStore`，也不新增匿名 `StreamingRestoreProvider` 或新的 Manager/Coordinator/Controller。
- 架构护栏：`MainActivityArchitectureContractTest` 禁止 `PlaybackQueueStreamingRestoreOwner` 回归，并要求 provider 继续实现
  `PlaybackQueueManager.StreamingRestoreProvider`。
- 行为护栏：`PlaybackMediaSourceProviderTest.streamingRestoreProviderPortDelegatesToHeaderStore` 覆盖 queue restore port，验证 restored track lookup 与 data-path header restore 都委托到既有 `StreamingPlaybackHeaderStore`，避免只依赖字符串契约。
- 验证命令：
  - `./gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest.streamingRestoreProviderPortDelegatesToHeaderStore --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :feature:playback:compileDebugKotlin :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 owner 收敛和 JVM/Robolectric 层恢复护栏，不替代冷启动/杀进程恢复、后台播放、锁屏/通知、耳机/蓝牙/来电中断的真机录屏和 logcat。

## 2026-07-07 P0 Automated Gate Refresh

- 覆盖矩阵：P0 单元测试护栏、`EchoDatabaseHelper` 测试基线、feature playback JVM/Robolectric 回归、debug APK 打包可用性。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./scripts/p0-stability-gate.ps1 -SkipDeviceProbe -IncludeAssemble` — 通过；报告 `app/build/p0-stability-gate/20260707-091345.md`。
- Gate 报告结果：`playback-stability-smoke.ps1` 语法通过、`StreamingViewModelTest` 通过、`EchoDatabaseHelperTest` 通过、`:feature:playback:testDebugUnitTest` 通过、`:app:assembleDebug` 通过，APK `app/build/outputs/apk/debug/app-debug.apk` 大小 36,332,646 bytes。
- 设备状态：`adb devices` 当前未列出设备；本次 gate 使用 `-SkipDeviceProbe`，因此不替代播放服务稳定性矩阵中的本地播放、后台/锁屏/通知控制、冷启动/杀进程恢复、耳机/蓝牙/来电中断真机验收。

## 2026-07-07 MuMu Launch Smoke Crash Fix

- 覆盖矩阵：播放服务稳定性矩阵 / debug APK 安装、MainActivity 冷启动、播放服务创建、force-stop 后重启、fatal-crash logcat 采样。
- 发现问题 1：MuMu `127.0.0.1:7555` 首次启动 debug APK 后，`MainActivityBase.initializeLibraryGateway()` 在 `initializeRouteStoresAndStatus()` 之前执行，导致 Hilt 提供的 `MainLibraryGatewayFactory` 收到 null `routeActions` 并在 Kotlin 非空参数处崩溃。
- 修复点 1：`MainActivityBase.onCreate()` 现在在 gateway 绑定前初始化 route/status/store owners；`MainActivityArchitectureContractTest.mainActivityInitializesRouteStatusStoresBeforeGatewayBindings` 防止顺序回退。
- 发现问题 2：修复 Activity 启动后，`EchoPlaybackService.onCreate()` 在 `playbackNotificationManager` 初始化前捕获 `playbackNotificationManager::mediaMetadataForTrack`，服务创建时触发 NPE。
- 修复点 2：早期 queue preparation owner 改为延迟 lambda，notification manager 尚未初始化时返回 null metadata；后续已初始化路径仍可使用正常 metadata provider。`MainActivityArchitectureContractTest.playbackServiceDoesNotCaptureNotificationManagerBeforeItIsInitialized` 覆盖危险初始化窗口。
- 设备脚本修复：`playback-stability-smoke.ps1` 默认 evidence 目录名会将设备 serial 中的冒号替换为 `_`，避免 Windows 路径下 `adb pull` 失败。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest.mainActivityInitializesRouteStatusStoresBeforeGatewayBindings --tests app.yukine.MainActivityArchitectureContractTest.playbackServiceDoesNotCaptureNotificationManagerBeforeItIsInitialized :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./scripts/playback-stability-smoke.ps1 -SkipBuild -SkipManualCheckpoint -DeviceSerial 127.0.0.1:7555 -LaunchWaitSeconds 8 -RelaunchWaitSeconds 8` — 通过；证据目录 `app/build/playback-stability/20260707-094019-127.0.0.1_7555`。
- 设备状态：MuMu smoke 已证明安装、冷启动、进程存活、force-stop 后重启和 fatal-crash 采样；手工播放/暂停/切歌/seek、后台、锁屏、通知、耳机/蓝牙/来电中断仍需人工录屏与 logcat，不能标为完成。

## 2026-07-07 Startup Page Background Restore Fix

- 覆盖矩阵：启动稳定性与设置持久化回归；不替代播放服务人工矩阵。
- 发现问题：更换页面背景后，重新进入应用时导航根先绑定空的 `SettingsViewModel.chromeState`；持久化的 `PageBackgrounds` 虽已加载进 `MainSettingsStore`，但只有进入设置页渲染时才会同步到 chrome state，导致背景看起来没有固化。
- 修复点：`SettingsContextProvider` 创建完成后立即刷新一次 settings context，把已加载的 `pageBackgrounds` 和 `nowPlayingGesturesEnabled` 推入 `SettingsViewModel.chromeState`，让 `EchoNavGraph` 冷启动时直接拿到持久化背景。
- 回归护栏：`SettingsViewModelTest.updateSettingsContextPublishesPreferencesAndRuntimeStatus` 覆盖 chrome state 行为；`MainActivityArchitectureContractTest.mainActivityPushesLoadedSettingsContextBeforeSettingsPageNavigation` 覆盖启动 wiring，防止再次依赖“进入设置页”触发同步。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.SettingsViewModelTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `adb -s 127.0.0.1:7555 install -r app/build/outputs/apk/debug/app-debug.apk && adb -s 127.0.0.1:7555 shell am start -W -n app.yukine/.MainActivity` — MuMu 冷启动通过，logcat 300 行未发现 `FATAL EXCEPTION` / `AndroidRuntime`。
- 设备状态：已安装到 MuMu `127.0.0.1:7555` 并完成冷启动崩溃采样；背景视觉需在已有自定义背景数据的设备上复核。

## 2026-07-07 EchoDatabaseHelper Concurrent Playback Restore Baseline

- 覆盖矩阵：P0 `EchoDatabaseHelper` 测试基线 / 并发写入保护；播放服务矩阵 / 冷启动队列恢复、杀进程恢复的持久化前置护栏。
- 新增测试：
  - `EchoDatabaseHelperTest.concurrentPlaybackQueueWritesKeepOneCompleteQueueSnapshot`：多线程同时保存不同播放队列时，最终数据库只能出现一个完整队列快照，不能混入不同 writer 的队列行或错配 `playback_queue_index`。
  - `EchoDatabaseHelperTest.concurrentPlaybackPositionWritesKeepTrackAndPositionPairAtomic`：多线程同时保存播放进度时，最终 `playback_position_track_id` 与 `playback_position_ms` 必须来自同一次写入，避免冷启动恢复成错配曲目/进度。
- 已有基线：migration 测试覆盖 legacy v1 和 partial playback queue schema；事务回滚测试覆盖 tracks、remote source、play history、playlist、queue、playback position 等路径；并发 upsert 测试覆盖同 ID 和多 ID track 写入。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --tests StreamingViewModelTest --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：这是 JVM/Robolectric 层持久化护栏，不替代 MuMu/真机上的冷启动队列恢复、杀进程恢复录屏和 logcat。

## 2026-07-07 Paused Playback Position Retention Fix

- 覆盖矩阵：播放服务稳定性矩阵 / 暂停/恢复、进度保存、冷启动恢复位置前置护栏。
- 发现问题：前一轮 `PlaybackPlayerStateOwner` 只在 `player.isPlaying()` 时对停滞的 raw `currentPosition` 做单调时钟补偿；暂停后如果 Media3/MuMu raw position 回到 `0`，暂停态快照会把最后有效进度覆盖成 `0:00`。
- 修复点：
  - `PlaybackPlayerStateOwner.positionMs()` 在暂停态返回 raw position 与最后估算进度的较大值，避免暂停瞬间清零。
  - 播放从暂停或显式 seek 恢复时，如果 raw position 仍低于已知可信进度，先保留该进度，再在后续播放 tick 中继续推进。
  - `EchoPlaybackService.seekTo(...)`、恢复位置 seek 和队尾归零 seek 改为用 `setPositionEstimate(...)` seed 目标位置，而不是一律 reset。
- 新增自动化护栏：
  - `PlaybackPlayerStateOwnerTest.keepsEstimatedPositionWhenPausedPlayerReportsZero`
  - `PlaybackPlayerStateOwnerTest.resumesProgressFromPausedEstimateWhenPlayerStillReportsZero`
  - `PlaybackPlayerStateOwnerTest.explicitPositionEstimateSeedsSeekTargetWhenPlayerReportsZero`
- MuMu 设备证据：当前 debug APK 安装到 `127.0.0.1:7555` 后播放再暂停，暂停按钮状态为 `content-desc=播放`；UI dump 显示暂停采样 `0:04/4:25`、稍后采样 `0:07/4:25`，数据库 raw dump 显示 `playback_position_ms4765` / `playback_position_ms7488`，fatal 过滤为空。证据文件在 `app/build/yukine-pause-playing.xml`、`app/build/yukine-pause-paused.xml`、`app/build/yukine-pause-paused-later.xml`、`app/build/yukine-pause-retain-logcat.txt`。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest --tests app.yukine.playback.PlaybackPlayerStateOwnerTest --tests app.yukine.playback.PlaybackStateSnapshotOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
- 设备状态：本次覆盖 MuMu 播放页暂停后进度不归零和数据库保存非零进度；通知/锁屏暂停恢复、耳机/蓝牙/来电中断仍需按矩阵补录屏与 logcat。

## 2026-07-07 Queue Sheet Open/Close Freeze Fix

- 覆盖矩阵：播放服务稳定性矩阵 / 播放队列打开、关闭、队列渲染响应性前置护栏。
- 发现问题：队列页行 key 通过 `TrackRowKeyPolicy.occurrenceKey(tracks, index)` 逐行向前扫描；`QueueViewModel.bind(...)`、曲库列表和合集列表在同一轮渲染中重复调用时会把大列表构建放大到 O(n²)。底部队列弹层打开时还会为整条队列一次性创建 `QueueTrackActions` 和全量 key list，增加主线程分配压力。
- 修复点：
  - `TrackRowKeyPolicy.occurrenceKeys(...)` 一次遍历生成重复曲目稳定 key，保持 `id:occurrence` 语义不变。
  - `QueueViewModel`、`TrackListRenderController`、`CollectionsRenderController` 改用批量 key，避免大队列/大曲库平方级构建。
  - `QueueDestination` 改为按可见行懒创建 `QueueTrackActions`；`QueueScreen` 保留旧 list API，并新增 lazy action 入口；拖拽状态清理不再每次重组分配 `tracks.map { key }`。
- 新增自动化护栏：`TrackRowKeyPolicyTest.occurrenceKeysMatchPerRowOccurrenceKeyForDuplicateTracks` 与 `occurrenceKeysReturnsEmptyListForMissingTracks`。
- 验证命令：
  - `./gradlew.bat :app:testDebugUnitTest --tests app.yukine.TrackRowKeyPolicyTest --tests app.yukine.queue.QueueViewModelTest --tests app.yukine.queue.QueueDestinationTest :feature:ui-common:compileDebugKotlin :feature:navigation:compileDebugKotlin :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` — 通过（BUILD SUCCESSFUL）。
  - `./gradlew.bat :app:assembleDebug --console=plain` — 通过（BUILD SUCCESSFUL）。
  - MuMu `127.0.0.1:7555` 安装启动后连续 3 次点击底部队列按钮并用 Back 关闭；UI dump 证据 `app/build/yukine-queue-open-1.xml` 到 `app/build/yukine-queue-open-3.xml` 显示队列 sheet（30 曲目），`app/build/yukine-queue-close-1.xml` 到 `app/build/yukine-queue-close-3.xml` 关闭后不再包含队列 sheet；logcat 700 行未发现 `FATAL EXCEPTION`、`ANR in app.yukine` 或 `Input dispatching timed out`。
- 设备状态：MuMu 自动开关烟测证明未卡死/未崩溃；大于当前 30 首队列的极端大队列仍建议手动复测手感。
