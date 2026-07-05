package app.yukine.playback;

import app.yukine.playback.manager.PlaybackSleepTimerManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PlaybackSleepTimerCommandOwnerTest {
    @Test
    public void delegatesSleepTimerActionsToPlaybackCommands() {
        List<String> events = new ArrayList<>();
        PlaybackSleepTimerCommandOwner owner = new PlaybackSleepTimerCommandOwner(
                () -> events.add("pause"),
                () -> events.add("publish")
        );

        owner.pausePlayback();
        owner.publishState();

        assertEquals(
                java.util.Arrays.asList(
                        "pause",
                        "publish"
                ),
                events
        );
    }

    @Test
    public void delegatesSleepTimerCancelToManager() {
        List<String> events = new ArrayList<>();
        FakeSleepScheduler scheduler = new FakeSleepScheduler(events);
        PlaybackSleepTimerManager manager = new PlaybackSleepTimerManager(
                scheduler,
                new FakeSleepActions(events),
                () -> 0L,
                240,
                60000L
        );
        PlaybackSleepTimerCommandOwner owner = new PlaybackSleepTimerCommandOwner(
                () -> events.add("pauseOwner"),
                () -> events.add("publishOwner")
        );
        owner.bindPlaybackSleepTimerManager(manager);

        owner.cancelSleepTimer(false);
        owner.cancelSleepTimer(true);

        assertEquals(
                java.util.Arrays.asList(
                        "remove",
                        "remove",
                        "publishManager"
                ),
                events
        );
    }

    @Test
    public void delegatesSleepTimerStartToManager() {
        List<String> events = new ArrayList<>();
        FakeSleepScheduler scheduler = new FakeSleepScheduler(events);
        PlaybackSleepTimerManager manager = new PlaybackSleepTimerManager(
                scheduler,
                new FakeSleepActions(events),
                () -> 0L,
                240,
                60000L
        );
        PlaybackSleepTimerCommandOwner owner = new PlaybackSleepTimerCommandOwner(
                () -> events.add("pauseOwner"),
                () -> events.add("publishOwner")
        );
        owner.bindPlaybackSleepTimerManager(manager);

        owner.startSleepTimerMinutes(3);

        assertEquals(180000L, owner.sleepTimerRemainingMs());
        assertEquals(
                java.util.Arrays.asList(
                        "remove",
                        "post:60000",
                        "publishManager"
                ),
                events
        );
    }

    @Test
    public void requiresBoundSleepTimerManager() {
        PlaybackSleepTimerCommandOwner owner = new PlaybackSleepTimerCommandOwner(
                () -> {
                },
                () -> {
                }
        );

        assertEquals(
                "sleepTimerManager",
                assertThrows(NullPointerException.class, () -> owner.cancelSleepTimer(false)).getMessage()
        );
        assertEquals(
                "sleepTimerManager",
                assertThrows(NullPointerException.class, () -> owner.startSleepTimerMinutes(3)).getMessage()
        );
        assertEquals(
                "sleepTimerManager",
                assertThrows(NullPointerException.class, owner::sleepTimerRemainingMs).getMessage()
        );
    }

    private static final class FakeSleepScheduler implements PlaybackSleepTimerManager.CallbackScheduler {
        private final List<String> events;

        FakeSleepScheduler(List<String> events) {
            this.events = events;
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            events.add("post:" + delayMs);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            events.add("remove");
        }
    }

    private static final class FakeSleepActions implements PlaybackSleepTimerManager.Actions {
        private final List<String> events;

        FakeSleepActions(List<String> events) {
            this.events = events;
        }

        @Override
        public void pausePlayback() {
            events.add("pauseManager");
        }

        @Override
        public void publishState() {
            events.add("publishManager");
        }
    }

}
