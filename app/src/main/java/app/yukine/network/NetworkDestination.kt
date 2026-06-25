package app.yukine.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.MainRoutes
import app.yukine.navigation.EchoNavHostState
import app.yukine.ui.SettingsListScrollState
import app.yukine.ui.SettingsScreen
import app.yukine.ui.StreamingSearchScreen
import app.yukine.library.LibraryTrackListDestination

@Composable
fun NetworkDestination(hostState: EchoNavHostState) {
    val route by hostState.navigationViewModel.state.collectAsState()
    val networkMenuState by hostState.networkMenuViewModel.uiState.collectAsState()
    when (route.networkPage) {
        MainRoutes.NETWORK_SOURCES -> NetworkSourcesDestination(state = hostState.networkSourcesViewModel.uiState)

        MainRoutes.NETWORK_STREAMING,
        MainRoutes.NETWORK_STREAMING_HUB -> {
            val streamingState by hostState.streamingViewModel.streaming.collectAsState()
            StreamingSearchScreen(
                state = streamingState,
                labels = streamingState.searchChromeLabels,
                actions = streamingState.searchChromeActions
            )
        }

        MainRoutes.NETWORK_STREAM_LIST,
        MainRoutes.NETWORK_WEBDAV_TRACKS,
        MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS -> LibraryTrackListDestination(
            state = hostState.libraryViewModel.trackList
        )

        MainRoutes.NETWORK_HOME,
        MainRoutes.NETWORK_WEBDAV -> SettingsScreen(
            title = networkMenuState.title.ifBlank { "Network" },
            metrics = networkMenuState.metrics,
            actions = networkMenuState.actions,
            scrollState = SettingsListScrollState()
        )

        else -> SettingsScreen(
            title = networkMenuState.title.ifBlank { "Network" },
            metrics = networkMenuState.metrics,
            actions = networkMenuState.actions,
            scrollState = SettingsListScrollState()
        )
    }
}
