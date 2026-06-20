package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueActionBindingsTest {
    @Test
    fun forwardsQueueActionsToBoundOperations() {
        val calls = mutableListOf<String>()
        var hasService = true
        val bindings = QueueActionBindings(
            playbackActionResultApplier = QueuePlaybackActionResultApplier { calls += "apply:${it?.status}" },
            playbackServiceAvailability = QueuePlaybackServiceAvailability { hasService },
            moveQueueTrackAction = QueueMoveAction { fromIndex, toIndex -> calls += "move:$fromIndex:$toIndex" },
            renderNowBarAction = QueueNoArgAction { calls += "nowBar" },
            renderSelectedTabAction = QueueNoArgAction { calls += "tab" },
            clearQueueConfirmer = QueueNoArgAction { calls += "confirm" },
            queueEmptyStatusProvider = QueueStatusProvider { "empty" },
            statusSink = QueueStatusSink { calls += "status:$it" }
        )

        assertTrue(bindings.hasPlaybackService())
        bindings.applyPlaybackActionResult(PlaybackActionResultUi(null, "done", false, false, false, false))
        bindings.moveQueueTrack(1, 2)
        bindings.renderNowBar()
        bindings.renderSelectedTab()
        bindings.confirmClearQueue()
        assertEquals("empty", bindings.queueEmptyStatus())
        bindings.setStatus("hello")

        hasService = false
        assertFalse(bindings.hasPlaybackService())
        assertEquals(
            listOf("apply:done", "move:1:2", "nowBar", "tab", "confirm", "status:hello"),
            calls
        )
    }
}
