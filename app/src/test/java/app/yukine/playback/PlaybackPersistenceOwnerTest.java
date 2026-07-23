package app.yukine.playback;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public final class PlaybackPersistenceOwnerTest {
    @Test
    public void flushQueuesBarrierWithoutWaitingForBlockedDatabaseWork() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        PlaybackPersistenceOwner owner = new PlaybackPersistenceOwner(null, executor);

        try {
            long startedAt = System.nanoTime();
            owner.flushPendingWrites();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue("flush blocked for " + elapsedMs + "ms", elapsedMs < 250L);
            release.countDown();
            assertTrue(executor.submit(() -> { }).get(1, TimeUnit.SECONDS) == null);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
