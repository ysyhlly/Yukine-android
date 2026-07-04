package app.yukine

internal class PlaybackServiceHostController(
    private val host: Host
) : PlaybackServiceConnectionController.Listener {
    interface Host {
        fun attachPlaybackService(service: PlaybackServiceHostPort)

        fun detachPlaybackService()

        fun renderPlaybackChrome()
    }

    override fun onPlaybackServiceConnected(service: PlaybackServiceHostPort) {
        host.attachPlaybackService(service)
        renderPlaybackChrome()
    }

    override fun onPlaybackServiceDisconnected() {
        host.detachPlaybackService()
        renderPlaybackChrome()
    }

    private fun renderPlaybackChrome() {
        host.renderPlaybackChrome()
    }
}
