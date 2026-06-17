package app.yukine.collections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.CollectionsViewModel
import app.yukine.ui.CollectionsActions
import app.yukine.ui.CollectionsScreen

/**
 * Native Compose destination for the Collections tab.
 *
 * Renders directly from [CollectionsViewModel.screen] via [collectAsState], mirroring the
 * QueueDestination reference slice. Unlike Queue (whose positional actions are trivial
 * index→intent maps the destination builds itself), the Collections action set is a
 * data-bound bundle assembled from many sections/playlists; that assembly stays in the host
 * (the existing CollectionsRenderController logic) and is passed in as [actions].
 *
 * This keeps the destination a pure, testable "state → screen" renderer while the heavier
 * action wiring is reused from the host during the mount-point cutover.
 */
@Composable
fun CollectionsDestination(
    viewModel: CollectionsViewModel,
    actions: CollectionsActions
) {
    val uiState by viewModel.screen.collectAsState()
    CollectionsScreen(uiState, actions)
}
