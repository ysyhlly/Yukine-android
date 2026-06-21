# Codex 方向规划：StreamingViewModel 拆分收尾

日期：2026-06-19
作者：Claude（接力审查后产出）
状态：**方向建议，未动代码**。Claude 评估后认为这是 codex MVVM 主线的活，强行并行会与契约测试和下一轮规划冲突，故只给方向。

---

## 一、现状（codex 已完成的部分）

`StreamingViewModel` 已被拆出（1178 行），streaming 全部逻辑已迁入。`MainActivityViewModel` 从 2572 行降到 1368 行。**这一步是对的。**

但目前停在「**facade 中间态**」：

- `MainActivityViewModel` 保留了 **86 个 streaming/heartbeat 转发方法**，每个都是一行 `streamingViewModel.xxx(...)`。
- `MainActivityViewModel.streaming` 是 `get() = streamingViewModel.streaming` 的转发属性。
- 调用方（6 个 controller + MainActivity）仍调 `viewModel.xxxStreaming(...)`，没有直连 `StreamingViewModel`。
- `StreamingViewModel` 不走 Hilt（无 `@HiltViewModel`/`@Inject`），靠 4 个 `bindXxx()` 手动注入依赖：`bindStreamingRepository` / `bindStreamingPlaybackCoordinator` / `bindStreamingLocalPlaylistOperations` / `bindStreamingTrackMatchStore`。

## 二、为什么 Claude 没有直接接手删 facade

1. **契约测试（67 个断言）故意锁定了 facade**。例如 `MainActivityArchitectureContractTest` 第 24-27、35 行**要求** `viewModel.configureStreamingRepository()` 存在、要求 `StreamingGatewayEventController` 经它调用、并禁止 MainActivity 直调。删转发会直接违反 codex 自己立的契约。
2. **`MainActivityViewModelStreamingTest`（2414 行）有 159 处 `viewModel.xxxStreaming` 调用、55 个构造点**，本质是借 facade 测 streaming 逻辑。删转发会一次性炸掉这一整片测试。
3. **`MainActivityViewModel.kt:361` 的 `private var streamingViewModel = StreamingViewModel()` 不是 bug**：66 个测试构造点里只有 3 个调 `bindStreamingViewModel`，其余 60+ 个依赖这个占位实例兜底。它是「未绑定场景的默认实现」，**不能简单改 lateinit 或删除**（会炸 60+ 测试）。生产环境里它在 onCreate 第一时间被 bind 替换、从不启动协程，无泄漏。

→ 结论：这些不是「没拆干净的尾巴」，是**互相咬合的一组约束**，必须由掌握 MVVM 主线节奏的一方（codex），把生产代码、契约测试、单测**一起改**。Claude 并行做只会制造两套打架的契约。

## 三、建议的终态

- `StreamingViewModel` 升级为自洽的 `@HiltViewModel`，构造注入 `StreamingRepositorySource`，自己拥有 `configureStreamingRepository()` / `clearExpiredStreamingCache()`，删掉 4 个 `bindXxx` 手动注入。
- 6 个 controller + MainActivity 直接持有并调用 `StreamingViewModel`，不再经 `MainActivityViewModel`。
- `MainActivityViewModel` 删除 86 个 streaming 转发方法 + `streaming` 转发属性 + `streamingViewModel` 字段，彻底与 streaming 解耦。
- `MainActivityViewModelStreamingTest` 整体迁移为 `StreamingViewModelTest`（直接构造 `StreamingViewModel`）。
- 契约测试改为断言「streaming 入口属于 StreamingViewModel」，删掉锁 facade 的旧断言。

## 四、分阶段执行计划（按依赖顺序，每阶段独立编译+测试通过后再进下一阶段）

整体原则：**先让 StreamingViewModel 自洽 → 再逐个迁调用方 → 调用方清空后才删对应转发 → 最后迁测试、改契约**。每个 controller 是独立编译单元，按"调用次数从少到多"推进，先拿最小的练通路径。

### 阶段 0：StreamingViewModel 自洽（前提，最关键）
- 给 `StreamingViewModel` 加 `@HiltViewModel` + `@Inject constructor(streamingRepositorySource: StreamingRepositorySource)`。
- 把 `configureStreamingRepository()` / `clearExpiredStreamingCache()` 从 `MainActivityViewModel` 搬进来；内部直接用注入的 source，不再靠 `bindStreamingRepository`。
- `MainActivity:286` 已经是 `ViewModelProvider(this).get(StreamingViewModel.class)`，Hilt 化后照常工作。
- 保留 `bindXxx` 一轮（planner/taskQueue/localPlaylistOps/trackMatchStore 仍来自 Activity 装配），后续阶段再逐个 Hilt 化。
- 处理 `MainActivityViewModel:361` 占位实例：阶段 0 暂不动（等调用方和测试都迁走后，它自然无引用，最后一阶段删）。
- 契约测试同步：第 34-40 行关于 `configureStreamingRepository`/`clearExpiredStreamingCache` 归属的断言，从 `MainActivityViewModel` 改为断言 `StreamingViewModel` 拥有。

### 阶段 1～5：逐个迁调用方（每个 controller 一个 PR/提交）
按从小到大顺序，降低单步风险：
| 顺序 | Controller | viewModel.streaming 调用数 |
|---|---|---|
| 1 | DailyRecommendationController | 2 |
| 2 | StreamingGatewayEventController | 2 |
| 3 | StreamingPlaybackController | 7 |
| 4 | HeartbeatRecommendationController | 12 |
| 5 | StreamingPlaylistController | 19 |

每个 controller 的迁移动作：
1. 构造函数把 `MainActivityViewModel` 换成（或增加）`StreamingViewModel` 参数。
2. 内部 `viewModel.xxxStreaming(...)` 改为 `streamingViewModel.xxx(...)`。
3. MainActivity 装配处传入 `streamingViewModel`。
4. 跑该 controller 对应的单测 + `assembleDebug`。
5. 同步更新契约测试里针对该 controller 的断言。

### 阶段 6：删 facade
- 全部调用方迁完后，`MainActivityViewModel` 的 86 个转发方法 + `streaming` 属性 + `streamingViewModel` 字段 + `bindStreamingViewModel` 已无人调用，整体删除。
- `MainActivity:287` 的 `viewModel.bindStreamingViewModel(...)` 删除。
- 契约测试删掉 `bindStreamingViewModel`、facade 转发相关的旧断言。

### 阶段 7：迁测试
- `MainActivityViewModelStreamingTest`（2414 行，159 处 facade 调用）整体迁为 `StreamingViewModelTest`：构造 `StreamingViewModel` 直接测，依赖用 fake 注入。
- 这是最大的机械工作量，但阶段 6 之后它已经无法编译（facade 没了），所以**必须和阶段 6 在同一提交内完成**，否则构建红。

## 五、风险与注意

- **阶段 6 和 7 必须同提交**：删 facade 会让 159 处测试调用编译失败，测试迁移不能拖到下一轮。
- **占位实例（:361）最后删**：它是 60+ 测试的兜底，提前删会大面积红。等测试迁到直接构造 StreamingViewModel 后才安全。
- **每阶段都要改契约测试**：契约测试是 codex 的护栏，每动一处 facade 就要同步它，否则护栏自己先红。
- **Hilt 化 StreamingViewModel 后注意**：`MainActivityViewModelStreamingTest` 现在用无参 `StreamingViewModel()` 构造（如 1241、1264 行），Hilt 化后需改为带 `StreamingRepositorySource` 参数构造，阶段 0 就要顺手改这几处。
- 工作量估算：阶段 0 中等，阶段 1-5 每个小到中，阶段 6+7 大（主要是 2414 行测试迁移）。建议 6-8 次提交完成，不要一把梭。

## 六、Claude 这侧的相关改动（不冲突，供参考）

Claude 前几轮在**非 streaming 区**做了安全/工程化/lint 加固，详见 `docs/CODEX_HANDOFF_2026-06-18.md`。与本拆分计划无重叠。其中一条相关：`MainActivityViewModel` 的 `togglePlaybackRemote`/`seekRemote`/`nextRemote`/`previousRemote` 已加 `runCatching` 防裸奔崩溃——这几个是 dashboard 远程操作，**不属于 streaming 拆分范围**，迁移时不用管。

## 七、2026-06-19 Codex 执行结果：阶段 6/7 已完成

本轮已按阶段 6/7 收尾，`MainActivityViewModel` 不再作为 streaming facade：

- 删除 `MainActivityViewModel` 内部 `streamingViewModel` 占位字段、`streaming` 转发属性、`bindStreamingViewModel` 以及所有 `return streamingViewModel.xxx(...)` streaming/heartbeat/manual-cookie/playlist proxy。
- `MainActivity` 剩余旧入口已改为直连 `StreamingViewModel`，包括 `PlaybackStartBindings` 的 `stopHeartbeatRecommendationMode`、播放队列解析、歌单导入/同步、手动 cookie、provider track id、推荐 seed 等入口。
- `StreamingManualCookieController`、`StreamingSearchActionHandlerBindings`、`StreamingAuthCallbackBindings` 已去掉 `MainActivityViewModel` 构造依赖，改为直接依赖 `StreamingViewModel`。
- 原 `MainActivityViewModelStreamingTest` 已删除；关键覆盖迁到 `StreamingViewModelTest`，并新增 `MainDispatcherRule` 供 streaming controller/viewmodel 测试使用。
- `MainActivityArchitectureContractTest` 已改成新契约：streaming 行为入口属于 `StreamingViewModel`，`MainActivityViewModel.kt` 只暂存仍被复用的顶层数据类/接口，不再允许 facade 转发。

验证结果：

- 残留扫描：生产代码未发现 `viewModel.*Streaming*` / `viewModel.*Heartbeat*` / `viewModel.getStreaming()` / `bindStreamingViewModel` 等旧路径。
- Focused 单测通过：`StreamingViewModelTest`、`StreamingSearchActionHandlerBindingsTest`、`StreamingAuthCallbackBindingsTest`、`StreamingManualCookieBindingsTest`、`DailyRecommendationControllerTest`、`HeartbeatRecommendationControllerTest`、`StreamingPlaybackControllerTest`、`StreamingPlaylistControllerTest`。
- 完整验证通过：`.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug --console=plain`。
- APK 已打包：`E:\ECHO andriod\echo-android\app\build\outputs\apk\debug\app-debug.apk`，大小 `17,881,413` bytes，时间 `2026/6/19 07:20:15`。

后续建议：

- 下一轮可以继续把 `MainActivityViewModel.kt` 中仍留存的 streaming 顶层数据类/接口拆到更合适的 `Streaming*Contracts.kt`/`Streaming*Models.kt`，这是文件归属清理，不再是 facade 阻塞。
- `StreamingViewModel` 仍保留若干 `bindXxx`（playback coordinator/local playlist operations/track match store）作为 Activity 装配边界；后续若要继续 Hilt 化，可按依赖来源逐个拆。
