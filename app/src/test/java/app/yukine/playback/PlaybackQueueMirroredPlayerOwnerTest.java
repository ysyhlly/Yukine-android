package app.yukine.playback;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
        PlaybackQueueManager.QueueTrackMatcher queueTrackMatcher = (index, matchedTrack) -> {
            events.add("track:" + index + ":" + matchedTrack.id);
            return matchedTrack == track;
        };
        PlaybackQueueMirroredPlayerOwner.MirroredQueueMatcher matcher =
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
                        () -> (itemCount, suppliedMatcher) -> {
                            events.add("match:" + itemCount);
                            return suppliedMatcher.matches(0, track);
                        },
                        queueTrackMatcher
                );

        assertTrue(matcher.matchesCurrentQueue());

        assertEquals(
                java.util.Arrays.asList(
                        "mirrors",
                        "hasPlayer",
                        "count",
                        "match:1",
                        "track:0:7"
                ),
                events
        );
    }

    @Test
    public void matcherReturnsFalseWhenMirrorStateOrPlayerIsMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredPlayerOwner.MirroredQueueMatcher matcher =
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
                        () -> (itemCount, suppliedMatcher) -> {
                            events.add("operations");
                            return true;
                        },
                        (index, track) -> true
                );

        assertFalse(matcher.matchesCurrentQueue());
        assertEquals(java.util.Collections.singletonList("mirrors"), events);
    }

    @Test
    public void matcherReturnsFalseWhenPlayerStateCannotBeRead() {
        PlaybackQueueMirroredPlayerOwner.MirroredQueueMatcher matcher =
                PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                        () -> true,
                        () -> true,
                        () -> {
                            throw new IllegalStateException("released");
                        },
                        () -> (itemCount, suppliedMatcher) -> true,
                        (index, track) -> true
                );

        assertFalse(matcher.matchesCurrentQueue());
    }

    @Test
    public void matcherReturnsFalseWhenPlaybackQueueManagerSupplierIsMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredPlayerOwner.MirroredQueueMatcher matcher =
                PlaybackQueueMirroredPlayerOwner.fromPlaybackQueueManager(
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

        assertFalse(matcher.matchesCurrentQueue());
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
