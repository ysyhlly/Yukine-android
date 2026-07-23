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
        coordinator.onSystemFallback(
            AudioOutputMode.DIRECT_PCM,
            48_000,
            AudioFallbackReason.DEVICE_DETACHED,
            "detached"
        )
        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun fallbackSnapshotWithoutTypedReasonDoesNotLatchUsb() {
        val coordinator = PlaybackAudioOutputCoordinator()
        coordinator.updateRequests(true, true, usbProfile)
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                "DAC",
                48_000,
                24,
                2,
                0,
                AudioFallbackReason.NONE,
                0, 0, 0, 0, 0, 0, 0.0, ""
            )
        )

        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun differentKnownMediaFormatAllowsExactlyOneFreshUsbAttempt() {
        val coordinator = PlaybackAudioOutputCoordinator()
        coordinator.updateRequests(true, true, usbProfile)
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                "DAC",
                44_100,
                24,
                2,
                0,
                AudioFallbackReason.CLOCK_NEGOTIATION_FAILED,
                0, 0, 0, 0, 0, 0, 0.0, "clock failed"
            )
        )

        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
        assertTrue(coordinator.armUsbRetryForMediaItemTransition(48_000, 24, 2, false))
        assertFalse(coordinator.armUsbRetryForMediaItemTransition(48_000, 24, 2, false))
        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun identicalFailedMediaFormatDoesNotReenterUsb() {
        val coordinator = failedCoordinator(44_100, 24, 2)

        assertFalse(coordinator.armUsbRetryForMediaItemTransition(44_100, 24, 2, false))
        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun incompleteNextMediaFormatDoesNotReenterUsb() {
        val coordinator = failedCoordinator(44_100, 24, 2)

        assertFalse(coordinator.armUsbRetryForMediaItemTransition(0, 24, 2, false))
        assertFalse(coordinator.armUsbRetryForMediaItemTransition(48_000, 0, 2, false))
        assertFalse(coordinator.armUsbRetryForMediaItemTransition(48_000, 24, 0, false))
        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun unknownDecodedPcmAllowsOneRetryAfterFormatOrSessionFailure() {
        listOf(
            AudioFallbackReason.NO_COMPATIBLE_ENDPOINT,
            AudioFallbackReason.CLOCK_NEGOTIATION_FAILED,
            AudioFallbackReason.SESSION_RECONFIGURE_FAILED,
            AudioFallbackReason.FORMAT_UNSUPPORTED
        ).forEach { reason ->
            val coordinator = failedCoordinator(0, 0, 0, reason)

            assertTrue(reason.name, coordinator.armUsbRetryForMediaItemTransition(0, 0, 0, true))
            assertFalse(reason.name, coordinator.armUsbRetryForMediaItemTransition(0, 0, 0, true))
            assertEquals(reason.name, AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
            assertEquals(reason.name, AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
        }
    }

    @Test
    fun knownStreamingPcmAllowsOneRetryAfterSameFormatFailure() {
        val coordinator = failedCoordinator(44_100, 24, 2)

        assertTrue(coordinator.armUsbRetryForMediaItemTransition(44_100, 24, 2, true))
        assertFalse(coordinator.armUsbRetryForMediaItemTransition(44_100, 24, 2, true))
        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun unknownDecodedPcmStaysLatchedAfterDevicePermissionOrTransferFailure() {
        listOf(
            AudioFallbackReason.NO_USB_DEVICE,
            AudioFallbackReason.USB_PERMISSION_DENIED,
            AudioFallbackReason.NATIVE_LIBRARY_UNAVAILABLE,
            AudioFallbackReason.TRANSFER_FAILED
        ).forEach { reason ->
            val coordinator = failedCoordinator(0, 0, 0, reason)

            assertFalse(reason.name, coordinator.armUsbRetryForMediaItemTransition(0, 0, 0, true))
            assertEquals(reason.name, AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
        }
    }

    @Test
    fun knownDifferentFormatStaysLatchedAfterPermissionFailure() {
        val coordinator = failedCoordinator(
            44_100,
            24,
            2,
            AudioFallbackReason.USB_PERMISSION_DENIED
        )

        assertFalse(coordinator.armUsbRetryForMediaItemTransition(96_000, 24, 2, false))
        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun explicitUsbToggleClearsFormatFailureLatch() {
        val coordinator = failedCoordinator(44_100, 24, 2)

        assertEquals(AudioOutputMode.DIRECT_PCM, coordinator.updateRequests(true, false, usbProfile))
        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun successfulRateSwitchClearsFormatFailureLatch() {
        val coordinator = PlaybackAudioOutputCoordinator()
        coordinator.updateRequests(true, true, usbProfile)
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                "DAC",
                44_100,
                16,
                2,
                0,
                AudioFallbackReason.SESSION_RECONFIGURE_FAILED,
                0, 0, 0, 0, 0, 0, 0.0, "reconfigure failed"
            )
        )
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.ACTIVE,
                "DAC",
                96_000,
                24,
                2,
                0,
                AudioFallbackReason.NONE,
                4, 10, 10, 0, 0, 9600, 96_000.0, ""
            )
        )

        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
    }

    @Test
    fun differentNegotiatingFormatClearsPreviousFormatFailure() {
        val coordinator = PlaybackAudioOutputCoordinator()
        coordinator.updateRequests(true, true, usbProfile)
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                "DAC",
                44_100,
                16,
                2,
                0,
                AudioFallbackReason.CLOCK_NEGOTIATION_FAILED,
                0, 0, 0, 0, 0, 0, 0.0, "44.1 failed"
            )
        )
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot.transition(
                AudioTransport.USB_PCM,
                "DAC",
                44_100,
                96_000,
                24,
                2
            )
        )

        assertEquals(AudioOutputMode.USB_EXCLUSIVE, coordinator.updateRequests(true, true, usbProfile))
    }

    private fun failedCoordinator(
        sampleRateHz: Int,
        bitDepth: Int,
        channelCount: Int,
        reason: AudioFallbackReason = AudioFallbackReason.CLOCK_NEGOTIATION_FAILED
    ): PlaybackAudioOutputCoordinator = PlaybackAudioOutputCoordinator().also { coordinator ->
        coordinator.updateRequests(true, true, usbProfile)
        coordinator.onUsbSnapshot(
            AudioOutputSnapshot(
                AudioTransport.USB_PCM,
                AudioOutputPhase.FALLBACK,
                "DAC",
                sampleRateHz,
                bitDepth,
                channelCount,
                0,
                reason,
                0, 0, 0, 0, 0, 0, 0.0, "usb failed"
            )
        )
    }
}
