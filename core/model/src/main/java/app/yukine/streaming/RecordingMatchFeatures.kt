package app.yukine.streaming

enum class ArtistRole {
    PRIMARY,
    FEATURED,
    PERFORMER,
    CHARACTER,
    VOICE_ACTOR,
    COMPOSER
}

enum class WorkCreditRole {
    COMPOSER,
    SONGWRITER,
    LYRICIST,
    PUBLISHER
}

data class WorkCreditFeature(
    val canonicalId: String? = null,
    val canonicalName: String,
    val aliases: Set<String> = emptySet(),
    val role: WorkCreditRole
)

enum class EvidenceTrustTier(val weight: Double) {
    VERIFIED_ID(1.0),
    DIRECT_CONFIRMED(0.9),
    PARSED_TAG(0.7),
    LEGACY(0.5),
    UNRESOLVED(0.2);

    companion object {
        @JvmStatic
        fun closest(value: Double): EvidenceTrustTier =
            entries.minBy { kotlin.math.abs(it.weight - value) }
    }
}

data class EvidenceTrustProfile(
    val title: EvidenceTrustTier = EvidenceTrustTier.PARSED_TAG,
    val artist: EvidenceTrustTier = EvidenceTrustTier.PARSED_TAG,
    val version: EvidenceTrustTier = EvidenceTrustTier.PARSED_TAG,
    val identifier: EvidenceTrustTier = EvidenceTrustTier.UNRESOLVED,
    val workCredit: EvidenceTrustTier = EvidenceTrustTier.UNRESOLVED
) {
    val aggregateWeight: Double
        get() = listOf(title.weight, artist.weight, version.weight, identifier.weight)
            .average()
}

data class ArtistFeature(
    val canonicalId: String? = null,
    val canonicalName: String,
    val aliases: Set<String> = emptySet(),
    val role: ArtistRole = ArtistRole.PRIMARY
)

data class FingerprintFeature(
    val algorithm: String,
    val value: String,
    val algorithmVersion: Int = 1
)

enum class VersionEvidenceSource {
    TITLE,
    PROVIDER,
    ALBUM,
    NONE
}

data class RecordingVersionEvidence(
    val type: RecordingVersionType,
    val signature: String,
    val source: VersionEvidenceSource,
    val confidence: Double
) {
    val strongForConflict: Boolean
        get() = source == VersionEvidenceSource.TITLE ||
            source == VersionEvidenceSource.PROVIDER ||
            (type == RecordingVersionType.ORIGINAL && confidence >= 0.80)
}

/** Immutable, Android-free feature snapshot used by every recording matching policy. */
data class RecordingMatchFeatures(
    val normalizedTitle: String,
    val titleTokens: Set<String>,
    val titleBigrams: Set<String>,
    val titleAliases: Set<String>,
    val canonicalArtists: List<ArtistFeature>,
    val durationMs: Long?,
    val versionType: RecordingVersionType,
    val versionSignature: String,
    val versionEvidence: RecordingVersionEvidence,
    val albumKey: String?,
    val canonicalWorkId: String?,
    val canonicalWorkConfirmed: Boolean,
    val canonicalAlbumId: String?,
    val canonicalAlbumConfirmed: Boolean,
    val isrc: String?,
    val isrcs: Set<String> = emptySet(),
    val recordingMbid: String?,
    val workMbid: String?,
    val workIdentifiers: Set<String> = emptySet(),
    val workCredits: List<WorkCreditFeature> = emptyList(),
    val fingerprint: FingerprintFeature?,
    val provider: String?,
    val providerTrackId: String?,
    val providerTrackIdConfirmed: Boolean,
    val trustProfile: EvidenceTrustProfile = EvidenceTrustProfile()
)

object RecordingMatchFeatureExtractor {
    fun extract(reference: StreamingTrackMatchPolicy.Reference): RecordingMatchFeatures {
        val normalizedTitle = RecordingVersionClassifier.coreTitle(reference.title)
        val versionEvidence = RecordingVersionClassifier.extractEvidence(
            title = reference.title,
            album = reference.album.orEmpty(),
            explicitType = reference.versionType
        )
        val versionType = versionEvidence.type
        val aliases = reference.titleAliases.asSequence()
            .map(RecordingVersionClassifier::coreTitle)
            .filter(String::isNotBlank)
            .toSortedSet()
        val primaryNames = StreamingTrackMatchPolicy.canonicalArtistNames(
            reference.artistNames + reference.artist
        )
        val artistIds = reference.primaryArtistIds.toList().sorted()
        val primaryArtists = primaryNames.mapIndexed { index, name ->
            ArtistFeature(
                canonicalId = artistIds.getOrNull(index).takeIf { artistIds.size == primaryNames.size },
                canonicalName = name,
                role = ArtistRole.PRIMARY
            )
        }.ifEmpty {
            artistIds.map { id -> ArtistFeature(canonicalId = id, canonicalName = "", role = ArtistRole.PRIMARY) }
        }
        val secondaryArtists = buildList {
            addAll(reference.featuredArtistNames.toArtistFeatures(ArtistRole.FEATURED))
            addAll(reference.characterArtistNames.toArtistFeatures(ArtistRole.CHARACTER))
            addAll(reference.voiceActorNames.toArtistFeatures(ArtistRole.VOICE_ACTOR))
        }
        val fingerprint = reference.fingerprint.trim().takeIf(String::isNotBlank)?.let { value ->
            FingerprintFeature(algorithm = "acoustid", value = value)
        }
        val normalizedIsrcs = (reference.isrcs + reference.isrc.orEmpty())
            .asSequence()
            .map(StreamingTrackMatchPolicy::normalizeIsrc)
            .filter(String::isNotBlank)
            .toSortedSet()
        val normalizedWorkIdentifiers = (
            reference.workIdentifiers.asSequence() +
                sequenceOf(reference.workMbid)
                    .filter(String::isNotBlank)
                    .map { value ->
                        listOf("MUSICBRAINZ_WORK_ID", "", value)
                            .joinToString(WORK_IDENTIFIER_SEPARATOR.toString())
                    }
            )
            .map(::normalizeWorkIdentifier)
            .filter(String::isNotBlank)
            .toSortedSet()
        return RecordingMatchFeatures(
            normalizedTitle = normalizedTitle,
            titleTokens = tokens(normalizedTitle),
            titleBigrams = bigrams(normalizedTitle),
            titleAliases = aliases,
            canonicalArtists = primaryArtists + secondaryArtists,
            durationMs = reference.durationMs?.takeIf { it > 0L },
            versionType = versionType,
            versionSignature = versionEvidence.signature,
            versionEvidence = versionEvidence,
            albumKey = reference.album.orEmpty()
                .let(StreamingTrackMatchPolicy::canonicalAlbum)
                .takeIf(String::isNotBlank),
            canonicalWorkId = normalizeIdentifier(reference.canonicalWorkId).takeIf(String::isNotBlank),
            canonicalWorkConfirmed = reference.canonicalWorkConfirmed,
            canonicalAlbumId = normalizeIdentifier(reference.canonicalAlbumId).takeIf(String::isNotBlank),
            canonicalAlbumConfirmed = reference.canonicalAlbumConfirmed,
            isrc = normalizedIsrcs.firstOrNull(),
            isrcs = normalizedIsrcs,
            recordingMbid = normalizeIdentifier(reference.recordingMbid).takeIf(String::isNotBlank),
            workMbid = normalizeIdentifier(reference.workMbid).takeIf(String::isNotBlank),
            workIdentifiers = normalizedWorkIdentifiers,
            workCredits = reference.workCredits,
            fingerprint = fingerprint,
            provider = reference.provider.trim().takeIf(String::isNotBlank),
            providerTrackId = normalizeIdentifier(reference.providerTrackId).takeIf(String::isNotBlank),
            providerTrackIdConfirmed = reference.providerTrackIdConfirmed,
            trustProfile = reference.trustProfile
        )
    }

    internal fun tokens(value: String): Set<String> = value.split(' ')
        .filter(String::isNotBlank)
        .toSortedSet()

    private fun normalizeWorkIdentifier(value: String): String {
        val parts = value.split(WORK_IDENTIFIER_SEPARATOR, limit = 3)
        val type: String
        val namespace: String
        val identifier: String
        if (parts.size == 3) {
            type = normalizeIdentifier(parts[0])
            namespace = normalizeIdentifier(parts[1])
            identifier = normalizeIdentifier(parts[2])
        } else {
            type = "unspecified"
            namespace = ""
            identifier = normalizeIdentifier(value)
        }
        if (identifier.isBlank()) return ""
        return listOf(type, namespace, identifier)
            .joinToString(WORK_IDENTIFIER_SEPARATOR.toString())
    }

    private const val WORK_IDENTIFIER_SEPARATOR = '\u001F'

    internal fun bigrams(value: String): Set<String> {
        val compact = value.filterNot(Char::isWhitespace)
        if (compact.length < 2) return compact.takeIf(String::isNotBlank)?.let(::setOf).orEmpty()
        return (0 until compact.lastIndex).mapTo(sortedSetOf()) { index ->
            compact.substring(index, index + 2)
        }
    }

    private fun List<String>.toArtistFeatures(role: ArtistRole): List<ArtistFeature> = asSequence()
        .flatMap { StreamingTrackMatchPolicy.canonicalArtistNames(listOf(it)).asSequence() }
        .distinct()
        .sorted()
        .map { name -> ArtistFeature(canonicalName = name, role = role) }
        .toList()

    private fun normalizeIdentifier(value: String): String = value.trim()
        .uppercase(java.util.Locale.ROOT)
        .filter(Char::isLetterOrDigit)
}
