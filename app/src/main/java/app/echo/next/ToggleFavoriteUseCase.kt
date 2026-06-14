package app.echo.next

import app.echo.next.data.MusicLibraryRepository
import app.echo.next.model.Track

internal interface FavoriteOperations {
    fun setFavorite(track: Track, favorite: Boolean)
}

internal class MusicLibraryFavoriteOperations(
    private val repository: MusicLibraryRepository
) : FavoriteOperations {
    override fun setFavorite(track: Track, favorite: Boolean) {
        repository.setFavorite(track, favorite)
    }
}

internal class ToggleFavoriteUseCase(
    private val operations: FavoriteOperations
) {
    fun execute(track: Track?, favorite: Boolean): Boolean {
        if (track == null) {
            return false
        }
        operations.setFavorite(track, favorite)
        return true
    }
}
