package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal fun interface NowPlayingStoresReadySource {
    fun storesReady(): Boolean
}

internal fun interface NowPlayingPlaybackSnapshotSource {
    fun playbackSnapshot(): PlaybackStateSnapshot
}

internal fun interface NowPlayingFavoriteIdsSource {
    fun favoriteIds(): Set<Long>
}

internal fun interface NowPlayingLyricsStateSource {
    fun lyricsState(): LyricsState?
}

internal fun interface NowPlayingLanguageModeSource {
    fun languageMode(): String
}

internal fun interface NowPlayingQueueVisibilitySource {
    fun queueVisible(): Boolean
}

internal fun interface NowPlayingFloatingLyricsSink {
    fun publishFloatingLyrics(
        trackTitle: String,
        artist: String,
        coverUri: String?,
        playing: Boolean,
        activeLine: String
    )
}

internal fun interface NowPlayingQueueInputsSyncer {
    fun syncQueueInputs()
}

internal fun interface MainNowPlayingStateListenerFactory {
    fun create(
        storesReadySource: NowPlayingStoresReadySource,
        playbackSnapshotSource: NowPlayingPlaybackSnapshotSource,
        favoriteIdsSource: NowPlayingFavoriteIdsSource,
        lyricsStateSource: NowPlayingLyricsStateSource,
        languageModeSource: NowPlayingLanguageModeSource,
        queueVisibilitySource: NowPlayingQueueVisibilitySource,
        floatingLyricsSink: NowPlayingFloatingLyricsSink,
        queueInputsSyncer: NowPlayingQueueInputsSyncer
    ): NowPlayingStateController.Listener
}

internal class MainNowPlayingStateListener(
    private val storesReadySource: NowPlayingStoresReadySource,
    private val playbackSnapshotSource: NowPlayingPlaybackSnapshotSource,
    private val favoriteIdsSource: NowPlayingFavoriteIdsSource,
    private val lyricsStateSource: NowPlayingLyricsStateSource,
    private val languageModeSource: NowPlayingLanguageModeSource,
    private val queueVisibilitySource: NowPlayingQueueVisibilitySource,
    private val floatingLyricsSink: NowPlayingFloatingLyricsSink,
    private val queueInputsSyncer: NowPlayingQueueInputsSyncer
) : NowPlayingStateController.Listener {
    override fun storesReady(): Boolean =
        storesReadySource.storesReady()

    override fun playbackSnapshot(): PlaybackStateSnapshot =
        playbackSnapshotSource.playbackSnapshot()

    override fun favoriteIds(): Set<Long> =
        favoriteIdsSource.favoriteIds()

    override fun lyricsState(): LyricsState? =
        lyricsStateSource.lyricsState()

    override fun languageMode(): String =
        languageModeSource.languageMode()

    override fun queueVisible(): Boolean =
        queueVisibilitySource.queueVisible()

    override fun publishFloatingLyrics(state: NowPlayingUiState) {
        floatingLyricsSink.publishFloatingLyrics(
            state.trackTitle,
            state.artist,
            state.coverUri,
            state.isPlaying,
            activeLyricLine(state)
        )
    }

    override fun syncQueueInputs() {
        queueInputsSyncer.syncQueueInputs()
    }

    private fun activeLyricLine(state: NowPlayingUiState?): String {
        val lines = state?.lyrics?.lines.orEmpty()
        return lines.firstOrNull { it.active }?.text
            ?: lines.firstOrNull()?.text
            ?: ""
    }
}
