package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackNotificationStateOwnerTest {
    @Test
    public void delegatesNotificationStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = new Track(7L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:7");
        PlaybackNotificationStateOwner owner = new PlaybackNotificationStateOwner(
                queueStateOwner(track),
                () -> {
                    events.add("playing");
                    return true;
                },
                () -> {
                    events.add("preparing");
                    return false;
                },
                favoriteTrack -> {
                    events.add("favorite:" + (favoriteTrack == null ? -1L : favoriteTrack.id));
                    return favoriteTrack == track;
                },
                () -> {
                    events.add("token");
                    return null;
                }
        );

        assertFalse(owner.isQueueEmpty());
        assertTrue(owner.isPlaying());
        assertFalse(owner.isPreparing());
        assertSame(track, owner.currentTrack());
        assertTrue(owner.isFavorite(track));
        assertNull(owner.playbackSessionPlatformToken());

        assertEquals(
                java.util.Arrays.asList(
                        "playing",
                        "preparing",
                        "favorite:7",
                        "token"
                ),
                events
        );
    }

    @Test
    public void returnsInactivePlaybackStateWhenSuppliersAreMissing() {
        PlaybackNotificationStateOwner owner = new PlaybackNotificationStateOwner(
                null,
                null,
                null,
                track -> false,
                () -> null
        );

        assertFalse(owner.isPlaying());
        assertFalse(owner.isPreparing());
    }

    @Test
    public void returnsEmptyQueueStateWhenQueueSuppliersAreMissing() {
        PlaybackNotificationStateOwner owner = new PlaybackNotificationStateOwner(
                null,
                null,
                null,
                track -> false,
                () -> null
        );

        assertTrue(owner.isQueueEmpty());
        assertNull(owner.currentTrack());
    }

    @Test
    public void returnsEmptyQueueStateWhenQueueManagerIsMissing() {
        PlaybackNotificationStateOwner owner = new PlaybackNotificationStateOwner(
                new PlaybackQueueStateOwner(() -> null),
                null,
                null,
                track -> false,
                () -> null
        );

        assertTrue(owner.isQueueEmpty());
        assertNull(owner.currentTrack());
    }

    private static PlaybackQueueStateOwner queueStateOwner(Track track) {
        PlaybackQueueManager queueManager = playbackQueueManager();
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        return new PlaybackQueueStateOwner(() -> queueManager);
    }

    private static PlaybackQueueManager playbackQueueManager() {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                new PlaybackTransitionStateManager(),
                new Random(1L)
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
