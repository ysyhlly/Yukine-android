package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.media3.exoplayer.ExoPlayer;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class PlaybackQueueStateOwnerTest {
    @Test
    public void delegatesQueueStateSnapshotToPlaybackQueueManager() {
        Track track = track(12L);
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        PlaybackQueueStateOwner owner =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> queueManager);

        assertSame(track, owner.queueStateSnapshot().getCurrentTrack());
        assertSame(track, owner.currentTrack());
        assertEquals(false, owner.queueStateSnapshot().isQueueEmpty());
        assertFalse(owner.isQueueEmpty());
    }

    @Test
    public void returnsEmptyQueueStateWhenManagerSupplierOrManagerAreMissing() {
        PlaybackQueueStateOwner missingManagerProvider =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(null);
        PlaybackQueueStateOwner missingManager =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> null);

        assertEmpty(missingManagerProvider.queueStateSnapshot());
        assertEmpty(missingManager.queueStateSnapshot());
        assertSame(null, missingManagerProvider.currentTrack());
        assertSame(null, missingManager.currentTrack());
        assertTrue(missingManagerProvider.isQueueEmpty());
        assertTrue(missingManager.isQueueEmpty());
    }

    @Test
    public void failedTrackPolicyUsesQueueStateSnapshot() {
        Track failed = track(7L);
        PlaybackQueueStateOwner missingQueue = PlaybackQueueStateOwner.fromPlaybackQueueManager(null);
        PlaybackQueueStateOwner singleTrack = ownerForTracks(failed);
        PlaybackQueueStateOwner multipleTracks = ownerForTracks(failed, track(8L));

        assertFalse(missingQueue.canSkipFailedTrack(failed));
        assertFalse(multipleTracks.canSkipFailedTrack(null));
        assertFalse(multipleTracks.canSkipFailedTrack(track(-1L)));
        assertFalse(singleTrack.canSkipFailedTrack(failed));
        assertTrue(multipleTracks.canSkipFailedTrack(failed));
    }

    @Test
    public void returnsEmptyQueueSnapshotWhenManagerIsMissing() {
        PlaybackQueueStateOwner missingManagerProvider =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(null);
        PlaybackQueueStateOwner missingManager =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> null);

        assertTrue(missingManagerProvider.queueSnapshot().isEmpty());
        assertTrue(missingManager.queueSnapshot().isEmpty());
        assertTrue(missingManagerProvider.upcomingTracksForPrecache(3).isEmpty());
        assertTrue(missingManager.upcomingTracksForPrecache(3).isEmpty());
    }

    @Test
    public void delegatesQueueReadsToPlaybackQueueManager() {
        PlaybackRuntimeStateManager runtimeStateManager = playbackRuntimeStateManager();
        runtimeStateManager.setRepeatMode(REPEAT_OFF);
        PlaybackQueueManager queueManager = playbackQueueManager(runtimeStateManager);
        Track first = track(1L);
        Track second = track(2L);
        Track third = track(3L);
        queueManager.playQueue(Arrays.asList(first, second, third), 1, -1L);
        PlaybackQueueStateOwner owner =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> queueManager);

        assertTrackIds(Arrays.asList(1L, 2L, 3L), owner.queueSnapshot());
        assertTrackIds(Collections.singletonList(3L), owner.upcomingTracksForPrecache(3));
    }

    private static void assertEmpty(PlaybackQueueManager.QueueStateSnapshot snapshot) {
        assertSame(null, snapshot.getCurrentTrack());
        assertEquals(-1, snapshot.getCurrentIndex());
        assertEquals(0, snapshot.getQueueSize());
        assertEquals(true, snapshot.isQueueEmpty());
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static void assertTrackIds(List<Long> expectedIds, List<Track> tracks) {
        List<Long> actualIds = new ArrayList<>();
        for (Track track : tracks) {
            actualIds.add(track.id);
        }
        assertEquals(expectedIds, actualIds);
    }

    private static PlaybackQueueStateOwner ownerForTracks(Track... tracks) {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        queueManager.playQueue(Arrays.asList(tracks), 0, -1L);
        return PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> queueManager);
    }

    private static PlaybackQueueManager playbackQueueManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                runtimeStateManager,
                new PlaybackTransitionStateManager(),
                new Random(1L)
        );
    }

    private static PlaybackRuntimeStateManager playbackRuntimeStateManager() {
        return new PlaybackRuntimeStateManager(
                new PlaybackRuntimeStateManager.StateProvider() {
                    @Override
                    public ExoPlayer player() {
                        return null;
                    }

                    @Override
                    public boolean playerMirrorsQueue() {
                        return false;
                    }

                    @Override
                    public Track currentTrack() {
                        return null;
                    }
                }
        );
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

        @Override
        public void stopAndClear() {
        }

        @Override
        public boolean persistQueueAsync(
                java.util.List<app.yukine.model.Track> tracks,
                int currentIndex
        ) {
            return false;
        }

        @Override
        public boolean persistQueueNow(
                java.util.List<app.yukine.model.Track> tracks,
                int currentIndex
        ) {
            return false;
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
