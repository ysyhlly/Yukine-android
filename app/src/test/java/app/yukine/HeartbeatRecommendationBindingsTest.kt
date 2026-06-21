package app.yukine

import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartbeatRecommendationBindingsTest {
    @Test
    fun forwardsHeartbeatRecommendationEdges() {
        val calls = mutableListOf<String>()
        val bindings = HeartbeatRecommendationBindings(
            playbackServiceAvailability = QueuePlaybackServiceAvailability {
                calls += "service"
                true
            },
            seedRequestProvider = HeartbeatSeedRequestProvider { provider ->
                calls += "seed:${provider.wireName}"
                HeartbeatRecommendationSeedRequest(seedTrackId = "seed", playlistId = "seed")
            },
            heartbeatRecommendationStopper = QueueNoArgAction { calls += "stop" },
            queueAppender = HeartbeatQueueAppender { presentation ->
                calls += "append:${presentation.readyStatus}"
            },
            heartbeatTrackListPlayer = HeartbeatTrackListPlayer { tracks, emptyStatus, playingStatus ->
                calls += "play:${tracks.size}:$emptyStatus:$playingStatus"
            },
            seedMissLogger = HeartbeatSeedMissLogger { request ->
                calls += "miss:${request.seedMissingMessage}"
            },
            statusSink = QueueStatusSink { status -> calls += "status:$status" }
        )

        bindings.hasPlaybackService()
        bindings.seedRequest(StreamingProviderName.NETEASE)
        bindings.stopHeartbeatRecommendationMode()
        bindings.appendToQueue(StreamingRecommendationPresentation(readyStatus = "Ready"))
        bindings.playHeartbeatRecommendationTracks(emptyList(), "Empty", "Playing")
        bindings.logSeedMiss(HeartbeatRecommendationSeedRequest(seedMissingMessage = "Missing seed"))
        bindings.setStatus("Done")

        assertEquals(
            listOf(
                "service",
                "seed:netease",
                "stop",
                "append:Ready",
                "play:0:Empty:Playing",
                "miss:Missing seed",
                "status:Done"
            ),
            calls
        )
    }
}
