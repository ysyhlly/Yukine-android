package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackRecoveryCommandOwnerTest {
    @Test
    public void delegatesRecoveryPrepareToPlaybackOwner() {
        List<String> events = new ArrayList<>();
        PlaybackRecoveryCommandOwner owner = new PlaybackRecoveryCommandOwner(
                playWhenReady -> events.add("prepare:" + playWhenReady)
        );

        owner.prepareCurrent(true);
        owner.prepareCurrent(false);

        assertEquals(
                java.util.Arrays.asList(
                        "prepare:true",
                        "prepare:false"
                ),
                events
        );
    }
}
