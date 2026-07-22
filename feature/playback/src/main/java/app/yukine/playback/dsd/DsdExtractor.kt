package app.yukine.playback.dsd

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val MIME_AUDIO_DSD = "audio/x-dsd"

@UnstableApi
internal class DsdExtractorsFactory : ExtractorsFactory {
    private val defaults = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> =
        arrayOf(DsdExtractor(), *defaults.createExtractors())
}

/** Emits raw DSD access units with an exact, container-derived seek map. */
@UnstableApi
internal class DsdExtractor : Extractor {
    private var output: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null
    private var info: DsdStreamInfo? = null
    private var initialized = false
    private var bytesReadFromAudio = 0L

    override fun sniff(input: ExtractorInput): Boolean {
        val signature = ByteArray(4)
        return try {
            input.peekFully(signature, 0, signature.size)
            signature.contentEquals("DSD ".toByteArray(Charsets.US_ASCII)) ||
                signature.contentEquals("FRM8".toByteArray(Charsets.US_ASCII))
        } finally {
            input.resetPeekPosition()
        }
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (!initialized) initializeStream(input)
        val streamInfo = info ?: return Extractor.RESULT_END_OF_INPUT
        val remaining = streamInfo.dataLength - bytesReadFromAudio
        if (remaining <= 0L) return Extractor.RESULT_END_OF_INPUT
        val accessUnitBytes = if (streamInfo.container == DsdContainer.DSF) {
            streamInfo.blockSizePerChannel * streamInfo.channelCount
        } else {
            SAMPLE_CHUNK_BYTES - (SAMPLE_CHUNK_BYTES % streamInfo.channelCount)
        }
        val requested = minOf(accessUnitBytes.toLong(), remaining).toInt()
        val written = trackOutput?.sampleData(input, requested, true) ?: -1
        if (written <= 0) return Extractor.RESULT_END_OF_INPUT
        val sampleTimeUs = bytesReadFromAudio * 8L * 1_000_000L /
            (streamInfo.channelCount * streamInfo.sampleRateHz)
        trackOutput?.sampleMetadata(
            sampleTimeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            written,
            0,
            null
        )
        bytesReadFromAudio += written
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        val streamInfo = info
        bytesReadFromAudio = if (streamInfo == null) 0L else {
            (position - streamInfo.dataOffset).coerceIn(0L, streamInfo.dataLength)
        }
    }

    override fun release() = Unit

    private fun initializeStream(input: ExtractorInput) {
        val headerLength = when {
            input.length in 1..MAX_HEADER_BYTES.toLong() -> input.length.toInt()
            else -> MAX_HEADER_BYTES
        }
        val header = ByteArray(headerLength)
        var count = 0
        while (count < header.size) {
            val read = input.peek(header, count, header.size - count)
            if (read <= 0) break
            count += read
        }
        input.resetPeekPosition()
        val parsed = try {
            DsdContainerParser.parse(header.copyOf(count))
        } catch (failure: IllegalArgumentException) {
            throw IOException(failure.message, failure)
        }
        if (parsed.dstCompressed) throw IOException("DST-compressed DSDIFF is not supported")
        if (parsed.dataOffset > Int.MAX_VALUE) throw IOException("DSD header is too large")
        input.skipFully(parsed.dataOffset.toInt())
        info = parsed
        bytesReadFromAudio = 0L
        trackOutput?.format(
            Format.Builder()
                .setSampleMimeType(MIME_AUDIO_DSD)
                .setSampleRate(parsed.sampleRateHz)
                .setChannelCount(parsed.channelCount)
                .setAverageBitrate(parsed.sampleRateHz * parsed.channelCount)
                .setInitializationData(listOf(DsdFormatMetadata.encode(parsed)))
                .build()
        )
        output?.seekMap(DsdSeekMap(parsed))
        initialized = true
    }

    private class DsdSeekMap(private val info: DsdStreamInfo) : SeekMap {
        override fun isSeekable(): Boolean = true
        override fun getDurationUs(): Long = info.durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val clampedTime = timeUs.coerceIn(0L, info.durationUs)
            return SeekMap.SeekPoints(SeekPoint(clampedTime, info.seekByteOffset(clampedTime)))
        }
    }

    private companion object {
        const val MAX_HEADER_BYTES = 256 * 1024
        const val SAMPLE_CHUNK_BYTES = 32 * 1024
    }
}

internal data class DsdFormatMetadata(
    val container: DsdContainer,
    val blockSizePerChannel: Int,
    val lsbFirst: Boolean,
    val dstCompressed: Boolean
) {
    companion object {
        fun encode(info: DsdStreamInfo): ByteArray = ByteBuffer.allocate(12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(if (info.container == DsdContainer.DSF) 1 else 2)
            .putInt(info.blockSizePerChannel)
            .put(if (info.lsbFirst) 1 else 0)
            .put(if (info.dstCompressed) 1 else 0)
            .putShort(0)
            .array()

        fun decode(bytes: ByteArray): DsdFormatMetadata {
            require(bytes.size >= 12) { "Missing DSD format metadata" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val container = when (buffer.int) {
                1 -> DsdContainer.DSF
                2 -> DsdContainer.DFF
                else -> error("Unknown DSD container metadata")
            }
            val blockSize = buffer.int
            val lsbFirst = buffer.get().toInt() != 0
            val dst = buffer.get().toInt() != 0
            return DsdFormatMetadata(container, blockSize, lsbFirst, dst)
        }
    }
}
