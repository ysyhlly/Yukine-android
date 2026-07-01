package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlaybackQueueStreamingRestoreResolverOwnerTest {
    @Test
    public void delegatesStreamingRestoreToMediaSourceResolver() {
        List<String> events = new ArrayList<>();
        Track input = track(11L);
        Track restored = track(12L);
        PlaybackQueueStreamingRestoreResolverOwner owner = new PlaybackQueueStreamingRestoreResolverOwner(
                new FakeMediaSourceRestoreResolver(events, restored)
        );

        assertSame(restored, owner.restoredTrackForPreparation(input));
        owner.restoreHeadersForDataPath("streaming:test:11");

        org.junit.Assert.assertEquals(
                Arrays.asList(
                        "track:11",
                        "headers:streaming:test:11"
                ),
                events
        );
    }

    @Test
    public void ignoresMissingMediaSourceResolver() {
        PlaybackQueueStreamingRestoreResolverOwner owner = new PlaybackQueueStreamingRestoreResolverOwner(
                null
        );

        assertNull(owner.restoredTrackForPreparation(track(13L)));
        owner.restoreHeadersForDataPath("streaming:test:13");
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse("https://example.test/" + id + ".mp3"),
                "streaming:test:" + id
        );
    }

    private static final class FakeMediaSourceRestoreResolver
            implements PlaybackQueueStreamingRestoreResolverOwner.MediaSourceRestoreResolver {
        private final List<String> events;
        private final Track restored;

        private FakeMediaSourceRestoreResolver(List<String> events, Track restored) {
            this.events = events;
            this.restored = restored;
        }

        @Override
        public Track restoredTrackForPreparation(Track track) {
            events.add("track:" + track.id);
            return restored;
        }

        @Override
        public void restoreHeadersForDataPath(String dataPath) {
            events.add("headers:" + dataPath);
        }
    }
}
