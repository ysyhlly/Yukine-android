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
    libraryControlsEnabled: Boolean = false,
    compactCards: Boolean = true,
    onNavigateUp: Runnable? = null
) {
    val uiState by state.collectAsState()
    // Back priority: isBack action in headerActions (carries business semantics like closeGroup) > onNavigateUp (generic navigation back)
    val backAction = uiState.headerActions.firstOrNull { action -> action.isBack }
    val effectiveNavigateUp = backAction?.onClick ?: onNavigateUp
    BackHandler(enabled = effectiveNavigateUp != null) {
        effectiveNavigateUp?.run()
    }
    TrackListScreen(
        title = uiState.title,
        tracks = uiState.rows,
        actions = uiState.actions,
        headerMetrics = uiState.headerMetrics,
        headerActions = uiState.headerActions,
        emptyText = uiState.emptyText,
        modeActions = uiState.modeActions,
        labels = uiState.labels,
        onSearch = onSearch,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion,
        footerAlbums = uiState.footerAlbums,
        libraryUi = uiState.libraryUi,
        libraryActionHandler = actionHandler,
        libraryControlsEnabled = libraryControlsEnabled,
        compactCards = compactCards,
        context = uiState.context,
        onNavigateUp = effectiveNavigateUp
    )
}
