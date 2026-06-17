# ECHO NEXT Android

Release manual checklist: [docs/RELEASE_EXPERIENCE_CHECKLIST.md](docs/RELEASE_EXPERIENCE_CHECKLIST.md)

Playback service stability matrix: [docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md](docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md)

## 2026-06-17 更新摘要

本轮 Android 版本重点补强了曲库、歌词、通知栏、NowBar、队列和网易云心动推荐体验：

- 曲库页的歌曲、专辑、艺人、文件夹和歌单分组均支持有效选中；专辑和艺人会显示对应封面。
- 曲库长按操作扩展为可删除文件夹分组、歌单、网络歌曲和批量歌曲，并带确认流程。
- 应用启动不再无条件扫描歌曲；需要权限或用户手动扫描后再刷新曲库。
- NowBar 高度和底部按钮布局已调整，显示音频条/波形时不会挤掉歌词条、播放控制或底部导航。
- 队列页高度提升到接近全屏 80%，并压缩顶部队列占位，让列表内容更靠前。
- 系统通知栏和媒体会话优先使用当前歌曲封面作为 artwork，并提供更稳定的通知控制图标。
- 歌词源新增网易云歌词，网易云曲目会优先使用 `songId` 请求歌词；仍保留本地 `.lrc` 与 LRCLIB 兜底。
- 歌名、作者名、专辑名支持点击复制；歌词行也支持点击复制对应行文本。
- 心动推荐每批请求提升到 60 首，队列剩余较多时提前自动补货，避免播完停止。
- 心动推荐 seed 不再只依赖当前歌曲，会优先使用选中歌单歌曲和当前队列歌曲，当前播放歌曲只作为兜底。
- 网易云心动推荐会多轮滚动拉取，返回过少时用相似歌曲补齐，减少“只有几首歌”的情况。

最新本地验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

以上两项已通过。最新 debug APK 输出为 `app/build/outputs/apk/debug/app-debug.apk`，生成时间为 `2026-06-17 17:24:12`，大小约 `18.3 MB`。

ECHO NEXT 的原生 Android 移植版。

这个实现是一个原生 Android 音乐播放器，围绕 Kotlin/Compose UI 界面、Java Android 生命周期编排、AndroidX Media3 播放，以及 SQLite 持久化构建。它实现了当前 MVP 阶段 ECHO NEXT 需要的 Android 核心能力：

- 通过 `MediaStore` 发现本地音频。
- 通过系统文件选择器导入选定的音频文件和文件夹，并持久化 URI 访问权限。
- 导入本地 M3U/M3U8 歌单，用于流 URL、歌单导入/合并，以及歌单 M3U8 导出。
- 配置 WebDAV 远程来源、同步，并通过已缓存的远程曲目播放。
- 使用本地 SQLite 曲库缓存曲目、文档导入、流媒体、WebDAV 曲目、收藏、播放历史、歌单、队列状态、播放位置和设置。
- 通过 AndroidX Media3 `ExoPlayer` 播放。
- 通过 AndroidX Media3 `MediaSessionService` 后台播放。
- 通过 Android media session 支持通知、锁屏、耳机和蓝牙媒体控制。
- 在有线或蓝牙输出断开时自动暂停。
- 基于 Compose 的曲库、搜索、队列、正在播放、网络来源、设置和收藏集界面。
- 按歌曲、专辑、艺术家和文件夹对曲库分组。
- 基于 SQLite 的收藏、最近播放、最多播放记录、歌单和歌单成员关系。
- 支持创建歌单、重命名歌单、删除歌单、曲目排序、移除曲目、播放选中歌单、添加到歌单、导入/合并选中或当前歌单的 M3U/M3U8，并通过系统文件创建器导出当前歌单为 M3U8。
- 持久化队列、随机播放、关闭重复/全部重复/单曲重复、播放速度、应用音量和睡眠定时器。
- 流 URL 规范化/去重，并在收藏、历史、歌单、队列和播放位置中迁移流媒体编辑引用。
- 加载本地同名 `.lrc` 歌词，并在正在播放界面显示当前歌词行。
- 可选的 LRCLIB 在线歌词兜底，以及可配置的歌词时间偏移。
- 主题和强调色设置，包括跟随系统、深色、浅色、AMOLED、高对比度和命名调色板模式。

Windows SMTC、托盘行为、全局桌面快捷键、Windows 音频宿主、内置下载器/转换器二进制、Electron 插件、Discord 状态和 AirPlay 辅助二进制等仅限桌面端的集成能力，被有意排除在 Android MVP 范围之外。关于功能对齐边界和发布检查清单，请参阅仓库根目录下的 Android 规划文档。

## 当前环境说明

Gradle wrapper 已可用，当前命令行验证可以正常运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
.\gradlew.bat lintRelease
```

这些命令已在本地 Android 化验证期间通过。2026-06-05，在完成播放队列索引加固、恢复队列播放行为修复、删除曲目后的持久化队列清理，以及 media session 生命周期加固后，`assembleDebugAndroidTest`、`assembleDebug`、`lintDebug`、`assembleRelease`、`bundleRelease` 和 `lintRelease` 均通过。`connectedDebugAndroidTest` 在 Android 13/API 33 的 `EchoApi33` 模拟器上通过了 32 个 instrumentation 测试；脚本化的单设备 `-Connected -DeviceSerial emulator-5554` 路径也在指定模拟器上通过了同样的 32 个测试，当时还有其他设备处于连接状态。用户指定的 MuMu-MUSIC Android 12/API 32 实例通过了 32 个手动单设备 instrumentation 测试，并完成了签名 release 启动冒烟测试，作为兼容性信心依据。完成 icon-first Compose UI 调整后，最新 debug 构建已安装并在 MuMu-MUSIC 上启动，截图证据位于 `app/build/tmp/echo-ui-icon-first-mumu-music-20260605-pulled.png`；脚本化单设备 connected 路径也在同一指定设备上重新成功跑完了 32 个 instrumentation 测试。临时冒烟密钥生成了签名 release APK/AAB 产物，签名 release APK 已安装并在 API 33 模拟器上启动，`app.echo.next/.MainActivity` 已恢复；API 33 release 冒烟测试也已通过 `-ReleaseSmoke -DeviceSerial emulator-5554` 重新成功执行。2026-06-04，在完成 M3U/M3U8 解析/导出和流媒体编辑引用迁移加固后，同样的 debug 和 lint 检查也已通过。Debug APK 生成在 `app/build/outputs/apk/debug/` 下；release APK/AAB 输出生成在 `app/build/outputs/apk/release/` 和 `app/build/outputs/bundle/release/` 下。

可重复执行的本地验证入口是：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
```

添加 `-Connected` 可执行已连接设备的 instrumentation 测试；添加 `-ReleaseSmoke -CreateSmokeKeystore` 可在已连接的 Android 13+/API 33 或更新版本模拟器/设备上，用临时密钥执行签名 release 冒烟测试。如果连接了多个目标，请传入 `-DeviceSerial <serial>`；对于 connected instrumentation，脚本只会在该指定目标上安装 debug APK 并运行 `adb shell am instrument`。`-AllowPreAndroid13Smoke` 仅用于非 release 的历史信心检查。

本地模拟器/冒烟测试临时文件，例如 `screen-*.png`、`window-*.xml`、`echo_next_*.db` 和 `tmp-m3u/`，已被 `.gitignore` 忽略。

公开发布仍需要维护者持有的生产 keystore，以及在 Android 13+ 实体设备上完成媒体控制验收。
