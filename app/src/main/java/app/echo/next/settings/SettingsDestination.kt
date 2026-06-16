package app.echo.next.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.echo.next.SettingsUiState
import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsListScrollState
import app.echo.next.ui.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Settings screens (home + the ~20 sub-pages share
 * [SettingsScreen]).
 *
 * Title and metrics are read reactively from [SettingsViewModel.uiState] via [collectAsState];
 * the [actions] (which carry the click callbacks that SettingsItem deliberately drops) are
 * injected by the host, which reuses the existing SettingsPageRenderController assembly. Each
 * settings sub-page is a route in the Settings nav sub-graph that supplies its own actions.
 */
@Composable
fun SettingsDestination(
    state: StateFlow<SettingsUiState>,
    actions: List<SettingsAction>,
    scrollState: SettingsListScrollState = SettingsListScrollState()
) {
    val uiState by state.collectAsState()
    SettingsScreen(uiState.title, uiState.metrics, actions, scrollState)
}
