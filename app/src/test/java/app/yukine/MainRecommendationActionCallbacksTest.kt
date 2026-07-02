package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class MainRecommendationActionCallbacksTest {
    @Test
    fun delegatesRecommendationActionCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val seedRequest = HeartbeatRecommendationSeedRequest(seedTrackId = "seed", playlistId = "playlist")
        val presentation = StreamingRecommendationPresentation(
            tracks = listOf(recommendationTrack(1L)),
            readyStatus = "Ready"
        )
        val callbacks = callbacks(calls, seedRequest)

        callbacks.setStatus("Loading")
        callbacks.playDailyRecommendation(presentation)
        assertEquals(seedRequest, callbacks.seedRequest(StreamingProviderName.NETEASE))
        callbacks.playHeartbeatRecommendation(presentation)
        callbacks.logSeedMiss(HeartbeatRecommendationSeedRequest(seedMissingMessage = "missing"))

        assertEquals(
            listOf(
                "status:Loading",
                "daily:Ready",
                "seed:netease",
                "heartbeat:Ready",
                "miss:missing"
            ),
            calls
        )
    }

    @Test
    fun factoryCreatesRecommendationActionCallbacks() {
        val calls = mutableListOf<String>()
        val callbacks = StreamingModule.provideMainRecommendationActionCallbacksFactory().create(
            RecommendationStatusSink { calls += "status:$it" },
            DailyRecommendationPlayerSink { calls += "daily:${it.tracks.size}" },
            RecommendationSeedRequestProvider {
                calls += "seed:${it.wireName}"
                HeartbeatRecommendationSeedRequest(seedTrackId = "seed")
            },
            RecommendationHeartbeatPlayerSink { calls += "heartbeat:${it.tracks.size}" },
            RecommendationSeedMissLogger { calls += "miss:${it.seedMissingMessage}" }
        )

        callbacks.playDailyRecommendation(StreamingRecommendationPresentation(tracks = listOf(recommendationTrack(2L))))
        assertEquals("seed", callbacks.seedRequest(StreamingProviderName.NETEASE).seedTrackId)
        callbacks.playHeartbeatRecommendation(StreamingRecommendationPresentation(tracks = listOf(recommendationTrack(3L))))
        callbacks.setStatus("Ready")
        callbacks.logSeedMiss(HeartbeatRecommendationSeedRequest(seedMissingMessage = "missing"))

        assertEquals(listOf("daily:1", "seed:netease", "heartbeat:1", "status:Ready", "miss:missing"), calls)
    }

    private fun callbacks(
        calls: MutableList<String>,
        seedRequest: HeartbeatRecommendationSeedRequest
    ): RecommendationActionCallbacks =
        MainRecommendationActionCallbacks(
            statusSink = RecommendationStatusSink { calls += "status:$it" },
            dailyPlayerSink = DailyRecommendationPlayerSink { calls += "daily:${it.readyStatus}" },
            seedRequestProvider = RecommendationSeedRequestProvider {
                calls += "seed:${it.wireName}"
                seedRequest
            },
            heartbeatPlayerSink = RecommendationHeartbeatPlayerSink { calls += "heartbeat:${it.readyStatus}" },
            seedMissLogger = RecommendationSeedMissLogger { calls += "miss:${it.seedMissingMessage}" }
        )
}

private fun recommendationTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
