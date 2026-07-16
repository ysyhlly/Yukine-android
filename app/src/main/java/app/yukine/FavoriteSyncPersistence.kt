package app.yukine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.streaming.StreamingCapabilityResolver
import app.yukine.streaming.StreamingMediaType
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingTrack
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal class SharedPreferencesFavoriteSyncRepository(context: Context) : FavoriteSyncRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutableState = MutableStateFlow(decode(preferences.getString(KEY_STATE, null)))
    private var batchDepth = 0
    private var batchDirty = false
    override val state: StateFlow<FavoriteSyncStoreState> = mutableState.asStateFlow()

    override fun update(transform: (FavoriteSyncStoreState) -> FavoriteSyncStoreState): FavoriteSyncStoreState =
        synchronized(lock) {
            transform(mutableState.value).also { next ->
                mutableState.value = next
                if (batchDepth > 0) {
                    batchDirty = true
                } else {
                    persist(next)
                }
            }
        }

    override fun beginBatch() {
        synchronized(lock) { batchDepth++ }
    }

    override fun endBatch() {
        synchronized(lock) {
            check(batchDepth > 0) { "Favorite sync batch was not started" }
            batchDepth--
            if (batchDepth == 0 && batchDirty) {
                batchDirty = false
                persist(mutableState.value)
            }
        }
    }

    private fun persist(state: FavoriteSyncStoreState) {
        preferences.edit().putString(KEY_STATE, encode(state).toString()).apply()
    }

    private fun encode(state: FavoriteSyncStoreState): JSONObject = JSONObject()
        .put("version", CURRENT_VERSION)
        .put("lastSyncAtMs", state.lastSyncAtMs)
        .put("preferences", JSONObject()
            .put("autoSyncEnabled", state.preferences.autoSyncEnabled)
            .put("syncOnForeground", state.preferences.syncOnForeground)
            .put("periodicSyncEnabled", state.preferences.periodicSyncEnabled)
            .put("wifiOnly", state.preferences.wifiOnly)
            .put("propagateRemovals", state.preferences.propagateRemovals)
            .put("confirmLowConfidence", state.preferences.confirmLowConfidence)
            .put("intervalMinutes", state.preferences.intervalMinutes))
        .put("favorites", JSONArray(state.favorites.map { value -> JSONObject()
            .put("unifiedId", value.unifiedId).put("localTrackId", value.localTrackId)
            .put("recordingId", value.recordingId).put("canonicalUuid", value.canonicalUuid)
            .put("title", value.title).put("artist", value.artist).put("album", value.album)
            .put("durationMs", value.durationMs).put("isrc", value.isrc).put("active", value.active)
            .put("sourceProvider", value.sourceProvider?.wireName)
            .put("sourceProviderTrackId", value.sourceProviderTrackId)
            .put("lastBatchId", value.lastBatchId).put("updatedAtMs", value.updatedAtMs) }))
        .put("mappings", JSONArray(state.mappings.map { value -> JSONObject()
            .put("unifiedId", value.unifiedId).put("provider", value.provider.wireName)
            .put("providerTrackId", value.providerTrackId).put("status", value.status.name)
            .put("confidence", value.confidence.toDouble()).put("lastBatchId", value.lastBatchId)
            .put("errorMessage", value.errorMessage).put("updatedAtMs", value.updatedAtMs)
            .put("active", value.active).put("recordingId", value.recordingId) }))
        .put("cursors", JSONArray(state.cursors.map { value -> JSONObject()
            .put("provider", value.provider.wireName).put("cursor", value.cursor)
            .put("seenProviderTrackIds", JSONArray(value.seenProviderTrackIds.toList()))
            .put("lastSyncAtMs", value.lastSyncAtMs) }))
        .put("operations", JSONArray(state.operations.map { value -> JSONObject()
            .put("operationId", value.operationId).put("unifiedId", value.unifiedId)
            .put("action", value.action.name).put("sourceProvider", value.sourceProvider?.wireName)
            .put("targetProvider", value.targetProvider.wireName).put("batchId", value.batchId)
            .put("status", value.status.name).put("attemptCount", value.attemptCount)
            .put("nextAttemptAtMs", value.nextAttemptAtMs).put("errorMessage", value.errorMessage)
            .put("updatedAtMs", value.updatedAtMs).put("recordingId", value.recordingId) }))
        .put("conflicts", JSONArray(state.conflicts.map { value -> JSONObject()
            .put("conflictId", value.conflictId).put("unifiedId", value.unifiedId)
            .put("provider", value.provider.wireName).put("candidateProviderTrackId", value.candidateProviderTrackId)
            .put("confidence", value.confidence.toDouble()).put("status", value.status.name)
            .put("createdAtMs", value.createdAtMs).put("recordingId", value.recordingId) }))
        .put("pendingImports", JSONArray(state.pendingImports.map { value -> JSONObject()
            .put("provider", value.provider.wireName).put("providerTrackId", value.providerTrackId)
            .put("title", value.title).put("artist", value.artist).put("album", value.album)
            .put("durationMs", value.durationMs).put("isrc", value.isrc)
            .put("attemptCount", value.attemptCount).put("errorMessage", value.errorMessage)
            .put("updatedAtMs", value.updatedAtMs) }))
        .put("logs", JSONArray(state.logs.map { value -> JSONObject()
            .put("batchId", value.batchId).put("provider", value.provider?.wireName)
            .put("message", value.message).put("status", value.status.name)
            .put("createdAtMs", value.createdAtMs) }))

    private fun decode(raw: String?): FavoriteSyncStoreState {
        if (raw.isNullOrBlank()) return FavoriteSyncStoreState()
        return runCatching {
            val root = JSONObject(raw)
            val storedVersion = root.optInt("version", 1)
            val preferenceJson = root.optJSONObject("preferences") ?: JSONObject()
            FavoriteSyncStoreState(
                favorites = root.objects("favorites").map { value -> UnifiedFavorite(
                    unifiedId = value.optString("unifiedId"), localTrackId = value.optLong("localTrackId"),
                    title = value.optString("title"), artist = value.optString("artist"), album = value.optString("album"),
                    durationMs = value.optLong("durationMs"), isrc = value.nullableString("isrc"),
                    active = value.optBoolean("active", true), sourceProvider = value.provider("sourceProvider"),
                    sourceProviderTrackId = value.nullableString("sourceProviderTrackId"),
                    lastBatchId = value.optString("lastBatchId"), updatedAtMs = value.optLong("updatedAtMs"),
                    recordingId = value.optLong("recordingId"),
                    canonicalUuid = value.optString("canonicalUuid").ifBlank {
                        value.optString("unifiedId").removePrefix("recording:")
                    }) },
                mappings = root.objects("mappings").mapNotNull { value -> value.provider("provider")?.let { provider ->
                    ProviderFavoriteMapping(
                        unifiedId = value.optString("unifiedId"), provider = provider,
                        providerTrackId = value.optString("providerTrackId"), status = value.syncStatus(),
                        confidence = value.optDouble("confidence").toFloat(), lastBatchId = value.optString("lastBatchId"),
                        errorMessage = value.optString("errorMessage"), updatedAtMs = value.optLong("updatedAtMs"),
                        active = value.optBoolean("active", true), recordingId = value.optLong("recordingId")
                    ) } },
                cursors = root.objects("cursors").mapNotNull { value -> value.provider("provider")?.let { provider ->
                    FavoriteSyncCursor(provider, value.optString("cursor"), value.strings("seenProviderTrackIds").toSet(),
                        value.optLong("lastSyncAtMs")) } },
                operations = root.objects("operations").mapNotNull { value -> value.provider("targetProvider")?.let { target ->
                    FavoriteSyncOperation(
                        operationId = value.optString("operationId"), unifiedId = value.optString("unifiedId"),
                        action = runCatching { FavoriteSyncAction.valueOf(value.optString("action")) }
                            .getOrDefault(FavoriteSyncAction.ADD),
                        sourceProvider = value.provider("sourceProvider"), targetProvider = target,
                        batchId = value.optString("batchId"), status = value.syncStatus(),
                        attemptCount = value.optInt("attemptCount"), nextAttemptAtMs = value.optLong("nextAttemptAtMs"),
                        errorMessage = value.optString("errorMessage"), updatedAtMs = value.optLong("updatedAtMs"),
                        recordingId = value.optLong("recordingId")
                    ) } },
                conflicts = root.objects("conflicts").mapNotNull { value -> value.provider("provider")?.let { provider ->
                    FavoriteSyncConflict(
                        conflictId = value.optString("conflictId"), unifiedId = value.optString("unifiedId"),
                        provider = provider, candidateProviderTrackId = value.optString("candidateProviderTrackId"),
                        confidence = value.optDouble("confidence").toFloat(), status = value.syncStatus(),
                        createdAtMs = value.optLong("createdAtMs"), recordingId = value.optLong("recordingId")
                    ) } },
                pendingImports = root.objects("pendingImports").mapNotNull { value ->
                    value.provider("provider")?.let { provider -> PendingProviderFavorite(
                        provider = provider, providerTrackId = value.optString("providerTrackId"),
                        title = value.optString("title"), artist = value.optString("artist"),
                        album = value.optString("album"), durationMs = value.optLong("durationMs"),
                        isrc = value.nullableString("isrc"), attemptCount = value.optInt("attemptCount", 1),
                        errorMessage = value.optString("errorMessage"), updatedAtMs = value.optLong("updatedAtMs")
                    ) }
                },
                logs = root.objects("logs").map { value -> FavoriteSyncTaskLog(value.optString("batchId"),
                    value.provider("provider"), value.optString("message"), value.syncStatus(), value.optLong("createdAtMs")) },
                preferences = FavoriteSyncPreferences(
                    autoSyncEnabled = if (storedVersion < 2) true
                        else preferenceJson.optBoolean("autoSyncEnabled", true),
                    syncOnForeground = preferenceJson.optBoolean("syncOnForeground", true),
                    periodicSyncEnabled = preferenceJson.optBoolean("periodicSyncEnabled", true),
                    wifiOnly = preferenceJson.optBoolean("wifiOnly", false),
                    propagateRemovals = if (storedVersion < 2) true
                        else preferenceJson.optBoolean("propagateRemovals", true),
                    confirmLowConfidence = true,
                    intervalMinutes = preferenceJson.optInt("intervalMinutes", 30).coerceAtLeast(15)),
                lastSyncAtMs = root.optLong("lastSyncAtMs")
            )
        }.getOrDefault(FavoriteSyncStoreState())
    }

    private companion object {
        const val PREFS_NAME = "cross_provider_favorite_sync"
        const val KEY_STATE = "state_v1"
        const val CURRENT_VERSION = 3
    }
}

internal class StreamingFavoriteProviderAdapter(
    private val source: StreamingRepositorySource
) : FavoriteProviderAdapter {
    private fun repository(): StreamingRepository = source.current()

    override suspend fun capabilities(): List<ProviderCapability> {
        val repository = repository()
        val descriptors = repository.providers()
        val resolved = repository.providerCapabilities().associateBy { it.provider }
        return descriptors.map { descriptor ->
            val capability = resolved[descriptor.name] ?: StreamingCapabilityResolver.providerCapability(descriptor)
            val auth = if (descriptor.capabilities.supportsAuth) {
                runCatching { repository.authState(descriptor.name) }.getOrDefault(descriptor.auth)
            } else descriptor.auth.copy(connected = true)
            ProviderCapability(
                provider = descriptor.name,
                displayName = descriptor.displayName,
                enabled = descriptor.enabled,
                loggedIn = !descriptor.capabilities.supportsAuth || auth.connected,
                authorized = !descriptor.capabilities.supportsAuth || auth.connected,
                canPullFavorites = capability.supportsFavoritesRead,
                canAddFavorite = capability.supportsFavoritesWrite,
                canRemoveFavorite = capability.supportsFavoritesWrite
            )
        }
    }

    override suspend fun pullFavoriteDelta(
        provider: StreamingProviderName,
        cursor: FavoriteSyncCursor?
    ): FavoritePullDelta {
        val current = repository().userLikedTracks(provider).distinctBy { it.providerTrackId }
        val observed = current.mapTo(linkedSetOf()) { it.providerTrackId }
        val removed = cursor?.seenProviderTrackIds.orEmpty() - observed
        return FavoritePullDelta(
            added = current.filterNot { it.providerTrackId in cursor?.seenProviderTrackIds.orEmpty() },
            cursor = observed.sorted().joinToString("|").hashCode().toString(),
            observedProviderTrackIds = observed,
            removedProviderTrackIds = removed
        )
    }

    override suspend fun addFavorite(provider: StreamingProviderName, providerTrackId: String) {
        repository().setUserTrackFavorite(provider, providerTrackId, true)
    }

    override suspend fun removeFavorite(provider: StreamingProviderName, providerTrackId: String) {
        repository().setUserTrackFavorite(provider, providerTrackId, false)
    }

    override suspend fun search(provider: StreamingProviderName, track: UnifiedFavorite): List<StreamingTrack> {
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return emptyList()
        return repository().search(provider, query, setOf(StreamingMediaType.TRACK), pageSize = 10).tracks
    }
}

internal class MusicLibraryUnifiedFavoriteLibrary(
    private val repository: MusicLibraryRepository
) : UnifiedFavoriteLibrary {
    private val pendingConfirmedTrackIds = linkedSetOf<Long>()
    override fun importExternalFavorite(track: StreamingTrack): Track =
        StreamingPlaybackAdapter.placeholderTrack(track).also { repository.setFavorite(it, true) }

    override fun localTrack(localTrackId: Long): Track? =
        repository.loadTrack(localTrackId)

    override fun setFavorite(track: Track, favorite: Boolean) = repository.setFavorite(track, favorite)

    override fun isFavorite(localTrackId: Long): Boolean = repository.isFavorite(localTrackId)

    override fun canonicalId(localTrackId: Long): String? = repository.loadCanonicalId(localTrackId)

    override fun recordingId(localTrackId: Long): Long = repository.loadRecordingId(localTrackId)

    override fun confirmedProviderTrackId(recordingId: Long, provider: StreamingProviderName): String =
        repository.loadConfirmedProviderTrackId(recordingId, provider.wireName)

    override fun confirmDirectProviderSource(
        localTrackId: Long,
        provider: StreamingProviderName,
        providerTrackId: String
    ): Boolean {
        val confirmed = repository.confirmDirectProviderSourceWithoutIdentityIngest(
            localTrackId,
            provider.wireName,
            providerTrackId
        )
        if (confirmed) synchronized(pendingConfirmedTrackIds) {
            pendingConfirmedTrackIds += localTrackId
        }
        return confirmed
    }

    override fun flushConfirmedSources(): Int {
        val trackIds = synchronized(pendingConfirmedTrackIds) {
            pendingConfirmedTrackIds.toList().also { pendingConfirmedTrackIds.clear() }
        }
        return repository.ingestConfirmedIdentitySources(trackIds)
    }

    override fun favoriteTracks(): List<Track> = repository.loadFavoriteTracks()

    override fun updateFavoriteSyncState(recordingId: Long, syncState: String) =
        repository.updateFavoriteSyncState(recordingId, syncState)
}

internal class AndroidFavoriteSyncNetworkPolicy(context: Context) : FavoriteSyncNetworkPolicy {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun canSync(wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

private fun JSONObject.objects(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull(array::optJSONObject)
}

private fun JSONObject.strings(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.provider(key: String): StreamingProviderName? =
    nullableString(key)?.let(StreamingProviderName::fromWireName)

private fun JSONObject.syncStatus(): FavoriteSyncStatus =
    runCatching { FavoriteSyncStatus.valueOf(optString("status").uppercase(Locale.ROOT)) }
        .getOrDefault(FavoriteSyncStatus.PENDING)
