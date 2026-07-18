package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack

internal fun interface StreamingPlaylistDialogStatusSink {
    fun setStatus(status: String)
}

internal fun interface StreamingPlaylistImportRunner {
    fun runStreamingPlaylistImport(
        provider: StreamingProviderName,
        playlistName: String,
        tracks: List<Track>
    )
}

internal fun interface AccountPlaylistImportSink {
    fun importSelectedAccountPlaylists(
        provider: StreamingProviderName,
        playlists: List<StreamingPlaylist>
    )
}

internal fun interface StreamingLikedTracksImportSink {
    fun importStreamingLikedTracks(provider: StreamingProviderName)
}

internal fun interface AccountPlaylistPreviewSink {
    fun previewAccountPlaylist(provider: StreamingProviderName, playlist: StreamingPlaylist)
}

internal fun interface SelectedStreamingTracksImportSink {
    fun importSelectedStreamingTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        playlistName: String,
        tracks: List<StreamingTrack>,
        linkToProviderPlaylist: Boolean
    )
}

internal class MainStreamingPlaylistDialogListener(
    private val statusSink: StreamingPlaylistDialogStatusSink,
    private val playlistImportRunner: StreamingPlaylistImportRunner,
    private val accountPlaylistImportSink: AccountPlaylistImportSink,
    private val accountPlaylistPreviewSink: AccountPlaylistPreviewSink,
    private val likedTracksImportSink: StreamingLikedTracksImportSink,
    private val selectedTracksImportSink: SelectedStreamingTracksImportSink
) : StreamingPlaylistDialogController.Listener {
    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }

    override fun runStreamingPlaylistImport(
        provider: StreamingProviderName,
        playlistName: String,
        tracks: List<Track>
    ) {
        playlistImportRunner.runStreamingPlaylistImport(provider, playlistName, tracks)
    }

    override fun importSelectedAccountPlaylists(
        provider: StreamingProviderName,
        playlists: List<StreamingPlaylist>
    ) {
        accountPlaylistImportSink.importSelectedAccountPlaylists(provider, playlists)
    }

    override fun previewAccountPlaylist(provider: StreamingProviderName, playlist: StreamingPlaylist) {
        accountPlaylistPreviewSink.previewAccountPlaylist(provider, playlist)
    }

    override fun importStreamingLikedTracks(provider: StreamingProviderName) {
        likedTracksImportSink.importStreamingLikedTracks(provider)
    }

    override fun importSelectedStreamingTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        playlistName: String,
        tracks: List<StreamingTrack>,
        linkToProviderPlaylist: Boolean
    ) {
        selectedTracksImportSink.importSelectedStreamingTracks(
            provider,
            providerPlaylistId,
            playlistName,
            tracks,
            linkToProviderPlaylist
        )
    }
}
