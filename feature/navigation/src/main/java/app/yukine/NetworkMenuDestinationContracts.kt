package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric

data class NetworkMenuUiState(
    val title: String = "",
    val metrics: List<SettingsMetric> = emptyList(),
    val actions: List<SettingsAction> = emptyList()
)
