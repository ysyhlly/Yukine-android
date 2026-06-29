package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.queue.QueueViewModel

data class MainActivityViewModels(
    val mainActivityViewModel: MainActivityViewModel,
    val navigationViewModel: NavigationViewModel,
    val playbackViewModel: PlaybackViewModel,
    val streamingViewModel: StreamingViewModel,
    val streamingRecommendationViewModel: StreamingRecommendationViewModel,
    val homeDashboardViewModel: HomeDashboardViewModel,
    val nowPlayingViewModel: NowPlayingViewModel,
    val queueViewModel: QueueViewModel,
    val downloadsViewModel: DownloadsViewModel,
    val searchViewModel: SearchViewModel,
    val lyricsViewModel: LyricsViewModel,
    val libraryViewModel: LibraryViewModel,
    val collectionsViewModel: CollectionsViewModel,
    val settingsViewModel: SettingsViewModel,
    val networkMenuViewModel: NetworkMenuViewModel,
    val networkActionsViewModel: ViewModel,
    val statusMessageViewModel: ViewModel,
    val networkSourcesViewModel: NetworkSourcesViewModel,
    val shellViewModel: ShellViewModel
)
