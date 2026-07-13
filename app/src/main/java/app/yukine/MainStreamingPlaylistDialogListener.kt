package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName

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

internal class MainStreamingPlaylistDialogListener(
    private val statusSink: StreamingPlaylistDialogStatusSink,
    private val playlistImportRunner: StreamingPlaylistImportRunner,
    private val accountPlaylistImportSink: AccountPlaylistImportSink,
    private val likedTracksImportSink: StreamingLikedTracksImportSink
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

    override fun importStreamingLikedTracks(provider: StreamingProviderName) {
        likedTracksImportSink.importStreamingLikedTracks(provider)
    }
}
