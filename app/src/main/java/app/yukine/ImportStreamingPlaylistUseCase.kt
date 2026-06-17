package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.PlaylistImportResult
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal interface StreamingPlaylistImportOperations {
    fun importStreamingPlaylist(playlistName: String?, tracks: List<Track>): PlaylistImportResult

    fun linkPlaylist(localPlaylistId: Long, provider: StreamingProviderName, providerPlaylistId: String)
}

internal class MusicLibraryStreamingPlaylistImportOperations(
    private val repository: MusicLibraryRepository,
    private val syncStore: StreamingPlaylistSyncStore
) : StreamingPlaylistImportOperations {
    override fun importStreamingPlaylist(playlistName: String?, tracks: List<Track>): PlaylistImportResult =
        repository.importStreamingPlaylist(playlistName, tracks)

    override fun linkPlaylist(localPlaylistId: Long, provider: StreamingProviderName, providerPlaylistId: String) {
        syncStore.linkPlaylist(localPlaylistId, provider, providerPlaylistId)
    }
}

internal class ImportStreamingPlaylistUseCase(
    private val operations: StreamingPlaylistImportOperations
) {
    fun execute(
        playlistName: String?,
        provider: StreamingProviderName,
        providerPlaylistId: String?,
        streamingTracks: List<StreamingTrack?>?
    ): PlaylistImportResult =
        execute(playlistName, provider, providerPlaylistId, streamingTracks, false)

    fun execute(
        playlistName: String?,
        provider: StreamingProviderName,
        providerPlaylistId: String?,
        streamingTracks: List<StreamingTrack?>?,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): PlaylistImportResult {
        val placeholders = streamingTracks
            .orEmpty()
            .mapNotNull { track -> track?.let { StreamingPlaybackAdapter.placeholderTrack(it) } }
        val result = operations.importStreamingPlaylist(playlistName, placeholders)
        val cleanProviderPlaylistId = providerPlaylistId?.trim().orEmpty()
        if (result.playlistId >= 0L && (cleanProviderPlaylistId.isNotEmpty() || linkWhenProviderPlaylistIdBlank)) {
            operations.linkPlaylist(result.playlistId, provider, cleanProviderPlaylistId)
        }
        return result
    }
}
