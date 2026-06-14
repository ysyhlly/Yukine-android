package app.echo.next

import app.echo.next.data.MusicLibraryRepository
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingPlaybackAdapter
import app.echo.next.streaming.StreamingPlaylistSyncStore
import app.echo.next.streaming.StreamingTrack

internal interface StreamingPlaylistSyncOperations {
    fun syncStreamingPlaylist(playlistId: Long, tracks: List<Track>): Int

    fun markSynced(playlistId: Long)
}

internal class MusicLibraryStreamingPlaylistSyncOperations(
    private val repository: MusicLibraryRepository,
    private val syncStore: StreamingPlaylistSyncStore
) : StreamingPlaylistSyncOperations {
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
    fun execute(
        link: StreamingPlaylistSyncStore.LinkedPlaylist,
        streamingTracks: List<StreamingTrack?>?
    ): SyncStreamingPlaylistResult {
        val placeholders = streamingTracks
            .orEmpty()
            .mapNotNull { track -> track?.let { StreamingPlaybackAdapter.placeholderTrack(it) } }
        if (placeholders.isEmpty()) {
            operations.markSynced(link.localPlaylistId)
            return SyncStreamingPlaylistResult(link.localPlaylistId, 0, true)
        }
        val count = operations.syncStreamingPlaylist(link.localPlaylistId, placeholders)
        operations.markSynced(link.localPlaylistId)
        return SyncStreamingPlaylistResult(link.localPlaylistId, count, false)
    }
}
