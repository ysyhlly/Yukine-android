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

仍留在 app 的 UI 文件:

- `SearchScreen.kt`: 仍直接依赖 `SearchViewModel` 和 `MainActivityStreamingState`。
- `StreamingSearchScreen.kt`: 仍依赖 `MainActivityStreamingState`。

### Phase 5.6 status: partially implemented

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
- 包名保持 `app.yukine.navigation`，调用侧无需大规模 import churn。

暂留 app 的导航文件:

- `EchoNavGraph.kt`
- `EchoNavHostState.kt`
- `EchoNavHostBridge.kt`

原因: 这些文件仍直接持有或调用 app ViewModel / app event 类型，包括
`MainActivityViewModel`、`NavigationViewModel`、`HomeDashboardViewModel`、
`LibraryViewModel`、`CollectionsViewModel`、
`SettingsViewModel`、`NetworkMenuViewModel`、`NetworkSourcesViewModel`、
`StreamingViewModel`、`PlaybackViewModel`、`DownloadsViewModel`、
`TrackDownloadManager`、`SearchViewModel`、`QueueViewModel`。

下一步要完成 Phase 5.6，应先新增窄接口 contract，而不是让
`:feature:navigation` 依赖 app:

```kotlin
interface NowBarStateProvider {
    val nowBarState: StateFlow<NowBarState>
}

interface PlaybackSnapshotProvider {
    val playbackSnapshot: StateFlow<PlaybackStateSnapshot>
}
```

然后让 app ViewModel 实现这些接口，再迁移 `EchoNavGraph` /
`EchoNavHostState` / `EchoNavHostBridge` / `NowPlayingDestination`。

### 2026-06-28 continuation notes

- `NowPlayingViewModel` 已实现 `NowBarStateProvider`。
- `PlaybackViewModel` 已实现 `PlaybackSnapshotProvider`。
- `EchoNavGraph` 已改为从 provider 采集 `StateFlow`，NowBar 事件通过回调分发，不再直接读 `PlaybackViewModel.playback`。
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
  `MainActivityHomeDashboardUiState` and `emptyHomeDashboardActions()`.
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

## 1. 已完成工作总结

### 模块现状

| 模块 | 文件数 | 职责 |
|------|--------|------|
| `:core:model` | 24 | 纯领域模型 (Track, Playlist, PlaybackStateSnapshot, streaming wire/provider/content models, StreamingTrackMatchPolicy 等) |
| `:core:common` | 9 | 跨模块接口/共享工具 (StreamingDataPathParser, StreamingDataPathMetadata, StreamingQualityPreference, StreamingNetworkQuality, StreamingPlaylistLinkParser, StreamingCookieHeaderParser, TrackDownloadFileNamePolicy, SecureSecretStore, EmbeddedArtwork) |
| `:feature:streaming` | 23 | 流媒体网关/适配器/Room 缓存 |
| `:feature:data` | 10 | 音乐库/数据库/WebDAV/文件导入 |
| `:feature:playback` | 33 | Media3 播放管理器 (Queue/State/Effect) |
| `:feature:ui-common` | 34 | 纯 Compose UI 组件/screen/labels |
| `:feature:navigation` | 10 | route/scaffold/navigation shell primitives/Home + Settings + Now Playing destination contracts |
| `:app` | 182 | Activity/Coordinator/ViewModel/app-owned destinations/DI |

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
15. **Home navigation contracts**：`MainActivityHomeDashboardUiState`、`emptyHomeDashboardActions()` 与 `HomeDestination` 已归属 `:feature:navigation`，app `HomeDashboardViewModel` 继续实现状态更新/数据拉取
16. **Settings destination contract**：`SettingsDestination` 已归属 `:feature:navigation`，通过窄 `SettingsDestinationState` 读取 title/metrics/actions；app `SettingsState` 只实现该只读投影，仍保留完整设置状态事实源

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
- `SearchDestination.kt`, `SearchScreen.kt`, `UnifiedSearchScreen.kt`
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
interface PlaybackStateProvider {
    val playback: StateFlow<MainActivityPlaybackState>
}
interface NavigationStateProvider {
    val state: StateFlow<MainActivityRouteState>
}
```

`:app` 中的 ViewModel 实现这些接口。`EchoNavHostState` 只依赖接口。

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
1. `DownloadsCoordinator` — 无 Listener
2. `FileIOCoordinator`
3. `NavigationCoordinator` — Listener → AppEventBus
4. `LibraryCoordinator`
5. `PlaybackCoordinator`
6. `StreamingCoordinator`
7. `SettingsCoordinator`

每个 Coordinator 的 `interface Listener { ... }` 替换为注入 `AppEventBus` 发事件。

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
    @Inject lateinit var playbackCoordinator: PlaybackCoordinator
    @Inject lateinit var streamingCoordinator: StreamingCoordinator
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
| God Class | `app/src/main/java/app/yukine/MainActivityBase.java` (3872 行) |
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
