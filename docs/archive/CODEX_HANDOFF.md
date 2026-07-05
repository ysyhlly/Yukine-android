# Codex Handoff: MainActivity Coordinator 拆分

日期: 2026-06-28

## 已完成

当前仍保留 1 个 Coordinator 类；Phase 6 稳定化已删除不再有行为收益的装配层：

| Coordinator | 文件 | 职责 |
|---|---|---|
| NetworkRenderCoordinator | `NetworkRenderCoordinator.kt` | 网络页分发渲染 |

已删除：

- `DownloadsCoordinator`: 只转发到 `DownloadRequestController`。
- `FileIOCoordinator`: 只持有 document/background/backup launcher 字段。
- `NetworkCoordinator`: 只剩未调用的旧装配层；实际网络路径已由 `MainActivityBase` 直接构造 `NetworkRequestController`、`NetworkMenuEventController`、`NetworkSourcesEventController` 和 `NetworkRenderCoordinator`。
- `LibraryCoordinator`: 只被构造，实际曲库加载/渲染/collections 路径没有经过它。
- `NavigationCoordinator`: 只被构造，实际路由/返回/搜索/滑动路径没有经过它。
- `SettingsCoordinator`: 重复 `MainActivityBase` 已有的 `SettingsRuntimeApplier` 绑定，且渲染方法没有活跃调用方。
- `StreamingCoordinator`: 只剩 auth intent 转发，`MainActivityBase` 已直接调用 `StreamingAuthCallbackController`。
- `PlaybackCoordinator`: 只剩播放启动、service bind/release 和 app visible 转发；`MainActivityBase` 已直接调用 `PlaybackStartController`、`PlaybackServiceConnectionController` 和 `EchoPlaybackService`。
- `NowPlayingQueueCoordinator`: 先被收窄为 queue intent listener，随后确认会被 `mountNavHostShell()` 的实际 `queueViewModel.bindIntentListener(...)` 覆盖，已删除死绑定。

剩余 Coordinator 不应被当作统一模板；只保留仍有真实运行时职责的窄 owner。

## 未完成：MainActivity 重写 (Task #2)

### 目标

将 `MainActivity.java` 从 3,579 行缩减到 < 400 行，仅保留：
- 生命周期方法 (onCreate/onResume/onPause/onDestroy)
- Coordinator 实例化 + Listener 桥接
- Compose setContent + NavHost
- 少量共享基础设施 (executors, permissionController, uiShellController)

### 实施步骤

1. **继续收敛剩余 Coordinator**
   - 先确认 Coordinator 有真实运行时调用；不要为“将来接入”保留死装配层
   - 跨 Coordinator 通信仍通过 Listener 回调到 MainActivity，再转发到目标 Coordinator
   - 已删除的 coordinator 不应作为新模板

2. **只替换有净收益的直接调用**
   - 下载请求已保留为直接调用 `downloadRequestController`，不要恢复 `DownloadsCoordinator`
   - 播放启动已保留为直接调用 `playbackStartController.playTrackList(...)`；不要恢复 `PlaybackCoordinator` 或 coordinator-local pending playback 状态
   - 等等，逐个 Coordinator 替换

3. **删除已迁移到 Coordinator 的 field 和 private 方法**

4. **不要恢复 NetworkCoordinator**
   - 旧的 `initialize()` 路径已经废弃
   - 网络请求、菜单、来源和渲染的实际装配点保留在现有 live controller/render coordinator 路径

5. **不要恢复 LibraryCoordinator / NavigationCoordinator**
   - 旧文件没有活跃调用路径
   - 后续若迁移曲库或路由行为，必须把行为移动到真实 owner 并加行为/合同证据

### 播放服务生命周期

```java
playbackServiceConnectionController.bind();
playbackServiceConnectionController.release();
if (playbackService != null) {
    playbackService.setAppVisible(true);
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
   - 多个 Controller 只有 1-2 个方法时，优先评估是否能变成无状态 policy/object 或并入真实 owner
   - `PlaybackStateUpdateController` 已改为无状态 `object`，不要恢复 `MainActivityBase` 字段或手动构造

### P2 - 可以做

6. **Feature Module 拆分**
   - `:feature:streaming`, `:feature:network`, `:feature:downloads`
   - 依赖 `:core:model`, `:core:playback`, `:core:data`

7. **Compose 全面迁移**
   - 当前 UI 层是 Compose + 自定义 RenderController 混合
   - 目标：纯 Compose，RenderController 退化为 ViewModel→State 映射

8. **单元测试**
   - 每个 Coordinator 的 Listener 可 mock，方便验证事件路由
   - `PlaybackCoordinator` 已删除；pending playback 归 `PlaybackStartController`，service bind/release 归 `PlaybackServiceConnectionController`

## 编译验证命令

```bash
./gradlew compileDebugKotlin compileDebugJavaWithJavac --max-workers=1
```

## 注意事项

- 剩余 Coordinator 均为 `internal class`，包级可见性
- 不要恢复 `NetworkCoordinator.initialize()`；它已经随未使用的 `NetworkCoordinator` 删除
- 不要恢复 `LibraryCoordinator` / `NavigationCoordinator`；它们已经随未使用构造路径删除
- 不要恢复 `SettingsCoordinator`；保留直接 `SettingsRuntimeApplier` 绑定，后续要减少的是宿主装配而不是增加重复 coordinator
- 不要恢复 `StreamingCoordinator`；auth intent 直接进入 `StreamingAuthCallbackController`
- 不要恢复 `PlaybackCoordinator`；播放启动、service 连接和 app visible 已分别由现有 owner 直接承担
