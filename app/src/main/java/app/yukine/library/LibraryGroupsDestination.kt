package app.yukine.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.MainActivityLibraryGroupsUiState
import app.yukine.TrackDownloadItem
import app.yukine.ui.LibraryGroupsScreen
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Library grouping screens (albums / artists / folders).
 *
 * Reads a fully assembled [MainActivityLibraryGroupsUiState] from [state] and renders it
 * directly through [LibraryGroupsScreen], so Compose depends on [LibraryViewModel] state instead
 * of legacy host-injected chrome parameters.
 */
@Composable
fun LibraryGroupsDestination(
    state: StateFlow<MainActivityLibraryGroupsUiState>,
    onSearch: Runnable = Runnable { },
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val uiState by state.collectAsState()
    LibraryGroupsScreen(
        uiState.title,
        uiState.rows,
        uiState.actions,
        uiState.emptyText,
        uiState.modeActions,
        onSearch,
        activeDownload,
        playbackQuality,
        audioMotion
    )
}
