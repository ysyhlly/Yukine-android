# Echo Android 多模块架构拆分 — Codex 交接规范

> 日期: 2026-06-28
> 状态: 已审批，待执行
> 执行方: Codex

---

## 1. 背景

Echo Android 当前是单 `:app` 模块（313 源文件），核心问题：
- `MainActivityBase.java`（3,872 行）手动组装 186 个字段，是典型 God Class
- 所有 Coordinator/Controller/ViewModel 在 onCreate 中 `new` 出来，绕过 Hilt DI
- 无模块边界 → 任何层可随意 import 任何层
- 编译无法并行，增量编译范围过大

本文档定义目标架构和分步执行方案。每个 Phase 保证编译通过 + 测试通过。

---

## 2. 目标模块结构

```
:core:model          → 纯领域模型 (Track, Playlist, RemoteSource...)
:core:common         → 跨模块接口 (StreamingDataPathParser) + 工具类
:feature:streaming   → 流媒体网关/适配器/Room 缓存
:feature:data        → 音乐库/SQLite/WebDAV/Dashboard
:feature:playback    → Media3 服务 + 20+ 播放管理器
:feature:ui-common   → Theme/Icons/共享 Composable 组件
:feature:navigation  → EchoNavGraph/Destination/Screen Composable
:app                 → MainActivity/DI Module/Coordinator/Controller
```

---

## 3. 模块依赖图

```
:app ─────→ :feature:navigation ──→ :feature:ui-common ──→ :core:model
  │                │
  │                └──→ :core:common (路由常量)
  │
  ├──→ :feature:playback ──→ :feature:data ──→ :core:model
  │          │                     │
  │          ├──→ :feature:streaming ──→ :core:model
  │          │            │
  │          └──→ :core:common ←──┘
  │
  ├──→ :feature:streaming
  ├──→ :feature:data
  ├──→ :feature:ui-common
  ├──→ :core:model
  └──→ :core:common
```

**硬规则**：`:feature:navigation` 不依赖 data/streaming/playback。通过 ViewModel 接口获取数据。

---

## 4. 执行步骤

### Phase 0: Gradle 多模块骨架

不移动任何源码，只建结构。

1. `settings.gradle` 添加所有 module include
2. 每个模块创建 `build.gradle`（android-library + kotlin + compose + ksp + hilt）
3. `:app` 的 `build.gradle` 添加 `implementation project(":core:model")` 等
4. 验证: `./gradlew assembleDebug`

### Phase 1: 提取 `:core:model`

移动 `app/src/main/java/app/yukine/model/` → `core/model/src/main/java/app/yukine/model/`

文件: `Track.java`, `Playlist.java`, `RemoteSource.java`, `PlaybackQueueState.java`,
`PlaylistImportResult.java`, `TrackPlayRecord.java`, `WebDavSyncResult.java`, `LyricsLine.java`

依赖: 仅 Android SDK (Parcelable)。

### Phase 2: 提取 `:core:common` + 创建接口

**新建接口:**
```kotlin
// core/common/src/main/java/app/yukine/common/StreamingDataPathParser.kt
package app.yukine.common

interface StreamingDataPathParser {
    fun isStreamingTrack(dataPath: String): Boolean
    fun providerName(dataPath: String): String?
    fun providerTrackId(dataPath: String): String
}
```

**移动工具类:** `MainRoutes.java`, `LibraryGrouping.kt`, `MainExecutors.kt`, `AppLanguage.java`

### Phase 3: 提取 `:feature:streaming`

移动 `app/src/main/java/app/yukine/streaming/` 整个包（含 `cache/` 子包）。

**关键改动:** `StreamingPlaybackAdapter` 实现 `StreamingDataPathParser` 接口。
DI 模块绑定: `@Provides @Singleton fun provideStreamingDataPathParser(): StreamingDataPathParser = StreamingPlaybackAdapter`

模块依赖: `:core:model`, `:core:common`。含 Room KSP。

### Phase 4: 提取 `:feature:data`

移动 `app/src/main/java/app/yukine/data/` + `dashboard/` 包。

**关键改动 — MusicLibraryRepository.java:**
```java
// 移除: import app.yukine.streaming.StreamingPlaybackAdapter;
// 新增: import app.yukine.common.StreamingDataPathParser;
// 构造函数新增参数: StreamingDataPathParser streamingDataPathParser
// 替换: StreamingPlaybackAdapter.INSTANCE.providerName() → streamingDataPathParser.providerName()
```

模块依赖: `:core:model`, `:core:common`。**不依赖** `:feature:streaming`。

### Phase 5: 提取 `:feature:playback`

移动 `app/src/main/java/app/yukine/playback/` 整个包。

模块依赖: `:feature:data`, `:feature:streaming`, `:core:model`。

### Phase 5.5: 提取 `:feature:ui-common`

移动无业务依赖的 UI 组件:
- Theme: `EchoTheme.kt`, `EchoThemePresets.kt`, `EchoMotion.kt`, `EchoMobileLayoutMetrics.kt`
- Icons: `EchoIcons.kt`, `ArtworkLoader.kt`, `ArtworkFallback.kt`
- 组件: `EchoGlass.kt`, `EchoPageScaffold.kt`, `EchoStateCard.kt`, `CollapsibleSearchHeader.kt`,
  `YukineSearchBar.kt`, `TrackCurrentIndicator.kt`, `PlaybackProgressState.kt`,
  `PlaybackWaveform.kt`, `NowBar.kt`, `BackgroundTransformGeometry.kt`,
  `EchoViewBackground.java`, `EchoDialog.java`

模块依赖: `:core:model`, Compose BOM。

### Phase 5.6: 提取 `:feature:navigation`

移动 Navigation + Destination + Screen:
- `navigation/` 包: `EchoNavGraph.kt`, `EchoNavHostBridge.kt`, `EchoNavHostState.kt`, `EchoRoutes.kt`, `EchoScaffold.kt`
- 所有 Destination: `home/`, `now/`, `queue/`, `collections/`, `library/`, `network/`, `search/`, `settings/`, `downloads/`
- 所有 Screen Composable + UiState/Actions data class

**ViewModel 接口化:**
```kotlin
// feature/navigation/src/.../NavViewModelContracts.kt
interface PlaybackStateProvider {
    val playback: StateFlow<MainActivityPlaybackState>
}
interface NavigationStateProvider {
    val state: StateFlow<MainActivityRouteState>
}
```
`:app` 中的 ViewModel 实现这些接口。`EchoNavHostState` 接收接口类型。

模块依赖: `:feature:ui-common`, `:core:model`, `:core:common`。

### Phase 6: 消灭 MainActivityBase God Class

**6a.** 创建 `AppEventBus.kt` + `AppEvent` sealed interface
**6b.** 逐个 Coordinator 加 `@Inject`，Listener 回调改为 `appEventBus.emit(...)`
  - 顺序: Downloads → FileIO → Navigation → Library → Playback → Streaming → Settings
**6c.** 9 个无 @HiltViewModel 的 ViewModel 加上 `@HiltViewModel @Inject constructor`
**6d.** 删除 `MainActivityBase.java`，`MainActivity.kt` < 300 行

---

## 5. 验证策略

每个 Phase 完成后执行:
```bash
./gradlew assembleDebug && ./gradlew test
```
全部 138 个单元测试必须通过。

---

## 6. 约束

- Phase 0-5.6: 纯移动 + 接口提取，不改业务逻辑
- Phase 6: 逻辑改造，每个子步骤单独 commit
- Java 文件在 Phase 1-5.6 中不转 Kotlin
- UiState/Actions data class 随 Screen 移入 `:feature:navigation`
