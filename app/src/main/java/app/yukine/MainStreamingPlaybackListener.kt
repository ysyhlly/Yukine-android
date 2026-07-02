package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality

internal fun interface StreamingPlaybackLanguageProvider {
    fun languageMode(): String
}

internal fun interface AdaptiveStreamingQualityProvider {
    fun adaptiveStreamingQuality(): StreamingAudioQuality
}

internal fun interface SelectedStreamingQualityProvider {
    fun selectedStreamingQuality(): StreamingAudioQuality
}

internal fun interface StreamingQueueSnapshotSource {
    fun queueSnapshot(): List<Track>
}

internal fun interface HeartbeatRecommendationAppendHandler {
    fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot)
}

internal fun interface StreamingPlaybackStatusSink {
    fun setStatus(status: String)
}

internal fun interface MainStreamingPlaybackListenerFactory {
    fun create(
        languageProvider: StreamingPlaybackLanguageProvider,
        adaptiveQualityProvider: AdaptiveStreamingQualityProvider,
        selectedQualityProvider: SelectedStreamingQualityProvider,
        queueSnapshotSource: StreamingQueueSnapshotSource,
        heartbeatAppendHandler: HeartbeatRecommendationAppendHandler,
        resultSink: PlaybackActionResultSink,
        statusSink: StreamingPlaybackStatusSink
    ): StreamingPlaybackController.Listener
}

internal class MainStreamingPlaybackListener(
    private val languageProvider: StreamingPlaybackLanguageProvider,
    private val adaptiveQualityProvider: AdaptiveStreamingQualityProvider,
    private val selectedQualityProvider: SelectedStreamingQualityProvider,
    private val queueSnapshotSource: StreamingQueueSnapshotSource,
    private val heartbeatAppendHandler: HeartbeatRecommendationAppendHandler,
    private val resultSink: PlaybackActionResultSink,
    private val statusSink: StreamingPlaybackStatusSink
) : StreamingPlaybackController.Listener {
    override fun languageMode(): String =
        languageProvider.languageMode()

    override fun adaptiveStreamingQuality(): StreamingAudioQuality =
        adaptiveQualityProvider.adaptiveStreamingQuality()

    override fun selectedStreamingQuality(): StreamingAudioQuality =
        selectedQualityProvider.selectedStreamingQuality()

    override fun queueSnapshot(): List<Track> =
        queueSnapshotSource.queueSnapshot()

    override fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot) {
        heartbeatAppendHandler.maybeAppendHeartbeatRecommendations(snapshot)
    }

    override fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        resultSink.apply(result)
    }

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }
}
