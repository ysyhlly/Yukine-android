package app.yukine.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackQueueStopClearOwnerTest {
    @Test
    public void delegatesStopClearPreparationToCurrentOperations() {
        FakeQueueStopClearOperations operations = new FakeQueueStopClearOperations();
        PlaybackQueueStopClearOwner owner = new PlaybackQueueStopClearOwner(() -> operations);

        assertTrue(owner.prepareStopAndClearPlaybackState());

        assertTrue(operations.called);
    }

    @Test
    public void reportsUnhandledWhenDependenciesAreMissing() {
        PlaybackQueueStopClearOwner missingProvider = new PlaybackQueueStopClearOwner(null);
        PlaybackQueueStopClearOwner missingOperations = new PlaybackQueueStopClearOwner(() -> null);

        assertFalse(missingProvider.prepareStopAndClearPlaybackState());
        assertFalse(missingOperations.prepareStopAndClearPlaybackState());
    }

    private static final class FakeQueueStopClearOperations
            implements PlaybackQueueStopClearOwner.QueueStopClearOperations {
        private boolean called;

        @Override
        public void prepareStopAndClearPlaybackState() {
            called = true;
        }
    }
}
