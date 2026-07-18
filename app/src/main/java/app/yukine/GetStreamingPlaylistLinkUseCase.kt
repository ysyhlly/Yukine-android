package app.yukine

import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingPlaylistSyncDirection
import app.yukine.streaming.StreamingProviderName

internal interface StreamingPlaylistLinkOperations {
    fun getLink(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist?

    fun getLink(provider: StreamingProviderName, providerPlaylistId: String): StreamingPlaylistSyncStore.LinkedPlaylist?

    fun linkPlaylist(
        localPlaylistId: Long,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        direction: StreamingPlaylistSyncDirection
    ) = Unit
}

internal class StreamingPlaylistSyncStoreLinkOperations(
    private val syncStore: StreamingPlaylistSyncStore
) : StreamingPlaylistLinkOperations {
    override fun getLink(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? =
        syncStore.getLink(localPlaylistId)

    override fun getLink(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): StreamingPlaylistSyncStore.LinkedPlaylist? =
        syncStore.getLink(provider, providerPlaylistId)

    override fun linkPlaylist(
        localPlaylistId: Long,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        direction: StreamingPlaylistSyncDirection
    ) {
        syncStore.linkPlaylist(localPlaylistId, provider, providerPlaylistId, direction)
    }
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

    fun execute(provider: StreamingProviderName, providerPlaylistId: String): StreamingPlaylistSyncStore.LinkedPlaylist? {
        val cleanProviderPlaylistId = providerPlaylistId.trim()
        if (cleanProviderPlaylistId.isEmpty()) {
            return null
        }
        return operations.getLink(provider, cleanProviderPlaylistId)
    }

    fun link(
        localPlaylistId: Long,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        direction: StreamingPlaylistSyncDirection
    ) {
        val cleanProviderPlaylistId = providerPlaylistId.trim()
        if (localPlaylistId < 0L || cleanProviderPlaylistId.isEmpty()) {
            return
        }
        operations.linkPlaylist(localPlaylistId, provider, cleanProviderPlaylistId, direction)
    }
}
