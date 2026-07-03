package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class PlaybackQueuePersistenceOwnerTest {
    @Test
    public void delegatesQueuePersistenceToPlaybackQueueManager() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackQueueManager queueManager = queueManager(store);
        Track track = track(42L);
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        store.resetCounts();
        PlaybackQueuePersistenceOwner owner =
                new PlaybackQueuePersistenceOwner(queueManager, store);

        owner.persistQueueState();
        owner.savePlaybackResumeRequested(true);
        owner.savePlaybackResumeRequested(false);
        owner.requestPlaybackResume();
        owner.clearPlaybackResumeRequest();
        owner.persistCurrentPlaybackPosition(true);
        owner.persistCurrentPlaybackPosition(false);

        assertEquals(1, store.saveCalls);
        assertEquals(4, store.resumeCalls);
        assertEquals(false, store.lastResumeRequested);
        assertEquals(2, store.positionCalls);
        assertEquals(42L, store.lastPositionTrackId);
        assertEquals(0L, store.lastPositionMs);
    }

    @Test
    public void ignoresMissingPlaybackQueueManager() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackQueuePersistenceOwner missingManager =
                new PlaybackQueuePersistenceOwner(null, store);

        missingManager.persistQueueState();
        missingManager.savePlaybackResumeRequested(false);
        missingManager.requestPlaybackResume();
        missingManager.clearPlaybackResumeRequest();
        missingManager.persistCurrentPlaybackPosition(false);

        assertEquals(3, store.resumeCalls);
    }

    @Test
    public void ignoresMissingQueueStoreForResumeRequests() {
        PlaybackQueuePersistenceOwner owner =
                new PlaybackQueuePersistenceOwner(null, null);

        owner.savePlaybackResumeRequested(true);
        owner.requestPlaybackResume();
        owner.clearPlaybackResumeRequest();
    }

    private static PlaybackQueueManager queueManager(FakeQueueStore store) {
        return new PlaybackQueueManager(
                store,
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private int saveCalls;
        private int resumeCalls;
        private boolean lastResumeRequested;
        private int positionCalls;
        private long lastPositionTrackId = -1L;
        private long lastPositionMs = -1L;

        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(Collections.emptyList(), -1);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
            saveCalls++;
        }

        @Override
        public boolean loadResumeRequested() {
            return false;
        }

        @Override
        public void saveResumeRequested(boolean requested) {
            resumeCalls++;
            lastResumeRequested = requested;
        }

        @Override
        public boolean loadPlaybackRestoreEnabled() {
            return true;
        }

        @Override
        public void savePlaybackRestoreEnabled(boolean enabled) {
        }

        @Override
        public long loadPlaybackPositionTrackId() {
            return -1L;
        }

        @Override
        public long loadPlaybackPositionMs() {
            return 0L;
        }

        @Override
        public void savePlaybackPosition(long trackId, long positionMs) {
            positionCalls++;
            lastPositionTrackId = trackId;
            lastPositionMs = positionMs;
        }

        void resetCounts() {
            saveCalls = 0;
            resumeCalls = 0;
            positionCalls = 0;
            lastResumeRequested = false;
            lastPositionTrackId = -1L;
            lastPositionMs = -1L;
        }
    }

    private static final class NoopQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        @Override
        public void prepareCurrent(boolean playWhenReady) {
        }

        @Override
        public void publishState() {
        }
    }

    private static final class NoopStreamingRestoreProvider
            implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoreTrackForPlayback(Track track) {
            return track;
        }
    }

    private static final class NoopMirroredQueuePlayer
            implements PlaybackQueueManager.MirroredQueuePlayer {
        @Override
        public boolean matchesCurrentQueue() {
            return false;
        }

        @Override
        public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
            return false;
        }
    }
}
