package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackCrossfadeStateOwnerTest {
    @Test
    public void delegatesCrossfadeStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackCrossfadeStateOwner owner = new PlaybackCrossfadeStateOwner(
                () -> {
                    events.add("fadeOut");
                    return false;
                },
                () -> {
                    events.add("player");
                    return true;
                },
                () -> {
                    events.add("playing");
                    return true;
                },
                () -> {
                    events.add("repeat");
                    return 2;
                },
                repeatMode -> {
                    events.add("policy:" + repeatMode);
                    return repeatMode == 2;
                },
                () -> {
                    events.add("volume");
                    return 0.75f;
                }
        );

        assertFalse(owner.fadeOutAdvancing());
        assertTrue(owner.playerAvailable());
        assertTrue(owner.isPlaying());
        assertTrue(owner.canCrossfadeAdvance());
        assertEquals(0.75f, owner.baseVolume(), 0.001f);
        assertEquals(
                java.util.Arrays.asList(
                        "fadeOut",
                        "player",
                        "playing",
                        "repeat",
                        "policy:2",
                        "volume"
                ),
                events
        );
    }
}
