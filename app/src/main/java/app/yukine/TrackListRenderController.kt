package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
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
                    showPlaylistAction,
                    TrackRowKeyPolicy.occurrenceKey(tracks, index)
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

        viewModel.clearLibraryGroups()
        viewModel.updateTrackList(title, rows)
        listener.publishTrackListChrome(actions, headerMetrics, headerActions, emptyText, modeActions, labels)
    }

    fun renderRecommendation(title: String, tracks: List<Track>) {
        val rows = ArrayList<TrackRowUiState>()
        val actions = ArrayList<TrackRowActions>()
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(
                    track,
                    null,
                    emptySet(),
                    "",
                    true,
                    TrackRowKeyPolicy.occurrenceKey(tracks, index)
                )
            )
            actions.add(
                TrackRowActions(
                    Runnable { listener.playTrackList(tracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) }
                )
            )
        }
        viewModel.clearLibraryGroups()
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
