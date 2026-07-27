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
        val knownIds = idsProvider?.favoriteIds() ?: data.favoriteIds()
        val previousFavorite = track.id in knownIds
        val nextFavorite = !previousFavorite
        // Optimistic UI on the local store only. Notify gateway after Room success so remote
        // favorite sync does not flash and roll back twice on a failed write.
        data.setFavorite(track.id, nextFavorite)
        scope.launch {
            try {
                val written = mutations.runLocked {
                    currentWriter.writeFavorite(track, nextFavorite)
                }
                if (written) {
                    gateway()?.applyFavorite(track.id, nextFavorite)
                } else {
                    data.setFavorite(track.id, previousFavorite)
                    gateway()?.showStatusKey("library.favorite.failed")
                }
            } catch (error: CancellationException) {
                data.setFavorite(track.id, previousFavorite)
                throw error
            } catch (_: Exception) {
                data.setFavorite(track.id, previousFavorite)
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
        // Optimistic local UI; gateway notified only for ids that persist successfully.
        data.setFavorites(pendingIds, true)
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
                val failedIds = pendingIds - succeededIds
                if (failedIds.isNotEmpty()) {
                    data.setFavorites(failedIds, false)
                    gateway()?.showStatusKey("library.favorite.failed")
                }
                if (succeededIds.isNotEmpty()) {
                    gateway()?.applyFavorites(succeededIds, true)
                }
            } catch (error: CancellationException) {
                data.setFavorites(pendingIds, false)
                throw error
            } catch (_: Exception) {
                data.setFavorites(pendingIds, false)
                gateway()?.showStatusKey("library.favorite.failed")
            }
        }.invokeOnCompletion {
            data.endFavoriteMutations(pendingIds)
        }
    }
}
