# ECHO Android 代码审查 Bug 清单（2026-07-22）

> 14 轮深度审查，覆盖 60+ 模块，累计发现 ~170 个问题。
> 已修复 32 个（标记 ✅），以下为剩余待修复问题。

---

## 严重 (Critical) — 2 个待修复，12 个本轮已修复

### C-01. UsbPcmWriter 使用 bulkTransfer 写入等时端点 ✅
- **文件**: `feature/playback/src/main/java/app/yukine/playback/usb/UsbPcmWriter.kt#L119`
- **问题**: `bulkTransfer()` 用于等时(isochronous)端点，USB 独占在大多数 DAC 上无法工作
- **影响**: USB 独占播放功能在大部分 DAC 设备上完全失效
- **修复**: Android 公共 USB API 不支持等时传输；端点类型现在显式分流。ISO 路径使用随 APK 动态链接的 libusb 1.0.29、持续 event thread、预分配 transfer ring、逐包状态与取消后回收；bulk 只保留给真正的 bulk 端点。

### C-02. NativeAudioFocusController 清除主线程所有回调 ✅
- **文件**: `feature/playback/src/main/java/app/yukine/playback/manager/NativeAudioFocusController.kt#L147`
- **问题**: `handler.removeCallbacksAndMessages(null)` 清除主线程 MessageQueue 中所有回调
- **影响**: 其他模块 post 到主线程的任务被意外取消（通知更新、UI 刷新等）
- **修复方向**: 使用特定 token 或仅 `removeCallbacks(specificRunnable)`

### C-03. PlaybackServiceRuntime WIFI_MODE_FULL_LOW_LATENCY 在 API<29 崩溃 ✅
- **文件**: `app/src/main/java/app/yukine/playback/PlaybackServiceRuntime.java#L928`
- **问题**: `WIFI_MODE_FULL_LOW_LATENCY` 常量在 API 29 以下不存在
- **影响**: Android 9 及以下设备在流媒体播放时直接崩溃
- **修复方向**: 添加 `Build.VERSION.SDK_INT >= 29` 守卫

### C-04. WebDavClient XXE 漏洞 ✅
- **文件**: `feature/data/src/main/java/app/yukine/data/WebDavClient.java#L284`
- **问题**: XML 解析器未禁用外部实体，存在 XXE 注入风险
- **影响**: 恶意 WebDAV 服务器可读取设备本地文件或发起 SSRF
- **修复方向**: 设置 `XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES = false`

### C-05. WebDavClient 响应体无大小限制 → OOM ✅
- **文件**: `feature/data/src/main/java/app/yukine/data/WebDavClient.java#L369`
- **问题**: PROPFIND 响应体读入内存无上限
- **影响**: 恶意/超大目录列表可导致 OOM 崩溃
- **修复方向**: 设置最大读取字节数（如 50MB），超出时截断或报错

### C-06. WebDavClient 曲目重定位丢失元数据
- **文件**: `feature/data/src/main/java/app/yukine/data/WebDavClient.java#L539`
- **问题**: 文件移动/重命名后重新同步时，已有元数据（播放次数、收藏状态）丢失
- **影响**: 用户曲库统计数据在文件重组后全部归零
- **修复方向**: 基于内容哈希或 inode 识别移动文件，保留原记录

### C-07. TrackDownloadManager 下载 ID 计数器重启后冲突 ✅
- **文件**: `app/src/main/java/app/yukine/TrackDownloadManager.kt#L59`
- **问题**: `customIdCounter` 始终从 -2 开始，重启后覆盖已有记录
- **影响**: 重启后第一次下载静默覆盖已有下载记录
- **修复方向**: `restoreRecords()` 后将计数器设为已恢复最小 ID - 1

### C-08. FloatingLyricsService closestTextAt 算法错误 ✅
- **文件**: `app/src/main/java/app/yukine/FloatingLyricsService.kt#L309`
- **问题**: 二分查找逻辑错误导致翻译行与原文行不同步
- **影响**: 悬浮歌词翻译显示错位
- **修复方向**: 修正二分查找边界条件

### C-09. IdentityEnhancementEngine 置信度逻辑反转 ✅
- **文件**: `feature/data/src/main/java/app/yukine/data/enrichment/IdentityEnhancementEngine.kt#L645`
- **问题**: 置信度比较方向反转，高置信度候选被拒绝，低置信度被接受
- **影响**: 身份匹配结果完全错误
- **修复方向**: 反转比较运算符

### C-10. StreamingPlaylistImporter syncRemotePlaylist 仅获取第一页 ✅
- **文件**: `feature/streaming/src/main/java/app/yukine/streaming/StreamingPlaylistImporter.kt#L91`
- **问题**: `pageSize=2000` 仅获取第一页，未检查 `hasMore`
- **影响**: >2000 首的播放列表同步验证误报失败、删除遗漏、重复添加
- **修复方向**: 循环分页获取完整列表

### C-11. PlaybackCrossfadeAdvanceManager 取消后音量未恢复 ✅
- **文件**: `feature/playback/src/main/java/app/yukine/playback/manager/PlaybackCrossfadeAdvanceManager.kt#L83`
- **问题**: Crossfade 取消后播放器音量停留在淡出中间值
- **影响**: 取消 crossfade 后音量突然降低
- **修复方向**: 取消时调用 `player.volume = 1.0f`

### C-12. PlaybackServiceRuntime 输出模式切换时播放位置丢失 ✅
- **文件**: `app/src/main/java/app/yukine/playback/PlaybackServiceRuntime.java#L2008-L2046`
- **问题**: 切换输出模式时未保存/恢复当前播放位置
- **影响**: 用户切换 USB/标准输出后从头播放
- **修复方向**: 切换前 `getCurrentPosition()`，切换后 `seekTo()`

### C-13. PlaybackServiceRuntime preparing 状态永久卡死 ✅
- **文件**: `app/src/main/java/app/yukine/playback/PlaybackServiceRuntime.java#L1706-L1741`
- **问题**: 播放器创建失败时 preparing 标志未重置
- **影响**: 后续所有播放操作被阻塞
- **修复方向**: 在 catch 块中重置 preparing 状态

### C-14. PlaylistRepository loadTracks 索引配对错误 ✅
- **文件**: `feature/data/src/main/java/app/yukine/data/PlaylistRepository.java#L296-L300`
- **问题**: INNER JOIN 排除已删除曲目后索引错位，错误配对 recording→track
- **影响**: 播放列表显示/播放错误曲目
- **修复方向**: 修改查询返回 recording_id，使用基于 ID 的配对

---

## 警告 (Warning) — 85 个待修复，5 个本轮已修复

### 播放核心

| # | 文件 | 问题 |
|---|------|------|
| W-01 | `PlaybackQueueManager.kt#L674` | `replaceQueuedTrackById` 缺少 `preparing()` 检查，缓冲中替换后播放停滞 |
| W-02 | `PlaybackQueueManager.kt#L481` | `CopyOnWriteArrayList` 上 `clear()+addAll()` 非原子，并发读取观察到空队列 |
| W-03 | `PlaybackServiceRuntime.java#L2041` | STANDARD 模式切换后音频效果器未重新绑定 |
| W-04 | `PlaybackServiceRuntime.java#L2012` | 模式切换未取消进行中的 Crossfade |
| W-05 | `PlaybackVisualizationAnalyzer.kt#L253` | 异常时 `waveformGeneratingKey` 永久卡死，阻止后续重试 |
| W-06 | `PlaybackNotificationManager.kt#L99` | STOP 动作触发不必要的前台通知发布-移除循环 |
| W-07 | `PlaybackRestoreReceiver.java#L30` | 绕过 Hilt DI 直接构造 Repository |
| W-08 | `PlaybackPlayerFactory.kt#L119` | `player.release()` 异常时 `cacheReleaser` 不执行 |

### USB/音频

| # | 文件 | 问题 |
|---|------|------|
| W-09 ✅ | `UsbAudioDescriptorParser.kt` / `UsbRawDescriptorParser.kt` | UAC1 使用 endpoint sample-frequency control；UAC2 从描述符解析 Clock Source ID，无法解析时拒绝猜测并显式降级。 |

### 数据层

| # | 文件 | 问题 |
|---|------|------|
| W-10 | `LibraryRepository.java#L1291` | 删除当前播放曲目后队列索引错误 |
| W-11 | `LibraryRepository.java#L1000` | `saveStreamingTrackMatch` 冲突时丢失已有匹配 |
| W-12 | `LibraryRepository.java#L527` | 增量扫描 diff 对重复 dataPath 产生孤儿数据 |
| W-13 | `PlaylistRepository.java#L226` | `moveTrack` 事务外计算索引，并发修改时交换错误曲目 |
| W-14 | `PlaybackPersistenceRepository.java#L121` | `saveQueue` 事务外读取身份信息 |
| W-15 | `IdentityBackfillCoordinator.kt#L148` | `consolidateAliasSource` 删除源后未调用 `refreshActiveSource` |
| W-16 | `RecordingRelationStore.kt#L31` | `upsert()` 读-写无事务保护，后台聚类可覆盖用户手动决策 |
| W-17 | `YukineDaos.kt#L485` | `canonicalPlayedSince` GROUP BY 导致 JOIN 使用非确定性值 |
| W-18 | `DuplicateCandidateRepository.kt#L93` | 用户显式确认合并仍套用批量自动阈值（≥0.95） |
| W-19 ✅ | `StreamingRepository.kt#L127` | search 缓存写入条件 `\|\|` 应为 `&&` |

### 流媒体

| # | 文件 | 问题 |
|---|------|------|
| W-20 | `LocalQqMusicStreamingClient.kt#L141` | `total` 报告当前页数量而非实际总数 |
| W-21 | `StreamingSearchStateOwner.kt#L100` | 无 Job 追踪，旧搜索结果覆盖新查询 |
| W-22 | `StreamingAuthStateOwner.kt#L221` | 共享 loading 状态被后台操作错误清除 |
| W-23 | `StreamingPlaybackResolutionStateOwner.kt#L498` | 共享 CompletableDeferred 取消传染 |

### 同步/下载

| # | 文件 | 问题 |
|---|------|------|
| W-24 | `FavoriteSyncDomain.kt#L363` | EventBus publish/bind 竞态导致收藏事件永久丢失 |
| W-25 | `TrackDownloadManager.kt#L478` | 暂停→立即恢复竞态将任务错误标记为 Failed |
| W-26 | `FavoriteSyncWorker.kt#L73` | 所有异常一律 retry，不可恢复错误无限重试 |

### 身份/验证

| # | 文件 | 问题 |
|---|------|------|
| W-27 ✅ | `DefaultRecordingSourceVerificationGateway.kt#L89` | WebDAV 验证通过明文 HTTP 发送 Basic Auth 凭据 |
| W-28 | `DefaultRecordingSourceVerificationGateway.kt#L127` | 码率 0 被视为有效值而非"未知" |

### UI/ViewModel

| # | 文件 | 问题 |
|---|------|------|
| W-29 ✅ | `LyricsViewModel.kt#L284` | 加载缺少异常处理，失败时 UI 永久卡在"加载中" |
| W-30 | `NowPlayingViewModel.kt#L118` | 非线程安全 `mutableSetOf` 无同步保护 |
| W-31 | `SettingsPageStateOwners.kt#L105` | HomeDashboardLayout 保存失败回滚因类型不匹配失效 |

### 核心模型

| # | 文件 | 问题 |
|---|------|------|
| W-32 | `Track.java` | 核心模型缺少 `equals()`/`hashCode()` |
| W-33 | `ChromaprintSegmentAlignment.kt#L14` | data class 含 IntArray，equals/hashCode 使用引用相等 |

### 通知/歌词

| # | 文件 | 问题 |
|---|------|------|
| W-34 ✅ | `PlaybackLyricsManager.kt#L126` | `notificationLyricText` 曲目匹配仅比较 title，同名曲显示错误歌词 |
| W-35 ✅ | `EchoApplication.kt#L11` | `CrashLogger.install` 在 `applyPendingRestore` 之后，初始化崩溃无日志 |

### Metadata Gateway (TypeScript)

| # | 文件 | 问题 |
|---|------|------|
| W-36 | `transport.ts#L580` | 熔断器 half_open 探针中止后永久卡死 |
| W-37 | `transport.ts#L256` | `coordinatedFetch` 等待循环中 abort 异常未捕获 |
| W-38 | `recording.ts#L150` | `normalizeMetadataText` 清空 "FT Island" 艺人名 |
| W-39 | `recording.ts#L63` | 多词版本模式不匹配连字符形式 |
| W-40 | `dashboard-metrics.ts#L450` | 指标刷新 Promise 链永久断裂 |
| W-41 | `server.ts#L147` | 优雅关闭异常跳过后续资源清理 |
| W-42 | `sqlite-cache.ts#L120` | `stale_until` 计算与 Redis 缓存不一致 |

### 其他警告

| # | 文件 | 问题 |
|---|------|------|
| W-43 | `LyricsDocumentParser.kt#L576` | LRC 时间戳 `else -> 0L` 分支防御性缺陷 |
| W-44 | `PlaybackMediaSourceProvider.kt#L272` | `continuousCachedBytes` 返回总量而非连续前缀 |
| W-45 | `PlaybackShutdownCoordinator.kt#L83` | `handleServiceDestroyed` 未保存恢复播放标记 |
| W-46 | `MusicLibraryRepository.java#L1881` | `streamDataPaths` 缺少 null 检查 |
| W-47 | `YukineEntities.kt#L122` | `playlist_tracks` 缺少 `track_id` 单列索引 |
| W-48 | `YukineEntities.kt#L616` | `track_sources.data_path` 缺少索引 |

---

## 建议 (Suggestion) — 47 个

| # | 文件 | 问题 |
|---|------|------|
| S-01 | `PlaybackQueueManager.kt#L1195` | `upcomingTracksForPrecache` 忽略 shuffle 模式 |
| S-02 | `YukineMigrations.kt#L436` | `normalizeV32` 将录音置信度直接用作标识符置信度 |
| S-03 | `QueueViewModel.kt#L98` | `distinctUntilChanged` 因 Track 无 equals 而失效 |
| S-04 | `NowPlayingViewModel.kt#L650` | `toggleLyrics` read-modify-write 潜在 lost-update |
| S-05 | `PlaybackPrecacheManager.java#L716` | `clearPrecacheState()` 死代码 |
| S-06 | `PlaybackPrecacheManager.java#L151` | `release()` check-then-act 非原子 |
| S-07 | `PlaybackNotificationManager.kt#L67` | 歌词变化驱动通知高频重建（电量消耗） |
| S-08 | `EchoPlaybackService.java#L150` | `stopForeground(true)` API 31+ 已弃用 |
| S-09 | `PlaybackRestoreReceiver.java#L26` | `goAsync()` 缺少超时保护 |
| S-10 | `RecordingMatchScoringConfig.kt#L39` | 精确浮点比较验证权重之和 |
| S-11 | `StreamingRepository.kt#L325` | `raceKnownPlaybackAttempts` 死代码 |
| S-12 | `manager.ts#L119` | Provider failures 数组缺少上限约束 |
| S-13 | `core.ts#L1054` | `trustedQqMusicImage` 域名白名单 `.qq.com` 过于宽泛 |
| S-14 | `CrashLogger.kt#L54` | 结构化头部缺少异常类型名称 |
| S-15 | `core.ts#L781` | QQ Music 歌曲搜索响应解析结构可能不正确（无测试覆盖） |

---

## 已修复 (32 个) ✅

| # | 文件 | 修复内容 |
|---|------|----------|
| ✅ | `UsbAudioDeviceManager.kt` | `RECEIVER_NOT_EXPORTED` → `RECEIVER_EXPORTED` |
| ✅ | `Mp4MetadataParser.java` | `isAtomType` 添加 `& 0xff` 无符号转换 |
| ✅ | `Mp3MetadataParser.java` | ID3v2.3 扩展头跳过 `+4` |
| ✅ | `PlaybackAudioEffectManager.kt` | 预设路径添加 `setEnabled()` |
| ✅ | `M3uPlaylistParser.java` | `lastIndexOf` → `indexOf` |
| ✅ | `FloatingLyricsPlaybackActionMapper.kt` | `PLAY` → `RESTORE_AND_PLAY` |
| ✅ | `PlaybackSleepTimerManager.kt` | 到期时添加 `publishState()` |
| ✅ | `ItunesMetadataClient.kt` | 过期缓存 `allEndpointsFailed = false` |
| ✅ | `MetadataGatewayClient.kt` | 长度检查 `<` → `<=` |
| ✅ | `DefaultRecordingSourceVerificationGateway.kt` | 重抛 `CancellationException` |
| ✅ | `PlaybackRecoveryScheduler.kt` | 添加 `@Volatile` |
| ✅ | `PlaybackWarmupCoordinator.kt` | 添加 `@Volatile` |
| ✅ | `core.ts` + `v2.ts` | 网易云歌词来源 `"netease"` + schema |
| ✅ | `StreamingGateway.kt` | 熔断器改用 Atomic 类 |
| ✅ | `LocalBilibiliStreamingClient.kt` | `hasMore` 使用 `effectivePageSize` |
| C-03 | `PlaybackServiceRuntime.java` + `PlaybackWifiLockOwner.java` | API 29 以下回退 `WIFI_MODE_FULL` |
| C-04 | `WebDavClient.java` | 禁用 DOCTYPE、外部实体和 XInclude，并拒绝实体解析 |
| C-05 | `WebDavClient.java` | PROPFIND 响应体限制为 50 MiB |
| C-07 | `TrackDownloadManager.kt` | 从已恢复的负数 ID 推导下一个自定义下载 ID |
| C-09 | `IdentityEnhancementEngine.kt` | 指纹验证候选使用满置信度，未验证候选使用 provider score |
| C-12 | `PlaybackServiceRuntime.java` | 输出模式重建显式恢复播放位置与播放意图 |
| C-13 | `PlaybackServiceRuntime.java` | 播放器创建和初始化异常统一清除 preparing 状态 |
| C-14 | `PlaylistRepository.java` + `YukineDaos.kt` | 使用 recording ID 键控投影配对曲目 |
| C-01 | `UsbPcmTransport.kt` + `usb_iso_jni.cpp` | ISO 端点改用 libusb 异步 transfer ring，补 high-bandwidth、feedback、逐包指标与安全取消 |
| W-09 | `UsbAudioDescriptorParser.kt` + `UsbRawDescriptorParser.kt` | 分离 UAC1 endpoint 与 UAC2 Clock Source 采样率控制，禁止猜测 entity ID |
| C-02 | `NativeAudioFocusController.kt` | 释放音频焦点时只移除控制器自己的回调，不再清空共享主线程 Handler |
| C-08 | `FloatingLyricsService.kt` | 翻译与罗马音只选择当前时间之前的歌词行，避免提前显示未来行 |
| C-10 | `StreamingPlaylistImporter.kt` | 完整分页读取远端播放列表，并对无进展与页数上限做失败保护 |
| C-11 | `PlaybackCrossfadeAdvanceManager.kt` | 普通取消淡出时恢复应用音量，release 路径保持不写播放器 |
| W-19 | `StreamingRepository.kt` | 仅在启用缓存且搜索结果非空时写入搜索缓存 |
| W-27 | `DefaultRecordingSourceVerificationGateway.kt` | 禁止通过明文 HTTP 发送 WebDAV Basic Auth，保留无认证 HTTP |
| W-29 | `LyricsViewModel.kt` | 加载异常进入 NOT_FOUND 终态，同时继续传播协程取消 |
| W-34 | `PlaybackLyricsManager.kt` | 通知歌词优先按 track ID、回退按标题与艺人匹配 |
| W-35 | `EchoApplication.kt` | 在启动恢复前安装 CrashLogger，覆盖恢复阶段崩溃 |

---

## 修复优先级建议

### P0 — 本轮已完成（影响核心功能/安全）
- ✅ C-03 (API<29 崩溃)
- ✅ C-04 (XXE 漏洞)
- ✅ C-05 (OOM)
- ✅ C-07 (下载记录覆盖)
- ✅ C-09 (置信度反转)
- ✅ C-12 (位置丢失)
- ✅ C-13 (preparing 卡死)
- ✅ C-14 (播放列表错误曲目)

### P1 — 本迭代修复（影响用户体验）
- C-01, C-06
- W-01 ~ W-10

### P2 — 下迭代修复（边缘场景/性能）
- 其余 Warning 和 Suggestion
