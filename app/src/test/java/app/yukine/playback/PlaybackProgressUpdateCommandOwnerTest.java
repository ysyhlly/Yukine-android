package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackProgressUpdateCommandOwnerTest {
    @Test
    public void delegatesProgressTickActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackProgressUpdateCommandOwner owner = new PlaybackProgressUpdateCommandOwner(
                () -> events.add("publish"),
                force -> events.add("persist:" + force)
        );

        owner.publishState();
        owner.persistPlaybackPosition();

        assertEquals(
                java.util.Arrays.asList(
                        "publish",
                        "persist:false"
                ),
                events
        );
    }
}
