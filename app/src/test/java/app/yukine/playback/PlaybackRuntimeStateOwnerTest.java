package app.yukine.playback;

import android.net.Uri;

import app.yukine.model.Track;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackRuntimeStateOwnerTest {
    @Test
    public void delegatesRuntimeStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(1L);
        PlaybackRuntimeStateOwner owner = new PlaybackRuntimeStateOwner(
                () -> {
                    events.add("player");
                    return null;
                },
                () -> {
                    events.add("mirrors");
                    return true;
                },
                () -> {
                    events.add("track");
                    return track;
                }
        );

        assertNull(owner.player());
        assertTrue(owner.playerMirrorsQueue());
        assertSame(track, owner.currentTrack());
        assertEquals(
                java.util.Arrays.asList(
                        "player",
                        "mirrors",
                        "track"
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
                Uri.parse("content://media/audio/" + id),
                "/music/" + id + ".mp3"
        );
    }
}
