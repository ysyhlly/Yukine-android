package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingTrack

internal interface StreamingPlaylistSyncOperations {
    fun playlistExists(playlistId: Long): Boolean

    fun syncStreamingPlaylist(playlistId: Long, tracks: List<Track>): Int

    fun markSynced(playlistId: Long)
}

internal class MusicLibraryStreamingPlaylistSyncOperations(
    private val repository: MusicLibraryRepository,
    private val syncStore: StreamingPlaylistSyncStore
) : StreamingPlaylistSyncOperations {
    override fun playlistExists(playlistId: Long): Boolean =
        repository.loadPlaylists().any { it.id == playlistId }

    override fun syncStreamingPlaylist(playlistId: Long, tracks: List<Track>): Int =
        repository.syncStreamingPlaylist(playlistId, tracks)

    override fun markSynced(playlistId: Long) {
        syncStore.markSynced(playlistId)
    }
}

internal data class SyncStreamingPlaylistResult(
    val playlistId: Long,
    val syncedCount: Int,
    val empty: Boolean
)

internal class SyncStreamingPlaylistUseCase(
    private val operations: StreamingPlaylistSyncOperations
) {
    fun playlistExists(playlistId: Long): Boolean {
        if (playlistId < 0L) {
            return false
        }
        return operations.playlistExists(playlistId)
    }

    fun execute(
        link: StreamingPlaylistSyncStore.LinkedPlaylist,
        streamingTracks: List<StreamingTrack?>?
    ): SyncStreamingPlaylistResult {
        if (!playlistExists(link.localPlaylistId)) {
            return SyncStreamingPlaylistResult(link.localPlaylistId, 0, true)
        }
        val placeholders = streamingTracks
            .orEmpty()
            .mapNotNull { track ->
                track?.playableLibraryTrackOrNull()?.let(StreamingPlaybackAdapter::placeholderTrack)
            }
        if (placeholders.isEmpty()) {
            operations.markSynced(link.localPlaylistId)
            return SyncStreamingPlaylistResult(link.localPlaylistId, 0, true)
        }
        val count = operations.syncStreamingPlaylist(link.localPlaylistId, placeholders)
        operations.markSynced(link.localPlaylistId)
        return SyncStreamingPlaylistResult(link.localPlaylistId, count, false)
    }
}
