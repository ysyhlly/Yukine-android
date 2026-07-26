package app.yukine.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.yukine.playback.manager.AudioOutputMode;
import org.junit.Test;

public final class PlaybackUsbDeviceChangePolicyTest {
    @Test
    public void replacingUsbDeviceForcesRebuildEvenWhenModeIsUnchanged() {
        assertTrue(PlaybackServiceRuntime.shouldForceUsbRebuild(
                AudioOutputMode.USB_EXCLUSIVE,
                AudioOutputMode.USB_EXCLUSIVE,
                10,
                11
        ));
    }

    @Test
    public void duplicateCallbackForSameUsbDeviceDoesNotRebuild() {
        assertFalse(PlaybackServiceRuntime.shouldForceUsbRebuild(
                AudioOutputMode.USB_EXCLUSIVE,
                AudioOutputMode.USB_EXCLUSIVE,
                11,
                11
        ));
    }

    @Test
    public void detachAndModeFallbackUseNormalModeSwitchPath() {
        assertFalse(PlaybackServiceRuntime.shouldForceUsbRebuild(
                AudioOutputMode.USB_EXCLUSIVE,
                AudioOutputMode.DIRECT_PCM,
                11,
                -1
        ));
    }
}
