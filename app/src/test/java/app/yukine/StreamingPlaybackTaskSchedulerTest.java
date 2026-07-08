package app.yukine;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StreamingPlaybackTaskSchedulerTest {
    @Test
    public void currentPlaybackTasksDoNotWaitForActiveNextTrackResolve() {
        StreamingPlaybackTaskScheduler scheduler = new StreamingPlaybackTaskScheduler();
        StringBuilder order = new StringBuilder();
        final Runnable[] nextCompletion = new Runnable[1];

        scheduler.scheduleNextUrlResolve(completion -> {
            order.append("next-start,");
            nextCompletion[0] = completion;
        });
        scheduler.scheduleNextUrlResolve(completion -> {
            order.append("next-queued,");
            completion.run();
        });
        scheduler.scheduleCurrentUrlResolve(completion -> {
            order.append("current,");
            completion.run();
        });
        scheduler.scheduleCurrentPlaybackRecovery(completion -> {
            order.append("recovery,");
            completion.run();
        });

        if (nextCompletion[0] != null) {
            nextCompletion[0].run();
        }

        Assert.assertEquals("next-start,current,recovery,next-queued,", order.toString());
    }

    @Test
    public void shutdownClearsQueuedWorkAndIgnoresLateCompletion() {
        StreamingPlaybackTaskScheduler scheduler = new StreamingPlaybackTaskScheduler();
        StringBuilder order = new StringBuilder();
        final Runnable[] firstCompletion = new Runnable[1];

        scheduler.scheduleCurrentUrlResolve(completion -> {
            order.append("first,");
            firstCompletion[0] = completion;
        });
        scheduler.scheduleCurrentUrlResolve(completion -> {
            order.append("queued,");
            completion.run();
        });

        scheduler.shutdownNow();
        if (firstCompletion[0] != null) {
            firstCompletion[0].run();
        }

        Assert.assertEquals("first,", order.toString());
    }

    @Test
    public void shutdownIgnoresNewWork() {
        StreamingPlaybackTaskScheduler scheduler = new StreamingPlaybackTaskScheduler();
        AtomicInteger calls = new AtomicInteger();

        scheduler.shutdownNow();
        scheduler.scheduleCurrentPlaybackRecovery(completion -> {
            calls.incrementAndGet();
            completion.run();
        });
        scheduler.scheduleCurrentUrlResolve(completion -> {
            calls.incrementAndGet();
            completion.run();
        });
        scheduler.scheduleNextUrlResolve(completion -> {
            calls.incrementAndGet();
            completion.run();
        });

        Assert.assertEquals(0, calls.get());
    }

    @Test
    public void throwingCriticalTaskReportsFailureAndDrainsNextCriticalTask() {
        AtomicReference<StreamingPlaybackTaskScheduler.Priority> failedPriority = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        StreamingPlaybackTaskScheduler scheduler = new StreamingPlaybackTaskScheduler((priority, exception) -> {
            failedPriority.set(priority);
            failure.set(exception);
        });
        StringBuilder order = new StringBuilder();

        scheduler.scheduleCurrentUrlResolve(completion -> {
            order.append("throw,");
            throw new IllegalStateException("critical");
        });
        scheduler.scheduleCurrentPlaybackRecovery(completion -> {
            order.append("recovery,");
            completion.run();
        });

        Assert.assertEquals("throw,recovery,", order.toString());
        Assert.assertEquals(StreamingPlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE, failedPriority.get());
        Assert.assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    public void throwingNextResolveTaskReportsFailureAndDrainsNextResolveTask() {
        AtomicReference<StreamingPlaybackTaskScheduler.Priority> failedPriority = new AtomicReference<>();
        StreamingPlaybackTaskScheduler scheduler = new StreamingPlaybackTaskScheduler((priority, exception) ->
                failedPriority.set(priority)
        );
        StringBuilder order = new StringBuilder();

        scheduler.scheduleNextUrlResolve(completion -> {
            order.append("throw,");
            throw new IllegalStateException("next");
        });
        scheduler.scheduleNextUrlResolve(completion -> {
            order.append("next,");
            completion.run();
        });

        Assert.assertEquals("throw,next,", order.toString());
        Assert.assertEquals(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, failedPriority.get());
    }
}
