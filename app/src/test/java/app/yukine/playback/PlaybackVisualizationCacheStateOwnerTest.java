package app.yukine.playback;

import android.net.Uri;
import android.os.Handler;

import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlaybackVisualizationCacheStateOwnerTest {
    @Test
    public void delegatesVisualizationCacheStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = new Track(41L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:41");
        PlaybackQueueManager queueManager = playbackQueueManager();
        queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        Runnable task = () -> events.add("task");
        Handler handler = new Handler();
        PlaybackVisualizationCacheStateOwner owner = new PlaybackVisualizationCacheStateOwner(
                handler,
                queueStateOwner(queueManager),
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
    public void returnsNullCurrentTrackWhenSupplierIsMissing() {
        PlaybackVisualizationCacheStateOwner missingProviderOwner = new PlaybackVisualizationCacheStateOwner(
                null,
                null,
                task -> {
                }
        );
        PlaybackVisualizationCacheStateOwner nullTrackOwner = new PlaybackVisualizationCacheStateOwner(
                null,
                queueStateOwner(playbackQueueManager()),
                task -> {
                }
        );

        assertNull(missingProviderOwner.currentTrack());
        assertNull(nullTrackOwner.currentTrack());
    }

    private static PlaybackQueueStateOwner queueStateOwner(PlaybackQueueManager queueManager) {
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
                playbackRuntimeStateManager(),
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
