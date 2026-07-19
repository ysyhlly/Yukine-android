package app.yukine.streaming

enum class ArtistRole {
    PRIMARY,
    FEATURED,
    PERFORMER,
    CHARACTER,
    VOICE_ACTOR,
    COMPOSER
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
            source == VersionEvidenceSource.PROVIDER
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
    val recordingMbid: String?,
    val workMbid: String?,
    val fingerprint: FingerprintFeature?,
    val provider: String?,
    val providerTrackId: String?,
    val providerTrackIdConfirmed: Boolean
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
            isrc = StreamingTrackMatchPolicy.normalizeIsrc(reference.isrc).takeIf(String::isNotBlank),
            recordingMbid = normalizeIdentifier(reference.recordingMbid).takeIf(String::isNotBlank),
            workMbid = normalizeIdentifier(reference.workMbid).takeIf(String::isNotBlank),
            fingerprint = fingerprint,
            provider = reference.provider.trim().takeIf(String::isNotBlank),
            providerTrackId = normalizeIdentifier(reference.providerTrackId).takeIf(String::isNotBlank),
            providerTrackIdConfirmed = reference.providerTrackIdConfirmed
        )
    }

    internal fun tokens(value: String): Set<String> = value.split(' ')
        .filter(String::isNotBlank)
        .toSortedSet()

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
