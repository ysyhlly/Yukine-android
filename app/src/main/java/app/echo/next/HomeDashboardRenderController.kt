package app.echo.next

import app.echo.next.model.Track
import app.echo.next.model.TrackPlayRecord
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.ui.HomeDashboardActions
import java.util.ArrayList

internal class HomeDashboardRenderController(
    private val viewModel: MainActivityViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun openLibraryMode(mode: String)

        fun continuePlayback(track: Track?)

        fun openNowPlaying()

        fun playTrack(track: Track)

        fun refreshLibrary()

        fun openQueue()

        fun shuffleAll()

        fun openStreaming()

        fun openCollections()

        fun playDailyRecommendations()

        fun playHeartbeatRecommendations()

        fun publishHomeDashboardActions(actions: HomeDashboardActions)
    }

    /**
     * Render the home dashboard.
     * Uses backend API if configured, falls back to local data otherwise.
     */
    fun render(
        languageMode: String,
        allTracks: List<Track>,
        visibleTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        playbackState: PlaybackStateSnapshot?
    ) {
        // Start async fetch from backend (will fall back to local if unavailable)
        viewModel.fetchHomeDashboard(
            localTracks = if (visibleTracks.isNotEmpty()) visibleTracks else allTracks,
            localRecords = recentRecords,
            localPlayback = playbackState,
            onComplete = Runnable {
                // State is updated in ViewModel, UI will observe the change
            }
        )

        // Build actions based on local data (for immediate interaction)
        val recentTracks = recentRecords
            .filter { it.track != null }
            .sortedByDescending { it.playedAt }
            .take(8)
            .map { it.track }
        val continueTrack = playbackState?.currentTrack
            ?: recentTracks.firstOrNull()
            ?: visibleTracks.firstOrNull()
            ?: allTracks.firstOrNull()

        val statActions = ArrayList<Runnable>()
        // Stats will be populated by backend response, but we need placeholder actions
        for (mode in listOf(LibraryGrouping.SONGS, LibraryGrouping.ALBUMS, LibraryGrouping.ARTISTS, LibraryGrouping.FOLDERS)) {
            statActions.add(Runnable { listener.openLibraryMode(mode) })
        }

        val recentActions = ArrayList<Runnable>()
        for (track in recentTracks) {
            recentActions.add(Runnable { listener.playTrack(track) })
        }

        val actions = HomeDashboardActions(
            onOpenStat = statActions,
            onContinue = Runnable {
                val liveTrack = viewModel.playback.value.snapshot.currentTrack ?: continueTrack
                listener.continuePlayback(liveTrack)
            },
            onOpenNowPlaying = Runnable { listener.openNowPlaying() },
            onPlayRecent = recentActions,
            onRefresh = Runnable { listener.refreshLibrary() },
            onViewQueue = Runnable { listener.openQueue() },
            onShuffleAll = Runnable { listener.shuffleAll() },
            onRecentTabChanged = { /* Tab switching handled locally in Compose */ },
            onDailyRecommend = Runnable { listener.playDailyRecommendations() },
            onHeartbeatRecommend = Runnable { listener.playHeartbeatRecommendations() },
            onOpenCollections = Runnable { listener.openCollections() }
        )
        listener.publishHomeDashboardActions(actions)
    }
}
