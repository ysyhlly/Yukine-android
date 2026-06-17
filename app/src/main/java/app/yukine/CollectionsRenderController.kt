package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.CollectionActionUiState
import app.yukine.ui.CollectionMetricUiState
import app.yukine.ui.CollectionTrackSectionActions
import app.yukine.ui.CollectionTrackSectionUiState
import app.yukine.ui.CollectionsActions
import app.yukine.ui.CollectionsUiState
import app.yukine.ui.PlaylistRowActions
import app.yukine.ui.PlaylistRowUiState
import app.yukine.ui.PlaylistTrackActions
import app.yukine.ui.PlaylistTrackUiState
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import java.text.DateFormat
import java.util.ArrayList
import java.util.Date

internal class CollectionsRenderController(
    private val viewModel: CollectionsViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun showCreatePlaylist()

        fun openPlaylistM3uFilePicker()

        fun confirmClearPlayHistory()

        fun requestBack()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun selectPlaylist(playlistId: Long)

        fun showRenamePlaylist(playlist: Playlist)

        fun confirmDeletePlaylist(playlist: Playlist)

        fun openSelectedPlaylistExportDocument()

        fun importSelectedPlaylistToStreaming()

        fun importFavoritesToStreaming()

        fun importStreamingFavorites()

        fun syncSelectedPlaylistFromStreaming()

        fun moveSelectedPlaylistTrack(playlistId: Long, track: Track, trackIndex: Int, direction: Int)

        fun removeSelectedPlaylistTrack(playlistId: Long, track: Track)

        fun publishCollectionsActions(actions: CollectionsActions)
    }

    fun render(
        languageMode: String,
        favoriteTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        mostPlayedRecords: List<TrackPlayRecord>,
        playlists: List<Playlist>,
        selectedPlaylistTracks: List<Track>,
        selectedPlaylistId: Long,
        playbackState: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>
    ) {
        val fallbackPlaylistTitle = text(languageMode, "playlist")
        viewModel.updateCollections(
            favoriteTracks,
            recentRecords,
            mostPlayedRecords,
            playlists,
            selectedPlaylistTracks,
            selectedPlaylistId,
            fallbackPlaylistTitle
        )
        val metrics = ArrayList<CollectionMetricUiState>()
        metrics.add(CollectionMetricUiState(text(languageMode, "favorites"), favoriteTracks.size.toString()))
        metrics.add(CollectionMetricUiState(text(languageMode, "recent"), recentRecords.size.toString()))
        metrics.add(CollectionMetricUiState(text(languageMode, "playlists"), playlists.size.toString()))

        val topActionRows = ArrayList<CollectionActionUiState>()
        val topActions = ArrayList<Runnable>()
        addCollectionAction(topActionRows, topActions, text(languageMode, "new.playlist"), Runnable {
            listener.showCreatePlaylist()
        })
        addCollectionAction(topActionRows, topActions, text(languageMode, "import.playlist.m3u"), Runnable {
            listener.openPlaylistM3uFilePicker()
        })
        if (favoriteTracks.isNotEmpty()) {
            addCollectionAction(topActionRows, topActions, text(languageMode, "import.favorites.to.streaming"), Runnable {
                listener.importFavoritesToStreaming()
            })
        }
        addCollectionAction(topActionRows, topActions, text(languageMode, "streaming.import.liked"), Runnable {
            listener.importStreamingFavorites()
        })
        if (recentRecords.isNotEmpty() || mostPlayedRecords.isNotEmpty()) {
            addCollectionAction(topActionRows, topActions, text(languageMode, "clear.play.history"), Runnable {
                listener.confirmClearPlayHistory()
            })
        }

        val currentTrack = playbackState?.currentTrack
        val trackSections = ArrayList<CollectionTrackSectionUiState>()
        val trackSectionActions = ArrayList<CollectionTrackSectionActions>()
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "favorites",
            text(languageMode, "favorites"),
            favoriteTracks,
            text(languageMode, "no.favorites"),
            text(languageMode, "no.favorites.description"),
            text(languageMode, "play.favorites"),
            null,
            currentTrack,
            favoriteIds
        )
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "recent",
            text(languageMode, "recent"),
            tracksFromRecords(recentRecords),
            text(languageMode, "no.recent.tracks"),
            text(languageMode, "no.recent.tracks.description"),
            text(languageMode, "play.recent"),
            recordDetails(recentRecords, showPlayCount = false, languageMode),
            currentTrack,
            favoriteIds
        )
        addCollectionTrackSection(
            trackSections,
            trackSectionActions,
            "most-played",
            text(languageMode, "most.played"),
            tracksFromRecords(mostPlayedRecords),
            text(languageMode, "no.play.history"),
            text(languageMode, "no.play.history.description"),
            text(languageMode, "play.most.played"),
            recordDetails(mostPlayedRecords, showPlayCount = true, languageMode),
            currentTrack,
            favoriteIds
        )

        val playlistRows = ArrayList<PlaylistRowUiState>()
        val playlistActions = ArrayList<PlaylistRowActions>()
        buildPlaylistRows(playlistRows, playlistActions, playlists, selectedPlaylistId, languageMode)

        val selectedPlaylistActionRows = ArrayList<CollectionActionUiState>()
        val selectedPlaylistActions = ArrayList<Runnable>()
        val selectedPlaylistRows = ArrayList<PlaylistTrackUiState>()
        val selectedPlaylistTrackActions = ArrayList<PlaylistTrackActions>()
        if (selectedPlaylistId >= 0L && selectedPlaylistTracks.isNotEmpty()) {
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "play.playlist"), Runnable {
                listener.playTrackList(selectedPlaylistTracks, 0)
            })
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "export.playlist"), Runnable {
                listener.openSelectedPlaylistExportDocument()
            })
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "import.playlist.to.streaming"), Runnable {
                listener.importSelectedPlaylistToStreaming()
            })
            buildSelectedPlaylistRows(
                selectedPlaylistRows,
                selectedPlaylistTrackActions,
                selectedPlaylistId,
                selectedPlaylistTracks,
                currentTrack,
                favoriteIds
            )
        }
        // Sync button for streaming-linked playlists (even if empty)
        if (selectedPlaylistId >= 0L) {
            addCollectionAction(selectedPlaylistActionRows, selectedPlaylistActions, text(languageMode, "sync.streaming.playlist"), Runnable {
                listener.syncSelectedPlaylistFromStreaming()
            })
        }

        val state = CollectionsUiState(
            text(languageMode, "tab.collections"),
            text(languageMode, "back"),
            metrics,
            topActionRows,
            trackSections,
            text(languageMode, "playlists"),
            text(languageMode, "no.playlists"),
            text(languageMode, "no.playlists.description"),
            playlistRows,
            selectedPlaylistId >= 0L,
            selectedPlaylistName(playlists, selectedPlaylistId, fallbackPlaylistTitle),
            text(languageMode, "no.tracks.in.playlist"),
            text(languageMode, "no.tracks.in.playlist.description"),
            selectedPlaylistActionRows,
            selectedPlaylistRows,
            text(languageMode, "favorite"),
            text(languageMode, "remove.favorite"),
            text(languageMode, "add.to.playlist"),
            text(languageMode, "rename"),
            text(languageMode, "delete"),
            text(languageMode, "up"),
            text(languageMode, "down"),
            text(languageMode, "remove")
        )
        val actions = CollectionsActions(
            Runnable { listener.requestBack() },
            topActions,
            trackSectionActions,
            playlistActions,
            selectedPlaylistActions,
            selectedPlaylistTrackActions
        )
        viewModel.updateScreen(state)
        listener.publishCollectionsActions(actions)
    }

    private fun addCollectionAction(
        rows: ArrayList<CollectionActionUiState>,
        actions: ArrayList<Runnable>,
        label: String,
        action: Runnable
    ) {
        rows.add(CollectionActionUiState(label))
        actions.add(action)
    }

    private fun addCollectionTrackSection(
        sections: ArrayList<CollectionTrackSectionUiState>,
        sectionActions: ArrayList<CollectionTrackSectionActions>,
        key: String,
        title: String,
        tracks: List<Track>,
        emptyText: String,
        emptyDescription: String,
        playActionLabel: String,
        details: List<String>?,
        currentTrack: Track?,
        favoriteIds: Set<Long>
    ) {
        val rows = ArrayList<TrackRowUiState>()
        val rowActions = ArrayList<TrackRowActions>()
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(
                    track,
                    currentTrack,
                    favoriteIds,
                    if (details != null && index < details.size) details[index] else "",
                    true
                )
            )
            rowActions.add(
                TrackRowActions(
                    Runnable { listener.playTrackList(tracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) }
                )
            )
        }
        sections.add(CollectionTrackSectionUiState(key, title, emptyText, emptyDescription, playActionLabel, rows))
        sectionActions.add(CollectionTrackSectionActions(Runnable {
            listener.playTrackList(tracks, 0)
        }, rowActions))
    }

    private fun recordDetails(records: List<TrackPlayRecord>, showPlayCount: Boolean, languageMode: String): ArrayList<String> {
        val details = ArrayList<String>()
        for (record in records) {
            details.add(
                if (showPlayCount) {
                    playCountLabel(record.playCount, languageMode)
                } else {
                    text(languageMode, "played.at") + formatDateTime(record.playedAt)
                }
            )
        }
        return details
    }

    private fun buildPlaylistRows(
        rows: ArrayList<PlaylistRowUiState>,
        actions: ArrayList<PlaylistRowActions>,
        playlistsToRender: List<Playlist>,
        selectedPlaylistId: Long,
        languageMode: String
    ) {
        for (playlist in playlistsToRender) {
            rows.add(CollectionRowStateFactory.playlistRow(playlist, selectedPlaylistId, languageMode))
            actions.add(
                PlaylistRowActions(
                    Runnable { listener.selectPlaylist(playlist.id) },
                    Runnable { listener.showRenamePlaylist(playlist) },
                    Runnable { listener.confirmDeletePlaylist(playlist) }
                )
            )
        }
    }

    private fun buildSelectedPlaylistRows(
        rows: ArrayList<PlaylistTrackUiState>,
        actions: ArrayList<PlaylistTrackActions>,
        playlistIdForRows: Long,
        selectedPlaylistTracks: List<Track>,
        currentTrack: Track?,
        favoriteIds: Set<Long>
    ) {
        for (index in selectedPlaylistTracks.indices) {
            val track = selectedPlaylistTracks[index]
            rows.add(
                TrackRowStateFactory.playlistRow(
                    TrackRowKeyPolicy.occurrenceKey(selectedPlaylistTracks, index),
                    track,
                    currentTrack,
                    favoriteIds,
                    index > 0,
                    index < selectedPlaylistTracks.size - 1
                )
            )
            actions.add(
                PlaylistTrackActions(
                    Runnable { listener.playTrackList(selectedPlaylistTracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.moveSelectedPlaylistTrack(playlistIdForRows, track, index, -1) },
                    Runnable { listener.moveSelectedPlaylistTrack(playlistIdForRows, track, index, 1) },
                    Runnable { listener.removeSelectedPlaylistTrack(playlistIdForRows, track) }
                )
            )
        }
    }

    private fun tracksFromRecords(records: List<TrackPlayRecord>): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        for (record in records) {
            tracks.add(record.track)
        }
        return tracks
    }

    private fun selectedPlaylistName(playlists: List<Playlist>, selectedPlaylistId: Long, fallbackPlaylistTitle: String): String {
        for (playlist in playlists) {
            if (playlist.id == selectedPlaylistId) {
                return playlist.name
            }
        }
        return fallbackPlaylistTitle
    }

    private fun playCountLabel(count: Int, languageMode: String): String =
        if (count == 1) {
            text(languageMode, "played.once")
        } else {
            text(languageMode, "played.times.prefix") + count + text(languageMode, "played.times.suffix")
        }

    private fun formatDateTime(timestampMs: Long): String {
        if (timestampMs <= 0L) {
            return ""
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(timestampMs))
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
}
