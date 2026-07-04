package app.yukine.playback;

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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class PlaybackQueueStateOwnerTest {
    @Test
    public void delegatesQueueStateReadsToPlaybackQueueManager() {
        Track track = track(12L);
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        PlaybackQueueStateOwner owner = queueStateOwner(queueManager);

        assertSame(track, owner.currentTrack());
        assertFalse(owner.isQueueEmpty());
        assertFalse(owner.hasMultipleTracks());
        assertTrue(owner.isAtEndOfQueue());
    }

    @Test
    public void supplierBackedOwnerReadsLateBoundQueueManager() {
        Track track = track(13L);
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        AtomicReference<PlaybackQueueManager> queueManagerRef = new AtomicReference<>();
        PlaybackQueueStateOwner owner = new PlaybackQueueStateOwner(queueManagerRef::get);

        assertSame(null, owner.currentTrack());
        assertTrue(owner.isQueueEmpty());

        queueManagerRef.set(queueManager);

        assertSame(track, owner.currentTrack());
        assertFalse(owner.isQueueEmpty());
    }

    @Test
    public void queueStateSnapshotDerivedFlagsUseMinimalQueueState() {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        Track first = track(1L);
        Track second = track(2L);
        Track third = track(3L);
        queueManager.playQueue(Arrays.asList(first, second, third), 1, -1L);
        PlaybackQueueStateOwner owner = queueStateOwner(queueManager);

        assertSame(second, owner.currentTrack());
        assertFalse(owner.isQueueEmpty());
        assertTrue(owner.hasMultipleTracks());
        assertFalse(owner.isAtEndOfQueue());
    }

    @Test
    public void queueStateSnapshotCurrentTrackFollowsBoundManagerWithoutLocalState() {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        Track first = track(21L);
        Track second = track(22L);
        Track replacement = track(23L);
        queueManager.playQueue(Arrays.asList(first, second), 0, -1L);
        PlaybackQueueStateOwner owner = queueStateOwner(queueManager);

        assertSame(first, owner.currentTrack());

        queueManager.playQueue(Collections.singletonList(replacement), 0, -1L);

        assertSame(replacement, owner.currentTrack());
    }

    @Test
    public void currentTrackIsDerivedFromLatestQueueStateWithoutLocalState() {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        Track first = track(31L);
        Track replacement = track(32L);
        queueManager.playQueue(Collections.singletonList(first), 0, -1L);
        PlaybackQueueStateOwner owner = queueStateOwner(queueManager);

        assertSame(first, owner.currentTrack());

        queueManager.playQueue(Collections.singletonList(replacement), 0, -1L);

        assertSame(replacement, owner.currentTrack());
    }

    @Test
    public void returnsEmptyQueueStateWhenManagerIsMissing() {
        PlaybackQueueStateOwner missingManager = new PlaybackQueueStateOwner();

        assertSame(null, missingManager.currentTrack());
        assertTrue(missingManager.isQueueEmpty());
        assertFalse(missingManager.hasMultipleTracks());
        assertFalse(missingManager.isAtEndOfQueue());
    }

    @Test
    public void currentTrackReturnsNullWhenManagerIsMissing() {
        PlaybackQueueStateOwner missingManager = new PlaybackQueueStateOwner();

        assertSame(null, missingManager.currentTrack());
    }

    @Test
    public void isQueueEmptyIsDerivedFromLatestQueueStateWithoutLocalState() {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        PlaybackQueueStateOwner owner = queueStateOwner(queueManager);

        assertTrue(owner.isQueueEmpty());

        queueManager.playQueue(Collections.singletonList(track(41L)), 0, -1L);

        assertFalse(owner.isQueueEmpty());
    }

    @Test
    public void queueBoundaryFlagsAreDerivedFromLatestQueueStateWithoutLocalState() {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        PlaybackQueueStateOwner owner = queueStateOwner(queueManager);

        assertFalse(owner.hasMultipleTracks());
        assertFalse(owner.isAtEndOfQueue());

        queueManager.playQueue(Arrays.asList(track(51L), track(52L)), 0, -1L);

        assertTrue(owner.hasMultipleTracks());
        assertFalse(owner.isAtEndOfQueue());

        queueManager.playQueue(Arrays.asList(track(53L), track(54L)), 1, -1L);

        assertTrue(owner.hasMultipleTracks());
        assertTrue(owner.isAtEndOfQueue());
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static PlaybackQueueStateOwner queueStateOwner(PlaybackQueueManager queueManager) {
        return new PlaybackQueueStateOwner(() -> queueManager);
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
