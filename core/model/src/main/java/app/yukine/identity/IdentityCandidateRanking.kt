package app.yukine.identity

import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.RecordingVersionType
import app.yukine.streaming.StreamingTrackMatchPolicy
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class RecordingVariantType {
    ORIGINAL,
    LIVE,
    REMIX,
    COVER,
    ACOUSTIC,
    INSTRUMENTAL,
    KARAOKE,
    DEMO,
    EDIT,
    UNKNOWN
}

/** Pure, offline recording evidence used by search-provider adapters and tests. */
data class RecordingMatchEvidence(
    val provider: String,
    val providerItemId: String,
    val title: String,
    val primaryArtistIds: Set<Long> = emptySet(),
    val primaryArtistNames: Set<String> = emptySet(),
    val album: String = "",
    val durationMs: Long = 0L,
    val isrc: String = "",
    val recordingMbid: String = "",
    val workMbid: String = "",
    val acoustId: String = "",
    val fingerprintVerified: Boolean = false,
    val providerScore: Double = 0.0,
    val variantType: RecordingVariantType = RecordingVariantType.UNKNOWN,
    val isKnownCover: Boolean = false
)

data class ArtistMatchEvidence(
    val provider: String,
    val providerItemId: String,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
    val artistMbid: String = "",
    val confirmedProviderMapping: Boolean = false,
    val confirmedAliasMatch: Boolean = false,
    val sharedTrackRatio: Double = 0.0,
    val albumMatch: Boolean = false,
    val countryCode: String = "",
    val artistType: ArtistType = ArtistType.UNKNOWN,
    val providerScore: Double = 0.0,
    val matchingProviderCount: Int = 1,
    val isKnownCoverArtist: Boolean = false,
    val relatedVoiceArtistId: String = ""
)

data class RankedIdentityCandidate<T>(
    val candidate: T,
    val score: Double,
    val hardConflict: Boolean,
    val reasons: List<String>
)

data class AutoConfirmation<T>(
    val winner: RankedIdentityCandidate<T>?,
    val bestScore: Double,
    val runnerUpScore: Double,
    val margin: Double,
    val eligible: Boolean
)

object RecordingVariantRecognizer {
    private val live = token("live", "现场", "演唱会", "concert")
    private val remix = token("remix", "mix", "重混", "混音", "bootleg")
    private val cover = token("cover", "翻唱", "covered by", "歌ってみた")
    private val acoustic = token("acoustic", "unplugged", "不插电")
    private val instrumental = token("instrumental", "伴奏", "纯音乐", "off vocal")
    private val karaoke = token("karaoke", "卡拉ok", "卡拉 ok")
    private val demo = token("demo", "样带", "试听版")
    private val edit = token("radio edit", "edit", "剪辑版", "短版")

    fun recognize(title: String, album: String = ""): RecordingVariantType {
        val value = IdentityTextNormalizer.normalizeForSearch("$title $album")
        return when {
            cover.containsMatchIn(value) -> RecordingVariantType.COVER
            live.containsMatchIn(value) -> RecordingVariantType.LIVE
            remix.containsMatchIn(value) -> RecordingVariantType.REMIX
            acoustic.containsMatchIn(value) -> RecordingVariantType.ACOUSTIC
            instrumental.containsMatchIn(value) -> RecordingVariantType.INSTRUMENTAL
            karaoke.containsMatchIn(value) -> RecordingVariantType.KARAOKE
            demo.containsMatchIn(value) -> RecordingVariantType.DEMO
            edit.containsMatchIn(value) -> RecordingVariantType.EDIT
            value.isBlank() -> RecordingVariantType.UNKNOWN
            else -> RecordingVariantType.ORIGINAL
        }
    }

    private fun token(vararg values: String): Regex = Regex(
        values.joinToString("|") { Regex.escape(IdentityTextNormalizer.normalizeForSearch(it)) },
        setOf(RegexOption.IGNORE_CASE)
    )
}

object IdentityTextNormalizer {
    private val featSuffix = Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.+$", RegexOption.IGNORE_CASE)
    private val punctuation = Regex("[\\p{P}\\p{S}]+")
    private val whitespace = Regex("\\s+")

    fun normalizeForSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(featSuffix, "")
        .replace(punctuation, " ")
        .replace(whitespace, " ")
        .trim()
}

class RecordingCandidateRanker(
    private val minimumScore: Double = 0.92,
    private val minimumMargin: Double = 0.08
) {
    fun rank(
        target: RecordingMatchEvidence,
        candidates: List<RecordingMatchEvidence>
    ): List<RankedIdentityCandidate<RecordingMatchEvidence>> = candidates
        .map { score(target, it) }
        .sortedWith(compareByDescending<RankedIdentityCandidate<RecordingMatchEvidence>> { it.score }
            .thenBy { it.candidate.provider }
            .thenBy { it.candidate.providerItemId })

    fun chooseForAutoConfirmation(
        target: RecordingMatchEvidence,
        candidates: List<RecordingMatchEvidence>
    ): AutoConfirmation<RecordingMatchEvidence> = choose(rank(target, candidates))

    private fun score(
        target: RecordingMatchEvidence,
        candidate: RecordingMatchEvidence
    ): RankedIdentityCandidate<RecordingMatchEvidence> {
        val reasons = mutableListOf<String>()
        val supplementalConflict = recordingHardConflict(target, candidate, reasons)
        val evaluation = RecordingMatchEvaluatorV2.evaluate(target.toReference(), candidate.toReference())
        reasons += evaluation.explanation
        evaluation.hardConflicts.forEach { conflict ->
            reasons += when (conflict) {
                app.yukine.streaming.RecordingMatchHardConflict.PRIMARY_ARTIST -> "different_primary_artist"
                app.yukine.streaming.RecordingMatchHardConflict.VERSION -> "different_variant"
                app.yukine.streaming.RecordingMatchHardConflict.RECORDING_MBID -> "different_recording_mbid"
                app.yukine.streaming.RecordingMatchHardConflict.FINGERPRINT -> "different_acoustid"
                else -> "different_${conflict.name.lowercase(Locale.ROOT)}"
            }
        }
        val hardConflict = supplementalConflict || evaluation.hardConflicts.isNotEmpty()
        val fingerprintScore = candidate.providerScore.coerceIn(0.0, 1.0)
            .takeIf { candidate.fingerprintVerified && it > 0.0 }
            ?: 0.0
        if (fingerprintScore > 0.0) reasons += "acoustid_fingerprint_match"
        return RankedIdentityCandidate(
            candidate = candidate,
            score = if (hardConflict) 0.0 else maxOf(evaluation.sameRecordingProbability, fingerprintScore),
            hardConflict = hardConflict,
            reasons = reasons
        )
    }

    private fun recordingHardConflict(
        target: RecordingMatchEvidence,
        candidate: RecordingMatchEvidence,
        reasons: MutableList<String>
    ): Boolean {
        if (sameNormalizedId(target.workMbid, candidate.workMbid)
            && target.primaryArtistIds.isNotEmpty() && candidate.primaryArtistIds.isNotEmpty()
            && target.primaryArtistIds.intersect(candidate.primaryArtistIds).isEmpty()
        ) reasons += "same_work_different_artist_cover"
        if (target.isKnownCover != candidate.isKnownCover) reasons += "original_cover_conflict"
        return reasons.isNotEmpty()
    }

    private fun choose(
        ranked: List<RankedIdentityCandidate<RecordingMatchEvidence>>
    ): AutoConfirmation<RecordingMatchEvidence> {
        val best = ranked.firstOrNull()
        val runnerUp = ranked.getOrNull(1)?.score ?: 0.0
        val bestScore = best?.score ?: 0.0
        val margin = bestScore - runnerUp
        return AutoConfirmation(
            winner = best,
            bestScore = bestScore,
            runnerUpScore = runnerUp,
            margin = margin,
            eligible = best != null && !best.hardConflict && bestScore >= minimumScore && margin >= minimumMargin
        )
    }
}

private fun RecordingMatchEvidence.toReference(): StreamingTrackMatchPolicy.Reference =
    StreamingTrackMatchPolicy.Reference(
        title = title,
        artistNames = primaryArtistNames.toList(),
        primaryArtistIds = primaryArtistIds.mapTo(linkedSetOf(), Long::toString),
        album = album,
        durationMs = durationMs.takeIf { it > 0L },
        isrc = isrc,
        provider = provider,
        providerTrackId = providerItemId,
        recordingMbid = recordingMbid,
        workMbid = workMbid,
        fingerprint = acoustId,
        versionType = variantType.toV2()
    )

private fun RecordingVariantType.toV2(): RecordingVersionType = when (this) {
    RecordingVariantType.ORIGINAL -> RecordingVersionType.ORIGINAL
    RecordingVariantType.LIVE -> RecordingVersionType.LIVE
    RecordingVariantType.REMIX -> RecordingVersionType.REMIX
    RecordingVariantType.COVER -> RecordingVersionType.COVER
    RecordingVariantType.ACOUSTIC -> RecordingVersionType.ACOUSTIC
    RecordingVariantType.INSTRUMENTAL -> RecordingVersionType.INSTRUMENTAL
    RecordingVariantType.KARAOKE -> RecordingVersionType.KARAOKE
    RecordingVariantType.DEMO -> RecordingVersionType.DEMO
    RecordingVariantType.EDIT -> RecordingVersionType.RADIO_EDIT
    RecordingVariantType.UNKNOWN -> RecordingVersionType.UNKNOWN
}

class ArtistCandidateRanker(
    private val minimumScore: Double = 0.92,
    private val minimumMargin: Double = 0.08
) {
    fun rank(
        target: ArtistMatchEvidence,
        candidates: List<ArtistMatchEvidence>
    ): List<RankedIdentityCandidate<ArtistMatchEvidence>> = candidates
        .map { score(target, it) }
        .sortedWith(compareByDescending<RankedIdentityCandidate<ArtistMatchEvidence>> { it.score }
            .thenBy { it.candidate.provider }
            .thenBy { it.candidate.providerItemId })

    fun chooseForAutoConfirmation(
        target: ArtistMatchEvidence,
        candidates: List<ArtistMatchEvidence>
    ): AutoConfirmation<ArtistMatchEvidence> {
        val ranked = rank(target, candidates)
        val best = ranked.firstOrNull()
        val runnerUp = ranked.getOrNull(1)?.score ?: 0.0
        val bestScore = best?.score ?: 0.0
        val margin = bestScore - runnerUp
        return AutoConfirmation(
            winner = best,
            bestScore = bestScore,
            runnerUpScore = runnerUp,
            margin = margin,
            eligible = best != null && !best.hardConflict && bestScore >= minimumScore && margin >= minimumMargin
        )
    }

    private fun score(
        target: ArtistMatchEvidence,
        candidate: ArtistMatchEvidence
    ): RankedIdentityCandidate<ArtistMatchEvidence> {
        val reasons = mutableListOf<String>()
        if (artistHardConflict(target, candidate, reasons)) {
            return RankedIdentityCandidate(candidate, 0.0, true, reasons)
        }
        if (sameStrongId(target.artistMbid, candidate.artistMbid, "artist_mbid", reasons)
            || candidate.confirmedProviderMapping
        ) {
            if (candidate.confirmedProviderMapping) reasons += "confirmed_provider_mapping"
            return RankedIdentityCandidate(candidate, 1.0, false, reasons)
        }

        var score = 0.0
        val targetNames = (target.aliases + target.displayName).map(IdentityTextNormalizer::normalizeForSearch).toSet()
        val candidateNames = (candidate.aliases + candidate.displayName).map(IdentityTextNormalizer::normalizeForSearch).toSet()
        if (candidate.confirmedAliasMatch || targetNames.intersect(candidateNames).isNotEmpty()) {
            score += 0.35
            reasons += "alias"
        }
        if (sameText(target.displayName, candidate.displayName)) {
            score += 0.25
            reasons += "primary_name"
        }
        val sharedTrackScore = candidate.sharedTrackRatio.coerceIn(0.0, 1.0) * 0.20
        if (sharedTrackScore > 0.0) reasons += "shared_tracks"
        score += sharedTrackScore
        if (candidate.albumMatch) {
            score += 0.10
            reasons += "album"
        }
        if (countryAndTypeMatch(target, candidate)) {
            score += 0.05
            reasons += "country_and_type"
        }
        val providerQualityScore = candidate.providerScore.coerceIn(0.0, 1.0) * 0.05
        if (providerQualityScore > 0.0) reasons += "provider_quality"
        score += providerQualityScore
        if (candidate.matchingProviderCount >= 2) {
            score += 0.05
            reasons += "multiple_providers"
        }
        return RankedIdentityCandidate(candidate, score.coerceIn(0.0, 1.0), false, reasons)
    }

    private fun artistHardConflict(
        target: ArtistMatchEvidence,
        candidate: ArtistMatchEvidence,
        reasons: MutableList<String>
    ): Boolean {
        if (differentKnownId(target.artistMbid, candidate.artistMbid)) reasons += "different_artist_mbid"
        if (target.artistType != ArtistType.UNKNOWN && candidate.artistType != ArtistType.UNKNOWN
            && target.artistType != candidate.artistType
        ) reasons += "different_artist_type"
        if (target.countryCode.isNotBlank() && candidate.countryCode.isNotBlank()
            && !target.countryCode.equals(candidate.countryCode, ignoreCase = true)
            && sameText(target.displayName, candidate.displayName)
        ) reasons += "same_name_different_country"
        if (target.isKnownCoverArtist != candidate.isKnownCoverArtist) reasons += "original_cover_artist_conflict"
        if (target.relatedVoiceArtistId.isNotBlank() && candidate.relatedVoiceArtistId.isNotBlank()
            && target.relatedVoiceArtistId != candidate.relatedVoiceArtistId
        ) reasons += "virtual_voice_artist_conflict"
        return reasons.isNotEmpty()
    }

    private fun countryAndTypeMatch(target: ArtistMatchEvidence, candidate: ArtistMatchEvidence): Boolean {
        val countryMatches = target.countryCode.isNotBlank() && candidate.countryCode.isNotBlank()
            && target.countryCode.equals(candidate.countryCode, ignoreCase = true)
        val typeMatches = target.artistType != ArtistType.UNKNOWN && target.artistType == candidate.artistType
        return countryMatches && typeMatches
    }
}

private fun sameText(left: String, right: String): Boolean {
    val normalizedLeft = IdentityTextNormalizer.normalizeForSearch(left)
    val normalizedRight = IdentityTextNormalizer.normalizeForSearch(right)
    return normalizedLeft.isNotBlank() && normalizedLeft == normalizedRight
}

private fun sameNormalizedId(left: String, right: String): Boolean = left.isNotBlank()
    && right.isNotBlank()
    && left.filter(Char::isLetterOrDigit).equals(right.filter(Char::isLetterOrDigit), ignoreCase = true)

private fun differentKnownId(left: String, right: String): Boolean = left.isNotBlank()
    && right.isNotBlank()
    && !left.equals(right, ignoreCase = true)

private fun sameStrongId(left: String, right: String, reason: String, reasons: MutableList<String>): Boolean {
    val same = left.isNotBlank() && right.isNotBlank() && left.equals(right, ignoreCase = true)
    if (same) reasons += reason
    return same
}

private fun durationDifference(left: Long, right: Long): Long? = if (left > 0L && right > 0L) abs(left - right) else null
