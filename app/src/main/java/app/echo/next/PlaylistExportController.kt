package app.echo.next

import android.net.Uri

internal class PlaylistExportController(private val listener: Listener) {
    interface Listener {
        fun openPlaylistExportDocument(playlistName: String)

        fun exportPlaylist(exportUri: Uri, playlistId: Long, playlistName: String)

        fun setStatus(status: String)
    }

    private var pendingPlaylistId: Long = -1L
    private var pendingPlaylistName: String = ""

    fun openSelectedPlaylistExportDocument(playlistId: Long, playlistName: String, hasTracks: Boolean) {
        if (playlistId < 0L || !hasTracks) {
            listener.setStatus("Status")
            return
        }
        pendingPlaylistId = playlistId
        pendingPlaylistName = playlistName
        listener.openPlaylistExportDocument(playlistName)
    }

    fun exportSelectedPlaylistToUri(exportUri: Uri) {
        val playlistId = pendingPlaylistId
        val playlistName = pendingPlaylistName
        pendingPlaylistId = -1L
        pendingPlaylistName = ""
        if (playlistId < 0L) {
            listener.setStatus("Status")
            return
        }
        listener.setStatus("Status")
        listener.exportPlaylist(exportUri, playlistId, playlistName)
    }
}
