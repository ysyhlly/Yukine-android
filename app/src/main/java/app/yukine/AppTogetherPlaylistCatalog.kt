package app.yukine

import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import app.yukine.playback.TogetherHttpRangeProbe
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import app.yukine.together.TogetherPlaylistCatalogPort
import app.yukine.together.TogetherPlaylistCatalogResult
import app.yukine.together.TogetherPlaylistLoadResult
import app.yukine.together.TogetherPlaylistRef
import app.yukine.together.TogetherPlaylistSummary
import app.yukine.together.TogetherQueueItem
import app.yukine.together.TogetherQueueSource
import app.yukine.together.TogetherSkippedItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bridges Together to the existing local and account-backed playlist owners. */
internal class AppTogetherPlaylistCatalog(
    private val libraryViewModel: LibraryViewModel,
    private val streamingViewModel: StreamingViewModel,
    private val quality: () -> StreamingAudioQuality,
    private val languageMode: () -> String = { AppLanguage.MODE_CHINESE }
) : TogetherPlaylistCatalogPort {
    override suspend fun listPlaylists(): TogetherPlaylistCatalogResult {
        val playlists = ArrayList<TogetherPlaylistSummary>()
        val warnings = ArrayList<String>()
        libraryViewModel.data.playlists().forEach { playlist ->
            playlists += TogetherPlaylistSummary(
                ref = TogetherPlaylistRef.Local(playlist.id),
                title = playlist.name,
                subtitle = text("together.playlist.source.local"),
                trackCount = playlist.trackCount
            )
        }
        streamingViewModel.state.providers
            .asSequence()
            .filter { descriptor ->
                descriptor.enabled &&
                    descriptor.capabilities.supportsPlaylists &&
                    descriptor.capabilities.supportsAudioResolve
            }
            .forEach { descriptor ->
                runCatching {
                    streamingViewModel.playlists.userPlaylistsForTogether(descriptor.name)
                }.onSuccess { remotePlaylists ->
                    remotePlaylists.forEach { playlist ->
                        if (playlist.providerPlaylistId.isNotBlank()) {
                            playlists += TogetherPlaylistSummary(
                                ref = TogetherPlaylistRef.Streaming(
                                    playlist.provider.wireName,
                                    playlist.providerPlaylistId
                                ),
                                title = playlist.title,
                                subtitle = descriptor.displayName,
                                trackCount = playlist.trackCount ?: 0
                            )
                        }
                    }
                }.onFailure { error ->
                    warnings += "${descriptor.displayName}: ${error.safeMessage()}"
                }
            }
        return TogetherPlaylistCatalogResult(playlists, warnings)
    }

    override suspend fun loadPlaylist(ref: TogetherPlaylistRef): TogetherPlaylistLoadResult =
        when (ref) {
            is TogetherPlaylistRef.Local -> loadLocal(ref)
            is TogetherPlaylistRef.Streaming -> loadStreaming(ref)
        }

    private suspend fun loadLocal(ref: TogetherPlaylistRef.Local): TogetherPlaylistLoadResult {
        val playlist = libraryViewModel.data.playlists().firstOrNull { it.id == ref.playlistId }
        val tracks = libraryViewModel.playlists.loadPlaylistTracksForTogether(ref.playlistId)
        return prepareTracks(
            playlist?.name ?: text("together.playlist.source.local"),
            tracks.map(::LocalTrackCandidate)
        )
    }

    private suspend fun loadStreaming(ref: TogetherPlaylistRef.Streaming): TogetherPlaylistLoadResult {
        val cloudTitle = text("together.playlist.source.cloud")
        val provider = StreamingProviderName.fromWireName(ref.provider)
            ?: return TogetherPlaylistLoadResult(
                title = cloudTitle,
                items = emptyList(),
                skipped = listOf(
                    TogetherSkippedItem(cloudTitle, text("together.skip.provider.unavailable"))
                )
            )
        val (title, tracks) = streamingViewModel.playlists.playlistTracksForTogether(
            provider,
            ref.providerPlaylistId
        )
        return prepareTracks(title, tracks.map(::StreamingTrackCandidate))
    }

    private suspend fun prepareTracks(
        title: String,
        candidates: List<TrackCandidate>
    ): TogetherPlaylistLoadResult {
        val items = ArrayList<TogetherQueueItem>()
        val skipped = ArrayList<TogetherSkippedItem>()
        val seen = HashSet<String>()
        for (candidate in candidates) {
            if (!seen.add(candidate.dedupeKey)) continue
            runCatching { candidate.prepare() }
                .onSuccess { item -> items += item }
                .onFailure { error ->
                    skipped += TogetherSkippedItem(candidate.title, error.safeMessage())
                }
        }
        return TogetherPlaylistLoadResult(title, items, skipped)
    }

    private sealed interface TrackCandidate {
        val title: String
        val dedupeKey: String
        suspend fun prepare(): TogetherQueueItem
    }

    private inner class LocalTrackCandidate(private val track: Track) : TrackCandidate {
        override val title: String = track.title
        override val dedupeKey: String = localSource(track)
            .takeIf(String::isNotBlank)
            ?.let { "local:$it" }
            ?: "streaming:${StreamingDataPathMetadata.providerName(track.dataPath)}:" +
                StreamingDataPathMetadata.providerTrackId(track.dataPath)

        override suspend fun prepare(): TogetherQueueItem {
            val localSource = localSource(track)
            if (localSource.isNotBlank()) {
                val file = localSource.removePrefix("file://").let(::File)
                require(localSource.startsWith("content://") || file.isFile) {
                    text("together.skip.local.unreadable")
                }
                val size = if (file.isFile) file.length() else 0L
                return TogetherQueueItem(
                    // Match TogetherQueueItemMapper / Media3 track id so remove + retain share identity.
                    stableId = track.id.toString(),
                    title = track.title,
                    artist = track.artist,
                    sourceUri = localSource,
                    sizeBytes = size,
                    source = TogetherQueueSource.Local(localSource, size)
                )
            }
            val provider = StreamingDataPathMetadata.provider(track.dataPath)
            val providerTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath)
            if (provider != null && providerTrackId.isNotBlank()) {
                return prepareStreaming(
                    StreamingTrack(
                        provider = provider,
                        providerTrackId = providerTrackId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                        playable = true,
                        luoxueMusicInfoJson =
                            StreamingDataPathMetadata.luoxueMusicInfoJson(track.dataPath)
                    )
                )
            }
            error(text("together.skip.local.missing"))
        }
    }

    private inner class StreamingTrackCandidate(private val track: StreamingTrack) : TrackCandidate {
        override val title: String = track.title
        override val dedupeKey: String = track.stableKey

        override suspend fun prepare(): TogetherQueueItem = prepareStreaming(track)
    }

    private suspend fun prepareStreaming(track: StreamingTrack): TogetherQueueItem =
        withContext(Dispatchers.IO) {
            require(track.playable) {
                track.unavailableReason ?: text("together.skip.not.playable")
            }
            val selectedQuality = quality()
            val resolved = streamingViewModel.playlists.resolvePlaybackForTogether(track, selectedQuality)
            require(resolved.url.startsWith("https://") || resolved.url.startsWith("http://")) {
                text("together.skip.no.http.source")
            }
            val size = TogetherHttpRangeProbe.contentLength(resolved.url, resolved.headers)
            val source = TogetherQueueSource.Streaming(
                provider = track.provider.wireName,
                providerTrackId = track.providerTrackId,
                quality = selectedQuality.wireName,
                durationMs = track.durationMs ?: 0L,
                resolvedUrl = resolved.url,
                headers = resolved.headers,
                expiresAtEpochMs = resolved.expiresAtEpochMs,
                mimeType = resolved.mimeType.orEmpty(),
                supportsRange = size > 0L,
                sizeBytes = size,
                luoxueMusicInfoJson = track.luoxueMusicInfoJson
            )
            TogetherQueueItem(
                stableId = track.stableKey,
                title = track.title,
                artist = track.artist,
                sourceUri = resolved.url,
                sizeBytes = size,
                source = source
            )
        }

    private fun localSource(track: Track): String {
        val contentUri = track.contentUri?.toString().orEmpty()
        if (contentUri.startsWith("content://", ignoreCase = true)) {
            return contentUri
        }
        if (contentUri.startsWith("file://", ignoreCase = true) &&
            File(contentUri.removePrefix("file://")).isFile
        ) {
            return contentUri
        }
        if (!StreamingDataPathMetadata.isStreamingTrack(track.dataPath) &&
            File(track.dataPath).isFile
        ) {
            return track.dataPath
        }
        return ""
    }

    private fun text(key: String): String = AppLanguage.text(languageMode(), key)

    private fun Throwable.safeMessage(): String =
        message?.takeIf(String::isNotBlank) ?: text("together.skip.resolve.failed")
}
