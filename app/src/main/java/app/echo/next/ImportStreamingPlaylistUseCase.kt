package app.echo.next

import app.echo.next.data.MusicLibraryRepository
import app.echo.next.model.PlaylistImportResult
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingPlaybackAdapter
import app.echo.next.streaming.StreamingPlaylistSyncStore
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack

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
