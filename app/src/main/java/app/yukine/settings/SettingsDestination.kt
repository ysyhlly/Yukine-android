package app.yukine.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.SettingsState
import app.yukine.TrackDownloadItem
import app.yukine.ui.SettingsListScrollState
import app.yukine.ui.SettingsScreen
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Settings screens (home + the ~20 sub-pages share
 * [SettingsScreen]).
 *
 * Title, metrics, and actions are read reactively from [SettingsViewModel.state] via
 * [collectAsState]. Actions stay in [SettingsState] because they carry click callbacks while
 * [SettingsItem] remains a display-only projection.
 */
@Composable
fun SettingsDestination(
    state: StateFlow<SettingsState>,
    scrollState: SettingsListScrollState = SettingsListScrollState(),
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val settingsState by state.collectAsState()
    SettingsScreen(
        title = settingsState.ui.title,
        metrics = settingsState.ui.metrics,
        actions = settingsState.actions,
        scrollState = scrollState,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}
