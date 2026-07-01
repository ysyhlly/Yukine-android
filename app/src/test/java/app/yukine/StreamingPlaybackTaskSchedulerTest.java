package app.yukine;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

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
}
