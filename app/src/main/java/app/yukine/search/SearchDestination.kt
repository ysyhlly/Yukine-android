package app.yukine.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.StreamingViewModel
import app.yukine.SearchViewModel
import app.yukine.TrackDownloadItem
import app.yukine.ui.UnifiedSearchActions
import app.yukine.ui.UnifiedSearchScreen
import app.yukine.ui.YukineOrbAudioMotion

@Composable
fun SearchDestination(
    searchViewModel: SearchViewModel,
    streamingViewModel: StreamingViewModel,
    actions: UnifiedSearchActions,
    activeDownload: TrackDownloadItem?,
    playbackQuality: String,
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    val streamingState by streamingViewModel.streaming.collectAsState()
    UnifiedSearchScreen(
        state = searchViewModel,
        streamingState = streamingState,
        actions = actions,
        activeDownload = activeDownload,
        playbackQuality = playbackQuality,
        audioMotion = audioMotion
    )
}
