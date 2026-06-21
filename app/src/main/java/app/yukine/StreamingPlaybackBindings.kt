package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality

internal fun interface StreamingPlaybackControllerQualityProvider {
    fun quality(): StreamingAudioQuality
}

internal fun interface StreamingPlaybackQueueProvider {
    fun queue(): List<Track>
}

internal fun interface StreamingHeartbeatAppender {
    fun append(snapshot: PlaybackStateSnapshot)
}

internal class StreamingPlaybackBindings(
    private val languageModeProvider: StatusLanguageModeProvider,
    private val adaptiveQualityProvider: StreamingPlaybackControllerQualityProvider,
    private val selectedQualityProvider: StreamingPlaybackControllerQualityProvider,
    private val queueProvider: StreamingPlaybackQueueProvider,
    private val heartbeatAppender: StreamingHeartbeatAppender,
    private val playbackActionResultApplier: QueuePlaybackActionResultApplier,
    private val statusSink: QueueStatusSink
) : StreamingPlaybackController.Listener {
    override fun languageMode(): String {
        return languageModeProvider.languageMode()
    }

    override fun adaptiveStreamingQuality(): StreamingAudioQuality {
        return adaptiveQualityProvider.quality()
    }

    override fun selectedStreamingQuality(): StreamingAudioQuality {
        return selectedQualityProvider.quality()
    }

    override fun queueSnapshot(): List<Track> {
        return queueProvider.queue()
    }

    override fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot) {
        heartbeatAppender.append(snapshot)
    }

    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        playbackActionResultApplier.apply(result)
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
