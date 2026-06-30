package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackProgressUpdateStateOwnerTest {
    @Test
    public void delegatesProgressStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackProgressUpdateStateOwner owner = new PlaybackProgressUpdateStateOwner(
                () -> {
                    events.add("playing");
                    return true;
                },
                () -> {
                    events.add("preparing");
                    return false;
                }
        );

        assertTrue(owner.isPlaying());
        assertFalse(owner.isPreparing());
        assertEquals(
                java.util.Arrays.asList(
                        "playing",
                        "preparing"
                ),
                events
        );
    }
}
