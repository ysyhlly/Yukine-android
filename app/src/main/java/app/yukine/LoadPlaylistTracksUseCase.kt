package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track

internal interface PlaylistTrackOperations {
    fun loadPlaylistTracks(playlistId: Long): List<Track>
}

internal class MusicLibraryPlaylistTrackOperations(
    private val repository: MusicLibraryRepository
) : PlaylistTrackOperations {
    override fun loadPlaylistTracks(playlistId: Long): List<Track> =
        repository.loadPlaylistTracks(playlistId)
}

internal class LoadPlaylistTracksUseCase(
    private val operations: PlaylistTrackOperations
) {
    fun execute(playlistId: Long): List<Track> {
        if (playlistId < 0L) {
            return emptyList()
        }
        return operations.loadPlaylistTracks(playlistId)
    }
}
