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
import app.yukine.data.enrichment.MetadataGatewayArtistProvider
import app.yukine.data.enrichment.MetadataGatewayClient
import app.yukine.data.enrichment.MetadataGatewayRecordingProvider
import app.yukine.data.enrichment.RoomRecordingFingerprintLookup
import app.yukine.data.enrichment.StreamingSearchRecordingMetadataProvider
import app.yukine.data.enrichment.UrlConnectionMetadataHttpTransport
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
        if (settings.mode() == MetadataGatewayMode.OFFLINE) return@withContext Result.success()
        val metadataEndpoint = settings.effectiveGatewayEndpoint()
        if (metadataEndpoint.isBlank()) {
            Log.i(TAG, "Metadata gateway is not configured; identity enhancement remains offline")
            return@withContext Result.success()
        }
        val authStore = LocalStreamingAuthStore(context)
        val metadataGatewayClient = MetadataGatewayClient(
            cache = cache,
            transport = transport,
            endpoint = metadataEndpoint,
            applicationVersion = BuildConfig.VERSION_NAME,
        )
        val providers = buildList {
            add(MetadataGatewayRecordingProvider(
                metadataGatewayClient,
                RoomRecordingFingerprintLookup(database)
            ))
            if (authStore.hasStoredCredential(StreamingProviderName.NETEASE)) {
                val client = LocalNeteaseStreamingClient(authStore)
                add(StreamingSearchRecordingMetadataProvider(StreamingProviderName.NETEASE, client::search))
            }
            if (authStore.hasStoredCredential(StreamingProviderName.QQ_MUSIC)) {
                val client = LocalQqMusicStreamingClient(authStore)
                add(StreamingSearchRecordingMetadataProvider(StreamingProviderName.QQ_MUSIC, client::search))
            }
        }
        val engine = IdentityEnhancementEngine(
            recordings = RoomRecordingIdentityRepository(database),
            artists = RoomArtistIdentityRepository(database),
            candidates = RoomIdentityCandidateRepository(database),
            jobs = RoomIdentityJobRepository(database),
            providers = providers,
            artistProviders = listOf(
                MetadataGatewayArtistProvider(metadataGatewayClient)
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
        const val MAX_JOBS_PER_RUN = 20
    }
}

enum class MetadataGatewayMode { SHARED, CUSTOM, OFFLINE }

class IdentityEnhancementSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun mode(): MetadataGatewayMode {
        val stored = preferences.getString(KEY_GATEWAY_MODE, "").orEmpty()
        if (stored.isBlank() && musicBrainzProxy().isNotBlank()) return MetadataGatewayMode.CUSTOM
        val defaultMode = if (normalizeMetadataGatewayEndpoint(BuildConfig.ECHO_METADATA_GATEWAY_URL).isBlank()) {
            MetadataGatewayMode.OFFLINE
        } else {
            MetadataGatewayMode.SHARED
        }
        return runCatching { MetadataGatewayMode.valueOf(stored) }.getOrDefault(defaultMode)
    }

    fun setMode(value: MetadataGatewayMode) {
        preferences.edit().putString(KEY_GATEWAY_MODE, value.name).apply()
    }

    fun customGatewayEndpoint(): String = normalizeMetadataGatewayEndpoint(
        preferences.getString(KEY_CUSTOM_GATEWAY, musicBrainzProxy()).orEmpty()
    )

    fun setCustomGatewayEndpoint(value: String) {
        preferences.edit()
            .putString(KEY_CUSTOM_GATEWAY, normalizeMetadataGatewayEndpoint(value))
            .putString(KEY_GATEWAY_MODE, MetadataGatewayMode.CUSTOM.name)
            .apply()
    }

    fun effectiveGatewayEndpoint(): String = when (mode()) {
        MetadataGatewayMode.SHARED -> normalizeMetadataGatewayEndpoint(BuildConfig.ECHO_METADATA_GATEWAY_URL)
        MetadataGatewayMode.CUSTOM -> customGatewayEndpoint()
        MetadataGatewayMode.OFFLINE -> ""
    }

    /** Legacy compatibility for installs that already stored the old MusicBrainz-only proxy. */
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

        @JvmStatic
        fun normalizeMetadataGatewayEndpoint(value: String?): String =
            MetadataGatewayClient.normalizeEndpoint(value)

        const val PREFS_NAME = "identity_enhancement_settings"
        const val KEY_MUSICBRAINZ_PROXY = "musicbrainz_proxy"
        const val KEY_GATEWAY_MODE = "metadata_gateway_mode"
        const val KEY_CUSTOM_GATEWAY = "metadata_gateway_endpoint"
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
