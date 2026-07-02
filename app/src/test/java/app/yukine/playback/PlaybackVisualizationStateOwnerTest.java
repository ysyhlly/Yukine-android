package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaybackVisualizationStateOwnerTest {
    @Test
    public void delegatesVisualizationStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackVisualizationStateOwner owner = new PlaybackVisualizationStateOwner(
                () -> {
                    events.add("visible");
                    return true;
                },
                durationMs -> {
                    events.add("buffered:" + durationMs);
                    return 0.5f;
                },
                () -> events.add("publish")
        );

        assertTrue(owner.isAppVisible());
        assertEquals(0.5f, owner.bufferedProgress(1200L), 0.001f);
        owner.publishState();
        assertEquals(
                java.util.Arrays.asList(
                        "visible",
                        "buffered:1200",
                        "publish"
                ),
                events
        );
    }
}
