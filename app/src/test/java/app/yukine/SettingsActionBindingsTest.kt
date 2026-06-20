package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsActionBindingsTest {
    @Test
    fun forwardsSettingsActionEdges() {
        val calls = mutableListOf<String>()
        val bindings = SettingsActionBindings(
            streamingGatewayEndpointApplier = SettingsStreamingGatewayEndpointApplier { calls += "gateway:$it" },
            playbackActionResultApplier = QueuePlaybackActionResultApplier { calls += "playback:${it?.status}" },
            lyricsReloader = QueueNoArgAction { calls += "reload" }
        )

        bindings.applyStreamingGatewayEndpoint("endpoint")
        bindings.applyPlaybackActionResult(PlaybackActionResultUi(null, "done", false, false, false, false))
        bindings.reloadCurrentLyrics()

        assertEquals(listOf("gateway:endpoint", "playback:done", "reload"), calls)
    }
}
