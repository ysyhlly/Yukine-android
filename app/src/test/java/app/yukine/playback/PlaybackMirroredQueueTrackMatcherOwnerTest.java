package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import androidx.media3.common.MediaItem;

import app.yukine.model.Track;
import org.junit.Test;

public class PlaybackMirroredQueueTrackMatcherOwnerTest {
    @Test
    public void delegatesPlayerMediaItemAndTrackToMatcher() {
        MediaItem mediaItem = new MediaItem.Builder().setUri("https://example.test/one.mp3").build();
        Track track = track();
        FakeTrackMediaItemMatcher matcher = new FakeTrackMediaItemMatcher(true);
        PlaybackMirroredQueueTrackMatcherOwner owner = new PlaybackMirroredQueueTrackMatcherOwner(
                index -> index == 2 ? mediaItem : null,
                matcher
        );

        assertEquals(true, owner.matches(2, track));
        assertEquals(mediaItem, matcher.lastMediaItem);
        assertEquals(track, matcher.lastTrack);
    }

    @Test
    public void returnsFalseWhenMatcherDependenciesAreMissingOrPlayerCannotRead() {
        Track track = track();
        PlaybackMirroredQueueTrackMatcherOwner missingProvider =
                new PlaybackMirroredQueueTrackMatcherOwner(null, (mediaItem, itemTrack) -> true);
        PlaybackMirroredQueueTrackMatcherOwner missingMatcher =
                new PlaybackMirroredQueueTrackMatcherOwner(index -> null, null);
        PlaybackMirroredQueueTrackMatcherOwner throwingProvider =
                new PlaybackMirroredQueueTrackMatcherOwner(
                        index -> {
                            throw new IndexOutOfBoundsException("missing");
                        },
                        (mediaItem, itemTrack) -> true
                );

        assertEquals(false, missingProvider.matches(0, track));
        assertEquals(false, missingMatcher.matches(0, track));
        assertEquals(false, throwingProvider.matches(0, track));
    }

    private static Track track() {
        return new Track(
                11L,
                "Track",
                "Artist",
                "Album",
                1000L,
                Uri.parse("https://example.test/one.mp3"),
                "streaming:test:11"
        );
    }

    private static final class FakeTrackMediaItemMatcher
            implements PlaybackMirroredQueueTrackMatcherOwner.TrackMediaItemMatcher {
        private final boolean matches;
        private MediaItem lastMediaItem;
        private Track lastTrack;

        private FakeTrackMediaItemMatcher(boolean matches) {
            this.matches = matches;
        }

        @Override
        public boolean mediaItemMatchesTrackForReuse(MediaItem mediaItem, Track track) {
            lastMediaItem = mediaItem;
            lastTrack = track;
            return matches;
        }
    }
}
