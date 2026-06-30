package app.yukine.playback.manager

internal class PlaybackRecoveryScheduler(
    private val backgroundScheduler: BackgroundScheduler,
    private val mainScheduler: MainScheduler,
    private val actions: Actions
) {
    fun interface BackgroundScheduler {
        fun schedule(task: Runnable)
    }

    interface MainScheduler {
        fun post(task: Runnable)
        fun removeCallbacks(task: Runnable)
    }

    fun interface Actions {
        fun prepareCurrent(playWhenReady: Boolean)
    }

    private var generation = 0
    private var pendingMainTask: Runnable? = null
    private var released = false

    fun scheduleCurrentPlaybackRecovery(playWhenReady: Boolean) {
        if (released) {
            return
        }
        val taskGeneration = ++generation
        backgroundScheduler.schedule(Runnable {
            if (released || taskGeneration != generation) {
                return@Runnable
            }
            val mainTask = Runnable {
                if (released || taskGeneration != generation) {
                    return@Runnable
                }
                pendingMainTask = null
                actions.prepareCurrent(playWhenReady)
            }
            pendingMainTask = mainTask
            mainScheduler.post(mainTask)
        })
    }

    fun cancel() {
        generation++
        pendingMainTask?.let { mainScheduler.removeCallbacks(it) }
        pendingMainTask = null
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        cancel()
    }
}
