package app.yukine.fingerprint

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import app.yukine.fingerprint.AudioFingerprintCandidate
import app.yukine.fingerprint.AudioFingerprintEvidence
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Decodes bounded local segments and streams PCM directly into Chromaprint. */
internal class ChromaprintSegmentAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    fun analyze(candidate: AudioFingerprintCandidate): AudioFingerprintEvidence {
        val durationMs = candidate.track.durationMs.coerceAtLeast(0L)
        return analyzeSegments(
            candidate,
            candidate.track.contentUri,
            segmentStarts(durationMs),
            if (durationMs <= SINGLE_SEGMENT_MAX_MS) "FULL_OR_HEAD" else "HEAD_MIDDLE_TAIL",
            allowWholeTrackPcmHash = true
        )
    }

    /**
     * Decodes only the cached leading media bytes copied by the playback service. The original
     * optimistic-lock signature remains on [candidate], while [cachedFile] is a disposable,
     * network-free decoder input. A partial prefix can never claim a whole-track PCM hash.
     */
    fun analyzeCachedHead(
        candidate: AudioFingerprintCandidate,
        cachedFile: File
    ): AudioFingerprintEvidence {
        check(cachedFile.isFile && cachedFile.length() > 0L) { "EMPTY_CACHED_PREFIX" }
        return analyzeSegments(
            candidate,
            Uri.fromFile(cachedFile),
            listOf(0L),
            "CACHED_HEAD",
            allowWholeTrackPcmHash = false
        )
    }

    private fun analyzeSegments(
        candidate: AudioFingerprintCandidate,
        mediaUri: Uri,
        starts: List<Long>,
        kind: String,
        allowWholeTrackPcmHash: Boolean
    ): AudioFingerprintEvidence {
        check(ChromaprintNative.isAvailable()) { "NATIVE_UNAVAILABLE" }
        val durationMs = candidate.track.durationMs.coerceAtLeast(0L)
        val segments = starts.map { startMs ->
            decodeSegment(mediaUri, startMs, segmentDuration(durationMs, startMs))
        }
        check(segments.isNotEmpty() && segments.all { it.fingerprint.isNotBlank() }) {
            "EMPTY_FINGERPRINT"
        }
        val encoded = JSONObject()
            .put("version", ALGORITHM_VERSION)
            .put("kind", kind)
            .put("segments", JSONArray().apply {
                segments.forEach { segment ->
                    put(JSONObject()
                        .put("startMs", segment.startMs)
                        .put("durationMs", segment.decodedDurationMs)
                        .put("fingerprint", segment.fingerprint)
                        .put("rawHex", segment.rawWords.joinToString("") { word ->
                            "%08x".format(word.toLong() and 0xffffffffL)
                        })
                        .put("pcmSha256", segment.pcmSha256))
                }
            })
            .toString()
        val coversWholeTrack = allowWholeTrackPcmHash && segments.size == 1 && durationMs > 0L &&
            segments.single().startMs == 0L &&
            segments.single().decodedDurationMs + FULL_COVERAGE_TOLERANCE_MS >= durationMs
        return AudioFingerprintEvidence(
            pcmHash = if (coversWholeTrack) segments.single().pcmSha256 else "",
            chromaprint = encoded,
            algorithmVersion = ALGORITHM_VERSION,
            analyzedDurationMs = segments.sumOf { it.decodedDurationMs }
        )
    }

    private fun decodeSegment(
        mediaUri: Uri,
        startMs: Long,
        durationMs: Long
    ): SegmentResult {
        check(durationMs > 0L) { "INVALID_DURATION" }
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val session = checkNotNull(ChromaprintNative.create()) { "NATIVE_CREATE_FAILED" }
        try {
            extractor.setDataSource(appContext, mediaUri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("NO_AUDIO_TRACK")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME)) { "NO_AUDIO_MIME" }
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val digest = MessageDigest.getInstance("SHA-256")
            val info = MediaCodec.BufferInfo()
            val endUs = (startMs + durationMs) * 1_000L
            var inputDone = false
            var outputDone = false
            var sessionStarted = false
            var outputFormat = format
            var firstPcmUs = -1L
            var decodedFrames = 0L
            var decodedSampleRate = 0
            var idlePolls = 0
            while (!outputDone && idlePolls < MAX_IDLE_POLLS) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                var progressed = false
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(POLL_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        progressed = true
                        val input = decoder.getInputBuffer(inputIndex)
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        val timeUs = extractor.sampleTime
                        if (size <= 0 || timeUs < 0L || timeUs > endUs) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, endUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = decoder.dequeueOutputBuffer(info, POLL_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = decoder.outputFormat
                        progressed = true
                    }
                    outputIndex >= 0 -> {
                        progressed = true
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0 && info.presentationTimeUs >= startMs * 1_000L) {
                            val encoding = outputFormat.intOr(
                                MediaFormat.KEY_PCM_ENCODING,
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            check(encoding == AudioFormat.ENCODING_PCM_16BIT) { "UNSUPPORTED_PCM_$encoding" }
                            val sampleRate = outputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 0)
                            val channels = outputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 0)
                            check(sampleRate > 0 && channels > 0) { "INVALID_PCM_FORMAT" }
                            if (!sessionStarted) {
                                check(session.start(sampleRate, channels)) { "NATIVE_START_FAILED" }
                                sessionStarted = true
                            }
                            val pcm = output.duplicate().apply {
                                position(info.offset)
                                limit(info.offset + info.size)
                            }.slice().order(ByteOrder.nativeOrder())
                            val bytes = ByteArray(pcm.remaining())
                            pcm.duplicate().get(bytes)
                            digest.update(bytes)
                            check(session.feed(pcm, pcm.remaining() / Short.SIZE_BYTES)) {
                                "NATIVE_FEED_FAILED"
                            }
                            decodedFrames += (pcm.remaining() / Short.SIZE_BYTES / channels).toLong()
                            decodedSampleRate = sampleRate
                            if (firstPcmUs < 0L) firstPcmUs = info.presentationTimeUs
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 ||
                            info.presentationTimeUs >= endUs
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
                idlePolls = if (progressed) 0 else idlePolls + 1
            }
            check(sessionStarted && firstPcmUs >= 0L) { "NO_PCM" }
            val fingerprint = checkNotNull(session.finishEvidence()) { "NATIVE_FINISH_FAILED" }
            check(fingerprint.rawWords.isNotEmpty()) { "EMPTY_RAW_FINGERPRINT" }
            val decodedDurationMs = if (decodedSampleRate <= 0) 0L
            else decodedFrames * 1_000L / decodedSampleRate
            return SegmentResult(
                startMs,
                decodedDurationMs,
                fingerprint.encoded,
                fingerprint.rawWords,
                digest.digest().joinToString("") { "%02x".format(it) }
            )
        } finally {
            session.close()
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun segmentStarts(durationMs: Long): List<Long> {
        if (durationMs <= SINGLE_SEGMENT_MAX_MS) return listOf(0L)
        val middle = (durationMs / 2L - SEGMENT_DURATION_MS / 2L).coerceAtLeast(0L)
        val tail = (durationMs - SEGMENT_DURATION_MS).coerceAtLeast(0L)
        return listOf(0L, middle, tail).distinct()
    }

    private fun segmentDuration(durationMs: Long, startMs: Long): Long =
        if (durationMs <= 0L) SEGMENT_DURATION_MS
        else (durationMs - startMs).coerceIn(1L, SEGMENT_DURATION_MS)

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private data class SegmentResult(
        val startMs: Long,
        val decodedDurationMs: Long,
        val fingerprint: String,
        val rawWords: IntArray,
        val pcmSha256: String
    )

    companion object {
        const val ALGORITHM_VERSION = 1
        private const val SEGMENT_DURATION_MS = 10_000L
        private const val SINGLE_SEGMENT_MAX_MS = 30_000L
        private const val FULL_COVERAGE_TOLERANCE_MS = 1_000L
        private const val POLL_TIMEOUT_US = 8_000L
        private const val MAX_IDLE_POLLS = 500
    }
}
