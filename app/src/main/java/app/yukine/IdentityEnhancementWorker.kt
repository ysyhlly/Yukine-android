package app.yukine

import android.content.Context
import android.util.Log
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
import app.yukine.data.RoomArtistIdentityRepository
import app.yukine.data.RoomIdentityCandidateRepository
import app.yukine.data.RoomIdentityJobRepository
import app.yukine.data.RoomProviderResponseCacheRepository
import app.yukine.data.RoomRecordingIdentityRepository
import app.yukine.data.enrichment.IdentityEnhancementEngine
import app.yukine.data.enrichment.ItunesMetadataClient
import app.yukine.data.enrichment.ItunesRecordingMetadataProvider
import app.yukine.data.enrichment.MusicBrainzMetadataClient
import app.yukine.data.enrichment.MusicBrainzArtistMetadataProvider
import app.yukine.data.enrichment.MusicBrainzRecordingMetadataProvider
import app.yukine.data.enrichment.StreamingSearchRecordingMetadataProvider
import app.yukine.data.enrichment.UrlConnectionMetadataHttpTransport
import app.yukine.data.enrichment.WikimediaArtistMetadataClient
import app.yukine.data.enrichment.WikimediaArtistMetadataProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.playback.IdentityEnhancementPlaybackGate
import app.yukine.streaming.LocalNeteaseStreamingClient
import app.yukine.streaming.LocalQqMusicStreamingClient
import app.yukine.streaming.LocalStreamingAuthStore
import app.yukine.streaming.StreamingProviderName
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdentityEnhancementWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (IdentityEnhancementPlaybackGate.shouldDefer()) {
            Log.i(TAG, "Deferring identity enhancement while the app is visible or playback is active")
            return@withContext Result.retry()
        }
        val context = applicationContext
        val database = YukineDatabase.getInstance(context)
        val cache = RoomProviderResponseCacheRepository(database)
        val transport = UrlConnectionMetadataHttpTransport()
        val settings = IdentityEnhancementSettingsStore(context)
        val authStore = LocalStreamingAuthStore(context)
        val musicBrainzClient = MusicBrainzMetadataClient(
            cache = cache,
            transport = transport,
            applicationVersion = BuildConfig.VERSION_NAME,
            contact = PROJECT_URL,
            customProxy = settings.musicBrainzProxy()
        )
        val providers = buildList {
            add(MusicBrainzRecordingMetadataProvider(musicBrainzClient))
            if (authStore.hasStoredCredential(StreamingProviderName.NETEASE)) {
                val client = LocalNeteaseStreamingClient(authStore)
                add(StreamingSearchRecordingMetadataProvider(StreamingProviderName.NETEASE, client::search))
            }
            if (authStore.hasStoredCredential(StreamingProviderName.QQ_MUSIC)) {
                val client = LocalQqMusicStreamingClient(authStore)
                add(StreamingSearchRecordingMetadataProvider(StreamingProviderName.QQ_MUSIC, client::search))
            }
            add(ItunesRecordingMetadataProvider(ItunesMetadataClient(
                cache = cache,
                transport = transport,
                applicationVersion = BuildConfig.VERSION_NAME,
                contact = PROJECT_URL
            )))
        }
        val wikimediaClient = WikimediaArtistMetadataClient(
            cache = cache,
            transport = transport,
            applicationVersion = BuildConfig.VERSION_NAME,
            contact = PROJECT_URL
        )
        val engine = IdentityEnhancementEngine(
            recordings = RoomRecordingIdentityRepository(database),
            artists = RoomArtistIdentityRepository(database),
            candidates = RoomIdentityCandidateRepository(database),
            jobs = RoomIdentityJobRepository(database),
            providers = providers,
            artistProviders = listOf(
                MusicBrainzArtistMetadataProvider(musicBrainzClient),
                WikimediaArtistMetadataProvider(wikimediaClient)
            )
        )
        val run = runCatching { engine.runReadyJobs(MAX_JOBS_PER_RUN) }
        run.fold(
            onSuccess = { outcome ->
                if (outcome.retried > 0 && outcome.succeeded == 0) Result.retry() else Result.success()
            },
            onFailure = { error ->
                Log.w(TAG, "Identity enhancement run failed", error)
                Result.retry()
            }
        )
    }

    private companion object {
        const val TAG = "IdentityEnhancement"
        const val PROJECT_URL = "https://github.com/ysyhlly/Yukine-android"
        const val MAX_JOBS_PER_RUN = 20
    }
}

class IdentityEnhancementSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun musicBrainzProxy(): String = normalizeMusicBrainzProxy(
        preferences.getString(KEY_MUSICBRAINZ_PROXY, "").orEmpty()
    )

    fun setMusicBrainzProxy(value: String) {
        preferences.edit().putString(KEY_MUSICBRAINZ_PROXY, normalizeMusicBrainzProxy(value)).apply()
    }

    companion object {
        @JvmStatic
        fun normalizeMusicBrainzProxy(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return ""
            if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return ""
            return trimmed.trimEnd('/') + "/"
        }

        const val PREFS_NAME = "identity_enhancement_settings"
        const val KEY_MUSICBRAINZ_PROXY = "musicbrainz_proxy"
    }
}

object IdentityEnhancementScheduler {
    private const val IMMEDIATE_WORK = "identity_enhancement_now"
    private const val PERIODIC_WORK = "identity_enhancement_periodic"
    private const val TAG = "IdentityEnhancement"

    fun schedule(context: Context) {
        runCatching {
            val manager = WorkManager.getInstance(context.applicationContext)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            manager.enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IdentityEnhancementWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()
            )
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IdentityEnhancementWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()
            )
        }.onFailure { error -> Log.w(TAG, "Unable to schedule identity enhancement", error) }
    }
}
