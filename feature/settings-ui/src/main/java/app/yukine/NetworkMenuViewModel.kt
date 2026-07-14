package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMenuViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkMenuUiState())
    val uiState: StateFlow<NetworkMenuUiState> = _uiState.asStateFlow()

    fun updateMenu(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ) {
        _uiState.value = NetworkMenuUiState(
            title = title,
            metrics = metrics.toList(),
            actions = actions.toList()
        )
    }
}
