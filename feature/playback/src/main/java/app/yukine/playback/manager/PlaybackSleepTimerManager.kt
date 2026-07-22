package app.yukine.playback.manager

import java.util.function.LongSupplier

internal class PlaybackSleepTimerManager @JvmOverloads constructor(
    private val scheduler: CallbackScheduler,
    private val actions: Actions,
    private val nowMs: LongSupplier = LongSupplier { System.currentTimeMillis() },
    private val maxMinutes: Int = DEFAULT_MAX_MINUTES,
    private val tickMs: Long = DEFAULT_TICK_MS
) {
    interface CallbackScheduler {
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
    }

    interface Actions {
        fun pausePlayback()
        fun publishState()
    }

    private var endsAtMs = 0L
    private val timerRunnable = Runnable { onTimerTick() }
    private var released = false

    fun startMinutes(minutes: Int) {
        if (released) {
            return
        }
        if (minutes <= 0) {
            cancel(publish = true)
            return
        }
        endsAtMs = nowMs.asLong + minOf(minutes, maxMinutes) * 60000L
        scheduleNextTick()
        actions.publishState()
    }

    fun cancel(publish: Boolean) {
        endsAtMs = 0L
        scheduler.removeCallbacks(timerRunnable)
        if (publish && !released) {
            actions.publishState()
        }
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        cancel(publish = false)
    }

    fun remainingMs(): Long {
        if (endsAtMs <= 0L) {
            return 0L
        }
        return maxOf(0L, endsAtMs - nowMs.asLong)
    }

    private fun onTimerTick() {
        if (released || endsAtMs <= 0L) {
            return
        }
        val remainingMs = remainingMs()
        if (remainingMs <= 0L) {
            endsAtMs = 0L
            actions.pausePlayback()
            actions.publishState()
            return
        }
        actions.publishState()
        scheduler.postDelayed(timerRunnable, minOf(remainingMs, tickMs))
    }

    private fun scheduleNextTick() {
        if (released) {
            return
        }
        scheduler.removeCallbacks(timerRunnable)
        val remainingMs = remainingMs()
        if (remainingMs > 0L) {
            scheduler.postDelayed(timerRunnable, minOf(remainingMs, tickMs))
        }
    }

    private companion object {
        const val DEFAULT_MAX_MINUTES = 240
        const val DEFAULT_TICK_MS = 60000L
    }
}
