package app.echo.next.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.echo.next.MainActivityLibraryGroupsUiState
import app.echo.next.ui.LibraryGroupActions
import app.echo.next.ui.LibraryGroupsScreen
import app.echo.next.ui.TrackListModeAction
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Library grouping screens (albums / artists / folders).
 *
 * Reads title+rows from a [StateFlow] of [MainActivityLibraryGroupsUiState]; the per-group
 * [actions] and the [modeActions]/[emptyText] chrome are injected by the host (reusing the
 * existing LibraryGroupsRenderController assembly). Mirrors the factory's StateFlow split.
 */
@Composable
fun LibraryGroupsDestination(
    state: StateFlow<MainActivityLibraryGroupsUiState>,
    actions: List<LibraryGroupActions>,
    emptyText: String = "",
    modeActions: List<TrackListModeAction> = emptyList()
) {
    val uiState by state.collectAsState()
    LibraryGroupsScreen(uiState.title, uiState.rows, actions, emptyText, modeActions)
}
