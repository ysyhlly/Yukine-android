package app.echo.next.streaming

import android.net.Uri
import app.echo.next.model.Track
import app.echo.next.streaming.cache.StreamingCacheRepository
import java.util.concurrent.ConcurrentHashMap

interface StreamingPlaybackHeaderStore {
    fun register(dataPath: String, headers: Map<String, String>)

    fun forDataPath(dataPath: String?): Map<String, String>

    fun restoreForDataPath(dataPath: String?): Boolean

    fun restoredTrackFor(track: Track?): Track?
}

class PersistentStreamingPlaybackHeaders(
    private val cacheRepository: StreamingCacheRepository
) : StreamingPlaybackHeaderStore {
    private val headersByDataPath = ConcurrentHashMap<String, Map<String, String>>()

    override
    fun register(dataPath: String, headers: Map<String, String>) {
        if (dataPath.isBlank()) {
            return
        }
        if (headers.isEmpty()) {
            headersByDataPath.remove(dataPath)
            return
        }
        headersByDataPath[dataPath] = headers.toMap()
    }

    override
    fun forDataPath(dataPath: String?): Map<String, String> {
        if (dataPath.isNullOrBlank()) {
            return emptyMap()
        }
        return headersByDataPath[dataPath].orEmpty()
    }

    override
    fun restoreForDataPath(dataPath: String?): Boolean {
        if (dataPath.isNullOrBlank() || headersByDataPath.containsKey(dataPath)) {
            return false
        }
        val provider = StreamingPlaybackAdapter.providerName(dataPath) ?: return false
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(dataPath).takeIf { it.isNotBlank() } ?: return false
        val cached = cacheRepository.cachedPlaybackBlocking(provider, providerTrackId) ?: return false
        val headers = StreamingGatewayJson.playbackSource(cached).headers
        if (headers.isEmpty()) {
            return false
        }
        register(dataPath, headers)
        return true
    }

    override fun restoredTrackFor(track: Track?): Track? {
        if (!StreamingPlaybackAdapter.isUnresolvedStreamingTrack(track)) {
            return null
        }
        val dataPath = track?.dataPath ?: return null
        val provider = StreamingPlaybackAdapter.providerName(dataPath) ?: return null
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(dataPath).takeIf { it.isNotBlank() } ?: return null
        val cached = cacheRepository.cachedPlaybackBlocking(provider, providerTrackId) ?: return null
        val source = StreamingGatewayJson.playbackSource(cached)
        if (source.url.isBlank()) {
            return null
        }
        register(dataPath, source.headers)
        return Track(
            track.id,
            track.title,
            track.artist,
            track.album,
            track.durationMs,
            Uri.parse(source.url),
            dataPath,
            track.albumId,
            track.albumArtUri
        )
    }
}

object StreamingPlaybackHeaders : StreamingPlaybackHeaderStore {
    private val headersByDataPath = ConcurrentHashMap<String, Map<String, String>>()

    override fun register(dataPath: String, headers: Map<String, String>) {
        if (dataPath.isBlank()) {
            return
        }
        if (headers.isEmpty()) {
            headersByDataPath.remove(dataPath)
            return
        }
        headersByDataPath[dataPath] = headers.toMap()
    }

    override fun forDataPath(dataPath: String?): Map<String, String> {
        if (dataPath.isNullOrBlank()) {
            return emptyMap()
        }
        return headersByDataPath[dataPath].orEmpty()
    }

    override fun restoreForDataPath(dataPath: String?): Boolean {
        return false
    }

    override fun restoredTrackFor(track: Track?): Track? {
        return null
    }
}
