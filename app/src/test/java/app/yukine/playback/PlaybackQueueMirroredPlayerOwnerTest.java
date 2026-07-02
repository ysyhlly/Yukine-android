package app.yukine.playback;

import android.net.Uri;

import app.yukine.model.Track;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                () -> {
                    events.add("currentTrack");
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
                        "currentTrack",
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
                            events.add("hasPlayer");
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
                        "hasPlayer",
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

}
