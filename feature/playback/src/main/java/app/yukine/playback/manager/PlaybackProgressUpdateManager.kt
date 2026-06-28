package app.yukine.playback.manager

internal class PlaybackProgressUpdateManager @JvmOverloads constructor(
    private val scheduler: CallbackScheduler,
    private val stateProvider: StateProvider,
    private val actions: Actions,
    private val tickMs: Long = DEFAULT_TICK_MS
) {
    interface CallbackScheduler {
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
    }

    interface StateProvider {
        fun isPlaying(): Boolean
        fun isPreparing(): Boolean
    }

    interface Actions {
        fun publishState()
        fun persistPlaybackPosition()
    }

    private val progressRunnable = Runnable { onProgressTick() }

    fun startIfNeeded() {
        scheduler.removeCallbacks(progressRunnable)
        if (shouldRun()) {
            scheduler.postDelayed(progressRunnable, tickMs)
        }
    }

    fun stop() {
        scheduler.removeCallbacks(progressRunnable)
    }

    private fun onProgressTick() {
        actions.publishState()
        actions.persistPlaybackPosition()
        if (shouldRun()) {
            scheduler.postDelayed(progressRunnable, tickMs)
        }
    }

    private fun shouldRun(): Boolean = stateProvider.isPlaying() || stateProvider.isPreparing()

    private companion object {
        const val DEFAULT_TICK_MS = 1000L
    }
}
