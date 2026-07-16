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
import app.yukine.data.room.YukineDatabase
import app.yukine.playback.IdentityEnhancementPlaybackGate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdentityBackfillWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (IdentityEnhancementPlaybackGate.shouldDefer()) {
            Log.i(TAG, "Deferring canonical identity backfill while the app is visible or playback is active")
            return@withContext Result.retry()
        }
        runCatching {
            val store = IdentityBackfillCheckpointStore(applicationContext)
            val result = IdentityBackfillCoordinator(
                YukineDatabase.getInstance(applicationContext)
            ).runBatch(store.load())
            store.save(result.checkpoint)
            setProgress(result.checkpoint.progress.toWorkData())
            if (result.complete) Result.success(result.checkpoint.progress.toWorkData()) else Result.retry()
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
                .apply { if (automatic) setRequiresDeviceIdle(true) }
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
        lastRecordingId = preferences.getLong(KEY_LAST_RECORDING_ID, 0L),
        progress = IdentityBackfillProgress(
            total = preferences.getInt(KEY_TOTAL, 0),
            processed = preferences.getInt(KEY_PROCESSED, 0),
            merged = preferences.getInt(KEY_MERGED, 0),
            pending = preferences.getInt(KEY_PENDING, 0),
            lxMigrated = preferences.getInt(KEY_LX_MIGRATED, 0),
            lxDeleted = preferences.getInt(KEY_LX_DELETED, 0)
        ),
        legacyLuoxueCleaned = preferences.getBoolean(KEY_LX_CLEANED, false)
    )

    fun save(checkpoint: IdentityBackfillCheckpoint) {
        val progress = checkpoint.progress
        preferences.edit()
            .putLong(KEY_LAST_RECORDING_ID, checkpoint.lastRecordingId)
            .putInt(KEY_TOTAL, progress.total)
            .putInt(KEY_PROCESSED, progress.processed)
            .putInt(KEY_MERGED, progress.merged)
            .putInt(KEY_PENDING, progress.pending)
            .putInt(KEY_LX_MIGRATED, progress.lxMigrated)
            .putInt(KEY_LX_DELETED, progress.lxDeleted)
            .putBoolean(KEY_LX_CLEANED, checkpoint.legacyLuoxueCleaned)
            .apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "canonical_identity_backfill_v1"
        const val KEY_LAST_RECORDING_ID = "last_recording_id"
        const val KEY_TOTAL = "total"
        const val KEY_PROCESSED = "processed"
        const val KEY_MERGED = "merged"
        const val KEY_PENDING = "pending"
        const val KEY_LX_MIGRATED = "lx_migrated"
        const val KEY_LX_DELETED = "lx_deleted"
        const val KEY_LX_CLEANED = "lx_cleaned"
    }
}

private fun IdentityBackfillProgress.toWorkData(): Data = Data.Builder()
    .putInt("total", total)
    .putInt("processed", processed)
    .putInt("merged", merged)
    .putInt("pending", pending)
    .putInt("lxMigrated", lxMigrated)
    .putInt("lxDeleted", lxDeleted)
    .build()
