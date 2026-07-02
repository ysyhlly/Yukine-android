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
                () -> {
                    events.add("hasMultiple");
                    return true;
                },
                () -> {
                    events.add("atEnd");
                    return false;
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
                        "hasMultiple",
                        "atEnd",
                        "repeat",
                        "volume"
                ),
                events
        );
    }

    @Test
    public void crossfadeAdvancePolicyUsesQueueStateAndRepeatMode() {
        PlaybackCrossfadeStateOwner missingQueue = owner(false, false, PlaybackRepeatMode.REPEAT_ALL);
        PlaybackCrossfadeStateOwner singleTrack = owner(false, false, PlaybackRepeatMode.REPEAT_ALL);
        PlaybackCrossfadeStateOwner repeatOffBeforeEnd = owner(
                true,
                false,
                PlaybackRepeatMode.REPEAT_OFF
        );
        PlaybackCrossfadeStateOwner repeatOffAtEnd = owner(
                true,
                true,
                PlaybackRepeatMode.REPEAT_OFF
        );
        PlaybackCrossfadeStateOwner repeatAllAtEnd = owner(
                true,
                true,
                PlaybackRepeatMode.REPEAT_ALL
        );

        assertFalse(missingQueue.canCrossfadeAdvance());
        assertFalse(singleTrack.canCrossfadeAdvance());
        assertTrue(repeatOffBeforeEnd.canCrossfadeAdvance());
        assertFalse(repeatOffAtEnd.canCrossfadeAdvance());
        assertTrue(repeatAllAtEnd.canCrossfadeAdvance());
    }

    private static PlaybackCrossfadeStateOwner owner(
            boolean hasMultipleTracks,
            boolean atEndOfQueue,
            int repeatMode
    ) {
        return new PlaybackCrossfadeStateOwner(
                () -> false,
                () -> true,
                () -> true,
                () -> repeatMode,
                () -> hasMultipleTracks,
                () -> atEndOfQueue,
                () -> 1.0f
        );
    }
}
