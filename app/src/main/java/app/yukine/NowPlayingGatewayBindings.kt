package app.yukine

import app.yukine.model.Track

internal fun interface NowPlayingCurrentTrackProvider {
    fun currentTrack(): Track?
}

internal fun interface NowPlayingTrackAction {
    fun run(track: Track)
}

internal fun interface NowPlayingStatusMessageProvider {
    fun text(key: String): String
}

internal class NowPlayingGatewayBindings(
    private val playPauseAction: Runnable,
    private val nextAction: Runnable,
    private val previousAction: Runnable,
    private val seekAction: NowPlayingLongAction,
    private val currentTrackProvider: NowPlayingCurrentTrackProvider,
    private val favoriteAction: NowPlayingTrackAction,
    private val shuffleAction: Runnable,
    private val repeatAction: Runnable,
    private val statusMessageProvider: NowPlayingStatusMessageProvider
) : NowPlayingGateway {
    override fun playPause() {
        playPauseAction.run()
    }

    override fun next() {
        nextAction.run()
    }

    override fun previous() {
        previousAction.run()
    }

    override fun seekTo(positionMs: Long) {
        seekAction.run(positionMs)
    }

    override fun toggleFavorite() {
        val track = currentTrackProvider.currentTrack() ?: return
        favoriteAction.run(track)
    }

    override fun toggleShuffle() {
        shuffleAction.run()
    }

    override fun cycleRepeatMode() {
        repeatAction.run()
    }

    override fun statusMessage(key: String): String {
        return statusMessageProvider.text(key)
    }
}

internal fun interface NowPlayingLongAction {
    fun run(value: Long)
}
