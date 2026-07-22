package app.yukine.playback.dsd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DsdContainerParserTest {
    @Test
    fun parsesDsfAndAlignsSeekToChannelBlocks() {
        val bytes = ByteArray(92 + 8_192)
        ascii(bytes, 0, "DSD ")
        leLong(bytes, 4, 28)
        leLong(bytes, 12, bytes.size.toLong())
        ascii(bytes, 28, "fmt ")
        leLong(bytes, 32, 52)
        leInt(bytes, 40, 1)
        leInt(bytes, 44, 0)
        leInt(bytes, 48, 2)
        leInt(bytes, 52, 2)
        leInt(bytes, 56, 2_822_400)
        leInt(bytes, 60, 1)
        leLong(bytes, 64, 2_822_400)
        leInt(bytes, 72, 4_096)
        ascii(bytes, 80, "data")
        leLong(bytes, 84, 8_204)

        val info = DsdContainerParser.parse(bytes)

        assertEquals(DsdContainer.DSF, info.container)
        assertEquals(64, info.dsdRate)
        assertEquals(1_000_000L, info.durationUs)
        assertEquals(92L, info.dataOffset)
        assertEquals(92L, info.seekByteOffset(500_000L))
        assertTrue(info.lsbFirst)
        assertFalse(info.dstCompressed)
    }

    @Test
    fun parsesUncompressedDff() {
        val prop = ByteArray(4 + 16 + 18 + 20)
        ascii(prop, 0, "SND ")
        chunk(prop, 4, "FS  ", intBytes(2_822_400))
        chunk(prop, 20, "CHNL", byteArrayOf(0, 2, 'S'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 'T'.code.toByte()))
        chunk(prop, 38, "CMPR", "DSD \u0000\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1))
        val dsd = ByteArray(16)
        val file = ByteArray(16 + 12 + prop.size + 12 + dsd.size)
        ascii(file, 0, "FRM8")
        beLong(file, 4, (file.size - 12).toLong())
        ascii(file, 12, "DSD ")
        chunk(file, 16, "PROP", prop)
        chunk(file, 28 + prop.size, "DSD ", dsd)

        val info = DsdContainerParser.parse(file)

        assertEquals(DsdContainer.DFF, info.container)
        assertEquals(2, info.channelCount)
        assertEquals(64L, info.sampleCountPerChannel)
        assertFalse(info.dstCompressed)
    }

    private fun chunk(target: ByteArray, offset: Int, id: String, payload: ByteArray) {
        ascii(target, offset, id)
        beLong(target, offset + 4, payload.size.toLong())
        payload.copyInto(target, offset + 12)
    }
    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
    private fun ascii(target: ByteArray, offset: Int, value: String) =
        value.toByteArray(Charsets.ISO_8859_1).copyInto(target, offset)
    private fun leInt(target: ByteArray, offset: Int, value: Int) =
        ByteBuffer.wrap(target, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    private fun leLong(target: ByteArray, offset: Int, value: Long) =
        ByteBuffer.wrap(target, offset, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
    private fun beLong(target: ByteArray, offset: Int, value: Long) =
        ByteBuffer.wrap(target, offset, 8).order(ByteOrder.BIG_ENDIAN).putLong(value)
}
