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
    val route by hostState.mainViewModel.state.collectAsState()
    when (route.networkPage) {
        MainRoutes.NETWORK_SOURCES -> NetworkSourcesDestination(
            state = hostState.networkSourcesViewModel.screen,
            actions = hostState.networkSourceActions,
            headerActions = hostState.networkSourceHeaderActions,
            emptyText = hostState.networkSourceEmptyText,
            labels = hostState.networkSourceLabels
        )

        MainRoutes.NETWORK_STREAMING,
        MainRoutes.NETWORK_STREAMING_HUB -> {
            val streamingState by hostState.mainViewModel.streaming.collectAsState()
            StreamingSearchScreen(
                state = streamingState,
                labels = hostState.streamingSearchLabels,
                actions = hostState.streamingSearchActions
            )
        }

        MainRoutes.NETWORK_STREAM_LIST,
        MainRoutes.NETWORK_WEBDAV_TRACKS,
        MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS -> LibraryTrackListDestination(
            state = hostState.libraryViewModel.trackList,
            actions = hostState.trackListActions,
            headerMetrics = hostState.trackListHeaderMetrics,
            headerActions = hostState.trackListHeaderActions,
            emptyText = hostState.trackListEmptyText,
            labels = hostState.trackListLabels
        )

        MainRoutes.NETWORK_HOME,
        MainRoutes.NETWORK_WEBDAV -> SettingsScreen(
            title = hostState.networkMenuTitle.ifBlank { "Network" },
            metrics = hostState.networkMenuMetrics,
            actions = hostState.networkMenuActions,
            scrollState = SettingsListScrollState()
        )

        else -> SettingsScreen(
            title = hostState.networkMenuTitle.ifBlank { "Network" },
            metrics = hostState.networkMenuMetrics,
            actions = hostState.networkMenuActions,
            scrollState = SettingsListScrollState()
        )
    }
}
