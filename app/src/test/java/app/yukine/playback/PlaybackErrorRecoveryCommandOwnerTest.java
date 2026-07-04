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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackErrorRecoveryCommandOwnerTest {
    @Test
    public void delegatesErrorRecoveryActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(7L);
        PlaybackErrorRecoveryCommandOwner owner = new PlaybackErrorRecoveryCommandOwner(
                queueManager(track, 2),
                playWhenReady -> events.add("prepare:" + playWhenReady),
                () -> events.add("next"),
                message -> events.add("error:" + message),
                () -> events.add("publish"),
                (message, error) -> events.add("warn:" + message + ":" + error.getMessage())
        );

        assertSame(track, owner.currentTrack());
        assertTrue(owner.canSkipFailedTrack(track));
        assertEquals(false, owner.canSkipFailedTrack(null));
        assertEquals(false, owner.canSkipFailedTrack(track(-1L)));
        assertEquals("trackId=7, title=Track 7, dataPath=file:7, uri=null", owner.debugTrack(track));
        assertEquals("trackId=7, title=Track 7, dataPath=file:7, uri=null", owner.debugCurrentTrack());
        assertEquals("track=<null>", owner.debugTrack(null));
        owner.prepareCurrent(true);
        owner.skipToNext();
        owner.setErrorMessage("Unable");
        owner.publishState();
        owner.logWarning("Playback failed", new Exception("boom"));

        assertEquals(
                java.util.Arrays.asList(
                        "prepare:true",
                        "next",
                        "error:Unable",
                        "publish",
                        "warn:Playback failed:boom"
                ),
                events
        );
    }

    @Test
    public void handlesMissingOrSingleTrackState() {
        Track track = track(7L);
        PlaybackErrorRecoveryCommandOwner missingStateOwner = new PlaybackErrorRecoveryCommandOwner(
                playbackQueueManager(),
                playWhenReady -> {
                },
                () -> {
                },
                message -> {
                },
                () -> {
                },
                (message, error) -> {
                }
        );
        PlaybackErrorRecoveryCommandOwner singleTrackOwner = new PlaybackErrorRecoveryCommandOwner(
                queueManager(track, 1),
                playWhenReady -> {
                },
                () -> {
                },
                message -> {
                },
                () -> {
                },
                (message, error) -> {
                }
        );

        assertEquals(null, missingStateOwner.currentTrack());
        assertEquals("track=<null>", missingStateOwner.debugCurrentTrack());
        assertFalse(missingStateOwner.canSkipFailedTrack(track));
        assertSame(track, singleTrackOwner.currentTrack());
        assertEquals("trackId=7, title=Track 7, dataPath=file:7, uri=null", singleTrackOwner.debugCurrentTrack());
        assertFalse(singleTrackOwner.canSkipFailedTrack(track));
    }

    @Test(expected = NullPointerException.class)
    public void constructorRequiresQueueManager() {
        new PlaybackErrorRecoveryCommandOwner(
                null,
                playWhenReady -> {
                },
                () -> {
                },
                message -> {
                },
                () -> {
                },
                (message, error) -> {
                }
        );
    }

    @Test
    public void readsCurrentTrackDirectlyFromPlaybackQueueManager() {
        Track track = track(8L);
        PlaybackQueueManager queueManager = queueManager(track, 2);
        PlaybackErrorRecoveryCommandOwner owner = new PlaybackErrorRecoveryCommandOwner(
                queueManager,
                playWhenReady -> {
                },
                () -> {
                },
                message -> {
                },
                () -> {
                },
                (message, error) -> {
                }
        );
        Track replacement = track(18L);

        assertSame(track, owner.currentTrack());
        queueManager.playQueue(java.util.Arrays.asList(replacement, track(19L)), 0, -1L);
        assertSame(replacement, owner.currentTrack());
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static PlaybackQueueManager queueManager(Track current, int queueSize) {
        PlaybackQueueManager queueManager = playbackQueueManager();
        List<Track> queue = new ArrayList<>();
        queue.add(current);
        for (int index = 1; index < queueSize; index++) {
            queue.add(track(current.id + index));
        }
        queueManager.playQueue(queue, 0, -1L);
        return queueManager;
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
