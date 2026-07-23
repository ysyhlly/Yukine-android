package app.yukine

import android.content.Context
import app.yukine.diagnostics.DiagnosticLog
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yukine.data.IdentityBackfillCheckpoint
import app.yukine.data.IdentityBackfillCoordinator
import app.yukine.data.IdentityBackfillProgress
import app.yukine.data.IdentityBackfillStage
import app.yukine.data.LibraryDedupMaintenance
import app.yukine.data.room.YukineDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class IdentityBackfillWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = IdentityBackfillCheckpointStore(applicationContext)
        try {
            store.markWork(id.toString(), IdentityBackfillRuntimeState.RUNNING)
            val database = YukineDatabase.getInstance(applicationContext)
            val maintenance = LibraryDedupMaintenance(database)
            val expectedMode = inputData.getString(KEY_DEDUP_MODE)
            val expectedGeneration = inputData.getLong(KEY_DEDUP_GENERATION, -1L)
            if ((expectedMode != null && expectedMode != maintenance.currentMode().name) ||
                (expectedGeneration >= 0L && expectedGeneration != maintenance.currentGeneration())
            ) {
                store.updateRuntimeState(IdentityBackfillRuntimeState.COMPLETED)
                Result.success(store.load().toWorkData())
            } else {
                val maintenanceResult = maintenance.prepareForCurrentMode()
                val coordinator = IdentityBackfillCoordinator(database)
                var checkpoint = store.load()
                var completedResult: Result? = null
                while (completedResult == null) {
                    currentCoroutineContext().ensureActive()
                    val batch = coordinator.runBatch(checkpoint)
                    checkpoint = batch.checkpoint
                    store.save(checkpoint)
                    setProgress(checkpoint.toWorkData())
                    if (batch.complete) {
                        val output = checkpoint.toWorkData(
                            reverted = maintenanceResult.reverted,
                            reviewRequired = maintenanceResult.reviewRequired
                        )
                        store.updateRuntimeState(IdentityBackfillRuntimeState.COMPLETED)
                        completedResult = Result.success(output)
                    } else {
                        yield()
                    }
                }
                completedResult
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiagnosticLog.w(TAG, "Canonical identity backfill failed", error)
            val message = error.message?.trim().orEmpty()
                .ifBlank { error.javaClass.simpleName }
                .take(MAX_ERROR_LENGTH)
            store.updateRuntimeState(IdentityBackfillRuntimeState.FAILED, message)
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR, message)
                    .build()
            )
        }
    }

    private companion object {
        const val TAG = "IdentityBackfill"
        const val KEY_DEDUP_MODE = "dedupMode"
        const val KEY_DEDUP_GENERATION = "dedupGeneration"
        const val KEY_ERROR = "error"
        const val MAX_ERROR_LENGTH = 240
    }
}

enum class IdentityBackfillScheduleKind {
    ENQUEUED,
    ALREADY_ACTIVE,
    FAILED
}

data class IdentityBackfillScheduleResult(
    val kind: IdentityBackfillScheduleKind,
    val errorMessage: String = ""
)

enum class IdentityBackfillRuntimeState {
    IDLE,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class IdentityBackfillRuntimeStatus(
    val state: IdentityBackfillRuntimeState = IdentityBackfillRuntimeState.IDLE,
    val workId: String = "",
    val errorMessage: String = "",
    val updatedAt: Long = 0L
)

object IdentityBackfillScheduler {
    const val UNIQUE_WORK_NAME = "canonical_identity_backfill_v1"

    fun scheduleAutomatic(context: Context): IdentityBackfillScheduleResult {
        // Automatic startup must not replace an active manual rebuild that carries a
        // mode/generation snapshot. The worker resolves the current mode on its IO dispatcher.
        return enqueue(
            context,
            ExistingWorkPolicy.KEEP,
            awaitResult = false,
            captureDedupSnapshot = false
        )
    }

    /** Manual rebuild replaces a completed task; callers should use restart=false to reuse active work. */
    fun scheduleManual(
        context: Context,
        restart: Boolean = false
    ): IdentityBackfillScheduleResult {
        if (!restart) return rebuildOrReuseBlocking(context)
        IdentityBackfillCheckpointStore(context).reset()
        return enqueue(
            context,
            ExistingWorkPolicy.REPLACE,
            awaitResult = true,
            captureDedupSnapshot = true
        )
    }

    /** Must be called from a background executor; active work is reused instead of duplicated. */
    fun rebuildOrReuseBlocking(context: Context): IdentityBackfillScheduleResult {
        val appContext = context.applicationContext
        val store = IdentityBackfillCheckpointStore(appContext)
        return runCatching {
            val manager = WorkManager.getInstance(appContext)
            val active = manager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
                .firstOrNull { !it.state.isFinished }
            if (active != null) {
                store.markWork(
                    active.id.toString(),
                    active.state.toIdentityBackfillRuntimeState(),
                    active.outputData.getString("error").orEmpty()
                )
                IdentityBackfillScheduleResult(IdentityBackfillScheduleKind.ALREADY_ACTIVE)
            } else {
                store.reset()
                enqueue(
                    appContext,
                    ExistingWorkPolicy.REPLACE,
                    awaitResult = true,
                    captureDedupSnapshot = true
                )
            }
        }.getOrElse { error ->
            val message = error.identityBackfillMessage()
            DiagnosticLog.w("IdentityBackfill", "Unable to query existing backfill", error)
            store.updateRuntimeState(IdentityBackfillRuntimeState.FAILED, message)
            IdentityBackfillScheduleResult(IdentityBackfillScheduleKind.FAILED, message)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        IdentityBackfillCheckpointStore(appContext)
            .updateRuntimeState(IdentityBackfillRuntimeState.CANCELLED)
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun syncRuntimeStatus(context: Context, workInfos: List<WorkInfo>) {
        val store = IdentityBackfillCheckpointStore(context.applicationContext)
        val current = store.runtimeStatus()
        val workInfo = workInfos.firstOrNull { it.id.toString() == current.workId }
            ?: return
        store.markWork(
            workInfo.id.toString(),
            workInfo.state.toIdentityBackfillRuntimeState(),
            workInfo.outputData.getString("error").orEmpty()
        )
    }

    private fun enqueue(
        context: Context,
        policy: ExistingWorkPolicy,
        awaitResult: Boolean,
        captureDedupSnapshot: Boolean
    ): IdentityBackfillScheduleResult {
        val appContext = context.applicationContext
        val store = IdentityBackfillCheckpointStore(appContext)
        return runCatching {
            val requestBuilder = OneTimeWorkRequestBuilder<IdentityBackfillWorker>()
            if (captureDedupSnapshot) {
                val maintenance = LibraryDedupMaintenance(
                    YukineDatabase.getInstance(appContext)
                )
                requestBuilder.setInputData(
                    Data.Builder()
                        .putString("dedupMode", maintenance.currentMode().name)
                        .putLong("dedupGeneration", maintenance.currentGeneration())
                        .build()
                )
            }
            val request = requestBuilder.build()
            if (policy != ExistingWorkPolicy.KEEP) {
                store.markWork(
                    request.id.toString(),
                    IdentityBackfillRuntimeState.QUEUED
                )
            }
            val operation = WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                request
            )
            if (awaitResult) operation.result.get()
            IdentityBackfillScheduleResult(IdentityBackfillScheduleKind.ENQUEUED)
        }.getOrElse { error ->
            val message = error.identityBackfillMessage()
            DiagnosticLog.w("IdentityBackfill", "Unable to schedule backfill", error)
            store.updateRuntimeState(IdentityBackfillRuntimeState.FAILED, message)
            IdentityBackfillScheduleResult(IdentityBackfillScheduleKind.FAILED, message)
        }
    }

}

internal class IdentityBackfillCheckpointStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): IdentityBackfillCheckpoint = IdentityBackfillCheckpoint(
        stage = runCatching {
            IdentityBackfillStage.valueOf(
                preferences.getString(KEY_STAGE, IdentityBackfillStage.NORMALIZE.name)
                    ?: IdentityBackfillStage.NORMALIZE.name
            )
        }.getOrDefault(IdentityBackfillStage.NORMALIZE),
        algorithmVersion = preferences.getInt(
            KEY_ALGORITHM_VERSION,
            IdentityBackfillCheckpoint.CURRENT_ALGORITHM_VERSION
        ),
        stageCursorId = preferences.getLong(KEY_STAGE_CURSOR_ID, 0L),
        lastRecordingId = preferences.getLong(KEY_LAST_RECORDING_ID, 0L),
        progress = IdentityBackfillProgress(
            total = preferences.getInt(KEY_TOTAL, 0),
            processed = preferences.getInt(KEY_PROCESSED, 0),
            normalized = preferences.getInt(KEY_NORMALIZED, 0),
            classified = preferences.getInt(KEY_CLASSIFIED, 0),
            merged = preferences.getInt(KEY_MERGED, 0),
            relations = preferences.getInt(KEY_RELATIONS, 0),
            pending = preferences.getInt(KEY_PENDING, 0),
            lxMigrated = preferences.getInt(KEY_LX_MIGRATED, 0),
            lxDeleted = preferences.getInt(KEY_LX_DELETED, 0),
            deleted = preferences.getInt(KEY_DELETED, 0),
            errors = preferences.getInt(KEY_ERRORS, 0)
        ),
        legacyLuoxueCleaned = preferences.getBoolean(KEY_LX_CLEANED, false)
    )

    fun save(checkpoint: IdentityBackfillCheckpoint) {
        val progress = checkpoint.progress
        check(preferences.edit()
            .putString(KEY_STAGE, checkpoint.stage.name)
            .putInt(KEY_ALGORITHM_VERSION, checkpoint.algorithmVersion)
            .putLong(KEY_STAGE_CURSOR_ID, checkpoint.stageCursorId)
            .putLong(KEY_LAST_RECORDING_ID, checkpoint.lastRecordingId)
            .putInt(KEY_TOTAL, progress.total)
            .putInt(KEY_PROCESSED, progress.processed)
            .putInt(KEY_NORMALIZED, progress.normalized)
            .putInt(KEY_CLASSIFIED, progress.classified)
            .putInt(KEY_MERGED, progress.merged)
            .putInt(KEY_RELATIONS, progress.relations)
            .putInt(KEY_PENDING, progress.pending)
            .putInt(KEY_LX_MIGRATED, progress.lxMigrated)
            .putInt(KEY_LX_DELETED, progress.lxDeleted)
            .putInt(KEY_DELETED, progress.deleted)
            .putInt(KEY_ERRORS, progress.errors)
            .putBoolean(KEY_LX_CLEANED, checkpoint.legacyLuoxueCleaned)
            .commit()) { "Unable to persist identity backfill checkpoint" }
    }

    fun runtimeStatus(): IdentityBackfillRuntimeStatus {
        val stateName = preferences.getString(KEY_RUNTIME_STATE, null)
        val state = runCatching {
            IdentityBackfillRuntimeState.valueOf(stateName.orEmpty())
        }.getOrElse {
            if (preferences.getString(KEY_STAGE, null) == IdentityBackfillStage.COMPLETE.name) {
                IdentityBackfillRuntimeState.COMPLETED
            } else {
                IdentityBackfillRuntimeState.IDLE
            }
        }
        return IdentityBackfillRuntimeStatus(
            state = state,
            workId = preferences.getString(KEY_WORK_ID, "").orEmpty(),
            errorMessage = preferences.getString(KEY_RUNTIME_ERROR, "").orEmpty(),
            updatedAt = preferences.getLong(KEY_RUNTIME_UPDATED_AT, 0L)
        )
    }

    fun markWork(
        workId: String,
        state: IdentityBackfillRuntimeState,
        errorMessage: String = ""
    ) {
        check(preferences.edit()
            .putString(KEY_WORK_ID, workId)
            .putString(KEY_RUNTIME_STATE, state.name)
            .putString(KEY_RUNTIME_ERROR, errorMessage.take(MAX_RUNTIME_ERROR_LENGTH))
            .putLong(KEY_RUNTIME_UPDATED_AT, System.currentTimeMillis())
            .commit()) { "Unable to persist identity backfill runtime state" }
    }

    fun updateRuntimeState(
        state: IdentityBackfillRuntimeState,
        errorMessage: String = ""
    ) {
        val current = runtimeStatus()
        markWork(current.workId, state, errorMessage)
    }

    fun reset() {
        check(preferences.edit().clear().commit()) { "Unable to reset identity backfill checkpoint" }
    }

    private companion object {
        const val PREFS_NAME = "canonical_identity_backfill_v1"
        const val KEY_STAGE = "stage"
        const val KEY_ALGORITHM_VERSION = "algorithm_version"
        const val KEY_STAGE_CURSOR_ID = "stage_cursor_id"
        const val KEY_LAST_RECORDING_ID = "last_recording_id"
        const val KEY_TOTAL = "total"
        const val KEY_PROCESSED = "processed"
        const val KEY_NORMALIZED = "normalized"
        const val KEY_CLASSIFIED = "classified"
        const val KEY_MERGED = "merged"
        const val KEY_RELATIONS = "relations"
        const val KEY_PENDING = "pending"
        const val KEY_LX_MIGRATED = "lx_migrated"
        const val KEY_LX_DELETED = "lx_deleted"
        const val KEY_DELETED = "deleted"
        const val KEY_ERRORS = "errors"
        const val KEY_LX_CLEANED = "lx_cleaned"
        const val KEY_WORK_ID = "work_id"
        const val KEY_RUNTIME_STATE = "runtime_state"
        const val KEY_RUNTIME_ERROR = "runtime_error"
        const val KEY_RUNTIME_UPDATED_AT = "runtime_updated_at"
        const val MAX_RUNTIME_ERROR_LENGTH = 240
    }
}

internal fun WorkInfo.State.toIdentityBackfillRuntimeState(): IdentityBackfillRuntimeState = when (this) {
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.BLOCKED -> IdentityBackfillRuntimeState.QUEUED
    WorkInfo.State.RUNNING -> IdentityBackfillRuntimeState.RUNNING
    WorkInfo.State.SUCCEEDED -> IdentityBackfillRuntimeState.COMPLETED
    WorkInfo.State.FAILED -> IdentityBackfillRuntimeState.FAILED
    WorkInfo.State.CANCELLED -> IdentityBackfillRuntimeState.CANCELLED
}

private fun Throwable.identityBackfillMessage(): String =
    message?.trim().orEmpty().ifBlank { javaClass.simpleName }.take(240)

private fun IdentityBackfillCheckpoint.toWorkData(
    reverted: Int = 0,
    reviewRequired: Int = 0
): Data = Data.Builder()
    .putString("stage", stage.name)
    .putInt("algorithmVersion", algorithmVersion)
    .putInt("total", progress.total)
    .putInt("processed", progress.processed)
    .putInt("normalized", progress.normalized)
    .putInt("classified", progress.classified)
    .putInt("merged", progress.merged)
    .putInt("relations", progress.relations)
    .putInt("pending", progress.pending)
    .putInt("lxMigrated", progress.lxMigrated)
    .putInt("lxDeleted", progress.lxDeleted)
    .putInt("deleted", progress.deleted)
    .putInt("errors", progress.errors)
    .putInt("dedupReverted", reverted)
    .putInt("dedupReviewRequired", reviewRequired)
    .build()
