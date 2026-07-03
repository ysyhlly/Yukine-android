package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class PlaybackQueueRestoreOwnerTest {
    @Test
    public void restoresAndPreparesPlaybackWhenQueueIsRestorable() {
        List<String> events = new ArrayList<>();
        FakeQueueStore store = new FakeQueueStore(
                new PlaybackQueueState(Collections.singletonList(track(1L)), 0),
                false
        );
        PlaybackQueueRestoreOwner owner = owner(queueManager(store), new FakeRestorePlaybackBoundary(events));

        owner.restoreLastPlayback(true);

        assertEquals("create,prepare:true", String.join(",", events));
    }

    @Test
    public void publishesStateWhenRestoreDoesNotPreparePlayback() {
        List<String> events = new ArrayList<>();
        FakeQueueStore store = new FakeQueueStore(
                new PlaybackQueueState(Collections.singletonList(track(2L)), -1),
                false
        );
        PlaybackQueueRestoreOwner owner = owner(queueManager(store), new FakeRestorePlaybackBoundary(events));

        owner.restoreLastPlayback(true);

        assertEquals("create,publish", String.join(",", events));
    }

    @Test
    public void nullPlaybackQueueManagerPublishesEmptyRestoreState() {
        List<String> events = new ArrayList<>();
        PlaybackQueueRestoreOwner missingManager =
                new PlaybackQueueRestoreOwner(
                        null,
                        () -> events.add("create"),
                        new FakeRestorePlaybackBoundary(events)
                );

        missingManager.restoreLastPlayback(false);

        assertEquals("publish", String.join(",", events));
    }

    @Test
    public void ignoresMissingRestoreBoundary() {
        FakeQueueStore store = new FakeQueueStore(
                new PlaybackQueueState(Collections.singletonList(track(3L)), 0),
                true
        );
        PlaybackQueueRestoreOwner owner = owner(queueManager(store), null);

        owner.restoreLastPlayback(false);

        assertEquals(1, store.loadCalls);
    }

    @Test
    public void delegatesRestoreEnabledSetting() {
        FakeQueueStore store = new FakeQueueStore(new PlaybackQueueState(Collections.emptyList(), -1), false);
        PlaybackQueueRestoreOwner owner = owner(queueManager(store), null);
        PlaybackQueueRestoreOwner missingManager =
                new PlaybackQueueRestoreOwner(null, null, null);

        owner.setPlaybackRestoreEnabled(true);
        missingManager.setPlaybackRestoreEnabled(false);

        assertEquals(1, store.saveRestoreEnabledCalls);
        assertEquals(true, store.lastRestoreEnabled);
    }

    @Test
    public void delegatesQueueRestoreSnapshot() {
        FakeQueueStore store = new FakeQueueStore(
                new PlaybackQueueState(Collections.singletonList(track(4L)), 0),
                false
        );
        PlaybackQueueRestoreOwner owner = owner(queueManager(store), null);
        PlaybackQueueRestoreOwner missingManager =
                new PlaybackQueueRestoreOwner(null, null, null);

        owner.restorePlaybackQueue();
        missingManager.restorePlaybackQueue();

        assertEquals(1, store.loadCalls);
    }

    private static PlaybackQueueRestoreOwner owner(
            PlaybackQueueManager queueManager,
            FakeRestorePlaybackBoundary boundary
    ) {
        return new PlaybackQueueRestoreOwner(
                queueManager,
                boundary == null ? null : boundary::createPlayerIfNeeded,
                boundary
        );
    }

    private static PlaybackQueueManager queueManager(FakeQueueStore store) {
        return new PlaybackQueueManager(
                store,
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null,
                new Random(0L)
        );
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                1000L,
                null,
                "streaming:netease:" + id
        );
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private final PlaybackQueueState state;
        private final boolean resumeRequested;
        private int loadCalls;
        private int saveRestoreEnabledCalls;
        private boolean lastRestoreEnabled;

        private FakeQueueStore(PlaybackQueueState state, boolean resumeRequested) {
            this.state = state;
            this.resumeRequested = resumeRequested;
        }

        @Override
        public PlaybackQueueState load() {
            loadCalls++;
            return state;
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
        }

        @Override
        public boolean loadResumeRequested() {
            return resumeRequested;
        }

        @Override
        public void saveResumeRequested(boolean requested) {
        }

        @Override
        public boolean loadPlaybackRestoreEnabled() {
            return true;
        }

        @Override
        public void savePlaybackRestoreEnabled(boolean enabled) {
            saveRestoreEnabledCalls++;
            lastRestoreEnabled = enabled;
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
        }
    }

    private static final class FakeRestorePlaybackBoundary
            implements PlaybackQueueManager.QueuePlaybackActions {
        private final List<String> events;

        private FakeRestorePlaybackBoundary(List<String> events) {
            this.events = events;
        }

        public void createPlayerIfNeeded() {
            events.add("create");
        }

        @Override
        public void prepareCurrent(boolean playWhenReady) {
            events.add("prepare:" + playWhenReady);
        }

        @Override
        public void publishState() {
            events.add("publish");
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
        public Track restoredTrackFor(Track track) {
            return track;
        }

        @Override
        public void restoreForDataPath(String dataPath) {
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
