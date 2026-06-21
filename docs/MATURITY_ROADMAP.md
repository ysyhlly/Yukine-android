# ECHO Android 播放器成熟度路线图

日期：2026-06-20  
状态：当前工作树台账，按成熟播放器能力补齐进度维护  
对标：Poweramp / AIMP / Musicolet / 椒盐音乐等本地播放器

---

## 1. 目标

把 ECHO 从“功能丰富的播放器原型”推进到“用户可长期使用、可上架、可迁移数据、可在车机/桌面小部件/通知栏等系统入口稳定工作的成熟播放器”。

路线图采用四类状态：

| 状态 | 含义 |
|---|---|
| 已落地 | 当前工作树已有实现，仍可能需要真机或场景验收 |
| 部分落地 | 代码已有基础能力，但体验、验收或边界仍不完整 |
| 未开始 | 尚未看到可用实现 |
| 待回归 | 曾经修过或接过线，但必须用指定场景再次验证 |

---

## 2. 当前成熟度总览

| 能力 | 状态 | 当前证据 | 还缺什么 |
|---|---|---|---|
| 播放队列冷启动恢复 | 待回归 | `EchoPlaybackService` 有队列持久化、恢复和前台服务保活逻辑 | 必须按删除当前曲、流媒体 header 失效、杀进程重启逐项验收 |
| 均衡器 / 音效 | 已落地 | `AudioEffectSettings`、`Equalizer`、`BassBoost`、`Virtualizer`、`LoudnessEnhancer`、设置页入口 | 真机确认不同厂商 audio session 变化后的重绑 |
| 桌面小部件 | 已落地 | `EchoPlaybackWidgetProvider`、`widget_playback.xml`、`playback_widget_info.xml`、Manifest receiver | 真机添加 2x1/4x1 小部件并验证后台控制 |
| 新用户引导流 | 已落地 | `OnboardingScreen.kt`、`onboarding_completed` 设置、权限/扫描/导入歌单/流媒体引导 | 清数据首次启动完整走一遍 |
| 正在播放手势 | 已落地 | `NowPlayingGestureActions`，左右切歌、上下调 app volume | 真机滑动阈值手感微调 |
| 通知栏歌词 | 部分落地 | `FloatingLyricsPublisher` 联动播放通知 content text / ticker / extras | 厂商“流体云/灵动岛”展示能力取决于系统通知模板，需真机确认 |
| 后台保活 / 通知控制 | 部分落地 | `START_STICKY`、前台服务、通知动作、widget action 使用 foreground service | Android 12+、国产 ROM 后台限制场景回归 |
| Android Auto | 部分落地 | `EchoPlaybackService` 已扩展为 Media3 `MediaLibraryService` 并注册 service intent | DHU/车机模拟器浏览树和播放控制验收 |
| ReplayGain | 已落地 | `ReplayGainParser`、Track/DB 字段、播放音量 multiplier | 补 FLAC/MP3/OGG/M4A 样本覆盖 |
| Gapless | 待回归 | `EchoPlaybackService` 会复用已有 mirrored playlist，初始化时不再先 `stop()` | 需要无缝测试音源确认自动续播无断点 |
| Crossfade | 部分落地 | 手动下一首前已有轻量 fade-out 过渡 | 还缺可配置时长和双 player 真 crossfade |
| 智能歌单 | 部分落地 | 已有每日推荐/心动推荐等流媒体推荐，`play_events` 可用 | 缺本地虚拟歌单：最近添加、最近播放、一周最爱、很久没听 |
| 标签编辑器 | 未开始 | 当前元数据为只读扫描/解析 | 需要写回权限、格式支持和失败回滚设计 |
| 数据备份 / 恢复 | 未开始 | 只有 M3U 歌单导入/导出路径 | 缺歌单、收藏、设置、流媒体匹配信息全量导出/导入 |
| Last.fm / ListenBrainz | 未开始 | 未看到 scrobble client | 需要授权、播放阈值、异步提交和失败重试 |
| Predictive Back | 未开始 | Manifest 未见 `android:enableOnBackInvokedCallback="true"` | 需要 Android 14+ 手势返回预览验收 |
| Monochrome launcher icon | 已落地 | `ic_echo_monochrome.xml` 和 adaptive icon `monochrome` | 主题图标真机确认 |
| Per-app language | 已落地 | Manifest `android:localeConfig="@xml/locales_config"` | Android 13+ 系统语言设置确认 |
| 平板 / 折叠屏 | 未开始 | 主要仍是手机布局 | 需要 NavigationRail / 双栏 / 横屏适配 |
| Notification channel 分离 | 部分落地 | 播放通知、悬浮歌词各有 channel | 下载/缓存/系统消息 channel 还未成体系 |

---

## 3. P0：留存底座

### 3.1 播放队列恢复稳定化

状态：待回归

目标：
- 退出再进不闪退。
- 杀进程重启后恢复当前队列、当前曲目、播放位置、播放模式。
- 当前曲目被删除、流媒体链接失效、header 过期时不崩溃。

涉及文件：
- `app/src/main/java/app/yukine/playback/EchoPlaybackService.java`
- `app/src/main/java/app/yukine/playback/PlaybackStateSnapshot.java`
- `app/src/main/java/app/yukine/data/EchoDatabaseHelper.java`

验收：
- 冷启动恢复：保留队列、当前 index、播放位置。
- 杀进程恢复：通知或小部件点击播放能继续。
- 删除当前本地文件：UI 显示可理解错误，队列跳过或停在空态，不崩。
- 流媒体链接失效：尝试刷新/重新解析，失败时保留队列并显示状态。

### 3.2 新用户引导流

状态：已落地

当前行为：
- 首次启动显示中文新手引导。
- 引导包含：权限、扫描手机歌曲、导入已有 M3U/M3U8 歌单、可选流媒体连接。
- 未完成音频权限、通知权限、本地扫描前，不能进入首页。
- 导入歌单和流媒体入口在基础设置完成前不可用。

涉及文件：
- `app/src/main/java/app/yukine/ui/OnboardingScreen.kt`
- `app/src/main/java/app/yukine/EchoApp.kt`
- `app/src/main/java/app/yukine/MainActivity.java`
- `app/src/main/java/app/yukine/data/EchoDatabaseHelper.java`

验收：
- 清数据首次启动进入引导页。
- 拒绝权限后底部按钮仍禁用，并明确提示缺什么。
- 授权并扫描完成后按钮变为“进入 ECHO”。
- 重新启动后不重复显示引导。

---

## 4. P1：用户 5 分钟内能感知的能力

### 4.1 均衡器 / 音效

状态：已落地

当前实现：
- Android 系统 audio effect API。
- 支持 Equalizer、BassBoost、Virtualizer、LoudnessEnhancer。
- `AudioEffectSettings` 可持久化到 settings。
- 设置页已有音效入口和预设/强度选项。
- 播放服务在 audio session 上创建和应用 effect。

验收：
- 开关音效不会中断播放。
- 切歌或 audio session 变化后效果仍生效。
- 不支持的 effect 不导致崩溃。

### 4.2 桌面小部件

状态：已落地

当前实现：
- 使用传统 `RemoteViews`，不是 Glance。
- 支持封面、标题、艺人、上一首、播放/暂停、下一首。
- 复用 `EchoPlaybackService` action。

涉及文件：
- `app/src/main/java/app/yukine/playback/EchoPlaybackWidgetProvider.java`
- `app/src/main/res/layout/widget_playback.xml`
- `app/src/main/res/xml/playback_widget_info.xml`
- `app/src/main/AndroidManifest.xml`

验收：
- 未打开 App 时小部件可控制播放。
- 服务未连接时点击播放可拉起服务并恢复队列。
- 深色/浅色桌面下文字和按钮可读。

### 4.3 正在播放手势

状态：已落地

当前实现：
- 正在播放内容卡片区域消费手势。
- 左滑下一首，右滑上一首。
- 上下滑以 5% 步进调整 app volume。
- 点击封面进入沉浸歌词。

涉及文件：
- `app/src/main/java/app/yukine/ui/NowPlayingScreen.kt`
- `app/src/main/java/app/yukine/now/NowPlayingDestination.kt`
- `app/src/main/java/app/yukine/navigation/EchoNavGraph.kt`
- `app/src/main/java/app/yukine/NowPlayingViewModel.kt`

验收：
- 轻微滚动不误触切歌。
- 手势不影响底部 tab pager。
- 上下滑调音量后设置页 app volume 同步。

### 4.4 通知栏歌词 / 状态栏展示

状态：部分落地

当前实现：
- `FloatingLyricsPublisher` 发布当前歌词行。
- 播放通知会优先显示当前歌词，并写入 ticker / extras。

限制：
- Android 标准通知不能保证所有厂商的“流体云/灵动岛”都会展示歌词。
- 厂商系统通常只读取标准 media notification 字段，展示策略不可控。

下一步：
- 保持标准通知字段完整：title、artist、text、subText、largeIcon、MediaStyle。
- 在支持动态岛/流体云的目标机型上确认系统是否读取 `contentText` 或 media metadata。

---

## 5. P2：成熟播放器核心差异

### 5.1 Android Auto

状态：部分落地

当前实现：
- `EchoPlaybackService` 已继承 Media3 `MediaLibraryService`。
- 注册 `MediaLibraryService` 和 `MediaSessionService` intent。
- `MediaLibrarySession.Callback` 暴露曲库根和 children。

涉及文件：
- `app/src/main/java/app/yukine/playback/EchoPlaybackService.java`
- `app/src/main/AndroidManifest.xml`

缺口：
- DHU/车机模拟器验收未记录。
- 车机分类树需要确认：最近播放、歌单、艺人、专辑、全部歌曲。
- 车机上播放失败时的错误文案和空态需要确认。

验收：
- Android Auto 能浏览曲库。
- 车机端可以播放、暂停、上一首、下一首。
- 手机端通知、小部件、耳机按键仍兼容。

### 5.2 Gapless / Crossfade

Gapless 状态：待回归  
Crossfade 状态：部分落地

当前实现：
- 播放服务已出现 Media3 playlist 相关调用，具备把队列镜像到 ExoPlayer 的基础。
- `prepareMirroredQueue()` 会优先复用已有 mirrored playlist，避免切歌时反复重建播放器队列。
- 初始化 mirrored playlist 时不再先 `player.stop()`，减少连续播放断点。

Gapless 下一步：
- 梳理所有切歌路径，禁止对连续播放路径使用 `stop()` + 单曲 `setMediaItem()`。
- 使用无缝测试音源验证两首连续音频之间没有静音。

Crossfade 建议：
- 第一阶段已做：手动下一首前短暂 fade-out，然后跳到下一首并恢复正常音量。
- 下一阶段：把 fade-out 时长做成设置项，提供 0/2/4/6 秒。
- 最终阶段：双 player 真 crossfade，当前曲剩余 N 秒时预热下一首并淡入。

验收：
- Gapless：两首无间断 FLAC/WAV 连续播放无断点。
- Fade-out：最后 N 秒音量线性或缓动降低，下一首按队列进入。
- Crossfade：下一首淡入期间当前曲淡出，完成后释放旧 player。

### 5.3 智能歌单

状态：部分落地

当前已有：
- `play_events` 可作为统计基础。
- 已有每日推荐/心动推荐等流媒体推荐能力。

缺口：
- 本地虚拟歌单尚未形成统一入口。

第一版范围：
- 最近添加。
- 最近播放。
- 一周最爱。
- 很久没听。

实现建议：
- 不写入普通 playlists 表，先作为只读虚拟集合。
- 从曲库页或收藏页进入。
- 每个虚拟集合支持直接播放和加入队列。

验收：
- 新增/播放歌曲后虚拟歌单排序更新。
- 空数据时显示中文小白空态。
- 不污染用户手动创建的歌单。

### 5.4 ReplayGain

状态：已落地

当前实现：
- Track 和数据库已有 `replay_gain_track_db`、`replay_gain_album_db`。
- `ReplayGainParser` 读取 tag。
- 播放时把 app volume 乘以 replay gain multiplier。

验收：
- FLAC、MP3、OGG、M4A 至少各一首样本。
- track gain 优先于 album gain。
- 极端 gain 值不会爆音，音量被限制在安全范围。

---

## 6. P3：平台适配和上架体验

### 6.1 Predictive Back

状态：未开始

实现：
- Manifest `<application>` 增加 `android:enableOnBackInvokedCallback="true"`。
- 检查自定义 back policy 和 Compose/Activity back dispatcher 的启用时机。

验收：
- Android 14+ 从设置子页、网络子页、Now Playing 沉浸歌词返回时有预览动画。
- 返回不会跳错 tab 或重复消费。

### 6.2 Monochrome launcher icon

状态：已落地

证据：
- `app/src/main/res/drawable/ic_echo_monochrome.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_echo_launcher.xml`

验收：
- Android 13+ 开启主题图标后，ECHO 图标能跟随系统主题。

### 6.3 Per-app language

状态：已落地

证据：
- `app/src/main/res/xml/locales_config.xml`
- Manifest `android:localeConfig="@xml/locales_config"`

验收：
- Android 13+ 系统设置中能单独设置 ECHO 语言。
- App 内语言设置和系统 per-app language 不互相打架。

### 6.4 平板 / 折叠屏

状态：未开始

实现建议：
- 引入窗口宽度判断。
- 手机保持 bottom tabs。
- 平板/横屏切到 NavigationRail + 双栏。
- 曲库/队列/正在播放支持左右并排。

验收：
- 7 英寸平板、10 英寸平板、折叠屏展开态不会只是手机 UI 拉伸。
- 列表和 Now Playing 不互相挤压。

### 6.5 Notification channel 分离

状态：部分落地

当前已有：
- 播放通知 channel。
- 悬浮歌词 channel。

缺口：
- 下载/缓存 channel。
- 系统消息 channel。
- 用户可独立关闭非播放通知。

---

## 7. P4：高级本地曲库能力

### 7.1 数据备份 / 恢复

状态：未开始

第一版范围：
- 导出 JSON/ZIP：歌单、收藏、设置、流媒体匹配信息。
- 通过 SAF `ACTION_CREATE_DOCUMENT` 保存。
- 恢复通过 SAF `ACTION_OPEN_DOCUMENT` 选择文件。

暂不做：
- 直接覆盖数据库文件。
- 备份音频文件本体。
- 备份系统 Keystore 中不可导出的密钥。

验收：
- 备份、清数据、恢复后，歌单/收藏/设置一致。
- 版本号不匹配时给出中文错误。
- 恢复失败不破坏当前库。

### 7.2 标签编辑器

状态：未开始

前置问题：
- SAF 写权限。
- MP3/FLAC/OGG/M4A 格式支持。
- 文件写回失败回滚。
- MediaStore 元数据刷新。

建议：
- 先做只读“查看标签详情”。
- 再做可写编辑器。
- 最后做批量编辑。

验收：
- 修改标题/艺人/专辑后，重启 App 仍显示新值。
- 第三方播放器读取同一文件能看到新 tag。
- 写入失败时保留原文件。

### 7.3 Last.fm / ListenBrainz

状态：未开始

建议：
- 先支持 ListenBrainz，token 模式更简单。
- Last.fm 放第二阶段，处理 API key、session、签名。

触发规则：
- 播放超过 50% 或超过 4 分钟，取较小者。
- 短音频小于 30 秒不提交。
- 离线失败进入重试队列。

验收：
- 正在播放更新能显示。
- scrobble 出现在用户主页。
- 重复播放不重复提交同一次。

---

## 8. 下一步执行顺序

1. Predictive Back  
   最小改动，补齐上架体验短板。

2. Gapless 回归  
   核心播放器体验，先证明当前 Media3 playlist 路径真的无缝。

3. Crossfade 第一阶段  
   先做 fade-out 设置，不直接上双 player。

4. 本地智能歌单  
   复用 `play_events`，做只读虚拟集合。

5. Android Auto DHU 验收  
   代码已有基础，下一步是模拟器/真机行为确认和空态修正。

6. 备份 / 恢复  
   用户换机价值高，先做 JSON/ZIP，不碰音频文件本体。

7. 平板 / 折叠屏  
   在核心体验稳定后统一做布局升级。

8. 标签编辑器和 Scrobble  
   都需要更完整的权限/网络失败处理，放到后段。

---

## 9. 每波验收命令

每完成一波至少运行：

```powershell
.\gradlew.bat --no-daemon --no-watch-fs --max-workers=1 :app:testDebugUnitTest :app:assembleDebug --console=plain
```

如果本机 Gradle wrapper 锁或 daemon 卡住，至少运行：

```powershell
.\gradlew.bat --no-daemon --no-watch-fs --max-workers=1 :app:compileDebugKotlin --console=plain
.\gradlew.bat --no-daemon --no-watch-fs --max-workers=1 :app:assembleDebug --console=plain
```

手动验收清单：
- 清数据首次启动引导。
- 冷启动队列恢复。
- 杀进程后通知/小部件恢复播放。
- 本地文件被删除。
- 流媒体链接失效。
- 通知栏歌词。
- 小部件控制。
- 手势切歌和音量。
- Android 13+ 主题图标和单独语言。
- Android 14+ predictive back。
- 横屏和平板。

---

## 10. 文档维护规则

- 每实现一个能力，把状态从“未开始/部分落地”更新到“已落地”或“待回归”。
- 不能只因为代码合并就标“已完成”，必须写明验收证据。
- 涉及外部平台的能力，例如 Android Auto、Last.fm、系统流体云，必须区分“代码接线完成”和“目标环境验收通过”。
- 如果后续实现和本文冲突，以当前工作树和最新验收结果为准。
