package app.yukine.playback

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.function.LongSupplier

class PlaybackCrossfadeAdvanceManagerTest {
    @Test
    fun fadeOutCompletesBySkippingNextAndRestoringVolume() {
        val scheduler = FakeScheduler()
        val state = FakeState()
        val actions = FakeActions(state)
        var now = 0L
        val manager = PlaybackCrossfadeAdvanceManager(
            scheduler,
            state,
            actions,
            LongSupplier { now },
            fadeOutMs = 700L,
            fadeStepMs = 70L
        )

        assertTrue(manager.startFadeOutThenNext())
        scheduler.posted.single().run()
        now = 700L
        scheduler.delayed.single().run()

        assertEquals(listOf(1.0f), actions.volumes)
        assertEquals(1, actions.skipNextCalls)
        assertEquals(1, actions.applyVolumeCalls)
        assertFalse(state.fadeOutAdvancing)
    }

    @Test
    fun releaseCancelsPendingFadeAndStopsFuturePlayerWrites() {
        val scheduler = FakeScheduler()
        val state = FakeState()
        val actions = FakeActions(state)
        val manager = PlaybackCrossfadeAdvanceManager(
            scheduler,
            state,
            actions,
            LongSupplier { 0L }
        )

        assertTrue(manager.startFadeOutThenNext())
        val pending = scheduler.posted.single()
        manager.release()
        pending.run()

        assertEquals(listOf(pending), scheduler.removed)
        assertEquals(emptyList<Float>(), actions.volumes)
        assertEquals(0, actions.skipNextCalls)
        assertFalse(state.fadeOutAdvancing)
    }

    @Test
    fun releaseIsIdempotentAfterPendingFadeIsCancelled() {
        val scheduler = FakeScheduler()
        val state = FakeState()
        val actions = FakeActions(state)
        val manager = PlaybackCrossfadeAdvanceManager(
            scheduler,
            state,
            actions,
            LongSupplier { 0L }
        )

        assertTrue(manager.startFadeOutThenNext())
        val pending = scheduler.posted.single()
        manager.release()
        val removedAfterFirstRelease = scheduler.removed.toList()
        val fadeStateCallsAfterFirstRelease = actions.fadeOutAdvancingCalls
        manager.release()
        pending.run()

        assertEquals(listOf(pending), removedAfterFirstRelease)
        assertEquals(removedAfterFirstRelease, scheduler.removed)
        assertEquals(fadeStateCallsAfterFirstRelease, actions.fadeOutAdvancingCalls)
        assertEquals(emptyList<Float>(), actions.volumes)
        assertEquals(0, actions.skipNextCalls)
        assertFalse(state.fadeOutAdvancing)
    }

    @Test
    fun repeatOffAtQueueEndSkipsFadeOutAdvance() {
        val scheduler = FakeScheduler()
        val state = FakeState().apply {
            canCrossfadeAdvance = false
        }
        val actions = FakeActions(state)
        val manager = PlaybackCrossfadeAdvanceManager(
            scheduler,
            state,
            actions,
            LongSupplier { 0L }
        )

        assertFalse(manager.startFadeOutThenNext())

        assertEquals(emptyList<Runnable>(), scheduler.posted)
        assertEquals(0, actions.skipNextCalls)
        assertFalse(state.fadeOutAdvancing)
    }

    @Test
    fun fadeOutVolumeIsClampedByOwner() {
        val scheduler = FakeScheduler()
        val state = FakeState().apply {
            baseVolume = 1.4f
        }
        val actions = FakeActions(state)
        val manager = PlaybackCrossfadeAdvanceManager(
            scheduler,
            state,
            actions,
            LongSupplier { 0L }
        )

        assertTrue(manager.startFadeOutThenNext())
        scheduler.posted.single().run()

        assertEquals(listOf(1.0f), actions.volumes)
    }

    private class FakeScheduler : PlaybackCrossfadeAdvanceManager.CallbackScheduler {
        val posted = mutableListOf<Runnable>()
        val delayed = mutableListOf<Runnable>()
        val removed = mutableListOf<Runnable>()

        override fun post(runnable: Runnable) {
            posted += runnable
        }

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            delayed += runnable
        }

        override fun removeCallbacks(runnable: Runnable) {
            removed += runnable
        }
    }

    private class FakeState : PlaybackCrossfadeAdvanceManager.StateProvider {
        var fadeOutAdvancing = false
        var playerAvailable = true
        var playing = true
        var canCrossfadeAdvance = true
        var baseVolume = 1.0f

        override fun fadeOutAdvancing(): Boolean = fadeOutAdvancing

        override fun playerAvailable(): Boolean = playerAvailable

        override fun isPlaying(): Boolean = playing

        override fun canCrossfadeAdvance(): Boolean = canCrossfadeAdvance

        override fun baseVolume(): Float = baseVolume
    }

    private class FakeActions(
        private val state: FakeState
    ) : PlaybackCrossfadeAdvanceManager.Actions {
        val volumes = mutableListOf<Float>()
        var skipNextCalls = 0
        var applyVolumeCalls = 0
        var fadeOutAdvancingCalls = 0

        override fun setFadeOutAdvancing(enabled: Boolean) {
            fadeOutAdvancingCalls++
            state.fadeOutAdvancing = enabled
        }

        override fun setPlayerVolume(volume: Float) {
            volumes += volume
        }

        override fun skipToNextImmediately() {
            skipNextCalls++
        }

        override fun applyAppVolume() {
            applyVolumeCalls++
        }
    }
}
