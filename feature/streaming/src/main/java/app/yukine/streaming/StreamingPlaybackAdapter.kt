package app.yukine.streaming

import android.net.Uri
import app.yukine.common.StreamingDataPathMetadata
import app.yukine.common.StreamingDataPathParser
import app.yukine.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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

object StreamingPlaybackAdapter : StreamingDataPathParser {
    private const val DATA_PATH_PREFIX = "streaming:"

    fun toTrack(
        source: StreamingPlaybackSource,
        metadata: StreamingTrack? = null,
        headers: StreamingPlaybackHeaderStore = StreamingPlaybackHeaders
    ): Track {
        val title = metadata?.title?.takeIf { it.isNotBlank() } ?: source.providerTrackId
        val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: source.provider.wireName
        val album = metadata?.album?.takeIf { it.isNotBlank() } ?: "Streaming"
        val dataPath = dataPath(source, metadata)
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
            (metadata?.coverUrl ?: metadata?.coverThumbUrl)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        )
    }

    /**
     * Builds a local [Track] placeholder for a streaming track that has NOT been resolved to a
     * playback URL yet (e.g. imported from a remote playlist). The placeholder carries the
     * provider + providerTrackId in its dataPath so playback can resolve the real URL on demand.
     * Its contentUri is empty until resolved.
     */
    fun placeholderTrack(track: StreamingTrack): Track {
        val dataPath = "$DATA_PATH_PREFIX${track.provider.wireName}:${track.providerTrackId}${metadataQuery(track)}"
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
        return StreamingDataPathMetadata.isStreamingTrack(track?.dataPath)
    }

    override fun isStreamingTrack(dataPath: String): Boolean {
        return StreamingDataPathMetadata.isStreamingTrack(dataPath)
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

    override fun providerTrackId(dataPath: String): String {
        return StreamingDataPathMetadata.providerTrackId(dataPath)
    }

    fun streamingProviderName(dataPath: String): StreamingProviderName? {
        return StreamingDataPathMetadata.provider(dataPath)
    }

    /**
     * Returns every playable source encoded in a streaming [track]'s data path. The primary
     * provider is always first, including tracks created before alternate source metadata was
     * introduced. Invalid or stale option payloads never prevent playback of that primary source.
     */
    fun playbackCandidates(track: Track?): List<StreamingPlaybackCandidate> {
        val dataPath = track?.dataPath ?: return emptyList()
        val provider = streamingProviderName(dataPath) ?: return emptyList()
        val providerTrackId = providerTrackId(dataPath).takeIf { it.isNotBlank() } ?: return emptyList()
        val decoded = sourceOptions(dataPath)
        val primary = decoded.firstOrNull { candidate ->
            candidate.provider == provider &&
                candidate.providerTrackId == providerTrackId &&
                candidate.quality == null
        } ?: StreamingPlaybackCandidate(
            provider = provider,
            quality = null,
            label = provider.wireName,
            providerTrackId = providerTrackId,
            available = true
        )
        return distinctPlaybackCandidates(provider, providerTrackId, listOf(primary) + decoded)
    }

    override fun providerName(dataPath: String): String? {
        return StreamingDataPathMetadata.providerName(dataPath)
    }

    private fun stableTrackId(provider: StreamingProviderName, providerTrackId: String): Long {
        val value = "${provider.wireName}:$providerTrackId".hashCode().toLong().absoluteValue
        return if (value == 0L) 1L else value
    }

    private fun dataPath(source: StreamingPlaybackSource, metadata: StreamingTrack?): String {
        return "$DATA_PATH_PREFIX${source.provider.wireName}:${source.providerTrackId}${metadataQuery(metadata)}"
    }

    private fun metadataQuery(metadata: StreamingTrack?): String {
        if (metadata == null) {
            return ""
        }
        val params = linkedMapOf<String, String>()
        metadata.description?.takeIf { it.isNotBlank() }?.let { params["desc"] = it }
        val lyricSources = metadata.lyricSources
            .sortedBy { it.priority }
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")
        if (lyricSources.isNotBlank()) {
            params["lyrics"] = lyricSources
        }
        val playbackSources = metadata.playbackCandidates
            .map { candidate ->
                val quality = candidate.quality?.wireName?.uppercase()
                if (quality.isNullOrBlank()) candidate.label else "${candidate.label} $quality"
            }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")
        if (playbackSources.isNotBlank()) {
            params["sources"] = playbackSources
        }
        val playbackOptions = playbackSourceOptions(metadata)
        if (playbackOptions.isNotBlank()) {
            params["sourceOptions"] = playbackOptions
        }
        if (params.isEmpty()) {
            return ""
        }
        return params.entries.joinToString(prefix = "?", separator = "&") { entry ->
            "${entry.key}=${URLEncoder.encode(entry.value, StandardCharsets.UTF_8.name())}"
        }
    }

    private fun playbackSourceOptions(metadata: StreamingTrack): String {
        val array = JSONArray()
        val primary = StreamingPlaybackCandidate(
            provider = metadata.provider,
            quality = null,
            label = metadata.provider.wireName,
            providerTrackId = metadata.providerTrackId,
            available = metadata.playable
        )
        distinctPlaybackCandidates(
            metadata.provider,
            metadata.providerTrackId,
            listOf(primary) + metadata.playbackCandidates
        ).forEach { candidate ->
            val providerTrackId = candidate.providerTrackId ?: return@forEach
            array.put(
                JSONObject()
                    .put("provider", candidate.provider.wireName)
                    .put("providerTrackId", providerTrackId)
                    .put("quality", candidate.quality?.wireName)
                    .put("label", candidate.label.ifBlank { candidate.provider.wireName })
                    .put("available", candidate.available)
            )
        }
        return if (array.length() > 1) array.toString() else ""
    }

    private fun sourceOptions(dataPath: String): List<StreamingPlaybackCandidate> {
        val encoded = queryParam(dataPath, "sourceOptions") ?: return emptyList()
        val options = runCatching { JSONArray(encoded) }.getOrNull() ?: return emptyList()
        return (0 until options.length()).mapNotNull { index ->
            val option = options.optJSONObject(index) ?: return@mapNotNull null
            val provider = StreamingProviderName.fromWireName(option.optString("provider"))
                ?: return@mapNotNull null
            val providerTrackId = option.optString("providerTrackId")
                .ifBlank { option.optString("id") }
                .trim()
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            StreamingPlaybackCandidate(
                provider = provider,
                quality = option.optString("quality")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(StreamingAudioQuality::fromWireName),
                label = option.optString("label").trim().ifBlank { provider.wireName },
                providerTrackId = providerTrackId,
                available = option.optBoolean("available", true)
            )
        }
    }

    private fun distinctPlaybackCandidates(
        primaryProvider: StreamingProviderName,
        primaryProviderTrackId: String,
        candidates: List<StreamingPlaybackCandidate>
    ): List<StreamingPlaybackCandidate> {
        val seen = linkedSetOf<String>()
        return candidates.mapNotNull { candidate ->
            val providerTrackId = candidate.providerTrackId?.trim().takeIf { !it.isNullOrBlank() }
                ?: primaryProviderTrackId.takeIf { candidate.provider == primaryProvider }
                ?: return@mapNotNull null
            val normalized = candidate.copy(
                providerTrackId = providerTrackId,
                label = candidate.label.ifBlank { candidate.provider.wireName }
            )
            val key = "${normalized.provider.wireName}:$providerTrackId:${normalized.quality?.wireName.orEmpty()}"
            normalized.takeIf { seen.add(key) }
        }
    }

    private fun queryParam(dataPath: String, target: String): String? {
        val query = dataPath.substringAfter('?', "")
        if (query.isBlank()) {
            return null
        }
        return query.split('&').firstNotNullOfOrNull { parameter ->
            val separator = parameter.indexOf('=')
            if (separator < 0) {
                return@firstNotNullOfOrNull null
            }
            val key = decodeQueryComponent(parameter.substring(0, separator))
            if (key != target) {
                null
            } else {
                decodeQueryComponent(parameter.substring(separator + 1))
            }
        }
    }

    private fun decodeQueryComponent(value: String): String {
        return runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
            .getOrDefault(value)
    }

}
