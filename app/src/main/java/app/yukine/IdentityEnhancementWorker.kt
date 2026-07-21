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
import app.yukine.data.RoomAlbumIdentityRepository
import app.yukine.data.RoomIdentityCandidateRepository
import app.yukine.data.RoomIdentityJobRepository
import app.yukine.data.RoomProviderResponseCacheRepository
import app.yukine.data.RoomRecordingIdentityRepository
import app.yukine.data.enrichment.IdentityEnhancementEngine
import app.yukine.data.enrichment.MetadataGatewayArtistProvider
import app.yukine.data.enrichment.MetadataGatewayAlbumProvider
import app.yukine.data.enrichment.MetadataGatewayClient
import app.yukine.data.enrichment.MetadataGatewayRecordingProvider
import app.yukine.data.enrichment.MusicBrainzMetadataClient
import app.yukine.data.enrichment.RoomMissingRecordingCoverWriter
import app.yukine.data.enrichment.RoomRecordingFingerprintLookup
import app.yukine.data.enrichment.StreamingSearchRecordingMetadataProvider
import app.yukine.data.enrichment.UrlConnectionMetadataHttpTransport
import app.yukine.data.room.YukineDatabase
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
        val jobs = RoomIdentityJobRepository(database)
        if (settings.needsArtistAvatarRepair()) {
            jobs.requeueMissingArtistAvatarJobs(System.currentTimeMillis())
            settings.markArtistAvatarRepairScheduled()
        }
        if (settings.needsFailedJobRecovery()) {
            jobs.recoverFailedArtistJobs(System.currentTimeMillis())
            settings.markFailedJobRecoveryDone()
        }
        val authStore = LocalStreamingAuthStore(context)
        val metadataGatewayClient = MetadataGatewayClient(
            cache = cache,
            transport = transport,
            endpoint = metadataEndpoint,
            applicationVersion = BuildConfig.VERSION_NAME,
            requestQuota = PersistentMetadataGatewayRequestQuota(context)
        )
        val musicBrainzClient = MusicBrainzMetadataClient(
            cache = cache,
            transport = transport,
            applicationVersion = BuildConfig.VERSION_NAME,
            contact = MUSICBRAINZ_CONTACT
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
            albums = RoomAlbumIdentityRepository(database),
            candidates = RoomIdentityCandidateRepository(database),
            jobs = jobs,
            providers = providers,
            artistProviders = listOf(
                MetadataGatewayArtistProvider(
                    metadataGatewayClient,
                    musicBrainzClient::artistAvatarUrl
                )
            ),
            albumProviders = listOf(MetadataGatewayAlbumProvider(metadataGatewayClient)),
            missingCoverWriter = RoomMissingRecordingCoverWriter(database)
        )
        val run = runCatching {
            val onDemand = inputData.getBoolean(KEY_ON_DEMAND, false)
            engine.runReadyJobs(if (onDemand) ON_DEMAND_JOB_LIMIT else Int.MAX_VALUE)
        }
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
        const val MUSICBRAINZ_CONTACT = "https://github.com/ysyhlly/Yukine-android"
        const val KEY_ON_DEMAND = "on_demand"
        const val ON_DEMAND_JOB_LIMIT = 5
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

    fun needsArtistAvatarRepair(): Boolean =
        preferences.getInt(KEY_ARTIST_AVATAR_REPAIR_VERSION, 0) < ARTIST_AVATAR_REPAIR_VERSION

    fun markArtistAvatarRepairScheduled() {
        check(
            preferences.edit()
                .putInt(KEY_ARTIST_AVATAR_REPAIR_VERSION, ARTIST_AVATAR_REPAIR_VERSION)
                .commit()
        ) { "Unable to persist artist avatar repair version" }
    }

    fun needsFailedJobRecovery(): Boolean {
        val last = preferences.getLong(KEY_LAST_FAILED_RECOVERY, 0L)
        return System.currentTimeMillis() - last >= FAILED_RECOVERY_INTERVAL_MS
    }

    fun markFailedJobRecoveryDone() {
        preferences.edit()
            .putLong(KEY_LAST_FAILED_RECOVERY, System.currentTimeMillis())
            .apply()
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
        const val KEY_ARTIST_AVATAR_REPAIR_VERSION = "artist_avatar_repair_version"
        const val KEY_LAST_FAILED_RECOVERY = "last_failed_job_recovery"
        const val ARTIST_AVATAR_REPAIR_VERSION = 3
        const val FAILED_RECOVERY_INTERVAL_MS = 24L * 60L * 60L * 1_000L
    }
}

object IdentityEnhancementScheduler {
    private const val IMMEDIATE_WORK = "identity_enhancement_now"
    private const val PERIODIC_WORK = "identity_enhancement_periodic"
    private const val PERIODIC_INTERVAL_MINUTES = 15L
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
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<IdentityEnhancementWorker>(
                    PERIODIC_INTERVAL_MINUTES,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()
            )
        }.onFailure { error -> Log.w(TAG, "Unable to schedule identity enhancement", error) }
    }
}
