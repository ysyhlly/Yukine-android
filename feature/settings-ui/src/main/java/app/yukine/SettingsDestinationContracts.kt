package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric

data class SettingsChromeState(
    val pageBackgrounds: PageBackgrounds = PageBackgrounds.empty(),
    val nowPlayingGesturesEnabled: Boolean = true,
    val customBackgroundBlurEnabled: Boolean = false,
    val customBackgroundBlurRadiusDp: Float = 24f,
    val glassBlurEnabled: Boolean = false,
    val glassBlurRadiusDp: Float = 18f,
    val glassSurfaceOpacity: Float = 0.62f
)

interface SettingsDestinationState {
    val destinationTitle: String
    val destinationMetrics: List<SettingsMetric>
    val destinationActions: List<SettingsAction>
}
