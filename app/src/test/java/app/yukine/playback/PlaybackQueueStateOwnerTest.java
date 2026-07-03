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
    public void delegatesQueueStateReadsToPlaybackQueueManager() {
        Track track = track(12L);
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        PlaybackQueueStateOwner owner = new PlaybackQueueStateOwner();
        owner.bindPlaybackQueueManager(queueManager);
        PlaybackQueueManager.QueueStateSnapshot snapshot = owner.queueStateSnapshot();

        assertSame(track, snapshot.getCurrentTrack());
        assertEquals(0, snapshot.getCurrentIndex());
        assertEquals(1, snapshot.getQueueSize());
        assertFalse(snapshot.isQueueEmpty());
        assertFalse(snapshot.getHasMultipleTracks());
        assertTrue(snapshot.isAtEndOfQueue());
    }

    @Test
    public void bindPlaybackQueueManagerSupportsRebindingForIsolatedOwners() {
        Track track = track(13L);
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        PlaybackQueueStateOwner owner = new PlaybackQueueStateOwner();

        owner.bindPlaybackQueueManager(queueManager);

        assertSame(track, owner.queueStateSnapshot().getCurrentTrack());

        owner.bindPlaybackQueueManager(null);

        assertSame(null, owner.queueStateSnapshot().getCurrentTrack());
        assertTrue(owner.queueStateSnapshot().isQueueEmpty());
    }

    @Test
    public void queueStateSnapshotDerivedFlagsUseMinimalQueueState() {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        Track first = track(1L);
        Track second = track(2L);
        Track third = track(3L);
        queueManager.playQueue(Arrays.asList(first, second, third), 1, -1L);
        PlaybackQueueStateOwner owner =
                new PlaybackQueueStateOwner(queueManager);
        PlaybackQueueManager.QueueStateSnapshot snapshot = owner.queueStateSnapshot();

        assertSame(second, snapshot.getCurrentTrack());
        assertEquals(1, snapshot.getCurrentIndex());
        assertEquals(3, snapshot.getQueueSize());
        assertFalse(snapshot.isQueueEmpty());
        assertTrue(snapshot.getHasMultipleTracks());
        assertFalse(snapshot.isAtEndOfQueue());
    }

    @Test
    public void queueStateSnapshotCurrentTrackFollowsBoundManagerWithoutLocalState() {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        Track first = track(21L);
        Track second = track(22L);
        Track replacement = track(23L);
        queueManager.playQueue(Arrays.asList(first, second), 0, -1L);
        PlaybackQueueStateOwner owner = new PlaybackQueueStateOwner(queueManager);

        PlaybackQueueManager.QueueStateSnapshot initialSnapshot = owner.queueStateSnapshot();
        assertSame(first, initialSnapshot.getCurrentTrack());

        queueManager.playQueue(Collections.singletonList(replacement), 0, -1L);
        PlaybackQueueManager.QueueStateSnapshot updatedSnapshot = owner.queueStateSnapshot();

        assertSame(replacement, updatedSnapshot.getCurrentTrack());
    }

    @Test
    public void returnsEmptyQueueStateWhenManagerIsMissing() {
        PlaybackQueueStateOwner missingManager = new PlaybackQueueStateOwner();

        PlaybackQueueManager.QueueStateSnapshot snapshot = missingManager.queueStateSnapshot();
        assertSame(null, snapshot.getCurrentTrack());
        assertEquals(-1, snapshot.getCurrentIndex());
        assertEquals(0, snapshot.getQueueSize());
        assertTrue(snapshot.isQueueEmpty());
        assertFalse(snapshot.getHasMultipleTracks());
        assertFalse(snapshot.isAtEndOfQueue());
    }

    @Test
    public void returnsEmptyQueueSnapshotWhenManagerIsMissing() {
        PlaybackQueueStateOwner missingManager = new PlaybackQueueStateOwner();

        assertTrue(missingManager.queueSnapshot().isEmpty());
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
                new PlaybackQueueStateOwner(queueManager);

        assertTrackIds(Arrays.asList(1L, 2L, 3L), owner.queueSnapshot());
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
