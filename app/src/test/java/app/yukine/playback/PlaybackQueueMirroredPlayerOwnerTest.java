package app.yukine.playback;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

import app.yukine.model.PlaybackQueueState;
import app.yukine.playback.manager.PlaybackQueueStore;

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
                () -> {
                    events.add("track");
                    return track;
                },
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
                        "matches",
                        "hasPlayer",
                        "preparing:false",
                        "track",
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
                () -> track(1L),
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
    public void seekFailureClearsMirroredQueueState() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredPlayerOwner owner = new PlaybackQueueMirroredPlayerOwner(
                () -> true,
                () -> true,
                preparing -> events.add("preparing:" + preparing),
                () -> null,
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
        PlaybackQueueManager queueManager = queueManager(Collections.singletonList(track));
        PlaybackQueueManager.QueueTrackMatcher queueTrackMatcher = (index, matchedTrack) -> {
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
                            events.add("hasPlayer");
                            return true;
                        },
                        () -> {
                            events.add("count");
                            return 1;
                        },
                        () -> queueManager,
                        queueTrackMatcher
                );

        assertTrue(matcher.getAsBoolean());

        assertEquals(
                java.util.Arrays.asList(
                        "mirrors",
                        "hasPlayer",
                        "count",
                        "track:0:7"
                ),
                events
        );
    }

    @Test
    public void matcherReturnsFalseWhenMirrorStateOrPlayerIsMissing() {
        List<String> events = new ArrayList<>();
        BooleanSupplier matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> {
                            events.add("mirrors");
                            return false;
                        },
                        () -> {
                            events.add("hasPlayer");
                            return true;
                        },
                        () -> {
                            events.add("count");
                            return 1;
                        },
                        () -> {
                            events.add("operations");
                            return queueManager(Collections.emptyList());
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
                        () -> true,
                        () -> {
                            throw new IllegalStateException("released");
                        },
                        () -> queueManager(Collections.emptyList()),
                        (index, track) -> true
                );

        assertFalse(matcher.getAsBoolean());
    }

    @Test
    public void matcherReturnsFalseWhenPlaybackQueueManagerSupplierIsMissing() {
        List<String> events = new ArrayList<>();
        BooleanSupplier matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> {
                            events.add("mirrors");
                            return true;
                        },
                        () -> {
                            events.add("hasPlayer");
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
                java.util.Arrays.asList("mirrors", "hasPlayer"),
                events
        );
    }

    private static PlaybackQueueManager queueManager(List<Track> queue) {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new ArrayList<>(queue),
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
