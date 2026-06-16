package app.echo.next.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.echo.next.MainActivityTrackListUiState
import app.echo.next.ui.TrackListHeaderAction
import app.echo.next.ui.TrackListHeaderMetric
import app.echo.next.ui.TrackListLabels
import app.echo.next.ui.TrackListModeAction
import app.echo.next.ui.TrackListScreen
import app.echo.next.ui.TrackRowActions
import kotlinx.coroutines.flow.StateFlow

/**
 * Native Compose destination for track-list screens (Library songs, playlist tracks, network
 * stream/webdav lists all share [TrackListScreen]).
 *
 * Reads title+rows from a [StateFlow] of [MainActivityTrackListUiState] via [collectAsState];
 * the per-row [actions] and the chrome bits (header metrics/actions, mode actions, labels) are
 * injected by the host, which reuses the existing TrackListRenderController assembly. Mirrors
 * the factory's StateFlow→TrackListScreen split so behaviour is identical to the legacy path.
 */
@Composable
fun LibraryTrackListDestination(
    state: StateFlow<MainActivityTrackListUiState>,
    actions: List<TrackRowActions>,
    headerMetrics: List<TrackListHeaderMetric> = emptyList(),
    headerActions: List<TrackListHeaderAction> = emptyList(),
    emptyText: String = "",
    modeActions: List<TrackListModeAction> = emptyList(),
    labels: TrackListLabels = TrackListLabels()
) {
    val uiState by state.collectAsState()
    TrackListScreen(
        uiState.title,
        uiState.rows,
        actions,
        headerMetrics,
        headerActions,
        emptyText,
        modeActions,
        labels
    )
}
