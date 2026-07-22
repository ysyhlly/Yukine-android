package app.yukine.playback.dsd

import app.yukine.playback.AudioFallbackReason
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DoPPackerTest {
    @Test
    fun alternatesMarkersPerStereoFrame() {
        val packed = DoPPacker().pack(
            listOf(
                byteArrayOf(0x11, 0x22, 0x33, 0x44),
                byteArrayOf(0x55, 0x66, 0x77, 0x7f)
            )
        )

        assertArrayEquals(
            byteArrayOf(
                0x11, 0x22, 0x05, 0x55, 0x66, 0x05,
                0x33, 0x44, 0xfa.toByte(), 0x77, 0x7f, 0xfa.toByte()
            ),
            packed
        )
    }

    @Test
    fun unknownOrUnverifiedNativeProfileFallsBackToDop() {
        val decision = DsdOutputPolicy.decide(
            bitPerfectRequested = true,
            usbExclusiveRequested = true,
            vendorId = 0x20B1,
            productId = 0x000A,
            dsdRate = 64,
            dopPcmRates = setOf(176_400)
        )

        assertEquals(DsdTransportDecision.DOP, decision.transport)
    }

    @Test
    fun unsupportedDopFailsWithTypedReason() {
        val decision = DsdOutputPolicy.decide(
            bitPerfectRequested = true,
            usbExclusiveRequested = true,
            vendorId = 1,
            productId = 2,
            dsdRate = 512,
            dopPcmRates = setOf(192_000)
        )

        assertEquals(DsdTransportDecision.ERROR, decision.transport)
        assertEquals(AudioFallbackReason.DOP_UNSUPPORTED, decision.fallbackReason)
    }

    @Test
    fun dsfBlocksAreDeinterleavedAndLsbBitsAreNormalized() {
        val channels = DsdPayloadDecoder.toCanonicalChannels(
            payload = byteArrayOf(0x01, 0x02, 0x04, 0x08),
            channelCount = 2,
            metadata = DsdFormatMetadata(DsdContainer.DSF, 2, lsbFirst = true, dstCompressed = false)
        )

        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x40), channels[0])
        assertArrayEquals(byteArrayOf(0x20, 0x10), channels[1])
    }
}
