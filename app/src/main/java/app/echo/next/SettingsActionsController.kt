package app.echo.next

import app.echo.next.data.MusicLibraryRepository
import app.echo.next.ui.EchoTheme

internal class SettingsActionsController(
    private val repository: MusicLibraryRepository,
    private val executors: MainExecutors,
    private val listener: Listener
) {
    interface Listener {
        fun onThemeModeApplied(mode: String)

        fun onAccentModeApplied(accent: String)

        fun onLanguageModeApplied(languageMode: String)

        fun onPlaybackSpeedApplied(speed: Float)

        fun onAppVolumeApplied(volume: Float)

        fun onStreamingAudioQualityApplied(quality: String)

        fun onConcurrentPlaybackEnabledApplied(enabled: Boolean)

        fun onOnlineLyricsEnabledApplied(enabled: Boolean)

        fun onLyricsOffsetApplied(offsetMs: Long)
    }

    fun applyThemeMode(nextMode: String) {
        val mode = EchoTheme.normalizeMode(nextMode)
        EchoTheme.setMode(mode)
        listener.onThemeModeApplied(mode)
        executors.io { repository.saveThemeMode(mode) }
    }

    fun applyAccentMode(nextAccent: String) {
        val accent = EchoTheme.normalizeAccent(nextAccent)
        EchoTheme.setAccent(accent)
        listener.onAccentModeApplied(accent)
        executors.io { repository.saveAccentMode(accent) }
    }

    fun applyLanguageMode(nextLanguageMode: String) {
        val languageMode = AppLanguage.normalizeMode(nextLanguageMode)
        listener.onLanguageModeApplied(languageMode)
        executors.io { repository.saveLanguageMode(languageMode) }
    }

    fun applyPlaybackSpeed(speed: Float) {
        val normalizedSpeed = normalizePlaybackSpeed(speed)
        listener.onPlaybackSpeedApplied(normalizedSpeed)
        executors.io { repository.savePlaybackSpeed(normalizedSpeed) }
    }

    fun applyAppVolume(volume: Float) {
        val normalizedVolume = normalizeAppVolume(volume)
        listener.onAppVolumeApplied(normalizedVolume)
        executors.io { repository.saveAppVolume(normalizedVolume) }
    }

    fun applyStreamingAudioQuality(quality: String) {
        val normalizedQuality = StreamingQualityPreference.normalize(quality)
        listener.onStreamingAudioQualityApplied(normalizedQuality)
        executors.io { repository.saveStreamingAudioQuality(normalizedQuality) }
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        listener.onOnlineLyricsEnabledApplied(enabled)
        executors.io { repository.saveOnlineLyricsEnabled(enabled) }
    }

    fun setConcurrentPlaybackEnabled(enabled: Boolean) {
        listener.onConcurrentPlaybackEnabledApplied(enabled)
        executors.io { repository.saveConcurrentPlaybackEnabled(enabled) }
    }

    fun applyLyricsOffset(offsetMs: Long) {
        val normalizedOffsetMs = normalizeLyricsOffsetMs(offsetMs)
        listener.onLyricsOffsetApplied(normalizedOffsetMs)
        executors.io { repository.saveLyricsOffsetMs(normalizedOffsetMs) }
    }

    private fun normalizePlaybackSpeed(speed: Float): Float {
        if (speed < 0.5f) {
            return 0.5f
        }
        if (speed > 2.0f) {
            return 2.0f
        }
        return Math.round(speed * 100.0f) / 100.0f
    }

    private fun normalizeAppVolume(volume: Float): Float {
        if (volume < 0.0f) {
            return 0.0f
        }
        if (volume > 1.0f) {
            return 1.0f
        }
        return Math.round(volume * 100.0f) / 100.0f
    }

    private fun normalizeLyricsOffsetMs(offsetMs: Long): Long {
        if (offsetMs < -5000L) {
            return -5000L
        }
        if (offsetMs > 5000L) {
            return 5000L
        }
        return Math.round(offsetMs / 100.0) * 100L
    }
}
