---
kind: build_system
name: Gradle 多模块构建与 CI/发布流水线
category: build_system
scope:
    - '**'
source_files:
    - build.gradle
    - settings.gradle
    - gradle.properties
    - gradle/libs.versions.toml
    - app/build.gradle
    - .github/workflows/android.yml
    - scripts/verify-release.ps1
    - metadata-gateway/Dockerfile
    - web-prototype/package.json
---

## 1. 构建系统与工具链
- Android 应用使用 Android Gradle Plugin (AGP) 8.10.1 + Kotlin 2.2.21，通过 gradle/libs.versions.toml 集中管理所有插件与依赖版本。
- 根工程采用 settings.gradle 声明式多模块：app（主壳）+ core:*（model/common/designsystem）+ feature:*（streaming/data/playback/player-ui/library-ui/settings-ui/streaming-ui/navigation），由 app 装配。
- 启用 Configuration Cache、Parallel Build、Build Cache、nonTransitiveRClass，JVM 堆 3G，Kotlin Daemon 2G，以加速增量编译。
- 支持 CMake NDK 原生库（Chromaprint），ABI 限定为 arm64-v8a, x86_64，C++ 标准 C++17，优化 -O3。
- Room schema 生成到 app/schemas；Hilt/KSP 注解处理器统一在 libs.versions.toml 中声明。

## 2. 关键文件与位置
- 根构建配置：build.gradle、settings.gradle、gradle.properties、gradle/libs.versions.toml
- 应用模块：app/build.gradle（签名、buildConfigField、lint baseline、ProGuard、NDK/CMake）
- 各子模块：core/*/build.gradle、feature/*/build.gradle（仅声明依赖，无重复 AGP 配置）
- CI：.github/workflows/android.yml（单元测试 + lint + assembleDebug）
- 本地发布门禁：scripts/verify-release.ps1（打包 Debug/Release/AAB、安装、权限授予、logcat 断言、截图归档）
- 元数据网关容器化：metadata-gateway/Dockerfile（Node 24 multi-stage，暴露 8787，健康检查 /health）
- Web 原型：web-prototype/package.json（Vite 6 + React 19，独立 npm 构建）

## 3. 架构与约定
- 版本集中化：所有第三方库与插件版本集中在 libs.versions.toml，模块内仅引用 libs.xxx，禁止硬编码版本号。
- 签名与密钥：Release 签名通过环境变量 ECHO_RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD 注入，CI 或本地脚本提供；默认不签名，便于调试。
- BuildConfig 注入：QQ/微信 AppId、Metadata Gateway URL、版本号等通过 findProperty(...) 或 System.getenv(...) 注入，支持不同环境覆盖。
- 版本码策略：versionCode = max(GITHUB_RUN_NUMBER, ECHO_VERSION_CODE, 8)，保证 CI 递增且不低于基线 8。
- Lint 治理：app/lint-baseline.xml 记录历史问题，新违规直接阻断构建。
- 测试分层：test/（Robolectric + JUnit）、androidTest/（Instrumentation）、connectedDebugAndroidTest 一键跑真机。
- Web 与网关独立构建：web-prototype 用 Vite，metadata-gateway 用 Node.js + Wrangler，均通过各自 package.json 管理，不与 Gradle 耦合。

## 4. 开发者应遵循的规则
1. 新增依赖一律写入 gradle/libs.versions.toml，模块内只写 implementation libs.xxx，禁止在 build.gradle 里写死版本。
2. 不要修改根 build.gradle 的 pluginManagement/repositories；仓库源变更应在 settings.gradle 的 dependencyResolutionManagement 中统一调整。
3. Release 签名凭据必须通过环境变量传入，不得提交到代码库；本地快速验证可用 scripts/verify-release.ps1 -CreateSmokeKeystore。
4. 新增 feature 模块时：在 settings.gradle 中 include，并在 app/build.gradle 添加 implementation project(":feature:xxx")，保持单点装配。
5. Room 迁移：更新 Schema 后需同步 feature/data/schemas 目录下的 JSON 文件，确保向后兼容。
6. NDK/CMake 变更：仅在 app/build.gradle 的 externalNativeBuild 和 ndk.abiFilters 中调整 ABI，避免引入未测试架构。
7. CI 门禁：提交前至少运行 ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug，确保与 GitHub Actions 一致。