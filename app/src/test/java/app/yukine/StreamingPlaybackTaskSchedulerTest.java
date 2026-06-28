package app.yukine;

import org.junit.Assert;
import org.junit.Test;

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
}
