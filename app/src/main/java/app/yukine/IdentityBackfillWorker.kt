package app.yukine

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yukine.data.IdentityBackfillCheckpoint
import app.yukine.data.IdentityBackfillCoordinator
import app.yukine.data.IdentityBackfillProgress
import app.yukine.data.IdentityBackfillStage
import app.yukine.data.room.YukineDatabase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdentityBackfillWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val store = IdentityBackfillCheckpointStore(applicationContext)
            val result = IdentityBackfillCoordinator(
                YukineDatabase.getInstance(applicationContext)
            ).runBatch(store.load())
            store.save(result.checkpoint)
            setProgress(result.checkpoint.toWorkData())
            if (result.complete) Result.success(result.checkpoint.toWorkData()) else Result.retry()
        }.getOrElse { error ->
            Log.w(TAG, "Canonical identity backfill failed", error)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "IdentityBackfill"
    }
}

object IdentityBackfillScheduler {
    const val UNIQUE_WORK_NAME = "canonical_identity_backfill_v1"

    fun scheduleAutomatic(context: Context) {
        // REPLACE also upgrades an already-persisted eager request from the first v1 build.
        enqueue(context, ExistingWorkPolicy.REPLACE, automatic = true)
    }

    /** Manual rebuild replaces a completed task; callers should use restart=false to reuse active work. */
    fun scheduleManual(context: Context, restart: Boolean = false) {
        if (restart) IdentityBackfillCheckpointStore(context).reset()
        enqueue(
            context,
            if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            automatic = false
        )
    }

    /** Must be called from a background executor; active work is reused instead of duplicated. */
    fun rebuildOrReuseBlocking(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val active = manager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
            .any { !it.state.isFinished }
        if (active) {
            enqueue(context, ExistingWorkPolicy.KEEP, automatic = false)
        } else {
            IdentityBackfillCheckpointStore(context).reset()
            enqueue(context, ExistingWorkPolicy.REPLACE, automatic = false)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun enqueue(
        context: Context,
        policy: ExistingWorkPolicy,
        automatic: Boolean
    ) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val requestBuilder = OneTimeWorkRequestBuilder<IdentityBackfillWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            if (automatic) {
                requestBuilder.setInitialDelay(AUTOMATIC_INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            }
            val request = requestBuilder.build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                request
            )
        }.onFailure { error -> Log.w("IdentityBackfill", "Unable to schedule backfill", error) }
    }

    private const val AUTOMATIC_INITIAL_DELAY_SECONDS = 30L
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
    }
}

private fun IdentityBackfillCheckpoint.toWorkData(): Data = Data.Builder()
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
    .build()
