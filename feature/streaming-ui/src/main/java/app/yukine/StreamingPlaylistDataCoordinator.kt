package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylistImporter
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Owns the data-side work for streaming playlist transfers. UI state and presentation remain in
 * StreamingViewModel, while repository paging and local-playlist mutations have one feature owner.
 */
internal class StreamingPlaylistDataCoordinator(
    private val repositoryProvider: () -> StreamingRepository,
    private val localOperationsProvider: () -> StreamingLocalPlaylistOperations?,
    private val ioDispatcherProvider: () -> CoroutineDispatcher
) {
    suspend fun loadPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): Pair<String, List<StreamingTrack>> {
        val repository = repositoryProvider()
        return withContext(ioDispatcherProvider()) {
            val tracks = ArrayList<StreamingTrack>()
            var playlistName: String? = null
            var page = 1
            var total: Int? = null
            while (true) {
                val detail = repository.playlist(
                    provider = provider,
                    providerPlaylistId = providerPlaylistId,
                    page = page,
                    pageSize = STREAMING_PLAYLIST_PAGE_SIZE,
                    useCache = false
                )
                if (playlistName.isNullOrBlank()) {
                    playlistName = detail.playlist?.title?.takeIf { it.isNotBlank() }
                }
                total = detail.total ?: total
                val remainingCapacity = STREAMING_PLAYLIST_MAX_TRACKS - tracks.size
                val acceptedTracks = if (remainingCapacity > 0) {
                    detail.tracks.take(remainingCapacity)
                } else {
                    emptyList()
                }
                tracks.addAll(acceptedTracks)

                val reachedTotal = total?.let { expected -> tracks.size >= expected } == true
                val reachedLocalPageCap = page >= STREAMING_PLAYLIST_MAX_PAGES
                val reachedLocalTrackCap = acceptedTracks.size < detail.tracks.size ||
                    tracks.size >= STREAMING_PLAYLIST_MAX_TRACKS
                if (!detail.hasMore || detail.tracks.isEmpty() || reachedTotal ||
                    reachedLocalPageCap || reachedLocalTrackCap
                ) {
                    break
                }
                page += 1
            }
            (playlistName?.takeIf { it.isNotBlank() }
                ?: "Streaming playlist $providerPlaylistId") to tracks
        }
    }

    suspend fun importTracksToLocal(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        tracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): StreamingLocalPlaylistImportResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        val result = withContext(ioDispatcherProvider()) {
            operations.importStreamingPlaylist(
                playlistName,
                provider,
                providerPlaylistId,
                tracks,
                linkWhenProviderPlaylistIdBlank
            )
        }
        return StreamingLocalPlaylistImportResult(
            playlistName = result.playlistName,
            playlistAddedCount = result.playlistAddedCount,
            empty = result.isEmpty
        )
    }

    suspend fun syncPlaylistToLocal(
        link: StreamingPlaylistSyncStore.LinkedPlaylist
    ): StreamingLocalPlaylistSyncResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        val repository = repositoryProvider()
        val tracks = if (link.providerPlaylistId.isNullOrBlank()) {
            repository.userLikedTracks(link.provider)
        } else {
            loadPlaylistTracks(link.provider, link.providerPlaylistId).second
        }
        return withContext(ioDispatcherProvider()) {
            operations.syncStreamingPlaylist(link, tracks)
        }
    }

    suspend fun ensureLoginPlaylist(
        playlistName: String,
        provider: StreamingProviderName
    ): StreamingLoginPlaylistResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        return withContext(ioDispatcherProvider()) {
            operations.ensureStreamingLoginPlaylist(playlistName, provider)
        }
    }

    suspend fun importPlaylistToStreaming(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>
    ): app.yukine.streaming.StreamingPlaylistImportSummary {
        val importer = StreamingPlaylistImporter(repositoryProvider())
        val summary = importer.importToStreaming(
            provider,
            playlistName,
            localTracks
        )
        return importer.createRemotePlaylist(summary)
    }

    companion object {
        const val STREAMING_PLAYLIST_PAGE_SIZE = 2_000
        const val STREAMING_PLAYLIST_MAX_PAGES = 50
        const val STREAMING_PLAYLIST_MAX_TRACKS =
            STREAMING_PLAYLIST_PAGE_SIZE * STREAMING_PLAYLIST_MAX_PAGES
    }
}
