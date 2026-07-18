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
    private val importLyrics: Consumer<Track>,
    private val clearLyrics: Consumer<Track>,
    private val switchSource: Consumer<NowPlayingEffect.SwitchSource>,
    private val switchLibrarySource: Consumer<NowPlayingEffect.SwitchLibrarySource>,
    private val showMessage: Consumer<String>
) {
    constructor(
        viewModel: NowPlayingViewModel,
        openQueue: Runnable,
        openAddToPlaylist: Consumer<Track>,
        shareTrack: Consumer<Track>,
        downloadTrack: Consumer<Track>,
        switchSource: Consumer<NowPlayingEffect.SwitchSource>,
        switchLibrarySource: Consumer<NowPlayingEffect.SwitchLibrarySource>,
        showMessage: Consumer<String>
    ) : this(
        viewModel,
        openQueue,
        openAddToPlaylist,
        shareTrack,
        downloadTrack,
        Consumer {},
        Consumer {},
        switchSource,
        switchLibrarySource,
        showMessage
    )

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
            is NowPlayingEffect.ImportLyrics -> importLyrics.accept(effect.track)
            is NowPlayingEffect.ClearLyrics -> clearLyrics.accept(effect.track)
            is NowPlayingEffect.SwitchSource -> switchSource.accept(effect)
            is NowPlayingEffect.SwitchLibrarySource -> switchLibrarySource.accept(effect)
            is NowPlayingEffect.ShowMessage -> showMessage.accept(effect.message)
        }
    }
}
