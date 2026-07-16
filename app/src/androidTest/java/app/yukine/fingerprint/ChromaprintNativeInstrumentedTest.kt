package app.yukine.fingerprint

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.model.Track
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaprintNativeInstrumentedTest {
    @Test
    fun fingerprintsStreamingPcmOnDevice() {
        assertTrue(ChromaprintNative.isAvailable())
        assertFalse(ChromaprintNative.version().isBlank())

        val sampleRate = 44_100
        val seconds = 12
        val chunkSamples = 4_096
        val session = ChromaprintNative.create()
        assertNotNull(session)
        session!!.use {
            assertTrue(it.start(sampleRate, 1))
            var written = 0
            val totalSamples = sampleRate * seconds
            while (written < totalSamples) {
                val count = minOf(chunkSamples, totalSamples - written)
                val pcm = ByteBuffer.allocateDirect(count * Short.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                repeat(count) { offset ->
                    val time = (written + offset).toDouble() / sampleRate
                    val mixed = sin(2.0 * PI * 440.0 * time) * 0.55 +
                        sin(2.0 * PI * 660.0 * time) * 0.25
                    pcm.putShort((mixed * Short.MAX_VALUE).toInt().toShort())
                }
                pcm.flip()
                assertTrue(it.feed(pcm, count))
                written += count
            }
            val evidence = it.finishEvidence()
            assertNotNull(evidence)
            assertFalse(evidence!!.encoded.isBlank())
            assertTrue(evidence.rawWords.isNotEmpty())
        }
    }

    @Test
    fun decodesRealLocalTrackAndProducesVersionedEvidence() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wave = File(context.cacheDir, "chromaprint-segment-test.wav")
        writeWave(wave, seconds = 12)
        try {
            val track = Track(
                77L,
                "Generated wave",
                "Test",
                "",
                12_000L,
                Uri.fromFile(wave),
                wave.absolutePath,
                -1L,
                null
            )
            val evidence = ChromaprintSegmentAnalyzer(context).analyze(
                AudioFingerprintCandidate(88L, track, "test-signature")
            )

            assertEquals(ChromaprintSegmentAnalyzer.ALGORITHM_VERSION, evidence.algorithmVersion)
            val json = JSONObject(evidence.chromaprint)
            assertEquals(1, json.getInt("version"))
            assertEquals(1, json.getJSONArray("segments").length())
            val segment = json.getJSONArray("segments").getJSONObject(0)
            assertFalse(segment.getString("fingerprint").isBlank())
            assertTrue(segment.getString("rawHex").length >= 8)
            assertEquals(0, segment.getString("rawHex").length % 8)
            assertFalse(segment.getString("pcmSha256").isBlank())
        } finally {
            wave.delete()
        }
    }

    @Test
    fun decodesCachedWebDavHeadWithoutClaimingWholeTrackHash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cachedHead = File(context.cacheDir, "chromaprint-webdav-cached-head.wav")
        writeWave(cachedHead, seconds = 12)
        try {
            val track = Track(
                91L,
                "Cached WebDAV wave",
                "Test",
                "",
                240_000L,
                Uri.parse("https://example.invalid/music/test.wav#echoRevision=etag-1"),
                "webdav:/music/test.wav",
                -1L,
                null
            )
            val evidence = ChromaprintSegmentAnalyzer(context).analyzeCachedHead(
                AudioFingerprintCandidate(92L, track, "webdav-signature"),
                cachedHead
            )

            assertEquals(ChromaprintSegmentAnalyzer.ALGORITHM_VERSION, evidence.algorithmVersion)
            assertTrue(evidence.pcmHash.isBlank())
            assertTrue(evidence.analyzedDurationMs > 0L)
            val json = JSONObject(evidence.chromaprint)
            assertEquals("CACHED_HEAD", json.getString("kind"))
            assertEquals(1, json.getJSONArray("segments").length())
            val segment = json.getJSONArray("segments").getJSONObject(0)
            assertEquals(0L, segment.getLong("startMs"))
            assertFalse(segment.getString("fingerprint").isBlank())
            assertFalse(segment.getString("pcmSha256").isBlank())
        } finally {
            cachedHead.delete()
        }
    }

    private fun writeWave(file: File, seconds: Int) {
        val sampleRate = 44_100
        val sampleCount = sampleRate * seconds
        val dataBytes = sampleCount * Short.SIZE_BYTES
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            fun writeAscii(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
            fun writeLe16(value: Int) {
                output.write(value and 0xff)
                output.write(value ushr 8 and 0xff)
            }
            fun writeLe32(value: Int) {
                output.write(value and 0xff)
                output.write(value ushr 8 and 0xff)
                output.write(value ushr 16 and 0xff)
                output.write(value ushr 24 and 0xff)
            }
            writeAscii("RIFF")
            writeLe32(36 + dataBytes)
            writeAscii("WAVEfmt ")
            writeLe32(16)
            writeLe16(1)
            writeLe16(1)
            writeLe32(sampleRate)
            writeLe32(sampleRate * Short.SIZE_BYTES)
            writeLe16(Short.SIZE_BYTES)
            writeLe16(16)
            writeAscii("data")
            writeLe32(dataBytes)
            repeat(sampleCount) { sample ->
                val time = sample.toDouble() / sampleRate
                val mixed = sin(2.0 * PI * 440.0 * time) * 0.55 +
                    sin(2.0 * PI * 660.0 * time) * 0.25
                writeLe16((mixed * Short.MAX_VALUE).toInt())
            }
        }
    }
}
