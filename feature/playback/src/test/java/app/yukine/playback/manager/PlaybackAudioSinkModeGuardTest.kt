package app.yukine.playback.manager

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackAudioSinkModeGuardTest {
    @Test
    fun usbExclusiveWithoutSinkFallsBackToDirectPcm() {
        assertEquals(
            AudioOutputMode.DIRECT_PCM,
            PlaybackAudioSinkModeGuard.resolve(
                AudioOutputMode.USB_EXCLUSIVE,
                hasUsbAudioSink = false
            )
        )
    }

    @Test
    fun usbExclusiveWithSinkRemainsExclusive() {
        assertEquals(
            AudioOutputMode.USB_EXCLUSIVE,
            PlaybackAudioSinkModeGuard.resolve(
                AudioOutputMode.USB_EXCLUSIVE,
                hasUsbAudioSink = true
            )
        )
    }

    @Test
    fun nonUsbModesDoNotRequireUsbSink() {
        listOf(
            AudioOutputMode.STANDARD,
            AudioOutputMode.HARDWARE_OFFLOAD,
            AudioOutputMode.DIRECT_PCM
        ).forEach { mode ->
            assertEquals(mode, PlaybackAudioSinkModeGuard.resolve(mode, hasUsbAudioSink = false))
        }
    }
}
