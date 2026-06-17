package app.echo.next.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.echo.next.CollectionsViewModel
import app.echo.next.LibraryViewModel
import app.echo.next.MainActivityViewModel
import app.echo.next.NetworkSourcesViewModel
import app.echo.next.NowPlayingViewModel
import app.echo.next.SettingsViewModel
import app.echo.next.ui.CollectionsActions
import app.echo.next.ui.HomeDashboardActions
import app.echo.next.ui.LibraryGroupActions
import app.echo.next.ui.NetworkSourceActions
import app.echo.next.ui.NetworkSourceLabels
import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsListScrollState
import app.echo.next.ui.SettingsMetric
import app.echo.next.ui.StreamingSearchActions
import app.echo.next.ui.StreamingSearchLabels
import app.echo.next.ui.TrackListHeaderAction
import app.echo.next.ui.TrackListHeaderMetric
import app.echo.next.ui.TrackListLabels
import app.echo.next.ui.TrackListModeAction
import app.echo.next.ui.TrackRowActions

class EchoNavHostState(
    val mainViewModel: MainActivityViewModel,
    val nowPlayingViewModel: NowPlayingViewModel,
    val libraryViewModel: LibraryViewModel,
    val collectionsViewModel: CollectionsViewModel,
    val settingsViewModel: SettingsViewModel,
    val networkSourcesViewModel: NetworkSourcesViewModel,
    homeActions: HomeDashboardActions,
    trackListActions: List<TrackRowActions> = emptyList(),
    trackListHeaderMetrics: List<TrackListHeaderMetric> = emptyList(),
    trackListHeaderActions: List<TrackListHeaderAction> = emptyList(),
    trackListEmptyText: String = "",
    trackListModeActions: List<TrackListModeAction> = emptyList(),
    trackListLabels: TrackListLabels = TrackListLabels(),
    libraryGroupActions: List<LibraryGroupActions> = emptyList(),
    libraryGroupEmptyText: String = "",
    libraryGroupModeActions: List<TrackListModeAction> = emptyList(),
    collectionsActions: CollectionsActions,
    settingsActions: List<SettingsAction> = emptyList(),
    settingsScrollState: SettingsListScrollState = SettingsListScrollState(),
    networkSourceActions: List<NetworkSourceActions> = emptyList(),
    networkSourceHeaderActions: List<TrackListHeaderAction> = emptyList(),
    networkSourceEmptyText: String = "",
    networkSourceLabels: NetworkSourceLabels = NetworkSourceLabels(),
    networkMenuTitle: String = "",
    networkMenuMetrics: List<SettingsMetric> = emptyList(),
    networkMenuActions: List<SettingsAction> = emptyList(),
    streamingSearchLabels: StreamingSearchLabels = StreamingSearchLabels.empty(),
    streamingSearchActions: StreamingSearchActions = StreamingSearchActions.empty(),
    selectedTabRoute: String = HomeTab.route
) {
    var selectedTabRoute by mutableStateOf(selectedTabRoute)
    var homeActions by mutableStateOf(homeActions)
    var trackListActions by mutableStateOf(trackListActions)
    var trackListHeaderMetrics by mutableStateOf(trackListHeaderMetrics)
    var trackListHeaderActions by mutableStateOf(trackListHeaderActions)
    var trackListEmptyText by mutableStateOf(trackListEmptyText)
    var trackListModeActions by mutableStateOf(trackListModeActions)
    var trackListLabels by mutableStateOf(trackListLabels)
    var libraryGroupActions by mutableStateOf(libraryGroupActions)
    var libraryGroupEmptyText by mutableStateOf(libraryGroupEmptyText)
    var libraryGroupModeActions by mutableStateOf(libraryGroupModeActions)
    var collectionsActions by mutableStateOf(collectionsActions)
    var settingsActions by mutableStateOf(settingsActions)
    var settingsScrollState by mutableStateOf(settingsScrollState)
    var networkSourceActions by mutableStateOf(networkSourceActions)
    var networkSourceHeaderActions by mutableStateOf(networkSourceHeaderActions)
    var networkSourceEmptyText by mutableStateOf(networkSourceEmptyText)
    var networkSourceLabels by mutableStateOf(networkSourceLabels)
    var networkMenuTitle by mutableStateOf(networkMenuTitle)
    var networkMenuMetrics by mutableStateOf(networkMenuMetrics)
    var networkMenuActions by mutableStateOf(networkMenuActions)
    var streamingSearchLabels by mutableStateOf(streamingSearchLabels)
    var streamingSearchActions by mutableStateOf(streamingSearchActions)
    var openNowPlayingImmersive by mutableStateOf(false)
}
