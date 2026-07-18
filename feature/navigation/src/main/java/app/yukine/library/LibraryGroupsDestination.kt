package app.yukine.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.LibraryGroupsDestinationState
import app.yukine.ui.LibraryActionHandler
import app.yukine.TrackDownloadItem
import app.yukine.ui.LibraryGroupsScreen
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Library grouping screens (albums / artists / folders).
 *
 * Reads a fully assembled [LibraryGroupsDestinationState] from [state] and renders it
 * directly through [LibraryGroupsScreen], so Compose depends on LibraryViewModel state
 * instead of legacy host-injected chrome parameters.
 */
@Composable
fun LibraryGroupsDestination(
    state: StateFlow<LibraryGroupsDestinationState>,
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
    BackHandler(enabled = onNavigateUp != null) {
        onNavigateUp?.run()
    }
    LibraryGroupsScreen(
        uiState.title,
        uiState.rows,
        uiState.actions,
        uiState.emptyText,
        uiState.modeActions,
        onSearch,
        activeDownload,
        playbackQuality,
        audioMotion,
        uiState.libraryUi,
        actionHandler,
        libraryControlsEnabled,
        uiState.playlistFolders,
        onNavigateUp,
        compactCards
    )
}
