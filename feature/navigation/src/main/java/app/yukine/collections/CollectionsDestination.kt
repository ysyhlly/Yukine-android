package app.yukine.collections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.CollectionsDestinationStateProvider
import app.yukine.ui.CollectionsScreen

/**
 * Native Compose destination for the Collections tab.
 *
 * Renders directly from a narrow provider via [collectAsState]. The app ViewModel owns
 * both display state and the section/playlist action bundle.
 */
@Composable
fun CollectionsDestination(
    provider: CollectionsDestinationStateProvider
) {
    val uiState by provider.screen.collectAsState()
    CollectionsScreen(uiState, uiState.actions)
}
