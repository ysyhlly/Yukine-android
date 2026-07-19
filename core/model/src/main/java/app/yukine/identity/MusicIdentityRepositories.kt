package app.yukine.identity

import app.yukine.model.Track

interface RecordingIdentityRepository {
    fun ensureCanonicalForTrack(track: Track): CanonicalRecording
    fun canonicalForRecording(recordingId: Long): CanonicalRecording?
    fun canonicalForUuid(canonicalId: String): CanonicalRecording?
    fun canonicalForLocalTrack(trackId: Long): CanonicalRecording?
    fun canonicalForProviderTrack(provider: String, providerTrackId: String): CanonicalRecording?
    fun confirmedSources(recordingId: Long): List<TrackSourceMapping>
    fun attachIdentifier(recordingId: Long, identifier: RecordingIdentifier)
    fun attachWorkIdentifier(recordingId: Long, identifier: WorkIdentifier) = Unit
    fun mergeRecordings(sourceRecordingId: Long, targetRecordingId: Long): CanonicalRecording
    fun splitSource(sourceId: Long): CanonicalRecording
    fun pruneOrphans()
}

interface ArtistIdentityRepository {
    fun ensureArtistsForTrack(track: Track): List<ArtistCredit>
    fun artistByKey(artistKey: Long): CanonicalArtist?
    fun artistById(artistId: String): CanonicalArtist?
    fun artistForProvider(provider: String, providerArtistId: String): CanonicalArtist?
    fun aliases(artistKey: Long): List<ArtistAlias>
    fun creditsForRecording(recordingId: Long): List<ArtistCredit>
    fun updateAvatarIfMissing(artistKey: Long, avatarUrl: String, source: String) = Unit
    fun updateDescriptionIfMissing(artistKey: Long, description: String, source: String) = Unit
    fun confirmAlias(artistKey: Long, alias: ArtistAlias)
    fun mergeArtists(sourceArtistKey: Long, targetArtistKey: Long): CanonicalArtist
    fun splitArtistMapping(mappingId: Long): CanonicalArtist
}

interface AlbumIdentityRepository {
    fun albumByKey(albumKey: Long): CanonicalAlbum?
    fun albumForProvider(provider: String, providerAlbumId: String): CanonicalAlbum?
    fun albumForReleaseGroup(releaseGroupMbid: String): CanonicalAlbum?
    fun albumForRelease(releaseMbid: String): CanonicalAlbum?
    fun aliases(albumKey: Long): List<AlbumAlias>
    fun confirmCandidate(albumKey: Long, candidate: AnonymousAlbumCandidate, verifiedAt: Long)
}

interface IdentityCandidateRepository {
    fun saveCandidate(candidate: IdentityCandidate)
    fun pendingCandidates(targetType: IdentityTargetType, targetId: Long): List<IdentityCandidate>
    fun confirmCandidate(candidateId: String): IdentityCandidate
    fun rejectCandidate(candidateId: String): IdentityCandidate
    fun markAsAlternateVersion(candidateId: String, variantType: String): IdentityCandidate
}

interface IdentityJobRepository {
    fun readyJobs(now: Long, limit: Int): List<IdentityResolutionJob>
    fun claim(jobId: String, now: Long): IdentityResolutionJob?
    fun markSucceeded(jobId: String, now: Long)
    fun markRetry(jobId: String, nextAttemptAt: Long, error: String, now: Long)
    fun markFailed(jobId: String, error: String, now: Long)
}

interface ProviderResponseCacheRepository {
    fun response(provider: String, endpoint: String, requestHash: String, now: Long): ProviderCachedResponse?
    fun endpointHealth(provider: String, endpoint: String): ProviderEndpointHealth
    fun saveSuccess(
        provider: String,
        endpoint: String,
        requestHash: String,
        responseJson: String,
        now: Long,
        ttlMs: Long
    )
    fun recordFailure(provider: String, endpoint: String, error: String, now: Long): ProviderEndpointHealth
}
