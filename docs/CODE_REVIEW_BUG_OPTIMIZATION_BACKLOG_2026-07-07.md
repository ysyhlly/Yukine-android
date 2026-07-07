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

Status: Fixed in current working tree, keep regression coverage; still needs an end-to-end Compose input smoke when UI test infrastructure is available.

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

## Open Findings

### P2 - Session Queue Timeline Rebuilds The Full Queue On System Reads

Status: Open.

Evidence:

- `PlaybackSessionPlayer.getCurrentTimeline()` calls `sessionQueueTimeline()`.
- `sessionQueueTimeline()` calls `sessionQueueTracks()` and maps every track into a `MediaItem` for a new `SessionQueueTimeline`.
- `PlaybackSessionCommandOwner.sessionQueueTracks()` currently calls `stateProvider.queueSnapshot()`, which copies the full queue.
- `getMediaItemCount()` and `getMediaItemAt(index)` also call `sessionQueueTracks()`; `getMediaItemAt(index)` then converts the requested track into a `MediaItem`.

Impact:

- The system queue visibility fix is correct, but frequent MediaSession reads on a 500-track streaming queue can repeatedly copy the full app queue and rebuild many `MediaItem` objects.
- This work is outside the ExoPlayer preparation path, so it should not reintroduce the original playback-start freeze, but it can still add avoidable allocation pressure while system media UI is open.

Low-risk approach:

- Keep the session-facing queue view.
- Cache `SessionQueueTimeline` by a cheap queue key such as `(queueSize, currentIndex, currentTrackId, first/last track id or queue version)` and rebuild only when that key changes.
- Prefer narrow access for `getMediaItemCount()` / `getCurrentMediaItemIndex()` so those calls do not need a full queue snapshot.
- Keep `getMediaItemAt(index)` lazy; do not restore or resolve streaming URLs just to build session metadata.

Suggested tests:

- Extend `PlaybackSessionPlayerTest` with a delegate that counts `sessionQueueTracks()` calls across repeated `mediaItemCount`, `currentMediaItemIndex`, and `currentTimeline` reads.
- Add a regression that queue changes invalidate the cached timeline while repeated reads of the same queue do not rebuild every item.

### P1 - Streaming Playlist Pagination Has No Local Safety Cap

Status: Open.

Evidence:

- `StreamingViewModel.loadStreamingPlaylistTracks(...)` requests remote playlist pages with `pageSize = 2000`.
- The loop stops only when `detail.hasMore` is false, `detail.tracks` is empty, or the optional remote `total` has been reached.
- If a provider or gateway keeps returning non-empty pages with `hasMore = true` and no reliable `total`, the import/sync job can keep fetching pages for a long time.
- Existing coverage, `StreamingViewModelTest.fetchStreamingPlaylistTracksLoadsAllPages`, protects the normal two-page path but does not cover a misbehaving provider.

Impact:

- This fits the "scanning/importing songs can get stuck" symptom family for provider-backed playlist imports.
- The work runs in `viewModelScope`, so it should not block the UI thread directly, but it can keep network/provider work alive and hold the streaming loading state until cancellation or failure.

Low-risk approach:

- Keep loading all normal pages, but add a local safety cap such as max pages and/or max tracks per import.
- Stop with a partial result plus status when the cap is hit, rather than looping indefinitely.
- Preserve existing two-page and empty-playlist behavior.

Suggested tests:

- Add a fake gateway that always returns `hasMore = true` with non-empty tracks and no `total`; assert `loadStreamingPlaylistTracks(...)` stops at the local cap.
- Add a normal multi-page test below the cap to preserve existing full-import behavior.

## Reviewed And Not Flagged

- `LyricsRepository.firstLrclibRecord()` creates a temporary two-thread executor for exact/search lyric racing, but it cancels futures and calls `executor.shutdownNow()` in `finally`.
- `MainExecutors` has an explicit `shutdownNow()` method and guards rejected execution.
- `PlaybackPrecacheManager` uses a named daemon `ThreadFactory` for a managed executor path; the raw `Thread` creation is localized to thread factory construction.
- Persisted page backgrounds are hydrated before settings page navigation: `MainActivityBase` calls `settingsStore.load(loadSettingsPreferencesUseCase.execute())`, creates `SettingsContextProvider`, then calls `refreshSettingsContext()`; `MainActivityArchitectureContractTest.mainActivityPushesLoadedSettingsContextBeforeSettingsPageNavigation` protects that ordering.
- Android Auto / MediaLibrary support is not absent: `PlaybackMediaLibraryCallback` implements `onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onAddMediaItems`, and `onSetMediaItems`; active playback timeline exposure for large queues is now covered by `PlaybackSessionPlayer`, with the remaining concern narrowed to repeated full queue/timeline rebuilding.
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

1. Device-smoke the lazy queue UI with 500 streaming tracks.
2. Cache the session-facing queue timeline and add narrow reads so repeated Android media UI queries do not rebuild the full queue.
3. Add a local pagination/track cap for streaming playlist import and sync.
4. Add a Compose-level search input regression test before changing the route/ViewModel query handoff again.
5. Audit remaining explicit full `queueSnapshot()` consumers and add narrower reads only where the caller does not need queue contents.
