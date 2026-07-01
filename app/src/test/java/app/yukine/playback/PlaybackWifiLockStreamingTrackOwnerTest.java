package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import app.yukine.model.Track;

import org.junit.Test;

public class PlaybackWifiLockStreamingTrackOwnerTest {
    @Test
    public void delegatesCurrentTrackToProvider() {
        Track track = track("https://example.com/song.mp3");
        PlaybackWifiLockStreamingTrackOwner owner =
                new PlaybackWifiLockStreamingTrackOwner(() -> track);

        assertEquals(track, owner.currentTrack());
    }

    @Test
    public void returnsNullWhenProviderIsMissing() {
        PlaybackWifiLockStreamingTrackOwner owner =
                new PlaybackWifiLockStreamingTrackOwner(null);

        assertNull(owner.currentTrack());
    }

    private static Track track(String uri) {
        return new Track(1L, "Track", "Artist", "Album", 180000L, Uri.parse(uri), uri);
    }
}
