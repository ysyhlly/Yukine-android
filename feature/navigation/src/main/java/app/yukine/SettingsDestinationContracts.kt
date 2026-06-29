package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric

data class SettingsChromeState(
    val pageBackgrounds: PageBackgrounds = PageBackgrounds.empty(),
    val nowPlayingGesturesEnabled: Boolean = true
)

interface SettingsDestinationState {
    val destinationTitle: String
    val destinationMetrics: List<SettingsMetric>
    val destinationActions: List<SettingsAction>
}
