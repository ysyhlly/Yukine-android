package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.model.RemoteSource
import app.yukine.ui.NetworkSourceActions
import app.yukine.ui.NetworkSourceLabels
import app.yukine.ui.NetworkSourceUiState
import app.yukine.ui.TrackListHeaderAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkSourcesUiState(
    val title: String = "",
    val sources: List<RemoteSource> = emptyList(),
    val rows: List<NetworkSourceUiState> = emptyList(),
    val actions: List<NetworkSourceActions> = emptyList(),
    val headerActions: List<TrackListHeaderAction> = emptyList(),
    val emptyText: String = "",
    val labels: NetworkSourceLabels = NetworkSourceLabels(),
    val selectedSourceId: Long = -1L,
    val loading: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null
)

class NetworkSourcesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkSourcesUiState())
    val uiState: StateFlow<NetworkSourcesUiState> = _uiState.asStateFlow()

    fun updateSources(
        title: String,
        sources: List<RemoteSource>,
        rows: List<NetworkSourceUiState>,
        actions: List<NetworkSourceActions> = _uiState.value.actions,
        headerActions: List<TrackListHeaderAction> = _uiState.value.headerActions,
        emptyText: String = _uiState.value.emptyText,
        labels: NetworkSourceLabels = _uiState.value.labels,
        selectedSourceId: Long = _uiState.value.selectedSourceId
    ) {
        _uiState.value = _uiState.value.copy(
            title = title,
            sources = sources.toList(),
            rows = rows.toList(),
            actions = actions.toList(),
            headerActions = headerActions.toList(),
            emptyText = emptyText,
            labels = labels,
            selectedSourceId = selectedSourceId
        )
    }

    fun setLoading(loading: Boolean, statusMessage: String = _uiState.value.statusMessage) {
        _uiState.value = _uiState.value.copy(
            loading = loading,
            statusMessage = statusMessage,
            error = null
        )
    }

    fun setError(error: String?) {
        _uiState.value = _uiState.value.copy(
            loading = false,
            error = error
        )
    }
}
