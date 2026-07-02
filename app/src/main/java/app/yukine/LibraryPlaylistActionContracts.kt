package app.yukine

import app.yukine.model.Track

data class LibraryDefaultPlaylistAddResultUi(
    val playlistId: Long = -1L,
    val added: Boolean = false
)

data class LibraryPlaylistActionPresentation(
    val status: String = ""
)

interface LibraryPlaylistActionGateway {
    fun addToDefaultPlaylist(track: Track?): LibraryDefaultPlaylistAddResultUi?

    fun createPlaylist(name: String): Long

    fun renamePlaylist(playlistId: Long, name: String): Boolean

    fun deletePlaylist(playlistId: Long): Boolean

    fun removeTrackFromPlaylist(playlistId: Long, track: Track?): Boolean

    fun movePlaylistTrack(playlistId: Long, track: Track?, trackIndex: Int, direction: Int): Boolean

    fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean
}
