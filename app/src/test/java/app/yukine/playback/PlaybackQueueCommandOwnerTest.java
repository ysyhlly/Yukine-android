package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackQueueCommandOwnerTest {
    @Test
    public void delegatesQueueActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(7L);
        PlaybackQueueManager queueManager = queueManagerWithCurrent(track);
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                () -> queueManager,
                (currentTrack, playWhenReady) -> events.add("prepare:" + currentTrack.id + ":" + playWhenReady),
                () -> events.add("publish")
        );

        owner.prepareCurrent(true);
        owner.publishState();

        assertEquals(
                java.util.Arrays.asList(
                        "prepare:7:true",
                        "publish"
                ),
                events
        );
        assertFalse(owner.runIfCurrentTrackMissing(() -> events.add("fallback")));
        assertTrue(owner.prepareCurrentOrRunFallback(false, () -> events.add("fallback")));
        assertEquals(
                java.util.Arrays.asList(
                        "prepare:7:true",
                        "publish",
                        "prepare:7:false"
                ),
                events
        );
    }

    @Test
    public void ignoresPrepareWhenCurrentTrackIsMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                () -> null,
                (currentTrack, playWhenReady) -> events.add("prepare"),
                () -> events.add("publish")
        );

        owner.prepareCurrent(true);
        owner.publishState();

        assertEquals(Collections.singletonList("publish"), events);
        assertTrue(owner.runIfCurrentTrackMissing(() -> events.add("fallback")));
        assertFalse(owner.prepareCurrentOrRunFallback(false, () -> events.add("fallback")));
        assertEquals(java.util.Arrays.asList("publish", "fallback", "fallback"), events);
    }

    private static PlaybackQueueManager queueManagerWithCurrent(Track currentTrack) {
        PlaybackQueueManager queueManager = new PlaybackQueueManager(
                new FakeQueueStore(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null
        );
        queueManager.playQueue(Collections.singletonList(currentTrack), 0, 0L);
        return queueManager;
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, null, "file:" + id);
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private List<Track> savedTracks = Collections.emptyList();
        private int savedIndex = -1;

        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(savedTracks, savedIndex);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
            savedTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);
            savedIndex = currentIndex;
        }

        @Override
        public boolean loadResumeRequested() {
            return false;
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
