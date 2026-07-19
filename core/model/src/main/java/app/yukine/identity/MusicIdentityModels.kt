package app.yukine.identity

/** Stable application-owned identity for one concrete recording. */
data class CanonicalRecording(
    val recordingId: Long,
    val canonicalId: String,
    val musicBrainzRecordingId: String = "",
    val musicBrainzWorkId: String = "",
    val title: String = "",
    val primaryArtistDisplay: String = "",
    val durationMs: Long = 0L,
    val isrc: String = "",
    val acoustId: String = "",
    val matchStatus: IdentityMatchStatus = IdentityMatchStatus.UNRESOLVED,
    val confidence: Double = 0.0,
    val metadataSource: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** One physical or online source that can provide audio for a canonical recording. */
data class TrackSourceMapping(
    val sourceId: Long,
    val recordingId: Long,
    val canonicalId: String,
    val provider: String,
    val providerTrackId: String,
    val localTrackId: Long? = null,
    val dataPath: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val quality: String = "",
    val playable: Boolean = true,
    val matchStatus: IdentityMatchStatus = IdentityMatchStatus.UNRESOLVED,
    val confidence: Double = 0.0,
    val lastSuccessfulAt: Long = 0L,
    val lastVerifiedAt: Long = 0L,
    val legacyLocalKey: String = "",
    val codec: String = "",
    val bitrateKbps: Int = 0,
    val lastFailureAt: Long = 0L,
    val failureReason: String = "",
    val failureCount: Int = 0,
    val albumArtist: String = "",
    val composer: String = "",
    val releaseType: String = "",
    val year: Int = 0,
    val albumId: Long? = null
)

/** A globally meaningful identifier such as a Recording MBID, ISRC, or AcoustID. */
data class RecordingIdentifier(
    val recordingId: Long,
    val canonicalId: String,
    val identifierType: String,
    val namespace: String = "",
    val identifierValue: String,
    val source: String = "",
    val confidence: Double = 0.0,
    val verifiedAt: Long = 0L
)

enum class IdentityMatchStatus {
    CONFIRMED,
    CANDIDATE,
    REJECTED,
    UNVERIFIED_LEGACY,
    UNRESOLVED
}

data class CanonicalArtist(
    val artistKey: Long,
    val artistId: String,
    val displayName: String,
    val sortName: String = "",
    val artistType: ArtistType = ArtistType.UNKNOWN,
    val countryCode: String = "",
    val musicBrainzArtistId: String = "",
    val avatarUrl: String = "",
    val matchStatus: IdentityMatchStatus = IdentityMatchStatus.UNRESOLVED,
    val confidence: Double = 0.0,
    val metadataSource: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val description: String = ""
)

data class ArtistAlias(
    val artistKey: Long,
    val artistId: String,
    val alias: String,
    val normalizedAlias: String,
    val locale: String = "",
    val script: String = "",
    val aliasType: ArtistAliasType = ArtistAliasType.ALIAS,
    val source: String = "",
    val confidence: Double = 0.0,
    val verifiedAt: Long = 0L
)

data class ArtistSourceMapping(
    val mappingId: Long,
    val artistKey: Long,
    val artistId: String,
    val provider: String,
    val providerArtistId: String,
    val displayName: String = "",
    val matchStatus: IdentityMatchStatus = IdentityMatchStatus.UNRESOLVED,
    val confidence: Double = 0.0,
    val lastVerifiedAt: Long = 0L
)

data class ArtistCredit(
    val recordingId: Long,
    val canonicalId: String,
    val artistKey: Long,
    val artistId: String,
    val role: ArtistCreditRole,
    val position: Int,
    val creditedName: String,
    val joinPhrase: String = "",
    val confidence: Double = 0.0
)

data class TrackArtistIdentity(
    val localTrackId: Long,
    val artistKey: Long,
    val artistId: String,
    val displayName: String,
    val creditedName: String,
    val avatarUrl: String = "",
    val role: ArtistCreditRole,
    val position: Int
)

enum class ArtistType { PERSON, GROUP, VIRTUAL, CHARACTER, ORCHESTRA, CHOIR, UNKNOWN }

enum class ArtistAliasType { PRIMARY, ALIAS, TRANSLATION, ROMANIZATION, FORMER_NAME, SEARCH_HINT }

enum class ArtistCreditRole {
    PRIMARY,
    FEATURED,
    PERFORMER,
    COMPOSER,
    LYRICIST,
    ARRANGER,
    REMIXER,
    COVER_ARTIST,
    CHARACTER_VOICE,
    UNKNOWN
}

data class CanonicalAlbum(
    val albumKey: Long,
    val albumId: String,
    val displayName: String,
    val sortName: String = "",
    val albumArtistKey: Long? = null,
    val albumArtistDisplay: String = "",
    val musicBrainzReleaseGroupId: String = "",
    val musicBrainzReleaseId: String = "",
    val releaseType: String = "",
    val year: Int = 0,
    val matchStatus: IdentityMatchStatus = IdentityMatchStatus.UNRESOLVED,
    val confidence: Double = 0.0,
    val metadataSource: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class AlbumAlias(
    val albumKey: Long,
    val alias: String,
    val normalizedAlias: String,
    val locale: String = "",
    val aliasType: String = "ALIAS",
    val source: String = "",
    val confidence: Double = 0.0,
    val verifiedAt: Long = 0L
)

data class AnonymousAlbumCandidate(
    val provider: String,
    val providerAlbumId: String,
    val title: String,
    val aliases: Set<String> = emptySet(),
    val artist: String = "",
    val musicBrainzReleaseGroupId: String = "",
    val musicBrainzReleaseId: String = "",
    val releaseType: String = "",
    val year: Int = 0,
    val providerScore: Double = 0.0
)

data class AnonymousAlbumProviderResult(
    val candidates: List<AnonymousAlbumCandidate>,
    val endpoint: String = "",
    val fromCache: Boolean = false,
    val staleCache: Boolean = false,
    val allEndpointsFailed: Boolean = false
)

enum class IdentityTargetType { RECORDING, ARTIST, ALBUM }

enum class IdentityCandidateStatus {
    PENDING,
    AUTO_CONFIRMED,
    USER_CONFIRMED,
    ALTERNATE_VERSION,
    REJECTED,
    EXPIRED
}

data class IdentityCandidate(
    val candidateId: String,
    val targetType: IdentityTargetType,
    val targetId: Long,
    val provider: String,
    val providerItemId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val isrc: String = "",
    val variantType: String = "UNKNOWN",
    val score: Double = 0.0,
    val status: IdentityCandidateStatus = IdentityCandidateStatus.PENDING,
    val evidenceJson: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class IdentityJobStatus { PENDING, RUNNING, RETRY, SUCCEEDED, FAILED }

data class IdentityResolutionJob(
    val jobId: String,
    val targetType: IdentityTargetType,
    val targetId: Long,
    val priority: Int = 0,
    val reason: String = "NEW_TRACK",
    val attemptCount: Int = 0,
    val nextAttemptAt: Long = 0L,
    val lastError: String = "",
    val status: IdentityJobStatus = IdentityJobStatus.PENDING,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class ProviderCacheFreshness { FRESH, STALE }

data class ProviderCachedResponse(
    val provider: String,
    val endpoint: String,
    val requestHash: String,
    val responseJson: String,
    val createdAt: Long,
    val expiresAt: Long,
    val freshness: ProviderCacheFreshness
)

data class ProviderEndpointHealth(
    val provider: String,
    val endpoint: String,
    val failureCount: Int = 0,
    val circuitOpenUntil: Long = 0L,
    val lastError: String = ""
) {
    fun canRequest(now: Long): Boolean = circuitOpenUntil <= now
}

data class ProviderArtistCandidate(
    val providerArtistId: String,
    val displayName: String,
    val sortName: String = ""
)

data class AnonymousRecordingCandidate(
    val provider: String,
    val providerItemId: String,
    val title: String,
    val artists: List<ProviderArtistCandidate> = emptyList(),
    val album: String = "",
    val durationMs: Long = 0L,
    val isrc: String = "",
    val recordingMbid: String = "",
    val workMbid: String = "",
    val acoustId: String = "",
    val coverUrl: String = "",
    /** True only when the gateway returned this candidate for the uploaded Chromaprint. */
    val fingerprintVerified: Boolean = false,
    val variantType: RecordingVariantType = RecordingVariantType.UNKNOWN,
    val providerScore: Double = 0.0
)

data class AnonymousProviderResult(
    val candidates: List<AnonymousRecordingCandidate>,
    val endpoint: String = "",
    val fromCache: Boolean = false,
    val staleCache: Boolean = false,
    val allEndpointsFailed: Boolean = false
)

data class AnonymousArtistCandidate(
    val provider: String,
    val providerItemId: String,
    val displayName: String,
    val sortName: String = "",
    val aliases: Set<String> = emptySet(),
    val countryCode: String = "",
    val artistType: ArtistType = ArtistType.UNKNOWN,
    val artistMbid: String = "",
    val avatarUrl: String = "",
    val providerScore: Double = 0.0,
    val description: String = ""
)

data class AnonymousArtistProviderResult(
    val candidates: List<AnonymousArtistCandidate>,
    val endpoint: String = "",
    val fromCache: Boolean = false,
    val staleCache: Boolean = false,
    val allEndpointsFailed: Boolean = false
)
