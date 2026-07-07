# ECHO/YUKINE 架构稳定化方向切换 - 2026-06-27

## 背景

本文件根据最新代码审查反馈更新迁移方向。此前的架构迁移过度强调“继续拆 owner/manager/coordinator”，已经暴露出新的风险：

- 新增抽象过快，`Manager` / `Coordinator` / `Publisher` 数量增长，部分只是把依赖换成接口转发。
- `EchoPlaybackService`、`MainActivity` 等核心类仍然庞大，且在拆分过程中引入了大量跨 Java/Kotlin 边界调用。
- `PlaybackQueueManager.QueueProvider` 一类的大接口已经接近“接口上帝对象”，不是继续扩展的好方向。
- 架构契约测试大量依赖字符串匹配，只能防止特定代码片段回流，不能证明依赖方向、运行行为或集成路径正确。
- 当前工作树存在大量未提交迁移文件，继续高速改动会降低可审查性和回滚能力。

## 新方向

从现在起，架构工作的默认方向从“继续新增 owner 并快速迁移”切换为：

1. 冻结新增抽象。
2. 收敛已有中间层。
3. 减少接口方法和跨语言边界。
4. 补足可复现验证证据。
5. 只在净减少复杂度时继续迁移。

换句话说：先让当前迁移变得可审查、可回滚、可证明，再谈下一轮拆分。

## Playback Target Shape

For an Android music player like Yukine, the target architecture is not "more layers." It is a stable playback core, one-way UI state flow, a clear Service boundary, and testable data/policy owners. The target path is:

```text
UI / Compose
  -> ViewModel / UI State
  -> Playback command owner
  -> PlaybackService boundary
  -> Playback engine managers
  -> ExoPlayer / MediaSession / Notification
```

`EchoPlaybackService` should converge toward the Android/Media3 system boundary: Media3/ExoPlayer lifecycle, MediaSession, foreground service and notification lifecycle, service binding, external intents, and media buttons. Queue strategy, cache policy, URI/media item decisions, lyrics sync, download policy, and notification action mapping should not remain long-term Service responsibilities.

Playback policy should move into small real owners:

- `PlaybackQueueManager`: queue, current index, restore, skip/replace rules, and preferably the authority for queue/current track state.
- `PlaybackItemResolver`: local/streaming track to playable URI or MediaItem resolution.
- `PlaybackCachePolicy`: cache keys, precache, failure cleanup, and partial cache handling.
- `PlaybackNotificationController`: notification display state and action mapping.
- `LyricsPlaybackBridge`: playback state to lyrics state synchronization.

UI/ViewModel should not directly depend on the concrete `EchoPlaybackService` implementation. UI emits semantic commands such as play tracks, pause, seek, toggle favorite, and restore queue; a narrow playback command owner or gateway receives them. Do not add or expand a universal `PlaybackServiceFacade`.

Queue, current index, and current track should have one authority. The default target is `PlaybackQueueManager` as that authority, with Service and UI consuming or mapping its snapshot instead of spreading the same mutable state across Service fields, ViewModel mirrors, QueueManager state, and MediaSession queue.

Large interfaces default to reduction. For `PlaybackQueueManager.QueueProvider`, prefer deleting derivable methods, splitting by responsibility, or letting the manager hold a clear state-owner dependency directly. Do not add methods merely to make migration convenient.

Playback tests outrank UI snapshots for this area: queue restore, skip next/previous, replace current track, local/streaming URI resolution, notification action, background playback, service restart recovery, and cache partial failure cleanup. Each playback service slimming slice should move one policy cluster, run the matching playback unit tests, and record a reproducible smoke result when runtime behavior is affected.

## 2026-06-28 根因审查修正

新的代码审查把方向再往前推了一层：继续删除薄包装是必要的，但不是充分条件。当前最大风险仍是三个根因级热点：

- `MainActivity` 仍然承担应用组装、匿名回调、平台入口和多 feature 策略拼接；如果删 Controller 后逻辑回流到 Activity，就不是架构收敛。
- `EchoPlaybackService` 仍然保留大量播放队列、缓存、URI 判断和调度细节；服务瘦身要优先复用已有 manager，而不是再包一层新 service facade。
- `EchoDatabaseHelper` 仍是手写 SQL / migration 链路；在迁 Room 前必须先补 migration/事务测试，不能在脏工作树里直接大改数据层。

因此后续默认顺序调整为：先压低 `MainActivity.onCreate` 装配密度和匿名回调策略，再推进播放服务职责下沉和数据库/并发护栏；纯转发层清理只能作为配套动作，不能替代根因治理。

## 立即生效的规则

- 不再为“架构更干净”默认新增 `Manager`、`Coordinator`、`Controller`、`Bindings`、`Gateway`。
- 新抽象必须满足净收益：删除或合并更多转发层，减少调用路径、接口方法数、状态源或平台耦合点。
- 禁止用“删除 Controller”换取 `MainActivity` 匿名 listener、私有 helper 或 `onCreate` 装配继续膨胀。
- 删除旧测试前必须证明等价行为测试已经迁到新 owner；字符串契约测试只能做报警，不能当行为覆盖。
- 禁止继续扩张 `PlaybackQueueManager.QueueProvider` 这类大接口；下一步应拆小、合并、内联或改为更直接的依赖。
- 不把 Java 核心类继续包上一层 Kotlin 小 owner，除非能同时减少 Java/Kotlin 互操作面和调用层级。
- 不新增裸 `Thread`、无 shutdown 证据的 `ExecutorService` 或分散 scheduler；并发模型要收敛到现有协程/调度器或明确生命周期 owner。
- 不把 Room 迁移当成顺手清理；触碰 `EchoDatabaseHelper` schema/migration 前先补 migration/事务测试。
- 字符串契约测试不能作为唯一护栏；关键路径需要补充行为单测、依赖方向检查、集成 smoke 或设备证据。
- 对 Windows/KSP 验证，一次只跑一个 Gradle 命令，先使用项目默认 daemon/workers；只有复现 daemon、KSP 或锁冲突后，才降级使用 `--no-daemon` 或 `--max-workers=1`。
- 每个架构切片必须记录当前 dirty worktree 风险、验证命令、失败/通过结果，以及是否需要提交或拆分审查。

## 新执行顺序

### P0 - 稳定化冻结

- 记录当前未提交文件和大文件行数，不再继续扩大改动面。
- 串行跑 `compileDebugKotlin + compileDebugJavaWithJavac` 和当前受影响 focused tests。
- 标记当前新增 owner 中哪些是真策略、哪些只是转发。
- 建议在用户确认后把当前稳定点提交，建立可回滚基线。

### P1 - 根因热点收缩

- 降低 `MainActivity.onCreate` 手动装配密度，把稳定依赖迁入 Hilt 或 feature-level assembly owner，但禁止制造新的全局 God Coordinator。
- 把已经回流到 Activity 的匿名策略重新推回真实 feature owner；Activity 只保留生命周期、Compose root、service binding 和平台 launcher delegation。
- 继续把 `EchoPlaybackService` 中非服务职责的队列/缓存/URI/调度逻辑下沉到已有 manager，并用播放相关单测或 smoke 记录兜底。
- 为 `EchoDatabaseHelper` 建立 migration/事务测试基线，再评估 Room 或仓库拆分。
- 盘点并发模型，标出裸 Thread、独立 ExecutorService、自定义 scheduler 的 owner、shutdown 和替换路径。

### P2 - 收敛已有抽象

- 优先合并或删除纯转发 owner，而不是继续新增 owner。
- 对超过约 12 个方法的 provider/listener 接口做拆分或反向收敛。
- 缩短 UI 到播放服务的实际调用路径，减少“UI -> Activity -> Controller -> Controller -> Manager -> Store -> Service”式链路。
- 对 `PlaybackQueueManager.QueueProvider` 做减法：先归类方法，再把平台、状态、持久化、调度拆开或回收给真实 owner。

### P3 - 验证替代字符串契约

- 保留少量字符串契约作为回归报警，但不要让它们替代结构性验证。
- 增加依赖方向检查，例如 Service 不应依赖 Activity，UI 不应直接依赖具体 Service 实现。
- 对首次启动、本地扫描、本地播放、后台播放、通知控制、歌词、队列恢复、流媒体登录至少保留可复现 smoke 记录。

### P4 - 再评估迁移

- 只有当一个区域已经有稳定基线、真实测试证据、较短调用路径后，才继续迁移。
- 迁移优先选择小而完整的模块，例如歌词或单一平台能力，而不是同时改播放、设置、流媒体、数据层。
- 不追求文件数或类名上的“MVVM 完整”，以运行路径更短、状态源更少、回滚更容易为准。

## 当前收口状态

当前最重要的收口不是再开新抽象，而是把已经迁移出来的播放边界停在一个可维护的平衡点：

- `EchoPlaybackService` 不再直接 import `MainActivity`，通知/前台入口改为包级 launcher intent。
- `EchoPlaybackService` 已直接持有 `PlaybackSessionManager`；`PlaybackSessionGateway` 这种只转发 manager 生命周期调用的服务内 gateway 已删除。
- `SettingsPageRenderController` 已删除；设置页滚动回到 `SettingsViewModel`，剩余文案格式化迁入 `SettingsLabelFormatter`。
- `PlaybackQueueManager.QueueProvider.currentTrack()` 已从大接口移除，当前曲目由 manager 基于 `queue()` / `currentIndex()` 自行计算。
- `DownloadQualitySelectedCallback` 已删除，下载音质选择回调直接使用 Kotlin 函数类型。
- `DownloadManagerProvider` 已删除，下载队列晚绑定改为直接函数依赖。
- `NetworkMenuContentSink` 已删除，网络菜单 chrome 发布由 `NetworkMenuEventController` 直接更新 `NetworkMenuViewModel`。
- `CollectionsActionsSink` 和无调用的 `publishCollectionsActions(...)` Activity 回调已删除，Collections actions 由 `CollectionsRenderController` 直接发布到 `CollectionsViewModel`。
- Collections、Library、NowPlaying、Queue 中无调用的迁移期回调类型已删除，保留直接 owner 调用路径。
- Queue、Queue render、Network track-list、Collections、Settings controls 中额外无调用的 action contract 已删除，只保留仍有生产调用的边界类型。
- 推荐链路中无生产持有方的自我实现接口已删除，`StreamingRecommendationViewModel` 保留直接 action 方法，`HeartbeatRecommendationPlayer` 保留为 controller-facing 边界。
- `PlaylistExportController` 已删除，歌单导出的 pending playlist id/name 由已有 `DocumentPickerController` 持有并随导出 URI 直接回调给文档 listener。
- `LibraryPlaylistExportCallback` 已删除，歌单导出 Java 入口不再保留无实际消费方的空回调参数。
- root package 的 main/test `*Bindings*` 数量已用目录级架构契约锁定为 0，避免旧桥接层悄悄回流。
- `LibraryPlayHistoryClearedCallback` / `clearPlayHistoryJava(...)` 已删除，播放历史清空保持 `PlayHistoryActionController` 直接调用 `LibraryViewModel.clearPlayHistory { ... }`。
- 重复的 collections 收藏写路径已删除，收藏 mutation 只保留 `LibraryFavoriteWriter -> ToggleFavoriteUseCase` 这一条生产路径。
- `NowPlayingPlaybackGatewayAdapter` 中仅包装服务 lookup/start 的 `PlaybackServiceProvider` / `PlaybackServiceStarter` 已删除，改为直接函数依赖。
- `BackupRestoreLauncher` 中仅包装 status key 的 `BackupStatusSink` 已删除，改为直接函数依赖。
- `DownloadRequestController` 中仅包装 status message 的 `DownloadStatusSink` 已删除，改为直接函数依赖。
- `TrackShareLauncher` 中仅包装 language/share-style 字符串读取的 provider 已删除，改为直接函数依赖。
- `PlayHistoryActionController` 中仅包装 language/status 的 provider/sink 已删除，改为直接函数依赖。
- `BackgroundImagePickerController` 中仅包装 preview language/transform lookup 的 provider 已删除，改为直接函数依赖。
- `StatusMessageController` / `MessageTextResolver` 中仅包装 language lookup 的 provider 已删除，统一为直接 `Supplier<String>` 依赖。
- `PlaybackQueueManager.QueueProvider` 已经足够大，后续默认动作是减法，不是再加方法。
- 架构验证已经分层：继续保留少量字符串契约报警，但必须配合行为测试和编译证据。
- 当前只在确有净收益时继续做收敛型切片，例如删除重复转发、合并薄包装、缩短服务到应用入口的依赖。

这意味着“完成架构迁移”不应理解为“继续拆到没有类”，而应理解为“主要迁移目标已经落地，接下来进入收口/稳定阶段”。

## 2026-06-29 P1 Evidence

- Library collection/import gateway assembly moved out of `MainActivityBase`
  anonymous blocks into existing library use-case owners and `LibraryModule`
  providers. `MainActivityBase.java` is now 2873 lines; root-package files
  remain 172, root `*Bindings*` remains 0, and root `*Controller*` remains
  44.
- Verification used default Gradle daemon/workers: focused
  `LibraryCollectionUseCasesTest`, `LibraryImportUseCasesTest`, and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Library document gateway assembly also moved into `LibraryModule`; Activity
  now binds an injected `LibraryDocumentGateway` and no longer constructs
  `MusicLibraryImportOperations` or `ContentResolverLibraryDocumentGateway`
  locally. `MainActivityBase.java` is now 2872 lines; root-package files remain
  172, root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `ContentResolverLibraryDocumentGatewayTest` and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Settings preference load/apply use-case assembly moved out of
  `MainActivityBase` into `SettingsModule`. Activity now injects
  `LoadSettingsPreferencesUseCase` and `ApplySettingsPreferenceUseCase`, while
  Settings still flows through `SettingsViewModel -> preference/runtime
  applier`; no settings gateway, coordinator, controller, or bindings layer was
  added. `MainActivityBase.java` is now 2866 lines; root-package files are now
  173, root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `LoadSettingsPreferencesUseCaseTest`, `ApplySettingsPreferenceUseCaseTest`,
  and `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Library store/search assembly now mirrors the existing playback store factory
  shape: `MainLibraryStoreFactory` creates the ViewModel-bound store, while
  `LibraryModule` owns `LibrarySearchUseCase` construction. Activity now calls
  `libraryStoreFactory.create(viewModel)` and no longer constructs
  `MainLibraryStore`, `LibrarySearchUseCase`, or `MusicLibrarySearchOperations`
  directly. `MainActivityBase.java` is now 2864 lines; root-package files
  remain 173, root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `LibrarySearchUseCaseTest`, `NetworkLibraryStoreDirectAccessTest`,
  `PlayHistoryActionControllerTest`, and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Streaming search action-handler assembly now mirrors the existing streaming
  listener factory shape: `MainStreamingSearchActionHandlerFactory` creates the
  `DefaultStreamingSearchActionHandler`, while `StreamingModule` owns that
  construction. Activity still supplies its ViewModel and streaming action
  gateway, but no longer directly constructs the handler. `MainActivityBase.java`
  is now 2865 lines; root-package files remain 173, root `*Bindings*` remains
  0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `DefaultStreamingSearchActionHandlerTest` and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Track share operations assembly moved out of `MainActivityBase`: `ShellModule`
  now owns `TrackShareManagerOperations(trackShareManager, nativeMusicShareManager)`
  and Activity injects `TrackShareOperations` for `TrackShareLauncher`. Activity
  no longer holds `TrackShareManager` or `NativeMusicShareManager` fields.
  `MainActivityBase.java` is now 2862 lines; root-package files remain 173,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `TrackShareLauncherTest` and `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Settings store assembly moved out of `MainActivityBase`:
  `SettingsModule` now provides the Activity-scoped `MainSettingsStore`, and
  Activity injects it before loading persisted preferences. Activity no longer
  calls `new MainSettingsStore()` in `initializeStoresAndDataGateways()`.
  `MainActivityBase.java` is now 2861 lines; root-package files remain 173,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `LoadSettingsPreferencesUseCaseTest`, `ApplySettingsPreferenceUseCaseTest`,
  and `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Lyrics settings use-case assembly moved out of `MainActivityBase`:
  `SettingsModule` now owns
  `LoadLyricsSettingsUseCase(MusicLibraryLyricsSettingsOperations(repository))`,
  and Activity injects the use case while configuring `LyricsViewModel`.
  `MainActivityBase.java` is now 2858 lines; root-package files remain 173,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `LoadLyricsSettingsUseCaseTest` and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Lyrics loader assembly moved out of `MainActivityBase`:
  `SettingsModule` now owns
  `LoadTrackLyricsUseCaseLyricsLoader(LoadTrackLyricsUseCase(LyricsRepositoryLoadOperations()))`,
  and Activity injects `LyricsLoader` while configuring `LyricsViewModel`.
  `MainActivityBase.java` is now 2857 lines; root-package files remain 173,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `LoadTrackLyricsUseCaseTest`, `LyricsViewModelTest`, and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Network action use-case assembly moved out of `MainActivityBase`:
  `LibraryModule` now owns `NetworkActionUseCases` construction from the
  existing WebDAV and network-library operations. Activity injects the
  aggregate and only binds it into `NetworkActionsViewModel`, instead of
  constructing two operations and 11 use cases in `initializeNetworkOwners()`.
  `MainActivityBase.java` is now 2840 lines; root-package files remain 173,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `NetworkActionsViewModelTest`, `NetworkLibraryUseCasesTest`,
  `WebDavSourceUseCasesTest`, and `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Library gateway host policy moved out of the Java anonymous
  `LibraryGateway` block into `MainLibraryGateway`, created by
  `MainLibraryGatewayFactory` from `LibraryModule`. Activity still supplies true
  host/platform capabilities, but `MainLibraryGateway` now owns status key
  resolution, favorite refresh ordering, library routing, search, import, scan,
  playlist-add, and track-list play delegation. `MainRouteController`
  implements the narrow `LibraryRouteActions` boundary, avoiding a replacement
  anonymous route adapter. `MainActivityBase.java` is now 2789 lines;
  root-package files are now 174, root `*Bindings*` remains 0, and root
  `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `MainLibraryGatewayTest`, `LibraryViewModelTest`, `MainRouteControllerTest`,
  and `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Streaming action gateway host policy moved out of the Java anonymous
  `MainActivityStreamingActionGateway` block into `MainStreamingActionGateway`,
  created by `MainStreamingActionGatewayFactory` from `StreamingModule`.
  Activity still supplies true host/platform capabilities, but
  `MainStreamingActionGateway` now owns selected quality, language, auth launch,
  resolved-track playback, login-success, and manual-cookie import ordering.
  `MainActivityBase.java` is now 2769 lines; root-package files are now 175,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `MainStreamingActionGatewayTest`, `DefaultStreamingSearchActionHandlerTest`,
  and `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Now-playing playback gateway service-start construction moved out of
  `MainActivityBase.initializeNowPlayingGateways()` into
  `MainNowPlayingPlaybackGatewayFactory` from `PlaybackUiModule` plus
  `NowPlayingPlaybackServiceStarter`. Activity only supplies the real
  `playbackService` lookup and no longer constructs
  `NowPlayingPlaybackGatewayAdapter` or the playback service start `Intent`
  locally. `MainActivityBase.java` is now 2762 lines; root-package files remain
  175, root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `NowPlayingPlaybackGatewayAdapterTest`, `MainNowPlayingGatewayTest`,
  `NowPlayingViewModelTest`, and `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.

- Play-history action controller construction moved out of
  `MainActivityBase.initializeStoresAndDataGateways()` into
  `MainPlayHistoryActionControllerFactory` from `LibraryModule`. The existing
  `PlayHistoryActionController` remains the behavior owner for clearing
  history, publishing status, updating `PlayHistoryStateStore`, and reloading
  collections; Activity no longer directly calls
  `new PlayHistoryActionController(...)`. `MainActivityBase.java` is now 2760
  lines; root-package files remain 175, root `*Bindings*` remains 0, and root
  `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `PlayHistoryActionControllerTest` and `MainActivityArchitectureContractTest`,
  then `compileDebugKotlin + compileDebugJavaWithJavac`.
- Network action result policy moved out of the Java anonymous
  `NetworkActionsViewModel.Listener` block into `MainNetworkActionsListener`,
  created by `MainNetworkActionsListenerFactory` from `LibraryModule`.
  `NetworkActionsViewModel` still owns use-case execution; the listener owns
  result routing to library replacement, now-playing queue retain/replace,
  network navigation, collections reload, and status publication.
  `MainActivityBase.java` is now 2697 lines; root-package files remain 175,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `MainNetworkActionsListenerTest`, `NetworkActionsViewModelTest`, and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Settings runtime controls moved out of `MainActivityBase` anonymous
  implementations into `MainSettingsRuntimeApplierFactory` plus concrete
  controls beside `SettingsRuntimeApplier`, provided by `SettingsModule`.
  Activity now only passes existing playback service, lyrics ViewModel, and
  permission-controller providers into `settingsRuntimeApplierFactory.create(...)`;
  it no longer directly constructs settings runtime controls or calls
  `FloatingLyricsService` from the settings runtime setup. `MainActivityBase.java`
  is now 2632 lines; root-package files remain 175, root `*Bindings*` remains
  0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `SettingsRuntimeApplierTest`, `SettingsViewModelTest`, and
  `MainActivityArchitectureContractTest`, then
  `compileDebugKotlin + compileDebugJavaWithJavac`.
- Collections render action policy moved out of the Java anonymous
  `CollectionsRenderController.Listener` block into `MainCollectionsRenderListener`,
  created by `MainCollectionsRenderListenerFactory` from `LibraryModule`.
  Activity still supplies true host/platform capabilities, but the listener now
  owns collections action routing and the selected-playlist export guard.
  `MainActivityBase.java` is now 2562 lines; root-package files are now 176,
  root `*Bindings*` remains 0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `MainCollectionsRenderListenerTest` and `MainActivityArchitectureContractTest`,
  then `compileDebugKotlin + compileDebugJavaWithJavac`.
- Library-groups render action policy moved out of the Java anonymous
  `LibraryGroupsRenderController.Listener` block into `MainLibraryGroupsRenderListener`,
  created by `MainLibraryGroupsRenderListenerFactory` from `LibraryModule`.
  `ArtistInfoRepository` is now provided by `LibraryModule`, so Activity no
  longer directly constructs it for the groups renderer. `MainActivityBase.java`
  is now 2517 lines; root-package files are now 177, root `*Bindings*` remains
  0, and root `*Controller*` remains 44.
- Verification used default Gradle daemon/workers: focused
  `MainLibraryGroupsRenderListenerTest` and `MainActivityArchitectureContractTest`,
  then `compileDebugKotlin + compileDebugJavaWithJavac`.

## 对既有计划的覆盖

本文件不删除 `docs/ARCHITECTURE_REMEDIATION_PLAN_2026-06-26.md`，但从 2026-06-27 起覆盖其中“继续推进拆分”的默认解释。

当旧计划和本文件冲突时，以本文件为准：

- 先冻结和根因热点收缩，再新增 owner。
- 先降低 `MainActivity` / `EchoPlaybackService` / `EchoDatabaseHelper` 风险，再继续薄包装清理。
- 先证明行为，再调整目录。
- 先保护测试，再删除旧 owner。
- 先减少转发层，再追求命名和包结构。
- 先处理过度抽象，再继续 P1-6/P1-7/P1-8。

## 下一步建议

1. 先冻结当前大 diff，列出删除的测试及其替代行为测试。
2. 盘点 `MainActivity.onCreate` 手动 `new`、匿名 listener 和字段数量，优先选择一个 feature assembly 切片做减法。
3. 盘点当前新增 playback owner，标出可合并、可内联、必须保留三类，并对 `PlaybackQueueManager.QueueProvider` 出方法分组。
4. 为 `EchoDatabaseHelper` 设计 migration/事务测试基线，不在无测试状态下直接 Room 重写。
5. 增加结构性依赖检查和 smoke 记录：`EchoPlaybackService` 不应依赖 `MainActivity`，播放/通知/队列恢复路径必须可复现。
6. 在继续代码迁移前，先完成一次可审查基线提交或用户确认的稳定点。

## 2026-07-07 Abstraction Convergence Audit

This pass intentionally did **not** add any new Manager/Coordinator/Controller layer. It checked the
current root/app playback owner surface before choosing the next slice.

Commands/evidence used:

```powershell
# Controller/Manager/Coordinator size and listener reference inventory across app + feature modules.
# Focused CodeGraph queries for MainActivityBase, PlaybackStateUpdateController,
# PlaybackControllerMediaItemsOwner, MainUiShellController, and playback queue owner candidates.
```

Findings:

- `PlaybackStateUpdateController` is small, but it is not pure forwarding: it owns playback-state derived
  decisions (`loadLyrics`, history refresh, selected-tab render, Now Playing update, error display) and is
  protected by `PlaybackStateUpdateControllerTest` plus architecture contracts. Do not delete it as a
  cosmetic cleanup.
- `PlaybackControllerMediaItemsOwner` is also small, but it owns the Media3 controller-queue resolution to
  local queue playback handoff and has dedicated tests. Keep it until controller media item handling is moved
  into a stronger playback command boundary.
- `MainUiShellController` centralizes theme-surface and toast/status suppression; deleting it would push UI
  shell state back into `MainActivityBase`, which violates the current Activity slimming direction.
- Playback `*Owner` classes remain numerous. Many are architecture-contract protected, so removing one safely
  requires replacing string-only contracts with behavior tests for the affected shutdown/queue/notification path
  first.
- The safest immediate P0 work is still behavior evidence, not structural deletion. This pass therefore
  strengthened `EchoDatabaseHelperTest` transaction coverage instead of forcing a risky owner removal.

Next candidate convergence slices:

1. Replace brittle architecture assertions for one playback owner with behavior tests, then remove or merge the
   owner only if the replacement shortens the Service path and reduces total forwarding methods.
2. Audit `PlaybackQueueRuntimeStateManager` + `PlaybackQueueMirrorStateOwner` together. They currently isolate
   `playerMirrorsQueue`; merging is only acceptable if it does not reintroduce direct runtime-state calls into
   `EchoPlaybackService`.
3. Audit shutdown owners (`PlaybackQueuePersistenceOwner`, lifecycle/service/playback resource owners) as a
   batch. Do not collapse them until shutdown order has behavior tests for queue persistence, resume-request
   clearing, notification teardown, and player release.

## 2026-07-07 QueueProvider Production Guard

Follow-up architecture guard after the abstraction audit:

- Production source no longer contains `PlaybackQueueManager.QueueProvider` or a `QueueProvider` interface.
- Added `MainActivityArchitectureContractTest.productionPlaybackQueueManagerDoesNotReintroduceQueueProviderInterface` to scan `feature/playback/src/main/java` and `app/src/main/java/app/yukine/playback` for `QueueProvider` / `queueProvider` reintroduction.
- This is a regression alarm only; behavior remains protected by the focused queue, restore, persistence, and database transaction tests.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest.productionPlaybackQueueManagerDoesNotReintroduceQueueProviderInterface --console=plain
# BUILD SUCCESSFUL
```

## 2026-07-07 Playback Queue Persistence Owner Collapse

Follow-up to the abstraction convergence audit: `PlaybackQueuePersistenceOwner` was removed as a pure
forwarding owner.

## 2026-07-07 Playback Queue Restore Owner Collapse

Follow-up pure-forwarding convergence slice:

- Removed `PlaybackQueueRestoreOwner` and its isolated forwarding test.
- `EchoPlaybackService` now calls the existing `PlaybackQueueManager` restore APIs directly for service start,
  user-requested restore, and restore-enable runtime setting updates.
- Queue restore policy remains in `PlaybackQueueManager`; the service only maps
  `RestorePlaybackResult` to existing service lifecycle actions (`createPlayerIfNeeded`,
  `prepareCurrent`, `publishState`).
- `MainActivityArchitectureContractTest` now prevents `PlaybackQueueRestoreOwner` from returning, while still
  requiring persistence and restore-enable state to stay out of the service.
- Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
# BUILD SUCCESSFUL

.\gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
# BUILD SUCCESSFUL
```

What changed:

- Deleted `app/src/main/java/app/yukine/playback/PlaybackQueuePersistenceOwner.java` and its forwarding-only
  unit test.
- `EchoPlaybackService` now calls the existing real owner, `PlaybackQueueManager`, directly for play/pause
  resume-request writes and throttled position persistence.
- Shutdown queue persistence still stays out of anonymous service wiring through
  `PlaybackShutdownLifecycleResourcesOwner.playbackQueueLifecycleStoreFromQueueManager(...)`, which adapts the
  existing `PlaybackQueueManager` to the lifecycle shutdown boundary.
- Added behavior coverage for the lifecycle adapter in `PlaybackShutdownLifecycleResourcesOwnerTest` so this
  is not protected only by string contracts.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.PlaybackShutdownLifecycleResourcesOwnerTest --tests app.yukine.MainActivityArchitectureContractTest.playbackQueuePersistenceIsOwnedOutsideEchoPlaybackService --tests app.yukine.MainActivityArchitectureContractTest.playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster --tests app.yukine.MainActivityArchitectureContractTest.playbackServiceShutdownIsOwnedOutsideEchoPlaybackService --console=plain
# BUILD SUCCESSFUL
```

## 2026-07-07 EchoPlaybackService Forwarding Cleanup

Small convergence slice after the P0 gate work:

- Removed the private `EchoPlaybackService.currentTrack()` and `EchoPlaybackService.isPlaying()` forwarding
  helpers.
- Service call sites now consume the existing state owners directly:
  `PlaybackQueueStateOwner.currentTrack()` and `PlaybackPlayerStateOwner.isPlaying()`.
- Updated `MainActivityArchitectureContractTest` so the contract prevents the private current-track forwarding
  helper from returning, while still requiring current-track reads to go through `PlaybackQueueStateOwner`.
- Audit metrics after the slice: `EchoPlaybackService.java` 1464 lines, 56 `private Playback*` fields,
  8 `fromPlaybackQueueManager(...)` calls, 42 app playback `Playback*Owner.java` files.
- This did not add a Manager/Coordinator/Controller and did not move policy back into the service.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
# BUILD SUCCESSFUL
```

## 2026-07-07 Streaming Restore Owner Collapse

Follow-up pure-forwarding convergence slice:

- Removed `PlaybackQueueStreamingRestoreOwner` and its isolated forwarding-only test.
- `PlaybackMediaSourceProvider` now directly implements `PlaybackQueueManager.StreamingRestoreProvider`, keeping streaming header restore and restored-track lookup beside URI/media-item/cache-key policy.
- `EchoPlaybackService` passes the existing media-source owner into `PlaybackQueueManager`; it no longer owns a `PlaybackQueueStreamingRestoreOwner` field or a wrapper construction step.
- Updated `MainActivityArchitectureContractTest` to prevent the wrapper from returning while still requiring queue restore to go through the narrow `StreamingRestoreProvider` port.
- Added behavior coverage in `PlaybackMediaSourceProviderTest.streamingRestoreProviderPortDelegatesToHeaderStore`, proving the queue restore port delegates both restored-track lookup and data-path header restore to the existing header store.
- Audit metrics after the slice: `EchoPlaybackService.java` 1459 lines, 55 `private Playback*` fields, 8 `fromPlaybackQueueManager(...)` calls, 0 `queueStateSnapshot()` suppliers, 41 `Playback*Owner` files.
- This did not add a Manager/Coordinator/Controller and did not move streaming restore policy into the service.

Verification:

```powershell
.\gradlew.bat :feature:playback:compileDebugKotlin :feature:playback:testDebugUnitTest --tests app.yukine.playback.PlaybackMediaSourceProviderTest --tests app.yukine.playback.PlaybackQueueManagerTest :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
# BUILD SUCCESSFUL
```
