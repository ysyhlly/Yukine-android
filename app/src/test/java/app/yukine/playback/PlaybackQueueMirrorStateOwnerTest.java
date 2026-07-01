package app.yukine.playback;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PlaybackQueueMirrorStateOwnerTest {
    @Test
    public void delegatesMirrorStateToRuntimeOperations() {
        FakeMirrorStateOperations operations = new FakeMirrorStateOperations();
        PlaybackQueueMirrorStateOwner owner = new PlaybackQueueMirrorStateOwner(() -> operations);

        assertFalse(owner.playerMirrorsQueue());
        owner.setPlayerMirrorsQueue(true);
        assertTrue(owner.playerMirrorsQueue());
        owner.setPlayerMirrorsQueue(false);
        assertFalse(owner.playerMirrorsQueue());
    }

    @Test
    public void missingRuntimeOperationsFallBackToNotMirrored() {
        PlaybackQueueMirrorStateOwner missingOperations = new PlaybackQueueMirrorStateOwner(() -> null);
        PlaybackQueueMirrorStateOwner missingProvider = new PlaybackQueueMirrorStateOwner(null);

        missingOperations.setPlayerMirrorsQueue(true);
        missingProvider.setPlayerMirrorsQueue(true);

        assertFalse(missingOperations.playerMirrorsQueue());
        assertFalse(missingProvider.playerMirrorsQueue());
    }

    private static final class FakeMirrorStateOperations
            implements PlaybackQueueMirrorStateOwner.MirrorStateOperations {
        private boolean playerMirrorsQueue;

        @Override
        public boolean playerMirrorsQueue() {
            return playerMirrorsQueue;
        }

        @Override
        public void setPlayerMirrorsQueue(boolean enabled) {
            playerMirrorsQueue = enabled;
        }
    }
}
