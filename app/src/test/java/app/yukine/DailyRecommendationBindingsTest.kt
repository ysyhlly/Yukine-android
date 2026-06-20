package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyRecommendationBindingsTest {
    @Test
    fun forwardsDailyRecommendationEdges() {
        val calls = mutableListOf<String>()
        val bindings = DailyRecommendationBindings(
            presentationBuilder = DailyRecommendationPresentationBuilder { tracks, emptyStatus, title ->
                calls += "build:${tracks.size}:$emptyStatus:$title"
                StreamingRecommendationPresentation(
                    tracks = emptyList(),
                    emptyStatus = emptyStatus,
                    readyStatus = title,
                    title = title
                )
            },
            trackListPlayer = DailyRecommendationTrackListPlayer { presentation ->
                calls += "play:${presentation.emptyStatus}:${presentation.title}"
            },
            statusSink = QueueStatusSink { status -> calls += "status:$status" }
        )

        bindings.playRecommendationTracks(emptyList(), "Empty", "Daily")
        bindings.setStatus("Loading")

        assertEquals(
            listOf("build:0:Empty:Daily", "play:Empty:Daily", "status:Loading"),
            calls
        )
    }
}
