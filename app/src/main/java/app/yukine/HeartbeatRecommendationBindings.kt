package app.yukine

import app.yukine.streaming.StreamingTrack
import app.yukine.streaming.StreamingProviderName

internal fun interface HeartbeatSeedRequestProvider {
    fun request(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest
}

internal fun interface HeartbeatQueueAppender {
    fun append(presentation: StreamingRecommendationPresentation)
}

internal fun interface HeartbeatTrackListPlayer {
    fun play(streamingTracks: List<StreamingTrack>, emptyStatus: String, playingStatus: String)
}

internal fun interface HeartbeatSeedMissLogger {
    fun log(request: HeartbeatRecommendationSeedRequest)
}

internal class HeartbeatRecommendationBindings(
    private val playbackServiceAvailability: QueuePlaybackServiceAvailability,
    private val seedRequestProvider: HeartbeatSeedRequestProvider,
    private val heartbeatRecommendationStopper: QueueNoArgAction,
    private val queueAppender: HeartbeatQueueAppender,
    private val heartbeatTrackListPlayer: HeartbeatTrackListPlayer,
    private val seedMissLogger: HeartbeatSeedMissLogger,
    private val statusSink: QueueStatusSink
) : HeartbeatRecommendationController.Listener {
    override fun hasPlaybackService(): Boolean {
        return playbackServiceAvailability.hasService()
    }

    override fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest {
        return seedRequestProvider.request(provider)
    }

    override fun stopHeartbeatRecommendationMode() {
        heartbeatRecommendationStopper.run()
    }

    override fun appendToQueue(presentation: StreamingRecommendationPresentation) {
        queueAppender.append(presentation)
    }

    override fun playHeartbeatRecommendationTracks(
        streamingTracks: List<StreamingTrack>,
        emptyStatus: String,
        playingStatus: String
    ) {
        heartbeatTrackListPlayer.play(streamingTracks, emptyStatus, playingStatus)
    }

    override fun logSeedMiss(request: HeartbeatRecommendationSeedRequest) {
        seedMissLogger.log(request)
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
