package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Playlist
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName

internal interface StreamingLoginPlaylistOperations {
    fun createPlaylist(name: String): Long

    fun loadPlaylists(): List<Playlist>

    fun linkPlaylist(localPlaylistId: Long, provider: StreamingProviderName, providerPlaylistId: String)
}

internal class MusicLibraryStreamingLoginPlaylistOperations(
    private val repository: MusicLibraryRepository,
    private val syncStore: StreamingPlaylistSyncStore
) : StreamingLoginPlaylistOperations {
    override fun createPlaylist(name: String): Long =
        repository.createPlaylist(name)

    override fun loadPlaylists(): List<Playlist> =
        repository.loadPlaylists()

    override fun linkPlaylist(localPlaylistId: Long, provider: StreamingProviderName, providerPlaylistId: String) {
        syncStore.linkPlaylist(localPlaylistId, provider, providerPlaylistId)
    }
}

internal data class EnsureStreamingLoginPlaylistResult(
    val playlistId: Long,
    val playlistName: String
)

internal class EnsureStreamingLoginPlaylistUseCase(
    private val operations: StreamingLoginPlaylistOperations
) {
    fun execute(
        playlistName: String,
        provider: StreamingProviderName
    ): EnsureStreamingLoginPlaylistResult {
        var playlistId = operations.createPlaylist(playlistName)
        if (playlistId < 0L) {
            playlistId = operations.loadPlaylists()
                .firstOrNull { it.name == playlistName }
                ?.id
                ?: -1L
        }
        if (playlistId >= 0L) {
            operations.linkPlaylist(playlistId, provider, "")
        }
        return EnsureStreamingLoginPlaylistResult(playlistId, playlistName)
    }
}
