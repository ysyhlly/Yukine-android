package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.HomeDashboardActions
import java.util.ArrayList
import java.util.Collections

internal fun interface LibraryModeOpener {
    fun open(mode: String)
}

internal fun interface NullableTrackAction {
    fun run(track: Track?)
}

internal fun interface TrackListProvider {
    fun tracks(): List<Track>
}

internal fun interface NetworkPageNavigator {
    fun navigate(page: String)
}

internal fun interface HomeDashboardActionsSink {
    fun publish(actions: HomeDashboardActions)
}

internal class HomeDashboardRenderBindings(
    private val libraryModeOpener: LibraryModeOpener,
    private val continuePlaybackAction: NullableTrackAction,
    private val openNowPlayingAction: Runnable,
    private val playTrackListAction: TrackListPlaybackAction,
    private val refreshLibraryAction: Runnable,
    private val openQueueAction: Runnable,
    private val allTracksProvider: TrackListProvider,
    private val openStreamingAction: Runnable,
    private val openCollectionsAction: Runnable,
    private val recommendationActionRunner: RecommendationActionRunner,
    private val actionsSink: HomeDashboardActionsSink,
    private val openSearchAction: Runnable = Runnable { }
) : HomeDashboardRenderController.Listener {
    override fun openLibraryMode(mode: String) {
        libraryModeOpener.open(mode)
    }

    override fun continuePlayback(track: Track?) {
        continuePlaybackAction.run(track)
    }

    override fun openNowPlaying() {
        openNowPlayingAction.run()
    }

    override fun playTrack(track: Track) {
        playTrackListAction.play(Collections.singletonList(track), 0)
    }

    override fun refreshLibrary() {
        refreshLibraryAction.run()
    }

    override fun openQueue() {
        openQueueAction.run()
    }

    override fun shuffleAll() {
        val allTracks = allTracksProvider.tracks()
        if (allTracks.isNotEmpty()) {
            val shuffled = ArrayList(allTracks)
            shuffled.shuffle()
            playTrackListAction.play(shuffled, 0)
        }
    }

    override fun openStreaming() {
        openStreamingAction.run()
    }

    override fun openCollections() {
        openCollectionsAction.run()
    }

    override fun openSearch() {
        openSearchAction.run()
    }

    override fun playDailyRecommendations() {
        recommendationActionRunner.run(RecommendationAction.PlayDaily(StreamingProviderName.NETEASE))
    }

    override fun playHeartbeatRecommendations() {
        recommendationActionRunner.run(RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE))
    }

    override fun publishHomeDashboardActions(actions: HomeDashboardActions) {
        actionsSink.publish(actions)
    }
}
