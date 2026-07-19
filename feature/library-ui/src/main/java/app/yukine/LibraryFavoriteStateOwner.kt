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
    private val data: LibraryDataStateOwner,
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
        if (!data.beginFavoriteMutation(track.id)) return
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
        }.invokeOnCompletion {
            data.endFavoriteMutations(setOf(track.id))
        }
    }

    fun favoriteAll(tracks: List<Track>) {
        val currentWriter = writer ?: return
        val tracksById = tracks
            .asSequence()
            .filter { TrackIdentity.isUsable(it.id) }
            .associateBy { it.id }
        val pendingIds = data.beginFavoriteMutations(tracksById.keys)
        if (pendingIds.isEmpty()) return
        scope.launch {
            try {
                val succeededIds = mutations.runLocked {
                    pendingIds.mapNotNullTo(LinkedHashSet()) { trackId ->
                        val track = tracksById.getValue(trackId)
                        try {
                            trackId.takeIf { currentWriter.writeFavorite(track, true) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                if (succeededIds.isNotEmpty()) {
                    gateway()?.applyFavorites(succeededIds, true)
                }
                if (succeededIds.size < pendingIds.size) {
                    gateway()?.showStatusKey("library.favorite.failed")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway()?.showStatusKey("library.favorite.failed")
            }
        }.invokeOnCompletion {
            data.endFavoriteMutations(pendingIds)
        }
    }
}
