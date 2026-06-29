package app.yukine

import app.yukine.playback.EchoPlaybackService

internal fun interface MainPlaybackServiceHostFactory {
    fun create(
        playbackSpeedSource: MainPlaybackServiceHost.PlaybackSpeedSource,
        appVolumeSource: MainPlaybackServiceHost.AppVolumeSource,
        concurrentPlaybackSource: MainPlaybackServiceHost.ConcurrentPlaybackSource,
        statusBarLyricsSource: MainPlaybackServiceHost.StatusBarLyricsSource,
        playbackRestoreSource: MainPlaybackServiceHost.PlaybackRestoreSource,
        replayGainSource: MainPlaybackServiceHost.ReplayGainSource,
        playbackServiceAttacher: MainPlaybackServiceHost.PlaybackServiceAttacher,
        playbackServiceClearer: MainPlaybackServiceHost.PlaybackServiceClearer,
        playbackStoreResetter: MainPlaybackServiceHost.PlaybackStoreResetter,
        pendingTracksPlayer: MainPlaybackServiceHost.PendingTracksPlayer,
        selectedTabRenderer: MainPlaybackServiceHost.SelectedTabRenderer,
        nowBarRenderer: MainPlaybackServiceHost.NowBarRenderer
    ): PlaybackServiceHostController.Host
}

internal class MainPlaybackServiceHost(
    private val playbackSpeedSource: PlaybackSpeedSource,
    private val appVolumeSource: AppVolumeSource,
    private val concurrentPlaybackSource: ConcurrentPlaybackSource,
    private val statusBarLyricsSource: StatusBarLyricsSource,
    private val playbackRestoreSource: PlaybackRestoreSource,
    private val replayGainSource: ReplayGainSource,
    private val playbackServiceAttacher: PlaybackServiceAttacher,
    private val playbackServiceClearer: PlaybackServiceClearer,
    private val playbackStoreResetter: PlaybackStoreResetter,
    private val pendingTracksPlayer: PendingTracksPlayer,
    private val selectedTabRenderer: SelectedTabRenderer,
    private val nowBarRenderer: NowBarRenderer
) : PlaybackServiceHostController.Host {
    fun interface PlaybackSpeedSource {
        fun playbackSpeed(): Float
    }

    fun interface AppVolumeSource {
        fun appVolume(): Float
    }

    fun interface ConcurrentPlaybackSource {
        fun concurrentPlaybackEnabled(): Boolean
    }

    fun interface StatusBarLyricsSource {
        fun statusBarLyricsEnabled(): Boolean
    }

    fun interface PlaybackRestoreSource {
        fun playbackRestoreEnabled(): Boolean
    }

    fun interface ReplayGainSource {
        fun replayGainEnabled(): Boolean
    }

    fun interface PlaybackServiceAttacher {
        fun attachPlaybackService(service: EchoPlaybackService)
    }

    fun interface PlaybackServiceClearer {
        fun clearPlaybackService()
    }

    fun interface PlaybackStoreResetter {
        fun resetPlaybackStore()
    }

    fun interface PendingTracksPlayer {
        fun playPendingTracksIfNeeded()
    }

    fun interface SelectedTabRenderer {
        fun renderSelectedTab()
    }

    fun interface NowBarRenderer {
        fun renderNowBar()
    }

    override fun playbackSpeed(): Float = playbackSpeedSource.playbackSpeed()

    override fun appVolume(): Float = appVolumeSource.appVolume()

    override fun concurrentPlaybackEnabled(): Boolean = concurrentPlaybackSource.concurrentPlaybackEnabled()

    override fun statusBarLyricsEnabled(): Boolean = statusBarLyricsSource.statusBarLyricsEnabled()

    override fun playbackRestoreEnabled(): Boolean = playbackRestoreSource.playbackRestoreEnabled()

    override fun replayGainEnabled(): Boolean = replayGainSource.replayGainEnabled()

    override fun attachPlaybackService(service: EchoPlaybackService) {
        playbackServiceAttacher.attachPlaybackService(service)
        service.setAppVisible(true)
    }

    override fun clearPlaybackService() {
        playbackServiceClearer.clearPlaybackService()
    }

    override fun resetPlaybackStore() {
        playbackStoreResetter.resetPlaybackStore()
    }

    override fun playPendingTracksIfNeeded() {
        pendingTracksPlayer.playPendingTracksIfNeeded()
    }

    override fun renderSelectedTab() {
        selectedTabRenderer.renderSelectedTab()
    }

    override fun renderNowBar() {
        nowBarRenderer.renderNowBar()
    }
}
