package app.yukine.playback.manager

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

/**
 * 播放服务拆分的边界契约。
 *
 * 先把责任边界钉住，后续再从 `EchoPlaybackService` 里逐段搬实现。
 * 当前文件只做契约占位，不改变运行时行为。
 */
internal interface SessionManager

internal interface QueueManager

internal interface NotificationManager

internal interface AudioEffectManager

internal interface LyricsPublisher {
    fun bind()
    fun release()
    fun setStatusBarLyricsEnabled(enabled: Boolean)
    fun syncFloatingLyricsPlaybackState(snapshot: PlaybackStateSnapshot)
    fun notificationLyricText(track: Track?): String
    fun sanitizeNotificationLyric(value: String?): String
}

internal interface SpectrumAnalyzer

internal interface VisualizationAnalyzer

internal interface VisualizationCacheManager
