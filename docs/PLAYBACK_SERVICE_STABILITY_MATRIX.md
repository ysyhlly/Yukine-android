# Echo Android 播放服务稳定性验收矩阵

本文档用于 P1-06 播放服务稳定性验收。它不是替代自动化测试，而是给每个候选发布提供可复跑、可记录证据的人工/半自动矩阵。执行时请把结果、设备、构建号、日志和截图/录屏路径同步回发布清单。

## 2026-06-29 P0 Playback Baseline

Current playback refactor baseline evidence is captured in
`docs/PLAYBACK_P0_BASELINE_2026-06-29.md`. Automated compile and focused
playback-adjacent tests passed with default Gradle daemon/workers. Device
smoke for local playback, background playback, notification controls, queue
restore, lyrics, and streaming playback was not executed for that checkpoint;
record those rows before claiming runtime smoke coverage for a playback slice.


## 2026-07-07 自动化护栏与 MuMu 设备 Smoke 记录

> 说明：本节记录当前工作站可执行的单元/Robolectric/构建护栏和 MuMu 设备 smoke。自动/半自动 smoke 不能替代本矩阵要求的人工播放、锁屏、通知、蓝牙/耳机和来电中断录屏，因此下列手工场景仍不能填写为通过。

2026-07-07 07:47 复跑 `scripts\p0-stability-gate.ps1 -SkipDeviceProbe -IncludeAssemble` 通过，报告写入 `app\build\p0-stability-gate\20260707-074708.md`；随后单独执行 `adb devices`，设备列表为空，因此真机矩阵仍保持未验收状态。

2026-07-07 09:13 复跑 `scripts\p0-stability-gate.ps1 -SkipDeviceProbe -IncludeAssemble` 通过，报告写入 `app\build\p0-stability-gate\20260707-091345.md`；本轮单独执行 `adb devices` 仍未列出设备，因此下表仍只代表自动化底座，不代表真机播放服务稳定性验收完成。

2026-07-07 09:40 连接 MuMu 模拟器 `127.0.0.1:7555`（model `ALN_AL00`）后复跑 `scripts\playback-stability-smoke.ps1 -SkipBuild -SkipManualCheckpoint -DeviceSerial 127.0.0.1:7555 -LaunchWaitSeconds 8 -RelaunchWaitSeconds 8` 通过，证据目录 `app\build\playback-stability\20260707-094019-127.0.0.1_7555`。本次 smoke 证明 debug APK 安装、冷启动、MainActivity 进程存活、截图采集、force-stop 后重启、fatal-crash logcat 采样通过；`matrix-results.md` 仍将本地播放、暂停/切歌/seek、后台、锁屏、通知、耳机、蓝牙、来电等人工场景标为 `Not run`。

2026-07-07 10:39 在同一 MuMu 模拟器 `127.0.0.1:7555` 上复跑当前 debug APK 的非交互 smoke：`.\scripts\playback-stability-smoke.ps1 -SkipBuild -SkipManualCheckpoint -DeviceSerial '127.0.0.1:7555' -LaunchWaitSeconds 8 -RelaunchWaitSeconds 8` 通过，证据目录 `app\build\playback-stability\20260707-103934-127.0.0.1_7555`。本次刷新验证了安装、冷启动、进程存活、截图采集、force-stop 后冷启动恢复采样和 fatal-crash logcat 扫描；人工播放、通知/锁屏控制、耳机/蓝牙/来电中断矩阵仍未填写为通过。

2026-07-07 10:44 复现播放页进度回归：播放中页面显示“暂停”且总时长正常，但“已播放”停在 `0:00`，`playback_position_ms` 仍为 `0`。修复后安装当前 debug APK 到 MuMu，播放约 7 秒后 UI dump 显示“已播放 `0:08`”和底部进度 `0:09`，数据库 raw dump 显示 `playback_position_ms` 更新为 `6215`，logcat fatal 扫描无命中。回归截图/XML/日志写入 `app\build\yukine-progress-fixed.xml` 与 `app\build\yukine-progress-fixed-logcat.txt`。

2026-07-07 11:15 复现“暂停后进度归零”回归：原补偿逻辑只覆盖播放中 raw position 停滞，暂停态如果播放器 raw position 回到 `0` 会覆盖最后估算进度。修复后安装当前 debug APK 到 MuMu，播放后暂停，UI dump 显示暂停态按钮为 `content-desc=播放` 且进度保持非零：暂停采样 `0:04/4:25`，稍后采样 `0:07/4:25`；数据库 raw dump 显示 `playback_position_ms4765` / `playback_position_ms7488`，fatal 过滤为空。证据文件：`app\build\yukine-pause-playing.xml`、`app\build\yukine-pause-paused.xml`、`app\build\yukine-pause-paused-later.xml`、`app\build\yukine-pause-retain-logcat.txt`。

| 矩阵场景 | 已有自动化/构建护栏 | 仍需真机证据 |
| --- | --- | --- |
| 本地歌曲首次播放 | `PlaybackQueueManagerTest.playFirstQueuedTrackPersistsResumeRequestForColdStartRestore` 覆盖无当前曲目时从队列第一首开始播放会持久化 index/resume；`PlaybackQueueManagerTest.playQueuePersistsAndStartsPlayback` 覆盖播放队列启动。 | 播放页、NowBar、通知三处状态一致截图/录屏。 |
| 暂停/恢复 | `PlaybackNotificationManagerTest` 覆盖通知 action 到 pause/play 映射；`PlaybackSessionPlayerTest` 覆盖 MediaSession play/pause 委托；`PlaybackPlayerStateOwnerTest.keepsEstimatedPositionWhenPausedPlayerReportsZero` 和 `resumesProgressFromPausedEstimateWhenPlayerStillReportsZero` 覆盖播放器暂停/恢复时 raw position 回 0 不覆盖最后有效进度。 | 播放页、通知、锁屏三处交替控制录屏。 |
| 进度拖动 | `PlaybackSessionPlayerTest.seekCommandsClampNegativePositionsBeforeDelegating`、`PlaybackQueueManagerTest.playQueueClampsExplicitStartPositionForImmediateRestore` 覆盖 seek/start position 边界。 | 实际拖动中段、末尾、开头后音频/进度/歌词同步录屏与 logcat。 |
| 切歌 | `PlaybackQueueManagerTest.skipToNextMovesCursorPersistsAndPreparesPlayback`、`skipToPreviousMovesCursorAndPreparesPlayback`、`skipToNextAtQueueEndWithRepeatOffDoesNotRestartCurrentTrack` 覆盖索引、持久化和 repeat-off 队尾边界。 | 连续上一首/下一首录屏，确认标题、通知和队列索引同步。 |
| 睡眠定时 | `PlaybackSleepTimerManagerTest.cancelPreventsAlreadyDequeuedExpiryTickFromPausingPlayback` 覆盖取消后旧回调不会再次暂停。 | 真机短倒计时触发/取消录屏。 |
| 后台播放 | 流媒体临时 URL 播放失败会由 `PlaybackStateEventControllerTest.resolvedStreamingPlaybackErrorRefreshesUrlAndSuppressesStaleError` 覆盖自动刷新入口；`:app:assembleDebug` 与其余 playback 单测只证明构建和核心 owner 行为。 | Home 后持续播放超过音源 URL 有效期、返回 UI 同步录屏/通知截图。 |
| 锁屏控制 | `PlaybackSessionPlayerTest` 覆盖 MediaSession 可用命令与上一首/下一首/seek 委托。 | 锁屏暂停、播放、下一首录屏。 |
| 通知控制 | `PlaybackNotificationManagerTest` 覆盖通知按钮 action 映射和 stop action；`PlaybackQueueManagerTest.skipToNextAtQueueEndWithRepeatOffDoesNotRestartCurrentTrack` 覆盖通知委托到 queue owner 的队尾边界。 | 通知暂停/播放/上一首/下一首录屏。 |
| 通知栏歌词 | `PlaybackLyricsManagerTest.movingPlaybackToBackgroundImmediatelyStartsLiveLyricsAndRefreshesNotification` 覆盖前后台切换即时刷新；`rapidBackgroundLyricLinesDoNotDropTheLatestNotificationRefresh` 覆盖短间隔歌词行不会丢失最后一次通知/MediaSession 刷新；`serviceProgressAdvancesPublishedLyricsTimelineAfterActivityStopsPublishing` 和 `serviceProgressDoesNotReuseLyricsTimelineForAnotherTrack` 覆盖 Activity 不再发布时的服务侧续推与按曲目 ID 隔离。 | 有时间戳歌词时前台播放、按 Home、锁屏、返回前台；逐行确认通知、锁屏/OEM 媒体面板不滞留旧歌词。 |
| 耳机断开 | `PlaybackNoisyReceiverManagerTest.audioBecomingNoisyBroadcastPausesOnlyActivePlayback` 覆盖 `ACTION_AUDIO_BECOMING_NOISY` 只暂停活跃播放。 | 有线/蓝牙断开真实录屏和 `AudioManager` logcat。 |
| 蓝牙切换 | noisy receiver 与 MediaSession command 单测覆盖部分服务行为。 | 扬声器 -> 蓝牙 -> 断开 -> 重连全流程录屏/logcat。 |
| 音频独占 / 来电 | `PlaybackRuntimeStateManagerTest.concurrentPlaybackSetterAppliesAudioFocusHandling` 覆盖“音频独占”开关反向映射后的 Media3 audio-focus handling 配置；`SettingsViewModelTest.audioExclusiveMapsToTheInverseConcurrentPlaybackRuntimeSetting` 覆盖 UI 与存储兼容映射。 | 真实或模拟来电/其他媒体抢焦点录屏和 logcat。 |
| 冷启动恢复 | `PlaybackQueueManagerTest.restorePlaybackQueue...` 系列覆盖过滤坏行、index 重映射、全坏队列清理、resume 标记清理；`StreamingRepositoryTest.persistentHeadersReplaceExpiredPersistedUrlWithFreshCachedUrl` 覆盖恢复队列用有效缓存 URL 替换旧地址；`EchoDatabaseHelperTest.savePlaybackPositionRollsBackTrackIdWhenPositionWriteFails` 覆盖恢复曲目 id 与位置毫秒的事务原子性。 | 杀进程/冷启动后当前曲、队列、位置恢复录屏/logcat；另需覆盖缓存已过期时自动联网重解析。 |
| 后台被杀 | 队列/位置/SQLite 事务护栏已覆盖一部分持久化基础；无 force-stop 运行证据。 | `adb shell am force-stop app.yukine` 后重新打开的命令输出、录屏、logcat。 |
| 无效本地 URI | `PlaybackErrorRecoveryManagerTest.invalidLocalTrackSkipsToNextWhenQueueCanContinue`、`PlaybackQueueManagerTest.restorePlaybackQueueClearsPersistedStateWhenAllRowsAreFilteredOut` 覆盖不可恢复本地项处理。 | 真机缺失 MediaStore/文件条目的失败状态录屏/logcat。 |
| 空队列控制 | `PlaybackQueueManagerTest.retainTracksWithEmptyKeepSetClearsQueueAndStopsPlayback` 覆盖空保留集合清空/停止边界。 | 空队列 UI 截图和控制不崩溃录屏。 |
| 删除当前曲目 | `PlaybackQueueManagerTest.removeCurrentTrackKeepsQueueAtNextTrackAndPreparesPausedPlayback`、`PlaybackQueueMutationOwnerTest.retainEmptyTrackSetClearsExistingQueueThroughManager` 覆盖删除/同步后的队列修正；`EchoDatabaseHelperTest.deleteTrackRemovesReferencesEventsAndReconcilesPlaybackState` 覆盖 SQLite 引用清理、play_events 清理、队列压缩、current index 重映射和播放位置重置。 | 播放中删除当前曲目或移出曲库录屏。 |
| 播放历史/数据一致性 | `EchoDatabaseHelperTest` 覆盖升级、队列保存回滚、曲库全量刷新失败回滚、远端替换回滚、WebDAV 源编辑成功清理旧远程曲目且失败回滚旧远程曲目删除、WebDAV 源删除失败回滚缓存曲目删除、WebDAV 缓存-only 清理失败回滚引用清理、并发 upsert、`markPlayedRollsBackHistoryWhenPlayEventInsertFails`、删除曲目后的 play_history/play_events 引用清理、流媒体曲目批量删除失败回滚引用清理、清空播放历史时 play_history/play_events 的事务原子性、删除不存在 playlist 时不半提交 dangling playlist_tracks、添加/移出/清空/移动歌单成员与 playlist touch 的事务原子性，缺失 playlist 行时不会新增/删除/清空/重排 dangling playlist_tracks，`updateAudioSpecsRollsBackTrackUpdateWhenQueueMirrorFails` 覆盖 tracks 与 playback_queue 音频规格镜像的事务原子性。 | 最近播放/播放历史 UI 真机冒烟。 |

最近通过的通用门禁：

```powershell
# 2026-07-07 本轮复核
./gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --rerun-tasks --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :feature:playback:testDebugUnitTest --rerun-tasks --console=plain
# BUILD SUCCESSFUL

adb devices
# List of devices attached
# <empty>

# 2026-07-07 追加数据库曲库刷新事务护栏
./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.replaceTracksRollsBackExistingLibraryWhenReplacementBatchFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL

# 2026-07-07 追加真机证据采集脚本入口
$tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile('scripts\playback-stability-smoke.ps1', [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { exit 1 } else { 'PowerShell syntax OK' }
# PowerShell syntax OK

adb devices
# List of devices attached
# <empty>

# 2026-07-07 增强真机证据脚本：非交互模式、pidof 存活检查、Fatal logcat 扫描
$tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile('scripts\playback-stability-smoke.ps1', [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { exit 1 } else { 'PowerShell syntax OK' }
# PowerShell syntax OK

./gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL

adb devices
# List of devices attached
# <empty>

# 2026-07-07 证据目录自动生成 matrix-results.md 模板
$tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile('scripts\playback-stability-smoke.ps1', [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { exit 1 } else { 'PowerShell syntax OK' }
# PowerShell syntax OK

adb devices
# List of devices attached
# <empty>

# 2026-07-07 P0 自动化门禁脚本
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\p0-stability-gate.ps1 -SkipDeviceProbe
# BUILD SUCCESSFUL
# Covers playback-stability-smoke.ps1 syntax, StreamingViewModelTest, EchoDatabaseHelperTest, and :feature:playback:testDebugUnitTest.

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\p0-stability-gate.ps1 -SkipDeviceProbe -IncludeAssemble
# BUILD SUCCESSFUL
# Covers playback-stability-smoke.ps1 syntax, StreamingViewModelTest, EchoDatabaseHelperTest, :feature:playback:testDebugUnitTest, and :app:assembleDebug.
# Debug APK: app\build\outputs\apk\debug\app-debug.apk

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\p0-stability-gate.ps1 -SkipDeviceProbe -IncludeAssemble -ReportPath app\build\p0-stability-gate\latest.md
# BUILD SUCCESSFUL
# Report: app\build\p0-stability-gate\latest.md
# Report records adb devices as skipped because no physical/emulator device is attached.

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\p0-stability-gate.ps1 -IncludeAssemble -ReportPath app\build\p0-stability-gate\requires-device.md
# EXPECTED FAILURE on current workstation
# Report: app\build\p0-stability-gate\requires-device.md
# Report records adb devices as Fail because no physical/emulator device is attached.

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteTrackRemovesReferencesEventsAndReconcilesPlaybackState --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteMissingPlaylistDoesNotMutateDanglingPlaylistRows --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.removeTrackFromMissingPlaylistLeavesMembershipUntouched --tests app.yukine.data.EchoDatabaseHelperTest.clearMissingPlaylistLeavesMembershipUntouched --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.clearPlayHistoryRollsBackHistoryWhenEventDeleteFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteRemoteSourceTracksRollsBackWhenReferenceCleanupFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteStreamTracksRollsBackTrackDeleteWhenReferenceCleanupFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.deleteRemoteSourceRollsBackTrackDeleteWhenSourceDeleteFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.saveRemoteSourceRollsBackTrackDeleteWhenSourceUpdateFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.saveRemoteSourceUpdateDeletesOldCachedTracksAndKeepsSource --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.savePlaybackPositionRollsBackTrackIdWhenPositionWriteFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.addTrackToPlaylistMissingPlaylistReturnsFalseWithoutDanglingMembership --tests app.yukine.data.EchoDatabaseHelperTest.removeTrackFromMissingPlaylistLeavesMembershipUntouched --tests app.yukine.data.EchoDatabaseHelperTest.clearMissingPlaylistLeavesMembershipUntouched --tests app.yukine.data.EchoDatabaseHelperTest.movePlaylistTrackAtMissingPlaylistReturnsFalseWithoutReorder --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.movePlaylistTrackAtSwapsByVisibleIndex --tests app.yukine.data.EchoDatabaseHelperTest.movePlaylistTrackAtMissingPlaylistReturnsFalseWithoutReorder --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.addTrackToPlaylistMissingPlaylistReturnsFalseWithoutDanglingMembership --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest.updateAudioSpecsRollsBackTrackUpdateWhenQueueMirrorFails --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
# BUILD SUCCESSFUL

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
# BUILD SUCCESSFUL
# Covers assembleDebugAndroidTest, assembleDebug, lintDebug, assembleRelease, bundleRelease, lintRelease.

./gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --console=plain
# BUILD SUCCESSFUL

./gradlew.bat :app:testDebugUnitTest --tests app.yukine.MainActivityArchitectureContractTest --tests app.yukine.playback.EchoPlaybackServiceTest :app:compileDebugKotlin :app:compileDebugJavaWithJavac --console=plain
# BUILD SUCCESSFUL
# 验证删除纯转发 PlaybackQueueRestoreOwner 后，队列恢复策略仍由 PlaybackQueueManager 持有，
# Service 仅映射 RestorePlaybackResult 到已有生命周期动作。

adb devices
# List of devices attached
# <empty>

# 2026-07-07 10:38 P0 数据库与流媒体单测复核
./gradlew.bat :app:testDebugUnitTest --tests app.yukine.data.EchoDatabaseHelperTest --tests StreamingViewModelTest --console=plain
# BUILD SUCCESSFUL

# 2026-07-07 10:39 当前 MuMu debug APK 非交互播放服务 smoke
.\scripts\playback-stability-smoke.ps1 -SkipBuild -SkipManualCheckpoint -DeviceSerial '127.0.0.1:7555' -LaunchWaitSeconds 8 -RelaunchWaitSeconds 8
# PASS
# Evidence: app\build\playback-stability\20260707-103934-127.0.0.1_7555

# 2026-07-07 10:45 播放进度显示/保存回归复核
./gradlew.bat :feature:playback:testDebugUnitTest :app:testDebugUnitTest --tests StreamingViewModelTest --tests app.yukine.playback.PlaybackPlayerStateOwnerTest --tests app.yukine.playback.PlaybackStateSnapshotOwnerTest --tests app.yukine.playback.PlaybackProgressUpdateCommandOwnerTest --console=plain
# BUILD SUCCESSFUL

# 2026-07-07 11:18 暂停后进度不归零回归复核
./gradlew.bat :app:testDebugUnitTest --tests StreamingViewModelTest --tests app.yukine.playback.PlaybackPlayerStateOwnerTest --tests app.yukine.playback.PlaybackStateSnapshotOwnerTest --tests app.yukine.MainActivityArchitectureContractTest --console=plain
# BUILD SUCCESSFUL
```

## 0. 执行记录

| 项目 | 记录 |
| --- | --- |
| 日期 |  |
| 执行人 |  |
| 分支 / commit |  |
| APK / AAB 路径 |  |
| 设备 / Android 版本 |  |
| 音频输出设备 | 手机扬声器 / 有线耳机 / 蓝牙耳机 / 车机 |
| 流媒体账号状态 | 未登录 / 已登录 / 过期 |
| 证据目录 |  |

建议同时保存：

- `adb logcat` 片段，过滤 `EchoPlaybackService`、`MediaSession`、`ExoPlayer`、`AudioManager`。
- 播放页、通知、锁屏控制和失败状态截图。
- 涉及后台/来电/蓝牙切换时的短录屏。

## 1. 阻断分级

| 级别 | 定义 | 发布处理 |
| --- | --- | --- |
| P0 | 崩溃、卡死、后台播放失控、断开耳机后外放、通知/锁屏控制完全不可用、数据丢失。 | 阻断发布，修复后重跑相关矩阵。 |
| P1 | 播放可恢复但状态错误、提示不清楚、恢复路径不稳定、部分系统控制失效。 | 记录影响范围，发布前需明确修复或降级方案。 |
| P2 | 轻微视觉/文案/日志问题，不影响播放链路。 | 可带风险发布，但需记录回归计划。 |

## 2. 基础状态矩阵

| 场景 | 准备 | 步骤 | 通过标准 | 证据 | 结果 |
| --- | --- | --- | --- | --- | --- |
| 本地歌曲首次播放 | 本地曲库至少 3 首歌曲。 | 从曲库播放第一首，打开播放页，等待 10 秒。 | 播放开始；NowBar、播放页、通知状态一致；进度持续更新。 | 播放页截图、通知截图。 |  |
| 暂停/恢复 | 任意本地歌曲播放中。 | 播放页暂停，通知恢复播放，锁屏再次暂停。 | 三处控制都生效；状态不会互相打架。 | 录屏。 |  |
| 进度拖动 | 任意有时长歌曲。 | 拖到中段、接近末尾、再拖回开头。 | 实际播放位置、进度条、歌词/波形按预期同步；接近末尾不越界。 | 录屏、logcat。 |  |
| 切歌 | 队列至少 5 首。 | 连续点下一首 5 次，再上一首 3 次。 | 当前曲目、队列索引、通知标题同步；无空白状态。 | 录屏。 |  |
| 随机/重复 | 队列至少 5 首。 | 切换随机、列表循环、单曲循环，观察自然播完。 | 图标状态和实际下一首行为一致；设置重启后保留。 | 录屏、重启后截图。 |  |
| 睡眠定时 | 播放中。 | 设置短倒计时，等待触发；再设置一次并取消。 | 到时暂停；取消后不会再次触发。 | 录屏、logcat。 |  |

## 3. 系统媒体矩阵

| 场景 | 准备 | 步骤 | 通过标准 | 证据 | 结果 |
| --- | --- | --- | --- | --- | --- |
| 后台播放 | 任意歌曲播放中。 | 按 Home，等待 2 分钟，再回到应用。 | 播放持续；返回后 UI 与通知状态同步。 | 录屏、通知截图。 |  |
| 锁屏控制 | 播放中并锁屏。 | 在锁屏执行暂停、播放、下一首。 | 控制生效；标题/封面/进度合理更新。 | 锁屏截图或录屏。 |  |
| 通知控制 | 播放中拉下通知。 | 执行暂停、播放、上一首、下一首。 | 控制即时生效；通知不会丢失或重复出现。 | 录屏。 |  |
| 通知栏歌词 | 播放一首有时间戳歌词的歌曲。 | 前台播放数行后按 Home、锁屏，再返回前台。 | 每次歌词行变化都同步到通知；前后台切换后当前行立即出现，锁屏/OEM 媒体面板不保持旧行。 | 通知/锁屏截图、录屏。 |  |
| 耳机断开 | 有线或蓝牙耳机播放中。 | 拔出有线耳机或断开蓝牙。 | 触发 `ACTION_AUDIO_BECOMING_NOISY` 后按预期暂停；不会突然外放。 | logcat、录屏。 |  |
| 蓝牙切换 | 蓝牙耳机或车机可用。 | 手机扬声器 -> 蓝牙 -> 断开 -> 重新连接。 | 播放状态可理解；媒体按键仍能控制；无崩溃。 | 录屏、logcat。 |  |
| 音频独占 | 开启“音频独占”（默认）。 | 播放 Echo，同时打开其他音乐/视频 App 播放。 | Echo 请求系统媒体焦点；遵守焦点的其他 App 暂停或静音。不承诺强制停止所有第三方 App。 | 录屏、logcat。 |  |
| 音频混音 | 关闭“音频独占”。 | Echo 播放中再播放其他媒体 App。 | Echo 不主动请求焦点；允许与其他媒体同时播放，与设置说明一致。 | 录屏。 |  |
| 来电/通话 | 可模拟来电或使用真实测试机。 | 播放中接入通话，结束后返回。 | 通话期间播放行为符合系统预期；结束后可手动或自动恢复。 | 录屏、logcat。 |  |

## 4. 恢复与异常矩阵

| 场景 | 准备 | 步骤 | 通过标准 | 证据 | 结果 |
| --- | --- | --- | --- | --- | --- |
| 冷启动恢复 | 播放一首本地歌到中段。 | 退出应用并杀进程，重新打开。 | 队列、当前曲目、位置恢复或给出明确可恢复状态；不崩溃。 | 录屏、logcat。 |  |
| 后台被杀 | 后台播放中。 | 使用系统任务管理或 `adb shell am force-stop app.yukine` 后重新打开。 | 不进入崩溃循环；播放服务可重新建立；状态清楚。 | 命令输出、logcat。 |  |
| 无效本地 URI | 准备缺失或不可访问的曲目条目。 | 播放该条目。 | 不崩溃；显示可理解的失败状态；可继续播放其他歌曲。 | 录屏、logcat。 |  |
| 空队列控制 | 清空队列。 | 打开队列、点击播放控制。 | 空状态有标题/说明；控制不会崩溃。 | 截图。 |  |
| 超大队列 | 准备 500 首以上曲目。 | 播放、快速切歌、打开队列滚动。 | 队列操作可用；切歌无明显卡顿；不会 ANR。 | 录屏、logcat。 |  |
| 删除当前曲目 | 播放中删除当前曲目或移出曲库。 | 回到播放页和队列。 | 队列索引修正；播放停在明确状态或切到下一首；不崩溃。 | 录屏。 |  |

## 5. 流媒体播放矩阵

| 场景 | 准备 | 步骤 | 通过标准 | 证据 | 结果 |
| --- | --- | --- | --- | --- | --- |
| 流媒体解析播放 | 已登录网易云或配置可用网关。 | 播放一首未缓存流媒体歌曲。 | 先显示解析/加载反馈，解析后播放；失败可理解。 | 录屏、logcat。 |  |
| 播放中断网 | 流媒体播放中。 | 关闭网络 30 秒，再恢复。 | 出错不崩溃；恢复后可重试或继续；提示不暴露技术堆栈。 | 录屏、logcat。 |  |
| URL 过期恢复 | 使用可能过期的流媒体播放 URL。 | 播放到失败或重启后恢复。 | 触发恢复解析；不能恢复时有明确提示。 | logcat、录屏。 |  |
| 波形早期反馈 | 播放未缓存完歌曲。 | 打开播放页观察前 30 秒。 | 有占位或部分波形；不会等歌曲过半才出现反馈。 | 录屏。 |  |
| 大歌单流媒体队列 | 导入或播放 100 首以上流媒体歌单。 | 连续切歌、返回播放页、查看队列。 | 当前 URL 解析优先；队列不卡死；预缓存不阻塞主播放。 | 录屏、logcat。 |  |

## 6. 回归命令

人工矩阵前后至少执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\p0-stability-gate.ps1
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.StreamingViewModelTest --console=plain
.\gradlew.bat :feature:playback:testDebugUnitTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests app.yukine.playback.* --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

默认 `scripts\p0-stability-gate.ps1` 会要求 `adb devices` 至少发现一台在线设备；没有设备时会写出失败报告并退出失败，避免把未执行的播放服务真机验收误报为通过。无设备工作站可用 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\p0-stability-gate.ps1 -SkipDeviceProbe` 复跑自动化底座；需要同时产出 debug APK 时追加 `-IncludeAssemble`。脚本默认写出 `app\build\p0-stability-gate\<timestamp>.md` 报告，也可用 `-ReportPath <path>` 指定归档位置。

发布候选还应执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
```

连接设备时追加：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1 -Connected -DeviceSerial <serial>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\playback-stability-smoke.ps1 -DeviceSerial <serial>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\playback-stability-smoke.ps1 -DeviceSerial <serial> -SkipManualCheckpoint
```

`scripts\playback-stability-smoke.ps1` 会安装 debug APK、授权、清空 logcat、启动应用、采集截图 / `dumpsys activity` / `dumpsys media_session` / `dumpsys notification` / `dumpsys audio`，并在人工完成播放、暂停、切歌、进度拖动、后台、锁屏、通知、耳机/蓝牙/来电中断操作后采集 post-manual 证据和一次 `force-stop` 重启采样。脚本会扫描采样 logcat 中的 `FATAL EXCEPTION` / `AndroidRuntime` / `Process: app.yukine`，并在证据目录生成 `matrix-results.md` 结果填写模板。`-SkipManualCheckpoint` 只用于快速证明安装、启动、force-stop relaunch 和崩溃采样；脚本产物只能作为证据目录入口，矩阵每一行仍需人工按通过标准填写结果。

## 7. 结论模板

| 项目 | 记录 |
| --- | --- |
| P0 阻断数 |  |
| P1 风险数 |  |
| P2 记录数 |  |
| 已回归场景 |  |
| 未覆盖场景与原因 |  |
| 是否允许发布 | 是 / 否 |

若任一 P0 场景失败，不允许发布。若 P1 场景失败，发布前必须记录用户影响、回滚/降级方案和下一次回归入口。
