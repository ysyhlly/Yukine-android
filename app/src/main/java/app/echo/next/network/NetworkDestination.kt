package app.echo.next.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.echo.next.MainRoutes
import app.echo.next.navigation.EchoNavHostState
import app.echo.next.ui.SettingsListScrollState
import app.echo.next.ui.SettingsScreen
import app.echo.next.ui.StreamingSearchScreen
import app.echo.next.library.LibraryTrackListDestination

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
