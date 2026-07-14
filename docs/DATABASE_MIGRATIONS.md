# YUKINE 数据库迁移

## 当前基线

- 数据库文件：`echo_next.db`。
- Room 数据库：`YukineDatabase`，当前 schema 版本 15。
- 支持升级来源：版本 1 至 14。
- 迁移入口：`YukineMigrations.all`；每个受支持旧版本都有到 v15 的显式 `Migration`。
- 禁止 `fallbackToDestructiveMigration`、删库重建或迁移失败后清库兜底。

v15 由以下聚焦 DAO/Repository 管理：Library、Playlist、History、PlaybackPersistence、Settings、RemoteSource、StreamingTrackMatch。跨表写入使用 Room 事务保持歌单、收藏、历史、队列与曲目引用的一致性。

## 零损原则

1. 先验证旧表中的关键值和主键合法性，再开始 schema normalization。
2. 迁移必须在 SQLite 事务内完成；任何异常都中止打开。
3. 失败时保留原文件、原 schema version 和原数据，不允许自动删除数据库。
4. 表名、列名、默认值、索引、唯一约束和用户可见语义必须保持兼容。
5. 备份恢复只能在数据库尚未打开的冷启动阶段执行，并使用可回滚替换。

`YukineSchema.normalizeV15` 负责把各历史版本规范化为 Room v15 等价 schema。新安装会额外恢复 `playlists.name TEXT NOT NULL UNIQUE` 的精确历史约束，避免 Room 注解表达能力差异改变既有语义。

## 自动迁移夹具

`YukineDatabaseMigrationTest` 当前覆盖：

- v1 夹具：曲目、收藏、播放历史和播放事件升级后保留，并补齐后续音频/队列列。
- v14 完整夹具：曲目、收藏、历史、歌单及顺序、512 项队列、当前索引、播放位置、设置、隐藏曲目、远程源和流媒体匹配逐项保留。
- 非法旧数据：例如设置表出现 null 主键时，迁移必须失败；随后只读打开原文件仍为 v14，异常行和值保持不变。
- 聚焦 DAO/Repository：Library、Playlist、History、PlaybackPersistence、Settings、RemoteSource 的读写和事务语义。

运行方式：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :feature:data:testDebugUnitTest --console=plain
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
