package app.yukine.data.enrichment

import app.yukine.data.IdentityMutationGate
import app.yukine.identity.AnonymousRecordingCandidate
import app.yukine.identity.AnonymousRecordingMetadataProvider
import app.yukine.identity.AnonymousProviderResult
import app.yukine.identity.AnonymousArtistCandidate
import app.yukine.identity.AnonymousArtistMetadataProvider
import app.yukine.identity.AnonymousArtistProviderResult
import app.yukine.identity.ArtistAlias
import app.yukine.identity.ArtistCandidateRanker
import app.yukine.identity.ArtistMatchEvidence
import app.yukine.identity.ArtistIdentityRepository
import app.yukine.identity.CanonicalArtist
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateRepository
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityEnhancementRunResult
import app.yukine.identity.IdentityJobRepository
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.IdentityTextNormalizer
import app.yukine.identity.RecordingCandidateRanker
import app.yukine.identity.RecordingIdentityRepository
import app.yukine.identity.RecordingIdentifier
import app.yukine.identity.RecordingMatchEvidence
import app.yukine.identity.RecordingVariantRecognizer
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

fun interface MissingRecordingCoverWriter {
    fun writeIfMissing(recordingId: Long, coverUrl: String, updatedAt: Long)
}

class IdentityEnhancementEngine(
    private val recordings: RecordingIdentityRepository,
    private val artists: ArtistIdentityRepository,
    private val candidates: IdentityCandidateRepository,
    private val jobs: IdentityJobRepository,
    private val providers: List<AnonymousRecordingMetadataProvider>,
    private val artistProviders: List<AnonymousArtistMetadataProvider> = emptyList(),
    private val recordingRanker: RecordingCandidateRanker = RecordingCandidateRanker(),
    private val artistRanker: ArtistCandidateRanker = ArtistCandidateRanker(),
    private val missingCoverWriter: MissingRecordingCoverWriter = MissingRecordingCoverWriter { _, _, _ -> },
    private val now: () -> Long = System::currentTimeMillis
) {
    fun runReadyJobs(limit: Int = 20): IdentityEnhancementRunResult {
        var claimedCount = 0
        var succeeded = 0
        var retried = 0
        var failed = 0
        var saved = 0
        jobs.readyJobs(now(), limit.coerceIn(1, 100)).forEach { pending ->
            IdentityMutationGate.withLock {
                val claimed = jobs.claim(pending.jobId, now()) ?: return@withLock
                claimedCount++
                when (claimed.targetType) {
                    IdentityTargetType.ARTIST -> {
                        runCatching { enhanceArtist(claimed.targetId) }.fold(
                            onSuccess = { outcome ->
                                saved += outcome.candidatesSaved
                                if (outcome.shouldRetry) {
                                    jobs.markRetry(
                                        claimed.jobId,
                                        retryAt(claimed.attemptCount, now()),
                                        outcome.error.ifBlank { "All artist metadata providers unavailable" },
                                        now()
                                    )
                                    retried++
                                } else {
                                    jobs.markSucceeded(claimed.jobId, now())
                                    succeeded++
                                }
                            },
                            onFailure = { error ->
                                if (claimed.attemptCount >= MAX_ATTEMPTS - 1) {
                                    jobs.markFailed(claimed.jobId, error.message.orEmpty(), now())
                                    failed++
                                } else {
                                    jobs.markRetry(
                                        claimed.jobId,
                                        retryAt(claimed.attemptCount, now()),
                                        error.message.orEmpty(),
                                        now()
                                    )
                                    retried++
                                }
                            }
                        )
                    }
                    IdentityTargetType.RECORDING -> runCatching {
                        enhanceRecording(claimed.targetId)
                    }.fold(
                        onSuccess = { outcome ->
                            saved += outcome.candidatesSaved
                            if (outcome.shouldRetry) {
                                jobs.markRetry(
                                    claimed.jobId,
                                    retryAt(claimed.attemptCount, now()),
                                    outcome.error.ifBlank { "All metadata providers unavailable" },
                                    now()
                                )
                                retried++
                            } else {
                                jobs.markSucceeded(claimed.jobId, now())
                                succeeded++
                            }
                        },
                        onFailure = { error ->
                            if (claimed.attemptCount >= MAX_ATTEMPTS - 1) {
                                jobs.markFailed(claimed.jobId, error.message.orEmpty(), now())
                                failed++
                            } else {
                                jobs.markRetry(
                                    claimed.jobId,
                                    retryAt(claimed.attemptCount, now()),
                                    error.message.orEmpty(),
                                    now()
                                )
                                retried++
                            }
                        }
                    )
                }
            }
        }
        return IdentityEnhancementRunResult(claimedCount, succeeded, retried, failed, saved)
    }

    private fun enhanceRecording(recordingId: Long): RecordingOutcome {
        val recording = requireNotNull(recordings.canonicalForRecording(recordingId)) {
            "Recording $recordingId no longer exists"
        }
        val credits = artists.creditsForRecording(recordingId)
        val primaryCredits = credits.filter { it.role.name == "PRIMARY" }
        val primaryArtist = primaryCredits.joinToString(" / ") { it.creditedName }.ifBlank {
            recording.primaryArtistDisplay
        }
        val source = recordings.confirmedSources(recordingId).firstOrNull()
        val target = RecordingMatchEvidence(
            provider = "canonical",
            providerItemId = recording.canonicalId,
            title = recording.title,
            primaryArtistIds = primaryCredits.map { it.artistKey }.toSet(),
            primaryArtistNames = primaryCredits.map { it.creditedName }.toSet() + recording.primaryArtistDisplay,
            album = source?.album.orEmpty(),
            durationMs = recording.durationMs,
            isrc = recording.isrc,
            recordingMbid = recording.musicBrainzRecordingId,
            workMbid = recording.musicBrainzWorkId,
            acoustId = recording.acoustId,
            variantType = RecordingVariantRecognizer.recognize(recording.title, source?.album.orEmpty())
        )

        val responses = queryRecordingProviders(recording, primaryArtist)
        val successfulResponses = responses.mapNotNull { it.second.getOrNull() }
        val allUnavailable = responses.isEmpty() || successfulResponses.all { it.allEndpointsFailed }
        val rawCandidates = successfulResponses.flatMap { it.candidates }.distinctBy { it.provider to it.providerItemId }
        val evidenceByCandidate = rawCandidates.associateWith { it.toMatchEvidence() }
        val ranked = recordingRanker.rank(target, evidenceByCandidate.values.toList())
        val decision = recordingRanker.chooseForAutoConfirmation(target, evidenceByCandidate.values.toList())
        val rankedByKey = ranked.associateBy { it.candidate.provider to it.candidate.providerItemId }
        val time = now()
        rawCandidates.forEach { candidate ->
            val ranking = rankedByKey.getValue(candidate.provider to candidate.providerItemId)
            val autoConfirmed = decision.eligible && decision.winner?.candidate?.let {
                it.provider == candidate.provider && it.providerItemId == candidate.providerItemId
            } == true
            if (autoConfirmed) {
                attachStrongIdentifiers(recording, candidate, time)
                missingCoverWriter.writeIfMissing(recordingId, candidate.coverUrl, time)
            }
            candidates.saveCandidate(
                IdentityCandidate(
                    candidateId = stableCandidateId(recordingId, candidate),
                    targetType = IdentityTargetType.RECORDING,
                    targetId = recordingId,
                    provider = candidate.provider,
                    providerItemId = candidate.providerItemId,
                    title = candidate.title,
                    artist = candidate.artists.joinToString(" / ") { it.displayName },
                    album = candidate.album,
                    durationMs = candidate.durationMs,
                    isrc = candidate.isrc,
                    variantType = candidate.variantType.name,
                    score = ranking.score,
                    status = if (autoConfirmed) IdentityCandidateStatus.AUTO_CONFIRMED else IdentityCandidateStatus.PENDING,
                    evidenceJson = evidenceJson(candidate, ranking.reasons, ranking.hardConflict, decision.margin),
                    createdAt = time,
                    updatedAt = time
                )
            )
        }
        val error = responses.mapNotNull { (provider, response) ->
            response.exceptionOrNull()?.let { "$provider: ${it.message.orEmpty()}" }
        }.joinToString("; ")
        return RecordingOutcome(rawCandidates.size, allUnavailable, error)
    }

    private fun enhanceArtist(artistKey: Long): RecordingOutcome {
        val artist = requireNotNull(artists.artistByKey(artistKey)) { "Artist $artistKey no longer exists" }
        val aliases = artists.aliases(artistKey)
        val target = ArtistMatchEvidence(
            provider = "canonical",
            providerItemId = artist.artistId,
            displayName = artist.displayName,
            aliases = aliases.map { it.alias }.toSet(),
            artistMbid = artist.musicBrainzArtistId,
            countryCode = artist.countryCode,
            artistType = artist.artistType
        )
        val responses = queryArtistProviders(artist, aliases)
        val successful = responses.mapNotNull { it.second.getOrNull() }
        val allUnavailable = responses.isEmpty() || successful.all { it.allEndpointsFailed }
        val rawCandidates = successful.flatMap { it.candidates }.distinctBy { it.provider to it.providerItemId }
        val evidence = rawCandidates.associateWith { candidate ->
            ArtistMatchEvidence(
                provider = candidate.provider,
                providerItemId = candidate.providerItemId,
                displayName = candidate.displayName,
                aliases = candidate.aliases,
                artistMbid = candidate.artistMbid,
                confirmedProviderMapping = artists.artistForProvider(
                    candidate.provider,
                    candidate.providerItemId
                )?.artistKey == artistKey,
                countryCode = candidate.countryCode,
                artistType = candidate.artistType,
                providerScore = candidate.providerScore
            )
        }
        val ranked = artistRanker.rank(target, evidence.values.toList())
        val decision = artistRanker.chooseForAutoConfirmation(target, evidence.values.toList())
        val rankedByKey = ranked.associateBy { it.candidate.provider to it.candidate.providerItemId }
        val time = now()
        val autoConfirmedCandidate = rawCandidates.firstOrNull { candidate ->
            decision.eligible && decision.winner?.candidate?.let {
                it.provider == candidate.provider && it.providerItemId == candidate.providerItemId
            } == true
        }
        val exactProfileCandidates = rawCandidates.filter { candidate ->
            val ranking = rankedByKey.getValue(candidate.provider to candidate.providerItemId)
            candidate.providerScore >= 0.95 &&
                !ranking.hardConflict &&
                ranking.score >= 0.60 &&
                IdentityTextNormalizer.normalizeForSearch(candidate.displayName) ==
                IdentityTextNormalizer.normalizeForSearch(artist.displayName)
        }
        val avatarCandidate = autoConfirmedCandidate?.takeIf { it.avatarUrl.isNotBlank() }
            ?: exactProfileCandidates.filter { it.avatarUrl.isNotBlank() }.singleOrNull()
        val descriptionCandidate = autoConfirmedCandidate?.takeIf { it.description.isNotBlank() }
            ?: exactProfileCandidates.filter { it.description.isNotBlank() }.singleOrNull()
        if (artist.avatarUrl.isBlank() && avatarCandidate != null) {
            artists.updateAvatarIfMissing(artistKey, avatarCandidate.avatarUrl, avatarCandidate.provider)
        }
        if (artist.description.isBlank() && descriptionCandidate != null) {
            artists.updateDescriptionIfMissing(
                artistKey,
                descriptionCandidate.description,
                descriptionCandidate.provider
            )
        }
        rawCandidates.forEach { candidate ->
            val ranking = rankedByKey.getValue(candidate.provider to candidate.providerItemId)
            val autoConfirmed = decision.eligible && decision.winner?.candidate?.let {
                it.provider == candidate.provider && it.providerItemId == candidate.providerItemId
            } == true
            candidates.saveCandidate(
                IdentityCandidate(
                    candidateId = stableArtistCandidateId(artistKey, candidate),
                    targetType = IdentityTargetType.ARTIST,
                    targetId = artistKey,
                    provider = candidate.provider,
                    providerItemId = candidate.providerItemId,
                    title = candidate.displayName,
                    artist = candidate.displayName,
                    score = ranking.score,
                    status = if (autoConfirmed) IdentityCandidateStatus.AUTO_CONFIRMED else IdentityCandidateStatus.PENDING,
                    evidenceJson = JSONObject()
                        .put("providerScore", candidate.providerScore)
                        .put("artistMbid", candidate.artistMbid)
                        .put("country", candidate.countryCode)
                        .put("artistType", candidate.artistType.name)
                        .put("aliases", JSONArray(candidate.aliases.toList()))
                        .put("avatarUrl", candidate.avatarUrl)
                        .put("description", candidate.description)
                        .put("hardConflict", ranking.hardConflict)
                        .put("margin", decision.margin)
                        .put("reasons", JSONArray(ranking.reasons))
                        .toString(),
                    createdAt = time,
                    updatedAt = time
                )
            )
        }
        val error = responses.mapNotNull { (provider, response) ->
            response.exceptionOrNull()?.let { "$provider: ${it.message.orEmpty()}" }
        }.joinToString("; ")
        val missingAvatar = artist.avatarUrl.isBlank() && rawCandidates.none { it.avatarUrl.isNotBlank() }
        return RecordingOutcome(
            rawCandidates.size,
            allUnavailable || missingAvatar,
            error.ifBlank { if (missingAvatar) "Artist avatar unavailable" else "" }
        )
    }

    private fun AnonymousRecordingCandidate.toMatchEvidence(): RecordingMatchEvidence {
        val mappedArtistIds = artists.mapNotNull {
            this@IdentityEnhancementEngine.artists.artistForProvider(provider, it.providerArtistId)?.artistKey
        }.toSet()
        return RecordingMatchEvidence(
            provider = provider,
            providerItemId = providerItemId,
            title = title,
            primaryArtistIds = mappedArtistIds,
            primaryArtistNames = artists.map { it.displayName }.toSet(),
            album = album,
            durationMs = durationMs,
            isrc = isrc,
            recordingMbid = recordingMbid,
            workMbid = workMbid,
            acoustId = acoustId,
            fingerprintVerified = fingerprintVerified,
            providerScore = providerScore,
            variantType = variantType
        )
    }

    private fun queryRecordingProviders(
        recording: CanonicalRecording,
        primaryArtist: String
    ): List<Pair<String, Result<AnonymousProviderResult>>> {
        val primary = providers.firstOrNull() ?: return emptyList()
        val primaryResponse = runCatching { primary.search(recording, primaryArtist) }
        val responses = mutableListOf(primary.providerName to primaryResponse)
        val primaryValue = primaryResponse.getOrNull()
        if (primaryValue != null && !primaryValue.allEndpointsFailed && primaryValue.candidates.isNotEmpty()) {
            return responses
        }
        providers.drop(1).forEach { provider ->
            responses += provider.providerName to runCatching { provider.search(recording, primaryArtist) }
        }
        return responses
    }

    private fun queryArtistProviders(
        artist: CanonicalArtist,
        aliases: List<ArtistAlias>
    ): List<Pair<String, Result<AnonymousArtistProviderResult>>> {
        val primary = artistProviders.firstOrNull() ?: return emptyList()
        val primaryResponse = runCatching { primary.search(artist, aliases) }
        val responses = mutableListOf(primary.providerName to primaryResponse)
        val primaryValue = primaryResponse.getOrNull()
        if (primaryValue != null && !primaryValue.allEndpointsFailed && primaryValue.candidates.isNotEmpty()) {
            return responses
        }
        artistProviders.drop(1).forEach { provider ->
            responses += provider.providerName to runCatching { provider.search(artist, aliases) }
        }
        return responses
    }

    private fun evidenceJson(
        candidate: AnonymousRecordingCandidate,
        reasons: List<String>,
        hardConflict: Boolean,
        margin: Double
    ): String = JSONObject()
        .put("providerScore", candidate.providerScore)
        .put("hardConflict", hardConflict)
        .put("margin", margin)
        .put("reasons", JSONArray(reasons))
        .put("artistIds", JSONArray(candidate.artists.map { it.providerArtistId }))
        .put("recordingMbid", candidate.recordingMbid)
        .put("workMbid", candidate.workMbid)
        .put("acoustId", candidate.acoustId)
        .put("coverUrl", candidate.coverUrl)
        .put("fingerprintVerified", candidate.fingerprintVerified)
        .toString()

    private fun attachStrongIdentifiers(
        recording: CanonicalRecording,
        candidate: AnonymousRecordingCandidate,
        verifiedAt: Long
    ) {
        val values = listOf(
            "MUSICBRAINZ_RECORDING_ID" to candidate.recordingMbid,
            "MUSICBRAINZ_WORK_ID" to candidate.workMbid,
            "ISRC" to candidate.isrc,
            "ACOUSTID" to candidate.acoustId
        )
        values.filter { it.second.isNotBlank() }.forEach { (type, value) ->
            recordings.attachIdentifier(
                recording.recordingId,
                RecordingIdentifier(
                    recordingId = recording.recordingId,
                    canonicalId = recording.canonicalId,
                    identifierType = type,
                    identifierValue = value,
                    source = candidate.provider,
                    confidence = if (candidate.fingerprintVerified) {
                        candidate.providerScore.coerceIn(0.0, 1.0)
                    } else 1.0,
                    verifiedAt = verifiedAt
                )
            )
        }
    }

    private fun stableCandidateId(recordingId: Long, candidate: AnonymousRecordingCandidate): String =
        UUID.nameUUIDFromBytes(
            "RECORDING:$recordingId:${candidate.provider}:${candidate.providerItemId}"
                .toByteArray(StandardCharsets.UTF_8)
        ).toString()

    private fun stableArtistCandidateId(artistKey: Long, candidate: AnonymousArtistCandidate): String =
        UUID.nameUUIDFromBytes(
            "ARTIST:$artistKey:${candidate.provider}:${candidate.providerItemId}"
                .toByteArray(StandardCharsets.UTF_8)
        ).toString()

    private fun retryAt(attemptCount: Int, time: Long): Long {
        val exponent = attemptCount.coerceIn(0, 7)
        val delay = (BASE_RETRY_MS shl exponent).coerceAtMost(MAX_RETRY_MS)
        return if (Long.MAX_VALUE - time < delay) Long.MAX_VALUE else time + delay
    }

    private data class RecordingOutcome(
        val candidatesSaved: Int,
        val shouldRetry: Boolean,
        val error: String
    )

    private companion object {
        const val MAX_ATTEMPTS = 8
        const val BASE_RETRY_MS = 15L * 60L * 1_000L
        const val MAX_RETRY_MS = 24L * 60L * 60L * 1_000L
    }
}
