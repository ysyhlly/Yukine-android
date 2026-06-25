package app.yukine.collections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.CollectionsViewModel
import app.yukine.ui.CollectionsScreen

/**
 * Native Compose destination for the Collections tab.
 *
 * Renders directly from [CollectionsViewModel.screen] via [collectAsState].
 * The view model owns both display state and the section/playlist action bundle.
 */
@Composable
fun CollectionsDestination(
    viewModel: CollectionsViewModel
) {
    val uiState by viewModel.screen.collectAsState()
    CollectionsScreen(uiState, uiState.actions)
}
