package app.yukine

import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingQualityPreference

/** Stateless localized status text for streaming playback and settings surfaces. */
internal object StreamingStatusTextFactory {
    @JvmStatic
    fun playback(
        languageMode: String,
        quality: StreamingAudioQuality? = null
    ): StreamingPlaybackStatusText {
        val qualityLabel = quality?.let {
            SettingsLabelFormatter.streamingQualityLabel(
                StreamingQualityPreference.valueFor(it),
                languageMode
            )
        }.orEmpty()
        return StreamingPlaybackStatusText(
            resolving = text(languageMode, "streaming.resolving"),
            resolveFailed = text(languageMode, "streaming.resolve.failed"),
            qualityDowngrading = text(languageMode, "streaming.quality.downgrading") + qualityLabel,
            qualityDowngraded = text(languageMode, "streaming.quality.downgraded") + qualityLabel,
            qualityRefreshing = text(languageMode, "streaming.quality.refreshing") + qualityLabel,
            qualityRefreshed = text(languageMode, "streaming.quality.refreshed") + qualityLabel
        )
    }

    @JvmStatic
    fun settings(
        languageMode: String,
        qualityPreference: String? = null
    ): StreamingStatusText {
        val qualityLabel = qualityPreference?.let {
            SettingsLabelFormatter.streamingQualityLabel(it, languageMode)
        }.orEmpty()
        return StreamingStatusText(
            streamingQualityApplied = text(languageMode, "streaming.quality.applied") + qualityLabel
        )
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
}
