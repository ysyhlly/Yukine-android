package app.echo.next

import app.echo.next.model.Playlist
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.ui.NetworkSourceUiState
import app.echo.next.ui.PlaylistRowUiState

internal object CollectionRowStateFactory {
    @JvmStatic
    fun playlistRow(playlist: Playlist, selectedPlaylistId: Long): PlaylistRowUiState =
        playlistRow(playlist, selectedPlaylistId, AppLanguage.MODE_ENGLISH)

    @JvmStatic
    fun playlistRow(playlist: Playlist, selectedPlaylistId: Long, languageMode: String): PlaylistRowUiState =
        PlaylistRowUiState(
            playlist.name,
            trackCountLabel(playlist.trackCount, languageMode),
            playlist.id == selectedPlaylistId
        )

    @JvmStatic
    fun networkSourceRow(source: RemoteSource, tracks: List<Track>): NetworkSourceUiState =
        networkSourceRow(source, tracks, AppLanguage.MODE_ENGLISH)

    @JvmStatic
    fun networkSourceRow(source: RemoteSource, tracks: List<Track>, languageMode: String): NetworkSourceUiState =
        NetworkSourceUiState(
            source.id,
            source.name,
            NetworkLibrary.remoteSourceSubtitle(source, tracks, languageMode),
            source.lastStatus
        )

    private fun trackCountLabel(count: Int, languageMode: String): String {
        if (AppLanguage.isChinese(languageMode)) {
            return "$count ${AppLanguage.text(languageMode, "tracks")}"
        }
        return if (count == 1) "1 track" else "$count tracks"
    }
}
