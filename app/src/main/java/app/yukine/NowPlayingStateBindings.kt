package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal fun interface NowPlayingStoresReadyProvider {
    fun ready(): Boolean
}

internal fun interface NowPlayingSnapshotProvider {
    fun snapshot(): PlaybackStateSnapshot
}

internal fun interface NowPlayingFavoriteIdsProvider {
    fun favoriteIds(): Set<Long>
}

internal fun interface NowPlayingLyricsProvider {
    fun lyricsState(): LyricsState?
}

internal fun interface NowPlayingLanguageProvider {
    fun languageMode(): String
}

internal fun interface NowPlayingFloatingLyricsPublisher {
    fun publish(state: NowPlayingUiState)
}

internal fun interface NowPlayingQueueInputSynchronizer {
    fun sync()
}

internal class NowPlayingStateBindings(
    private val storesReadyProvider: NowPlayingStoresReadyProvider,
    private val snapshotProvider: NowPlayingSnapshotProvider,
    private val favoriteIdsProvider: NowPlayingFavoriteIdsProvider,
    private val lyricsProvider: NowPlayingLyricsProvider,
    private val languageProvider: NowPlayingLanguageProvider,
    private val floatingLyricsPublisher: NowPlayingFloatingLyricsPublisher,
    private val queueInputSynchronizer: NowPlayingQueueInputSynchronizer
) : NowPlayingStateController.Listener {
    override fun storesReady(): Boolean = storesReadyProvider.ready()

    override fun playbackSnapshot(): PlaybackStateSnapshot = snapshotProvider.snapshot()

    override fun favoriteIds(): Set<Long> = favoriteIdsProvider.favoriteIds()

    override fun lyricsState(): LyricsState? = lyricsProvider.lyricsState()

    override fun languageMode(): String = languageProvider.languageMode()

    override fun publishFloatingLyrics(state: NowPlayingUiState) {
        floatingLyricsPublisher.publish(state)
    }

    override fun syncQueueInputs() {
        queueInputSynchronizer.sync()
    }
}
