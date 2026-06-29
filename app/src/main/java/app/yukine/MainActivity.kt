package app.yukine

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import app.yukine.queue.QueueViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Android manifest entry point. The legacy Java host remains behind
 * MainActivityBase while shell ownership moves to Kotlin.
 */
@AndroidEntryPoint
class MainActivity : MainActivityBase() {
    private val mainActivityViewModel: MainActivityViewModel by viewModels()
    private val navigationViewModel: NavigationViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val streamingViewModel: StreamingViewModel by viewModels()
    private val streamingRecommendationViewModel: StreamingRecommendationViewModel by viewModels()
    private val homeDashboardViewModel: HomeDashboardViewModel by viewModels()
    private val nowPlayingViewModel: NowPlayingViewModel by viewModels()
    private val queueViewModel: QueueViewModel by viewModels()
    private val downloadsViewModel: DownloadsViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val lyricsViewModel: LyricsViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val collectionsViewModel: CollectionsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val networkMenuViewModel: NetworkMenuViewModel by viewModels()
    private val networkActionsViewModel: NetworkActionsViewModel by viewModels()
    private val statusMessageViewModel: StatusMessageViewModel by viewModels()
    private val networkSourcesViewModel: NetworkSourcesViewModel by viewModels()
    private val shellViewModel: ShellViewModel by viewModels()

    override fun createActivityViewModels(): MainActivityViewModels =
        MainActivityViewModels(
            mainActivityViewModel = mainActivityViewModel,
            navigationViewModel = navigationViewModel,
            playbackViewModel = playbackViewModel,
            streamingViewModel = streamingViewModel,
            streamingRecommendationViewModel = streamingRecommendationViewModel,
            homeDashboardViewModel = homeDashboardViewModel,
            nowPlayingViewModel = nowPlayingViewModel,
            queueViewModel = queueViewModel,
            downloadsViewModel = downloadsViewModel,
            searchViewModel = searchViewModel,
            lyricsViewModel = lyricsViewModel,
            libraryViewModel = libraryViewModel,
            collectionsViewModel = collectionsViewModel,
            settingsViewModel = settingsViewModel,
            networkMenuViewModel = networkMenuViewModel,
            networkActionsViewModel = networkActionsViewModel,
            statusMessageViewModel = statusMessageViewModel,
            networkSourcesViewModel = networkSourcesViewModel,
            shellViewModel = shellViewModel
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}
