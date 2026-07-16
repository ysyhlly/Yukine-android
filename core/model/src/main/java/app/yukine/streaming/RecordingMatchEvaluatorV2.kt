package app.yukine.streaming

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

enum class RecordingVersionType {
    ORIGINAL,
    LIVE,
    REMIX,
    COVER,
    ACOUSTIC,
    INSTRUMENTAL,
    KARAOKE,
    DEMO,
    RADIO_EDIT,
    EXTENDED,
    CLEAN,
    EXPLICIT,
    SPED_UP,
    SLOWED,
    REMASTER,
    ALTERNATE_TAKE,
    UNKNOWN
}

enum class RecordingMatchHardConflict {
    PROVIDER_ID,
    RECORDING_MBID,
    WORK_MBID,
    FINGERPRINT,
    ISRC,
    PRIMARY_ARTIST,
    VERSION
}

enum class MatchEvidence {
    CONFIRMED_PROVIDER_ID,
    RECORDING_MBID,
    WORK_MBID,
    FINGERPRINT,
    ISRC,
    TITLE,
    PRIMARY_ARTIST,
    DURATION,
    VERSION,
    ALBUM
}

enum class RecordingRelationship {
    SAME_RECORDING,
    SAME_WORK_DIFFERENT_VERSION,
    CANNOT_LINK,
    UNKNOWN
}

data class MatchEvaluation(
    val sameRecordingProbability: Double,
    val sameWorkProbability: Double,
    val relationship: RecordingRelationship,
    val recordingConfidenceCeiling: Double,
    val workConfidenceCeiling: Double,
    val titleScore: Double,
    val artistScore: Double,
    val durationScore: Double,
    val versionScore: Double,
    val albumScore: Double,
    val identifierEvidence: Set<String>,
    val evidence: Set<MatchEvidence>,
    val hardConflicts: Set<RecordingMatchHardConflict>,
    val versionType: RecordingVersionType,
    val referenceVersionType: RecordingVersionType,
    val durationDeltaMs: Long?,
    val durationToleranceMs: Long,
    val explanation: List<String>,
    val scoreVersion: Int = RecordingMatchEvaluatorV2.SCORE_VERSION
) {
    val hasHardConflict: Boolean get() = hardConflicts.isNotEmpty()

    /** Compatibility name for callers whose policy is specifically recording identity. */
    val similarityScore: Double get() = sameRecordingProbability
}

/** Pure V2 recording identity evaluator. Source availability and quality are intentionally absent. */
object RecordingMatchEvaluatorV2 {
    const val SCORE_VERSION = 3
    const val AUTO_MERGE_MINIMUM_SCORE = 0.92
    const val AUTO_MERGE_MINIMUM_MARGIN = 0.08

    private const val TITLE_WEIGHT = 0.34
    private const val ARTIST_WEIGHT = 0.28
    private const val DURATION_WEIGHT = 0.18
    private const val VERSION_WEIGHT = 0.15
    private const val ALBUM_WEIGHT = 0.05

    fun evaluate(
        reference: StreamingTrackMatchPolicy.Reference,
        candidate: StreamingTrack
    ): MatchEvaluation = evaluate(reference, StreamingTrackMatchPolicy.reference(candidate))

    fun evaluate(
        reference: StreamingTrackMatchPolicy.Reference,
        candidate: StreamingTrackMatchPolicy.Reference
    ): MatchEvaluation = evaluate(
        RecordingMatchFeatureExtractor.extract(reference),
        RecordingMatchFeatureExtractor.extract(candidate)
    )

    fun evaluate(
        reference: RecordingMatchFeatures,
        candidate: RecordingMatchFeatures,
        includeExplanation: Boolean = true
    ): MatchEvaluation {
        val explanation = mutableListOf<String>()
        val identifiers = linkedSetOf<String>()
        val evidence = linkedSetOf<MatchEvidence>()
        val hardConflicts = linkedSetOf<RecordingMatchHardConflict>()

        compareIdentifier(
            reference.provider.takeIf { reference.providerTrackIdConfirmed }.orEmpty(),
            reference.providerTrackId.takeIf { reference.providerTrackIdConfirmed }.orEmpty(),
            candidate.provider.takeIf { candidate.providerTrackIdConfirmed }.orEmpty(),
            candidate.providerTrackId.takeIf { candidate.providerTrackIdConfirmed }.orEmpty(),
            "confirmed_provider_id",
            RecordingMatchHardConflict.PROVIDER_ID,
            identifiers,
            hardConflicts
        )
        compareStrongIdentifier(
            reference.recordingMbid.orEmpty(),
            candidate.recordingMbid.orEmpty(),
            "recording_mbid",
            RecordingMatchHardConflict.RECORDING_MBID,
            identifiers,
            hardConflicts
        )
        compareStrongIdentifier(
            reference.workMbid.orEmpty(),
            candidate.workMbid.orEmpty(),
            "work_mbid",
            RecordingMatchHardConflict.WORK_MBID,
            identifiers,
            hardConflicts
        )
        compareStrongIdentifier(
            reference.fingerprint?.value.orEmpty(),
            candidate.fingerprint?.value.orEmpty(),
            "fingerprint",
            RecordingMatchHardConflict.FINGERPRINT,
            identifiers,
            hardConflicts
        )
        compareStrongIdentifier(
            reference.isrc.orEmpty(),
            candidate.isrc.orEmpty(),
            "isrc",
            RecordingMatchHardConflict.ISRC,
            identifiers,
            hardConflicts
        )

        val titleScore = titleSimilarity(reference, candidate)
        if (titleScore >= 0.85) evidence += MatchEvidence.TITLE
        if (includeExplanation) explanation += "title=${formatScore(titleScore)}"

        val referencePrimaryArtists = reference.canonicalArtists.filter { it.role == ArtistRole.PRIMARY }
        val candidatePrimaryArtists = candidate.canonicalArtists.filter { it.role == ArtistRole.PRIMARY }
        val referenceArtistIds = referencePrimaryArtists.mapNotNullTo(linkedSetOf(), ArtistFeature::canonicalId)
        val candidateArtistIds = candidatePrimaryArtists.mapNotNullTo(linkedSetOf(), ArtistFeature::canonicalId)
        val referenceArtists = referencePrimaryArtists.flatMapTo(linkedSetOf()) { it.aliases + it.canonicalName }
            .filterTo(linkedSetOf(), String::isNotBlank)
        val candidateArtists = candidatePrimaryArtists.flatMapTo(linkedSetOf()) { it.aliases + it.canonicalName }
            .filterTo(linkedSetOf(), String::isNotBlank)
        val commonArtistIds = referenceArtistIds.intersect(candidateArtistIds)
        val artistScore = if (commonArtistIds.isNotEmpty()) {
            1.0
        } else {
            setSimilarity(referenceArtists, candidateArtists)
        }
        if (artistScore >= 0.92) evidence += MatchEvidence.PRIMARY_ARTIST
        if (referenceArtistIds.isNotEmpty() && candidateArtistIds.isNotEmpty() &&
            commonArtistIds.isEmpty()
        ) {
            hardConflicts += RecordingMatchHardConflict.PRIMARY_ARTIST
        } else if (referenceArtists.isNotEmpty() && candidateArtists.isNotEmpty() &&
            referenceArtists.intersect(candidateArtists).isEmpty()
        ) {
            hardConflicts += RecordingMatchHardConflict.PRIMARY_ARTIST
        }
        if (includeExplanation) explanation += "artist=${formatScore(artistScore)}"

        val durationDeltaMs = durationDelta(reference.durationMs, candidate.durationMs)
        val durationToleranceMs = dynamicDurationToleranceMs(reference.durationMs, candidate.durationMs)
        val durationScore = durationSimilarity(durationDeltaMs, durationToleranceMs)
        if (durationScore >= 0.80) evidence += MatchEvidence.DURATION
        if (includeExplanation) explanation += "duration=${formatScore(durationScore)}"

        val referenceVersion = reference.versionType
        val candidateVersion = candidate.versionType
        val versionScore = versionSimilarity(referenceVersion, candidateVersion)
        if (RecordingVersionClassifier.hardConflict(referenceVersion, candidateVersion, reference.versionSignature, candidate.versionSignature)) {
            hardConflicts += RecordingMatchHardConflict.VERSION
        }
        if (versionScore >= 0.75) evidence += MatchEvidence.VERSION
        if (includeExplanation) {
            explanation += "version=${referenceVersion.name}->${candidateVersion.name}:${formatScore(versionScore)}"
        }

        val albumScore = albumSimilarity(reference.albumKey.orEmpty(), candidate.albumKey.orEmpty())
        if (albumScore >= 0.65) evidence += MatchEvidence.ALBUM
        if (includeExplanation) explanation += "album=${formatScore(albumScore)}"

        var sameRecordingProbability = titleScore * TITLE_WEIGHT +
            artistScore * ARTIST_WEIGHT +
            durationScore * DURATION_WEIGHT +
            versionScore * VERSION_WEIGHT +
            albumScore * ALBUM_WEIGHT

        sameRecordingProbability = min(
            sameRecordingProbability,
            conditionalVersionScoreCeiling(referenceVersion, candidateVersion)
        )

        val directIdentity = identifiers.any { it == "confirmed_provider_id" || it == "recording_mbid" || it == "fingerprint" }
        val isrcIdentity = "isrc" in identifiers
        if (hardConflicts.isEmpty()) {
            if (directIdentity) sameRecordingProbability = 1.0
            else if (isrcIdentity) sameRecordingProbability = max(sameRecordingProbability, 0.98)
        } else {
            sameRecordingProbability = min(
                sameRecordingProbability,
                hardConflictScoreCeiling(hardConflicts)
            )
            if (includeExplanation) {
                explanation += "hard_conflicts=${hardConflicts.joinToString(",") { it.name }}"
            }
        }
        if (includeExplanation && identifiers.isNotEmpty()) {
            explanation += "identifiers=${identifiers.joinToString(",")}"
        }
        identifiers.mapNotNullTo(evidence) { identifier ->
            when (identifier) {
                "confirmed_provider_id" -> MatchEvidence.CONFIRMED_PROVIDER_ID
                "recording_mbid" -> MatchEvidence.RECORDING_MBID
                "work_mbid" -> MatchEvidence.WORK_MBID
                "fingerprint" -> MatchEvidence.FINGERPRINT
                "isrc" -> MatchEvidence.ISRC
                else -> null
            }
        }

        val recordingConfidenceCeiling = recordingConfidenceCeiling(
            reference = reference,
            candidate = candidate,
            hasStrongRecordingIdentity = directIdentity || isrcIdentity
        )
        sameRecordingProbability = min(sameRecordingProbability, recordingConfidenceCeiling)

        val sameWorkIdentity = "work_mbid" in identifiers
        val explicitVersionRelationship = RecordingMatchHardConflict.VERSION in hardConflicts
        val workConfidenceCeiling = workConfidenceCeiling(
            reference = reference,
            candidate = candidate,
            artistScore = artistScore,
            sameWorkIdentity = sameWorkIdentity,
            explicitVersionRelationship = explicitVersionRelationship
        )
        var sameWorkProbability = sameWorkProbability(
            titleScore = titleScore,
            artistScore = artistScore,
            durationScore = durationScore,
            albumScore = albumScore,
            sameWorkIdentity = sameWorkIdentity,
            explicitVersionRelationship = explicitVersionRelationship,
            workMbidConflict = RecordingMatchHardConflict.WORK_MBID in hardConflicts
        )
        sameWorkProbability = min(sameWorkProbability, workConfidenceCeiling)
        val relationship = relationship(
            sameRecordingProbability = sameRecordingProbability,
            sameWorkProbability = sameWorkProbability,
            titleScore = titleScore,
            artistScore = artistScore,
            sameWorkIdentity = sameWorkIdentity,
            explicitVersionRelationship = explicitVersionRelationship,
            hardConflicts = hardConflicts
        )
        if (includeExplanation) {
            explanation += "same_recording=${formatScore(sameRecordingProbability)}"
            explanation += "same_work=${formatScore(sameWorkProbability)}"
            explanation += "relationship=${relationship.name}"
            explanation += "confidence_ceilings=${formatScore(recordingConfidenceCeiling)}/${formatScore(workConfidenceCeiling)}"
        }

        return MatchEvaluation(
            sameRecordingProbability = sameRecordingProbability.coerceIn(0.0, 1.0),
            sameWorkProbability = sameWorkProbability.coerceIn(0.0, 1.0),
            relationship = relationship,
            recordingConfidenceCeiling = recordingConfidenceCeiling,
            workConfidenceCeiling = workConfidenceCeiling,
            titleScore = titleScore,
            artistScore = artistScore,
            durationScore = durationScore,
            versionScore = versionScore,
            albumScore = albumScore,
            identifierEvidence = identifiers,
            evidence = evidence,
            hardConflicts = hardConflicts,
            versionType = candidateVersion,
            referenceVersionType = referenceVersion,
            durationDeltaMs = durationDeltaMs,
            durationToleranceMs = durationToleranceMs,
            explanation = explanation
        )
    }

    fun dynamicDurationToleranceMs(leftDurationMs: Long?, rightDurationMs: Long?): Long {
        val duration = sequenceOf(leftDurationMs, rightDurationMs)
            .mapNotNull { it?.takeIf { value -> value > 0L } }
            .minOrNull()
            ?: return 8_000L
        return max(2_500L, min(8_000L, (duration * 0.025).toLong()))
    }

    private fun compareIdentifier(
        leftProvider: String,
        leftId: String,
        rightProvider: String,
        rightId: String,
        evidence: String,
        conflict: RecordingMatchHardConflict,
        identifiers: MutableSet<String>,
        hardConflicts: MutableSet<RecordingMatchHardConflict>
    ) {
        if (leftProvider.isBlank() || rightProvider.isBlank() || !leftProvider.equals(rightProvider, true)) return
        compareStrongIdentifier(leftId, rightId, evidence, conflict, identifiers, hardConflicts)
    }

    private fun compareStrongIdentifier(
        left: String,
        right: String,
        evidence: String,
        conflict: RecordingMatchHardConflict,
        identifiers: MutableSet<String>,
        hardConflicts: MutableSet<RecordingMatchHardConflict>
    ) {
        val cleanLeft = normalizedIdentifier(left)
        val cleanRight = normalizedIdentifier(right)
        if (cleanLeft.isBlank() || cleanRight.isBlank()) return
        if (cleanLeft == cleanRight) identifiers += evidence else hardConflicts += conflict
    }

    private fun titleSimilarity(left: RecordingMatchFeatures, right: RecordingMatchFeatures): Double {
        if (left.titleAliases.isEmpty() && right.titleAliases.isEmpty()) {
            return textSimilarity(
                left.normalizedTitle,
                right.normalizedTitle,
                left.titleTokens,
                right.titleTokens,
                left.titleBigrams,
                right.titleBigrams
            )
        }
        val leftTitles = left.titleAliases + left.normalizedTitle
        val rightTitles = right.titleAliases + right.normalizedTitle
        return leftTitles.maxOfOrNull { leftTitle ->
            rightTitles.maxOfOrNull { rightTitle -> textSimilarity(leftTitle, rightTitle) } ?: 0.0
        } ?: 0.0
    }

    private fun textSimilarity(left: String, right: String): Double {
        return textSimilarity(
            left,
            right,
            RecordingMatchFeatureExtractor.tokens(left),
            RecordingMatchFeatureExtractor.tokens(right),
            RecordingMatchFeatureExtractor.bigrams(left),
            RecordingMatchFeatureExtractor.bigrams(right)
        )
    }

    private fun textSimilarity(
        left: String,
        right: String,
        leftTokens: Set<String>,
        rightTokens: Set<String>,
        leftBigrams: Set<String>,
        rightBigrams: Set<String>
    ): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        val tokenScore = dice(leftTokens, rightTokens)
        val bigramScore = dice(leftBigrams, rightBigrams)
        val editScore = normalizedEditSimilarity(left, right)
        return (tokenScore * 0.35 + bigramScore * 0.35 + editScore * 0.30).coerceIn(0.0, 1.0)
    }

    private fun dice(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return 2.0 * left.intersect(right).size / (left.size + right.size).toDouble()
    }

    private fun normalizedEditSimilarity(left: String, right: String): Double {
        val maxLength = max(left.length, right.length)
        if (maxLength == 0) return 1.0
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        left.forEachIndexed { leftIndex, leftChar ->
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = min(
                    min(current[rightIndex] + 1, previous[rightIndex + 1] + 1),
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1
                )
            }
            current.copyInto(previous)
        }
        return 1.0 - previous[right.length].toDouble() / maxLength
    }

    private fun setSimilarity(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.45
        if (left == right) return 1.0
        val intersection = left.intersect(right).size
        if (intersection == 0) return 0.0
        if (intersection == min(left.size, right.size)) return 0.92
        return intersection.toDouble() / left.union(right).size
    }

    private fun durationSimilarity(deltaMs: Long?, toleranceMs: Long): Double {
        if (deltaMs == null) return 0.55
        val ratio = deltaMs.toDouble() / toleranceMs.coerceAtLeast(1L)
        // Treat the dynamic tolerance as one standard deviation of a Gaussian kernel. With every
        // other field exact, a duration inside that tolerance can still reach the 0.92 automatic
        // merge floor; values beyond it decay quickly and never bypass hard version conflicts.
        return exp(-0.5 * ratio * ratio)
    }

    private fun versionSimilarity(left: RecordingVersionType, right: RecordingVersionType): Double = when {
        left == right -> 1.0
        left == RecordingVersionType.UNKNOWN || right == RecordingVersionType.UNKNOWN -> 0.60
        setOf(left, right) == setOf(RecordingVersionType.ORIGINAL, RecordingVersionType.REMASTER) -> 0.95
        setOf(left, right) == setOf(RecordingVersionType.CLEAN, RecordingVersionType.EXPLICIT) -> 0.75
        setOf(left, right) == setOf(RecordingVersionType.ORIGINAL, RecordingVersionType.RADIO_EDIT) -> 0.55
        setOf(left, right) == setOf(RecordingVersionType.ORIGINAL, RecordingVersionType.ACOUSTIC) -> 0.25
        left in RecordingVersionClassifier.conditionalTypes || right in RecordingVersionClassifier.conditionalTypes -> 0.30
        else -> 0.0
    }

    private fun albumSimilarity(left: String, right: String): Double {
        if (left.isBlank() && right.isBlank()) return 0.50
        if (left.isBlank() || right.isBlank()) return 0.40
        if (left == right) return 1.0
        if (left.contains(right) || right.contains(left)) return 0.65
        return 0.20
    }

    private fun durationDelta(left: Long?, right: Long?): Long? {
        val safeLeft = left?.takeIf { it > 0L } ?: return null
        val safeRight = right?.takeIf { it > 0L } ?: return null
        return abs(safeLeft - safeRight)
    }

    private fun hardConflictScoreCeiling(conflicts: Set<RecordingMatchHardConflict>): Double = when {
        RecordingMatchHardConflict.WORK_MBID in conflicts -> 0.30
        RecordingMatchHardConflict.RECORDING_MBID in conflicts ||
            RecordingMatchHardConflict.FINGERPRINT in conflicts ||
            RecordingMatchHardConflict.ISRC in conflicts -> 0.40
        RecordingMatchHardConflict.VERSION in conflicts -> 0.55
        RecordingMatchHardConflict.PRIMARY_ARTIST in conflicts -> 0.60
        else -> 0.65
    }

    private fun recordingConfidenceCeiling(
        reference: RecordingMatchFeatures,
        candidate: RecordingMatchFeatures,
        hasStrongRecordingIdentity: Boolean
    ): Double {
        if (hasStrongRecordingIdentity) return 1.0
        val hasTitle = reference.normalizedTitle.isNotBlank() && candidate.normalizedTitle.isNotBlank()
        val hasArtist = reference.canonicalArtists.any { it.role == ArtistRole.PRIMARY } &&
            candidate.canonicalArtists.any { it.role == ArtistRole.PRIMARY }
        val hasDuration = reference.durationMs != null && candidate.durationMs != null
        return when {
            hasTitle && hasArtist && hasDuration -> 1.0
            hasTitle && hasArtist -> 0.94
            hasTitle && hasDuration -> 0.88
            hasTitle -> 0.75
            else -> 0.40
        }
    }

    private fun workConfidenceCeiling(
        reference: RecordingMatchFeatures,
        candidate: RecordingMatchFeatures,
        artistScore: Double,
        sameWorkIdentity: Boolean,
        explicitVersionRelationship: Boolean
    ): Double {
        if (sameWorkIdentity) return 1.0
        val hasTitle = reference.normalizedTitle.isNotBlank() && candidate.normalizedTitle.isNotBlank()
        val hasArtist = reference.canonicalArtists.any { it.role == ArtistRole.PRIMARY } &&
            candidate.canonicalArtists.any { it.role == ArtistRole.PRIMARY }
        return when {
            hasTitle && hasArtist && artistScore >= 0.92 -> 0.95
            hasTitle && explicitVersionRelationship -> 0.90
            hasTitle -> 0.79
            else -> 0.45
        }
    }

    private fun sameWorkProbability(
        titleScore: Double,
        artistScore: Double,
        durationScore: Double,
        albumScore: Double,
        sameWorkIdentity: Boolean,
        explicitVersionRelationship: Boolean,
        workMbidConflict: Boolean
    ): Double {
        if (workMbidConflict) return 0.0
        if (sameWorkIdentity) return 1.0
        var probability = titleScore * 0.70 +
            artistScore * 0.15 +
            durationScore * 0.05 +
            albumScore * 0.10
        if (explicitVersionRelationship && titleScore >= 0.95) {
            probability = max(probability, 0.86)
        }
        if (titleScore < 0.35) probability = min(probability, 0.30)
        return probability.coerceIn(0.0, 1.0)
    }

    private fun relationship(
        sameRecordingProbability: Double,
        sameWorkProbability: Double,
        titleScore: Double,
        artistScore: Double,
        sameWorkIdentity: Boolean,
        explicitVersionRelationship: Boolean,
        hardConflicts: Set<RecordingMatchHardConflict>
    ): RecordingRelationship = when {
        RecordingMatchHardConflict.WORK_MBID in hardConflicts -> RecordingRelationship.CANNOT_LINK
        hardConflicts.isEmpty() && sameRecordingProbability >= AUTO_MERGE_MINIMUM_SCORE ->
            RecordingRelationship.SAME_RECORDING
        sameWorkProbability >= SAME_WORK_MINIMUM_SCORE &&
            (sameWorkIdentity || titleScore >= 0.90 &&
                (artistScore >= 0.92 || explicitVersionRelationship)) ->
            RecordingRelationship.SAME_WORK_DIFFERENT_VERSION
        hardConflicts.isNotEmpty() -> RecordingRelationship.CANNOT_LINK
        else -> RecordingRelationship.UNKNOWN
    }

    private fun conditionalVersionScoreCeiling(
        left: RecordingVersionType,
        right: RecordingVersionType
    ): Double = when (setOf(left, right)) {
        setOf(RecordingVersionType.CLEAN, RecordingVersionType.EXPLICIT) -> 0.90
        setOf(RecordingVersionType.ORIGINAL, RecordingVersionType.RADIO_EDIT) -> 0.88
        else -> 1.0
    }

    private fun normalizedIdentifier(value: String): String = value
        .uppercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private fun formatScore(value: Double): String = "%.3f".format(java.util.Locale.ROOT, value)

    private const val SAME_WORK_MINIMUM_SCORE = 0.85
}

object RecordingVersionClassifier {
    internal val conditionalTypes = setOf(
        RecordingVersionType.ACOUSTIC,
        RecordingVersionType.RADIO_EDIT,
        RecordingVersionType.EXTENDED,
        RecordingVersionType.CLEAN,
        RecordingVersionType.EXPLICIT
    )

    private val hardTypes = setOf(
        RecordingVersionType.LIVE,
        RecordingVersionType.REMIX,
        RecordingVersionType.COVER,
        RecordingVersionType.ACOUSTIC,
        RecordingVersionType.INSTRUMENTAL,
        RecordingVersionType.KARAOKE,
        RecordingVersionType.DEMO,
        RecordingVersionType.SPED_UP,
        RecordingVersionType.SLOWED,
        RecordingVersionType.ALTERNATE_TAKE
    )

    private val rules = listOf(
        RecordingVersionType.RADIO_EDIT to token("radio edit", "radio version", "电台版", "電台版"),
        RecordingVersionType.EXTENDED to token("extended", "extended mix", "加长版", "加長版"),
        RecordingVersionType.ALTERNATE_TAKE to token("alternate take", "alternate version", "alt take", "take \\d+", "ver\\.?\\s*\\d+", "version\\s+\\d+", "\\d{2,4}\\s*ver\\.?", "別テイク", "另一个版本", "另一版本"),
        RecordingVersionType.INSTRUMENTAL to token("instrumental", "off vocal", "伴奏", "纯音乐", "純音樂", "インスト"),
        RecordingVersionType.KARAOKE to token("karaoke", "卡拉ok", "卡拉 ok", "カラオケ"),
        RecordingVersionType.SPED_UP to token("sped up", "speed up", "加速版"),
        RecordingVersionType.SLOWED to token("slowed", "slowed reverb", "慢速版", "降速版"),
        RecordingVersionType.REMASTER to token("remaster", "remastered", "重制", "重製", "リマスター"),
        RecordingVersionType.ACOUSTIC to token("acoustic", "unplugged", "不插电", "不插電", "アコースティック"),
        RecordingVersionType.EXPLICIT to token("explicit", "脏版", "髒版"),
        RecordingVersionType.CLEAN to token("clean", "clean version", "洁版", "潔版"),
        RecordingVersionType.COVER to token("cover", "covered by", "翻唱", "歌ってみた", "カバー"),
        RecordingVersionType.LIVE to token("live", "live at", "live in", "concert", "现场", "現場", "演唱会", "演唱會", "ライブ"),
        RecordingVersionType.REMIX to token("remix", "rework", "bootleg", "混音", "重混", "リミックス"),
        RecordingVersionType.DEMO to token("demo", "样带", "樣帶", "试听版", "試聽版", "デモ")
    )

    private val bracketed = Regex("""(?:\(|\[|（|【)\s*([^()\[\]（）【】]*)\s*(?:\)|\]|）|】)""")
    private val dashTail = Regex("""\s+[-–—]\s+(.+)$""")
    private val unmarkedVersionSuffix = Regex(
        """(?i)\s+(?:live|remix|rework|cover|acoustic|instrumental|karaoke|demo|remaster(?:ed)?|sped\s+up|slowed|现场|現場|混音|翻唱|伴奏|纯音乐|純音樂|重制|重製)$"""
    )

    fun classify(title: String, album: String = ""): RecordingVersionType {
        val evidence = buildList {
            bracketed.findAll(title).forEach { add(it.groupValues[1]) }
            dashTail.find(title)?.groupValues?.getOrNull(1)?.let(::add)
            if (unmarkedVersionSuffix.containsMatchIn(title)) {
                add(title)
            }
            if (album.isNotBlank()) add(album)
        }
        rules.forEach { (type, pattern) ->
            if (evidence.any(pattern::containsMatchIn)) return type
        }
        return if (StreamingTrackMatchPolicy.canonicalTitle(title).isBlank()) {
            RecordingVersionType.UNKNOWN
        } else {
            RecordingVersionType.ORIGINAL
        }
    }

    fun coreTitle(title: String): String {
        var value = bracketed.replace(title) { match ->
            val qualifier = match.groupValues[1]
            if (isVersionQualifier(qualifier)) {
                " "
            } else {
                match.value
            }
        }
        dashTail.find(value)?.let { tail ->
            if (isVersionQualifier(tail.groupValues[1])) {
                value = value.substring(0, tail.range.first)
            }
        }
        return StreamingTrackMatchPolicy.canonicalTitle(value)
    }

    private fun isVersionQualifier(value: String): Boolean =
        rules.any { (_, pattern) -> pattern.containsMatchIn(value) }

    fun hardConflict(
        leftTitle: String,
        leftAlbum: String,
        rightTitle: String,
        rightAlbum: String,
        leftVersion: RecordingVersionType = classify(leftTitle, leftAlbum),
        rightVersion: RecordingVersionType = classify(rightTitle, rightAlbum)
    ): Boolean {
        return hardConflict(
            leftVersion,
            rightVersion,
            versionSignature(leftTitle, leftAlbum, leftVersion),
            versionSignature(rightTitle, rightAlbum, rightVersion)
        )
    }

    fun hardConflict(
        left: RecordingVersionType,
        right: RecordingVersionType,
        leftSignature: String = "",
        rightSignature: String = ""
    ): Boolean {
        if (left == RecordingVersionType.UNKNOWN || right == RecordingVersionType.UNKNOWN) return false
        if (setOf(left, right) == setOf(RecordingVersionType.ORIGINAL, RecordingVersionType.REMASTER)) return false
        if (left != right && (left in hardTypes || right in hardTypes)) return true
        if (left == right && left in hardTypes) {
            return leftSignature.isNotBlank() && rightSignature.isNotBlank() && leftSignature != rightSignature
        }
        return false
    }

    internal fun versionSignature(
        title: String,
        album: String,
        explicitType: RecordingVersionType? = null
    ): String {
        val type = explicitType ?: classify(title, album)
        return buildList {
        bracketed.findAll(title)
            .map { it.groupValues[1] }
            .filter { classify(it) == type }
            .forEach(::add)
        dashTail.find(title)?.groupValues?.getOrNull(1)?.takeIf { classify(it) == type }?.let(::add)
        if (isEmpty() && classify(album) == type) add(album)
        }.joinToString("|") { StreamingTrackMatchPolicy.canonicalTitle(it) }
    }

    private fun token(vararg expressions: String): Regex = Regex(
        expressions.joinToString("|") { expression ->
            val body = if ("\\" in expression) expression else Regex.escape(expression)
            "(?<![\\p{L}\\p{N}])(?:$body)(?![\\p{L}\\p{N}])"
        },
        RegexOption.IGNORE_CASE
    )
}
