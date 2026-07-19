package app.yukine.data

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
    }
}
