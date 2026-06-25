package app.yukine

import app.yukine.streaming.StreamingProviderName

internal fun interface HeartbeatSeedRequestProvider {
    fun request(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest
}

internal fun interface HeartbeatQueueAppender {
    fun append(presentation: StreamingRecommendationPresentation)
}

internal fun interface HeartbeatPresentationPlayer {
    fun play(presentation: StreamingRecommendationPresentation)
}

internal fun interface HeartbeatSeedMissLogger {
    fun log(request: HeartbeatRecommendationSeedRequest)
}

internal class HeartbeatRecommendationBindings(
    private val playbackServiceAvailability: QueuePlaybackServiceAvailability,
    private val seedRequestProvider: HeartbeatSeedRequestProvider,
    private val heartbeatRecommendationStopper: QueueNoArgAction,
    private val queueAppender: HeartbeatQueueAppender,
    private val heartbeatPresentationPlayer: HeartbeatPresentationPlayer,
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

    override fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation) {
        heartbeatPresentationPlayer.play(presentation)
    }

    override fun logSeedMiss(request: HeartbeatRecommendationSeedRequest) {
        seedMissLogger.log(request)
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
