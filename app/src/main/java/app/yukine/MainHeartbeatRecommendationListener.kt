package app.yukine

internal fun interface HeartbeatPlaybackServiceAvailability {
    fun hasPlaybackService(): Boolean
}

internal fun interface HeartbeatModeStopper {
    fun stopHeartbeatRecommendationMode()
}

internal fun interface HeartbeatRecommendationQueueAppender {
    fun appendToQueue(presentation: StreamingRecommendationPresentation)
}

internal fun interface HeartbeatRecommendationPlayerSink {
    fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation)
}

internal fun interface HeartbeatSeedMissLogger {
    fun logSeedMiss(request: HeartbeatRecommendationSeedRequest)
}

internal fun interface HeartbeatStatusSink {
    fun setStatus(status: String)
}

internal fun interface MainHeartbeatRecommendationListenerFactory {
    fun create(
        serviceAvailability: HeartbeatPlaybackServiceAvailability,
        seedRequestProvider: HeartbeatSeedRequestProvider,
        modeStopper: HeartbeatModeStopper,
        queueAppender: HeartbeatRecommendationQueueAppender,
        playerSink: HeartbeatRecommendationPlayerSink,
        seedMissLogger: HeartbeatSeedMissLogger,
        statusSink: HeartbeatStatusSink
    ): HeartbeatRecommendationController.Listener
}

internal class MainHeartbeatRecommendationListener(
    private val serviceAvailability: HeartbeatPlaybackServiceAvailability,
    private val seedRequestProvider: HeartbeatSeedRequestProvider,
    private val modeStopper: HeartbeatModeStopper,
    private val queueAppender: HeartbeatRecommendationQueueAppender,
    private val playerSink: HeartbeatRecommendationPlayerSink,
    private val seedMissLogger: HeartbeatSeedMissLogger,
    private val statusSink: HeartbeatStatusSink
) : HeartbeatRecommendationController.Listener {
    override fun hasPlaybackService(): Boolean =
        serviceAvailability.hasPlaybackService()

    override fun seedRequest(provider: app.yukine.streaming.StreamingProviderName): HeartbeatRecommendationSeedRequest =
        seedRequestProvider.request(provider)

    override fun stopHeartbeatRecommendationMode() {
        modeStopper.stopHeartbeatRecommendationMode()
    }

    override fun appendToQueue(presentation: StreamingRecommendationPresentation) {
        queueAppender.appendToQueue(presentation)
    }

    override fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation) {
        playerSink.playHeartbeatRecommendation(presentation)
    }

    override fun logSeedMiss(request: HeartbeatRecommendationSeedRequest) {
        seedMissLogger.logSeedMiss(request)
    }

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }
}
