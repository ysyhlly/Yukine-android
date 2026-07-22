package app.yukine.playback.manager

import java.util.function.LongSupplier

internal class PlaybackCrossfadeAdvanceManager @JvmOverloads constructor(
    private val scheduler: CallbackScheduler,
    private val stateProvider: StateProvider,
    private val actions: Actions,
    private val nowMs: LongSupplier = LongSupplier { System.currentTimeMillis() },
    private val fadeOutMs: Long = DEFAULT_FADE_OUT_MS,
    private val fadeStepMs: Long = DEFAULT_FADE_STEP_MS
) {
    interface CallbackScheduler {
        fun post(runnable: Runnable)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
    }

    interface StateProvider {
        fun fadeOutAdvancing(): Boolean
        fun playerAvailable(): Boolean
        fun isPlaying(): Boolean
        fun canCrossfadeAdvance(): Boolean
        fun baseVolume(): Float
    }

    interface Actions {
        fun setFadeOutAdvancing(enabled: Boolean)
        fun setPlayerVolume(volume: Float)
        fun skipToNextImmediately()
        fun applyAppVolume()
    }

    private var activeFadeRunnable: Runnable? = null
    private var released = false

    fun startFadeOutThenNext(): Boolean {
        if (released
            || stateProvider.fadeOutAdvancing()
            || !stateProvider.playerAvailable()
            || !stateProvider.isPlaying()
            || !stateProvider.canCrossfadeAdvance()
        ) {
            return false
        }
        actions.setFadeOutAdvancing(true)
        val baseVolume = stateProvider.baseVolume()
        val startedAtMs = nowMs.asLong
        val fadeRunnable = object : Runnable {
            override fun run() {
                if (released || activeFadeRunnable !== this) {
                    return
                }
                if (!stateProvider.playerAvailable()) {
                    finish(applyVolume = false)
                    return
                }
                if (!stateProvider.isPlaying()) {
                    finish(applyVolume = true)
                    return
                }
                val elapsedMs = nowMs.asLong - startedAtMs
                if (elapsedMs >= fadeOutMs) {
                    actions.skipToNextImmediately()
                    finish(applyVolume = true)
                    return
                }
                val fraction = 1.0f - maxOf(0f, minOf(1.0f, elapsedMs / fadeOutMs.toFloat()))
                try {
                    actions.setPlayerVolume(normalizedVolume(baseVolume * fraction))
                } catch (_: IllegalStateException) {
                    finish(applyVolume = false)
                    return
                }
                scheduler.postDelayed(this, fadeStepMs)
            }
        }
        activeFadeRunnable = fadeRunnable
        scheduler.post(fadeRunnable)
        return true
    }

    fun cancel() {
        cancel(restoreVolume = true)
    }

    private fun cancel(restoreVolume: Boolean) {
        val hadActiveFade = activeFadeRunnable != null
        cancelActiveRunnable()
        actions.setFadeOutAdvancing(false)
        if (restoreVolume && hadActiveFade) {
            actions.applyAppVolume()
        }
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        cancel(restoreVolume = false)
    }

    private fun finish(applyVolume: Boolean) {
        cancelActiveRunnable()
        actions.setFadeOutAdvancing(false)
        if (applyVolume) {
            actions.applyAppVolume()
        }
    }

    private fun normalizedVolume(volume: Float): Float {
        return maxOf(0.0f, minOf(1.0f, volume))
    }

    private fun cancelActiveRunnable() {
        activeFadeRunnable?.let { scheduler.removeCallbacks(it) }
        activeFadeRunnable = null
    }

    private companion object {
        const val DEFAULT_FADE_OUT_MS = 700L
        const val DEFAULT_FADE_STEP_MS = 70L
    }
}
