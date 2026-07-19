# 曲库去重合并率提升方案

## 问题诊断

经过对现有去重管道的完整分析，发现以下核心瓶颈导致大量应合并的歌曲未被合并：

### 瓶颈 1：置信度天花板过低（最关键）

`RecordingMatchEvaluatorV2.recordingConfidenceCeiling()` 中：
- 当双方都没有强标识符（confirmed provider ID / MBID / fingerprint）时：
  - `hasTitle && hasArtist && hasDuration` → 天花板 = **0.91**
  - 而 SAFE 模式的自动合并阈值 = **0.92**
- 这意味着：**对于绝大多数本地歌曲（没有 ISRC/MBID/AcoustID），即使所有元数据完美匹配，分数也永远无法达到 0.92 的 SAFE 阈值**
- 文件：`core/model/src/main/java/app/yukine/streaming/RecordingMatchEvaluatorV2.kt` 第 767-787 行

### 瓶颈 2：SAFE 模式时长门禁过严

`OfflinePhysicalSourceClusterer` 第 281-283 行：
```kotlin
automaticEligible = dedupPolicy.allowMissingDuration ||
    currentSources.all { it.durationMs > 0L } &&
    candidateSources.all { it.durationMs > 0L }
```
- SAFE 模式 `allowMissingDuration = false`，任何缺少时长的曲目直接丧失自动合并资格

### 瓶颈 3：候选召回依赖精确匹配

`SourceRecordingCandidateGenerator` 的召回策略：
- `EXACT_TITLE_ARTIST`：要求规范化后的 title + artist 完全一致
- `EXACT_TITLE`：要求 title 完全一致 + duration bucket 接近
- 对于艺人名拼写变体（如 "周杰倫" vs "周杰伦"、"Aimer" vs "aimer"）或标题微小差异，trigram/bigram 召回能力有限
- SAFE 模式下 embedding recall 为 SHADOW（仅记录不合并）

### 瓶颈 4：Complete-Link 评估过于保守

两个 Recording 之间的所有 source 对都必须通过阈值。如果某个 source 元数据质量差（如从不同来源导入、标签不完整），会拖低整体分数。

---

## 改进方案

### 改动 1：提升元数据完备时的置信度天花板

**文件**: `core/model/src/main/java/app/yukine/streaming/RecordingMatchEvaluatorV2.kt`

将 `recordingConfidenceCeiling()` 中 `hasTitle && hasArtist && hasDuration` 的天花板从 0.91 提升到 0.95：

```kotlin
return when {
    hasResolvedWorkIdentity && hasDuration -> 1.0
    hasResolvedWorkIdentity -> 0.90
    hasTitle && hasArtist && hasDuration -> 0.95  // 原 0.91
    hasTitle && hasArtist -> 0.94
    hasTitle && hasDuration -> 0.88
    hasTitle -> 0.75
    else -> 0.40
}
```

理由：当 title、artist、duration 三者都具备且匹配良好时，元数据证据已足够充分。0.91 的天花板使得 SAFE 模式（阈值 0.92）永远无法仅凭元数据合并，这不合理。

### 改动 2：SAFE 模式允许缺失时长但降低置信度

**文件**: `feature/data/src/main/java/app/yukine/data/LibraryDedupPolicy.kt`

将 SAFE 模式的 `allowMissingDuration` 改为 `true`，但配合更严格的分数要求：

```kotlin
LibraryDedupMode.SAFE -> LibraryDedupPolicy(
    mode = mode,
    autoMergeMinimumScore = 0.92,
    autoMergeMinimumMargin = 0.08,
    embeddingRecallMode = EmbeddingRecallMode.SHADOW,
    scoringMode = IdentityScoringMode.V5_SHADOW,
    allowMissingDuration = true  // 原 false
)
```

同时确保 `durationSimilarity()` 在 deltaMs == null 时返回的 0.55 已经足够惩罚缺失时长的情况（V4 权重 0.30 * 0.55 = 0.165 的损失），不需要额外的硬门禁。

### 改动 3：增强艺人名模糊召回

**文件**: `feature/data/src/main/java/app/yukine/data/SourceRecordingCandidateGenerator.kt`

在 `Descriptor` 构建和 bucket 分配中增加 artist 的 trigram 索引：

- 新增 `artistTrigrams` 字段到 `Descriptor`
- 在 `generate()` 中增加 `artistTrigramPostings` 索引
- 当 `EXACT_TITLE_ARTIST` 未命中但 `EXACT_TITLE` 命中时，用 artist trigram 相似度作为辅助排序信号

### 改动 4：Complete-Link 评估引入 Source 信任度加权

**文件**: `feature/data/src/main/java/app/yukine/data/OfflinePhysicalSourceClusterer.kt`

当前 `completeLinkEvaluation` 已有 `TRUST_SOURCE_BALANCED_MEDIAN` 聚合策略，但仅在 source 数 > 阈值时启用。改进：

- 降低触发 source-balanced median 的 source 数量门槛
- 对于只有 1-2 个 source 的 recording，使用 `PairAggregate` 的 `robustRecording` 分数而非严格 minimum

### 改动 5：SAFE 模式启用 Embedding Recall（可选，风险较高）

**文件**: `feature/data/src/main/java/app/yukine/data/LibraryDedupPolicy.kt`

将 SAFE 模式的 `embeddingRecallMode` 从 `SHADOW` 改为 `ON`：

```kotlin
LibraryDedupMode.SAFE -> LibraryDedupPolicy(
    ...
    embeddingRecallMode = EmbeddingRecallMode.ON,  // 原 SHADOW
    ...
)
```

注意：这会让 embedding 召回的候选直接参与合并决策。由于 complete-link + 阈值仍然生效，风险可控，但建议先观察 SHADOW 数据再决定。

---

## 实施优先级

| 优先级 | 改动 | 预期效果 | 风险 |
|--------|------|----------|------|
| P0 | 改动 1：天花板 0.91→0.95 | 解除最核心瓶颈，大量完美匹配的歌曲可以合并 | 低 |
| P0 | 改动 2：SAFE 允许缺失时长 | 解除时长为 0 的曲目的合并封锁 | 低 |
| P1 | 改动 3：Artist trigram 召回 | 提升艺人名变体的召回率 | 低 |
| P1 | 改动 4：Complete-Link 加权 | 减少低质量 source 对整体分数的拖累 | 中 |
| P2 | 改动 5：SAFE 启用 Embedding | 提升标题差异较大但语义相似的召回 | 中 |

## 验证计划

- 运行现有 `RecordingIdentityBenchmarkRunner` 确认 recall@20 和 false merge 指标
- 运行 `LibraryDedupPolicyTest`、`SourceRecordingCandidateGeneratorTest`、`RecordingMatchEvaluatorV5Test` 确认无回归
- 运行 `CanonicalLibraryDedupInstrumentedTest` 确认端到端合并行为
- 在真机/模拟器上执行 identity backfill 并观察合并数量变化
