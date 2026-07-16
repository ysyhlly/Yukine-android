package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceSelectionEvaluatorTest {
    private val now = 1_700_000_000_000L

    @Test
    fun rejectsUnconfirmedUnplayableAndUnverifiedRemoteSources() {
        assertFalse(evaluate(source(playable = false)).eligible)
        assertFalse(evaluate(source(confirmed = false)).eligible)
        assertFalse(evaluate(source(provider = "netease")).eligible)
        assertTrue(evaluate(source(provider = "webdav")).eligible)
    }

    @Test
    fun localWinsEquivalentPhysicalSourcesWithoutMixingIdentityConfidence() {
        val ranked = PlaybackSourceSelectionEvaluator.rank(
            listOf(
                source(sourceId = 2L, provider = "webdav", qualityScore = 900),
                source(sourceId = 1L, provider = "local", qualityScore = 900)
            ),
            now
        )

        assertEquals(1L, ranked.first().source.sourceId)
        assertTrue(ranked.first().sourceSelectionScore > ranked.last().sourceSelectionScore)
    }

    @Test
    fun recentHighQualityVerifiedSourceCanBeatStaleLowQualityLocalSource() {
        val ranked = PlaybackSourceSelectionEvaluator.rank(
            listOf(
                source(sourceId = 1L, provider = "local", qualityScore = 200),
                source(
                    sourceId = 2L,
                    provider = "webdav",
                    qualityScore = 1_000,
                    lastSuccessfulAt = now - 1_000L,
                    lastVerifiedAt = now - 1_000L
                )
            ),
            now
        )

        assertEquals(2L, ranked.first().source.sourceId)
    }

    @Test
    fun repeatedFailuresLowerSourceSelectionScoreOnly() {
        val healthy = evaluate(source(failureCount = 0))
        val failing = evaluate(source(failureCount = 5))

        assertTrue(healthy.sourceSelectionScore > failing.sourceSelectionScore)
        assertTrue("failure_penalty" in failing.reasons)
    }

    private fun evaluate(source: PlaybackSourceSelectionFeatures) =
        PlaybackSourceSelectionEvaluator.evaluate(source, now)

    private fun source(
        sourceId: Long = 1L,
        provider: String = "local",
        playable: Boolean = true,
        confirmed: Boolean = true,
        qualityScore: Int = 900,
        lastSuccessfulAt: Long = 0L,
        lastVerifiedAt: Long = 0L,
        failureCount: Int = 0
    ) = PlaybackSourceSelectionFeatures(
        sourceId = sourceId,
        provider = provider,
        playable = playable,
        confirmed = confirmed,
        qualityScore = qualityScore,
        lastSuccessfulAt = lastSuccessfulAt,
        lastVerifiedAt = lastVerifiedAt,
        failureCount = failureCount
    )
}
