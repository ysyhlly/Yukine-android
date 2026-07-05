# Yukine 架构整治计划（复核版）

日期：2026-06-26  
适用范围：`app/src/main/java/app/yukine` 当前工作树  
用途：作为后续架构整治、MVVM 迁移、依赖注入补齐、目录重组和测试护栏的执行参考

---

## 1. 文档定位

这份文档不是替代现有迁移文档，而是对外部审查结论做一次当前工作树复核后，产出的执行计划表。

与现有文档的关系：

- `docs/MVVM_MIGRATION_HANDOFF.md`
  - 仍是主迁移手册，记录长期目标、边界规则和阶段性完成情况。
- `docs/MVVM_MIGRATION_PROGRESS_2026-06-25.md`
  - 记录最近一轮迁移结果和验证情况。
- `docs/STREAMING_VIEWMODEL_SPLIT_PLAN.md`
  - 只负责 streaming 方向的专项拆分。
- 本文档
  - 负责把“审查问题 -> 当前事实 -> 可执行计划 -> 验收方式”整理成一份总表。

---

## 2. 复核结论

下列结论基于 2026-06-26 对当前仓库的本地复核，不直接照抄旧审查。

### 2.1 已复核的事实

| 项目 | 复核结果 | 备注 |
|---|---:|---|
| `app/src/main/java/app/yukine` ??????? | 288 | ??????????? |
| `app/src/main/java/app/yukine` ?????? | 160 | ????????? |
| `*Controller` ??? | 56 | ???? 58??????? |
| `*Bindings` 文件数 | 3 | 说明桥接层仍然很多 |
| `*ViewModel` 文件数 | 18 | ViewModel 已明显扩张 |
| `app/src/test/java/app/yukine` ????? | 137 | ???? 165 ?????????? |
| `MainActivity.java` ?? | 3663 | ????????? |
| `EchoPlaybackService.java` 行数 | 3893 | 播放边界体量很大 |
| `StreamingViewModel.kt` 行数 | 1932 | 仍是超大 ViewModel |
| `SettingsViewModel.kt` 行数 | 771 | 已迁出不少逻辑，但仍偏大 |
| Hilt Module 文件数 | 2 | `DashboardDataModule.kt`、`StreamingDataModule.kt` |
| `@HiltViewModel` 数量 | 5 | Hilt 已接入，但覆盖不均匀 |

### 2.2 旧审查中需要修正的点

1. “依赖注入配置缺失”不完全准确。
   当前仓库已经有 Hilt、2 个 Module、5 个 `@HiltViewModel`。问题不是“没有 DI”，而是“DI 覆盖不完整，MainActivity 仍大量手动装配”。

2. “MainActivity 只有字段注入、ViewModel 手动管理”需要更精确。
   当前 `MainActivity` 里既有 5 个 `@Inject` 字段，也有大量 `new XxxController(...)`、`new XxxUseCase(...)`、`new XxxBindings(...)`，同时通过 `new ViewModelProvider(this).get(...)` 手动获取 11+ 个 ViewModel。核心问题是宿主装配过重，不是单一技术点。

3. “Repository 没有响应式流”需要收窄表述。
   当前很多 ViewModel 已经用 `StateFlow` 持有 UI 状态，但 `MusicLibraryRepository.java` 仍主要暴露同步 `List` / imperative load-save API，响应式数据源没有真正下沉到数据层。

4. “立即按功能域拆包”方向正确，但不应作为第一步。
   纯目录搬迁会制造大量 import churn。如果 owner 边界还没收稳，先搬文件只会增加 review 成本。更合理顺序是：先收边界和宿主职责，再按稳定 owner 移包。

5. 当前工作树处于迁移中，不是静态旧项目。
   仓库已有大量未提交和未跟踪改动，且 `docs/MVVM_MIGRATION_HANDOFF.md` 已明确把外部审查作为优先级输入，而不是无条件事实源。

---

## 3. 当前核心问题

| 编号 | 问题 | 当前证据 | 直接风险 |
|---|---|---|---|
| A1 | 根目录文件爆炸 | 根目录 210 个文件 | 文件查找困难，owner 不清晰 |
| A2 | 宿主过重 | `MainActivity.java` 2299 行，手动装配大量对象 | 新功能继续堆进 Activity |
| A3 | ????? | 56 ? Controller + 3 ? Bindings | ???????????? |
| A4 | DI 覆盖不完整 | 只有 2 个 Module，`MainActivity` 仍手动 `new` 大量依赖 | 替换实现困难，依赖图分散 |
| A5 | 数据层同步接口为主 | `MusicLibraryRepository.java` 主要暴露 `List`/同步 load/save | UI 更新依赖手动刷新和桥接 |
| A6 | 超大状态 owner | `StreamingViewModel.kt` 1932 行，`SettingsViewModel.kt` 771 行 | feature 边界继续模糊 |
| A7 | 迁移过程中的脏工作树 | 当前有大量 `M/D/??` 改动 | 不适合做无边界的大搬迁 |

---

## 4. 执行原则

1. 先做边界收敛，再做目录整理。
2. 先降低 `MainActivity` 装配密度，再补 DI。
3. 先把数据 owner 稳住，再推进数据层响应式改造。
4. 每个阶段都必须带最小验证：至少一条快速检查和一条信心检查。
5. 不在当前脏工作树里做“纯重命名 + 纯搬家”的大提交。
6. 当前方向（2026-06-28）改为“先收口、再判断是否继续迁移”。暂停默认新增 `Manager` / `Coordinator` / `Controller` / `Bindings` / `Gateway`，优先删除纯转发层、补契约测试、恢复可审查基线；只有当一个切片能净减少文件数、方法数、状态源、依赖或调用链时，才继续推进 P1/P2。
7. 新审查修正（2026-06-28）：仅删除薄包装不能算完成迁移；必须优先降低 `MainActivity` 手动装配/匿名回调、`EchoPlaybackService` 播放策略残留、`EchoDatabaseHelper` migration 风险，以及并发模型碎片化。
8. 删除旧 Controller/Test 前必须说明行为覆盖迁到了哪里；字符串契约测试只能防回流，不能替代行为测试、依赖方向检查或 smoke 证据。
9. 当前收敛事实（2026-06-29）：`DownloadsCoordinator`、`FileIOCoordinator`、`NetworkCoordinator`、`LibraryCoordinator`、`NavigationCoordinator`、`SettingsCoordinator`、`StreamingCoordinator`、`PlaybackCoordinator`、`NowPlayingQueueCoordinator` 已删除；`:app` 根包文件数为 144，`MainActivityBase.java` 为 3613 行，剩余 coordinator 只有 `NetworkRenderCoordinator`。播放启动现在直接进入 `PlaybackStartController`，service bind/release 直接进入 `PlaybackServiceConnectionController`，app visible 直接调用 `EchoPlaybackService.setAppVisible(...)`；queue intents 继续由 `mountNavHostShell()` 中实际生效的 `queueViewModel.bindIntentListener(...)` 路径和 `QueueActionController` 处理；`PlaybackStateUpdateController` 已改为无状态 `object`，不再由宿主持有/构造；`PlaybackServiceHostController` 保留 service connect/disconnect 策略 owner，但不再作为 Activity 字段保存；`NetworkMenuRenderController`、`NetworkTrackListRenderController`、`NetworkSourcesRenderController` 和 `StreamingSearchRenderController` 保留为 `NetworkRenderCoordinator` 消费的 render owner，但不再作为 Activity 字段保存；streaming auth intent 直接进入 `StreamingAuthCallbackController`。
10. 2026-06-29 continuation fact: `PlaybackActionController.Listener`, `QueueActionController.Listener`, `QueueRenderController.Listener`, `HomeDashboardRenderController.Listener`, `NowPlayingStateController.Listener`, `RecommendationActionCallbacks`, `StreamingPlaybackController.Listener`, `PlaybackStartController.Listener`, `PlaybackStateEventController.Listener`, `PlaybackServiceHostController.Host`, `TrackListRenderController.Listener`, `HeartbeatRecommendationController.Listener`, `DocumentPickerController.Listener`, `BackgroundImagePickerController.Listener`, and `MainPermissionController.Listener` policy have moved out of Java anonymous blocks into `MainPlaybackActionListener`, `MainQueueActionListener`, `MainQueueRenderListener`, `MainHomeDashboardRenderListener`, `MainNowPlayingStateListener`, `MainRecommendationActionCallbacks`, `MainStreamingPlaybackListener`, `MainPlaybackStartListener`, `MainPlaybackStateEventListener`, `MainPlaybackServiceHost`, `MainTrackListRenderListener`, `MainHeartbeatRecommendationListener`, `MainDocumentPickerListener`, `MainBackgroundImagePickerListener`, and `MainPermissionListener`; shell/home listeners are provided by `ShellModule`, playback listeners/host owners are provided by `PlaybackUiModule`, track-list render listener is provided by `LibraryModule`, recommendation and heartbeat recommendation listeners are provided by `StreamingModule`, platform picker and permission listeners are provided by `PlatformModule`, and all are covered by focused behavior tests plus `MainActivityArchitectureContractTest`. Pending playback state now lives in `MainPlaybackStartListener` instead of `MainActivityBase`, playback state event policy now lives in `MainPlaybackStateEventListener`, playback service host attach/clear/render policy now lives in `MainPlaybackServiceHost`, track-list action/chrome policy now lives in `MainTrackListRenderListener`, queue render action policy now lives in `MainQueueRenderListener`, home dashboard action/shuffle policy now lives in `MainHomeDashboardRenderListener`, now-playing state/floating-lyrics policy now lives in `MainNowPlayingStateListener`, and recommendation action callback policy now lives in `MainRecommendationActionCallbacks`. Current recheck after this slice: `MainActivityBase.java` 3187 lines, `EchoPlaybackService.java` 2469 lines, `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, 56 main-source Java files, 44 main-source `*Controller` files, 127 dirty entries.
11. 2026-06-29 continuation fact: `StreamingPlaylistDialogController.Listener` callbacks moved out of the Java base anonymous block into `MainStreamingPlaylistDialogListener`, provided by `StreamingModule` and covered by `MainStreamingPlaylistDialogListenerTest` plus `MainActivityArchitectureContractTest`. The Java base now injects `MainStreamingPlaylistDialogListenerFactory` and only wires existing status/controller lambdas into it. Current recheck after this slice: `MainActivityBase.java` 3179 lines, `EchoPlaybackService.java` 2469 lines, `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, 56 main-source Java files, 44 main-source `*Controller` files, 127 dirty entries.
12. 2026-06-29 continuation fact: `StreamingPlaylistController.Listener` policy moved out of the Java base anonymous block into `MainStreamingPlaylistListener`, provided by `StreamingModule` and covered by `MainStreamingPlaylistListenerTest`, `StreamingPlaylistControllerTest`, and `MainActivityArchitectureContractTest`. The Java base now injects `MainStreamingPlaylistListenerFactory` and only supplies existing route, library, status, navigation, and dialog lambdas to the Kotlin owner. Current recheck after this slice: `MainActivityBase.java` 3131 lines, `EchoPlaybackService.java` 2469 lines, `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, 56 main-source Java files, 44 main-source `*Controller` files, 131 dirty entries.
13. 2026-06-29 continuation fact: `StreamingPlaylistImportDialogController.Listener` callbacks moved out of the Java base anonymous block into `MainStreamingPlaylistImportDialogListener`, provided by `StreamingModule` and covered by `MainStreamingPlaylistImportDialogListenerTest`, `StreamingPlaylistControllerTest`, and `MainActivityArchitectureContractTest`. The Java base now injects `MainStreamingPlaylistImportDialogListenerFactory` and only supplies selected-provider, Luoxue dialog, and playlist-link import lambdas to the Kotlin owner. Current recheck after this slice: `MainActivityBase.java` 3121 lines, `EchoPlaybackService.java` 2469 lines, `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, 56 main-source Java files, 44 main-source `*Controller` files, 133 dirty entries.
14. 2026-06-29 continuation fact: `StreamingManualCookieController.Listener` callbacks moved out of the Java base anonymous block into `MainStreamingManualCookieListener`, provided by `StreamingModule` and covered by `MainStreamingManualCookieListenerTest`, `DefaultStreamingSearchActionHandlerTest`, and `MainActivityArchitectureContractTest`. The Java base now injects `MainStreamingManualCookieListenerFactory` and only supplies selected-provider, manual-cookie dialog, login-success, and status lambdas to the Kotlin owner. Current recheck after this slice: `MainActivityBase.java` 3107 lines, `EchoPlaybackService.java` 2469 lines, `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, 56 main-source Java files, 44 main-source `*Controller` files, 135 dirty entries.
15. 2026-06-29 P0 baseline freeze recheck: current `git status --short` reports four modified entries: `M docs/ARCHITECTURE_REMEDIATION_PLAN_2026-06-26.md`, `M docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`, `M docs/MVVM_MIGRATION_HANDOFF.md`, and `M gradle.properties`. There are no current deleted test files under `app/src/test` or `app/src/androidTest`, so the deleted-test replacement map is empty for this checkpoint. Current counts are `MainActivityBase.java` 2968 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 170, and root-package Java files 15. `ShellViewModel` / `ShellState` / `ShellAction` source files are absent; remaining mentions are historical correction notes or contract-test guards. Serial verification passed with default Gradle settings: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain` and `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain`. Do not default to `--no-daemon` or `--max-workers=1`; use them only after a reproducible daemon, KSP, or lock issue.
16. 2026-06-29 P1 `initializeStoresAndDataGateways()` playlist action gateway slice: `LibraryModule` now provides the stable `LibraryPlaylistActionGateway` through `MainLibraryPlaylistActionGateway`, backed by `MusicLibraryPlaylistActionOperations(repository, syncStore)`. `MainActivityBase` now binds the injected gateway directly and no longer owns the anonymous playlist action gateway block, the local `MusicLibraryPlaylistActionOperations`, seven playlist action use case constructors, or the `StreamingPlaylistSyncStore` field. `LibraryPlaylistActionContracts.kt` now holds the playlist action contract types outside the large `LibraryViewModel.kt`, while `LibraryViewModel` remains the UI state/action owner. Current recheck after this slice: `MainActivityBase.java` 2927 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 172, root-package Java files 15, and dirty entries 11 before this documentation update. Field count is not yet a P1 exit win because the Activity swapped the sync-store field for an injected gateway, but host data-layer coupling and manual construction both decreased. Guarded by `MainLibraryPlaylistActionGatewayTest` and `MainActivityArchitectureContractTest`; focused tests passed with `.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainLibraryPlaylistActionGatewayTest --tests app.yukine.MainActivityArchitectureContractTest --rerun-tasks --console=plain` after a default cached run exposed stale transformed class output, and compile passed with `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain`.
17. 2026-06-29 P1 library collection/import gateway slice: `LibraryModule` now provides `LibraryCollectionGateway` through `MainLibraryCollectionGateway`, `LibraryImportGateway` through `MainLibraryImportGateway`, and `LibraryDocumentGateway` through `ContentResolverLibraryDocumentGateway`, reusing the existing collection/import/document owners instead of adding root bindings or controllers. `MainActivityBase` now binds the injected gateways directly and no longer owns the anonymous collection/import gateway blocks, host-owned `MusicLibraryCollectionOperations`, repeated import use case constructors, Activity-local `MusicLibraryImportOperations`, `ContentResolverLibraryDocumentGateway(getContentResolver(), ...)`, or `toLibraryLoadResultUi(...)`. Current recheck after the document gateway provider slice: `MainActivityBase.java` 2872 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 172, and root-package Java files 15. Guarded by `LibraryCollectionUseCasesTest`, `LibraryImportUseCasesTest`, `ContentResolverLibraryDocumentGatewayTest`, and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.
18. 2026-06-29 P1 settings preference provider slice: `SettingsModule` now provides `LoadSettingsPreferencesUseCase` and `ApplySettingsPreferenceUseCase`; `MainActivityBase` only calls the injected use cases for settings load and persistence binding. The host no longer constructs `MusicLibrarySettingsPreferenceLoadOperations`, `LoadSettingsPreferencesUseCase`, `MusicLibrarySettingsPreferenceOperations`, or `ApplySettingsPreferenceUseCase`. Current recheck after this slice: `MainActivityBase.java` 2866 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 173, and root-package Java files 15. Root files increase by one for the settings Hilt module; Activity manual construction and call-chain length decrease. Guarded by `LoadSettingsPreferencesUseCaseTest`, `ApplySettingsPreferenceUseCaseTest`, and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.
19. 2026-06-29 P1 library store factory slice: `MainLibraryStore` now exposes `MainLibraryStoreFactory`, and `LibraryModule` provides both `LibrarySearchUseCase` and that factory. `MainActivityBase` now calls `libraryStoreFactory.create(viewModel)` and no longer constructs `MainLibraryStore`, `LibrarySearchUseCase`, or `MusicLibrarySearchOperations` directly. Current recheck after this slice: `MainActivityBase.java` 2864 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 173, and root-package Java files 15. Guarded by `LibrarySearchUseCaseTest`, `NetworkLibraryStoreDirectAccessTest`, `PlayHistoryActionControllerTest`, and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers after rerunning the same focused command with a longer tool timeout.
20. 2026-06-29 P1 streaming search action handler factory slice: `DefaultStreamingSearchActionHandler` now exposes `MainStreamingSearchActionHandlerFactory`, provided by `StreamingModule`. `MainActivityBase` now calls `streamingSearchActionHandlerFactory.create(streamingViewModel, streamingActionGateway)` and no longer constructs `DefaultStreamingSearchActionHandler` directly in production. Current recheck after this slice: `MainActivityBase.java` 2865 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 173, and root-package Java files 15. The manual-construction count decreases, while line count and field count are not claimed as wins because the injected factory replaces local construction. Guarded by `DefaultStreamingSearchActionHandlerTest` and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.
21. 2026-06-29 P1 track share operations provider slice: `ShellModule` now provides `TrackShareOperations` through `TrackShareManagerOperations(trackShareManager, nativeMusicShareManager)`. `MainActivityBase` now injects `TrackShareOperations` and no longer holds `TrackShareManager` / `NativeMusicShareManager` fields or constructs `TrackShareManagerOperations` locally. Current recheck after this slice: `MainActivityBase.java` 2862 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 173, and root-package Java files 15. Activity field count and manual-construction count decrease without adding root files, bindings, or controllers. Guarded by `TrackShareLauncherTest` and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.
22. 2026-06-29 P1 settings store provider slice: `SettingsModule` now provides the Activity-scoped `MainSettingsStore`. `MainActivityBase` injects `settingsStore` and no longer constructs `new MainSettingsStore()` in `initializeStoresAndDataGateways()`. Current recheck after this slice: `MainActivityBase.java` 2861 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 173, and root-package Java files 15. Activity manual-construction count decreases without adding root files, bindings, or controllers. Guarded by `LoadSettingsPreferencesUseCaseTest`, `ApplySettingsPreferenceUseCaseTest`, and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.
23. 2026-06-29 P1 lyrics settings use-case provider slice: `SettingsModule` now provides `LoadLyricsSettingsUseCase` through `MusicLibraryLyricsSettingsOperations(repository)`. `MainActivityBase` injects `loadLyricsSettingsUseCase` and no longer constructs `LoadLyricsSettingsUseCase` or `MusicLibraryLyricsSettingsOperations` locally while configuring `LyricsViewModel`. Current recheck after this slice: `MainActivityBase.java` 2858 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 173, and root-package Java files 15. Activity manual-construction count decreases without adding root files, bindings, or controllers. Guarded by `LoadLyricsSettingsUseCaseTest` and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.
24. 2026-06-29 P1 library gateway policy owner slice: `LibraryModule` now provides `MainLibraryGatewayFactory`, and `MainLibraryGateway` owns the former `LibraryGateway` Java anonymous block policy for track-list play, localized status keys, favorite refresh, playlist add, library routing, search, import, and scan. `MainRouteController` implements the narrow `LibraryRouteActions` boundary, so Activity does not replace the old anonymous block with another route adapter. Current recheck after this slice: `MainActivityBase.java` 2789 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 174, and root-package Java files 15. Root files increase by one for behavior coverage, while Activity anonymous policy and host routing logic decrease. Guarded by `MainLibraryGatewayTest`, `LibraryViewModelTest`, `MainRouteControllerTest`, and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.
25. 2026-06-29 P1 streaming action gateway policy owner slice: `StreamingModule` now provides `MainStreamingActionGatewayFactory`, and `MainStreamingActionGateway` owns the former Java anonymous `MainActivityStreamingActionGateway` policy for quality/language lookup, auth launch delegation, resolved-track playback, login-success playlist handling, and manual cookie import ordering. `MainActivityBase` now wires host/platform lambdas into the factory and no longer contains `new MainActivityStreamingActionGateway()`. Current recheck after this slice: `MainActivityBase.java` 2769 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 175, and root-package Java files 15. Root files increase by one for behavior coverage, while Activity anonymous streaming policy decreases. Guarded by `MainStreamingActionGatewayTest`, `DefaultStreamingSearchActionHandlerTest`, and `MainActivityArchitectureContractTest`; focused tests and compile passed with default daemon/workers.

---

## 5. 全部计划表

| ID | 优先级 | 工作流 | 目标 | 主要文件/边界 | 前置依赖 | 交付物 | 快速检查 | 信心检查 |
|---|---|---|---|---|---|---|---|---|
| P0-1 | P0 | 基线冻结 | 明确当前编译、测试、脏工作树状态 | `README.md`、`docs/*`、Gradle、`git status` | 无 | 一份基线记录和切片策略 | `git status --short` 已记录 | `compileDebugKotlin + compileDebugJavaWithJavac` |
| P0-2 | P0 | 文档对齐 | 明确本文档与 `MVVM_MIGRATION_HANDOFF.md` 的职责边界 | `docs/MVVM_MIGRATION_HANDOFF.md`、本文档 | P0-1 | 文档引用关系稳定 | 文档链接可追踪 | 后续阶段更新有统一入口 |
| P0-3 | P0 | 行为覆盖盘点 | 统计已删除测试和每条迁移切片的替代行为测试 | `app/src/test/java/app/yukine`、`MainActivityArchitectureContractTest.java` | P0-1 | 删除测试 -> 替代测试映射 | `git status --short` 中 D 测试已归类 | 受影响 focused tests 可单独运行 |
| P1-1 | P1 | 宿主装配收缩 | 把 `MainActivity.onCreate` 手动装配和匿名 listener 策略继续移出宿主 | `MainActivity.java`、`di/*`、feature assembly owner | P0-1, P0-3 | Activity 只保留 lifecycle/root/service/launcher delegation | `new Xxx`、匿名 listener、字段数下降 | 定向编译 + 行为测试 + 契约测试 |
| P1-2 | P1 | 平台 owner 补齐 | picker、dialog、backup、share、permission 全部有明确 owner | `*PickerController`、`*DialogController`、`SettingsEffectBindings.kt` | P1-1 | 平台边界清单和 owner 清单 | Activity 不再新增平台分支 | 定向单测覆盖平台 owner |
| P1-3 | P1 | 桥接层清理 | 删除只转发一层的 Controller/Bindings，且不得把策略回流到 Activity | `*Controller.kt`、`*Bindings.kt`、各 `ViewModel` | P1-1, P1-2 | 事件路径缩短，回调接口收窄 | Activity 装配/匿名策略不增加 | 相关行为单测和契约测试通过 |
| P1-4 | P1 | `Settings` 收口 | 完成设置页 owner 收敛，避免回流到宿主 | `SettingsViewModel.kt`、`SettingsPageStateBuilder.kt`、`SettingsRuntimeApplier.kt` | P1-2, P1-3 | 设置链路只经 `ViewModel -> effect/runtime/persistence` | 不新增 settings gateway 回流 | `SettingsViewModelTest` + 契约测试 |
| P1-5 | P1 | `Streaming` 收口 | 继续拆 `StreamingViewModel` 的搜索、认证、歌单、推荐、播放编排 | `StreamingViewModel.kt`、`StreamingRecommendationViewModel.kt`、`StreamingPlaylistController.kt` | P1-3 | 按 feature 拆小 owner | 行数和职责下降 | `Streaming*Test` + 编译 |
| P1-6 | P1 | 播放服务瘦身 | 把队列计算、缓存调度、URI 判断等非服务职责下沉到已有 manager | `playback/EchoPlaybackService.java`、`playback/manager/*` | P1-1 | Service 只保留 Media3/lifecycle/session 边界 | Service 行数/策略方法下降 | 播放相关单测 + smoke build |
| P1-7 | P1 | DI 补齐 | 把稳定 use case/controller/executor 的手动 `new` 收进 Hilt，减少宿主装配 | `di/*`、`MainActivity.java`、相关 owner | P1-1, P1-3 | DI 图更完整，装配减少 | `MainActivity` 的 `new` 数量下降 | 编译 + 关键注入路径测试 |
| P1-8 | P1 | 数据层护栏 | 先为 `EchoDatabaseHelper` 建 migration/事务测试，再拆 `MusicLibraryRepository` 或评估 Room | `data/MusicLibraryRepository.java`、`EchoDatabaseHelper.java`、数据库测试 | P1-7 | migration 基线和数据 owner 边界 | schema 变化有测试覆盖 | migration/事务测试 + 编译 |
| P1-9 | P1 | 并发模型收敛 | 盘点并减少裸 Thread、独立 ExecutorService、自定义 scheduler | `LyricsRepository`、`PlaybackPrecacheManager`、`PlaybackTaskScheduler`、协程调用点 | P1-6 | 每条后台任务有 owner/shutdown/生命周期说明 | 新代码不新增裸线程 | 相关单测 + 编译 |
| P2-1 | P2 | 响应式数据源 | 把关键读取路径从同步 `List` 提升到 `Flow`/可观察接口 | `data/*Repository*`、ViewModel | P1-8 | 关键列表页不再靠手动刷新驱动 | 新读路径有 observe API | 定向 ViewModel 测试 |
| P2-2 | P2 | 包结构重组 | 在边界稳定后，把 root 目录文件迁回 feature 域 | `app/src/main/java/app/yukine/*` | P1-3, P1-5, P1-8 | 根目录显著瘦身 | root 文件数下降 | 全量编译 + 搜索路径复核 |
| P2-3 | P2 | 命名与文案规范 | 统一后缀、文案入口、事件命名 | `AppLanguage.java`、`*Controller`、`*Bindings`、`*ViewModel` | P2-2 | 一致的命名规范和文案入口 | 新代码符合规则 | 定向测试 + grep 复核 |
| P2-4 | P2 | Kotlin 风格收口 | 不可变集合、`buildList`、减少 Java 风格样板 | Kotlin 源文件 | P1-8 | 代码风格一致 | review 中无新增冗余集合 | 编译 |
| P2-5 | P2 | 质量门禁 | 加强 Detekt/ktlint/契约测试/回归脚本 | Gradle、测试目录、`docs/RELEASE_EXPERIENCE_CHECKLIST.md` | 全部前置 | 明确门禁脚本和失败标准 | 本地可单独执行 | 定向测试 + `:app:check` |

---

## 6. 推荐执行顺序

### 第一批：必须先做

1. `P0-1` 基线冻结
2. `P0-3` 行为覆盖盘点
3. `P1-1` 宿主装配收缩
4. `P1-6` 播放服务瘦身
5. `P1-8` 数据层护栏

原因：

- 这是后续一切拆分的前提：先确保当前大 diff 可审查、行为测试不丢、三个 god-class 根因有明确下降路径。
- 如果不先收宿主、服务和数据库护栏，继续删桥接层只会把复杂度换位置。
- `P1-6` follows the playback target shape in `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`: `EchoPlaybackService` keeps only Android/Media3 system boundaries, while queue/cache/resolve/lyrics/notification policy moves into small real owners without adding a universal `PlaybackServiceFacade`.

### 第二批：收稳定边界

1. `P1-4` Settings
2. `P1-5` Streaming
3. `P1-2` 平台 owner 补齐
4. `P1-3` 桥接层清理
5. `P1-7` DI 补齐
6. `P1-9` 并发模型收敛

原因：

- 这几条是当前最明显的高耦合热点。
- 它们稳定后，数据层和目录重组才不会搬到一半又改 owner。

### 第三批：做结构性收尾

1. `P1-8` 数据层拆分或 Room 迁移评估
2. `P2-1` 响应式数据源
3. `P2-2` 包结构重组
4. `P2-3` 到 `P2-5` 规范与门禁

原因：

- 这些工作更适合在 owner 已稳定后进行。
- 其中“包结构重组”应视为收尾，不建议作为开场动作。

---

## 7. 每阶段统一验收标准

每个阶段至少满足以下四项：

1. 变更目标明确
   - 能用一句话说清“哪个 owner 变薄了，哪个 owner 接手了”。
2. 事件路径缩短
   - 至少减少一层无策略转发。
3. 测试有覆盖
   - 至少补一条与本阶段改动直接相关的单测或契约测试。
4. 编译可恢复
   - `:app:compileDebugKotlin :app:compileDebugJavaWithJavac` 通过。

对高风险阶段再增加：

- `:app:testDebugUnitTest`
- 相关设备/运行时 smoke 验证

---

## 8. 当前最合适的下一步

当前最适合立刻开始的是：

1. 先把当前已迁出的桥接层和 shell 统一收口，避免再开新 owner。
2. 以单个 Gradle 任务串行验证当前切片，先拿到稳定编译结果，再决定是否继续下一刀。
3. 只保留能够证明净减复杂度的迁移动作，并同步更新 `MainActivityArchitectureContractTest.java`。

不建议立刻开始：

- 大规模包移动
- 单纯重命名
- 在当前脏工作树上做机械性全仓整理
- 并行推进新的迁移切片

---

## 9. 复核备注

- 本计划明确采用“复核优先”原则：外部审查结论只作为优先级输入，不直接当作当前事实。
- 当前仓库已处于持续迁移中，很多问题是“仍未完成”，不是“完全没有做”。
- 后续如果根目录文件数、`MainActivity` 行数、`StreamingViewModel` 行数明显变化，本文档应同步更新对应数字。


## 2026-06-27 DIRECTION PIVOT: stabilization before more extraction

- New controlling doc: `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`.
- This pivot supersedes the previous default of continuing fast owner/manager extraction when the two conflict.
- Freeze broad architecture expansion first: do not add new `Manager`, `Coordinator`, `Controller`, `Bindings`, or `Gateway` layers by default.
- Stabilize the dirty migration surface, record current counts, and prefer reviewable commits/checkpoints before more migration.
- Reduce existing over-abstraction: merge/delete forwarding-only owners, shrink oversized provider/listener interfaces, and shorten UI -> service/data call chains.
- 2026-06-28 update: `PlaybackSessionGateway` was deleted as a forwarding-only service-internal wrapper; `EchoPlaybackService` now calls `PlaybackSessionManager` directly.
- 2026-06-28 update: `SettingsPageRenderController` was deleted after render ownership moved to `SettingsViewModel`; remaining label helpers now live in `SettingsLabelFormatter`.
- 2026-06-28 update: `PlaybackQueueManager.QueueProvider.currentTrack()` was removed; the manager derives it from `queue()` and `currentIndex()` to shrink the large provider interface.
- 2026-06-28 update: `DownloadQualitySelectedCallback` was removed in favor of a direct Kotlin function callback.
- 2026-06-28 update: `DownloadManagerProvider` was removed in favor of a direct `() -> TrackDownloadRequestQueue?` dependency.
- 2026-06-28 update: `NetworkMenuContentSink` was removed; `NetworkMenuEventController` now updates `NetworkMenuViewModel` directly, shortening network menu chrome publication by one forwarding interface.
- 2026-06-28 update: `CollectionsActionsSink` and the dead `publishCollectionsActions(...)` listener override were removed; collections actions now publish directly through `CollectionsViewModel.updateActions(...)`.
- 2026-06-28 update: unused migration callback types were removed from Collections, Library, NowPlaying, and Queue contracts where direct owner calls already exist.
- 2026-06-28 update: additional unused action contracts were removed from queue, queue render, network track-list, collections render, and settings controls paths after exact reference checks.
- 2026-06-28 update: recommendation self-interfaces were removed; `StreamingRecommendationViewModel` now owns direct action methods while `HeartbeatRecommendationPlayer` remains as the controller-facing boundary.
- 2026-06-28 update: `PlaylistExportController` was deleted; pending playlist export state now lives in `DocumentPickerController`, which emits export URI plus playlist id/name directly to the existing document listener.
- 2026-06-28 update: `LibraryPlaylistExportCallback` was removed after the only production export caller used an empty callback; the Java export entry now has no callback parameter.
- 2026-06-28 update: root-package `*Bindings*` zero count is now protected by `MainActivityArchitectureContractTest.rootPackageHasNoMigrationBindingsFiles`, adding a directory-level guard beyond per-file string checks.
- 2026-06-28 update: `LibraryPlayHistoryClearedCallback` and `LibraryViewModel.clearPlayHistoryJava(...)` were removed after play-history clearing settled on the direct `PlayHistoryActionController -> clearPlayHistory { ... }` path.
- 2026-06-28 update: the duplicate collection favorite write path was removed (`saveLibraryFavorite*`, `LibraryCollectionGateway.setFavorite`, and `SetLibraryFavoriteUseCase`); favorite toggles now stay on `LibraryFavoriteWriter -> ToggleFavoriteUseCase`.
- 2026-06-28 update: `PlaybackServiceProvider` and `PlaybackServiceStarter` were removed from `NowPlayingPlaybackGatewayAdapter`; the adapter now takes direct function dependencies while keeping `NowPlayingPlaybackGateway` as the service boundary.
- 2026-06-28 update: `BackupStatusSink` was removed from `BackupRestoreLauncher`; backup status-key publishing now uses a direct `(String) -> Unit` dependency.
- 2026-06-28 update: `DownloadStatusSink` was removed from `DownloadRequestController`; download feedback now uses a direct `(String) -> Unit` dependency.
- 2026-06-28 update: `TrackShareLanguageProvider` and `TrackShareStyleProvider` were removed from `TrackShareLauncher`; share language/style lookup now uses direct `() -> String` dependencies.
- 2026-06-28 update: `PlayHistoryLanguageModeProvider` and `PlayHistoryStatusSink` were removed from `PlayHistoryActionController`; play-history status publishing now uses direct function dependencies while keeping `PlayHistoryStateStore`.
- 2026-06-28 update: `BackgroundLanguageModeProvider` and `BackgroundTransformProvider` were removed from `BackgroundImagePickerController`; preview language/transform lookup now uses direct function dependencies.
- 2026-06-28 update: `StatusLanguageModeProvider` and `MessageLanguageModeProvider` were removed from status-message localization; `StatusMessageController` and `MessageTextResolver` now share a direct `Supplier<String>` language dependency.
- 2026-06-28 audit correction: wrapper deletion is no longer the default next step unless it also reduces `MainActivity` assembly/anonymous listener policy, preserves or replaces behavior tests, and does not defer `EchoPlaybackService` / `EchoDatabaseHelper` root risks.
- 2026-06-29 update: streaming search render listener policy moved out of the Java base anonymous `StreamingSearchRenderController.Listener` block into `MainStreamingSearchRenderListener`, provided by `StreamingModule` and covered by `MainStreamingSearchRenderListenerTest`.
- 2026-06-29 recheck after that slice: dirty entries 137, main Java files 56, controller files 44, `MainActivityBase.java` 3024 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines.
- 2026-06-29 direction correction: stop treating anonymous listener extraction as an unbounded migration loop. Execute two lanes in batches: finish at most one large listener slice, then pivot to Hilt construction migration for stable `initializeStoresAndDataGateways()` dependencies such as `LibraryPlaylistActionGateway`, `StreamingLocalPlaylistOperations`, `LoadSettingsPreferencesUseCase`, `ImportStreamingPlaylistUseCase`, `SyncStreamingPlaylistUseCase`, `EnsureStreamingLoginPlaylistUseCase`, and `GetStreamingPlaylistLinkUseCase`.
- 2026-06-29 acceptance correction: a new owner must delete Java anonymous business policy, reduce Activity manual construction, shorten a call chain, split a large interface, or add real behavior coverage. Delegation-only listener tests are insufficient as a long-term proof; every 2-3 listener slices must be balanced by a streaming/playback/data behavior test or a harder Hilt/interface reduction slice.
- 2026-06-29 checkpoint requirement: before the next broad slice, record dirty status, compile/test status, current class/file counts, and recommend a commit or rollback checkpoint because the migration surface is already large.
- 2026-06-29 Hilt slice: `StreamingModule` now provides `StreamingPlaylistSyncStore`, `ImportStreamingPlaylistUseCase`, `SyncStreamingPlaylistUseCase`, `EnsureStreamingLoginPlaylistUseCase`, `GetStreamingPlaylistLinkUseCase`, and `StreamingLocalPlaylistOperations` through `MainStreamingLocalPlaylistOperations`. `MainActivityBase` only binds the injected operations into `StreamingViewModel`; the old Java anonymous operations block and four manual use case constructors were removed. Recheck after this slice: dirty entries 5 from clean baseline, main Java files 56, controller files 44, `MainActivityBase.java` 2965 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines.
- 2026-06-29 review response: the provisional `ShellViewModel` / `ShellState` / `ShellAction` line was removed because it was not consumed by the real navigation path (`NavigationViewModel`, `MainRouteController`, and `EchoNavHostState`) and would create a parallel shell state model. Reintroduce typed shell state only when it becomes the single runtime source. The download directory picker unavailable message now uses `AppLanguage` key `download.directory.picker.unavailable` instead of hard-coded Chinese text.
- 2026-06-29 P0 freeze checkpoint: current `git status --short` reports four modified entries: `M docs/ARCHITECTURE_REMEDIATION_PLAN_2026-06-26.md`, `M docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`, `M docs/MVVM_MIGRATION_HANDOFF.md`, and `M gradle.properties`; no test deletions are present, so there is no deleted-test replacement mapping to record. Current recheck counts are `MainActivityBase.java` 2968 lines, `EchoPlaybackService.java` 2469 lines, `feature:data` `EchoDatabaseHelper.java` 2117 lines, `StreamingViewModel.kt` 2013 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, root-package files 170, and root-package Java files 15. Compile and `MainActivityArchitectureContractTest` both passed serially with default Gradle settings, without `--no-daemon` or `--max-workers=1`.
- 2026-06-29 P1 Hilt construction slice: playlist action assembly moved from `MainActivityBase.initializeStoresAndDataGateways()` into `LibraryModule` + `MainLibraryPlaylistActionGateway`. The Java base now binds an injected `LibraryPlaylistActionGateway`, and the old anonymous gateway, local playlist operations, seven use case constructors, and `StreamingPlaylistSyncStore` field are gone from Activity. Recheck counts are `MainActivityBase.java` 2927 lines, root-package `*Bindings*` 0, root-package `*Controller*` 44, and root-package files 172. Focused gateway and architecture tests plus compile passed with default daemon/workers; `--rerun-tasks` was needed once to avoid a stale cached transform output, not as a daemon/KSP fallback.
- 2026-06-29 P1 Hilt construction slice: collection/import gateway assembly moved from Activity anonymous blocks into `LibraryModule` plus `MainLibraryCollectionGateway` and `MainLibraryImportGateway`. `MainActivityBase.java` is now 2873 lines, root-package files remain 172, root-package `*Bindings*` remain 0, and root-package `*Controller*` remains 44. Focused collection/import gateway tests, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: document gateway assembly moved from Activity-local `MusicLibraryImportOperations` plus `ContentResolverLibraryDocumentGateway(getContentResolver(), ...)` into `LibraryModule.provideLibraryDocumentGateway(...)`. `MainActivityBase` now injects and binds `LibraryDocumentGateway`; `MainActivityBase.java` is now 2872 lines, root-package files remain 172, root-package `*Bindings*` remain 0, and root-package `*Controller*` remains 44. Focused document gateway and architecture tests plus compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: settings preference load/apply use cases moved from Activity-local construction into `SettingsModule`. `MainActivityBase` now injects `LoadSettingsPreferencesUseCase` and `ApplySettingsPreferenceUseCase`; `MainActivityBase.java` is now 2866 lines, root-package files are now 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused settings use-case tests, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: library store/search assembly now uses `MainLibraryStoreFactory` and a `LibraryModule`-provided `LibrarySearchUseCase`. `MainActivityBase.java` is now 2864 lines, root-package files remain 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused library search/store tests, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: streaming search action-handler assembly now uses `MainStreamingSearchActionHandlerFactory`, provided by `StreamingModule`. `MainActivityBase.java` is now 2865 lines, root-package files remain 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused action-handler behavior tests, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: track share operations assembly moved from Activity-local `TrackShareManagerOperations(trackShareManager, nativeMusicShareManager)` into `ShellModule.provideTrackShareOperations(...)`. `MainActivityBase.java` is now 2862 lines, root-package files remain 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused share-launcher behavior tests, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: settings store assembly moved from Activity-local `new MainSettingsStore()` into `SettingsModule.provideMainSettingsStore()`. `MainActivityBase.java` is now 2861 lines, root-package files remain 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused settings use-case tests, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: lyrics settings use-case assembly moved from Activity-local `LoadLyricsSettingsUseCase(MusicLibraryLyricsSettingsOperations(repository))` into `SettingsModule.provideLoadLyricsSettingsUseCase(...)`. `MainActivityBase.java` is now 2858 lines, root-package files remain 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused lyrics settings use-case tests, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: lyrics loader assembly moved from Activity-local `LoadTrackLyricsUseCaseLyricsLoader(LoadTrackLyricsUseCase(LyricsRepositoryLoadOperations()))` into `SettingsModule.provideLyricsLoader()`. `MainActivityBase.java` is now 2857 lines, root-package files remain 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused `LoadTrackLyricsUseCaseTest`, `LyricsViewModelTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: network action use-case assembly moved from Activity-local `NetworkActionUseCases(...)`, `MusicLibraryWebDavSourceOperations(repository)`, and `MusicLibraryNetworkLibraryOperations(repository)` into `LibraryModule.provideNetworkActionUseCases(...)`. `MainActivityBase.java` is now 2840 lines, root-package files remain 173, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused `NetworkActionsViewModelTest`, `NetworkLibraryUseCasesTest`, `WebDavSourceUseCasesTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 policy-owner slice: library gateway host policy moved from the Java anonymous `LibraryGateway` block into `MainLibraryGateway`, created by `LibraryModule.provideMainLibraryGatewayFactory()`. `MainRouteController` now implements `LibraryRouteActions`, Activity only supplies existing host/platform lambdas, and `MainActivityBase.java` is now 2789 lines; root-package files are now 174, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused `MainLibraryGatewayTest`, `LibraryViewModelTest`, `MainRouteControllerTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 policy-owner slice: streaming action gateway host policy moved from the Java anonymous `MainActivityStreamingActionGateway` block into `MainStreamingActionGateway`, created by `StreamingModule.provideMainStreamingActionGatewayFactory()`. Activity only supplies existing host/platform lambdas, and `MainActivityBase.java` is now 2769 lines; root-package files are now 175, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused `MainStreamingActionGatewayTest`, `DefaultStreamingSearchActionHandlerTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 policy-owner slice: now-playing playback gateway service-start construction moved from `MainActivityBase.initializeNowPlayingGateways()` into `PlaybackUiModule.provideMainNowPlayingPlaybackGatewayFactory(...)` plus `NowPlayingPlaybackServiceStarter`. Activity only binds `nowPlayingPlaybackGatewayFactory.create(() -> playbackService)` and no longer constructs `NowPlayingPlaybackGatewayAdapter` or the playback service start `Intent`; `MainActivityBase.java` is now 2762 lines, root-package files remain 175, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused `NowPlayingPlaybackGatewayAdapterTest`, `MainNowPlayingGatewayTest`, `NowPlayingViewModelTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 Hilt construction slice: play-history action controller creation moved from `MainActivityBase.initializeStoresAndDataGateways()` into `LibraryModule.provideMainPlayHistoryActionControllerFactory()`. Activity still keeps the real confirmation-dialog path but no longer calls `new PlayHistoryActionController(...)`; `MainActivityBase.java` is now 2760 lines, root-package files remain 175, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused `PlayHistoryActionControllerTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 policy-owner slice: the long `NetworkActionsViewModel.Listener` anonymous block moved from `MainActivityBase.initializeNetworkOwners()` into `MainNetworkActionsListener`, created by `LibraryModule.provideMainNetworkActionsListenerFactory()`. Activity now only binds the injected listener factory with existing callbacks for library replacement, network navigation, collections reload, and status publication; `MainActivityBase.java` is now 2697 lines, root-package files remain 175, root-package `*Bindings*` remain 0, and root-package `*Controller*` remain 44. Focused `MainNetworkActionsListenerTest`, `NetworkActionsViewModelTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 settings runtime controls slice: settings runtime playback, lyrics, and floating-lyrics controls moved out of `MainActivityBase` anonymous implementations into `MainSettingsRuntimeApplierFactory` plus concrete controls beside `SettingsRuntimeApplier`, provided by `SettingsModule`. Activity now only supplies existing runtime providers to `settingsRuntimeApplierFactory.create(...)`; `MainActivityBase.java` is now 2632 lines, root-package files remain 175, root-package `*Bindings*` remain 0, and root-package `*Controller*` remains 44. Focused `SettingsRuntimeApplierTest`, `SettingsViewModelTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 collections render listener slice: the anonymous `CollectionsRenderController.Listener` block moved out of `MainActivityBase.initializeRenderOwners()` into `MainCollectionsRenderListener`, created by `LibraryModule.provideMainCollectionsRenderListenerFactory()`. Activity now only supplies existing host/platform lambdas and selected-playlist state sources; `MainActivityBase.java` is now 2562 lines, root-package files are now 176, root-package `*Bindings*` remain 0, and root-package `*Controller*` remains 44. Focused `MainCollectionsRenderListenerTest`, architecture contract, and compile passed with default daemon/workers.
- 2026-06-29 P1 library-groups render listener slice: the anonymous `LibraryGroupsRenderController.Listener` block and local `new ArtistInfoRepository()` moved out of `MainActivityBase.initializeRenderOwners()` into `MainLibraryGroupsRenderListener`, `LibraryModule.provideMainLibraryGroupsRenderListenerFactory()`, and `LibraryModule.provideArtistInfoRepository()`. Activity now only supplies existing host callbacks to the factory; `MainActivityBase.java` is now 2517 lines, root-package files are now 177, root-package `*Bindings*` remain 0, and root-package `*Controller*` remains 44. Focused `MainLibraryGroupsRenderListenerTest`, architecture contract, and compile passed with default daemon/workers.
- Do not expand `PlaybackQueueManager.QueueProvider` or similar large interfaces without a prior split/merge/inline plan.
- String-based architecture contracts are not enough for fragile flows; pair them with behavior tests, dependency-direction checks, integration smoke, or device evidence.
- Continue P1/P2 only after a slice demonstrably reduces net files, methods, state sources, dependencies, or call-chain length.
## 2026-06-27 ????

- ???????????/????????????? owner ????
- `EchoPlaybackService` ???? `MainActivity` ???????????/???????? launcher intent?
- `PlaybackQueueManager.QueueProvider` ??????????????????????????????
- ?????????????????????????????
- ???????? `docs/ARCHITECTURE_STABILIZATION_PIVOT_2026-06-27.md`???? P1/P2 ????????????????
