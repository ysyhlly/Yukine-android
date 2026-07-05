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
        FakeQueueNavigationActions actions = new FakeQueueNavigationActions(events, false, false, false);
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                actions::playFirstQueuedTrack,
                actions::skipToNextImmediately,
                actions::skipToPrevious,
                actions::reuseMirroredQueueIfAvailable,
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
        FakeQueueNavigationActions actions = new FakeQueueNavigationActions(events, true, true, false);
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                actions::playFirstQueuedTrack,
                actions::skipToNextImmediately,
                actions::skipToPrevious,
                actions::reuseMirroredQueueIfAvailable,
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
        List<String> events = new ArrayList<>();
        PlaybackQueueNavigationOwner missingActions = new PlaybackQueueNavigationOwner(null, null, null, null, null);
        FakeQueueNavigationActions actions = new FakeQueueNavigationActions(events, true, true, true);
        PlaybackQueueNavigationOwner missingReuseHandler = new PlaybackQueueNavigationOwner(
                actions::playFirstQueuedTrack,
                actions::skipToNextImmediately,
                actions::skipToPrevious,
                actions::reuseMirroredQueueIfAvailable,
                null
        );

        missingActions.skipToNextImmediately();
        missingActions.playFirstQueuedTrack();
        missingActions.skipToPrevious();
        missingActions.reuseMirroredQueueIfAvailable(true, 200L);
        missingReuseHandler.skipToNextImmediately();
        missingReuseHandler.skipToPrevious();
        missingReuseHandler.reuseMirroredQueueIfAvailable(false, 300L);

        assertEquals(Arrays.asList("next", "previous", "reuseExisting:false:300"), events);
    }

    @Test
    public void delegatesExistingMirroredQueueReuseAndNotifiesBoundary() {
        List<String> events = new ArrayList<>();
        FakeQueueNavigationActions actions = new FakeQueueNavigationActions(events, false, false, true);
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                actions::playFirstQueuedTrack,
                actions::skipToNextImmediately,
                actions::skipToPrevious,
                actions::reuseMirroredQueueIfAvailable,
                playWhenReady -> events.add("reuse:" + playWhenReady)
        );

        assertTrue(owner.reuseMirroredQueueIfAvailable(true, 4500L));

        assertEquals(Arrays.asList("reuseExisting:true:4500", "reuse:true"), events);
    }

    @Test
    public void doesNotNotifyBoundaryWhenExistingMirroredQueueCannotBeReused() {
        List<String> events = new ArrayList<>();
        FakeQueueNavigationActions actions = new FakeQueueNavigationActions(events, false, false, false);
        PlaybackQueueNavigationOwner owner = new PlaybackQueueNavigationOwner(
                actions::playFirstQueuedTrack,
                actions::skipToNextImmediately,
                actions::skipToPrevious,
                actions::reuseMirroredQueueIfAvailable,
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

    private static final class FakeQueueNavigationActions {
        private final List<String> events;
        private final boolean nextReusesMirroredQueue;
        private final boolean previousReusesMirroredQueue;
        private final boolean reusesExistingMirroredQueue;
        private FakeQueueNavigationActions(
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

        public void playFirstQueuedTrack() {
            events.add("first");
        }

        public boolean skipToNextImmediately() {
            events.add("next");
            return nextReusesMirroredQueue;
        }

        public boolean skipToPrevious() {
            events.add("previous");
            return previousReusesMirroredQueue;
        }

        public boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
            events.add("reuseExisting:" + playWhenReady + ":" + startPositionMs);
            return reusesExistingMirroredQueue;
        }

    }

}
