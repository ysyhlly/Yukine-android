package app.yukine.playback;

import android.net.Uri;

import androidx.media3.common.MediaItem;

import app.yukine.model.Track;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

public class PlaybackPrecacheStateOwnerTest {
    @Test
    public void delegatesPrecacheStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(1L);
        MediaItem mediaItem = MediaItem.fromUri("https://example.test/one.mp3");
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackPrecacheStateOwner owner = new PlaybackPrecacheStateOwner(
                () -> {
                    events.add("track");
                    return track;
                },
                () -> {
                    events.add("mediaItem");
                    return mediaItem;
                },
                () -> {
                    events.add("diagnostics");
                    return diagnostics;
                }
        );

        assertSame(track, owner.currentTrack());
        assertSame(mediaItem, owner.currentPlayerMediaItem());
        assertSame(diagnostics, owner.streamingDiagnostics());
        assertEquals(
                java.util.Arrays.asList(
                        "track",
                        "mediaItem",
                        "diagnostics"
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
