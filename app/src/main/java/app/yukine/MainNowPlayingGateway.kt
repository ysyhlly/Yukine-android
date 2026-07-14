package app.yukine

import app.yukine.model.Track

internal fun interface PlaybackActionControllerProvider {
    fun controller(): PlaybackActionController?
}

internal fun interface MainPlaybackSnapshotSource {
    fun snapshot(): app.yukine.playback.PlaybackStateSnapshot?
}

internal fun interface NowPlayingFavoriteToggler {
    fun toggleFavorite(track: Track)
}

internal fun interface NowPlayingSeekHandler {
    fun seekTo(positionMs: Long)
}

internal fun interface NowPlayingStatusTextProvider {
    fun text(key: String): String
}

internal class MainNowPlayingGateway(
    private val playbackActionControllerProvider: PlaybackActionControllerProvider,
    private val playbackSnapshotSource: MainPlaybackSnapshotSource,
    private val favoriteToggler: NowPlayingFavoriteToggler,
    private val seekHandler: NowPlayingSeekHandler,
    private val statusTextProvider: NowPlayingStatusTextProvider
) : NowPlayingGateway {
    override fun playPause() {
        playbackActionControllerProvider.controller()?.togglePlayback()
    }

    override fun next() {
        playbackActionControllerProvider.controller()?.skipToNext()
    }

    override fun previous() {
        playbackActionControllerProvider.controller()?.skipToPrevious()
    }

    override fun seekTo(positionMs: Long) {
        seekHandler.seekTo(positionMs)
    }

    override fun toggleFavorite() {
        val track = playbackSnapshotSource.snapshot()?.currentTrack ?: return
        favoriteToggler.toggleFavorite(track)
    }

    override fun toggleShuffle() {
        playbackActionControllerProvider.controller()?.toggleShuffle()
    }

    override fun cycleRepeatMode() {
        playbackActionControllerProvider.controller()?.cycleRepeat()
    }

    override fun statusMessage(key: String): String = statusTextProvider.text(key)
}
