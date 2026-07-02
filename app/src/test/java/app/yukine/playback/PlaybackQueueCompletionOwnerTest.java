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
        PlaybackQueueCompletionOwner owner = owner(
                new FakeQueueCompletionActions(
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
        PlaybackQueueCompletionOwner owner = owner(
                new FakeQueueCompletionActions(
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
        PlaybackQueueCompletionOwner owner = owner(
                new FakeQueueCompletionActions(
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
        PlaybackQueueCompletionOwner owner = owner(
                new FakeQueueCompletionActions(
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
        PlaybackQueueCompletionOwner owner = owner(
                new FakeQueueCompletionActions(
                        events,
                        PlaybackQueueManager.PlaybackCompletionAction.STOP_AT_END
                ),
                null
        );

        assertTrue(owner.prepareStopAndClearPlaybackState());
        assertTrue(owner.prepareStopAtEndOfQueue());
        owner.prepareStopAfterAutomaticAdvance(7);

        assertEquals(
                Arrays.asList(
                        "prepareStopAndClear",
                        "prepareStopAtEnd",
                        "prepareStopAfterAutomaticAdvance:7"
                ),
                events
        );
    }

    @Test
    public void missingQueueOperationsUseStopAndClearBoundaryAndReportUnhandledStopAtEnd() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner missingActions = new PlaybackQueueCompletionOwner(
                null,
                null,
                null,
                null,
                null,
                new FakeCompletionBoundary(events)
        );

        missingActions.playAfterCompletion();
        assertFalse(missingActions.prepareStopAndClearPlaybackState());
        assertFalse(missingActions.prepareStopAtEndOfQueue());
        missingActions.prepareStopAfterAutomaticAdvance(5);

        assertEquals(Arrays.asList("stopAndClear"), events);
    }

    @Test
    public void missingPlaybackQueueManagerSupplierUsesStopAndClearBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = PlaybackQueueCompletionOwner.fromPlaybackQueueManager(
                null,
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        assertFalse(owner.prepareStopAndClearPlaybackState());
        assertFalse(owner.prepareStopAtEndOfQueue());
        owner.prepareStopAfterAutomaticAdvance(7);
        assertEquals(Arrays.asList("stopAndClear"), events);
    }

    @Test
    public void missingBoundaryDoesNotCrash() {
        PlaybackQueueCompletionOwner owner = owner(
                new FakeQueueCompletionActions(
                        new ArrayList<>(),
                        PlaybackQueueManager.PlaybackCompletionAction.ADVANCE_TO_NEXT
                ),
                null
        );

        owner.playAfterCompletion();
    }

    private static PlaybackQueueCompletionOwner owner(
            FakeQueueCompletionActions actions,
            PlaybackQueueCompletionOwner.CompletionBoundary boundary
    ) {
        return new PlaybackQueueCompletionOwner(
                actions::playbackCompletionAction,
                actions::preparePlaybackCompletion,
                actions::prepareStopAndClearPlaybackState,
                actions::prepareStopAtEndOfQueue,
                actions::prepareStopAfterAutomaticAdvance,
                boundary
        );
    }

    private static final class FakeQueueCompletionActions {
        private final List<String> events;
        private final PlaybackQueueManager.PlaybackCompletionAction completionAction;

        private FakeQueueCompletionActions(
                List<String> events,
                PlaybackQueueManager.PlaybackCompletionAction completionAction
        ) {
            this.events = events;
            this.completionAction = completionAction;
        }

        public PlaybackQueueManager.PlaybackCompletionAction playbackCompletionAction() {
            events.add("action");
            return completionAction;
        }

        public void preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction action) {
            events.add("prepare:" + action.name());
        }

        public boolean prepareStopAndClearPlaybackState() {
            events.add("prepareStopAndClear");
            return true;
        }

        public boolean prepareStopAtEndOfQueue() {
            events.add("prepareStopAtEnd");
            return true;
        }

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
