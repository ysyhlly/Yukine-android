package app.yukine.playback;

import android.os.Handler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class PlaybackVisualizationCacheStateOwnerTest {
    @Test
    public void delegatesVisualizationCacheStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = new Track(41L, "Track", "Artist", "Album", 1000L, null, "file:41");
        PlaybackQueueManager queueManager = queueManager(track);
        Runnable task = () -> events.add("task");
        Handler handler = new Handler();
        PlaybackVisualizationCacheStateOwner owner = new PlaybackVisualizationCacheStateOwner(
                handler,
                queueManager,
                scheduled -> {
                    events.add("schedule");
                    assertSame(task, scheduled);
                }
        );

        assertSame(handler, owner.mainHandler());
        assertSame(track, owner.currentTrack());
        owner.scheduleVisualizationCacheTask(task);
        assertEquals(
                java.util.Collections.singletonList("schedule"),
                events
        );
    }

    @Test
    public void readsCurrentTrackFromPlaybackQueueManager() {
        Track track = new Track(42L, "Track", "Artist", "Album", 1000L, null, "file:42");
        PlaybackQueueManager queueManager = queueManager(null);
        PlaybackVisualizationCacheStateOwner nullTrackOwner = new PlaybackVisualizationCacheStateOwner(
                new Handler(),
                queueManager,
                task -> {
                }
        );

        assertNull(nullTrackOwner.currentTrack());
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        assertSame(track, nullTrackOwner.currentTrack());
    }

    @Test(expected = NullPointerException.class)
    public void constructorRequiresPlaybackQueueManager() {
        new PlaybackVisualizationCacheStateOwner(
                new Handler(),
                null,
                task -> {
                }
        );
    }

    @Test
    public void constructorRequiresMainHandler() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> new PlaybackVisualizationCacheStateOwner(
                        null,
                        queueManager(null),
                        task -> {
                        }
                )
        );

        assertEquals("mainHandler", error.getMessage());
    }

    @Test
    public void constructorRequiresCacheTaskScheduler() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> new PlaybackVisualizationCacheStateOwner(
                        new Handler(),
                        queueManager(null),
                        null
                )
        );

        assertEquals("cacheTaskScheduler", error.getMessage());
    }

    private static PlaybackQueueManager queueManager(Track current) {
        PlaybackQueueManager queueManager = new PlaybackQueueManager(
                new FakeQueueStore(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null
        );
        if (current != null) {
            queueManager.playQueue(Collections.singletonList(current), 0, -1L);
        }
        return queueManager;
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
            return false;
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

    private static final class NoopQueuePlaybackActions implements PlaybackQueueManager.QueuePlaybackActions {
        @Override
        public void prepareCurrent(boolean playWhenReady) {
        }

        @Override
        public void publishState() {
        }
    }

    private static final class NoopStreamingRestoreProvider implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoreTrackForPlayback(Track track) {
            return track;
        }
    }

    private static final class NoopMirroredQueuePlayer implements PlaybackQueueManager.MirroredQueuePlayer {
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
