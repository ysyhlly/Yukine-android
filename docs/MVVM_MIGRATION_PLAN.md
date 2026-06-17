# ECHO Android 完全 MVVM 迁移方案

## 目标

将当前的混合式架构迁移为清晰的 MVVM 分层：

```text
Compose UI -> Feature ViewModel -> UseCase -> Repository -> DataSource / Service / Gateway
```

迁移后的基本原则：

- Activity 只作为应用宿主，负责生命周期、权限入口、系统回调和 Compose root。
- 每个功能域拥有独立 ViewModel。
- ViewModel 不直接访问数据库、播放服务、网络客户端或 Android 系统细节。
- Repository 负责数据来源协调。
- UseCase 负责业务流程编排。
- 播放服务通过接口暴露状态与控制能力，UI 和 ViewModel 不直接依赖具体 Service。

## 当前主要问题

- `MainActivity.java` 体量过大，承担生命周期、播放服务、权限、扫描、导航、回调协调等大量职责。
- `MainActivityViewModel.kt` 作为全局状态中心，混合了路由、曲库、播放、流媒体、推荐、设置等多个领域。
- UseCase、Repository、Controller、Store 已经存在，但边界不统一。
- Java Activity 与 Kotlin ViewModel 之间存在大量回调和反向绑定。
- 播放服务、曲库、流媒体、设置等功能仍通过大类间接耦合。

## 目标包结构

先按包重组，不急于拆 Gradle module：

```text
app.echo.next
  core/
    model/
    common/
    permissions/
    logging/
  data/
    library/
    lyrics/
    settings/
    playback/
    streaming/
  domain/
    library/
    lyrics/
    settings/
    playback/
    streaming/
    recommendation/
  feature/
    home/
    library/
    playlist/
    player/
    queue/
    settings/
    streaming/
    recommendation/
  service/
    playback/
  ui/
    components/
    theme/
    navigation/
```

## ViewModel 拆分目标

| ViewModel | 职责 |
|---|---|
| `HomeViewModel` | 首页统计、最近播放、推荐入口 |
| `LibraryViewModel` | 曲库列表、分组、搜索、收藏 |
| `PlaylistViewModel` | 歌单列表、歌单详情、导入、删除 |
| `PlayerViewModel` | 当前播放、NowBar、播放控制 |
| `QueueViewModel` | 队列展示、排序、删除、清空 |
| `LyricsViewModel` | 歌词加载、偏移、当前行 |
| `SettingsViewModel` | 设置页状态和偏好保存 |
| `StreamingViewModel` | 在线搜索、登录、歌单导入、音源选择 |
| `RecommendationViewModel` | 每日推荐、心动推荐、自动填充队列 |
| `NavigationViewModel` | Tab、页面路由、沉浸式播放页入口 |

## 统一 ViewModel 形态

每个功能 ViewModel 统一使用 `UiState`、`UiEvent`、`UiEffect`：

```kotlin
data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface LibraryEvent {
    data class Search(val query: String) : LibraryEvent
    data class ToggleFavorite(val track: Track) : LibraryEvent
    data class Play(val track: Track) : LibraryEvent
}

sealed interface LibraryEffect {
    data class ShowMessage(val message: String) : LibraryEffect
}
```

UI 只消费状态、发送事件：

```kotlin
LibraryScreen(
    state = state,
    onEvent = viewModel::onEvent
)
```

禁止 UI 直接调用 Repository、Service、Gateway 或大型 Controller。

## Repository 与 DataSource

目标 Repository：

```text
LibraryRepository
PlaylistRepository
PlaybackRepository
LyricsRepository
SettingsRepository
StreamingRepository
RecommendationRepository
```

底层 DataSource：

```text
LocalLibraryDataSource
MediaStoreDataSource
WebDavDataSource
PlaybackServiceDataSource
StreamingGatewayDataSource
LyricsRemoteDataSource
LyricsLocalDataSource
SettingsDataSource
```

职责边界：

- Repository 对上提供稳定业务数据接口。
- DataSource 处理 SQLite、Room、MediaStore、WebDAV、HTTP、Service Binder 等具体细节。
- ViewModel 只依赖 UseCase。
- UseCase 只依赖 Repository 或领域接口。

## 播放服务隔离

目标是让 UI 和 ViewModel 不直接依赖 `EchoPlaybackService`。

新增接口：

```kotlin
interface PlaybackController {
    val playbackState: Flow<PlaybackStateSnapshot>
    val queue: Flow<List<Track>>

    suspend fun play(track: Track, queue: List<Track>)
    suspend fun pause()
    suspend fun resume()
    suspend fun seek(positionMs: Long)
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun setQueue(queue: List<Track>, index: Int)
}
```

`EchoPlaybackService` 只作为 `PlaybackController` 的实现细节。

## UseCase 补齐清单

优先抽出这些 UseCase：

- `ScanLocalMusicUseCase`
- `LoadLibraryUseCase`
- `LoadLibraryGroupsUseCase`
- `SearchLibraryUseCase`
- `ToggleFavoriteUseCase`
- `BuildPlaybackQueueUseCase`
- `PlayTrackUseCase`
- `UpdatePlaybackQueueUseCase`
- `LoadLyricsForTrackUseCase`
- `ResolveStreamingTrackUseCase`
- `SearchStreamingUseCase`
- `ImportStreamingPlaylistUseCase`
- `FetchDailyRecommendationsUseCase`
- `FetchHeartbeatRecommendationsUseCase`
- `DeleteTrackUseCase`
- `DeletePlaylistUseCase`
- `LoadSettingsUseCase`
- `SaveSettingsUseCase`

## 迁移阶段

### 阶段 1：收缩 Activity

目标：让 `MainActivity.java` 从协调中心变为宿主。

任务：

- 抽出 `PermissionCoordinator`。
- 抽出 `PlaybackServiceConnector`。
- 抽出系统 Intent 处理器。
- 抽出 Toast、Dialog、权限结果等一次性事件处理。
- Compose root 只接收 ViewModel state 与事件回调。

验收：

- `MainActivity` 不再直接编排曲库、流媒体、歌词、推荐逻辑。
- 新功能不需要继续往 `MainActivity.java` 添加大量匿名内部类。

### 阶段 2：拆设置与歌词

目标：先迁移低风险功能。

任务：

- 保留并强化 `SettingsViewModel`。
- 保留并强化 `LyricsViewModel`。
- 歌词加载统一通过 `LoadLyricsForTrackUseCase`。
- 设置读写统一通过 `SettingsRepository`。

验收：

- 设置页不依赖 `MainActivityViewModel`。
- 歌词页不依赖 `MainActivityViewModel`。

### 阶段 3：拆流媒体

目标：把在线搜索、登录、歌单导入、音源能力从大 ViewModel 中剥离。

任务：

- 新增 `StreamingViewModel`。
- 流媒体搜索走 `SearchStreamingUseCase`。
- 在线歌单导入走 `ImportStreamingPlaylistUseCase`。
- 登录状态、provider 切换、分页、错误状态全部由 `StreamingViewModel` 管理。

验收：

- `MainActivityViewModel` 不再包含流媒体搜索、认证、分页、导入逻辑。
- 新增音源只改 streaming feature，不改主 Activity。

### 阶段 4：拆曲库与歌单

目标：让曲库和歌单成为独立 feature。

任务：

- 新增 `LibraryViewModel`。
- 新增 `PlaylistViewModel`。
- 曲库分组、搜索、收藏、删除、扫描结果刷新迁入对应 ViewModel。
- 曲库数据访问统一走 `LibraryRepository`。

验收：

- 曲库页只依赖 `LibraryUiState`。
- 歌单页只依赖 `PlaylistUiState`。
- 曲库扫描与 UI 展示解耦。

### 阶段 5：拆播放与队列

目标：播放状态和队列不再由全局 ViewModel 直接维护。

任务：

- 新增 `PlaybackController`。
- 新增 `PlayerViewModel`。
- 新增 `QueueViewModel`。
- NowBar、播放页、队列页统一订阅播放状态。
- 播放服务状态通过 Flow 暴露。

验收：

- UI 不直接绑定 `EchoPlaybackService`。
- 播放服务可以用 fake controller 做单元测试。
- 队列操作不需要改 `MainActivity.java`。

### 阶段 6：拆推荐

目标：每日推荐、心动推荐、自动补队列独立。

任务：

- 新增 `RecommendationViewModel`。
- 推荐种子选择、自动填充、去重、失败重试迁入推荐领域。
- 与播放队列通过 `PlaybackController` 或 `PlaybackRepository` 交互。

验收：

- 心动推荐逻辑不再散落在 Activity 和 Main ViewModel。
- 推荐逻辑可以脱离 UI 单测。

### 阶段 7：导航独立

目标：业务状态和路由状态分离。

任务：

- 新增 `NavigationViewModel` 或使用 Compose Navigation 管理路由。
- Tab、曲库模式、详情页、沉浸式播放页入口独立维护。
- 各 feature ViewModel 不保存全局 tab 状态。

验收：

- 点击 NowBar、切 tab、进入沉浸式页面不依赖业务 ViewModel 副作用。
- 路由状态可以单独测试。

## 推荐迁移顺序

1. 整理包结构，不改行为。
2. 收缩 `MainActivity.java`。
3. 抽 `SettingsViewModel` 和 `LyricsViewModel`。
4. 抽 `StreamingViewModel`。
5. 抽 `LibraryViewModel` 和 `PlaylistViewModel`。
6. 引入 `PlaybackController`。
7. 抽 `PlayerViewModel` 和 `QueueViewModel`。
8. 抽 `RecommendationViewModel`。
9. 独立导航。
10. 删除旧 `MainActivityViewModel` 的聚合职责。

## 测试策略

每迁移一个 feature，同步补三类测试：

- ViewModel 状态测试。
- UseCase 行为测试。
- Repository fake 数据源测试。

播放相关额外增加：

- fake `PlaybackController`。
- 播放失败策略测试。
- 队列更新和当前曲目同步测试。

流媒体相关额外增加：

- fake `StreamingGateway`。
- 登录状态测试。
- 搜索分页测试。
- 歌单导入测试。
- 音源失败和重试测试。

## 最终验收标准

- `MainActivity` 少于 300 行。
- 单个 ViewModel 少于 500 行。
- ViewModel 不直接访问 SQLite、HTTP、Service Binder。
- UI 只消费 `UiState`，只发送 `UiEvent`。
- 播放、曲库、流媒体、设置、歌词、推荐可以分别单测。
- 新增一个功能不需要同时修改 `MainActivity.java` 和 `MainActivityViewModel.kt`。
- `MainActivityViewModel` 被删除，或只保留极薄的兼容 facade。

