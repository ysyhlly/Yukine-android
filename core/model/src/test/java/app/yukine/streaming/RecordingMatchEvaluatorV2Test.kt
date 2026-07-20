package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMatchEvaluatorV2Test {
    @Test
    fun normalizesNfkcPunctuationAndTranslatedArtistAlias() {
        val result = evaluate(
            reference(title = "Ｅｃｈｏ！！", artist = "こはならむ", album = "Album"),
            reference(title = "Echo", artist = "こはならむ (Kohana Lam)", album = "Album")
        )

        assertEquals(1.0, result.titleScore, 0.0001)
        assertTrue(result.artistScore >= 0.92)
        assertFalse(result.hasHardConflict)
    }

    @Test
    fun usesDurationRelativeToleranceWithSafeBounds() {
        assertEquals(2_000L, RecordingMatchEvaluatorV2.dynamicDurationToleranceMs(60_000L, 61_000L))
        assertEquals(6_000L, RecordingMatchEvaluatorV2.dynamicDurationToleranceMs(300_000L, 301_000L))
        assertEquals(6_000L, RecordingMatchEvaluatorV2.dynamicDurationToleranceMs(600_000L, 601_000L))
    }

    @Test
    fun safeDurationDriftCanReachTheStrictMergeFloorButLargerDriftCannot() {
        val reference = reference(
            durationMs = 240_000L,
            canonicalWorkId = "work-echo",
            canonicalWorkConfirmed = true
        )
        val safe = evaluate(
            reference,
            reference(
                durationMs = 243_700L,
                canonicalWorkId = "work-echo",
                canonicalWorkConfirmed = true
            )
        )
        val outside = evaluate(
            reference,
            reference(
                durationMs = 243_900L,
                canonicalWorkId = "work-echo",
                canonicalWorkConfirmed = true
            )
        )

        assertFalse(safe.hasHardConflict)
        assertTrue(safe.similarityScore >= RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
        assertTrue(outside.similarityScore < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
    }

    @Test
    fun metadataOnlyAutoMergeReachesThresholdWithCompleteMetadata() {
        val unresolved = evaluate(reference(), reference())
        val resolved = evaluate(
            reference(canonicalWorkId = "work-echo", canonicalWorkConfirmed = true),
            reference(canonicalWorkId = "work-echo", canonicalWorkConfirmed = true)
        )

        assertEquals(0.95, unresolved.recordingConfidenceCeiling, 0.0001)
        assertTrue(unresolved.similarityScore >= RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
        assertEquals(1.0, resolved.canonicalWorkIdentityScore, 0.0001)
        assertTrue("canonical_work" in resolved.identifierEvidence)
        assertTrue(resolved.similarityScore >= RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
    }

    @Test
    fun conflictingCanonicalWorkIdsAreHardSeparated() {
        val result = evaluate(
            reference(canonicalWorkId = "work-left", canonicalWorkConfirmed = true),
            reference(canonicalWorkId = "work-right", canonicalWorkConfirmed = true)
        )

        assertTrue(RecordingMatchHardConflict.CANONICAL_WORK in result.hardConflicts)
        assertEquals(0.0, result.canonicalWorkIdentityScore, 0.0001)
        assertTrue(result.sameRecordingProbability <= 0.30)
    }

    @Test
    fun classifiesStructuredVersionsWithoutTreatingSongTitleAsLiveRecording() {
        assertEquals(RecordingVersionType.ORIGINAL, RecordingVersionClassifier.classify("Live Forever"))
        assertEquals(RecordingVersionType.LIVE, RecordingVersionClassifier.classify("Echo (Live at Tokyo)"))
        assertEquals(RecordingVersionType.REMIX, RecordingVersionClassifier.classify("Echo - Club Remix"))
        assertEquals(RecordingVersionType.COVER, RecordingVersionClassifier.classify("Echo（翻唱）"))
        assertEquals(RecordingVersionType.REMASTER, RecordingVersionClassifier.classify("Echo (2024 Remastered)"))
        assertEquals(RecordingVersionType.ALTERNATE_TAKE, RecordingVersionClassifier.classify("Echo (2023 Ver.)"))
        assertEquals("echo", RecordingVersionClassifier.coreTitle("Echo Live at Tokyo"))
        assertEquals("echo", RecordingVersionClassifier.coreTitle("Echo - 2024 Remastered"))
        assertEquals("同一首歌", RecordingVersionClassifier.coreTitle("同一首歌 专辑版"))
        assertEquals("echo", RecordingVersionClassifier.coreTitle("Echo (Album Version)"))
    }

    @Test
    fun albumOnlyVersionWordsAreWeakEvidenceAndCannotCreateAConflict() {
        val original = reference()
        val albumOnlyLive = evaluate(original, reference(album = "Echo Live Deluxe"))

        assertEquals(VersionEvidenceSource.ALBUM, albumOnlyLive.versionEvidence.source)
        assertFalse(albumOnlyLive.versionEvidence.strongForConflict)
        assertFalse(RecordingMatchHardConflict.VERSION in albumOnlyLive.hardConflicts)
    }

    @Test
    fun liveAndDifferentArtistAreHardConflictsButRemasterIsCompatible() {
        val original = reference()
        val live = evaluate(original, reference(title = "Echo (Live)", album = "Live Album"))
        val differentArtist = evaluate(original, reference(artist = "Other Artist"))
        val remaster = evaluate(original, reference(title = "Echo (2024 Remastered)"))

        assertTrue(RecordingMatchHardConflict.VERSION in live.hardConflicts)
        assertTrue(RecordingMatchHardConflict.PRIMARY_ARTIST in differentArtist.hardConflicts)
        assertFalse(remaster.hasHardConflict)
    }

    @Test
    fun separatesRecordingProbabilityFromSameWorkRelationship() {
        val original = reference()
        val live = evaluate(original, reference(title = "Echo (Live at Tokyo)", album = "Live Album"))

        assertTrue(live.sameRecordingProbability < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
        assertTrue(live.sameWorkProbability >= 0.85)
        assertEquals(RecordingRelationship.SAME_WORK_DIFFERENT_VERSION, live.relationship)
        assertTrue(RecordingMatchHardConflict.VERSION in live.hardConflicts)
    }

    @Test
    fun sameWorkMbidRecognizesCoverWithoutAllowingRecordingMerge() {
        val original = reference(workMbid = "work-123")
        val cover = evaluate(
            original,
            reference(
                title = "Echo (Cover)",
                artist = "Cover Artist",
                workMbid = "work-123"
            )
        )

        assertEquals(1.0, cover.sameWorkProbability, 0.0001)
        assertEquals(RecordingRelationship.SAME_WORK_DIFFERENT_VERSION, cover.relationship)
        assertTrue(cover.sameRecordingProbability < RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE)
        assertTrue(MatchEvidence.WORK_MBID in cover.evidence)
    }

    @Test
    fun sameTitleDifferentArtistWithoutWorkEvidenceIsCannotLink() {
        val result = evaluate(reference(), reference(artist = "Unrelated Artist"))

        assertTrue(result.sameWorkProbability < 0.85)
        assertEquals(RecordingRelationship.CANNOT_LINK, result.relationship)
        assertTrue(RecordingMatchHardConflict.PRIMARY_ARTIST in result.hardConflicts)
    }

    @Test
    fun conflictingWorkMbidRejectsBothRecordingAndWorkIdentity() {
        val result = evaluate(
            reference(workMbid = "work-left"),
            reference(workMbid = "work-right")
        )

        assertEquals(0.0, result.sameWorkProbability, 0.0001)
        assertTrue(result.sameRecordingProbability <= 0.30)
        assertEquals(RecordingRelationship.CANNOT_LINK, result.relationship)
        assertTrue(RecordingMatchHardConflict.WORK_MBID in result.hardConflicts)
    }

    @Test
    fun explicitVersionFamiliesRemainWorkRelationsAndNeverRecordingMerges() {
        val original = reference()
        val versions = listOf(
            "Echo (Live at Tokyo)",
            "Echo (Club Remix)",
            "Echo (Instrumental)",
            "Echo (Karaoke)",
            "Echo (Acoustic)",
            "Echo (Sped Up)",
            "Echo (Slowed)",
            "Echo (Alternate Take)"
        )

        versions.forEach { title ->
            val result = evaluate(original, reference(title = title))
            assertTrue(title, result.sameRecordingProbability < 0.92)
            assertTrue(title, result.sameWorkProbability >= 0.85)
            assertEquals(title, RecordingRelationship.SAME_WORK_DIFFERENT_VERSION, result.relationship)
            assertTrue(title, RecordingMatchHardConflict.VERSION in result.hardConflicts)
        }
    }

    @Test
    fun sameIsrcNeverOverridesArtistConflict() {
        val result = evaluate(
            reference(isrc = "JPABC1234567"),
            reference(artist = "Other Artist", isrc = "JP-ABC-12-34567")
        )

        assertTrue("isrc" in result.identifierEvidence)
        assertTrue(RecordingMatchHardConflict.PRIMARY_ARTIST in result.hardConflicts)
        assertTrue(result.similarityScore <= 0.60)
    }

    @Test
    fun scoreIsSymmetric() {
        val left = reference(title = "Echo", durationMs = 180_000L)
        val right = reference(title = "Echo!", durationMs = 183_000L)

        assertEquals(evaluate(left, right).similarityScore, evaluate(right, left).similarityScore, 0.0001)
    }

    private fun evaluate(
        left: StreamingTrackMatchPolicy.Reference,
        right: StreamingTrackMatchPolicy.Reference
    ): MatchEvaluation = RecordingMatchEvaluatorV2.evaluate(left, right)

    private fun reference(
        title: String = "Echo",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 180_000L,
        isrc: String = "",
        workMbid: String = "",
        canonicalWorkId: String = "",
        canonicalWorkConfirmed: Boolean = false,
        canonicalAlbumId: String = "",
        canonicalAlbumConfirmed: Boolean = false
    ) = StreamingTrackMatchPolicy.Reference(
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        isrc = isrc,
        workMbid = workMbid,
        canonicalWorkId = canonicalWorkId,
        canonicalWorkConfirmed = canonicalWorkConfirmed,
        canonicalAlbumId = canonicalAlbumId,
        canonicalAlbumConfirmed = canonicalAlbumConfirmed
    )
}
