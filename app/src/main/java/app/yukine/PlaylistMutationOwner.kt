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
        viewModel.playlists.createPlaylistJava(name, ::onPlaylistCreated)
    }

    override fun renamePlaylist(playlistId: Long, name: String) {
        viewModel.playlists.renamePlaylistJava(playlistId, name, ::onPlaylistRenamed)
    }

    override fun deletePlaylist(playlistId: Long, name: String) {
        viewModel.playlists.deletePlaylistJava(playlistId, name, ::onPlaylistDeleted)
    }

    override fun addToDefaultPlaylist(track: Track) {
        viewModel.playlists.addToDefaultPlaylistJava(track, ::onDefaultPlaylistTrackAdded)
    }

    override fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModel.playlists.addTrackToPlaylistJava(playlistId, trackId, ::onTrackAddedToPlaylist)
    }

    fun onDefaultPlaylistTrackAdded(playlistId: Long, added: Boolean) {
        statusSink.setStatus(LibraryPlaylistStatusFactory.defaultAdd(added, languageMode()).status)
        routeController.setSelectedPlaylistId(playlistId)
        collectionsLoader.loadCollections()
    }

    fun onPlaylistCreated(playlistId: Long) {
        if (playlistId >= 0L) {
            routeController.setSelectedPlaylistId(playlistId)
        }
        statusSink.setStatus(LibraryPlaylistStatusFactory.created(languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onPlaylistRenamed(playlistId: Long, renamed: Boolean) {
        if (renamed) {
            routeController.setSelectedPlaylistId(playlistId)
        }
        statusSink.setStatus(LibraryPlaylistStatusFactory.renamed(renamed, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onPlaylistDeleted(playlistId: Long, name: String, deleted: Boolean) {
        if (deleted && routeController.selectedPlaylistId() == playlistId) {
            routeController.setSelectedPlaylistId(-1L)
        }
        statusSink.setStatus(LibraryPlaylistStatusFactory.deleted(name, deleted, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onSelectedPlaylistTrackRemoved(playlistId: Long, track: Track) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(LibraryPlaylistStatusFactory.removed(track, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    fun onSelectedPlaylistTrackMoved(playlistId: Long, track: Track, direction: Int, moved: Boolean) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(
            LibraryPlaylistStatusFactory.moved(track, direction, moved, languageMode()).status
        )
        collectionsLoader.loadCollections()
    }

    fun onTrackAddedToPlaylist(playlistId: Long, added: Boolean) {
        routeController.setSelectedPlaylistId(playlistId)
        statusSink.setStatus(LibraryPlaylistStatusFactory.defaultAdd(added, languageMode()).status)
        collectionsLoader.loadCollections()
    }

    private fun languageMode(): String = languageModeSource.languageMode()
}
