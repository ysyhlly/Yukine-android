package app.yukine.playback

import app.yukine.playback.manager.PlaybackProgressUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackProgressUpdateManagerTest {
    @Test
    fun startSchedulesTickOnlyWhenPlaybackIsActive() {
        val scheduler = FakeScheduler()
        val manager = PlaybackProgressUpdateManager(
            scheduler,
            FakeStateProvider(playing = true),
            FakeActions()
        )

        manager.startIfNeeded()

        assertEquals(1, scheduler.removeCalls)
        assertEquals(1000L, scheduler.lastDelayMs)
        assertNotNull(scheduler.pendingRunnable)
    }

    @Test
    fun startSkipsScheduleWhenPlaybackIsIdle() {
        val scheduler = FakeScheduler()
        val manager = PlaybackProgressUpdateManager(
            scheduler,
            FakeStateProvider(playing = false, preparing = false),
            FakeActions()
        )

        manager.startIfNeeded()

        assertEquals(1, scheduler.removeCalls)
        assertEquals(null, scheduler.pendingRunnable)
    }

    @Test
    fun tickPublishesPersistsAndReschedulesWhilePreparing() {
        val scheduler = FakeScheduler()
        val actions = FakeActions()
        val manager = PlaybackProgressUpdateManager(
            scheduler,
            FakeStateProvider(preparing = true),
            actions
        )

        manager.startIfNeeded()
        scheduler.runPending()

        assertEquals(listOf("publish", "persist"), actions.calls)
        assertEquals(1000L, scheduler.lastDelayMs)
        assertNotNull(scheduler.pendingRunnable)
    }

    @Test
    fun stopCancelsPendingTick() {
        val scheduler = FakeScheduler()
        val manager = PlaybackProgressUpdateManager(
            scheduler,
            FakeStateProvider(playing = true),
            FakeActions()
        )

        manager.startIfNeeded()
        manager.stop()

        assertEquals(null, scheduler.pendingRunnable)
    }

    private class FakeScheduler : PlaybackProgressUpdateManager.CallbackScheduler {
        var pendingRunnable: Runnable? = null
        var lastDelayMs: Long = -1L
        var removeCalls = 0

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            pendingRunnable = runnable
            lastDelayMs = delayMs
        }

        override fun removeCallbacks(runnable: Runnable) {
            removeCalls += 1
            if (pendingRunnable === runnable) {
                pendingRunnable = null
            }
        }

        fun runPending() {
            val runnable = pendingRunnable
            pendingRunnable = null
            runnable?.run()
        }
    }

    private class FakeStateProvider(
        private val playing: Boolean = false,
        private val preparing: Boolean = false
    ) : PlaybackProgressUpdateManager.StateProvider {
        override fun isPlaying(): Boolean = playing
        override fun isPreparing(): Boolean = preparing
    }

    private class FakeActions : PlaybackProgressUpdateManager.Actions {
        val calls = mutableListOf<String>()

        override fun publishState() {
            calls.add("publish")
        }

        override fun persistPlaybackPosition() {
            calls.add("persist")
        }
    }
}
