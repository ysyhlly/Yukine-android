package app.yukine.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityMutationGateTest {
    @Test
    fun concurrentIdentityMutationsAreSerialized() {
        val executor = Executors.newFixedThreadPool(4)
        val ready = CountDownLatch(4)
        val start = CountDownLatch(1)
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()

        try {
            val futures = (1..4).map {
                executor.submit {
                    ready.countDown()
                    assertTrue(start.await(2, TimeUnit.SECONDS))
                    IdentityMutationGate.withLock {
                        val current = active.incrementAndGet()
                        maximumActive.accumulateAndGet(current) { previous, value ->
                            maxOf(previous, value)
                        }
                        Thread.sleep(30)
                        active.decrementAndGet()
                    }
                }
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(3, TimeUnit.SECONDS) }

            assertEquals(1, maximumActive.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun nestedIdentityMutationDoesNotDeadlock() {
        val value = IdentityMutationGate.withLock {
            IdentityMutationGate.withLock { 42 }
        }

        assertEquals(42, value)
    }
}
