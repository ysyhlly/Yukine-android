package app.yukine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StreamingPlaybackTaskSchedulerTest {
    @Test
    public void runsHigherPriorityTaskBeforeQueuedNextTrackWork() {
        StreamingPlaybackTaskScheduler scheduler = new StreamingPlaybackTaskScheduler();
        StringBuilder order = new StringBuilder();
        final StreamingPlaybackTaskScheduler.Completion[] lowCompletion = new StreamingPlaybackTaskScheduler.Completion[1];

        scheduler.schedule(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, completion -> {
            order.append("next-start,");
            lowCompletion[0] = completion;
        });
        scheduler.schedule(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, completion -> {
            order.append("next-queued,");
            completion.complete();
        });
        scheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, completion -> {
            order.append("recovery,");
            completion.complete();
        });

        lowCompletion[0].complete();

        assertEquals("next-start,recovery,next-queued,", order.toString());
    }
}
