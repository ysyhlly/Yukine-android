package app.yukine.playback;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

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

        assertSame(restored, owner.restoreTrackForPlayback(input));
        assertEquals(
                java.util.Arrays.asList(
                        "track:1",
                        "headers:streaming:test:2"
                ),
                events
        );
    }

    @Test
    public void missingRestoreActionsAreSafe() {
        PlaybackQueueStreamingRestoreOwner owner = new PlaybackQueueStreamingRestoreOwner(null, null);
        Track input = track(3L);

        assertSame(input, owner.restoreTrackForPlayback(input));
    }

    @Test
    public void rejectsTracksThatMediaResolverCannotRestoreForQueuePlayback() {
        List<String> events = new ArrayList<>();
        PlaybackQueueStreamingRestoreOwner owner = new PlaybackQueueStreamingRestoreOwner(
                track -> {
                    events.add("track:" + track.id);
                    return track;
                },
                dataPath -> events.add("headers:" + dataPath)
        );
        Track invalid = new Track(
                -1L,
                "Invalid",
                "Artist",
                "Album",
                180000L,
                Uri.parse("content://example.test/invalid"),
                "streaming:test:invalid"
        );

        assertEquals(null, owner.restoreTrackForPlayback(invalid));
        assertEquals(java.util.Collections.emptyList(), events);
    }

    @Test
    public void mediaSourceProviderConstructorRequiresMediaSourceProvider() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> new PlaybackQueueStreamingRestoreOwner((PlaybackMediaSourceProvider) null)
        );

        assertEquals("mediaSourceProvider", error.getMessage());
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
