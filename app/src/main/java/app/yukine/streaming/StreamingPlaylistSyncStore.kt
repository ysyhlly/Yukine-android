package app.yukine.streaming

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
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
        val lastSyncMs: Long
    )

    /**
     * Links a local playlist to a streaming playlist for periodic sync.
     * If providerPlaylistId is empty, this creates a "user favorites" link that syncs
     * the user's liked songs from that provider.
     */
    fun linkPlaylist(
        localPlaylistId: Long,
        provider: StreamingProviderName,
        providerPlaylistId: String = ""
    ) {
        val links = loadLinksMap().toMutableMap()
        links[localPlaylistId.toString()] = JSONObject().apply {
            put("provider", provider.wireName)
            put("providerPlaylistId", providerPlaylistId)
            put("lastSyncMs", 0L)
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
     * Updates the last sync timestamp for a playlist.
     */
    fun markSynced(localPlaylistId: Long) {
        val links = loadLinksMap().toMutableMap()
        val existing = links[localPlaylistId.toString()] ?: return
        val json = JSONObject(existing)
        json.put("lastSyncMs", System.currentTimeMillis())
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
            LinkedPlaylist(
                localPlaylistId = localPlaylistId,
                provider = provider,
                providerPlaylistId = json.optString("providerPlaylistId", ""),
                lastSyncMs = json.optLong("lastSyncMs", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}
