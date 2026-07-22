package app.yukine

import android.content.Context
import app.yukine.diagnostics.DiagnosticLog
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yukine.data.MusicLibraryRepository
import app.yukine.streaming.KugouExperimentalSyncStore
import app.yukine.streaming.LocalStreamingAuthStore
import app.yukine.streaming.StreamingGatewayException
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaylistSyncSnapshot
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepositoryFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Network-constrained compensation sync for linked Kugou playlists.
 *
 * Until the private-write contract gate is verified, this worker intentionally performs only
 * remote-to-local reconciliation. Enabling the experiment never turns guessed account endpoints
 * into writes.
 */
class KugouPlaylistSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = singleFlight.withLock {
        withContext(Dispatchers.IO) {
            val experimentStore = KugouExperimentalSyncStore(applicationContext)
            val authStore = LocalStreamingAuthStore(applicationContext)
            val auth = authStore.authState(StreamingProviderName.KUGOU)
            val experiment = experimentStore.status(auth.connected)
            if (!experiment.userEnabled) return@withContext Result.success()

            val syncStore = StreamingPlaylistSyncStore(applicationContext)
            val links = syncStore.getAllLinks().filter {
                it.provider == StreamingProviderName.KUGOU && it.providerPlaylistId.isNotBlank()
            }
            if (links.isEmpty()) {
                experimentStore.recordReadOnlyResult("没有需要补偿同步的酷狗歌单")
                return@withContext Result.success()
            }

            val repository = StreamingRepositoryFactory.remote(
                applicationContext,
                StreamingGatewaySettingsStore(applicationContext).endpoint()
            )
            val library = MusicLibraryRepository(applicationContext, StreamingPlaybackAdapter)
            try {
                var syncedPlaylists = 0
                links.forEach { link ->
                    if (library.loadPlaylists().none { it.id == link.localPlaylistId }) {
                        return@forEach
                    }
                    var page = 1
                    var title = "酷狗歌单"
                    val remoteTracks = ArrayList<app.yukine.streaming.StreamingTrack>()
                    while (page <= MAX_PLAYLIST_PAGES && remoteTracks.size < MAX_PLAYLIST_TRACKS) {
                        val detail = repository.playlist(
                            provider = StreamingProviderName.KUGOU,
                            providerPlaylistId = link.providerPlaylistId,
                            page = page,
                            pageSize = PLAYLIST_PAGE_SIZE,
                            useCache = false
                        )
                        detail.playlist?.title?.takeIf(String::isNotBlank)?.let { title = it }
                        remoteTracks += detail.tracks.take(
                            MAX_PLAYLIST_TRACKS - remoteTracks.size
                        )
                        if (!detail.hasMore || detail.tracks.isEmpty()) break
                        page += 1
                    }
                    val placeholders = remoteTracks
                        .mapNotNull { it.playableLibraryTrackOrNull() }
                        .map(StreamingPlaybackAdapter::placeholderTrack)
                    library.syncStreamingPlaylist(link.localPlaylistId, placeholders)
                    val observedAt = System.currentTimeMillis()
                    syncStore.updateBaseline(
                        link.localPlaylistId,
                        StreamingPlaylistSyncSnapshot(
                            title = title,
                            orderedTrackIds = remoteTracks.map { it.providerTrackId },
                            updatedAtMs = observedAt
                        ),
                        localUpdatedAtMs = observedAt,
                        remoteUpdatedAtMs = null,
                        remoteObservedChangeAtMs = observedAt
                    )
                    syncedPlaylists += 1
                }
                experimentStore.recordSyncSuccess(
                    "后台只读补偿同步完成：$syncedPlaylists 个歌单"
                )
                Result.success()
            } catch (error: Exception) {
                val safeReason = when (error) {
                    is StreamingGatewayException -> "gateway:${error.code.name.lowercase()}"
                    else -> error.javaClass.simpleName.ifBlank { "unknown" }
                }
                experimentStore.recordReadOnlyResult("后台补偿同步失败：$safeReason")
                Result.retry()
            }
        }
    }

    private companion object {
        val singleFlight = Mutex()
        const val PLAYLIST_PAGE_SIZE = 2_000
        const val MAX_PLAYLIST_PAGES = 50
        const val MAX_PLAYLIST_TRACKS = PLAYLIST_PAGE_SIZE * MAX_PLAYLIST_PAGES
    }
}

object KugouPlaylistSyncScheduler {
    private const val IMMEDIATE_WORK = "kugou_playlist_sync_now"
    private const val PERIODIC_WORK = "kugou_playlist_sync_periodic"
    private const val PERIODIC_INTERVAL_MINUTES = 15L
    private const val TAG = "KugouPlaylistSync"

    fun schedule(context: Context) {
        runCatching {
            val manager = WorkManager.getInstance(context.applicationContext)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            manager.enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<KugouPlaylistSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.MINUTES
                    )
                    .build()
            )
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<KugouPlaylistSyncWorker>(
                    PERIODIC_INTERVAL_MINUTES,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.MINUTES
                    )
                    .build()
            )
        }.onFailure { error ->
            DiagnosticLog.w(TAG, "Unable to schedule Kugou playlist sync", error)
        }
    }
}
