package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.model.RemoteSource
import app.yukine.ui.NetworkSourceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkSourcesUiState(
    val title: String = "",
    val sources: List<RemoteSource> = emptyList(),
    val rows: List<NetworkSourceUiState> = emptyList(),
    val selectedSourceId: Long = -1L,
    val loading: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null
)

class NetworkSourcesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkSourcesUiState())
    val uiState: StateFlow<NetworkSourcesUiState> = _uiState.asStateFlow()

    private val screenState = MutableStateFlow(MainActivityNetworkSourcesUiState())
    val screen: StateFlow<MainActivityNetworkSourcesUiState> = screenState.asStateFlow()

    fun updateSources(
        title: String,
        sources: List<RemoteSource>,
        rows: List<NetworkSourceUiState>,
        selectedSourceId: Long = _uiState.value.selectedSourceId
    ) {
        _uiState.value = _uiState.value.copy(
            title = title,
            sources = sources.toList(),
            rows = rows.toList(),
            selectedSourceId = selectedSourceId
        )
        screenState.value = MainActivityNetworkSourcesUiState(title, rows.toList())
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
