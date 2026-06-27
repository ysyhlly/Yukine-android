# ECHO/YUKINE 架构优化执行计划（2026-06-27）

## 目标

把现有重构方案整理成可执行版本，并按“绞杀者模式（Strangler Fig Pattern）”逐步落地。

核心原则：

1. 不推倒重来，先并行保留旧实现与新边界。
2. 先收窄责任，再搬文件。
3. 先做可验证的最小切片，再扩大范围。
4. 旧路径只在新路径可用并且有测试保护后再删除。

## 当前基线

当前仓库里最明显的两个大热点仍然是：

- `MainActivity.java`：`3339` 行
- `EchoPlaybackService.java`：`3893` 行

这说明宿主装配和播放服务都还没有完成拆边界。
本次执行不从大规模重命名或整包搬迁开始，而是从最薄、最清晰的边界切入。

## 归纳后的主线

### 1. 宿主收缩

把 `MainActivity` 逐步收回到：

- 生命周期宿主
- Compose/导航入口
- 平台能力委托点
- 只保留必须的装配逻辑

### 2. 播放服务拆边界

把 `EchoPlaybackService` 内部职责拆成若干可独立演进的 owner：

- `SessionManager`
- `QueueManager`
- `NotificationManager`
- `AudioEffectManager`
- `LyricsPublisher`
- `SpectrumAnalyzer`

### 3. 控制器收口

把只做转发的 `Controller` / `Bindings` 逐步替换掉。
前提是：新的直达路径已经存在，而且 contract test 已经覆盖。

### 4. 边界稳定后再整理目录

等 owner 边界稳定后，再做：

- `core/`、`data/`、`domain/`、`playback/`、`ui/`、`streaming/` 结构整理
- 命名统一
- Kotlin/Java 风格收口
- 质量门禁补强

## 本轮先执行什么

第一刀先落在播放边界的“契约层”，不急着改运行时行为。

原因很简单：

- 播放服务是当前最肥的 owner 之一
- 这里的职责拆分最适合先做“接口边界”而不是“整体搬家”
- 这样可以先把后续迁移的落点固定住

### 本轮起步切片

1. 在 `app.yukine.playback.manager` 下建立播放边界契约。
2. 后续再把 `EchoPlaybackService` 的 queue / session / notification / effect / lyric / spectrum 逻辑逐个搬出去。
3. 只要新 owner 还没接管完成，旧实现继续保留。

## 验收方式

每个切片至少满足四件事：

1. 清楚说明哪个 owner 变薄了
2. 清楚说明哪条事件路径变短了
3. 有一个对应的 focused test 或 contract test
4. 编译能回得来

## 任务顺序

1. 先稳住播放边界
2. 再收宿主和平台能力
3. 再清转发层
4. 再补 DI、数据边界和目录结构

## 备注

`docs/MVVM_MIGRATION_HANDOFF.md` 继续作为迁移手册。
这份文件只作为当前执行顺序和切片说明。
