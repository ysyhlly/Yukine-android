package app.yukine

import app.yukine.playback.EchoPlaybackService

internal class PlaybackServiceHostController(
    private val host: Host
) : PlaybackServiceConnectionController.Listener {
    interface Host {
        fun playbackSpeed(): Float

        fun appVolume(): Float

        fun concurrentPlaybackEnabled(): Boolean

        fun attachPlaybackService(service: EchoPlaybackService)

        fun clearPlaybackService()

        fun resetPlaybackStore()

        fun playPendingTracksIfNeeded()

        fun renderSelectedTab()

        fun renderNowBar()
    }

    override fun onPlaybackServiceConnected(service: EchoPlaybackService) {
        host.attachPlaybackService(service)
        service.setPlaybackSpeed(host.playbackSpeed())
        service.setAppVolume(host.appVolume())
        service.setConcurrentPlaybackEnabled(host.concurrentPlaybackEnabled())
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
