package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackQueuePersistenceOwnerTest {
    @Test
    public void delegatesQueuePersistenceToCurrentOperations() {
        FakeQueuePersistenceOperations operations = new FakeQueuePersistenceOperations();
        PlaybackQueuePersistenceOwner owner = new PlaybackQueuePersistenceOwner(() -> operations);

        owner.persistQueueState();
        owner.savePlaybackResumeRequested(true);
        owner.savePlaybackResumeRequested(false);
        owner.requestPlaybackResume();
        owner.clearPlaybackResumeRequest();
        owner.persistCurrentPlaybackPosition(true);
        owner.persistCurrentPlaybackPosition(false);

        assertEquals(1, operations.persistQueueCalls);
        assertEquals(4, operations.resumeCalls);
        assertEquals(false, operations.lastResumeRequested);
        assertEquals(2, operations.positionCalls);
        assertEquals(false, operations.lastPositionForce);
    }

    @Test
    public void ignoresMissingQueuePersistenceOperations() {
        PlaybackQueuePersistenceOwner missingSupplier = new PlaybackQueuePersistenceOwner(null);
        PlaybackQueuePersistenceOwner missingOperations = new PlaybackQueuePersistenceOwner(() -> null);

        missingSupplier.persistQueueState();
        missingSupplier.savePlaybackResumeRequested(true);
        missingSupplier.requestPlaybackResume();
        missingSupplier.clearPlaybackResumeRequest();
        missingSupplier.persistCurrentPlaybackPosition(true);
        missingOperations.persistQueueState();
        missingOperations.savePlaybackResumeRequested(false);
        missingOperations.requestPlaybackResume();
        missingOperations.clearPlaybackResumeRequest();
        missingOperations.persistCurrentPlaybackPosition(false);
    }

    private static final class FakeQueuePersistenceOperations
            implements PlaybackQueuePersistenceOwner.QueuePersistenceOperations {
        private int persistQueueCalls;
        private int resumeCalls;
        private boolean lastResumeRequested;
        private int positionCalls;
        private boolean lastPositionForce;

        @Override
        public void persistQueueState() {
            persistQueueCalls++;
        }

        @Override
        public void savePlaybackResumeRequested(boolean requested) {
            resumeCalls++;
            lastResumeRequested = requested;
        }

        @Override
        public void persistCurrentPlaybackPosition(boolean force) {
            positionCalls++;
            lastPositionForce = force;
        }
    }
}
