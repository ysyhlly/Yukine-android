package app.yukine.playback.usb

import android.hardware.usb.UsbConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioStreamConfigTest {
    @Test
    fun calculatesPacked24BitStereoFrames() {
        val config = UsbAudioStreamConfig(
            endpointAddress = 1,
            maxPacketSize = 294,
            sampleRateHz = 44_100,
            bitDepth = 24,
            channelCount = 2,
            interfaceNumber = 1,
            alternateSetting = 2
        )
        assertEquals(6, config.bytesPerFrame)
        assertEquals(49, config.framesPerPacket)
        assertFalse(config.allowUac2PcmRateMismatch)
    }

    @Test
    fun carriesExplicitUac2PcmRateMismatchCompatibilityFlag() {
        val config = UsbAudioStreamConfig(
            endpointAddress = 1,
            maxPacketSize = 294,
            sampleRateHz = 44_100,
            bitDepth = 24,
            channelCount = 2,
            interfaceNumber = 1,
            alternateSetting = 2,
            allowUac2PcmRateMismatch = true
        )

        assertTrue(config.allowUac2PcmRateMismatch)
    }

    @Test
    fun classifiesEndpointTransportWithoutClaimingControlEndpoints() {
        assertEquals(UsbPcmTransportKind.BULK, usbPcmTransportKind(UsbConstants.USB_ENDPOINT_XFER_BULK))
        assertEquals(UsbPcmTransportKind.ISOCHRONOUS, usbPcmTransportKind(UsbConstants.USB_ENDPOINT_XFER_ISOC))
        assertEquals(UsbPcmTransportKind.UNSUPPORTED, usbPcmTransportKind(UsbConstants.USB_ENDPOINT_XFER_CONTROL))
    }

    @Test
    fun decodesHighBandwidthTransactionsFromWMaxPacketSize() {
        val wMaxPacketSize = 512 or (2 shl 11)
        val config = UsbAudioStreamConfig(
            endpointAddress = 1,
            maxPacketSize = wMaxPacketSize,
            sampleRateHz = 192_000,
            bitDepth = 32,
            channelCount = 2,
            interfaceNumber = 1,
            alternateSetting = 3
        )

        assertEquals(3, config.transactionsPerServiceInterval)
        assertEquals(1_536, config.maximumPayloadBytes)
        assertEquals(192, config.framesPerPacket)
    }
}
