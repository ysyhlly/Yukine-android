package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackQueuePersistenceOwnerTest {
    @Test
    public void delegatesQueuePersistenceToCurrentActions() {
        FakeQueuePersistenceActions actions = new FakeQueuePersistenceActions();
        PlaybackQueuePersistenceOwner owner = new PlaybackQueuePersistenceOwner(
                actions::persistQueueState,
                actions::savePlaybackResumeRequested,
                actions::persistCurrentPlaybackPosition
        );

        owner.persistQueueState();
        owner.savePlaybackResumeRequested(true);
        owner.savePlaybackResumeRequested(false);
        owner.requestPlaybackResume();
        owner.clearPlaybackResumeRequest();
        owner.persistCurrentPlaybackPosition(true);
        owner.persistCurrentPlaybackPosition(false);

        assertEquals(1, actions.persistQueueCalls);
        assertEquals(4, actions.resumeCalls);
        assertEquals(false, actions.lastResumeRequested);
        assertEquals(2, actions.positionCalls);
        assertEquals(false, actions.lastPositionForce);
    }

    @Test
    public void ignoresMissingQueuePersistenceActions() {
        PlaybackQueuePersistenceOwner missingActions = new PlaybackQueuePersistenceOwner(null, null, null);

        missingActions.persistQueueState();
        missingActions.savePlaybackResumeRequested(false);
        missingActions.requestPlaybackResume();
        missingActions.clearPlaybackResumeRequest();
        missingActions.persistCurrentPlaybackPosition(false);
    }

    private static final class FakeQueuePersistenceActions {
        private int persistQueueCalls;
        private int resumeCalls;
        private boolean lastResumeRequested;
        private int positionCalls;
        private boolean lastPositionForce;

        public void persistQueueState() {
            persistQueueCalls++;
        }

        public void savePlaybackResumeRequested(boolean requested) {
            resumeCalls++;
            lastResumeRequested = requested;
        }

        public void persistCurrentPlaybackPosition(boolean force) {
            positionCalls++;
            lastPositionForce = force;
        }
    }
}
