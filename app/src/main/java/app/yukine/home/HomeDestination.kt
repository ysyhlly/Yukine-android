package app.yukine.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.MainActivityHomeDashboardUiState
import app.yukine.ui.HomeDashboardActions
import app.yukine.ui.HomeDashboardScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for the Home dashboard tab.
 *
 * Reads from a [StateFlow] of [MainActivityHomeDashboardUiState] (currently owned by
 * MainActivityViewModel) via [collectAsState] and renders the internal HomeDashboardScreen.
 * Taking the StateFlow rather than the whole ViewModel keeps the destination decoupled and
 * independently testable; [actions] is injected by the host, mirroring CollectionsDestination.
 */
@Composable
fun HomeDestination(
    state: StateFlow<MainActivityHomeDashboardUiState>,
    actions: HomeDashboardActions
) {
    val uiState by state.collectAsState()
    HomeDashboardScreen(uiState.content, actions)
}
