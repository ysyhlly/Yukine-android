package app.yukine.playback.usb

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPcmRepackerTest {
    @Test
    fun leftAlignsPacked16BitSamplesIn32BitSubslots() {
        val source = byteArrayOf(0x34, 0x12, 0x00, 0x80.toByte())

        val output = repackPcmSamples(source, 2, 4)

        assertArrayEquals(
            byteArrayOf(0, 0, 0x34, 0x12, 0, 0, 0x00, 0x80.toByte()),
            output
        )
    }

    @Test
    fun leftAlignsPacked24BitSamplesIn32BitSubslots() {
        val source = byteArrayOf(0x56, 0x34, 0x12, 0x00, 0x00, 0x80.toByte())

        val output = repackPcmSamples(source, 3, 4)

        assertArrayEquals(
            byteArrayOf(0, 0x56, 0x34, 0x12, 0, 0x00, 0x00, 0x80.toByte()),
            output
        )
    }

    @Test
    fun preservesAlreadyMatchingContainer() {
        val source = byteArrayOf(1, 2, 3, 4)

        assertSame(source, repackPcmSamples(source, 2, 2))
    }

    @Test
    fun carriesSplitSampleAcrossMedia3Buffers() {
        val first = repackPcmChunk(
            source = byteArrayOf(0x34),
            pendingRemainder = byteArrayOf(),
            sourceSampleBytes = 2,
            targetSampleBytes = 4
        )
        val second = repackPcmChunk(
            source = byteArrayOf(0x12, 0x78, 0x56, 0x01),
            pendingRemainder = first.remainder,
            sourceSampleBytes = 2,
            targetSampleBytes = 4
        )

        assertArrayEquals(byteArrayOf(), first.payload)
        assertArrayEquals(byteArrayOf(0x34), first.remainder)
        assertArrayEquals(
            byteArrayOf(0, 0, 0x34, 0x12, 0, 0, 0x78, 0x56),
            second.payload
        )
        assertArrayEquals(byteArrayOf(0x01), second.remainder)
    }

    @Test
    fun matchingContainerAcceptsArbitraryChunkBoundaries() {
        val source = byteArrayOf(1, 2, 3)

        val chunk = repackPcmChunk(source, byteArrayOf(), 2, 2)

        assertSame(source, chunk.payload)
        assertArrayEquals(byteArrayOf(), chunk.remainder)
    }

    @Test
    fun acceptsOnlyDecodedIntegerPcmAndNeverCompressedFlac() {
        val pcm16 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()
        val pcmFloat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .build()
        val flac = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .build()

        assertTrue(isUsbPcmFormat(pcm16))
        assertFalse(isUsbPcmFormat(pcmFloat))
        assertFalse(isUsbPcmFormat(flac))
    }
}
