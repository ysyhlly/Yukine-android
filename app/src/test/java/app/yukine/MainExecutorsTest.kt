package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MainExecutorsTest {
    @Test
    fun tasksRunBeforeShutdown() {
        val executors = MainExecutors()
        val latch = CountDownLatch(3)

        try {
            executors.io { latch.countDown() }
            executors.lyrics { latch.countDown() }
            executors.network { latch.countDown() }

            assertTrue(latch.await(1, TimeUnit.SECONDS))
        } finally {
            executors.shutdownNow()
        }
    }

    @Test
    fun tasksAfterShutdownAreIgnored() {
        val executors = MainExecutors()
        val calls = AtomicInteger()

        executors.shutdownNow()
        executors.io { calls.incrementAndGet() }
        executors.lyrics { calls.incrementAndGet() }
        executors.network { calls.incrementAndGet() }

        Thread.sleep(100L)
        assertEquals(0, calls.get())
    }
}
