package app.yukine.playback;

import app.yukine.playback.manager.PlaybackProgressUpdateManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackProgressUpdateCommandOwnerTest {
    @Test
    public void delegatesProgressTickActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackProgressUpdateCommandOwner owner = new PlaybackProgressUpdateCommandOwner(
                () -> events.add("publish"),
                force -> events.add("persist:" + force)
        );

        owner.publishState();
        owner.persistPlaybackPosition();

        assertEquals(
                Arrays.asList(
                        "publish",
                        "persist:false"
                ),
                events
        );
    }

    @Test
    public void delegatesProgressUpdateLifecycleToManager() {
        FakeProgressScheduler scheduler = new FakeProgressScheduler();
        FakeProgressState state = new FakeProgressState();
        state.playing = true;
        PlaybackProgressUpdateManager manager = new PlaybackProgressUpdateManager(
                scheduler,
                state,
                new FakeProgressActions(),
                1000L
        );
        PlaybackProgressUpdateCommandOwner owner = new PlaybackProgressUpdateCommandOwner(
                () -> {
                },
                force -> {
                },
                () -> manager
        );

        owner.startProgressUpdates();
        owner.stopProgressUpdates();

        assertEquals(Arrays.asList(1000L), scheduler.delays);
        assertEquals(2, scheduler.removeCallbacks);
    }

    @Test
    public void toleratesMissingProgressUpdateManagerProvider() {
        PlaybackProgressUpdateCommandOwner owner = new PlaybackProgressUpdateCommandOwner(
                () -> {
                },
                force -> {
                }
        );

        owner.startProgressUpdates();
        owner.stopProgressUpdates();
    }

    private static final class FakeProgressScheduler
            implements PlaybackProgressUpdateManager.CallbackScheduler {
        final List<Long> delays = new ArrayList<>();
        int removeCallbacks;

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            delays.add(delayMs);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            removeCallbacks++;
        }
    }

    private static final class FakeProgressState
            implements PlaybackProgressUpdateManager.StateProvider {
        boolean playing;
        boolean preparing;

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public boolean isPreparing() {
            return preparing;
        }
    }

    private static final class FakeProgressActions
            implements PlaybackProgressUpdateManager.Actions {
        @Override
        public void publishState() {
        }

        @Override
        public void persistPlaybackPosition() {
        }
    }
}
