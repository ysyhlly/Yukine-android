package app.yukine.playback

import app.yukine.playback.manager.PlaybackSleepTimerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.function.LongSupplier

class PlaybackSleepTimerManagerTest {
    @Test
    fun startPublishesStateAndSchedulesNextMinuteTick() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val clock = MutableClock(1000L)
        val manager = PlaybackSleepTimerManager(scheduler, actions, clock)

        manager.startMinutes(30)

        assertEquals(30 * 60000L, manager.remainingMs())
        assertEquals(listOf("publish"), actions.calls)
        assertEquals(60000L, scheduler.lastDelayMs)
        assertTrue(scheduler.pendingRunnable != null)
    }

    @Test
    fun timerTickBeforeExpiryPublishesAndReschedules() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val clock = MutableClock(0L)
        val manager = PlaybackSleepTimerManager(scheduler, actions, clock)

        manager.startMinutes(2)
        actions.calls.clear()
        clock.now = 70000L
        scheduler.runPending()

        assertEquals(listOf("publish"), actions.calls)
        assertEquals(50000L, scheduler.lastDelayMs)
    }

    @Test
    fun timerTickAtExpiryPausesPlayback() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val clock = MutableClock(0L)
        val manager = PlaybackSleepTimerManager(scheduler, actions, clock)

        manager.startMinutes(1)
        actions.calls.clear()
        clock.now = 60000L
        scheduler.runPending()

        assertEquals(listOf("pause"), actions.calls)
        assertEquals(0L, manager.remainingMs())
    }

    @Test
    fun cancelClearsTimerAndOptionallyPublishes() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val manager = PlaybackSleepTimerManager(scheduler, actions, MutableClock())

        manager.startMinutes(15)
        actions.calls.clear()
        manager.cancel(publish = true)

        assertEquals(0L, manager.remainingMs())
        assertEquals(null, scheduler.pendingRunnable)
        assertEquals(listOf("publish"), actions.calls)
    }

    @Test
    fun cancelWithoutPublishClearsTimerSilently() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val manager = PlaybackSleepTimerManager(scheduler, actions, MutableClock())

        manager.startMinutes(15)
        actions.calls.clear()
        manager.cancel(publish = false)

        assertEquals(0L, manager.remainingMs())
        assertEquals(null, scheduler.pendingRunnable)
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun releasePreventsAlreadyDequeuedTickAndFutureStarts() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val clock = MutableClock(0L)
        val manager = PlaybackSleepTimerManager(scheduler, actions, clock)

        manager.startMinutes(1)
        val dequeuedTick = scheduler.takePending()
        actions.calls.clear()
        clock.now = 60000L
        manager.release()
        dequeuedTick?.run()
        manager.startMinutes(10)

        assertEquals(0L, manager.remainingMs())
        assertEquals(null, scheduler.pendingRunnable)
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun releaseIsIdempotentAfterPendingTickIsCancelled() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val manager = PlaybackSleepTimerManager(scheduler, actions, MutableClock())

        manager.startMinutes(1)
        actions.calls.clear()
        manager.release()
        val removeCountAfterFirstRelease = scheduler.removeCount
        manager.release()

        assertEquals(0L, manager.remainingMs())
        assertEquals(null, scheduler.pendingRunnable)
        assertEquals(removeCountAfterFirstRelease, scheduler.removeCount)
        assertEquals(emptyList<String>(), actions.calls)
    }

    private class MutableClock(var now: Long = 0L) : LongSupplier {
        override fun getAsLong(): Long = now
    }

    private class FakeScheduler : PlaybackSleepTimerManager.CallbackScheduler {
        var pendingRunnable: Runnable? = null
        var lastDelayMs: Long = -1L
        var removeCount = 0

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            pendingRunnable = runnable
            lastDelayMs = delayMs
        }

        override fun removeCallbacks(runnable: Runnable) {
            removeCount++
            if (pendingRunnable === runnable) {
                pendingRunnable = null
            }
        }

        fun runPending() {
            val runnable = pendingRunnable
            pendingRunnable = null
            runnable?.run()
        }

        fun takePending(): Runnable? {
            val runnable = pendingRunnable
            pendingRunnable = null
            return runnable
        }
    }

    private class FakeActions : PlaybackSleepTimerManager.Actions {
        val calls = mutableListOf<String>()

        override fun pausePlayback() {
            calls.add("pause")
        }

        override fun publishState() {
            calls.add("publish")
        }
    }
}
