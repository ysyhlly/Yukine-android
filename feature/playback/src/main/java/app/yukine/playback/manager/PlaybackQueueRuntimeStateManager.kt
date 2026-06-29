package app.yukine.playback.manager

internal class PlaybackQueueRuntimeStateManager {
    private var playerMirrorsQueue = false

    fun playerMirrorsQueue(): Boolean {
        return playerMirrorsQueue
    }

    fun setPlayerMirrorsQueue(enabled: Boolean) {
        playerMirrorsQueue = enabled
    }
}
