package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Process;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class PlaybackTaskSchedulerTest {
    @Test
    public void runtimeExceptionDoesNotStopSchedulerWorker() throws Exception {
        PlaybackTaskScheduler scheduler = scheduler();
        CountDownLatch afterFailure = new CountDownLatch(1);

        scheduler.schedule(
                PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                () -> {
                    throw new IllegalStateException("boom");
                }
        );
        scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, afterFailure::countDown);

        assertTrue(afterFailure.await(2, TimeUnit.SECONDS));
        scheduler.shutdownNow();
    }

    @Test
    public void shutdownNowClearsQueuedTasksAndRejectsNewTasks() throws Exception {
        PlaybackTaskScheduler scheduler = scheduler();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        scheduler.schedule(
                PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                () -> {
                    calls.incrementAndGet();
                    firstStarted.countDown();
                    try {
                        releaseFirst.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                }
        );
        scheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, calls::incrementAndGet);

        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        scheduler.shutdownNow();
        scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, calls::incrementAndGet);
        releaseFirst.countDown();

        Thread.sleep(100L);
        assertEquals(1, calls.get());
    }

    @Test
    public void shutdownNowPreventsDequeuedTaskFromStarting() throws Exception {
        CountDownLatch taskDequeued = new CountDownLatch(1);
        CountDownLatch allowTaskStart = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        PlaybackTaskScheduler scheduler = new PlaybackTaskScheduler(
                "test-playback-task-scheduler",
                Process.THREAD_PRIORITY_BACKGROUND,
                () -> {
                    taskDequeued.countDown();
                    try {
                        allowTaskStart.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                }
        );

        scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, calls::incrementAndGet);

        assertTrue(taskDequeued.await(2, TimeUnit.SECONDS));
        scheduler.shutdownNow();
        allowTaskStart.countDown();

        Thread.sleep(100L);
        assertEquals(0, calls.get());
    }

    @Test
    public void nullTasksAreIgnored() throws Exception {
        PlaybackTaskScheduler scheduler = scheduler();

        scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, null);
        scheduler.execute(null);

        scheduler.shutdownNow();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    private static PlaybackTaskScheduler scheduler() {
        return new PlaybackTaskScheduler("test-playback-task-scheduler", Process.THREAD_PRIORITY_BACKGROUND);
    }
}
