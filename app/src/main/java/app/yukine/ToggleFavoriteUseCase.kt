package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track

internal interface FavoriteOperations {
    fun isFavorite(trackId: Long): Boolean
    fun setFavorite(track: Track, favorite: Boolean)
}

internal class MusicLibraryFavoriteOperations(
    private val repository: MusicLibraryRepository,
    private val syncEvents: FavoriteSyncEventBus? = null
) : FavoriteOperations {
    override fun isFavorite(trackId: Long): Boolean {
        return repository.isFavorite(trackId)
    }

    override fun setFavorite(track: Track, favorite: Boolean) {
        val changed = repository.isFavorite(track.id) != favorite
        repository.setFavorite(track, favorite)
        if (changed) syncEvents?.publish(track, favorite)
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

    fun toggle(track: Track?): Boolean {
        if (track == null) {
            return false
        }
        operations.setFavorite(track, !operations.isFavorite(track.id))
        return true
    }

    fun isFavorite(track: Track?): Boolean {
        return track != null && operations.isFavorite(track.id)
    }
}
