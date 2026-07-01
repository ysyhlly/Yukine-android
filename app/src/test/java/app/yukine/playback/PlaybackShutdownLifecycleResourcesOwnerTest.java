package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class PlaybackShutdownLifecycleResourcesOwnerTest {
    @Test
    public void delegatesLifecyclePersistenceStateAndNotification() {
        List<String> calls = new ArrayList<>();
        PlaybackShutdownLifecycleResourcesOwner owner = new PlaybackShutdownLifecycleResourcesOwner(
                () -> calls.add("position"),
                new FakeQueueLifecycleStore(calls),
                new FakePlaybackStateProvider(true, false),
                () -> true,
                () -> calls.add("notification")
        );

        owner.persistPlaybackPosition();
        owner.persistPlaybackQueue();
        owner.savePlaybackResumeRequested(owner.isPlaying() || owner.isPreparing());
        assertTrue(owner.hasNotificationWorthyState());
        owner.publishPlaybackNotification();

        assertEquals(
                Arrays.asList(
                        "position",
                        "queue",
                        "resume:true",
                        "notification"
                ),
                calls
        );
    }

    @Test
    public void reportsPreparingPlaybackForResumeRequest() {
        PlaybackShutdownLifecycleResourcesOwner owner = new PlaybackShutdownLifecycleResourcesOwner(
                null,
                null,
                new FakePlaybackStateProvider(false, true),
                null,
                null
        );

        assertFalse(owner.isPlaying());
        assertTrue(owner.isPreparing());
    }

    @Test
    public void toleratesMissingOptionalOwners() {
        PlaybackShutdownLifecycleResourcesOwner owner = new PlaybackShutdownLifecycleResourcesOwner(
                null,
                null,
                null,
                null,
                null
        );

        owner.persistPlaybackPosition();
        owner.persistPlaybackQueue();
        owner.savePlaybackResumeRequested(true);
        owner.publishPlaybackNotification();
        assertFalse(owner.isPlaying());
        assertFalse(owner.isPreparing());
        assertFalse(owner.hasNotificationWorthyState());
    }

    private static final class FakeQueueLifecycleStore
            implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore {
        private final List<String> calls;

        FakeQueueLifecycleStore(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void persistQueueState() {
            calls.add("queue");
        }

        @Override
        public void savePlaybackResumeRequested(boolean requested) {
            calls.add("resume:" + requested);
        }
    }

    private static final class FakePlaybackStateProvider
            implements PlaybackShutdownLifecycleResourcesOwner.PlaybackStateProvider {
        private final boolean playing;
        private final boolean preparing;

        FakePlaybackStateProvider(boolean playing, boolean preparing) {
            this.playing = playing;
            this.preparing = preparing;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public boolean isPreparing() {
            return preparing;
        }
    }
}
