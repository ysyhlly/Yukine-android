package app.echo.next.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.echo.next.MainActivityNetworkSourcesUiState
import app.echo.next.ui.NetworkSourceActions
import app.echo.next.ui.NetworkSourceLabels
import app.echo.next.ui.NetworkSourcesScreen
import app.echo.next.ui.TrackListHeaderAction
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for the Network "sources" sub-page (WebDAV/remote sources list).
 *
 * Reads title+rows from a [StateFlow] of [MainActivityNetworkSourcesUiState]; per-source
 * [actions], [headerActions], [emptyText] and [labels] are injected by the host (reusing the
 * existing NetworkSourcesRenderController assembly). One sub-page of the Network tab's nav
 * sub-graph; the menu/streaming/webdav sub-pages get their own destinations during cutover.
 */
@Composable
fun NetworkSourcesDestination(
    state: StateFlow<MainActivityNetworkSourcesUiState>,
    actions: List<NetworkSourceActions>,
    headerActions: List<TrackListHeaderAction> = emptyList(),
    emptyText: String = "",
    labels: NetworkSourceLabels = NetworkSourceLabels()
) {
    val uiState by state.collectAsState()
    NetworkSourcesScreen(uiState.title, uiState.rows, actions, headerActions, emptyText, labels)
}
