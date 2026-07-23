package app.yukine.playback.usb

import app.yukine.playback.AudioFallbackReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbSessionOpenRetryPolicyTest {
    @Test
    fun retriesOnlyTransientSessionNegotiationFailures() {
        assertTrue(
            UsbSessionOpenRetryPolicy.shouldRetry(
                AudioFallbackReason.CLOCK_NEGOTIATION_FAILED
            )
        )
        assertTrue(
            UsbSessionOpenRetryPolicy.shouldRetry(
                AudioFallbackReason.SESSION_RECONFIGURE_FAILED
            )
        )
    }

    @Test
    fun doesNotRetryPermissionDeviceOrFormatFailures() {
        listOf(
            AudioFallbackReason.USB_PERMISSION_DENIED,
            AudioFallbackReason.NO_USB_DEVICE,
            AudioFallbackReason.NO_COMPATIBLE_ENDPOINT,
            AudioFallbackReason.FORMAT_UNSUPPORTED,
            AudioFallbackReason.NATIVE_LIBRARY_UNAVAILABLE
        ).forEach { reason ->
            assertFalse(reason.name, UsbSessionOpenRetryPolicy.shouldRetry(reason))
        }
    }
}
