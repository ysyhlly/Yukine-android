package app.yukine.playback;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PlaybackQueueMirrorStateOwnerTest {
    @Test
    public void delegatesMirrorStateToRuntimeOperations() {
        FakeMirrorStateActions actions = new FakeMirrorStateActions();
        PlaybackQueueMirrorStateOwner owner = new PlaybackQueueMirrorStateOwner(
                actions::playerMirrorsQueue,
                actions::setPlayerMirrorsQueue
        );

        assertFalse(owner.playerMirrorsQueue());
        owner.setPlayerMirrorsQueue(true);
        assertTrue(owner.playerMirrorsQueue());
        owner.setPlayerMirrorsQueue(false);
        assertFalse(owner.playerMirrorsQueue());
    }

    @Test
    public void missingRuntimeOperationsFallBackToNotMirrored() {
        PlaybackQueueMirrorStateOwner missingOperations =
                new PlaybackQueueMirrorStateOwner(null, null);

        missingOperations.setPlayerMirrorsQueue(true);

        assertFalse(missingOperations.playerMirrorsQueue());
    }

    private static final class FakeMirrorStateActions {
        private boolean playerMirrorsQueue;

        public boolean playerMirrorsQueue() {
            return playerMirrorsQueue;
        }

        public void setPlayerMirrorsQueue(boolean enabled) {
            playerMirrorsQueue = enabled;
        }
    }
}
