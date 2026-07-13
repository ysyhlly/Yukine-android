package app.yukine

import app.yukine.ui.CollectionsUiState
import kotlinx.coroutines.flow.StateFlow

interface CollectionsDestinationStateProvider {
    val screen: StateFlow<CollectionsUiState>
}
