package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import java.util.ArrayList

internal data class TrackListChromeState(
    val actions: List<TrackRowActions>,
    val headerMetrics: List<TrackListHeaderMetric>,
    val headerActions: List<TrackListHeaderAction>,
    val emptyText: String,
    val modeActions: List<TrackListModeAction>,
    val labels: TrackListLabels
)

internal class TrackListRenderController(
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun downloadTrack(track: Track)

        fun downloadTracks(tracks: List<Track>)

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
        favoriteIds: Set<Long>,
        footerAlbums: List<TrackListAlbumCardUiState> = emptyList()
    ) {
        val rows = ArrayList<TrackRowUiState>()
        val actions = ArrayList<TrackRowActions>()
        val effectiveHeaderActions = ArrayList(headerActions)
        if (tracks.isNotEmpty() && effectiveHeaderActions.none { it.label == "下载当前列表" }) {
            effectiveHeaderActions.add(TrackListHeaderAction("下载当前列表", Runnable { listener.downloadTracks(tracks) }))
        }
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
                    Runnable { listener.downloadTrack(track) },
                    if (showStreamActions) Runnable { listener.showEditStream(track) } else null,
                    if (showStreamActions) Runnable { listener.confirmDeleteTrack(track) } else null
                )
            )
        }

        viewModel.clearLibraryGroups()
        if (footerAlbums.isEmpty()) {
            viewModel.updateTrackList(title, rows)
        } else {
            viewModel.updateTrackList(title, rows, footerAlbums)
        }
        listener.publishTrackListChrome(actions, headerMetrics, effectiveHeaderActions, emptyText, modeActions, labels)
    }

    fun renderRecommendation(title: String, tracks: List<Track>) {
        renderRecommendation(title, tracks, AppLanguage.MODE_CHINESE)
    }

    fun renderRecommendation(title: String, tracks: List<Track>, languageMode: String) {
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
                    Runnable { listener.showAddToPlaylist(track) },
                    Runnable { listener.downloadTrack(track) }
                )
            )
        }
        viewModel.clearLibraryGroups()
        viewModel.updateTrackList(title, rows)
        listener.publishTrackListChrome(
            actions,
            listOf(TrackListHeaderMetric(AppLanguage.text(languageMode, "tracks"), "${tracks.size}")),
            listOf(TrackListHeaderAction(AppLanguage.text(languageMode, "download.current.list"), Runnable { listener.downloadTracks(tracks) })),
            "",
            emptyList(),
            TrackListLabels(
                AppLanguage.text(languageMode, "favorite"),
                AppLanguage.text(languageMode, "remove.favorite"),
                AppLanguage.text(languageMode, "add.to.playlist"),
                AppLanguage.text(languageMode, "edit"),
                AppLanguage.text(languageMode, "delete"),
                AppLanguage.text(languageMode, "download")
            )
        )
    }
}
