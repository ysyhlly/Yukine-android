package app.yukine

internal class NowPlayingEffectController(
    private val listener: Listener
) {
    interface Listener {
        fun openQueue()

        fun openAddToPlaylist(effect: NowPlayingEffect.OpenAddToPlaylist)

        fun shareTrack(effect: NowPlayingEffect.ShareTrack)

        fun downloadTrack(effect: NowPlayingEffect.DownloadTrack)

        fun switchSource(effect: NowPlayingEffect.SwitchSource)

        fun showMessage(message: String)
    }

    fun handle(effects: List<NowPlayingEffect>?) {
        if (effects.isNullOrEmpty()) {
            return
        }
        effects.forEach { effect ->
            when (effect) {
                NowPlayingEffect.OpenQueue -> listener.openQueue()
                is NowPlayingEffect.OpenAddToPlaylist -> listener.openAddToPlaylist(effect)
                is NowPlayingEffect.ShareTrack -> listener.shareTrack(effect)
                is NowPlayingEffect.DownloadTrack -> listener.downloadTrack(effect)
                is NowPlayingEffect.SwitchSource -> listener.switchSource(effect)
                is NowPlayingEffect.ShowMessage -> listener.showMessage(effect.message)
            }
        }
    }
}
