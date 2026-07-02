package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class PlaybackShutdownServiceResourcesOwnerTest {
    @Test
    public void delegatesEachShutdownResourceAction() {
        List<String> calls = new ArrayList<>();
        PlaybackShutdownServiceResourcesOwner owner = owner(calls);

        owner.unregisterNoisyReceiver();
        owner.releaseWarmup();
        owner.releaseVisualizationAnalyzer();
        owner.releaseRecoveryScheduler();
        owner.shutdownTaskSchedulers();
        owner.releaseErrorRecovery();
        owner.releaseProgressUpdates();
        owner.releaseSleepTimer();
        owner.releaseCrossfade();
        owner.clearMainCallbacks();
        owner.releaseVisualizationCache();
        owner.releaseNotificationArtwork();
        owner.releasePrecache();
        owner.releaseStatePublisher();

        assertEquals(
                Arrays.asList(
                        "noisy",
                        "warmup",
                        "analyzer",
                        "recovery-scheduler",
                        "schedulers",
                        "recovery",
                        "progress",
                        "sleep",
                        "crossfade",
                        "callbacks",
                        "visualization",
                        "artwork",
                        "precache",
                        "state"
                ),
                calls
        );
    }

    @Test
    public void ignoresMissingResourceActions() {
        PlaybackShutdownServiceResourcesOwner owner = new PlaybackShutdownServiceResourcesOwner(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        owner.unregisterNoisyReceiver();
        owner.releaseWarmup();
        owner.releaseVisualizationAnalyzer();
        owner.releaseRecoveryScheduler();
        owner.shutdownTaskSchedulers();
        owner.releaseErrorRecovery();
        owner.releaseProgressUpdates();
        owner.releaseSleepTimer();
        owner.releaseCrossfade();
        owner.clearMainCallbacks();
        owner.releaseVisualizationCache();
        owner.releaseNotificationArtwork();
        owner.releasePrecache();
        owner.releaseStatePublisher();
    }

    @Test
    public void ownsPlaybackTaskSchedulerShutdownList() {
        AtomicInteger executedTasks = new AtomicInteger();
        PlaybackTaskScheduler first = new PlaybackTaskScheduler("first-test-shutdown-scheduler");
        PlaybackTaskScheduler second = new PlaybackTaskScheduler("second-test-shutdown-scheduler");

        PlaybackShutdownServiceResourcesOwner.shutdownPlaybackTaskSchedulers(first, null, second).run();
        first.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, executedTasks::incrementAndGet);
        second.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, executedTasks::incrementAndGet);

        assertEquals(0, executedTasks.get());
    }

    @Test
    public void releaseFromRunsProvidedActionWhenResourceExists() {
        AtomicReference<String> released = new AtomicReference<>();

        PlaybackShutdownServiceResourcesOwner.releaseFrom(
                () -> "resource",
                released::set
        ).run();

        assertEquals("resource", released.get());
    }

    @Test
    public void releaseFromIgnoresMissingProviderResourceOrAction() {
        PlaybackShutdownServiceResourcesOwner.releaseFrom(null, resource -> {
            throw new AssertionError("missing provider should not run release action");
        }).run();
        PlaybackShutdownServiceResourcesOwner.releaseFrom(() -> null, resource -> {
            throw new AssertionError("missing resource should not run release action");
        }).run();
        PlaybackShutdownServiceResourcesOwner.releaseFrom(() -> "resource", null).run();
    }

    private static PlaybackShutdownServiceResourcesOwner owner(List<String> calls) {
        return new PlaybackShutdownServiceResourcesOwner(
                () -> calls.add("noisy"),
                () -> calls.add("warmup"),
                () -> calls.add("analyzer"),
                () -> calls.add("recovery-scheduler"),
                () -> calls.add("schedulers"),
                () -> calls.add("recovery"),
                () -> calls.add("progress"),
                () -> calls.add("sleep"),
                () -> calls.add("crossfade"),
                () -> calls.add("callbacks"),
                () -> calls.add("visualization"),
                () -> calls.add("artwork"),
                () -> calls.add("precache"),
                () -> calls.add("state")
        );
    }
}
