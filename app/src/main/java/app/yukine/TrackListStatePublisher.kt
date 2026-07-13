package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.TrackListAlbumCardUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapts feature-owned track-list requests to the immutable state consumed by Compose.
 *
 * This keeps language, favorite and playback projections out of the Activity while preserving
 * [TrackListStateReducer] as the single row-presentation implementation.
 */
internal class TrackListStatePublisher(
    private val controller: TrackListStateReducer,
    private val libraryState: StateFlow<LibraryStoreState>,
    private val settingsState: StateFlow<SettingsState>,
    private val playbackReadModel: PlaybackReadModel
) {
    fun publishLibraryGroup(request: LibraryGroupTrackListRequest) {
        publish(
            title = request.title,
            tracks = request.tracks,
            showPlaylistAction = true,
            headerMetrics = request.headerMetrics,
            headerActions = request.headerActions,
            footerAlbums = request.footerAlbums
        )
    }

    fun publishLibraryPlaylist(request: LibraryPlaylistTrackListRequest) {
        publish(
            title = request.title,
            tracks = request.tracks,
            showPlaylistAction = true,
            headerMetrics = request.headerMetrics,
            headerActions = request.headerActions,
            emptyText = request.emptyText,
            modeActions = request.modeActions
        )
    }

    fun publishNetwork(request: NetworkTrackListRequest) {
        publish(
            title = request.title,
            tracks = request.tracks,
            showPlaylistAction = request.showPlaylistAction,
            details = request.details,
            showStreamActions = request.showStreamActions,
            headerMetrics = request.headerMetrics,
            headerActions = request.headerActions,
            emptyText = request.emptyText,
            labels = request.labels
        )
    }

    private fun publish(
        title: String,
        tracks: List<Track>,
        showPlaylistAction: Boolean,
        details: List<String> = emptyList(),
        showStreamActions: Boolean = false,
        headerMetrics: List<TrackListHeaderMetric> = emptyList(),
        headerActions: List<TrackListHeaderAction> = emptyList(),
        emptyText: String = "",
        modeActions: List<TrackListModeAction> = emptyList(),
        labels: TrackListLabels = labels(settingsState.value.preferences.languageMode),
        playbackState: PlaybackStateSnapshot = playbackReadModel.state.value,
        footerAlbums: List<TrackListAlbumCardUiState> = emptyList()
    ) {
        controller.reduce(
            title = title,
            tracks = tracks,
            showPlaylistAction = showPlaylistAction,
            details = details,
            showStreamActions = showStreamActions,
            headerMetrics = headerMetrics,
            headerActions = headerActions,
            emptyText = emptyText,
            modeActions = modeActions,
            labels = labels,
            playbackState = playbackState,
            favoriteIds = libraryState.value.favoriteTrackIds,
            footerAlbums = footerAlbums
        )
    }

    private fun labels(languageMode: String): TrackListLabels = TrackListLabels(
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
}
