package app.yukine.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.LibraryGroupsDestinationState
import app.yukine.ui.LibraryActionHandler
import app.yukine.ui.LibraryGroupsScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for Library grouping screens (albums / artists / folders).
 *
 * Reads a fully assembled [LibraryGroupsDestinationState] from [state] and renders it
 * directly through [LibraryGroupsScreen], so Compose depends on LibraryViewModel state
 * instead of legacy host-injected chrome parameters.
 */
@Composable
fun LibraryGroupsDestination(
    state: StateFlow<LibraryGroupsDestinationState>,
    actionHandler: LibraryActionHandler = LibraryActionHandler { },
    compactCards: Boolean = true,
    onNavigateUp: Runnable? = null
) {
    val uiState by state.collectAsState()
    BackHandler(enabled = onNavigateUp != null) {
        onNavigateUp?.run()
    }
    LibraryGroupsScreen(
        uiState.title,
        uiState.rows,
        uiState.actions,
        uiState.emptyText,
        uiState.modeActions,
        uiState.libraryUi,
        actionHandler,
        uiState.playlistFolders,
        onNavigateUp,
        compactCards
    )
}
