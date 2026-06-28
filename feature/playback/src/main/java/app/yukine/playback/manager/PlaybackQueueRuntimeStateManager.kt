package app.yukine.playback.manager

internal class PlaybackQueueRuntimeStateManager {
    private var playerMirrorsQueue = false
    private var currentIndex = -1

    fun playerMirrorsQueue(): Boolean {
        return playerMirrorsQueue
    }

    fun setPlayerMirrorsQueue(enabled: Boolean) {
        playerMirrorsQueue = enabled
    }

    fun currentIndex(): Int {
        return currentIndex
    }

    fun setCurrentIndex(index: Int) {
        currentIndex = index
    }

    fun clampCurrentIndex(queueSize: Int): Int {
        if (queueSize <= 0) {
            return 0
        }
        return maxOf(0, minOf(currentIndex, queueSize - 1))
    }

    fun setClampedCurrentIndex(index: Int, queueSize: Int) {
        currentIndex = if (queueSize <= 0) {
            -1
        } else {
            maxOf(0, minOf(index, queueSize - 1))
        }
    }
}
