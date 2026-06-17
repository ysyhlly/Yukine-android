package app.yukine.ui

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.HashSet
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun rememberPlaybackWaveform(
    context: Context,
    trackId: Long,
    contentUriString: String?,
    dataPath: String,
    durationMs: Long,
    barCount: Int
): State<FloatArray?> {
    val key = PlaybackWaveformCache.key(trackId, contentUriString, dataPath, durationMs, barCount)
    return produceState<FloatArray?>(initialValue = PlaybackWaveformCache.get(key), key) {
        if (!PlaybackWaveformCache.canRead(dataPath, contentUriString, durationMs, barCount)) {
            value = null
            return@produceState
        }
        value = PlaybackWaveformCache.get(key)
        if (value != null) {
            return@produceState
        }
        if (!PlaybackWaveformCache.beginGeneration(key)) {
            repeat(20) {
                delay(120L)
                value = PlaybackWaveformCache.get(key)
                if (value != null) {
                    return@produceState
                }
            }
            return@produceState
        }
        try {
            val appContext = context.applicationContext
            val waveform = withContext(Dispatchers.IO) {
                PlaybackWaveformCache.extract(appContext, contentUriString, dataPath, durationMs, barCount)
            }
            if (waveform != null) {
                PlaybackWaveformCache.put(key, waveform)
            }
            value = waveform
        } finally {
            PlaybackWaveformCache.finishGeneration(key)
        }
    }
}

internal object PlaybackWaveformCache {
    private const val MAX_ENTRIES = 24
    private val inFlight = HashSet<String>()
    private val cache = object : LinkedHashMap<String, FloatArray>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: String): FloatArray? = cache[key]

    @Synchronized
    fun put(key: String, waveform: FloatArray) {
        cache[key] = waveform
    }

    @Synchronized
    fun beginGeneration(key: String): Boolean {
        if (cache.containsKey(key)) {
            return false
        }
        return inFlight.add(key)
    }

    @Synchronized
    fun finishGeneration(key: String) {
        inFlight.remove(key)
    }

    fun key(
        trackId: Long,
        contentUriString: String?,
        dataPath: String,
        durationMs: Long,
        barCount: Int
    ): String = "$trackId|${contentUriString.orEmpty()}|$dataPath|$durationMs|$barCount"

    fun canRead(dataPath: String, contentUriString: String?, durationMs: Long, barCount: Int): Boolean {
        if (durationMs <= 0L || barCount <= 0) {
            return false
        }
        if (
            dataPath.startsWith("stream:") ||
            dataPath.startsWith("streaming:") ||
            dataPath.startsWith("webdav:")
        ) {
            return false
        }
        if (isRemoteUri(contentUriString)) {
            return false
        }
        if (!contentUriString.isNullOrBlank()) {
            return true
        }
        if (dataPath.startsWith("document:")) {
            return dataPath.removePrefix("document:").isNotBlank()
        }
        return dataPath.isNotBlank()
    }

    fun extract(
        context: Context,
        contentUriString: String?,
        dataPath: String,
        durationMs: Long,
        barCount: Int
    ): FloatArray? {
        val source = sourceUri(contentUriString, dataPath) ?: return null
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            extractor.setDataSource(context, source, null)
            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) {
                return null
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            decode(extractor, decoder, format, durationMs, barCount)
        } catch (ignored: Exception) {
            null
        } finally {
            try {
                decoder?.stop()
            } catch (ignored: RuntimeException) {
            }
            try {
                decoder?.release()
            } catch (ignored: RuntimeException) {
            }
            try {
                extractor.release()
            } catch (ignored: RuntimeException) {
            }
        }
    }

    private fun decode(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        sourceFormat: MediaFormat,
        durationMs: Long,
        barCount: Int
    ): FloatArray? {
        val energy = FloatArray(barCount)
        val counts = IntArray(barCount)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var outputFormat = sourceFormat
        val durationUs = max(1L, durationMs * 1000L)
        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(8_000L)
                if (inputIndex >= 0) {
                    val input = decoder.getInputBuffer(inputIndex)
                    val sampleSize = if (input == null) -1 else extractor.readSampleData(input, 0)
                    if (sampleSize <= 0) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0L),
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = decoder.dequeueOutputBuffer(info, 8_000L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                else -> if (outputIndex >= 0) {
                    val output = decoder.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0) {
                        readPcm(output, info, outputFormat, durationUs, energy, counts)
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
        val peaks = FloatArray(barCount)
        var maxPeak = 0f
        var minPeak = Float.MAX_VALUE
        for (index in peaks.indices) {
            if (counts[index] <= 0) {
                continue
            }
            val rms = sqrt(energy[index] / counts[index])
            peaks[index] = rms
            maxPeak = max(maxPeak, rms)
            minPeak = minOf(minPeak, rms)
        }
        if (maxPeak <= 0f) {
            return null
        }
        val floor = if (minPeak == Float.MAX_VALUE) 0f else minPeak * 0.72f
        val span = (maxPeak - floor).coerceAtLeast(0.001f)
        for (index in peaks.indices) {
            peaks[index] = ((peaks[index] - floor) / span).coerceIn(0f, 1f)
        }
        return peaks
    }

    private fun readPcm(
        output: ByteBuffer,
        info: MediaCodec.BufferInfo,
        format: MediaFormat,
        durationUs: Long,
        energy: FloatArray,
        counts: IntArray
    ) {
        val sampleRate = intValue(format, MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
        val channels = intValue(format, MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val encoding = intValue(format, MediaFormat.KEY_PCM_ENCODING).let {
            if (it == 0) AudioFormat.ENCODING_PCM_16BIT else it
        }
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        val bytesPerFrame = bytesPerSample * channels
        if (bytesPerFrame <= 0) {
            return
        }
        val data = output.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        data.position(info.offset)
        data.limit(info.offset + info.size)
        val frameCount = info.size / bytesPerFrame
        for (frame in 0 until frameCount) {
            var peak = 0f
            for (channel in 0 until channels) {
                peak = max(peak, sampleAmplitude(data, encoding))
            }
            val timeUs = info.presentationTimeUs + (frame * 1_000_000L / sampleRate)
            val bucket = ((timeUs.coerceAtLeast(0L) * energy.size) / durationUs)
                .toInt()
                .coerceIn(0, energy.lastIndex)
            energy[bucket] += peak * peak
            counts[bucket] += 1
        }
    }

    private fun sampleAmplitude(buffer: ByteBuffer, encoding: Int): Float {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> {
                val value = (buffer.get().toInt() and 0xff) - 128
                kotlin.math.abs(value) / 128f
            }
            AudioFormat.ENCODING_PCM_FLOAT -> kotlin.math.abs(buffer.float)
            else -> kotlin.math.abs(buffer.short.toInt()) / 32768f
        }.coerceIn(0f, 1f)
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return index
            }
        }
        return -1
    }

    private fun intValue(format: MediaFormat, key: String): Int {
        if (!format.containsKey(key)) {
            return 0
        }
        return try {
            format.getInteger(key)
        } catch (ignored: RuntimeException) {
            0
        }
    }

    private fun sourceUri(contentUriString: String?, dataPath: String): Uri? {
        if (!contentUriString.isNullOrBlank()) {
            val uri = Uri.parse(contentUriString)
            if (uri != Uri.EMPTY) {
                return uri
            }
        }
        if (dataPath.startsWith("document:")) {
            return Uri.parse(dataPath.removePrefix("document:"))
        }
        if (dataPath.isBlank()) {
            return null
        }
        return Uri.fromFile(File(dataPath))
    }

    private fun isRemoteUri(value: String?): Boolean {
        if (value.isNullOrBlank()) {
            return false
        }
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
    }
}
