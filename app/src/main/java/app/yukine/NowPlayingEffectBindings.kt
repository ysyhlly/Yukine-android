package app.yukine

internal fun interface NowPlayingQueueOpener {
    fun openQueue()
}

internal fun interface NowPlayingAddToPlaylistOpener {
    fun open(effect: NowPlayingEffect.OpenAddToPlaylist)
}

internal class NowPlayingEffectBindings(
    private val queueOpener: NowPlayingQueueOpener,
    private val addToPlaylistOpener: NowPlayingAddToPlaylistOpener,
    private val statusSink: QueueStatusSink
) : NowPlayingEffectController.Listener {
    override fun openQueue() {
        queueOpener.openQueue()
    }

    override fun openAddToPlaylist(effect: NowPlayingEffect.OpenAddToPlaylist) {
        addToPlaylistOpener.open(effect)
    }

    override fun showMessage(message: String) {
        statusSink.set(message)
    }
}
