package app.echo.next.streaming

import android.net.Uri
import app.echo.next.model.Track
import kotlin.math.absoluteValue

interface StreamingPlaybackTrackAdapter {
    fun toTrack(source: StreamingPlaybackSource, metadata: StreamingTrack? = null): Track
}

class HeaderBackedStreamingPlaybackTrackAdapter(
    private val headers: StreamingPlaybackHeaderStore = StreamingPlaybackHeaders
) : StreamingPlaybackTrackAdapter {
    override fun toTrack(source: StreamingPlaybackSource, metadata: StreamingTrack?): Track {
        return StreamingPlaybackAdapter.toTrack(source, metadata, headers)
    }
}

object StreamingPlaybackAdapter {
    private const val DATA_PATH_PREFIX = "streaming:"

    fun toTrack(
        source: StreamingPlaybackSource,
        metadata: StreamingTrack? = null,
        headers: StreamingPlaybackHeaderStore = StreamingPlaybackHeaders
    ): Track {
        val title = metadata?.title?.takeIf { it.isNotBlank() } ?: source.providerTrackId
        val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: source.provider.wireName
        val album = metadata?.album?.takeIf { it.isNotBlank() } ?: "Streaming"
        val dataPath = dataPath(source)
        headers.register(dataPath, source.headers)
        return Track(
            stableTrackId(source.provider, source.providerTrackId),
            title,
            artist,
            album,
            metadata?.durationMs ?: 0L,
            Uri.parse(source.url),
            dataPath,
            0L,
            metadata?.coverUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        )
    }

    /**
     * Builds a local [Track] placeholder for a streaming track that has NOT been resolved to a
     * playback URL yet (e.g. imported from a remote playlist). The placeholder carries the
     * provider + providerTrackId in its dataPath so playback can resolve the real URL on demand.
     * Its contentUri is empty until resolved.
     */
    fun placeholderTrack(track: StreamingTrack): Track {
        val dataPath = "$DATA_PATH_PREFIX${track.provider.wireName}:${track.providerTrackId}"
        return Track(
            stableTrackId(track.provider, track.providerTrackId),
            track.title.takeIf { it.isNotBlank() } ?: track.providerTrackId,
            track.artist.takeIf { it.isNotBlank() } ?: track.provider.wireName,
            track.album?.takeIf { it.isNotBlank() } ?: "Streaming",
            track.durationMs ?: 0L,
            Uri.EMPTY,
            dataPath,
            0L,
            (track.coverThumbUrl ?: track.coverUrl)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        )
    }

    fun isStreamingTrack(track: Track?): Boolean {
        return track?.dataPath?.startsWith(DATA_PATH_PREFIX) == true
    }

    /**
     * A streaming track is "unresolved" when it has no real playback URI yet — true for
     * placeholders imported from a remote playlist before their first playback.
     */
    fun isUnresolvedStreamingTrack(track: Track?): Boolean {
        if (!isStreamingTrack(track)) {
            return false
        }
        val uri = track?.contentUri
        return uri == null || uri == Uri.EMPTY || uri.toString().isBlank()
    }

    fun providerTrackId(dataPath: String): String {
        return parsedDataPath(dataPath)?.providerTrackId.orEmpty()
    }

    fun providerName(dataPath: String): StreamingProviderName? {
        return parsedDataPath(dataPath)?.provider
    }

    private fun stableTrackId(provider: StreamingProviderName, providerTrackId: String): Long {
        val value = "${provider.wireName}:$providerTrackId".hashCode().toLong().absoluteValue
        return if (value == 0L) 1L else value
    }

    private fun dataPath(source: StreamingPlaybackSource): String {
        return "$DATA_PATH_PREFIX${source.provider.wireName}:${source.providerTrackId}"
    }

    private fun parsedDataPath(dataPath: String): ParsedStreamingDataPath? {
        val markerStart = dataPath.indexOf(DATA_PATH_PREFIX)
        if (markerStart < 0) {
            return null
        }
        val remainder = dataPath.substring(markerStart + DATA_PATH_PREFIX.length)
        val providerEnd = remainder.indexOf(':')
        if (providerEnd <= 0 || providerEnd >= remainder.length - 1) {
            return null
        }
        val provider = providerNameFromDataPathWire(remainder.substring(0, providerEnd)) ?: return null
        val rawTrackId = remainder.substring(providerEnd + 1)
        val trackId = rawTrackId.substringBefore(':')
            .substringBefore('|')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
        if (trackId.isBlank()) {
            return null
        }
        return ParsedStreamingDataPath(provider, trackId)
    }

    private fun providerNameFromDataPathWire(wireName: String): StreamingProviderName? {
        val normalized = wireName.trim()
            .lowercase()
            .replace("-", "")
            .replace("_", "")
        return when (normalized) {
            "netease", "neteasecloud", "neteasemusic", "163", "163music" -> StreamingProviderName.NETEASE
            else -> StreamingProviderName.entries.firstOrNull {
                it.wireName.replace("_", "") == normalized
            }
        }
    }

    private data class ParsedStreamingDataPath(
        val provider: StreamingProviderName,
        val providerTrackId: String
    )
}
