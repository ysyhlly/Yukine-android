package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackSessionRefreshOwnerTest {
    @Test
    public void refreshesCurrentPlaybackSessionWhenAvailable() {
        MutableSessionOperationsSource source = new MutableSessionOperationsSource();
        FakeSessionOperations first = new FakeSessionOperations();
        FakeSessionOperations second = new FakeSessionOperations();
        PlaybackSessionRefreshOwner owner = new PlaybackSessionRefreshOwner(source::sessionOperations);

        source.sessionOperations = first;
        owner.refreshPlaybackSession();
        source.sessionOperations = second;
        owner.refreshPlaybackSession();

        assertEquals(1, first.refreshPlayerCalls);
        assertEquals(1, second.refreshPlayerCalls);
    }

    @Test
    public void ignoresMissingPlaybackSession() {
        PlaybackSessionRefreshOwner nullSupplierOwner = new PlaybackSessionRefreshOwner(null);
        PlaybackSessionRefreshOwner missingSessionOwner = new PlaybackSessionRefreshOwner(() -> null);

        nullSupplierOwner.refreshPlaybackSession();
        missingSessionOwner.refreshPlaybackSession();
    }

    private static final class MutableSessionOperationsSource {
        private FakeSessionOperations sessionOperations;

        public PlaybackSessionRefreshOwner.SessionOperations sessionOperations() {
            return sessionOperations;
        }
    }

    private static final class FakeSessionOperations
            implements PlaybackSessionRefreshOwner.SessionOperations {
        private int refreshPlayerCalls;

        @Override
        public void refreshPlayer() {
            refreshPlayerCalls++;
        }
    }
}
