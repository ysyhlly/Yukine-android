# ECHO/YUKINE 架构优化执行摘要

## 目标

把既有重构方案整理成可执行摘要，并继续按绞杀者模式推进，优先收敛 `EchoPlaybackService` 的重职责。

## 当前策略

1. 先保留现有运行路径。
2. 先拆责任边界，不急着做目录级重组。
3. 先把可验证的 owner 抽出来，再逐步替换旧实现。

## 已完成

- `PlaybackLyricsManager` 已接管歌词相关责任。
- `PlaybackNotificationManager` 已接管通知构建与更新。
- `PlaybackMediaLibraryCallback` 已接管媒体库回调适配。
- `PlaybackAudioEffectManager`、`PlaybackQueueStore`、`PlaybackSessionManager`、`PlaybackNotificationChannelOwner` 已落地。
- `PlaybackVisualizationAnalyzer` 已接管波形/频谱快照、缓存进度、暖机延迟与后台生成调度。
- `PlaybackQueueManager` 已接管队列装载、恢复、增删改、重排与下一首推进。
- `PlaybackRuntimeStateManager` 已接管播放模式、速度、音量、ReplayGain 与音频焦点应用。
- `PlaybackMediaLibraryCallback` 已接管 MediaItem 与 Track 的解析回填。
- `PlaybackStatePublisher` 已接管歌词、通知和 widget 的状态发布，并统一了通知触发入口。

## 这一步的改动

- `EchoPlaybackService` 只保留协调与转发。
- 视觉分析相关状态从服务中移除。
- 波形/频谱生成逻辑转移到 `PlaybackVisualizationAnalyzer`。
- 修复了视觉缓存长度读取的实现。
- 队列与运行时播放状态逻辑从服务中进一步抽出，服务体量缩到 2780 行。
- 通知触发从服务直接调用收束到 `PlaybackStatePublisher.publishNotification(...)`。

## 结果

- `:app:compileDebugKotlin` 通过。
- `:app:compileDebugJavaWithJavac` 通过。
- `PlaybackQueueManagerTest` 和 `PlaybackRuntimeStateManagerTest` 通过。

## 下一步

继续收口 `EchoPlaybackService` 内剩余的大块预缓存与播放状态协调逻辑，然后补契约测试，最后再考虑目录级整理。

## 2026-06-27 ADDENDUM: Playback shutdown boundary

- `PlaybackShutdownCoordinator` now separates playback-level `releasePlaybackResources()` from service-level `releaseServiceResources()`.
- `stopAndClear()` no longer reuses full service teardown, so it does not close schedulers/receiver early and no longer double-releases the player.
- `PlaybackShutdownCoordinatorTest` covers the two cleanup levels; serial compile and targeted unit test passed.
## 2026-06-27 ADDENDUM: Playback position boundary

- `PlaybackPositionManager` now owns restored position state, explicit resume positions, clamping, throttled saves, current-position reset, and stop/clear position cleanup.
- `EchoPlaybackService` no longer stores playback-position recovery fields or calls playback-position store methods directly.
- `PlaybackQueueManager.playQueue(..., startPositionMs)` now passes explicit start positions into the in-memory restored-position owner, fixing the immediate prepare path.
- Focused position, queue, and architecture contract tests passed after serial compile.
## 2026-06-27 ADDENDUM: Playback sleep timer boundary

- `PlaybackSleepTimerManager` now owns sleep timer state, remaining-time calculation, timer callback scheduling, expiry pause, and cancel/publish policy.
- `EchoPlaybackService` only provides handler scheduling and playback action callbacks; timer end time and runnable are no longer service fields.
- Focused sleep timer test and architecture contract passed after serial compile.
### 2026-06-27 addendum - Playback Wi-Fi lock owner
- Added PlaybackWifiLockManager to own streaming-track lock acquire/release guards.
- EchoPlaybackService now keeps only Android WifiLock adapter construction and delegates playback-state decisions to the manager.
- Added PlaybackWifiLockManagerTest and the playbackWifiLockIsOwnedOutsideEchoPlaybackService architecture contract.
- Verified serially with compileDebugKotlin/compileDebugJavaWithJavac and the focused Wi-Fi lock tests using --max-workers=1.
### 2026-06-27 addendum - Playback noisy receiver owner
- Added PlaybackNoisyReceiverManager for ACTION_AUDIO_BECOMING_NOISY receiver creation, idempotent registration, unregister cleanup, and platform already-unregistered tolerance.
- EchoPlaybackService now provides only the Android register/unregister adapter and pause-if-playing action.
- Added PlaybackNoisyReceiverManagerTest and the playbackNoisyReceiverIsOwnedOutsideEchoPlaybackService architecture contract.
- Verified serially with compileDebugKotlin/compileDebugJavaWithJavac and the focused noisy receiver tests using --max-workers=1.
### 2026-06-27 addendum - Playback progress update owner
- Added PlaybackProgressUpdateManager for one-second playback progress ticks, including scheduler cancellation, active playback/preparing checks, publishState, and throttled position persistence.
- EchoPlaybackService now delegates progress start/stop to the manager and no longer stores progressRunnable or directly schedules it on mainHandler.
- Added PlaybackProgressUpdateManagerTest and the playbackProgressUpdatesAreOwnedOutsideEchoPlaybackService architecture contract.
- Verified serially with compileDebugKotlin/compileDebugJavaWithJavac and the focused progress update tests using --max-workers=1.
### 2026-06-27 addendum - Playback state broadcast owner
- Extended PlaybackStatePublisher to own playback listener registration, buffering fan-out, and state fan-out while keeping lyrics/notification/widget publication in one place.
- EchoPlaybackService now delegates listener registration, unregistration, state publish, and buffering publish to the publisher instead of holding its own listener set.
- Added/updated PlaybackStatePublisherTest and the playbackStateBroadcastsAreOwnedOutsideEchoPlaybackService architecture contract.
- Verified serially with compileDebugKotlin/compileDebugJavaWithJavac and the focused state-broadcast tests using --max-workers=1.
### 2026-06-27 addendum - Playback error recovery owner
- Added PlaybackErrorRecoveryManager to own streaming retry tracking, delayed re-prepare, skip-on-repeat-failure, and terminal playback error messaging.
- EchoPlaybackService now forwards playback-ready and playback-error events to the recovery manager instead of carrying the retry state itself.
- Added PlaybackErrorRecoveryManagerTest and the playbackErrorRecoveryIsOwnedOutsideEchoPlaybackService architecture contract.
- Verified serially with compileDebugKotlin/compileDebugJavaWithJavac and the focused error-recovery tests using --max-workers=1.
## NOTE 29 - Playback mode owner consolidation (2026-06-27)
- Owner clarified: PlaybackRuntimeStateManager now owns shuffleEnabled, repeatMode, playbackSpeed, appVolume, ReplayGain, and concurrent playback state.
- Path shortened: EchoPlaybackService seeds runtime state from MusicLibraryRepository, then delegates playback-mode application and snapshot reads to PlaybackRuntimeStateManager.
- Guard: MainActivityArchitectureContractTest.playbackRuntimeStateIsOwnedOutsideEchoPlaybackService prevents those fields from returning to EchoPlaybackService.
- Verification: serial `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackRuntimeStateManagerTest / architecture contract passed.

## NOTE 30 - Playback readiness and error state owner consolidation (2026-06-27)
- Owner clarified: PlaybackRuntimeStateManager now owns preparing and errorMessage in addition to playback mode/speed/volume/focus state.
- Path shortened: EchoPlaybackService writes readiness and error state through PlaybackRuntimeStateManager setters and snapshots read the owner directly.
- Guard: PlaybackRuntimeStateManagerTest covers preparing/error transitions; MainActivityArchitectureContractTest asserts EchoPlaybackService no longer declares `private boolean preparing` or `private String errorMessage`.
- Verification: serial `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackRuntimeStateManagerTest / architecture contract passed.
## NOTE 31 - Playback transition state owner (2026-06-27)
- Owner clarified: PlaybackTransitionStateManager now owns lastMarkedTrack and fadeOutAdvancing state.
- Path shortened: EchoPlaybackService now records playback marking and fade-out transition state through PlaybackTransitionStateManager instead of holding private transition fields.
- Guard: PlaybackTransitionStateManagerTest covers set/clear behavior; MainActivityArchitectureContractTest.playbackTransitionStateIsOwnedOutsideEchoPlaybackService rejects private lastMarkedTrack/fadeOutAdvancing fields returning to EchoPlaybackService.
- Verification: serial `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackTransitionStateManagerTest / architecture contract passed.
## NOTE 32 - Playback queue runtime mirror state owner (2026-06-27)
- Owner clarified: PlaybackQueueRuntimeStateManager now owns playerMirrorsQueue state.
- Path shortened: EchoPlaybackService no longer carries the private playerMirrorsQueue field; Media3 queue mirror reads/writes go through PlaybackQueueRuntimeStateManager.
- Guard: PlaybackQueueRuntimeStateManagerTest covers mirror state transitions; MainActivityArchitectureContractTest.playbackQueueRuntimeStateIsOwnedOutsideEchoPlaybackService rejects the private field and direct assignments returning to EchoPlaybackService.
- Verification: serial `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackQueueRuntimeStateManagerTest / architecture contract passed.
- Follow-up: currentIndex remains in EchoPlaybackService and is the next high-value queue state migration target.
## NOTE 33 - Playback queue current index owner (2026-06-27)
- Owner clarified: PlaybackQueueRuntimeStateManager now owns currentIndex alongside playerMirrorsQueue.
- Path shortened: EchoPlaybackService no longer declares a private currentIndex field; queue cursor reads/writes go through currentIndex()/setCurrentIndex()/setClampedCurrentIndex() backed by PlaybackQueueRuntimeStateManager.
- Guard: PlaybackQueueRuntimeStateManagerTest covers currentIndex set/clamp behavior; MainActivityArchitectureContractTest.playbackQueueRuntimeStateIsOwnedOutsideEchoPlaybackService rejects private currentIndex fields and direct currentIndex assignments returning to EchoPlaybackService.
- Verification: serial `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackQueueRuntimeStateManagerTest / architecture contract passed.
- Follow-up: queue cursor algorithms still live in EchoPlaybackService fallback paths; next migration can move those fallback mutations into PlaybackQueueManager or the queue runtime owner now that the state source is centralized.
## NOTE 34 - Playback queue fallback algorithm removal (2026-06-27)
- Owner clarified: PlaybackQueueManager is now the only owner for public queue mutation algorithms such as append, advance-next, move, remove, retain, clear, and replace queued tracks.
- Path shortened: EchoPlaybackService public queue entry points now delegate to PlaybackQueueManager instead of carrying duplicate fallback algorithms for queue mutation and collapse behavior.
- Guard: MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster rejects old fallback markers such as wasEmpty queue append handling, removedBeforeCurrent removal logic, targetAlreadyQueued collapse logic, random next-index selection, and replaceAndCollapseQueuedTrack returning to EchoPlaybackService.
- Verification: serial `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackQueueManagerTest / architecture contract passed.
- Follow-up: skipToPrevious and replaceCurrentTrackAndResume still contain direct queue cursor/current-track mutation in EchoPlaybackService and are candidates for the next PlaybackQueueManager slice.
## NOTE 35 - Playback previous and current-track recovery queue owner (2026-06-27)
- Owner clarified: PlaybackQueueManager now owns skip-to-previous queue cursor mutation and current-track replacement/resume queue mutation.
- Path shortened: EchoPlaybackService keeps the >3s seek-to-zero branch and external recovery callbacks, but delegates previous-track cursor movement and replaceCurrentTrackAndResume queue updates to PlaybackQueueManager.
- Guard: PlaybackQueueManagerTest covers skipToPrevious and replaceCurrentTrackAndResume recovery scheduling; MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster rejects direct previous cursor mutation, direct queue.set(currentIndex(), replacement), and inline streaming recovery recording returning to EchoPlaybackService.
- Verification: serial `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackQueueManagerTest / architecture contract passed.
- Follow-up: remaining queue-facing service code is mostly playback preparation/reuse, current-track replacement helper, and Media3 mirrored-queue integration.
## NOTE 36 - Playback mirrored queue reuse owner (2026-06-27)
- PlaybackQueueManager now owns the mirrored-queue reuse decision and queue-side reuse state reset.
- EchoPlaybackService.seekExistingMirroredQueue(...) now delegates to PlaybackQueueManager.reuseMirroredQueueIfAvailable(...), while Media3 player seek/play remains in the service boundary.
- Added PlaybackQueueManagerTest coverage for successful reuse and failed-seek mirror cleanup; strengthened the playback queue mutation architecture contract.
- Verified serially with `gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused PlaybackQueueManagerTest / architecture contract.
## NOTE 37 - Playback mirrored queue preparation owner (2026-06-27)
- PlaybackQueueManager now owns mirrored-queue preparation eligibility and header-restore iteration.
- EchoPlaybackService.prepareMirroredQueue(...) now consumes PlaybackQueueManager.mirroredQueueTracksForPreparation() and keeps only MediaSource construction plus ExoPlayer calls.
- PlaybackQueueManagerTest covers snapshot/header restore and unplayable-track rejection; the architecture contract rejects the old queueTrack Uri/header loop in EchoPlaybackService.
- Verified serially with focused PlaybackQueueManagerTest / architecture contract and compileDebugKotlin/compileDebugJavaWithJavac using --max-workers=1.
## 2026-06-27 DIRECTION PIVOT: stabilization before more extraction

- New controlling doc: `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`.
- This pivot supersedes the previous default of continuing fast owner/manager extraction when the two conflict.
- Freeze broad architecture expansion first: do not add new `Manager`, `Coordinator`, `Controller`, `Bindings`, or `Gateway` layers by default.
- Stabilize the dirty migration surface, record current counts, and prefer reviewable commits/checkpoints before more migration.
- Reduce existing over-abstraction: merge/delete forwarding-only owners, shrink oversized provider/listener interfaces, and shorten UI -> service/data call chains.
- Do not expand `PlaybackQueueManager.QueueProvider` or similar large interfaces without a prior split/merge/inline plan.
- String-based architecture contracts are not enough for fragile flows; pair them with behavior tests, dependency-direction checks, integration smoke, or device evidence.
- Continue P1/P2 only after a slice demonstrably reduces net files, methods, state sources, dependencies, or call-chain length.
## 2026-06-27 ????

- ???????????/????????????? owner ????
- `EchoPlaybackService` ???? `MainActivity` ???????????/???????? launcher intent?
- `PlaybackQueueManager.QueueProvider` ??????????????????????????????
- ?????????????????????????????
- ???????? `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`???? P1/P2 ????????????????

## 2026-06-27 current slice note

- `EchoPlaybackService` no longer depends on `MainActivity` for launch intent creation.
- `samePlaybackUri` was removed from the service boundary and remains as direct comparison logic inside the relevant playback owners.
- `PlaybackQueueManagerTest` was kept on Robolectric with `sdk = [34]` and the focused playback contract/tests passed.
- `StreamingPlaybackTaskScheduler` now owns the task queue interface directly; the adapter file was deleted and the focused scheduler test was renamed accordingly.
- The scheduler binding in `MainActivity` is now direct, so the streaming task queue path has one fewer forwarding layer.
- `PlaybackStartController` no longer uses the temporary `PlaybackController` facade. It receives the streaming resolver, local track-list player, and playback-result applier directly, removing the `PlaybackStartControllerAdapter` hop from the playback start path.
- The old `app.yukine.playback.PlaybackController` / `PlaybackServiceController` facade and its fake test double were deleted because production no longer referenced them.
- `docs/MVVM_MIGRATION_HANDOFF.md` now marks the old playback facade task as retired history rather than current migration work.
- The old 7.3.1 facade plan is treated as retired history in the migration ledger, not an active to-do item.
- Current playback migration work now continues from the streamlined shell/service/boundary paths, not from the retired playback facade track.
- `NetworkRequestController` now implements `NetworkDialogController.Listener` directly, replacing the forwarding-only `NetworkDialogEventController` in the network dialog path.
- `MainActivity` now passes `networkRequestController` directly to `NetworkDialogController`, removing one network dialog wiring object without moving the status or network operation policy.
- `NetworkRequestControllerTest` covers the dialog listener path; `MainActivityArchitectureContractTest.mainActivityDelegatesNetworkOperationsThroughRequestController` rejects the old event-controller bridge.
- Verified serially with `gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.NetworkRequestControllerTest --tests app.yukine.MainActivityArchitectureContractTest.mainActivityDelegatesNetworkOperationsThroughRequestController --console=plain`.
- `StreamingGatewayEventController` was removed first; the remaining thin `StreamingGatewayController` was later folded into direct `MainActivity` helper methods for provider refresh and endpoint application.
- `MainActivity` no longer keeps a gateway controller field or construction path; `refreshStreamingProviders()` and `applyStreamingGatewayEndpoint(...)` call `StreamingViewModel` / `StreamingGatewaySettingsStore` directly.
- The architecture contract rejects the old event-controller and gateway-controller bridges while preserving provider refresh, endpoint normalization, render, and status publication behavior.
- Verified serially with `gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest.mainActivityDoesNotDirectlyOwnStreamingRepositoryProvider --tests app.yukine.MainActivityArchitectureContractTest.streamingGatewaySettingsBoundaryIsKotlin --tests app.yukine.MainActivityArchitectureContractTest.settingsEventsStayInSettingsViewModel --console=plain`.
- `NowPlayingRenderController` was deleted after rechecking the active runtime path: `NowPlayingDestination` collects `NowPlayingViewModel.uiState`, while the old controller only created an unpublished `NowPlayingUiState` and returned whether a current track existed.
- `MainActivity.updateNowPlayingContent()` now checks the playback snapshot directly for the lyrics refresh fallback, and the unused `renderNowPlaying()` method was removed.
- The now-playing architecture contract now rejects `NowPlayingRenderController` and the stale `renderNowPlaying()` entry point.
- `MainActivity` one-hop wrappers were further reduced: `removeQueueTrack`, `moveQueueTrack`, `syncUpdatedStreamQueue`, `retainPlaybackTracks`, `syncSelectedPlaylistFromStreaming`, and `remoteSourceName` were removed.
- Their call sites now enter the real owners directly: `QueueActionController`, `NowPlayingViewModel`, `StreamingPlaylistController`, and `MainLibraryStore`.
- The architecture contract now rejects the removed private wrappers while preserving the direct owner calls.
- `StreamingSearchEventController` was removed as a pure forwarding layer; `MainActivity` now wires `StreamingSearchRenderController.Listener` inline to `DefaultStreamingSearchActionHandler`, playlist/recommendation/dialog owners, and `StreamingViewModel.updateStreamingSearchChrome(...)`.
- `LibraryDocumentGatewayBindings` was renamed to `ContentResolverLibraryDocumentGateway`, leaving no root-package `*Bindings` files while preserving M3U import/export document behavior.
- WebDAV remote-source playback now goes through `NetworkSourcesEventController.playRemoteSourceTracks(...)` from both source rows and source track-list actions; the duplicate `MainActivity.playRemoteSourceTracks(...)` logic was removed.
- Onboarding actions now call `OnboardingController` directly from `OnboardingActions`; the private onboarding action wrappers were removed from `MainActivity`.
- Streaming playlist import refresh now calls `loadLibrary(true)` directly from `StreamingPlaylistController.Listener`; the private `refreshLibraryAfterStreamingImport()` host wrapper was removed.
- Host-only wrappers for manual streaming cookie import, streaming playback pre-resolve, remote-source clear-and-navigate, and UiShell scrolling/tab updates were inlined to their real owners.
- Startup library loading now branches on media permission inline; the private `loadLibraryOnStartup()` wrapper was removed.
- Lyrics state refresh now reads the `LyricsViewModel` snapshot directly from the listener, and unused `ensureLyricsLoaded(...)` was removed.
- `QueueIntentController` was removed as a pure queue intent forwarding layer; `QueueViewModel.bindIntentListener(...)` now dispatches directly to the existing owner calls.
- The no-op runtime language update bridge was removed from `SettingsRuntimeEffect`, `SettingsRuntimeApplier`, and `MainUiShellController`.
