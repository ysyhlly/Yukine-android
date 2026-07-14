package app.yukine.network

import androidx.compose.runtime.Composable
import app.yukine.LibraryTrackListDestinationState
import app.yukine.NetworkPage
import app.yukine.NetworkMenuUiState
import app.yukine.NetworkSourcesUiState
import app.yukine.TrackDownloadItem
import app.yukine.library.LibraryTrackListDestination
import app.yukine.ui.YukineOrbAudioMotion
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NetworkDestination(
    networkPage: NetworkPage,
    menuState: NetworkMenuUiState,
    sourcesState: StateFlow<NetworkSourcesUiState>,
    trackListState: StateFlow<LibraryTrackListDestinationState>,
    streamingContent: @Composable () -> Unit,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    when (networkPage) {
        NetworkPage.Sources -> NetworkSourcesDestination(state = sourcesState)

        NetworkPage.Streaming,
        NetworkPage.StreamingHub -> streamingContent()

        NetworkPage.StreamList,
        NetworkPage.WebDavTracks,
        NetworkPage.WebDavSourceTracks -> LibraryTrackListDestination(
            state = trackListState,
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion
        )

        NetworkPage.Home,
        NetworkPage.WebDav -> NetworkMenuDestination(
            state = menuState,
            activeDownload = activeDownload,
            playbackQuality = playbackQuality,
            audioMotion = audioMotion
        )

    }
}
