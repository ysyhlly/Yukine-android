package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.model.Track
import app.yukine.ui.UnifiedSearchActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UnifiedSearchUiState(
    val query: String = "",
    val localTracks: List<Track> = emptyList(),
    val searched: Boolean = false,
    val actions: UnifiedSearchActions = UnifiedSearchActions.empty()
)

class SearchViewModel : ViewModel() {
    private val searchState = MutableStateFlow(UnifiedSearchUiState())

    val uiState: StateFlow<UnifiedSearchUiState> = searchState.asStateFlow()

    fun updateQuery(query: String) {
        searchState.value = searchState.value.copy(query = query)
    }

    fun updateResults(query: String, localTracks: List<Track>) {
        searchState.value = UnifiedSearchUiState(
            query = query,
            localTracks = localTracks,
            searched = query.isNotBlank()
        )
    }

    fun clearSearch() {
        searchState.value = UnifiedSearchUiState()
    }

    fun updateActions(actions: UnifiedSearchActions) {
        searchState.value = searchState.value.copy(actions = actions)
    }
}
