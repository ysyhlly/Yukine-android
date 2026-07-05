# Codex Handoff: EchoPlaybackService 瘦身 (Phase 2)

日期: 2026-06-28

## 已完成 (Phase 1)

Service 从 2790 → 2487 行，提取了 3 个独立类：

| 提取类 | 文件 | 减少行数 | 职责 |
|---|---|---|---|
| `PlaybackMediaSourceProvider` | `playback/manager/PlaybackMediaSourceProvider.kt` | ~130 | 音频缓存、MediaSource、HTTP headers、WebDAV auth |
| `PlaybackSessionPlayer` | `playback/manager/PlaybackSessionPlayer.kt` | ~125 | ForwardingPlayer 代理，通过 Delegate 接口回调 |
| `PlaybackPlayerFactory` | `playback/manager/PlaybackPlayerFactory.kt` | ~50 | ExoPlayer 构建（buffer策略、AudioProcessor） + 释放 |

模式：每个提取类通过接口（Delegate/StateProvider/Runnable）回调 Service，不持有 Service 引用。

## 未完成：继续瘦身 (估计可再减 ~1200 行)

### 提取 1: PlaybackServiceWiring (P0, ~700 行)

**目标**: 将 `onCreate()` 中 810 行的 manager 实例化移到独立的 wiring 类。

**当前状态**: `onCreate()` (lines 308-1118) 全部是 `new XxxManager(new XxxManager.Callback() { ... })` 嵌套。

**实施方案**:

```kotlin
@UnstableApi
internal class PlaybackServiceComponents(
    val queueStore: PlaybackQueueStore,
    val positionManager: PlaybackPositionManager,
    val sleepTimerManager: PlaybackSleepTimerManager,
    val errorRecoveryManager: PlaybackErrorRecoveryManager,
    val progressUpdateManager: PlaybackProgressUpdateManager,
    val lyricsManager: LyricsPublisher,
    val notificationManager: PlaybackNotificationManager,
    val queueManager: PlaybackQueueManager,
    val mediaLibraryCallback: PlaybackMediaLibraryCallback,
    val sessionManager: PlaybackSessionManager,
    val visualizationAnalyzer: PlaybackVisualizationAnalyzer,
    val visualizationCacheManager: PlaybackVisualizationCacheManager,
    val warmupCoordinator: PlaybackWarmupCoordinator,
    val shutdownCoordinator: PlaybackShutdownCoordinator,
    val artworkManager: PlaybackNotificationArtworkManager,
    val statePublisher: PlaybackStatePublisher,
    val precacheManager: PlaybackPrecacheManager,
    val noisyReceiverManager: PlaybackNoisyReceiverManager,
    val wifiLockManager: PlaybackWifiLockManager,
) {
    companion object {
        @JvmStatic
        fun create(host: ServiceHost): PlaybackServiceComponents { ... }
    }
}
```

**关键接口**: 定义 `PlaybackServiceComponents.ServiceHost`，暴露 Service 中被回调的方法（约 30 个）：
```kotlin
interface ServiceHost {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun stopAndClear()
    fun isPlaying(): Boolean
    fun currentTrack(): Track?
    fun currentIndex(): Int
    fun positionMs(): Long
    fun queue(): CopyOnWriteArrayList<Track>
    fun prepareCurrent(playWhenReady: Boolean)
    fun publishState()
    fun publishPlaybackNotification(force: Boolean)
    fun startProgressUpdates()
    fun persistPlaybackPositionThrottled(force: Boolean)
    fun persistPlaybackQueue()
    fun savePlaybackResumeRequested(requested: Boolean)
    fun isHttpUri(uri: Uri?): Boolean
    fun cacheKeyForTrack(track: Track?): String?
    fun cacheDataSourceForTrack(track: Track): CacheDataSource
    fun mediaCacheKeyForTrack(track: Track?): String
    fun continuousCachedBytes(cacheKey: String): Long
    fun contentLengthForCacheKey(cacheKey: String?): Long
    fun headersForTrack(track: Track): Map<String, String>
    fun audioCache(): SimpleCache
    fun bufferedProgress(durationMs: Long): Float
    fun debugTrack(track: Track?): String
    fun activityPendingIntent(): PendingIntent
    fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent
    fun startPlaybackForeground(notification: Notification): Boolean
    fun refreshPlaybackSession()
    // ... 以及 PlaybackRuntimeStateManager, PlaybackQueueRuntimeStateManager 的直接访问
    fun runtimeState(): PlaybackRuntimeStateManager
    fun queueRuntimeState(): PlaybackQueueRuntimeStateManager
    fun transitionState(): PlaybackTransitionStateManager
    fun mainHandler(): Handler
    fun streamingPlaybackHeaderStore(): StreamingPlaybackHeaderStore
    fun repository(): MusicLibraryRepository
    fun mediaSourceProvider(): PlaybackMediaSourceProvider
    fun playbackTaskScheduler(): PlaybackTaskScheduler
    fun visualizationTaskScheduler(): PlaybackTaskScheduler
    fun streamingDiagnostics(): PlaybackStreamingDiagnostics
    fun appVisible(): Boolean
}
```

**Service 改为**:
```java
@Override
public void onCreate() {
    super.onCreate();
    // ... 少量初始化 (mediaSourceProvider, playerFactory, runtimeStateManagers)
    components = PlaybackServiceComponents.create(serviceHost);
    // 解包常用引用
    playbackQueueManager = components.getQueueManager();
    playbackNotificationManager = components.getNotificationManager();
    // ...
    restorePlaybackQueue();
    publishState();
}
```

### 提取 2: PlaybackControlActions (P1, ~200 行)

**目标**: 将 play/pause/seekTo/skipToNext/skipToPrevious/stopAndClear 等核心控制方法提取。

**涉及方法** (lines 1271-1500):
- `play()` (30 行)
- `pause()` (12 行)
- `seekTo(long)` (14 行)
- `skipToNext()` (7 行)
- `skipToPrevious()` (11 行)
- `startFadeOutThenNext()` (50 行 crossfade)
- `stopAndClear()` (23 行)
- `playAfterCompletion()` (20 行)
- `stopAtEndOfQueue()` (20 行)

**模式**:
```kotlin
internal class PlaybackControlActions(
    private val host: Host
) {
    interface Host {
        fun player(): ExoPlayer?
        fun currentTrack(): Track?
        fun isPlaying(): Boolean
        fun queue(): List<Track>
        fun currentIndex(): Int
        fun prepareCurrent(playWhenReady: Boolean)
        // ... 其他需要的回调
    }
    fun play() { ... }
    fun pause() { ... }
    // ...
}
```

### 提取 3: PlaybackPreparationManager (P1, ~150 行)

**目标**: 提取 `prepareCurrent()`, `prepareMirroredQueue()`, `prepareSingleTrack()`。

**涉及方法** (lines 1704-1830):
- `prepareCurrent(boolean playWhenReady)`
- `prepareMirroredQueue(boolean, long)`
- `prepareSingleTrack(Track, boolean, long)`
- `canMirrorQueueToPlayer()`
- `seekExistingMirroredQueue(boolean, long)`

### 提取 4: PlayerListener 具名类 (P2, ~90 行)

**当前**: `playerListener` 是 Service 内的匿名 `Player.Listener` (lines 209-299)。

**改为**:
```kotlin
internal class PlaybackPlayerListener(
    private val host: Host
) : Player.Listener {
    interface Host { ... }
}
```

## 编译验证

```bash
./gradlew compileDebugKotlin compileDebugJavaWithJavac --max-workers=1
```

## 重要约束

1. **不要改动 public API** — Service 的 public 方法签名不能变（被 Activity/ViewModel 直接调用）
2. **保持运行时行为不变** — 纯重构，不改逻辑
3. **每次提取后编译验证** — 不要一次做太多
4. **Kotlin 包路径** — 新文件放在 `app.yukine.playback.manager`，用 `internal` 可见性
5. **Java 包私有访问** — 如果 Kotlin 类需要访问 Service 的 package-private 方法，通过接口传递，不要改原方法的可见性
6. **匿名接口** — Java 调 Kotlin `fun interface` 时 SAM 转换不生效，Kotlin 接口需要保持普通 interface（不加 fun）
7. **与 MainActivity Coordinator PR 无冲突** — 这些变更仅在 `playback/` 包内

## 优先级和预期效果

| 提取 | 优先级 | 预期减少行数 | 复杂度 |
|---|---|---|---|
| PlaybackServiceWiring | P0 | ~700 | 高 (接口大) |
| PlaybackControlActions | P1 | ~200 | 中 |
| PlaybackPreparationManager | P1 | ~150 | 中 |
| PlayerListener | P2 | ~90 | 低 |

全部完成后 Service 预计 < 1300 行，主要剩余：
- 生命周期方法 (onCreate/onDestroy/onStartCommand)
- public API 方法（thin delegates）
- snapshot() 状态快照
- 少量工具方法
