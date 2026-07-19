---
kind: dependency_management
name: YUKINE 多语言依赖统一治理（Gradle Version Catalog + npm lockfile）
category: dependency_management
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - settings.gradle
    - build.gradle
    - gradle.properties
    - app/build.gradle
    - core/model/build.gradle
    - feature/data/build.gradle
    - metadata-gateway/package.json
    - web-prototype/package.json
---

## 1. 使用的系统与工具
- Android/Java/Kotlin：Gradle 8.x + Kotlin 2.2，采用 **Version Catalog**（`gradle/libs.versions.toml`）集中声明所有第三方库与插件版本，并通过 `alias(libs.plugins.*)` / `libs.*` 在模块中引用。
- Node.js（元数据网关、Web 原型）：npm + TypeScript，使用 `package.json` + `package-lock.json` 锁定依赖树；Cloudflare Worker 通过 Wrangler 构建。
- 仓库未使用私有 Maven/NPM 代理或 vendoring，全部依赖来自 Google/Maven Central/npm 公共源。

## 2. 关键文件与位置
- Gradle 版本目录与仓库策略：
  - `gradle/libs.versions.toml` — 全局 `[versions]`、`[libraries]`、`[plugins]` 定义
  - `settings.gradle` — `dependencyResolutionManagement.repositoriesMode.set(FAIL_ON_PROJECT_REPOS)` 禁止子模块自行声明仓库
  - `build.gradle`（根）— 仅注册 plugins，不引入任何依赖
  - `gradle.properties` — 启用并行、缓存、配置缓存、AndroidX、非传递 RClass
- Android 模块 build 脚本（示例）：
  - `app/build.gradle`、`core/model/build.gradle`、`feature/data/build.gradle` 等，均通过 `alias(libs.plugins.*)` 和 `libs.*` 引用依赖
- Node.js 依赖清单：
  - `metadata-gateway/package.json` + `package-lock.json`
  - `web-prototype/package.json` + `package-lock.json`

## 3. 架构与约定
### 3.1 Android 侧（Gradle Version Catalog）
- **单一真相源**：所有 AGP、Kotlin、Hilt、Compose BOM、Media3、Room、OkHttp、Junit、Robolectric 等版本号集中在 `gradle/libs.versions.toml` 的 `[versions]` 段，`[libraries]` 中以 `version.ref = xxx` 引用，避免散落在各模块。
- **BOM 管理 Compose**：通过 `platform(libs.androidx.compose.bom)` 在 app 与测试作用域引入，Compose 组件不再单独写版本。
- **插件别名化**：根 `build.gradle` 用 `apply false` 注册插件，子模块通过 `alias(libs.plugins.android.application)` 等引用，保证插件版本与 catalog 一致。
- **仓库白名单**：`settings.gradle` 中只允许 `google()`、`mavenCentral()`，并设置 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`，子模块无法私自添加仓库，防止“幽灵依赖”。
- **模块化依赖边界**：`core:*` 为共享层，`feature:*` 之间按功能解耦，`app` 作为装配层聚合所有模块；`api project(...)` 仅在真正需要暴露 API 时使用（如 `feature:data` 对 `core:model` 使用 `api`）。
- **NDK/CMake 集成**：`app/build.gradle` 显式声明 NDK ABI 过滤与 CMake 路径，原生依赖随构建拉取，不纳入版本目录。

### 3.2 Node.js 侧（npm + lockfile）
- `metadata-gateway/package.json` 将开发期依赖（wrangler、typescript、tsx、@cloudflare/workers-types）放入 `devDependencies`，运行时无额外依赖，便于 Cloudflare Workers 部署。
- `web-prototype/package.json` 固定 React 19.2.0、Vite 6.4.2、@vitejs/plugin-react 5.0.4，配合 `package-lock.json` 确保本地与 CI 一致性。
- 两个子项目各自维护独立 lockfile，互不影响。

## 4. 开发者应遵循的规则
1. **新增/升级 Android 依赖必须改 `gradle/libs.versions.toml`**，并在模块中通过 `libs.xxx` 引用；禁止在任意 `build.gradle` 中硬编码版本号。
2. **不要在任何子模块的 `build.gradle` 中添加 `repositories {}` 块**，否则会被 `FAIL_ON_PROJECT_REPOS` 拒绝；如需私有源，请在 `settings.gradle` 的 `dependencyResolutionManagement.repositories` 中统一添加。
3. **Compose 相关依赖一律通过 BOM 引入**，不要在模块里再写 `implementation 'androidx.compose.ui:ui:...'` 带版本号的行。
4. **插件版本统一由 catalog 管理**，新增插件先在根 `build.gradle` 注册 `apply false`，再在子模块按需 `alias(libs.plugins.xxx) apply true`。
5. **Node.js 子项目升级依赖时提交 `package-lock.json`**，避免团队间依赖树不一致导致构建差异。
6. **敏感信息（签名、AppId、Gateway URL）通过环境变量或 gradle.properties 注入**，不要写入源码或锁文件（已在 `app/build.gradle` 中体现模式，其他模块沿用即可）。