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


## 历史切片 delta（已归档）

本文件曾记录每个 playback 切片的 P1-P6 Delta（约 4400 行）。这些内容属于 git log 而非基线文档，已于 2026-07-05 移除以恢复可审查性。历史 delta 见：

```bash
git log --oneline docs/PLAYBACK_P0_BASELINE_2026-06-29.md
```
