package app.yukine.playback;

import android.net.Uri;

import androidx.media3.exoplayer.ExoPlayer;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackQueueMirroredPlayerOwnerTest {
    @Test
    public void delegatesMirroredQueueReuseInPlaybackOrder() {
        List<String> events = new ArrayList<>();
        Track track = track(1L);
        PlaybackQueueMirroredPlayerOwner owner = new PlaybackQueueMirroredPlayerOwner(
                () -> {
                    events.add("matches");
                    return true;
                },
                () -> {
                    events.add("hasPlayer");
                    return true;
                },
                preparing -> events.add("preparing:" + preparing),
                queueStateOwner(track),
                resetTrack -> events.add("waveform:" + resetTrack.id),
                () -> events.add("apply"),
                (index, positionMs) -> events.add("seek:" + index + ":" + positionMs),
                playWhenReady -> events.add("ready:" + playWhenReady),
                () -> events.add("play"),
                enabled -> events.add("mirror:" + enabled),
                error -> events.add("log")
        );

        assertTrue(owner.matchesCurrentQueue());
        assertTrue(owner.seekTo(2, 3000L, true));

        assertEquals(
                java.util.Arrays.asList(
                        "hasPlayer",
                        "matches",
                        "hasPlayer",
                        "preparing:false",
                        "waveform:1",
                        "apply",
                        "seek:2:3000",
                        "ready:true",
                        "play"
                ),
                events
        );
    }

    @Test
    public void seekFailsWithoutPlayer() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredPlayerOwner owner = new PlaybackQueueMirroredPlayerOwner(
                () -> true,
                () -> false,
                preparing -> events.add("preparing"),
                queueStateOwner(track(1L)),
                track -> events.add("waveform"),
                () -> events.add("apply"),
                (index, positionMs) -> events.add("seek"),
                playWhenReady -> events.add("ready"),
                () -> events.add("play"),
                enabled -> events.add("mirror"),
                error -> events.add("log")
        );

        assertFalse(owner.seekTo(0, 0L, true));
        assertEquals(java.util.Collections.emptyList(), events);
    }

    @Test
    public void seekSkipsWaveformResetWhenCurrentTrackIsMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredPlayerOwner owner = new PlaybackQueueMirroredPlayerOwner(
                () -> true,
                () -> true,
                preparing -> events.add("preparing:" + preparing),
                queueStateOwner(null),
                track -> events.add("waveform"),
                () -> events.add("apply"),
                (index, positionMs) -> events.add("seek:" + index + ":" + positionMs),
                playWhenReady -> events.add("ready:" + playWhenReady),
                () -> events.add("play"),
                enabled -> events.add("mirror"),
                error -> events.add("log")
        );

        assertTrue(owner.seekTo(1, 1200L, false));

        assertEquals(
                java.util.Arrays.asList(
                        "preparing:false",
                        "apply",
                        "seek:1:1200",
                        "ready:false"
                ),
                events
        );
    }

    @Test
    public void matchesCurrentQueueFailsWithoutPlayer() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredPlayerOwner owner = new PlaybackQueueMirroredPlayerOwner(
                () -> {
                    events.add("matches");
                    return true;
                },
                () -> {
                    events.add("hasPlayer");
                    return false;
                },
                preparing -> events.add("preparing"),
                queueStateOwner(track(1L)),
                track -> events.add("waveform"),
                () -> events.add("apply"),
                (index, positionMs) -> events.add("seek"),
                playWhenReady -> events.add("ready"),
                () -> events.add("play"),
                enabled -> events.add("mirror"),
                error -> events.add("log")
        );

        assertFalse(owner.matchesCurrentQueue());
        assertEquals(java.util.Collections.singletonList("hasPlayer"), events);
    }

    @Test
    public void seekFailureClearsMirroredQueueState() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredPlayerOwner owner = new PlaybackQueueMirroredPlayerOwner(
                () -> true,
                () -> true,
                preparing -> events.add("preparing:" + preparing),
                queueStateOwner(null),
                track -> events.add("waveform"),
                () -> events.add("apply"),
                (index, positionMs) -> {
                    throw new IllegalStateException("seek failed");
                },
                playWhenReady -> events.add("ready"),
                () -> events.add("play"),
                enabled -> events.add("mirror:" + enabled),
                error -> events.add("log:" + error.getMessage())
        );

        assertFalse(owner.seekTo(3, 4000L, true));

        assertEquals(
                java.util.Arrays.asList(
                        "preparing:false",
                        "apply",
                        "mirror:false",
                        "log:seek failed"
                ),
                events
        );
    }

    @Test
    public void matcherDelegatesMirroredQueueMatchInPlaybackOrder() {
        List<String> events = new ArrayList<>();
        Track track = track(7L);
        List<Track> queueSnapshot = Collections.singletonList(track);
        java.util.function.BiPredicate<Integer, Track> queueTrackMatcher = (index, matchedTrack) -> {
            events.add("track:" + index + ":" + matchedTrack.id);
            return matchedTrack == track;
        };
        BooleanSupplier matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> {
                            events.add("mirrors");
                            return true;
                        },
                        () -> {
                            events.add("count");
                            return 1;
                        },
                        () -> queueSnapshot,
                        queueTrackMatcher
                );

        assertTrue(matcher.getAsBoolean());

        assertEquals(
                java.util.Arrays.asList(
                        "mirrors",
                        "count",
                        "track:0:7"
                ),
                events
        );
    }

    @Test
    public void matcherReturnsFalseWhenItemCountDiffers() {
        List<String> events = new ArrayList<>();
        BooleanSupplier matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> true,
                        () -> 2,
                        () -> Collections.singletonList(track(7L)),
                        (index, matchedTrack) -> {
                            events.add("track:" + index + ":" + matchedTrack.id);
                            return true;
                        }
                );

        assertFalse(matcher.getAsBoolean());
        assertEquals(Collections.emptyList(), events);
    }

    @Test
    public void matcherReturnsFalseWhenMirrorStateIsMissing() {
        List<String> events = new ArrayList<>();
        BooleanSupplier matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> {
                            events.add("mirrors");
                            return false;
                        },
                        () -> {
                            events.add("count");
                            return 1;
                        },
                        () -> {
                            events.add("snapshot");
                            return Collections.emptyList();
                        },
                        (index, track) -> true
                );

        assertFalse(matcher.getAsBoolean());
        assertEquals(java.util.Collections.singletonList("mirrors"), events);
    }

    @Test
    public void matcherReturnsFalseWhenPlayerStateCannotBeRead() {
        BooleanSupplier matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> true,
                        () -> {
                            throw new IllegalStateException("released");
                        },
                        Collections::emptyList,
                        (index, track) -> true
                );

        assertFalse(matcher.getAsBoolean());
    }

    @Test
    public void matcherReturnsFalseWhenQueueSnapshotSupplierIsMissing() {
        List<String> events = new ArrayList<>();
        BooleanSupplier matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> {
                            events.add("mirrors");
                            return true;
                        },
                        () -> {
                            events.add("count");
                            return 1;
                        },
                        null,
                        (index, track) -> true
                );

        assertFalse(matcher.getAsBoolean());
        assertEquals(
                java.util.Collections.singletonList("mirrors"),
                events
        );
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse("content://media/audio/" + id),
                "/music/" + id + ".mp3"
        );
    }

    private static PlaybackQueueStateOwner queueStateOwner(Track track) {
        PlaybackQueueManager queueManager = playbackQueueManager(playbackRuntimeStateManager());
        if (track != null) {
            queueManager.playQueue(Collections.singletonList(track), 0, -1L);
        }
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
