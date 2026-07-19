package app.yukine.fingerprint

import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.RecordingMatchHardConflict
import app.yukine.streaming.StreamingTrackMatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaprintSegmentAlignmentTest {
    @Test
    fun identicalSegmentsRecoverStableOffset() {
        val source = evidence(segment(0L, 1), segment(30_000L, 2), segment(60_000L, 3))
        val candidate = evidence(segment(1_500L, 1), segment(31_500L, 2), segment(61_500L, 3))

        val result = ChromaprintSegmentAligner.align(source, candidate)

        assertTrue(result.stable)
        assertFalse(result.structuralJump)
        assertTrue(result.similarity > 0.99)
        assertEquals(1_500L, result.offsetMs)
        assertEquals(1.0, result.speedRatio, 0.001)
    }

    @Test
    fun sparseCodecBitNoiseStillAligns() {
        val clean = words(4)
        val noisy = clean.copyOf().also { values ->
            values.indices.filter { it % 7 == 0 }.forEach { index ->
                values[index] = values[index] xor 0b10101
            }
        }
        val source = evidence(
            ChromaprintSegment(0L, 10_000L, clean),
            ChromaprintSegment(30_000L, 10_000L, words(5))
        )
        val candidate = evidence(
            ChromaprintSegment(600L, 10_000L, noisy),
            ChromaprintSegment(30_600L, 10_000L, words(5))
        )

        val result = ChromaprintSegmentAligner.align(source, candidate)

        assertTrue(result.stable)
        assertTrue(result.similarity > 0.98)
    }

    @Test
    fun threeSegmentsFitSmallSpeedDifference() {
        val source = evidence(segment(0L, 6), segment(30_000L, 7), segment(60_000L, 8))
        val candidate = evidence(segment(900L, 6), segment(31_500L, 7), segment(62_100L, 8))

        val result = ChromaprintSegmentAligner.align(source, candidate)

        assertTrue(result.stable)
        assertFalse(result.structuralJump)
        assertEquals(1.02, result.speedRatio, 0.002)
        assertEquals(900L, result.offsetMs)
    }

    @Test
    fun reorderedSegmentsAreStructuralJump() {
        val source = evidence(segment(0L, 9), segment(30_000L, 10), segment(60_000L, 11))
        val candidate = evidence(segment(60_000L, 9), segment(30_000L, 10), segment(0L, 11))

        val result = ChromaprintSegmentAligner.align(source, candidate)

        assertTrue(result.structuralJump)
        assertFalse(result.stable)
    }

    @Test
    fun partialSingleSegmentCannotReachAutomaticMergeThreshold() {
        val metadata = evaluate("Song", "Artist", "Song", "Artist")
            .copy(sameRecordingProbability = 0.88)
        val alignment = ChromaprintSegmentAligner.align(
            evidence(segment(0L, 12)),
            evidence(segment(400L, 12))
        )

        val refined = AudioMatchRefiner.refine(metadata, alignment)

        assertTrue(refined.sameRecordingProbability <= 0.90)
        assertTrue(refined.sameRecordingProbability < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
    }

    @Test
    fun exactPcmCannotOverrideVersionConflict() {
        val metadata = evaluate("Song", "Artist", "Song (Live)", "Artist")
        assertTrue(RecordingMatchHardConflict.VERSION in metadata.hardConflicts)
        val alignment = ChromaprintSegmentAligner.align(
            TraditionalAudioEvidence(pcmHash = "same"),
            TraditionalAudioEvidence(pcmHash = "same")
        )

        val refined = AudioMatchRefiner.refine(metadata, alignment)

        assertEquals(metadata, refined)
    }

    @Test
    fun exactPcmRaisesCompatibleMetadataToCertainRecording() {
        val metadata = evaluate("Song", "Artist", "Song", "Artist")
            .copy(sameRecordingProbability = 0.70)
        val alignment = ChromaprintSegmentAligner.align(
            TraditionalAudioEvidence(pcmHash = "same"),
            TraditionalAudioEvidence(pcmHash = "same")
        )

        val refined = AudioMatchRefiner.refine(metadata, alignment)

        assertEquals(1.0, refined.sameRecordingProbability, 0.0)
        assertEquals(AudioMatchRefiner.SCORE_VERSION, refined.scoreVersion)
        assertTrue("pcm_hash" in refined.identifierEvidence)
    }

    @Test
    fun v5DifferentIsrcNeedsStrongMultiSegmentAudioToAutoMerge() {
        val metadata = RecordingMatchEvaluatorV2.evaluateV5(
            StreamingTrackMatchPolicy.Reference(
                title = "Song",
                artist = "Artist",
                durationMs = 200_000L,
                isrcs = setOf("USAAA2600001")
            ),
            StreamingTrackMatchPolicy.Reference(
                title = "Song",
                artist = "Artist",
                durationMs = 200_000L,
                isrcs = setOf("USBBB2600002")
            )
        )
        assertTrue(metadata.sameRecordingProbability < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
        val alignment = ChromaprintSegmentAligner.align(
            evidence(segment(0L, 20), segment(30_000L, 21)),
            evidence(segment(500L, 20), segment(30_500L, 21))
        )

        val refined = AudioMatchRefiner.refine(metadata, alignment)

        assertEquals(app.yukine.streaming.RecordingRelationship.SAME_RECORDING, refined.relationship)
        assertTrue(refined.sameRecordingProbability >= RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
        assertEquals(RecordingMatchEvaluatorV2.V5_SCORE_VERSION, refined.scoreVersion)
    }

    private fun evaluate(
        leftTitle: String,
        leftArtist: String,
        rightTitle: String,
        rightArtist: String
    ) = RecordingMatchEvaluatorV2.evaluate(
        StreamingTrackMatchPolicy.Reference(
            title = leftTitle,
            artist = leftArtist,
            durationMs = 200_000L
        ),
        StreamingTrackMatchPolicy.Reference(
            title = rightTitle,
            artist = rightArtist,
            durationMs = 200_000L
        )
    )

    private fun evidence(vararg segments: ChromaprintSegment) =
        TraditionalAudioEvidence(segments = segments.toList())

    private fun segment(startMs: Long, seed: Int) =
        ChromaprintSegment(startMs, 10_000L, words(seed))

    private fun words(seed: Int): IntArray = IntArray(64) { index ->
        var value = seed * 0x45d9f3b + index * 0x119de1f3
        value = value xor (value ushr 16)
        value * 0x45d9f3b
    }
}
