package app.yukine.streaming

import android.net.Uri
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import app.yukine.streaming.cache.StreamingCacheRepository
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

interface StreamingPlaybackHeaderStore {
    fun register(dataPath: String, headers: Map<String, String>)

    fun forDataPath(dataPath: String?): Map<String, String>

    fun restoreForDataPath(dataPath: String?): Boolean

    fun restoredTrackFor(track: Track?): Track?
}

class PersistentStreamingPlaybackHeaders(
    private val cacheRepository: StreamingCacheRepository,
    private val localAuthStore: StreamingLocalAuthStore? = null
) : StreamingPlaybackHeaderStore {
    private val headersByDataPath = ConcurrentHashMap<String, Map<String, String>>()

    override
    fun register(dataPath: String, headers: Map<String, String>) {
        if (dataPath.isBlank()) {
            return
        }
        val headerKey = headerKey(dataPath)
        val runtimeHeaders = headersWithStreamingAuth(dataPath, headers, localAuthStore)
        if (runtimeHeaders.isEmpty()) {
            headersByDataPath.remove(headerKey)
            return
        }
        headersByDataPath[headerKey] = runtimeHeaders
    }

    override
    fun forDataPath(dataPath: String?): Map<String, String> {
        if (dataPath.isNullOrBlank()) {
            return emptyMap()
        }
        return headersByDataPath[headerKey(dataPath)].orEmpty()
    }

    override
    fun restoreForDataPath(dataPath: String?): Boolean {
        if (dataPath.isNullOrBlank() || headersByDataPath.containsKey(headerKey(dataPath))) {
            return false
        }
        val provider = StreamingPlaybackAdapter.streamingProviderName(dataPath) ?: return false
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(dataPath).takeIf { it.isNotBlank() } ?: return false
        val cached = cacheRepository.cachedPlaybackBlocking(
            provider,
            providerTrackId,
            StreamingPlaybackAdapter.luoxueMusicInfoJson(dataPath)
        ) ?: return false
        val source = runCatching { StreamingGatewayJson.playbackSource(cached) }.getOrNull() ?: return false
        if (!isSupportedPlaybackSourceUrl(source.url)) {
            return false
        }
        val headers = source.headers
        if (headers.isEmpty()) {
            return false
        }
        register(dataPath, headers)
        return true
    }

    override fun restoredTrackFor(track: Track?): Track? {
        if (!StreamingPlaybackAdapter.isStreamingTrack(track)) {
            return null
        }
        val dataPath = track?.dataPath ?: return null
        val provider = StreamingPlaybackAdapter.streamingProviderName(dataPath) ?: return null
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(dataPath).takeIf { it.isNotBlank() } ?: return null
        val cached = cacheRepository.cachedPlaybackBlocking(
            provider,
            providerTrackId,
            StreamingPlaybackAdapter.luoxueMusicInfoJson(dataPath)
        ) ?: return null
        val source = runCatching { StreamingGatewayJson.playbackSource(cached) }.getOrNull() ?: return null
        if (!isSupportedPlaybackSourceUrl(source.url)) {
            return null
        }
        register(dataPath, source.headers)
        val restoredUri = Uri.parse(source.url)
        if (track.contentUri?.toString() == source.url) {
            return null
        }
        return Track(
            track.id,
            track.title,
            track.artist,
            track.album,
            track.durationMs,
            restoredUri,
            dataPath,
            track.albumId,
            track.albumArtUri,
            track.codec,
            track.bitrateKbps,
            track.sampleRateHz,
            track.bitsPerSample,
            track.channelCount,
            track.replayGainTrackDb,
            track.replayGainAlbumDb
        )
    }

    private fun headerKey(dataPath: String): String {
        return StreamingDataPathMetadata.cacheIdentity(dataPath) ?: dataPath
    }
}

/** Shared cache/restore guard: a stored URL must be safe to hand to Media3. */
internal fun isSupportedPlaybackSourceUrl(value: String?): Boolean {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank() || clean.contains(";invalid;", ignoreCase = true)) {
        return false
    }
    val uri = runCatching { URI(clean) }.getOrNull() ?: return false
    return when (uri.scheme?.lowercase()) {
        "http", "https" -> !uri.host.isNullOrBlank()
        "content", "file", "android.resource" -> true
        else -> false
    }
}

internal fun headersWithStreamingAuth(
    dataPath: String,
    headers: Map<String, String>,
    localAuthStore: StreamingLocalAuthStore?
): Map<String, String> {
    val runtimeHeaders = headers.toMutableMap()
    if (
        StreamingPlaybackAdapter.streamingProviderName(dataPath) == StreamingProviderName.QQ_MUSIC &&
        runtimeHeaders["Cookie"].isNullOrBlank()
    ) {
        localAuthStore?.cookieHeader(StreamingProviderName.QQ_MUSIC)
            ?.takeIf(::hasQqPlaybackCredential)
            ?.let { cookie -> runtimeHeaders["Cookie"] = cookie }
    }
    return runtimeHeaders.toMap()
}

object StreamingPlaybackHeaders : StreamingPlaybackHeaderStore {
    private val headersByDataPath = ConcurrentHashMap<String, Map<String, String>>()

    override fun register(dataPath: String, headers: Map<String, String>) {
        if (dataPath.isBlank()) {
            return
        }
        val headerKey = headerKey(dataPath)
        if (headers.isEmpty()) {
            headersByDataPath.remove(headerKey)
            return
        }
        headersByDataPath[headerKey] = headers.toMap()
    }

    override fun forDataPath(dataPath: String?): Map<String, String> {
        if (dataPath.isNullOrBlank()) {
            return emptyMap()
        }
        return headersByDataPath[headerKey(dataPath)].orEmpty()
    }

    override fun restoreForDataPath(dataPath: String?): Boolean {
        return false
    }

    override fun restoredTrackFor(track: Track?): Track? {
        return null
    }

    private fun headerKey(dataPath: String): String {
        return StreamingDataPathMetadata.cacheIdentity(dataPath) ?: dataPath
    }
}
