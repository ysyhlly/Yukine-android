package app.yukine.network

import androidx.compose.runtime.Composable
import app.yukine.NetworkMenuUiState
import app.yukine.TrackDownloadItem
import app.yukine.ui.SettingsListScrollState
import app.yukine.ui.SettingsScreen
import app.yukine.ui.YukineOrbAudioMotion

@Composable
fun NetworkMenuDestination(
    state: NetworkMenuUiState,
    scrollState: SettingsListScrollState = SettingsListScrollState(),
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    SettingsScreen(
        title = state.title.ifBlank { "Network" },
        metrics = state.metrics,
        actions = state.actions,
        scrollState = scrollState,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}
