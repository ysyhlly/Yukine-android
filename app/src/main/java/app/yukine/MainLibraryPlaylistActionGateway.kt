package app.yukine

import app.yukine.model.Track

internal class MainLibraryPlaylistActionGateway(
    operations: PlaylistActionOperations
) : LibraryPlaylistActionGateway {
    private val addToDefaultPlaylistUseCase = AddToDefaultPlaylistUseCase(operations)
    private val createPlaylistUseCase = CreatePlaylistUseCase(operations)
    private val renamePlaylistUseCase = RenamePlaylistUseCase(operations)
    private val deletePlaylistUseCase = DeletePlaylistUseCase(operations)
    private val removeTrackFromPlaylistUseCase = RemoveTrackFromPlaylistUseCase(operations)
    private val movePlaylistTrackUseCase = MovePlaylistTrackUseCase(operations)
    private val addTrackToPlaylistUseCase = AddTrackToPlaylistUseCase(operations)

    override fun addToDefaultPlaylist(track: Track?): LibraryDefaultPlaylistAddResultUi? =
        addToDefaultPlaylistUseCase.execute(track)?.let { result ->
            LibraryDefaultPlaylistAddResultUi(result.playlistId, result.added)
        }

    override fun createPlaylist(name: String): Long =
        createPlaylistUseCase.execute(name)

    override fun renamePlaylist(playlistId: Long, name: String): Boolean =
        renamePlaylistUseCase.execute(playlistId, name)

    override fun deletePlaylist(playlistId: Long): Boolean =
        deletePlaylistUseCase.execute(playlistId)

    override fun removeTrackFromPlaylist(playlistId: Long, track: Track?): Boolean =
        removeTrackFromPlaylistUseCase.execute(playlistId, track)

    override fun movePlaylistTrack(
        playlistId: Long,
        track: Track?,
        trackIndex: Int,
        direction: Int
    ): Boolean =
        movePlaylistTrackUseCase.execute(playlistId, track, trackIndex, direction)

    override fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean =
        addTrackToPlaylistUseCase.execute(playlistId, trackId)
}
