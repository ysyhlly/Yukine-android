package app.yukine.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.SettingsUiState
import app.yukine.TrackDownloadItem
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsListScrollState
import app.yukine.ui.SettingsScreen
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Settings screens (home + the ~20 sub-pages share
 * [SettingsScreen]).
 *
 * Title and metrics are read reactively from [SettingsViewModel.uiState] via [collectAsState];
 * the [actions] (which carry the click callbacks that SettingsItem deliberately drops) are
 * injected by the host, which reuses the existing SettingsPageRenderController assembly. Each
 * settings sub-page is a route in the Settings nav sub-graph that supplies its own actions.
 */
@Composable
fun SettingsDestination(
    state: StateFlow<SettingsUiState>,
    actions: List<SettingsAction>,
    scrollState: SettingsListScrollState = SettingsListScrollState(),
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val uiState by state.collectAsState()
    SettingsScreen(
        title = uiState.title,
        metrics = uiState.metrics,
        actions = actions,
        scrollState = scrollState,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}
