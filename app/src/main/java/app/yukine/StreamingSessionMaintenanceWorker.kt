package app.yukine

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yukine.streaming.LocalStreamingAuthStore
import app.yukine.streaming.RemoteStreamingGateway
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import java.util.concurrent.TimeUnit

/**
 * Low-frequency safety net for locally persisted music sessions. Foreground maintenance remains
 * the primary path; WorkManager only gives an already-saved session a chance to renew while the
 * user has not opened the app for a while.
 */
class StreamingSessionMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val authStore = LocalStreamingAuthStore(context)
        val gateway = RemoteStreamingGateway(
            endpointBaseUrl = StreamingGatewaySettingsStore(context).endpoint(),
            localAuthStore = authStore,
            webCookieSessionSource = AndroidStreamingWebCookieSessionSource()
        )
        val repository = StreamingRepository(gateway)
        var hasTransientFailure = false
        listOf(StreamingProviderName.NETEASE, StreamingProviderName.QQ_MUSIC).forEach { provider ->
            if (!authStore.hasStoredCredential(provider)) {
                return@forEach
            }
            val result = runCatching { repository.refreshAuthSession(provider) }
            if (result.isFailure) {
                hasTransientFailure = true
            }
        }
        return if (hasTransientFailure) Result.retry() else Result.success()
    }
}

object StreamingSessionMaintenanceScheduler {
    private const val UNIQUE_WORK_NAME = "streaming_session_maintenance"
    private const val TAG = "StreamingSessionWork"

    fun schedule(context: Context) {
        // WorkManager is intentionally a best-effort safety net. Some host/test environments do
        // not install its default initializer; foreground session maintenance remains available.
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<StreamingSessionMaintenanceWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to schedule streaming session maintenance", error)
        }
    }
}
