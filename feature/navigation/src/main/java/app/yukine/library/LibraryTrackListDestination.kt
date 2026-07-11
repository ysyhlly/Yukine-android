package app.yukine.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.LibraryTrackListDestinationState
import app.yukine.ui.LibraryActionHandler
import app.yukine.TrackDownloadItem
import app.yukine.ui.TrackListScreen
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for track-list screens (Library songs, playlist tracks, network
 * stream/webdav lists all share [TrackListScreen]).
 *
 * Reads a fully assembled [LibraryTrackListDestinationState] from [state] via [collectAsState] and
 * renders it directly through [TrackListScreen]. This keeps Compose aligned with
 * LibraryViewModel as the single owner of track-list content plus chrome.
 */
@Composable
fun LibraryTrackListDestination(
    state: StateFlow<LibraryTrackListDestinationState>,
    onSearch: Runnable = Runnable { },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty,
    actionHandler: LibraryActionHandler = LibraryActionHandler { },
    libraryControlsEnabled: Boolean = false
) {
    val uiState by state.collectAsState()
    val backAction = uiState.headerActions.firstOrNull { action ->
        action.label.contains("Back", ignoreCase = true) || action.label.contains("返回")
    }
    BackHandler(enabled = backAction != null) {
        backAction?.onClick?.run()
    }
    TrackListScreen(
        uiState.title,
        uiState.rows,
        uiState.actions,
        uiState.headerMetrics,
        uiState.headerActions,
        uiState.emptyText,
        uiState.modeActions,
        uiState.labels,
        onSearch,
        activeDownload,
        playbackQuality,
        audioMotion,
        uiState.footerAlbums,
        uiState.libraryUi,
        actionHandler,
        libraryControlsEnabled
    )
}
