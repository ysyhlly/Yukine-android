package app.yukine.playback;

import android.net.Uri;

import app.yukine.model.Track;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PlaybackQueueStreamingRestoreOwnerTest {
    @Test
    public void delegatesStreamingRestoreToMediaResolver() {
        List<String> events = new ArrayList<>();
        Track input = track(1L);
        Track restored = track(2L);
        PlaybackQueueStreamingRestoreOwner owner = new PlaybackQueueStreamingRestoreOwner(
                new PlaybackQueueStreamingRestoreOwner.StreamingRestoreResolver() {
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
        );

        assertSame(restored, owner.restoredTrackFor(input));
        owner.restoreForDataPath("streaming:test:1");
        assertEquals(
                java.util.Arrays.asList(
                        "track:1",
                        "headers:streaming:test:1"
                ),
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
                Uri.parse("https://example.test/" + id + ".mp3"),
                "streaming:test:" + id
        );
    }
}
