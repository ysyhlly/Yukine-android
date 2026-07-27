package app.yukine.together

import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import java.io.File

object TogetherQueueItemMapper {
    fun fromTracks(tracks: List<Track>): List<TogetherQueueItem> =
        tracks.map(::fromTrack)

    fun fromTrack(track: Track): TogetherQueueItem {
        localSource(track)?.let { sourceUri ->
            return localItem(track, sourceUri)
        }

        val provider = StreamingDataPathMetadata.provider(track.dataPath)
        val providerTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
        if (provider != null && providerTrackId.isNotBlank()) {
            val resolvedUrl = track.contentUri
                ?.toString()
                .orEmpty()
                .takeIf(::isHttpUrl)
                .orEmpty()
            val source = TogetherQueueSource.Streaming(
                provider = provider.wireName,
                providerTrackId = providerTrackId,
                quality = StreamingDataPathMetadata.quality(track.dataPath),
                durationMs = track.durationMs,
                resolvedUrl = resolvedUrl,
                mimeType = StreamingDataPathMetadata.playbackMimeType(track.dataPath),
                luoxueMusicInfoJson = StreamingDataPathMetadata.luoxueMusicInfoJson(track.dataPath)
            )
            return TogetherQueueItem(
                // Canonical streaming key — never Media3 synthetic id — so live updateQueue
                // does not rename rows relative to catalog/native fileIDs.
                stableId = TogetherStableIds.streaming(provider.wireName, providerTrackId),
                title = track.title,
                artist = track.artist,
                sourceUri = track.dataPath,
                shareable = true,
                album = track.album,
                artworkUri = track.albumArtUri?.toString().orEmpty(),
                durationMs = track.durationMs,
                source = source
            )
        }

        val sourceUri = track.dataPath.takeIf(String::isNotBlank)
            ?: track.contentUri?.toString().orEmpty()
        return localItem(track, sourceUri)
    }

    private fun localSource(track: Track): String? {
        val contentUri = track.contentUri?.toString().orEmpty()
        if (isLocalUri(contentUri)) {
            return contentUri
        }
        if (!StreamingDataPathMetadata.isStreamingTrack(track.dataPath) &&
            isLocalUri(track.dataPath)
        ) {
            return track.dataPath
        }
        return null
    }

    private fun localItem(track: Track, sourceUri: String): TogetherQueueItem {
        val file = sourceUri.removePrefix("file://").let(::File)
        val readable = sourceUri.startsWith("content://", ignoreCase = true) || file.isFile
        val sizeBytes = file.takeIf(File::isFile)?.length() ?: 0L
        return TogetherQueueItem(
            stableId = track.id.toString(),
            title = track.title,
            artist = track.artist,
            sourceUri = sourceUri,
            sizeBytes = sizeBytes,
            shareable = readable,
            album = track.album,
            artworkUri = track.albumArtUri?.toString().orEmpty(),
            durationMs = track.durationMs,
            source = TogetherQueueSource.Local(
                uri = sourceUri,
                sizeBytes = sizeBytes
            )
        )
    }

    private fun isLocalUri(value: String): Boolean {
        if (value.isBlank()) return false
        return when {
            value.startsWith("content://", ignoreCase = true) -> true
            value.startsWith("file://", ignoreCase = true) ->
                File(value.removePrefix("file://")).isFile
            "://" in value -> false
            else -> File(value).isFile
        }
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
}
