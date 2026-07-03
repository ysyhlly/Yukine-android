package app.yukine.playback;

import android.net.Uri;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackCrossfadeStateOwnerTest {
    @Test
    public void delegatesCrossfadeStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackCrossfadeStateOwner owner = new PlaybackCrossfadeStateOwner(
                () -> {
                    events.add("fadeOut");
                    return false;
                },
                () -> {
                    events.add("player");
                    return true;
                },
                () -> {
                    events.add("playing");
                    return true;
                },
                () -> {
                    events.add("repeat");
                    return 2;
                },
                queueStateOwner(2, 0),
                () -> {
                    events.add("volume");
                    return 0.75f;
                }
        );

        assertFalse(owner.fadeOutAdvancing());
        assertTrue(owner.playerAvailable());
        assertTrue(owner.isPlaying());
        assertTrue(owner.canCrossfadeAdvance());
        assertEquals(0.75f, owner.baseVolume(), 0.001f);
        assertEquals(
                java.util.Arrays.asList(
                        "fadeOut",
                        "player",
                        "playing",
                        "repeat",
                        "volume"
                ),
                events
        );
    }

    @Test
    public void crossfadeAdvancePolicyUsesQueueStateAndRepeatMode() {
        PlaybackCrossfadeStateOwner missingQueue = owner(null, PlaybackRepeatMode.REPEAT_ALL);
        PlaybackCrossfadeStateOwner singleTrack = owner(queueStateOwner(1, 0), PlaybackRepeatMode.REPEAT_ALL);
        PlaybackCrossfadeStateOwner repeatOffBeforeEnd = owner(queueStateOwner(2, 0), PlaybackRepeatMode.REPEAT_OFF);
        PlaybackCrossfadeStateOwner repeatOffAtEnd = owner(queueStateOwner(2, 1), PlaybackRepeatMode.REPEAT_OFF);
        PlaybackCrossfadeStateOwner repeatAllAtEnd = owner(queueStateOwner(2, 1), PlaybackRepeatMode.REPEAT_ALL);

        assertFalse(missingQueue.canCrossfadeAdvance());
        assertFalse(singleTrack.canCrossfadeAdvance());
        assertTrue(repeatOffBeforeEnd.canCrossfadeAdvance());
        assertFalse(repeatOffAtEnd.canCrossfadeAdvance());
        assertTrue(repeatAllAtEnd.canCrossfadeAdvance());
    }

    private static PlaybackCrossfadeStateOwner owner(
            PlaybackQueueStateOwner queueStateOwner,
            int repeatMode
    ) {
        return new PlaybackCrossfadeStateOwner(
                () -> false,
                () -> true,
                () -> true,
                () -> repeatMode,
                queueStateOwner,
                () -> 1.0f
        );
    }

    private static PlaybackQueueStateOwner queueStateOwner(int queueSize, int currentIndex) {
        PlaybackQueueManager queueManager = playbackQueueManager();
        List<Track> queue = new ArrayList<>();
        for (int index = 0; index < queueSize; index++) {
            queue.add(track(index + 1L));
        }
        queueManager.playQueue(queue, currentIndex, -1L);
        return new PlaybackQueueStateOwner(queueManager);
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
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
