package app.yukine.playback;

import android.net.Uri;

import app.yukine.model.Track;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackActiveStateOwnerTest {
    @Test
    public void delegatesActivePlaybackStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(1L);
        PlaybackActiveStateOwner owner = new PlaybackActiveStateOwner(
                () -> {
                    events.add("track");
                    return track;
                },
                () -> {
                    events.add("playing");
                    return true;
                },
                () -> {
                    events.add("preparing");
                    return false;
                }
        );

        assertSame(track, owner.currentTrack());
        assertTrue(owner.isPlaying());
        assertFalse(owner.isPreparing());
        assertEquals(
                java.util.Arrays.asList(
                        "track",
                        "playing",
                        "preparing"
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
