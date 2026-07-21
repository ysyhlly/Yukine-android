package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMatchScoringConfigTest {
    @Test
    fun v4PresetHasCorrectWeightsAndThresholds() {
        val config = RecordingMatchScoringConfig.V4

        assertEquals(RecordingMatchEvaluatorV2.SCORE_VERSION, config.scoreVersion)
        assertEquals(0.50, config.workWeight, 0.0001)
        assertEquals(0.30, config.durationWeight, 0.0001)
        assertEquals(0.20, config.versionWeight, 0.0001)
        assertFalse(config.softIsrcConflict)
        assertEquals(0.0, config.artistTrigramCap, 0.0001)
        assertEquals(1.0, config.artistTrigramHardConflictThreshold, 0.0001)
        assertFalse(config.useV5TitleWeighting)
        assertFalse(config.useAuthorScoreWeighting)
    }

    @Test
    fun v5PresetHasCorrectWeightsAndThresholds() {
        val config = RecordingMatchScoringConfig.V5

        assertEquals(RecordingMatchEvaluatorV2.V5_SCORE_VERSION, config.scoreVersion)
        assertEquals(0.60, config.workWeight, 0.0001)
        assertEquals(0.15, config.durationWeight, 0.0001)
        assertEquals(0.25, config.versionWeight, 0.0001)
        assertTrue(config.softIsrcConflict)
        assertEquals(0.85, config.artistTrigramCap, 0.0001)
        assertEquals(0.60, config.artistTrigramHardConflictThreshold, 0.0001)
        assertTrue(config.useV5TitleWeighting)
        assertTrue(config.useAuthorScoreWeighting)
    }

    @Test
    fun weightsSumToOneForBothPresets() {
        assertEquals(1.0, RecordingMatchScoringConfig.V4.workWeight +
            RecordingMatchScoringConfig.V4.durationWeight +
            RecordingMatchScoringConfig.V4.versionWeight, 0.0001)
        assertEquals(1.0, RecordingMatchScoringConfig.V5.workWeight +
            RecordingMatchScoringConfig.V5.durationWeight +
            RecordingMatchScoringConfig.V5.versionWeight, 0.0001)
    }

    @Test
    fun thresholdsAreInValidRange() {
        listOf(RecordingMatchScoringConfig.V4, RecordingMatchScoringConfig.V5).forEach { config ->
            assertTrue("artistTrigramCap in [0,1]", config.artistTrigramCap in 0.0..1.0)
            assertTrue("artistTrigramHardConflictThreshold in [0,1]",
                config.artistTrigramHardConflictThreshold in 0.0..1.0)
            assertTrue("softIsrcPenalty in [0,1]", config.softIsrcPenalty in 0.0..1.0)
            assertTrue("softIsrcCeiling in [0,1]", config.softIsrcCeiling in 0.0..1.0)
            assertTrue("durationMissingScore in [0,1]", config.durationMissingScore in 0.0..1.0)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun weightsNotSummingToOneAreRejected() {
        RecordingMatchScoringConfig(
            scoreVersion = 99,
            workWeight = 0.50,
            durationWeight = 0.30,
            versionWeight = 0.30, // Sum = 1.10
            softIsrcConflict = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidArtistTrigramCapIsRejected() {
        RecordingMatchScoringConfig(
            scoreVersion = 99,
            workWeight = 0.50,
            durationWeight = 0.30,
            versionWeight = 0.20,
            softIsrcConflict = false,
            artistTrigramCap = 1.5 // Invalid: > 1.0
        )
    }

    @Test
    fun v4AndV5ProduceDifferentScoreVersions() {
        assertTrue(RecordingMatchScoringConfig.V4.scoreVersion != RecordingMatchScoringConfig.V5.scoreVersion)
    }

    @Test
    fun v5SoftIsrcCeilingIsBelowAutoMergeThreshold() {
        // V5's soft ISRC ceiling must be below the auto-merge threshold to prevent
        // ISRC-conflicting pairs from auto-merging on metadata alone.
        assertTrue(
            RecordingMatchScoringConfig.V5.softIsrcCeiling < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE
        )
    }

    @Test
    fun v5ArtistTrigramCapIsBelowAutoMergeThreshold() {
        // V5's artist trigram cap must be below the auto-merge threshold to prevent
        // trigram similarity alone from triggering auto-merge.
        assertTrue(
            RecordingMatchScoringConfig.V5.artistTrigramCap < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE
        )
    }
}
