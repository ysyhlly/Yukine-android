package app.yukine.streaming

import app.yukine.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/** Shared identity, ranking, and cross-provider recording matching policy. */
object StreamingTrackMatchPolicy {
    private val unknownValues = setOf(
        "<unknown>",
        "unknown",
        "unknown song",
        "unknown artist",
        "unknown album",
        "\u672a\u77e5\u6b4c\u66f2",
        "\u672a\u77e5\u827a\u4eba",
        "\u672a\u77e5\u4e13\u8f91"
    )
    private val bracketedQualifier = Regex(
        """(?:\(|\[|（|【)\s*([^()\[\]（）【】]*)\s*(?:\)|\]|）|】)"""
    )
    private val featuredArtistSuffix = Regex(
        """(?i)\s*[-–—]?\s*(?:(?:\(|\[|（|【)\s*)?(?:feat(?:uring)?\.?|ft\.?)\s+[^()\[\]（）【】]+(?:\s*(?:\)|\]|）|】))?\s*$"""
    )
    private val translatedAliasMarker = Regex(
        """(?i)(?:\b(?:translation|translated|chinese)\b|中文|中译|中譯|翻译|翻譯|译名|譯名|[的了们們是后後我你他她这這个個为為鱼])"""
    )
    private val versionQualifier = Regex(
        """(?i)\b(?:remix|mix|version|ver\.?|live|acoustic|instrumental|demo|edit|cover|karaoke|radio|extended|rework|remaster(?:ed)?|remake|alternate|alt\.?|intro|outro|interlude)\b|(?:伴奏|纯音乐|純音樂|翻唱|现场|現場|演唱会|演唱會|混音|版本|重制|重製|リミックス|ライブ|バージョン|アコースティック|インスト|デモ|カバー)"""
    )
    private val artistSeparator = Regex(
        """(?i)\s*(?:[/／、,，;；&＆+＋×]|\bfeat(?:uring)?\.?\b|\bft\.?\b|\bwith\b|\band\b|和|与|與|及)\s*"""
    )
    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")

    data class Reference(
        val title: String,
        val titleAliases: List<String> = emptyList(),
        val artist: String = "",
        val artistNames: List<String> = emptyList(),
        val primaryArtistIds: Set<String> = emptySet(),
        val featuredArtistNames: List<String> = emptyList(),
        val characterArtistNames: List<String> = emptyList(),
        val voiceActorNames: List<String> = emptyList(),
        val album: String? = null,
        val durationMs: Long? = null,
        val isrc: String? = null,
        val provider: String = "",
        val providerTrackId: String = "",
        val providerTrackIdConfirmed: Boolean = false,
        val recordingMbid: String = "",
        val workMbid: String = "",
        val fingerprint: String = "",
        val versionType: RecordingVersionType? = null
    )

    data class CandidateMatch(
        val track: StreamingTrack,
        val score: Int,
        val confidence: Float,
        val reliable: Boolean,
        val isrcExact: Boolean,
        val evaluation: MatchEvaluation
    )

    data class ShadowComparison(
        val v1Top1ProviderTrackId: String?,
        val v2Top1ProviderTrackId: String?,
        val v2Top1Score: Double,
        val changed: Boolean
    )

    fun searchQuery(track: Track?): String {
        if (track == null) return ""
        val title = sanitize(track.title)
        val artist = sanitize(track.artist)
        return when {
            title.isNotBlank() && artist.isNotBlank() -> "$title $artist"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> ""
        }
    }

    /** LX result order is title-driven; artist metadata is intentionally not added to this query. */
    fun titleSearchQuery(track: Track?): String = track?.let { sanitize(it.title) }.orEmpty()

    fun reference(track: Track): Reference = Reference(
        title = track.title,
        artist = track.artist,
        album = track.album,
        durationMs = track.durationMs.takeIf { it > 0L }
    )

    fun reference(track: StreamingTrack): Reference = Reference(
        title = track.title,
        artist = track.artist,
        artistNames = track.artists.map { it.name },
        album = track.album,
        durationMs = track.durationMs?.takeIf { it > 0L },
        isrc = track.isrc,
        provider = track.provider.wireName,
        providerTrackId = track.providerTrackId
    )

    /**
     * Returns the highest ranked search result. Weak metadata still falls back deterministically to
     * the provider's original order, while callers that persist a merge can use
     * [pickReliableCandidate].
     */
    fun pickBestCandidate(local: Track?, candidates: List<StreamingTrack>): StreamingTrack? {
        if (local == null) return null
        return rankCandidates(reference(local), candidates).firstOrNull()?.track
    }

    fun pickReliableCandidate(local: Track?, candidates: List<StreamingTrack>): StreamingTrack? {
        if (local == null) return null
        return rankCandidates(reference(local), candidates).firstOrNull { it.reliable }?.track
    }

    fun rankCandidates(reference: Reference, candidates: List<StreamingTrack>): List<CandidateMatch> {
        val referenceFeatures = RecordingMatchFeatureExtractor.extract(reference)
        return candidates.withIndex()
            .map { indexed ->
                val candidateFeatures = RecordingMatchFeatureExtractor.extract(reference(indexed.value))
                indexed.index to candidateMatch(
                    indexed.value,
                    RecordingMatchEvaluatorV2.evaluate(
                        referenceFeatures,
                        candidateFeatures,
                        includeExplanation = false
                    )
                )
            }
            .sortedWith(
                compareByDescending<Pair<Int, CandidateMatch>> { it.second.evaluation.sameRecordingProbability }
                    .thenByDescending { it.second.evaluation.identifierEvidence.size }
                    .thenByDescending { it.second.evaluation.versionScore }
                    .thenBy { it.second.evaluation.durationDeltaMs ?: Long.MAX_VALUE }
                    .thenBy { it.first }
            )
            .map { it.second }
    }

    /** Legacy production ranking retained for shadow comparison and instant feature-flag rollback. */
    fun pickBestCandidateV1(reference: Reference, candidates: List<StreamingTrack>): StreamingTrack? {
        if (candidates.isEmpty()) return null
        val titleNeedle = sanitize(reference.title).lowercase(Locale.ROOT)
        val artistNeedle = sanitize(reference.artist).lowercase(Locale.ROOT)
        return candidates.firstOrNull { candidate ->
            sanitize(candidate.title).lowercase(Locale.ROOT) == titleNeedle &&
                sanitize(candidate.artist).lowercase(Locale.ROOT) == artistNeedle
        } ?: candidates.firstOrNull { candidate ->
            titleNeedle.isNotBlank() &&
                sanitize(candidate.title).lowercase(Locale.ROOT).contains(titleNeedle)
        } ?: candidates.first()
    }

    fun shadowCompare(reference: Reference, candidates: List<StreamingTrack>): ShadowComparison {
        val v1 = pickBestCandidateV1(reference, candidates)
        val v2 = rankCandidates(reference, candidates).firstOrNull()
        val v1Id = v1?.providerTrackId
        val v2Id = v2?.track?.providerTrackId
        return ShadowComparison(
            v1Top1ProviderTrackId = v1Id,
            v2Top1ProviderTrackId = v2Id,
            v2Top1Score = v2?.evaluation?.sameRecordingProbability ?: 0.0,
            changed = v1Id != v2Id
        )
    }

    fun evaluate(reference: Reference, candidate: StreamingTrack): CandidateMatch {
        return candidateMatch(candidate, RecordingMatchEvaluatorV2.evaluate(reference, candidate))
    }

    private fun candidateMatch(candidate: StreamingTrack, evaluation: MatchEvaluation): CandidateMatch {
        val reliable = evaluation.hardConflicts.isEmpty() &&
            evaluation.titleScore >= 0.99 &&
            evaluation.artistScore >= 0.92 &&
            evaluation.versionScore >= 0.99 &&
            (evaluation.durationDeltaMs == null ||
                evaluation.durationDeltaMs <= evaluation.durationToleranceMs)
        return CandidateMatch(
            track = candidate,
            score = (evaluation.sameRecordingProbability * 10_000.0).roundToInt(),
            confidence = evaluation.sameRecordingProbability.toFloat(),
            reliable = reliable,
            isrcExact = "isrc" in evaluation.identifierEvidence,
            evaluation = evaluation
        )
    }

    fun isSameRecording(
        left: Reference,
        right: Reference,
        durationToleranceMs: Long = RecordingMatchEvaluatorV2.dynamicDurationToleranceMs(
            left.durationMs,
            right.durationMs
        )
    ): Boolean {
        val evaluation = RecordingMatchEvaluatorV2.evaluate(left, right)
        return evaluation.hardConflicts.isEmpty() &&
            evaluation.titleScore >= 0.99 &&
            evaluation.artistScore >= 0.92 &&
            evaluation.versionScore >= 0.99 &&
            (evaluation.durationDeltaMs == null || evaluation.durationDeltaMs <= durationToleranceMs.coerceAtLeast(0L))
    }

    fun isSameRecording(
        left: StreamingTrack,
        right: StreamingTrack,
        durationToleranceMs: Long = RecordingMatchEvaluatorV2.dynamicDurationToleranceMs(
            left.durationMs,
            right.durationMs
        )
    ): Boolean = isSameRecording(reference(left), reference(right), durationToleranceMs)

    fun canonicalTitle(value: String): String = canonicalText(
        withoutTranslatedAliasQualifier(withoutFeaturedArtistSuffix(normalized(value)))
    )

    fun canonicalAlbum(value: String): String = canonicalText(
        withoutTranslatedAliasQualifier(withoutFeaturedArtistSuffix(normalized(value)))
    )

    fun canonicalArtistKey(values: List<String>): String = canonicalArtistNames(values).joinToString("\u0001")

    fun normalizeIsrc(value: String?): String = value.orEmpty()
        .uppercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    internal fun canonicalArtistNames(values: List<String>): Set<String> = values.asSequence()
        .flatMap { value -> artistSeparator.split(normalized(value)).asSequence() }
        .flatMap { value -> artistAliases(value).asSequence() }
        .filter { it.isNotBlank() && it !in unknownValues }
        .toSortedSet()

    /**
     * Providers commonly append a transliteration or alternate stage name in brackets. Treat the
     * outside name and every bracketed name as aliases so `こはならむ` can match
     * `こはならむ (Kohana Lam)` and reversed aliases remain order-independent.
     */
    private fun artistAliases(value: String): List<String> {
        val aliases = bracketedQualifier.findAll(value)
            .map { canonicalText(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toList()
        val primary = canonicalText(bracketedQualifier.replace(value, " "))
        return buildList(aliases.size + 1) {
            if (primary.isNotBlank()) add(primary)
            addAll(aliases)
        }
    }

    private fun versionSignature(value: String): Set<String> = versionQualifier
        .findAll(withoutTranslatedAliasQualifier(normalized(value)))
        .map { canonicalText(it.value) }
        .filter { it.isNotBlank() }
        .toSortedSet()

    private fun sanitize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val trimmed = value.trim()
        return trimmed.takeUnless { normalized(it) in unknownValues }.orEmpty()
    }

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .trim()

    private fun canonicalText(value: String): String = value
        .replace(punctuation, " ")
        .replace(whitespace, " ")
        .trim()

    private fun withoutFeaturedArtistSuffix(value: String): String = featuredArtistSuffix.replace(value, " ")

    private fun withoutTranslatedAliasQualifier(value: String): String = bracketedQualifier.replace(value) { match ->
        val qualifier = match.groupValues[1].trim()
        if (translatedAliasMarker.containsMatchIn(qualifier) && !versionQualifier.containsMatchIn(qualifier)) {
            " "
        } else {
            match.value
        }
    }
}
