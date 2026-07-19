package app.yukine.streaming

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages the mapping between local playlists and their linked streaming playlists,
 * and schedules periodic sync to keep local playlists updated with cloud content.
 *
 * Each linked playlist is stored as: localPlaylistId -> { provider, providerPlaylistId, lastSyncMs }
 */
class StreamingPlaylistSyncStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("streaming_playlist_sync", Context.MODE_PRIVATE)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var syncRunnable: Runnable? = null
    private var syncListener: SyncListener? = null

    companion object {
        private const val KEY_LINKED_PLAYLISTS = "linked_playlists"
        private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    interface SyncListener {
        fun onSyncNeeded(links: List<LinkedPlaylist>)
    }

    data class LinkedPlaylist(
        val localPlaylistId: Long,
        val provider: StreamingProviderName,
        val providerPlaylistId: String,
        val lastSyncMs: Long,
        val direction: StreamingPlaylistSyncDirection = StreamingPlaylistSyncDirection.REMOTE_TO_LOCAL,
        val baseline: StreamingPlaylistSyncSnapshot? = null,
        val localUpdatedAtMs: Long? = null,
        val remoteUpdatedAtMs: Long? = null,
        val remoteObservedChangeAtMs: Long? = null,
        val localDeletedAtMs: Long? = null,
        val remoteDeletedAtMs: Long? = null,
        val consecutiveRemoteMissing: Int = 0,
        val pendingOperations: List<StreamingPlaylistPendingOperation> = emptyList(),
        val lastError: String? = null
    )

    /**
     * Links a local playlist to a streaming playlist for periodic sync.
     * If providerPlaylistId is empty, this creates a "user favorites" link that syncs
     * the user's liked songs from that provider.
     */
    fun linkPlaylist(
        localPlaylistId: Long,
        provider: StreamingProviderName,
        providerPlaylistId: String = "",
        direction: StreamingPlaylistSyncDirection? = null
    ) {
        if (!ProviderRolePolicy.canSyncPlaylists(provider.wireName)) return
        val resolvedDirection = direction ?: if (provider == StreamingProviderName.KUGOU) {
            StreamingPlaylistSyncDirection.BIDIRECTIONAL
        } else {
            StreamingPlaylistSyncDirection.REMOTE_TO_LOCAL
        }
        val links = loadLinksMap().toMutableMap()
        links[localPlaylistId.toString()] = JSONObject().apply {
            put("provider", provider.wireName)
            put("providerPlaylistId", providerPlaylistId)
            put("lastSyncMs", 0L)
            put("direction", resolvedDirection.name)
        }.toString()
        saveLinksMap(links)
    }

    /**
     * Removes the sync link for a local playlist.
     */
    fun unlinkPlaylist(localPlaylistId: Long) {
        val links = loadLinksMap().toMutableMap()
        links.remove(localPlaylistId.toString())
        saveLinksMap(links)
    }

    /**
     * Returns the linked streaming info for a local playlist, or null if not linked.
     */
    fun getLink(localPlaylistId: Long): LinkedPlaylist? {
        val json = loadLinksMap()[localPlaylistId.toString()] ?: return null
        return parseLink(localPlaylistId, json)
    }

    /**
     * Returns all linked playlists.
     */
    fun getAllLinks(): List<LinkedPlaylist> {
        return loadLinksMap().mapNotNull { (key, value) ->
            val localId = key.toLongOrNull() ?: return@mapNotNull null
            parseLink(localId, value)
        }
    }

    /**
     * Returns the local playlist linked to a streaming account playlist.
     */
    fun getLink(provider: StreamingProviderName, providerPlaylistId: String): LinkedPlaylist? {
        val cleanProviderPlaylistId = providerPlaylistId.trim()
        if (cleanProviderPlaylistId.isEmpty()) {
            return null
        }
        return getAllLinks().firstOrNull { link ->
            link.provider == provider && link.providerPlaylistId == cleanProviderPlaylistId
        }
    }

    /**
     * Updates the last sync timestamp for a playlist.
     */
    fun markSynced(localPlaylistId: Long) {
        updateLink(localPlaylistId) { json ->
            json.put("lastSyncMs", System.currentTimeMillis())
            json.put("lastError", JSONObject.NULL)
        }
    }

    fun updateBaseline(
        localPlaylistId: Long,
        snapshot: StreamingPlaylistSyncSnapshot,
        localUpdatedAtMs: Long? = snapshot.updatedAtMs,
        remoteUpdatedAtMs: Long? = snapshot.updatedAtMs,
        remoteObservedChangeAtMs: Long? = null
    ) {
        updateLink(localPlaylistId) { json ->
            json.put("lastSyncMs", System.currentTimeMillis())
            json.put("baseline", snapshot.toJson())
            json.putNullableLong("localUpdatedAtMs", localUpdatedAtMs)
            json.putNullableLong("remoteUpdatedAtMs", remoteUpdatedAtMs)
            json.putNullableLong("remoteObservedChangeAtMs", remoteObservedChangeAtMs)
            json.put("consecutiveRemoteMissing", 0)
            json.put("lastError", JSONObject.NULL)
        }
    }

    fun markRemoteMissing(localPlaylistId: Long, observedAtMs: Long): Int {
        var missingCount = 0
        updateLink(localPlaylistId) { json ->
            missingCount = json.optInt("consecutiveRemoteMissing", 0) + 1
            json.put("consecutiveRemoteMissing", missingCount)
            if (missingCount >= 2) {
                json.put("remoteDeletedAtMs", observedAtMs)
            }
        }
        return missingCount
    }

    fun replacePendingOperations(
        localPlaylistId: Long,
        operations: List<StreamingPlaylistPendingOperation>
    ) {
        updateLink(localPlaylistId) { json ->
            json.put(
                "pendingOperations",
                JSONArray().apply { operations.forEach { put(it.toJson()) } }
            )
        }
    }

    fun recordFailure(localPlaylistId: Long, message: String) {
        updateLink(localPlaylistId) { json ->
            json.put("lastError", message.take(500))
        }
    }

    private fun updateLink(localPlaylistId: Long, mutate: (JSONObject) -> Unit) {
        val links = loadLinksMap().toMutableMap()
        val existing = links[localPlaylistId.toString()] ?: return
        val json = JSONObject(existing)
        mutate(json)
        links[localPlaylistId.toString()] = json.toString()
        saveLinksMap(links)
    }

    /**
     * Starts periodic sync. Call from Activity onResume.
     */
    fun startPeriodicSync(listener: SyncListener) {
        this.syncListener = listener
        stopPeriodicSync()
        syncRunnable = object : Runnable {
            override fun run() {
                triggerSync()
                mainHandler.postDelayed(this, SYNC_INTERVAL_MS)
            }
        }
        // Initial sync after short delay
        mainHandler.postDelayed(syncRunnable!!, 5000L)
    }

    /**
     * Stops periodic sync. Call from Activity onPause.
     */
    fun stopPeriodicSync() {
        syncRunnable?.let { mainHandler.removeCallbacks(it) }
        syncRunnable = null
    }

    /**
     * Triggers an immediate sync check.
     */
    fun triggerSync() {
        val links = getAllLinks()
        if (links.isNotEmpty()) {
            syncListener?.onSyncNeeded(links)
        }
    }

    private fun loadLinksMap(): Map<String, String> {
        val raw = prefs.getString(KEY_LINKED_PLAYLISTS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveLinksMap(links: Map<String, String>) {
        val json = JSONObject()
        links.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_LINKED_PLAYLISTS, json.toString()).apply()
    }

    private fun parseLink(localPlaylistId: Long, jsonStr: String): LinkedPlaylist? {
        return try {
            val json = JSONObject(jsonStr)
            val providerWire = json.optString("provider")
            val provider = StreamingProviderName.fromWireName(providerWire) ?: return null
            if (!ProviderRolePolicy.canSyncPlaylists(provider.wireName)) return null
            LinkedPlaylist(
                localPlaylistId = localPlaylistId,
                provider = provider,
                providerPlaylistId = json.optString("providerPlaylistId", ""),
                lastSyncMs = json.optLong("lastSyncMs", 0L),
                direction = runCatching {
                    StreamingPlaylistSyncDirection.valueOf(
                        json.optString(
                            "direction",
                            StreamingPlaylistSyncDirection.REMOTE_TO_LOCAL.name
                        )
                    )
                }.getOrDefault(StreamingPlaylistSyncDirection.REMOTE_TO_LOCAL),
                baseline = json.optJSONObject("baseline")?.toSyncSnapshot(),
                localUpdatedAtMs = json.optNullableLong("localUpdatedAtMs"),
                remoteUpdatedAtMs = json.optNullableLong("remoteUpdatedAtMs"),
                remoteObservedChangeAtMs = json.optNullableLong("remoteObservedChangeAtMs"),
                localDeletedAtMs = json.optNullableLong("localDeletedAtMs"),
                remoteDeletedAtMs = json.optNullableLong("remoteDeletedAtMs"),
                consecutiveRemoteMissing = json.optInt("consecutiveRemoteMissing", 0),
                pendingOperations = json.optJSONArray("pendingOperations")
                    ?.toPendingOperations()
                    .orEmpty(),
                lastError = json.optString("lastError").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.putNullableLong(key: String, value: Long?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONArray.toPendingOperations(): List<StreamingPlaylistPendingOperation> =
        buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.toPendingOperation()?.let(::add)
            }
        }
}

enum class StreamingPlaylistSyncDirection {
    REMOTE_TO_LOCAL,
    LOCAL_TO_REMOTE,
    BIDIRECTIONAL
}
