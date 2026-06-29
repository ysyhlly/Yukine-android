package app.yukine.network

import androidx.compose.runtime.Composable
import app.yukine.LibraryTrackListDestinationState
import app.yukine.MainRoutes
import app.yukine.NetworkMenuUiState
import app.yukine.NetworkSourcesUiState
import app.yukine.library.LibraryTrackListDestination
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NetworkDestination(
    networkPage: String,
    menuState: NetworkMenuUiState,
    sourcesState: StateFlow<NetworkSourcesUiState>,
    trackListState: StateFlow<LibraryTrackListDestinationState>,
    streamingContent: @Composable () -> Unit
) {
    when (networkPage) {
        MainRoutes.NETWORK_SOURCES -> NetworkSourcesDestination(state = sourcesState)

        MainRoutes.NETWORK_STREAMING,
        MainRoutes.NETWORK_STREAMING_HUB -> streamingContent()

        MainRoutes.NETWORK_STREAM_LIST,
        MainRoutes.NETWORK_WEBDAV_TRACKS,
        MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS -> LibraryTrackListDestination(
            state = trackListState
        )

        MainRoutes.NETWORK_HOME,
        MainRoutes.NETWORK_WEBDAV -> NetworkMenuDestination(state = menuState)

        else -> NetworkMenuDestination(state = menuState)
    }
}
