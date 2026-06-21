package app.yukine

import app.yukine.model.PlaylistImportResult
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal class StreamingLocalPlaylistOperationsBindings(
    private val importUseCase: ImportStreamingPlaylistUseCase,
    private val syncUseCase: SyncStreamingPlaylistUseCase,
    private val ensureLoginPlaylistUseCase: EnsureStreamingLoginPlaylistUseCase,
    private val linkUseCase: GetStreamingPlaylistLinkUseCase
) : StreamingLocalPlaylistOperations {
    override fun importStreamingPlaylist(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        streamingTracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): PlaylistImportResult =
        importUseCase.execute(
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
        val result = syncUseCase.execute(link, streamingTracks)
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
        val result = ensureLoginPlaylistUseCase.execute(playlistName, provider)
        return StreamingLoginPlaylistResult(
            playlistId = result.playlistId,
            playlistName = result.playlistName
        )
    }

    override fun linkedPlaylist(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? =
        linkUseCase.execute(localPlaylistId)
}
