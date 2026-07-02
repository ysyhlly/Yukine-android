package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackQueueCommandOwnerTest {
    @Test
    public void delegatesQueueActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                playWhenReady -> events.add("prepare:" + playWhenReady),
                () -> events.add("publish")
        );

        owner.prepareCurrent(true);
        owner.publishState();

        assertEquals(
                java.util.Arrays.asList(
                        "prepare:true",
                        "publish"
                ),
                events
        );
    }
}
