package app.yukine

import app.yukine.streaming.StreamingPlaylistSyncStore

internal interface StreamingPlaylistLinkOperations {
    fun getLink(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist?
}

internal class StreamingPlaylistSyncStoreLinkOperations(
    private val syncStore: StreamingPlaylistSyncStore
) : StreamingPlaylistLinkOperations {
    override fun getLink(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? =
        syncStore.getLink(localPlaylistId)
}

internal class GetStreamingPlaylistLinkUseCase(
    private val operations: StreamingPlaylistLinkOperations
) {
    fun execute(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? {
        if (localPlaylistId < 0L) {
            return null
        }
        return operations.getLink(localPlaylistId)
    }
}
