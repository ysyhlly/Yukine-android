package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class PlaybackTaskSchedulerTest {
    @Test
    public void runtimeExceptionDoesNotStopSchedulerWorker() throws Exception {
        AtomicReference<PlaybackTaskScheduler.Priority> failedPriority = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        PlaybackTaskScheduler scheduler = new PlaybackTaskScheduler(
                "test-playback-task-scheduler",
                Process.THREAD_PRIORITY_BACKGROUND,
                () -> {
                },
                (priority, exception) -> {
                    failedPriority.set(priority);
                    failure.set(exception);
                }
        );
        CountDownLatch afterFailure = new CountDownLatch(1);

        scheduler.schedule(
                PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                () -> {
                    throw new IllegalStateException("boom");
                }
        );
        scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, afterFailure::countDown);

        assertTrue(afterFailure.await(2, TimeUnit.SECONDS));
        assertEquals(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, failedPriority.get());
        assertTrue(failure.get() instanceof IllegalStateException);
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
    public void scheduledTasksRunByPlaybackPriorityBeforeSequenceOrder() throws Exception {
        CountDownLatch firstTaskDequeued = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        CountDownLatch scheduledTasksFinished = new CountDownLatch(2);
        AtomicBoolean blockFirstTask = new AtomicBoolean(true);
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        PlaybackTaskScheduler scheduler = new PlaybackTaskScheduler(
                "test-playback-task-scheduler",
                Process.THREAD_PRIORITY_BACKGROUND,
                () -> {
                    if (blockFirstTask.compareAndSet(true, false)) {
                        firstTaskDequeued.countDown();
                        try {
                            releaseFirstTask.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
        );

        scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, () -> {
        });
        assertTrue(firstTaskDequeued.await(2, TimeUnit.SECONDS));

        scheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, () -> {
            events.add("next-precache");
            scheduledTasksFinished.countDown();
        });
        scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE, () -> {
            events.add("current-url");
            scheduledTasksFinished.countDown();
        });
        releaseFirstTask.countDown();

        assertTrue(scheduledTasksFinished.await(2, TimeUnit.SECONDS));
        scheduler.shutdownNow();
        assertEquals(2, events.size());
        assertEquals("current-url", events.get(0));
        assertEquals("next-precache", events.get(1));
    }

    @Test
    public void executeAfterShutdownIsIgnored() throws Exception {
        PlaybackTaskScheduler scheduler = scheduler();
        AtomicInteger calls = new AtomicInteger();

        scheduler.shutdownNow();
        assertFalse(scheduler.schedule(
                PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE,
                calls::incrementAndGet
        ));
        scheduler.execute(calls::incrementAndGet);

        Thread.sleep(100L);
        assertEquals(0, calls.get());
    }

    @Test
    public void nullTasksAreIgnored() throws Exception {
        PlaybackTaskScheduler scheduler = scheduler();

        assertFalse(scheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, null));
        scheduler.execute(null);

        scheduler.shutdownNow();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    private static PlaybackTaskScheduler scheduler() {
        return new PlaybackTaskScheduler("test-playback-task-scheduler", Process.THREAD_PRIORITY_BACKGROUND);
    }
}
