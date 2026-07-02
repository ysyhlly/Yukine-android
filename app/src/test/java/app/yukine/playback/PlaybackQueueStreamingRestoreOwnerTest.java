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
                track -> {
                    events.add("track:" + track.id);
                    return restored;
                },
                dataPath -> events.add("headers:" + dataPath)
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

    @Test
    public void missingRestoreActionsAreSafe() {
        PlaybackQueueStreamingRestoreOwner owner = new PlaybackQueueStreamingRestoreOwner(null, null);

        org.junit.Assert.assertNull(owner.restoredTrackFor(track(3L)));
        owner.restoreForDataPath("streaming:test:3");
    }

    @Test
    public void mediaSourceProviderFactoryIsSafeWhenProviderIsMissing() {
        PlaybackQueueStreamingRestoreOwner owner =
                PlaybackQueueStreamingRestoreOwner.fromMediaSourceProvider(null);

        org.junit.Assert.assertNull(owner.restoredTrackFor(track(4L)));
        owner.restoreForDataPath("streaming:test:4");
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
