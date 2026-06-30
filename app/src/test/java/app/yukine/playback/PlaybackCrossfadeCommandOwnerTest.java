package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlaybackCrossfadeCommandOwnerTest {
    @Test
    public void delegatesCrossfadeActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackCrossfadeCommandOwner owner = new PlaybackCrossfadeCommandOwner(
                enabled -> events.add("fade:" + enabled),
                volume -> events.add("volume:" + volume),
                () -> events.add("nextImmediately"),
                () -> events.add("applyAppVolume")
        );

        owner.setFadeOutAdvancing(true);
        owner.setPlayerVolume(0.5f);
        owner.skipToNextImmediately();
        owner.applyAppVolume();
        owner.setFadeOutAdvancing(false);

        assertEquals(
                java.util.Arrays.asList(
                        "fade:true",
                        "volume:0.5",
                        "nextImmediately",
                        "applyAppVolume",
                        "fade:false"
                ),
                events
        );
    }
}
