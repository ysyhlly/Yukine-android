# Echo Android 多模块拆分 Phase 2 — Codex 交接文档

> 日期: 2026-06-28
> 前置: Phase 0-5 已完成，assembleDebug 编译通过
> 分支: `feat/coordinator-extraction`
> 执行方: Codex

---

## 2026-06-28 Codex continuation update

本轮继续推进了 Phase 5.5 / Phase 5.6 的架构落地，当前 `assembleDebug`
已重新通过。

### Phase 5.5 status: implemented

- 新增并接入 `:feature:ui-common`。
- `settings.gradle` 已包含 `include ":feature:ui-common"`。
- `:app` 已依赖 `implementation project(":feature:ui-common")`。
- `:feature:ui-common` 当前包含 34 个源码文件。
- 已迁出主题、设计 token、通用 UI 组件、NowBar、波形、通用列表/页面 screen。
- `noto_sans_cjk_sc_regular.otf`、`outfit.ttf`、`ic_stat_echo.xml`、`ic_echo_launcher.xml`
  已复制到 `feature/ui-common/src/main/res`，库模块不再引用 app `R`。
- `LyricUiLine` 已从 `NowPlayingScreen.kt` 抽到
  `feature/ui-common/src/main/java/app/yukine/ui/LyricUiLine.kt`。
- `TrackDownloadStatus`、`TrackDownloadItem`、`DownloadsUiState` 已上移到
  `core/model/src/main/java/app/yukine/TrackDownloadModels.kt`，避免 UI 模块反向依赖 app。
- `RequestedRoutePolicy.kt` 已迁入
  `feature/ui-common/src/main/java/app/yukine/ui/RequestedRoutePolicy.kt`，保持
  `app.yukine.ui` 包名不变。
- `BackgroundPreviewScreen.kt` 已迁入
  `feature/ui-common/src/main/java/app/yukine/ui/BackgroundPreviewScreen.kt`。
  `BackgroundPreviewActivity` 继续用 `AppLanguage` 解析文案，并通过
  `BackgroundPreviewLabels` 传给纯 UI screen，避免 `:feature:ui-common` 反向依赖 app。
- `SearchScreen.kt` / `UnifiedSearchScreen` 已迁入
  `feature/ui-common/src/main/java/app/yukine/ui/SearchScreen.kt`，并通过
  `UnifiedSearchUiState` / `UnifiedSearchStreamingState` 读取纯 UI 状态。
- `StreamingSearchScreen.kt` 已迁入
  `feature/ui-common/src/main/java/app/yukine/ui/StreamingSearchScreen.kt`。
- `StreamingSearchState` / `StreamingSearchAuthLaunch` 已迁入
  `feature/ui-common/src/main/java/app/yukine/StreamingSearchContracts.kt`。

### Phase 5.6 status: implemented

- 新增并接入 `:feature:navigation`。
- `settings.gradle` 已包含 `include ":feature:navigation"`。
- `:app` 已依赖 `implementation project(":feature:navigation")`。
- `:feature:navigation` 当前包含:
  - `EchoRoutes.kt`
  - `EchoScaffold.kt`
  - `EchoNowBar.kt`
  - `NavViewModelContracts.kt`
  - `NowPlayingContracts.kt`
  - `NowPlayingDestination.kt`
  - `HomeDashboardContracts.kt`
  - `HomeDestination.kt`
  - `SettingsDestinationContracts.kt`
  - `SettingsDestination.kt`
  - `QueueDestinationContracts.kt`
  - `QueueDestination.kt`
  - `DownloadsDestinationContracts.kt`
  - `DownloadsDestination.kt`
  - `SearchDestination.kt`
  - `CollectionsDestinationContracts.kt`
  - `CollectionsDestination.kt`
  - `LibraryDestinationContracts.kt`
  - `LibraryGroupsDestination.kt`
  - `LibraryTrackListDestination.kt`
  - `NetworkSourcesDestinationContracts.kt`
  - `NetworkSourcesDestination.kt`
  - `NetworkMenuDestinationContracts.kt`
  - `NetworkMenuDestination.kt`
  - `NetworkDestination.kt`
  - `EchoNavGraph.kt`
  - `EchoNavHostState.kt`
  - `EchoNavHostBridge.kt`
- 包名保持 `app.yukine.navigation`，调用侧无需大规模 import churn。

`EchoNavGraph.kt` / `EchoNavHostState.kt` / `EchoNavHostBridge.kt` originally
stayed in `:app` while the ViewModel-specific dependencies were narrowed. They
now live in `:feature:navigation`; app ViewModels continue to implement or
publish the narrow state/action contracts consumed by the navigation shell.

Phase 5.6 was completed by adding narrow contracts instead of making
`:feature:navigation` depend on app:

```kotlin
interface NowBarStateProvider {
    val nowBarState: StateFlow<NowBarState>
}

interface PlaybackSnapshotProvider {
    val playbackSnapshot: StateFlow<PlaybackStateSnapshot>
}
```

App ViewModels implement or publish these interfaces/state flows; the
navigation shell consumes only those contracts.

### 2026-06-28 continuation notes

- `NowPlayingViewModel` 已实现 `NowBarStateProvider`。
- `PlaybackViewModel` 已实现 `PlaybackSnapshotProvider`。
- `EchoNavGraph` 已改为从 provider 采集 `StateFlow`，NowBar 事件通过回调分发，不再直接读 `PlaybackViewModel.playback`。
- `EchoNavHostState` 已改为接收 `PlaybackSnapshotProvider`，不再直接公开或导入 `PlaybackViewModel`。
- `NowPlayingDestination.kt` 已重写为纯装配入口，继续把 app 侧 `NowPlayingViewModel` 状态映射到 ui-common 的 `NowPlayingScreen`。
- `NowPlayingDestination.kt` 和 Now Playing navigation contracts 已迁入
  `:feature:navigation`；`EchoNavGraph` 仍留在 app 侧负责装配 app-owned
  ViewModel/handler。
- 当前 `assembleDebug` 仍然通过。

### Verification after this update

All commands were run serially on Windows with Android Studio JBR:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:ui-common:compileDebugKotlin :feature:ui-common:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: PlaybackCoordinator removal

Continued Phase 6 stabilization by deleting the now forwarding-only
`PlaybackCoordinator`:

- Confirmed the only live production calls were play-start forwarding,
  `bind()`, `release()`, and foreground/background visibility forwarding.
- Replaced host playback start with direct `PlaybackStartController.playTrackList(...)`.
- Replaced service bind/release with direct `PlaybackServiceConnectionController`
  calls.
- Replaced app visibility forwarding with direct `EchoPlaybackService.setAppVisible(...)`.
- Updated `MainActivityArchitectureContractTest` to guard against restoring the
  coordinator field, constructor, file, or `playbackCoordinator.*` calls.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.PlaybackStartControllerTest --tests app.yukine.PlaybackViewModelTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: StreamingCoordinator removal

Continued Phase 6 stabilization by deleting the now forwarding-only
`StreamingCoordinator`:

- Confirmed the only live production calls were
  `streamingCoordinator.handleInitialIntent(getIntent())` and
  `streamingCoordinator.handleNewIntent(intent)`.
- Replaced both calls with direct `StreamingAuthCallbackController` calls.
- Kept existing `MainActivityBase` streaming provider refresh, gateway endpoint,
  unified streaming playback, selected quality, and source-switch paths as the
  active implementations; the coordinator copies of those methods had no active
  caller.
- Updated `MainActivityArchitectureContractTest` to guard against restoring the
  coordinator field, constructor, or `streamingCoordinator.*` calls.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.StreamingEventControllersTest --tests app.yukine.StreamingViewModelTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Network render controller field removal

Continued Phase 6 stabilization by reducing `MainActivityBase` retained network
render assembly state without deleting the real render owners:

- Kept `NetworkRenderCoordinator` as the network page dispatch/render owner.
- Kept `NetworkMenuRenderController`, `NetworkTrackListRenderController`,
  `NetworkSourcesRenderController`, and `StreamingSearchRenderController` as
  focused render owners consumed by `NetworkRenderCoordinator`.
- Removed the four corresponding Activity fields from `MainActivityBase`.
- Constructed those render controllers as local values in `onCreate` and passed
  them directly into `NetworkRenderCoordinator`; no later lifecycle code reads
  them from the Activity.
- Updated `MainActivityArchitectureContractTest` to guard against restoring
  those Activity fields while preserving the constructor/wiring checks.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug
```

Result: all passed.

### 2026-06-29 continuation: PlaybackServiceHostController field removal

Continued Phase 6 stabilization by reducing `MainActivityBase` retained
playback assembly state without deleting the service host policy owner:

- Kept `PlaybackServiceHostController` as the owner for playback service
  connect/disconnect policy: service attachment, playback settings application,
  pending playback, store reset, and chrome refresh.
- Removed the `PlaybackServiceHostController playbackServiceHostController`
  field from `MainActivityBase`.
- Constructed the service host controller as a local value immediately before
  wiring it into `PlaybackServiceConnectionController`; no later lifecycle code
  reads it from the Activity.
- Updated `MainActivityArchitectureContractTest` to guard against restoring the
  Activity field while preserving the controller/host wiring checks.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug
```

Result: all passed.

### 2026-06-29 continuation: PlaybackStateUpdateController assembly reduction

Continued Phase 6 stabilization by removing one more stateless playback
assembly dependency from `MainActivityBase`:

- Converted `PlaybackStateUpdateController` from an instance class to an
  `internal object`; its `resolve(...)` policy remains in the same owner.
- Removed the `playbackStateUpdateController` field and
  `new PlaybackStateUpdateController()` construction from `MainActivityBase`.
- Simplified `PlaybackStateEventController` so it calls
  `PlaybackStateUpdateController.resolve(...)` directly instead of accepting a
  constructor dependency.
- Kept `PlaybackStateUpdateControllerTest` as focused behavior evidence and
  updated `MainActivityArchitectureContractTest` to guard against restoring the
  host field or manual construction.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.PlaybackStateUpdateControllerTest
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug
```

Result: all passed.

### 2026-06-29 continuation: NowPlayingQueueCoordinator removal

Continued Phase 6 stabilization by deleting the remaining dead queue
coordinator binding:

- Confirmed `NowPlayingQueueCoordinator` had already been reduced to a queue
  intent listener only.
- Confirmed `MainActivityBase.mountNavHostShell()` installs the actual
  `queueViewModel.bindIntentListener(...)` path after coordinator setup, so the
  coordinator listener was overwritten and no longer the live owner.
- Removed the `nowPlayingQueueCoordinator` field, `installCoordinators()` dead
  setup method, and `NowPlayingQueueCoordinator.kt`.
- Updated `MainActivityArchitectureContractTest` to guard against restoring the
  coordinator file, field, constructor, method, or `nowPlayingQueueCoordinator.*`
  calls while keeping assertions for the actual queue intent path.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.QueueActionControllerTest --tests app.yukine.NowPlayingViewModelTest
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug
```

Result: all passed.

### 2026-06-29 continuation: NowPlayingQueueCoordinator surface reduction

Continued Phase 6 stabilization by shrinking `NowPlayingQueueCoordinator` to
the only live path currently called by `MainActivityBase`:

- Kept `bindQueueIntentListener()` as the coordinator's active responsibility.
- Removed unused coordinator-owned `handleNowPlayingEvent(...)`,
  `handleNowPlayingEffects()`, `bindQueueViewModelInputs(...)`, and
  `renderQueue(...)` methods.
- Reduced the constructor to `NowPlayingViewModel`, `QueueViewModel`,
  `QueueActionController`, and the queue-intent listener.
- Removed stale constructor dependencies on `NowPlayingStateController`,
  `QueueRenderController`, `MainPlaybackStore`, `MainLibraryStore`,
  `MainSettingsStore`, and `StatusMessageController`.
- Reduced `NowPlayingQueueCoordinator.Listener` to the three callbacks still
  used by queue intents: play, add-to-playlist, and app back.
- Updated `MainActivityArchitectureContractTest` to guard against these
  rendering/effect/status/store dependencies returning to the coordinator.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.QueueActionControllerTest --tests app.yukine.NowPlayingViewModelTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional verification after this continuation

This continuation moved `RequestedRoutePolicy.kt` and `BackgroundPreviewScreen.kt`
into `:feature:ui-common`, and kept background preview localization in the app
activity through `BackgroundPreviewLabels`.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:ui-common:compileDebugKotlin :feature:ui-common:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

Result: both passed.

Attempted focused unit test:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.ui.RequestedRoutePolicyTest --console=plain
```

Result: blocked before executing the target test by existing
`:app:compileDebugUnitTestKotlin` migration fallout, including stale
`StreamingQualityPreference` test references and cross-module `internal`
visibility issues in playback/streaming/ui tests.

### Additional continuation: shared streaming data-path metadata

Phase 5.6 should not continue by adding broad ViewModel wrapper interfaces for every
app-owned navigation dependency. The current safe slice was to remove duplicated
streaming data-path quality parsing from app navigation and playback service code.

- Added `StreamingDataPathMetadata` in `:core:common`.
- `EchoNavGraph` now uses `StreamingDataPathMetadata.quality(...)` instead of a local
  `qualityFromDataPath(...)` helper.
- `EchoPlaybackService` uses the same helper for streaming recovery diagnostics.
- Added `StreamingDataPathMetadataTest` under `:core:common`.
- `rg "qualityFromDataPath"` now returns no remaining production references.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :core:common:testDebugUnitTest --tests app.yukine.common.StreamingDataPathMetadataTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed. The app compile emitted only existing Java deprecation notes.

### Additional continuation: onboarding UI moved to ui-common

The next safe Phase 5.5/5.6-aligned slice moved the pure onboarding Compose UI out of
`:app` without introducing a new owner or wrapper:

- Moved `OnboardingScreen.kt` from `app/src/main/java/app/yukine/ui` to
  `feature/ui-common/src/main/java/app/yukine/ui`, preserving the package
  `app.yukine.ui`.
- Removed the `AppLanguage` dependency from the moved UI by adding
  `StreamingUsageNoticeLabels`.
- `EchoAppHost` now resolves the onboarding streaming-usage notice labels through
  `AppLanguage` in `:app` and passes them into the pure UI screen.
- `StreamingSearchLabels` now carries the same notice labels so
  `StreamingSearchScreen` can keep reusing `StreamingUsageNotice(...)` without making
  `:feature:ui-common` depend back on app localization.
- `rg "AppLanguage" feature/ui-common/src/main/java` returns no production hits.

### Additional continuation: embedded artwork moved to core-common

The next dependency-direction cleanup moved embedded artwork handling to the existing
shared core module instead of keeping UI code dependent on data:

- Moved `EmbeddedArtwork.java` from `:feature:data` to `:core:common` as
  `app.yukine.common.EmbeddedArtwork`.
- Moved `EmbeddedArtworkTest` to `:core:common` and kept the defensive-copy cache
  behavior coverage.
- Updated data import/scanner paths, playback notification artwork, live lyrics
  notification artwork, track download artwork, playback service, and `ArtworkLoader`
  to use `app.yukine.common.EmbeddedArtwork`.
- Replaced `:feature:ui-common`'s `implementation project(":feature:data")` with
  `implementation project(":core:common")`.
- Updated `MainActivityArchitectureContractTest` path resolution so the existing
  embedded-artwork contract follows the new core-common owner.

### Additional continuation: repeat-mode constants moved to core-model

The next dependency-direction cleanup removed the remaining `:feature:playback`
dependency from `:feature:ui-common`:

- Moved `PlaybackRepeatMode.kt` from `:feature:playback` to `:core:model`, keeping
  the package `app.yukine.playback` to avoid import churn.
- `NowBar.kt` continues to consume the same constants, now through the existing
  `:core:model` API dependency.
- Removed `implementation project(":feature:playback")` from
  `feature/ui-common/build.gradle`.
- `:feature:ui-common` now depends on core modules plus Compose libraries, and no
  longer depends on `:feature:data` or `:feature:playback`.

### Additional continuation: streaming wire enums moved to core-model

The next Phase 5.6 preparation slice moved pure streaming wire enums to the existing
model module so app/UI/navigation code can reference provider and quality types without
depending on streaming implementation code:

- Added `core/model/src/main/java/app/yukine/streaming/StreamingEnums.kt`.
- Moved `StreamingProviderName`, `StreamingMediaType`, `StreamingAudioQuality`,
  `StreamingProviderStatus`, `StreamingErrorCode`, and `StreamingAuthKind` out of
  `feature/streaming/src/main/java/app/yukine/streaming/StreamingContracts.kt`.
- Kept package `app.yukine.streaming` unchanged to avoid import churn.
- Added `StreamingEnumsTest` in `:core:model` for provider alias, quality, and error
  code wire-name behavior.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :core:model:testDebugUnitTest --tests app.yukine.streaming.StreamingEnumsTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed. Streaming compile emitted an existing Kotlin warning in
`StreamingPlaylistImporter.kt`; app compile emitted an existing Java deprecation warning.

### Additional continuation: streaming data-path identity parsing moved to core-common

The next safe Phase 5.6 preparation slice extended the existing shared
`StreamingDataPathMetadata` helper so app navigation/Now Playing code can inspect
streaming identities without calling the streaming implementation adapter:

- `StreamingDataPathMetadata` now owns `isStreamingTrack(...)`, `provider(...)`,
  `providerName(...)`, and `providerTrackId(...)` in addition to `quality(...)`.
- `StreamingPlaybackAdapter` keeps its public API, but delegates data-path identity
  parsing to `:core:common`.
- `NowPlayingDestination` no longer imports or calls `StreamingPlaybackAdapter` just to
  render source info/source choices.
- `StreamingDataPathMetadataTest` now covers provider aliases, query/fragment trimming,
  and the QQ Music `songMid|mediaMid` case so `|` is preserved.

Verification:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :core:common:testDebugUnitTest --tests app.yukine.common.StreamingDataPathMetadataTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: streaming provider capability models moved to core-model

The next Phase 5.6 preparation slice moved pure provider capability modeling and
capability resolution out of the streaming implementation module:

- Added `core/model/src/main/java/app/yukine/streaming/StreamingProviderModels.kt`
  for `StreamingProviderCapabilities`, `StreamingAuthState`,
  `StreamingProviderDescriptor`, `StreamingProviderHealth`, and
  `StreamingProviderCapability`.
- Moved `StreamingCapabilityResolver` to `:core:model`, preserving package
  `app.yukine.streaming` to avoid import churn.
- Removed the old resolver file from `:feature:streaming` and removed the moved
  provider model definitions from `StreamingContracts.kt`.
- Moved `StreamingCapabilityResolverTest` to `:core:model`.
- Added a `MainActivityArchitectureContractTest` guard so the resolver boundary does
  not drift back into `:feature:streaming`.

Verification:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :core:model:testDebugUnitTest --tests app.yukine.streaming.StreamingCapabilityResolverTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### Additional continuation: streaming content/request models moved to core-model

The next Phase 5.6 preparation slice finished separating pure streaming data models
from streaming implementation code:

- Moved `StreamingTrack`, `StreamingAlbum`, `StreamingArtist`, `StreamingPlaylist`,
  `StreamingMvItem`, search/playlist/playback/auth request/result models,
  playback candidates, lyric sources, and gateway diagnostics to
  `core/model/src/main/java/app/yukine/streaming/StreamingContentModels.kt`.
- Kept package `app.yukine.streaming` unchanged, so app/UI call sites do not need
  import churn while the implementation boundary changes underneath.
- `feature/streaming/src/main/java/app/yukine/streaming/StreamingContracts.kt` now
  contains only the `StreamingProvider` interface and its default behavior.
- Added `StreamingContentModelsTest` under `:core:model` to cover `StreamingTrack`
  stable keys, `StreamingSearchResult.unifiedItems`, and diagnostics cache-hit rate.
- Extended `MainActivityArchitectureContractTest` so streaming content models stay in
  `:core:model` and the provider interface stays in `:feature:streaming`.

Verification:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :core:model:testDebugUnitTest --tests app.yukine.streaming.StreamingContentModelsTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: streaming quality preference moved to core-common

The next Phase 5.6 preparation slice moved the remaining settings-facing streaming
quality helpers out of the streaming implementation module:

- Moved `StreamingQualityPreference.kt` to
  `core/common/src/main/java/app/yukine/streaming/StreamingQualityPreference.kt`.
- Moved its Android network helper `StreamingNetworkQuality.kt` to
  `:core:common` as well, keeping the package `app.yukine.streaming`.
- This keeps app settings/loading code on the same API while avoiding a dependency on
  `:feature:streaming` for preference normalization and quality ceiling mapping.
- Added `StreamingQualityPreferenceTest` under `:core:common` for normalization,
  stable option ordering, and `StreamingAudioQuality` mapping.
- Extended `MainActivityArchitectureContractTest` so these helpers stay out of
  `:feature:streaming`.

Verification:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :core:common:testDebugUnitTest --tests app.yukine.streaming.StreamingQualityPreferenceTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: track download file-name policy moved to core-common

The next Phase 5.6 cleanup moved a pure download naming policy out of `:app`
without changing its public API:

- Moved `TrackDownloadFileNamePolicy.kt` to
  `core/common/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt`.
- Kept package `app.yukine`, so `TrackDownloadManager` continues to call the
  same `TrackDownloadFileNamePolicy.audioFileName(...)`,
  `baseName(...)`, and `artworkFileName(...)` API.
- Moved `TrackDownloadFileNamePolicyTest` to `:core:common`, preserving the
  existing UTF-8 filename behavior coverage.
- Extended `MainActivityArchitectureContractTest` so the policy stays out of
  `:app`, and updated `HandoffExperienceContractTest` path fallback for the
  new core-common owner.

Verification:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :core:common:testDebugUnitTest --tests app.yukine.TrackDownloadFileNamePolicyTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.HandoffExperienceContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: streaming track match policy moved to core-model

The next Phase 5.6 cleanup moved a pure matching policy out of the streaming
implementation module:

- Moved `StreamingTrackMatchPolicy.kt` to
  `core/model/src/main/java/app/yukine/streaming/StreamingTrackMatchPolicy.kt`.
- Kept package `app.yukine.streaming`, so app streaming search/recommendation code
  and `StreamingPlaylistImporter` continue to call the same API.
- This leaves provider implementation in `:feature:streaming` while search query
  normalization and local/streaming candidate matching live next to the streaming
  model types they operate on.
- Added `StreamingTrackMatchPolicyTest` under `:core:model`.
- Extended `MainActivityArchitectureContractTest` so the policy stays out of
  `:feature:streaming`.

Verification:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :core:model:testDebugUnitTest --tests app.yukine.streaming.StreamingTrackMatchPolicyTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: streaming input parsers moved to core-common

The next Phase 5.6 cleanup moved pure user-input parsers out of the streaming
implementation module:

- Moved `StreamingPlaylistLinkParser.kt` to
  `core/common/src/main/java/app/yukine/streaming/StreamingPlaylistLinkParser.kt`.
- Moved `StreamingCookieHeaderParser.kt` to
  `core/common/src/main/java/app/yukine/streaming/StreamingCookieHeaderParser.kt`.
- Kept package `app.yukine.streaming`, so `StreamingViewModel` and streaming
  implementation call sites keep the same parser APIs.
- Moved `StreamingPlaylistLinkParserTest` and `StreamingCookieHeaderParserTest`
  to `:core:common`.
- Extended `MainActivityArchitectureContractTest` so these parsers stay out of
  `:feature:streaming`.

Verification:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :core:common:testDebugUnitTest --tests app.yukine.streaming.StreamingPlaylistLinkParserTest --tests app.yukine.streaming.StreamingCookieHeaderParserTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: app data-path parsing uses core-common metadata

The next Phase 5.6 cleanup removed app-only pure data-path parsing calls to the
streaming playback adapter where no playback-track adaptation is needed:

- `NowPlayingStateFactory` now renders source info through
  `StreamingDataPathMetadata.provider(...)`.
- `StreamingTrackMatchUseCase` now reads direct provider/providerTrackId values
  through `StreamingDataPathMetadata`, while keeping its Netease URL fallback and
  repository match-store behavior unchanged.
- `TrackShareManager` now reads provider/providerTrackId fallback values through
  `StreamingDataPathMetadata`, while keeping its share-specific providerTrackId
  cleanup for `|`, query, and fragment suffixes.
- Extended `MainActivityArchitectureContractTest` so these app call sites do not
  drift back to `StreamingPlaybackAdapter` for pure metadata reads.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.NowPlayingStateFactoryTest --tests app.yukine.StreamingTrackMatchUseCaseTest --tests app.yukine.TrackShareManagerTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: Now Playing destination moved to navigation

The next Phase 5.6 slice moved a now-pure destination and its navigation-facing
contracts into `:feature:navigation` without adding a new owner or wrapper:

- Moved `NowPlayingDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/now/NowPlayingDestination.kt`,
  preserving package `app.yukine.now`.
- Added `NowPlayingContracts.kt` in `:feature:navigation` for
  `NowPlayingEvent`, `RepeatModeUi`, `LyricsUiState`, `NowPlayingUiState`, and
  `NowPlayingScreenStateProvider`, preserving package `app.yukine` so app and Java
  call sites keep the same FQNs.
- Removed the moved state/event declarations from `NowPlayingViewModel`; it now
  remains focused on behavior/effects and implements the navigation-owned provider.
- Added `implementation project(":core:common")` to `feature/navigation/build.gradle`
  because the moved destination uses `StreamingDataPathMetadata`.
- Extended `MainActivityArchitectureContractTest` so the destination/provider stay out
  of `:app` and the contracts stay in `:feature:navigation`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.NowPlayingViewModelTest --tests app.yukine.NowPlayingStateControllerTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: Now Playing provider naming in app nav host

The next Phase 5.6 cleanup kept `EchoNavGraph` / `EchoNavHostState` in `:app`
but removed the remaining concrete ViewModel wording from the app nav host's
Now Playing state boundary:

- Renamed `EchoNavHostState.nowPlayingViewModel` to `nowPlayingStateProvider`.
- Kept the type as `NowPlayingScreenStateProvider`, owned by `:feature:navigation`.
- Updated `EchoNavGraph` source switching to call
  `hostState.nowPlayingStateProvider.switchSource(...)`.
- Updated navigation tests and `MainActivityArchitectureContractTest` so this path
  does not drift back to `hostState.nowPlayingViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: Home destination moved to navigation

The next Phase 5.6 slice moved the Home destination into `:feature:navigation`
and kept the app ViewModel as the behavior/state owner:

- Moved `HomeDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/home/HomeDestination.kt`,
  preserving package `app.yukine.home`.
- Added `HomeDashboardContracts.kt` in `:feature:navigation` for
  `HomeDashboardDestinationState` and `emptyHomeDashboardActions()`.
- Removed those state/action default declarations from `HomeDashboardViewModel`;
  the ViewModel now consumes the navigation-owned contract and continues to own
  updates/fetching.
- Extended `MainActivityArchitectureContractTest` so `HomeDestination` stays out
  of `:app` and the Home dashboard contract stays in `:feature:navigation`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.home.HomeDestinationTest --tests app.yukine.HomeDashboardViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: Settings destination moved to navigation

The next Phase 5.6 slice moved Settings rendering into `:feature:navigation`
without moving the full app-owned Settings state:

- Moved `SettingsDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/settings/SettingsDestination.kt`,
  preserving package `app.yukine.settings`.
- Added `SettingsDestinationContracts.kt` in `:feature:navigation` with a narrow
  `SettingsDestinationState` projection for title, metrics, and actions.
- Kept `SettingsState` in `:app`, but made it implement
  `SettingsDestinationState`; `SettingsViewModel.state` remains the single
  authoritative state source.
- Extended `MainActivityArchitectureContractTest` so `SettingsDestination` stays
  out of `:app` and does not import app `SettingsState`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.settings.SettingsDestinationTest --tests app.yukine.SettingsViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### Additional continuation: Queue destination moved to navigation

The next Phase 5.6 slice moved the Queue destination into `:feature:navigation`
without moving queue behavior or introducing a new state source:

- Moved `QueueDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/queue/QueueDestination.kt`,
  preserving package `app.yukine.queue`.
- Added `QueueDestinationContracts.kt` in `:feature:navigation` with
  `QueueDestinationState` and the narrow `QueueDestinationStateProvider`.
- Removed the app-owned `MainActivityQueueUiState` wrapper from
  `MainActivityViewModel.kt`; `QueueViewModel` now exposes
  `StateFlow<QueueDestinationState>` and implements the provider.
- Kept `QueueViewModel` in `:app` as the only queue state/intent owner; the
  moved destination only collects the provider flows and forwards row actions.
- Extended `MainActivityArchitectureContractTest` so `QueueDestination` stays
  out of `:app`, the queue contract stays in `:feature:navigation`, and
  `QueueViewModel` does not drift back to `MainActivityQueueUiState`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.queue.QueueDestinationTest --tests app.yukine.queue.QueueViewModelTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed. A first combined focused test command also produced 0
failure/0 error XML for the same Queue/Nav test classes, but the outer shell
timeout cut off the Gradle process; the split reruns above exited successfully.

### Additional continuation: Collections destination moved to navigation

The next Phase 5.6 slice moved Collections rendering into `:feature:navigation`
while keeping app `CollectionsViewModel` as the state/action owner:

- Moved `CollectionsDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/collections/CollectionsDestination.kt`,
  preserving package `app.yukine.collections`.
- Added `CollectionsDestinationContracts.kt` in `:feature:navigation` with the
  narrow `CollectionsDestinationStateProvider`.
- Kept `CollectionsViewModel.screen` as the single source of
  `CollectionsUiState`; the ViewModel now implements the provider.
- Extended `MainActivityArchitectureContractTest` so the destination stays out
  of `:app`, the contract stays in `:feature:navigation`, and the destination
  does not import the concrete `CollectionsViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.collections.CollectionsDestinationTest --tests app.yukine.CollectionsViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed. The focused UI/navigation test command emitted existing
Compose `createComposeRule` deprecation warnings only.

### 2026-06-29 continuation: Library destinations moved to navigation

The next Phase 5.6 slice moved the Library group and track-list render
destinations into `:feature:navigation` while keeping `LibraryViewModel` in
`:app` as the single content/chrome state owner:

- Added `LibraryDestinationContracts.kt` in `:feature:navigation` with
  `LibraryGroupsDestinationState` and `LibraryTrackListDestinationState`.
  These are pure destination render-state models backed only by ui-common
  row/action/label types.
- Removed those two state data classes from `MainActivityViewModel.kt`.
- Moved `LibraryGroupsDestination.kt` and `LibraryTrackListDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/library`, preserving package
  `app.yukine.library`.
- Kept `LibraryViewModel.libraryGroups` and `LibraryViewModel.trackList` as the
  authoritative `StateFlow` sources; no Library behavior moved.
- Extended `MainActivityArchitectureContractTest` so the two destinations stay
  out of `:app`, and the Library destination state contracts stay in
  `:feature:navigation`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.library.LibraryGroupsDestinationTest --tests app.yukine.library.LibraryTrackListDestinationTest --tests app.yukine.LibraryViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Network sources destination moved to navigation

The next Phase 5.6 slice moved the Network sources sub-page render boundary
into `:feature:navigation` while keeping Network behavior in `:app`:

- Added `NetworkSourcesDestinationContracts.kt` in `:feature:navigation` with
  the pure `NetworkSourcesUiState` render model.
- Removed `NetworkSourcesUiState` from `NetworkSourcesViewModel.kt`; the
  ViewModel still owns all state updates and exposes
  `StateFlow<NetworkSourcesUiState>`.
- Moved `NetworkSourcesDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/network/NetworkSourcesDestination.kt`,
  preserving package `app.yukine.network`.
- Kept `NetworkDestination.kt` in `:app` because its streaming branch still
  depended on the app graph wiring the streaming screen/state.
- Extended `MainActivityArchitectureContractTest` so the sources destination
  stays out of `:app`, and the sources render-state contract stays in
  `:feature:navigation`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.network.NetworkSourcesDestinationTest --tests app.yukine.NetworkSourcesViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Network menu destination moved to navigation

The next Phase 5.6 slice moved the Network home/WebDAV menu render boundary
into `:feature:navigation` while keeping menu behavior in `:app`:

- Added `NetworkMenuDestinationContracts.kt` in `:feature:navigation` with
  the pure `NetworkMenuUiState` render model.
- Removed `NetworkMenuUiState` from `NetworkMenuViewModel.kt`; the ViewModel
  still owns all menu updates and exposes `StateFlow<NetworkMenuUiState>`.
- Added `NetworkMenuDestination.kt` in
  `feature/navigation/src/main/java/app/yukine/network`, preserving package
  `app.yukine.network`.
- Updated app-owned `NetworkDestination.kt` to delegate the Network home/WebDAV
  menu branches to `NetworkMenuDestination`.
- Kept full `NetworkDestination.kt` in `:app` because its streaming branch still
  depended on the app graph wiring the streaming screen/state.
- Extended `MainActivityArchitectureContractTest` so the menu destination and
  state contract stay in `:feature:navigation`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.NetworkMenuEventControllerTest --tests app.yukine.NetworkMenuEventControllerPlaybackTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Network destination dispatch moved to navigation

The next Phase 5.6 slice moved the Network route dispatch destination into
`:feature:navigation` without moving app-owned Streaming UI/state:

- Moved `NetworkDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/network/NetworkDestination.kt`,
  preserving package `app.yukine.network`.
- Changed the destination to depend on `networkPage`, `NetworkMenuUiState`,
  `StateFlow<NetworkSourcesUiState>`, and `StateFlow<LibraryTrackListDestinationState>`.
- Kept the Streaming branch app-owned by injecting a `streamingContent`
  composable lambda from `EchoNavGraph`; navigation does not import
  `EchoNavHostState`, `StreamingSearchScreen`, or `StreamingSearchState`.
- Extended `MainActivityArchitectureContractTest` so the destination stays out
  of `:app` and the app graph remains the only place composing the Streaming branch.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Downloads destination moved to navigation

The next Phase 5.6 slice moved the Downloads render destination into
`:feature:navigation` while keeping the download manager and platform picker in
`:app`:

- Added `DownloadsDestinationContracts.kt` in `:feature:navigation` with
  `DownloadsDestinationActions`, a narrow action bundle for refresh, directory,
  item, batch, and platform picker actions.
- Moved `DownloadsDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/downloads/DownloadsDestination.kt`,
  preserving package `app.yukine.downloads`.
- Changed the destination to consume `StateFlow<DownloadsUiState>`,
  `Flow<Unit>` open-directory requests, and `DownloadsDestinationActions`.
- Kept `DownloadsViewModel`, `DownloadsEffect`, `TrackDownloadManager`, and the
  document picker in `:app`; `EchoNavGraph` maps app effects/actions into the
  pure destination contract.
- Extended `MainActivityArchitectureContractTest` so the Downloads destination
  stays out of `:app` and does not import app download owners.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.DownloadsViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Unified search UI and destination moved

The next Phase 5.5/5.6 slice moved unified Search rendering out of `:app`
without moving app-owned search/streaming state owners:

- Added `UnifiedSearchContracts.kt` in `:feature:ui-common` with the pure
  `UnifiedSearchUiState` render model.
- Moved `SearchScreen.kt` / `UnifiedSearchScreen` to
  `feature/ui-common/src/main/java/app/yukine/ui/SearchScreen.kt`.
- Changed `UnifiedSearchScreen` to consume `UnifiedSearchUiState` and
  `UnifiedSearchStreamingState` instead of `SearchViewModel` and
  `StreamingSearchState`.
- Moved `SearchDestination.kt` to
  `feature/navigation/src/main/java/app/yukine/search/SearchDestination.kt`,
  preserving package `app.yukine.search`.
- Kept `SearchViewModel` and `StreamingViewModel` in `:app`; `EchoNavGraph`
  maps streaming state into `UnifiedSearchStreamingState`.
- Extended `MainActivityArchitectureContractTest` so the unified Search screen
  stays out of `:app`, the destination stays in `:feature:navigation`, and only
  app graph sees app-owned streaming/search state.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:ui-common:compileDebugKotlin :feature:ui-common:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Streaming search screen moved to UI common

The next Phase 5.5 cleanup moved the remaining Streaming search screen and its
pure render state out of `:app`:

- Added `StreamingPlaylistImportSummary.kt` in `:core:model` and changed
  `StreamingPlaylistImporter` to return that top-level pure model instead of a
  nested importer type.
- Added `StreamingSearchContracts.kt` in `:feature:ui-common` with
  `StreamingSearchState` and `StreamingSearchAuthLaunch`.
- Moved `StreamingSearchScreen.kt` to
  `feature/ui-common/src/main/java/app/yukine/ui/StreamingSearchScreen.kt`,
  preserving package `app.yukine.ui`.
- Kept `StreamingViewModel`, streaming action handlers, repository wiring, and
  auth/platform launch behavior in `:app`.
- Extended `MainActivityArchitectureContractTest` so streaming state contracts
  and screen rendering stay out of `:app` while the ViewModel remains the owner.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :core:model:compileDebugKotlin :core:model:compileDebugJavaWithJavac :feature:ui-common:compileDebugKotlin :feature:ui-common:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :feature:streaming:compileDebugKotlin :feature:streaming:compileDebugJavaWithJavac :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --tests app.yukine.StreamingSearchScreenStatusTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Playback snapshot provider narrowed

The next Phase 5.6 cleanup kept `EchoNavGraph` / `EchoNavHostState` in `:app`
but narrowed the playback state boundary:

- Changed `EchoNavHostState` to accept `PlaybackSnapshotProvider` directly
  instead of a concrete `PlaybackViewModel`.
- Kept `PlaybackViewModel` in `:app` as the playback state owner and interface
  implementation; `MainActivityBase` still passes the existing instance into
  the host state constructor.
- Updated navigation tests and architecture contract tests so the host state
  cannot drift back to importing or exposing `PlaybackViewModel`.
- Updated legacy handoff/experience test paths to read the moved
  `feature/ui-common` `StreamingSearchScreen`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.HandoffExperienceContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: unused MainActivityViewModel removed from nav host

The next Phase 5.6 cleanup removed a stale app ViewModel dependency from the
remaining app-owned navigation host:

- Removed the unused `mainViewModel` constructor property from `EchoNavHostState`.
- Updated `MainActivityBase` and navigation test fixtures to stop passing
  `MainActivityViewModel` into the host state.
- Extended `MainActivityArchitectureContractTest` so the host state cannot drift
  back to importing or exposing `MainActivityViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: navigation route state narrowed

The next Phase 5.6 cleanup removed another concrete ViewModel dependency from
the remaining app-owned navigation host:

- Changed `EchoNavHostState` to accept `StateFlow<NavigationRouteState>`
  instead of concrete `NavigationViewModel`.
- Updated `EchoNavGraph` to collect `hostState.routeState` for Network route
  rendering.
- Kept `NavigationViewModel` and `MainRouteController` in `:app` as the route
  state owner/persistence path; `MainActivityBase` passes
  `navigationViewModel.state` into the host.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing `NavigationViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Collections provider narrowed in nav host

The next Phase 5.6 cleanup reused the existing Collections destination
contract to remove another concrete ViewModel dependency from the app nav host:

- Changed `EchoNavHostState.collectionsViewModel` to
  `collectionsStateProvider: CollectionsDestinationStateProvider`.
- Updated `EchoNavGraph` to call
  `CollectionsDestination(hostState.collectionsStateProvider)`.
- Kept `CollectionsViewModel` in `:app` as the collections state/action owner
  and existing `CollectionsDestinationStateProvider` implementation.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing `CollectionsViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Library state flows narrowed in nav host

The next Phase 5.6 cleanup removed the concrete Library ViewModel dependency
from the app nav host while keeping Library state ownership in `:app`:

- Changed `EchoNavHostState` to accept
  `StateFlow<LibraryGroupsDestinationState>` and
  `StateFlow<LibraryTrackListDestinationState>` instead of `LibraryViewModel`.
- Updated `EchoNavGraph` to pass those flows into `LibraryGroupsDestination`,
  `LibraryTrackListDestination`, and `NetworkDestination`.
- Kept `LibraryViewModel` in `:app` as the Library state/action owner;
  `MainActivityBase` passes `libraryViewModel.getLibraryGroups()` and
  `libraryViewModel.getTrackList()` into the nav host.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing `LibraryViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
git diff --check
```

Result: all passed. `git diff --check` reported only existing LF/CRLF
normalization warnings.

### 2026-06-29 continuation: Home dashboard state narrowed in nav host

The next Phase 5.6 cleanup removed the concrete Home dashboard ViewModel
dependency from the app nav host while preserving Home ownership in `:app`:

- Changed `EchoNavHostState` to accept
  `StateFlow<HomeDashboardDestinationState>` instead of
  `HomeDashboardViewModel`.
- Updated `EchoNavGraph` to pass `hostState.homeDashboardState` to
  `HomeDestination`.
- Kept `HomeDashboardViewModel` in `:app` as the Home state/action owner;
  `MainActivityBase` passes `homeDashboardViewModel.getUiState()` into the
  nav host.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing `HomeDashboardViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Network state flows narrowed in nav host

The next Phase 5.6 cleanup removed concrete Network ViewModel dependencies
from the app nav host while preserving Network ownership in `:app`:

- Changed `EchoNavHostState` to accept `StateFlow<NetworkMenuUiState>` and
  `StateFlow<NetworkSourcesUiState>` instead of `NetworkMenuViewModel` and
  `NetworkSourcesViewModel`.
- Updated `EchoNavGraph` to collect `hostState.networkMenuState` and pass
  `hostState.networkSourcesState` to `NetworkDestination`.
- Kept `NetworkMenuViewModel` and `NetworkSourcesViewModel` in `:app` as the
  Network state/action owners; `MainActivityBase` passes their `uiState` flows
  into the nav host.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing concrete Network ViewModels.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Streaming state flow narrowed in nav host

The next Phase 5.6 cleanup removed the concrete Streaming ViewModel
dependency from the app nav host while preserving Streaming ownership in
`:app`:

- Changed `EchoNavHostState` to accept
  `StateFlow<StreamingSearchState>` instead of `StreamingViewModel`.
- Updated `EchoNavGraph` to collect `hostState.streamingState` for both the
  Network streaming page and the unified Search page.
- Kept `StreamingViewModel` in `:app` as the streaming search/auth/playback
  and playlist state/action owner; `MainActivityBase` passes
  `streamingViewModel.getStreaming()` into the nav host.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing concrete `StreamingViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Settings state and scroll narrowed in nav host

The next Phase 5.6 cleanup removed the concrete Settings ViewModel dependency
from the app nav host while preserving Settings ownership in `:app`:

- Changed `EchoNavHostState` to accept `StateFlow<SettingsState>` and
  `SettingsListScrollState` instead of `SettingsViewModel`.
- Updated `EchoNavGraph` to collect `hostState.settingsState` for page
  backgrounds / now-playing gesture preferences and to pass
  `hostState.settingsState` plus `hostState.settingsScrollState` into
  `SettingsDestination`.
- Kept `SettingsViewModel` in `:app` as the settings state/action/effect owner;
  `MainActivityBase` passes `settingsViewModel.getState()` and
  `settingsViewModel.getScrollState()` into the nav host.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing concrete `SettingsViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Downloads state/effect/actions narrowed in nav host

The next Phase 5.6 cleanup removed the concrete Downloads ViewModel
dependency from the app nav host and removed the Downloads-only picker action
parameter from `EchoAppHost` / `EchoNavHostBridge`:

- Changed `EchoNavHostState` to accept `StateFlow<DownloadsUiState>`,
  `Flow<Unit>` open-directory requests, and `DownloadsDestinationActions`
  instead of concrete `DownloadsViewModel`.
- Moved `DownloadsEffect.OpenDirectoryPicker -> Flow<Unit>` mapping into
  `DownloadsViewModel.openDirectoryRequests()`, keeping the effect projection
  with the app-owned Downloads state/action owner.
- Updated `EchoNavGraph` so `DownloadsDestination` consumes
  `hostState.downloadsState`, `hostState.downloadsOpenDirectoryRequests`, and
  `hostState.downloadsActions`; nav graph no longer imports
  `DownloadsViewModel` or `DownloadsEffect`.
- Removed `openDownloadDirectoryPickerAction` from the Compose host/bridge
  call path; `MainActivityBase` now includes the document picker launch in the
  `DownloadsDestinationActions` it passes through the host state.
- Extended `MainActivityArchitectureContractTest` so the nav host cannot drift
  back to importing or exposing concrete `DownloadsViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.DownloadsViewModelTest --console=plain
```

Result: all passed. The first focused-test attempt used the same test set but
hit the local command timeout before Gradle returned a result; rerunning with a
longer timeout passed.

### 2026-06-29 continuation: Search state narrowed in nav host

The next Phase 5.6 cleanup removed the concrete Search ViewModel from the
Compose host / bridge / nav graph parameter chain while preserving Search
ownership in `:app`:

- Changed `EchoNavHostState` to accept `StateFlow<UnifiedSearchUiState>`
  instead of passing `SearchViewModel` through `EchoAppHost` and
  `EchoNavHostBridge`.
- Updated `EchoNavGraph` so `SearchDestination` consumes
  `hostState.searchState`; the nav graph no longer imports or references
  `SearchViewModel`.
- Removed `EchoNavHostMount.searchViewModel()` and the matching
  `EchoNavHostBridge` parameter, shortening the Compose shell handoff by one
  app ViewModel parameter.
- Kept `SearchViewModel` in `:app` as the unified search state/action owner;
  `MainActivityBase` passes `searchViewModel.getUiState()` into the nav host
  and still updates query/results/actions through the ViewModel.
- Extended `MainActivityArchitectureContractTest` so the shell cannot drift
  back to passing concrete `SearchViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.SearchViewModelTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Queue provider narrowed in nav host

The next Phase 5.6 cleanup removed the concrete Queue ViewModel from the
Compose host / bridge / nav graph parameter chain while preserving Queue
ownership in `:app`:

- Changed `EchoNavHostState` to accept `QueueDestinationStateProvider`; app
  `QueueViewModel` already implements this narrow provider contract.
- Updated `EchoNavGraph` so the queue bottom sheet calls
  `QueueDestination(hostState.queueStateProvider, ...)` instead of receiving
  a concrete `QueueViewModel` parameter.
- Removed `EchoNavHostMount.queueViewModel()` and the matching
  `EchoNavHostBridge` / `EchoNavGraph` parameters, shortening the Compose
  shell handoff by one app ViewModel parameter.
- Kept `QueueViewModel` in `:app` as the queue state/intent owner;
  `MainActivityBase` passes `queueViewModel` into `EchoNavHostState` as the
  provider and still binds queue inputs / intent listener in the app host.
- Extended `MainActivityArchitectureContractTest` so the shell cannot drift
  back to passing concrete `QueueViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.queue.QueueDestinationTest --tests app.yukine.queue.QueueViewModelTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Active download controller narrowed in nav host

The next Phase 5.6 cleanup removed the concrete download manager type from
the app nav host while preserving download ownership in `:app`:

- Changed `EchoNavHostState` to accept `TrackDownloadController?` instead of
  concrete `TrackDownloadManager?` for active-download polling.
- Updated `EchoNavGraph` to read `hostState.trackDownloadController.snapshot()`
  when deriving the active download badge/card state.
- Kept `TrackDownloadManager` in `:app` as the injected download queue,
  directory, and request owner; `MainActivityBase` still passes the same
  manager instance because it implements the narrower controller interface.
- Extended `MainActivityArchitectureContractTest` so the host state cannot
  drift back to importing or exposing concrete `TrackDownloadManager`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.DownloadsViewModelTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Home dashboard contract renamed away from Activity

The next Phase 5.6 cleanup removed the remaining Activity-prefixed Home
dashboard state name from the navigation contract while preserving the same
state structure and app-owned ViewModel behavior:

- Renamed `MainActivityHomeDashboardUiState` to
  `HomeDashboardDestinationState` in `:feature:navigation`.
- Updated `HomeDestination`, `HomeDashboardViewModel`, `EchoNavHostState`, and
  Home destination tests to use the destination-owned contract name.
- Extended `MainActivityArchitectureContractTest` so the Home contract cannot
  drift back to declaring `MainActivityHomeDashboardUiState`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.home.HomeDestinationTest --tests app.yukine.HomeDashboardViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Library destination contracts renamed away from Activity

The next Phase 5.6 cleanup removed the remaining Activity-prefixed Library
destination state names from `:feature:navigation` while preserving the same
state fields and app-owned `LibraryViewModel` behavior:

- Renamed `MainActivityLibraryGroupsUiState` to
  `LibraryGroupsDestinationState`.
- Renamed `MainActivityTrackListUiState` to
  `LibraryTrackListDestinationState`.
- Updated Library destinations, `NetworkDestination`, `LibraryViewModel`,
  `EchoNavGraph`, `EchoNavHostState`, app host construction, and focused tests
  to use the destination-owned names.
- Extended `MainActivityArchitectureContractTest` so the Library contracts
  cannot drift back to declaring the old `MainActivity*` state names.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.library.LibraryGroupsDestinationTest --tests app.yukine.library.LibraryTrackListDestinationTest --tests app.yukine.LibraryViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Streaming search contracts renamed away from Activity

The next Phase 5.6 cleanup removed the remaining Activity-prefixed Streaming
search UI contract names from `:feature:ui-common` while preserving the same
state fields and app-owned `StreamingViewModel` behavior:

- Renamed `MainActivityStreamingState` to `StreamingSearchState`.
- Renamed `MainActivityStreamingAuthLaunch` to `StreamingSearchAuthLaunch`.
- Updated `StreamingSearchScreen`, `StreamingViewModel`, auth launch/action
  handlers, `EchoNavGraph`, `EchoNavHostState`, app host construction, and
  focused tests to use the UI contract names.
- Extended `MainActivityArchitectureContractTest` so the streaming contracts
  cannot drift back to declaring the old `MainActivity*` state names.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --tests app.yukine.DefaultStreamingSearchActionHandlerTest --tests app.yukine.StreamingEventControllersTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Navigation route state renamed away from Activity

The next Phase 5.6 cleanup removed the stale Activity-prefixed route state
name while keeping route ownership and persistence in the existing app owners:

- Renamed `MainActivityRouteState` to `NavigationRouteState`.
- Renamed `MainActivityRouteStateStore` to `NavigationRouteStateStore`.
- Updated `NavigationViewModel`, `MainRouteController`, `EchoNavHostState`,
  route controller tests, and architecture contract assertions.
- Kept the store in `:app` for this slice because it still uses app-owned route
  default normalization from `MainRoutes` and `LibraryGrouping`; the pure
  `NavigationRouteState` contract was moved to `:feature:navigation` in the
  later continuation below.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainRouteControllerTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Playback view state renamed away from Activity

The next Phase 5.6 cleanup removed the remaining stale Activity-prefixed playback
view-state name without changing playback ownership:

- Renamed `MainActivityPlaybackState` to `PlaybackViewState`.
- Kept the state in `PlaybackViewModel`; `MainPlaybackStore` still reads through
  `PlaybackViewModel.playback`, and `MainActivityViewModel` remains free of playback
  state.
- Updated `MainActivityArchitectureContractTest` so the old
  `MainActivityPlaybackState` name cannot drift back into the playback owner.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.PlaybackViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Library store state renamed away from Activity

The next Phase 5.6 cleanup removed the remaining stale Activity-prefixed
library aggregate state name without changing library ownership:

- Renamed `MainActivityLibraryState` to `LibraryStoreState`.
- Kept the state in `MainActivityViewModel`; `MainLibraryStore` remains the
  compatibility facade reading from `MainActivityViewModel.library`.
- Updated `MainActivityViewModelTest` and `MainActivityArchitectureContractTest`
  so the old `MainActivityLibraryState` name cannot drift back into the app
  library state owner.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityViewModelTest --tests app.yukine.NetworkLibraryStoreDirectAccessTest --tests app.yukine.PlayHistoryActionControllerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Navigation route state contract moved

The next Phase 5.6 cleanup moved the pure route-state contract out of `:app`
while keeping route persistence in the existing app owners:

- Moved `NavigationRouteState` to `:feature:navigation`.
- Kept `NavigationRouteStateStore` in `MainActivityViewModel.kt` because it
  still owns `SavedStateHandle` restore/save compatibility and app route
  default normalization.
- Replaced the state default's app-internal `LibraryGrouping.SONGS` dependency
  with a local pure `"songs"` default so `:feature:navigation` does not depend
  back on app code.
- Updated `MainActivityArchitectureContractTest` so the route-state contract
  stays in `:feature:navigation` and does not drift back into
  `MainActivityViewModel`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainRouteControllerTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: Settings host chrome state narrowed

The next Phase 5.6 cleanup removed the app-owned `SettingsState` type from
the nav host while preserving Settings ownership in the app ViewModel:

- Added `SettingsChromeState` to `:feature:navigation` for the shell-only
  page background and Now Playing gesture values.
- Changed `EchoNavHostState.settingsState` to `StateFlow<SettingsDestinationState>`
  and added `settingsChromeState: StateFlow<SettingsChromeState>`.
- `EchoNavGraph` now reads page backgrounds and Now Playing gestures from the
  chrome projection instead of `SettingsState.preferences`.
- `SettingsViewModel` continues to own full `SettingsState`, and now exposes a
  narrow `chromeState` projection for the shell.
- Updated `SettingsViewModelTest`, nav graph/bridge fixtures, and
  `MainActivityArchitectureContractTest` so this boundary cannot drift back to
  full app `SettingsState` in the nav host.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.SettingsViewModelTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: all passed.

### 2026-06-29 continuation: track download controller contract moved

The next Phase 5.6 cleanup moved the active-download control contract out of
`:app` while keeping the concrete download queue/platform owner unchanged:

- Added `TrackDownloadController` and `TrackDownloadActionResult` to
  `:feature:navigation` in `DownloadsDestinationContracts.kt`.
- Removed those contract definitions from `TrackDownloadManager.kt`.
- `TrackDownloadManager` still implements `TrackDownloadRequestQueue`, and app
  `TrackDownloadDirectoryController` / `TrackDownloadRequestQueue` continue to
  extend the moved navigation-facing controller.
- `EchoNavHostState` still accepts `TrackDownloadController?`, but the type no
  longer belongs to an app implementation file.
- Updated `MainActivityArchitectureContractTest` so the contract stays in
  `:feature:navigation` and cannot drift back into `TrackDownloadManager.kt`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.DownloadsViewModelTest --tests app.yukine.DownloadRequestControllerTest --tests app.yukine.navigation.EchoNavGraphTest --tests app.yukine.navigation.EchoNavHostBridgeTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
```

Result: both passed.

### 2026-06-29 continuation: navigation shell moved to feature module

The next Phase 5.6 cleanup moved the remaining navigation shell files out of
`:app` after their app-owned dependencies had been narrowed:

- Moved `EchoNavGraph.kt`, `EchoNavHostState.kt`, and `EchoNavHostBridge.kt` to
  `feature/navigation/src/main/java/app/yukine/navigation`.
- Kept the package `app.yukine.navigation`, so app wiring and tests keep the
  same imports.
- `EchoNavGraph` now compiles inside `:feature:navigation`, consuming only
  core model/common types, `:feature:ui-common` screens/contracts, and
  `:feature:navigation` destination/provider contracts.
- Updated `MainActivityArchitectureContractTest` to read the new module paths
  and assert the shell files do not drift back into `:app`.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :feature:navigation:compileDebugKotlin :feature:navigation:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
```

Result: both passed.

### 2026-06-29 continuation: Phase 6 coordinator reduction started

Phase 6 is now being applied through the 2026-06-27 stabilization pivot: reduce
existing MainActivityBase assembly and forwarding layers before adding new
AppEventBus or Coordinator infrastructure.

The first Phase 6-aligned cleanup removed six thin, stale, or duplicate coordinators:

- Deleted `DownloadsCoordinator`; its `downloadTrack(...)` method only forwarded
  to the existing `DownloadRequestController`, and its custom-directory method
  duplicated the existing `MainActivityBase.setCustomDownloadFolder(...)` path.
- Deleted `FileIOCoordinator`; it was a pure holder for
  `DocumentPickerController`, `BackgroundImagePickerController`, and
  `BackupRestoreLauncher`, with no behavior.
- Deleted stale `NetworkCoordinator`; `MainActivityBase` already owns the live
  network request/menu/source/render wiring directly and `NetworkCoordinator`
  was only constructed, never initialized or called.
- Deleted stale `LibraryCoordinator`; `MainActivityBase` still owns the active
  library load/render/collection private methods and the coordinator was only
  constructed, never called.
- Deleted stale `NavigationCoordinator`; route/back/search/swipe behavior still
  runs through the existing `MainActivityBase` private methods and the
  coordinator was only constructed, never called.
- Deleted duplicate `SettingsCoordinator`; `MainActivityBase` already constructs
  and binds `SettingsRuntimeApplier` directly, and the coordinator only rebound
  the same runtime effect listener while its `renderSettings(...)` method had no
  active caller.
- Updated `NowPlayingQueueCoordinator.Listener.downloadTrack(...)` wiring in
  `MainActivityBase` to call `downloadRequestController.downloadTrack(track)`
  directly, matching the other existing download call sites.
- Updated `MainActivityArchitectureContractTest` to guard against these
  forwarding-only coordinators returning.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.SettingsRuntimeApplierTest --tests app.yukine.SettingsViewModelTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
git diff --check -- app\src\main\java\app\yukine\MainActivityBase.java app\src\main\java\app\yukine\SettingsCoordinator.kt app\src\test\java\app\yukine\MainActivityArchitectureContractTest.java docs\CODEX_HANDOFF.md docs\CODEX_MODULARIZATION_HANDOFF_PHASE2_2026-06-28.md docs\MVVM_MIGRATION_HANDOFF.md docs\ARCHITECTURE_REMEDIATION_PLAN_2026-06-26.md
```

Result: all passed. `git diff --check` emitted only LF-to-CRLF working-copy warnings.

### 2026-06-29 continuation: PlaybackCoordinator surface reduction

Continued Phase 6 stabilization by shrinking `PlaybackCoordinator` instead of
adding a replacement assembly layer:

- Reduced `PlaybackCoordinator` from a 12-argument constructor to the three live
  dependencies it actually needs: `PlaybackStartController`,
  `PlaybackServiceConnectionController`, and its service visibility listener.
- Removed unused coordinator-owned playback entry/result methods:
  `playTrackListFromHost(...)`, `applyPlaybackActionResult(...)`, and
  `flushPendingPlayback()`.
- Removed the coordinator-local pending playback cache. Host playback now
  delegates to `PlaybackStartController.playTrackList(...)`, which already owns
  service start, pending-track save/clear, resolving status, streaming
  resolution, and result application.
- Updated `MainActivityBase` construction and
  `MainActivityArchitectureContractTest` guards so the coordinator does not
  regain `PlaybackActionController`, `PlaybackServiceHostController`,
  `PlaybackStateUpdateController`, `PlaybackStateEventController`,
  `MainPlaybackStore`, or `NowPlayingViewModel` dependencies.

Verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --max-workers=1 :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.PlaybackViewModelTest --tests app.yukine.NowPlayingViewModelTest --console=plain
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug --console=plain
```

Result: all passed.

## 1. 已完成工作总结

### 模块现状

| 模块 | 文件数 | 职责 |
|------|--------|------|
| `:core:model` | 25 | 纯领域模型 (Track, Playlist, PlaybackStateSnapshot, streaming wire/provider/content models, StreamingTrackMatchPolicy, StreamingPlaylistImportSummary 等) |
| `:core:common` | 9 | 跨模块接口/共享工具 (StreamingDataPathParser, StreamingDataPathMetadata, StreamingQualityPreference, StreamingNetworkQuality, StreamingPlaylistLinkParser, StreamingCookieHeaderParser, TrackDownloadFileNamePolicy, SecureSecretStore, EmbeddedArtwork) |
| `:feature:streaming` | 23 | 流媒体网关/适配器/Room 缓存 |
| `:feature:data` | 10 | 音乐库/数据库/WebDAV/文件导入 |
| `:feature:playback` | 33 | Media3 播放管理器 (Queue/State/Effect) |
| `:feature:ui-common` | 38 | 纯 Compose UI 组件/screen/labels/Search and Streaming search render contracts |
| `:feature:navigation` | 29 | route/scaffold/navigation shell primitives/NavGraph/NavHostState/NavHostBridge/Home + Settings + Now Playing + Queue + Downloads + active download controller + Search + Collections + Library + Network sources/menu/destination contracts |
| `:app` | 144 | Activity/Coordinator/ViewModel/app-owned state/action/platform owners/DI |

### 依赖图

```
:app ──→ :feature:playback ──→ :feature:data ──→ :core:model
  │            │                     │
  │            ├──→ :feature:streaming ──→ :core:model
  │            │            │
  │            └──→ :core:common ←──┘
  │
  ├──→ :feature:streaming
  ├──→ :feature:data
  ├──→ :feature:ui-common ──→ :core:model / :core:common
  ├──→ :feature:navigation ──→ :feature:ui-common / :core:model / :core:common
  ├──→ :core:model
  └──→ :core:common
```

### 关键架构决策

1. **包名保持**：移入新模块的文件保持原 Java 包名不变
2. **接口解耦**：`StreamingDataPathParser`（在 `:core:common`）打断 data→streaming 循环依赖
3. **方法重命名**：`StreamingPlaybackAdapter.providerName()` 现在是接口实现（返回 String?），枚举版本改名为 `streamingProviderName()`
4. **StreamingDataPathMetadata**：streaming dataPath 的 quality/provider/providerTrackId 解析已集中到 `:core:common`，app UI、分享卡片、曲目匹配不再为纯 metadata 读取调用 streaming adapter
5. **Streaming wire/provider/content models**：`StreamingProviderName`、`StreamingAudioQuality`、`StreamingProviderDescriptor`、`StreamingProviderCapability`、`StreamingCapabilityResolver`、`StreamingTrack`、`StreamingSearchResult` 等纯模型/能力判断已上移到 `:core:model`（包名仍为 `app.yukine.streaming`）
6. **StreamingTrackMatchPolicy**：本地曲目和流媒体候选匹配策略已上移到 `:core:model`，app search/recommendation 与 streaming importer 共享同一纯策略
7. **Streaming input parsers**：`StreamingPlaylistLinkParser` 和 `StreamingCookieHeaderParser` 已上移到 `:core:common`，app 的歌单链接/Cookie 输入解析不再依赖 streaming 实现模块
8. **Streaming quality helpers**：`StreamingQualityPreference` 和 `StreamingNetworkQuality` 已上移到 `:core:common`，app settings/host code 不再需要从 streaming 实现模块获取质量偏好工具
9. **TrackDownloadFileNamePolicy**：下载文件名策略已上移到 `:core:common`（包名仍为 `app.yukine`），`TrackDownloadManager` 保持原 API 调用
10. **PlaybackRepeatMode**：独立常量对象已上移到 `:core:model`（包名仍为 `app.yukine.playback`），替代对 Service 类常量的引用，并避免 `:feature:ui-common` 依赖播放实现模块
11. **NowPlaying navigation contracts**：`NowPlayingEvent`、`NowPlayingUiState`、`LyricsUiState`、`RepeatModeUi`、`NowPlayingScreenStateProvider` 与 `NowPlayingDestination` 已归属 `:feature:navigation`，包名保持不变，app ViewModel 只实现 provider/处理行为
12. **internal→public**：`PlaybackManagerContracts.kt` 接口改为 public 以支持跨模块实现
13. **Hilt 绑定**：`StreamingDataPathParser` 的 @Provides 在 `app/di/StreamingDataModule.kt`
14. **NowPlaying provider naming**：`EchoNavHostState` 在 app 侧公开 `nowPlayingStateProvider`，避免剩余 navigation shell 继续暴露具体 `NowPlayingViewModel` 语义
15. **Home navigation contracts**：`HomeDashboardDestinationState`、`emptyHomeDashboardActions()` 与 `HomeDestination` 已归属 `:feature:navigation`，app `HomeDashboardViewModel` 继续实现状态更新/数据拉取
16. **Settings destination contract**：`SettingsDestination` 已归属 `:feature:navigation`，通过窄 `SettingsDestinationState` 读取 title/metrics/actions；app `SettingsState` 只实现该只读投影，仍保留完整设置状态事实源
17. **Queue destination contract**: `QueueDestination`, `QueueDestinationState`, and `QueueDestinationStateProvider` live in `:feature:navigation`; app `QueueViewModel` remains the single queue state/intent owner and implements the narrow provider.
18. **Collections destination contract**: `CollectionsDestination` and `CollectionsDestinationStateProvider` live in `:feature:navigation`; app `CollectionsViewModel` remains the single collections state/action owner and implements the narrow provider.
19. **Library destination contracts**: `LibraryGroupsDestination`, `LibraryTrackListDestination`, `LibraryGroupsDestinationState`, and `LibraryTrackListDestinationState` live in `:feature:navigation`; app `LibraryViewModel` remains the single Library state/action owner.
20. **Network sources destination contract**: `NetworkSourcesDestination` and `NetworkSourcesUiState` live in `:feature:navigation`; app `NetworkSourcesViewModel` remains the single Network sources state/action owner.
21. **Network menu destination contract**: `NetworkMenuDestination` and `NetworkMenuUiState` live in `:feature:navigation`; app `NetworkMenuViewModel` remains the single Network menu state/action owner.
22. **Network destination dispatch contract**: `NetworkDestination` lives in `:feature:navigation` and depends on route/state parameters plus an injected `streamingContent` lambda; app `EchoNavGraph` remains the place wiring the app-owned `StreamingViewModel`.
23. **Downloads destination contract**: `DownloadsDestination` and `DownloadsDestinationActions` live in `:feature:navigation`; app `DownloadsViewModel`, `DownloadsEffect`, `TrackDownloadManager`, and the document picker remain the download state/platform owners and are injected by `EchoNavGraph`.
24. **Unified Search UI/destination contract**: `UnifiedSearchScreen`, `UnifiedSearchUiState`, and `UnifiedSearchStreamingState` live in `:feature:ui-common`, while `SearchDestination` lives in `:feature:navigation`; app `SearchViewModel` and `StreamingViewModel` remain the single state/action owners and `EchoNavGraph` maps app streaming state into the pure search UI contract.
25. **Streaming search UI contracts**: `StreamingSearchScreen`, `StreamingSearchState`, and `StreamingSearchAuthLaunch` live in `:feature:ui-common`; `StreamingViewModel` remains in `:app` as the state/action owner. `StreamingPlaylistImportSummary` is now a pure `:core:model` type so UI code does not depend on `:feature:streaming`.
26. **Playback snapshot provider boundary**: `EchoNavHostState` now depends on `PlaybackSnapshotProvider` instead of concrete `PlaybackViewModel`; `PlaybackViewModel` remains the app-owned implementation and playback state source.
27. **Unused host dependency removal**: `EchoNavHostState` no longer accepts `MainActivityViewModel`; remaining host state fields are consumed directly by `EchoNavGraph` or related navigation rendering paths.
28. **Navigation route state boundary**: `EchoNavHostState` now depends on `StateFlow<NavigationRouteState>` instead of concrete `NavigationViewModel`; app `NavigationViewModel` and `MainRouteController` remain the route state owner/persistence path.
29. **Collections host provider boundary**: `EchoNavHostState` now depends on `CollectionsDestinationStateProvider` instead of concrete `CollectionsViewModel`; app `CollectionsViewModel` remains the state/action owner and provider implementation.
30. **Library host state boundary**: `EchoNavHostState` now depends on Library state flows instead of concrete `LibraryViewModel`; app `LibraryViewModel` remains the Library state/action owner.
31. **Home host state boundary**: `EchoNavHostState` now depends on the Home dashboard state flow instead of concrete `HomeDashboardViewModel`; app `HomeDashboardViewModel` remains the Home state/action owner.
32. **Network host state boundary**: `EchoNavHostState` now depends on Network menu/source state flows instead of concrete `NetworkMenuViewModel` and `NetworkSourcesViewModel`; app Network ViewModels remain the state/action owners.
33. **Streaming host state boundary**: `EchoNavHostState` now depends on the streaming state flow instead of concrete `StreamingViewModel`; app `StreamingViewModel` remains the streaming search/auth/playback/playlist state/action owner.
34. **Settings host state boundary**: `EchoNavHostState` now depends on the settings state flow and settings scroll state instead of concrete `SettingsViewModel`; app `SettingsViewModel` remains the settings state/action/effect owner.
35. **Downloads host state boundary**: `EchoNavHostState` now depends on the downloads state flow, open-directory request flow, and destination actions instead of concrete `DownloadsViewModel`; app `DownloadsViewModel` remains the downloads state/action/effect owner.
36. **Search host state boundary**: `EchoNavHostState` now depends on the unified search state flow instead of passing concrete `SearchViewModel` through the Compose host/bridge/nav graph; app `SearchViewModel` remains the search state/action owner.
37. **Queue host provider boundary**: `EchoNavHostState` now depends on `QueueDestinationStateProvider` instead of passing concrete `QueueViewModel` through the Compose host/bridge/nav graph; app `QueueViewModel` remains the queue state/intent owner.
38. **Active download host controller boundary**: `EchoNavHostState` now depends on `TrackDownloadController?` for active-download snapshots instead of concrete `TrackDownloadManager?`; app `TrackDownloadManager` remains the download queue/directory/request owner.
39. **Home dashboard destination contract naming**: Home dashboard state is now named `HomeDashboardDestinationState`, removing the stale `MainActivity*` prefix from the `:feature:navigation` contract while keeping `HomeDashboardViewModel` as the app state/action owner.
40. **Library destination contract naming**: Library groups and track-list state are now named `LibraryGroupsDestinationState` and `LibraryTrackListDestinationState`, removing stale `MainActivity*` prefixes from the `:feature:navigation` contracts while keeping `LibraryViewModel` as the app state/action owner.
41. **Streaming search contract naming**: Streaming search state and pending auth-launch state are now named `StreamingSearchState` and `StreamingSearchAuthLaunch`, removing stale `MainActivity*` prefixes from the `:feature:ui-common` contracts while keeping `StreamingViewModel` as the app state/action owner.
42. **Navigation route state naming**: route state is now named `NavigationRouteState` and persisted by `NavigationRouteStateStore`, removing stale `MainActivity*` naming.
43. **Playback view-state naming**: playback view state is now named `PlaybackViewState`, removing stale `MainActivity*` naming while keeping `PlaybackViewModel` as the app-owned playback snapshot/queue state source.
44. **Library store-state naming**: app library aggregate state is now named `LibraryStoreState`, removing stale `MainActivity*` naming while keeping `MainActivityViewModel` and `MainLibraryStore` as the current app-owned library state/facade path.
45. **Navigation route-state contract ownership**: pure `NavigationRouteState` now lives in `:feature:navigation`; app `NavigationRouteStateStore` still owns SavedStateHandle restore/save compatibility and app route-default normalization.
46. **Settings host chrome boundary**: `EchoNavHostState` now depends on `SettingsDestinationState` plus `SettingsChromeState` instead of full app `SettingsState`; app `SettingsViewModel` remains the full settings state/effect owner.
47. **Track download controller contract ownership**: `TrackDownloadController` and `TrackDownloadActionResult` now live in `:feature:navigation`; app `TrackDownloadManager` remains the concrete download queue/directory/platform owner through `TrackDownloadRequestQueue`.
48. **Navigation shell ownership**: `EchoNavGraph`, `EchoNavHostState`, and `EchoNavHostBridge` now live in `:feature:navigation`; `:app` remains the ViewModel/platform owner and wires the shell through `EchoAppHost`.
49. **Phase 6 coordinator reduction start**: forwarding-only `DownloadsCoordinator` and behaviorless `FileIOCoordinator` were deleted; MainActivityBase now wires download track requests directly to `DownloadRequestController` while preserving the existing custom download folder path.
50. **Stale network coordinator removal**: unused `NetworkCoordinator` was deleted after confirming `MainActivityBase` already constructs the live `NetworkRequestController`, `NetworkMenuEventController`, `NetworkSourcesEventController`, and `NetworkRenderCoordinator` paths directly; architecture tests now guard against reintroducing that dead assembly layer.
51. **Stale library/navigation coordinator removal**: unused `LibraryCoordinator` and `NavigationCoordinator` were deleted after confirming they were only constructed and never called; `MainActivityBase` still owns the active library and route/search/back/swipe paths, so this slice removes dead assembly without moving behavior.
52. **Duplicate settings coordinator removal**: unused `SettingsCoordinator` was deleted after confirming `MainActivityBase` already creates and binds `SettingsRuntimeApplier` directly; the removed coordinator only rebound the same runtime effect listener and had no active render caller.
53. **PlaybackCoordinator surface reduction**: `PlaybackCoordinator` first delegated host play requests directly to `PlaybackStartController`, removing unused action/result/pending methods and dropping stale ViewModel/store/controller constructor dependencies.
54. **NowPlayingQueueCoordinator surface reduction**: `NowPlayingQueueCoordinator` first owned only a queue intent listener binding after unused now-playing effect handling, queue render/input helpers, and stale store/render/status constructor dependencies were removed.
55. **StreamingCoordinator removal**: `StreamingCoordinator` was deleted after its only live calls were auth intent forwarding; `MainActivityBase` now calls `StreamingAuthCallbackController` directly and the existing host streaming methods remain the active provider/gateway/playback/source-switch paths.
56. **PlaybackCoordinator removal**: `PlaybackCoordinator` was deleted after it became pure forwarding; `MainActivityBase` now calls `PlaybackStartController`, `PlaybackServiceConnectionController`, and `EchoPlaybackService.setAppVisible(...)` directly.
57. **NowPlayingQueueCoordinator removal**: `NowPlayingQueueCoordinator` was deleted after confirming its listener binding was overwritten by the actual `mountNavHostShell()` queue intent binding; queue intents remain handled through `queueViewModel.bindIntentListener(...)` and `QueueActionController`.
58. **PlaybackStateUpdateController assembly reduction**: `PlaybackStateUpdateController` is now a stateless `object`; `PlaybackStateEventController` calls its playback update policy directly, and `MainActivityBase` no longer keeps or constructs this dependency.
59. **PlaybackServiceHostController field removal**: `PlaybackServiceHostController` remains the playback service connect/disconnect policy owner, but `MainActivityBase` no longer retains it as a field; the host controller is a local value passed directly into `PlaybackServiceConnectionController`.
60. **Network render controller field removal**: `NetworkRenderCoordinator` remains the network dispatch/render owner, but `MainActivityBase` no longer retains `NetworkMenuRenderController`, `NetworkTrackListRenderController`, `NetworkSourcesRenderController`, or `StreamingSearchRenderController` as fields; they are local assembly values passed directly into the coordinator.

---

## 2. 待执行: Phase 5.5 — 提取 `:feature:ui-common`

### 目标
纯 UI 组件库。无 ViewModel、无业务逻辑依赖。

### 步骤

1. `settings.gradle` 添加 `include ':feature:ui-common'`

2. 创建 `feature/ui-common/build.gradle`:
```groovy
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace "app.yukine.feature.uicommon"
    compileSdk 35
    defaultConfig { minSdk 23 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose true }
}

dependencies {
    api project(":core:model")
    implementation platform(libs.androidx.compose.bom)
    implementation libs.androidx.compose.ui
    implementation libs.androidx.compose.material3
    implementation libs.androidx.compose.foundation
}
```

3. 移动文件（保持原包名）:

**Theme & Design Token:**
- `app/.../ui/EchoTheme.kt`
- `app/.../ui/EchoThemePresets.kt`
- `app/.../ui/EchoMotion.kt`
- `app/.../ui/EchoMobileLayoutMetrics.kt`

**Icons & Assets:**
- `app/.../ui/EchoIcons.kt`
- `app/.../ui/ArtworkLoader.kt`

**共享 Composable:**
- `app/.../ui/EchoGlass.kt`
- `app/.../ui/EchoPageScaffold.kt`
- `app/.../ui/EchoStateCard.kt`
- `app/.../ui/CollapsibleSearchHeader.kt`
- `app/.../ui/YukineSearchBar.kt`
- `app/.../ui/TrackCurrentIndicator.kt`
- `app/.../ui/PlaybackProgressState.kt`
- `app/.../ui/PlaybackWaveform.kt`
- `app/.../ui/NowBar.kt`
- `app/.../ui/BackgroundTransformGeometry.kt`
- `app/.../EchoViewBackground.java`
- `app/.../EchoDialog.java`

4. `:app/build.gradle` 添加 `implementation project(":feature:ui-common")`

5. 验证: `./gradlew assembleDebug`

### 判断标准
文件应当移入 `:feature:ui-common` 当且仅当:
- 不 import 任何 ViewModel 类
- 不 import `app.yukine.data.*` 或 `app.yukine.streaming.*`（`:core:model` 的 Track 等除外）
- 不依赖 Activity/Context 的业务方法

如果某文件 import 了 ViewModel 或业务类，**留在 `:app`**。

---

## 3. 待执行: Phase 5.6 — 提取 `:feature:navigation`

### 目标
Navigation 图 + 所有 Destination/Screen Composable 独立成模块。

### 步骤

1. `settings.gradle` 添加 `include ':feature:navigation'`

2. 创建 `feature/navigation/build.gradle`:
```groovy
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace "app.yukine.feature.navigation"
    compileSdk 35
    defaultConfig { minSdk 23 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose true }
}

dependencies {
    api project(":feature:ui-common")
    api project(":core:model")
    implementation project(":core:common")
    implementation platform(libs.androidx.compose.bom)
    implementation libs.androidx.compose.ui
    implementation libs.androidx.compose.material3
    implementation libs.androidx.navigation.compose
    implementation libs.hilt.android
    implementation libs.hilt.navigation.compose
    ksp libs.hilt.compiler
}
```

3. 移动文件:

**Navigation 核心:**
- `EchoNavGraph.kt`, `EchoNavHostBridge.kt`, `EchoNavHostState.kt`
- `EchoRoutes.kt`, `EchoScaffold.kt`

**Destination + Screen:**
- `HomeDestination.kt`, `HomeDashboardScreen.kt`
- `NowPlayingDestination.kt`, `NowPlayingScreen.kt`
- `QueueDestination.kt`, `QueueScreen.kt`
- `CollectionsDestination.kt`, `CollectionsScreen.kt`
- `SearchDestination.kt`, `SearchScreen.kt`, `UnifiedSearchScreen.kt` (moved; app still owns `SearchViewModel`)
- `SettingsDestination.kt`, `SettingsScreen.kt`
- `LibraryGroupsDestination.kt`, `LibraryGroupsScreen.kt`
- `DownloadsDestination.kt`, `DownloadsScreen.kt`
- `NetworkDestination.kt`, `NetworkSourcesScreen.kt`
- `StreamingSearchScreen.kt`, `OnboardingScreen.kt`
- `PlaylistListScreen.kt`, `PlaylistTrackScreen.kt`
- 相关 UiState data class 文件

### ViewModel 接口化

`EchoNavHostState` 原本直接引用 `:app` 中的 ViewModel 实现。改为:

```kotlin
// feature/navigation/.../NavViewModelContracts.kt
interface NowBarStateProvider {
    val nowBarState: StateFlow<NowBarState>
}

interface PlaybackSnapshotProvider {
    val playbackSnapshot: StateFlow<PlaybackStateSnapshot>
}
```

`:app` 中的 ViewModel 实现这些接口。`EchoNavHostState` 的 Now Playing /
Playback 边界只依赖接口。

4. 验证: `./gradlew assembleDebug`

**规则**: `:feature:navigation` **不依赖** data/streaming/playback — 数据通过 ViewModel StateFlow 接口获取。

---

## 4. 待执行: Phase 6 — 消灭 MainActivityBase God Class

### 前置条件
Phase 5.5 和 5.6 完成。

### 6a. 创建 AppEventBus

```kotlin
// app/src/main/java/app/yukine/AppEventBus.kt
@Singleton
class AppEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    fun emit(event: AppEvent) { _events.tryEmit(event) }
}

sealed interface AppEvent {
    data class PlayTrackList(val tracks: List<Track>, val index: Int) : AppEvent
    data class NavigateToTab(val tab: String) : AppEvent
    data class ShowStatus(val message: String) : AppEvent
    // ... 每个 Coordinator.Listener 回调映射为一个 event
}
```

### 6b. 逐个将 Coordinator 改为 @Inject

顺序（从最简单到最复杂）:
1. `DownloadsCoordinator` — 已删除；下载请求直接使用 `DownloadRequestController`
2. `FileIOCoordinator` — 已删除；原文件只有 controller/launcher 字段容器
3. `NetworkCoordinator` — 已删除；旧文件只剩未调用的装配层，实际网络路径已由现有 request/menu/source/render controller 直接承担
4. `LibraryCoordinator` — 已删除；旧文件只被构造，实际曲库加载/渲染/collections 路径没有经过它
5. `NavigationCoordinator` — 已删除；旧文件只被构造，实际路由/返回/搜索/滑动路径没有经过它
6. `PlaybackCoordinator` — 已删除；旧文件只剩播放启动、service bind/release 和 app visible 转发
7. `NowPlayingQueueCoordinator` — 已删除；旧 listener 绑定会被 `mountNavHostShell()` 的实际 queue intent 绑定覆盖
8. `StreamingCoordinator` — 已删除；旧文件只剩 auth intent 转发，`MainActivityBase` 直接调用 `StreamingAuthCallbackController`
9. `SettingsCoordinator` — 已删除；`MainActivityBase` 已直接创建并绑定 `SettingsRuntimeApplier`，旧文件只重复绑定 runtime effect listener

不要把剩余 Coordinator 统一替换成 `AppEventBus` 转发层；只有当事件 owner 明确减少宿主装配或状态源时才继续迁移。

### 6c. ViewModel 改为 @HiltViewModel

```kotlin
// BEFORE:
class XViewModel : ViewModel() { ... }
// 使用: ViewModelProvider(this).get(XViewModel::class.java)

// AFTER:
@HiltViewModel
class XViewModel @Inject constructor(...) : ViewModel() { ... }
// 使用: val viewModel: XViewModel by viewModels()
```

### 6d. 删除 MainActivityBase，重写 MainActivity

目标: `MainActivity.kt` < 300 行，`@AndroidEntryPoint`，Hilt 注入所有 Coordinator。

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playbackServiceConnectionController: PlaybackServiceConnectionController
    @Inject lateinit var streamingAuthCallbackController: StreamingAuthCallbackController
    // ...
    @Inject lateinit var appEventBus: AppEventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectAppEvents()
        installComposeContent()
    }
}
```

---

## 5. 构建与验证

### 编译命令
```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug --no-daemon
```

### 测试命令
```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test --no-daemon
```

### JDK
Android Studio 内置 JBR (JDK 21)，路径: `C:\Program Files\Android\Android Studio\jbr`

### 每个 Phase 完成后的验证清单
1. `assembleDebug` 编译通过
2. `test` 单元测试全部通过
3. 安装到设备，验证: 播放本地音乐、播放流媒体、搜索、下载、导航切换

---

## 6. 关键文件路径

| 用途 | 路径 |
|------|------|
| God Class | `app/src/main/java/app/yukine/MainActivityBase.java` (3613 行) |
| 播放服务 | `app/src/main/java/app/yukine/playback/EchoPlaybackService.java` |
| 数据层核心 | `feature/data/src/main/java/app/yukine/data/MusicLibraryRepository.java` |
| 流媒体适配器 | `feature/streaming/src/main/java/app/yukine/streaming/StreamingPlaybackAdapter.kt` |
| 解耦接口 | `core/common/src/main/java/app/yukine/common/StreamingDataPathParser.kt` |
| DI 模块 | `app/src/main/java/app/yukine/di/StreamingDataModule.kt` |
| Gradle 配置 | `settings.gradle`, `app/build.gradle` |
| 版本目录 | `gradle/libs.versions.toml` |
| Playback 契约接口 | `feature/playback/.../manager/PlaybackManagerContracts.kt` |
| Repeat 常量 | `core/model/src/main/java/app/yukine/playback/PlaybackRepeatMode.kt` |

---

## 7. 已知陷阱

1. **BroadcastReceiver 不支持 Hilt 注入**: `PlaybackRestoreReceiver` 手动 new Repository，传入 `StreamingPlaybackAdapter.INSTANCE` 作为 parser。如果 Repository 构造函数再变，这里要同步更新。

2. **StreamingProviderName 枚举 vs String**: 模块外部应使用 `providerName()` (String?)，模块内部使用 `streamingProviderName()` (枚举)。

3. **DashboardRepository 留在 :app**: 因为它依赖 UI 类型 (`HomeDashboardUiState`)，不适合放入 `:feature:data`。

4. **internal 可见性**: `:feature:playback` 中 internal 的 Manager 类不可从 `:app` 访问。只有 Contracts 接口是 public。如果 Service 需要创建 Manager 实例，走 factory 或 Hilt。

5. **EchoPlaybackService**: 这是最大的耦合点。它在 `:app` 中，依赖 `:feature:playback` 的 Manager + `:feature:data` 的 Repository + `:feature:streaming` 的 Headers。长期目标是让 Service 只做薄代理，但目前不动它。

---

## 8. 执行约束

- Phase 5.5/5.6 是纯机械移动 + 接口提取，**不改业务逻辑**
- Phase 6 是逻辑改造，需逐步验证。每个子步骤单独 commit
- Java 文件在 Phase 5.5/5.6 中**不转 Kotlin**
- MainActivityBase 的 Kotlin 转换在 Phase 6d 中进行
- 每步完成后 commit，commit message 格式: `Extract :feature:ui-common module` / `Convert XCoordinator to @Inject`
