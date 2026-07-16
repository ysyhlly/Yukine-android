package app.yukine.data

import app.yukine.data.room.YukineDatabase
import app.yukine.identity.MusicIdentityDiagnostics

data class IdentityBackfillProgress(
    val total: Int = 0,
    val processed: Int = 0,
    val merged: Int = 0,
    val pending: Int = 0,
    val lxMigrated: Int = 0,
    val lxDeleted: Int = 0
)

data class IdentityBackfillCheckpoint(
    val lastRecordingId: Long = 0L,
    val progress: IdentityBackfillProgress = IdentityBackfillProgress(),
    val legacyLuoxueCleaned: Boolean = false
)

data class IdentityBackfillBatchResult(
    val checkpoint: IdentityBackfillCheckpoint,
    val complete: Boolean
)

/** Purely offline, idempotent canonical identity maintenance used by WorkManager and settings. */
class IdentityBackfillCoordinator(
    private val database: YukineDatabase
) {
    private val dao = database.musicIdentityDao()

    fun runBatch(
        checkpoint: IdentityBackfillCheckpoint,
        maxRecordings: Int = DEFAULT_BATCH_SIZE
    ): IdentityBackfillBatchResult {
        val startedAt = System.nanoTime()
        var current = checkpoint
        if (!current.legacyLuoxueCleaned) {
            val cleanup = cleanupLegacyLuoxue()
            current = current.copy(
                legacyLuoxueCleaned = true,
                progress = current.progress.copy(
                    lxMigrated = current.progress.lxMigrated + cleanup.migrated,
                    lxDeleted = current.progress.lxDeleted + cleanup.deleted
                )
            )
        }

        val total = current.progress.total.takeIf { it > 0 } ?: dao.recordingCount()
        val recordingIds = dao.recordingIdsAfter(
            current.lastRecordingId,
            maxRecordings.coerceIn(1, DEFAULT_BATCH_SIZE)
        )
        if (recordingIds.isEmpty()) {
            val progress = current.progress.copy(
                total = total,
                pending = dao.pendingRecordingCandidateCount()
            )
            return IdentityBackfillBatchResult(current.copy(progress = progress), complete = true)
        }

        val merged = SourceIdentityIngestor(database).ingestRecordings(recordingIds)
        val lastRecordingId = recordingIds.maxOrNull() ?: current.lastRecordingId
        val progress = current.progress.copy(
            total = total,
            processed = (current.progress.processed + recordingIds.size).coerceAtMost(total),
            merged = current.progress.merged + merged,
            pending = dao.pendingRecordingCandidateCount()
        )
        val reachedTimeSlice = elapsedMs(startedAt) >= MAX_RUN_SLICE_MS
        val complete = !reachedTimeSlice && dao.recordingIdsAfter(lastRecordingId, 1).isEmpty()
        return IdentityBackfillBatchResult(
            checkpoint = current.copy(lastRecordingId = lastRecordingId, progress = progress),
            complete = complete
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

        val originalRecordingIds = originalSources.map { it.recordingId }.distinct()
        // A legacy LX carrier that already shares a canonical recording with a real identity
        // source needs no matching pass. Re-ingesting those recordings scans the global candidate
        // graph during app startup and can starve Room's single TRUNCATE-mode connection pool.
        val legacyOnlyRecordingIds = originalRecordingIds.filter { recordingId ->
            dao.sources(recordingId).none { source -> source.provider != LUOXUE_PROVIDER }
        }
        val migrated = if (legacyOnlyRecordingIds.isEmpty()) {
            0
        } else {
            SourceIdentityIngestor(
                database,
                MusicIdentityDiagnostics.process(),
                includeLegacyLuoxue = true
            ).ingestRecordings(legacyOnlyRecordingIds)
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
            operationStore.recordSnapshotAudit(
                operationType = OPERATION_TYPE,
                recordingIds = affectedRecordingIds,
                before = before
            )
        }
        return LegacyCleanupResult(migrated = migrated, deleted = remainingSources.size)
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000L

    private data class LegacyCleanupResult(
        val migrated: Int = 0,
        val deleted: Int = 0
    )

    private companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val MAX_RUN_SLICE_MS = 10_000L
        const val LUOXUE_PROVIDER = "luoxue"
        const val OPERATION_TYPE = "CLEANUP_LEGACY_LUOXUE"
    }
}
