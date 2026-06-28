package app.yukine

import app.yukine.playback.AudioEffectSettings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

internal object SettingsLabelFormatter {
    @JvmStatic
    fun playbackSpeedLabel(speed: Float): String {
        val normalized = normalizePlaybackSpeed(speed)
        if (abs(normalized - round(normalized)) < 0.01f) {
            return round(normalized).toInt().toString() + "x"
        }
        return String.format(Locale.ROOT, "%.2fx", normalized).replace(Regex("0x$"), "x")
    }

    @JvmStatic
    fun appVolumeLabel(volume: Float): String =
        (normalizeAppVolume(volume) * 100.0f).roundToInt().toString() + "%"

    @JvmStatic
    fun streamingQualityLabel(quality: String, languageMode: String): String {
        return when (StreamingQualityPreference.normalize(quality)) {
            StreamingQualityPreference.AUTO -> text(languageMode, "quality.auto")
            StreamingQualityPreference.STANDARD -> text(languageMode, "quality.standard")
            StreamingQualityPreference.HIGH -> text(languageMode, "quality.high")
            StreamingQualityPreference.LOSSLESS -> text(languageMode, "quality.lossless")
            StreamingQualityPreference.HIRES -> text(languageMode, "quality.hires")
            else -> text(languageMode, "quality.high")
        }
    }

    @JvmStatic
    fun shareStyleLabel(style: String, languageMode: String): String {
        return when (TrackShareStyle.normalize(style)) {
            TrackShareStyle.PLATFORM_CARD -> text(languageMode, "share.style.platform.card")
            TrackShareStyle.CARD -> text(languageMode, "share.style.card")
            else -> text(languageMode, "share.style.text")
        }
    }

    @JvmStatic
    fun audioEffectsLabel(settings: AudioEffectSettings?, languageMode: String): String {
        val effects = settings ?: AudioEffectSettings.DEFAULT
        if (!effects.enabled) {
            return text(languageMode, "off")
        }
        return text(languageMode, "enabled") + " / " + equalizerPresetLabel(effects.preset, languageMode)
    }

    @JvmStatic
    fun equalizerPresetLabel(preset: Int, languageMode: String): String {
        return when (preset) {
            AudioEffectSettings.PRESET_CUSTOM -> text(languageMode, "eq.custom")
            0 -> text(languageMode, "eq.normal")
            1 -> text(languageMode, "eq.classical")
            2 -> text(languageMode, "eq.dance")
            else -> text(languageMode, "eq.preset") + " " + preset
        }
    }

    @JvmStatic
    fun lyricsOffsetLabel(offsetMs: Long): String {
        val normalized = normalizeLyricsOffsetMs(offsetMs)
        if (normalized == 0L) {
            return "0 ms"
        }
        val sign = if (normalized > 0L) "+" else "-"
        val absolute = abs(normalized)
        if (absolute % 1000L == 0L) {
            return sign + (absolute / 1000L) + " s"
        }
        return sign + String.format(Locale.ROOT, "%.1f", absolute / 1000.0) + " s"
    }

    @JvmStatic
    fun pageBackgroundPageLabel(page: String, languageMode: String): String {
        return when (PageBackgrounds.normalizePage(page)) {
            PageBackgrounds.PAGE_ALL -> text(languageMode, "page.background.all")
            PageBackgrounds.PAGE_HOME -> text(languageMode, "tab.home")
            PageBackgrounds.PAGE_LIBRARY -> text(languageMode, "tab.library")
            PageBackgrounds.PAGE_PLAYER -> text(languageMode, "tab.playing")
            PageBackgrounds.PAGE_SETTINGS -> text(languageMode, "tab.settings")
            else -> text(languageMode, "page.background")
        }
    }

    private fun text(languageMode: String, key: String): String =
        AppLanguage.text(languageMode, key)

    private fun normalizePlaybackSpeed(speed: Float): Float {
        if (speed < 0.5f) {
            return 0.5f
        }
        if (speed > 2.0f) {
            return 2.0f
        }
        return round(speed * 100.0f) / 100.0f
    }

    private fun normalizeAppVolume(volume: Float): Float {
        if (volume < 0.0f) {
            return 0.0f
        }
        if (volume > 1.0f) {
            return 1.0f
        }
        return round(volume * 100.0f) / 100.0f
    }

    private fun normalizeLyricsOffsetMs(offsetMs: Long): Long {
        if (offsetMs < -5000L) {
            return -5000L
        }
        if (offsetMs > 5000L) {
            return 5000L
        }
        return round(offsetMs / 100.0).toLong() * 100L
    }
}
