package app.yukine.identity

import app.yukine.streaming.IdentityScoringMode
import app.yukine.streaming.RecordingMatchEvaluatorV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityCandidateRankingTest {
    @Test
    fun recordingRankerUsesSharedV5ShadowPolicyByDefault() {
        val target = recording("local", "1", artistIds = setOf(7L))
        val candidate = recording("webdav", "2", artistIds = setOf(7L))

        val shadow = RecordingCandidateRanker().rank(target, listOf(candidate)).single()
        val on = RecordingCandidateRanker(scoringMode = IdentityScoringMode.V5_ON)
            .rank(target, listOf(candidate))
            .single()

        assertEquals(RecordingMatchEvaluatorV2.SCORE_VERSION, shadow.scoreVersion)
        assertTrue(shadow.shadowScore != null)
        assertEquals(RecordingMatchEvaluatorV2.V5_SCORE_VERSION, on.scoreVersion)
        assertTrue(on.shadowScore != null)
    }

    @Test
    fun recognizesRecordingVariantsWithoutChangingDisplayText() {
        assertEquals(RecordingVariantType.ORIGINAL, RecordingVariantRecognizer.recognize("星空"))
        assertEquals(RecordingVariantType.LIVE, RecordingVariantRecognizer.recognize("星空 (Live at Tokyo)"))
        assertEquals(RecordingVariantType.REMIX, RecordingVariantRecognizer.recognize("星空 - Club Remix"))
        assertEquals(RecordingVariantType.COVER, RecordingVariantRecognizer.recognize("星空（翻唱）"))
        assertEquals(
            RecordingVariantType.ORIGINAL,
            RecordingVariantRecognizer.recognize("星空", "Instrumental")
        )
    }

    @Test
    fun sameIsrcArtistTitleAndDurationCanAutoConfirm() {
        val target = recording("local", "1", artistIds = setOf(7L), isrc = "JPABC1234567")
        val candidates = listOf(
            recording("lx", "wrong", title = "别的歌", artistIds = setOf(7L)),
            recording("netease", "right", artistIds = setOf(7L), isrc = "JP-ABC-12-34567")
        )

        val result = RecordingCandidateRanker().chooseForAutoConfirmation(target, candidates)

        assertTrue(result.eligible)
        assertEquals("right", result.winner?.candidate?.providerItemId)
        assertEquals(1.0, result.bestScore, 0.0001)
        assertTrue(result.margin >= 0.08)
    }

    @Test
    fun differentPrimaryArtistAndLiveVersionAreHardRejected() {
        val target = recording("local", "1", artistIds = setOf(7L))
        val differentArtist = recording("lx", "2", artistIds = setOf(9L))
        val live = recording(
            "qq",
            "3",
            artistIds = setOf(7L),
            variant = RecordingVariantType.LIVE
        )

        val ranked = RecordingCandidateRanker().rank(target, listOf(differentArtist, live))

        assertTrue(ranked.all { it.hardConflict })
        assertTrue(ranked.flatMap { it.reasons }.contains("different_primary_artist"))
        assertTrue(ranked.flatMap { it.reasons }.contains("different_variant"))
    }

    @Test
    fun sameWorkWithDifferentArtistIsCoverRelationshipNotRecordingMerge() {
        val target = recording("local", "1", artistIds = setOf(7L), workMbid = "work-1")
        val cover = recording("itunes", "2", artistIds = setOf(9L), workMbid = "work-1")

        val ranked = RecordingCandidateRanker().rank(target, listOf(cover)).single()

        assertTrue(ranked.hardConflict)
        assertTrue(ranked.reasons.contains("same_work_different_artist_cover"))
    }

    @Test
    fun closeRecordingScoresNeverAutoConfirm() {
        val target = recording("local", "1", artistIds = setOf(7L), isrc = "JPABC1234567")
        val candidates = listOf(
            recording("qq", "a", artistIds = setOf(7L), isrc = "JPABC1234567"),
            recording("netease", "b", artistIds = setOf(7L), isrc = "JPABC1234567")
        )

        val result = RecordingCandidateRanker().chooseForAutoConfirmation(target, candidates)

        assertFalse(result.eligible)
        assertEquals(0.0, result.margin, 0.0001)
    }

    @Test
    fun acoustIdFingerprintCanBeStrongEvidenceWithSafeMargin() {
        val target = recording("local", "1", artistIds = setOf(7L))
        val strong = recording(
            "acoustid",
            "fingerprint-hit",
            artistIds = setOf(7L),
            fingerprintVerified = true,
            providerScore = 0.98
        )
        val weak = recording("musicbrainz", "text-only", title = "星空 (Edit)", artistIds = setOf(7L))

        val result = RecordingCandidateRanker().chooseForAutoConfirmation(target, listOf(weak, strong))

        assertTrue(result.eligible)
        assertEquals("fingerprint-hit", result.winner?.candidate?.providerItemId)
        assertTrue(result.bestScore >= 0.98)
        assertTrue(result.margin >= 0.08)
    }

    @Test
    fun acoustIdFingerprintNeverOverridesArtistOrVersionConflict() {
        val target = recording("local", "1", artistIds = setOf(7L))
        val conflict = recording(
            "acoustid",
            "wrong-live",
            artistIds = setOf(9L),
            variant = RecordingVariantType.LIVE,
            fingerprintVerified = true,
            providerScore = 1.0
        )

        val ranked = RecordingCandidateRanker().rank(target, listOf(conflict)).single()

        assertTrue(ranked.hardConflict)
        assertEquals(0.0, ranked.score, 0.0)
    }

    @Test
    fun artistNeedsRichEvidenceAndSafeMargin() {
        val target = artist("local", "hanser", type = ArtistType.PERSON, country = "CN")
        val strong = artist(
            "musicbrainz",
            "strong",
            aliases = setOf("憨色"),
            type = ArtistType.PERSON,
            country = "CN",
            sharedTrackRatio = 1.0,
            albumMatch = true,
            providers = 2
        )
        val weak = artist("itunes", "weak", displayName = "Hanser Project")

        val result = ArtistCandidateRanker().chooseForAutoConfirmation(target, listOf(weak, strong))

        assertTrue(result.eligible)
        assertEquals("strong", result.winner?.candidate?.providerItemId)
        assertEquals(1.0, result.bestScore, 0.0001)
    }

    @Test
    fun artistSameNameDifferentCountryAndGroupPersonAreRejected() {
        val target = artist("local", "same", type = ArtistType.PERSON, country = "CN")
        val countryConflict = artist("mb", "country", type = ArtistType.PERSON, country = "JP")
        val typeConflict = artist("qq", "type", type = ArtistType.GROUP, country = "CN")

        val ranked = ArtistCandidateRanker().rank(target, listOf(countryConflict, typeConflict))

        assertTrue(ranked.all { it.hardConflict })
        assertTrue(ranked.flatMap { it.reasons }.contains("same_name_different_country"))
        assertTrue(ranked.flatMap { it.reasons }.contains("different_artist_type"))
    }

    @Test
    fun virtualCharacterAndDifferentVoiceActorAreRejected() {
        val target = artist(
            "local",
            "virtual",
            type = ArtistType.VIRTUAL,
            voiceArtist = "voice-a"
        )
        val candidate = artist(
            "wikidata",
            "virtual-other",
            type = ArtistType.VIRTUAL,
            voiceArtist = "voice-b"
        )

        val ranked = ArtistCandidateRanker().rank(target, listOf(candidate)).single()

        assertTrue(ranked.hardConflict)
        assertTrue(ranked.reasons.contains("virtual_voice_artist_conflict"))
    }

    @Test
    fun providerQualityBreaksOtherwiseEquivalentArtistCandidates() {
        val target = artist("local", "hanser")
        val lowQuality = artist("itunes", "low", providerScore = 0.10)
        val highQuality = artist("musicbrainz", "high", providerScore = 0.95)

        val ranked = ArtistCandidateRanker().rank(target, listOf(lowQuality, highQuality))

        assertEquals("high", ranked.first().candidate.providerItemId)
        assertTrue(ranked.first().score > ranked.last().score)
        assertTrue(ranked.first().reasons.contains("provider_quality"))
    }

    private fun recording(
        provider: String,
        id: String,
        title: String = "星空",
        artistIds: Set<Long> = emptySet(),
        isrc: String = "",
        workMbid: String = "",
        variant: RecordingVariantType = RecordingVariantType.ORIGINAL,
        fingerprintVerified: Boolean = false,
        providerScore: Double = 0.0
    ) = RecordingMatchEvidence(
        provider = provider,
        providerItemId = id,
        title = title,
        primaryArtistIds = artistIds,
        primaryArtistNames = setOf("Hanser"),
        album = "Album",
        durationMs = 240_000L,
        isrc = isrc,
        workMbid = workMbid,
        fingerprintVerified = fingerprintVerified,
        providerScore = providerScore,
        variantType = variant
    )

    private fun artist(
        provider: String,
        id: String,
        displayName: String = "Hanser",
        aliases: Set<String> = setOf("hanser"),
        type: ArtistType = ArtistType.UNKNOWN,
        country: String = "",
        sharedTrackRatio: Double = 0.0,
        albumMatch: Boolean = false,
        providerScore: Double = 0.0,
        providers: Int = 1,
        voiceArtist: String = ""
    ) = ArtistMatchEvidence(
        provider = provider,
        providerItemId = id,
        displayName = displayName,
        aliases = aliases,
        sharedTrackRatio = sharedTrackRatio,
        albumMatch = albumMatch,
        countryCode = country,
        artistType = type,
        providerScore = providerScore,
        matchingProviderCount = providers,
        relatedVoiceArtistId = voiceArtist
    )
}
