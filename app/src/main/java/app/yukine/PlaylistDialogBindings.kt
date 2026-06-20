package app.yukine

internal fun interface PlaylistNameAction {
    fun run(name: String)
}

internal fun interface PlaylistRenameAction {
    fun rename(playlistId: Long, name: String)
}

internal fun interface PlaylistDeleteAction {
    fun delete(playlistId: Long, name: String)
}

internal fun interface PlaylistTrackAddAction {
    fun add(playlistId: Long, trackId: Long)
}

internal class PlaylistDialogBindings(
    private val createPlaylistAction: PlaylistNameAction,
    private val renamePlaylistAction: PlaylistRenameAction,
    private val deletePlaylistAction: PlaylistDeleteAction,
    private val addTrackToPlaylistAction: PlaylistTrackAddAction
) : PlaylistDialogController.Listener {
    override fun createPlaylist(name: String) {
        createPlaylistAction.run(name)
    }

    override fun renamePlaylist(playlistId: Long, name: String) {
        renamePlaylistAction.rename(playlistId, name)
    }

    override fun deletePlaylist(playlistId: Long, name: String) {
        deletePlaylistAction.delete(playlistId, name)
    }

    override fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        addTrackToPlaylistAction.add(playlistId, trackId)
    }
}
