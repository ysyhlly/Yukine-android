package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track

/** Owns playlist mutations and their route/status/collection consequences. */
internal class PlaylistMutationOwner @JvmOverloads constructor(
    private val viewModel: LibraryViewModel,
    private val routeController: MainRouteController,
    private val languageModeSource: LanguageModeSource,
    private val statusSink: StatusSink,
    private val collectionsLoader: CollectionsLoader,
    private val libraryStore: LibraryDataStateOwner = viewModel.dataOwner()
) : PlaylistDialogController.Listener {
    fun interface LanguageModeSource {
        fun languageMode(): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    fun interface CollectionsLoader {
        fun loadCollections()
    }

    override fun createPlaylist(name: String) {
        viewModel.playlists.createPlaylistJava(name) { playlistId ->
            onPlaylistCreated(playlistId, name)
        }
    }

    override fun renamePlaylist(playlistId: Long, name: String) {
        viewModel.playlists.renamePlaylistJava(playlistId, name) { _, renamed ->
            onPlaylistRenamed(playlistId, name, renamed)
        }
    }

    override fun deletePlaylist(playlistId: Long, name: String) {
        viewModel.playlists.deletePlaylistJava(playlistId, name) { _, _, deleted ->
            onPlaylistDeleted(playlistId, name, deleted)
        }
    }

    override fun addToDefaultPlaylist(track: Track) {
        viewModel.playlists.addToDefaultPlaylistJava(track) { playlistId, added ->
            onDefaultPlaylistTrackAdded(playlistId, track, added)
        }
    }

    override fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModel.playlists.addTrackToPlaylistJava(playlistId, trackId) { _, added ->
            val track = libraryStore.state.value.allTracks.firstOrNull { it.id == trackId }
                ?: libraryStore.state.value.visibleTracks.firstOrNull { it.id == trackId }
                ?: libraryStore.state.value.selectedPlaylistTracks.firstOrNull { it.id == trackId }
            onTrackAddedToPlaylist(playlistId, track, added)
        }
    }

    fun onDefaultPlaylistTrackAdded(playlistId: Long, track: Track?, added: Boolean) {
        statusSink.setStatus(LibraryPlaylistStatusFactory.defaultAdd(added, languageMode()).status)
        routeController.setSelectedPlaylistId(playlistId)
        if (added) {
            patchTrackAdded(playlistId, track)
        } else {
            collectionsLoader.loadCollections()
        }
    }

    fun onPlaylistCreated(playlistId: Long, name: String = "") {
        if (playlistId >= 0L) {
            routeController.setSelectedPlaylistId(playlistId)
            val now = System.currentTimeMillis()
            libraryStore.patchAddPlaylist(Playlist(playlistId, name.ifBlank { "Playlist" }, 0, now, now))
            libraryStore.clearSelectedPlaylistTracks()
        } else {
            collectionsLoader.loadCollections()
        }
        statusSink.setStatus(LibraryPlaylistStatusFactory.created(languageMode()).status)
    }

    fun onPlaylistRenamed(playlistId: Long, name: String, renamed: Boolean) {
        if (renamed) {
            routeController.setSelectedPlaylistId(playlistId)
            if (!libraryStore.patchPlaylistName(playlistId, name)) {
                collectionsLoader.loadCollections()
            }
        }
        statusSink.setStatus(LibraryPlaylistStatusFactory.renamed(renamed, languageMode()).status)
    }

    fun onPlaylistDeleted(playlistId: Long, name: String, deleted: Boolean) {
        val wasSelected = routeController.selectedPlaylistId() == playlistId
        if (deleted && wasSelected) {
            routeController.setSelectedPlaylistId(-1L)
        }
        if (deleted) {
            if (!libraryStore.patchRemovePlaylist(playlistId, clearSelectedTracks = wasSelected)) {
                collectionsLoader.loadCollections()
            }
        }
        statusSink.setStatus(LibraryPlaylistStatusFactory.deleted(name, deleted, languageMode()).status)
    }

    fun onSelectedPlaylistTrackRemoved(playlistId: Long, track: Track) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(LibraryPlaylistStatusFactory.removed(track, languageMode()).status)
        if (!libraryStore.patchRemoveSelectedPlaylistTrack(playlistId, track)) {
            collectionsLoader.loadCollections()
        }
    }

    fun onSelectedPlaylistTrackMoved(
        playlistId: Long,
        track: Track,
        trackIndex: Int,
        direction: Int,
        moved: Boolean
    ) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(
            LibraryPlaylistStatusFactory.moved(track, direction, moved, languageMode()).status
        )
        if (!moved) return
        if (!libraryStore.patchMoveSelectedPlaylistTrack(trackIndex, direction)) {
            collectionsLoader.loadCollections()
        }
    }

    fun onTrackAddedToPlaylist(playlistId: Long, track: Track?, added: Boolean) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(LibraryPlaylistStatusFactory.defaultAdd(added, languageMode()).status)
        if (added) {
            patchTrackAdded(playlistId, track)
        } else {
            collectionsLoader.loadCollections()
        }
    }

    private fun patchTrackAdded(playlistId: Long, track: Track?) {
        val selected = routeController.selectedPlaylistId() == playlistId
        if (!libraryStore.patchTrackAddedToPlaylist(playlistId, track, appendToSelected = selected)) {
            collectionsLoader.loadCollections()
        }
    }

    private fun languageMode(): String = languageModeSource.languageMode()
}
