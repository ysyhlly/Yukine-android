# Codex 交接：小白实操评测发现的体验缺陷修复计划

日期：2026-06-23
来源：用 adb 在 MuMu 模拟器（Android 12 / SDK 32 / 1080x2345）上派两名"零基础小白"子代理实操评测 YUKINE 播放器，并逐条在代码层核实根因。
本轮范围：**用户体验关键缺陷修复**，按 P0/P1/P2 分档。每项已定位到确切文件与行号。codex 直接按此实施。
验证基线：`./gradlew.bat assembleDebug lintDebug` + 相关单测；改完装回 MuMu 复测同路径。

---

## 评测背景（为什么提这些）

- 第一轮（空设备）：小白**连第一首歌都放不出来**，且全程无操作反馈——上手即劝退。
- 第二轮（推了 3 首测试音频后）：播放内核其实扎实（播放/暂停/切歌/队列/收藏/歌词都真能用，`dumpsys media_session` 佐证 state 真实切换），但手势、进度条、长按等交互有明显坑。
- 结论：**底子不错，"新用户上手"这条命脉是断的**。综合体验 5.5/10（有歌 7 分，空手上手 4 分）。

---

## P0 — 必修四项（决定能否用 + 第一印象）

### P0-1　救活全局状态反馈（性价比最高，一处修复治一大片）

**症状**：小白点"扫描曲库"毫无反馈、搜索 0 结果不解释、权限缺失无提示。

**根因（已坐实）**：状态反馈管线整条是死的。
- `MainActivity.setStatus()`（[MainActivity.java:2114](../app/src/main/java/app/yukine/MainActivity.java#L2114)）→ `StatusMessageController.setStatus()`（[StatusMessageController.java:16](../app/src/main/java/app/yukine/StatusMessageController.java#L16)）→ `host.updateStatus()` → `MainUiShellController.updateStatus()`（[MainUiShellController.java:82](../app/src/main/java/app/yukine/MainUiShellController.java#L82)）。
- **`MainUiShellController.updateStatus(String)` 是空方法**（方法体为空）。这是 Compose 迁移后遗留的"僵尸控制器"：旧 Java 状态管线还在调，实现已被掏空。`setStatus` 在 30+ 处被调用，提示全部石沉大海。
- 对照：`MainActivity.showActionFeedback()`（[MainActivity.java:2121](../app/src/main/java/app/yukine/MainActivity.java#L2121)）**是能弹 Toast 的**（`Toast.makeText`），只用于下载/分享/音源动作；扫描走 `LibraryEvent.ScanLibrary → gateway.scanLibrary()`（[LibraryViewModel.kt:293](../app/src/main/java/app/yukine/LibraryViewModel.kt#L293)）→ `loadLibrary(false)`（[MainActivity.java:318](../app/src/main/java/app/yukine/MainActivity.java#L318) 绑定），绕开了 Toast。

**改法**：
1. 给 `MainUiShellController` 接通可见反馈。它已持有 `activity`（构造于 [MainActivity.java:352](../app/src/main/java/app/yukine/MainActivity.java#L352)），在 `updateStatus(status)` 里对非空 `status` 走 `Toast.makeText(activity, status, Toast.LENGTH_SHORT)`。
2. **防滥弹（关键）**：`loadLibrary` 在启动/每次回库都触发 `"loading.library"`（[MainActivity.java:1454](../app/src/main/java/app/yukine/MainActivity.java#L1452)），无脑弹会刷屏。两种处理任选：
   - 在 `loadLibrary` 的**结果回调**（[MainActivity.java:1461](../app/src/main/java/app/yukine/MainActivity.java#L1461)）里改用 `showActionFeedback` 报"找到 N 首 / 未找到歌曲"，加载中的瞬态 status 不弹。
   - 或给 `setStatus` 增加 `transient` 重载，瞬态态只记录不弹、结果态才弹。
3. 搜索无结果同理：`applySearch` 结果为空时给"没找到相关歌曲"。

**涉及文件**：[MainUiShellController.java](../app/src/main/java/app/yukine/MainUiShellController.java)、[MainActivity.java](../app/src/main/java/app/yukine/MainActivity.java)（`loadLibrary` / `applySearch`）。
**风险**：低。注意别把高频瞬态态做成刷屏。

---

### P0-2　全屏播放页"下滑关闭"手势（并修掉误调音量的坑）

**症状**：小白在全屏播放页想"下滑关闭"，结果触发音量手势把声音调到 0%（`dumpsys` 确认 STREAM_MUSIC Muted: true），且页面根本关不掉，也没有恢复提示。

**根因（已坐实）**：`NowPlayingScreen.kt` 的 `nowPlayingGestureInput`（[NowPlayingScreen.kt:587](../app/src/main/java/app/yukine/ui/NowPlayingScreen.kt#L587)）把**竖滑全部映射成音量**（[L624-635](../app/src/main/java/app/yukine/ui/NowPlayingScreen.kt#L624)），横滑切歌（[L617-623](../app/src/main/java/app/yukine/ui/NowPlayingScreen.kt#L617)），**没有任何关闭手势**。

**改法**：重新划分手势语义——
- **向下大幅滑动 = 关闭/收起播放页**（对齐主流播放器肌肉记忆）。
- 音量手势改到封面右侧专属竖向区域，或直接移除（系统音量键已够用），避免与"下滑关闭"抢手势。
- 横滑切歌保留；用主轴方向 + 起点位置区分手势，避免冲突。

**实施前必须先核实**：全屏播放页的承载方式。小白报告它表现得**像底部"播放"Tab 而非可下滑收起的弹层**。需确认它是 NavHost 里的一个 destination 还是 overlay——"关闭"语义要对应到正确的导航返回/收起动作（查 [navigation/EchoNavGraph.kt](../app/src/main/java/app/yukine/navigation/EchoNavGraph.kt)、`EchoRoutes.kt`）。

**涉及文件**：[NowPlayingScreen.kt](../app/src/main/java/app/yukine/ui/NowPlayingScreen.kt)；可能涉及导航宿主。
**风险**：中。多手势共存的方向判定 + 关闭语义对应导航结构。

### P0-3　进度条改"点哪跳哪 + 整条可拖"

**症状**：进度条只有在播放中、手指正好抓住小圆点往两边拖才有效；点击进度条空白处、或从别的位置拖，完全没反应。普通人很难拖准。

**根因（已坐实）**：`NowBar.kt` 的进度条手势（[NowBar.kt:782](../app/src/main/java/app/yukine/ui/NowBar.kt#L782)）用 `awaitFirstDown + drag(down.id)`，**只认抓住 thumb 拖动**，无 tap-to-seek，拖动也要命中起始 down。

**改法**：在该 `pointerInput` 中：
- 增加 `detectTapGestures { offset -> onSeek.seekTo(scrub.scrubTo(offset.x, size.width.toFloat())) }`，实现点击即跳。
- 让拖动从任意落点开始（不强制命中 thumb）。
- `SeekAction.seekTo`（[NowBar.kt:189](../app/src/main/java/app/yukine/ui/NowBar.kt#L189)）已有，直接复用。
- 全屏播放页若有独立进度条，同样处理。

**涉及文件**：[NowBar.kt](../app/src/main/java/app/yukine/ui/NowBar.kt)。
**风险**：低，纯 Compose，单文件。

---

### P0-4　在线音乐入口"去技术化"（这是信息架构问题，不是缺功能）

**症状**：小白在"音源与网络"里找不到任何"登录网易云/QQ音乐"的傻瓜入口，只看到 `10.0.2.2:43990`、`gateway://unconfigured` 这类网关黑话，100% 看不懂，直接放弃。首页"去连接"卡片点了"像没反应"。

**根因（已坐实）**：**登录能力其实完整存在，只是被技术配置淹没、可发现性极差**。
- 网页登录：`StreamingWebAuthActivity`（[StreamingWebAuthActivity.kt:24](../app/src/main/java/app/yukine/StreamingWebAuthActivity.kt#L24)），经 `StreamingAuthLauncher`（[StreamingAuthLauncher.kt:11](../app/src/main/java/app/yukine/StreamingAuthLauncher.kt#L11)）启动，已在 [MainActivity.java:243](../app/src/main/java/app/yukine/MainActivity.java#L243) 接线。
- 本地直连登录：网易云/QQ（`LocalNeteaseStreamingClient`、`LocalQqMusicStreamingClient`）。
- 网关黑话来自硬编码 UI 文案 [AppLanguage.java:238](../app/src/main/java/app/yukine/AppLanguage.java#L238)（`streaming.gateway.emulator` 等）。
- 首页"去连接"卡片回调链是通的：`StreamingGuideCard.onClick`（[HomeDashboardScreen.kt:149](../app/src/main/java/app/yukine/ui/HomeDashboardScreen.kt#L149)）→ `onConnectStreaming`（[HomeDashboardRenderController.kt:100](../app/src/main/java/app/yukine/HomeDashboardRenderController.kt#L100)）→ `listener.openStreaming()` → 跳 `NETWORK_STREAMING_HUB`（[MainActivity.java:447](../app/src/main/java/app/yukine/MainActivity.java#L447)）。**不是死按钮**，是跳到了那个黑话页 + 无 Toast 反馈，主观上像"没反应"。

**改法**（UI 重排 + 文案，不动核心逻辑）：
- 流媒体 hub 页顶部放醒目的"登录网易云 / 登录 QQ 音乐"按钮，直接触发已有的 `StreamingAuthLauncher`。
- 把"网关服务器 / IP / 端口"整体收进"高级设置"折叠区，默认不展示。
- 首页"连接流媒体账号"卡片点击后直达**登录选择**，而非网关配置页。

**实施前先核实**：`NETWORK_STREAMING_HUB` 页对应的 Compose 文件结构（查 `MainRoutes` 常量 + 对应 screen）。
**涉及文件**：流媒体 hub screen、[AppLanguage.java](../app/src/main/java/app/yukine/AppLanguage.java)（文案）、[HomeDashboardScreen.kt](../app/src/main/java/app/yukine/ui/HomeDashboardScreen.kt)。
**风险**：中。主要是 UI 重排，需先摸清 hub 页结构。

---

## P1 — 体验补全

### P1-1　歌曲列表长按菜单接线

**症状**：长按歌曲行无任何反应；功能藏在不显眼的"…"三点按钮里。

**根因（已坐实）**：**长按基础设施已存在**——`TrackRow` 用 `combinedClickable` + `onLongClick`（[TrackListScreen.kt:432](../app/src/main/java/app/yukine/ui/TrackListScreen.kt#L432)）。但 `TrackRowActions` 多数构造器把 `onLongPress` 传 `null`（[TrackListScreen.kt:61](../app/src/main/java/app/yukine/ui/TrackListScreen.kt#L61) / L67 / L74），调用方没接线，所以长按等于无。

**改法**：给列表调用方接上 `onLongPress`，弹出底部操作面板（收藏 / 加入歌单 / 下载 / 下一首播放 等）。与项目自有 `操作逻辑优化方案.md` 的 P0"统一 TrackActionSheet"方向一致——建议新建可复用 `ui/TrackActionSheet.kt`，列表/队列/全屏播放器共用。

**风险**：中。"收藏 / 加歌单 / 下载"复用现有回调即可**零 Java 改动先上**；"下一首 / 加入队列"需新增 `LibraryEvent` 打通到 `EchoPlaybackService`（核对是否已有 `addToQueue` / `playNext` / `moveMediaItem`），按子项排期。

---

### P1-2　文案本地化补全（中英混用）

**症状**：三点菜单 "Favorite" / "Add to playlist" 是英文，"下载"是中文，混排。

**根因（已坐实）**：`TrackListLabels` 英文硬编码默认值——`favoriteLabel = "Favorite"`、`removeFavoriteLabel = "Remove favorite"`、`addToPlaylistLabel = "Add to playlist"`（[TrackListScreen.kt:104](../app/src/main/java/app/yukine/ui/TrackListScreen.kt#L104)）。

**改法**：调用方通过 `AppLanguage.text(...)` 传入本地化 label，或默认值改中文。一并排查 NowBar / 全屏页同类英文默认值（如 `"Repeat off"`，[NowBar.kt:185](../app/src/main/java/app/yukine/ui/NowBar.kt#L185)）。
**风险**：低。

---

### P1-3　歌词加载失败兜底

**症状**：部分歌曲（无在线歌词的）一直卡在"正在从多个来源加载歌词"，永久转圈。

**根因（待定位）**：在线歌词多来源加载无超时/失败态。本轮未深挖歌词加载的 ViewModel/Repository，**实施前补一次定位**（搜 `加载歌词` / lyrics loading 相关 state）。

**改法**：加载加超时；失败/无结果显示明确"暂无歌词 / 重试"，不无限 loading。
**风险**：低-中。

---

## P2 — 打磨（来自项目自有 `操作逻辑优化方案.md`，验证后纳入）

- **队列拖拽排序 + 侧滑删除**：`ui/QueueScreen.kt`，需 `EchoPlaybackService.moveMediaItem`。
- **导航返回栈语义化**：`MainBackNavigationPolicy`，牵动 Java 外壳与 instrumentation 测试，单独立项。
- **导入音频白屏**：SAF picker 启动前约 1s 白屏，小白易误判闪退。
- **首页右上角易误触搜索**：热区调整（小白第一下就点错跳到搜索页）。

---

## 落地顺序与验证

1. **第一批 = P0-1 + P0-3**（纯反馈 / 纯 Compose，风险最低，立竿见影）。
2. **第二批 = P0-2 + P0-4**（手势重构 + 流媒体 IA，需先核实承载结构）。
3. **第三批 = P1**。P2 单独排期。

每批：
- `./gradlew.bat assembleDebug lintDebug` + 相关单测通过。
- 装回 MuMu（`adb -s 127.0.0.1:7555 install -r app/build/outputs/apk/debug/app-debug.apk`），**复测同一条小白路径**确认体验改善（闭环验证）。
- 注意现有 instrumentation / 架构契约测试（如 `MainActivityArchitectureContractTest`）不要回归。

## 复测要点（给 codex 自检用）

- P0-1：设置→曲库→扫描曲库，应出现"找到 N 首 / 未找到歌曲"可见提示；搜索无结果应有文案。
- P0-2：全屏页向下大幅滑动应关闭页面，且**不再静音**。
- P0-3：进度条点击任意位置应跳转；整条可拖。
- P0-4：首页"去连接"→应直达登录入口；网关配置默认收进高级。
- P1-1：长按歌曲行应弹出操作面板。
- P1-2：三点菜单全中文。


