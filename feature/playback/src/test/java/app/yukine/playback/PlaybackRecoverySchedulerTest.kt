package app.yukine.playback

import app.yukine.playback.manager.PlaybackRecoveryScheduler
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoverySchedulerTest {
    @Test
    fun recoveryPostsPrepareToMainThread() {
        val background = FakeBackgroundScheduler()
        val main = FakeMainScheduler()
        val actions = FakeActions()
        val scheduler = PlaybackRecoveryScheduler(background, main, actions)

        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = true)
        background.tasks.single().run()
        main.tasks.single().run()

        assertEquals(listOf(true), actions.prepareCalls)
    }

    @Test
    fun releaseBeforeBackgroundTaskRunsPreventsPreparePost() {
        val background = FakeBackgroundScheduler()
        val main = FakeMainScheduler()
        val actions = FakeActions()
        val scheduler = PlaybackRecoveryScheduler(background, main, actions)

        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = true)
        scheduler.release()
        background.tasks.single().run()

        assertEquals(0, main.tasks.size)
        assertEquals(emptyList<Boolean>(), actions.prepareCalls)
    }

    @Test
    fun releaseBeforeMainTaskRunsRemovesAndSuppressesPrepare() {
        val background = FakeBackgroundScheduler()
        val main = FakeMainScheduler()
        val actions = FakeActions()
        val scheduler = PlaybackRecoveryScheduler(background, main, actions)

        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = false)
        background.tasks.single().run()
        val pending = main.tasks.single()
        scheduler.release()
        pending.run()

        assertEquals(listOf(pending), main.removed)
        assertEquals(emptyList<Boolean>(), actions.prepareCalls)
    }

    @Test
    fun cancelBeforeBackgroundTaskRunsPreventsPreparePost() {
        val background = FakeBackgroundScheduler()
        val main = FakeMainScheduler()
        val actions = FakeActions()
        val scheduler = PlaybackRecoveryScheduler(background, main, actions)

        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = true)
        scheduler.cancel()
        background.tasks.single().run()

        assertEquals(0, main.tasks.size)
        assertEquals(emptyList<Boolean>(), actions.prepareCalls)
    }

    @Test
    fun cancelBeforeMainTaskRunsRemovesAndSuppressesPrepare() {
        val background = FakeBackgroundScheduler()
        val main = FakeMainScheduler()
        val actions = FakeActions()
        val scheduler = PlaybackRecoveryScheduler(background, main, actions)

        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = false)
        background.tasks.single().run()
        val pending = main.tasks.single()
        scheduler.cancel()
        pending.run()

        assertEquals(listOf(pending), main.removed)
        assertEquals(emptyList<Boolean>(), actions.prepareCalls)
    }

    @Test
    fun cancelAllowsFutureRecoveryBeforeRelease() {
        val background = FakeBackgroundScheduler()
        val main = FakeMainScheduler()
        val actions = FakeActions()
        val scheduler = PlaybackRecoveryScheduler(background, main, actions)

        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = false)
        scheduler.cancel()
        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = true)
        background.tasks[0].run()
        background.tasks[1].run()
        main.tasks.single().run()

        assertEquals(listOf(true), actions.prepareCalls)
    }

    @Test
    fun releaseIsIdempotentAfterPendingMainTaskIsCancelled() {
        val background = FakeBackgroundScheduler()
        val main = FakeMainScheduler()
        val actions = FakeActions()
        val scheduler = PlaybackRecoveryScheduler(background, main, actions)

        scheduler.scheduleCurrentPlaybackRecovery(playWhenReady = true)
        background.tasks.single().run()
        val pending = main.tasks.single()
        scheduler.release()
        scheduler.release()

        assertEquals(listOf(pending), main.removed)
        assertEquals(emptyList<Boolean>(), actions.prepareCalls)
    }

    private class FakeBackgroundScheduler : PlaybackRecoveryScheduler.BackgroundScheduler {
        val tasks = mutableListOf<Runnable>()

        override fun schedule(task: Runnable) {
            tasks += task
        }
    }

    private class FakeMainScheduler : PlaybackRecoveryScheduler.MainScheduler {
        val tasks = mutableListOf<Runnable>()
        val removed = mutableListOf<Runnable>()

        override fun post(task: Runnable) {
            tasks += task
        }

        override fun removeCallbacks(task: Runnable) {
            removed += task
        }
    }

    private class FakeActions : PlaybackRecoveryScheduler.Actions {
        val prepareCalls = mutableListOf<Boolean>()

        override fun prepareCurrent(playWhenReady: Boolean) {
            prepareCalls += playWhenReady
        }
    }
}
