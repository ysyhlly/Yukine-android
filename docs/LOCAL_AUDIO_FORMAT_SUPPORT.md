# 本地音频格式支持

Echo 对 MediaStore 扫描和 SAF 文件/文件夹导入使用同一份格式能力表。扩展名、文件 MIME、容器内首个音频轨 MIME 和设备解码器能力共同决定是否写入曲库；元数据缺失本身不会导致文件被拒绝。

## 支持矩阵

| 分类 | 格式 | 导入与播放规则 |
| --- | --- | --- |
| 稳定支持 | MP3、AAC/ADTS、M4A/音频型 MP4（AAC）、FLAC、PCM WAV、Ogg Vorbis、Ogg Opus | 容器与编码匹配，且设备存在所需解码器时导入并允许播放。 |
| 条件支持 | DSF、未使用 DST 压缩的 DFF | 保留现有 USB Exclusive/DoP 门控；普通系统输出不承诺支持。 |
| 不支持 | ALAC、WMA、APE、WavPack、TTA、AIFF、AMR、MIDI、DST 压缩 DFF | 新导入时跳过并计入聚合摘要。 |
| 不支持 | 加密音乐缓存、未知音频格式 | 新导入时跳过；普通非音频文件直接忽略，不计入摘要。 |

M4A/MP4 只接受 AAC 音频轨且不得含视频轨；WAV 只接受 PCM；Ogg 容器只接受 Vorbis 或 Opus。已知不支持的扩展名优先于文件提供方上报的 `audio/*` MIME，避免伪造或错误 MIME 绕过能力表。

## 旧曲库条目

升级不会删除 Room 中已有的旧格式条目，也不会破坏收藏、歌单、队列或历史关联。Library、Search、Collections、Playlist 和 Queue 会显示“不支持格式”，单曲播放动作被禁用，其他管理动作继续可用。DSF/DFF 不受通用禁播逻辑影响，仍由 USB/DSD 输出策略给出具体条件提示。

## 失败与恢复

已知不支持格式在创建 MediaSource 前被拦截。旧队列或历史中的未知条目若在 Media3 运行时报告容器/解码格式不支持，会映射为 `FORMAT_UNSUPPORTED`，显示明确提示，并按现有恢复策略推进到下一首。网络流媒体行为不受本策略影响。

本次不引入 FFmpeg、libVLC 或第二套播放器栈。设备厂商的解码器差异通过缓存的 `MediaCodecList` 能力探测处理。

## 自动化验证

- `LocalAudioFormatPolicyTest`：扩展名/MIME 别名、伪造 MIME、容器编码约束、DSF/DFF、未知格式和摘要聚合。
- `LocalAudioCandidateProbeTest`：API 23/27/33 下的 SAF/MediaStore 探测、设备解码器缺失和容器失败。
- `PlaybackMediaSourceProviderTest`、`PlaybackErrorRecoveryManagerTest`：播放预检、Media3 错误映射和队列恢复。
- `TrackRowStateFactoryTest`：Library、Playlist、Queue 的统一禁播状态以及 DSF 门控豁免。

真机验收仍需使用无版权短音频样本验证“有解码器则播放并推进进度、无解码器则导入时跳过并汇总”，结果记录到 [播放服务稳定性验收矩阵](PLAYBACK_SERVICE_STABILITY_MATRIX.md)。
