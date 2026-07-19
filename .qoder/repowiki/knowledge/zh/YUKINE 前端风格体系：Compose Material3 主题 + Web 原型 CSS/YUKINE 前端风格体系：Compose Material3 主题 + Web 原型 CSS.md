---
kind: frontend_style
name: YUKINE 前端风格体系：Compose Material3 主题 + Web 原型 CSS
category: frontend_style
scope:
    - '**'
source_files:
    - core/designsystem/src/main/java/app/yukine/ui/EchoTheme.kt
    - web-prototype/src/styles.css
    - web-prototype/src/App.jsx
    - web-prototype/package.json
---

## 1. 系统/方法概述
- Android 端采用 **Jetpack Compose + Material Design 3**，通过 `core/designsystem` 模块集中定义设计令牌（调色板、字体、圆角、阴影）与多模式主题切换，所有 Feature 模块统一通过 `EchoTheme` 包裹获取一致外观。
- Web 原型位于 `web-prototype`，基于 **Vite + React**，使用原生 CSS（无 Tailwind/样式库），以类名驱动布局与视觉，并内置一套“Discrete 0.7”风格的覆盖层用于对比验证。图标来自 `@phosphor-icons/react`。
- 两者共享同一套产品语言：浅蓝主色、大圆角卡片、毛玻璃 Chrome、柔和渐变背景、Noto Sans CJK + Outfit 字体组合，以及一致的间距/字号层级。

## 2. 关键文件与包
- Android 主题与令牌
  - `core/designsystem/src/main/java/app/yukine/ui/EchoTheme.kt` — 主题入口、调色板、字体、形状、涟漪、Haze 玻璃参数、多模式/多强调色解析、Material3 ColorScheme 映射。
  - `core/designsystem/build.gradle` — 声明 Haze、Compose Material3、Compose UI 等依赖。
- Web 原型样式与入口
  - `web-prototype/src/styles.css` — 全局字体、CSS 变量、移动端容器 `.mobile-prototype`、页面骨架、搜索框、继续播放卡片、推荐列表、底部导航、Sheet 弹窗、Toast 动画及 Discrete 风格覆盖。
  - `web-prototype/src/App.jsx` — React 组件树，按 Tab 渲染 Home/Library/Placeholder，挂载 NowBar、BottomNav、Sheet。
  - `web-prototype/package.json` — Vite 6 + React 19 + Phosphor Icons 依赖清单。

## 3. 架构与设计约定
### Android（Compose）
- **单一主题入口**：`EchoTheme { ... }` 内部计算 `EchoPalette` → 生成 `ColorScheme`/`Typography`/`Shapes`，并通过 `LocalIndication`、`LocalRippleConfiguration` 注入强调色涟漪。
- **设计令牌分层**：
  - `EchoPalette`：背景/表面/面板/强调/文本/边框等语义化颜色，提供 ARGB 便捷属性供 Java 侧读取。
  - `EchoTypography`：基于 Noto Sans CJK SC 的 display/headline/title/body/label/caption/small 层级。
  - `EchoShapes`：small(8dp)/medium(14dp)/large(20dp)/full(28dp)/pill(50%) 圆角族。
  - `EchoElevations`：card(6dp)/chrome(10dp) 阴影等级。
- **多模式与强调色**：支持 system/dynamic/dark/light/amoled/contrast/graphite/mist/midnight/forest/ocean/daylight 等模式，以及 blue/teal/rose/violet/amber/emerald/cyan/lime/red/indigo/pine/peach + dynamic_system/background 强调色；运行时可切换并持久化。
- **动态内容色**：`readableContentColor` 保证 WCAG 可读性；`withAccessibleContentColors` 自动修正 onAccent/onSurfaceVariant 等。
- **玻璃效果**：`EchoGlassSpec` + `dev.chrisbanes.haze.HazeState` 控制模糊半径、饱和度、透明度，配合 `LocalEchoCustomBackground` 在自定义壁纸下切换半透明填充。
- **调用约定**：Feature 屏幕直接使用 `MaterialTheme.typography.*` / `MaterialTheme.colorScheme.*`，禁止硬编码颜色值。

### Web 原型（CSS）
- **全局变量与字体**：`:root` 设置 `Outfit` + `Noto Sans SC` 字体栈，`--accent/--ink/--secondary/--separator/--fill` 等 CSS 变量承载品牌色。
- **移动端容器**：`.mobile-prototype` 固定 390×844 视口，配合径向渐变与 `ambient` 光斑营造氛围背景。
- **组件类命名**：`home-header` / `continue-card` / `recent-grid` / `recommend-list` / `now-bar` / `bottom-nav` / `sheet-backdrop` / `toast` 等，全部小写连字符，避免 BEM 前缀，保持扁平可读。
- **Discrete 风格覆盖**：`.discrete-revision` 作为根修饰类，通过大量子选择器覆写默认样式，实现“原生内容优先、玻璃仅用于 Chrome”的对比版本。
- **响应式与无障碍**：`prefers-reduced-motion` 禁用动画；小屏媒体查询调整 body 居中策略。

## 4. 开发者应遵循的规则
- **Android**
  - 所有 Compose 界面必须被 `EchoTheme` 包裹；颜色/字体/圆角一律走 `MaterialTheme.*` 或 `Echo*` 对象，禁止硬编码十六进制颜色。
  - 新增主题模式时，先在 `EchoTheme` 中补充 `paletteForMode` 分支与 `modeOptions()` 枚举，再暴露给 Settings 模块。
  - 需要毛玻璃效果时使用 `HazeState` + `EchoGlassSpec`，并通过 `LocalEchoGlassEnabled/Opacity/BlurRadius` 控制开关与参数。
  - 与 Java/Kotlin 非 Compose 代码交互时，使用 `EchoTheme.backgroundArgb()/surfaceArgb()/textArgb()` 等 ARGB 辅助方法。
- **Web 原型**
  - 新增 UI 元素时复用现有类名范式（如 `section`/`section-title`/`tile-grid`），并在 `styles.css` 中追加对应规则，保持与 Discrete 覆盖层兼容。
  - 图标统一从 `@phosphor-icons/react` 引入，尺寸遵循现有 15–22px 范围，active 态用 `weight="fill"` 区分。
  - 如需扩展配色，先在 `:root` 或 `.discrete-revision` 中声明 CSS 变量，再在组件中选择性覆盖，避免散落 `#xxxxxx` 常量。

## 5. 参考证据
- Android 主题核心：`core/designsystem/src/main/java/app/yukine/ui/EchoTheme.kt`
- Web 原型样式：`web-prototype/src/styles.css`、`web-prototype/src/App.jsx`、`web-prototype/package.json`
