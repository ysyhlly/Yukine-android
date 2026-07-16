# 统一歌曲身份版本交付说明（2026-07-16）

## 交付包

- Release 测试 APK：`app/build/outputs/apk/release/echo-0.2.0-rc.1-release-test-signed.apk`
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 包名：`app.yukine`
- 版本：`0.2.0-rc.1`（versionCode 7）
- minSdk / targetSdk：23 / 35
- Release 测试 APK 文件大小：23,373,267 bytes
- Release 测试 APK SHA-256：`CF36B41608F3AD31DB36FD38865D08CC36A21B44E873A3B9AC59E99DBD45DBDA`
- 签名：Android Debug RSA 2048；APK Signature Scheme v1、v2、v3 验证通过
- Release Multidex：2 个 dex

这是经过 R8 与资源压缩、完整且可直接安装的 release 变体 APK，不是增量包。当前环境没有生产签名参数，因此该测试包使用 Android Debug 证书签名，适合测试与验收；对外发布应换用正式私钥重新签名，不能把 debug 证书当作生产升级证书。

## 数据迁移

- Room 当前版本为 v20，支持从 v1 至 v19 直接迁移。
- 收藏、歌单、历史、队列改为引用 canonical recording；平台、本地与 WebDAV 条目作为来源映射保留。
- canonical UUID 在后台 MusicBrainz、iTunes、网易或 QQ 增强时不更换。
- 合并、拆分和撤销在 Room 事务中完成；迁移或事务异常不允许删库兜底。
- 已自动验证 v1 至 v19 路由、v1/v8/v14/v15 独立夹具、非法旧数据回滚和外键完整性。

升级前建议复制 `echo_next.db` 及其同目录辅助文件。不要在唯一一份真实用户数据库上反复执行候选版本迁移。

## 回滚边界

- APK 降级不能自动把 v20 数据库还原成旧 schema。
- 若必须回滚，应同时恢复“旧 APK + 升级前完整数据库备份”；只安装旧 APK 可能因 Room schema 版本较新而无法启动。
- 身份合并的用户操作可在没有后续身份写入时使用应用内一次性撤销；这不替代发布级数据库备份。
- MusicBrainz 与候选缓存属于冷数据，可在服务不可用时忽略；播放、收藏、歌单和曲库不依赖它们在线。

## 验证结果

- JVM：1644 tests，0 failures，0 errors，0 skipped。
- App instrumentation：29 tests，0 failures，包含 Room、引用迁移、M3U、生命周期后台/恢复与 Activity 重建。
- Streaming instrumentation：9 tests，0 failures，包含 QuickJS Promise、HTTP 响应、异步初始化、取消安全、歌词/封面和来源顺序。
- Android Studio 模拟器：release 测试 APK 覆盖安装成功；离线冷启动、退后台再恢复均无 AndroidRuntime 崩溃，crash buffer 为空。
- 大数据：1000 个身份候选分页无重复；10240 首曲库深分页在测试预算内完成。
- 候选闭环：支持在单一 Room 事务内批量拒绝证据明确为硬冲突的候选，低分候选不会被自动拒绝；完整安全评分维度可查看，敏感 URL、Token 与 Cookie 会隐藏。
- 固定验收样本见 `docs/IDENTITY_ACCEPTANCE_BASELINE.md`，覆盖 Original、Live、Remix、翻唱和同名不同艺人。

## 已知限制与待真机验收

- `192.168.1.24:44007` 在交付时拒绝 ADB 连接，mDNS 未发现无线调试设备。因此真实手机上的 WebDAV、高码率 FLAC、锁屏后台播放和连续切歌 P50/P95 仍需使用新的有效无线调试端口补测。
- 网易与 QQ 后台身份候选需要已有账号凭据；无凭据时降级到 MusicBrainz 缓存、iTunes 与本地证据，不伪造匿名平台访问。
- Android Debug 证书只用于测试安装。正式发布包必须使用稳定的生产签名，并再次执行签名、安装和升级覆盖测试。
