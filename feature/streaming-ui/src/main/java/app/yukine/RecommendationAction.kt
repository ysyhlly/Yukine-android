package app.yukine

import app.yukine.streaming.StreamingProviderName

sealed interface RecommendationAction {
    val provider: StreamingProviderName?

    data class PlayDaily(override val provider: StreamingProviderName?) : RecommendationAction

    data class PlayHeartbeat(override val provider: StreamingProviderName?) : RecommendationAction
}

interface RecommendationActionCallbacks {
    fun setStatus(status: String)

    fun playDailyRecommendation(presentation: StreamingRecommendationPresentation)

    fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest

    fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation)

    fun logSeedMiss(request: HeartbeatRecommendationSeedRequest)
}
