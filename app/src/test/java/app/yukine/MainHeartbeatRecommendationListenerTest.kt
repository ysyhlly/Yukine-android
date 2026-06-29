package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class MainHeartbeatRecommendationListenerTest {
    @Test
    fun delegatesHeartbeatRecommendationCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val seedRequest = HeartbeatRecommendationSeedRequest(
            candidates = listOf(heartbeatListenerTrack(1L)),
            seedTrackId = "seed",
            playlistId = "playlist",
            seedMissingMessage = "missing"
        )
        val presentation = StreamingRecommendationPresentation(
            tracks = listOf(heartbeatListenerTrack(2L)),
            readyStatus = "Ready"
        )
        val listener = MainHeartbeatRecommendationListener(
            serviceAvailability = HeartbeatPlaybackServiceAvailability { true },
            seedRequestProvider = HeartbeatSeedRequestProvider { provider ->
                calls += "seed:${provider.wireName}"
                seedRequest
            },
            modeStopper = HeartbeatModeStopper { calls += "stop" },
            queueAppender = HeartbeatRecommendationQueueAppender { calls += "append:${it.tracks.size}" },
            playerSink = HeartbeatRecommendationPlayerSink { calls += "play:${it.readyStatus}" },
            seedMissLogger = HeartbeatSeedMissLogger { calls += "miss:${it.seedMissingMessage}" },
            statusSink = HeartbeatStatusSink { calls += "status:$it" }
        )

        assertEquals(true, listener.hasPlaybackService())
        assertEquals(seedRequest, listener.seedRequest(StreamingProviderName.NETEASE))
        listener.stopHeartbeatRecommendationMode()
        listener.appendToQueue(presentation)
        listener.playHeartbeatRecommendation(presentation)
        listener.logSeedMiss(seedRequest)
        listener.setStatus("Playing")

        assertEquals(
            listOf("seed:netease", "stop", "append:1", "play:Ready", "miss:missing", "status:Playing"),
            calls
        )
    }

    @Test
    fun factoryCreatesHeartbeatRecommendationControllerListener() {
        val factory = StreamingModule.provideMainHeartbeatRecommendationListenerFactory()
        val calls = mutableListOf<String>()
        val listener = factory.create(
            HeartbeatPlaybackServiceAvailability { false },
            HeartbeatSeedRequestProvider { HeartbeatRecommendationSeedRequest(seedTrackId = "seed") },
            HeartbeatModeStopper { calls += "stop" },
            HeartbeatRecommendationQueueAppender { calls += "append" },
            HeartbeatRecommendationPlayerSink { calls += "play" },
            HeartbeatSeedMissLogger { calls += "miss" },
            HeartbeatStatusSink { calls += "status:$it" }
        )

        assertEquals(false, listener.hasPlaybackService())
        assertEquals("seed", listener.seedRequest(StreamingProviderName.NETEASE).seedTrackId)
        listener.stopHeartbeatRecommendationMode()
        listener.appendToQueue(StreamingRecommendationPresentation(tracks = listOf(heartbeatListenerTrack(3L))))
        listener.playHeartbeatRecommendation(StreamingRecommendationPresentation(readyStatus = "Ready"))
        listener.logSeedMiss(HeartbeatRecommendationSeedRequest(seedMissingMessage = "missing"))
        listener.setStatus("Ready")

        assertEquals(listOf("stop", "append", "play", "miss", "status:Ready"), calls)
    }
}

private fun heartbeatListenerTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
