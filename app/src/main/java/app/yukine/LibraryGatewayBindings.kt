package app.yukine

import app.yukine.model.Track

internal fun interface LibraryTrackListAction {
    fun run(tracks: List<Track>, index: Int)
}

internal fun interface LibraryTrackAction {
    fun run(track: Track)
}

internal fun interface LibraryStatusTextProvider {
    fun text(key: String): String
}

internal fun interface LibraryStatusSink {
    fun set(message: String)
}

internal fun interface LibraryFavoriteAction {
    fun set(trackId: Long, favorite: Boolean)
}

internal fun interface LibraryModeAction {
    fun run(mode: String)
}

internal fun interface LibraryGroupAction {
    fun open(key: String, title: String)
}

internal fun interface LibraryPlaylistAction {
    fun open(playlistId: Long, title: String)
}

internal fun interface LibrarySearchAction {
    fun search(query: String)
}

internal class LibraryGatewayBindings(
    private val playTrackListAction: LibraryTrackListAction,
    private val statusTextProvider: LibraryStatusTextProvider,
    private val statusSink: LibraryStatusSink,
    private val favoriteAction: LibraryFavoriteAction,
    private val addToPlaylistAction: LibraryTrackAction,
    private val changeGroupModeAction: LibraryModeAction,
    private val openGroupAction: LibraryGroupAction,
    private val openPlaylistAction: LibraryPlaylistAction,
    private val backFromGroupAction: Runnable,
    private val searchAction: LibrarySearchAction,
    private val importFilesAction: Runnable,
    private val scanLibraryAction: Runnable
) : LibraryGateway {
    override fun playTrackList(tracks: List<Track>, index: Int) {
        playTrackListAction.run(tracks, index)
    }

    override fun showStatusKey(key: String) {
        statusSink.set(statusTextProvider.text(key))
    }

    override fun applyFavorite(trackId: Long, favorite: Boolean) {
        favoriteAction.set(trackId, favorite)
    }

    override fun addToPlaylist(track: Track) {
        addToPlaylistAction.run(track)
    }

    override fun changeGroupMode(mode: String) {
        changeGroupModeAction.run(mode)
    }

    override fun openGroup(key: String, title: String) {
        openGroupAction.open(key, title)
    }

    override fun openPlaylist(playlistId: Long, title: String) {
        openPlaylistAction.open(playlistId, title)
    }

    override fun backFromGroup() {
        backFromGroupAction.run()
    }

    override fun search(query: String) {
        searchAction.search(query)
    }

    override fun importFiles() {
        importFilesAction.run()
    }

    override fun scanLibrary() {
        scanLibraryAction.run()
    }
}
