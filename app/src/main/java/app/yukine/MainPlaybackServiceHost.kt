package app.yukine

internal fun interface MainPlaybackServiceHostFactory {
    fun create(
        playbackSettingsSource: MainPlaybackServiceHost.PlaybackSettingsSource,
        playbackServiceAttacher: MainPlaybackServiceHost.PlaybackServiceAttacher,
        playbackServiceDetacher: MainPlaybackServiceHost.PlaybackServiceDetacher,
        pendingTracksPlayer: MainPlaybackServiceHost.PendingTracksPlayer,
        playbackChromeRenderer: MainPlaybackServiceHost.PlaybackChromeRenderer
    ): PlaybackServiceHostController.Host
}

internal data class PlaybackServiceConnectionSettings(
    val playbackSpeed: Float,
    val appVolume: Float,
    val concurrentPlaybackEnabled: Boolean,
    val statusBarLyricsEnabled: Boolean,
    val playbackRestoreEnabled: Boolean,
    val replayGainEnabled: Boolean
)

internal class MainPlaybackServiceHost(
    private val playbackSettingsSource: PlaybackSettingsSource,
    private val playbackServiceAttacher: PlaybackServiceAttacher,
    private val playbackServiceDetacher: PlaybackServiceDetacher,
    private val pendingTracksPlayer: PendingTracksPlayer,
    private val playbackChromeRenderer: PlaybackChromeRenderer
) : PlaybackServiceHostController.Host {
    fun interface PlaybackSettingsSource {
        fun playbackSettings(): PlaybackServiceConnectionSettings
    }

    fun interface PlaybackServiceAttacher {
        fun attachPlaybackService(service: PlaybackServiceHostPort)
    }

    fun interface PlaybackServiceDetacher {
        fun detachPlaybackService()
    }

    fun interface PendingTracksPlayer {
        fun playPendingTracksIfNeeded()
    }

    fun interface PlaybackChromeRenderer {
        fun renderPlaybackChrome()
    }

    override fun attachPlaybackService(service: PlaybackServiceHostPort) {
        playbackServiceAttacher.attachPlaybackService(service)
        service.setAppVisible(true)
        val settings = playbackSettingsSource.playbackSettings()
        service.setPlaybackSpeed(settings.playbackSpeed)
        service.setAppVolume(settings.appVolume)
        service.setConcurrentPlaybackEnabled(settings.concurrentPlaybackEnabled)
        service.setStatusBarLyricsEnabled(settings.statusBarLyricsEnabled)
        service.setPlaybackRestoreEnabled(settings.playbackRestoreEnabled)
        service.setReplayGainEnabled(settings.replayGainEnabled)
        pendingTracksPlayer.playPendingTracksIfNeeded()
    }

    override fun detachPlaybackService() {
        playbackServiceDetacher.detachPlaybackService()
    }

    override fun renderPlaybackChrome() {
        playbackChromeRenderer.renderPlaybackChrome()
    }
}
