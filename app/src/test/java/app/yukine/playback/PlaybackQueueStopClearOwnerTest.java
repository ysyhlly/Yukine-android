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
        PlaybackQueueStopClearOwner missingSupplier = new PlaybackQueueStopClearOwner(null);
        PlaybackQueueStopClearOwner missingOperations = new PlaybackQueueStopClearOwner(() -> null);

        assertFalse(missingSupplier.prepareStopAndClearPlaybackState());
        assertFalse(missingOperations.prepareStopAndClearPlaybackState());
    }

    @Test
    public void missingPlaybackQueueManagerSupplierReportsUnhandled() {
        PlaybackQueueStopClearOwner owner =
                PlaybackQueueStopClearOwner.fromPlaybackQueueManager(null);

        assertFalse(owner.prepareStopAndClearPlaybackState());
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
