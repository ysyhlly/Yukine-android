package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PlaybackPositionStateOwnerTest {
    @Test
    public void delegatesPositionStateToPlaybackBoundary() {
        List<String> events = new ArrayList<>();
        Track track = new Track(23L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:23");
        PlaybackPositionStateOwner owner = new PlaybackPositionStateOwner(
                () -> {
                    events.add("track");
                    return track;
                },
                () -> {
                    events.add("position");
                    return 321L;
                }
        );

        assertSame(track, owner.currentTrack());
        assertEquals(321L, owner.positionMs());
        assertEquals(
                java.util.Arrays.asList(
                        "track",
                        "position"
                ),
                events
        );
    }
}
