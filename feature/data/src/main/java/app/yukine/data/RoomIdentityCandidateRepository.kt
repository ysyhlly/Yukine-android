package app.yukine.data

import app.yukine.data.room.ArtistSourceMappingEntity
import app.yukine.data.room.IdentityCandidateEntity
import app.yukine.data.room.MusicIdentityDao
import app.yukine.data.room.RecordingIdentifierEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateRepository
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.RecordingVariantType
import app.yukine.identity.RecordingIdentifier
import app.yukine.streaming.ProviderRolePolicy
import java.util.Locale
import java.util.concurrent.Callable
import org.json.JSONObject

class RoomIdentityCandidateRepository(
    private val database: YukineDatabase
) : IdentityCandidateRepository {
    private companion object {
        const val INTERNAL_STORED_MATCH_ITEM_ID = "__stored_match__"
    }

    private val dao: MusicIdentityDao
        get() = database.musicIdentityDao()
    private val providerSourceIdentityWriter: ProviderSourceIdentityWriter
        get() = ProviderSourceIdentityWriter(dao)

    override fun saveCandidate(candidate: IdentityCandidate) {
        database.runInTransaction {
            require(candidate.candidateId.isNotBlank()) { "Candidate ID is required" }
            requireTarget(candidate.targetType, candidate.targetId)
            val now = System.currentTimeMillis()
            dao.upsert(
                IdentityCandidateEntity(
                    candidateId = candidate.candidateId,
                    targetType = candidate.targetType.name,
                    targetId = candidate.targetId,
                    provider = candidate.provider.trim().lowercase(Locale.ROOT),
                    providerItemId = candidate.providerItemId.trim(),
                    title = candidate.title.trim(),
                    artist = candidate.artist.trim(),
                    album = candidate.album.trim(),
                    durationMs = candidate.durationMs.coerceAtLeast(0L),
                    isrc = normalizeIsrc(candidate.isrc),
                    variantType = candidate.variantType.trim().ifBlank { "UNKNOWN" },
                    score = candidate.score.coerceIn(0.0, 1.0),
                    status = candidate.status.name,
                    evidenceJson = candidate.evidenceJson,
                    createdAt = candidate.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = candidate.updatedAt.takeIf { it > 0L } ?: now
                )
            )
        }
    }

    override fun pendingCandidates(
        targetType: IdentityTargetType,
        targetId: Long
    ): List<IdentityCandidate> = dao.candidates(targetType.name, targetId)
        .filter {
            it.status == IdentityCandidateStatus.PENDING.name &&
                it.providerItemId != INTERNAL_STORED_MATCH_ITEM_ID
        }
        .map(IdentityCandidateEntity::toModel)

    override fun confirmCandidate(candidateId: String): IdentityCandidate {
        val confirmed = database.runInTransaction(Callable {
            var candidate = requireCandidate(candidateId)
            require(candidate.status == IdentityCandidateStatus.PENDING.name) {
                "Only pending candidates can be confirmed"
            }
            require(!hasHardConflict(candidate.evidenceJson)) {
                "Candidate has a hard identity conflict"
            }
            requireTarget(IdentityTargetType.valueOf(candidate.targetType), candidate.targetId)
            if (candidate.targetType == IdentityTargetType.RECORDING.name) {
                val ownerRecordingId = dao.source(candidate.provider, candidate.providerItemId)
                    ?.recordingId
                    ?.takeIf { it != candidate.targetId }
                if (ownerRecordingId != null) {
                    val sourceRecordingId = maxOf(candidate.targetId, ownerRecordingId)
                    val targetRecordingId = minOf(candidate.targetId, ownerRecordingId)
                    RoomRecordingIdentityRepository(database).mergeRecordingsInTransaction(
                        sourceRecordingId,
                        targetRecordingId
                    )
                    candidate = dao.candidate(
                        IdentityTargetType.RECORDING.name,
                        targetRecordingId,
                        candidate.provider,
                        candidate.providerItemId
                    ) ?: candidate.copy(
                        candidateId = ProviderSourceIdentityWriter.candidateId(
                            targetRecordingId,
                            candidate.provider,
                            candidate.providerItemId
                        ),
                        targetId = targetRecordingId,
                        updatedAt = System.currentTimeMillis()
                    ).also(dao::upsert)
                }
            }
            when (candidate.targetType) {
                IdentityTargetType.RECORDING.name -> confirmRecordingCandidate(candidate)
                IdentityTargetType.ARTIST.name -> confirmArtistCandidate(candidate)
                else -> error("Unknown candidate target ${candidate.targetType}")
            }
            val now = System.currentTimeMillis()
            check(
                dao.updateCandidateStatus(
                    candidate.candidateId,
                    IdentityCandidateStatus.USER_CONFIRMED.name,
                    now
                ) == 1
            ) { "Candidate disappeared during confirmation" }
            val confirmed = requireCandidate(candidate.candidateId)
            recordCandidateOperation(IdentityOperationType.CONFIRM_CANDIDATE, candidate, confirmed)
            confirmed.toModel()
        })
        if (confirmed.targetType == IdentityTargetType.RECORDING) {
            SourceIdentityIngestor(database).ingestRecordings(listOf(confirmed.targetId))
        }
        return confirmed
    }

    override fun rejectCandidate(candidateId: String): IdentityCandidate =
        database.runInTransaction(Callable {
            val candidate = requireCandidate(candidateId)
            require(candidate.status == IdentityCandidateStatus.PENDING.name) {
                "Only pending candidates can be rejected"
            }
            check(
                dao.updateCandidateStatus(
                    candidateId,
                    IdentityCandidateStatus.REJECTED.name,
                    System.currentTimeMillis()
                ) == 1
            ) { "Candidate disappeared during rejection" }
            rejectCandidateSource(candidate)
            val rejected = requireCandidate(candidateId)
            recordCandidateOperation(IdentityOperationType.REJECT_CANDIDATE, candidate, rejected)
            rejected.toModel()
        })

    fun rejectObviousMismatches(recordingId: Long): Int =
        database.runInTransaction(Callable {
            requireNotNull(dao.recording(recordingId)) { "Unknown recording $recordingId" }
            val obviousMismatches = dao.candidates(IdentityTargetType.RECORDING.name, recordingId)
                .filter {
                    it.status == IdentityCandidateStatus.PENDING.name &&
                        hasHardConflict(it.evidenceJson)
                }
            val now = System.currentTimeMillis()
            obviousMismatches.forEach { candidate ->
                check(
                    dao.updateCandidateStatus(
                        candidate.candidateId,
                        IdentityCandidateStatus.REJECTED.name,
                        now
                    ) == 1
                ) { "Candidate disappeared during batch rejection" }
                rejectCandidateSource(candidate)
                recordCandidateOperation(
                    IdentityOperationType.REJECT_CANDIDATE,
                    candidate,
                    requireCandidate(candidate.candidateId)
                )
            }
            obviousMismatches.size
        })

    override fun markAsAlternateVersion(
        candidateId: String,
        variantType: String
    ): IdentityCandidate = database.runInTransaction(Callable {
        val candidate = requireCandidate(candidateId)
        require(candidate.targetType == IdentityTargetType.RECORDING.name) {
            "Only recording candidates can be marked as another version"
        }
        require(candidate.status == IdentityCandidateStatus.PENDING.name) {
            "Only pending candidates can be marked as another version"
        }
        val variant = runCatching {
            RecordingVariantType.valueOf(variantType.trim().uppercase(Locale.ROOT))
        }.getOrNull()
        require(variant != null && variant != RecordingVariantType.ORIGINAL && variant != RecordingVariantType.UNKNOWN) {
            "A concrete alternate version type is required"
        }
        dao.upsert(
            candidate.copy(
                variantType = variant.name,
                status = IdentityCandidateStatus.ALTERNATE_VERSION.name,
                updatedAt = System.currentTimeMillis()
            )
        )
        rejectCandidateSource(candidate)
        requireCandidate(candidateId).toModel()
    })

    fun alternateVersionCandidates(
        targetType: IdentityTargetType,
        targetId: Long,
        limit: Int = 100,
        offset: Int = 0
    ): List<IdentityCandidate> = dao.candidatePage(
        targetType.name,
        targetId,
        IdentityCandidateStatus.ALTERNATE_VERSION.name,
        limit.coerceIn(1, 200),
        offset.coerceAtLeast(0)
    ).map(IdentityCandidateEntity::toModel)

    private fun confirmRecordingCandidate(candidate: IdentityCandidateEntity) {
        require(candidate.providerItemId != INTERNAL_STORED_MATCH_ITEM_ID) {
            "Internal streaming-match cache entries cannot be confirmed as provider sources"
        }
        val recording = requireNotNull(dao.recording(candidate.targetId))
        if (ProviderRolePolicy.canPersistCanonicalSource(candidate.provider)) {
            providerSourceIdentityWriter.saveUserConfirmedSource(
                candidate.targetId,
                candidate.provider,
                candidate.providerItemId,
                candidate.title,
                candidate.artist,
                candidate.album,
                candidate.durationMs
            )
        } else {
            attachMetadataIdentifiers(recording.canonicalUuid, candidate)
        }
        val isrc = normalizeIsrc(candidate.isrc)
        if (isrc.isNotBlank()) {
            val existingIdentifier = dao.identifier("ISRC", "", isrc)
            require(existingIdentifier == null || existingIdentifier.recordingId == candidate.targetId) {
                "Candidate ISRC belongs to another recording"
            }
            require(recording.isrc.isBlank() || normalizeIsrc(recording.isrc) == isrc) {
                "Candidate ISRC conflicts with the recording"
            }
            dao.update(recording.copy(isrc = isrc, updatedAt = System.currentTimeMillis()))
            dao.upsert(
                RecordingIdentifierEntity(
                    recordingId = candidate.targetId,
                    identifierType = "ISRC",
                    namespace = "",
                    identifierValue = isrc,
                    source = candidate.provider,
                    confidence = 1.0,
                    verifiedAt = System.currentTimeMillis()
                )
            )
        }
        if (ProviderRolePolicy.canPersistCanonicalSource(candidate.provider)) {
            dao.refreshActiveSource(candidate.targetId)
        }
    }

    private fun attachMetadataIdentifiers(canonicalId: String, candidate: IdentityCandidateEntity) {
        val evidence = runCatching { JSONObject(candidate.evidenceJson) }.getOrDefault(JSONObject())
        val values = listOf(
            "MUSICBRAINZ_RECORDING_ID" to evidence.optString("recordingMbid"),
            "MUSICBRAINZ_WORK_ID" to evidence.optString("workMbid"),
            "ACOUSTID" to evidence.optString("acoustId")
        )
        val repository = RoomRecordingIdentityRepository(database)
        values.filter { it.second.isNotBlank() }.forEach { (type, value) ->
            repository.attachIdentifier(
                candidate.targetId,
                RecordingIdentifier(
                    recordingId = candidate.targetId,
                    canonicalId = canonicalId,
                    identifierType = type,
                    identifierValue = value,
                    source = candidate.provider,
                    confidence = candidate.score.coerceIn(0.0, 1.0),
                    verifiedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun confirmArtistCandidate(candidate: IdentityCandidateEntity) {
        val artist = requireNotNull(dao.artist(candidate.targetId))
        val mapped = dao.artistForProvider(candidate.provider, candidate.providerItemId)
        require(mapped == null || mapped.id == artist.id) {
            "Provider artist already belongs to another canonical artist"
        }
        val evidence = runCatching { JSONObject(candidate.evidenceJson) }.getOrDefault(JSONObject())
        val avatarUrl = evidence.optString("avatarUrl").trim().takeIf {
            it.startsWith("https://", ignoreCase = true)
        }.orEmpty()
        if (artist.avatarUrl.isBlank() && avatarUrl.isNotBlank()) {
            dao.update(
                artist.copy(
                    avatarUrl = avatarUrl,
                    metadataSource = candidate.provider,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        dao.upsert(
            ArtistSourceMappingEntity(
                mappingId = dao.artistMappings(candidate.targetId)
                    .firstOrNull {
                        it.provider == candidate.provider &&
                            it.providerArtistId == candidate.providerItemId
                    }
                    ?.mappingId,
                artistId = candidate.targetId,
                provider = candidate.provider,
                providerArtistId = candidate.providerItemId,
                displayName = candidate.artist.ifBlank { candidate.title },
                status = "CONFIRMED",
                confidence = 1.0,
                lastVerifiedAt = System.currentTimeMillis()
            )
        )
    }

    private fun rejectCandidateSource(candidate: IdentityCandidateEntity) {
        if (candidate.targetType != IdentityTargetType.RECORDING.name ||
            candidate.providerItemId == INTERNAL_STORED_MATCH_ITEM_ID
        ) {
            return
        }
        providerSourceIdentityWriter.rejectUnconfirmedSource(
            candidate.targetId,
            candidate.provider,
            candidate.providerItemId
        )
    }

    private fun requireTarget(targetType: IdentityTargetType, targetId: Long) {
        val exists = when (targetType) {
            IdentityTargetType.RECORDING -> dao.recording(targetId) != null
            IdentityTargetType.ARTIST -> dao.artist(targetId) != null
        }
        require(exists) { "Unknown ${targetType.name.lowercase()} target $targetId" }
    }

    private fun requireCandidate(candidateId: String): IdentityCandidateEntity =
        requireNotNull(dao.candidate(candidateId.trim())) { "Unknown candidate $candidateId" }

    private fun recordCandidateOperation(
        operationType: String,
        before: IdentityCandidateEntity,
        after: IdentityCandidateEntity
    ) {
        if (before.targetType != IdentityTargetType.RECORDING.name) return
        IdentityOperationStore(database).recordAudit(
            operationType = operationType,
            sourceRecordingId = before.targetId,
            targetRecordingId = before.targetId,
            beforePayload = JSONObject()
                .put("candidateId", before.candidateId)
                .put("status", before.status),
            afterPayload = JSONObject()
                .put("candidateId", after.candidateId)
                .put("status", after.status)
        )
    }

    private fun normalizeIsrc(value: String): String =
        value.filter(Char::isLetterOrDigit).uppercase(Locale.ROOT)

    private fun hasHardConflict(evidenceJson: String): Boolean {
        if (evidenceJson.isBlank()) return false
        return runCatching { JSONObject(evidenceJson).optBoolean("hardConflict", false) }
            .getOrDefault(false)
    }
}
