package app.yukine

import app.yukine.streaming.StreamingProviderName
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationActionControllerTest {
    @Test
    fun forwardsTypedActionsToRecommendationActionHandlerWithLanguageAndCallbacks() {
        val calls = mutableListOf<String>()
        val callbacks = object : RecommendationActionCallbacks {
            override fun setStatus(status: String) {
                calls += "status:$status"
            }

            override fun playDailyRecommendation(presentation: StreamingRecommendationPresentation) {
                calls += "daily:${presentation.title}"
            }

            override fun seedRequest(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest {
                calls += "seed:${provider.wireName}"
                return HeartbeatRecommendationSeedRequest(seedTrackId = "seed", playlistId = "seed")
            }

            override fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation) {
                calls += "heartbeat:${presentation.title}"
            }

            override fun logSeedMiss(request: HeartbeatRecommendationSeedRequest) {
                calls += "miss:${request.seedMissingMessage}"
            }
        }
        val controller = RecommendationActionController(
            recommendationActionHandler = RecommendationActionHandler { action, languageMode, receivedCallbacks ->
                calls += "action:${action.provider}:$languageMode"
                receivedCallbacks.setStatus("Loading")
                receivedCallbacks.seedRequest(StreamingProviderName.NETEASE)
                Job()
            },
            languageProvider = RecommendationLanguageProvider { AppLanguage.MODE_ENGLISH },
            callbacks = callbacks
        )

        controller.run(RecommendationAction.PlayDaily(StreamingProviderName.NETEASE))

        assertEquals(
            listOf(
                "action:${StreamingProviderName.NETEASE}:${AppLanguage.MODE_ENGLISH}",
                "status:Loading",
                "seed:netease"
            ),
            calls
        )
    }

    @Test
    fun actionBindingsForwardRecommendationCallbacksToPlatformEdges() {
        val calls = mutableListOf<String>()
        val bindings = RecommendationActionBindings(
            dailyRecommendationPlayer = DailyRecommendationTrackListPlayer { presentation ->
                calls += "daily:${presentation.title}"
            },
            seedRequestProvider = HeartbeatSeedRequestProvider { provider ->
                calls += "seed:${provider.wireName}"
                HeartbeatRecommendationSeedRequest(seedTrackId = "seed", playlistId = "seed")
            },
            heartbeatPresentationPlayer = HeartbeatPresentationPlayer { presentation ->
                calls += "heartbeat:${presentation.title}"
            },
            seedMissLogger = HeartbeatSeedMissLogger { request ->
                calls += "miss:${request.seedMissingMessage}"
            },
            statusSink = QueueStatusSink { status ->
                calls += "status:$status"
            }
        )

        bindings.setStatus("Loading")
        bindings.playDailyRecommendation(StreamingRecommendationPresentation(title = "Daily"))
        bindings.seedRequest(StreamingProviderName.QQ_MUSIC)
        bindings.playHeartbeatRecommendation(StreamingRecommendationPresentation(title = "Heartbeat"))
        bindings.logSeedMiss(HeartbeatRecommendationSeedRequest(seedMissingMessage = "No seed"))

        assertEquals(
            listOf(
                "status:Loading",
                "daily:Daily",
                "seed:qqmusic",
                "heartbeat:Heartbeat",
                "miss:No seed"
            ),
            calls
        )
    }
}
