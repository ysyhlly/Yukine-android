package app.yukine

import app.yukine.model.Playlist
import app.yukine.ui.PlaylistRowUiState

object CollectionRowStateFactory {
    @JvmStatic
    fun playlistRow(playlist: Playlist, selectedPlaylistId: Long): PlaylistRowUiState =
        playlistRow(playlist, selectedPlaylistId, AppLanguage.MODE_ENGLISH)

    @JvmStatic
    fun playlistRow(playlist: Playlist, selectedPlaylistId: Long, languageMode: String): PlaylistRowUiState =
        PlaylistRowUiState(
            playlist.name,
            trackCountLabel(playlist.trackCount, languageMode),
            playlist.id == selectedPlaylistId,
            playlist.id
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
