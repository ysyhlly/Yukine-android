package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import app.yukine.ui.HomeDashboardLayout

data class SettingsChromeState(
    val pageBackgrounds: PageBackgrounds = PageBackgrounds.empty(),
    val homeDashboardLayout: HomeDashboardLayout = HomeDashboardLayout.Classic,
    val nowPlayingGesturesEnabled: Boolean = true,
    val customBackgroundBlurEnabled: Boolean = false,
    val customBackgroundBlurRadiusDp: Float = 24f,
    val glassBlurEnabled: Boolean = false,
    val glassBlurRadiusDp: Float = 18f,
    val glassSurfaceOpacity: Float = 0.62f,
    val compactSettingsCards: Boolean = false
)

interface SettingsDestinationState {
    val destinationTitle: String
    val destinationMetrics: List<SettingsMetric>
    val destinationActions: List<SettingsAction>
    val destinationIssues: List<SettingsIssue>
        get() = emptyList()
    val destinationIssuesTitle: String
        get() = ""
    val destinationSearchEntries: List<SettingsSearchEntry>
        get() = emptyList()
    val destinationSearchPlaceholder: String
        get() = ""
    val destinationSearchResultsTitle: String
        get() = ""
    val destinationSearchEmptyMessage: String
        get() = ""
    val destinationHighlightedEntryId: SettingsEntryId?
        get() = null
    val destinationCompactSettingsCards: Boolean
        get() = false
}
