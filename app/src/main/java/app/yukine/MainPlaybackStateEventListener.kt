package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

internal fun interface MainPlaybackStateEventListenerFactory {
    fun create(
        selectedTabSource: MainPlaybackStateEventListener.SelectedTabSource,
        currentLyricsTrackIdSource: MainPlaybackStateEventListener.CurrentLyricsTrackIdSource,
        playbackSettingsSaver: MainPlaybackStateEventListener.PlaybackSettingsSaver,
        lyricsLoader: MainPlaybackStateEventListener.LyricsLoader,
        collectionsLoader: MainPlaybackStateEventListener.CollectionsLoader,
        nowBarRenderer: MainPlaybackStateEventListener.NowBarRenderer,
        homeDashboardPlaybackUpdater: MainPlaybackStateEventListener.HomeDashboardPlaybackUpdater,
        selectedTabRenderer: MainPlaybackStateEventListener.SelectedTabRenderer,
        nowPlayingContentUpdater: MainPlaybackStateEventListener.NowPlayingContentUpdater,
        nextStreamingTrackPreResolver: MainPlaybackStateEventListener.NextStreamingTrackPreResolver,
        streamingBufferingRecoveryHandler: MainPlaybackStateEventListener.StreamingBufferingRecoveryHandler,
        currentStreamingTrackResolver: MainPlaybackStateEventListener.CurrentStreamingTrackResolver,
        statusSink: MainPlaybackStateEventListener.StatusSink
    ): PlaybackStateEventController.Listener
}

internal class MainPlaybackStateEventListener(
    private val selectedTabSource: SelectedTabSource,
    private val currentLyricsTrackIdSource: CurrentLyricsTrackIdSource,
    private val playbackSettingsSaver: PlaybackSettingsSaver,
    private val lyricsLoader: LyricsLoader,
    private val collectionsLoader: CollectionsLoader,
    private val nowBarRenderer: NowBarRenderer,
    private val homeDashboardPlaybackUpdater: HomeDashboardPlaybackUpdater,
    private val selectedTabRenderer: SelectedTabRenderer,
    private val nowPlayingContentUpdater: NowPlayingContentUpdater,
    private val nextStreamingTrackPreResolver: NextStreamingTrackPreResolver,
    private val streamingBufferingRecoveryHandler: StreamingBufferingRecoveryHandler,
    private val currentStreamingTrackResolver: CurrentStreamingTrackResolver,
    private val statusSink: StatusSink
) : PlaybackStateEventController.Listener {
    fun interface SelectedTabSource {
        fun selectedTab(): String
    }

    fun interface CurrentLyricsTrackIdSource {
        fun currentLyricsTrackId(): Long
    }

    fun interface PlaybackSettingsSaver {
        fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float)
    }

    fun interface LyricsLoader {
        fun loadLyrics(track: Track?)
    }

    fun interface CollectionsLoader {
        fun loadCollections()
    }

    fun interface NowBarRenderer {
        fun renderNowBar()
    }

    fun interface HomeDashboardPlaybackUpdater {
        fun updateHomeDashboardPlayback(snapshot: PlaybackStateSnapshot)
    }

    fun interface SelectedTabRenderer {
        fun renderSelectedTab()
    }

    fun interface NowPlayingContentUpdater {
        fun updateNowPlayingContent()
    }

    fun interface NextStreamingTrackPreResolver {
        fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot)
    }

    fun interface StreamingBufferingRecoveryHandler {
        fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot)
    }

    fun interface CurrentStreamingTrackResolver {
        fun resolveCurrentStreamingTrackIfNeeded(): Boolean
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    override fun selectedTab(): String = selectedTabSource.selectedTab()

    override fun currentLyricsTrackId(): Long = currentLyricsTrackIdSource.currentLyricsTrackId()

    override fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float) {
        playbackSettingsSaver.savePlaybackSettings(playbackSpeed, appVolume)
    }

    override fun loadLyrics(track: Track?) {
        lyricsLoader.loadLyrics(track)
    }

    override fun loadCollections() {
        collectionsLoader.loadCollections()
    }

    override fun renderNowBar() {
        nowBarRenderer.renderNowBar()
    }

    override fun updateHomeDashboardPlayback(snapshot: PlaybackStateSnapshot) {
        homeDashboardPlaybackUpdater.updateHomeDashboardPlayback(snapshot)
    }

    override fun renderSelectedTab() {
        selectedTabRenderer.renderSelectedTab()
    }

    override fun updateNowPlayingContent() {
        nowPlayingContentUpdater.updateNowPlayingContent()
    }

    override fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot) {
        nextStreamingTrackPreResolver.preResolveNextStreamingTrack(snapshot)
    }

    override fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot) {
        streamingBufferingRecoveryHandler.recoverStreamingBuffering(snapshot)
    }

    override fun resolveCurrentStreamingTrackIfNeeded(): Boolean =
        currentStreamingTrackResolver.resolveCurrentStreamingTrackIfNeeded()

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }
}
