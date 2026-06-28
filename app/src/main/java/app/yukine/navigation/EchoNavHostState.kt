package app.yukine.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yukine.CollectionsViewModel
import app.yukine.DownloadsViewModel
import app.yukine.LibraryViewModel
import app.yukine.HomeDashboardViewModel
import app.yukine.MainActivityViewModel
import app.yukine.NavigationViewModel
import app.yukine.NetworkMenuViewModel
import app.yukine.NetworkSourcesViewModel
import app.yukine.NowPlayingScreenStateProvider
import app.yukine.PlaybackViewModel
import app.yukine.SettingsViewModel
import app.yukine.StreamingViewModel
import app.yukine.TrackDownloadManager
import kotlinx.coroutines.flow.StateFlow

private val EmptyRealtimeBands = FloatArray(0)

class EchoNavHostState @JvmOverloads constructor(
    val mainViewModel: MainActivityViewModel,
    val navigationViewModel: NavigationViewModel,
    val homeDashboardViewModel: HomeDashboardViewModel,
    val nowPlayingStateProvider: NowPlayingScreenStateProvider,
    val libraryViewModel: LibraryViewModel,
    val collectionsViewModel: CollectionsViewModel,
    val settingsViewModel: SettingsViewModel,
    val networkMenuViewModel: NetworkMenuViewModel,
    val networkSourcesViewModel: NetworkSourcesViewModel,
    val streamingViewModel: StreamingViewModel,
    val playbackViewModel: PlaybackViewModel,
    selectedTabRoute: String = HomeTab.route,
    val downloadsViewModel: DownloadsViewModel = DownloadsViewModel(),
    val trackDownloadManager: TrackDownloadManager? = null,
    val realtimeBeatProvider: () -> Float = { 0f },
    val realtimeBandsProvider: () -> FloatArray = { EmptyRealtimeBands },
    val visualMotionEnabled: Boolean = true
) {
    val nowBarStateProvider: NowPlayingScreenStateProvider = nowPlayingStateProvider
    val playbackSnapshotProvider: PlaybackSnapshotProvider = playbackViewModel
    val nowPlayingUiState: StateFlow<app.yukine.NowPlayingUiState> = nowPlayingStateProvider.uiState

    var selectedTabRoute by mutableStateOf(selectedTabRoute)
}
