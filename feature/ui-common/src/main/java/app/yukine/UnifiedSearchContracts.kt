package app.yukine

import app.yukine.model.Track
import app.yukine.ui.UnifiedSearchActions

data class UnifiedSearchUiState(
    val query: String = "",
    val localTracks: List<Track> = emptyList(),
    val searched: Boolean = false,
    val actions: UnifiedSearchActions = UnifiedSearchActions.empty()
)
