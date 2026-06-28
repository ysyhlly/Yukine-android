package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric

interface SettingsDestinationState {
    val destinationTitle: String
    val destinationMetrics: List<SettingsMetric>
    val destinationActions: List<SettingsAction>
}
