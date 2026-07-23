package app.yukine.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackBootRestorePolicyTest {
    @Test
    public void target35DefersMediaPlaybackServiceOnAndroid15() {
        assertFalse(PlaybackBootRestorePolicy.canStartMediaPlaybackService(35, 35));
        assertFalse(PlaybackBootRestorePolicy.canStartMediaPlaybackService(36, 35));
    }

    @Test
    public void olderDeviceOrTargetKeepsLegacyBootRestore() {
        assertTrue(PlaybackBootRestorePolicy.canStartMediaPlaybackService(34, 35));
        assertTrue(PlaybackBootRestorePolicy.canStartMediaPlaybackService(35, 34));
    }
}
