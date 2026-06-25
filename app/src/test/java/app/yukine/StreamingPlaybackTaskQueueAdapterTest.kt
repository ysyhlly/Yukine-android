package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPlaybackTaskQueueAdapterTest {
    @Test
    fun currentPlaybackTasksDoNotWaitForActiveNextTrackResolve() {
        val scheduler = StreamingPlaybackTaskScheduler()
        val adapter = StreamingPlaybackTaskQueueAdapter(scheduler)
        val order = StringBuilder()
        var nextCompletion: Runnable? = null

        adapter.scheduleNextUrlResolve { completion ->
            order.append("next-start,")
            nextCompletion = completion
        }
        adapter.scheduleNextUrlResolve { completion ->
            order.append("next-queued,")
            completion.run()
        }
        adapter.scheduleCurrentUrlResolve { completion ->
            order.append("current,")
            completion.run()
        }
        adapter.scheduleCurrentPlaybackRecovery { completion ->
            order.append("recovery,")
            completion.run()
        }

        nextCompletion?.run()

        assertEquals("next-start,current,recovery,next-queued,", order.toString())
    }
}
