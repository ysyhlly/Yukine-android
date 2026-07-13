package app.yukine

import app.yukine.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Focused owner for playlist loading, collection reads and playlist mutations. */
class LibraryPlaylistStateOwner internal constructor(
    private val scope: CoroutineScope,
    private val mutations: LibraryMutationContext,
    private val gateway: () -> LibraryGateway?
) {
    private var trackLoader: LibraryPlaylistTrackLoader? = null
    private var collectionGateway: LibraryCollectionGateway? = null
    private var actionGateway: LibraryPlaylistActionGateway? = null
    private var collectionLoadJob: Job? = null
    private var nextCollectionLoadId = 0L
    private var activeCollectionLoadId = 0L

    fun bindTrackLoader(next: LibraryPlaylistTrackLoader?) {
        trackLoader = next
    }

    fun bindCollectionGateway(next: LibraryCollectionGateway?) {
        collectionGateway = next
    }

    fun bindActionGateway(next: LibraryPlaylistActionGateway?) {
        actionGateway = next
    }

    fun play(playlistId: Long) {
        val loader = trackLoader
        if (loader == null) {
            gateway()?.showStatusKey("no.tracks.in.playlist")
            return
        }
        scope.launch {
            try {
                val tracks = mutations.runLocked { loader.loadPlaylistTracks(playlistId) }
                if (tracks.isEmpty()) {
                    gateway()?.showStatusKey("no.tracks.in.playlist")
                } else {
                    gateway()?.playTrackList(tracks, 0)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                gateway()?.showStatusKey("library.playlist.load.failed")
            }
        }
    }

    fun loadCollections(
        selectedPlaylistId: Long,
        onLoaded: ((LibraryCollectionsResult) -> Unit)? = null
    ) {
        val source = collectionGateway ?: return
        val loadId = ++nextCollectionLoadId
        activeCollectionLoadId = loadId
        collectionLoadJob?.cancel()
        collectionLoadJob = scope.launch {
            try {
                val result = mutations.runLocked { source.loadCollections(selectedPlaylistId) }
                if (activeCollectionLoadId == loadId) onLoaded?.invoke(result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeCollectionLoadId == loadId) gateway()?.showStatusKey("library.collections.failed")
            } finally {
                if (activeCollectionLoadId == loadId) {
                    activeCollectionLoadId = 0L
                    collectionLoadJob = null
                }
            }
        }
    }

    fun loadCollectionsJava(selectedPlaylistId: Long, onLoaded: LibraryCollectionsCallback?) {
        loadCollections(selectedPlaylistId) { result -> onLoaded?.onLoaded(result) }
    }

    fun clearPlayHistory(onCleared: ((Int) -> Unit)? = null) {
        val source = collectionGateway ?: return
        mutations.launch("library.history.clear.failed", source::clearPlayHistory, onCleared)
    }

    fun addAllToDefault(tracks: List<Track>) {
        val actions = actionGateway ?: return
        if (tracks.isEmpty()) return
        scope.launch {
            val succeeded = mutations.runLocked {
                tracks.count { track ->
                    try {
                        actions.addToDefaultPlaylist(track)?.added == true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            gateway()?.showStatusKey(
                if (succeeded == tracks.size) "added.to.playlist" else "could.not.add.to.playlist"
            )
        }
    }

    fun addToDefaultPlaylist(
        track: Track?,
        onAdded: ((LibraryDefaultPlaylistAddResultUi) -> Unit)? = null
    ) {
        if (track == null) return
        val actions = actionGateway ?: return
        mutations.launch(
            failureStatusKey = "library.playlist.action.failed",
            operation = { actions.addToDefaultPlaylist(track) },
            onSuccess = { result ->
                if (result == null) gateway()?.showStatusKey("library.playlist.action.failed")
                else onAdded?.invoke(result)
            }
        )
    }

    fun addToDefaultPlaylistJava(track: Track?, onAdded: LibraryDefaultPlaylistTrackAddedCallback?) {
        addToDefaultPlaylist(track) { result -> onAdded?.onAdded(result.playlistId, result.added) }
    }

    fun createPlaylist(name: String, onCreated: ((Long) -> Unit)? = null) {
        val actions = actionGateway ?: return
        mutations.launch(
            "library.playlist.action.failed",
            { actions.createPlaylist(name) },
            { playlistId ->
                if (playlistId >= 0L) onCreated?.invoke(playlistId)
                else gateway()?.showStatusKey("library.playlist.action.failed")
            }
        )
    }

    fun createPlaylistJava(name: String, onCreated: LibraryPlaylistCreatedCallback?) {
        createPlaylist(name) { playlistId -> onCreated?.onCreated(playlistId) }
    }

    fun renamePlaylist(playlistId: Long, name: String, onRenamed: ((Boolean) -> Unit)? = null) {
        val actions = actionGateway ?: return
        mutations.launch("library.playlist.action.failed", { actions.renamePlaylist(playlistId, name) }, onRenamed)
    }

    fun renamePlaylistJava(playlistId: Long, name: String, onRenamed: LibraryPlaylistRenamedCallback?) {
        renamePlaylist(playlistId, name) { renamed -> onRenamed?.onRenamed(playlistId, renamed) }
    }

    fun deletePlaylist(playlistId: Long, name: String, onDeleted: ((Boolean) -> Unit)? = null) {
        val actions = actionGateway ?: return
        mutations.launch("library.playlist.action.failed", { actions.deletePlaylist(playlistId) }, onDeleted)
    }

    fun deletePlaylistJava(playlistId: Long, name: String, onDeleted: LibraryPlaylistDeletedCallback?) {
        deletePlaylist(playlistId, name) { deleted -> onDeleted?.onDeleted(playlistId, name, deleted) }
    }

    fun removeSelectedPlaylistTrack(
        playlistId: Long,
        track: Track?,
        onRemoved: ((Track) -> Unit)? = null
    ) {
        if (playlistId < 0L || track == null) return
        val actions = actionGateway ?: return
        mutations.launch(
            "library.playlist.action.failed",
            { actions.removeTrackFromPlaylist(playlistId, track) },
            { removed -> if (removed) onRemoved?.invoke(track) }
        )
    }

    fun removeSelectedPlaylistTrackJava(
        playlistId: Long,
        track: Track?,
        onRemoved: LibrarySelectedPlaylistTrackRemovedCallback?
    ) {
        removeSelectedPlaylistTrack(playlistId, track) { removed -> onRemoved?.onRemoved(playlistId, removed) }
    }

    fun moveSelectedPlaylistTrack(
        playlistId: Long,
        track: Track?,
        trackIndex: Int,
        direction: Int,
        onMoved: ((Boolean) -> Unit)? = null
    ) {
        if (playlistId < 0L || track == null) return
        val actions = actionGateway ?: return
        mutations.launch(
            "library.playlist.action.failed",
            { actions.movePlaylistTrack(playlistId, track, trackIndex, direction) },
            onMoved
        )
    }

    fun moveSelectedPlaylistTrackJava(
        playlistId: Long,
        track: Track?,
        trackIndex: Int,
        direction: Int,
        onMoved: LibrarySelectedPlaylistTrackMovedCallback?
    ) {
        moveSelectedPlaylistTrack(playlistId, track, trackIndex, direction) { moved ->
            if (track != null) onMoved?.onMoved(playlistId, track, direction, moved)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long, onAdded: ((Boolean) -> Unit)? = null) {
        val actions = actionGateway ?: return
        mutations.launch("library.playlist.action.failed", { actions.addTrackToPlaylist(playlistId, trackId) }, onAdded)
    }

    fun addTrackToPlaylistJava(playlistId: Long, trackId: Long, onAdded: LibraryTrackAddedToPlaylistCallback?) {
        addTrackToPlaylist(playlistId, trackId) { added -> onAdded?.onAdded(playlistId, added) }
    }
}

/** Stateless status presentation for playlist actions. */
object LibraryPlaylistStatusFactory {
    fun defaultAdd(added: Boolean, languageMode: String) = LibraryPlaylistActionPresentation(
        status = text(languageMode, if (added) "added.to.playlist" else "could.not.add.to.playlist")
    )

    fun created(languageMode: String) = LibraryPlaylistActionPresentation(
        status = text(languageMode, "playlist.created")
    )

    fun renamed(renamed: Boolean, languageMode: String) = LibraryPlaylistActionPresentation(
        status = text(languageMode, if (renamed) "playlist.renamed" else "playlist.rename.failed")
    )

    fun deleted(name: String?, deleted: Boolean, languageMode: String) = LibraryPlaylistActionPresentation(
        status = if (deleted) text(languageMode, "deleted.playlist.prefix") + name.orEmpty()
        else text(languageMode, "could.not.delete.playlist")
    )

    fun removed(track: Track?, languageMode: String) = LibraryPlaylistActionPresentation(
        status = text(languageMode, "removed.from.playlist.prefix") + (track?.title ?: "")
    )

    fun moved(track: Track?, direction: Int, moved: Boolean, languageMode: String) =
        LibraryPlaylistActionPresentation(
            status = if (moved) {
                text(languageMode, if (direction < 0) "moved.up.prefix" else "moved.down.prefix") +
                    (track?.title ?: "")
            } else {
                text(languageMode, "move.failed")
            }
        )

    private fun text(languageMode: String, key: String) = AppLanguage.text(languageMode, key)
}
