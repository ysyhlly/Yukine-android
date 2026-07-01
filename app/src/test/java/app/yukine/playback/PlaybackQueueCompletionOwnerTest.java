package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.yukine.playback.manager.PlaybackQueueManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaybackQueueCompletionOwnerTest {
    @Test
    public void routesRepeatCurrentCompletionThroughQueuePreparationAndBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = new PlaybackQueueCompletionOwner(
                () -> new FakeQueueCompletionOperations(
                        events,
                        PlaybackQueueManager.PlaybackCompletionAction.REPEAT_CURRENT
                ),
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        assertEquals(
                Arrays.asList("action", "prepare:REPEAT_CURRENT", "prepareCurrent:true"),
                events
        );
    }

    @Test
    public void routesStopAtEndCompletionThroughQueuePreparationAndBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = new PlaybackQueueCompletionOwner(
                () -> new FakeQueueCompletionOperations(
                        events,
                        PlaybackQueueManager.PlaybackCompletionAction.STOP_AT_END
                ),
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        assertEquals(
                Arrays.asList("action", "prepare:STOP_AT_END", "stopAtEnd"),
                events
        );
    }

    @Test
    public void routesAdvanceCompletionThroughQueuePreparationAndBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = new PlaybackQueueCompletionOwner(
                () -> new FakeQueueCompletionOperations(
                        events,
                        PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT
                ),
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        assertEquals(
                Arrays.asList("action", "prepare:ADVANCE_TO_NEXT", "skipNext"),
                events
        );
    }

    @Test
    public void stopAndClearCompletionDoesNotPrepareQueueCompletion() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = new PlaybackQueueCompletionOwner(
                () -> new FakeQueueCompletionOperations(
                        events,
                        PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                ),
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        assertEquals(Arrays.asList("action", "stopAndClear"), events);
    }

    @Test
    public void delegatesStopPreparationsToQueueOperations() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = new PlaybackQueueCompletionOwner(
                () -> new FakeQueueCompletionOperations(
                        events,
                        PlaybackQueueManager.PlaybackCompletionAction.STOP_AT_END
                ),
                null
        );

        assertTrue(owner.prepareStopAtEndOfQueue());
        owner.prepareStopAfterAutomaticAdvance(7);

        assertEquals(Arrays.asList("prepareStopAtEnd", "prepareStopAfterAutomaticAdvance:7"), events);
    }

    @Test
    public void missingQueueOperationsUseStopAndClearBoundaryAndReportUnhandledStopAtEnd() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner missingSupplier = new PlaybackQueueCompletionOwner(
                null,
                new FakeCompletionBoundary(events)
        );
        PlaybackQueueCompletionOwner missingOperations = new PlaybackQueueCompletionOwner(
                () -> null,
                new FakeCompletionBoundary(events)
        );

        missingSupplier.playAfterCompletion();
        assertFalse(missingSupplier.prepareStopAtEndOfQueue());
        missingSupplier.prepareStopAfterAutomaticAdvance(5);
        missingOperations.playAfterCompletion();
        assertFalse(missingOperations.prepareStopAtEndOfQueue());
        missingOperations.prepareStopAfterAutomaticAdvance(6);

        assertEquals(Arrays.asList("stopAndClear", "stopAndClear"), events);
    }

    @Test
    public void missingPlaybackQueueManagerSupplierUsesStopAndClearBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = PlaybackQueueCompletionOwner.fromPlaybackQueueManager(
                null,
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        assertFalse(owner.prepareStopAtEndOfQueue());
        owner.prepareStopAfterAutomaticAdvance(7);
        assertEquals(Arrays.asList("stopAndClear"), events);
    }

    @Test
    public void missingBoundaryDoesNotCrash() {
        PlaybackQueueCompletionOwner owner = new PlaybackQueueCompletionOwner(
                () -> new FakeQueueCompletionOperations(
                        new ArrayList<>(),
                        PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT
                ),
                null
        );

        owner.playAfterCompletion();
    }

    private static final class FakeQueueCompletionOperations
            implements PlaybackQueueCompletionOwner.QueueCompletionOperations {
        private final List<String> events;
        private final PlaybackQueueManager.PlaybackCompletionAction completionAction;

        private FakeQueueCompletionOperations(
                List<String> events,
                PlaybackQueueManager.PlaybackCompletionAction completionAction
        ) {
            this.events = events;
            this.completionAction = completionAction;
        }

        @Override
        public PlaybackQueueManager.PlaybackCompletionAction playbackCompletionAction() {
            events.add("action");
            return completionAction;
        }

        @Override
        public void preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction action) {
            events.add("prepare:" + action.name());
        }

        @Override
        public void prepareStopAtEndOfQueue() {
            events.add("prepareStopAtEnd");
        }

        @Override
        public void prepareStopAfterAutomaticAdvance(int completedIndex) {
            events.add("prepareStopAfterAutomaticAdvance:" + completedIndex);
        }
    }

    private static final class FakeCompletionBoundary
            implements PlaybackQueueCompletionOwner.CompletionBoundary {
        private final List<String> events;

        private FakeCompletionBoundary(List<String> events) {
            this.events = events;
        }

        @Override
        public void stopAndClear() {
            events.add("stopAndClear");
        }

        @Override
        public void prepareCurrent(boolean playWhenReady) {
            events.add("prepareCurrent:" + playWhenReady);
        }

        @Override
        public void stopAtEndOfQueue() {
            events.add("stopAtEnd");
        }

        @Override
        public void skipToNext() {
            events.add("skipNext");
        }
    }
}
