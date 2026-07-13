package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Track
import app.yukine.ui.UnifiedSearchActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun interface LocalTrackSearch {
    fun search(source: List<Track>, query: String): List<Track>
}

class SearchViewModel : ViewModel() {
    private val searchState = MutableStateFlow(UnifiedSearchUiState())
    private var stateSourcesJob: Job? = null

    val uiState: StateFlow<UnifiedSearchUiState> = searchState.asStateFlow()

    fun bindStateSources(
        queryState: StateFlow<String>?,
        libraryState: StateFlow<LibraryStoreState>?,
        localTrackSearch: LocalTrackSearch?
    ) {
        stateSourcesJob?.cancel()
        stateSourcesJob = null
        if (queryState == null || libraryState == null || localTrackSearch == null) {
            return
        }
        stateSourcesJob = viewModelScope.launch {
            combine(
                queryState,
                libraryState.map { it.allTracks }.distinctUntilChanged()
            ) { query, tracks ->
                query to localTrackSearch.search(tracks, query)
            }.collect { (query, matches) ->
                updateResults(query, matches)
            }
        }
    }

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
        searchState.value = UnifiedSearchUiState(actions = searchState.value.actions)
    }

    fun updateActions(actions: UnifiedSearchActions) {
        searchState.value = searchState.value.copy(actions = actions)
    }
}
