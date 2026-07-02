package app.yukine

import app.yukine.model.PlaylistImportResult
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal class MainStreamingLocalPlaylistOperations(
    private val importStreamingPlaylistUseCase: ImportStreamingPlaylistUseCase,
    private val syncStreamingPlaylistUseCase: SyncStreamingPlaylistUseCase,
    private val ensureStreamingLoginPlaylistUseCase: EnsureStreamingLoginPlaylistUseCase,
    private val streamingPlaylistLinkUseCase: GetStreamingPlaylistLinkUseCase
) : StreamingLocalPlaylistOperations {
    override fun playlistExists(localPlaylistId: Long): Boolean =
        syncStreamingPlaylistUseCase.playlistExists(localPlaylistId)

    override fun importStreamingPlaylist(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        streamingTracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): PlaylistImportResult =
        importStreamingPlaylistUseCase.execute(
            playlistName,
            provider,
            providerPlaylistId,
            streamingTracks,
            linkWhenProviderPlaylistIdBlank
        )

    override fun syncStreamingPlaylist(
        link: StreamingPlaylistSyncStore.LinkedPlaylist,
        streamingTracks: List<StreamingTrack>
    ): StreamingLocalPlaylistSyncResult {
        val result = syncStreamingPlaylistUseCase.execute(link, streamingTracks)
        return StreamingLocalPlaylistSyncResult(
            playlistId = result.playlistId,
            syncedCount = result.syncedCount,
            empty = result.empty
        )
    }

    override fun ensureStreamingLoginPlaylist(
        playlistName: String,
        provider: StreamingProviderName
    ): StreamingLoginPlaylistResult {
        val result = ensureStreamingLoginPlaylistUseCase.execute(playlistName, provider)
        return StreamingLoginPlaylistResult(
            playlistId = result.playlistId,
            playlistName = result.playlistName
        )
    }

    override fun linkedPlaylist(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? =
        streamingPlaylistLinkUseCase.execute(localPlaylistId)

    override fun linkedPlaylist(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): StreamingPlaylistSyncStore.LinkedPlaylist? =
        streamingPlaylistLinkUseCase.execute(provider, providerPlaylistId)
}
