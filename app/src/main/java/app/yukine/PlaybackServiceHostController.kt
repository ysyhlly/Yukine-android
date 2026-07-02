package app.yukine

internal class PlaybackServiceHostController(
    private val host: Host
) : PlaybackServiceConnectionController.Listener {
    interface Host {
        fun playbackSpeed(): Float

        fun appVolume(): Float

        fun concurrentPlaybackEnabled(): Boolean

        fun statusBarLyricsEnabled(): Boolean

        fun playbackRestoreEnabled(): Boolean

        fun replayGainEnabled(): Boolean

        fun attachPlaybackService(service: PlaybackServiceHostPort)

        fun clearPlaybackService()

        fun resetPlaybackStore()

        fun playPendingTracksIfNeeded()

        fun renderSelectedTab()

        fun renderNowBar()
    }

    override fun onPlaybackServiceConnected(service: PlaybackServiceHostPort) {
        host.attachPlaybackService(service)
        service.setPlaybackSpeed(host.playbackSpeed())
        service.setAppVolume(host.appVolume())
        service.setConcurrentPlaybackEnabled(host.concurrentPlaybackEnabled())
        service.setStatusBarLyricsEnabled(host.statusBarLyricsEnabled())
        service.setPlaybackRestoreEnabled(host.playbackRestoreEnabled())
        service.setReplayGainEnabled(host.replayGainEnabled())
        host.playPendingTracksIfNeeded()
        renderPlaybackChrome()
    }

    override fun onPlaybackServiceDisconnected() {
        host.clearPlaybackService()
        host.resetPlaybackStore()
        renderPlaybackChrome()
    }

    private fun renderPlaybackChrome() {
        host.renderSelectedTab()
        host.renderNowBar()
    }
}
