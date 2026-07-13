package app.yukine

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
    val networkSourcesViewModel: NetworkSourcesViewModel
) {
    companion object {
        fun from(activity: ComponentActivity): MainActivityViewModels {
            val provider = ViewModelProvider(activity)
            return MainActivityViewModels(
                mainActivityViewModel = provider[MainActivityViewModel::class.java],
                navigationViewModel = provider[NavigationViewModel::class.java],
                playbackViewModel = provider[PlaybackViewModel::class.java],
                streamingViewModel = provider[StreamingViewModel::class.java],
                streamingRecommendationViewModel = provider[StreamingRecommendationViewModel::class.java],
                homeDashboardViewModel = provider[HomeDashboardViewModel::class.java],
                nowPlayingViewModel = provider[NowPlayingViewModel::class.java],
                queueViewModel = provider[QueueViewModel::class.java],
                downloadsViewModel = provider[DownloadsViewModel::class.java],
                searchViewModel = provider[SearchViewModel::class.java],
                lyricsViewModel = provider[LyricsViewModel::class.java],
                libraryViewModel = provider[LibraryViewModel::class.java],
                collectionsViewModel = provider[CollectionsViewModel::class.java],
                settingsViewModel = provider[SettingsViewModel::class.java],
                networkMenuViewModel = provider[NetworkMenuViewModel::class.java],
                networkActionsViewModel = provider[NetworkActionsViewModel::class.java],
                statusMessageViewModel = provider[StatusMessageViewModel::class.java],
                networkSourcesViewModel = provider[NetworkSourcesViewModel::class.java]
            )
        }
    }
}
