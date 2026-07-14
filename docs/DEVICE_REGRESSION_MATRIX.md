# YUKINE 设备回归矩阵

本矩阵区分“自动化已覆盖”和“必须真机确认”。未连接设备时只能标记编译通过，不能把真机项目写成已通过。

## 自动门禁

| 门禁 | 命令 | 通过条件 |
|---|---|---|
| 数据迁移与 Repository | `:feature:data:testDebugUnitTest` | v1/v14/失败保留夹具及 DAO 行为全绿 |
| 完整 JVM 单测 | `testDebugUnitTest` | 全模块单测全绿 |
| 静态检查 | `lintDebug` | 无失败项 |
| Debug 构建 | `assembleDebug` | APK 成功生成 |
| Instrumentation 编译 | `:app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac` | 测试 APK 源码全部可编译 |
| 中文/UTF-8 | `scan_mojibake.py` | 本次改动文件 `TOTAL=0` |
| 依赖边界 | `MainActivityArchitectureContractTest` | Activity/Service/Room/feature 边界报警全绿 |

## 真机/模拟器矩阵

| 场景 | Android 6/7 | Android 8-11 | Android 12-14 | Android 15 | 验收要点 |
|---|---:|---:|---:|---:|---|
| 首次启动、权限、Onboarding | 待测 | 待测 | 待测 | 待测 | 拒绝/允许/再次授权路径，无死循环 |
| 本地扫描与大曲库 | 待测 | 待测 | 待测 | 待测 | 无主线程卡顿，隐藏曲目不复现 |
| 本地播放、seek、切歌 | 待测 | 待测 | 待测 | 待测 | 进度、当前曲目、队列索引一致 |
| 随机/列表循环/单曲循环 | 待测 | 待测 | 待测 | 待测 | Service、通知、UI 模式一致 |
| 后台、锁屏、进程重建 | 待测 | 待测 | 待测 | 待测 | 队列/位置恢复，Activity 不参与后台反应 |
| 通知、耳机、蓝牙媒体键 | 待测 | 待测 | 待测 | 待测 | play/pause/next/previous 语义正确 |
| Android Auto / 车机 | 不适用或待测 | 待测 | 待测 | 待测 | MediaLibraryService 连接、控制和元数据 |
| Now Bar / Now Playing / Queue | 待测 | 待测 | 待测 | 待测 | 旋转、前后台、进程恢复后状态一致 |
| 歌词、通知歌词、悬浮歌词 | 待测 | 待测 | 待测 | 待测 | 逐句同步、权限和关闭路径正确 |
| 流媒体登录/搜索/解析/恢复 | 待测 | 待测 | 待测 | 待测 | 临时 URL 失效后续播且队列不丢 |
| WebDAV / M3U / 远程源 | 待测 | 待测 | 待测 | 待测 | 导入、同步、播放、删除语义一致 |
| 设置即时应用与重启恢复 | 待测 | 待测 | 待测 | 待测 | 单一命令路径，无镜像状态回滚 |
| 备份导入/失败回滚 | 待测 | 待测 | 待测 | 待测 | 冷启动替换，失败保留原数据库 |
| 大队列（500+） | 待测 | 待测 | 待测 | 待测 | 进度更新不复制完整队列，无明显掉帧 |

## 执行记录模板

每次候选包记录：

- Commit / APK SHA-256：
- 设备型号、系统版本、厂商 ROM：
- 数据库来源版本和副本校验值：
- 自动门禁结果：
- 真机场景结果与日志路径：
- 已知限制：
- 执行人和时间：

有连接设备时执行：

```powershell
adb devices
.\gradlew.bat --no-daemon --max-workers=1 connectedDebugAndroidTest --console=plain
```

无设备、设备 unauthorized/offline 或厂商能力不可用时，必须在发布结论中明确列为未验证，而不是跳过后判定通过。
