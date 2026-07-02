package app.yukine

import app.yukine.model.Track
import app.yukine.ui.HomeDashboardActions
import java.util.Collections

internal fun interface MainHomeDashboardRenderListenerFactory {
    fun create(
        libraryModeOpener: MainHomeDashboardRenderListener.LibraryModeOpener,
        playbackContinuer: MainHomeDashboardRenderListener.PlaybackContinuer,
        nowPlayingOpener: MainHomeDashboardRenderListener.NowPlayingOpener,
        trackListPlayer: MainHomeDashboardRenderListener.TrackListPlayer,
        libraryRefresher: MainHomeDashboardRenderListener.LibraryRefresher,
        queueOpener: MainHomeDashboardRenderListener.QueueOpener,
        allTracksSource: MainHomeDashboardRenderListener.AllTracksSource,
        streamingOpener: MainHomeDashboardRenderListener.StreamingOpener,
        collectionsOpener: MainHomeDashboardRenderListener.CollectionsOpener,
        searchOpener: MainHomeDashboardRenderListener.SearchOpener,
        dailyRecommendationsPlayer: MainHomeDashboardRenderListener.DailyRecommendationsPlayer,
        heartbeatRecommendationsPlayer: MainHomeDashboardRenderListener.HeartbeatRecommendationsPlayer,
        actionsPublisher: MainHomeDashboardRenderListener.ActionsPublisher
    ): HomeDashboardRenderController.Listener
}

internal class MainHomeDashboardRenderListener(
    private val libraryModeOpener: LibraryModeOpener,
    private val playbackContinuer: PlaybackContinuer,
    private val nowPlayingOpener: NowPlayingOpener,
    private val trackListPlayer: TrackListPlayer,
    private val libraryRefresher: LibraryRefresher,
    private val queueOpener: QueueOpener,
    private val allTracksSource: AllTracksSource,
    private val streamingOpener: StreamingOpener,
    private val collectionsOpener: CollectionsOpener,
    private val searchOpener: SearchOpener,
    private val dailyRecommendationsPlayer: DailyRecommendationsPlayer,
    private val heartbeatRecommendationsPlayer: HeartbeatRecommendationsPlayer,
    private val actionsPublisher: ActionsPublisher
) : HomeDashboardRenderController.Listener {
    fun interface LibraryModeOpener {
        fun openLibraryMode(mode: String)
    }

    fun interface PlaybackContinuer {
        fun continuePlayback(track: Track?)
    }

    fun interface NowPlayingOpener {
        fun openNowPlaying()
    }

    fun interface TrackListPlayer {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface LibraryRefresher {
        fun refreshLibrary()
    }

    fun interface QueueOpener {
        fun openQueue()
    }

    fun interface AllTracksSource {
        fun allTracks(): List<Track>
    }

    fun interface StreamingOpener {
        fun openStreaming()
    }

    fun interface CollectionsOpener {
        fun openCollections()
    }

    fun interface SearchOpener {
        fun openSearch()
    }

    fun interface DailyRecommendationsPlayer {
        fun playDailyRecommendations()
    }

    fun interface HeartbeatRecommendationsPlayer {
        fun playHeartbeatRecommendations()
    }

    fun interface ActionsPublisher {
        fun publishHomeDashboardActions(actions: HomeDashboardActions)
    }

    override fun openLibraryMode(mode: String) {
        libraryModeOpener.openLibraryMode(mode)
    }

    override fun continuePlayback(track: Track?) {
        playbackContinuer.continuePlayback(track)
    }

    override fun openNowPlaying() {
        nowPlayingOpener.openNowPlaying()
    }

    override fun playTrack(track: Track) {
        trackListPlayer.playTrackList(listOf(track), 0)
    }

    override fun refreshLibrary() {
        libraryRefresher.refreshLibrary()
    }

    override fun openQueue() {
        queueOpener.openQueue()
    }

    override fun shuffleAll() {
        val allTracks = allTracksSource.allTracks()
        if (allTracks.isEmpty()) {
            return
        }
        trackListPlayer.playTrackList(ArrayList(allTracks).also(Collections::shuffle), 0)
    }

    override fun openStreaming() {
        streamingOpener.openStreaming()
    }

    override fun openCollections() {
        collectionsOpener.openCollections()
    }

    override fun openSearch() {
        searchOpener.openSearch()
    }

    override fun playDailyRecommendations() {
        dailyRecommendationsPlayer.playDailyRecommendations()
    }

    override fun playHeartbeatRecommendations() {
        heartbeatRecommendationsPlayer.playHeartbeatRecommendations()
    }

    override fun publishHomeDashboardActions(actions: HomeDashboardActions) {
        actionsPublisher.publishHomeDashboardActions(actions)
    }
}
