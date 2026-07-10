package app.yukine.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.SettingsDestinationState
import app.yukine.TrackDownloadItem
import app.yukine.ui.SettingsListScrollState
import app.yukine.ui.SettingsScreen
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Settings screens (home + the ~20 sub-pages share
 * [SettingsScreen]).
 *
 * Title, metrics, and actions are read reactively from a navigation-owned projection.
 * The app SettingsViewModel can keep a richer SettingsState without making this
 * destination depend on app-owned settings internals.
 */
@Composable
fun SettingsDestination(
    state: StateFlow<SettingsDestinationState>,
    scrollState: SettingsListScrollState = SettingsListScrollState(),
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val settingsState by state.collectAsState()
    val backAction = settingsState.destinationActions.firstOrNull { action ->
        action.isBack || isSettingsBackAction(action.label)
    }
    BackHandler(enabled = backAction != null) {
        backAction?.onClick?.run()
    }
    SettingsScreen(
        title = settingsState.destinationTitle,
        metrics = settingsState.destinationMetrics,
        actions = settingsState.destinationActions,
        scrollState = scrollState,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}

private fun isSettingsBackAction(label: String): Boolean =
    label.startsWith("Back", ignoreCase = true) || label.contains("返回")
