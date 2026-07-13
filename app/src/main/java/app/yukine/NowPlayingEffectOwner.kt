package app.yukine

import app.yukine.model.Track
import java.util.function.Consumer

/** Consumes typed now-playing effects at the platform boundary. */
internal class NowPlayingEffectOwner(
    private val viewModel: NowPlayingViewModel,
    private val openQueue: Runnable,
    private val openAddToPlaylist: Consumer<Track>,
    private val shareTrack: Consumer<Track>,
    private val downloadTrack: Consumer<Track>,
    private val switchSource: Consumer<NowPlayingEffect.SwitchSource>,
    private val switchLibrarySource: Consumer<NowPlayingEffect.SwitchLibrarySource>,
    private val showMessage: Consumer<String>
) {
    fun handle(event: NowPlayingEvent?) {
        if (event == null) return
        viewModel.onEvent(event)
        viewModel.drainEffects().forEach(::dispatch)
    }

    internal fun dispatch(effect: NowPlayingEffect) {
        when (effect) {
            NowPlayingEffect.OpenQueue -> openQueue.run()
            is NowPlayingEffect.OpenAddToPlaylist -> openAddToPlaylist.accept(effect.track)
            is NowPlayingEffect.ShareTrack -> shareTrack.accept(effect.track)
            is NowPlayingEffect.DownloadTrack -> downloadTrack.accept(effect.track)
            is NowPlayingEffect.SwitchSource -> switchSource.accept(effect)
            is NowPlayingEffect.SwitchLibrarySource -> switchLibrarySource.accept(effect)
            is NowPlayingEffect.ShowMessage -> showMessage.accept(effect.message)
        }
    }
}
