package app.yukine

import app.yukine.streaming.StreamingProviderName

internal fun interface RecommendationStatusSink {
    fun setStatus(status: String)
}

internal fun interface DailyRecommendationPlayerSink {
    fun playDailyRecommendation(presentation: StreamingRecommendationPresentation)
}

internal fun interface RecommendationSeedRequestProvider {
    fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest
}

internal fun interface RecommendationHeartbeatPlayerSink {
    fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation)
}

internal fun interface RecommendationSeedMissLogger {
    fun logSeedMiss(request: HeartbeatRecommendationSeedRequest)
}

internal fun interface MainRecommendationActionCallbacksFactory {
    fun create(
        statusSink: RecommendationStatusSink,
        dailyPlayerSink: DailyRecommendationPlayerSink,
        seedRequestProvider: RecommendationSeedRequestProvider,
        heartbeatPlayerSink: RecommendationHeartbeatPlayerSink,
        seedMissLogger: RecommendationSeedMissLogger
    ): RecommendationActionCallbacks
}

internal class MainRecommendationActionCallbacks(
    private val statusSink: RecommendationStatusSink,
    private val dailyPlayerSink: DailyRecommendationPlayerSink,
    private val seedRequestProvider: RecommendationSeedRequestProvider,
    private val heartbeatPlayerSink: RecommendationHeartbeatPlayerSink,
    private val seedMissLogger: RecommendationSeedMissLogger
) : RecommendationActionCallbacks {
    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }

    override fun playDailyRecommendation(presentation: StreamingRecommendationPresentation) {
        dailyPlayerSink.playDailyRecommendation(presentation)
    }

    override fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest =
        seedRequestProvider.seedRequest(provider)

    override fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation) {
        heartbeatPlayerSink.playHeartbeatRecommendation(presentation)
    }

    override fun logSeedMiss(request: HeartbeatRecommendationSeedRequest) {
        seedMissLogger.logSeedMiss(request)
    }
}
