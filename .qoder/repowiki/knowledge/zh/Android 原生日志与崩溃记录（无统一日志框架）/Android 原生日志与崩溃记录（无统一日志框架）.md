---
kind: logging_system
name: Android 原生日志与崩溃记录（无统一日志框架）
category: logging_system
scope:
    - '**'
source_files:
    - app/src/main/java/app/yukine/diagnostics/CrashLogger.kt
    - app/proguard-rules.pro
---

本仓库的 Android 端**未引入统一的日志框架**（如 Timber、SLF4J、Logback 等），而是采用以下轻量组合：

1. **运行时调试/告警输出**：各模块直接通过 `android.util.Log` 调用，以类常量 `TAG` 区分来源，使用 `Log.w / Log.i / Log.e` 等标准级别。常见于 Worker、Service、Activity 及 Repository 中，例如 `FavoriteSyncWorker.kt`、`FloatingLyricsService.kt`、`IdentityBackfillWorker.kt`、`DashboardRepository.kt` 等。
2. **崩溃本地持久化**：`app/src/main/java/app/yukine/diagnostics/CrashLogger.kt` 提供单例 `CrashLogger.install(context)`，在 `EchoApplication` 启动时注册为全局 `UncaughtExceptionHandler`，将未捕获异常连同线程名、时间戳写入应用私有目录 `filesDir/crash-logs/crash-<timestamp>.log`，并保留最近 10 个文件自动清理。该实现不联网、不依赖第三方 SaaS，仅用于本地排障。
3. **ProGuard 保护**：`proguard-rules.pro` 中对 `CrashLogger` 做了 `-keep` 规则，确保混淆后仍可被反射或外部工具读取。

**架构与约定**
- 没有集中式 Logger 抽象层或日志门面；每个类自行 import `android.util.Log` 并使用自身 TAG。
- 日志级别选择偏保守：业务路径多用 `Log.w` 标记可恢复异常，严重错误用 `Log.e`，信息性输出较少。
- CrashLogger 是唯一的“跨进程”日志载体，其他日志均走 logcat，不存在结构化 JSON 字段、分级 sink 或远程上报通道。

**开发者应遵循的规则**
- 新增代码如需记录异常，优先使用 `android.util.Log.w(TAG, message, error)` 形式，保持 TAG 与类名一致。
- 需要持久化不可复现场景时，参考 `CrashLogger` 模式：写入 `filesDir` 下子目录、限制文件数量、全程 try-catch 避免二次崩溃。
- 不要引入额外日志库；若未来需要结构化日志或远程上报，应在 `diagnostics` 包内扩展新的 logger 组件，而非散落在各 feature 模块。

由于缺少统一框架与集中配置，当前日志系统属于**低成熟度**状态——能覆盖基本调试与崩溃收集，但不具备分级路由、结构化字段、性能开关等能力。