package app.yukine.playback.manager

import app.yukine.playback.AudioFallbackReason
import app.yukine.playback.AudioOutputPhase
import app.yukine.playback.AudioOutputSnapshot
import app.yukine.playback.AudioTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAudioOutputCoordinatorTest {
    private val usbProfile = AudioDeviceCapabilityProbe.AudioDeviceProfile(
        nativeSampleRateHz = 48_000,
        supportsOffload = false,
        supportedSampleRates = listOf(44_100, 48_000),
        deviceName = "USB",
        isUsbAudioDeviceConnected = true,
        usbDeviceName = "DAC"
    )

    @Test
    fun usbRequestIsNegotiatingUntilNativeSinkReportsActive() {
        val coordinator = PlaybackAudioOutputCoordinator()
        val target = coordinator.updateRequests(true, true, usbProfile)
        coordinator.onTargetMode(target, 44_100, "DAC")

        assertEquals(AudioOutputPhase.NEGOTIATING, coordinator.snapshot().phase)
        assertFalse(coordinator.usbActive())

        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.ACTIVE,
                "DAC",
                44_100,
                24,
                2,
                0,
                AudioFallbackReason.NONE,
                4, 8, 8, 0, 0, 4410, 44_100.0, ""
            )
        )
        assertTrue(coordinator.usbActive())
    }

    @Test
    fun typedUsbFailureSurvivesSystemFallback() {
        val coordinator = PlaybackAudioOutputCoordinator()
        coordinator.onSystemFallback(
            AudioOutputMode.DIRECT_PCM,
            48_000,
            AudioFallbackReason.DEVICE_DETACHED,
            "detached"
        )
        coordinator.onTargetMode(AudioOutputMode.DIRECT_PCM, 48_000, "")

        assertEquals(AudioOutputPhase.FALLBACK, coordinator.snapshot().phase)
        assertEquals(AudioFallbackReason.DEVICE_DETACHED, coordinator.snapshot().fallbackReason)
        assertEquals(AudioTransport.SYSTEM_DIRECT_PCM, coordinator.snapshot().transport)
    }

    @Test
    fun failedUsbSessionDoesNotImmediatelyRenegotiateOnDuplicateDeviceCallback() {
        val coordinator = PlaybackAudioOutputCoordinator()
        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                "DAC",
                48_000,
                16,
                2,
                0,
                AudioFallbackReason.NO_COMPATIBLE_ENDPOINT,
                0, 0, 0, 0, 0, 0, 0.0, "unsupported alternate"
            )
        )

        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun physicalDetachClearsUsbFailureLatchForReconnect() {
        val coordinator = PlaybackAudioOutputCoordinator()
        coordinator.updateRequests(true, true, usbProfile)
        coordinator.onSystemFallback(
            AudioOutputMode.DIRECT_PCM,
            48_000,
            AudioFallbackReason.TRANSFER_FAILED,
            "transfer failed"
        )
        val disconnectedProfile = usbProfile.copy(isUsbAudioDeviceConnected = false)

        assertEquals(
            AudioOutputMode.DIRECT_PCM,
            coordinator.updateRequests(true, true, disconnectedProfile)
        )
        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
    }
}
