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

internal fun interface DailyRecommendationTrackListPlayer {
    fun play(presentation: StreamingRecommendationPresentation)
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

internal class RecommendationActionBindings(
    private val dailyRecommendationPlayer: DailyRecommendationTrackListPlayer,
    private val seedRequestProvider: HeartbeatSeedRequestProvider,
    private val heartbeatPresentationPlayer: HeartbeatPresentationPlayer,
    private val seedMissLogger: HeartbeatSeedMissLogger,
    private val statusSink: QueueStatusSink
) : RecommendationActionCallbacks {
    override fun setStatus(status: String) {
        statusSink.set(status)
    }

    override fun playDailyRecommendation(presentation: StreamingRecommendationPresentation) {
        dailyRecommendationPlayer.play(presentation)
    }

    override fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest {
        return seedRequestProvider.request(provider)
    }

    override fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation) {
        heartbeatPresentationPlayer.play(presentation)
    }

    override fun logSeedMiss(request: HeartbeatRecommendationSeedRequest) {
        seedMissLogger.log(request)
    }
}
