package app.yukine.data

import app.yukine.data.room.ArtistAliasEntity
import app.yukine.data.room.ArtistSourceMappingEntity
import app.yukine.data.room.CanonicalArtistEntity
import app.yukine.data.room.CanonicalRecordingEntity
import app.yukine.data.room.IdentityCandidateEntity
import app.yukine.data.room.IdentityResolutionJobEntity
import app.yukine.data.room.RecordingArtistCreditEntity
import app.yukine.data.room.RecordingIdentifierEntity
import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.identity.ArtistAlias
import app.yukine.identity.ArtistAliasType
import app.yukine.identity.ArtistCredit
import app.yukine.identity.ArtistCreditRole
import app.yukine.identity.ArtistSourceMapping
import app.yukine.identity.ArtistType
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityJobStatus
import app.yukine.identity.IdentityMatchStatus
import app.yukine.identity.IdentityResolutionJob
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.RecordingIdentifier
import app.yukine.identity.TrackSourceMapping

internal fun CanonicalRecordingEntity.toModel(): CanonicalRecording = CanonicalRecording(
    recordingId = requireNotNull(id),
    canonicalId = canonicalUuid,
    musicBrainzRecordingId = musicBrainzRecordingId,
    musicBrainzWorkId = musicBrainzWorkId,
    title = title,
    primaryArtistDisplay = primaryArtistDisplay,
    durationMs = durationMs,
    isrc = isrc,
    acoustId = acoustId,
    matchStatus = enumValueOr(matchStatus, IdentityMatchStatus.UNRESOLVED),
    confidence = confidence,
    metadataSource = metadataSource,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun TrackSourceMappingEntity.toModel(recording: CanonicalRecordingEntity): TrackSourceMapping =
    TrackSourceMapping(
        sourceId = requireNotNull(sourceId),
        recordingId = recordingId,
        canonicalId = recording.canonicalUuid,
        provider = provider,
        providerTrackId = providerTrackId,
        localTrackId = localTrackId,
        dataPath = dataPath,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        quality = quality,
        playable = playable,
        matchStatus = enumValueOr(matchStatus, IdentityMatchStatus.UNRESOLVED),
        confidence = confidence,
        lastSuccessfulAt = lastSuccessfulAt,
        lastVerifiedAt = lastVerifiedAt,
        legacyLocalKey = legacyLocalKey,
        codec = codec,
        bitrateKbps = bitrateKbps,
        lastFailureAt = lastFailureAt,
        failureReason = failureReason,
        failureCount = failureCount
    )

internal fun RecordingIdentifierEntity.toModel(recording: CanonicalRecordingEntity): RecordingIdentifier =
    RecordingIdentifier(
        recordingId = recordingId,
        canonicalId = recording.canonicalUuid,
        identifierType = identifierType,
        namespace = namespace,
        identifierValue = identifierValue,
        source = source,
        confidence = confidence,
        verifiedAt = verifiedAt
    )

internal fun CanonicalArtistEntity.toModel(): CanonicalArtist = CanonicalArtist(
    artistKey = requireNotNull(id),
    artistId = artistUuid,
    displayName = displayName,
    sortName = sortName,
    artistType = enumValueOr(artistType, ArtistType.UNKNOWN),
    countryCode = countryCode,
    musicBrainzArtistId = musicBrainzArtistId,
    avatarUrl = avatarUrl,
    matchStatus = enumValueOr(matchStatus, IdentityMatchStatus.UNRESOLVED),
    confidence = confidence,
    metadataSource = metadataSource,
    createdAt = createdAt,
    updatedAt = updatedAt,
    description = description
)

internal fun ArtistAliasEntity.toModel(artist: CanonicalArtistEntity): ArtistAlias = ArtistAlias(
    artistKey = artistId,
    artistId = artist.artistUuid,
    alias = alias,
    normalizedAlias = normalizedAlias,
    locale = locale,
    script = script,
    aliasType = enumValueOr(aliasType, ArtistAliasType.ALIAS),
    source = source,
    confidence = confidence,
    verifiedAt = verifiedAt
)

internal fun ArtistSourceMappingEntity.toModel(artist: CanonicalArtistEntity): ArtistSourceMapping =
    ArtistSourceMapping(
        mappingId = requireNotNull(mappingId),
        artistKey = artistId,
        artistId = artist.artistUuid,
        provider = provider,
        providerArtistId = providerArtistId,
        displayName = displayName,
        matchStatus = enumValueOr(status, IdentityMatchStatus.UNRESOLVED),
        confidence = confidence,
        lastVerifiedAt = lastVerifiedAt
    )

internal fun RecordingArtistCreditEntity.toModel(
    recording: CanonicalRecordingEntity,
    artist: CanonicalArtistEntity
): ArtistCredit = ArtistCredit(
    recordingId = recordingId,
    canonicalId = recording.canonicalUuid,
    artistKey = artistId,
    artistId = artist.artistUuid,
    role = enumValueOr(role, ArtistCreditRole.UNKNOWN),
    position = position,
    creditedName = creditedName,
    joinPhrase = joinPhrase,
    confidence = confidence
)

internal fun IdentityCandidateEntity.toModel(): IdentityCandidate = IdentityCandidate(
    candidateId = candidateId,
    targetType = enumValueOr(targetType, IdentityTargetType.RECORDING),
    targetId = targetId,
    provider = provider,
    providerItemId = providerItemId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    isrc = isrc,
    variantType = variantType,
    score = score,
    status = enumValueOr(status, IdentityCandidateStatus.PENDING),
    evidenceJson = evidenceJson,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun IdentityResolutionJobEntity.toModel(): IdentityResolutionJob = IdentityResolutionJob(
    jobId = jobId,
    targetType = enumValueOr(targetType, IdentityTargetType.RECORDING),
    targetId = targetId,
    priority = priority,
    reason = reason,
    attemptCount = attemptCount,
    nextAttemptAt = nextAttemptAt,
    lastError = lastError,
    status = enumValueOr(status, IdentityJobStatus.PENDING),
    createdAt = createdAt,
    updatedAt = updatedAt
)

private inline fun <reified T : Enum<T>> enumValueOr(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
