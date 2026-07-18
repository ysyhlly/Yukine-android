package app.yukine

import app.yukine.model.Track
import java.util.Collections

internal class HomeDashboardActionAdapter(
    private val libraryModeOpener: LibraryModeOpener,
    private val playbackContinuer: PlaybackContinuer,
    private val nowPlayingOpener: NowPlayingOpener,
    private val trackListPlayer: TrackListPlayer,
    private val libraryRefresher: LibraryRefresher,
    private val queueOpener: QueueOpener,
    private val streamingOpener: StreamingOpener,
    private val searchOpener: SearchOpener,
    private val dailyRecommendationsPlayer: DailyRecommendationsPlayer,
    private val heartbeatRecommendationsPlayer: HeartbeatRecommendationsPlayer,
    private val nextTrackPlayer: NextTrackPlayer = NextTrackPlayer { }
) : HomeDashboardIntentHandler {
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

    fun interface StreamingOpener {
        fun openStreaming()
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

    fun interface NextTrackPlayer {
        fun nextTrack()
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

    override fun nextTrack() {
        nextTrackPlayer.nextTrack()
    }

    override fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            return
        }
        trackListPlayer.playTrackList(ArrayList(tracks).also(Collections::shuffle), 0)
    }

    override fun openStreaming() {
        streamingOpener.openStreaming()
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

}
