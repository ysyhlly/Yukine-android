package app.yukine.data

import android.content.Context
import app.yukine.data.room.YukineDatabase
import app.yukine.data.room.IdentityResolutionJobEntity
import app.yukine.identity.CanonicalRecording
import app.yukine.identity.IdentityCandidate
import app.yukine.identity.IdentityCandidateStatus
import app.yukine.identity.IdentityTargetType
import app.yukine.identity.RecordingIdentifier
import app.yukine.identity.TrackSourceMapping
import app.yukine.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.Callable

data class RecordingMatchSnapshot(
    val track: Track,
    val recording: CanonicalRecording,
    val activeSource: TrackSourceMapping?,
    val sources: List<TrackSourceMapping>,
    val identifiers: List<RecordingIdentifier>,
    val variants: List<RecordingMatchVariant>,
    val pendingCandidates: List<IdentityCandidate>,
    val alternateVersions: List<IdentityCandidate>,
    val sourceTotal: Int,
    val candidateTotal: Int,
    val recentOperations: List<IdentityOperation> = emptyList()
)

data class RecordingMatchVariant(
    val groupId: String,
    val variantType: String,
    val displayName: String,
    val confidence: Double
)

data class RecordingSourceVerification(
    val success: Boolean,
    val codec: String = "",
    val bitrateKbps: Int = 0,
    val failureReason: String = ""
)

fun interface RecordingSourceVerificationGateway {
    suspend fun verify(source: TrackSourceMapping): RecordingSourceVerification
}

interface RecordingMatchDataSource {
    fun snapshotForLocalTrack(
        localTrackId: Long,
        sourceOffset: Int = 0,
        candidateOffset: Int = 0,
        pageSize: Int = 50
    ): RecordingMatchSnapshot?
    fun confirmCandidate(candidateId: String): IdentityCandidate
    fun rejectCandidate(candidateId: String): IdentityCandidate
    fun rejectObviousMismatches(recordingId: Long): Int
    fun markAsAlternateVersion(candidateId: String, variantType: String): IdentityCandidate
    fun requestCandidateRefresh(recordingId: Long)
    fun searchMergeCandidates(
        targetRecordingId: Long,
        query: String,
        limit: Int = 20
    ): List<RecordingMergeSearchResult>
    fun previewMerge(sourceRecordingId: Long, targetRecordingId: Long): RecordingMergePreview
    fun mergeRecordings(sourceRecordingId: Long, targetRecordingId: Long): CanonicalRecording
    fun previewSplit(recordingId: Long, sourceIds: Set<Long>): RecordingSplitPreview
    fun splitSources(sourceIds: Set<Long>, options: RecordingSplitOptions): CanonicalRecording
    fun sourcesForSplit(recordingId: Long): List<TrackSourceMapping>
    fun undoIdentityOperation(operationId: Long): IdentityOperation
    suspend fun verifySource(sourceId: Long): RecordingSourceVerification
    fun setPreferredSource(sourceId: Long)
    fun removeUnavailableSource(sourceId: Long)
}

/** Network-free Room facade for the explicit recording-match management UI. */
@Singleton
class RecordingMatchRepository internal constructor(
    private val database: YukineDatabase,
    private val sourceVerifier: RecordingSourceVerificationGateway = RecordingSourceVerificationGateway {
        RecordingSourceVerification(false, failureReason = "Source verification is unavailable")
    }
) : RecordingMatchDataSource {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        sourceVerifier: RecordingSourceVerificationGateway
    ) : this(
        YukineDatabase.getInstance(context.applicationContext),
        sourceVerifier
    )

    private val recordings = RoomRecordingIdentityRepository(database)
    private val candidates = RoomIdentityCandidateRepository(database)
    private val identityDao = database.musicIdentityDao()
    private val operationStore = IdentityOperationStore(database)

    override fun snapshotForLocalTrack(
        localTrackId: Long,
        sourceOffset: Int,
        candidateOffset: Int,
        pageSize: Int
    ): RecordingMatchSnapshot? {
        val track = database.libraryDao().loadTrack(localTrackId)?.let {
            app.yukine.data.room.TrackEntityMapper.track(it)
        }
            ?: return null
        val recording = recordings.canonicalForLocalTrack(localTrackId) ?: return null
        val entity = identityDao.recording(recording.recordingId) ?: return null
        val active = identityDao.activeSource(recording.recordingId)?.toModel(entity)
        val safePageSize = pageSize.coerceIn(1, 100)
        val sources = identityDao.sourcePage(
            recording.recordingId,
            safePageSize,
            sourceOffset.coerceAtLeast(0)
        )
            .map { it.toModel(entity) }
        val pending = identityDao.candidatePage(
            IdentityTargetType.RECORDING.name,
            recording.recordingId,
            IdentityCandidateStatus.PENDING.name,
            safePageSize,
            candidateOffset.coerceAtLeast(0)
        ).map { it.toModel() }
        return RecordingMatchSnapshot(
            track = track,
            recording = recording,
            activeSource = active,
            sources = sources,
            identifiers = identityDao.identifiers(recording.recordingId).map { it.toModel(entity) },
            variants = identityDao.variants(recording.recordingId).map {
                RecordingMatchVariant(it.variantGroupId, it.variantType, it.displayName, it.confidence)
            },
            pendingCandidates = pending,
            alternateVersions = candidates.alternateVersionCandidates(
                IdentityTargetType.RECORDING,
                recording.recordingId
            ),
            sourceTotal = identityDao.sourceCount(recording.recordingId),
            candidateTotal = identityDao.candidateCount(
                IdentityTargetType.RECORDING.name,
                recording.recordingId,
                IdentityCandidateStatus.PENDING.name
            ),
            recentOperations = operationStore.recent(recording.recordingId)
        )
    }

    override fun confirmCandidate(candidateId: String): IdentityCandidate =
        candidates.confirmCandidate(candidateId)

    override fun rejectCandidate(candidateId: String): IdentityCandidate =
        candidates.rejectCandidate(candidateId)

    override fun rejectObviousMismatches(recordingId: Long): Int =
        candidates.rejectObviousMismatches(recordingId)

    override fun markAsAlternateVersion(candidateId: String, variantType: String): IdentityCandidate =
        candidates.markAsAlternateVersion(candidateId, variantType)

    override fun requestCandidateRefresh(recordingId: Long) {
        requireNotNull(identityDao.recording(recordingId)) { "Unknown recording $recordingId" }
        val now = System.currentTimeMillis()
        identityDao.upsert(
            IdentityResolutionJobEntity(
                jobId = "manual-recording-refresh-$recordingId",
                targetType = IdentityTargetType.RECORDING.name,
                targetId = recordingId,
                priority = 100,
                reason = "MANUAL_CANDIDATE_REFRESH",
                attemptCount = 0,
                nextAttemptAt = 0L,
                lastError = "",
                status = "PENDING",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override fun searchMergeCandidates(
        targetRecordingId: Long,
        query: String,
        limit: Int
    ): List<RecordingMergeSearchResult> {
        requireNotNull(identityDao.recording(targetRecordingId)) { "Unknown recording $targetRecordingId" }
        return identityDao.searchRecordings(
            query = query.trim(),
            excludedRecordingId = targetRecordingId,
            limit = limit.coerceIn(1, 50)
        ).map { row ->
            RecordingMergeSearchResult(
                recordingId = row.recordingId,
                canonicalId = row.canonicalUuid,
                title = row.title,
                primaryArtistDisplay = row.primaryArtistDisplay,
                durationMs = row.durationMs,
                sourceCount = row.sourceCount,
                variantTypes = row.variantTypes.split(',').map(String::trim).filter(String::isNotBlank)
            )
        }
    }

    override fun previewMerge(
        sourceRecordingId: Long,
        targetRecordingId: Long
    ): RecordingMergePreview = database.runInTransaction(Callable {
        require(sourceRecordingId != targetRecordingId) { "Source and target recording are identical" }
        val sourceEntity = requireNotNull(identityDao.recording(sourceRecordingId)) {
            "Unknown recording $sourceRecordingId"
        }
        val targetEntity = requireNotNull(identityDao.recording(targetRecordingId)) {
            "Unknown recording $targetRecordingId"
        }
        val sourceCredits = identityDao.credits(sourceRecordingId)
        val targetCredits = identityDao.credits(targetRecordingId)
        val sourceVariants = identityDao.variants(sourceRecordingId)
        val targetVariants = identityDao.variants(targetRecordingId)
        val sourceHistory = database.historyDao().recordingHistory(sourceRecordingId)
        RecordingMergePreview(
            source = mergeSummary(sourceEntity, sourceVariants),
            target = mergeSummary(targetEntity, targetVariants),
            impact = RecordingMergeImpact(
                favoriteCount = if (database.libraryDao().recordingFavorite(sourceRecordingId) == null) 0 else 1,
                playlistItemCount = database.playlistDao().playlistRecordingReferences(sourceRecordingId).size,
                playHistoryCount = sourceHistory?.playCount ?: 0,
                playEventCount = database.historyDao().recordingEventCount(sourceRecordingId),
                queueItemCount = database.playbackPersistenceDao().queueRecordingCount(sourceRecordingId),
                sourceCount = identityDao.sourceCount(sourceRecordingId)
            ),
            warnings = RecordingMergePolicy.warnings(
                source = sourceEntity,
                target = targetEntity,
                sourceCredits = sourceCredits,
                targetCredits = targetCredits,
                sourceVariants = sourceVariants,
                targetVariants = targetVariants
            )
        )
    })

    override fun mergeRecordings(
        sourceRecordingId: Long,
        targetRecordingId: Long
    ): CanonicalRecording = recordings.mergeRecordings(sourceRecordingId, targetRecordingId)

    override fun previewSplit(
        recordingId: Long,
        sourceIds: Set<Long>
    ): RecordingSplitPreview = database.runInTransaction(Callable {
        val entity = requireNotNull(identityDao.recording(recordingId)) { "Unknown recording $recordingId" }
        val selectedIds = sourceIds.filter { it > 0L }.toSet()
        require(selectedIds.isNotEmpty()) { "At least one source must be selected" }
        val allSources = identityDao.sources(recordingId)
        val selected = allSources.filter { it.sourceId in selectedIds }
        require(selected.size == selectedIds.size) { "Selected source does not belong to recording $recordingId" }
        require(selected.size < allSources.size) {
            "A split must leave at least one source in the original recording"
        }
        val sourceHistory = database.historyDao().recordingHistory(recordingId)
        RecordingSplitPreview(
            original = mergeSummary(entity, identityDao.variants(recordingId)),
            selectedSources = selected.map { it.toModel(entity) },
            remainingSourceCount = allSources.size - selected.size,
            impact = RecordingMergeImpact(
                favoriteCount = if (database.libraryDao().recordingFavorite(recordingId) == null) 0 else 1,
                playlistItemCount = database.playlistDao().playlistRecordingReferences(recordingId).size,
                playHistoryCount = sourceHistory?.playCount ?: 0,
                playEventCount = database.historyDao().recordingEventCount(recordingId),
                queueItemCount = database.playbackPersistenceDao().queueRecordingCount(recordingId),
                sourceCount = selected.size
            )
        )
    })

    override fun splitSources(
        sourceIds: Set<Long>,
        options: RecordingSplitOptions
    ): CanonicalRecording = recordings.splitSources(sourceIds, options)

    override fun sourcesForSplit(recordingId: Long): List<TrackSourceMapping> {
        val entity = requireNotNull(identityDao.recording(recordingId)) { "Unknown recording $recordingId" }
        return identityDao.sources(recordingId).map { it.toModel(entity) }
    }

    override fun undoIdentityOperation(operationId: Long): IdentityOperation =
        operationStore.undo(operationId)

    override suspend fun verifySource(sourceId: Long): RecordingSourceVerification {
        val sourceEntity = requireNotNull(identityDao.source(sourceId)) { "Unknown source $sourceId" }
        val recording = requireNotNull(identityDao.recording(sourceEntity.recordingId)) {
            "Unknown recording ${sourceEntity.recordingId}"
        }
        val result = runCatching { sourceVerifier.verify(sourceEntity.toModel(recording)) }
            .getOrElse { RecordingSourceVerification(false, failureReason = it.javaClass.simpleName) }
        val now = System.currentTimeMillis()
        database.runInTransaction {
            val current = requireNotNull(identityDao.source(sourceId)) { "Source disappeared during verification" }
            if (result.success) {
                check(
                    identityDao.markSourceVerifiedSuccess(
                        sourceId,
                        now,
                        result.codec.trim(),
                        result.bitrateKbps.coerceAtLeast(0)
                    ) == 1
                )
            } else {
                check(
                    identityDao.markSourceVerificationFailure(
                        sourceId,
                        now,
                        sanitizeFailure(result.failureReason)
                    ) == 1
                )
                identityDao.refreshActiveSource(current.recordingId)
            }
        }
        return result.copy(failureReason = if (result.success) "" else sanitizeFailure(result.failureReason))
    }

    override fun setPreferredSource(sourceId: Long) {
        database.runInTransaction {
            val source = requireNotNull(identityDao.source(sourceId)) { "Unknown source $sourceId" }
            require(source.playable && source.matchStatus == "CONFIRMED" && source.isPreferredEligible()) {
                "Only a verified confirmed source can become preferred"
            }
            val recording = requireNotNull(identityDao.recording(source.recordingId))
            check(identityDao.setActiveSource(source.recordingId, sourceId) == 1) {
                "Unable to set preferred source"
            }
            operationStore.recordAudit(
                IdentityOperationType.SET_ACTIVE_SOURCE,
                source.recordingId,
                source.recordingId,
                org.json.JSONObject().put("sourceId", recording.activeSourceId ?: org.json.JSONObject.NULL),
                org.json.JSONObject().put("sourceId", sourceId)
            )
        }
    }

    override fun removeUnavailableSource(sourceId: Long) {
        database.runInTransaction {
            val source = requireNotNull(identityDao.source(sourceId)) { "Unknown source $sourceId" }
            require(!source.playable) { "Playable sources cannot be removed as unavailable" }
            require(identityDao.sourceCount(source.recordingId) > 1) {
                "The last source of a recording cannot be removed"
            }
            check(identityDao.deleteSource(sourceId) == 1) { "Unable to remove source" }
            identityDao.refreshActiveSource(source.recordingId)
        }
    }

    private fun sanitizeFailure(value: String): String = value
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[url]")
        .replace(Regex("(?i)(authorization|cookie|token|password)\\s*[:=]\\s*\\S+"), "$1=[redacted]")
        .trim()
        .take(240)
        .ifBlank { "Source verification failed" }

    private fun app.yukine.data.room.TrackSourceMappingEntity.isPreferredEligible(): Boolean =
        provider.lowercase() in PHYSICAL_SOURCE_PROVIDERS || lastVerifiedAt > 0L || lastSuccessfulAt > 0L

    private fun mergeSummary(
        entity: app.yukine.data.room.CanonicalRecordingEntity,
        variantEntities: List<app.yukine.data.room.RecordingVariantEntity>
    ): RecordingMergeSummary {
        val recordingId = requireNotNull(entity.id)
        return RecordingMergeSummary(
            recording = entity.toModel(),
            sources = identityDao.sources(recordingId).map { it.toModel(entity) },
            identifiers = identityDao.identifiers(recordingId).map { it.toModel(entity) },
            variants = variantEntities.map {
                RecordingMatchVariant(it.variantGroupId, it.variantType, it.displayName, it.confidence)
            }
        )
    }
}

private val PHYSICAL_SOURCE_PROVIDERS = setOf("local", "document", "webdav")
