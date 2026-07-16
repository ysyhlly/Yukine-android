# 统一歌曲身份固定验收基线

更新日期：2026-07-16

## 固定数据集

自动测试 `LibraryTrackMergePolicyTest.fixedIdentityAcceptanceDatasetKeepsOriginalLiveRemixCoverAndSameTitleDifferentArtistSeparate` 固定以下已知答案：

| 样本 | 预期身份 |
| --- | --- |
| 本地、WebDAV、网易、QQ、LX 的 `Baseline Song / Baseline Artist` | 合并为同一个 Original recording |
| `Baseline Song (Live) / Baseline Artist` | 独立 Live recording |
| `Baseline Song (Remix) / Baseline Artist` | 独立 Remix recording |
| `Baseline Song / Cover Artist` | 独立翻唱 recording |
| `Baseline Song / Different Artist` | 同名不同艺人，独立 recording |

相关安全回归还包括：

- `RecordingMatchRepositoryTest.liveRemixAndCoverHardConflictsCannotBeConfirmedEvenByTheManualUiPath`
- `RecordingMatchRepositoryTest.batchRejectOnlyRejectsPendingCandidatesWithExplicitHardConflicts`
- `RecordingMatchRepositoryTest.previewBlocksOriginalLiveConflictBeforeAnyMergeWrite`
- `MusicIdentityRepositoriesTest.equalStrongTagDoesNotMergeOriginalWithLiveVersion`

## 业务引用审计

Room v20 的稳定业务身份使用 `recording_id(Long)`：

- 收藏：`favorites.recording_id`
- 歌单：`playlist_recording_items.recording_id`
- 历史：`recording_play_history.recording_id`、`recording_play_events.recording_id`
- 队列：`playback_queue_identities.recording_id` 与可空 `preferred_source_id`
- 音源：`track_sources.recording_id`

仍存在部分以 `track_id` 命名的历史表和 DAO 查询，用于旧版本数据库兼容、UI 的本地 Track 映射及迁移期双写。身份合并、拆分和收藏同步以 canonical 表为事务主路径；删除这些兼容表必须另设数据库版本并完成旧备份导入、降级边界和真实升级测试，不能在 v20 内直接破坏。

对应事务回归：

- 合并迁移收藏、歌单、历史、事件、队列并去重。
- 拆分生成新 UUID，分别刷新两侧 `active_source_id`。
- 合并写入失败时整个事务回滚。
- 操作撤销恢复 canonical 表与兼容引用快照。

## 当前问题复现与设备边界

- 模拟器已覆盖联网/飞行模式冷启动、退后台恢复和 Activity 重建，未发现 `AndroidRuntime` 崩溃。
- 1000 条候选分页和 10240 首曲库深分页已纳入自动测试。
- PLG110 真机已通过无线调试端口 `192.168.1.24:40887` 完成数据库与切歌基线。
- 首批修复安装前快照为 735 tracks / 735 recordings / 797 sources；安装并启动后为 735 tracks / 734 recordings / 797 sources，新增 1 个多物理来源 recording 和 1 条可撤销 `MERGE_RECORDINGS` 操作。
- 实机确认两份 `Secret of my heart / 倉木麻衣 / 264594ms` 本地文件已归入同一 canonical recording；收藏 273 条、canonical 歌单项 516 条保持不变，`integrity_check=ok` 且无外键错误。
- 存量 WebDAV 同源重复需要完整 WebDAV 重扫后才进入新的离线物理来源聚类；仅安装或本地 MediaStore 扫描不会遍历所有旧 WebDAV recording。
- 300×300 主机评分基线为 90,000 次比较、Top1 300/300、P50 1.6582ms、P95 3.5807ms。
- 真机逐次切歌 7/8 成功、1 次超时；click→`PLAYER_READY` P50 1049ms、P95 3993ms；prepare→ready P50 188ms、P95 325ms。
- 旧基线使用 `PLAYER_READY`，不能代表音频首帧。当前代码已新增首个解码 PCM 经 AudioProcessor 的 `prepare/click → first PCM` P50/P95；仍需在下一次真机连接后重新采样，且该点仍早于硬件扬声器实际出声。
- 网易、QQ 红心真实账号回归需要有效账号凭据；无凭据时只执行本地优先、待处理队列和重试的自动测试。

完整审计见 `docs/UNIFIED_IDENTITY_STAGE0_AUDIT_2026-07-16.md`。
