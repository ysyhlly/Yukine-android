package app.yukine

import app.yukine.model.Track

internal class LibraryFavoriteWriterBindings(
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : LibraryFavoriteWriter {
    override fun writeFavorite(track: Track, favorite: Boolean): Boolean =
        toggleFavoriteUseCase.execute(track, favorite)
}

internal class LibraryPlaylistTrackLoaderBindings(
    private val loadPlaylistTracksUseCase: LoadPlaylistTracksUseCase
) : LibraryPlaylistTrackLoader {
    override fun loadPlaylistTracks(playlistId: Long): List<Track> =
        loadPlaylistTracksUseCase.execute(playlistId)
}
