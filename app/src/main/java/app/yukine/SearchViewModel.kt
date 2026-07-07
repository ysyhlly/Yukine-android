package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.model.Track
import app.yukine.ui.UnifiedSearchActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchViewModel : ViewModel() {
    private val searchState = MutableStateFlow(UnifiedSearchUiState())

    val uiState: StateFlow<UnifiedSearchUiState> = searchState.asStateFlow()

    fun updateQuery(query: String) {
        searchState.value = searchState.value.copy(query = query)
    }

    fun updateResults(query: String, localTracks: List<Track>) {
        val current = searchState.value
        val displayQuery = if (current.query.isNotBlank() && current.query != query) {
            current.query
        } else {
            query
        }
        searchState.value = UnifiedSearchUiState(
            query = displayQuery,
            localTracks = localTracks,
            searched = query.isNotBlank(),
            actions = current.actions
        )
    }

    fun clearSearch() {
        searchState.value = UnifiedSearchUiState()
    }

    fun updateActions(actions: UnifiedSearchActions) {
        searchState.value = searchState.value.copy(actions = actions)
    }
}
