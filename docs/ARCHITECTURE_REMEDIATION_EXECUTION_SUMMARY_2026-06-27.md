# ECHO/YUKINE 架构优化执行摘要

## 目标

把既有重构方案整理成可执行摘要，并继续按绞杀者模式推进，优先收敛 `EchoPlaybackService` 的重职责。

## 当前策略

1. 先保留现有运行路径。
2. 先拆责任边界，不急着做目录级重组。
3. 先把可验证的 owner 抽出来，再逐步替换旧实现。

## 已完成

- `PlaybackLyricsManager` 已接管歌词相关责任。
- `PlaybackNotificationManager` 已接管通知构建与更新。
- `PlaybackMediaLibraryCallback` 已接管媒体库回调适配。
- `PlaybackAudioEffectManager`、`PlaybackQueueStore`、`PlaybackSessionManager`、`PlaybackNotificationChannelOwner` 已落地。
- `PlaybackVisualizationAnalyzer` 已接管波形/频谱快照、缓存进度、暖机延迟与后台生成调度。

## 这一步的改动

- `EchoPlaybackService` 只保留协调与转发。
- 视觉分析相关状态从服务中移除。
- 波形/频谱生成逻辑转移到 `PlaybackVisualizationAnalyzer`。
- 修复了视觉缓存长度读取的实现。

## 结果

- `:app:compileDebugKotlin` 通过。
- `:app:compileDebugJavaWithJavac` 通过。

## 下一步

继续收口 `EchoPlaybackService` 内剩余的大块预缓存与播放状态协调逻辑，然后补契约测试，最后再考虑目录级整理。
