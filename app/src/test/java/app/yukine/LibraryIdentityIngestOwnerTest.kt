package app.yukine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIdentityIngestOwnerTest {
    @Test
    fun requestsDuringActivePassAreCoalescedIntoOneFinalPass() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCompleted = CountDownLatch(1)
        val calls = AtomicInteger()
        val publications = AtomicInteger()
        val executor = Executors.newSingleThreadExecutor()
        val owner = LibraryIdentityIngestOwner(
            operations = LibraryIdentityIngestOwner.Operations {
                when (calls.incrementAndGet()) {
                    1 -> {
                        firstStarted.countDown()
                        assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
                    }
                    2 -> secondCompleted.countDown()
                }
                1
            },
            executor = executor,
            testing = true,
            identityChanged = Runnable { publications.incrementAndGet() }
        )

        try {
            owner.schedule()
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

            owner.schedule()
            owner.schedule()
            releaseFirst.countDown()

            assertTrue(secondCompleted.await(2, TimeUnit.SECONDS))
            assertEquals(2, calls.get())
            assertEquals(2, publications.get())
        } finally {
            owner.release()
        }
    }
}
