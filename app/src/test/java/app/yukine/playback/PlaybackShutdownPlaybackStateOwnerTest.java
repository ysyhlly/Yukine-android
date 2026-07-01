package app.yukine.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackShutdownPlaybackStateOwnerTest {
    @Test
    public void delegatesPlaybackAndPreparingState() {
        PlaybackShutdownPlaybackStateOwner owner = new PlaybackShutdownPlaybackStateOwner(
                () -> true,
                () -> true
        );

        assertTrue(owner.isPlaying());
        assertTrue(owner.isPreparing());
    }

    @Test
    public void defaultsMissingStateProvidersToInactive() {
        PlaybackShutdownPlaybackStateOwner owner = new PlaybackShutdownPlaybackStateOwner(null, null);

        assertFalse(owner.isPlaying());
        assertFalse(owner.isPreparing());
    }
}
