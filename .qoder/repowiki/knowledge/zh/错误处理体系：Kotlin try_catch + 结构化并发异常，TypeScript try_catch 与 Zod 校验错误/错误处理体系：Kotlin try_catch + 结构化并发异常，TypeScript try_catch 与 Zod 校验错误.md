---
kind: error_handling
name: 错误处理体系：Kotlin try/catch + 结构化并发异常，TypeScript try/catch 与 Zod 校验错误
category: error_handling
scope:
    - '**'
source_files:
    - app/src/main/java/app/yukine/FavoriteSyncDomain.kt
    - app/src/main/java/app/yukine/BackgroundImageStore.kt
    - app/src/main/java/app/yukine/DocumentPickerController.kt
    - metadata-gateway/src/transport.ts
    - metadata-gateway/src/node/sqlite-cache.ts
    - metadata-gateway/src/core.ts
    - tmp/yukine-metadata-gateway/src/contracts/v2.ts
---

本仓库在 Android（Kotlin）与元数据网关（TypeScript/Node.js）两个子系统中分别采用各自语言生态的惯用错误处理方式，未建立跨模块的统一错误类型或中间件层。

### Android（Kotlin）
- 主要使用 `try { ... } catch (e: XxxException) { ... }` 捕获 I/O、权限等运行时异常，如 `BackgroundImageStore.kt` 捕获 `IOException`、`DocumentPickerController.kt` 捕获 `SecurityException` / `IllegalArgumentException`。
- 协程侧通过 `catch (cancelled: CancellationException)` 显式区分取消与业务错误（见 `FavoriteSyncDomain.kt`），并以 `catch (error: Throwable)` 兜底记录不可预期异常。
- 未发现自定义 `sealed class Error`、`Result<T>` 包装或全局 `CoroutineExceptionHandler`；错误信息多以日志形式输出，UI 层通过状态变量反馈。
- 无 `panic/recover` 对应物（Kotlin 无 panic），也未发现 `@Throws` 声明或统一异常基类。

### 元数据网关（TypeScript/Node.js）
- 核心逻辑广泛使用 `try { ... } catch (error) { ... }` 包裹网络请求与 SQLite 缓存操作（`transport.ts`、`sqlite-cache.ts`、`core.ts`）。
- 请求参数校验通过 `zod`，并通过 `validationIssues(error: z.ZodError)` 将校验错误转换为结构化字段级问题列表（`tmp/yukine-metadata-gateway/src/contracts/v2.ts`）。
- 存在少量自定义错误类型（如 `WorkQueueBusyError`），但未形成统一的错误枚举或 HTTP 错误码映射层；错误响应格式在各路由中自行构造。
- Node 服务器端未注册全局 unhandledRejection/uncaughtException 处理器，错误以 console 日志为主。

### Web 原型（React/Vite）
- 前端原型仅用于高保真交互演示，不包含后端错误处理逻辑，不涉及此主题。

### 结论与建议
当前错误处理是“就地 try/catch + 日志”模式，缺乏跨模块的错误类型定义、传播约定与用户可见的错误消息规范。若需提升可维护性，建议：
1. 在 `core/common` 或 `feature/data` 中定义统一的 Kotlin 错误 sealed class（如 `YukineError`），并在 Repository/UseCase 层返回 `Result<T, YukineError>`。
2. 为 TypeScript 网关定义统一的 `ApiError` 类型与 Zod 错误到 HTTP 响应的转换中间件。
3. 引入全局异常处理器（Android 的 `CoroutineExceptionHandler`、Node 的 `unhandledRejection` 钩子）进行集中上报与降级。