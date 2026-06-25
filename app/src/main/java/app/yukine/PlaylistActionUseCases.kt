package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylistSyncStore

internal data class DefaultPlaylistAddResult(
    @JvmField val playlistId: Long,
    @JvmField val added: Boolean
)

internal interface PlaylistActionOperations {
    fun ensureDefaultPlaylist(): Long
    fun createPlaylist(name: String): Long
    fun renamePlaylist(playlistId: Long, name: String): Boolean
    fun deletePlaylist(playlistId: Long): Boolean
    fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)
    fun movePlaylistTrackAt(playlistId: Long, trackIndex: Int, direction: Int): Boolean
}

internal class MusicLibraryPlaylistActionOperations(
    private val repository: MusicLibraryRepository,
    private val syncStore: StreamingPlaylistSyncStore? = null
) : PlaylistActionOperations {
    override fun ensureDefaultPlaylist(): Long = repository.ensureDefaultPlaylist()

    override fun createPlaylist(name: String): Long = repository.createPlaylist(name)

    override fun renamePlaylist(playlistId: Long, name: String): Boolean =
        repository.renamePlaylist(playlistId, name)

    override fun deletePlaylist(playlistId: Long): Boolean {
        val deleted = repository.deletePlaylist(playlistId)
        if (deleted) {
            syncStore?.unlinkPlaylist(playlistId)
        }
        return deleted
    }

    override fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean =
        repository.addTrackToPlaylist(playlistId, trackId)

    override fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        repository.removeTrackFromPlaylist(playlistId, trackId)
    }

    override fun movePlaylistTrackAt(playlistId: Long, trackIndex: Int, direction: Int): Boolean =
        repository.movePlaylistTrackAt(playlistId, trackIndex, direction)
}

internal class AddToDefaultPlaylistUseCase(
    private val operations: PlaylistActionOperations
) {
    fun execute(track: Track?): DefaultPlaylistAddResult? {
        if (track == null) {
            return null
        }
        val playlistId = operations.ensureDefaultPlaylist()
        val added = playlistId >= 0L && operations.addTrackToPlaylist(playlistId, track.id)
        return DefaultPlaylistAddResult(playlistId, added)
    }
}

internal class CreatePlaylistUseCase(
    private val operations: PlaylistActionOperations
) {
    fun execute(name: String): Long = operations.createPlaylist(name)
}

internal class RenamePlaylistUseCase(
    private val operations: PlaylistActionOperations
) {
    fun execute(playlistId: Long, name: String): Boolean {
        if (playlistId < 0L) {
            return false
        }
        return operations.renamePlaylist(playlistId, name)
    }
}

internal class DeletePlaylistUseCase(
    private val operations: PlaylistActionOperations
) {
    fun execute(playlistId: Long): Boolean {
        if (playlistId < 0L) {
            return false
        }
        return operations.deletePlaylist(playlistId)
    }
}

internal class AddTrackToPlaylistUseCase(
    private val operations: PlaylistActionOperations
) {
    fun execute(playlistId: Long, trackId: Long): Boolean {
        if (playlistId < 0L || trackId < 0L) {
            return false
        }
        return operations.addTrackToPlaylist(playlistId, trackId)
    }
}

internal class RemoveTrackFromPlaylistUseCase(
    private val operations: PlaylistActionOperations
) {
    fun execute(playlistId: Long, track: Track?): Boolean {
        if (playlistId < 0L || track == null) {
            return false
        }
        operations.removeTrackFromPlaylist(playlistId, track.id)
        return true
    }
}

internal class MovePlaylistTrackUseCase(
    private val operations: PlaylistActionOperations
) {
    fun execute(playlistId: Long, track: Track?, trackIndex: Int, direction: Int): Boolean {
        if (playlistId < 0L || track == null) {
            return false
        }
        return operations.movePlaylistTrackAt(playlistId, trackIndex, direction)
    }
}
