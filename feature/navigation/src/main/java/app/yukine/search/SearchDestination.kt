package app.yukine.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.TrackDownloadItem
import app.yukine.UnifiedSearchUiState
import app.yukine.ui.UnifiedSearchScreen
import app.yukine.ui.UnifiedSearchStreamingState
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SearchDestination(
    searchState: StateFlow<UnifiedSearchUiState>,
    streamingState: UnifiedSearchStreamingState,
    activeDownload: TrackDownloadItem?,
    playbackQuality: String,
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val uiState by searchState.collectAsState()
    UnifiedSearchScreen(
        searchState = uiState,
        streamingState = streamingState,
        actions = uiState.actions,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}
