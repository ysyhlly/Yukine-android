---
kind: configuration_system
name: YUKINE 多运行时配置体系：Gradle BuildConfig + 环境变量 + Wrangler 变量
category: configuration_system
scope:
    - '**'
source_files:
    - app/build.gradle
    - gradle.properties
    - app/src/main/java/app/yukine/IdentityEnhancementWorker.kt
    - app/src/main/java/app/yukine/NativeMusicShareManager.kt
    - app/src/main/java/app/yukine/SettingsContextProvider.kt
    - app/src/main/java/app/yukine/SettingsModule.kt
    - app/src/main/java/app/yukine/IdentityEnhancementProxyDialogController.java
    - metadata-gateway/src/node/config.ts
    - metadata-gateway/wrangler.toml
---

## 1. 系统概览
本仓库采用「构建期注入 + 运行期环境变量」的双层配置模型，覆盖 Android 应用、Node.js/Cloudflare Worker 元数据网关以及 Web 原型三个子项目。Android 侧通过 Gradle buildConfigField 将外部属性编译进 BuildConfig；后端以 process.env 读取运行时参数，并通过 wrangler.toml 的 [vars] 提供默认值与 secret 管理。

## 2. 关键文件与包
- Android 应用配置入口
  - app/build.gradle：集中声明所有 buildConfigField、签名凭据、版本号来源（优先 CI 环境变量，回退到本地 gradle.properties 或硬编码默认值）
  - gradle.properties：全局 Gradle/JVM 开关，不含业务配置
  - Kotlin/Java 使用方：IdentityEnhancementWorker.kt、NativeMusicShareManager.kt、SettingsContextProvider.kt、SettingsModule.kt、IdentityEnhancementProxyDialogController.java
- 元数据网关配置
  - metadata-gateway/src/node/config.ts：loadNodeGatewayConfig() 从 process.env 解析并校验数值范围，定义 NodeGatewayConfig 接口
  - metadata-gateway/wrangler.toml：Cloudflare Worker 部署配置，[vars] 提供默认 UA，secret 通过 wrangler secret put 注入
- Web 原型（无运行时配置，仅 Vite 开发服务器）
  - web-prototype/vite.config.mjs、web-prototype/package.json：不包含业务配置项

## 3. 架构与约定
### 3.1 配置分层与优先级
- 构建期常量：第三方 AppID、网关 URL、版本号，来源为 Gradle findProperty -> System.getenv -> 硬编码默认值
- 运行期环境变量：服务监听地址、缓存 TTL、上游超时、API Key，来源为 Node process.env / Cloudflare env.*
- 平台默认值：兜底值保证离线可运行，如 127.0.0.1:8787、https://metadata.ysyhly.cn/

### 3.2 Android 侧规则
- 所有对外部依赖的敏感标识（QQ/微信 AppID、网关 URL、签名密钥）一律通过 buildConfigField 注入，禁止在源码中硬编码
- 版本信息遵循「CI 显式 > 环境变量 > 默认值」的单调递增策略，versionCode 取 Math.max(requested, 8) 防止回退
- 签名凭据仅在同时提供四个字段时启用 release signing，避免误用 debug 签名发布

### 3.3 后端侧规则
- 所有数值型配置统一经 integer(value, min, max, fallback) 校验，越界即回退默认值，保障本地开发与 CI 环境一致行为
- Secret（如 ACOUSTID_API_KEY）走 wrangler secret，不在源码或 wrangler.toml 的 [vars] 中明文出现

## 4. 开发者应遵守的规则
1. 新增外部依赖标识：在 app/build.gradle 增加 def xxx = findProperty(...) ?: System.getenv(...) ?: ""，并在 defaultConfig.buildConfigField 暴露，然后在对应 Kotlin/Java 类中通过 BuildConfig.XXX 读取
2. 新增服务端配置：在 metadata-gateway/src/node/config.ts 的 NodeGatewayConfig 接口与 loadNodeGatewayConfig 中添加字段，保持最小/最大/默认值三元组完整
3. Secret 管理：仅允许通过 wrangler secret put 注入，不得出现在任何提交物中
4. 默认值语义：所有 fallback 必须保证本地开发可直接启动，不依赖额外配置文件
5. 版本与 ABI：minSdk/targetSdk/compileSdk 与 NDK ABI 过滤集中在 app/build.gradle 的 defaultConfig，修改需同步更新文档与 CI 矩阵