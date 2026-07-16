# YUKINE 数据库迁移

## 当前基线

- 数据库文件：`echo_next.db`。
- Room 数据库：`YukineDatabase`，当前 schema 版本 25。
- 支持升级来源：版本 1 至 24。
- 迁移入口：`YukineMigrations.all`；每个受支持旧版本都有到 v25 的显式 `Migration`。
- 禁止 `fallbackToDestructiveMigration`、删库重建或迁移失败后清库兜底。

v20 在原有 Library、Playlist、History、PlaybackPersistence、Settings、RemoteSource、StreamingTrackMatch 之上增加 canonical recording、来源映射、外部标识、候选、身份操作、后台增强缓存等表。v21 为队列、聚合历史和逐次播放事件执行一次幂等 canonical 引用修复，并用唯一 `legacy_event_id` 关联双轨事件，避免部分回填和重复迁移。v22 增加按 `source_id` 绑定、带算法版本和元数据签名的匹配特征快照；标题、艺人、专辑、版本、时长桶和 n-gram 只在来源变化或算法升级时重算。v23 增加 `source_recording_candidates`，为每个来源持久化最多 20 个粗排 recording 候选，并在特征行记录候选算法版本、全库快照签名和生成时间。v24 增加规范化录音对主键的 `recording_relations`，分别保存同录音概率、同作品概率、关系类型、算法版本与证据；人工拒绝和拆分使用锁定关系，后台评分不能覆盖。v25 增加内部 Long ID 与稳定 UUID 双身份的 `works`，并为既有 recording 幂等回填一对一初始 Work；同时增加按 `source_id` 绑定的 `audio_features` 冷表，保存内容签名、PCM/Chromaprint、录音/作品 embedding、版本分数和音频参数解析状态。业务热路径使用内部 `recording_id`，外部同步使用稳定 canonical UUID；跨表写入使用 Room 事务保持歌单、收藏、历史、队列与来源引用一致。

## 零损原则

1. 先验证旧表中的关键值和主键合法性，再开始 schema normalization。
2. 迁移必须在 SQLite 事务内完成；任何异常都中止打开。
3. 失败时保留原文件、原 schema version 和原数据，不允许自动删除数据库。
4. 表名、列名、默认值、索引、唯一约束和用户可见语义必须保持兼容。
5. 备份恢复只能在数据库尚未打开的冷启动阶段执行，并使用可回滚替换。

版本 1～19 先由 `YukineSchema.normalizeV20` 规范化为 Room v20 等价 schema，再依次执行 `normalizeV21`、`normalizeV22`、`normalizeV23`、`normalizeV24` 和 `normalizeV25`；真实 v20～v24 数据库只执行其后续幂等规范化，不能重新运行仍读取旧 `favorites.track_id` 的 v20 转换。新安装会额外恢复 `playlists.name TEXT NOT NULL UNIQUE` 的精确历史约束，避免 Room 注解表达能力差异改变既有语义。

## 自动迁移夹具

`YukineDatabaseMigrationTest` 当前覆盖：

- v1 夹具：曲目、收藏、播放历史和播放事件升级后保留，并补齐后续音频/队列列。
- v1 至 v19 路由矩阵：每个受支持起始版本均可原子升级至 v25，升级后通过外键与完整性检查。
- v21→v25 特征迁移夹具：匹配特征表、索引和候选快照列完整，既有设置不变，外键与完整性检查通过。
- v22→v25 候选迁移夹具：既有匹配特征无损增加候选快照字段，`source_recording_candidates` 的外键、复合主键和排序/版本索引完整，用户设置保持不变。
- v23→v25 关系迁移夹具：新增录音关系概率、关系类型、人工锁定字段与查询索引，用户设置保持不变。
- v24→v25 Work/音频特征夹具：既有 recording 获得稳定 `work_id`，Work 数量与 recording 数量一致；`audio_features` 的来源外键、内容签名与解析状态索引完整，设置及完整性检查保持不变。
- v20 部分引用夹具：同一平台来源对应多个旧 Track 时，补齐全部队列位置和聚合历史；重复时间戳的旧事件逐条绑定且不丢失 canonical-only 事件；重复执行修复不增加事件或计数。
- v14 完整夹具：曲目、收藏、历史、歌单及顺序、512 项队列、当前索引、播放位置、设置、隐藏曲目、远程源和流媒体匹配逐项保留。
- v15 身份夹具：旧平台映射迁移为未验证来源/候选，canonical UUID、整数主键、来源选择索引与后台缓存结构完整。
- 非法旧数据：例如设置表出现 null 主键时，迁移必须失败；随后只读打开原文件仍为 v14，异常行和值保持不变。
- 事务故障注入：身份合并在操作日志写入失败时，来源、收藏、歌单、历史、队列和 recording 全部回滚。
- 聚焦 DAO/Repository：Library、Playlist、History、PlaybackPersistence、Settings、RemoteSource 的读写和事务语义。

运行方式：

```powershell
.\gradlew.bat :feature:data:testDebugUnitTest
```

Instrumentation 中的 `RoomRepositoriesInstrumentedTest` 与 `MusicLibraryRepositoryInstrumentedTest` 用于验证真实 Android SQLite/Room 行为；发布门禁至少要先完成其编译，有连接设备时必须实际执行。

## 新增 schema 的流程

1. 导出并提交新 Room schema。
2. 添加从每个仍受支持版本到新版本的显式 Migration；不得只添加“上一版本到新版本”而使旧安装断链。
3. 先增加旧数据库夹具和逐表升级前后等价断言，再改生产 schema。
4. 为新增/变化的外键、索引、默认值、唯一约束和 nullability 增加断言。
5. 用数据库副本验证失败路径不会改变原文件。
6. 运行数据模块单测、完整单测、lint、assemble 和连接设备 instrumentation。

## 发布前人工校验

对 v1、代表性中间版本和真实 v14 数据库副本分别记录升级前后：表行数、主键集合、歌单顺序、队列顺序/索引、播放位置、设置键值、隐藏曲目、远程源和流媒体匹配。任一差异无法解释时停止发布；不要在原用户数据库上反复试迁移。
