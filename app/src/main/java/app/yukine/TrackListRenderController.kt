package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderActionKind
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.EchoIconKind
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.LibraryMode
import app.yukine.ui.TrackRowActions
import app.yukine.ui.TrackRowUiState
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList

internal class TrackListRenderController(
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    private var rowBuildJob: Job? = null

    interface Listener {
        fun playTrackList(tracks: List<Track>, index: Int)

        fun toggleFavorite(track: Track)

        fun showAddToPlaylist(track: Track)

        fun downloadTrack(track: Track)

        fun downloadTracks(tracks: List<Track>)

        fun showEditStream(track: Track)

        fun confirmDeleteTrack(track: Track)
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
        val presented = LibraryTrackPresentationPolicy.present(
            tracks,
            details,
            viewModel.libraryUi.value,
            favoriteIds
        )
        val visibleTracks = presented.map { it.track }
        val visibleDetails = presented.map { it.detail }
        publishTrackList(title, visibleTracks.size) {
            buildTrackListContent(
                visibleTracks,
                showPlaylistAction,
                visibleDetails,
                showStreamActions,
                headerMetrics,
                headerActions,
                emptyText,
                modeActions,
                labels,
                playbackState,
                favoriteIds,
                footerAlbums
            )
        }
    }

    fun renderRecommendation(title: String, tracks: List<Track>) {
        renderRecommendation(title, tracks, AppLanguage.MODE_CHINESE)
    }

    fun renderRecommendation(title: String, tracks: List<Track>, languageMode: String) {
        val headerMetrics = listOf(TrackListHeaderMetric(AppLanguage.text(languageMode, "tracks"), "${tracks.size}"))
        val headerActions = listOf(
            TrackListHeaderAction(
                AppLanguage.text(languageMode, "download.current.list"),
                Runnable { listener.downloadTracks(tracks) },
                icon = EchoIconKind.Download,
                kind = TrackListHeaderActionKind.DownloadCurrentList
            )
        )
        val labels = TrackListLabels(
            AppLanguage.text(languageMode, "favorite"),
            AppLanguage.text(languageMode, "remove.favorite"),
            AppLanguage.text(languageMode, "add.to.playlist"),
            AppLanguage.text(languageMode, "edit"),
            AppLanguage.text(languageMode, "delete"),
            AppLanguage.text(languageMode, "download"),
            AppLanguage.text(languageMode, "download.current.list"),
            AppLanguage.text(languageMode, "all.albums"),
            AppLanguage.text(languageMode, "play.all"),
            AppLanguage.text(languageMode, "shuffle")
        )
        publishTrackList(title, tracks.size) {
            buildRecommendationContent(tracks, headerMetrics, headerActions, labels)
        }
    }

    private fun publishTrackList(
        title: String,
        trackCount: Int,
        buildContent: () -> BuiltTrackListContent
    ) {
        rowBuildJob?.cancel()
        viewModel.clearLibraryGroups()
        if (trackCount < BACKGROUND_ROW_BUILD_THRESHOLD) {
            publishTrackListContent(title, buildContent())
            return
        }
        rowBuildJob = viewModel.viewModelScope.launch {
            val content = withContext(Dispatchers.Default) { buildContent() }
            publishTrackListContent(title, content)
        }
    }

    private fun publishTrackListContent(title: String, content: BuiltTrackListContent) {
        viewModel.updateVisibleTrackTargets(content.tracks, content.rows.map { it.key })
        viewModel.updateTrackListContentAndChrome(
            title,
            content.rows,
            content.footerAlbums,
            content.actions,
            content.headerMetrics,
            content.headerActions,
            content.emptyText,
            content.modeActions,
            content.labels
        )
    }

    private fun buildTrackListContent(
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
        footerAlbums: List<TrackListAlbumCardUiState>
    ): BuiltTrackListContent {
        val rows = ArrayList<TrackRowUiState>(tracks.size)
        val actions = ArrayList<TrackRowActions>(tracks.size)
        val effectiveHeaderActions = ArrayList(headerActions)
        val libraryMode = viewModel.libraryUi.value.mode
        val isSongsRoot = libraryMode == LibraryMode.Songs && modeActions.isNotEmpty()
        if (tracks.isNotEmpty() && isSongsRoot) {
            if (effectiveHeaderActions.none { it.kind == TrackListHeaderActionKind.PlayAll }) {
                effectiveHeaderActions.add(
                    TrackListHeaderAction(
                        labels.playAllLabel,
                        Runnable { listener.playTrackList(tracks, 0) },
                        icon = EchoIconKind.Play,
                        kind = TrackListHeaderActionKind.PlayAll
                    )
                )
            }
            if (effectiveHeaderActions.none { it.kind == TrackListHeaderActionKind.Shuffle }) {
                effectiveHeaderActions.add(
                    TrackListHeaderAction(
                        labels.shuffleLabel,
                        Runnable { listener.playTrackList(tracks.shuffled(), 0) },
                        icon = EchoIconKind.Shuffle,
                        kind = TrackListHeaderActionKind.Shuffle
                    )
                )
            }
        }
        if (tracks.isNotEmpty() && !isSongsRoot &&
            effectiveHeaderActions.none { it.kind == TrackListHeaderActionKind.DownloadCurrentList }
        ) {
            effectiveHeaderActions.add(
                TrackListHeaderAction(
                    labels.downloadCurrentListLabel,
                    Runnable { listener.downloadTracks(tracks) },
                    icon = EchoIconKind.Download,
                    kind = TrackListHeaderActionKind.DownloadCurrentList
                )
            )
        }
        val currentTrack = playbackState?.currentTrack
        val rowKeys = TrackRowKeyPolicy.occurrenceKeys(tracks)
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(
                TrackRowStateFactory.trackRow(
                    track,
                    currentTrack,
                    favoriteIds,
                    if (index < details.size) details[index] else "",
                    showPlaylistAction,
                    rowKeys[index]
                )
            )
            actions.add(
                TrackRowActions(
                    Runnable { listener.playTrackList(tracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) },
                    Runnable { listener.downloadTrack(track) },
                    if (showStreamActions) Runnable { listener.showEditStream(track) } else null,
                    Runnable { listener.confirmDeleteTrack(track) }
                )
            )
        }
        return BuiltTrackListContent(
            tracks,
            rows,
            footerAlbums,
            actions,
            headerMetrics,
            effectiveHeaderActions,
            emptyText,
            modeActions,
            labels
        )
    }

    private fun buildRecommendationContent(
        tracks: List<Track>,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        labels: TrackListLabels
    ): BuiltTrackListContent {
        val rows = ArrayList<TrackRowUiState>(tracks.size)
        val actions = ArrayList<TrackRowActions>(tracks.size)
        val rowKeys = TrackRowKeyPolicy.occurrenceKeys(tracks)
        for (index in tracks.indices) {
            val track = tracks[index]
            rows.add(TrackRowStateFactory.trackRow(track, null, emptySet(), "", true, rowKeys[index]))
            actions.add(
                TrackRowActions(
                    Runnable { listener.playTrackList(tracks, index) },
                    Runnable { listener.toggleFavorite(track) },
                    Runnable { listener.showAddToPlaylist(track) },
                    Runnable { listener.downloadTrack(track) }
                )
            )
        }
        return BuiltTrackListContent(
            tracks,
            rows,
            emptyList(),
            actions,
            headerMetrics,
            headerActions,
            "",
            emptyList(),
            labels
        )
    }

    private data class BuiltTrackListContent(
        val tracks: List<Track>,
        val rows: List<TrackRowUiState>,
        val footerAlbums: List<TrackListAlbumCardUiState>,
        val actions: List<TrackRowActions>,
        val headerMetrics: List<TrackListHeaderMetric>,
        val headerActions: List<TrackListHeaderAction>,
        val emptyText: String,
        val modeActions: List<TrackListModeAction>,
        val labels: TrackListLabels
    )

    private companion object {
        const val BACKGROUND_ROW_BUILD_THRESHOLD = 200
    }
}
