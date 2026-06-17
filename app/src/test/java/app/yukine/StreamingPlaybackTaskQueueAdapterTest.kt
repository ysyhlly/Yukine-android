package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingPlaybackTaskQueueAdapterTest {
    @Test
    fun schedulesTasksWithPlaybackPriorityOrder() {
        val scheduler = StreamingPlaybackTaskScheduler()
        val adapter = StreamingPlaybackTaskQueueAdapter(scheduler)
        val order = StringBuilder()
        var lowCompletion: Runnable? = null

        adapter.scheduleNextUrlResolve { completion ->
            order.append("next-start,")
            lowCompletion = completion
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

        lowCompletion?.run()

        assertEquals("next-start,recovery,current,next-queued,", order.toString())
    }
}
