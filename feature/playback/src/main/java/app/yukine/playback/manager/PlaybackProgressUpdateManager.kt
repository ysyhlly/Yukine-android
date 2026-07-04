package app.yukine.playback.manager

import java.util.function.BooleanSupplier
import java.util.function.Consumer

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

    companion object {
        private const val DEFAULT_TICK_MS = 1000L

        @JvmStatic
        fun stateProviderFromPlaybackState(
            playbackStateProvider: BooleanSupplier?,
            preparingStateProvider: BooleanSupplier?
        ): StateProvider = object : StateProvider {
            override fun isPlaying(): Boolean = playbackStateProvider?.asBoolean == true

            override fun isPreparing(): Boolean = preparingStateProvider?.asBoolean == true
        }

        @JvmStatic
        fun actionsFromCallbacks(
            statePublisher: Runnable?,
            positionPersister: Consumer<Boolean>?
        ): Actions = object : Actions {
            override fun publishState() {
                statePublisher?.run()
            }

            override fun persistPlaybackPosition() {
                positionPersister?.accept(false)
            }
        }
    }

    private val progressRunnable = Runnable { onProgressTick() }
    private var released = false

    fun startIfNeeded() {
        if (released) {
            return
        }
        scheduler.removeCallbacks(progressRunnable)
        if (shouldRun()) {
            scheduler.postDelayed(progressRunnable, tickMs)
        }
    }

    fun stop() {
        scheduler.removeCallbacks(progressRunnable)
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        stop()
    }

    private fun onProgressTick() {
        if (released) {
            return
        }
        actions.publishState()
        actions.persistPlaybackPosition()
        if (!released && shouldRun()) {
            scheduler.postDelayed(progressRunnable, tickMs)
        }
    }

    private fun shouldRun(): Boolean = stateProvider.isPlaying() || stateProvider.isPreparing()
}
