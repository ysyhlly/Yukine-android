package app.yukine.playback.dsd

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal enum class DsdContainer { DSF, DFF }

internal data class DsdStreamInfo(
    val container: DsdContainer,
    val sampleRateHz: Int,
    val channelCount: Int,
    val sampleCountPerChannel: Long,
    val dataOffset: Long,
    val dataLength: Long,
    val blockSizePerChannel: Int,
    val lsbFirst: Boolean,
    val dstCompressed: Boolean
) {
    val dsdRate: Int get() = if (sampleRateHz > 0) sampleRateHz / 44_100 else 0
    val durationUs: Long
        get() = if (sampleRateHz > 0) sampleCountPerChannel * 1_000_000L / sampleRateHz else 0L

    fun seekByteOffset(timeUs: Long): Long {
        if (durationUs <= 0L || dataLength <= 0L) return dataOffset
        val clamped = timeUs.coerceIn(0L, durationUs)
        val raw = dataOffset + dataLength * clamped / durationUs
        val alignment = if (container == DsdContainer.DSF) {
            (blockSizePerChannel.coerceAtLeast(1) * channelCount.coerceAtLeast(1)).toLong()
        } else {
            channelCount.coerceAtLeast(1).toLong()
        }
        return dataOffset + ((raw - dataOffset) / alignment) * alignment
    }
}

/** Strict DSF and uncompressed DSDIFF header parser. */
internal object DsdContainerParser {
    private const val MIN_DSF_HEADER = 92

    fun parse(bytes: ByteArray): DsdStreamInfo {
        require(bytes.size >= 12) { "DSD file is too short" }
        return when (ascii(bytes, 0, 4)) {
            "DSD " -> parseDsf(bytes)
            "FRM8" -> parseDff(bytes)
            else -> error("Unsupported DSD container")
        }
    }

    private fun parseDsf(bytes: ByteArray): DsdStreamInfo {
        require(bytes.size >= MIN_DSF_HEADER) { "Truncated DSF header" }
        require(leLong(bytes, 4) == 28L) { "Invalid DSF DSD chunk" }
        require(ascii(bytes, 28, 4) == "fmt ") { "Missing DSF fmt chunk" }
        val fmtSize = leLong(bytes, 32)
        require(fmtSize >= 52L && 28L + fmtSize <= bytes.size.toLong()) { "Invalid DSF fmt size" }
        val channels = leInt(bytes, 52)
        val sampleRate = leInt(bytes, 56)
        val bitsPerSample = leInt(bytes, 60)
        val sampleCount = leLong(bytes, 64)
        val blockSize = leInt(bytes, 72)
        require(channels in 1..2) { "Only mono/stereo DSF is supported" }
        require(sampleRate > 0 && sampleCount >= 0L && blockSize > 0) { "Invalid DSF audio format" }
        require(bitsPerSample == 1 || bitsPerSample == 8) { "Unsupported DSF bit order" }

        val dataHeaderOffset = (28L + fmtSize).toInt()
        require(dataHeaderOffset + 12 <= bytes.size) { "Missing DSF data chunk" }
        require(ascii(bytes, dataHeaderOffset, 4) == "data") { "Missing DSF data chunk" }
        val dataChunkSize = leLong(bytes, dataHeaderOffset + 4)
        require(dataChunkSize >= 12L) { "Invalid DSF data size" }
        return DsdStreamInfo(
            container = DsdContainer.DSF,
            sampleRateHz = sampleRate,
            channelCount = channels,
            sampleCountPerChannel = sampleCount,
            dataOffset = dataHeaderOffset + 12L,
            dataLength = dataChunkSize - 12L,
            blockSizePerChannel = blockSize,
            lsbFirst = bitsPerSample == 1,
            dstCompressed = false
        )
    }

    private fun parseDff(bytes: ByteArray): DsdStreamInfo {
        require(ascii(bytes, 12, 4) == "DSD ") { "Invalid DSDIFF form type" }
        var offset = 16
        var sampleRate = 0
        var channels = 0
        var dataOffset = -1L
        var dataLength = 0L
        var dst = false
        while (offset + 12 <= bytes.size) {
            val id = ascii(bytes, offset, 4)
            val size = beLong(bytes, offset + 4)
            require(size >= 0L) { "Invalid DSDIFF chunk size" }
            val payload = offset + 12
            val end = payload.toLong() + size
            if ((id == "DSD " || id == "DST ") && payload <= bytes.size) {
                dataOffset = payload.toLong()
                dataLength = size
                dst = id == "DST "
                break
            }
            require(end <= bytes.size.toLong()) { "Truncated DSDIFF chunk $id" }
            when (id) {
                "PROP" -> {
                    require(size >= 4L && ascii(bytes, payload, 4) == "SND ") {
                        "Invalid DSDIFF sound properties"
                    }
                    var propertyOffset = payload + 4
                    while (propertyOffset + 12 <= end) {
                        val propertyId = ascii(bytes, propertyOffset, 4)
                        val propertySize = beLong(bytes, propertyOffset + 4)
                        val propertyPayload = propertyOffset + 12
                        require(propertyPayload.toLong() + propertySize <= end) {
                            "Truncated DSDIFF property $propertyId"
                        }
                        when (propertyId) {
                            "FS  " -> if (propertySize >= 4) sampleRate = beInt(bytes, propertyPayload)
                            "CHNL" -> if (propertySize >= 2) channels = beShort(bytes, propertyPayload)
                            "CMPR" -> if (propertySize >= 4) dst = ascii(bytes, propertyPayload, 4) == "DST "
                        }
                        propertyOffset = alignedEven(propertyPayload.toLong() + propertySize).toInt()
                    }
                }
                "DSD " -> {
                    dataOffset = payload.toLong()
                    dataLength = size
                }
                "DST " -> {
                    dst = true
                    dataOffset = payload.toLong()
                    dataLength = size
                }
            }
            offset = alignedEven(end).toInt()
        }
        require(sampleRate > 0 && channels in 1..2) { "Incomplete DSDIFF audio properties" }
        require(dataOffset >= 0L && dataLength > 0L) { "Missing DSDIFF audio data" }
        val sampleCount = if (!dst) dataLength * 8L / channels else 0L
        return DsdStreamInfo(
            container = DsdContainer.DFF,
            sampleRateHz = sampleRate,
            channelCount = channels,
            sampleCountPerChannel = sampleCount,
            dataOffset = dataOffset,
            dataLength = dataLength,
            blockSizePerChannel = 1,
            lsbFirst = false,
            dstCompressed = dst
        )
    }

    private fun alignedEven(value: Long): Long = (value + 1L) and -2L
    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String {
        require(offset >= 0 && offset + length <= bytes.size) { "Truncated DSD header" }
        return bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
    }
    private fun leInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    private fun leLong(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long
    private fun beShort(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff
    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    private fun beLong(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long
}
