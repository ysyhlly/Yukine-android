package app.yukine.playback;

import org.junit.Test;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
        PlaybackQueueManager queueManager = queueManager();
        queueManager.playQueue(Collections.singletonList(track), 0, 0L);
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                new PlaybackQueueStateOwner(() -> queueManager),
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
                new PlaybackQueueStateOwner(this::missingQueueManager),
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

    private PlaybackQueueManager missingQueueManager() {
        return null;
    }

    private static PlaybackQueueManager queueManager() {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null,
                new Random(1L)
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(Collections.emptyList(), -1);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
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
