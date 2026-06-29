package app.yukine.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.NetworkSourcesUiState
import app.yukine.ui.NetworkSourcesScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for the Network "sources" sub-page (WebDAV/remote sources list).
 *
 * Reads title+rows from a [StateFlow] of [NetworkSourcesUiState]; per-source
 * actions, headerActions, emptyText and labels are assembled by NetworkSourcesViewModel.
 * One sub-page of the Network tab's nav sub-graph; menu/streaming/webdav pages remain
 * app-owned until their state contracts are narrow enough to move.
 */
@Composable
fun NetworkSourcesDestination(
    state: StateFlow<NetworkSourcesUiState>
) {
    val uiState by state.collectAsState()
    NetworkSourcesScreen(
        uiState.title,
        uiState.rows,
        uiState.actions,
        uiState.headerActions,
        uiState.emptyText,
        uiState.labels
    )
}
