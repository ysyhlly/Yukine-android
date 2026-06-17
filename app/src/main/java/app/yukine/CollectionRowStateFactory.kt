package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.ui.NetworkSourceUiState
import app.yukine.ui.PlaylistRowUiState

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

    @JvmStatic
    fun trackCountLabel(count: Int, languageMode: String): String {
        if (count == 1) {
            return AppLanguage.text(languageMode, "track.count.one")
        }
        return AppLanguage.text(languageMode, "track.count.prefix") +
            count +
            AppLanguage.text(languageMode, "track.count.suffix")
    }
}
