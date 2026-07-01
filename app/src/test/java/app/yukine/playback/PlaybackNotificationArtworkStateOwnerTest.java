package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PlaybackNotificationArtworkStateOwnerTest {
    @Test
    public void delegatesArtworkStateToPlaybackOwner() {
        List<String> events = new ArrayList<>();
        Track track = new Track(31L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:31");
        PlaybackNotificationArtworkStateOwner owner = new PlaybackNotificationArtworkStateOwner(
                () -> {
                    events.add("track");
                    return track;
                }
        );

        assertSame(track, owner.currentTrack());
        assertEquals(java.util.Collections.singletonList("track"), events);
    }

    @Test
    public void missingProviderReturnsNullTrack() {
        PlaybackNotificationArtworkStateOwner owner = new PlaybackNotificationArtworkStateOwner(null);

        assertSame(null, owner.currentTrack());
    }
}
