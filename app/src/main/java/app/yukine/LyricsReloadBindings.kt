package app.yukine

import app.yukine.model.Track

internal fun interface CurrentTrackProvider {
    fun currentTrack(): Track?
}

internal fun interface LyricsProviderTrackIdProvider {
    fun providerTrackId(track: Track?): String
}

internal fun interface LyricsReloadLoader {
    fun load(track: Track?, providerTrackId: String)
}

internal fun interface LyricsStatusTextProvider {
    fun text(key: String): String
}

internal class LyricsReloadBindings(
    private val currentTrackProvider: CurrentTrackProvider,
    private val providerTrackIdProvider: LyricsProviderTrackIdProvider,
    private val lyricsReloadLoader: LyricsReloadLoader,
    private val statusTextProvider: LyricsStatusTextProvider,
    private val statusSink: QueueStatusSink
) : LyricsReloadController.Listener {
    override fun currentTrack(): Track? = currentTrackProvider.currentTrack()

    override fun providerTrackId(track: Track?): String = providerTrackIdProvider.providerTrackId(track)

    override fun loadLyrics(track: Track?, providerTrackId: String) {
        lyricsReloadLoader.load(track, providerTrackId)
    }

    override fun noTrackSelectedStatus(): String = statusTextProvider.text("no.track.selected")

    override fun reloadingLyricsStatus(): String = statusTextProvider.text("reloading.lyrics")

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
