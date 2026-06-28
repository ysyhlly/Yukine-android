# Codex Handoff: MainActivity Coordinator 拆分

日期: 2026-06-28

## 已完成

9 个 Coordinator 类已创建并通过编译 (`compileDebugKotlin` + `compileDebugJavaWithJavac`):

| Coordinator | 文件 | 职责 |
|---|---|---|
| DownloadsCoordinator | `DownloadsCoordinator.kt` | 下载请求、自定义下载目录 |
| FileIOCoordinator | `FileIOCoordinator.kt` | 文件选择器（文档/背景图/备份恢复） |
| SettingsCoordinator | `SettingsCoordinator.kt` | 设置渲染、运行时样式应用 |
| NowPlayingQueueCoordinator | `NowPlayingQueueCoordinator.kt` | 正在播放事件、队列操作 |
| NetworkCoordinator | `NetworkCoordinator.kt` | 网络/WebDAV/串流源管理 |
| LibraryCoordinator | `LibraryCoordinator.kt` | 本地曲库加载/渲染/分组 |
| PlaybackCoordinator | `PlaybackCoordinator.kt` | 6 个播放控制器编排 |
| StreamingCoordinator | `StreamingCoordinator.kt` | 在线音源解析/切换/搜索 |
| NavigationCoordinator | `NavigationCoordinator.kt` | 路由状态、Tab/页面导航、搜索 |

所有 Coordinator 遵循 `NetworkRenderCoordinator` 模式：构造函数接收预构建的 Controller，通过 `Listener` 接口回调 Activity。

## 未完成：MainActivity 重写 (Task #2)

### 目标

将 `MainActivity.java` 从 3,579 行缩减到 < 400 行，仅保留：
- 生命周期方法 (onCreate/onResume/onPause/onDestroy)
- Coordinator 实例化 + Listener 桥接
- Compose setContent + NavHost
- 少量共享基础设施 (executors, permissionController, uiShellController)

### 实施步骤

1. **在 onCreate 中实例化 9 个 Coordinator**
   - 每个 Coordinator 的构造函数参数已在各 `.kt` 文件中定义
   - 跨 Coordinator 通信通过 Listener 回调到 MainActivity，再转发到目标 Coordinator
   - 示例：
     ```java
     libraryCoordinator = new LibraryCoordinator(
         libraryViewModel, collectionsViewModel, libraryStore, settingsStore,
         playbackStore, repository, permissionController, statusMessageController,
         trackListRenderController, libraryGroupsRenderController,
         libraryPlaylistsRenderController, collectionsRenderController,
         playHistoryActionController,
         new LibraryCoordinator.Listener() { ... }
     );
     ```

2. **替换 MainActivity 中的直接调用**
   - `downloadRequestController.downloadTrack(track)` → `downloadsCoordinator.downloadTrack(track)`
   - `playbackStartController.playTrackList(...)` → `playbackCoordinator.playTrackListFromHost(...)`
   - 等等，逐个 Coordinator 替换

3. **删除已迁移到 Coordinator 的 field 和 private 方法**

4. **NetworkCoordinator 特殊处理**
   - 它有 `initialize()` 方法需要在 onCreate 中额外参数才能完成初始化
   - 调用顺序：先构造 NetworkCoordinator，再调 `initialize(activity, routeController, ...)`

### Listener 桥接模板

```java
// PlaybackCoordinator.Listener 示例
new PlaybackCoordinator.Listener() {
    @Override public EchoPlaybackService currentPlaybackService() {
        return playbackServiceConnectionController.getService();
    }
    @Override public void renderSelectedTab() { renderTab(); }
    @Override public void renderNowBar() { nowBarRenderController.render(); }
}
```

## 后续架构改进（优先级排序）

### P0 - 必须做

1. **EchoPlaybackService 瘦身** (~800行)
   - 将 Manager 层（NotificationManager, LyricsManager, AudioEffectManager）提取为独立类
   - Service 只做 MediaSession 生命周期 + 前台通知

2. **线程模型统一**
   - 消除 `MainExecutors` 手动线程池，统一用 `viewModelScope` + `Dispatchers.IO`
   - `MainHandler` post → `lifecycleScope.launch(Dispatchers.Main)`

### P1 - 应该做

3. **Store 层消除**
   - `MainLibraryStore`, `MainPlaybackStore`, `MainSettingsStore` 是无类型的 in-memory cache
   - 迁移到 ViewModel StateFlow + Room

4. **EchoDatabaseHelper → Room**
   - 当前是原始 SQLiteOpenHelper，SQL 散落各处
   - 分步迁移：先建 Room entity/DAO，再逐表切换

5. **Controller 合并**
   - 多个 Controller 只有 1-2 个方法，可合并进 Coordinator
   - 例：`PlaybackStateUpdateController` 无构造参数，只有一个 `update()` 方法

### P2 - 可以做

6. **Feature Module 拆分**
   - `:feature:streaming`, `:feature:network`, `:feature:downloads`
   - 依赖 `:core:model`, `:core:playback`, `:core:data`

7. **Compose 全面迁移**
   - 当前 UI 层是 Compose + 自定义 RenderController 混合
   - 目标：纯 Compose，RenderController 退化为 ViewModel→State 映射

8. **单元测试**
   - 每个 Coordinator 的 Listener 可 mock，方便验证事件路由
   - PlaybackCoordinator 的 pending playback 逻辑需要测试

## 编译验证命令

```bash
./gradlew compileDebugKotlin compileDebugJavaWithJavac --max-workers=1
```

## 注意事项

- 所有 Coordinator 均为 `internal class`，包级可见性
- `NavigationCoordinator` 管理 `MainRouteController` 的状态读写
- `NetworkCoordinator.initialize()` 是唯一需要延迟初始化的 Coordinator（因为它内部构造了多个子 Controller）
- `SettingsCoordinator` 直接接收 `activity: ComponentActivity`（因为 `MainPermissionController.activity` 是 private 的）
