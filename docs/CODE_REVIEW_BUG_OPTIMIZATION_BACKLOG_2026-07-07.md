# Code Review Bug And Optimization Backlog - 2026-07-07

This note is a living review log for Yukine/ECHO Android. It focuses on bugs and low-risk optimizations that should not change existing user-facing behavior or playback experience.

## Review Scope

- Playback queue startup and large streaming queue behavior.
- Daily / heartbeat streaming recommendation playback entry points.
- Compose playback chrome and realtime visualization update loops.
- SQLite queue persistence and queue snapshot fan-out.
- Search input state handoff between route state, ViewModel state, and Compose.
- Settings chrome state hydration for persisted page backgrounds.
- MediaSession / MediaLibrary exposure for Android system controllers and Android Auto.
- Background task lifecycle and silent failure patterns.

Evidence used:

- CodeGraph review of playback queue, queue persistence, realtime visual, and scheduler paths.
- CodeGraph review of daily recommendation, heartbeat recommendation, queue ViewModel, search input, and settings background paths.
- CodeGraph review of current queue lazy-row implementation and MediaSession / MediaLibrary callback paths.
- Targeted scans for `withFrameNanos`, `while (true)`, `queueSnapshot()`, `savePlaybackQueue()`, `ExecutorService`, and raw `Thread`.
- Focused Gradle verification listed at the end of this document.

## Fixed In Current Working Tree

### P0 - Large Streaming Queue Can Block Playback Startup

Status: Fixed, keep regression coverage.

Evidence:

- `PlaybackQueueManager.playQueue()` used to call `persistQueue()` before `prepareCurrent(true)`.
- `persistQueue()` writes through `MusicLibraryRepository.savePlaybackQueue()`.
- `EchoDatabaseHelper.savePlaybackQueue()` does `DELETE` plus one `insertWithOnConflict()` per queue item in a transaction.
- For hundreds of streaming tracks, this can block the service path before the current song is prepared, matching the observed "small queue ok, large queue freezes, then later recovers" behavior.

Fix:

- Large queues now defer full queue persistence through `PlaybackQueueManager.QueuePlaybackActions.persistQueueAsync(...)`.
- Large-queue playback now writes the small resume checkpoint and prepares the current track before it submits the full queue save, so the background SQLite transaction cannot acquire the writer lock ahead of playback startup.
- `PlaybackQueueCommandOwner` keeps at most the latest pending full-queue snapshot. Rapid play/skip actions no longer enqueue an unbounded series of stale `DELETE + INSERT` rewrites, and each completed save yields back to the priority scheduler before a newer snapshot is written.
- `EchoPlaybackService` schedules the conflated save on the existing playback scheduler instead of blocking the playback-start path.
- Small queues keep synchronous persistence to preserve simple restore behavior.

Guardrail:

- `PlaybackQueueManagerTest.playLargeQueueDefersFullQueuePersistenceUntilAfterPlaybackStart`
- `PlaybackQueueCommandOwnerTest.conflatingQueuePersistenceKeepsOnlyLatestPendingSnapshot`
- `PlaybackQueueCommandOwnerTest.conflatingQueuePersistenceYieldsBeforeSavingUpdatesArrivingDuringWrite`

Follow-up:

- If restore-after-immediate-process-kill becomes a concern, add a tiny synchronous "current track + queue size + version" checkpoint before the async full save.

### P1 - Persisted Playback Progress Reopened Songs Mid-Track

Status: Fixed per current product behavior: only an explicit user pause creates a recoverable playback checkpoint.

Evidence:

- Periodic progress updates, seek, task removal, and service destruction must not turn playback into a future resume point.
- A user pause still needs to survive service recreation and resume from the exact paused position, including streaming tracks.

Fix:

- The progress callback boundary, seek, task removal, shutdown, queue changes, and completion paths do not create a checkpoint; only `pause()` writes one.
- Restoration accepts a checkpoint only when it belongs to the restored current track, has a positive position, and the previous session was paused. The restored position is explicit so streaming tracks can resume too.
- Starting playback consumes the checkpoint. Manual song selection, replay, skip, natural completion, and active-service recovery all begin at `0 ms`.
- A streaming source refresh may retain position only when its stable track ID still matches the current queue item; a late recovery for another song is ignored instead of replacing the current item or transferring its position.
- Headset-noisy and sleep-timer pauses use a non-persisting pause path, so only a user-initiated pause is recoverable.
- Temporary in-memory position handoff for streaming URL/source replacement remains intact so an in-flight recovery does not restart a song unexpectedly.
- Before replacing a single source or rebuilding a mirrored queue, the player-state owner drops the prior item's in-memory estimate. This prevents a newly buffering streaming song from inheriting the previous song's estimate.

Guardrail:

- `PlaybackPositionManagerTest.progressUpdatesDoNotPersistPlaybackPosition`
- `PlaybackPositionManagerTest.userPausePersistsTheCurrentPosition`
- `PlaybackQueueManagerTest.explicitPlayAfterPausedColdRestoreKeepsSavedPosition`
- `PlaybackQueueManagerTest.restorePlaybackQueueRestoresPausedStreamingProgress`
- `PlaybackQueueManagerTest.restorePlaybackQueueClearsCheckpointAfterAnActiveShutdown`
- `PlaybackQueueManagerTest.replaceCurrentTrackAndResumeIgnoresAStaleDifferentTrackRecovery`
- `PlaybackQueueManagerTest.replaceCurrentTrackAndResumeKeepsPositionForTheSameTrackWithARefreshedUri`
- `PlaybackQueueManagerTest.automaticMirroredTransitionClearsAUserPauseCheckpointBeforeTheNextTrackStarts`
- `PlaybackPlayerStateOwnerTest.resetPositionEstimateDropsOldPausedPositionBeforeReplacingTheSource`

### P0 - Mirrored Player Preparation Scales Poorly With Large Streaming Queues

Status: Fixed, keep regression coverage.

Evidence:

- Mirrored queue preparation restored streaming data and built media sources across the whole queue.
- This made a queue of hundreds behave like hundreds of tracks were being prepared, even though only the current track is needed to start playback.

Fix:

- Mirrored queue preparation is capped at 64 tracks.
- Larger queues fall back to single-current-track preparation while keeping the app queue intact.

Guardrail:

- `PlaybackQueueManagerTest.queuePreparationForLargeQueueAvoidsMirroringAndStreamingRestore`

### P0 - Notification Artwork Decode Was Scheduled On The Main Thread

Status: Fixed, keep regression coverage.

Evidence:

- `PlaybackNotificationArtworkManager` used the Android main executor for a task that can perform HTTP I/O, bitmap decode, and JPEG encoding.
- A first-seen album artwork request could therefore compete directly with frame rendering and playback-state dispatch during startup or a track transition.

Fix:

- The artwork manager now owns a bounded single-worker background executor at background priority; it is released through the existing service resource shutdown path.
- The worker is limited to HTTP/file reads, decode, and encode. Cache writes, current-track reads, MediaSession refresh, and notification refresh are posted back to the main executor.
- Rejected work removes its miss marker so a later state update can retry instead of permanently losing that artwork request.

Guardrail:

- `PlaybackNotificationArtworkManagerTest.artworkDecodeRunsBeforeMainThreadCachePublication`
- `PlaybackNotificationArtworkManagerTest.releaseAfterBackgroundDecodePreventsQueuedMainThreadPublication`
- `PlaybackNotificationArtworkManagerTest.rejectedArtworkWorkCanBeRequestedAgain`

### P1 - Playback State Fan-Out Could Accumulate Stale Main-Thread Updates

Status: Fixed, keep regression coverage.

Evidence:

- Every playback snapshot posted a separate main-thread callback even when a newer snapshot had already arrived.
- The service publishes playback state on the progress cadence, and widget updates rebuilt `RemoteViews` and crossed Binder even when only `positionMs` changed.

Fix:

- `PlaybackStateEventController` now retains at most one pending snapshot and dispatches the latest snapshot when the main thread catches up.
- Buffering callbacks intentionally remain uncoalesced because they drive streaming recovery.
- `PlaybackStatePublisher` updates widgets only when a field rendered by the widget (track text, artwork, or playing state) changes; normal listener, notification, and lyric state publication is unchanged.
- Library joins the existing meaningful playback-change render policy, so the current-row highlight updates immediately after a track switch without rebuilding the library for progress-only snapshots.

Guardrail:

- `PlaybackStateEventControllerTest.busyMainHandlerKeepsOnlyTheLatestPlaybackSnapshot`
- `PlaybackStateEventControllerTest.coalescedTrackTransitionStillUsesTheLatestTrackForUiEffects`
- `PlaybackStateUpdateControllerTest.resolveRendersLibraryWhenCurrentTrackChanges`
- `PlaybackStateUpdateControllerTest.resolveDoesNotRenderLibraryForProgressOnlyChange`
- `PlaybackStatePublisherTest.widgetSkipsProgressOnlySnapshotsButRefreshesForPlaybackOrArtworkChanges`

### P1 - Large Streaming Queue Restore Performed Per-Track Blocking Cache Reads

Status: Fixed for large queues; current-track cache recovery remains synchronous by design.

Evidence:

- `restorePlaybackQueue()` previously called streaming URL/header restoration for every persisted track.
- The persistent streaming header adapter bridges its Room cache read with `runBlocking`, so a large unresolved streaming queue made the service wait once per row during restore.

Fix:

- Queue restore first filters rows and resolves the final current index.
- Queues at or below the existing 64-track mirror threshold keep full restoration behavior.
- Larger queues restore only the selected current track; later tracks continue through the existing on-demand playback resolution path.

Guardrail:

- `PlaybackQueueManagerTest.restoreLargeQueueOnlyHydratesTheSelectedCurrentStreamingTrack`
- `PlaybackQueueManagerTest.restoreLargeQueueReplacesAndHydratesTheSelectedCurrentStreamingTrack`

### P1 - Realtime Visual Polling Recomputed Top-Level Navigation Too Often

Status: Tuned after device visual feedback, keep regression coverage.

Evidence:

- The 500 ms polling interval and visual-change thresholds made the spectrum ring visibly lag behind playback.

Fix:

- Restore a 33 ms realtime visual poll and publish each changed beat/band value.
- Keep polling limited to active playback and the routes that actually render the spectrum ring, including Home, Library, Queue, Settings, Search, Now Playing, and relevant Network pages. Stopped, disconnected, and non-ring pages still do not churn.

Guardrail:

- `MainActivityArchitectureContractTest.realtimeVisualizerDoesNotChurnDuringStartup`

### P1 - YukineDownloadOrb Had A Per-Frame Compose State Loop

Status: Fixed, needs device feel check.

Evidence:

- `YukineDownloadOrb` used two `rememberInfiniteTransition()` loops and a `LaunchedEffect` with `withFrameNanos`.
- The loop rebuilt rendered spectrum, peaks, and baselines every frame for a small chrome orb that appears on multiple pages.

Fix:

- Removed the per-frame rendered-spectrum state loop.
- Orb motion now derives from the already-throttled smooth playback position and current audio motion inputs.

Risk:

- Visual liveliness is intentionally reduced. It should remain acceptable because this is a small status affordance, not core playback controls.

Recommended check:

- On device, open Home, Search, Settings, and Now Playing while music is playing; confirm the orb still communicates playback/quality without visible jank.

### P1 - Queue UI No Longer Eagerly Builds Every Row

Status: Fixed in current working tree, focused tests passed; needs device feel check before packaging.

Evidence:

- `app.yukine.queue.QueueViewModel.bind(...)` now caps eager row creation at `EAGER_QUEUE_ROW_LIMIT = 96`.
- `QueueDestinationState` now carries `rowCount` and `rowAt(index)` so the UI can resolve rows lazily.
- `QueueScreen` now renders through `trackCount` / `trackAt(index)` instead of requiring a full `List<QueueTrackUiState>` for every row.
- `QueueViewModelTest.bind_largeQueueKeepsRowsLazyAndStillResolvesIndexedRows` covers a 160-track queue, 96 eager rows, and a late current row.

Risk:

- `rowAt(index)` still derives a row synchronously on the Compose path, so row construction should stay cheap and side-effect free.
- This reduces queue-open jank, but it does not by itself reduce upstream queue snapshot copies, MediaSession timeline exposure, or recommendation batch size.

Recommended check:

- On device, open/close a 500-track streaming queue and scroll near the middle/end.

### P1 - Daily Recommendation Initial Playback Queue Is Capped

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `StreamingRecommendationViewModel.prepareDailyRecommendationPresentation(...)` now applies `DAILY_RECOMMENDATION_PLAYBACK_LIMIT = 30`.
- `StreamingRecommendationViewModelTest.dailyRecommendationPresentationLimitsInitialPlaybackQueue` covers a 45-track provider response being capped to 30 placeholders.
- `StreamingRecommendationViewModelTest.dailyRecommendationPresentationKeepsSmallQueueCount` covers the small-list case so normal recommendations do not lose tracks below the cap.

Impact:

- This removes daily recommendation from the broad "one click can enqueue hundreds of streaming placeholders" risk bucket.
- Heartbeat recommendation still needs a separate cap/refill review because it uses a different use case and request count.

### P2 - PlaybackTaskScheduler Reports Task Failures And Keeps Running

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `PlaybackTaskScheduler.runLoop()` now catches `RuntimeException` from each scheduled task and forwards it to an `ErrorSink`.
- The default sink logs `Log.w(TAG, "Playback task failed: " + priority, exception)`.
- `PlaybackTaskSchedulerTest.runtimeExceptionDoesNotStopSchedulerWorker` schedules a throwing task followed by a successful task and asserts the failure is reported while the worker continues.

Impact:

- Playback-side URL resolve, queue persistence, precache, and waveform task failures should no longer disappear silently.
- The scheduler still keeps the worker alive after a task failure, preserving the original recovery behavior.

### P2 - Search Input Preserves Unsubmitted Text During Route Refresh

Status: Fixed and guarded in current working tree.

Evidence:

- `SearchInput` is a normal `TextField(value = query, onValueChange = onQueryChange)`.
- Typing flows through `SearchViewModel.updateQuery(...)`, while submit writes the trimmed query into route state through `performUnifiedSearch(...)`.
- `MainActivityBase.renderSearch()` still calls `searchViewModel.updateActions(...)` and then `refreshUnifiedSearch(false)`, which reads the submitted route query and calls `searchViewModel.updateResults(...)`.
- `SearchViewModel.updateResults(...)` now preserves the current non-blank text-field query when route state is stale.

Guardrail:

- `SearchViewModelTest.updateResultsDoesNotOverwriteUnsubmittedInputWithStaleRouteQuery`
- `SearchViewModelTest.updateResultsPreservesSearchInputActions`

Remaining check:

- Add a Compose-level `UnifiedSearchScreen` input test if/when the UI test harness is available, because the current JVM coverage protects the state owner but does not type into the actual text field.

### P2 - StreamingPlaybackTaskScheduler Reports Task Failures And Drains The Lane

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `StreamingPlaybackTaskScheduler.drainCritical()` and `drainNextResolve()` now catch `RuntimeException` from the scheduled task body.
- Failures are reported through an `ErrorSink` and logged by the default sink.
- Both catch paths complete the active lane and call the normal drain method, so a throwing URL resolve/recovery task does not strand later work.

Guardrail:

- `StreamingPlaybackTaskSchedulerTest.throwingCriticalTaskReportsFailureAndDrainsNextCriticalTask`
- `StreamingPlaybackTaskSchedulerTest.throwingNextResolveTaskReportsFailureAndDrainsNextResolveTask`

Impact:

- A bad streaming URL resolve or recovery task should no longer leave `criticalActive` or `nextResolveActive` stuck forever.

### P2 - Lazy Queue Row Keys No Longer Prefix-Scan On Every Late Row

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- The lazy queue UI still resolves late rows through `QueueDestinationState.rowAt(index)`.
- `QueueViewModel.QueueRowKeyFactory` now owns row-key creation for a bound queue and caches late duplicate-safe row keys instead of recomputing every duplicate prefix scan from scratch.
- This keeps the 96-row eager cap while making far-row duplicate keys stable without returning to full eager key generation.

Guardrail:

- `QueueViewModelTest.bind_largeQueueCachesLateDuplicateRowKeys`

### P1 - Heartbeat Recommendation Initial Playback Queue Is Capped

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `StreamingRecommendationViewModel.fetchHeartbeatRecommendations(...)` still requests `count = 60` from the provider so recommendation/refill quality is not reduced.
- `StreamingHeartbeatRecommendationUseCase` now has `initialPlaybackLimit = 30`.
- `playlistPlaceholders(...)` applies the initial cap before playback starts, while `appendPlaceholders(...)` remains uncapped for later refill.

Impact:

- Heartbeat recommendation no longer starts playback by synchronously materializing all 60 provider results into the initial queue.
- Refill behavior is preserved, so the app can still append more recommendations after playback is stable.

Guardrail:

- `StreamingHeartbeatRecommendationUseCaseTest.playlistPlaceholdersCapsInitialPlaybackWindowButAppendCanRefillMore`
- `StreamingRecommendationViewModelTest.heartbeatRecommendationPresentationLimitsInitialPlaybackQueue`

### P1 - Common Queue Reads Avoid Full `queueSnapshot()` Copies

Status: Fixed for common read paths in current working tree; full snapshots remain for explicit queue operations.

Evidence:

- `NowPlayingViewModel.hasQueue()` now reads `PlaybackStateSnapshot.queueSize` instead of taking a full queue snapshot.
- `StreamingPlaybackController.preResolveNextStreamingTrack(...)` reuses one queue snapshot per pass instead of taking repeated snapshots.
- Full queue snapshots remain available for paths that actually need queue contents.

Impact:

- Common "does a queue exist?" and next-track pre-resolve checks no longer create avoidable full-list copies during playback.
- This does not eliminate every `queueSnapshot()` use; explicit queue operations still need contents.

### P1 - SQLite Queue Persistence Uses Compiled Statements

Status: Fixed in current working tree, partly mitigated by async persistence.

Evidence:

- `EchoDatabaseHelper.savePlaybackQueue()` still keeps the existing transaction/schema semantics.
- Queue rows are now inserted through one compiled `SQLiteStatement` from `playbackQueueInsertSql()`, with `bindPlaybackQueue(...)` rebinding each row.
- Queue URL compaction/migration paths use the same compiled-statement helper shape.

Impact:

- Async scheduling removes the playback-start freeze from large queue persistence.
- Compiled statements reduce per-row `ContentValues`/insert overhead while keeping rollback behavior protected by existing tests.

Guardrail:

- `EchoDatabaseHelperTest.savePlaybackQueueRollsBackOldQueueWhenReplacementBatchFails`
- `EchoDatabaseHelperTest` concurrency/write baseline

### P2 - Download Snapshot Polling Is Visibility-Gated

Status: Fixed for the global active-download poll in current working tree.

Evidence:

- `EchoNavGraph` now computes `activeDownloadVisible` from visible consumers.
- The global active-download `LaunchedEffect` clears `activeDownload` and returns when no visible destination consumes it.
- `DownloadsDestination` remains composition-scoped.

Impact:

- Download snapshot polling no longer runs globally when the active screen cannot display the download orb/status.
- Remaining polling loops should still be reviewed individually, but this removes one broad navigation-level loop.

### P1 - MediaSession Timeline Matches The Underlying Player Shape

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- Large app queues still avoid ExoPlayer mirroring above `MAX_MIRRORED_QUEUE_TRACKS = 64`.
- `PlaybackSessionPlayer` no longer synthesizes a Timeline from the app queue. Timeline, media-item count, current index, and item reads all come from the same underlying ExoPlayer instance that emits Media3 callbacks.
- When ExoPlayer mirrors a small queue, system controllers see that mirrored queue. When ExoPlayer contains only the current item, including all large queues, system controllers see exactly that item at index 0.
- Rich notification metadata (current lyric and cached artwork bytes) remains limited to the current `getMediaMetadata()` result instead of being copied into a synthetic queue Timeline.
- Removing the synthetic queue prevents a single-item callback Timeline from being combined with a large app-queue index in Media3 position bundles.
- `PlaybackControllerMediaItemsOwner` still handles the opposite direction, where external controllers send media items into the app queue.

Impact:

- Android system controllers see the actual ExoPlayer shape without allocating or serializing thousands of synthetic `MediaItem` / `MediaMetadata` objects.
- The previous playback-start stability guard remains intact because this is a MediaSession-facing queue view, not full player media-source mirroring.

Guardrail:

- `PlaybackSessionPlayerTest.sessionPlayerKeepsUnderlyingTimelineInsteadOfSynthesizingTheLargeAppQueue`
- `PlaybackControllerMediaItemsOwnerTest.playsResolvedControllerQueue`

### P2 - Synthetic Session Queue API Removed

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- The retired synthetic Timeline required queue snapshot/size/index/item methods on `PlaybackSessionCommandOwner.StateProvider` and `PlaybackSessionPlayer.Delegate`.
- Those methods duplicated queue state at the MediaSession boundary and allowed getter state to diverge from ExoPlayer callback state.

Fix:

- Queue-facing session methods were removed from both interfaces.
- MediaSession transport commands still delegate to the app queue owners, while Timeline/state reads remain owned by ExoPlayer.

Guardrail:

- `PlaybackSessionCommandOwnerTest.delegatesMediaSessionCommandsToPlaybackOwners`

### P1 - Streaming Playlist Pagination Has A Local Safety Cap

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `StreamingViewModel.loadStreamingPlaylistTracks(...)` requests remote playlist pages with `pageSize = 2000`.
- The loop now also stops at `STREAMING_PLAYLIST_MAX_PAGES = 50`.
- The accepted track list is capped by `STREAMING_PLAYLIST_MAX_TRACKS`, derived from page size and max pages.
- The loop preserves the normal stop conditions: `detail.hasMore == false`, empty page, and declared remote `total` reached.

Impact:

- This fits the "scanning/importing songs can get stuck" symptom family for provider-backed playlist imports.
- A bad provider that keeps returning non-empty `hasMore = true` pages without a reliable `total` can no longer fetch forever from this ViewModel path.
- The work still runs in `viewModelScope`, so this guards long-lived import/sync jobs without moving provider work onto the UI thread.

Guardrail:

- `StreamingViewModelTest.fetchStreamingPlaylistTracksLoadsAllPages`
- `StreamingViewModelTest.fetchStreamingPlaylistTracksStopsAtLocalPaginationCap`

### P1 - Streaming Pre-Resolve Uses Bounded Queue Reads On Playback State Ticks

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `PlaybackStateEventController.handlePlaybackState(...)` calls `listener.preResolveNextStreamingTrack(snapshot)` on every playback-state update.
- `StreamingPlaybackController.preResolveNextStreamingTrack(...)` now calls `boundedPreResolveQueue(snapshot)` instead of `listener.queueSnapshot()`.
- `MainStreamingPlaybackListener` now delegates through `StreamingQueueReadSource`, which exposes full snapshot reads for existing UI paths plus narrow `queueSize()` / `queueTrackAt(index)` reads for pre-resolve.
- `MainActivityBase` binds the streaming playback listener to `playbackService.queueSize()` and `playbackService.queueTrackAt(index)`.
- `EchoPlaybackService` exposes `queueSize()` and `queueTrackAt(index)` through `PlaybackQueueStateOwner`, which already delegates to `PlaybackQueueManager.queueSize()` / `trackAt(index)`.
- The pre-resolve queue passed into the existing `StreamingViewModel` logic is normalized to current index `0` and bounded to current track, next track, and up to three unresolved streaming placeholders found through a small lookahead window.

Impact:

- This is a remaining large-streaming-queue pressure point after the MediaSession and queue UI optimizations.
- A 500-track streaming queue no longer allocates a full queue copy on frequent playback-state updates just to warm one next track and a small pre-resolve window.
- The policy stays in `StreamingPlaybackController`; `MainActivityBase` only wires the existing service boundary to the listener factory.
- `PlaybackStateEventController.publishQueueIfChanged(...)` still uses full `queueSnapshot()` only when the Queue tab is visible and the UI queue publication key changes.

Guardrail:

- `StreamingPlaybackControllerTest.preResolveUsesBoundedQueueReadsWithoutFullSnapshot`
- `StreamingPlaybackControllerTest.preResolveSkipsResolvedLookaheadTracksForStreamingCandidatesWithoutFullSnapshot`
- `MainStreamingPlaybackListenerTest.delegatesStreamingPlaybackCallbacksToInjectedOwners`
- `MainStreamingPlaybackListenerTest.factoryCreatesStreamingPlaybackControllerListener`
- `MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway`

### P1 - Now Bar Queue Input Sync Is Visibility-Gated

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `PlaybackStateEventController.handlePlaybackState(...)` calls `listener.renderNowBar()` on playback-state updates.
- `NowPlayingStateController.renderNowBar()` publishes the Now Bar state and used to call `syncQueueInputsIfChanged(snapshot)` whenever the current track/index/queue size changed.
- `MainActivityBase.bindQueueViewModelInputs()` pulls `playbackService.queueSnapshot()` before binding the queue ViewModel, so hidden Now Bar updates could still copy and bind a large queue on every track change.
- `renderQueue()` already performs a fresh queue render when the queue tab is selected.

Fix:

- `NowPlayingStateController` now checks `listener.queueVisible()` before calling `syncQueueInputs()`.
- `MainActivityBase` wires queue visibility as `TAB_QUEUE.equals(selectedTab())`.
- Entering the queue tab still renders through `renderQueue()`, so visible queue state remains fresh without background rebinding while Home/Search/Settings/Now tabs are active.

Impact:

- Large streaming queues no longer force QueueViewModel rebinding from hidden Now Bar updates on every current-track change.
- This removes one more "large queue feels fine after a long wait" pressure point without changing queue screen behavior.

Guardrail:

- `NowPlayingStateControllerTest.queueIdentityChangesDoNotSyncQueueInputsWhenQueueIsHidden`
- `NowPlayingStateControllerTest.queueIdentityChangesResyncQueueInputs`
- `MainNowPlayingStateListenerTest.delegatesNowPlayingStateInputsToInjectedOwners`
- `MainNowPlayingStateListenerTest.factoryCreatesNowPlayingStateControllerListener`
- `MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway`

### P1 - Playback Store Queue Publishing Is Queue-Tab Gated

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `PlaybackStateEventController.handlePlaybackState(...)` updates the lightweight playback snapshot first, then calls `publishQueueIfChanged(snapshot)`.
- `publishQueueIfChanged(...)` used to call `playbackStore.publish(queueSnapshotSource.queueSnapshot())` whenever current track, current index, or queue size changed.
- `PlaybackViewModel.updatePlayback(...)` defensively copies the supplied queue, so a 500-track service snapshot could become two full-list copies on each track change while Home/Search/Settings/Now tabs were visible.
- The active Queue tab path no longer depends on that legacy `PlaybackViewState.queue`; `renderQueue()` binds `QueueViewModel` directly from `playbackService.queueSnapshot()`.

Fix:

- `PlaybackStateEventController.publishQueueIfChanged(...)` now returns early unless `listener.selectedTab() == MainRoutes.TAB_QUEUE`.
- The existing queue identity key is still used while the Queue tab is visible.
- Hidden queue changes do not update `lastPublishedQueueKey`, so the same playback snapshot can still publish when the user later switches to Queue.

Impact:

- Large streaming queues no longer republish and copy the full queue into `PlaybackViewModel` on every hidden-tab track change.
- Queue tab freshness is preserved by the direct `QueueViewModel` bind path when the tab is rendered.

Guardrail:

- `PlaybackStateEventControllerTest.hiddenQueueTabDoesNotReadLargeQueueOnTrackChanges`
- `PlaybackStateEventControllerTest.queueTabPublishesAfterHiddenQueueChanges`
- `MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway`

### P2 - Search Input Has A Compose Regression Guard

Status: Guarded in current working tree, keep regression coverage.

Evidence:

- User-reported search regressions can happen at the Compose boundary even when `SearchViewModel.updateQuery(...)` is correct.
- `UnifiedSearchScreen` is a controlled input: `TextField.value` comes from `UnifiedSearchUiState.query`, while typing calls `UnifiedSearchActions.onQueryChange`.
- `SearchDestination` collects `UnifiedSearchUiState` and passes the state's current `actions` into `UnifiedSearchScreen`.

Fix:

- Added a stable `testTag("unified-search-input")` to the search `TextField`.
- Added `SearchDestinationTest.searchInputAcceptsTextAndSubmitsLatestQuery`, which types Chinese text, verifies it is displayed, and verifies IME search submits the latest query.

Impact:

- Future route/ViewModel/action rewiring cannot silently break basic search input without failing a focused Compose test.
- The test tag is not visible in the app UI and does not change interaction behavior.

Guardrail:

- `SearchDestinationTest.searchInputAcceptsTextAndSubmitsLatestQuery`

### P2 - Settings Library Back Navigation Has UI And ViewModel Guards

Status: Guarded in current working tree, keep regression coverage.

Evidence:

- User feedback identified the Settings -> Library page back path as fragile.
- `SettingsBackStack.parent(SettingsPage.Library)` already maps to `SettingsPage.LibraryGroup`, and `SettingsBackStack.parent(SettingsPage.LibraryGroup)` maps to `SettingsPage.Home`.
- `SettingsScreen` promotes the first back action into the title bar icon, so this path needs both ViewModel action coverage and Compose title-back coverage.

Fix:

- Added `SettingsViewModelTest.librarySettingsBackActionsNavigateThroughExpectedParents`.
- Added `SettingsDestinationTest.titleBackActionRunsLibrarySettingsBackAction`.

Impact:

- The visible title-bar back icon and the ViewModel-generated library back action are now regression-tested.
- This locks the intended navigation chain: Library settings -> Library group -> Settings home.

Guardrail:

- `SettingsViewModelTest.librarySettingsBackActionsNavigateThroughExpectedParents`
- `SettingsDestinationTest.titleBackActionRunsLibrarySettingsBackAction`

### P1 - Queue Tab Entry Avoids Legacy No-Op Action Construction

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `MainActivityBase.renderQueue()` used to call `queueRenderController.render(playbackService.queueSnapshot(), ...)`.
- `QueueRenderController.render(...)` builds one `QueueTrackActions` object per queue item before calling `listener.publishQueueChrome(...)`.
- The active `MainQueueRenderListener.publishQueueChrome(...)` intentionally keeps legacy no-op behavior because the Queue tab is backed by `QueueViewModel` / `QueueDestinationState`.
- This means entering the Queue tab could still allocate per-row action wrappers for hundreds of tracks even though the resulting chrome payload was discarded.

Fix:

- `MainActivityBase.renderQueue()` now calls `bindQueueViewModelInputs()` directly.
- This keeps the queue tab refresh on entry, which is especially important now that hidden Now Bar queue syncing is visibility-gated.
- The legacy `QueueRenderController`, `MainQueueRenderListener`, Hilt provider, Activity field, and focused no-op listener test were removed.
- `TrackListPlaybackAction` moved out of the deleted queue render file and now lives with the network event playback entry that still uses it.

Impact:

- Queue tab entry avoids one all-items action-construction pass.
- Large queue opening now relies on the existing lazy `QueueViewModel` row path instead of doing both lazy MVVM binding and discarded legacy chrome construction.
- The app also has one fewer forwarding/no-op render layer and one fewer Activity injection/field.

Guardrail:

- `MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway`
- `QueueViewModelTest`
- `NetworkMenuEventControllerPlaybackTest`

### P3 - Dashboard JSON Nullable Strings Are Parsed Explicitly

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `DashboardJson.kt` passed `null` as the fallback to Java `JSONObject.optString(...)` for nullable fields such as `artworkUrl`, `trackId`, `title`, `artist`, and `album`.
- Kotlin reported Java type-mismatch warnings during `:app:compileDebugKotlin`.
- The intended contract is nullable JSON string output: missing field and JSON `null` map to Kotlin `null`, while a present empty string remains `""`.

Fix:

- Added a local `JSONObject.optNullableString(...)` helper in `DashboardJson`.
- Replaced nullable `optString(..., null)` calls with the helper.
- Added `DashboardJsonTest` coverage for missing, explicit `null`, and empty-string values.

Impact:

- Removes Dashboard JSON Java interop warnings without changing the API contract.
- Makes remote dashboard null handling explicit and regression-tested.

Guardrail:

- `DashboardJsonTest.playbackStateParsesMissingNullAndEmptyNullableStrings`
- `DashboardJsonTest.recentActivityParsesNullableArtworkUrlWithoutDroppingEmptyString`

### P1 - Expanded Now Bar waveform can disappear at playback start

Finding:

- `WaveformProgress` already fell back to generated preview bars when service bars were missing, but when service bars existed and `serviceWaveformCachedProgress == 0f`, the draw loop skipped every unplayed/uncached bar.
- On a freshly started streaming track this could leave only the rail/thumb visible, so tapping the progress bar looked like the waveform was never drawn.

Change:

- Added `waveformVisibleProgressForDraw(...)` so visible generated service bars are not hidden behind media-cache progress.
- Kept the existing fallback behavior for missing or invisible service bars.
- Updated the architecture contract so this guard remains explicit.

Guardrail:

- `NowBarWaveformRangeTest.generatedServiceWaveformStaysVisibleBeforePlaybackOrCacheAdvance`
- `MainActivityArchitectureContractTest.swipeAndWaveformRegressionsStayFixed`

### P1 - Heartbeat recommendation seed matching scans full large queues

Finding:

- `HeartbeatRecommendationSeedResolver.request()` pulled service queue, playback-store queue, ViewModel queue, and library context before seed matching.
- `StreamingTrackMatchUseCase.heartbeatSeedCandidates(...)` and `snapshotQueueForHeartbeat(...)` then walked those lists; for hundreds of streaming tracks this could add visible delay around daily/heartbeat recommendation actions even though only a small seed sample is used.

Change:

- Added bounded queue context before seed matching.
- Service and ViewModel queues now keep the current-track neighborhood plus a small leading context window.
- Library context is capped before merging with the service queue.
- Current-track snapshot candidates are still passed separately, so the primary seed path remains intact.

Guardrail:

- `HeartbeatRecommendationSeedResolverTest.largeSeedQueuesAreBoundedAroundCurrentTrackBeforeMatching`

## Recently Closed Findings

### P1 Superseded - Synthetic MediaSession Timeline Cache

Evidence:

- An earlier fix cached a synthetic app-queue Timeline and added fingerprints for middle-queue replacements.
- Media3 player callbacks still carried the underlying ExoPlayer Timeline, so any separate getter Timeline could diverge in item count or current index.

Risk:

- Divergent callback/getter Timelines could produce stale system UI and invalid Media3 position bundles; on large queues that is more serious than a cache miss.

Change:

- The synthetic Timeline and its cache/key/fingerprint were removed.
- PlaybackSession reads now use the same underlying player state as Timeline callbacks.

Guardrail:

- `PlaybackSessionPlayerTest.sessionPlayerKeepsUnderlyingTimelineInsteadOfSynthesizingTheLargeAppQueue`

Device check:

- With a large NetEase queue, start playback, wait for several background URL resolves, then inspect Android media output / media session dump to confirm the visible queue titles match the app queue.

### P2 Fixed - MediaLibrary child paging converts only the requested page

Evidence:

- `PlaybackMediaLibraryCallback.onGetChildren(...)` calls `childrenForAutoParent(parentId)` first.
- `childrenForAutoParent(AUTO_ALL)` calls `autoItemsForTracks(dataSource.loadCachedTracks())`.
- Playlist, artist, and album child paths also build all matching `MediaItem`s before `pagedItems(children, page, pageSize)` slices the result.

Risk:

- Android Auto or a system browser requesting a small page from a large local library still pays the cost of loading and converting the full matching list.
- This is outside the immediate playback-start path, but can add background allocation pressure while the Android media browser is open.

Change:

- Paging now happens inside each child branch before tracks, playlists, artists, or albums are converted to `MediaItem`.
- Track paging counts only playable matches, so unplayable rows do not create short or shifted pages.
- The existing root/folder ordering and `onSetMediaItems(...)` behavior remain unchanged.
- Page offset arithmetic uses `Long`, avoiding overflow for malformed large page inputs.

Impact:

- Android Auto or a system browser requesting 20 songs now builds at most 20 track `MediaItem`s instead of converting the full library first.
- Group discovery may still scan cached tracks to preserve deterministic grouping, but final folder item conversion is page-bounded.

Guardrail:

- `PlaybackMediaLibraryCallbackTest.allSongsPagingConvertsOnlyRequestedPlayableTracks`

## Recently Closed Startup Finding

### P1 Fixed - Onboarding library scan has cancellable timeout recovery

Evidence:

- `OnboardingController.scanLibraryFromOnboarding()` sets `libraryScanInProgress = true`, mounts the shell, and calls `listener.loadLibrary(false)`.
- `OnboardingController.onLibraryScanResult(...)` is the only path that clears `libraryScanInProgress`.
- `MainActivityBase.loadLibrary(false)` forwards success and failure callbacks into `onLibraryScanResult(...)`, but there is no timeout or cancellation path if the refresh job never returns.
- `OnboardingControllerTest.failedScanClearsInProgressSoOnboardingCanRetry` covers explicit failure, but not a hung scan.

Risk:

- If MediaStore, database replacement, or repository refresh stalls, the initial page can remain on the second step with "等待曲库扫描完成" and no way to finish onboarding.
- This matches the reported "初始页点击第二步就不动了就进不去程序" failure mode.

Change:

- `OnboardingController` owns one 45-second scan timeout, ignores repeated scan taps while a scan is active, and removes its callback on success, failure, completion, or Activity destruction.
- `LibraryViewModel` owns one active library-load `Job`; a replacement load or onboarding timeout cancels it.
- Blocking cache/refresh calls use `runInterruptible(ioDispatcher)`, so cancellation interrupts cooperative MediaStore/database work and suppresses stale success/failure callbacks.
- Timeout returns onboarding to a retryable state and shows localized Chinese/English status through `AppLanguage`.

Guardrail:

- `OnboardingControllerTest.hungScanTimeoutCancelsLoadAndRestoresRetryableState`
- `LibraryViewModelTest.cancelLibraryLoadSuppressesQueuedRefreshAndStaleCallbacks`
- `MainActivityArchitectureContractTest`

## Open Findings

### P1 Open - Device scan refresh replaces the whole local table in one transaction

Evidence:

- `MusicLibraryRepository.refreshFromDevice()` runs `scanner.scan()`, then `database.replaceTracks(tracks)`, then `database.loadTracks()`.
- `MediaStoreMusicScanner.scan()` avoids opening each audio file, which is good, but returns the whole scanned list before any database write starts.
- `EchoDatabaseHelper.replaceTracks(...)` deletes non-document/non-stream rows and inserts every scanned track inside one transaction.

Risk:

- A large local library still has an all-or-nothing scan/replace/load cycle with no progress checkpoint and no cancellation signal.
- On slow storage or emulators, this can feel like "扫描歌曲直接卡住" even though the work is on `ioDispatcher`, because UI state only updates after the full replace and reload completes.

Low-risk approach:

- Preserve the current full-refresh semantics, but add progress/timeout instrumentation around scan, replace, and reload phases so logs can identify which phase stalls.
- Consider chunked replacement or staged temp-table swap later; that is riskier and should only follow database tests.
- Reuse existing `EchoDatabaseHelper` transaction tests before changing table replacement behavior.

Suggested tests:

- Add a fake `LibraryImportOperations.refreshFromDevice()` that delays indefinitely and verify the ViewModel/onboarding path exposes a retryable in-progress timeout.
- Add database-level tests for a chunked or staged refresh before changing `replaceTracks(...)`.

## Reviewed And Not Flagged

- `LyricsRepository.firstLrclibRecord()` creates a temporary two-thread executor for exact/search lyric racing, but it cancels futures and calls `executor.shutdownNow()` in `finally`.
- `MainExecutors` has an explicit `shutdownNow()` method and guards rejected execution.
- `PlaybackPrecacheManager` uses a named daemon `ThreadFactory` for a managed executor path; the raw `Thread` creation is localized to thread factory construction.
- Persisted page backgrounds are hydrated before settings page navigation: `MainActivityBase` calls `settingsStore.load(loadSettingsPreferencesUseCase.execute())`, creates `SettingsContextProvider`, then calls `refreshSettingsContext()`; `MainActivityArchitectureContractTest.mainActivityPushesLoadedSettingsContextBeforeSettingsPageNavigation` protects that ordering.
- Android Auto / MediaLibrary support remains available through `PlaybackMediaLibraryCallback` paging (`onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onAddMediaItems`, and `onSetMediaItems`); active playback Timeline state deliberately follows ExoPlayer instead of synthesizing the full large app queue.
- `TrackDownloadManager` has several `while (true)` loops, but the reviewed ones are bounded stream-copy loops that break on EOF and check pause/removal between chunks; `TrackDownloadManagerTest.shutdownStopsExecutorsAndRejectsNewAppManagedDownloads` covers executor shutdown.

## Verification Commands

Passed during this review batch:

```powershell
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest.playLargeQueueDefersFullQueuePersistenceUntilAfterPlaybackStart :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest.realtimeVisualizerDoesNotChurnDuringStartup --tests app.yukine.MainActivityArchitectureContractTest.playbackQueueManagerOwnsQueueStateWithoutQueueProvider --tests app.yukine.MainActivityArchitectureContractTest.playbackQueueManagerReadApiStaysNarrow --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.queue.QueueViewModelTest --tests app.yukine.queue.QueueDestinationTest --tests app.yukine.MainActivityArchitectureContractTest.queueMvvmSliceOwnsDestinationStateWithoutMainViewModelQueueState --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingRecommendationViewModelTest --tests app.yukine.StreamingHeartbeatRecommendationUseCaseTest --tests app.yukine.PlaybackStartControllerTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackTaskSchedulerTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionPlayerTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.SearchViewModelTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingPlaybackTaskSchedulerTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionCommandOwnerTest --tests app.yukine.StreamingViewModelTest.fetchStreamingPlaylistTracksLoadsAllPages --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingPlaybackTaskSchedulerTest --tests app.yukine.queue.QueueViewModelTest --tests app.yukine.StreamingHeartbeatRecommendationUseCaseTest --tests app.yukine.StreamingRecommendationViewModelTest --tests app.yukine.SearchViewModelTest --tests app.yukine.NowPlayingViewModelTest --tests app.yukine.data.EchoDatabaseHelperTest :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackTaskSchedulerTest --console=plain
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionPlayerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionCommandOwnerTest --console=plain
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.StreamingPlaybackControllerTest --tests app.yukine.MainStreamingPlaybackListenerTest --tests app.yukine.MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.MainPlaybackServiceHostTest --console=plain
.\gradlew.bat --no-daemon --no-configuration-cache --no-build-cache :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain
.\gradlew.bat --no-daemon --no-configuration-cache --no-build-cache :app:testDebugUnitTest --tests app.yukine.StreamingPlaybackControllerTest --tests app.yukine.StreamingViewModelTest --console=plain
.\gradlew.bat --no-daemon --no-configuration-cache --no-build-cache :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionPlayerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionCommandOwnerTest --tests app.yukine.playback.PlaybackQueueStateOwnerTest --console=plain
.\gradlew.bat --no-daemon --no-configuration-cache --no-build-cache :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.NowPlayingStateControllerTest --tests app.yukine.MainNowPlayingStateListenerTest --tests app.yukine.MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway --tests app.yukine.NowPlayingStateControllerTest --tests app.yukine.queue.QueueViewModelTest --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway --tests app.yukine.queue.QueueViewModelTest --tests app.yukine.NetworkMenuEventControllerPlaybackTest --tests app.yukine.NetworkSourcesEventControllerTest --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.dashboard.DashboardJsonTest :app:compileDebugKotlin --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.PlaybackStateEventControllerTest --tests app.yukine.MainActivityArchitectureContractTest.streamingActionsLiveInMainActivityViewModelAndGateway --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.search.SearchDestinationTest :feature:ui-common:compileDebugKotlin --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.SettingsViewModelTest.librarySettingsBackActionsNavigateThroughExpectedParents --tests app.yukine.settings.SettingsDestinationTest --console=plain
.\gradlew.bat --no-build-cache :feature:ui-common:testDebugUnitTest --tests app.yukine.ui.NowBarWaveformRangeTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest.swipeAndWaveformRegressionsStayFixed :feature:ui-common:compileDebugKotlin --console=plain
.\gradlew.bat --no-build-cache :app:testDebugUnitTest --tests app.yukine.HeartbeatRecommendationSeedResolverTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
python C:\Users\31283\.codex\skills\chinese-utf8-development\scripts\scan_mojibake.py app/src/main/java app/src/test/java feature core
```

Device smoke performed:

```powershell
adb -s 3B15CJ0166000000 install --no-streaming -r app\build\outputs\apk\debug\app-debug.apk
adb -s 3B15CJ0166000000 shell monkey -p app.yukine 1
adb -s 3B15CJ0166000000 logcat -d -t 500
```

Result:

- APK installed successfully.
- Cold launch completed.
- No `FATAL EXCEPTION` or `ANR` was found in the sampled logcat.

## Next Safe Slices

1. Device-smoke Android Auto/media browser paging against a large local library and confirm page ordering remains stable.
2. Add scan/replace/reload phase timing to large local-library refreshes so slow devices identify the stalled phase without changing full-refresh semantics.
3. Device-smoke onboarding timeout/cancel/retry with a deliberately stalled or very large MediaStore scan.
