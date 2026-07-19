# YUKINE Android

YUKINE 是一款以本地曲库为核心、兼顾流媒体导入与后台播放的 Android 音乐播放器。外部品牌统一写作 `YUKINE`，桌面显示名为 `Yukine`；工程中的部分 `Echo` 命名是迁移期内部标识。

> 当前项目按个人学习与 Beta 测试用途维护。
>
> 项目交流群 QQ 群：1013122077
>
> [English summary](#english-summary)

## 核心能力

- 本地歌曲扫描、曲库浏览、收藏、播放历史和歌单管理。
- Media3 后台播放、媒体通知、桌面小部件及耳机/车机控制入口。
- NowBar、正在播放页、本地/在线歌词、波形、沉浸歌词和悬浮歌词。
- 网易云、QQ、LX/洛雪等在线来源的登录、搜索、导入与播放源解析。
- WebDAV、远程流列表和 M3U/M3U8 网络曲库。
- 多音源聚合与切换、断点下载、ReplayGain、系统音效和安全备份恢复。

## 功能状态

| 能力 | 状态 | 说明 |
|---|---|---|
| 本地曲库、播放队列、后台播放、歌词 | 已实现 | 核心播放链路可用 |
| 搜索、多音源合并与切换 | 已实现 | 支持本地与已启用在线来源 |
| 备份与恢复 | 已实现 | ZIP 导出；恢复前校验，并在冷启动时执行可回滚替换 |
| WebDAV、远程流、M3U/M3U8 | 已实现 | 更多服务端和机型组合仍在回归 |
| 网易云、QQ、LX/洛雪等在线来源 | 部分实现 | 受账号、会员、地区、接口及脚本兼容性影响 |
| Android Auto | 基础能力 | 已接入 `MediaLibraryService`，完整浏览树仍待验收 |
| OPPO 流体云 | 机型相关 | 依赖系统对播放通知和歌词内容的支持 |

近期工作主要集中在模块化架构收敛、大曲库与队列性能、流媒体临时地址恢复、多音源识别、下载续传和备份安全。具体成熟度与后续计划见 [成熟度路线](docs/MATURITY_ROADMAP.md)。

## 技术与架构

| 层级 | 当前实现 |
|---|---|
| 平台 | Android，minSdk 23，targetSdk / compileSdk 35 |
| UI | Jetpack Compose、Material3、单 Activity、Compose NavHost |
| 播放 | AndroidX Media3 ExoPlayer、MediaSession、MediaLibraryService |
| 数据 | Room `YukineDatabase` v30，显式迁移覆盖 v1-v29 |
| 状态 | 聚焦 feature 模块、单向 `StateFlow` |
| 依赖注入 | Hilt |
| 构建 | Gradle、Kotlin、Java 17 |

主要模块：

- `:app`：应用入口、依赖注入汇合、Android 平台能力和播放服务。
- `:core:*`：稳定模型、通用能力和设计系统。
- `:feature:*‑ui`：曲库、播放、设置、流媒体等界面与状态。
- `:feature:playback` / `:feature:data` / `:feature:streaming`：播放契约、Room 数据层和流媒体底层能力。
- `:feature:navigation`：类型化路由与 destination 装配。
- `metadata-gateway/`：可选的独立元数据网关；本地开发需要 Node.js 24。

详细边界与状态所有权见 [架构说明](docs/ARCHITECTURE.md)。

## 快速开始

### 环境要求

- JDK 17。
- Android SDK 35；原生指纹模块使用 CMake 3.22.1。
- Android 6.0（API 23）或更高版本的设备/模拟器。
- 当前 APK ABI 为 `arm64-v8a` 和 `x86_64`。
- 无需单独安装 Gradle，仓库使用 Gradle Wrapper 8.11.1。

### 构建与安装

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

多设备连接时使用 `adb -s <serial> install -r ...`。默认应用 ID 为 `app.yukine`，当前版本与构建配置以 Gradle 文件为准。

### 首次启动

1. 授予本地音频读取权限。
2. 按引导扫描本地曲库；通知权限用于后台播放控制，悬浮歌词需单独授予悬浮窗权限。
3. WebDAV、远程列表和在线音源均为可选能力。登录 Cookie 保存在本机，但仍应只在受信任设备上使用。

## 测试

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
```

完整检查会额外执行 lint 和乱码扫描：

```powershell
.\gradlew.bat :app:check --console=plain
```

## 开发约定

- 新增用户可见文案时，同时提供中文和英文；主要入口为 `app/src/main/java/app/yukine/AppLanguage.java`。
- 避免在 Compose、Activity、Service 或 Dialog 中硬编码单语言文案。
- 音频播放优先级高于波形、频谱、封面解码、下载和 UI 反馈；外围任务不得阻塞 ExoPlayer。
- 外部品牌保持 `YUKINE`；内部遗留 `Echo` 命名按迁移技术债处理。
- 应用图标受保护，修改前阅读 [图标锁定说明](docs/APP_ICON_LOCK.md)。

## 文档索引

- [架构说明](docs/ARCHITECTURE.md)
- [数据库迁移](docs/DATABASE_MIGRATIONS.md)
- [设备回归矩阵](docs/DEVICE_REGRESSION_MATRIX.md)
- [播放服务稳定性矩阵](docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md)
- [成熟度路线](docs/MATURITY_ROADMAP.md)

## 使用与分发说明

- 在线搜索、播放、下载、歌词和封面受版权、会员、地区、平台协议和接口变化影响；项目不提供或承诺绕过版权保护。
- 网易云、QQ、LX/洛雪等名称仅表示可选第三方来源适配，不代表官方合作、授权或背书。
- 账号 Cookie 仅用于本机播放、搜索和歌单同步，不应上传到第三方服务器。
- 下载功能仅应用于用户有权保存和管理的内容，并应遵守来源平台规则与当地法律。
- 公开 APK 应来自固定渠道，并标注版本、发布时间和校验值。正式上架前需单独完成隐私、版权和商店合规评估。

## English Summary

YUKINE is a Chinese-first Android music player focused on local libraries, reliable Media3 background playback, lyrics, playlists, and optional online sources.

- Built with Jetpack Compose, Media3, Room, Hilt, Kotlin, and Java 17.
- Supports local scanning, queues, playlists, lyrics, widgets, WebDAV, remote lists, M3U/M3U8, downloads, and backup/restore.
- NetEase, QQ Music, LX, Android Auto, and device-specific integrations remain partially implemented or environment-dependent.
- Requires JDK 17, Android SDK 35, and an Android 6.0+ device or emulator.
- Online features may be limited by authentication, membership, region, platform policy, and API changes.

Build with:

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```
