package app.echo.next

import app.echo.next.model.Track
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.ui.TrackListHeaderAction
import app.echo.next.ui.TrackListHeaderMetric
import app.echo.next.ui.TrackListLabels
import app.echo.next.ui.TrackListModeAction
import app.echo.next.ui.TrackRowActions
import app.echo.next.ui.TrackRowUiState
import java.util.ArrayList

internal class TrackListRenderController(
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun showEditStream(track: Track)

        fun confirmDeleteTrack(track: Track)

        fun publishTrackList(title: String, rows: ArrayList<TrackRowUiState>)

        fun publishTrackListChrome(
            actions: List<TrackRowActions>,
            headerMetrics: List<TrackListHeaderMetric>,
            headerActions: List<TrackListHeaderAction>,
            emptyText: String,
            modeActions: List<TrackListModeAction>,
            labels: TrackListLabels
        )
    }

    fun render(
        title: String,
        tracks: List<Track>,
        showPlaylistAction: Boolean,
        details: List<String>,
        showStreamActions: Boolean,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        modeActions: List<TrackListModeAction>,
        labels: TrackListLabels,
        playbackState: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>
    ) {
        val rows = ArrayList<TrackRowUiState>()
        val actions = ArrayList<TrackRowActions>()
        val currentTrack = playbackState?.currentTrack
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(
                    track,
                    currentTrack,
                    favoriteIds,
                    if (index < details.size) details[index] else "",
                    showPlaylistAction
                )
            )
            actions.add(
                TrackRowActions(
                    Runnable { listener.playTrackList(tracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) },
                    if (showStreamActions) Runnable { listener.showEditStream(track) } else null,
                    if (showStreamActions) Runnable { listener.confirmDeleteTrack(track) } else null
                )
            )
        }

        viewModel.updateTrackList(title, rows)
        listener.publishTrackListChrome(actions, headerMetrics, headerActions, emptyText, modeActions, labels)
    }

    fun renderRecommendation(title: String, tracks: List<Track>) {
        val rows = ArrayList<TrackRowUiState>()
        val actions = ArrayList<TrackRowActions>()
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(track, null, emptySet(), "", true)
            )
            actions.add(
                TrackRowActions(
                    Runnable { listener.playTrackList(tracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) }
                )
            )
        }
        viewModel.updateTrackList(title, rows)
        listener.publishTrackListChrome(
            actions,
            listOf(TrackListHeaderMetric("曲目", "${tracks.size}")),
            emptyList(),
            "",
            emptyList(),
            TrackListLabels()
        )
    }
}
