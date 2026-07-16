package app.yukine.data

import app.yukine.data.room.TrackSourceMappingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.streaming.ProviderRolePolicy

enum class IdentityBackfillStage {
    NORMALIZE,
    CLASSIFY,
    INGEST,
    LX_CLEANUP,
    VALIDATE,
    COMPLETE
}

data class IdentityBackfillProgress(
    val total: Int = 0,
    val processed: Int = 0,
    val normalized: Int = 0,
    val classified: Int = 0,
    val merged: Int = 0,
    val relations: Int = 0,
    val pending: Int = 0,
    val lxMigrated: Int = 0,
    val lxDeleted: Int = 0,
    val deleted: Int = 0,
    val errors: Int = 0
)

data class IdentityBackfillCheckpoint(
    val stage: IdentityBackfillStage = IdentityBackfillStage.NORMALIZE,
    val algorithmVersion: Int = CURRENT_ALGORITHM_VERSION,
    val stageCursorId: Long = 0L,
    val lastRecordingId: Long = 0L,
    val progress: IdentityBackfillProgress = IdentityBackfillProgress(),
    val legacyLuoxueCleaned: Boolean = false
) {
    companion object {
        const val CURRENT_ALGORITHM_VERSION = 2
    }
}

data class IdentityBackfillBatchResult(
    val checkpoint: IdentityBackfillCheckpoint,
    val complete: Boolean
)

/** Offline, bounded and idempotent canonical maintenance used by WorkManager and settings. */
class IdentityBackfillCoordinator(
    private val database: YukineDatabase
) {
    private val dao = database.musicIdentityDao()

    fun runBatch(
        checkpoint: IdentityBackfillCheckpoint,
        maxRecordings: Int = DEFAULT_BATCH_SIZE
    ): IdentityBackfillBatchResult {
        val batchSize = maxRecordings.coerceIn(1, DEFAULT_BATCH_SIZE)
        val current = checkpoint.takeIf {
            it.algorithmVersion == IdentityBackfillCheckpoint.CURRENT_ALGORITHM_VERSION
        } ?: IdentityBackfillCheckpoint()
        return when (current.stage) {
            IdentityBackfillStage.NORMALIZE -> normalizeBatch(current, batchSize)
            IdentityBackfillStage.CLASSIFY -> classifyBatch(current, batchSize)
            IdentityBackfillStage.INGEST -> ingestBatch(current, batchSize)
            IdentityBackfillStage.LX_CLEANUP -> cleanupBatch(current)
            IdentityBackfillStage.VALIDATE -> validateBatch(current)
            IdentityBackfillStage.COMPLETE -> IdentityBackfillBatchResult(current, complete = true)
        }
    }

    private fun normalizeBatch(
        checkpoint: IdentityBackfillCheckpoint,
        batchSize: Int
    ): IdentityBackfillBatchResult {
        val sources = dao.sourcesAfter(checkpoint.stageCursorId, batchSize)
        if (sources.isEmpty()) {
            return IdentityBackfillBatchResult(
                checkpoint.copy(stage = IdentityBackfillStage.CLASSIFY, stageCursorId = 0L),
                complete = false
            )
        }
        var normalized = 0
        var merged = 0
        var relations = 0
        var errors = 0
        sources.forEach { source ->
            runCatching { normalizeSource(source) }
                .onSuccess { result ->
                    normalized += result.normalized
                    merged += result.merged
                    relations += result.ownerCollisions
                }
                .onFailure { errors += 1 }
        }
        val progress = checkpoint.progress.copy(
            normalized = checkpoint.progress.normalized + normalized,
            merged = checkpoint.progress.merged + merged,
            relations = checkpoint.progress.relations + relations,
            errors = checkpoint.progress.errors + errors
        )
        return IdentityBackfillBatchResult(
            checkpoint.copy(
                stageCursorId = sources.last().sourceId ?: checkpoint.stageCursorId,
                progress = progress
            ),
            complete = false
        )
    }

    private fun normalizeSource(source: TrackSourceMappingEntity): NormalizeResult {
        val sourceId = requireNotNull(source.sourceId)
        val normalized = ProviderRolePolicy.normalize(source.provider)
        if (normalized == source.provider || normalized.isBlank()) return NormalizeResult()
        val owner = dao.source(normalized, source.providerTrackId)
        if (owner == null) {
            check(dao.normalizeSourceProvider(sourceId, source.provider, normalized) == 1)
            return NormalizeResult(normalized = 1)
        }
        if (owner.recordingId == source.recordingId) {
            consolidateAliasSource(source, owner)
            return NormalizeResult(normalized = 1)
        }

        val write = ProviderSourceIdentityWriter(dao).saveUnverifiedCandidate(
            source.recordingId,
            normalized,
            source.providerTrackId,
            source.title,
            source.artist,
            source.album,
            source.durationMs,
            source.confidence
        )
        val ownerId = write.ownerRecordingId
        val merged = if (ownerId == null) 0 else {
            SourceIdentityIngestor(database).ingestRecordings(listOf(source.recordingId, ownerId))
        }
        val currentAlias = dao.source(sourceId)
        val currentOwner = dao.source(normalized, source.providerTrackId)
        if (currentAlias != null && currentOwner != null && currentAlias.recordingId == currentOwner.recordingId) {
            consolidateAliasSource(currentAlias, currentOwner)
            return NormalizeResult(normalized = 1, merged = merged, ownerCollisions = 1)
        }
        return NormalizeResult(merged = merged, ownerCollisions = 1)
    }

    private fun consolidateAliasSource(
        alias: TrackSourceMappingEntity,
        canonical: TrackSourceMappingEntity
    ) {
        val aliasId = requireNotNull(alias.sourceId)
        database.runInTransaction {
            check(dao.deleteSource(aliasId) == 1)
            dao.upsert(
                canonical.copy(
                    localTrackId = canonical.localTrackId ?: alias.localTrackId,
                    dataPath = canonical.dataPath.ifBlank { alias.dataPath },
                    title = canonical.title.ifBlank { alias.title },
                    artist = canonical.artist.ifBlank { alias.artist },
                    album = canonical.album.ifBlank { alias.album },
                    durationMs = canonical.durationMs.takeIf { it > 0L } ?: alias.durationMs,
                    playable = canonical.playable || alias.playable,
                    matchStatus = strongerStatus(canonical.matchStatus, alias.matchStatus),
                    confidence = maxOf(canonical.confidence, alias.confidence),
                    lastSuccessfulAt = maxOf(canonical.lastSuccessfulAt, alias.lastSuccessfulAt),
                    lastVerifiedAt = maxOf(canonical.lastVerifiedAt, alias.lastVerifiedAt)
                )
            )
        }
    }

    private fun classifyBatch(
        checkpoint: IdentityBackfillCheckpoint,
        batchSize: Int
    ): IdentityBackfillBatchResult {
        val missing = dao.tracksWithoutIdentitySource(batchSize)
        if (missing.isEmpty()) {
            val total = dao.recordingCount()
            return IdentityBackfillBatchResult(
                checkpoint.copy(
                    stage = IdentityBackfillStage.INGEST,
                    stageCursorId = 0L,
                    lastRecordingId = 0L,
                    progress = checkpoint.progress.copy(total = total)
                ),
                complete = false
            )
        }
        database.runInTransaction {
            OfflineMusicIdentityStore(dao).ensureTracks(missing, System.currentTimeMillis())
        }
        return IdentityBackfillBatchResult(
            checkpoint.copy(
                progress = checkpoint.progress.copy(
                    classified = checkpoint.progress.classified + missing.size
                )
            ),
            complete = false
        )
    }

    private fun ingestBatch(
        checkpoint: IdentityBackfillCheckpoint,
        batchSize: Int
    ): IdentityBackfillBatchResult {
        val recordingIds = dao.recordingIdsAfter(checkpoint.lastRecordingId, batchSize)
        if (recordingIds.isEmpty()) {
            return IdentityBackfillBatchResult(
                checkpoint.copy(
                    stage = IdentityBackfillStage.LX_CLEANUP,
                    stageCursorId = 0L,
                    progress = checkpoint.progress.copy(pending = dao.pendingRecordingCandidateCount())
                ),
                complete = false
            )
        }
        val merged = SourceIdentityIngestor(database).ingestRecordings(recordingIds)
        val lastRecordingId = recordingIds.maxOrNull() ?: checkpoint.lastRecordingId
        val total = checkpoint.progress.total.takeIf { it > 0 } ?: dao.recordingCount()
        return IdentityBackfillBatchResult(
            checkpoint.copy(
                lastRecordingId = lastRecordingId,
                progress = checkpoint.progress.copy(
                    total = total,
                    processed = (checkpoint.progress.processed + recordingIds.size).coerceAtMost(total),
                    merged = checkpoint.progress.merged + merged,
                    pending = dao.pendingRecordingCandidateCount()
                )
            ),
            complete = false
        )
    }

    private fun cleanupBatch(checkpoint: IdentityBackfillCheckpoint): IdentityBackfillBatchResult {
        val cleanup = cleanupLegacyLuoxue()
        return IdentityBackfillBatchResult(
            checkpoint.copy(
                stage = IdentityBackfillStage.VALIDATE,
                legacyLuoxueCleaned = true,
                progress = checkpoint.progress.copy(
                    merged = checkpoint.progress.merged + cleanup.merged,
                    lxMigrated = checkpoint.progress.lxMigrated + cleanup.migrated,
                    lxDeleted = checkpoint.progress.lxDeleted + cleanup.deleted,
                    deleted = checkpoint.progress.deleted + cleanup.deleted
                )
            ),
            complete = false
        )
    }

    private fun cleanupLegacyLuoxue(): LegacyCleanupResult {
        val originalSources = dao.sourcesForProvider(LUOXUE_PROVIDER)
        if (originalSources.isEmpty()) {
            database.runInTransaction {
                dao.deleteCandidatesForProvider(LUOXUE_PROVIDER)
                database.streamingTrackMatchDao().deleteProvider(LUOXUE_PROVIDER)
            }
            return LegacyCleanupResult()
        }

        var migrated = 0
        var merged = 0
        originalSources.forEach { source ->
            val exact = exactCanonicalIdentity(source.providerTrackId) ?: return@forEach
            val result = ProviderSourceIdentityWriter(dao).saveUnverifiedCandidate(
                source.recordingId,
                exact.first,
                exact.second,
                source.title,
                source.artist,
                source.album,
                source.durationMs,
                source.confidence
            )
            val ownerId = result.ownerRecordingId ?: return@forEach
            val mergeCount = SourceIdentityIngestor(database)
                .ingestRecordings(listOf(source.recordingId, ownerId))
            if (mergeCount > 0) {
                migrated += 1
                merged += mergeCount
            }
        }

        val remainingSources = dao.sourcesForProvider(LUOXUE_PROVIDER)
        val affectedRecordingIds = remainingSources.map { it.recordingId }.distinct()
        val operationStore = IdentityOperationStore(database)
        database.runInTransaction {
            val before = operationStore.capture(affectedRecordingIds)
            val localTrackIds = remainingSources.mapNotNull { it.localTrackId }.distinct()
            if (localTrackIds.isNotEmpty()) {
                LibraryRepository(database).deleteTracksInCurrentTransaction(localTrackIds)
            }
            dao.deleteSourcesForProvider(LUOXUE_PROVIDER)
            dao.deleteCandidatesForProvider(LUOXUE_PROVIDER)
            database.streamingTrackMatchDao().deleteProvider(LUOXUE_PROVIDER)
            OfflineMusicIdentityStore(database).pruneMissingTracks()
            operationStore.recordSnapshotAudit(OPERATION_TYPE, affectedRecordingIds, before)
        }
        return LegacyCleanupResult(migrated, remainingSources.size, merged)
    }

    private fun validateBatch(checkpoint: IdentityBackfillCheckpoint): IdentityBackfillBatchResult {
        val repaired = database.runInTransaction<Int> {
            dao.deleteDanglingCandidates() +
                dao.deleteDanglingJobs() +
                dao.clearMissingActiveSources() +
                dao.deleteSelfOwnedPendingRecordingCandidates()
        }
        val complete = checkpoint.copy(
            stage = IdentityBackfillStage.COMPLETE,
            progress = checkpoint.progress.copy(
                pending = dao.pendingRecordingCandidateCount(),
                deleted = checkpoint.progress.deleted + repaired
            )
        )
        return IdentityBackfillBatchResult(complete, complete = true)
    }

    private fun exactCanonicalIdentity(providerTrackId: String): Pair<String, String>? {
        val separator = providerTrackId.indexOf(':')
        if (separator <= 0 || separator == providerTrackId.lastIndex) return null
        val provider = ProviderRolePolicy.normalize(providerTrackId.substring(0, separator))
        if (provider !in setOf("netease", "qqmusic")) return null
        val id = providerTrackId.substring(separator + 1).substringBefore('?').substringBefore('#').trim()
        return id.takeIf { it.isNotBlank() }?.let { provider to it }
    }

    private fun strongerStatus(first: String, second: String): String {
        val rank = mapOf("REJECTED" to 0, "UNRESOLVED" to 1, "CANDIDATE" to 2, "CONFIRMED" to 3)
        return if ((rank[first] ?: 1) >= (rank[second] ?: 1)) first else second
    }

    private data class NormalizeResult(
        val normalized: Int = 0,
        val merged: Int = 0,
        val ownerCollisions: Int = 0
    )

    private data class LegacyCleanupResult(
        val migrated: Int = 0,
        val deleted: Int = 0,
        val merged: Int = 0
    )

    private companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val LUOXUE_PROVIDER = "luoxue"
        const val OPERATION_TYPE = "CLEANUP_LEGACY_LUOXUE"
    }
}
