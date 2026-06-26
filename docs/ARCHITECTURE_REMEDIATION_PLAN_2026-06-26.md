# Yukine 架构整治计划（复核版）

日期：2026-06-26  
适用范围：`app/src/main/java/app/yukine` 当前工作树  
用途：作为后续架构整治、MVVM 迁移、依赖注入补齐、目录重组和测试护栏的执行参考

---

## 1. 文档定位

这份文档不是替代现有迁移文档，而是对外部审查结论做一次当前工作树复核后，产出的执行计划表。

与现有文档的关系：

- `docs/MVVM_MIGRATION_HANDOFF.md`
  - 仍是主迁移手册，记录长期目标、边界规则和阶段性完成情况。
- `docs/MVVM_MIGRATION_PROGRESS_2026-06-25.md`
  - 记录最近一轮迁移结果和验证情况。
- `docs/STREAMING_VIEWMODEL_SPLIT_PLAN.md`
  - 只负责 streaming 方向的专项拆分。
- 本文档
  - 负责把“审查问题 -> 当前事实 -> 可执行计划 -> 验收方式”整理成一份总表。

---

## 2. 复核结论

下列结论基于 2026-06-26 对当前仓库的本地复核，不直接照抄旧审查。

### 2.1 已复核的事实

| 项目 | 复核结果 | 备注 |
|---|---:|---|
| `app/src/main/java/app/yukine` ??????? | 288 | ??????????? |
| `app/src/main/java/app/yukine` ?????? | 160 | ????????? |
| `*Controller` ??? | 56 | ???? 58??????? |
| `*Bindings` 文件数 | 3 | 说明桥接层仍然很多 |
| `*ViewModel` 文件数 | 18 | ViewModel 已明显扩张 |
| `app/src/test/java/app/yukine` ????? | 137 | ???? 165 ?????????? |
| `MainActivity.java` ?? | 3663 | ????????? |
| `EchoPlaybackService.java` 行数 | 3893 | 播放边界体量很大 |
| `StreamingViewModel.kt` 行数 | 1932 | 仍是超大 ViewModel |
| `SettingsViewModel.kt` 行数 | 771 | 已迁出不少逻辑，但仍偏大 |
| Hilt Module 文件数 | 2 | `DashboardDataModule.kt`、`StreamingDataModule.kt` |
| `@HiltViewModel` 数量 | 5 | Hilt 已接入，但覆盖不均匀 |

### 2.2 旧审查中需要修正的点

1. “依赖注入配置缺失”不完全准确。
   当前仓库已经有 Hilt、2 个 Module、5 个 `@HiltViewModel`。问题不是“没有 DI”，而是“DI 覆盖不完整，MainActivity 仍大量手动装配”。

2. “MainActivity 只有字段注入、ViewModel 手动管理”需要更精确。
   当前 `MainActivity` 里既有 5 个 `@Inject` 字段，也有大量 `new XxxController(...)`、`new XxxUseCase(...)`、`new XxxBindings(...)`，同时通过 `new ViewModelProvider(this).get(...)` 手动获取 11+ 个 ViewModel。核心问题是宿主装配过重，不是单一技术点。

3. “Repository 没有响应式流”需要收窄表述。
   当前很多 ViewModel 已经用 `StateFlow` 持有 UI 状态，但 `MusicLibraryRepository.java` 仍主要暴露同步 `List` / imperative load-save API，响应式数据源没有真正下沉到数据层。

4. “立即按功能域拆包”方向正确，但不应作为第一步。
   纯目录搬迁会制造大量 import churn。如果 owner 边界还没收稳，先搬文件只会增加 review 成本。更合理顺序是：先收边界和宿主职责，再按稳定 owner 移包。

5. 当前工作树处于迁移中，不是静态旧项目。
   仓库已有大量未提交和未跟踪改动，且 `docs/MVVM_MIGRATION_HANDOFF.md` 已明确把外部审查作为优先级输入，而不是无条件事实源。

---

## 3. 当前核心问题

| 编号 | 问题 | 当前证据 | 直接风险 |
|---|---|---|---|
| A1 | 根目录文件爆炸 | 根目录 210 个文件 | 文件查找困难，owner 不清晰 |
| A2 | 宿主过重 | `MainActivity.java` 2299 行，手动装配大量对象 | 新功能继续堆进 Activity |
| A3 | ????? | 56 ? Controller + 3 ? Bindings | ???????????? |
| A4 | DI 覆盖不完整 | 只有 2 个 Module，`MainActivity` 仍手动 `new` 大量依赖 | 替换实现困难，依赖图分散 |
| A5 | 数据层同步接口为主 | `MusicLibraryRepository.java` 主要暴露 `List`/同步 load/save | UI 更新依赖手动刷新和桥接 |
| A6 | 超大状态 owner | `StreamingViewModel.kt` 1932 行，`SettingsViewModel.kt` 771 行 | feature 边界继续模糊 |
| A7 | 迁移过程中的脏工作树 | 当前有大量 `M/D/??` 改动 | 不适合做无边界的大搬迁 |

---

## 4. 执行原则

1. 先做边界收敛，再做目录整理。
2. 先降低 `MainActivity` 装配密度，再补 DI。
3. 先把数据 owner 稳住，再推进数据层响应式改造。
4. 每个阶段都必须带最小验证：至少一条快速检查和一条信心检查。
5. 不在当前脏工作树里做“纯重命名 + 纯搬家”的大提交。

---

## 5. 全部计划表

| ID | 优先级 | 工作流 | 目标 | 主要文件/边界 | 前置依赖 | 交付物 | 快速检查 | 信心检查 |
|---|---|---|---|---|---|---|---|---|
| P0-1 | P0 | 基线冻结 | 明确当前编译、测试、脏工作树状态 | `README.md`、`docs/*`、Gradle、`git status` | 无 | 一份基线记录和切片策略 | `git status --short` 已记录 | `compileDebugKotlin + compileDebugJavaWithJavac` |
| P0-2 | P0 | 文档对齐 | 明确本文档与 `MVVM_MIGRATION_HANDOFF.md` 的职责边界 | `docs/MVVM_MIGRATION_HANDOFF.md`、本文档 | P0-1 | 文档引用关系稳定 | 文档链接可追踪 | 后续阶段更新有统一入口 |
| P1-1 | P1 | 宿主收缩 | 把 `MainActivity` 继续缩成 shell + lifecycle + launcher host | `MainActivity.java`、`EchoApp.kt`、`navigation/*` | P0-1 | 明确哪些能力必须离开 Activity | `MainActivityArchitectureContractTest` 更新 | 定向编译 + 契约测试 |
| P1-2 | P1 | 平台 owner 补齐 | picker、dialog、backup、share、permission 全部有明确 owner | `*PickerController`、`*DialogController`、`SettingsEffectBindings.kt` | P1-1 | 平台边界清单和 owner 清单 | Activity 不再新增平台分支 | 定向单测覆盖平台 owner |
| P1-3 | P1 | 桥接层清理 | 删除只转发一层的 Controller/Bindings | `*Controller.kt`、`*Bindings.kt`、各 `ViewModel` | P1-1, P1-2 | 事件路径缩短，回调接口收窄 | 新增事件不需要穿 4 层 | 相关单测和契约测试通过 |
| P1-4 | P1 | `Settings` 收口 | 完成设置页 owner 收敛，避免回流到宿主 | `SettingsViewModel.kt`、`SettingsPageStateBuilder.kt`、`SettingsRuntimeApplier.kt` | P1-2, P1-3 | 设置链路只经 `ViewModel -> effect/runtime/persistence` | 不新增 settings gateway 回流 | `SettingsViewModelTest` + 契约测试 |
| P1-5 | P1 | `Streaming` 收口 | 继续拆 `StreamingViewModel` 的搜索、认证、歌单、推荐、播放编排 | `StreamingViewModel.kt`、`StreamingRecommendationViewModel.kt`、`StreamingPlaylistController.kt` | P1-3 | 按 feature 拆小 owner | 行数和职责下降 | `Streaming*Test` + 编译 |
| P1-6 | P1 | 播放边界统一 | 推进 `PlaybackController` 真正成为统一播放入口 | `playback/PlaybackController.kt`、`PlaybackServiceController.kt`、`EchoPlaybackService.java` | P1-1 | UI 与 Service 的依赖方向更清晰 | `ViewModel` 不直接碰具体 Service | 播放相关单测 + smoke build |
| P1-7 | P1 | DI 补齐 | 把核心 use case/controller/executor 的手动 `new` 收进 Hilt | `di/*`、`MainActivity.java`、相关 owner | P1-1, P1-3 | DI 图更完整，装配减少 | `MainActivity` 的 `new` 数量下降 | 编译 + 关键注入路径测试 |
| P1-8 | P1 | 数据层拆分 | 从 `MusicLibraryRepository` 拆出 settings/library/playlist/streaming/playback 边界 | `data/MusicLibraryRepository.java`、`EchoDatabaseHelper.java` | P1-7 | 新接口边界，旧仓库逐步变薄 | 调用方开始依赖窄接口 | 编译 + 受影响用例测试 |
| P2-1 | P2 | 响应式数据源 | 把关键读取路径从同步 `List` 提升到 `Flow`/可观察接口 | `data/*Repository*`、ViewModel | P1-8 | 关键列表页不再靠手动刷新驱动 | 新读路径有 observe API | 定向 ViewModel 测试 |
| P2-2 | P2 | 包结构重组 | 在边界稳定后，把 root 目录文件迁回 feature 域 | `app/src/main/java/app/yukine/*` | P1-3, P1-5, P1-8 | 根目录显著瘦身 | root 文件数下降 | 全量编译 + 搜索路径复核 |
| P2-3 | P2 | 命名与文案规范 | 统一后缀、文案入口、事件命名 | `AppLanguage.java`、`*Controller`、`*Bindings`、`*ViewModel` | P2-2 | 一致的命名规范和文案入口 | 新代码符合规则 | 定向测试 + grep 复核 |
| P2-4 | P2 | Kotlin 风格收口 | 不可变集合、`buildList`、减少 Java 风格样板 | Kotlin 源文件 | P1-8 | 代码风格一致 | review 中无新增冗余集合 | 编译 |
| P2-5 | P2 | 质量门禁 | 加强 Detekt/ktlint/契约测试/回归脚本 | Gradle、测试目录、`docs/RELEASE_EXPERIENCE_CHECKLIST.md` | 全部前置 | 明确门禁脚本和失败标准 | 本地可单独执行 | 定向测试 + `:app:check` |

---

## 6. 推荐执行顺序

### 第一批：必须先做

1. `P0-1` 基线冻结
2. `P1-1` 宿主收缩
3. `P1-2` 平台 owner 补齐
4. `P1-3` 桥接层清理

原因：

- 这是后续一切拆分的前提。
- 如果不先收宿主和桥接层，后面的 DI、拆仓库、移目录都会反复返工。

### 第二批：收稳定边界

1. `P1-4` Settings
2. `P1-5` Streaming
3. `P1-6` Playback
4. `P1-7` DI 补齐

原因：

- 这几条是当前最明显的高耦合热点。
- 它们稳定后，数据层和目录重组才不会搬到一半又改 owner。

### 第三批：做结构性收尾

1. `P1-8` 数据层拆分
2. `P2-1` 响应式数据源
3. `P2-2` 包结构重组
4. `P2-3` 到 `P2-5` 规范与门禁

原因：

- 这些工作更适合在 owner 已稳定后进行。
- 其中“包结构重组”应视为收尾，不建议作为开场动作。

---

## 7. 每阶段统一验收标准

每个阶段至少满足以下四项：

1. 变更目标明确
   - 能用一句话说清“哪个 owner 变薄了，哪个 owner 接手了”。
2. 事件路径缩短
   - 至少减少一层无策略转发。
3. 测试有覆盖
   - 至少补一条与本阶段改动直接相关的单测或契约测试。
4. 编译可恢复
   - `:app:compileDebugKotlin :app:compileDebugJavaWithJavac` 通过。

对高风险阶段再增加：

- `:app:testDebugUnitTest`
- 相关设备/运行时 smoke 验证

---

## 8. 当前最合适的下一步

当前最适合立刻开始的是：

1. 以 `MainActivity.java` 为中心，列出仍然“手动 new + 手动回调装配”的 owner 清单。
2. 从其中优先挑平台能力 owner 和纯转发桥接层，按最小切片继续迁出。
3. 迁出后更新 `MainActivityArchitectureContractTest.java`，把“不得回流到 Activity”的约束固化。

不建议立刻开始：

- 大规模包移动
- 单纯重命名
- 在当前脏工作树上做机械性全仓整理

---

## 9. 复核备注

- 本计划明确采用“复核优先”原则：外部审查结论只作为优先级输入，不直接当作当前事实。
- 当前仓库已处于持续迁移中，很多问题是“仍未完成”，不是“完全没有做”。
- 后续如果根目录文件数、`MainActivity` 行数、`StreamingViewModel` 行数明显变化，本文档应同步更新对应数字。

