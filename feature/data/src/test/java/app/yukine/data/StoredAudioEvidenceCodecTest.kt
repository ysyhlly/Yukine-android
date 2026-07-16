package app.yukine.data

import app.yukine.data.room.AudioFeatureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StoredAudioEvidenceCodecTest {
    @Test
    fun decodesVersionedRawChromaprintSegments() {
        val decoded = StoredAudioEvidenceCodec.decode(
            feature(
                pcmHash = "pcm-hash",
                chromaprint = """{"version":1,"segments":[{"startMs":1200,"durationMs":10000,"rawHex":"00000001ffffffff80000000"}]}"""
            )
        )

        assertEquals("pcm-hash", decoded.pcmHash)
        assertEquals(1, decoded.segments.size)
        assertEquals(1_200L, decoded.segments.single().startMs)
        assertTrue(decoded.segments.single().words.contentEquals(intArrayOf(1, -1, Int.MIN_VALUE)))
    }

    @Test
    fun malformedOrLegacyPayloadKeepsOnlySafePcmEvidence() {
        val malformed = StoredAudioEvidenceCodec.decode(feature("pcm", "not-json"))
        val legacy = StoredAudioEvidenceCodec.decode(
            feature("", """{"segments":[{"startMs":0,"fingerprint":"encoded-only"}]}""")
        )

        assertEquals("pcm", malformed.pcmHash)
        assertTrue(malformed.segments.isEmpty())
        assertTrue(legacy.segments.isEmpty())
    }

    private fun feature(pcmHash: String, chromaprint: String) = AudioFeatureEntity(
        sourceId = 1L,
        contentSignature = "signature",
        pcmHash = pcmHash,
        chromaprint = chromaprint,
        recordingEmbedding = null,
        workEmbedding = null,
        versionScores = "",
        algorithmVersion = 1,
        audioSpecState = "PENDING",
        audioSpecAlgorithmVersion = 0,
        audioSpecAttemptCount = 0,
        lastAttemptAt = 0L,
        lastError = "",
        updatedAt = 0L
    )
}
