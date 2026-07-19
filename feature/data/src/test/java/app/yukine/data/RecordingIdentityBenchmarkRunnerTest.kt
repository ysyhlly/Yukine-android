package app.yukine.data

import app.yukine.streaming.RecordingMatchEvaluatorV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordingIdentityBenchmarkRunnerTest {
    @Test
    fun parsesVersionedJsonlAndRunsBaselineAndEnhanced() {
        val jsonl = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("recording-identity-v3-gold.jsonl")
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }

        val comparison = RecordingIdentityBenchmarkRunner().run(jsonl)

        assertEquals(1, comparison.schemaVersion)
        assertEquals(3, comparison.pairCount)
        assertTrue(comparison.baselineV6.recallAt20 in 0.0..1.0)
        assertTrue(comparison.enhancedV7.recallAt20 in 0.0..1.0)
        assertTrue(comparison.baselineV6.candidateCount >= 0)
        assertTrue(comparison.enhancedV7.candidateCount >= 0)
        assertEquals(
            comparison.baselineV6.candidateCount,
            comparison.baselineV6.evaluationCount
        )
        assertEquals(
            comparison.enhancedV7.candidateCount,
            comparison.enhancedV7.evaluationCount
        )
        assertEquals(RecordingMatchEvaluatorV2.SCORE_VERSION, comparison.v4Scoring.scoreVersion)
        assertEquals(RecordingMatchEvaluatorV2.V5_SCORE_VERSION, comparison.v5Scoring.scoreVersion)
        assertEquals(3, comparison.v4Scoring.overall.pairCount)
        assertEquals(3, comparison.v5Scoring.overall.pairCount)
        assertTrue(comparison.v4Scoring.overall.autoMergePrecision in 0.0..1.0)
        assertTrue(comparison.v5Scoring.overall.autoMergePrecision in 0.0..1.0)
        assertEquals(3, comparison.v5Scoring.byCategory.getValue("UNCATEGORIZED").pairCount)
    }
}
