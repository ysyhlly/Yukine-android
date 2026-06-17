package app.yukine

import android.content.Context
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingNetworkQuality

object StreamingQualityPreference {
    const val AUTO = "auto"
    const val STANDARD = "standard"
    const val HIGH = "high"
    const val LOSSLESS = "lossless"
    const val HIRES = "hires"

    @JvmStatic
    fun defaultValue(): String = HIGH

    @JvmStatic
    fun normalize(value: String?): String {
        return when (value?.trim()?.lowercase()) {
            AUTO -> AUTO
            STANDARD -> STANDARD
            HIGH -> HIGH
            LOSSLESS -> LOSSLESS
            HIRES -> HIRES
            else -> defaultValue()
        }
    }

    @JvmStatic
    fun options(): List<String> = listOf(AUTO, STANDARD, HIGH, LOSSLESS, HIRES)

    @JvmStatic
    fun ceilingFor(value: String?): StreamingAudioQuality {
        return when (normalize(value)) {
            STANDARD -> StreamingAudioQuality.STANDARD
            HIGH -> StreamingAudioQuality.HIGH
            LOSSLESS -> StreamingAudioQuality.LOSSLESS
            HIRES -> StreamingAudioQuality.HIRES
            else -> StreamingAudioQuality.LOSSLESS
        }
    }

    @JvmStatic
    fun playbackQuality(context: Context, value: String?): StreamingAudioQuality {
        val normalized = normalize(value)
        return if (normalized == AUTO) {
            StreamingNetworkQuality.preferredQuality(context, StreamingAudioQuality.LOSSLESS)
        } else {
            ceilingFor(normalized)
        }
    }

    @JvmStatic
    fun valueFor(quality: StreamingAudioQuality): String {
        return when (quality) {
            StreamingAudioQuality.STANDARD -> STANDARD
            StreamingAudioQuality.HIGH -> HIGH
            StreamingAudioQuality.LOSSLESS -> LOSSLESS
            StreamingAudioQuality.HIRES -> HIRES
        }
    }
}
