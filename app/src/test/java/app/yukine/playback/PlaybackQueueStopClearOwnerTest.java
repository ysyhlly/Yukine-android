package app.yukine.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackQueueStopClearOwnerTest {
    @Test
    public void delegatesStopClearPreparationToCurrentOperations() {
        boolean[] called = new boolean[1];
        PlaybackQueueStopClearOwner owner = new PlaybackQueueStopClearOwner(() -> () -> called[0] = true);

        assertTrue(owner.prepareStopAndClearPlaybackState());

        assertTrue(called[0]);
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

}
