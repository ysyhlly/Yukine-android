package app.yukine.playback;

import app.yukine.playback.manager.PlaybackSleepTimerManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackSleepTimerCommandOwnerTest {
    @Test
    public void delegatesSleepTimerActionsToPlaybackCommands() {
        List<String> events = new ArrayList<>();
        PlaybackSleepTimerCommandOwner owner = new PlaybackSleepTimerCommandOwner(
                new FakePlaybackCommands(events),
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
                new FakePlaybackCommands(events),
                () -> events.add("publishOwner"),
                () -> manager
        );

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
                new FakePlaybackCommands(events),
                () -> events.add("publishOwner"),
                () -> manager
        );

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
    public void toleratesMissingSleepTimerManagerProvider() {
        PlaybackSleepTimerCommandOwner owner = new PlaybackSleepTimerCommandOwner(
                new FakePlaybackCommands(new ArrayList<>()),
                () -> {
                }
        );

        owner.cancelSleepTimer(false);
        owner.cancelSleepTimer(true);
        owner.startSleepTimerMinutes(3);

        assertEquals(0L, owner.sleepTimerRemainingMs());
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

    private static final class FakePlaybackCommands implements PlaybackNotificationCommandOwner.PlaybackCommands {
        private final List<String> events;

        FakePlaybackCommands(List<String> events) {
            this.events = events;
        }

        @Override
        public void play() {
            events.add("play");
        }

        @Override
        public void pause() {
            events.add("pause");
        }

        @Override
        public void skipToPrevious() {
            events.add("previous");
        }

        @Override
        public void skipToNext() {
            events.add("next");
        }

        @Override
        public void toggleCurrentFavorite() {
            events.add("favorite");
        }

        @Override
        public void restoreLastPlayback(boolean playWhenReady) {
            events.add("restore:" + playWhenReady);
        }

        @Override
        public void stopAndClear() {
            events.add("stopAndClear");
        }
    }
}
