# Yukine Android MVVM 迁移接手清单

日期�?026-06-23  
状态：架构审查后形成的接手文档，未要求一次性重写�? 
适用范围：`app/src/main/java/app/yukine` 下当前单 Activity + Compose �?+ Java/Kotlin 迁移中间态�? 

---

## 2026-06-29 Codex continuation note

- Launcher component `.MainActivity` now resolves to Kotlin `MainActivity.kt`;
  the deleted Java `MainActivity.java` is guarded by
  `MainActivityArchitectureContractTest`.
- `MainActivityBase` is now a public legacy base behind the Kotlin shell.
  ViewModel acquisition moved to Kotlin `by viewModels()` delegates and
  `MainActivityViewModels`, so the Java base no longer calls
  `ViewModelProvider`.
- The provisional `ShellViewModel` / `ShellState` / `ShellAction` line was
  removed after review because it was not consumed by `MainActivityBase`,
  `NavigationViewModel`, `MainRouteController`, or `EchoNavHostState`. The
  active shell path remains Kotlin `MainActivity.kt` delegating into the
  existing route/nav owners until a future slice can make `ShellState` the
  single runtime state source instead of a parallel model.
- Hilt Activity-scoped modules now own several stable Activity dependencies:
  `ShellModule` provides `MainExecutors`, `PlatformModule` provides the main
  `Handler`, `StreamingModule` provides streaming playback scheduling,
  streaming playback resolution, and streaming track matching, and
  `LibraryModule` provides favorite toggling and playlist-track loading use
  cases.
- `PlaybackUiModule` now provides `MainPlaybackStoreFactory`; the Java base
  no longer constructs `MainPlaybackStore` directly and only passes the
  Kotlin-shell-owned `PlaybackViewModel` into the injected factory.
- Now-playing transport/favorite/status gateway policy moved out of the Java
  base anonymous `NowPlayingGateway` block into `MainNowPlayingGateway`, which
  is created through `PlaybackUiModule` and covered by
  `MainNowPlayingGatewayTest`.
- Playback action listener policy moved out of the Java base anonymous
  `PlaybackActionController.Listener` block into `MainPlaybackActionListener`,
  which is created through `PlaybackUiModule` and covered by
  `MainPlaybackActionListenerTest`.
- Queue action listener policy moved out of the Java base anonymous
  `QueueActionController.Listener` block into `MainQueueActionListener`, which
  is created through `PlaybackUiModule` and covered by
  `MainQueueActionListenerTest`.
- Queue render listener policy moved out of the Java base anonymous
  `QueueRenderController.Listener` block into `MainQueueRenderListener`, which
  is created through `PlaybackUiModule` and covered by
  `MainQueueRenderListenerTest`.
- Home dashboard render listener policy moved out of the Java base anonymous
  `HomeDashboardRenderController.Listener` block into
  `MainHomeDashboardRenderListener`, which is created through `ShellModule` and
  covered by `MainHomeDashboardRenderListenerTest`.
- Now-playing state listener policy moved out of the Java base anonymous
  `NowPlayingStateController.Listener` block into `MainNowPlayingStateListener`,
  which is created through `PlaybackUiModule` and covered by
  `MainNowPlayingStateListenerTest`.
- Recommendation action callbacks moved out of the Java base anonymous
  `RecommendationActionCallbacks` block into `MainRecommendationActionCallbacks`,
  which is created through `StreamingModule` and covered by
  `MainRecommendationActionCallbacksTest`.
- Streaming playlist dialog callbacks moved out of the Java base anonymous
  `StreamingPlaylistDialogController.Listener` block into
  `MainStreamingPlaylistDialogListener`, which is created through
  `StreamingModule` and covered by `MainStreamingPlaylistDialogListenerTest`.
- Streaming playlist controller listener policy moved out of the Java base
  anonymous `StreamingPlaylistController.Listener` block into
  `MainStreamingPlaylistListener`, which is created through `StreamingModule`
  and covered by `MainStreamingPlaylistListenerTest`.
- Streaming playlist import dialog callbacks moved out of the Java base
  anonymous `StreamingPlaylistImportDialogController.Listener` block into
  `MainStreamingPlaylistImportDialogListener`, which is created through
  `StreamingModule` and covered by
  `MainStreamingPlaylistImportDialogListenerTest`.
- Streaming manual cookie listener callbacks moved out of the Java base
  anonymous `StreamingManualCookieController.Listener` block into
  `MainStreamingManualCookieListener`, which is created through
  `StreamingModule` and covered by `MainStreamingManualCookieListenerTest`.
- Streaming search render listener policy moved out of the Java base anonymous
  `StreamingSearchRenderController.Listener` block into
  `MainStreamingSearchRenderListener`, which is created through
  `StreamingModule` and covered by `MainStreamingSearchRenderListenerTest`.
- Migration direction update: pause mechanical listener extraction after this
  high-value streaming search listener slice. Next architecture slices should
  run in two lanes but land in small batches: first record a checkpoint, then
  prefer Hilt construction migration in `initializeStoresAndDataGateways()`
  for `LibraryPlaylistActionGateway`, `StreamingLocalPlaylistOperations`,
  `LoadSettingsPreferencesUseCase`, `ImportStreamingPlaylistUseCase`,
  `SyncStreamingPlaylistUseCase`, `EnsureStreamingLoginPlaylistUseCase`, and
  `GetStreamingPlaylistLinkUseCase` before adding more one-hop listener
  factories.
- New owner acceptance is now stricter: a new owner must delete a Java
  anonymous business callback, reduce Activity manual construction, shorten a
  call chain, split a large interface, or add real behavior coverage. Listener
  delegation tests can remain, but every 2-3 listener slices need a behavior
  test for a real streaming/playback/data flow.
- Hilt construction migration has started for
  `initializeStoresAndDataGateways()`: `StreamingPlaylistSyncStore`,
  `ImportStreamingPlaylistUseCase`, `SyncStreamingPlaylistUseCase`,
  `EnsureStreamingLoginPlaylistUseCase`, `GetStreamingPlaylistLinkUseCase`,
  and `StreamingLocalPlaylistOperations` are now provided by
  `StreamingModule`. `MainActivityBase` only binds the injected
  `StreamingLocalPlaylistOperations` into `StreamingViewModel`; the previous
  Java anonymous `StreamingLocalPlaylistOperations` block and four manual
  use case constructors were removed. `MainStreamingLocalPlaylistOperations`
  covers the owner behavior with `MainStreamingLocalPlaylistOperationsTest`.
- The download directory picker unavailable message now uses
  `AppLanguage.text(..., "download.directory.picker.unavailable")` instead of
  a hard-coded Chinese string, preserving English parity.
- P0 baseline freeze recheck (2026-06-29): `git status --short` currently
  reports four modified entries:
  `M docs/ARCHITECTURE_REMEDIATION_PLAN_2026-06-26.md`,
  `M docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`,
  `M docs/MVVM_MIGRATION_HANDOFF.md`, and `M gradle.properties`; there are no
  current deleted test files under `app/src/test` or `app/src/androidTest`,
  so the deleted-test replacement map is empty for this checkpoint. Current
  counts are `MainActivityBase.java` 2968 lines,
  `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 170, and root-package Java files 15.
  `ShellViewModel` / `ShellState` / `ShellAction` source files are absent;
  remaining mentions are historical correction notes or contract-test guards.
  Serial verification passed with default Gradle settings:
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`
  and
  `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`.
  Do not default to `--no-daemon` or `--max-workers=1`; use them only after a
  reproducible daemon, KSP, or lock issue.
- P1 `initializeStoresAndDataGateways()` playlist action gateway slice
  (2026-06-29): `LibraryModule` now provides `LibraryPlaylistActionGateway`
  as `MainLibraryPlaylistActionGateway`, backed by
  `MusicLibraryPlaylistActionOperations(repository, syncStore)`. The playlist
  action contracts moved from the large `LibraryViewModel.kt` file into
  `LibraryPlaylistActionContracts.kt`, while `LibraryViewModel` remains the UI
  state/action owner. `MainActivityBase` now only calls
  `libraryViewModel.bindPlaylistActionGateway(libraryPlaylistActionGateway)`;
  the previous anonymous `new LibraryPlaylistActionGateway() { ... }`, the
  local `MusicLibraryPlaylistActionOperations`, seven playlist action use case
  constructors, and the Activity-owned `StreamingPlaylistSyncStore` field were
  removed from the host. This lowers `MainActivityBase.java` to 2927 lines and
  keeps root-package `*Bindings*` at 0 and root-package `*Controller*` at 44;
  root-package files are now 172 after adding the focused gateway and contract
  files. Guarded by `MainLibraryPlaylistActionGatewayTest` and
  `MainActivityArchitectureContractTest`. Focused tests passed with
  `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainLibraryPlaylistActionGatewayTest --tests app.yukine.MainActivityArchitectureContractTest --rerun-tasks --console=plain`
  after a default cached run exposed stale transformed class output; compile
  passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 library collection/import gateway slice (2026-06-29):
  `LibraryModule` now provides `LibraryCollectionGateway` through
  `MainLibraryCollectionGateway` and `LibraryImportGateway` through
  `MainLibraryImportGateway`. The concrete owners live beside the existing
  library collection/import use cases, so no new root-package files or root
  `*Bindings*` / `*Controller*` files were added. `MainActivityBase` now binds
  the injected collection/import gateways directly; the previous anonymous
  `new LibraryCollectionGateway() { ... }` and
  `new LibraryImportGateway() { ... }` blocks, the host-owned
  `MusicLibraryCollectionOperations`, the repeated import use case
  constructors, and the private `toLibraryLoadResultUi(...)` helper were
  removed.
  Current recheck: `MainActivityBase.java` 2873 lines,
  `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 172, and root-package Java files 15. Guarded by
  `LibraryCollectionUseCasesTest`,
  `LibraryImportUseCasesTest`, and `MainActivityArchitectureContractTest`.
  Verification passed with
  `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.LibraryCollectionUseCasesTest --tests app.yukine.LibraryImportUseCasesTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`
  and
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 library document gateway provider slice (2026-06-29):
  `LibraryModule` now provides `LibraryDocumentGateway` through
  `ContentResolverLibraryDocumentGateway` with an application `ContentResolver`
  and `MusicLibraryImportOperations(repository)`. `MainActivityBase` now binds
  the injected `libraryDocumentGateway` and no longer constructs
  `MusicLibraryImportOperations` or
  `ContentResolverLibraryDocumentGateway(getContentResolver(), ...)` locally.
  This finishes the current library import/document assembly path in Hilt
  without adding root `*Bindings*` or `*Controller*` files. Current recheck:
  `MainActivityBase.java` 2872 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 172, and root-package
  Java files 15. This slice reduces Activity manual construction and call-chain
  length; Activity field count is not claimed as a P1 exit win because the
  injected document gateway replaces local assembly. Guarded by
  `ContentResolverLibraryDocumentGatewayTest` and
  `MainActivityArchitectureContractTest`. Verification passed with
  `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.ContentResolverLibraryDocumentGatewayTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`
  and
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 settings preference provider slice (2026-06-29):
  `SettingsModule` now provides `LoadSettingsPreferencesUseCase` and
  `ApplySettingsPreferenceUseCase` from the existing settings preference
  operations. `MainActivityBase` now loads settings through the injected
  `loadSettingsPreferencesUseCase` and binds persistence through
  `applySettingsPreferenceUseCase::execute`; the host no longer constructs
  `MusicLibrarySettingsPreferenceLoadOperations`,
  `LoadSettingsPreferencesUseCase`, `MusicLibrarySettingsPreferenceOperations`,
  or `ApplySettingsPreferenceUseCase`. This keeps the Settings path as
  `SettingsViewModel -> preference/runtime applier` and does not add a settings
  gateway, coordinator, controller, or bindings layer. Current recheck:
  `MainActivityBase.java` 2866 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 173, and root-package
  Java files 15. The root file count increases by one for the feature-specific
  Hilt module; the Activity manual-construction count and call-chain length
  decrease. Guarded by `LoadSettingsPreferencesUseCaseTest`,
  `ApplySettingsPreferenceUseCaseTest`, and
  `MainActivityArchitectureContractTest`. Verification passed with
  `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.LoadSettingsPreferencesUseCaseTest --tests app.yukine.ApplySettingsPreferenceUseCaseTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`
  and
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 library store factory slice (2026-06-29):
  `MainLibraryStore` now has `MainLibraryStoreFactory`, mirroring the existing
  `MainPlaybackStoreFactory` pattern for ViewModel-bound stores. `LibraryModule`
  now provides `LibrarySearchUseCase` and `MainLibraryStoreFactory`, so
  `MainActivityBase` only calls `libraryStoreFactory.create(viewModel)`.
  The host no longer constructs `MainLibraryStore`, `LibrarySearchUseCase`, or
  `MusicLibrarySearchOperations` directly. Current recheck:
  `MainActivityBase.java` 2864 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 173, and root-package
  Java files 15. This slice reduces Activity store/search manual construction;
  Activity field count is not claimed as a win because the injected factory
  replaces local assembly. Guarded by `LibrarySearchUseCaseTest`,
  `NetworkLibraryStoreDirectAccessTest`, `PlayHistoryActionControllerTest`,
  and `MainActivityArchitectureContractTest`. The first focused-test attempt
  hit the tool timeout at 120s; a process check showed only Gradle/Kotlin
  daemons, then the same command passed with a longer timeout:
  `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.LibrarySearchUseCaseTest --tests app.yukine.NetworkLibraryStoreDirectAccessTest --tests app.yukine.PlayHistoryActionControllerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`.
  Compile passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 streaming search action handler factory slice (2026-06-29):
  `DefaultStreamingSearchActionHandler` now exposes
  `MainStreamingSearchActionHandlerFactory`, provided by `StreamingModule`.
  `MainActivityBase` now calls
  `streamingSearchActionHandlerFactory.create(streamingViewModel, streamingActionGateway)`
  and no longer directly constructs `DefaultStreamingSearchActionHandler` in
  production code. Current recheck: `MainActivityBase.java` 2865 lines,
  `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 173, and root-package Java files 15. This slice reduces
  one Activity streaming search manual-construction point; line count and field
  count are not claimed as wins because the injected factory replaces local
  construction. Guarded by `DefaultStreamingSearchActionHandlerTest` and
  `MainActivityArchitectureContractTest`; compile passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 track share operations provider slice (2026-06-29):
  `ShellModule` now provides `TrackShareOperations` through the existing
  `TrackShareManagerOperations(trackShareManager, nativeMusicShareManager)`.
  `MainActivityBase` injects `TrackShareOperations` and no longer holds
  `TrackShareManager` / `NativeMusicShareManager` fields or constructs
  `TrackShareManagerOperations` locally when creating `TrackShareLauncher`.
  Current recheck: `MainActivityBase.java` 2862 lines,
  `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 173, and root-package Java files 15. This slice reduces
  Activity fields and share operations manual construction without adding root
  files, bindings, or controllers. Guarded by `TrackShareLauncherTest` and
  `MainActivityArchitectureContractTest`; compile passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 settings store provider slice (2026-06-29):
  `SettingsModule` now provides the Activity-scoped `MainSettingsStore`.
  `MainActivityBase` injects `settingsStore` directly and no longer constructs
  `new MainSettingsStore()` inside `initializeStoresAndDataGateways()`.
  Current recheck: `MainActivityBase.java` 2861 lines,
  `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 173, and root-package Java files 15. This slice reduces
  Activity manual construction without adding root files, bindings, or
  controllers. Guarded by `LoadSettingsPreferencesUseCaseTest`,
  `ApplySettingsPreferenceUseCaseTest`, and `MainActivityArchitectureContractTest`;
  compile passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 lyrics settings use-case provider slice (2026-06-29):
  `SettingsModule` now provides `LoadLyricsSettingsUseCase` through the
  existing `MusicLibraryLyricsSettingsOperations(repository)`. `MainActivityBase`
  injects `loadLyricsSettingsUseCase` and no longer constructs
  `LoadLyricsSettingsUseCase` or `MusicLibraryLyricsSettingsOperations` locally
  while configuring `LyricsViewModel`. Current recheck:
  `MainActivityBase.java` 2858 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 173, and root-package
  Java files 15. This slice reduces Activity manual construction without adding
  root files, bindings, or controllers. Guarded by
  `LoadLyricsSettingsUseCaseTest` and `MainActivityArchitectureContractTest`;
  compile passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 lyrics loader provider slice (2026-06-29):
  `SettingsModule` now provides the `LyricsLoader` used by `LyricsViewModel`
  through the existing `LoadTrackLyricsUseCaseLyricsLoader`,
  `LoadTrackLyricsUseCase`, and `LyricsRepositoryLoadOperations` chain.
  `MainActivityBase` injects `lyricsLoader` and no longer constructs
  `LoadTrackLyricsUseCaseLyricsLoader`,
  `LoadTrackLyricsUseCase`, or `LyricsRepositoryLoadOperations` while
  configuring `LyricsViewModel`. Current recheck:
  `MainActivityBase.java` 2857 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 173, and root-package
  Java files 15. This slice reduces Activity manual construction without
  adding root files, bindings, controllers, or a new lyrics manager. Guarded by
  `LoadTrackLyricsUseCaseTest`, `LyricsViewModelTest`, and
  `MainActivityArchitectureContractTest`; compile passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 network action use-case provider slice (2026-06-29):
  `LibraryModule` now provides `NetworkActionUseCases` from the existing
  `MusicLibraryWebDavSourceOperations(repository)` and
  `MusicLibraryNetworkLibraryOperations(repository)` owners. `MainActivityBase`
  injects `networkActionUseCases` and no longer constructs
  `NetworkActionUseCases`, `MusicLibraryWebDavSourceOperations`,
  `MusicLibraryNetworkLibraryOperations`, or the 11 WebDAV/network library use
  cases in `initializeNetworkOwners()`. Current recheck:
  `MainActivityBase.java` 2840 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 173, and root-package
  Java files 15. This slice reduces Activity manual construction without
  adding root files, bindings, controllers, or a network gateway. Guarded by
  `NetworkActionsViewModelTest`, `NetworkLibraryUseCasesTest`,
  `WebDavSourceUseCasesTest`, and `MainActivityArchitectureContractTest`;
  compile passed with
  `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
- P1 library gateway policy owner slice (2026-06-29):
  `LibraryModule` now provides `MainLibraryGatewayFactory`, and
  `MainLibraryGateway` owns the former `LibraryGateway` host policy for
  track-list play, status key resolution, favorite refresh, playlist add,
  library group routing, search, import, and scan commands. `MainRouteController`
  implements the narrow `LibraryRouteActions` interface so Activity does not
  need a replacement anonymous routing block. `MainActivityBase` now only wires
  existing host/platform capabilities into the factory and no longer contains
  `libraryViewModel.bindGateway(new LibraryGateway() { ... })`. Current recheck:
  `MainActivityBase.java` 2789 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 174, and root-package
  Java files 15. Root files increase by one for a behavior owner, while the
  Activity anonymous policy block and call-chain policy move into focused
  coverage. Guarded by `MainLibraryGatewayTest`, `LibraryViewModelTest`,
  `MainRouteControllerTest`, and `MainActivityArchitectureContractTest`;
  focused tests and compile passed with default daemon/workers.
- P1 streaming action gateway policy owner slice (2026-06-29):
  `StreamingModule` now provides `MainStreamingActionGatewayFactory`, and
  `MainStreamingActionGateway` owns the former Java anonymous
  `MainActivityStreamingActionGateway` policy for selected quality, language,
  auth launch delegation, resolved-track playback, login-success playlist
  handling, and manual cookie import ordering. `MainActivityBase` now wires
  existing host/platform capabilities into the factory and no longer contains
  `new MainActivityStreamingActionGateway()`. Current recheck:
  `MainActivityBase.java` 2769 lines, `EchoPlaybackService.java` 2469 lines,
  `feature:data` `EchoDatabaseHelper.java` 2117 lines,
  `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0,
  root-package `*Controller*` 44, root-package files 175, and root-package
  Java files 15. Root files increase by one for a behavior owner, while the
  Activity anonymous streaming policy block moves into focused coverage.
  Guarded by `MainStreamingActionGatewayTest`,
  `DefaultStreamingSearchActionHandlerTest`, and
  `MainActivityArchitectureContractTest`; focused tests and compile passed
  with default daemon/workers.
- P1 now-playing playback gateway service-start slice (2026-06-29):
  `PlaybackUiModule` now provides `MainNowPlayingPlaybackGatewayFactory`, and
  `NowPlayingPlaybackServiceStarter` owns the Android
  `Intent(context, EchoPlaybackService::class.java)` start path. `MainActivityBase`
  now only binds `nowPlayingPlaybackGatewayFactory.create(() -> playbackService)`
  and no longer constructs `NowPlayingPlaybackGatewayAdapter` or the playback
  service start `Intent` locally. Current recheck: `MainActivityBase.java` 2762
  lines, `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 175, and root-package Java files 15. Guarded by
  `NowPlayingPlaybackGatewayAdapterTest`, `MainNowPlayingGatewayTest`,
  `NowPlayingViewModelTest`, and `MainActivityArchitectureContractTest`;
  focused tests and compile passed with default daemon/workers.
- P1 play-history action controller factory slice (2026-06-29):
  `LibraryModule` now provides `MainPlayHistoryActionControllerFactory`, and
  `MainActivityBase.initializeStoresAndDataGateways()` creates the existing
  `PlayHistoryActionController` through that injected factory instead of
  `new PlayHistoryActionController(...)`. The real owner and behavior stay in
  `PlayHistoryActionController`: it still clears through `LibraryViewModel`,
  updates `PlayHistoryStateStore`, publishes localized status, and reloads
  collections. Current recheck: `MainActivityBase.java` 2760 lines,
  `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 175, and root-package Java files 15. Guarded by
  `PlayHistoryActionControllerTest` and `MainActivityArchitectureContractTest`;
  focused tests and compile passed with default daemon/workers.
- P1 network actions listener policy slice (2026-06-29):
  The long `NetworkActionsViewModel.Listener` anonymous block moved out of
  `MainActivityBase.initializeNetworkOwners()` into `MainNetworkActionsListener`,
  provided by `LibraryModule` through `MainNetworkActionsListenerFactory`.
  `NetworkActionsViewModel` still owns use-case execution; the new listener
  only maps action results to library replacement, now-playing queue retain or
  replace, network navigation, collections reload, and status publication.
  `MainActivityBase` now only binds the injected listener factory with existing
  host callbacks. Current recheck: `MainActivityBase.java` 2697 lines,
  `EchoPlaybackService.java` 2469 lines, `feature:data`
  `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines,
  root-package `*Bindings*` 0, root-package `*Controller*` 44,
  root-package files 175, and root-package Java files 15. Guarded by
  `MainNetworkActionsListenerTest`, `NetworkActionsViewModelTest`, and
  `MainActivityArchitectureContractTest`; focused tests and compile passed
  with default daemon/workers.
- Streaming playback listener policy moved out of the Java base anonymous
  `StreamingPlaybackController.Listener` block into
  `MainStreamingPlaybackListener`, which is created through `PlaybackUiModule`
  and covered by `MainStreamingPlaybackListenerTest`.
- Playback start listener policy and pending playback state moved out of the
  Java base anonymous `PlaybackStartController.Listener` block into
  `MainPlaybackStartListener`, which is created through `PlaybackUiModule` and
  covered by `MainPlaybackStartListenerTest`.
- Playback state event listener policy moved out of the Java base anonymous
  `PlaybackStateEventController.Listener` block into
  `MainPlaybackStateEventListener`, which is created through
  `PlaybackUiModule` and covered by `MainPlaybackStateEventListenerTest`.
- Playback service host policy moved out of the Java base anonymous
  `PlaybackServiceHostController.Host` block into `MainPlaybackServiceHost`,
  which is created through `PlaybackUiModule` and covered by
  `MainPlaybackServiceHostTest`.
- Track-list render listener policy moved out of the Java base anonymous
  `TrackListRenderController.Listener` block into
  `MainTrackListRenderListener`, which is created through `LibraryModule` and
  covered by `MainTrackListRenderListenerTest`.
- Heartbeat recommendation listener policy moved out of the Java base
  anonymous `HeartbeatRecommendationController.Listener` block into
  `MainHeartbeatRecommendationListener`, which is created through
  `StreamingModule` and covered by `MainHeartbeatRecommendationListenerTest`.
- Document picker platform listener policy moved out of the Java base
  anonymous `DocumentPickerController.Listener` block into
  `MainDocumentPickerListener`, which is created through `PlatformModule` and
  covered by `MainDocumentPickerListenerTest`.
- Background image picker listener policy moved out of the Java base
  anonymous `BackgroundImagePickerController.Listener` block into
  `MainBackgroundImagePickerListener`, which is created through
  `PlatformModule` and covered by `MainBackgroundImagePickerListenerTest`.
- Permission result listener policy moved out of the Java base anonymous
  `MainPermissionController.Listener` block into `MainPermissionListener`,
  which is created through `PlatformModule` and covered by
  `MainPermissionListenerTest`.
- `MainActivityBase` no longer hand-constructs those dependencies; it only
  binds the injected owners into the existing feature ViewModels/controllers.
- Phase 5.6 active-download host boundary was tightened without changing
  download behavior.
- `TrackDownloadController` and `TrackDownloadActionResult` now live in
  `feature/navigation/src/main/java/app/yukine/DownloadsDestinationContracts.kt`.
- `TrackDownloadManager` stays in `:app` as the concrete download queue,
  directory, Android `DownloadManager`, and app-managed segmented download
  owner through `TrackDownloadRequestQueue`.
- `EchoNavHostState` still consumes `TrackDownloadController?`, but no longer
  depends on an app-owned contract definition for active download snapshots.
- Guarded by `MainActivityArchitectureContractTest` plus focused downloads and
  navigation tests.
- The remaining navigation shell files have now moved to `:feature:navigation`:
  `EchoNavGraph.kt`, `EchoNavHostState.kt`, and `EchoNavHostBridge.kt`.
- `:app` still owns ViewModels, platform launchers, and state/action
  production, while `EchoAppHost` wires those contracts into the feature
  navigation shell.
- Phase 6 has started as stabilization-first coordinator reduction rather than
  new event-bus expansion: forwarding-only `DownloadsCoordinator` and
  behaviorless `FileIOCoordinator` were deleted, with download track requests
  wired directly to the existing `DownloadRequestController`.
- The unused `NetworkCoordinator` has also been deleted; the live network
  request/menu/source/render wiring was already owned directly by
  `MainActivityBase` and guarded by `MainActivityArchitectureContractTest`.
- The unused `LibraryCoordinator` and `NavigationCoordinator` were deleted as
  dead assembly layers: both were constructed in `MainActivityBase` but never
  called. The active library and route/search/back/swipe paths remain in the
  existing host methods until a later slice can move behavior to a true owner.
- `SettingsCoordinator` was deleted as a duplicate runtime binding layer:
  `MainActivityBase` already constructs `SettingsRuntimeApplier` and binds it
  directly to `SettingsViewModel`; the coordinator only rebound the same
  listener and its render method had no caller.
- `PlaybackCoordinator` was narrowed to playback-start delegation plus service
  connection lifecycle. It no longer owns action/result application, ViewModel
  or store dependencies, state controllers, or its own pending playback cache;
  pending playback remains owned by `PlaybackStartController`.
- `PlaybackCoordinator` was then deleted after becoming pure forwarding:
  `MainActivityBase` now calls `PlaybackStartController`,
  `PlaybackServiceConnectionController`, and
  `EchoPlaybackService.setAppVisible(...)` directly.
- `NowPlayingQueueCoordinator` was narrowed to the live queue intent listener
  binding. It no longer owns unused Now Playing effect handling, queue render
  helpers, queue input binding, store/settings/status dependencies, or
  render/state controller constructor parameters.
- `NowPlayingQueueCoordinator` was then deleted after confirming
  `mountNavHostShell()` already installs the actual
  `queueViewModel.bindIntentListener(...)` path and overwrote the coordinator
  binding. Queue intents remain handled through the existing NavHost shell path
  and `QueueActionController`.
- `StreamingCoordinator` was deleted after its only live production use was
  forwarding initial/new intents to `StreamingAuthCallbackController`. The host
  now calls that controller directly, while the existing `MainActivityBase`
  streaming refresh, gateway endpoint, playback, quality, and source-switch
  methods remain the active implementations.
- `NetworkMenuRenderController`, `NetworkTrackListRenderController`,
  `NetworkSourcesRenderController`, and `StreamingSearchRenderController`
  remain focused render owners consumed by `NetworkRenderCoordinator`, but
  `MainActivityBase` now constructs them as local assembly values instead of
  retaining Activity fields.

---

## 0. 迁移原则

### 0.1 目标架构

目标不是套一个笨重框架，而是把当前混合架构收敛成清晰、轻量、可测试�?MVVM�?
```text
Compose UI
  -> Feature ViewModel
  -> UseCase / Feature Coordinator
  -> Repository / Platform Gateway / Playback Boundary
  -> Database / MediaStore / Service / Network / Android API
```

### 0.2 禁止方向

- 不要�?`MainActivity` 继续当总调度中心�?- 不要�?`MainActivity` 拆成另一个万�?`AppCoordinator` / `GodManager`�?- 不要为了 MVVM 增加无意义转发层�?- 不要让一个按钮点击经�?7�? 层只为了保存一个设置�?- 不要�?Compose 同时�?ViewModel state �?Activity 手动同步 state�?- 不要�?ViewModel 直接持有 `Context`、`Intent`、Dialog、Activity Result 等平台细节�?- 不要为了架构整洁破坏播放、后台服务、通知、小部件、歌词、下载、流媒体登录、首次引导�?
### 0.3 迁移验收总目�?
- `MainActivity.java` 从当前约 3171 行降到宿主级别，目标小于 500 行，长期目标小于 300 行�?- `EchoPlaybackService.java` 保留播放服务职责，但 UI 不直接依赖具�?service�?- 单个 ViewModel 目标小于 500 行；复杂 feature 可拆多个 ViewModel�?- UI 只消�?`UiState`，只发�?`UiAction` / `UiEvent`�?- 平台能力通过小型 owner 暴露：picker、permission、backup、dialog、share、system setting�?- 播放、设置、曲库、队列、歌词、流媒体、推荐都能单独单测�?
---

## 1. 当前架构事实

### 1.1 大类规模

当前重点大类�?
| 文件 | 当前角色 | 风险 |
|---|---|---|
| `MainActivity.java` | 应用宿主 + 路由 + 平台 API + �?feature 装配 + 大量回调 + 手动状态同�?| 上帝类，新增功能容易继续挂线 |
| `EchoPlaybackService.java` | Media3 播放服务、队列、通知、小部件、播放状态、部分音�?| 播放边界合理，但 UI/Activity 直接触碰过多 |
| `StreamingViewModel.kt` | 流媒体搜索、登录、导入、播放解析、推荐等大块逻辑 | 仍偏大，feature 边界未完全拆开 |
| `SettingsViewModel.kt` | 设置 UI state、事件、保存偏好、状态文�?| 已有 MVVM 雏形，但外围转发链过�?|
| `EchoNavHostState.kt` | Compose NavHost �?ViewModel 集合 + 大量 mutable chrome/actions/callback | �?ViewModel state 重复，Activity 手动同步 |

### 1.2 MainActivity 当前职责

`MainActivity.java` 目前至少承担这些职责�?
- 创建和绑定多�?ViewModel�?- 创建和持有几十个 Controller / Bindings�?- 保存 `MainRouteController`、`MainSettingsStore`、`MainLibraryStore`、`MainPlaybackStore`�?- 绑定播放服务，直接读�?`EchoPlaybackService`�?- 处理权限、Activity Result、文档选择、图片选择、备份导入导出�?- 创建多个 Dialog：网络源、歌单、流媒体 Cookie、流媒体歌单导入等�?- 持有 Compose 导航 chrome 临时状态：`navTrackListActions` 等；`navSettingsActions` 已清退，`navNetworkMenuActions` 已由 `NetworkMenuViewModel` 接管�?- 手动调用 `renderSelectedTab()`、`renderNowBar()`、`syncNavHostState()`�?- 处理曲库、歌单、播放、搜索、网络、设置、推荐多�?feature 的具体业务流程�?
### 1.3 当前事件路径示例

#### 设置项点�?
```text
SettingsScreen
  -> SettingsAction.onClick
  -> SettingsViewModel.onEvent
  -> SettingsEffect / SettingsPreferenceGateway / SettingsRuntimeEffectListener
  -> settingsStore / repository / runtime applier
  -> renderCurrentPage / state update
```

问题：链路太长，很多层只转发，无策略�?
#### 导航/页面 chrome

```text
RenderController 生成 actions
  -> MainActivity nav* 字段
  -> syncNavHostState()
  -> EchoNavHostState mutableStateOf
  -> EchoNavGraph / Destination
```

问题：页�?state 不由 feature ViewModel 直接拥有，Activity 变成 Compose state 中转站�?
#### 播放状�?
```text
EchoPlaybackService.snapshot()
MainPlaybackStore.snapshot()
PlaybackViewModel.playback
NowPlayingViewModel state
QueueViewModel bind(...)
MainActivity publishNowPlayingState(...)
```

问题：播放状态有多个读点和镜像点，容易出现谁是事实源不明确�?
---

## 2. 目标包与命名建议

不要第一步就大搬家。先�?feature 迁移，稳定后再整理包�?
建议长期包结构：

```text
app.yukine
  core/
    model/
    ui/
    platform/
    result/
  data/
    settings/
    library/
    playback/
    lyrics/
    streaming/
    backup/
  domain/
    settings/
    library/
    playback/
    lyrics/
    streaming/
    recommendation/
  feature/
    shell/
    home/
    library/
    playlists/
    player/
    queue/
    settings/
    network/
    streaming/
    downloads/
    onboarding/
  playback/
    service/
  navigation/
  ui/
    components/
    theme/
```

短期不强制移动已有文件。迁移新 owner 时优先放到现有相邻包，避免大规模 import churn�?
---

## 3. 全局迁移步骤

### 3.1 �?0 阶段：建立迁移护�?
#### 3.1.1 写架构契约测�?
目标：阻止继续往 `MainActivity` 塞新业务�?
具体任务�?
- 更新或新�?`MainActivityArchitectureContractTest.java`�?- 增加断言�?  - `MainActivity.java` 不新增新�?`showXxxDialog`，除非是平台 owner 的安装入口�?  - `MainActivity.java` 不新增新�?`startActivityForResult`�?  - `MainActivity.java` 不新增新�?`nav*Actions` 字段�?  - 新设置项不得要求同时修改 `SettingsGateway`、`SettingsGatewayBindings`、`SettingsActionController`、`MainActivity` 四层以上�?
验收�?
- 新增一个纯偏好设置时，不需要改 `MainActivity.java`�?- 契约测试失败信息能指出应该放到哪�?owner�?
#### 3.1.2 建立迁移工作�?
每个迁移 PR/提交必须写清�?
- 移走哪个职责�?- 原入口和新入口�?- 状态事实源是谁�?- 事件路径缩短了几层�?- 保留哪些兼容 facade�?- 删除哪些旧转发�?- 跑了哪些测试�?
---

## 4. MainActivity 收缩清单

### 4.1 �?Activity Result / Picker 能力

#### 现状

`MainActivity` 直接处理�?
- 音频文件选择�?- 音频文件夹选择�?- M3U/M3U8 导入导出�?- LX 源文件导入�?- 背景图片选择�?- 下载目录选择�?- 备份导入导出�?
已有 `DocumentPickerController`，但仍有�?picker 直接回流 Activity�?
#### 目标

Activity 只注�?launcher，所�?picker 逻辑由独�?owner 管：

```text
MainActivity
  -> PlatformLauncherRegistry
  -> DocumentPickerController
  -> BackupRestoreLauncher
  -> BackgroundImagePickerController
  -> DocumentPickerController.openDownloadFolderPicker
```

#### 具体任务

1. 新增 `BackgroundImagePickerController`�?   - 输入：`page: PageBackgroundTarget`�?   - 输出：`Flow` / callback `BackgroundImagePicked(page, uri)`�?   - 内部处理 `ACTION_OPEN_DOCUMENT`、`image/*`、持久读权限�?   - Activity 只调�?`backgroundImagePicker.open(page)`�?
2. 新增 `BackupRestoreLauncher`�?   - 替代 `exportBackup()`、`importBackup()`、`handleBackupResult()`�?   - 输出 `BackupEffect.ShowStatus` �?callback�?   - 保持 `BackupManager.INSTANCE.export/restore` 不变�?
3. 收敛 `DocumentPickerController`�?   - 把所�?`REQUEST_*` 常量集中�?controller�?   - Activity �?`onActivityResult` 只调用一�?`platformResultDispatcher.dispatch(...)`�?
4. 后续改用 Activity Result API�?   - 先封装旧 `startActivityForResult`，再迁到 `registerForActivityResult`�?   - 不要�?feature ViewModel 中直接使�?launcher�?
#### 文件

- `MainActivity.java`
- `DocumentPickerController.kt`
- 新增 `BackgroundImagePickerController.kt`
- 新增 `BackupRestoreLauncher.kt`
- 新增 `PlatformActivityResultDispatcher.kt`

#### 验收

- `MainActivity.java` 中不再出现新增业�?picker �?`Intent` 构造�?- `MainActivity.java` �?`onActivityResult` 不包含业务判断，只做分发�?- 背景图片、备份、音频导入、M3U 导入全部原行为可用�?
#### 测试

- `BackgroundImagePickerControllerTest`：page 归一化、空 uri、持久权限失败降级�?- `BackupRestoreLauncherTest`：导�?导入 success/fail 映射状态�?- 手动回归：选图后重启仍显示；备份导�?导入仍返回状态�?
#### 迁移进展�?026-06-23 / 2026-06-24 更新�?
- 背景图片选择已迁�?`BackgroundImagePickerController` / `BackgroundImagePickerBindings`�?- 备份导入导出已迁�?`BackupRestoreLauncher` / `BackupRestoreBindings`�?- 状态消息本地化和最新状态事实源已迁�?`StatusMessageViewModel`；`StatusMessageController` 保留�?Activity 兼容门面�?- 设置页新�?`SettingsPage` typed model，`SettingsBackStack` 已改�?typed page；外�?route 字符串暂保留为兼容层�?- 新增 `SettingsPageStateBuilder`，已承接 `title + metrics + actions -> SettingsUiState` �?mapper，并已迁�?`Home` / `AboutGroup` / `AppearanceGroup` / `SourcesGroup` / `PlaybackGroup` / `LyricsGroup` / `Theme` / `AdvancedTheme` / `Accent` / `Language` / `PageBackground` / `AudioEffects` / `ConcurrentPlayback` / `NowPlayingGestures` / `PlaybackRestore` / `ReplayGain` / `StatusBarLyrics` / `FloatingLyrics` / `SleepTimer` / `PlaybackSpeed` / `AppVolume` / `StreamingAudioQuality` / `ShareStyle` / `StreamingGateway` / `Library` / `Lyrics` 页面装配；后续可继续处理其他 group 页等剩余设置页�?- `SettingsViewModel` 已具�?`SettingsState(page, preferences, runtime, actions, ui)` 状态源，`NavigateSettingsPage` 事件会直接基于当�?`SettingsPreferencesSnapshot` / `RuntimeSettingsStatus` 调用 `renderCurrentPage(...)` 重建页面，不再通过 `SettingsGateway` / `MainActivity.navigateSettingsPage(...)` 回流。`SettingsDestination` 已直接消�?`SettingsViewModel.state` 中的 `actions/ui`，`MainActivity` / `EchoNavHostState` 不再持有 `navSettingsActions`；`SettingsRenderCoordinator`、`SettingsPageEventController`、`SettingsPageChromeBindings`、`SettingsScrollStateSink` 已删除，`SettingsPageRenderController` 现在只作为滚动兼容门面，`SettingsViewModel` 自身持有 `scrollState`，不再把设置列表滚动状态放在页面渲染器里�?- `NetworkMenuViewModel` 已接管网络页标题 / metrics / actions 状态；`NetworkMenuChromeBindings` 只负责把导航 chrome 同步进该 ViewModel，`NetworkDestination` 直接消费�?`uiState`，`EchoNavHostState` 不再保存 `networkMenuTitle` / `networkMenuMetrics` / `networkMenuActions` 这类页面临时 state�?- `HomeDashboardViewModel` 已接管首�?`HomeDashboardActions`；`HomeDashboardRenderBindings` 直接�?actions 发布�?ViewModel，`HomeDestination` �?`MainActivityHomeDashboardUiState` 同时消费内容�?actions，`EchoNavHostState` 不再保存 `homeActions` / `navHomeActions`�?- `SettingsGatewayBindings` 已删除：主题、强调色、语言、播放速度、应用音量、流媒体音质、分享样式、并发播放、在线歌词、歌词偏移、音效、状态栏歌词、悬浮歌词、播放页手势、播放恢复、ReplayGain 等纯偏好/运行时设置事件由 `SettingsViewModel.onEvent(...)` 直接调用自身 apply/set 方法并通过 `SettingsPreferenceGateway` 保存；睡眠计时、加载曲库、歌词重载、流媒体网关 endpoint 等外部协作也已改�?`SettingsEffect`。`SettingsActionController` / `SettingsActionBindings` 已删除�?- 设置平台 effect 已继续收敛：`OpenNetworkSources` / `OpenDownloads`、加载曲库、音频文�?文件夹选择、悬浮歌词权限入口、页面背景选择、备份导入导出、歌词重载、睡眠计时、流媒体网关 endpoint 应用都由 `SettingsViewModel` 发出 `SettingsEffect`，`MainActivity` 直接�?effect 接到 `SettingsEffectBindings` 执行页面切换、平�?launcher 或播�?歌词边界调用，不再占�?`SettingsGatewayBindings` / `SettingsActionController` 构造参数；清除页面背景�?`SettingsViewModel` 基于当前 `SettingsPreferencesSnapshot.pageBackgrounds` 直接更新、保存并重建当前设置页�?- 设置运行时副作用 owner 已继续推进：`SettingsRuntimeApplier` 承接主题 surface、语言 surface、播�?service 控制、歌词开�?偏移与悬浮歌词权限应用；运行时应用已类型化为 `SettingsRuntimeEffect`，并�?`SettingsViewModel.applyXxx(...)` 保存路径通过 `SettingsRuntimeEffectListener` 直接发出，`MainActivity` 只接�?`settingsViewModel.bindRuntimeEffectListener(settingsRuntimeApplier::apply)`。`SettingsViewModel` 现在会在纯偏�?运行时设置应用后同步更新自身 `SettingsPreferencesSnapshot` / `RuntimeSettingsStatus` 并重建当前页，通过 `SettingsStoreMirror` 同步 `MainSettingsStore` 兼容镜像，并直接发出 `SettingsEffect.ShowStatus` / `SettingsEffect.ReloadCurrentLyrics`；`SettingsAppliedListenerBindings` 已删除，状态文案和歌词重载不再需要额�?applied listener 兼容层�?- `MainActivity` 只创�?controller、调�?`open(page)`，并�?`onActivityResult` 委托 `handleActivityResult(...)`�?- `MainActivity` 不再持有背景图片/备份选择�?request code、pending page、`image/*` �?`application/zip` Intent 构造�?- 音频文件、音频文件夹、歌�?M3U 导入、网络页 M3U 导入这类无额外兜底状态的 picker 入口已继续直�?`DocumentPickerController.openAudioFilePicker/openAudioFolderPicker/openPlaylistM3uFilePicker/openM3uFilePicker`；`MainActivity` 不再保留 `openAudioFilePicker()` / `openAudioFolderPicker()` / `openPlaylistM3uFilePicker()` / `openM3uFilePicker()` 单行 wrapper。下载目录选择的兜底反馈已迁入 `DownloadDirectoryPickerController`，`DownloadsEffect.OpenDirectoryPicker` 通过 `EchoNavHostState.openDownloadDirectoryPickerAction` 调用�?controller，`MainActivity` 不再保留 `openDownloadFolderPicker()` wrapper。洛雪源导入已迁�?`LuoxueSourceImportController` / `LuoxueSourceImportDialogController`：文�?picker 缺失兜底、URL 输入、后台读�?拉取、解析保存、状态提示和 streaming provider 刷新均由�?owner 承接，`MainActivity` 只保留装配和 `DocumentPickerController`/`ContentResolver` 平台适配，不再保�?`openLuoxueSourceFilePicker()` / `showLuoxueSourceUrlDialog()` / `importLuoxueSourcesFromUrls()` / `saveImportedLuoxueSources()` 等业务方法�?- 流媒体手�?Cookie 输入弹窗已迁�?`StreamingManualCookieDialogController`，`StreamingManualCookieController` 继续负责认证请求和登录成功回调；`MainActivity` 只装�?dialog owner，不再保�?`showStreamingCookieDialogContent(...)` 或直接拼�?Cookie `EditText` / `EchoDialog`�?- 流媒体歌单链接输入弹窗已迁入 `StreamingPlaylistImportDialogController`：普�?provider 的链接输入、洛�?provider 的源导入分流、确认后进入 `StreamingPlaylistController.importStreamingPlaylistFromLink(...)` 均由�?owner 承接；`MainActivity` 不再保留 `showImportStreamingPlaylistDialog()` 或直接拼装歌单链�?`EditText` / `EchoDialog`�?- 流媒体歌单导�?provider picker、导入完成提示、账号歌单多选导入、收藏导�?provider picker 已迁�?`StreamingPlaylistDialogController`；`StreamingPlaylistController` 保持业务编排，`MainActivity` 只装�?dialog owner，不再保�?`showStreamingProviderPicker(...)` / `showStreamingPlaylistLoadedDialog(...)` / `showAccountPlaylistImportPicker(...)` / `showImportStreamingFavoritesProviderPicker()` / `accountPlaylistLabel(...)`�?- 结果回调继续进入 `SettingsViewModel.applyPageBackgrounds(...)`；页面背景状态事实源仍是 `PageBackgrounds` / settings preferences�?- 备份结果回调继续映射 `backup.*` 状�?key，并�?Activity 现有状态栏入口展示�?- 2026-06-24 审查补丁：启动首屏实时频谱轮询改为仅在播放中运行，未播放/服务未连接时复用空频谱，避免 app 打开动画期间每帧触发 Compose 重组；QQ 音乐登录态校验收紧为需�?`qqmusic_key` / `qm_keyst` / `psrf_qqaccess_token` 等真实凭证，避免只含 `uin/p_uin` 的假登录态进入播放解析�?
---

### 4.2 �?Dialog 能力

#### 现状

`MainActivity` 直接创建多个 `EchoDialog`�?
- WebDAV/网络源编辑�?- 歌单创建/重命�?删除确认�?- 添加到歌单�?- 流媒�?provider picker�?- 流媒�?Cookie 手动输入�?- LX 源导入方式选择�?- 账号歌单导入多选�?- 下载音质选择�?
#### 目标

Dialog 变成 UI effect，由 feature ViewModel �?feature coordinator 请求�?
```text
FeatureViewModel emits Effect.ShowDialog(...)
  -> Activity / DialogHost collects effect
  -> DialogController renders
  -> Result sent back as ViewModel action
```

#### 具体任务

1. 定义通用 effect�?
```kotlin
sealed interface UiEffect {
    data class ShowMessage(val text: String) : UiEffect
    data class ShowDialog(val request: DialogRequest) : UiEffect
    data class LaunchPlatformAction(val request: PlatformRequest) : UiEffect
}
```

2. 每个 feature 定义自己�?dialog request�?   - `PlaylistDialogRequest.Create`
   - `PlaylistDialogRequest.Rename`
   - `PlaylistDialogRequest.DeleteConfirm`
   - `StreamingDialogRequest.ProviderPicker`
   - `StreamingDialogRequest.CookieInput`
   - `DownloadDialogRequest.QualityPicker`

3. 新增 `DialogHostController`�?   - 只负责把 request 渲染�?`EchoDialog`�?   - 不做业务�?   - 用户确认后调�?`onDialogResult(result)`�?
4. 删除 `MainActivity` 中直接拼 dialog layout 的逻辑�?
#### 文件

- `MainActivity.java`
- `NetworkDialogController.java`
- `PlaylistDialogController.java`
- `ConfirmationDialogController.kt`
- `StreamingManualCookieController.kt`
- 新增 `DialogRequest.kt`
- 新增 `DialogHostController.kt`

#### 验收

- feature 业务不依�?`EchoDialog`�?- `MainActivity` 不再知道 Cookie 输入框、账号歌单多选布局、下载音质选项细节�?- Dialog 结果可单测�?
---

### 4.3 抽状态消息能�?
#### 现状

多处直接 `setStatus(AppLanguage.text(...))`�?
#### 目标

统一为：

```text
ViewModel / UseCase -> UiEffect.ShowMessage(messageKey or message)
StatusMessageViewModel -> Compose Snackbar/Status UI
```

#### 具体任务

1. 新增 `StatusMessageViewModel` 或复�?`StatusMessageController` 但让 Compose collect�?2. 定义�?
```kotlin
data class StatusMessage(
    val text: String,
    val level: StatusLevel = Info,
    val source: String = ""
)
```

3. 新增 `MessageTextResolver`�?   - �?UI 层根�?`languageMode` 解析 key�?   - Repository/UseCase 不拼 UI 文案�?
4. 逐步替换 `MainActivity.setStatus` 调用�?
#### 验收

- �?feature 不直接调�?`MainActivity.setStatus`�?- 状态消息可以在单元测试中断言 effect�?
迁移进展�?026-06-24）：

- `showActionFeedback(...)` 的空消息过滤与发布已下沉�?`StatusMessageController.showFeedback(...)`；下载请求、下载目录兜底、在线播放解�?音源切换、分享反馈等入口直接调用状态消�?owner，`MainActivity` 不再保留 `showActionFeedback(...)` �?wrapper�?- `MessageTextResolver` 已引入作�?UI �?message key 解析入口；备份导�?导出�?`backup.*` statusKey 现在通过 `StatusMessageController.setStatusKey(...)` 发布，`MainActivity` 不再�?`BackupRestoreLauncher` 直接调用 `AppLanguage.text(...)`�?- 设置返回栈已继续 typed 化：`MainBackNavigationPolicy` 直接接收 `SettingsPage`，`MainRouteController` 先把当前 settings route 转成 typed page 再参�?back policy，`SettingsBackStack.parentPage(String)` 兼容适配器已移除�?
---

## 5. 设置模块迁移清单

设置模块是最适合先完�?MVVM 化的样板�?
### 5.1 当前问题

当前设置链路分散�?
- `SettingsViewModel` 已同时持�?`SettingsState`、`SettingsUiState` 和滚动状态，事件大多已在 ViewModel 内处理，少量平台能力仍通过 effect 走外�?owner�?- `SettingsPageRenderController` 只保留页面滚动兼容入口与少量 label helper�?- `SettingsRenderCoordinator` 根据 `settingsPage` 调不�?render 方法�?- `SettingsGatewayBindings` 是很长的构造参数列表�?- `SettingsAppliedListenerBindings` 同时更新 `MainSettingsStore`、service、lyrics、状态消息、重新渲染�?- `MainSettingsStore`、DB settings、`SettingsViewModel`、`EchoNavHostState` 都参与状态�?
### 5.2 目标

设置页终态：

```text
SettingsScreen
  collect SettingsViewModel.uiState
  send SettingsAction

SettingsViewModel
  owns current page
  owns displayed settings state
  calls SettingsUseCases
  emits SettingsEffect

SettingsRepository
  load/save persistent preferences

SettingsRuntimeApplier
  applies runtime side effects: theme, playback speed, lyrics switches
```

### 5.3 具体任务

#### 5.3.1 合并页面渲染进入 ViewModel

当前�?
```text
SettingsViewModel.onEvent(NavigateSettingsPage(page))
  -> renderCurrentPage(page, preferences, runtime)
  -> state/ui 更新
```

目标�?
```text
SettingsViewModel.onAction(NavigatePage(page))
  -> state = buildPageState(page, preferences, runtimeStatus)
```

执行步骤�?
1. 新增 `SettingsPageStateBuilder`�?   - �?`SettingsPageRenderController` 迁出�?mapper 逻辑�?   - 输入 `SettingsPreferencesSnapshot`、`RuntimeSettingsStatus`、`languageMode`、`page`�?   - 输出 `SettingsUiState`�?
2. �?`SettingsViewModel` 持有�?
```kotlin
data class SettingsState(
    val page: SettingsPage,
    val preferences: SettingsPreferencesSnapshot,
    val runtime: RuntimeSettingsStatus,
    val ui: SettingsUiState
)
```

3. `SettingsRenderCoordinator` 降级或删除�?
验收�?
- 设置页切换不需�?`MainActivity.navigateSettingsPage()`�?- `SettingsPageRenderController` 不再直接调用 `viewModel.updatePage`，页面滚动状态由 `SettingsViewModel.scrollState` 持有�?
#### 5.3.2 替换 string page �?typed page

当前�?
- `MainRoutes.SETTINGS_*` 字符串�?- `SettingsBackStack.parentPage(settingsPage: String)`�?
目标�?
```kotlin
sealed interface SettingsPage {
    data object Home : SettingsPage
    data object AppearanceGroup : SettingsPage
    data object Theme : SettingsPage
    data object Accent : SettingsPage
    data object Language : SettingsPage
    data object PageBackground : SettingsPage
    ...
}
```

执行步骤�?
1. 新增 `SettingsPage.kt`�?2. 新增 `SettingsPage.fromRoute(route: String)` 兼容旧路由�?3. 修改 `SettingsBackStack` 接收 `SettingsPage`�?4. Compose 设置页只发�?typed page�?5. 保留�?`MainRoutes.SETTINGS_*` 作为导航兼容层，最后删除�?
验收�?
- 新设置页不再新增裸字符串 route�?- 设置返回栈单测覆盖每�?page�?
#### 5.3.3 合并 SettingsGateway/Bindings

当前 `SettingsGatewayBindings` 有大量一行转发�?
目标�?
```kotlin
SettingsViewModel.onAction(SettingsAction.ApplyTheme(mode))
  -> applySettingUseCase(...)
  -> emit RuntimeEffect.ApplyTheme(mode)
```

需要保留的边界�?
- 打开系统权限页�?- 打开文件选择器�?- 打开目录选择器�?- 打开网络源页面�?- 打开下载页面�?
其他纯偏好保存不应通过 `SettingsGateway`�?
执行步骤�?
1. 定义�?
```kotlin
sealed interface SettingsPlatformEffect {
    data object OpenAudioFilePicker : SettingsPlatformEffect
    data object OpenAudioFolderPicker : SettingsPlatformEffect
    data object OpenFloatingLyricsPermission : SettingsPlatformEffect
    data class ChoosePageBackground(val page: PageBackgroundTarget) : SettingsPlatformEffect
    data object OpenDownloads : SettingsPlatformEffect
    data object OpenNetworkSources : SettingsPlatformEffect
}
```

2. `SettingsViewModel` 对纯设置直接保存�?3. 对平台行�?emit effect�?4. Activity/DialogHost �?collect effect�?5. 删除 `SettingsGatewayBindings` 中非平台方法�?
验收�?
- 应用主题/强调�?语言/播放速度/音量/ReplayGain 不经�?Activity�?- 平台选择器仍通过 Activity/launcher 执行�?
#### 5.3.4 建立 SettingsRepository

当前偏好�?`MusicLibraryRepository -> EchoDatabaseHelper`�?
目标�?
```kotlin
interface SettingsRepository {
    val preferences: Flow<SettingsPreferencesSnapshot>
    suspend fun save(update: SettingsPreferenceUpdate)
}
```

执行步骤�?
1. 新增 `SettingsPreferencesSnapshot`，包含：
   - themeMode
   - accentMode
   - languageMode
   - playbackSpeed
   - appVolume
   - streamingAudioQuality
   - concurrentPlaybackEnabled
   - audioEffectSettings
   - statusBarLyricsEnabled
   - floatingLyricsEnabled
   - nowPlayingGesturesEnabled
   - playbackRestoreEnabled
   - replayGainEnabled
   - shareStyle
   - pageBackgrounds

2. 新增 `DatabaseSettingsRepository`�?3. 先内部复�?`MusicLibraryRepository` �?`EchoDatabaseHelper`，后续再移出�?4. 删除 `LoadSettingsPreferencesUseCase` �?`ApplySettingsPreferenceUseCase` 中重复接口，或让它们依赖 `SettingsRepository`�?
验收�?
- 设置读写有唯一 Repository�?- `MainSettingsStore` 不再是主要状态源�?
#### 5.3.5 设置运行时副作用 owner

当前 `SettingsAppliedListenerBindings` 同时处理�?
- 更新 `MainSettingsStore`�?- 应用主题�?- 更新语言�?- �?playback service�?- �?lyrics view model�?- �?floating lyrics service�?- setStatus�?- renderSelectedTab/renderNowBar�?
目标拆分�?
```text
SettingsRuntimeApplier
  -> ThemeRuntimeApplier
  -> PlaybackRuntimeSettingsApplier
  -> LyricsRuntimeSettingsApplier
  -> FloatingLyricsRuntimeApplier
```

执行步骤�?
1. 新增 `SettingsRuntimeEffect`�?
```kotlin
sealed interface SettingsRuntimeEffect {
    data class ApplyTheme(val mode: String, val accent: String) : SettingsRuntimeEffect
    data class ApplyPlaybackSpeed(val speed: Float) : SettingsRuntimeEffect
    data class ApplyAppVolume(val volume: Float) : SettingsRuntimeEffect
    data class ApplyAudioEffects(val settings: AudioEffectSettings) : SettingsRuntimeEffect
    data class SetStatusBarLyrics(val enabled: Boolean) : SettingsRuntimeEffect
    data class SetFloatingLyrics(val enabled: Boolean) : SettingsRuntimeEffect
}
```

2. `SettingsViewModel` 保存成功�?emit effect�?3. Activity 只把 effect 交给 `SettingsRuntimeApplier`�?4. `SettingsRuntimeApplier` 不做 UI 渲染，只做运行时应用�?5. UI 刷新�?`SettingsViewModel.uiState` 自然更新�?
验收�?
- `SettingsAppliedListenerBindings` 已删除，后续不得恢复；设置应用后的状态提示通过 `SettingsEffect.ShowStatus`，在线歌词重载通过 `SettingsEffect.ReloadCurrentLyrics`�?- 应用设置不调�?`renderSelectedTab()`�?
---

## 6. 导航�?Compose Shell 迁移清单

### 6.1 当前问题

`EchoNavHostState` 同时持有�?
- 多个 ViewModel�?- 当前 tab�?- 每个页面 action 列表�?- 每个页面 labels/metrics/empty text�?- 多个 callback�?- NowPlaying 手势开关�?- 页面背景�?- 搜索和下载目�?action�?
它本质是 Activity �?Compose 的大状态包�?
### 6.2 目标

```text
EchoAppHost
  -> EchoNavGraph
      collect NavigationViewModel.state
      each Destination collect its own ViewModel.state
```

`EchoNavHostState` 最终删除，或只保留很薄的：

```kotlin
data class EchoNavHostState(
    val appChromeState: AppChromeState
)
```

### 6.3 具体任务

#### 6.3.1 Shell typed state 暂缓（不要恢复 AppShellViewModel）

2026-06-29 correction: the provisional `ShellViewModel` / `ShellState` /
`ShellAction` line has been deleted and is guarded by
`MainActivityArchitectureContractTest`. The old sketch below is historical
context only, not an active migration step. Do not reintroduce an
`AppShellViewModel` unless it replaces the current `NavigationViewModel` /
`MainRouteController` / `EchoNavHostState` path as the single shell state
source.

职责�?
- 当前 tab�?- 是否显示 onboarding�?- bottom nav labels�?- NowBar 是否显示�?- 页面背景选择�?- 全局状态消息�?
不负责：

- 曲库 action�?- 设置 action�?- 网络 action�?- 具体页面业务�?
新增�?
```kotlin
data class AppShellUiState(
    val selectedTab: TabRoute,
    val tabs: List<EchoTabItem>,
    val showOnboarding: Boolean,
    val pageBackgrounds: PageBackgrounds,
    val nowBarVisible: Boolean
)
```

验收�?
- `EchoNavGraph` 不再�?`EchoNavHostState` 读取页面 action�?- tab 切换可单测�?
#### 6.3.2 页面 action 回归页面 ViewModel ✅ 已完成（2026-06-25 验证）

验证结论：以下所有 nav* 字段均已从 `MainActivity.java` 删除，grep 无匹配。曲库 action 已类型化为 `LibraryEvent`，队列为 `QueueIntent`，各页面 ViewModel 直接持有 UiState。

补充记录：`QueueIntentBindings` 已移除；2026-06-28 进一步删除 `QueueIntentController`，`QueueViewModel.bindIntentListener(...)` 现在直接把 `QueueIntent` 分发到 `LibraryEvent`、`QueueActionController` 和页面 owner，避免队列意图再经过一层纯转发 controller。

补充记录：`StreamingTrackMatchStoreBindings` 已移除，`StreamingTrackMatchUseCase` 直接实现 `StreamingTrackMatchStore`，`StreamingViewModel`、`StreamingRecommendationViewModel` 和心跳推荐 seed resolver 共享同一个 use case/store 实例，减少 use case -> bindings -> store 的纯转发层。

补充记录：`HeartbeatRecommendationSeedResolverBindings` 已移除，`HeartbeatRecommendationSeedResolver` 直接实现 `HeartbeatSeedRequestProvider` 并接收 service/store/viewModel/library 快照 provider，心动推荐 seed 请求不再经过额外 supplier 聚合绑定层。

补充记录：`LuoxueSourceImportBindings` 已移除，`ContentResolverLuoxueSourceDocumentReader` 归入 `LuoxueSourceImportController.kt`，洛雪源导入的文档读取平台适配留在该 feature owner 内，不再单独保留一层 binding 文件。

补充记录：`LyricsStateListenerBindings` 已移除，歌词状态刷新策略改为 `LyricsStateRefreshListener` 并归入 `LyricsViewModel.kt`，now bar 刷新和播放页内容刷新仍由可测 listener 保护，但不再保留独立 bindings 文件。

补充记录：`ConfirmationDialogBindings` 已移除，`ConfirmationDialogController.Listener` 由 `MainActivity` 直接提供，播放历史清空、队列清空、网络流媒体删除和远程源删除确认不再经过独立纯转发 binding 文件。

要删除的 Activity 字段（全部已删除）：
- `navTrackListActions`
- `navTrackListHeaderMetrics`
- `navTrackListHeaderActions`
- `navTrackListEmptyText`
- `navTrackListModeActions`
- `navTrackListLabels`
- `navLibraryGroupActions`
- `navLibraryGroupEmptyText`
- `navLibraryGroupModeActions`
- `navCollectionsActions`
- `navSettingsActions`
- `navSettingsScrollState`
- `navNetworkSourceActions`
- `navNetworkSourceHeaderActions`
- `navNetworkSourceEmptyText`
- `navNetworkSourceLabels`
- `navNetworkMenuTitle`
- `navNetworkMenuMetrics`
- `navNetworkMenuActions`
- `navStreamingSearchLabels`
- `navStreamingSearchActions`
- `navSearchActions`

迁移方式�?
- `HomeDashboardViewModel` 输出 `HomeDashboardUiState` + action event�?- `LibraryViewModel` 输出 `LibraryUiState`，包�?mode actions/header actions�?- `SettingsViewModel` 输出 `SettingsUiState`，包�?settings actions�?- `NetworkSourcesViewModel` 输出 `NetworkSourcesUiState`�?- `StreamingSearchViewModel` 输出 `StreamingSearchUiState`�?
验收�?
- `syncNavHostState()` 不再�?30 多个 setter�?- 新页�?action 不需要加 Activity 字段�?
#### 6.3.3 路由状态单源化

当前�?
- `MainRouteController`
- `NavigationViewModel`
- `EchoNavHostState.selectedTabRoute`
- Compose pager state

目标�?
- `NavigationViewModel` �?Compose Navigation 是唯一事实源�?- `MainRouteController` 只保留兼容层，逐步删除�?
任务�?
1. 定义 typed route�?
```kotlin
sealed interface AppRoute {
    data object Home : AppRoute
    data object Library : AppRoute
    data object Player : AppRoute
    data object Settings : AppRoute
    data class Network(val page: NetworkPage) : AppRoute
    data class Collection(val id: Long?) : AppRoute
}
```

2. `MainRoutes.java` 字符串只作为旧桥�?3. `MainRouteController` 迁成 Kotlin，并只负�?route reducer�?4. `EchoNavGraph` 根据 `NavigationState` 渲染�?
验收�?
- 返回键逻辑单测覆盖�?- tab 切换、设置子页、网络子页、播放沉浸页不依赖业�?ViewModel 副作用�?
---

## 7. 播放模块迁移清单

### 7.1 当前问题

播放状态和命令分散�?
- `EchoPlaybackService`
- `MainPlaybackStore`
- `PlaybackViewModel`
- `NowPlayingViewModel`
- `QueueViewModel`
- `NowPlayingStateController`
- `PlaybackStateUpdateController`
- `PlaybackStateUpdateController` is now a stateless `object`; `MainActivityBase`
  no longer keeps or constructs it, and `PlaybackStateEventController` calls the
  policy directly.
- `PlaybackActionController`
- `PlaybackStartController`
- Activity 手动 publish/render

### 7.2 目标

建立播放边界�?
```kotlin
interface PlaybackController {
    val state: StateFlow<PlaybackStateSnapshot>
    val queue: StateFlow<List<Track>>
    suspend fun playQueue(tracks: List<Track>, index: Int)
    suspend fun toggle()
    suspend fun next()
    suspend fun previous()
    suspend fun seekTo(positionMs: Long)
    suspend fun setShuffle(enabled: Boolean)
    suspend fun setRepeatMode(mode: Int)
    suspend fun moveQueueItem(from: Int, to: Int)
    suspend fun removeQueueItem(trackId: Long)
}
```

Service 是实现细节：

```text
EchoPlaybackService
  -> PlaybackServiceController : PlaybackController
```

### 7.3 具体任务

#### 7.3.1 PlaybackController facade（已退役）

1. 2026-06-28：`app.yukine.playback.PlaybackController`、`PlaybackServiceController`、`FakePlaybackController` 已删除。
2. 当前生产树继续使用根包 `app.yukine.PlaybackController` 及其现行调用链。
3. 旧的 7.3.1 只保留为历史记录，不再作为当前迁移目标。
验收：
- UI/ViewModel 不再引用已退役的 playback facade。
- 播放路径继续以当前 service/adapter 边界为准。
#### 7.3.2 统一播放 UiState

目标�?
```kotlin
data class PlaybackUiState(
    val currentTrack: Track?,
    val queue: List<Track>,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val lyricsLine: String,
    val waveform: WaveformState,
    val spectrum: SpectrumState
)
```

任务�?
- `NowBar`、`NowPlayingScreen`、`QueueScreen` 使用同一 `PlaybackUiState` 派生 state�?- 删除 `publishNowPlayingState` 这种 Activity 中转�?- `QueueViewModel.bind(...)` 不再每次�?Activity 手动�?state�?
验收�?
- NowBar、播放页、队列页显示一致�?- 播放进度和当前曲不依赖手�?render�?
现状与阻塞（2026-06-25 分析，详见 `docs/TASK_3_UNIFIED_PLAYBACK_STATE_ANALYSIS.md`）：
- `NowPlayingViewModel` / `QueueViewModel` 已各自持有独立 `StateFlow<UiState>`，且已以 `PlaybackStateSnapshot` 作为数据传输对象，未直接耦合 service。
- 仍为"推送式"而非"响应式"：`MainActivity` 中有约 9 处手动 `nowPlayingStateController.renderNowBar()` 调用，状态变化经 `NowPlayingStateController.publish(snapshot)` 手动推送到 ViewModel，而非 ViewModel 自动 `combine` 多个状态流。
- 阻塞点：要做成响应式（删除手动 `renderNowBar()`），需先把 7.3.1 的 `PlaybackController` 真正集成进 `MainActivity` 并由 ViewModel 注入 `combine` 播放/收藏/歌词/语言流。该集成是独立大任务，未完成前 7.3.2 不宜开工。
- 因此本节标记为"待 7.3.1 集成完成后启动"，避免半截改动破坏播放链路。

#### 7.3.3 保留服务稳定性约�?
迁移时禁止改动：

- MediaLibraryService 对外能力�?- 前台服务通知�?- 小部�?action�?- 队列恢复�?- 耳机/车机控制�?- 歌词通知�?- 音效 session 绑定�?
测试�?
- 编译�?- 播放本地文件�?- 后台播放�?- 通知栏控制�?- 小部件控制�?- 杀进程恢复�?
---

## 8. 曲库与歌单迁移清�?
### 8.1 当前问题

曲库数据、路由和 UI action 混合�?
- `MainLibraryStore` 保存可见曲目、收藏、选中歌单等�?- `LibraryViewModel` 已存在，但仍�?Activity �?`publishLibraryState`、`handleLibraryEvent`�?- `TrackListRenderController`、`LibraryGroupsRenderController`、`LibraryPlaylistsRenderController` 输出 chrome，再�?Activity 塞入 nav state�?
### 8.2 目标

```text
LibraryScreen
  -> LibraryViewModel.uiState
  -> LibraryViewModel.onAction
  -> LibraryUseCases
  -> LibraryRepository
```

歌单独立�?
```text
PlaylistListScreen / PlaylistTrackScreen
  -> PlaylistViewModel
  -> PlaylistUseCases
```

### 8.3 具体任务

#### 8.3.1 替换 MainLibraryStore

1. 定义 `LibraryState`�?
```kotlin
data class LibraryState(
    val allTracks: List<Track>,
    val visibleTracks: List<Track>,
    val favorites: Set<Long>,
    val mode: LibraryMode,
    val selectedGroup: LibraryGroup?,
    val selectedPlaylistId: Long,
    val searchQuery: String
)
```

2. `LibraryViewModel` 持有 `MutableStateFlow<LibraryState>`�?3. `MainLibraryStore` 只作为兼容读取，逐步删除�?4. `replaceLibrary`、`loadCollections`、`refreshLibraryAfterStreamingImport` 迁入 ViewModel/use case�?
验收：切换曲库模式不需要 `routeController.setLibraryMode` 后再 `renderSelectedTab`；收藏状态更新自动刷新列表和 NowBar。

迁移进展（2026-06-25）：
- ✅ `MainLibraryStore` 写接口已收敛：`setFavorite` / `toggleFavorite` / `clearPlayHistory` 等写方法移出，`MainLibraryStore` 退化为只读兼容 facade，所有读取都从 `MainActivityViewModel.library` 这一份 `MutableStateFlow<LibraryStoreState>` 派生。
- ✅ 收藏写路径改走 `MainActivityViewModel.setFavorite(...)`，播放历史清空改走 `MainActivityViewModel.clearPlayHistory()`，`PlayHistoryActionController` 通过 `PlayHistoryStateStore { viewModel.clearPlayHistory() }` 单向同步，不再回写 store。
- ✅ 契约测试 `MainActivityArchitectureContractTest` 已更新断言为 `libraryStateStore.clearPlayHistory()`，`PlayHistoryActionControllerTest` 已对齐到 `libraryStateStore` 装配。
- 🔲 仍待迁移：`replaceLibrary` / `loadCollections` / `refreshLibraryAfterStreamingImport` 仍经 store 转发到 ViewModel；完整 `LibraryState`（mode / selectedGroup / searchQuery 等）尚未独立成型，仍由 `LibraryStoreState` + 外部 store 共担。

#### 8.3.2 曲库 action typed �?
当前 action 是多�?`Runnable` / actions list�?
目标�?
```kotlin
sealed interface LibraryAction {
    data object Scan : LibraryAction
    data class Search(val query: String) : LibraryAction
    data class SelectMode(val mode: LibraryMode) : LibraryAction
    data class SelectGroup(val group: LibraryGroup) : LibraryAction
    data class ToggleFavorite(val track: Track) : LibraryAction
    data class Play(val tracks: List<Track>, val index: Int) : LibraryAction
    data class AddToPlaylist(val track: Track) : LibraryAction
}
```

验收�?
- `TrackListScreen` 不接�?`List<TrackRowActions>`，而是 `onAction(LibraryAction)`�?
#### 8.3.3 歌单独立 ViewModel

新增 `PlaylistViewModel`，负责：

- 歌单列表�?- 收藏歌单虚拟入口�?- 播放历史�?- 最�?最多播放�?- 歌单详情�?- 添加、移除、移动、重命名、删除�?- M3U 导入导出请求�?
验收�?
- 歌单 dialog 结果直接回到 `PlaylistViewModel.onAction`�?- Activity 不再处理 `createPlaylist`、`renamePlaylist`、`deletePlaylist`、`moveSelectedPlaylistTrack`�?
迁移进展（2026-06-25）：
- ? ?? CRUD ?"? Activity ?"????`MainActivity` ???? `createPlaylist` / `renamePlaylist` / `deletePlaylist` / `addTrackToPlaylist` / `moveSelectedPlaylistTrack` / `removeSelectedPlaylistTrack` ?????`PlaylistDialogController` ?????????
- ? CRUD ?????? `LibraryViewModel`?`createPlaylist/renamePlaylist/deletePlaylist/addTrackToPlaylist/moveSelectedPlaylistTrack` + presentation ????`MainActivity` ??????????????????? collections reload?`PlaylistDialogController` ??????
- ?? ?????????????**??** `LibraryViewModel` ???????? `PlaylistViewModel`????????????????????????????????????
- 判定：本节"Activity 不再处理 CRUD"验收项已满足；"新增独立 PlaylistViewModel"作为长期目标保留。

---

## 9. 流媒体迁移清�?
已有 `docs/STREAMING_VIEWMODEL_SPLIT_PLAN.md`，这里给接手版拆分点�?
### 9.1 当前问题

`StreamingViewModel` 已经从旧全局 ViewModel 中拆出，但仍偏大，职责包含：

- provider 列表和状态�?- 搜索�?- 登录/认证�?- Cookie 导入�?- 歌单导入�?- 播放 URL 解析�?- 推荐�?- 音质选择状态参与�?- 与本地歌�?匹配信息交互�?
### 9.2 目标拆分

建议拆成�?
| �?owner | 职责 |
|---|---|
| `StreamingProviderViewModel` | provider 列表、选择、登录状�?|
| `StreamingSearchViewModel` | 在线搜索、分页、搜索结果、搜索错�?|
| `StreamingAuthViewModel` | 登录、Cookie、本�?auth store、认证回�?|
| `StreamingPlaylistImportViewModel` | 账号歌单、URL/源歌单导入、本地落�?|
| `StreamingPlaybackResolver` | 播放 URL 解析、质量降级、预解析 |
| `StreamingRecommendationViewModel` | 每日推荐、心动推荐、推荐种�?|

### 9.3 具体任务

1. 先拆纯搜索�?   - 输入：query、provider、media type�?   - 输出：`StreamingSearchUiState`�?   - 保留�?`StreamingViewModel` facade 一轮�?
2. 再拆认证�?   - `StreamingAuthLauncher` 仍是平台 owner�?   - ViewModel �?emit `LaunchAuth(provider)` effect�?
3. 再拆歌单导入�?   - 导入确认 dialog 变成 effect�?   - 导入结果落到 `PlaylistRepository`�?
4. 最后拆播放解析�?   - `StreamingPlaybackResolver` 不依�?Activity�?   - 播放入口通过 `PlaybackController.playQueue`�?
验收�?
- 新增 provider 不改 `MainActivity.java`�?- 搜索测试不需要构造完�?`StreamingViewModel`�?- 登录测试不需要播�?controller�?- 播放解析测试不需�?UI�?
---

## 10. 下载模块迁移清单

### 10.1 当前问题

下载已有 `DownloadsViewModel`，但下载触发、音质选择、结�?status 仍有 Activity 参与�?
### 10.2 目标

```text
DownloadAction
  -> DownloadsViewModel
  -> TrackDownloadUseCase
  -> TrackDownloadManager
  -> DownloadUiState / DownloadEffect
```

### 10.3 具体任务

1. 新增 `DownloadRequest`�?   - current track
   - playlist
   - current list
   - with quality

2. `DownloadsViewModel` 负责�?   - 创建下载请求�?   - 暂停/继续/全部暂停/全部继续�?   - 打开下载目录 effect�?   - 下载状态消�?effect�?
3. Activity 只处理：
   - 目录选择平台 effect�?   - Android system permission/platform result�?
4. 下载音质 dialog 迁入 effect�?
验收�?
- `downloadTrackWithQuality`、`downloadTracks`、`showDownloadQualityDialog` 不在 Activity�?- 下载状态可单测�?
迁移进展�?026-06-24）：

- 下载触发、下载音质选择、未解析流媒体下载地址解析与入队刷新已迁入 `DownloadRequestController`�?- 下载音质 `EchoDialog` 渲染已迁�?`DownloadQualityDialogController`；`MainActivity` 只负责装�?chooser / resolver / status sink�?- `TrackDownloadManager` 通过 `TrackDownloadRequestQueue` 暴露下载入队与状态能力，便于 `DownloadRequestControllerTest` 使用 fake 队列覆盖普通单曲、未解析流媒体单曲、歌单批量下载路径�?- 下载目录预设切换与“选择目录”入口已迁入 `DownloadsViewModel` action / `DownloadsEffect.OpenDirectoryPicker`；`DownloadsDestination` 只消�?state/effect，目录能力由 `TrackDownloadDirectoryController` 暴露�?- `MainActivity` 已不再包�?`downloadTrackWithQuality`、`downloadTracks`、`enqueueTrackDownload`、`showDownloadQualityDialog`、`DownloadQualityCallback` 等下载业务方法�?- 歌单 CRUD / 曲目移除 / 曲目移动 / 添加到歌单的结果处理入口已继续收敛到 `MainActivity` ????：`MainActivity` 不再保留 `createPlaylist(...)`、`renamePlaylist(...)`、`deletePlaylist(...)`、`removeSelectedPlaylistTrack(...)`、`moveSelectedPlaylistTrack(...)`、`addTrackToPlaylist(...)` 这组纯业务转发方法；Dialog/Collections 入口直接调用 `MainActivity` ????，由其进�?`LibraryViewModel` 并统一发布状态、选中歌单�?collections reload�?- 歌单 dialog 入口继续下沉�?`PlaylistDialogController`：Library / Queue / Collections / NowPlaying 入口现在直接调用 `showAddToPlaylist(track)`，当前歌单列表由 `PlaylistProvider` 提供，无歌单时默认添加分支也�?dialog controller 回调 `addToDefaultPlaylist(track)`；创建、重命名、删除确认入口也直接进入 `PlaylistDialogController`，`MainActivity` 不再保留 `showCreatePlaylistDialog(...)`、`showRenamePlaylistDialog(...)`、`showAddToPlaylistDialog(...)`、`confirmDeletePlaylist(...)` 这组 dialog wrapper�?- 队列清空确认入口保留�?`QueueActionController.confirmClearQueue()` 做空队列判断，确认弹窗由 `QueueActionBindings` 进入 `ConfirmationDialogController.confirmClearQueue()`，确认后的执行动作通过 `ConfirmationDialogBindings` 调用 `QueueActionController.clearQueue()`；`MainActivity` 不再保留 `confirmClearQueue()` / `clearQueue()` 纯转发方法�?- 播放历史清空结果处理已迁�?`PlayHistoryActionController`：Collections 入口直接打开 `ConfirmationDialogController.confirmClearPlayHistory()`，确认后的执行动作通过 `ConfirmationDialogBindings` 调用 `PlayHistoryActionController.clearPlayHistory()`，由其进�?`LibraryViewModel.clearPlayHistory(...)`、同�?`MainLibraryStore`、发布状态并刷新 collections；`MainActivity` 不再保留 `confirmClearPlayHistory()` / `clearPlayHistory()` 业务 wrapper�?- 网络流媒体编辑和删除确认的薄转发继续移除：TrackList 入口直接调用 `NetworkDialogController.showEditStream(...)` / `ConfirmationDialogController.confirmDeleteTrack(...)`，分组删除直接调�?`ConfirmationDialogController.confirmDeleteTracks(...)`，确认后的删除动作直接进�?`NetworkRequestController.deleteAllStreams/deleteTrack/deleteTracks/deleteRemoteSource`；`MainActivity` 不再保留 `showEditStreamDialog(...)`、`confirmDeleteTrack(...)`、`confirmDeleteTracks(...)`、`deleteAllStreams()`、`deleteTrack(...)`、`deleteTracks(...)`、`deleteRemoteSource(...)` 这组网络业务 wrapper�?- WebDAV 远程源测�?同步已由 `NetworkSourcesEventController` 直接调用 `NetworkRequestController.testRemoteSource(...)` / `syncRemoteSource(sourceId, remoteSourceName)`，源名称�?`NetworkSourcesLibrarySourceBindings` 提供；远程源曲目列表页的同步 action 也直接进�?`NetworkRequestController.syncRemoteSource(sourceId, remoteSourceName(sourceId))`；`MainActivity` 中不再保�?`testRemoteSource(...)` / `syncRemoteSource(...)` wrapper�?
---

## 11. 歌词模块迁移清单

### 11.1 当前问题

`LyricsViewModel` 已存在，但歌�?reload、当前曲监听、通知/悬浮歌词开关仍�?Activity/Service/Settings 多点连接�?
### 11.2 目标

```text
LyricsViewModel
  -> LoadTrackLyricsUseCase
  -> LyricsRepository
  -> LyricsUiState
```

运行时发布：

```text
PlaybackController.currentTrack
  -> LyricsViewModel.load(track)
  -> LyricsPublisher
      -> notification lyrics
      -> floating lyrics
```

### 11.3 具体任务

1. `LyricsReloadController` 合并�?`LyricsViewModel` �?`LoadLyricsForCurrentTrackUseCase`�?2. `SettingsViewModel` 不直接控制歌�?ViewModel，改�?`LyricsSettingsChanged` effect�?3. `FloatingLyricsService` 启停�?`FloatingLyricsRuntimeApplier` 处理�?4. `LiveLyricsNotificationService` 保持平台边界�?
验收�?
- 改歌词偏移只影响 Lyrics state，不触发全页�?render�?- 当前曲变化自动加载歌词�?- 无歌词时通知/悬浮歌词优雅降级�?
迁移进展�?026-06-24）：

- `LyricsReloadController` / `LyricsReloadBindings` 已合并进 `LyricsViewModel.bindReloadGateway(...)` �?`LyricsViewModel.reloadCurrentLyrics(...)`；设置页重新加载歌词现在直接进入歌词 ViewModel，状态提示仍通过绑定�?status sink 输出�?- `SettingsActionController` 保留设置入口和睡眠计时边界，但不再通过 listener 转发歌词 reload；`SettingsActionBindings` 已移�?`lyricsReloader`�?
---

## 12. 推荐模块迁移清单

### 12.1 当前问题

每日推荐已收敛到 `StreamingRecommendationViewModel` + typed `RecommendationAction`；心动推荐、种子选择、播放入口仍有部分兼容边界：

- `HeartbeatRecommendationController`
- `StreamingViewModel`
- `MainActivity` helper 方法
- `playbackStartController`

### 12.2 目标

```text
RecommendationViewModel
  -> RecommendationUseCases
  -> StreamingRepository / LibraryRepository
  -> PlaybackController
```

### 12.3 具体任务

1. 定义�?
```kotlin
sealed interface RecommendationAction {
    data class PlayDaily(val provider: StreamingProviderName) : RecommendationAction
    data class PlayHeartbeat(val provider: StreamingProviderName) : RecommendationAction
}
```

2. 建立 `RecommendationSeedResolver`�?   - 当前播放歌曲�?   - 当前队列�?   - 当前歌单�?   - 收藏/最近播放�?
3. `RecommendationViewModel` 输出�?   - loading�?   - empty status�?   - ready status�?   - playable tracks�?
4. 播放通过 `PlaybackController`，不�?Activity�?
验收�?
- `heartbeatRecommendationSeedRequest` 不在 Activity�?- 推荐失败、空结果、成功播放可单测�?
迁移进展�?026-06-24）：

- 每日推荐已先拆到 `StreamingRecommendationViewModel` + `StreamingDailyRecommendationUseCase`：provider 选择、loading/empty/ready 状态、仓库拉取和 presentation 生成不再�?`DailyRecommendationController` 直接调用 `StreamingViewModel`�?- `DailyRecommendationController` / `DailyRecommendationBindings` 兼容壳已删除；每日推荐入口只�?typed `RecommendationAction.PlayDaily` -> `StreamingRecommendationViewModel.onAction(...)` -> presentation callback，且 `DailyRecommendationPlayer` 已由 NOTE 63 删除�?- `MainActivity` 仅保�?ViewModel 接线与播�?presentation 的平台尾部；provider 刷新后同步给 `StreamingRecommendationViewModel.updateProviders(...)`�?- 已补 `StreamingRecommendationViewModelTest`、`RecommendationActionControllerTest` 和架构契约；�?`DailyRecommendationControllerTest` / `DailyRecommendationBindingsTest` 已随兼容壳删除。下一步继续收敛心动推荐续�?refill，或�?dialog/settings 切片继续减少 `MainActivity` UI/platform 细节�?- 心动推荐种子请求已拆�?`HeartbeatRecommendationSeedResolver` / `HeartbeatRecommendationSeedResolverBindings`：候选队列合并、随机候选、direct seed、miss 诊断不再�?`StreamingViewModel.prepareHeartbeatRecommendationSeedRequest(...)` �?`MainActivity.heartbeatRecommendationSeedRequest(...)` 承担；`MainActivity` 只提�?service/store/viewModel/library context 快照来源�?- 心动推荐 owner 已继续迁�?`StreamingRecommendationViewModel` / `HeartbeatRecommendationPlayer`：`HeartbeatRecommendationController` 不再依赖整颗 `StreamingViewModel`，`resolveHeartbeatRecommendationSeed(...)`、`fetchHeartbeatRecommendations(...)`、refill/loading 状态、playlist/append presentation �?track-match store 绑定均由推荐 VM 持有；推荐播放尾部已收敛�?`PlaybackStartController.playRecommendation(...)` / `playHeartbeatRecommendation(...)` �?presentation 边界，`MainActivity` 不再保留 `playHeartbeatRecommendationTracks(...)` �?`openRecommendationPlaylist(...)` 兼容 helper�?- 推荐入口已整理为 typed `RecommendationAction` 并下沉到 `StreamingRecommendationViewModel.onAction(...)`：首页与流媒体页发�?`PlayDaily(provider)` / `PlayHeartbeat(provider)` action；`MainActivity` 不再持有 `DailyRecommendationController`�?- 推荐播放启动边界已先引入小型 `PlaybackController` facade：`PlaybackStartController` 不再直接持有 `resolveAndPlayStreamingTrack` / `playTrackList` / `applyPlaybackActionResult` 三段 listener 回调，而是通过 `PlaybackController.playTrackList(...)` 进入 `PlaybackStartControllerAdapter`；MainActivity 只装�?adapter �?`StreamingPlaybackController` + `NowPlayingViewModel` + result applier。该 facade 已由 NOTE 47 删除�?- 播放缓存已改为当前播放优先的并发切片调度：当前歌曲首段缓存使用最高优先级并立即启动，当前歌曲后续切片其次，下一首轻量预取最低；切换当前歌曲时会取消�?`CacheWriter`，下一�?URL 预解析使用独立低优先级通道，不再挡住用户刚点击的当前歌曲解析�?- now-playing 状态发布入口再收一层：`MainActivity` 已不再保�?`publishNowPlayingState(...)` 这类�?wrapper，事件回放时直接交给 `NowPlayingStateController.publish(snapshot)`；`NowPlayingStateController` 继续作为 UI state/floating lyrics 的事实源�?
---

## 13. 数据层迁移清�?
### 13.1 当前问题

`MusicLibraryRepository` 承担过多�?
- 曲库�?- 歌单�?- 设置�?- 播放队列�?- 远程源�?- WebDAV�?- 部分播放设置�?
### 13.2 目标 Repository

| Repository | 职责 |
|---|---|
| `SettingsRepository` | 所有设置偏�?|
| `LibraryRepository` | 本地曲库、扫描、搜索、分�?|
| `PlaylistRepository` | 歌单 CRUD、歌单曲目排序、M3U 导入导出 |
| `PlaybackQueueRepository` | 队列持久化、恢�?|
| `RemoteSourceRepository` | WebDAV/远程�?|
| `LyricsRepository` | 本地/在线歌词 |
| `StreamingRepository` | provider/gateway/search/playlists |
| `DownloadRepository` | 下载记录、下载状�?|

### 13.3 具体任务

1. 先只加接口，不搬 DB�?
```kotlin
interface SettingsRepository { ... }
interface PlaylistRepository { ... }
interface LibraryRepository { ... }
```

2. 实现类内部暂时委�?`MusicLibraryRepository`�?3. ViewModel/UseCase 改依赖新接口�?4. 当调用方迁完后，�?`MusicLibraryRepository` 拆薄�?
验收�?
- 设置 feature 不依�?`MusicLibraryRepository`�?- 歌单 feature 不依赖曲库扫描方法�?- 远程�?feature 不依赖设置方法�?
---

## 14. 测试迁移清单

### 14.1 必须新增�?fake

- `FakeSettingsRepository`
- `FakeLibraryRepository`
- `FakePlaylistRepository`
- `FakePlaybackController`
- `FakeStreamingRepository`
- `FakeLyricsRepository`
- `FakePlatformEffectCollector`
- `FakeStatusMessageSink`

### 14.2 每个 feature 必测

每个 ViewModel 至少覆盖�?
- 初始 state�?- loading state�?- 成功 state�?- �?state�?- 错误 state�?- 用户 action�?- 一次�?effect�?- 语言切换或文�?key 映射�?
### 14.3 回归命令

轻量编译�?
```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

单测�?
```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

打包�?
```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

完整检查：

```powershell
.\gradlew.bat :app:check --console=plain
```

### 14.4 关键手动回归

- 首次启动引导�?- 扫描本地音乐�?- 播放本地歌曲�?- 后台播放�?- 通知栏控制�?- 桌面小部件控制�?- 播放队列恢复�?- 歌词加载和偏移�?- 状态栏/悬浮歌词�?- 流媒体登录�?- 在线搜索和播放�?- 歌单导入�?- 下载暂停/继续�?- 设置主题/语言/音效/ReplayGain�?- 备份导入导出�?
---

## 15. 推荐执行顺序

### 15.1 第一批：低风险、收益高

1. �?`BackgroundImagePickerController`�?2. �?`BackupRestoreLauncher`�?3. �?`StatusMessageViewModel` 或统一 status effect�?4. 设置�?typed `SettingsPage`�?5. `SettingsPageStateBuilder` 替代 render controller 的纯 mapper�?
预期结果�?
- 新设置不再加�?Activity�?- 设置迁移成为后续 feature 样板�?
### 15.2 第二批：去掉 Activity 手动同步

1. `SettingsViewModel` 直接拥有设置 page state�?2. 删除 `SettingsRenderCoordinator`�?3. 删除大部�?`SettingsGatewayBindings`�?4. `EchoNavHostState` 不再保存 settings actions�?
预期结果�?
- 设置 feature 完整 MVVM�?
### 15.3 第三批：播放状态单源化

1. 引入 `PlaybackController`�?2. `PlaybackViewModel` 收敛播放 state�?3. NowBar/NowPlaying/Queue 使用同一 state�?4. Activity 不再 publish now playing state�?
预期结果�?
- 播放 UI 行为更稳定，测试更好写�?
### 15.4 第四批：曲库/歌单

1. `LibraryViewModel` 接管 `MainLibraryStore`�?2. `PlaylistViewModel` 接管歌单 CRUD/dialog result�?3. 删除 `navTrackList*`、`navLibraryGroup*`、`navCollectionsActions`�?
预期结果�?
- 曲库新增功能不碰 Activity�?
### 15.5 第五批：流媒体拆�?
1. 搜索拆出�?2. 认证拆出�?3. 歌单导入拆出�?4. 播放解析拆出�?5. 推荐拆出�?
预期结果�?
- `StreamingViewModel` 不再是第二个上帝 ViewModel�?
### 15.6 第六批：导航独立

2026-06-29 correction: this old navigation batch is not the current next
step. `AppShellViewModel` was removed after review because it would create a
parallel shell model. Keep the current route/nav owners until a future slice
can make typed shell state the single runtime source and update the
architecture contract in the same slice.

1. `AppShellViewModel`�?2. typed `AppRoute`�?3. 删除 `MainRouteController` 或降级为兼容 adapter�?4. 删除 `EchoNavHostState` 大状态包�?
预期结果�?
- Compose shell 真正�?ViewModel/Navigation state 驱动�?
---

## 16. 每次接手前检查表

开始前�?
- [ ] `git status --short`，确认他人改动�?- [ ] 读本文件对应章节�?- [ ] 读相�?feature ViewModel/Controller/Bindings�?- [ ] 写出当前事件路径�?- [ ] 写出目标事件路径�?- [ ] 明确状态事实源�?- [ ] 明确哪些旧类保留兼容�?
改动中：

- [ ] 不新增万�?manager/coordinator�?- [ ] 不新增只转发一行的 binding�?- [ ] 不把平台 API 放进 ViewModel�?- [ ] 不让 Repository 返回 UI 文案�?- [ ] 不让 Activity 新增业务分支�?- [ ] 不扩大播放服务职责�?
提交前：

- [ ] 编译通过�?- [ ] 对应 ViewModel/use case 测试通过�?- [ ] 关键手动路径不变�?- [ ] 删除不再使用的旧转发�?- [ ] 更新架构契约测试�?- [ ] 如果迁移了公共行为，更新本文件或相关 handoff�?
---

## 17. 完成定义

这轮 MVVM 迁移完成时应满足�?
- `MainActivity` 只负责宿主、launcher 注册、生命周期、Compose root、service bind�?- 业务事件不再回流�?Activity 编排�?- `EchoNavHostState` 被删除或变成极薄 app shell state�?- `MainSettingsStore`、`MainLibraryStore`、`MainPlaybackStore` 删除或只作为兼容 facade�?- 设置、曲库、歌单、播放、队列、歌词、流媒体、下载、推荐都有独�?ViewModel 和测试�?- 播放 service 通过 `PlaybackController` 暴露能力�?- 新增一个普通功能不需要修�?`MainActivity.java`�?- 新增一个设置不需要修改超�?3 个核心文件�?- 没有新的上帝类、上帝进程、万能协调器�?
2026-06-24 note: streaming search chrome state now lives in StreamingViewModel and is consumed by NetworkDestination directly; EchoNavHostState no longer mirrors streamingSearchLabels / streamingSearchActions.
2026-06-25 note: EchoNavHostState no longer mirrors nowPlayingGesturesEnabled / pageBackgrounds; EchoNavGraph reads both directly from SettingsViewModel.state.preferences and MainActivity no longer syncs them manually.
2026-06-25 note: openSearchAction was removed from EchoNavHostState; EchoNavGraph now derives the local search navigation callback itself and passes it to the library destinations.
2026-06-25 note: openDownloadDirectoryPickerAction / closeNowPlayingAction / nowPlayingEventHandler were also moved out of EchoNavHostState; EchoNavGraph/EchoNavHostBridge now receive those shell callbacks directly from EchoAppHost/MainActivity.
2026-06-25 note: openLibraryModeFromHome/openCollectionsFromHome/openSearchFromHome/openFavoritesCollectionFromLibrary thin wrappers were collapsed into the render bindings, keeping MainActivity as a direct callback host instead of a forwarding layer.
2026-06-25 note: openLibraryModeFromHome/openCollectionsFromHome/openSearchFromHome/openFavoritesCollectionFromLibrary thin wrappers were removed from MainActivity; call sites now enter route/navigation or library event paths directly.
2026-06-25 note: syncRouteFieldsFromViewModel was removed; MainRouteController already restores its initial state, so MainActivity no longer keeps a one-off route restoration wrapper.
2026-06-25 note: selectPlaylistFromCollections was removed; the collections binding now writes the selected playlist and reloads collections inline instead of going through a one-off MainActivity helper.
2026-06-25 note: openSelectedPlaylistExportDocument was removed; the collections binding now called PlaylistExportController directly with the selected playlist snapshot. This was later simplified by NOTE 64.
2026-06-25 note: finishOnboarding/openStreamingFromOnboarding now share completeOnboarding as the common shell tail, reducing repeated onboarding cleanup in MainActivity.
2026-06-25 note: searchViewModel was removed from EchoNavHostState; EchoAppHost now provides it as a direct NavHost bridge parameter, keeping the shell state focused on shared navigation state only.
2026-06-25 note: openNowPlayingImmersive was moved out of EchoNavHostState and now lives as local state inside EchoNavGraph, since it only serves the current shell session's immersive handoff.



2026-06-25 note: persistRouteFields was removed; MainActivity now calls MainRouteController.persist() directly at render/navigation persistence boundaries instead of keeping a forwarding-only helper.
2026-06-25 goal alignment note: the project audit report from C:\Users\31283\.codex\attachments\18684c6a-7b8d-4bc7-bd9b-430f25ee48f6\pasted-text.txt is now part of the migration target interpretation.
2026-06-25 goal alignment note: treat runtime stability, startup/playback smoothness, streaming/cache correctness, and shell-to-ViewModel ownership cleanup as higher priority than headline completion percentages.
2026-06-25 note: dead recommendation-stream code removed. `MainLibraryStore` lost its only mutable state fields (`recommendationStreamTitle` / `recommendationStreamTracks`) and the 5 methods around them (`showRecommendationStreamList` / `clearRecommendationStreamList` / `hasRecommendationStreamList` / `recommendationStreamTitle()` / `recommendationStreamTracks()`); the sole writer `showRecommendationStreamList` had zero call sites (the populate path was dropped when daily recommendations moved to StreamingRecommendationViewModel), so `hasRecommendationStreamList()` was always false and the NetworkRenderCoordinator recommendation branch + `NetworkTrackListRenderController.renderRecommendationStreamList` were dead. Removing them also retired the now-unused `Listener.playTrackList` interface method, the `NetworkTrackListRenderBindings.playTrackListAction` param, and two no-op `clearRecommendationStreamList()` calls in MainActivity (navigateNetworkPage / navigateToNetworkTabPage simplified). `MainLibraryStore` is now a pure read-only facade over MainActivityViewModel state (only `combinedSearchUseCase` remains, a stateless val), matching the 8.3.1 target. Verified: compile + full :app:testDebugUnitTest green.
2026-06-25 goal alignment note: use the audit as a prioritization input, not as a source of truth for exact percentages; validate each claimed milestone against current code, tests, and runtime behavior before marking it complete.
2026-06-25 note: LibraryTrackListDestination and LibraryGroupsDestination no longer carry legacy host-injected chrome parameters; both now render directly from LibraryViewModel StateFlow snapshots, keeping the Compose library boundary aligned with ViewModel-owned state.
2026-06-25 note: MainActivity no longer funnels library UI events through handleLibraryEvent/publishLibraryState; library render/queue/collections/now-playing call a narrower dispatchLibraryEvent path, while syncLibraryViewModelState is kept only as the explicit route+library snapshot sync boundary and favorites now read live IDs through LibraryFavoriteIdsProvider.
2026-06-25 note: dispatchLibraryEvent has now been removed as well; library render/queue/collections/now-playing event sinks call LibraryViewModel.onEvent directly, while syncLibraryViewModelState remains the only explicit MainActivity-to-LibraryViewModel snapshot sync boundary.
2026-06-25 note: syncLibraryViewModelState and LibraryViewModel.uiState/updateState have now been removed; production library rendering relies on LibraryViewModel track/group StateFlows plus direct event sinks, while favorite toggles read live IDs from MainLibraryStore through LibraryFavoriteIdsProvider instead of maintaining a separate route/library summary mirror.
2026-06-25 note: PlaybackController facade landed (PlaybackController.kt + PlaybackServiceController.kt) with a FakePlaybackController test double and FakePlaybackControllerTest; Activity/ViewModel integration is deferred to a follow-up so the existing service path stays untouched until 7.3.2 starts. See docs/MVVM_MIGRATION_PROGRESS_2026-06-25.md.
2026-06-25 note: MainLibraryStore write接口 (setFavorite/toggleFavorite/clearPlayHistory) removed; the store is now a read-only compatibility facade over MainActivityViewModel.library, with favorite writes going through MainActivityViewModel.setFavorite and play-history clears through MainActivityViewModel.clearPlayHistory via PlayHistoryStateStore.
2026-06-25 note: fixed two stale test seams left by the write-path move — PlayHistoryActionControllerTest now wires libraryStateStore = PlayHistoryStateStore { viewModel.clearPlayHistory() }, and MainActivityArchitectureContractTest asserts libraryStateStore.clearPlayHistory(). Full :app:testDebugUnitTest --rerun-tasks is green (690 tests).
2026-06-25 note: observed two flaky tests on the first full-suite pass (LyricsViewModelTest.loadPublishesLoadedLyricsState, StreamingViewModelTest.preResolveStreamingQueueWindowResolvesUpcomingTracksAfterNextTrack); both pass in isolation and on clean rerun, tracked as a separate follow-up (coroutine dispatcher timing / shared-state sensitivity), not caused by this session's changes.
2026-06-25 verification note: compileDebugKotlin+Java BUILD SUCCESSFUL; :app:testDebugUnitTest --rerun-tasks BUILD SUCCESSFUL (690 tests) on the current working tree, re-verified per the handoff requirement rather than relying on earlier reports.
2026-06-26 note: BackgroundImagePickerController was refined into a clearer two-stage platform owner. The preview screen now always uses the original picked image for zoom/pan fidelity, while persistence still happens only after Apply via an app-private compressed copy plus BackgroundTransform. This keeps the Activity free of picker/preview/save orchestration detail, preserves restart-safe backgrounds, and avoids regressing the user-visible preview quality. 2026-06-28 update: `BackgroundLanguageModeProvider` and `BackgroundTransformProvider` were removed; the controller now receives direct `() -> String` and `(String) -> BackgroundTransform` dependencies for preview language and transform lookup.
2026-06-26 note: DocumentPickerController now owns its Activity Result launcher instead of using request codes through MainActivity.onActivityResult. Audio import, folder import, download directory, M3U import/export, playlist M3U import, and Luoxue source import still share the same focused picker owner, while MainActivity no longer overrides onActivityResult for document picking. DocumentPickerControllerTest covers launch intents, selected URI dispatch, export URI dispatch, and canceled results.
2026-06-26 note: Track sharing now has a platform owner in TrackShareLauncher. NowPlaying effects call trackShareLauncher.share(track), while chooser creation, native QQ/WeChat music-card attempts, share-style lookup, and share failure/status mapping are no longer implemented in MainActivity. 2026-06-28 update: `TrackShareLanguageProvider` and `TrackShareStyleProvider` were removed; `TrackShareLauncher` now receives direct `() -> String` dependencies for language and share style. TrackShareLauncherTest covers null track feedback, missing share service, native platform-card success, chooser launch, and startActivity failure fallback.
2026-06-26 note: MainPermissionController now owns permission requests through ActivityResultContracts.RequestMultiplePermissions. AppPermissions only computes missing audio/notification permissions, and MainActivity handles the load-library/onboarding remount result tail inline without a separate permission-result adapter. MainPermissionControllerTest covers launch/no-launch behavior and callback dispatch.
2026-06-27 note: CollectionsReloader was removed as a one-hop shim. PlayHistoryActionController now takes a direct reload Runnable, so play-history clearing flows viewModel -> store -> reload without a dedicated forwarding owner.
2026-06-27 note: StreamingAuthCallbackBindings was removed. StreamingAuthCallbackController now owns URI parsing, manual-cookie routing, and auth completion directly from MainActivity wiring, so streaming auth callback handling no longer passes through a forwarding-only adapter.
2026-06-26 note: BackupRestoreBindings was removed as a forwarding-only adapter. 2026-06-28 update: BackupRestoreLauncher now accepts a direct `(String) -> Unit` status-key dependency, so backup result status keys go from the launcher to StatusMessageController without an extra binding or sink wrapper.
2026-06-26 note: DialogLanguageProviderBindings was removed. NetworkDialogController, PlaylistDialogController, and ConfirmationDialogController now share the same DialogLanguageProvider contract, so MainActivity can pass one language lambda directly instead of adapting it through a binding class.
2026-06-26 note: NetworkMenuChromeBindings was removed. Superseded on 2026-06-28 by NOTE 59: NetworkMenuContentSink is also gone, and NetworkMenuEventController updates NetworkMenuViewModel directly instead of routing network menu chrome state through a forwarding binding or sink.
2026-06-26 note: StreamingSearchChromeBindings was removed. StreamingSearchEventController now accepts a narrow StreamingSearchContentSink, and MainActivity wires StreamingViewModel.updateStreamingSearchChrome directly instead of routing streaming search chrome state through a forwarding binding.
2026-06-26 note: MainTabRendererBindings was removed. MainTabRenderDispatcher now owns the tab-to-render-callback mapping directly through Runnable callbacks, so MainActivity no longer constructs a forwarding renderer adapter for selected-tab rendering.
2026-06-26 note: StatusMessageHostBindings was removed. StatusMessageController now accepts a direct language-mode supplier and RawStatusUpdater, while the remaining raw status callback contract lives in StatusMessageContracts.kt for feature paths that still need it.
2026-06-26 note: StreamingGatewayHostBindings was removed. StreamingGatewayEventController now accepts its language provider, selected-tab render action, and status sink directly, so gateway endpoint apply/refresh no longer route through a forwarding host adapter.
2026-06-26 note: NetworkMenuPlayerBindings and NetworkSourcesPlayerBindings were removed. NetworkMenuEventController and NetworkSourcesEventController now receive the shared TrackListPlaybackAction directly, so network playback no longer routes through tiny rename-only adapters.
2026-06-26 note: NetworkMenuDialogBindings was removed. NetworkMenuEventController now receives the add stream, import M3U, and add WebDAV dialog launchers directly as Runnable callbacks from MainActivity, so the network dialog path no longer depends on a forwarding adapter.
2026-06-26 note: NetworkLibrarySourceBindings was removed. NetworkMenuEventController now reads stream tracks, stream counts, WebDAV tracks, and remote sources directly from MainLibraryStore method references, while NetworkSourcesEventController now reads remote source names and source tracks directly from MainLibraryStore method references.
2026-06-26 note: NetworkSourcesRenderBindings was removed. MainActivity now passes NetworkSourcesEventController directly into NetworkSourcesRenderController, so the network sources render path no longer needs an extra one-hop listener wrapper.
2026-06-26 note: PlaybackServiceHostBindings was removed. MainActivity now builds PlaybackServiceHostController with an anonymous Host implementation inline, so playback service attachment, store reset, and post-connect render hooks stay owned by the activity boundary without a forwarding binding.
2026-06-29 note: PlaybackServiceHostController remains the service connect/disconnect policy owner, but MainActivityBase no longer retains it as a field; it is constructed locally and passed directly into PlaybackServiceConnectionController. Later 2026-06-29 update: the anonymous `PlaybackServiceHostController.Host` block moved into `MainPlaybackServiceHost`, provided by `PlaybackUiModule`; MainActivityBase only supplies service/store/render lambdas.
2026-06-29 note: Network render controllers remain real render owners under NetworkRenderCoordinator, but MainActivityBase no longer keeps NetworkMenuRenderController, NetworkTrackListRenderController, NetworkSourcesRenderController, or StreamingSearchRenderController as fields.
2026-06-26 note: PermissionResultBindings was removed. 2026-06-29 update: permission result loading no longer lives inline in an anonymous `MainPermissionController.Listener`; `MainPermissionListener` now owns the permission success path and is provided by `PlatformModule`.
2026-06-29 note: Playback state event listener policy moved out of the Java base anonymous `PlaybackStateEventController.Listener` block into `MainPlaybackStateEventListener`. `MainActivityBase` now injects `MainPlaybackStateEventListenerFactory`, passes a lambda `ServiceQueueSource`, and leaves playback snapshot handling in `PlaybackStateEventController`.
2026-06-29 note: Track-list render listener policy moved out of the Java base anonymous `TrackListRenderController.Listener` block into `MainTrackListRenderListener`, provided by `LibraryModule`. Track-list play/favorite/playlist/download/edit/delete actions and chrome-state copying are now covered by `MainTrackListRenderListenerTest`.
2026-06-29 note: Queue render listener policy moved out of the Java base anonymous `QueueRenderController.Listener` block into `MainQueueRenderListener`, provided by `PlaybackUiModule`. Queue playback/favorite/add/remove/clear/back delegates are now covered by `MainQueueRenderListenerTest`, and the legacy no-op queue chrome callback is preserved in the Kotlin owner.
2026-06-29 note: Recommendation action callback policy moved out of the Java base anonymous `RecommendationActionCallbacks` block into `MainRecommendationActionCallbacks`, provided by `StreamingModule`. Daily recommendation playback, heartbeat seed lookup, heartbeat playback, seed-miss logging, and status routing now have focused `MainRecommendationActionCallbacksTest` coverage.
2026-06-29 note: Home dashboard render listener policy moved out of the Java base anonymous `HomeDashboardRenderController.Listener` block into `MainHomeDashboardRenderListener`, provided by `ShellModule`. Home dashboard navigation, playback continuation, recent-track play, shuffle-all queue creation, recommendation triggers, and action publication are now covered by `MainHomeDashboardRenderListenerTest`.
2026-06-29 note: Now-playing state listener policy moved out of the Java base anonymous `NowPlayingStateController.Listener` block into `MainNowPlayingStateListener`, provided by `PlaybackUiModule`. Store readiness, playback snapshot, favorite ids, lyrics state, language mode, floating-lyrics publication, active lyric selection, and queue input sync are now covered by `MainNowPlayingStateListenerTest`.
2026-06-26 note: LibrarySmallGatewayBindings was removed. MainActivity now binds favorite writing and playlist-track loading directly with lambdas, so the library toggle/load paths no longer need a two-method forwarding wrapper.
2026-06-26 note: NowPlayingStateBindings was removed. Superseded on 2026-06-29: MainActivity no longer wires NowPlayingStateController with an anonymous Listener inline; now-playing state publication inputs, floating-lyrics sync, and active lyric selection now go through `MainNowPlayingStateListener`.
2026-06-26 note: NetworkRequestBindings was removed. MainActivity now supplies NetworkRequestController labels and status callbacks directly with lambdas, so request status localization no longer needs a dedicated forwarding adapter.
2026-06-27 note: NetworkActionsResultBindings was removed. MainActivity now wires NetworkActionsViewModel.Listener inline, so network stream import/update/delete, WebDAV save/sync/test, playback track retention, network page navigation, status updates, and collections reload all stay at the host boundary without a forwarding result adapter.
2026-06-27 note: HomeDashboardRenderBindings was removed. Superseded on 2026-06-29: MainActivity no longer wires HomeDashboardRenderController.Listener inline; home mode switching, continue playback, now-playing refresh, per-track playback, library refresh, queue navigation, shuffle-all, streaming navigation, collections open, search refresh, and recommendation actions now go through `MainHomeDashboardRenderListener`.
2026-06-26 note: PlaylistExportBindings was removed. MainActivity wired PlaylistExportController with an anonymous Listener inline at that point; NOTE 64 later folded that pending export state into DocumentPickerController and removed the controller.
2026-06-26 note: BackNavigationBindings was removed. MainActivity now installs its OnBackPressedCallback inline, so the back-handler shell logic stays at the activity boundary without a forwarding adapter.
2026-06-26 note: SettingsControlsBindings was removed. MainActivity now supplies SettingsPlaybackServiceControls and SettingsLyricsControls inline via anonymous objects, so settings runtime setters stay at the shell boundary without a forwarding adapter.
2026-06-26 note: NowPlayingEffectBindings was removed. Superseded on 2026-06-28 by NOTE 48: the temporary `NowPlayingEffectController` shell dispatcher is also gone, and now-playing effects are drained directly in `MainActivity.handleNowPlayingEffects()` to call the existing platform owners.
2026-06-26 note: DocumentPickerBindings was removed. DocumentPickerController now receives a direct Listener from MainActivity, so audio import, folder import, download folder selection, playlist/M3U import-export, and Luoxue source import no longer pass through a one-hop binding.
2026-06-26 note: BackgroundImagePickerBindings was removed. BackgroundImagePickerController now reports picked page backgrounds directly to SettingsViewModel.applyPageBackgrounds and StatusMessageController, keeping preview/save ownership in the picker controller without a forwarding adapter.
2026-06-26 note: SettingsEffectBindings was removed. MainActivity now binds SettingsViewModel effects with an inline effect dispatcher, so settings navigation, platform launchers, lyrics reload, sleep timer, backup/restore, page background, and streaming gateway endpoint effects are explicit shell-boundary calls instead of a separate forwarding class.
2026-06-27 note: PlaybackQueueManager, PlaybackRuntimeStateManager, PlaybackMediaLibraryCallback, and PlaybackStatePublisher were introduced as playback boundary owners. Queue load/restore/reorder/advance now live in PlaybackQueueManager; playback mode/speed/volume/ReplayGain/audio-focus application now live in PlaybackRuntimeStateManager; MediaItem/Track resolution now lives in PlaybackMediaLibraryCallback; lyric/notification/widget publication and notification triggering now live in PlaybackStatePublisher; EchoPlaybackService shrank to 2740 lines in the current tree. Verified with serial `:app:compileDebugKotlin :app:compileDebugJavaWithJavac` and focused `PlaybackQueueManagerTest` / `PlaybackRuntimeStateManagerTest` / `PlaybackStatePublisherTest`.
2026-06-26 note: PlaylistDialogBindings was removed. PlaylistDialogController now receives a direct Listener that calls `MainActivity` ???? for create, rename, delete, default-add, and add-to-playlist results, while playlist selection data still comes from the PlaylistProvider.
2026-06-26 note: StreamingManualCookieBindings was removed. StreamingManualCookieController now receives a direct Listener for selected provider, dialog display, login-success refresh, and status updates, so the manual cookie auth path no longer depends on a forwarding binding.
2026-06-26 note: PlaybackActionBindings and PlaybackActionResultBindings were removed. MainActivity now wires PlaybackActionController with an inline anonymous Listener implementation, and playback result publication is handled directly inside MainActivity, so playback decision inputs and result commits stay at the shell boundary without an extra forwarding layer.
2026-06-26 note: StreamingSearchNavigatorBindings was removed. MainActivity now wires StreamingSearchEventController.Navigator inline, so network-home navigation, streaming playlist import, account playlist sync, liked-track import, recommendation actions, and import/cookie dialogs no longer pass through a one-hop navigator binding.
2026-06-26 note: StreamingSearchEventController now receives its Navigator inline from MainActivity. Superseded on 2026-06-28 by NOTE 50: recommendation actions no longer flow through `RecommendationActionController`; `MainActivity` calls `StreamingRecommendationViewModel.onAction(...)` directly with the shared callbacks.
2026-06-26 note: StreamingActionGatewayBindings was removed. MainActivity now wires MainActivityStreamingActionGateway inline, so streaming auth launch, playback quality, resolved-track playback, login-success handoff, and manual cookie import stay on the activity shell boundary without a forwarding adapter.
2026-06-26 note: MainActivityStreamingActionGateway is now provided inline by MainActivity, so the streaming auth and playback bridge no longer depends on a standalone gateway binding class.
2026-06-26 note: NowPlayingGatewayBindings, QueueActionBindings, LibraryGatewayBindings, and LibraryPlaylistActionGatewayBindings were removed. MainActivity now wires NowPlayingGateway, QueueActionController.Listener, LibraryGateway, and LibraryPlaylistActionGateway inline, so now-playing, queue, library, and playlist-action shells no longer depend on thin forwarding adapters.
2026-06-26 note: PlaybackServiceQueueSourceBindings was removed. MainActivity now provides PlaybackStateEventController.ServiceQueueSource inline, so playback state publishing no longer depends on a one-method queue-source adapter.
2026-06-26 note: RecommendationActionBindings was removed. Superseded on 2026-06-29: MainActivity no longer provides RecommendationActionCallbacks inline; daily recommendations, heartbeat recommendations, seed lookup, seed-miss logging, and status routing now go through `MainRecommendationActionCallbacks`.
2026-06-26 note: LibraryCollectionGatewayBindings was removed. MainActivity now provides LibraryCollectionGateway inline, so collection loading, play-history clearing, and favorite writes no longer depend on a separate forwarding adapter.
2026-06-26 note: LibraryImportGatewayBindings was removed. MainActivity now provides LibraryImportGateway inline, so cached refresh, audio tree import, URI import, and missing-spec parsing no longer depend on a separate forwarding adapter.
2026-06-26 note: StreamingPlaybackBindings was removed. MainActivity now provides StreamingPlaybackController.Listener inline, so streaming playback quality, queue snapshots, heartbeat append checks, playback result application, and status routing no longer pass through a pure forwarding binding.
2026-06-26 note: HeartbeatRecommendationBindings was removed. HeartbeatSeedRequestProvider now lives with HeartbeatRecommendationSeedResolver, and MainActivity provides HeartbeatRecommendationController.Listener inline, so service availability, queue append/playback, seed-miss logging, and status routing no longer pass through a separate heartbeat forwarding binding.
2026-06-26 note: PlaybackStateEventBindings was removed. MainActivity now provides PlaybackStateEventController.Listener inline, while the shared SettingsSelectedTabProvider contract moved to SettingsControls.kt for LyricsStateRefreshListener. Playback state updates now flow from PlaybackStateEventController directly to the shell listener without a forwarding binding layer.
2026-06-26 note: NetworkTrackListRenderBindings was removed. NetworkTrackListRequest lives beside NetworkTrackListRenderController, and MainActivity provides the render listener inline, so network stream/WebDAV track-list actions no longer pass through a one-hop binding while the request model remains owned by the render boundary. Superseded on 2026-06-28 by NOTE 62: the unused NetworkPageAction wrapper was removed.
2026-06-26 note: QueueRenderBindings was removed. TrackListPlaybackAction remains available for shared network playback actions. Superseded on 2026-06-28 by NOTE 62: the unused QueueTrackAction wrapper was removed. Superseded again on 2026-06-29: MainActivity no longer provides QueueRenderController.Listener inline; queue playback, favorite toggles, add-to-playlist, remove, clear, and back actions now go through `MainQueueRenderListener`.
2026-06-26 note: LibraryPlaylistsRenderBindings was removed. MainActivity now provides LibraryPlaylistsRenderController.Listener inline, keeping playlist/favorites/history events, delete confirmation, chrome publication, and playlist track-list rendering connected directly to LibraryViewModel/dialog/render methods without a forwarding binding.
2026-06-26 note: PlaybackStartBindings was removed. Superseded on 2026-06-28 by NOTE 47: `PlaybackController.kt` and `PlaybackStartControllerAdapter` are now gone; `StreamingTrackListResolver` and `PlaybackTrackListPlayer` live beside `PlaybackStartController.kt`, and playback start uses direct function dependencies rather than a pure forwarding facade.
2026-06-26 note: TrackListRenderBindings was removed. TrackListChromeState now lives beside TrackListRenderController, and MainActivity provides TrackListRenderController.Listener inline for track playback, favorite/add/download/edit/delete actions and chrome publication. Superseded on 2026-06-28 by NOTE 61: the unused LibraryEventSink compatibility type was removed.
2026-06-26 note: LibraryGroupsRenderBindings was removed. LibraryGroupsChromeState and LibraryGroupTrackListRequest now live beside LibraryGroupsRenderController, and MainActivity provides LibraryGroupsRenderController.Listener inline for group selection, favorites, playback, delete confirmation, chrome publication, and group track-list rendering.
2026-06-26 note: CollectionsRenderBindings was removed. CollectionsRenderController now owns the remaining collections action wiring directly, while MainActivity provides CollectionsRenderController.Listener inline for playlist creation/import/export, history clearing, track playback, favorite, download, playlist selection, rename/delete, streaming sync, move/remove, and collections action publication.
2026-06-27 note: PlaybackShutdownCoordinator now separates playback-level stop cleanup from service-level destruction. `stopAndClear()` calls `releasePlaybackResources()` for lyrics, Wi-Fi lock, and player only; `onDestroy()` calls `releaseServiceResources()` for receiver, schedulers, notification artwork, precache, Wi-Fi lock, and player. Do not collapse these paths back together; `PlaybackShutdownCoordinatorTest` guards the distinction. Verified with serial `:app:compileDebugKotlin :app:compileDebugJavaWithJavac` and targeted `:app:testDebugUnitTest --tests app.yukine.playback.PlaybackShutdownCoordinatorTest`.2026-06-27 note: PlaybackPositionManager now owns restored playback position, explicit resume position, clamping, throttled save, reset, and stop/clear position cleanup. EchoPlaybackService no longer keeps restored/last-saved position mutable fields or directly calls queueStore().savePlaybackPosition/loadPlaybackPosition; PlaybackQueueManager.playQueue(..., startPositionMs) now sets the in-memory restored position so immediate prepare honors explicit starts. Verified with serial compile and targeted PlaybackPositionManagerTest, PlaybackQueueManagerTest, and playback queue persistence/mutation contract tests.
2026-06-27 note (line break correction): PlaybackPositionManager now owns restored playback position, explicit resume position, clamping, throttled save, reset, and stop/clear position cleanup. EchoPlaybackService no longer keeps restored/last-saved position mutable fields or directly calls queueStore().savePlaybackPosition/loadPlaybackPosition; PlaybackQueueManager.playQueue(..., startPositionMs) now sets the in-memory restored position so immediate prepare honors explicit starts. Verified with serial compile and targeted PlaybackPositionManagerTest, PlaybackQueueManagerTest, and playback queue persistence/mutation contract tests.

2026-06-27 note: PlaybackSleepTimerManager now owns sleep timer end time, remaining-time calculation, callback scheduling, expiry pause, and cancel/publish policy. EchoPlaybackService no longer keeps sleepTimerEndsAtMs or sleepTimerRunnable and only provides handler scheduling plus pause/publish callbacks. Verified with serial compile and targeted PlaybackSleepTimerManagerTest plus playbackSleepTimerStateIsOwnedOutsideEchoPlaybackService contract.

- 2026-06-27: Playback Wi-Fi lock ownership moved into PlaybackWifiLockManager. EchoPlaybackService no longer stores the WifiLock field or local acquire/release helpers; it creates a narrow Android adapter and delegates streaming playback lock policy to the manager. Verified with serial compile and focused PlaybackWifiLockManagerTest plus MainActivityArchitectureContractTest.playbackWifiLockIsOwnedOutsideEchoPlaybackService.
- 2026-06-27: Playback noisy receiver ownership moved into PlaybackNoisyReceiverManager. EchoPlaybackService no longer stores noisyReceiver/noisyReceiverRegistered or local register/unregister helpers; it now delegates lifecycle to the manager through a narrow Android registrar. Verified with serial compile and focused PlaybackNoisyReceiverManagerTest plus MainActivityArchitectureContractTest.playbackNoisyReceiverIsOwnedOutsideEchoPlaybackService.
- 2026-06-27: Playback progress update ownership moved into PlaybackProgressUpdateManager. EchoPlaybackService no longer stores progressRunnable or direct mainHandler progress scheduling; playback events delegate to the manager for one-second publish/persist ticks. Verified with serial compile and focused PlaybackProgressUpdateManagerTest plus MainActivityArchitectureContractTest.playbackProgressUpdatesAreOwnedOutsideEchoPlaybackService.
- 2026-06-27: Playback state broadcast ownership moved into PlaybackStatePublisher. EchoPlaybackService no longer stores a listener set or manual state/buffering fan-out loops; it delegates register/unregister and publish paths to the publisher. Verified with serial compile and focused PlaybackStatePublisherTest plus MainActivityArchitectureContractTest.playbackStateBroadcastsAreOwnedOutsideEchoPlaybackService.
- 2026-06-27: Playback error recovery ownership moved into PlaybackErrorRecoveryManager. EchoPlaybackService no longer carries the retry-one/skip-on-repeat-failure branch or lastErrorTrackId state; it forwards ready/error events to the manager. Verified with serial compile and focused PlaybackErrorRecoveryManagerTest plus MainActivityArchitectureContractTest.playbackErrorRecoveryIsOwnedOutsideEchoPlaybackService.
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

## NOTE 38 - Streaming task queue owner collapse (2026-06-27)
- `StreamingPlaybackTaskScheduler` now implements `StreamingPlaybackTaskQueue` directly; the thin `StreamingPlaybackTaskQueueAdapter` was removed.
- `MainActivity` now binds the scheduler directly, keeping the streaming task path one hop shorter.
- The former adapter test was renamed into `StreamingPlaybackTaskSchedulerTest` so the queue ordering behavior still has coverage.
- Verified serially with `:app:compileDebugKotlin :app:compileDebugJavaWithJavac` and focused scheduler / architecture contract tests.

## NOTE 39 - Playback service boundary cleanup (2026-06-27)
- `EchoPlaybackService` no longer imports `MainActivity`; launch intent creation now uses the package launch intent path.
- `PlaybackQueueManager` owns the queue recovery comparison directly, and the cache owners compare `contentUri` locally instead of routing through the service.
- `PlaybackQueueManagerTest` remains Robolectric-backed with `sdk = [34]` to keep the URI behavior faithful.
- Verified serially with focused playback tests and the architecture contract after the cleanup.

## NOTE 40 - Playback start controller dependency shortcut (2026-06-27)
- Superseded by NOTE 47 on 2026-06-28.
- This slice first removed `PlaybackStartController.Listener.playbackController()` by injecting the temporary `PlaybackController` facade.
- NOTE 47 then removed that facade and the adapter as well, leaving `PlaybackStartController` with direct function dependencies for streaming resolution, local playback, and result application.

## NOTE 41 - Retired playback facade history (2026-06-28)
- The old `app.yukine.playback.PlaybackController` / `PlaybackServiceController` facade is deleted and kept only as history in the migration ledger.
- The test-only fake facade pair is also deleted.
- Do not treat the old 7.3.1 facade plan as active migration work anymore.
## NOTE 42 - Network dialog forwarding bridge removed (2026-06-28)
- `NetworkRequestController` now implements `NetworkDialogController.Listener` directly.
- `MainActivity` passes `networkRequestController` directly into `NetworkDialogController`; the forwarding-only `NetworkDialogEventController` and its standalone test were deleted.
- Dialog-driven add stream, import M3U, update stream, and WebDAV save still publish status before delegating to `NetworkOperationSink`, now covered by `NetworkRequestControllerTest`.
- Verified serially with focused `NetworkRequestControllerTest` and `MainActivityArchitectureContractTest.mainActivityDelegatesNetworkOperationsThroughRequestController`.
## NOTE 43 - Streaming gateway event bridge removed (2026-06-28)
- Superseded by NOTE 49: `StreamingGatewayController` has also been removed.
- The intermediate slice made `StreamingGatewayController` own repository configure, provider refresh completion rendering, and applied-endpoint status publication directly.
- `StreamingGatewayEventController` and its standalone test were deleted because it only adapted controller callback interfaces to `StreamingViewModel`, render, and status sinks.
- `MainActivity` no longer keeps a streaming gateway field; current startup/auth-refresh paths use `refreshStreamingProviders()` directly.
- Current verification lives in NOTE 49; the old focused `StreamingGatewayControllerTest` was removed with the controller.
## NOTE 46 - Streaming repository configure call moved back to direct owner (2026-06-28)
- `MainActivity` now calls `streamingViewModel.configureStreamingRepository()` directly instead of routing through `StreamingGatewayController.configureRepository()`.
- Superseded by NOTE 49: the remaining refresh/render/status wrapper has also been folded into direct host helpers.
- Current verification lives in NOTE 49.
## NOTE 47 - Playback start adapter folded into controller (2026-06-28)
- `PlaybackStartControllerAdapter` and the single-method `PlaybackController` interface were removed.
- `PlaybackStartController` now directly owns the streaming-resolver -> local-player -> result-applier start path through narrow function dependencies.
- `MainActivity` constructs `PlaybackStartController` with `streamingPlaybackController::resolveAndPlayStreamingTrack`, `nowPlayingViewModel::playTrackList`, and `this::applyPlaybackActionResult`, removing one playback-start hop.
- Verified serially with `PlaybackStartControllerTest`, the playback architecture contract, and compile `:app:compileDebugKotlin :app:compileDebugJavaWithJavac`.
## NOTE 48 - Now-playing effect dispatcher inlined (2026-06-28)
- `NowPlayingEffectController` and `NowPlayingEffectControllerTest` were removed because the controller only forwarded drained effects back to `MainActivity` platform actions.
- `MainActivity.handleNowPlayingEffects()` now drains `NowPlayingViewModel` effects directly and calls the existing owners: tab navigation, `PlaylistDialogController`, `TrackShareLauncher`, `DownloadRequestController`, source switching, and `StatusMessageController`.
- Guarded by `MainActivityArchitectureContractTest`, which rejects the old effect controller and adapter while preserving the direct platform-owner calls.
## NOTE 49 - Streaming gateway controller folded into direct helpers (2026-06-28)
- `StreamingGatewayController` and `StreamingGatewayControllerTest` were removed after its remaining responsibilities shrank to provider refresh completion rendering and endpoint apply status.
- `MainActivity.refreshStreamingProviders()` now calls `StreamingViewModel.refreshStreamingProviders()` directly and syncs recommendation providers when the job completes.
- `MainActivity.applyStreamingGatewayEndpoint(...)` now saves the normalized endpoint through `StreamingGatewaySettingsStore`, reconfigures `StreamingViewModel`, refreshes providers, renders immediately, and publishes the applied-endpoint status without a controller hop.
- Guarded by `MainActivityArchitectureContractTest`, which rejects the old controller/test and protects the direct refresh/apply helper path.
## NOTE 50 - Recommendation action runner folded into direct ViewModel call (2026-06-28)
- The `RecommendationActionController` class and `RecommendationActionRunner` / `RecommendationLanguageProvider` interfaces were removed because they only forwarded typed recommendation actions to `StreamingRecommendationViewModel.onAction(...)`.
- `RecommendationAction` and `RecommendationActionCallbacks` remain as the typed recommendation boundary in `RecommendationAction.kt`; `RecommendationActionHandler` was later removed by NOTE 63 after direct ViewModel calls became the only production path.
- `MainActivity.runRecommendationAction(...)` now passes the action, current language mode, and shared callbacks directly to `StreamingRecommendationViewModel`, preserving daily/heartbeat presentation playback, seed lookup, miss logging, and status routing without a runner hop.
- Guarded by `MainActivityArchitectureContractTest`, which rejects the old runner class while preserving the typed action and handler contracts.
## NOTE 51 - Download directory picker shell folded into DocumentPickerController (2026-06-28)
- `DownloadDirectoryPickerController` and `DownloadDirectoryPickerControllerTest` were removed because the controller only checked for a document picker, published a fixed fallback message, and called `openDownloadFolderPicker()`.
- `DocumentPickerController.openDownloadFolderPicker()` is now the direct owner for the download directory picker launch.
- `MainActivity.openDownloadDirectoryPickerAction()` keeps only the null-guard fallback (`目录选择暂不可用`) before calling the document picker directly.
- Guarded by `MainActivityArchitectureContractTest.downloadRequestsAreOwnedOutsideMainActivity`, which rejects the old controller and preserves the direct picker path.
## NOTE 44 - Now playing stale render bridge removed (2026-06-28)
- `NowPlayingRenderController` was deleted because it no longer published UI state; the active Now page is driven by `NowPlayingViewModel.uiState` through `NowPlayingDestination`.
- `MainActivity.updateNowPlayingContent()` now keeps only the current-track existence check used by the lyrics refresh fallback.
- The unused `renderNowPlaying()` host method was removed.
- Guarded by `MainActivityArchitectureContractTest`, which now rejects the stale render controller and host entry point.
## NOTE 45 - MainActivity one-hop wrappers collapsed (2026-06-28)
- Queue render/intent listeners now call `QueueActionController.removeQueueTrack(...)` and `moveQueueTrack(...)` directly; the private `removeQueueTrack` / `moveQueueTrack` host wrappers were removed.
- Network action result listeners now call `NowPlayingViewModel.replaceQueuedTrack(...)` and `retainTracks(...)` directly; the private queue sync/retention wrappers were removed.
- Collections streaming sync now calls `StreamingPlaylistController.syncSelectedPlaylistFromStreaming()` directly.
- WebDAV source sync now reads `libraryStore.remoteSourceName(sourceId)` inline instead of through a host helper.
- WebDAV source playback now reads `libraryStore.webDavTracksForSource(source.id)` inline, and the unused `streamingProviderTrackId(...)` / `syncStreamingPlaylists(...)` host helpers were removed.
- Playlist dialog and collections row actions now call `LibraryViewModel.*Java(...)` directly and keep only the result handlers in `MainActivity`; the private playlist CRUD/move/add forwarding helpers were removed.
- Unified search load-more now calls `StreamingSearchActionHandler.loadNextPage()` from the action lambda directly; the private host wrapper was removed.
- `StreamingSearchEventController` was removed as a pure forwarding layer. `MainActivity` wires `StreamingSearchRenderController.Listener` inline to the strategy-bearing `DefaultStreamingSearchActionHandler`, playlist/recommendation/dialog owners, and `StreamingViewModel.updateStreamingSearchChrome(...)`.
## NOTE 52 - Streaming search action handler renamed away from Bindings (2026-06-28)
- `StreamingSearchActionHandlerBindings` was renamed to `DefaultStreamingSearchActionHandler` because it is a strategy-bearing handler, not a pure forwarding binding.
- The handler still owns provider selection, search dispatch, auth capability checks, playback capability checks, auth launch clearing, resolved-track playback handoff, and load-more validation.
- `DefaultStreamingSearchActionHandlerTest` keeps the previous behavior coverage under the new owner name.
- Guarded by `MainActivityArchitectureContractTest`, which rejects the old `*Bindings` file and preserves the handler's direct `StreamingViewModel` / action-gateway path.
## NOTE 53 - Library document gateway renamed away from Bindings (2026-06-28)
- `LibraryDocumentGatewayBindings` was renamed to `ContentResolverLibraryDocumentGateway` because it owns document I/O strategy, M3U import fallback, playlist import result mapping, and playlist export text writing.
- `ContentResolverLibraryDocumentGatewayTest` keeps the previous import/export fallback coverage under the new owner name.
- Current P1 follow-up moved that document gateway assembly into `LibraryModule`; `MainActivityBase` now binds the injected `LibraryDocumentGateway`.
- Root-package `*Bindings` count is now zero; the remaining library document behavior is a real gateway owner, not a temporary binding shell.
- WebDAV remote-source playback now reuses `NetworkSourcesEventController.playRemoteSourceTracks(...)` from both sources rows and source track-list actions; the duplicate `MainActivity.playRemoteSourceTracks(...)` helper was removed.
- Onboarding actions now call `OnboardingController` directly from `OnboardingActions`; the private `finish/openStreaming/scanLibrary/importPlaylistFromOnboarding` host wrappers were removed.
- Streaming playlist import refresh now calls `loadLibrary(true)` directly from `StreamingPlaylistController.Listener`; the private `refreshLibraryAfterStreamingImport()` host wrapper was removed.
- Additional host-only wrappers were inlined: manual streaming cookie import now calls `StreamingViewModel` / `StreamingManualCookieController` directly, playback state pre-resolve calls `StreamingPlaybackController` directly, network track-list remote-source clearing calls `RouteController` plus `navigateNetworkPage(...)` directly, and render content now calls `UiShellController` scrolling/tab APIs directly.
- Startup library loading now performs the permission branch inline in the startup path; the private `loadLibraryOnStartup()` wrapper was removed.
- Lyrics state refresh now reads the `LyricsViewModel` snapshot directly in the listener, and the unused `ensureLyricsLoaded(...)` host method was removed.
- `QueueIntentController` was deleted as a pure `QueueIntent -> listener` forwarding layer. `QueueViewModel.bindIntentListener(...)` now dispatches sealed `QueueIntent` values directly to `LibraryViewModel`, `PlaylistDialogController`, `QueueActionController`, and back handling.
- The no-op language bridge was removed completely: `SettingsRuntimeEffect.UpdateLanguage`, `SettingsRuntimeApplier.updateLanguage(...)`, `SettingsRuntimeLanguageUpdater`, and `MainUiShellController.updateLanguage(...)` are gone; language mode still persists through `SettingsViewModel` and is consumed where it actually matters.
- Guarded by `MainActivityArchitectureContractTest` direct-call and no-wrapper assertions.
## NOTE 54 - Playback session forwarding gateway removed (2026-06-28)
- `PlaybackSessionGateway` was removed because it only forwarded `session()`, `bind()`, `refresh()`, and `release()` to `PlaybackSessionManager`.
- `EchoPlaybackService` now holds `PlaybackSessionManager` directly and calls `bind()`, `refreshPlayer()`, `release()`, and `session()` without the extra service-internal gateway hop.
- The media notification platform token now reads from `playbackSessionManager.session()` directly, preserving notification/session behavior while shortening the service path.
- Guarded by `MainActivityArchitectureContractTest.playbackSessionLifecycleIsOwnedOutsideEchoPlaybackService`, which rejects `PlaybackSessionGateway` and preserves the direct manager calls.
## NOTE 55 - Settings render controller collapsed into label formatter (2026-06-28)
- `SettingsPageRenderController` was removed because it no longer rendered settings pages; it only forwarded scroll-to-top to `SettingsViewModel` and hosted static label formatting helpers.
- `MainActivity.requestCurrentDirectoryScrollToTop()` now calls `SettingsViewModel.scrollToTopOnNextRender()` directly when the settings tab is active.
- The remaining label helpers now live in `SettingsLabelFormatter`, which has no `SettingsViewModel`, listener, scroll state, or page render dependency.
- Guarded by `MainActivityArchitectureContractTest`, which rejects the old controller/field and preserves direct `SettingsViewModel` scrolling plus formatter ownership.
## NOTE 56 - Playback queue provider current-track method removed (2026-06-28)
- `PlaybackQueueManager.QueueProvider.currentTrack()` was removed from the large provider interface.
- `PlaybackQueueManager` now derives the current track from its existing `queue()` and `currentIndex()` provider methods, reducing one service/fake implementation method without changing queue behavior.
- `EchoPlaybackService` no longer implements this provider method for the queue manager, and `PlaybackQueueManagerTest` uses the same manager-side current-track calculation as production.
- Guarded by `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster`, which rejects the provider interface method and preserves the private manager helper.
## NOTE 57 - Download quality callback wrapper removed (2026-06-28)
- `DownloadQualitySelectedCallback` was removed because it only wrapped a single Kotlin callback for download-quality selection.
- `DownloadQualityChooser.choose(...)` now accepts `(StreamingAudioQuality) -> Unit` directly, while `DownloadQualityDialogController` remains the platform dialog owner.
- `DownloadRequestControllerTest` keeps the quality selection flow coverage with the simpler function callback.
- Guarded by `MainActivityArchitectureContractTest.downloadRequestsAreOwnedOutsideMainActivity`, which rejects the old callback wrapper.
## NOTE 58 - Download manager provider wrapper removed (2026-06-28)
- `DownloadManagerProvider` was removed because it only returned the current `TrackDownloadRequestQueue`.
- `DownloadRequestController` now receives `() -> TrackDownloadRequestQueue?` directly, preserving late access to the manager while removing one named compatibility interface.
- `MainActivity` still supplies the current `trackDownloadManager` lazily through the constructor lambda, and `DownloadRequestControllerTest` keeps the queue/enqueue coverage.
- Guarded by `MainActivityArchitectureContractTest.downloadRequestsAreOwnedOutsideMainActivity`, which rejects the old provider wrapper and preserves the direct function dependency.
## NOTE 59 - Network menu content sink removed (2026-06-28)
- `NetworkMenuContentSink` was removed because it only forwarded `title + metrics + actions` into `NetworkMenuViewModel.updateMenu(...)`.
- `NetworkMenuEventController` now receives `NetworkMenuViewModel` directly and updates the true network-menu UI state owner from `publishNetworkMenu(...)`.
- `MainActivity` no longer constructs a three-argument menu chrome lambda for this path; it passes the existing `networkMenuViewModel` to the event controller.
- Guarded by `NetworkMenuEventControllerTest.publishNetworkMenuUpdatesViewModelDirectly` plus `MainActivityArchitectureContractTest`, which rejects the old sink and preserves the direct ViewModel update.
## NOTE 60 - Collections action dead callback removed (2026-06-28)
- `CollectionsActionsSink` was removed because it had no remaining production call sites after `CollectionsRenderController` started updating `CollectionsViewModel` directly.
- The unused `CollectionsRenderController.Listener.publishCollectionsActions(...)` callback and matching `MainActivity` override were removed.
- Collections action state now stays on the direct `CollectionsRenderController -> CollectionsViewModel.updateActions(...)` path.
- Guarded by `MainActivityArchitectureContractTest.collectionsRenderControllerIsKotlinUiStateBoundary`, which rejects the old sink, listener call, and Activity override.
## NOTE 61 - Unused migration callback types removed (2026-06-28)
- Dead compatibility types with no production call sites were removed: `PlaylistIdAction`, `PlaylistAction`, `SelectedPlaylistExportOpener`, `SelectedPlaylistTrackMover`, `SelectedPlaylistTrackRemover`, `LibraryEventSink`, `NowPlayingEventHandler`, and `QueueNoArgAction`.
- These types no longer represented active Java/Kotlin interop boundaries or testable policy; the remaining flows already call their ViewModel/controller owners directly.
- Guarded by `MainActivityArchitectureContractTest` assertions in the collections, playback/queue, and library boundary checks.
## NOTE 62 - More unused action contracts removed (2026-06-28)
- Additional production-dead action contracts were removed after exact reference checks: `QueuePlaybackServiceAvailability`, `QueueStatusProvider`, `QueueStatusSink`, `NetworkPageAction`, `TrackListDownloadAction`, `SettingsStatusSink`, and `QueueTrackAction`.
- `QueueActionContracts.kt` now keeps only `QueuePlaybackActionResultApplier`, the one remaining contract used by `PlaybackStartController`.
- Network track-list header actions and collections download actions already use direct `Runnable` / listener paths, so these named action wrappers no longer represented active boundaries.
- Guarded by `MainActivityArchitectureContractTest`, which now rejects these removed contracts in the playback/queue, collections, and network render checks.
## NOTE 63 - Recommendation self-interfaces removed (2026-06-28)
- `DailyRecommendationPlayer` and `RecommendationActionHandler` were removed because production code no longer holds recommendations through those interfaces.
- `StreamingRecommendationViewModel` keeps the same `onAction(...)` and `playDailyRecommendations(...)` methods directly, while `HeartbeatRecommendationPlayer` remains because `HeartbeatRecommendationController` still depends on that smaller behavior boundary.
- `RecommendationAction.kt` now contains only the typed action model and platform callback contract.
- Guarded by `MainActivityArchitectureContractTest`, which rejects the removed self-interfaces while preserving the direct ViewModel action path and heartbeat player boundary.
## NOTE 64 - Playlist export controller folded into document picker (2026-06-28)
- `PlaylistExportController` was removed because it only stored pending playlist id/name and forwarded the export URI back to `LibraryViewModel`.
- `DocumentPickerController.openPlaylistExportDocument(playlistId, playlistName)` now owns the create-document launch and pending export context, and emits `exportPlaylist(exportUri, playlistId, playlistName)` directly from the result callback.
- `MainActivity` keeps only the selected-playlist empty guard before launching the picker, then calls `LibraryViewModel.exportPlaylistJava(...)` from the existing document picker listener.
- Guarded by `DocumentPickerControllerTest` and `MainActivityArchitectureContractTest.mainSettingsStoreIsKotlinStateHolder`, which reject the removed controller while preserving playlist export document creation and export context delivery.
## NOTE 65 - Empty playlist export callback removed (2026-06-28)
- `LibraryPlaylistExportCallback` was removed because the only production caller passed an empty callback after playlist export.
- `LibraryViewModel.exportPlaylistJava(...)` now exposes the Java shell entry without a callback parameter and delegates to the existing coroutine export path.
- `MainActivity` calls the no-callback export method directly from `DocumentPickerController.Listener.exportPlaylist(...)`, reducing one Java/Kotlin interop interface without changing document export behavior.
- Guarded by `MainActivityArchitectureContractTest.mainSettingsStoreIsKotlinStateHolder`, which rejects the removed callback interface and the old four-argument Activity call.
## NOTE 66 - Root Bindings zero-count guard added (2026-06-28)
- Root-package main/test `*Bindings*` files are now guarded by a directory-level architecture contract instead of only many per-file string checks.
- `MainActivityArchitectureContractTest.rootPackageHasNoMigrationBindingsFiles` counts `app/src/main/java/app/yukine/*Bindings*` and `app/src/test/java/app/yukine/*Bindings*` and requires both to stay at zero.
- This turns the current zero-Bindings state into a structural regression guard for the stabilization pivot: new migration shells must justify themselves outside the old root-package bridge pattern.
## NOTE 67 - Dead play-history Java callback removed (2026-06-28)
- `LibraryPlayHistoryClearedCallback` and `LibraryViewModel.clearPlayHistoryJava(...)` were removed because play-history clearing now flows through `PlayHistoryActionController -> LibraryViewModel.clearPlayHistory { ... }`.
- The active path still updates the collection gateway, clears the `MainActivityViewModel` play-history snapshot via `PlayHistoryStateStore`, publishes status, and reloads collections.
- Guarded by `PlayHistoryActionControllerTest` plus `MainActivityArchitectureContractTest.mainSettingsStoreIsKotlinStateHolder`, which rejects the dead Java callback while preserving the direct Kotlin clear path.
## NOTE 68 - Duplicate collection favorite write path removed (2026-06-28)
- `LibraryViewModel.saveLibraryFavorite(...)`, `saveLibraryFavoriteJava(...)`, `LibraryCollectionGateway.setFavorite(...)`, and `SetLibraryFavoriteUseCase` were removed because production favorite toggles already use `LibraryEvent.ToggleFavorite -> LibraryFavoriteWriter -> ToggleFavoriteUseCase`.
- `LibraryCollectionGateway` now stays focused on loading collections and clearing play history; favorite mutation no longer has a second collection gateway path.
- Guarded by `LibraryViewModelTest` / `ToggleFavoriteUseCaseTest` for the active favorite path and `MainActivityArchitectureContractTest.mainSettingsStoreIsKotlinStateHolder`, which rejects the removed collection favorite write path.
## NOTE 69 - Now-playing playback service function wrappers removed (2026-06-28)
- `PlaybackServiceProvider` and `PlaybackServiceStarter` were removed from `NowPlayingPlaybackGatewayAdapter` because they only wrapped function dependencies.
- `NowPlayingPlaybackGatewayAdapter` now receives `() -> EchoPlaybackService?` and `(String?) -> Unit` directly, preserving the existing `NowPlayingPlaybackGateway` service boundary while reducing two named interop interfaces.
- Guarded by `MainActivityArchitectureContractTest`, which rejects the removed function wrappers and preserves the direct service dependencies.
## NOTE 70 - Backup status sink wrapper removed (2026-06-28)
- `BackupStatusSink` was removed from `BackupRestoreLauncher` because it only wrapped a status-key function.
- `BackupRestoreLauncher` now receives `(String) -> Unit` directly; backup export/import still maps results to `backup.*` status keys and lets `StatusMessageController` localize/publish them.
- Guarded by `BackupRestoreLauncherTest` plus `MainActivityArchitectureContractTest.backupRestoreLauncherIsOwnedOutsideMainActivity`, which rejects the removed sink wrapper and preserves the launcher-owned document intents.
## NOTE 71 - Download status sink wrapper removed (2026-06-28)
- `DownloadStatusSink` was removed from `DownloadRequestController` because it only wrapped a raw status-message function.
- `DownloadRequestController` now receives `(String) -> Unit` directly while keeping download quality selection, streaming download URL resolution, queue enqueue, and `DownloadsViewModel.refresh(...)` ownership unchanged.
- Guarded by `DownloadRequestControllerTest` plus `MainActivityArchitectureContractTest.downloadRequestsAreOwnedOutsideMainActivity`, which rejects the removed sink wrapper and preserves the download owner path.
## NOTE 72 - Track share provider wrappers removed (2026-06-28)
- `TrackShareLanguageProvider` and `TrackShareStyleProvider` were removed from `TrackShareLauncher` because they only wrapped zero-argument string providers.
- `TrackShareLauncher` now receives direct `() -> String` dependencies for language mode and share style while keeping `TrackShareStatusSink` for the two-channel feedback/status boundary.
- Guarded by `TrackShareLauncherTest` plus `MainActivityArchitectureContractTest`, which rejects the removed provider wrappers and preserves chooser/native-share behavior.
## NOTE 73 - Play-history function wrappers removed (2026-06-28)
- `PlayHistoryLanguageModeProvider` and `PlayHistoryStatusSink` were removed from `PlayHistoryActionController` because they only wrapped language-mode and status functions.
- `PlayHistoryActionController` now receives direct `() -> String` and `(String) -> Unit` dependencies while keeping `PlayHistoryStateStore` as the explicit state-clearing boundary.
- Guarded by `PlayHistoryActionControllerTest` plus `MainActivityArchitectureContractTest`, which rejects the removed wrappers and preserves the direct clear-history path.
## NOTE 74 - Background picker provider wrappers removed (2026-06-28)
- `BackgroundLanguageModeProvider` and `BackgroundTransformProvider` were removed from `BackgroundImagePickerController` because they only wrapped preview language and transform lookup functions.
- `BackgroundImagePickerController` now receives direct `() -> String` and `(String) -> BackgroundTransform` dependencies while keeping document/preview activity-result launchers as the real platform boundaries.
- Guarded by `BackgroundImagePickerControllerTest` plus `MainActivityArchitectureContractTest.backgroundImagePickerIsOwnedOutsideMainActivity`, which rejects the removed provider wrappers and preserves picker/preview/save ownership.
## NOTE 75 - Status message language provider wrappers removed (2026-06-28)
- `StatusLanguageModeProvider` and `MessageLanguageModeProvider` were removed because they only wrapped language-mode lookup for status localization.
- `StatusMessageController` and `MessageTextResolver` now share a direct `Supplier<String>` language dependency while keeping `RawStatusUpdater` as the status-output boundary.
- Guarded by `StatusMessageControllerTest`, `MessageTextResolverTest`, and `MainActivityArchitectureContractTest.mainActivityDelegatesStatusLocalizationToStatusMessageController`, which rejects the removed provider wrappers and preserves localized status publishing.
## NOTE 76 - Root-cause audit correction applied (2026-06-28)
- The latest review changes the migration default from "continue deleting wrapper layers" to "reduce the three root hotspots first": `MainActivity` assembly/anonymous callback policy, `EchoPlaybackService` playback policy residue, and `EchoDatabaseHelper` migration risk.
- A controller deletion is no longer accepted if behavior moves back into `MainActivity` private helpers, anonymous listeners, or `onCreate` construction. Host assembly density must go down, or the slice is not net architecture progress.
- Deleted controller tests must be mapped to replacement behavior tests under the new owner; string-based architecture contracts remain useful alarms but are not behavior coverage.
- Database work now starts with migration/transaction tests before Room or repository split work, and concurrency work starts with an inventory of raw Thread/ExecutorService/scheduler ownership and shutdown.
- Reflected in `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`, `docs/ARCHITECTURE_REMEDIATION_PLAN_2026-06-26.md`, and the `yukine-android-maintenance` skill.
## NOTE 77 - Settings runtime controls moved behind factory (2026-06-29)
- `SettingsModule` now provides `MainSettingsRuntimeApplierFactory`, and the concrete runtime controls live beside `SettingsRuntimeApplier` as `MainSettingsPlaybackServiceControls`, `MainSettingsLyricsControls`, and `MainSettingsFloatingLyricsControls`.
- `MainActivityBase` now only passes the existing theme, playback service, lyrics ViewModel, and permission-controller providers to `settingsRuntimeApplierFactory.create(...)`; it no longer constructs anonymous settings control implementations or calls `FloatingLyricsService` directly from settings runtime setup.
- The Settings path remains `SettingsViewModel -> preference/runtime applier`; no settings gateway, coordinator, controller, event bus, or root `*Bindings*` file was added. Current recheck: `MainActivityBase.java` 2632 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 175, and root-package Java files 15.
- Guarded by `SettingsRuntimeApplierTest`, `SettingsViewModelTest`, and `MainActivityArchitectureContractTest`; compile passed with `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
## NOTE 78 - Collections render listener policy moved out of Activity (2026-06-29)
- `LibraryModule` now provides `MainCollectionsRenderListenerFactory`, and `MainCollectionsRenderListener` owns the collections render action routing that was previously an anonymous `CollectionsRenderController.Listener` in `MainActivityBase`.
- `MainActivityBase` now creates `CollectionsRenderController` with `collectionsRenderListenerFactory.create(...)`; Activity only supplies existing host capabilities such as playlist dialogs, document pickers, download requests, selected-playlist state sources, streaming import actions, and selected-playlist mutation callbacks.
- The selected-playlist export guard moved with the listener: empty or missing selected playlists publish `no.tracks.in.playlist`, while valid exports call `openPlaylistExportDocument(playlistId, playlistName)`. No root `*Bindings*` or root `*Controller*` file was added. Current recheck: `MainActivityBase.java` 2562 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 176, and root-package Java files 15.
- Guarded by `MainCollectionsRenderListenerTest` and `MainActivityArchitectureContractTest`; compile passed with `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
## NOTE 79 - Library groups render listener policy moved out of Activity (2026-06-29)
- `LibraryModule` now provides `MainLibraryGroupsRenderListenerFactory` and `ArtistInfoRepository`. `MainLibraryGroupsRenderListener` owns the library-groups render action routing that was previously an anonymous `LibraryGroupsRenderController.Listener` in `MainActivityBase`.
- `MainActivityBase` now creates `LibraryGroupsRenderController` with `libraryGroupsRenderListenerFactory.create(...)` and the injected `artistInfoRepository`; Activity no longer constructs `new ArtistInfoRepository()` or the library-groups listener inline.
- The listener owns favorite-collection title resolution, chrome state copy construction, and library group track-list request construction, while Activity only supplies existing host callbacks for group open/clear/close, track playback, deletion confirmation, chrome publishing, and track-list rendering. Current recheck: `MainActivityBase.java` 2517 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 177, and root-package Java files 15.
- Guarded by `MainLibraryGroupsRenderListenerTest` and `MainActivityArchitectureContractTest`; compile passed with `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
## NOTE 80 - Playback P0 baseline freeze captured (2026-06-29)
- New controlling checkpoint: `docs/PLAYBACK_P0_BASELINE_2026-06-29.md`.
- Baseline facts at capture: `EchoPlaybackService.java` was 2469 lines, `PlaybackQueueManager.QueueProvider` had 33 methods, and the dirty worktree already contained the ongoing MainActivity/library-listener migration entries listed in that checkpoint. Those entries are preserved as user-owned migration work.
- Verification passed with default Gradle daemon/workers: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and focused playback tests via `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests "app.yukine.playback.*" --tests "app.yukine.*Playback*" --tests "app.yukine.*Queue*" --tests "app.yukine.*NowPlaying*" --tests "app.yukine.*Lyrics*" --tests "app.yukine.*Media*" --tests "app.yukine.*Notification*" --console=plain`.
- Device smoke for local playback, background playback, notification controls, queue restore, lyrics, and streaming playback is explicitly recorded as not run. The next playback implementation slice should start with a small queue-authority change and must not expand `QueueProvider`.
## NOTE 81 - Playback target architecture clarified (2026-06-29)
- Controlling target: `UI / Compose -> ViewModel / UI State -> playback command owner -> PlaybackService boundary -> playback engine managers -> ExoPlayer / MediaSession / Notification`.
- `EchoPlaybackService` should converge toward an Android/Media3 shell for lifecycle, MediaSession, foreground notification, service binding, external intents/media buttons, and ExoPlayer ownership. Queue strategy, playable URI/media item resolution, cache policy, notification action mapping, and lyrics sync should move into small real owners.
- Preferred owners for upcoming slices are `PlaybackQueueManager`, `PlaybackItemResolver`, `PlaybackCachePolicy`, `PlaybackNotificationController`, and `LyricsPlaybackBridge` or existing equivalents. Do not introduce a universal `PlaybackServiceFacade`.
- Queue/current-index/current-track must have one authority, preferably `PlaybackQueueManager`, with Service and UI consuming snapshots instead of mirroring mutable state across Service fields, ViewModels, manager state, and MediaSession queue.
- Large playback interfaces default to reduction. `PlaybackQueueManager.QueueProvider` should lose derivable methods, split by responsibility, or be replaced with a direct state owner dependency; do not add methods without a prior split/merge/inline plan.
- Playback validation priority is queue restore, skip next/previous, replace current track, local vs streaming URI resolution, notification action, background playback, service restart recovery, and cache partial failure cleanup.
## NOTE 82 - Playback queue restore filter moved into queue manager (2026-06-29)
- P1 queue-authority slice: `PlaybackQueueManager` now owns the restored-queue track eligibility rule that was previously exposed through `PlaybackQueueManager.QueueProvider.isRestorableQueueTrack(...)` and implemented by `EchoPlaybackService`.
- `QueueProvider` shrank from 33 to 32 methods. `EchoPlaybackService` no longer implements the provider method or carries the private `isRestorableQueueTrack(Track)` helper; the Service now delegates queue restore to `playbackQueueManager.restorePlaybackQueue()` without providing that policy.
- Behavior preserved: restore still rejects negative ids, blank data paths, missing local URIs, missing file targets, and empty URI strings, while allowing streaming placeholders and valid non-file URIs. `PlaybackQueueManagerTest.restorePlaybackQueueFiltersInvalidTracksInsideManager` covers that boundary.
- Guarded by `PlaybackQueueManagerTest` and `MainActivityArchitectureContractTest`, with app compile passing under default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Current recheck: `EchoPlaybackService.java` 2442 lines and `PlaybackQueueManager.QueueProvider` 32 methods.
## NOTE 83 - Playback queue current index moved into queue manager (2026-06-29)
- P1 queue-authority slice: `PlaybackQueueManager` now owns current-index state and clamping. `PlaybackQueueRuntimeStateManager` only owns `playerMirrorsQueue`; it no longer stores or clamps current index.
- `QueueProvider` shrank from 32 to 30 methods by deleting `currentIndex()` and `setCurrentIndex(index)`. `EchoPlaybackService` now reads/writes the queue cursor through `playbackQueueManager.currentIndex()`, `setCurrentIndex(...)`, `clampCurrentIndex(...)`, and `setClampedCurrentIndex(...)`.
- The queue owner now owns index updates for restore, append, next/previous, move, remove, replace, mirrored-queue reuse, and persistence. `PlaybackQueueManagerTest.currentIndexStateIsOwnedByQueueManager` covers set/clamp behavior, while the existing queue tests cover restore, previous, replace-current recovery, and mirrored queue reuse.
- Guarded by `PlaybackQueueManagerTest`, `PlaybackQueueRuntimeStateManagerTest`, and `MainActivityArchitectureContractTest`, with app compile passing under default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --tests app.yukine.playback.PlaybackQueueRuntimeStateManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Current recheck: `EchoPlaybackService.java` 2436 lines and `PlaybackQueueManager.QueueProvider` 30 methods.
## NOTE 84 - Playback queue resume-request persistence moved into queue manager (2026-06-29)
- P1 queue-authority slice: queue-driven resume-request writes now go directly through `PlaybackQueueManager -> PlaybackQueueStore.saveResumeRequested(...)` instead of `PlaybackQueueManager.QueueProvider.savePlaybackResumeRequested(...)`.
- `QueueProvider` shrank from 30 to 29 methods. `EchoPlaybackService` still keeps its private `savePlaybackResumeRequested(...)` helper for service-owned pause/stop/Media3 boundary events, but it no longer supplies that persistence callback to the queue manager.
- `PlaybackQueueManagerTest.queuePlaybackStartPersistsResumeRequestThroughStore` and the mirrored-queue reuse tests cover the queue-owned resume-request path. `MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster` guards against the provider method returning.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Current recheck: `EchoPlaybackService.java` 2431 lines and `PlaybackQueueManager.QueueProvider` 29 methods.
## NOTE 85 - Playback sleep timer command owner binding narrowed (2026-07-03)
- Wiring slice: `PlaybackSleepTimerCommandOwner` no longer keeps a `Supplier<PlaybackSleepTimerManager>` just to work around Service initialization order. `EchoPlaybackService` now creates the command owner, creates `PlaybackSleepTimerManager`, and immediately calls `bindPlaybackSleepTimerManager(...)`.
- This removes one supplier forwarding chain from Service without adding an owner or changing sleep-timer runtime policy. The remaining `() -> playbackSleepTimerManager` occurrence belongs to shutdown resource release wiring and is left for a P5 lifecycle audit instead of mixing shutdown behavior into this slice.
- `MainActivityArchitectureContractTest` now rejects the removed supplier/provider field and requires the bind call, while `PlaybackSleepTimerCommandOwnerTest` covers bound delegation and the unbound no-op state.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackSleepTimerCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks for this change. Current recheck: `EchoPlaybackService.java` 1330 lines, `private Playback*` fields 55, `fromPlaybackQueueManager(...)` 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke was not run because this slice did not change notification, background, lyrics, shutdown, or service lifecycle behavior.
## NOTE 86 - Playback crossfade command owner binding narrowed (2026-07-03)
- Wiring slice: `PlaybackCrossfadeCommandOwner` no longer keeps a `Supplier<PlaybackCrossfadeAdvanceManager>` for Service initialization order. `EchoPlaybackService` now creates the command owner, creates `PlaybackCrossfadeAdvanceManager`, and immediately calls `bindPlaybackCrossfadeAdvanceManager(...)`.
- This removes a second Service supplier forwarding chain in the same pattern as NOTE 85 without adding an owner or moving crossfade policy back into Service. The remaining `() -> playbackCrossfadeAdvanceManager` occurrence belongs to shutdown resource release wiring and is deferred to the P5 lifecycle audit.
- `MainActivityArchitectureContractTest` now rejects the removed crossfade supplier/provider field and requires the bind call. `PlaybackCrossfadeCommandOwnerTest` covers bound cancel/start delegation plus the unbound no-op state.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackCrossfadeCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks for this change. Current two-slice recheck: `EchoPlaybackService.java` 1330 lines, `private Playback*` fields 55, `fromPlaybackQueueManager(...)` 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Focused tests protecting the batch: `PlaybackSleepTimerCommandOwnerTest`, `PlaybackCrossfadeCommandOwnerTest`, and `MainActivityArchitectureContractTest`. Device smoke was not run because this slice did not change notification, background, lyrics, shutdown, or service lifecycle behavior.
## NOTE 87 - Playback queue state owner manager supplier removed (2026-07-03)
- Queue/state slice: `PlaybackQueueStateOwner` no longer accepts or stores `Supplier<PlaybackQueueManager>`. Production already used default construction plus `bindPlaybackQueueManager(...)`; tests now use a direct manager constructor or explicit bind/rebind instead of preserving a dynamic provider seam.
- This removes a queue-state supplier entry point and one constructor shape from the central queue-state owner without adding another owner or exposing `queueStateSnapshot()` publicly. The state owner still reads queue/current-track/current-index/queue-size from `PlaybackQueueManager` as the authority.
- `MainActivityArchitectureContractTest` now rejects `Supplier<PlaybackQueueManager>` and `playbackQueueManagerSupplier` inside `PlaybackQueueStateOwner`, while requiring the direct manager constructor and bind method. `PlaybackQueueStateOwnerTest` now covers bind/rebind plus empty-state behavior.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueStateOwnerTest --tests app.yukine.playback.PlaybackCrossfadeStateOwnerTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.playback.PlaybackFavoriteCommandOwnerTest --tests app.yukine.playback.PlaybackLyricsStateOwnerTest --tests app.yukine.playback.PlaybackNotificationStateOwnerTest --tests app.yukine.playback.PlaybackPlayHistoryRecorderTest --tests app.yukine.playback.PlaybackPrecacheStateOwnerTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.playback.PlaybackQueueMirroredPlayerOwnerTest --tests app.yukine.playback.PlaybackQueueMirroredTransitionOwnerTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --tests app.yukine.playback.PlaybackSessionCommandOwnerTest --tests app.yukine.playback.PlaybackStateSnapshotOwnerTest --tests app.yukine.playback.PlaybackVisualizationCacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1330 lines, `private Playback*` fields 55, `fromPlaybackQueueManager(...)` 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke was not run because this slice did not change notification, background, lyrics, shutdown, or service lifecycle behavior.
## NOTE 88 - Playback visualization snapshot supplier removed (2026-07-03)
- Wiring slice: `PlaybackStateSnapshotOwner.fromVisualizationAnalyzer(...)` now receives the already-created `PlaybackVisualizationAnalyzer` directly instead of a `Supplier<PlaybackVisualizationAnalyzer>`. `EchoPlaybackService` still owns analyzer construction, but no longer keeps a lambda forwarding chain for snapshot visualization reads.
- This does not add an owner or move visualization/cache/session policy. It only narrows the snapshot adapter boundary and leaves URI/media item resolution in `PlaybackMediaSourceProvider`, cache policy in existing playback cache owners, and notification/lyrics/shutdown untouched.
- `MainActivityArchitectureContractTest` now requires the direct analyzer adapter and rejects `fromVisualizationAnalyzerProvider(...)`, `Supplier<PlaybackVisualizationAnalyzer>`, and `visualizationAnalyzerProvider`. `PlaybackStateSnapshotOwnerTest.visualizationProviderUsesEmptySnapshotsWhenAnalyzerIsMissing` covers the missing-analyzer fallback.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackStateSnapshotOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1425 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background, lyrics, shutdown, or service lifecycle behavior.
## NOTE 89 - Playback queue completion lambda forwarding removed (2026-07-03)
- Queue/service wiring slice: `EchoPlaybackService` no longer routes queue completion work through `withPlaybackQueueCompletionOwner(Consumer<PlaybackQueueCompletionOwner>)` method references or lambdas. It now creates the short-lived `PlaybackQueueCompletionOwner` through `playbackQueueCompletionOwner()` and invokes the semantic completion/preparation method directly.
- This keeps queue completion policy in `PlaybackQueueCompletionOwner` and `PlaybackQueueManager`, does not resurrect `PlaybackQueueStopClearOwner`, and does not add a Service field or new owner. The slice removes one Service lambda/method-reference forwarding chain while preserving the no-direct-queue-manager rule for stop/clear and completion preparation.
- `MainActivityArchitectureContractTest` now rejects `withPlaybackQueueCompletionOwner(...)` and `Consumer<PlaybackQueueCompletionOwner>`, while requiring direct `playbackQueueCompletionOwner().playAfterCompletion()`, `prepareStopAndClearPlaybackState()`, `prepareStopAtEndOfQueue()`, and `prepareStopAfterAutomaticAdvance(completedIndex)` calls. `PlaybackQueueCompletionOwnerTest` continues to cover completion routing and stop preparation behavior.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCompletionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1423 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background, lyrics, shutdown, or service lifecycle behavior.
## NOTE 90 - Playback queue navigation lambda forwarding removed (2026-07-03)
- Queue/service wiring slice: `EchoPlaybackService` no longer routes queue navigation through `withPlaybackQueueNavigationOwner(Consumer<PlaybackQueueNavigationOwner>)` method references. It now creates the short-lived `PlaybackQueueNavigationOwner` through `playbackQueueNavigationOwner()` and invokes semantic navigation methods directly.
- This also removes the duplicate inline `new PlaybackQueueNavigationOwner(...)` construction in mirrored-queue reuse. Queue navigation policy remains in `PlaybackQueueNavigationOwner` and `PlaybackQueueManager`; Service still does not call `playbackQueueManager.skipToNextImmediately()`, `skipToPrevious()`, `playFirstQueuedTrack()`, or `reuseMirroredQueueIfAvailable(...)` directly.
- `MainActivityArchitectureContractTest` now rejects `withPlaybackQueueNavigationOwner(...)` and `Consumer<PlaybackQueueNavigationOwner>`, while requiring direct `playbackQueueNavigationOwner().skipToNextImmediately()`, `playFirstQueuedTrack()`, `skipToPrevious()`, and mirrored-queue reuse calls. `PlaybackQueueNavigationOwnerTest` continues to cover navigation delegation and mirrored-queue reuse notifications.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueNavigationOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1420 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background, lyrics, shutdown, or service lifecycle behavior.
## NOTE 91 - Playback queue persistence lambda forwarding removed (2026-07-03)
- Queue/service wiring slice: `EchoPlaybackService` no longer routes playback-resume and task-removed queue persistence through `withPlaybackQueuePersistenceOwner(Consumer<PlaybackQueuePersistenceOwner>)`. It now creates a short-lived `PlaybackQueuePersistenceOwner` through `playbackQueuePersistenceOwner()` and invokes persistence methods directly.
- This keeps queue persistence in `PlaybackQueuePersistenceOwner` and `PlaybackQueueManager`; Service still does not call `playbackQueueManager.persistQueueState()` or `savePlaybackResumeRequested(...)` directly. The existing shutdown lifecycle local `PlaybackQueuePersistenceOwner` remains unchanged, so this slice does not move or expand P5 shutdown ownership.
- `MainActivityArchitectureContractTest` now rejects `withPlaybackQueuePersistenceOwner(...)` and `Consumer<PlaybackQueuePersistenceOwner>`, while requiring direct `playbackQueuePersistenceOwner().requestPlaybackResume()`, `clearPlaybackResumeRequest()`, and task-removed persistence calls. `PlaybackQueuePersistenceOwnerTest` and `PlaybackShutdownLifecycleResourcesOwnerTest` protect the owner and lifecycle-store behavior.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueuePersistenceOwnerTest --tests app.yukine.playback.PlaybackShutdownLifecycleResourcesOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1419 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background playback, lyrics, shutdown coordinator behavior, or service destruction behavior.
## NOTE 92 - Playback queue restore lambda forwarding removed (2026-07-03)
- Queue/service wiring slice: `EchoPlaybackService` no longer routes queue restore through `withPlaybackQueueRestoreOwner(Consumer<PlaybackQueueRestoreOwner>)`. It now creates a short-lived `PlaybackQueueRestoreOwner` through `playbackQueueRestoreOwner()` and invokes restore methods directly.
- This keeps restore policy in `PlaybackQueueRestoreOwner` and `PlaybackQueueManager`; Service still does not call `playbackQueueManager.restorePlaybackQueue()`, `restoreLastPlayback(...)`, or `setPlaybackRestoreEnabled(...)` directly. No owner, field, facade, or package move was added.
- `MainActivityArchitectureContractTest` now rejects `withPlaybackQueueRestoreOwner(...)` and `Consumer<PlaybackQueueRestoreOwner>`, while requiring direct `playbackQueueRestoreOwner().restorePlaybackQueue()`, `restoreLastPlayback(playWhenRestored)`, and `setPlaybackRestoreEnabled(enabled)` calls. `PlaybackQueueRestoreOwnerTest` continues to protect restore, publish, prepare, and setting delegation behavior.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueRestoreOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1419 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background playback, lyrics, shutdown, or service lifecycle behavior.
## NOTE 93 - Playback queue mutation lambda forwarding removed (2026-07-03)
- Queue/service wiring slice: `EchoPlaybackService` no longer routes public queue mutation calls through `withPlaybackQueueMutationOwner(Consumer<PlaybackQueueMutationOwner>)`. It now creates the short-lived `PlaybackQueueMutationOwner` through `playbackQueueMutationOwner()` and invokes mutation methods directly for play queue, append, move, remove, retain, clear, and replace-by-id.
- This removes one Service lambda/method-reference forwarding chain and one duplicate inline owner construction in session media-item handling. Mutation policy remains in `PlaybackQueueMutationOwner` and `PlaybackQueueManager`; Service still does not call `playbackQueueManager.playQueue(...)`, `appendToQueue(...)`, `moveQueueTrack(...)`, `removeTracksById(...)`, `retainTracksById(...)`, `clearQueue()`, or `replaceQueuedTrackById(...)` directly. No owner, field, facade, or package move was added.
- `MainActivityArchitectureContractTest` now rejects `withPlaybackQueueMutationOwner(...)` and `Consumer<PlaybackQueueMutationOwner>`, while requiring direct `playbackQueueMutationOwner().playQueue(...)`, `appendToQueue(...)`, `moveQueueTrack(...)`, `removeTracksById(...)`, `retainTracksById(...)`, `clearQueue()`, and `replaceQueuedTrackById(...)` calls. `PlaybackQueueMutationOwnerTest` continues to protect mutation delegation and stop/clear behavior.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1413 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background playback, lyrics, shutdown, or service lifecycle behavior.
## NOTE 94 - Playback queue owner/interface audit checkpoint (2026-07-03)
- Audit scope after the queue completion/navigation/persistence/restore/mutation wiring slices: `PlaybackQueueManager.QueueProvider` and `interface QueueProvider` are absent from production playback code, and `MainActivityArchitectureContractTest` already guards against `new PlaybackQueueManager.QueueProvider()`, public `PlaybackQueueManager.currentTrack()`, and Service direct `playbackQueueManager.queueStateSnapshot()` access returning.
- Current queue/state API grouping: true queue commands are `playQueue(...)`, `appendToQueue(...)`, `moveQueueTrack(...)`, `removeTracksById(...)`, `retainTracksById(...)`, `replaceQueuedTrackById(...)`, `replaceCurrentTrackAndResume(...)`, and `replaceCurrentQueueTrack(...)`; navigation/completion commands are `playFirstQueuedTrack()`, `skipToNextImmediately()`, `skipToPrevious()`, `preparePlaybackCompletionAction()`, `prepareStopAtEndOfQueue()`, `prepareStopAfterAutomaticAdvance(...)`, and `prepareStopAndClearPlaybackState()`; persistence/restore commands are `persistQueueState()`, `restorePlaybackQueue()`, `restoreLastPlayback(...)`, and `setPlaybackRestoreEnabled(...)`; read/snapshot outputs are `queueSnapshot()`, `queueStateSnapshot()`, `upcomingTracksForPrecache(...)`, `queuePreparationForNewPlayer()`, `reuseMirroredQueueIfAvailable(...)`, and `applyMirroredTransitionIndex(...)`.
- Current derived-read boundary: `PlaybackQueueManager.QueueStateSnapshot` still stores only `currentTrack`, `currentIndex`, and `queueSize`; `isQueueEmpty`, `hasCurrentTrack`, `hasMultipleTracks`, and `isAtEndOfQueue` remain derived. `PlaybackQueueStateOwner` exposes the small Java-facing read helpers consumed by `PlaybackStateSnapshotOwner`, notification/lyrics/precache/visualization state owners, mirrored queue owners, crossfade, error recovery, and mutation clear checks. No `PlaybackQueueStateOwner` helper was identified as unused in this audit.
- Service wiring audit: `EchoPlaybackService.java` is 1413 lines, has 43 `private Playback*` fields, has 0 Service `fromPlaybackQueueManager(...)` calls, has 0 Service `queueStateSnapshot()` direct suppliers, and `Playback*Owner.java` file count remains 43. The four remaining `playbackQueueStateOwner::currentTrack` constructor dependencies feed distinct owners/managers: runtime state, position persistence, notification artwork, and Wi-Fi lock streaming policy. Do not collapse them into a single `Supplier<Track>` Service field unless the slice also removes a real owner/interface/constructor dependency; otherwise it turns Service into a wiring store.
- Next safe candidates from this audit: first, inspect whether any `PlaybackQueueManager` read output can be narrowed at the consumer boundary without adding a new facade; second, inspect `EchoPlaybackService.queueSnapshot()` callers from UI/app shell and identify whether any can move to an existing semantic playback/queue command owner; third, keep URI/cache audit read-only unless it can add focused resolver/cache behavior tests without expanding `PlaybackMediaSourceResolutionOwner`.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also rechecked app Kotlin/Java compile tasks as up-to-date. Device smoke remains out of scope because this checkpoint is a read-only owner/interface audit and does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 95 - Streaming pre-resolve queue snapshot reuse (2026-07-03)
- Queue/read boundary slice: `StreamingPlaybackController.preResolveNextStreamingTrack(...)` now reads `listener.queueSnapshot()` once per active playback-state event and reuses that list for both next-track pre-resolution and queue-window pre-resolution. Before this slice, the controller pulled the same queue snapshot twice, which could route two identical reads through `MainActivityBase -> EchoPlaybackService.queueSnapshot()` for one playback-state event.
- This does not add an owner, facade, field, or package move. The queue source remains the existing `StreamingQueueSnapshotSource`, and streaming URI resolution remains in the existing `StreamingViewModel` / resolver path. The concrete gain is one fewer queue snapshot supplier/Service read per active streaming pre-resolve event.
- Added `StreamingPlaybackControllerTest` coverage for the active path reusing one queue snapshot and the inactive path avoiding queue reads. `MainActivityArchitectureContractTest` now requires the local `val queueSnapshot = listener.queueSnapshot()` and guards the controller against returning to multiple `listener.queueSnapshot()` calls.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingPlaybackControllerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also rechecked app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1413 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 96 - Media source header boundary coverage (2026-07-03)
- URI/cache boundary test slice: `PlaybackMediaSourceProviderTest` now covers `headersForTrack(...)` as a provider-owned rule for both streaming playback headers and WebDAV Basic Auth headers. This locks the intended boundary that playable URI/media item/header resolution remains in `PlaybackMediaSourceProvider`, while cache/precache users consume it through `PlaybackMediaCacheOperations` instead of reimplementing header policy.
- This is a behavior-test strengthening slice only: no production owner, facade, bridge, field, package move, URI resolution path, or cache policy path changed. The concrete gain is stronger focused coverage for a previously undercovered provider boundary, reducing the risk that `PlaybackMediaSourceResolutionOwner` or a cache owner grows into a universal resolver.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain`. Current recheck: `EchoPlaybackService.java` 1413 lines, `private Playback*` fields 43, Service `fromPlaybackQueueManager(...)` count 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice changes only focused unit coverage and does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 97 - Precache cache data source boundary coverage (2026-07-03)
- Cache/precache boundary test slice: `PlaybackPrecacheManagerTest.failedCurrentLeadingRangeCleansActiveRangeForRetry` now asserts that current-track precache opens its cache data source through `PlaybackMediaCacheOperations.cacheDataSourceForTrack(track)`. This pairs with the provider-backed cache-operations/header tests and keeps cache data-source/header policy out of `EchoPlaybackService` and out of a broad media-source resolution owner.
- This is a behavior-test strengthening slice only: no production owner, facade, bridge, field, package move, URI resolution path, or cache policy path changed. The concrete gain is focused coverage for the `PlaybackPrecacheManager -> PlaybackMediaCacheOperations` cache-source boundary, reducing the chance that precache policy slips back into Service wiring.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --console=plain`, which also rechecked app Kotlin/Java compile tasks. Device smoke remains out of scope because this slice changes only focused unit coverage and docs, and does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 98 - Playback owner/interface audit after queue read and cache boundary slices (2026-07-03)
- Audit scope after NOTE 95-97: `StreamingPlaybackController` now reuses one queue snapshot per active pre-resolve event, `PlaybackMediaSourceProviderTest` covers provider-owned streaming/WebDAV headers, and `PlaybackPrecacheManagerTest` covers the `PlaybackPrecacheManager -> PlaybackMediaCacheOperations.cacheDataSourceForTrack(track)` cache-source boundary. These are test/read-boundary slices, not owner-count slices.
- Current queue/interface boundary: production playback code still has no `PlaybackQueueManager.QueueProvider` or `interface QueueProvider`; those strings only remain in architecture-contract negative assertions and unrelated recommendation queue-provider names. `PlaybackQueueManager.QueueStateSnapshot` still stores only `currentTrack`, `currentIndex`, and `queueSize`, with queue booleans derived. `PlaybackQueueStateOwner` remains the Java-facing read owner and keeps `queueStateSnapshot()` private.
- Current Service/cache/resolve boundary: `EchoPlaybackService.java` is 1318 lines, has 55 top-level `Playback...` field declarations, 24 top-level `Playback*Owner` field declarations, 5 short-lived queue-owner factory methods, 0 Service `fromPlaybackQueueManager(...)` calls, 0 Service direct `queueStateSnapshot(...)` calls, and 43 `Playback*Owner.java` files. `PlaybackMediaSourceResolutionOwner` has no production code hits; URI/media item/header resolution remains in `PlaybackMediaSourceProvider`, and cache/precache policy remains behind `PlaybackPrecacheManager` plus `PlaybackMediaCacheOperations`.
- Remaining risk and next candidates at this checkpoint: `MainActivityBase` direct `playbackService.queueSnapshot()` call sites should be narrowed without adding a facade (handled in NOTE 99). The four remaining `playbackQueueStateOwner::currentTrack` dependencies still feed distinct runtime-state, position, notification-artwork, and Wi-Fi-lock owners; do not collapse them into a single Service supplier unless that also removes a real constructor/interface dependency.
- Verification passed at T1 with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingPlaybackControllerTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.MainActivityArchitectureContractTest :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain`. Device smoke remains out of scope because this checkpoint is read-only/docs-only and does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 99 - MainActivity queue snapshot direct reads narrowed (2026-07-03)
- Queue/read boundary slice: `MainActivityBase` now routes heartbeat seed binding, streaming playback listener queue reads, queue ViewModel binding, queue rendering, and current streaming queue resolution through the existing `playbackQueueSnapshot()` helper instead of repeating `playbackService.queueSnapshot()` at each call site.
- This removes five Activity-to-Service queue snapshot read sites without adding an owner, facade, bridge, field, package move, or changing queue authority. The only remaining direct `playbackService.queueSnapshot()` in `MainActivityBase` is inside `playbackQueueSnapshot()`, so future callers must use the existing source boundary rather than adding new inline null-check suppliers.
- `MainActivityArchitectureContractTest` now asserts there is exactly one `playbackService.queueSnapshot()` occurrence in `MainActivityBase` and that the streaming playback listener uses `this::playbackQueueSnapshot`. Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also recompiled app Kotlin/Java after the Java method-reference changes. Device smoke remains out of scope because this slice changes only host wiring and architecture-contract coverage, not notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 100 - Queue state snapshot derived-read contract strengthened (2026-07-03)
- Queue/interface boundary test slice: `PlaybackQueueManagerTest` now directly covers that `QueueStateSnapshot` booleans are derived from only `currentTrack`, `currentIndex`, and `queueSize`. This protects the current narrow snapshot shape instead of adding stored booleans or widening the old provider-style read surface.
- The public API audit found `PlaybackQueueManager.currentIndex()` and `setCurrentIndex(...)` are already private implementation details; no production method was safe to delete in this slice because remaining public methods are consumed by existing queue command, navigation, completion, restore, persistence, mirrored-transition, preparation, or state owners. `MainActivityArchitectureContractTest` now guards the private index helper shape so those cursor methods do not return as public queue API.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice changes only focused unit/contract coverage and does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 101 - Playback queue/read owner-interface audit after NOTE 99-100 (2026-07-03)
- Audit scope after NOTE 99-100: Activity queue reads were narrowed to the existing `playbackQueueSnapshot()` helper, and `PlaybackQueueManager.QueueStateSnapshot` derived-read behavior plus private cursor helpers are now covered by focused tests/contracts. These two slices did not add owners, facades, bridges, fields, or package moves.
- Current metrics: `EchoPlaybackService.java` is 1318 lines, has 55 top-level `Playback...` field declarations, 24 top-level `Playback*Owner` field declarations, 0 Service `fromPlaybackQueueManager(...)` calls, 0 Service direct `queueStateSnapshot(...)` calls, 43 `Playback*Owner.java` files, 1 direct `MainActivityBase` `playbackService.queueSnapshot()` occurrence inside `playbackQueueSnapshot()`, and 4 `playbackQueueStateOwner::currentTrack` Service dependencies.
- Current boundary findings: `PlaybackQueueManager.currentIndex()` and `setCurrentIndex(...)` remain private implementation details; `QueueStateSnapshot` still stores only `currentTrack`, `currentIndex`, and `queueSize`; production playback code still has no `PlaybackQueueManager.QueueProvider`. The four remaining `playbackQueueStateOwner::currentTrack` dependencies feed feature/runtime owners across module boundaries (runtime gain, position persistence, notification artwork, and Wi-Fi lock policy), so collapsing them into a Service-level supplier would make Service a wiring store without reducing an owner/interface.
- Next safe candidates: inspect whether a feature-module state-provider helper can replace one remaining `currentTrackSupplier` without app-to-feature dependency inversion, or continue with resolver/cache behavior coverage. Defer notification/lyrics/shutdown/background behavior changes until the smoke table is stable.
- Verification for this audit uses the already-passed T1 command from NOTE 100: `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this checkpoint is read-only/docs-only and does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 102 - Wi-Fi lock current-track supplier removed from Service wiring (2026-07-03)
- Queue/state wiring slice: `PlaybackWifiLockManager` now receives the feature playback `PlaybackQueueManager` and reads `queueStateSnapshot().currentTrack` internally, instead of receiving `playbackQueueStateOwner::currentTrack` from `EchoPlaybackService`.
- This removes one Service current-track supplier chain without adding an owner, facade, bridge, package move, or moving streaming URI/media item/cache policy into Service. The remaining `playbackQueueStateOwner::currentTrack` Service dependencies are now 3 and still feed runtime state, position persistence, and notification artwork.
- `PlaybackWifiLockManagerTest` now builds current-track state through `PlaybackQueueManager.playQueue(...)`, so the focused behavior test exercises the same queue authority used at runtime. `MainActivityArchitectureContractTest` now requires the Service to pass `playbackQueueManager`, rejects the old Wi-Fi lock `playbackQueueStateOwner::currentTrack` wiring, and locks the remaining Service current-track supplier count at 3.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackWifiLockManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1413 lines, strict `private Playback*` field declarations 53, strict `private Playback*Owner` field declarations 22, Service `fromPlaybackQueueManager(...)` 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 103 - Position persistence current-track supplier removed from Service wiring (2026-07-03)
- Queue/state wiring slice: `PlaybackPositionManager.stateProviderFromPlaybackState(...)` now receives the feature playback `PlaybackQueueManager` and reads `queueStateSnapshot().currentTrack` internally, instead of receiving `playbackQueueStateOwner::currentTrack` from `EchoPlaybackService`.
- This removes one more Service current-track supplier chain without adding an owner, facade, bridge, package move, or moving position persistence back into Service. The remaining `playbackQueueStateOwner::currentTrack` Service dependencies are now 2 and still feed runtime replay-gain state plus notification artwork.
- `PlaybackPositionManagerTest` now builds current-track state through `PlaybackQueueManager.playQueue(...)`, so position persistence uses the same queue authority path as runtime playback. `MainActivityArchitectureContractTest` now requires the position state provider to receive `playbackQueueManager`, rejects the old position `playbackQueueStateOwner::currentTrack` wiring, and locks the remaining Service current-track supplier count at 2.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackPositionManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Current recheck: `EchoPlaybackService.java` 1413 lines, strict `private Playback*` field declarations 53, strict `private Playback*Owner` field declarations 22, Service `fromPlaybackQueueManager(...)` 0, Service `queueStateSnapshot()` direct suppliers 0, and `Playback*Owner.java` files 43. Device smoke remains out of scope because this slice does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 104 - Playback owner/interface audit after NOTE 102-103 (2026-07-03)
- Audit scope after NOTE 102-103: Wi-Fi lock streaming policy and position persistence both now read current-track state from `PlaybackQueueManager.queueStateSnapshot()` inside `feature:playback`, instead of receiving `playbackQueueStateOwner::currentTrack` from `EchoPlaybackService`. These slices did not add owners, facades, bridges, fields, package moves, URI/media item resolution paths, cache policy paths, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- Current metrics: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 53, strict `private Playback*Owner` field declarations are 22, Service `fromPlaybackQueueManager(...)` calls are 0, Service direct `queueStateSnapshot()` calls are 0, `Playback*Owner.java` files are 43, and Service `playbackQueueStateOwner::currentTrack` dependencies are down from 4 at NOTE 101 to 2.
- Current boundary findings: `PlaybackQueueManager.QueueStateSnapshot` still stores only `currentTrack`, `currentIndex`, and `queueSize`; production playback code still has no `PlaybackQueueManager.QueueProvider`; Service still does not call `playbackQueueManager.queueStateSnapshot()` directly. The two remaining `playbackQueueStateOwner::currentTrack` dependencies feed runtime replay-gain state and notification artwork refresh. Do not collapse them into a global Service supplier; next code slice should only continue if it removes a real constructor/interface dependency or strengthens focused behavior coverage.
- T1 verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackWifiLockManagerTest --tests app.yukine.playback.PlaybackPositionManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this checkpoint is read-only/docs-only and does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 105 - Playback queue snapshot interface audit after queue-state owner narrowing (2026-07-03)
- Audit scope after `822cabac4`: `PlaybackQueueStateOwner` now exposes one package-local `queueStateSnapshot()` read boundary plus `currentTrack()`, `queueSnapshot()`, and `upcomingTracksForPrecache(...)`; it no longer exposes `currentIndex()`, `queueSize()`, `isQueueEmpty()`, `hasMultipleTracks()`, or `isAtEndOfQueue()` as separate derived methods. Consumers that need derived booleans or counts read one `PlaybackQueueManager.QueueStateSnapshot` and derive from its source fields. This removed five Java-facing derived queue-state methods without adding an owner, facade, bridge, field, package move, or Service strategy branch.
- Stop/clear completion candidate status: `PlaybackQueueStopClearOwner.java` and `PlaybackQueueStopClearOwnerTest.java` are absent and guarded by `MainActivityArchitectureContractTest`; production has 0 `fromPlaybackQueueManager(...)` calls; `PlaybackQueueCompletionOwner` directly owns the remaining stop/clear, stop-at-end, and automatic-advance preparation calls into `PlaybackQueueManager`. There is no current stop/clear merge slice left that would reduce a field, factory, or `fromPlaybackQueueManager` call without inventing a new layer, so do not keep reopening this candidate mechanically.
- Current queue/API grouping: queue mutation commands remain in `PlaybackQueueMutationOwner`; navigation commands remain in `PlaybackQueueNavigationOwner`; completion preparation remains in `PlaybackQueueCompletionOwner`; persistence/restore remain in `PlaybackQueuePersistenceOwner` and `PlaybackQueueRestoreOwner`; read outputs remain `PlaybackQueueStateOwner` plus direct feature-module manager tests. `PlaybackQueueManager.currentIndex()` and `setCurrentIndex(...)` remain private; `QueueStateSnapshot` still stores only `currentTrack`, `currentIndex`, and `queueSize`; production playback code still has no `PlaybackQueueManager.QueueProvider`.
- Current metrics: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 53, strict `private Playback*Owner` field declarations are 22, Service `fromPlaybackQueueManager(...)` calls are 0, Service direct `queueStateSnapshot()` calls are 0, `Playback*Owner.java` files are 43, Service `playbackQueueStateOwner::currentTrack` dependencies remain 2, and `PlaybackQueueStateOwner` derived method count for `currentIndex/queueSize/isQueueEmpty/hasMultipleTracks/isAtEndOfQueue` is 0.
- Verification for the code slice passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueStateOwnerTest --tests app.yukine.playback.PlaybackCrossfadeStateOwnerTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.playback.PlaybackNotificationStateOwnerTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --tests app.yukine.playback.PlaybackQueueMirroredTransitionOwnerTest --tests app.yukine.playback.PlaybackStateSnapshotOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. This follow-up audit is docs-only; device smoke remains out of scope because it does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
- Next safe candidates: first, inspect the two remaining Service `playbackQueueStateOwner::currentTrack` constructor dependencies separately and only change one if it removes a constructor/interface dependency rather than adding a generic supplier; second, add resolver/cache behavior coverage if a concrete `PlaybackMediaSourceProvider` or `PlaybackPrecacheManager` gap is found; third, continue public queue API grouping only when a method can move from public to private or from Service wiring into an existing owner with focused tests.
## NOTE 106 - Playback owner/interface audit after cache fallback coverage (2026-07-03)
- Audit scope after `f7d645dc5`: `PlaybackPrecacheManagerTest.cacheStateReadFailureFallsBackToManagerOwnedCacheAttempt` now fixes the cache-state read failure behavior at the `PlaybackPrecacheManager -> PlaybackMediaCacheOperations` boundary. This is a behavior-test strengthening slice; it did not add an owner, facade, bridge, field, package move, Service strategy branch, URI/media item resolver path, or cache policy path.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1318 lines, strict `private Playback*` field declarations are 53, strict `private Playback*Owner` field declarations are 22, production `fromPlaybackQueueManager(...)` calls are 0, Service `playbackQueueStateOwner::currentTrack` dependencies remain 2, production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 44 by the recursive file metric, and production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Current queue/read boundary: `PlaybackQueueStateOwner.currentTrack()` remains as one package-local current-track convenience read. It is still consumed by multiple Java owners plus the two Service method references, so deleting it now would be a horizontal rewrite unless paired with a real constructor/interface reduction. Do not replace the remaining Service references with a generic `Supplier<Track>` field; that would turn `EchoPlaybackService` into a wiring store without reducing state authority.
- Remaining high-risk edge: the two Service `playbackQueueStateOwner::currentTrack` dependencies feed runtime replay-gain state and notification artwork. The runtime path may be a future candidate only if `PlaybackRuntimeStateManager` can read queue authority without cyclic construction or a broader interface. The notification artwork path stays deferred with P4 unless smoke coverage is stable.
- Verification for the added behavior slice passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --console=plain`. This audit is docs-only; device smoke remains out of scope because it does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 107 - Runtime replay-gain current-track supplier removed from Service wiring (2026-07-03)
- Queue/state wiring slice: `PlaybackRuntimeStateManager.stateProviderFromPlaybackState(...)` now receives a `Supplier<PlaybackQueueManager?>` and derives replay-gain current-track state from `queueStateSnapshot().currentTrack` inside `feature:playback`. `EchoPlaybackService` passes `() -> playbackQueueManager` instead of `playbackQueueStateOwner::currentTrack`, so runtime volume policy no longer depends on a Service-level current-track supplier.
- This removes one Service current-track supplier chain without adding an owner, facade, bridge, field, package move, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. The delayed supplier also avoids the earlier field-initialization risk where passing the `playbackQueueManager` field value directly would capture null before `onCreate` binding.
- `PlaybackRuntimeStateManagerTest` now builds replay-gain current-track state through `PlaybackQueueManager.playQueue(...)`, covering the same queue authority path used at runtime. `MainActivityArchitectureContractTest` requires the Service runtime state provider to pass `() -> playbackQueueManager`, rejects the old runtime `playbackQueueStateOwner::currentTrack` wiring, rejects `currentTrackSupplier: Supplier<Track?>?`, and locks Service `playbackQueueStateOwner::currentTrack` occurrences at 1.
- Current metrics after the slice: `EchoPlaybackService.java` is 1318 lines, strict `private Playback*` field declarations are 53, strict `private Playback*Owner` field declarations are 22, production `fromPlaybackQueueManager(...)` calls are 0, Service `playbackQueueStateOwner::currentTrack` dependencies are down from 2 to 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 44 by the recursive file metric. The remaining Service current-track supplier feeds notification artwork and stays deferred with P4 until smoke coverage is stable.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also recompiled app Kotlin/Java after the feature manager signature change. Device smoke remains out of scope because this slice does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior beyond the tested replay-gain queue-state source.
## NOTE 108 - Playback owner/interface audit after runtime and visualization cache slices (2026-07-03)
- Audit scope after NOTE 107 and `6b0cc0bdd`: runtime replay-gain state now reads current-track state from `PlaybackQueueManager.queueStateSnapshot()` inside `feature:playback`, and `PlaybackVisualizationCacheManagerTest.partiallyCachedVisualizationWindowResumesFromCachedBytes` covers partial-cache resume behavior through `PlaybackMediaCacheOperations.cachedBytesInRange(...)`. These slices did not add owners, facades, bridges, fields, package moves, URI/media item resolver paths, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1318 lines, strict `private Playback*` field declarations are 53, strict `private Playback*Owner` field declarations are 22, production `fromPlaybackQueueManager(...)` calls are 0, Service `playbackQueueStateOwner::currentTrack` dependencies are 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 44 by the recursive file metric. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Current boundary findings: queue authority remains in `PlaybackQueueManager`; `PlaybackQueueStateOwner.currentTrack()` remains a package-local Java-facing convenience read for existing owners, but Service no longer uses it for runtime replay gain. Cache policy remains in `PlaybackPrecacheManager` plus focused cache managers consuming `PlaybackMediaCacheOperations`; the visualization cache test now locks the partial cache resume offset and length so this policy does not drift back into Service.
- Remaining high-risk edge: the only Service `playbackQueueStateOwner::currentTrack` method reference feeds notification artwork. Leave that path alone until P4 smoke coverage is stable; replacing it with a generic `Supplier<Track>` field would make Service a wiring store without reducing state authority.
- T1 verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackRuntimeStateManagerTest --tests app.yukine.playback.PlaybackVisualizationCacheManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also rechecked app compile tasks as up-to-date. Device smoke remains out of scope because this checkpoint is docs-only and the covered slices did not change notification, background playback, lyrics, shutdown, or service lifecycle behavior.
## NOTE 109 - Playback owner/interface audit after visualization full-cache coverage (2026-07-03)
- Audit scope after `04aaf7ca0`: `PlaybackVisualizationCacheManagerTest.fullyCachedVisualizationWindowSkipsCacheWriterCreation` now covers the cache-owner full-hit path. This locks the rule that a fully cached visualization window only reads `PlaybackMediaCacheOperations.cachedBytesInRange(...)` and does not create a cache writer. The slice added no owner, facade, bridge, field, package move, URI/media item resolver path, cache policy path, Service strategy branch, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 43, strict `private Playback*Owner` field declarations are 19, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` / `playbackQueueStateOwner::currentTrack` wiring occurrences are 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 44 by the recursive file metric. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Queue/interface audit result: `MainActivityArchitectureContractTest` already guards against `QueueProvider`, `fromPlaybackQueueManager(...)`, public queue cursor helpers, direct Service `playbackQueueManager.queueStateSnapshot()` access, and widening `QueueStateSnapshot` beyond `currentTrack`, `currentIndex`, and `queueSize`. Adding more string-only contract assertions here would duplicate existing coverage; the next queue slice should only proceed if it removes a real public method, constructor dependency, supplier chain, state source, or Service strategy branch.
- Resolver/cache boundary audit result: URI/media item/header resolution remains in `PlaybackMediaSourceProvider`; cache/precache behavior remains behind `PlaybackPrecacheManager`, `PlaybackVisualizationCacheManager`, and `PlaybackMediaCacheOperations`. The only production `PlaybackMediaCacheOperations.fromMediaSourceProvider(...)` calls are the two explicit cache-owner bindings in `PlaybackPrecacheManager` and `PlaybackVisualizationCacheManager`.
- Verification for the added behavior slice passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackVisualizationCacheManagerTest --console=plain`. This audit is docs-only; device smoke remains out of scope because it does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 110 - Playback cache/precache boundary audit after unresolved and upcoming coverage (2026-07-03)
- Audit scope after `fe1e0bf30` and `fbeb3d7f7`: `PlaybackMediaCacheOperationsTest.providerBackedOperationsDoNotPrecacheUnresolvedStreamingPlaceholders` now fixes the boundary where provider-owned media identity cache keys do not automatically become precache keys, and `PlaybackPrecacheManagerTest.upcomingPrecacheSkipsTracksWithoutCachePolicyKey` now verifies that the submitted upcoming cache task opens the HTTP streaming candidate, not the local candidate without a cache policy key.
- These are behavior-test strengthening slices only. They added no owner, facade, bridge, field, package move, URI/media item resolver path, cache policy path, Service strategy branch, notification behavior, lyrics behavior, shutdown behavior, background playback behavior, or package structure change. The concrete gain is stronger coverage around the `PlaybackMediaSourceProvider -> PlaybackMediaCacheOperations -> PlaybackPrecacheManager` boundary so unresolved/local candidates do not drift into precache policy.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 43, strict `private Playback*Owner` field declarations are 19, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` / `playbackQueueStateOwner::currentTrack` wiring occurrences remain 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 44 by the recursive file metric. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Queue/API audit status is unchanged from NOTE 109: no safe public `PlaybackQueueManager` method deletion was found in this pass. `restorePlaybackQueue()`, `persistQueueState()`, `queueSnapshot()`, and `upcomingTracksForPrecache(...)` still have real restore/shutdown/UI/precache consumers, so removing them now would be a horizontal rewrite rather than an interface win.
- T1 verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaCacheOperationsTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this batch does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior outside the tested cache/precache decisions.
## NOTE 111 - Playback queue restore audit after filtered-index coverage (2026-07-03)
- Audit scope after `e822aeb02`: `PlaybackQueueManager.restorePlaybackQueue()` now clamps the restored current index after invalid queue entries are filtered, and `PlaybackQueueManagerTest.restorePlaybackQueueClampsCurrentIndexAfterFilteringInvalidTracks` covers the queue-authority behavior directly. This is a behavior-test strengthening slice; it added no owner, facade, bridge, field, package move, Service strategy branch, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 42, strict `private Playback*Owner` field declarations are 18, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` / `playbackQueueStateOwner::currentTrack` wiring occurrences remain 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Stop/clear candidate status: `PlaybackQueueStopClearOwner.java` and `PlaybackQueueStopClearOwnerTest.java` are still absent, Service has no `playbackQueueStopClearOwner` wiring, and `PlaybackQueueCompletionOwner` already owns stop/clear, stop-at-end, and automatic-advance preparation calls into `PlaybackQueueManager`. Do not reopen the stop/clear merge candidate unless a future diff can remove a real field, factory, interface method, supplier chain, or Service strategy branch.
- Queue/API audit status: `QueueStateSnapshot` still stores only `currentTrack`, `currentIndex`, and `queueSize`; `PlaybackQueueManager.currentIndex()` and `setCurrentIndex(...)` remain private implementation details; no safe public `PlaybackQueueManager` method deletion was found in this pass. The next queue slice should target a concrete consumer boundary, not another read-method shuffle.
- Verification for the restored-index slice passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain` and `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. This follow-up audit is docs-only; device smoke remains out of scope because it does not change notification, background playback, lyrics, shutdown, service lifecycle, or playback runtime behavior.
## NOTE 112 - Resolver/cache boundary audit after media identity and controller queue slices (2026-07-03)
- Audit scope after `bfba83e8e` and `2c766d847`: `PlaybackMediaSourceProviderTest.mirroredQueueReuseRequiresCacheKeyToMatchWhenBothArePresent` now fixes the MediaItem reuse rule that matching media id and resolved URI are not enough when both sides carry different cache keys, and `PlaybackMediaLibraryCallbackTest.controllerQueueForMediaItemsSkipsUnresolvedItemsAndKeepsResolvedOrder` / `controllerQueueForMediaItemsClampsStartIndexWhenSelectedItemDoesNotResolve` now cover controller MediaItem filtering plus filtered-queue start-index remapping.
- These slices strengthen resolver/queue behavior only. They added no owner, facade, bridge, field, package move, Service strategy branch, notification behavior, lyrics behavior, shutdown behavior, background playback behavior, or package structure change. URI, MediaItem, header, and media identity rules remain in `PlaybackMediaSourceProvider` / `PlaybackMediaLibraryCallback`; cache and precache policy still flow through `PlaybackPrecacheManager` plus `PlaybackMediaCacheOperations`.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 42, strict `private Playback*Owner` field declarations are 18, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` / `playbackQueueStateOwner::currentTrack` wiring occurrences remain 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: `PlaybackMediaSourceProvider` still owns `mediaItemIdentityMatchesForReuse(...)` and cache-key generation; `PlaybackMediaLibraryCallback` now resolves controller queues with a filtered index instead of leaking raw controller indexes downstream to `PlaybackQueueManager.playQueue(...)`; `PlaybackPrecacheManager.fromMediaSourceProvider(...)` remains the explicit cache policy bridge and should not be widened into a resolver facade.
- T1 verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain`, `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaLibraryCallbackTest --console=plain`, and `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this batch does not change notification, background playback, lyrics, shutdown, service lifecycle, or runtime foreground behavior.
## NOTE 113 - MediaLibrary callback resolution audit after shared-index slices (2026-07-03)
- Audit scope after `86c923703` and `ebd2a5ad9`: `PlaybackMediaLibraryCallback` now routes `onSetMediaItems(...)`, `controllerQueueForMediaItems(...)`, and `onAddMediaItems(...)` through the shared private `resolvedControllerTracks(...)` MediaItem-to-Track filtering path. `mediaItemsWithStartPosition(...)` and `controllerQueueForMediaItems(...)` both consume the same filtered start-index mapping, and `tracksForMediaItems(...)` now reuses that same path for append operations instead of keeping a second filtering loop.
- This is a boundary-tightening slice, not a new-owner slice. It reduced one duplicate callback interpretation of controller MediaItems and kept URI, MediaItem, media identity, header, cache-key, and cache policy decisions inside the existing `PlaybackMediaLibraryCallback`, `PlaybackMediaSourceProvider`, `PlaybackPrecacheManager`, and `PlaybackMediaCacheOperations` owners. It added no owner, facade, bridge, field, package move, Service strategy branch, notification behavior, lyrics behavior, shutdown behavior, background playback behavior, or package structure change.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 42, strict `private Playback*Owner` field declarations are 18, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` / `playbackQueueStateOwner::currentTrack` wiring occurrences remain 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Drift guard for the next slices: owner count is already high, so do not add or keep a `Playback*Owner` unless it removes a real state source, interface method, supplier chain, forwarding hop, or Service strategy decision, or adds behavior coverage. QueueProvider-era risk is not resolved by moving call sites; keep auditing queue/snapshot methods as real external inputs, snapshot-derived reads, or migration residue before making another queue cut. Also keep watching Service wiring density: line count alone is not enough if fields, factories, suppliers, or initialization blocks grow.
- Verification for the two behavior slices passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaLibraryCallbackTest --console=plain`; `86c923703` also passed `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. This audit is docs-only; device smoke remains out of scope because it does not change notification, background playback, lyrics, shutdown, service lifecycle, or runtime foreground behavior.
## NOTE 114 - Queue/precache owner audit after defensive snapshot and diagnostics wiring slices (2026-07-03)
- Audit scope after `801609f5e` and `eb6459ece`: `PlaybackQueueManagerTest.upcomingTracksForPrecacheIsOwnedByQueueManagerAndDefensive` now fixes the queue-to-precache rule that upcoming tracks are a QueueManager-owned defensive read, and `PlaybackPrecacheStateOwner` now receives the existing `PlaybackStreamingDiagnostics` instance directly instead of a `Supplier<PlaybackStreamingDiagnostics>`.
- Concrete win: one Service/precache supplier chain was removed from `EchoPlaybackService` wiring (`() -> streamingDiagnostics`), while the queue/precache read boundary gained focused behavior coverage. The cache/precache owner still gets current track and upcoming tracks through `PlaybackQueueStateOwner` / `PlaybackQueueManager`; it does not own a mutable queue view or derive queue position itself.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 42, strict `private Playback*Owner` field declarations are 18, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` / `playbackQueueStateOwner::currentTrack` wiring occurrences remain 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: `persistQueueState()` remains a tempting public API candidate but is tied to shutdown lifecycle persistence, so leave it alone until P5/shutdown smoke is in scope. `PlaybackQueueStateOwner.currentTrack()` still has multiple Java owner consumers; deleting it now would be horizontal churn unless paired with a real constructor/interface reduction. The remaining Service `playbackQueueStateOwner::currentTrack` occurrence feeds notification artwork and stays deferred with P4.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain` for the defensive upcoming-tracks slice and `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` for the diagnostics supplier slice. Device smoke remains out of scope because this batch does not change notification, background playback, lyrics, shutdown, service lifecycle, or runtime foreground behavior.
## NOTE 115 - Service supplier wiring audit after diagnostics recorder and widget context slices (2026-07-03)
- Audit scope after `8e340e31a` and `91174f9d0`: `PlaybackStreamingDiagnosticsRecorderOwner` now receives the existing `PlaybackStreamingDiagnostics` instance directly instead of `Supplier<PlaybackStreamingDiagnostics>`, and `PlaybackStatePublisherWidgetOwner` now receives the stable Service `Context` directly through `fromContext(this)` instead of `fromContextProvider(() -> this)`.
- Concrete win: two more Service wiring supplier chains were removed without adding owners, facades, bridges, package moves, queue state sources, URI/media item resolver paths, cache policy paths, notification action behavior, lyrics behavior, shutdown behavior, or background playback behavior. The remaining suppliers in this area either read mutable runtime state such as `player`, belong to notification/artwork P4 paths, or are shutdown lifecycle P5 paths and should not be flattened just for symmetry.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1413 lines, strict `private Playback*` field declarations are 42, strict `private Playback*Owner` field declarations are 18, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` / `playbackQueueStateOwner::currentTrack` wiring occurrences remain 1, and production `Playback*Owner` files under `app/src/main/java` plus `feature/playback/src/main/java` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: do not continue flattening the `PlaybackNotificationCommandOwner.notificationUpdaterFromNotificationManagerSupplier(() -> playbackNotificationManager)` or `PlaybackNotificationArtworkManager` current-track supplier until P4 smoke is stable. Do not flatten shutdown resource suppliers until P5. The safe near-term lane is still focused queue/state/API tests or low-risk direct-object wiring where the object is already final/stable.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackStreamingDiagnosticsRecorderOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` and `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackStatePublisherWidgetOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this batch does not change notification, background playback, lyrics, shutdown, service lifecycle, or runtime foreground behavior.

## NOTE 116 - Progress wiring and queue-preparation audit after local-owner and fallback coverage (2026-07-03)
- Audit scope after `3bede5486` and `c5dcee674`: `EchoPlaybackService` now keeps `PlaybackProgressUpdateCommandOwner` as an `onCreate` local dependency of `PlaybackProgressUpdateManager` instead of a long-lived Service field, and `PlaybackCurrentTrackPreparationQueueOwnerTest.queuePreparationSkipsMirroredSourcesWhenAnyQueuedTrackLacksPlayableUri` fixes the mirrored-queue fallback rule when any queued track lacks a playable URI.
- Concrete wins: one Service `private Playback*` field was removed without adding an owner, facade, bridge, package move, queue state source, URI/media item resolver path, cache policy path, notification action behavior, lyrics behavior, shutdown behavior, or background playback behavior. The queue-preparation fallback test strengthens the existing owner boundary before any future `prepareMirroredQueue(...)` cleanup, so batch media-source resolution is not requested for queues that must fall back to single-track preparation.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1317 lines, strict `private Playback*` field declarations are 42, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, and `Playback*Owner.java` files under `app/src/main/java/app/yukine/playback` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: the old stop/clear candidate remains closed because `PlaybackQueueStopClearOwner` is absent and `PlaybackQueueCompletionOwner` already owns stop/clear, stop-at-end, and automatic-advance preparation calls. `PlaybackCurrentTrackPreparationOwner` and `PlaybackCurrentTrackPreparationQueueOwner` should not be mechanically merged; doing so would risk growing a broad preparation facade unless a future slice also removes a real field, interface method, supplier chain, or Service strategy branch.
- T1 verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Focused coverage for the batch also passed through `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackProgressUpdateCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` and `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackCurrentTrackPreparationQueueOwnerTest --console=plain`. Device smoke remains out of scope because this batch does not change notification, background playback, lyrics, shutdown, service lifecycle, or runtime foreground behavior.
## NOTE 117 - Queue snapshot and resolver invalid-URI audit after focused boundary coverage (2026-07-03)
- Audit scope after `ec05e4ba7` and the current resolver test slice: `PlaybackQueueManagerTest.queueStateSnapshotOwnsSourceStateAndDerivedReads` fixes the queue snapshot rule that `currentTrack`, `currentIndex`, and `queueSize` are the source state while empty/current/multiple/end booleans remain derived reads, and `PlaybackMediaSourceProviderTest.prepareTrackForPlaybackReturnsGenericErrorForLocalTrackWithoutPlayableUri` fixes the invalid local-URI preparation rule inside the media source provider.
- Concrete wins: two focused behavior tests now protect queue/state and URI-resolution boundaries without adding an owner, facade, bridge, field, package move, Service strategy branch, notification behavior, lyrics behavior, shutdown behavior, background playback behavior, or cache policy path. The resolver slice keeps invalid URI handling in `PlaybackMediaSourceProvider.prepareTrackForPlayback(...)` instead of letting `EchoPlaybackService` or a new resolution facade own it.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1317 lines, strict `private Playback*` field declarations are 42, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, and `Playback*Owner.java` files under `app/src/main/java/app/yukine/playback` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: the old stop/clear candidate remains closed; `PlaybackQueueCompletionOwner` owns stop/clear preparation and there is no `PlaybackQueueStopClearOwner` to merge. Service no longer has direct queue snapshot calls, so the next queue slice should only proceed if it removes a real public API, supplier chain, constructor dependency, or strengthens behavior coverage. Resolver/cache work should continue inside `PlaybackMediaSourceProvider`, `PlaybackMediaCacheOperations`, and `PlaybackPrecacheManager`; do not add a broad resolution owner for invalid URI, MediaItem, header, and cache-key policy.
- T1 verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Focused coverage for the resolver slice also passed via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain`. Device smoke remains out of scope because this batch does not change notification, background playback, lyrics, shutdown, service lifecycle, or runtime foreground behavior.
## NOTE 118 - Queue-state interface audit after local media reuse and derived-read removal (2026-07-03)
- Audit scope after `415624ead` and the current queue-state interface slice: `PlaybackMediaSourceProviderTest.providerMatchesLocalMediaItemForTrackWithoutCacheKey` fixes the local MediaItem reuse rule that local tracks with no cache key still match by media id and URI, and `PlaybackQueueStateOwner` no longer exposes `isQueueEmpty()`, `hasMultipleTracks()`, or `isAtEndOfQueue()` wrapper methods.
- Concrete wins: three package-local derived read methods were removed from `PlaybackQueueStateOwner`; consumers that need queue booleans now read one `PlaybackQueueManager.QueueStateSnapshot` and use its derived properties directly. This narrows the Java owner interface without adding an owner, facade, bridge, field, package move, Service strategy branch, queue state source, URI/media item resolver path, cache policy path, notification action behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1317 lines, strict `private Playback*` field declarations are 42, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, and `Playback*Owner.java` files under `app/src/main/java/app/yukine/playback` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: `PlaybackQueueStateOwner.currentTrack()` remains because it is still a source-state convenience used by several Java owners; deleting it now would be horizontal churn unless paired with a real constructor/interface reduction. `PlaybackQueueStateOwner.queueSnapshot()` and `upcomingTracksForPrecache(...)` still represent list/precache reads with real consumers. The remaining safe queue direction is to remove a public manager method only when its consumer can move to an existing owner without widening Service wiring.
- Focused verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueStateOwnerTest --tests app.yukine.playback.PlaybackCrossfadeStateOwnerTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.playback.PlaybackNotificationStateOwnerTest --tests app.yukine.playback.PlaybackQueueMirroredTransitionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. The resolver focused test passed via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain`. Device smoke remains out of scope because this batch does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or runtime foreground behavior.
## NOTE 119 - Wi-Fi lock queue-state supplier audit after cache boundary coverage (2026-07-03)
- Audit scope after `6f79a731f` and the current Wi-Fi lock wiring slice: `PlaybackMediaCacheOperationsTest.providerBackedOperationsReadCommittedCacheBytesWithSafeOffset` fixes committed-cache span reads and safe negative-offset handling inside existing cache operations, while `PlaybackWifiLockManager` now reads the current track from `PlaybackQueueManager.queueStateSnapshot()` instead of receiving `playbackQueueStateOwner::currentTrack` from `EchoPlaybackService`.
- Concrete win: one more Service current-track supplier chain was removed without adding an owner, facade, bridge, field, package move, queue state source, URI/media item resolver path, cache policy path, notification action behavior, lyrics behavior, shutdown behavior, or background playback behavior. The Wi-Fi lock still owns only lock acquire/release policy; queue authority stays in `PlaybackQueueManager`.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1412 lines, strict `private Playback*` field declarations are 47, strict `private Playback*Owner` field declarations are 23, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, remaining Service `playbackQueueStateOwner::currentTrack` supplier occurrences are 2, and `Playback*Owner.java` files under `app/src/main/java/app/yukine/playback` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: the two remaining Service current-track suppliers feed position persistence and notification artwork. Position persistence should be examined separately because it may be movable into `PlaybackPositionManager` with queue snapshot coverage; notification artwork stays deferred with P4 until notification/background smoke is stable. Do not replace the remaining suppliers with a global Service snapshot facade.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackWifiLockManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. The cache boundary focused test passed earlier via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaCacheOperationsTest --console=plain`. Device smoke remains out of scope because this batch does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 120 - Position persistence queue-state supplier audit after feature T1 (2026-07-03)
- Audit scope after the current position persistence wiring slice: `PlaybackPositionManager.stateProviderFromPlaybackState(...)` now receives `PlaybackQueueManager` directly and reads the current track from `queueStateSnapshot().currentTrack`; `EchoPlaybackService` no longer passes `playbackQueueStateOwner::currentTrack` into the position manager state provider.
- Concrete win: another Service current-track supplier chain was removed without adding an owner, facade, bridge, field, package move, queue state source, URI/media item resolver path, cache policy path, notification action behavior, lyrics behavior, shutdown behavior, or background playback behavior. Position persistence continues to live in `PlaybackPositionManager`, while queue/current-track authority remains in `PlaybackQueueManager`.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1412 lines, strict `private Playback*` field declarations are 47, strict `private Playback*Owner` field declarations are 23, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, remaining Service `playbackQueueStateOwner::currentTrack` supplier occurrences are 1, and `Playback*Owner.java` files under `app/src/main/java/app/yukine/playback` are 43. Production code still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits.
- Boundary findings: the single remaining Service current-track supplier feeds `PlaybackNotificationArtworkManager`; leave it deferred with P4 notification/background smoke instead of wrapping it in a global snapshot facade. The safe next queue/API lane is a read-only `PlaybackQueueManager` public API grouping audit or focused behavior coverage for a real queue command.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackPositionManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` and T1 `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this batch does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 121 - QueueManager public API grouping and mutation behavior audit (2026-07-03)
- Audit scope after the current queue mutation behavior slice: `PlaybackQueueManager` public surface groups into command APIs (`playQueue`, append/play-first/skip/move/remove/retain/replace, completion preparation, mirrored reuse/transition), persistence and restore APIs (`persistQueueState`, `restorePlaybackQueue`, `restoreLastPlayback`, restore-enabled setting), and read APIs (`queueSnapshot`, `queueStateSnapshot`, `upcomingTracksForPrecache`). The read APIs still have real `PlaybackQueueStateOwner` / precache consumers; they are not migration residue to delete blindly.
- Concrete win: `PlaybackQueueManagerTest.removeTracksBeforeCurrentKeepsCurrentTrackAuthorityInQueueManager` now fixes the queue mutation rule that removing tracks before the current item preserves the same current track identity while normalizing `currentIndex` and persisted queue state. This strengthens the queue/current-track single-authority boundary without adding an owner, facade, bridge, field, package move, Service supplier, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- Boundary findings: `retainTracksById(...)` and `removeTracksById(...)` are still command APIs, not simple derived reads, because they can request stop-and-clear when the queue becomes empty and can trigger prepare/publish side effects. `queueSnapshot()` and `upcomingTracksForPrecache(...)` remain externally consumed through `PlaybackQueueStateOwner`; shrinking them requires a real consumer-path reduction, not a visibility-only edit.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 122 - Owner and Service wiring drift guard after checkpoint audit (2026-07-03)
- Audit scope after the clean checkpoint: `EchoPlaybackService.java` is 1317 lines by the current working tree, production `Playback*Owner.java` files under `app/src/main/java/app/yukine/playback` remain 43, the recursive production owner inventory including `feature/playback` is 44, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot()` mentions remain 0, and the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork.
- Contract hardening: `MainActivityArchitectureContractTest.playbackOwnerInventoryAndServiceWiringDoNotGrowWithoutAudit` already guards app playback owner count, forbidden resolver/facade/cache-policy files, and resolver/cache ownership. It now also caps Service `private Playback*` wiring declarations including `private final` initialized fields at 54, and caps Service `private Playback*Owner` wiring declarations including final initialized fields at 23. This closes the prior audit gap where the older helper did not count final initialized playback dependencies.
- Boundary findings: no new production `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider` hits were found. URI/media item/header resolution remains in `PlaybackMediaSourceProvider`; cache/precache policy remains behind `PlaybackMediaCacheOperations`, `PlaybackPrecacheManager`, and the existing visualization cache owner. The next code slice should still prove a real reduction or behavior win rather than adding another owner.
- Verification scope for this note is the architecture contract only because production playback behavior is unchanged. Device smoke remains deferred; this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 123 - WebDAV cache operations boundary coverage (2026-07-03)
- Audit scope after the current feature-only test slice: `PlaybackMediaCacheOperationsTest.providerBackedOperationsPrecacheWebDavHttpTracksWithProviderHeaders` now covers the WebDAV path where an HTTP WebDAV track receives a provider-owned `webdav:<sourceId>:...` cache key and both streaming header-store headers plus WebDAV Basic Authorization headers through `PlaybackMediaSourceProvider`.
- Concrete win: this strengthens the existing `PlaybackMediaSourceProvider -> PlaybackMediaCacheOperations -> PlaybackPrecacheManager` boundary without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. It specifically prevents a future simplification that treats cache operations as streaming-only and loses WebDAV auth/cache-key behavior.
- Current metrics from the working tree remain: `EchoPlaybackService.java` is 1317 lines, strict non-final `private Playback*` field declarations are 41, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork, app playback owner files remain 43, and recursive app+feature playback owner files remain 44.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.manager.PlaybackMediaCacheOperationsTest --console=plain`. Device smoke remains out of scope because this slice is feature test coverage only and does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 124 - Queue completion boundary localized out of Service fields (2026-07-03)
- Audit scope after the current Service wiring slice: `EchoPlaybackService` no longer keeps `playbackQueueCompletionBoundary` as a top-level final field. The `PlaybackQueueCompletionOwner.CompletionBoundary` implementation is now local to `playbackQueueCompletionOwner()`, which is already a short-lived factory for the completion owner.
- Concrete win: one `EchoPlaybackService` `private Playback*` wiring field and one `private Playback*Owner` wiring field were removed without adding an owner, facade, bridge, package move, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Completion behavior still runs through `PlaybackQueueCompletionOwner`; the Service only supplies boundary callbacks.
- Contract update: `MainActivityArchitectureContractTest.playbackOwnerInventoryAndServiceWiringDoNotGrowWithoutAudit` now caps Service `private Playback*` declarations including final initialized fields at 53 and `private Playback*Owner` declarations including final initialized fields at 22. The older strict non-final field metric remains 41.
- Current metrics from the working tree: `EchoPlaybackService.java` is 1315 lines, strict non-final `private Playback*` field declarations are 41, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork, app playback owner files remain 43, and recursive app+feature playback owner files remain 44.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCompletionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also recompiled app Kotlin and Java after the Service change. Device smoke remains out of scope because this does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 125 - Playback T1 verification after cache and completion wiring slices (2026-07-03)
- Batch scope: NOTE 123 strengthened the WebDAV cache operations boundary at `PlaybackMediaSourceProvider -> PlaybackMediaCacheOperations -> PlaybackPrecacheManager`; NOTE 124 removed the Service-level `playbackQueueCompletionBoundary` field while keeping completion behavior in `PlaybackQueueCompletionOwner`.
- T1 verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`.
- Current audit metrics after T1 remain: `EchoPlaybackService.java` is 1315 lines, strict non-final `private Playback*` field declarations are 41, Service `private Playback*` declarations including final initialized fields are capped by contract at 53, Service `private Playback*Owner` declarations including final initialized fields are capped by contract at 22, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot()` mentions are 0, the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork, app playback owner files remain 43, and recursive app+feature playback owner files remain 44.
- Batch result: one behavior boundary test was added and one Service wiring field/supplier object was localized out of the top-level Service field set. No owner, facade, bridge, package move, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior was added.
- Next safe lane: continue only with slices that remove another real Service wiring dependency, shrink a queue/state API, or add behavior coverage for a concrete resolver/cache edge. Keep the remaining notification artwork current-track supplier deferred to P4 smoke coverage.
## NOTE 126 - Precache range policy boundary coverage (2026-07-03)
- Audit scope after the current test-only slice: `PlaybackPrecacheManagerTest` now verifies that the current-leading precache path reads committed cache state through the existing `PlaybackMediaCacheOperations` boundary using the provider-owned precache cache key, position `0`, and `PlaybackPrecacheManager.PRECACHE_BYTES` length.
- Concrete win: this strengthens the existing cache/precache owner boundary without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. It specifically protects the rule that cache range policy belongs in `PlaybackPrecacheManager` and cache operations remain behind `PlaybackMediaCacheOperations`, not `EchoPlaybackService` or a new resolution facade.
- Production metrics and architecture caps are intentionally unchanged because this is a focused behavior-test slice. Do not treat this as owner shrink progress; it is boundary coverage that makes the next cache or resolver cleanup safer.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 127 - Queue mutation clear uses derived queue state (2026-07-03)
- Audit scope after the current queue-state slice: `PlaybackQueueMutationOwner.clearQueue()` now checks `playbackQueueManager.queueStateSnapshot().isQueueEmpty()` instead of reading `playbackQueueManager.queueSnapshot().isEmpty()`. The owner still delegates real queue mutations to `PlaybackQueueManager`; it no longer reads the full queue list for a boolean empty check.
- Concrete win: one broad queue list snapshot read in the mutation owner was replaced with an existing derived queue-state read, without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. `MainActivityArchitectureContractTest` now requires this narrower read and blocks the old `queueSnapshot().isEmpty()` pattern from returning in the mutation owner.
- Current audit metrics from the working tree: `EchoPlaybackService.java` is 1410 lines, strict non-final `private Playback*` field declarations are 41, single-line Service `private Playback*` declarations including final initialized fields are 43, single-line Service `private Playback*Owner` declarations including final initialized fields are 17, production `fromPlaybackQueueManager(...)` calls are 0, Service direct `queueStateSnapshot()` mentions are 0, the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork, and recursive app+feature playback owner files remain 44.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMutationOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` and T1 `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 128 - Provider cache data source header boundary coverage (2026-07-03)
- Audit scope after the current feature-only resolver/cache slice: `PlaybackMediaSourceProviderTest.cacheDataSourceForTrackUsesProviderOwnedHeaders` now exercises `PlaybackMediaSourceProvider.cacheDataSourceForTrack(...)` through a local HTTP socket and proves the provider-owned streaming headers are applied to the upstream request while the cache key still comes from `PlaybackMediaSourceProvider.cacheKeyForTrack(...)`.
- Concrete win: this strengthens the `PlaybackMediaSourceProvider -> CacheDataSource` boundary without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. It specifically protects the rule that URI/header/cache data-source construction stays in `PlaybackMediaSourceProvider`, not `EchoPlaybackService` or a new resolution facade.
- Production metrics and architecture caps are intentionally unchanged because this is focused feature test coverage. A first attempt using `com.sun.net.httpserver.HttpServer` failed to compile in the Android unit-test classpath; the final test uses `ServerSocket` and passed.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 129 - Favorite command reads queue snapshot directly (2026-07-03)
- Audit scope after the current queue-state forwarding slice: `PlaybackFavoriteCommandOwner.toggleCurrentFavorite(...)` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` directly instead of going through `PlaybackQueueStateOwner.currentTrack()`.
- Concrete win: one non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Favorite command behavior still depends on the queue authority snapshot from `PlaybackQueueManager`.
- Current audit metrics from the working tree remain: `EchoPlaybackService.java` is 1410 lines, strict non-final `private Playback*` field declarations are 41, single-line Service `private Playback*` declarations including final initialized fields are 43, production `fromPlaybackQueueManager(...)` calls are 0, Service direct `queueStateSnapshot()` mentions are 0, the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork, and recursive app+feature playback owner files remain 44.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackFavoriteCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 130 - Queue command owner reads queue snapshot directly after T1 (2026-07-03)
- Audit scope after the current queue command slice: `PlaybackQueueCommandOwner.currentTrack()` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` directly instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`.
- Concrete win: a second non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. The queue command path still uses the single queue authority snapshot from `PlaybackQueueManager`.
- Current audit metrics from the working tree remain: `EchoPlaybackService.java` is 1410 lines, strict non-final `private Playback*` field declarations are 41, single-line Service `private Playback*` declarations including final initialized fields are 43, production `fromPlaybackQueueManager(...)` calls are 0, Service direct `queueStateSnapshot()` mentions are 0, the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork, and recursive app+feature playback owner files remain 44.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain` and T1 `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this batch does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 131 - Error recovery command reads queue snapshot directly (2026-07-03)
- Audit scope after the current queue-state forwarding slice: `PlaybackErrorRecoveryCommandOwner.currentTrack()` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` directly instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`.
- Concrete win: a third non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Error recovery already used the same queue snapshot for failed-track skip policy, so this keeps both reads on the single `PlaybackQueueManager` queue authority snapshot.
- Current audit metrics from the working tree remain: `EchoPlaybackService.java` is 1410 lines, strict non-final `private Playback*` field declarations are 41, single-line Service `private Playback*` declarations including final initialized fields are 43, production `fromPlaybackQueueManager(...)` calls are 0, Service direct `queueStateSnapshot()` mentions are 0, the only Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork, and recursive app+feature playback owner files remain 44.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackErrorRecoveryCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 132 - Precache state reads queue snapshot directly after owner audit (2026-07-03)
- Owner/interface audit before this slice: `PlaybackQueueManager.QueueProvider` remains absent; `QueueStateSnapshot` still keeps only `currentTrack`, `currentIndex`, and `queueSize` as source state while deriving queue booleans; production `fromPlaybackQueueManager(...)` calls are 0; Service direct `queueStateSnapshot()` mentions are 0; the only Service current-track supplier remains the notification artwork supplier and is deferred with P4.
- Audit metrics from the working tree: `EchoPlaybackService.java` is 1410 lines, strict non-final `private Playback*` field declarations are 47, single-line Service `private Playback*` declarations including final initialized fields are 58, single-line Service `private Playback*Owner` declarations including final initialized fields are 27, and app/feature playback owner source files are 44.
- Audit scope after the current queue-state forwarding slice: `PlaybackPrecacheStateOwner.currentTrack()` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` directly instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`. This does not move cache policy out of `PlaybackPrecacheManager`; it only shortens the state-provider read path for the existing precache state owner.
- Concrete win: a fourth non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Focused coverage remains `PlaybackPrecacheStateOwnerTest`, with `MainActivityArchitectureContractTest` blocking the old forwarding call from returning.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, T1 `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, and T2 `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 133 - Play history action reads queue snapshot directly (2026-07-03)
- Audit scope after the current queue-state forwarding slice: `PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction(...)` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` via a local `QueueStateSnapshot` before calling `recordIfPlaybackStarted(...)`, instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`.
- Concrete win: a fifth non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. The existing history behavior remains protected by `PlaybackPlayHistoryRecorderTest.recordIfPlaybackStartedActionUsesRecorderAndLatestState`.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPlayHistoryRecorderTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 134 - Visualization cache state reads queue snapshot directly (2026-07-03)
- Owner/interface audit before this slice: `EchoPlaybackService.java` is 1410 lines, strict non-final `private Playback*` field declarations are 47, single-line Service `private Playback*` declarations including final initialized fields are 58, single-line Service `private Playback*Owner` declarations including final initialized fields are 27, production `fromPlaybackQueueManager(...)` calls are 0, Service direct `queueStateSnapshot()` mentions are 0, the only Service current-track supplier remains notification artwork, and app/feature playback owner source files remain 44.
- Audit scope after the current queue-state forwarding slice: `PlaybackVisualizationCacheStateOwner.currentTrack()` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` via a local `QueueStateSnapshot`, instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`. This keeps visualization cache ownership in the existing cache manager/state owner and does not add a snapshot facade or Service supplier.
- Concrete win: a sixth non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Focused coverage remains `PlaybackVisualizationCacheStateOwnerTest`, with `MainActivityArchitectureContractTest` blocking the old forwarding call from returning.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackVisualizationCacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, T1 `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, and T2 `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 135 - Session command reads queue snapshot directly (2026-07-03)
- Audit scope after the current queue-state forwarding slice: `PlaybackSessionCommandOwner.currentTrack()` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` via a local `QueueStateSnapshot`, instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`. This keeps MediaSession command delegation in the existing command owner and does not change session lifecycle, notification artwork, or Service wiring.
- Concrete win: a seventh non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Focused coverage remains `PlaybackSessionCommandOwnerTest`, including missing queue state and missing queue manager cases.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackSessionCommandOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 136 - Mirrored transition reads queue snapshot directly (2026-07-03)
- Audit scope after the current queue-state forwarding slice: `PlaybackQueueMirroredTransitionOwner.applyMirroredTransitionReason(...)` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` via a local `QueueStateSnapshot` before returning the transition current track, instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`. This keeps mirrored transition decisions in the existing owner and matches the owner’s existing `canApplyMirroredTransition()` snapshot read.
- Concrete win: an eighth non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Focused coverage remains `PlaybackQueueMirroredTransitionOwnerTest`, including transition current-track results and missing queue-state behavior.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMirroredTransitionOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, T1 `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, and T2 `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 137 - Mirrored player reads queue snapshot directly (2026-07-03)
- Audit scope after the current queue-state forwarding slice: `PlaybackQueueMirroredPlayerOwner.currentTrack()` now reads `PlaybackQueueStateOwner.queueStateSnapshot().getCurrentTrack()` via a local `QueueStateSnapshot`, instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`. This keeps mirrored player seek handling in the existing owner and does not add another queue/current-track state source.
- Concrete win: a ninth non-notification current-track forwarding chain was shortened without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. Focused coverage remains `PlaybackQueueMirroredPlayerOwnerTest`, including seek-to waveform reset, player seek, play-when-ready, and failure behavior.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueMirroredPlayerOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 138 - Playback owner/interface audit after current-track forwarding slices (2026-07-03)
- Audit scope after NOTE 135-137: `PlaybackSessionCommandOwner`, `PlaybackQueueMirroredTransitionOwner`, and `PlaybackQueueMirroredPlayerOwner` now read the queue authority snapshot directly through `PlaybackQueueStateOwner.queueStateSnapshot()` instead of forwarding through `PlaybackQueueStateOwner.currentTrack()`. No new owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior was added.
- Current metrics from the clean working tree: `EchoPlaybackService.java` is 1410 lines, strict `private Playback*` field declarations are 47, strict `private Playback*Owner` declarations are 23, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot(...)` mentions are 0, and production `Playback*Owner.java` files remain 43.
- Boundary findings: production `queueStateOwner.currentTrack()` forwarding remains only in `PlaybackLyricsStateOwner` and `PlaybackNotificationStateOwner`, plus the single Service `playbackQueueStateOwner::currentTrack` supplier feeding notification artwork. These are P4 notification/lyrics surfaces and should stay deferred until the smoke table is stable; changing them now would not be a low-risk queue/interface slice. The stop/clear candidate is already closed: `PlaybackQueueStopClearOwner` and its test file are absent, and `PlaybackQueueCompletionOwner` has no `fromPlaybackQueueManager(...)` factory. Resolver/cache boundaries are already guarded by `MainActivityArchitectureContractTest`, `PlaybackMediaSourceProviderTest`, `PlaybackMediaCacheOperationsTest`, and `PlaybackPrecacheManagerTest`; production still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider`.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this checkpoint is docs/audit-only and does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 139 - WebDAV media item cache-key boundary coverage (2026-07-03)
- Resolver/cache behavior slice: `PlaybackMediaSourceProviderTest.providerMediaItemForWebDavTrackUsesOwnedCacheKeyRule` now covers the provider-owned WebDAV MediaItem rule directly. A WebDAV track keeps its resolved URI, uses the track id as `mediaId`, and uses the WebDAV `dataPath` as the MediaItem custom cache key.
- Concrete win: stronger focused coverage for the `PlaybackMediaSourceProvider` URI/MediaItem/cache-key boundary without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. This complements the existing local, streaming, unresolved streaming, WebDAV header, and `PlaybackMediaCacheOperations` tests so WebDAV media identity does not drift into `EchoPlaybackService` or a new resolution facade.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --console=plain`. Device smoke remains out of scope because this slice changes only focused unit coverage and docs, not notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 140 - Visualization cache consumes provider cache key boundary (2026-07-03)
- Resolver/cache behavior slice: `PlaybackVisualizationCacheManagerTest.visualizationCacheDataSpecUsesMediaCacheOperationsCacheKey` now verifies that visualization cache writes use the cache key returned by `PlaybackMediaCacheOperations.cacheKeyForPrecache(...)` as the `DataSpec.key`. The test uses a WebDAV-style key to lock the consumer boundary without moving cache policy into `PlaybackVisualizationCacheManager`.
- Concrete win: stronger focused coverage for the `PlaybackMediaSourceProvider -> PlaybackMediaCacheOperations -> PlaybackVisualizationCacheManager` cache-key path without adding an owner, facade, bridge, field, package move, Service supplier, queue state source, URI/media item resolver path, cache policy path, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. This pairs with NOTE 139 so both MediaItem identity and visualization cache writes stay on provider/cache-operations rules rather than service-side string construction.
- Current two-slice audit metrics remain unchanged: `EchoPlaybackService.java` is 1410 lines, strict `private Playback*` field declarations are 47, strict `private Playback*Owner` declarations are 23, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot(...)` mentions are 0, and production `Playback*Owner.java` files remain 43. Focused tests protecting this batch are `PlaybackMediaSourceProviderTest`, `PlaybackVisualizationCacheManagerTest`, and `MainActivityArchitectureContractTest`.
- Verification passed with default Gradle daemon/workers via T0 `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackVisualizationCacheManagerTest --console=plain` and T1 `.\gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice changes only focused unit coverage and docs, not notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 141 - Resolver/cache boundary batch T2 compile checkpoint (2026-07-03)
- Batch scope after NOTE 139-140: the WebDAV MediaItem cache-key rule and visualization-cache `DataSpec.key` source are now covered by focused feature playback tests. These two slices did not change production code, add owners, widen resolver/cache facades, or move policy into `EchoPlaybackService`.
- T2 verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Current metrics remain `EchoPlaybackService.java` 1410 lines, strict `private Playback*` field declarations 47, strict `private Playback*Owner` declarations 23, production `fromPlaybackQueueManager(...)` calls 0, Service `queueStateSnapshot(...)` mentions 0, and production `Playback*Owner.java` files 43.
- Next low-risk candidates remain: only add resolver/cache tests where a concrete gap exists; otherwise resume queue/API read-only audit. Do not touch the remaining notification artwork supplier or `PlaybackLyricsStateOwner` / `PlaybackNotificationStateOwner` current-track reads until the smoke table is stable.
## NOTE 142 - Queue/API drift audit before next slice (2026-07-03)
- Audit scope: this is a read-only queue/API and wiring drift checkpoint after the resolver/cache batch. `PlaybackQueueManager.QueueProvider` is no longer available to shrink; the remaining risk is API/call-site drift, owner proliferation, and `EchoPlaybackService` becoming a wiring store.
- Queue/API grouping: real external queue inputs remain command methods such as `playQueue`, `appendToQueue`, `moveQueueTrack`, `removeTracksById`, `retainTracksById`, `replaceQueuedTrackById`, `replaceCurrentTrackAndResume`, navigation/completion methods, and restore/persistence methods. Derived read outputs remain `queueStateSnapshot`, `queueSnapshot`, `upcomingTracksForPrecache`, and `queuePreparationForNewPlayer`. `currentIndex`, `setCurrentIndex`, and `currentTrack` remain private manager implementation details, while `QueueStateSnapshot` still stores only `currentTrack`, `currentIndex`, and `queueSize` and derives booleans from those fields.
- Current drift metrics from the clean working tree: `EchoPlaybackService.java` is 1315 lines; strict non-final `private Playback*` declarations are 46; strict non-final `private Playback*Owner` declarations are 22; final-inclusive `private Playback*` wiring declarations are 57; final-inclusive `private Playback*Owner` wiring declarations are 26; app playback owner Java files remain 43; production `fromPlaybackQueueManager(...)` calls are 0; Service `queueStateSnapshot(...)` mentions are 0; and the only remaining Service `playbackQueueStateOwner::currentTrack` supplier is the notification artwork path.
- Boundary findings: production source still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, or `interface QueueProvider`. URI/media item resolution should stay in `PlaybackMediaSourceProvider`; cache/precache policy should stay in `PlaybackPrecacheManager`, `PlaybackMediaCacheOperations`, or a specific cache-policy owner. The notification artwork supplier plus `PlaybackLyricsStateOwner` and `PlaybackNotificationStateOwner` current-track reads remain P4 surfaces and should not be used as a low-risk P1 queue slice before smoke coverage is stable.
- Next safe cut rule: do not add or keep another `Playback*Owner` unless it removes a real state source, interface method, supplier chain, forwarding hop, Service strategy branch, or adds focused behavior coverage. The next queue/API slice should delete or narrow an actual public method/constructor dependency, or be another read-only audit; moving a call site without reducing one of those surfaces is not enough.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this checkpoint is docs/audit-only and does not change notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 143 - Preparation queue owner factory hop removed (2026-07-03)
- Small wiring slice: `EchoPlaybackService` now constructs `PlaybackCurrentTrackPreparationQueueOwner` directly with `PlaybackQueueManager`, `PlaybackMediaSourceProvider`, and the metadata provider. The owner no longer exposes the `fromMediaSourceProvider(...)` factory, removing one production factory/lambda hop while keeping URI/MediaSource resolution in `PlaybackMediaSourceProvider` and queue authority in `PlaybackQueueManager`.
- Concrete win: no owner, facade, bridge, field, state source, queue API, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior was added. The Service no longer calls `PlaybackCurrentTrackPreparationQueueOwner.fromMediaSourceProvider(...)`, and the architecture contract now blocks that factory from returning while still blocking `fromPlaybackQueueManager(...)` and Service-side `tracks -> mediaSourceProvider.mediaSourcesForTracks(...)` wiring.
- Current slice metrics: `EchoPlaybackService.java` is 1314 lines, strict non-final `private Playback*` declarations are 46, strict non-final `private Playback*Owner` declarations are 22, final-inclusive `private Playback*` wiring declarations are 57, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot(...)` mentions remain 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackCurrentTrackPreparationQueueOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice changes only construction wiring for existing queue preparation behavior, not notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 144 - Mirrored queue matcher factory hop removed (2026-07-03)
- Small wiring slice: `EchoPlaybackService` now constructs `PlaybackMirroredQueueTrackMatcherOwner` directly with the player supplier and `PlaybackMediaSourceProvider`. The owner no longer exposes `fromMediaSourceProvider(...)`, removing one production factory/lambda hop while keeping MediaItem reuse matching inside the existing mirrored queue matcher owner and provider boundary.
- Concrete win: no owner, facade, bridge, field, state source, queue API, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior was added. Service still does not call `mediaSourceProvider.mediaItemMatchesTrackForReuse(...)` or pass `mediaSourceProvider::mediaItemMatchesTrackForReuse`; the architecture contract now blocks the removed factory from returning.
- Current slice metrics: `EchoPlaybackService.java` is 1314 lines, strict non-final `private Playback*` declarations are 46, strict non-final `private Playback*Owner` declarations are 22, final-inclusive `private Playback*` wiring declarations are 57, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot(...)` mentions remain 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackMirroredQueueTrackMatcherOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice changes only construction wiring for existing mirrored queue matching behavior, not notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 145 - Two-slice wiring batch T2 checkpoint (2026-07-03)
- Batch scope after NOTE 143-144: two production factory/lambda hops were removed from `PlaybackCurrentTrackPreparationQueueOwner` and `PlaybackMirroredQueueTrackMatcherOwner`. Both changes keep URI/MediaSource/MediaItem decisions behind existing provider-backed owners and avoid new owner/facade/bridge files.
- Batch metrics remain: `EchoPlaybackService.java` is 1314 lines, strict non-final `private Playback*` declarations are 46, strict non-final `private Playback*Owner` declarations are 22, final-inclusive `private Playback*` wiring declarations are 57, production `fromPlaybackQueueManager(...)` calls are 0, Service `queueStateSnapshot(...)` mentions are 0, and app playback owner Java files remain 43.
- T2 verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
## NOTE 146 - Streaming restore owner factory hop removed (2026-07-03)
- Small wiring slice: `EchoPlaybackService` now constructs `PlaybackQueueStreamingRestoreOwner` directly with `PlaybackMediaSourceProvider`. The owner no longer exposes `fromMediaSourceProvider(...)`, removing one production factory/lambda hop while keeping streaming restored-track and header restore decisions behind `PlaybackMediaSourceProvider`.
- Concrete win: no owner, facade, bridge, field, state source, queue API, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior was added. Service still does not pass `mediaSourceProvider::restoredTrackForPreparation` or `mediaSourceProvider::restoreHeadersForDataPath`, and the architecture contract now blocks the removed factory from returning.
- Current slice metrics: `EchoPlaybackService.java` is 1314 lines, strict non-final `private Playback*` declarations are 46, strict non-final `private Playback*Owner` declarations are 22, final-inclusive `private Playback*` wiring declarations are 57, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot(...)` mentions remain 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueStreamingRestoreOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this slice changes only construction wiring for existing streaming restore behavior, not notification action mapping, background playback, lyrics, shutdown, service lifecycle, or foreground notification behavior.
## NOTE 147 - Factory-hop batch audit and T2 checkpoint (2026-07-03)
- Batch scope after NOTE 143-146: three provider-backed construction hops were removed from `PlaybackCurrentTrackPreparationQueueOwner`, `PlaybackMirroredQueueTrackMatcherOwner`, and `PlaybackQueueStreamingRestoreOwner`. These were narrow wiring reductions only; no owner, facade, bridge, state source, queue API, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior was added.
- Boundary audit: production source still has no `PlaybackMediaSourceResolutionOwner`, `PlaybackItemResolver`, `PlaybackQueueManager.QueueProvider`, `interface QueueProvider`, or `fromPlaybackQueueManager(...)` hits. `EchoPlaybackService` still has 0 `queueStateSnapshot(...)` mentions and 1 remaining `playbackQueueStateOwner::currentTrack` supplier for notification artwork, which remains deferred to P4 smoke coverage.
- Current metrics: `EchoPlaybackService.java` is 1314 lines, strict non-final `private Playback*` declarations are 46, strict non-final `private Playback*Owner` declarations are 22, final-inclusive `private Playback*` wiring declarations are 57, final-inclusive `private Playback*Owner` wiring declarations are 26, and app playback owner Java files remain 43.
- Remaining `fromMediaSourceProvider(...)` production hits are not automatically cleanup targets: `PlaybackCurrentTrackPreparationOwner`, `PlaybackVisualizationCacheManager`, `PlaybackPrecacheManager`, and `PlaybackMediaCacheOperations` still own real provider/cache/precache boundaries. The next slice should return to queue/API narrowing or add concrete resolver/cache behavior coverage, not keep mechanically deleting factories.
- T2 verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
## NOTE 148 - Queue/API no-op guard after owner drift risk review (2026-07-03)
- Read-only audit scope after NOTE 147: `PlaybackQueueManager.QueueProvider` is absent from production code, production `fromPlaybackQueueManager(...)` calls are 0, `EchoPlaybackService` has 0 direct `queueStateSnapshot(...)` mentions, and `PlaybackQueueStateOwner` still has no local `QueueProvider`, `QueueStateOperations`, `QueueSnapshotOperations`, or `UpcomingTracksOperations` interfaces. This means there is no remaining QueueProvider method list to shrink mechanically.
- Current metrics from the clean working tree: `EchoPlaybackService.java` is 1409 lines, strict non-final `private Playback*` declarations are 47, strict non-final `private Playback*Owner` declarations are 23, final-inclusive `private Playback*` wiring declarations are 58, final-inclusive `private Playback*Owner` wiring declarations are 27, and app playback owner Java files remain 43. The single Service `playbackQueueStateOwner::currentTrack` supplier still feeds notification artwork and remains a P4 smoke-gated surface.
- Queue/API classification: real external inputs remain queue command, restore, persistence, completion, navigation, replacement, mirrored transition, and precache-read APIs with behavior tests or owner consumers. Derived reads remain centralized as `QueueStateSnapshot` fields and properties, `queueSnapshot()`, `upcomingTracksForPrecache(...)`, and `queuePreparationForNewPlayer()`. `currentIndex`, `setCurrentIndex`, and manager `currentTrack()` remain private implementation details; turning those reads into new owner methods or suppliers would be interface growth.
- Boundary findings: `PlaybackQueueStateOwnerTest` already covers manager delegation, missing-manager fallbacks, rebinding, current-track derivation from `queueStateSnapshot()`, and precache read delegation. `MainActivityArchitectureContractTest` already blocks QueueProvider revival, queue-state supplier facades, Service direct queue snapshot reads, and public manager current-track/cursor exposure. The remaining `queueStateOwner.currentTrack()` reads are only `PlaybackLyricsStateOwner` and `PlaybackNotificationStateOwner`; they are P4 notification/lyrics surfaces and should not be touched before smoke is stable.
- Next safe cut rule: do not spend the next slice deleting or wrapping an owner unless it removes a real state source, public method, constructor dependency, supplier chain, forwarding hop, or Service strategy branch, or adds focused behavior coverage for a concrete queue/resolve/cache gap. If none of those is available, keep the next checkpoint read-only rather than moving call sites.
## NOTE 149 - Notification state owner localized out of Service fields (2026-07-03)
- Small Service wiring slice: `PlaybackNotificationStateOwner` is now a final local state provider in `EchoPlaybackService.onCreate()` instead of a top-level Service field. It is still passed directly to `PlaybackNotificationManager`; notification action mapping, artwork refresh, foreground lifecycle, lyrics, shutdown, and background playback behavior are unchanged.
- Concrete win: one `EchoPlaybackService` `private Playback*Owner` wiring field was removed without adding an owner, facade, bridge, supplier, state source, queue API, URI/media item resolver path, cache policy path, package move, or Service strategy branch. The architecture contract now blocks the field from returning while preserving the existing queue/player/preparing/favorite/session-token state-provider inputs.
- Current metrics after the slice: `EchoPlaybackService.java` is 1408 lines, strict non-final `private Playback*` declarations are 46, strict non-final `private Playback*Owner` declarations are 22, final-inclusive `private Playback*` wiring declarations are 57, final-inclusive `private Playback*Owner` wiring declarations are 26, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot(...)` mentions remain 0, the single Service `playbackQueueStateOwner::currentTrack` supplier remains notification artwork only, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Device smoke remains out of scope because this changes only construction lifetime for an existing notification state provider, not notification controls, background playback, lyrics, shutdown, service lifecycle, or foreground behavior.
## NOTE 150 - Session refresher localized out of Service fields (2026-07-03)
- Small Service wiring slice: `PlaybackNotificationArtworkBridgeOwner.SessionRefresher` is now a final local value in `EchoPlaybackService.onCreate()` instead of a top-level Service field. The same refresher is still passed to the lyrics notification bridge and the notification artwork bridge, so session refresh behavior remains owned by `PlaybackNotificationArtworkBridgeOwner` and `PlaybackSessionManager`.
- Concrete win: one more `EchoPlaybackService` `private Playback*Owner` wiring field was removed without adding an owner, facade, bridge, supplier, state source, queue API, URI/media item resolver path, cache policy path, package move, notification action behavior, lyrics sync behavior, shutdown behavior, or background playback behavior. The architecture contract now blocks the field from returning while still requiring `sessionRefresherFromPlaybackSessionManager(...)`.
- Current metrics after the slice: `EchoPlaybackService.java` remains 1408 lines, strict non-final `private Playback*` declarations are 45, strict non-final `private Playback*Owner` declarations are 21, final-inclusive `private Playback*` wiring declarations are 56, final-inclusive `private Playback*Owner` wiring declarations are 25, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot(...)` mentions remain 0, the single Service `playbackQueueStateOwner::currentTrack` supplier remains notification artwork only, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationArtworkBridgeOwnerTest --tests app.yukine.playback.PlaybackLyricsManagerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. T2 compile also passed via `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Device smoke remains out of scope because this changes only construction lifetime for an existing session refresher, not notification controls, background playback, lyrics runtime policy, shutdown, service lifecycle, or foreground behavior.
## NOTE 151 - Notification artwork source localized out of Service fields (2026-07-03)
- Small Service wiring slice: `PlaybackNotificationArtworkSource` is now a final local value in `EchoPlaybackService.onCreate()` instead of a top-level Service field. It still uses `PlaybackNotificationArtworkSource.fromSupplier(() -> playbackNotificationArtworkManager)` and is passed to both `PlaybackNotificationManager` and `PlaybackStatePublisher`, preserving the delayed artwork-manager lookup.
- Concrete win: one `EchoPlaybackService` `private Playback*` wiring field was removed without adding an owner, facade, bridge, state source, queue API, URI/media item resolver path, cache policy path, package move, notification action behavior, lyrics behavior, shutdown behavior, or background playback behavior. The architecture contract now blocks the field from returning while preserving the existing `fromSupplier(...)` boundary.
- Current metrics after the slice: `EchoPlaybackService.java` remains 1408 lines, strict non-final `private Playback*` declarations are 44, strict non-final `private Playback*Owner` declarations are 21, final-inclusive `private Playback*` wiring declarations are 55, final-inclusive `private Playback*Owner` wiring declarations are 25, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot(...)` mentions remain 0, the single Service `playbackQueueStateOwner::currentTrack` supplier remains notification artwork only, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationArtworkSourceTest --tests app.yukine.playback.PlaybackStatePublisherTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. T2 compile also passed via `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`. Device smoke remains out of scope because this changes only construction lifetime for an existing artwork source proxy, not notification controls, background playback, lyrics, shutdown, service lifecycle, or foreground behavior.
## NOTE 152 - Notification foreground owner localized out of Service fields (2026-07-03)
- Small Service wiring slice: `PlaybackNotificationForegroundOwner` is now a final local value in `EchoPlaybackService.onCreate()` instead of a top-level Service field. It is still the foreground boundary passed to `PlaybackNotificationCommandOwner` and `PlaybackNotificationManager`; the Service still owns the Android `startForeground`, `stopForeground`, `stopSelf`, and PendingIntent boundary callbacks.
- Concrete win: one `EchoPlaybackService` `private Playback*Owner` wiring field was removed without adding an owner, facade, bridge, supplier, state source, queue API, URI/media item resolver path, cache policy path, package move, notification action behavior, lyrics behavior, shutdown behavior, or background playback behavior. The architecture contract now blocks the foreground owner field from returning while preserving the existing foreground boundary owner.
- Current metrics after the slice: `EchoPlaybackService.java` remains 1408 lines, strict non-final `private Playback*` declarations are 43, strict non-final `private Playback*Owner` declarations are 20, final-inclusive `private Playback*` wiring declarations are 54, final-inclusive `private Playback*Owner` wiring declarations are 24, production `fromPlaybackQueueManager(...)` calls remain 0, Service `queueStateSnapshot(...)` mentions remain 0, the single Service `playbackQueueStateOwner::currentTrack` supplier remains notification artwork only, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationForegroundOwnerTest --tests app.yukine.playback.PlaybackNotificationCommandOwnerTest --tests app.yukine.playback.PlaybackNotificationManagerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`; device smoke remains out of scope because this changes only construction lifetime for the existing foreground owner, not notification controls, background playback, lyrics, shutdown, service lifecycle, or foreground behavior.
## NOTE 153 - Notification artwork current-track supplier removed from Service (2026-07-03)
- Queue/state wiring slice: `PlaybackNotificationArtworkManager` now receives the feature playback `PlaybackQueueManager` and reads `queueStateSnapshot().currentTrack` internally, instead of receiving `playbackQueueStateOwner::currentTrack` from `EchoPlaybackService`.
- Concrete win: this removes the last Service current-track supplier chain without adding an owner, facade, bridge, Service-level supplier field, queue API, URI/media item resolver path, cache policy path, package move, notification action mapping, lyrics sync, shutdown behavior, or background playback behavior. Notification artwork loading and refresh callbacks stay in the existing artwork manager/bridge path.
- `PlaybackNotificationArtworkManagerTest` now builds current-track state through `PlaybackQueueManager.playQueue(...)`, so the focused behavior test exercises the same queue authority used at runtime. `MainActivityArchitectureContractTest` now requires the Service to pass `playbackQueueManager`, rejects `playbackQueueStateOwner::currentTrack` returning anywhere in Service, and requires the artwork manager to hold `PlaybackQueueManager` instead of `Supplier<Track>`.
- Current metrics after the slice: `EchoPlaybackService.java` remains 1408 lines, strict non-final `private Playback*` declarations remain 43, strict non-final `private Playback*Owner` declarations remain 20, final-inclusive `private Playback*` wiring declarations remain 54, final-inclusive `private Playback*Owner` wiring declarations remain 24, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers are now 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackNotificationArtworkManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed feature/app compile tasks for this slice. Device smoke remains out of scope because this changes only construction/state-source wiring for existing notification artwork, not notification controls, background playback, lyrics, shutdown, service lifecycle, or foreground behavior.
## NOTE 154 - Precache upcoming queue read removed from StateProvider (2026-07-03)
- Cache/queue interface slice: `PlaybackPrecacheManager.StateProvider` no longer exposes `upcomingTracksForPrecache(int)`. `PlaybackPrecacheManager` now receives the existing `PlaybackQueueManager` and reads `upcomingTracksForPrecache(SEGMENTED_PRECACHE_CONCURRENCY)` directly from the queue authority.
- Concrete win: one interface method was removed from the precache state provider, and `PlaybackPrecacheStateOwner` no longer forwards upcoming queue reads through `PlaybackQueueStateOwner`. This does not add an owner, facade, bridge, Service-level supplier, queue API, URI/media item resolver path, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- `PlaybackPrecacheManagerTest.upcomingPrecacheReadsTracksThroughQueueManager` now builds upcoming state through `PlaybackQueueManager.playQueue(...)`; `MainActivityArchitectureContractTest` blocks `stateProvider.upcomingTracksForPrecache(...)` and requires the manager to hold `PlaybackQueueManager`.
- Current metrics after the slice: `EchoPlaybackService.java` is 1409 lines, strict non-final `private Playback*` declarations remain 43, strict non-final `private Playback*Owner` declarations remain 20, final-inclusive `private Playback*` wiring declarations remain 54, final-inclusive `private Playback*Owner` wiring declarations remain 24, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers remain 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.playback.PlaybackPrecacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Device smoke remains out of scope because this changes only precache queue-read ownership and interface width, not notification controls, background playback, lyrics, shutdown, service lifecycle, or foreground behavior.
## NOTE 155 - Precache current queue read removed from StateProvider (2026-07-03)
- Cache/queue interface slice: `PlaybackPrecacheManager.StateProvider` no longer exposes `currentTrack()`. `PlaybackPrecacheManager` already receives the existing `PlaybackQueueManager`, so current-track reads now go through `playbackQueueManager.queueStateSnapshot().getCurrentTrack()` inside the precache owner.
- Concrete win: one more precache state-provider interface method was removed, and `PlaybackPrecacheStateOwner` dropped its `PlaybackQueueStateOwner` constructor dependency and field. This does not add an owner, facade, bridge, Service-level supplier, queue API, URI/media item resolver path, cache policy path, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- `PlaybackPrecacheManagerTest` now builds current-track precache state through `PlaybackQueueManager.playQueue(...)`; `PlaybackPrecacheStateOwnerTest` covers only player media item and streaming diagnostics state; `MainActivityArchitectureContractTest` blocks `Track currentTrack();`, `stateProvider.currentTrack()`, and `PlaybackPrecacheStateOwner` queue-state dependencies from returning.
- Current metrics after the slice: `EchoPlaybackService.java` is 1408 lines, strict non-final `private Playback*` declarations remain 43, strict non-final `private Playback*Owner` declarations remain 20, final-inclusive `private Playback*` wiring declarations remain 54, final-inclusive `private Playback*Owner` wiring declarations remain 24, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers remain 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.playback.PlaybackPrecacheStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Device smoke remains out of scope because this changes only precache queue-read ownership and interface width, not notification controls, background playback, lyrics, shutdown, service lifecycle, or foreground behavior.
## NOTE 156 - QueueStateOwner upcoming precache read removed (2026-07-03)
- Queue/read interface slice: `PlaybackQueueStateOwner.upcomingTracksForPrecache(int)` was removed after the precache owner stopped consuming it. Upcoming precache reads now exist only on the real queue authority, `PlaybackQueueManager`, and the precache owner reaches that method directly.
- Concrete win: one Java-facing queue read API and one forwarding method were deleted without adding an owner, facade, bridge, supplier, state source, Service strategy branch, queue manager API, URI/media item resolver path, cache policy path, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior.
- `PlaybackQueueStateOwnerTest` now covers only the remaining Java-facing state reads (`queueStateSnapshot()`, `queueSnapshot()`, and `currentTrack()`); `MainActivityArchitectureContractTest` blocks the removed upcoming-precache method and forwarding call from returning. `PlaybackQueueManagerTest` and `PlaybackPrecacheManagerTest` remain the behavior coverage for upcoming precache ordering and precache consumption.
- Current metrics after the slice: `EchoPlaybackService.java` remains 1408 lines, strict non-final `private Playback*` declarations remain 43, strict non-final `private Playback*Owner` declarations remain 20, final-inclusive `private Playback*` wiring declarations remain 54, final-inclusive `private Playback*Owner` wiring declarations remain 24, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers remain 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueStateOwnerTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Device smoke remains out of scope because this changes only a Java-facing queue read method, not notification controls, background playback, lyrics, shutdown, service lifecycle, or foreground behavior.
## NOTE 157 - QueueStateOwner current-track helper removed (2026-07-03)
- Queue/read interface slice: `PlaybackQueueStateOwner.currentTrack()` was removed after the remaining production consumers were reduced to `PlaybackLyricsStateOwner` and `PlaybackNotificationStateOwner`. Those state providers now read `queueStateOwner.queueStateSnapshot().getCurrentTrack()` directly, while the Service still has no direct `queueStateSnapshot()` or `playbackQueueStateOwner::currentTrack` wiring.
- Concrete win: one more Java-facing derived queue read method was deleted without adding an owner, facade, bridge, supplier, state source, Service strategy branch, queue manager API, URI/media item resolver path, cache policy path, package move, notification action behavior, lyrics sync behavior, shutdown behavior, or background playback behavior.
- Focused coverage for this slice is `PlaybackQueueStateOwnerTest`, `PlaybackLyricsStateOwnerTest`, `PlaybackNotificationStateOwnerTest`, and `MainActivityArchitectureContractTest`. The contract now blocks `PlaybackQueueStateOwner.currentTrack()` and any `queueStateOwner.currentTrack()` production call from returning.
- Current metrics after the slice: `EchoPlaybackService.java` remains 1408 lines, strict non-final `private Playback*` declarations remain 43, strict non-final `private Playback*Owner` declarations remain 20, final-inclusive `private Playback*` wiring declarations remain 54, final-inclusive `private Playback*Owner` wiring declarations remain 24, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers remain 0, and app playback owner Java files remain 43.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackQueueStateOwnerTest --tests app.yukine.playback.PlaybackLyricsStateOwnerTest --tests app.yukine.playback.PlaybackNotificationStateOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also executed app Kotlin/Java compile tasks. Device smoke remains out of scope because this changes only Java-facing queue read plumbing inside existing state providers, not notification controls, background playback, lyrics sync, shutdown, service lifecycle, or foreground behavior.
## NOTE 158 - Resolver/cache boundary test for current player media item (2026-07-03)
- Resolver/cache boundary test slice: `PlaybackPrecacheManagerTest.currentPrecacheMatchesStateProviderMediaItemBeforeCacheRead` now verifies that current-track precache passes `StateProvider.currentPlayerMediaItem()` and the target track into the media-item matcher before deciding that the player can fill the leading cache range.
- Concrete win: this strengthens focused behavior coverage without adding or changing a production owner, facade, bridge, supplier, state source, Service strategy branch, queue API, URI/media item resolver path, cache policy path, package move, notification behavior, lyrics sync behavior, shutdown behavior, or background playback behavior. URI and MediaItem matching remain behind `PlaybackMediaSourceProvider` / the existing provider-backed matcher supplied to `PlaybackPrecacheManager`.
- Current audit metrics after the slice: `EchoPlaybackService.java` remains 1408 lines, strict non-final `private Playback*` declarations remain 43, strict non-final `private Playback*Owner` declarations remain 20, final-inclusive `private Playback*` wiring declarations remain 54, final-inclusive `private Playback*Owner` wiring declarations remain 24, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers remain 0, app playback owner Java files remain 43, and production `PlaybackMediaSourceResolutionOwner` / `PlaybackItemResolver` mentions remain 0.
- Verification passed with default Gradle daemon/workers via `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackPrecacheManagerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`, which also checked the architecture contract and reused passing app Kotlin/Java compile tasks. Device smoke remains out of scope because this is a test-only resolver/cache boundary slice.
## NOTE 159 - Owner/interface audit checkpoint after queue and resolver slices (2026-07-03)
- Read-only audit scope after NOTE 153-158: the queue current-track supplier chains are now gone from `EchoPlaybackService`, precache no longer receives queue reads through `PlaybackPrecacheManager.StateProvider`, `PlaybackQueueStateOwner` now exposes only `queueStateSnapshot()` and `queueSnapshot()`, and the resolver/cache coverage slice did not introduce a resolution facade.
- Current metrics from the clean working tree: `EchoPlaybackService.java` is 1408 lines, strict non-final `private Playback*` declarations are 43, strict non-final `private Playback*Owner` declarations are 20, final-inclusive `private Playback*` wiring declarations are 54, final-inclusive `private Playback*Owner` wiring declarations are 24, production `fromPlaybackQueueManager(...)` calls are 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions are 0, Service `queueStateSnapshot(...)` mentions are 0, Service `playbackQueueStateOwner::currentTrack` suppliers are 0, app playback owner Java files remain 43, production `PlaybackMediaSourceResolutionOwner` / `PlaybackItemResolver` mentions are 0, and production `fromMediaSourceProvider(...)` calls are 9.
- Queue/interface classification: there is no remaining `QueueProvider` method list to shrink. The remaining queue reads are real manager APIs with consumers: `queueSnapshot()` for external UI snapshots, `queueStateSnapshot()` for compact current/index/size state, `upcomingTracksForPrecache(...)` for cache scheduling, and `queuePreparationForNewPlayer()` for Media3 queue preparation. Reintroducing helper methods such as `currentTrack()` on Java-facing owners would widen the interface again.
- Owner drift finding: owner count is still the constraint. The next slice should not add or delete a `Playback*Owner` unless it removes a state source, public method, constructor dependency, supplier chain, forwarding hop, Service strategy branch, or adds focused behavior coverage for a concrete resolver/cache/queue gap. `PlaybackMediaSourceResolutionOwner` and `PlaybackItemResolver` are still absent; keep URI/MediaItem resolution in `PlaybackMediaSourceProvider`, and keep cache policy in `PlaybackPrecacheManager` or a specific cache-policy owner.
- Service wiring finding: the line count is no longer sufficient as a win metric. Track private `Playback*` fields, private `Playback*Owner` fields, owner file count, local factory/supplier count, and initialization length before claiming more Service progress. P4/P5 notification, lyrics, lifecycle, foreground, and shutdown surfaces remain smoke-gated and should not be the next default cut.
- Next safe cut rule: prefer a narrow behavior test or an existing-interface reduction over another owner extraction. If a code slice cannot prove one of those wins in 1-3 focused tests, keep the slice read-only and commit the audit instead of moving call sites.
## NOTE 160 - Lyrics state owner localized out of Service fields (2026-07-03)
- Small Service wiring slice: `PlaybackLyricsStateOwner` is now a final local state provider in `EchoPlaybackService.onCreate()` instead of a top-level Service field. It is still passed directly to `PlaybackLyricsManager`, and status-bar lyrics settings still flow through `playbackLyricsManager`; no lyrics notification, floating lyrics, shutdown, background playback, or MediaSession behavior changed.
- Concrete win: one `EchoPlaybackService` `private Playback*Owner` wiring field was removed without adding an owner, facade, bridge, supplier, state source, queue API, URI/media item resolver path, cache policy path, package move, or Service strategy branch. The architecture contract now blocks the lyrics state owner field from returning while preserving the existing `PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState(...)` boundary.
- Current metrics after the slice: `EchoPlaybackService.java` is 1407 lines, strict non-final `private Playback*` declarations are 42, strict non-final `private Playback*Owner` declarations are 19, final-inclusive `private Playback*` wiring declarations are 53, final-inclusive `private Playback*Owner` wiring declarations are 23, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers remain 0, app playback owner Java files remain 43, and production `PlaybackMediaSourceResolutionOwner` / `PlaybackItemResolver` mentions remain 0.
## NOTE 161 - Session command owner localized out of Service fields (2026-07-03)
- Small MediaSession wiring slice: `PlaybackSessionCommandOwner` is now a final local delegate in `EchoPlaybackService.onCreate()` instead of a top-level Service field. The existing `PlaybackSessionManager` player factory captures that delegate and still creates `PlaybackSessionPlayer(player, playbackSessionCommandOwner)`, so MediaSession command routing stays behind the same command owner.
- Concrete win: one more `EchoPlaybackService` `private Playback*Owner` wiring field and the lazy `if (playbackSessionCommandOwner == null)` branch were removed without adding an owner, facade, bridge, state source, queue API, URI/media item resolver path, cache policy path, package move, notification behavior, lyrics behavior, shutdown behavior, or background playback behavior. The architecture contract now blocks the session command owner field from returning while preserving the existing `PlaybackControllerMediaItemsOwner` and `PlaybackSessionCommandOwner` boundaries.
- Current metrics after the slice: `EchoPlaybackService.java` is 1404 lines, strict non-final `private Playback*` declarations are 41, strict non-final `private Playback*Owner` declarations are 18, final-inclusive `private Playback*` wiring declarations are 52, final-inclusive `private Playback*Owner` wiring declarations are 22, production `fromPlaybackQueueManager(...)` calls remain 0, production `PlaybackQueueManager.QueueProvider` / `interface QueueProvider` mentions remain 0, Service `queueStateSnapshot(...)` mentions remain 0, Service `playbackQueueStateOwner::currentTrack` suppliers remain 0, app playback owner Java files remain 43, and production `PlaybackMediaSourceResolutionOwner` / `PlaybackItemResolver` mentions remain 0.
## 2026-06-27 DIRECTION PIVOT: stabilization before more extraction

- New controlling doc: `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`.
- This pivot supersedes the previous default of continuing fast owner/manager extraction when the two conflict.
- Freeze broad architecture expansion first: do not add new `Manager`, `Coordinator`, `Controller`, `Bindings`, or `Gateway` layers by default.
- Stabilize the dirty migration surface, record current counts, and prefer reviewable commits/checkpoints before more migration.
- Reduce existing over-abstraction: merge/delete forwarding-only owners, shrink oversized provider/listener interfaces, and shorten UI -> service/data call chains.
- Do not expand `PlaybackQueueManager.QueueProvider` or similar large interfaces without a prior split/merge/inline plan.
- String-based architecture contracts are not enough for fragile flows; pair them with behavior tests, dependency-direction checks, integration smoke, or device evidence.
- For Windows/KSP verification, run Gradle invocations serially with project defaults first; use `--no-daemon` or `--max-workers=1` only after a reproducible daemon, KSP, or lock issue.
- Continue P1/P2 only after a slice demonstrably reduces net files, methods, state sources, dependencies, or call-chain length.
## 2026-06-27 ????

- ???????????/????????????? owner ????
- `EchoPlaybackService` ???? `MainActivity` ???????????/???????? launcher intent?
- `PlaybackQueueManager.QueueProvider` ??????????????????????????????
- ?????????????????????????????
- ???????? `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`???? P1/P2 ????????????????
