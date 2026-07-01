package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackShutdownPlaybackResourcesOwnerTest {
    @Test
    public void delegatesPlaybackResourceReleaseActions() {
        List<String> calls = new ArrayList<>();
        PlaybackShutdownPlaybackResourcesOwner owner = new PlaybackShutdownPlaybackResourcesOwner(
                () -> calls.add("lyrics"),
                () -> calls.add("wifi"),
                () -> calls.add("player")
        );

        owner.releaseLyrics();
        owner.releaseWifiLock();
        owner.releasePlayer();

        assertEquals(Arrays.asList("lyrics", "wifi", "player"), calls);
    }

    @Test
    public void releasePlayerResetsPlaybackStateAfterTransportRelease() {
        List<String> calls = new ArrayList<>();
        PlaybackShutdownPlaybackResourcesOwner owner = new PlaybackShutdownPlaybackResourcesOwner(
                () -> calls.add("lyrics"),
                () -> calls.add("wifi"),
                () -> calls.add("player"),
                () -> calls.add("mirror:false"),
                () -> calls.add("preparing:false")
        );

        owner.releasePlayer();

        assertEquals(
                Arrays.asList("player", "mirror:false", "preparing:false"),
                calls
        );
    }

    @Test
    public void ignoresMissingPlaybackResourceActions() {
        PlaybackShutdownPlaybackResourcesOwner owner = new PlaybackShutdownPlaybackResourcesOwner(
                null,
                null,
                null
        );

        owner.releaseLyrics();
        owner.releaseWifiLock();
        owner.releasePlayer();
    }
}
