package app.yukine.playback.manager

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

interface SessionManager

interface QueueManager

interface NotificationManager

interface AudioEffectManager

interface LyricsPublisher {
    fun bind()
    fun release()
    fun setStatusBarLyricsEnabled(enabled: Boolean)
    fun setSystemMediaLyricsTitleEnabled(enabled: Boolean)
    fun onAppVisibilityChanged()
    fun syncFloatingLyricsPlaybackState(snapshot: PlaybackStateSnapshot)
    fun notificationLyricText(track: Track?): String
    fun systemMediaTitleLyricText(track: Track?): String
    fun sanitizeNotificationLyric(value: String?): String
}

interface SpectrumAnalyzer

interface VisualizationAnalyzer

interface VisualizationCacheManager
