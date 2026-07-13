package app.yukine

import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainQueueActionListenerTest {
    @Test
    fun delegatesQueueActionCallbacksToInjectedOwners() {
        val result = PlaybackActionResultUi("moved")
        val appliedResults = mutableListOf<PlaybackActionResultUi?>()
        val moves = mutableListOf<Pair<Int, Int>>()
        val calls = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val listener = MainQueueActionListener(
            resultApplier = QueuePlaybackActionResultApplier { appliedResults += it },
            serviceAvailability = QueuePlaybackServiceAvailability { true },
            trackMoveSink = QueueTrackMoveSink { fromIndex, toIndex -> moves += fromIndex to toIndex },
            clearQueueConfirmer = QueueClearQueueConfirmer { calls += "confirmClear" },
            emptyStatusProvider = QueueEmptyStatusProvider { "Queue empty" },
            statusSink = QueueStatusSink { statuses += it }
        )

        listener.applyPlaybackActionResult(result)
        listener.moveQueueTrack(1, 3)
        listener.confirmClearQueue()
        listener.setStatus(listener.queueEmptyStatus())

        assertEquals(true, listener.hasPlaybackService())
        assertSame(result, appliedResults.single())
        assertEquals(listOf(1 to 3), moves)
        assertEquals(listOf("confirmClear"), calls)
        assertEquals(listOf("Queue empty"), statuses)
    }

    @Test
    fun directConstructionCreatesQueueActionControllerListener() {
        val appliedResults = mutableListOf<PlaybackActionResultUi?>()
        val calls = mutableListOf<String>()
        val listener = MainQueueActionListener(
            QueuePlaybackActionResultApplier { appliedResults += it },
            QueuePlaybackServiceAvailability { false },
            QueueTrackMoveSink { fromIndex, toIndex -> calls += "move:$fromIndex:$toIndex" },
            QueueClearQueueConfirmer { calls += "confirmClear" },
            QueueEmptyStatusProvider { "Queue empty" },
            QueueStatusSink { calls += "status:$it" }
        )
        val result = PlaybackActionResultUi(null)

        listener.applyPlaybackActionResult(result)
        listener.moveQueueTrack(0, 2)
        listener.confirmClearQueue()
        listener.setStatus(listener.queueEmptyStatus())

        assertEquals(false, listener.hasPlaybackService())
        assertSame(result, appliedResults.single())
        assertEquals(listOf("move:0:2", "confirmClear", "status:Queue empty"), calls)
    }
}
