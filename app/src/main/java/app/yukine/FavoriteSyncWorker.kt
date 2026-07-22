package app.yukine

import android.content.Context
import app.yukine.diagnostics.DiagnosticLog
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yukine.data.MusicLibraryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

internal class FavoriteSyncRunner(
    private val context: Context,
    private val musicLibraryRepository: MusicLibraryRepository,
    private val streamingRepositorySource: StreamingRepositorySource,
    private val favoriteSyncEventBus: FavoriteSyncEventBus,
    private val streamingTrackMatchUseCase: StreamingTrackMatchUseCase
) {
    fun createCoordinator(): FavoriteSyncCoordinator {
        val state = SharedPreferencesFavoriteSyncRepository(context)
        val library = MusicLibraryUnifiedFavoriteLibrary(musicLibraryRepository)
        return FavoriteSyncCoordinator(
            repository = state,
            providers = StreamingFavoriteProviderAdapter(streamingRepositorySource),
            library = library,
            trackMatches = streamingTrackMatchUseCase,
            eventBus = favoriteSyncEventBus,
            networkPolicy = AndroidFavoriteSyncNetworkPolicy(context),
            canonicalReconciler = FavoriteSyncCanonicalReconciler(state, library)
        )
    }

    suspend fun runOnce() {
        val coordinator = createCoordinator()
        try {
            coordinator.syncIncremental()
        } finally {
            coordinator.close()
        }
    }
}

/**
 * Application-scoped favorite sync entry point shared by WorkManager without routing work through
 * MainActivity. The coordinator remains the single behavior owner.
 */
class FavoriteSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            FavoriteSyncWorkerDependencies::class.java
        )
        val runner = FavoriteSyncRunner(
            context = applicationContext,
            musicLibraryRepository = dependencies.musicLibraryRepository(),
            streamingRepositorySource = dependencies.streamingRepositorySource(),
            favoriteSyncEventBus = dependencies.favoriteSyncEventBus(),
            streamingTrackMatchUseCase = StreamingTrackMatchUseCase(
                MusicLibraryStreamingTrackMatchOperations(dependencies.musicLibraryRepository())
            )
        )
        return try {
            runner.runOnce()
            Result.success()
        } catch (error: Throwable) {
            DiagnosticLog.w(TAG, "Background favorite synchronization failed", error)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "FavoriteSyncWorker"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface FavoriteSyncWorkerDependencies {
    fun musicLibraryRepository(): MusicLibraryRepository
    fun streamingRepositorySource(): StreamingRepositorySource
    fun favoriteSyncEventBus(): FavoriteSyncEventBus
}

internal object FavoriteSyncBackgroundScheduler {
    private const val UNIQUE_WORK_NAME = "favorite_sync_periodic"
    private const val TAG = "FavoriteSyncScheduler"

    fun restore(context: Context) {
        update(context, SharedPreferencesFavoriteSyncRepository(context).state.value.preferences)
    }

    fun update(context: Context, preferences: FavoriteSyncPreferences) {
        val workManager = runCatching { WorkManager.getInstance(context.applicationContext) }
            .getOrElse {
                DiagnosticLog.w(TAG, "WorkManager is unavailable", it)
                return
            }
        val schedule = favoriteSyncScheduleSpec(preferences)
        if (!schedule.enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FavoriteSyncWorker>(
            schedule.intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(schedule.networkType)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

internal data class FavoriteSyncScheduleSpec(
    val enabled: Boolean,
    val intervalMinutes: Long,
    val networkType: NetworkType
)

internal fun favoriteSyncScheduleSpec(preferences: FavoriteSyncPreferences): FavoriteSyncScheduleSpec =
    FavoriteSyncScheduleSpec(
        enabled = preferences.autoSyncEnabled && preferences.periodicSyncEnabled,
        intervalMinutes = preferences.intervalMinutes.coerceAtLeast(30).toLong(),
        networkType = if (preferences.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
    )
