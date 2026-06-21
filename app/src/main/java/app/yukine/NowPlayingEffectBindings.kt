package app.yukine

internal fun interface NowPlayingQueueOpener {
    fun openQueue()
}

internal fun interface NowPlayingAddToPlaylistOpener {
    fun open(effect: NowPlayingEffect.OpenAddToPlaylist)
}

internal fun interface NowPlayingTrackSharer {
    fun share(effect: NowPlayingEffect.ShareTrack)
}

internal fun interface NowPlayingTrackDownloader {
    fun download(effect: NowPlayingEffect.DownloadTrack)
}

internal fun interface NowPlayingSourceSwitcher {
    fun switch(effect: NowPlayingEffect.SwitchSource)
}

internal class NowPlayingEffectBindings(
    private val queueOpener: NowPlayingQueueOpener,
    private val addToPlaylistOpener: NowPlayingAddToPlaylistOpener,
    private val trackSharer: NowPlayingTrackSharer,
    private val trackDownloader: NowPlayingTrackDownloader,
    private val sourceSwitcher: NowPlayingSourceSwitcher,
    private val statusSink: QueueStatusSink
) : NowPlayingEffectController.Listener {
    override fun openQueue() {
        queueOpener.openQueue()
    }

    override fun openAddToPlaylist(effect: NowPlayingEffect.OpenAddToPlaylist) {
        addToPlaylistOpener.open(effect)
    }

    override fun shareTrack(effect: NowPlayingEffect.ShareTrack) {
        trackSharer.share(effect)
    }

    override fun downloadTrack(effect: NowPlayingEffect.DownloadTrack) {
        trackDownloader.download(effect)
    }

    override fun switchSource(effect: NowPlayingEffect.SwitchSource) {
        sourceSwitcher.switch(effect)
    }

    override fun showMessage(message: String) {
        statusSink.set(message)
    }
}
