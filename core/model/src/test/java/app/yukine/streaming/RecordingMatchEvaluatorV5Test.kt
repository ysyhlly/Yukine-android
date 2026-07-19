package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMatchEvaluatorV5Test {
    @Test
    fun differentIsrcIsSoftNegativeAndCannotAutoMergeOnMetadataAlone() {
        val result = evaluate(
            reference(isrcs = setOf("USABC1234567"), workId = "work-1"),
            reference(isrcs = setOf("JPXYZ7654321"), workId = "work-1")
        )

        assertTrue(RecordingMatchSoftConflict.ISRC in result.softConflicts)
        assertFalse(RecordingMatchHardConflict.ISRC in result.hardConflicts)
        assertTrue(result.sameRecordingProbability < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
    }

    @Test
    fun intersectingIsrcSetsRemainStrongEvidence() {
        val result = evaluate(
            reference(isrcs = setOf("USABC1234567", "JPXYZ7654321")),
            reference(isrcs = setOf("JP-XYZ-76-54321"))
        )

        assertTrue("isrc" in result.identifierEvidence)
        assertFalse(result.hasHardConflict)
        assertTrue(result.sameRecordingProbability >= 0.98)
    }

    @Test
    fun workAuthorsStrengthenWorkButNeverOverrideRecordingArtistConflict() {
        val credits = listOf(
            WorkCreditFeature(canonicalId = "composer-1", canonicalName = "Composer", role = WorkCreditRole.COMPOSER),
            WorkCreditFeature(canonicalName = "Writer", role = WorkCreditRole.SONGWRITER)
        )
        val result = evaluate(
            reference(workCredits = credits),
            reference(artist = "Cover Artist", workCredits = credits)
        )

        assertEquals(1.0, result.authorScore ?: 0.0, 0.0001)
        assertTrue(result.sameWorkProbability >= 0.85)
        assertTrue(result.sameRecordingProbability < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
        assertEquals(RecordingRelationship.SAME_WORK_DIFFERENT_VERSION, result.relationship)
    }

    @Test
    fun scoringPolicyDefaultsInvalidValuesToShadowAndPreservesV4Decision() {
        assertEquals(IdentityScoringMode.V5_SHADOW, IdentityScoringMode.fromStoredValue("invalid"))
        val left = RecordingMatchFeatureExtractor.extract(reference(isrcs = setOf("USABC1234567")))
        val right = RecordingMatchFeatureExtractor.extract(reference(isrcs = setOf("JPXYZ7654321")))
        val decision = RecordingMatchEvaluationPolicy.evaluate(left, right, IdentityScoringMode.V5_SHADOW)

        assertEquals(RecordingMatchEvaluatorV2.SCORE_VERSION, decision.active.scoreVersion)
        assertEquals(RecordingMatchEvaluatorV2.V5_SCORE_VERSION, decision.shadow?.scoreVersion)
        assertTrue(RecordingMatchHardConflict.ISRC in decision.active.hardConflicts)
        assertTrue(RecordingMatchSoftConflict.ISRC in decision.shadow!!.softConflicts)
    }

    @Test
    fun differentWorkIdentifierTypesAreNotComparedAsAConflict() {
        val result = evaluate(
            reference(workIdentifiers = setOf("MUSICBRAINZ_WORK_ID\u001F\u001Fwork-1")),
            reference(workIdentifiers = setOf("ISWC\u001Fiswc\u001FT-123.456.789-0"))
        )

        assertTrue(RecordingMatchHardConflict.WORK_MBID !in result.hardConflicts)
    }

    private fun evaluate(
        left: StreamingTrackMatchPolicy.Reference,
        right: StreamingTrackMatchPolicy.Reference
    ): MatchEvaluation = RecordingMatchEvaluatorV2.evaluateV5(left, right)

    private fun reference(
        title: String = "Echo",
        artist: String = "Artist",
        durationMs: Long = 180_000L,
        isrcs: Set<String> = emptySet(),
        workId: String = "",
        workCredits: List<WorkCreditFeature> = emptyList(),
        workIdentifiers: Set<String> = emptySet()
    ) = StreamingTrackMatchPolicy.Reference(
        title = title,
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        isrcs = isrcs,
        canonicalWorkId = workId,
        canonicalWorkConfirmed = workId.isNotBlank(),
        workCredits = workCredits,
        workIdentifiers = workIdentifiers
    )
}
