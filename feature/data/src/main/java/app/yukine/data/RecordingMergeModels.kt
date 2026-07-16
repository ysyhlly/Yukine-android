package app.yukine.data

import app.yukine.data.room.CanonicalRecordingEntity
import app.yukine.data.room.RecordingArtistCreditEntity
import app.yukine.data.room.RecordingVariantEntity
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.RecordingIdentifier
import app.yukine.identity.RecordingVariantRecognizer
import app.yukine.identity.RecordingVariantType
import app.yukine.identity.TrackSourceMapping
import java.util.Locale
import kotlin.math.abs

data class RecordingMergeSearchResult(
    val recordingId: Long,
    val canonicalId: String,
    val title: String,
    val primaryArtistDisplay: String,
    val durationMs: Long,
    val sourceCount: Int,
    val variantTypes: List<String>
)

data class RecordingMergeSummary(
    val recording: CanonicalRecording,
    val sources: List<TrackSourceMapping>,
    val identifiers: List<RecordingIdentifier>,
    val variants: List<RecordingMatchVariant>
)

data class RecordingMergeImpact(
    val favoriteCount: Int,
    val playlistItemCount: Int,
    val playHistoryCount: Int,
    val playEventCount: Int,
    val queueItemCount: Int,
    val sourceCount: Int
)

enum class RecordingMergeWarningCode {
    RECORDING_MBID_CONFLICT,
    ACOUSTID_CONFLICT,
    PRIMARY_ARTIST_CONFLICT,
    ISRC_CONFLICT,
    VARIANT_CONFLICT,
    TITLE_CONFLICT,
    DURATION_CONFLICT
}

data class RecordingMergeWarning(
    val code: RecordingMergeWarningCode,
    val blocking: Boolean
)

data class RecordingMergePreview(
    val source: RecordingMergeSummary,
    val target: RecordingMergeSummary,
    val impact: RecordingMergeImpact,
    val warnings: List<RecordingMergeWarning>
) {
    val blocked: Boolean
        get() = warnings.any(RecordingMergeWarning::blocking)
}

enum class RecordingSplitDestination {
    ORIGINAL,
    NEW_RECORDING
}

enum class RecordingSplitReference {
    FAVORITE,
    PLAYLISTS,
    QUEUE
}

data class RecordingSplitOptions(
    val favoriteDestination: RecordingSplitDestination = RecordingSplitDestination.ORIGINAL,
    val playlistDestination: RecordingSplitDestination = RecordingSplitDestination.ORIGINAL,
    val queueDestination: RecordingSplitDestination = RecordingSplitDestination.ORIGINAL
)

data class RecordingSplitPreview(
    val original: RecordingMergeSummary,
    val selectedSources: List<TrackSourceMapping>,
    val remainingSourceCount: Int,
    val impact: RecordingMergeImpact
)

internal object RecordingMergePolicy {
    private val protectedVariants = setOf(
        RecordingVariantType.ORIGINAL,
        RecordingVariantType.LIVE,
        RecordingVariantType.REMIX,
        RecordingVariantType.COVER,
        RecordingVariantType.ACOUSTIC,
        RecordingVariantType.INSTRUMENTAL,
        RecordingVariantType.KARAOKE
    )

    fun warnings(
        source: CanonicalRecordingEntity,
        target: CanonicalRecordingEntity,
        sourceCredits: List<RecordingArtistCreditEntity>,
        targetCredits: List<RecordingArtistCreditEntity>,
        sourceVariants: List<RecordingVariantEntity>,
        targetVariants: List<RecordingVariantEntity>
    ): List<RecordingMergeWarning> = buildList {
        if (differentStrongValue(source.musicBrainzRecordingId, target.musicBrainzRecordingId)) {
            add(RecordingMergeWarning(RecordingMergeWarningCode.RECORDING_MBID_CONFLICT, true))
        }
        if (differentStrongValue(source.acoustId, target.acoustId)) {
            add(RecordingMergeWarning(RecordingMergeWarningCode.ACOUSTID_CONFLICT, true))
        }

        val sourceArtists = primaryArtists(sourceCredits)
        val targetArtists = primaryArtists(targetCredits)
        if (sourceArtists.isNotEmpty() && targetArtists.isNotEmpty() && sourceArtists.none(targetArtists::contains)) {
            add(RecordingMergeWarning(RecordingMergeWarningCode.PRIMARY_ARTIST_CONFLICT, true))
        }

        val sourceIsrc = normalizeIsrc(source.isrc)
        val targetIsrc = normalizeIsrc(target.isrc)
        if (sourceIsrc.isNotBlank() && targetIsrc.isNotBlank() && sourceIsrc != targetIsrc) {
            add(RecordingMergeWarning(RecordingMergeWarningCode.ISRC_CONFLICT, false))
        }

        val leftVariants = effectiveVariants(source, sourceVariants)
        val rightVariants = effectiveVariants(target, targetVariants)
        if (leftVariants.isNotEmpty() && rightVariants.isNotEmpty() &&
            leftVariants.none(rightVariants::contains) &&
            (leftVariants + rightVariants).any(protectedVariants::contains)
        ) {
            add(RecordingMergeWarning(RecordingMergeWarningCode.VARIANT_CONFLICT, true))
        }

        val sourceTitle = normalizeText(source.title)
        val targetTitle = normalizeText(target.title)
        if (sourceTitle.isNotBlank() && targetTitle.isNotBlank() && sourceTitle != targetTitle) {
            add(RecordingMergeWarning(RecordingMergeWarningCode.TITLE_CONFLICT, false))
        }

        if (source.durationMs > 0L && target.durationMs > 0L) {
            val allowedDifference = maxOf(10_000L, maxOf(source.durationMs, target.durationMs) / 10L)
            if (abs(source.durationMs - target.durationMs) > allowedDifference) {
                add(RecordingMergeWarning(RecordingMergeWarningCode.DURATION_CONFLICT, false))
            }
        }
    }

    fun requireMergeAllowed(
        source: CanonicalRecordingEntity,
        target: CanonicalRecordingEntity,
        sourceCredits: List<RecordingArtistCreditEntity>,
        targetCredits: List<RecordingArtistCreditEntity>,
        sourceVariants: List<RecordingVariantEntity>,
        targetVariants: List<RecordingVariantEntity>
    ) {
        val blocking = warnings(
            source,
            target,
            sourceCredits,
            targetCredits,
            sourceVariants,
            targetVariants
        ).filter(RecordingMergeWarning::blocking)
        require(blocking.isEmpty()) {
            "Recording merge blocked: ${blocking.joinToString { it.code.name }}"
        }
    }

    private fun primaryArtists(credits: List<RecordingArtistCreditEntity>): Set<Long> = credits
        .asSequence()
        .filter { it.role == "PRIMARY" || it.role == "UNKNOWN" }
        .mapTo(linkedSetOf(), RecordingArtistCreditEntity::artistId)

    private fun effectiveVariants(
        recording: CanonicalRecordingEntity,
        variants: List<RecordingVariantEntity>
    ): Set<RecordingVariantType> {
        val explicit = variants.mapNotNullTo(linkedSetOf()) {
            runCatching { RecordingVariantType.valueOf(it.variantType.uppercase(Locale.ROOT)) }.getOrNull()
        }.filterTo(linkedSetOf()) { it != RecordingVariantType.UNKNOWN }
        if (explicit.isNotEmpty()) return explicit
        return setOf(RecordingVariantRecognizer.recognize(recording.title))
            .filterTo(linkedSetOf()) { it != RecordingVariantType.UNKNOWN }
    }

    private fun differentStrongValue(first: String, second: String): Boolean =
        first.isNotBlank() && second.isNotBlank() && !first.equals(second, ignoreCase = true)

    private fun normalizeIsrc(value: String): String =
        value.filter(Char::isLetterOrDigit).uppercase(Locale.ROOT)

    private fun normalizeText(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)
}
