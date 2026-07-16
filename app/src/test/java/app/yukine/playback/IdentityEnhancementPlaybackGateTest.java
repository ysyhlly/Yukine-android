package app.yukine.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public final class IdentityEnhancementPlaybackGateTest {
    @After
    public void tearDown() {
        IdentityEnhancementPlaybackGate.clear();
    }

    @Test
    public void playingOrPreparingDefersEnhancement() {
        IdentityEnhancementPlaybackGate.update(snapshot(true, false));
        assertTrue(IdentityEnhancementPlaybackGate.shouldDefer());

        IdentityEnhancementPlaybackGate.update(snapshot(false, true));
        assertTrue(IdentityEnhancementPlaybackGate.shouldDefer());

        IdentityEnhancementPlaybackGate.update(snapshot(false, false));
        assertFalse(IdentityEnhancementPlaybackGate.shouldDefer());
    }

    @Test
    public void visibleAppDefersEnhancementWithoutPlayback() {
        IdentityEnhancementPlaybackGate.update(snapshot(false, false));
        IdentityEnhancementPlaybackGate.setAppVisible(true);
        assertTrue(IdentityEnhancementPlaybackGate.shouldDefer());

        IdentityEnhancementPlaybackGate.setAppVisible(false);
        assertFalse(IdentityEnhancementPlaybackGate.shouldDefer());
    }

    private static PlaybackStateSnapshot snapshot(boolean playing, boolean preparing) {
        return new PlaybackStateSnapshot(
                null,
                -1,
                0,
                0L,
                0L,
                playing,
                preparing,
                "",
                false,
                0,
                1.0f,
                1.0f,
                0L,
                null,
                null,
                0.0f,
                0L
        );
    }
}
