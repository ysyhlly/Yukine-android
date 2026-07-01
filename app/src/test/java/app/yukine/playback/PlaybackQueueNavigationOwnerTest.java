package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackQueueNavigationOwnerTest {
    @Test
    public void delegatesSkipCommandsToQueueOperations() {
        List<String> events = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                () -> new FakeQueueNavigationOperations(events, false, false, false),
                playWhenReady -> events.add("reuse:" + playWhenReady)
        );

        owner.playFirstQueuedTrack();
        owner.skipToNextImmediately();
        owner.skipToPrevious();

        assertEquals(Arrays.asList("first", "next", "previous"), events);
    }

    @Test
    public void notifiesWhenMirroredQueueIsReused() {
        List<String> events = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                () -> new FakeQueueNavigationOperations(events, true, true, false),
                playWhenReady -> events.add("reuse:" + playWhenReady)
        );

        owner.skipToNextImmediately();
        owner.skipToPrevious();

        assertEquals(
                Arrays.asList(
                        "next",
                        "reuse:true",
                        "previous",
                        "reuse:true"
                ),
                events
        );
    }

    @Test
    public void ignoresMissingDependencies() {
        PlaybackQueueNavigationOwner missingSupplier = new PlaybackQueueNavigationOwner(null, null);
        PlaybackQueueNavigationOwner missingOperations = new PlaybackQueueNavigationOwner(() -> null, null);
        List<String> events = new ArrayList<>();
        PlaybackQueueNavigationOwner missingReuseHandler = new PlaybackQueueNavigationOwner(
                () -> new FakeQueueNavigationOperations(events, true, true, true),
                null
        );

        missingSupplier.skipToNextImmediately();
        missingSupplier.playFirstQueuedTrack();
        missingOperations.skipToPrevious();
        missingOperations.playFirstQueuedTrack();
        missingOperations.reuseMirroredQueueIfAvailable(true, 200L);
        missingReuseHandler.skipToNextImmediately();
        missingReuseHandler.skipToPrevious();
        missingReuseHandler.reuseMirroredQueueIfAvailable(false, 300L);

        assertEquals(Arrays.asList("next", "previous", "reuseExisting:false:300"), events);
    }

    @Test
    public void delegatesExistingMirroredQueueReuseAndNotifiesBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                () -> new FakeQueueNavigationOperations(events, false, false, true),
                playWhenReady -> events.add("reuse:" + playWhenReady)
        );

        assertTrue(owner.reuseMirroredQueueIfAvailable(true, 4500L));

        assertEquals(Arrays.asList("reuseExisting:true:4500", "reuse:true"), events);
    }

    @Test
    public void doesNotNotifyBoundaryWhenExistingMirroredQueueCannotBeReused() {
        List<String> events = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                () -> new FakeQueueNavigationOperations(events, false, false, false),
                playWhenReady -> events.add("reuse:" + playWhenReady)
        );

        assertFalse(owner.reuseMirroredQueueIfAvailable(false, 100L));

        assertEquals(Collections.singletonList("reuseExisting:false:100"), events);
    }

    @Test
    public void factoryIgnoresMissingPlaybackQueueManager() {
        List<String> events = new ArrayList<>();
        PlaybackQueueNavigationOwner owner = PlaybackQueueNavigationOwner.fromPlaybackQueueManager(
                () -> null,
                playWhenReady -> events.add("reuse:" + playWhenReady)
        );

        owner.playFirstQueuedTrack();
        owner.skipToNextImmediately();
        owner.skipToPrevious();
        owner.reuseMirroredQueueIfAvailable(true, 1L);

        assertEquals(Collections.emptyList(), events);
    }

    private static final class FakeQueueNavigationOperations
            implements PlaybackQueueNavigationOwner.QueueNavigationOperations {
        private final List<String> events;
        private final boolean nextReusesMirroredQueue;
        private final boolean previousReusesMirroredQueue;
        private final boolean reusesExistingMirroredQueue;
        private FakeQueueNavigationOperations(
                List<String> events,
                boolean nextReusesMirroredQueue,
                boolean previousReusesMirroredQueue,
                boolean reusesExistingMirroredQueue
        ) {
            this.events = events;
            this.nextReusesMirroredQueue = nextReusesMirroredQueue;
            this.previousReusesMirroredQueue = previousReusesMirroredQueue;
            this.reusesExistingMirroredQueue = reusesExistingMirroredQueue;
        }

        @Override
        public void playFirstQueuedTrack() {
            events.add("first");
        }

        @Override
        public boolean skipToNextImmediately() {
            events.add("next");
            return nextReusesMirroredQueue;
        }

        @Override
        public boolean skipToPrevious() {
            events.add("previous");
            return previousReusesMirroredQueue;
        }

        @Override
        public boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
            events.add("reuseExisting:" + playWhenReady + ":" + startPositionMs);
            return reusesExistingMirroredQueue;
        }

    }

}
