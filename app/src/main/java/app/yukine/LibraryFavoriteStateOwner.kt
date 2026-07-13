package app.yukine

import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The focused owner for favorite reads and atomic writes. */
internal class LibraryFavoriteStateOwner(
    private val scope: CoroutineScope,
    private val mutations: LibraryMutationContext,
    private val gateway: () -> LibraryGateway?
) {
    private var writer: LibraryFavoriteWriter? = null
    private var idsProvider: LibraryFavoriteIdsProvider? = null

    fun bindWriter(next: LibraryFavoriteWriter?) {
        writer = next
    }

    fun bindIdsProvider(next: LibraryFavoriteIdsProvider?) {
        idsProvider = next
    }

    fun toggle(track: Track?) {
        if (track == null || !TrackIdentity.isUsable(track.id)) return
        val currentWriter = writer
        if (currentWriter == null) {
            val favorite = track.id !in idsProvider?.favoriteIds().orEmpty()
            gateway()?.applyFavorite(track.id, favorite)
            return
        }
        scope.launch {
            try {
                val (favorite, written) = mutations.runLocked {
                    val next = track.id !in idsProvider?.favoriteIds().orEmpty()
                    next to currentWriter.writeFavorite(track, next)
                }
                if (written) {
                    gateway()?.applyFavorite(track.id, favorite)
                } else {
                    gateway()?.showStatusKey("library.favorite.failed")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway()?.showStatusKey("library.favorite.failed")
            }
        }
    }

    fun favoriteAll(tracks: List<Track>) {
        val currentWriter = writer ?: return
        if (tracks.isEmpty()) return
        scope.launch {
            val succeeded = mutations.runLocked {
                tracks.count { track ->
                    try {
                        currentWriter.writeFavorite(track, true)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            if (succeeded < tracks.size) gateway()?.showStatusKey("library.favorite.failed")
        }
    }
}
