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
- `EchoPlaybackService` schedules the deferred save on the existing playback scheduler instead of blocking the playback-start path.
- Small queues keep synchronous persistence to preserve simple restore behavior.

Guardrail:

- `PlaybackQueueManagerTest.playLargeQueueDefersFullQueuePersistenceUntilAfterPlaybackStart`
- `PlaybackQueueCommandOwnerTest`

Follow-up:

- If restore-after-immediate-process-kill becomes a concern, add a tiny synchronous "current track + queue size + version" checkpoint before the async full save.

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

### P1 - Realtime Visual Polling Recomputed Top-Level Navigation Too Often

Status: Fixed, keep regression coverage.

Evidence:

- `EchoNavGraph` polled realtime beat/bands every 120 ms whenever playback was active.
- The realtime values are passed through top-level `audioMotion`, so every update can recompose broad navigation chrome.

Fix:

- Poll interval increased to 500 ms.
- Realtime polling is only active when Now Playing / Queue playback UI is visible.

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

### P1 - MediaSession Timeline Exposes Large App Queues Without ExoPlayer Mirroring

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- Large app queues still avoid ExoPlayer mirroring when `PlaybackQueueManager.mirroredQueueTracksForPreparation()` returns `null` above `MAX_MIRRORED_QUEUE_TRACKS = 64`.
- `PlaybackSessionPlayer` now exposes the app queue through `getCurrentTimeline()`, `getMediaItemCount()`, `getCurrentMediaItemIndex()`, `getCurrentMediaItem()`, and `getMediaItemAt(index)`.
- `PlaybackSessionCommandOwner.sessionQueueTracks()` delegates to the service queue snapshot and `sessionQueueCurrentIndex()` delegates to the current app queue index.
- `PlaybackControllerMediaItemsOwner` still handles the opposite direction, where external controllers send media items into the app queue.

Impact:

- Android system controllers can see a large app queue without forcing ExoPlayer to prepare every streaming item.
- The previous playback-start stability guard remains intact because this is a MediaSession-facing queue view, not full player media-source mirroring.

Guardrail:

- `PlaybackSessionPlayerTest.sessionQueueExposesLargeAppQueueWithoutMirroringDelegatePlayer`
- `PlaybackControllerMediaItemsOwnerTest.playsResolvedControllerQueue`

### P0 - PlaybackSessionCommandOwner Unit Test Compiles Against Session Queue API

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `PlaybackSessionCommandOwner.StateProvider` now includes queue-facing methods: `queueSnapshot()`, `currentIndex()`, `queueSize()`, and `trackAt(index)`.
- `PlaybackSessionCommandOwnerTest` still used an anonymous fake that only implemented playback position/current-track methods.
- Running the app unit-test task failed during Java test compilation because the fake did not implement `trackAt(int)`.

Fix:

- `PlaybackSessionCommandOwnerTest` now supplies a small two-track queue through the fake `StateProvider`.
- The test also asserts `sessionQueueTracks()`, `sessionQueueSize()`, `sessionQueueCurrentIndex()`, and `sessionQueueTrackAt(index)` delegation.

Guardrail:

- `PlaybackSessionCommandOwnerTest.delegatesMediaSessionCommandsToPlaybackOwners`

### P2 - Session Queue Timeline Reuses Cached Timeline And Narrow Reads

Status: Fixed in current working tree, keep regression coverage.

Evidence:

- `PlaybackSessionPlayer.getMediaItemCount()` now uses `delegate.sessionQueueSize()` instead of reading a full queue snapshot.
- `PlaybackSessionPlayer.getMediaItemAt(index)` now uses `delegate.sessionQueueTrackAt(index)` and only materializes the requested `MediaItem`.
- `PlaybackSessionPlayer.sessionQueueTimeline()` caches `SessionQueueTimeline` by a cheap queue key: size, current index, first track id, current track id, and last track id.
- `PlaybackQueueStateOwner` and `PlaybackQueueManager` expose `queueSize()` / `trackAt(index)` so the session player can avoid full-list copies on common system reads.

Impact:

- Android media UI and lock-screen queue reads no longer need to copy the full app queue for count, current index, or single-item access.
- Timeline construction still materializes the visible session queue once per queue key, but repeated reads reuse the cached timeline until the queue changes.
- This keeps the queue visible to Android without reintroducing ExoPlayer full-queue mirroring for large streaming queues.

Guardrail:

- `PlaybackSessionPlayerTest.repeatedSessionQueueReadsReuseTimelineAndPreferNarrowAccess`
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

## Open Findings

### P1 Open - MediaSession timeline cache can miss middle-queue replacements

Evidence:

- `PlaybackSessionPlayer.getCurrentTimeline()` returns `sessionQueueTimeline()`.
- `sessionQueueTimeline()` is cached by `SessionQueueKey`.
- The key uses queue size, current index, first track id, current track id, and last track id.
- Large streaming queues can replace unresolved placeholders in the middle of the queue while size/current/first/last remain unchanged.

Risk:

- Android system media UI may keep a stale timeline for middle queue entries after background streaming pre-resolve or queue replacement.
- This is a plausible contributor to "songs still do not appear correctly in Android's media playback list" when the app queue is present but the system-facing timeline does not refresh.

Low-risk approach:

- Add a lightweight queue version or content revision from the queue owner and include it in `SessionQueueKey`.
- If a version is not available, include a small deterministic fingerprint over sampled queue ids around the current item and the requested visible window.
- Keep `getMediaItemCount()` and `getMediaItemAt(index)` narrow; avoid going back to full queue snapshots for common system reads.

Suggested tests:

- Extend `PlaybackSessionPlayerTest` with a queue of at least five items, build a timeline, replace a middle non-current item while size/current/first/last stay the same, then assert `getCurrentTimeline()` exposes the replacement after the key changes.
- Add a negative check that repeated reads without queue changes still reuse the cached timeline.

Device check:

- With a large NetEase queue, start playback, wait for several background URL resolves, then inspect Android media output / media session dump to confirm the visible queue titles match the app queue.

### P2 Open - MediaLibrary child paging materializes full lists before slicing

Evidence:

- `PlaybackMediaLibraryCallback.onGetChildren(...)` calls `childrenForAutoParent(parentId)` first.
- `childrenForAutoParent(AUTO_ALL)` calls `autoItemsForTracks(dataSource.loadCachedTracks())`.
- Playlist, artist, and album child paths also build all matching `MediaItem`s before `pagedItems(children, page, pageSize)` slices the result.

Risk:

- Android Auto or a system browser requesting a small page from a large local library still pays the cost of loading and converting the full matching list.
- This is outside the immediate playback-start path, but can add background allocation pressure while the Android media browser is open.

Low-risk approach:

- Push paging into each child branch before converting tracks to `MediaItem`.
- Preserve the existing root/folder structure and `onSetMediaItems(...)` behavior.
- Keep grouped artist/album folders deterministic; only page final child lists.

Suggested tests:

- Extend `PlaybackMediaLibraryCallbackTest` with a fake data source that counts converted tracks for a small page request from a large library.
- Assert `onGetChildren(AUTO_ALL, page = 0, pageSize = 20)` does not convert every cached track.

### P1 Open - Onboarding library scan has no timeout or cancellation recovery

Evidence:

- `OnboardingController.scanLibraryFromOnboarding()` sets `libraryScanInProgress = true`, mounts the shell, and calls `listener.loadLibrary(false)`.
- `OnboardingController.onLibraryScanResult(...)` is the only path that clears `libraryScanInProgress`.
- `MainActivityBase.loadLibrary(false)` forwards success and failure callbacks into `onLibraryScanResult(...)`, but there is no timeout or cancellation path if the refresh job never returns.
- `OnboardingControllerTest.failedScanClearsInProgressSoOnboardingCanRetry` covers explicit failure, but not a hung scan.

Risk:

- If MediaStore, database replacement, or repository refresh stalls, the initial page can remain on the second step with "等待曲库扫描完成" and no way to finish onboarding.
- This matches the reported "初始页点击第二步就不动了就进不去程序" failure mode.

Low-risk approach:

- Add an onboarding-level scan timeout that clears `libraryScanInProgress`, shows a retryable status, and lets the user continue to streaming/manual import only if product requirements allow skipping local scan.
- Keep the actual library refresh on the existing IO path; do not move scan work to the UI thread.
- Consider exposing a cancel/retry command rather than auto-retrying, because repeated MediaStore stalls can otherwise loop.

Suggested tests:

- Add an `OnboardingControllerTest` case where scan starts but no callback arrives; drive a timeout hook and assert `libraryScanInProgress()` becomes false and the missing setup message returns to "扫描本地曲库".
- Add an Activity-level contract that `loadLibrary(false)` failure and timeout both remount onboarding instead of leaving a permanent in-progress state.

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
- Android Auto / MediaLibrary support is not absent: `PlaybackMediaLibraryCallback` implements `onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onAddMediaItems`, and `onSetMediaItems`; active playback timeline exposure for large queues is covered by `PlaybackSessionPlayer`, with remaining system-facing risks tracked under Open Findings.
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

1. Device-smoke the bounded streaming pre-resolve, hidden Now Bar queue sync gating, and direct QueueViewModel entry path with 500 streaming tracks.
2. Audit remaining explicit full `queueSnapshot()` consumers and add narrower reads only where the caller does not need queue contents.
3. Continue targeted UI regression guards around previously reported issues, especially Now Playing waveform visibility and playback-page jank.
