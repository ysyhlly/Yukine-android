package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList

internal class LibraryPlaylistsRenderController(
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    private val favoritesKey = "virtual:favorites"
    private val historyKey = "virtual:play-history"

    interface Listener {
        fun openFavoritePlaylist(title: String)

        fun openPlayHistory(title: String)

        fun openPlaylist(playlistId: Long, title: String)

        fun playPlaylist(playlistId: Long)

        fun confirmDeletePlaylist(playlist: Playlist)

        fun backFromPlaylist()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun publishLibraryGroupsChrome(state: LibraryGroupsChromeState)

        fun renderPlaylistTracks(request: LibraryPlaylistTrackListRequest)
    }

    fun render(
        languageMode: String,
        playlists: List<Playlist>,
        selectedPlaylistId: Long,
        selectedLibraryGroupKey: String,
        selectedPlaylistName: String,
        selectedPlaylistTracks: List<Track>,
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        modeActions: List<TrackListModeAction>
    ) {
        if (selectedLibraryGroupKey == favoritesKey) {
            renderFavoriteTracks(languageMode, favoriteTracks, modeActions)
            return
        }
        if (selectedLibraryGroupKey == historyKey) {
            renderPlayHistory(languageMode, recentRecords, modeActions)
            return
        }
        if (selectedPlaylistId >= 0L && selectedLibraryGroupKey.startsWith("playlist:")) {
            renderPlaylistTracks(languageMode, selectedPlaylistName, selectedPlaylistTracks, modeActions)
            return
        }
        renderPlaylists(languageMode, playlists, favoriteTracks, recentRecords, modeActions)
    }

    private fun renderPlaylists(
        languageMode: String,
        playlists: List<Playlist>,
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        modeActions: List<TrackListModeAction>
    ) {
        val rows = ArrayList<LibraryGroupUiState>()
        val actions = ArrayList<LibraryGroupActions>()
        val favoriteTitle = AppLanguage.text(languageMode, "favorite.playlist")
        rows.add(
            LibraryGroupUiState(
                favoritesKey,
                favoriteTitle,
                CollectionRowStateFactory.trackCountLabel(favoriteTracks.size, languageMode)
            )
        )
        actions.add(
            LibraryGroupActions(
                Runnable { listener.openFavoritePlaylist(favoriteTitle) },
                Runnable { listener.playTrackList(favoriteTracks, 0) },
                favoriteTracks.isNotEmpty(),
                null
            )
        )
        val historyTracks = tracksFromRecords(recentRecords)
        val historyTitle = AppLanguage.text(languageMode, "play.history.playlist")
        rows.add(
            LibraryGroupUiState(
                historyKey,
                historyTitle,
                CollectionRowStateFactory.trackCountLabel(historyTracks.size, languageMode)
            )
        )
        actions.add(
            LibraryGroupActions(
                Runnable { listener.openPlayHistory(historyTitle) },
                Runnable { listener.playTrackList(historyTracks, 0) },
                historyTracks.isNotEmpty(),
                null
            )
        )
        for (playlist in playlists) {
            rows.add(
                LibraryGroupUiState(
                    "playlist:${playlist.id}",
                    playlist.name,
                    CollectionRowStateFactory.trackCountLabel(playlist.trackCount, languageMode)
                )
            )
            actions.add(
                LibraryGroupActions(
                    Runnable { listener.openPlaylist(playlist.id, playlist.name) },
                    Runnable { listener.playPlaylist(playlist.id) },
                    playlist.trackCount > 0,
                    Runnable { listener.confirmDeletePlaylist(playlist) }
                )
            )
        }

        val title = AppLanguage.text(languageMode, "playlists")
        viewModel.clearTrackList()
        viewModel.updateLibraryGroups(title, rows)
        listener.publishLibraryGroupsChrome(
            LibraryGroupsChromeState(
                actions = actions,
                emptyText = AppLanguage.text(languageMode, "no.playlists"),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun renderPlayHistory(
        languageMode: String,
        records: List<TrackPlayRecord>,
        modeActions: List<TrackListModeAction>
    ) {
        val tracks = tracksFromRecords(records)
        val title = AppLanguage.text(languageMode, "play.history.playlist")
        val headerMetrics = arrayListOf(
            TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString())
        )
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), Runnable {
                listener.backFromPlaylist()
            })
        )
        if (tracks.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.recent"), Runnable {
                    listener.playTrackList(tracks, 0)
                })
            )
        }
        listener.renderPlaylistTracks(
            LibraryPlaylistTrackListRequest(
                title = title,
                tracks = tracks,
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = AppLanguage.text(languageMode, "no.recent.tracks"),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun renderFavoriteTracks(
        languageMode: String,
        tracks: List<Track>,
        modeActions: List<TrackListModeAction>
    ) {
        val title = AppLanguage.text(languageMode, "favorite.playlist")
        val headerMetrics = arrayListOf(
            TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString())
        )
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), Runnable {
                listener.backFromPlaylist()
            })
        )
        if (tracks.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.favorites"), Runnable {
                    listener.playTrackList(tracks, 0)
                })
            )
        }
        listener.renderPlaylistTracks(
            LibraryPlaylistTrackListRequest(
                title = title,
                tracks = ArrayList(tracks),
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = AppLanguage.text(languageMode, "no.favorites"),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun renderPlaylistTracks(
        languageMode: String,
        selectedPlaylistName: String,
        tracks: List<Track>,
        modeActions: List<TrackListModeAction>
    ) {
        val headerMetrics = arrayListOf(
            TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), tracks.size.toString())
        )
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(
            TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), Runnable {
                listener.backFromPlaylist()
            })
        )
        if (tracks.isNotEmpty()) {
            headerActions.add(
                TrackListHeaderAction(AppLanguage.text(languageMode, "play.playlist"), Runnable {
                    listener.playTrackList(tracks, 0)
                })
            )
        }
        listener.renderPlaylistTracks(
            LibraryPlaylistTrackListRequest(
                title = selectedPlaylistName,
                tracks = ArrayList(tracks),
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = AppLanguage.text(languageMode, "no.tracks.in.playlist"),
                modeActions = ArrayList(modeActions)
            )
        )
    }

    private fun tracksFromRecords(records: List<TrackPlayRecord>): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        for (record in records) {
            tracks.add(record.track)
        }
        return tracks
    }
}

internal data class LibraryPlaylistTrackListRequest(
    val title: String,
    val tracks: ArrayList<Track>,
    val headerMetrics: ArrayList<TrackListHeaderMetric>,
    val headerActions: ArrayList<TrackListHeaderAction>,
    val emptyText: String,
    val modeActions: ArrayList<TrackListModeAction>
)
