package app.yukine

import app.yukine.streaming.StreamingProviderName
import kotlinx.coroutines.Job

sealed interface RecommendationAction {
    val provider: StreamingProviderName?

    data class PlayDaily(override val provider: StreamingProviderName?) : RecommendationAction

    data class PlayHeartbeat(override val provider: StreamingProviderName?) : RecommendationAction
}

fun interface RecommendationActionRunner {
    fun run(action: RecommendationAction)
}

interface RecommendationActionCallbacks {
    fun setStatus(status: String)

    fun playDailyRecommendation(presentation: StreamingRecommendationPresentation)

    fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest

    fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation)

    fun logSeedMiss(request: HeartbeatRecommendationSeedRequest)
}

fun interface RecommendationLanguageProvider {
    fun languageMode(): String
}

fun interface RecommendationActionHandler {
    fun onAction(
        action: RecommendationAction,
        languageMode: String,
        callbacks: RecommendationActionCallbacks
    ): Job
}

internal class RecommendationActionController(
    private val recommendationActionHandler: RecommendationActionHandler,
    private val languageProvider: RecommendationLanguageProvider,
    private val callbacks: RecommendationActionCallbacks
) : RecommendationActionRunner {
    override fun run(action: RecommendationAction) {
        recommendationActionHandler.onAction(action, languageProvider.languageMode(), callbacks)
    }
}
