package app.yukine

import android.content.Context
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingQualityPreference

internal interface StreamingPlaybackQuality {
    fun adaptive(): StreamingAudioQuality

    fun selected(): StreamingAudioQuality
}

/** Owns the runtime interpretation of the persisted streaming quality preference. */
internal class StreamingPlaybackQualityPolicy(
    private val context: Context,
    private val settingsStore: MainSettingsStore
) : StreamingPlaybackQuality {
    override fun adaptive(): StreamingAudioQuality = StreamingQualityPreference.playbackQuality(
        context,
        settingsStore.streamingAudioQuality()
    )

    override fun selected(): StreamingAudioQuality {
        val selected = settingsStore.streamingAudioQuality()
        return if (
            StreamingQualityPreference.AUTO == StreamingQualityPreference.normalize(selected)
        ) {
            adaptive()
        } else {
            StreamingQualityPreference.ceilingFor(selected)
        }
    }
}
