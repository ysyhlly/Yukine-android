package app.yukine

import app.yukine.model.Track

/** Owns playlist mutations and their route/status/collection consequences. */
internal class PlaylistMutationOwner(
    private val viewModel: LibraryViewModel,
    private val routeController: MainRouteController,
    private val languageModeSource: LanguageModeSource,
    private val statusSink: StatusSink,
    private val collectionsLoader: CollectionsLoader
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
        viewModel.createPlaylistJava(name, ::onPlaylistCreated)
    }

    override fun renamePlaylist(playlistId: Long, name: String) {
        viewModel.renamePlaylistJava(playlistId, name, ::onPlaylistRenamed)
    }

    override fun deletePlaylist(playlistId: Long, name: String) {
        viewModel.deletePlaylistJava(playlistId, name, ::onPlaylistDeleted)
    }

    override fun addToDefaultPlaylist(track: Track) {
        viewModel.addToDefaultPlaylistJava(track, ::onDefaultPlaylistTrackAdded)
    }

    override fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModel.addTrackToPlaylistJava(playlistId, trackId, ::onTrackAddedToPlaylist)
    }

    fun onDefaultPlaylistTrackAdded(playlistId: Long, added: Boolean) {
        statusSink.setStatus(viewModel.defaultPlaylistAddPresentation(added, languageMode()).status)
        routeController.setSelectedPlaylistId(playlistId)
        collectionsLoader.loadCollections()
    }

    fun onPlaylistCreated(playlistId: Long) {
        if (playlistId >= 0L) {
            routeController.setSelectedPlaylistId(playlistId)
        }
        statusSink.setStatus(viewModel.playlistCreatedPresentation(languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onPlaylistRenamed(playlistId: Long, renamed: Boolean) {
        if (renamed) {
            routeController.setSelectedPlaylistId(playlistId)
        }
        statusSink.setStatus(viewModel.playlistRenamedPresentation(renamed, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onPlaylistDeleted(playlistId: Long, name: String, deleted: Boolean) {
        if (deleted && routeController.selectedPlaylistId() == playlistId) {
            routeController.setSelectedPlaylistId(-1L)
        }
        statusSink.setStatus(viewModel.playlistDeletedPresentation(name, deleted, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onSelectedPlaylistTrackRemoved(playlistId: Long, track: Track) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(viewModel.selectedPlaylistTrackRemovedPresentation(track, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onSelectedPlaylistTrackMoved(playlistId: Long, track: Track, direction: Int, moved: Boolean) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(
            viewModel.selectedPlaylistTrackMovedPresentation(track, direction, moved, languageMode()).status
        )
        collectionsLoader.loadCollections()
    }

    fun onTrackAddedToPlaylist(playlistId: Long, added: Boolean) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(viewModel.trackAddedToPlaylistPresentation(added, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    private fun languageMode(): String = languageModeSource.languageMode()
}
