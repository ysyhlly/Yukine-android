package app.yukine.playback

import android.content.BroadcastReceiver
import android.content.IntentFilter
import app.yukine.playback.manager.PlaybackNoisyReceiverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackNoisyReceiverManagerTest {
    @Test
    fun registerAndUnregisterAreIdempotent() {
        val registrar = FakeRegistrar()
        val manager = PlaybackNoisyReceiverManager(registrar, FakeActions())

        manager.register()
        manager.register()
        manager.unregister()
        manager.unregister()

        assertEquals(1, registrar.registerCalls)
        assertEquals(1, registrar.unregisterCalls)
        assertNotNull(registrar.receiver)
        assertNotNull(registrar.filter)
    }

    @Test
    fun unregisterClearsStateWhenPlatformAlreadyUnregisteredReceiver() {
        val registrar = FakeRegistrar(throwOnUnregister = true)
        val manager = PlaybackNoisyReceiverManager(registrar, FakeActions())

        manager.register()
        manager.unregister()
        manager.unregister()

        assertEquals(1, registrar.registerCalls)
        assertEquals(1, registrar.unregisterCalls)
    }

    @Test
    fun actionsFromPlaybackStatePausesOnlyWhenPlaybackIsActive() {
        var playingPauseCalls = 0
        PlaybackNoisyReceiverManager.actionsFromPlaybackState(
            { true },
            Runnable { playingPauseCalls += 1 }
        ).pauseIfPlaying()

        var stoppedPauseCalls = 0
        PlaybackNoisyReceiverManager.actionsFromPlaybackState(
            { false },
            Runnable { stoppedPauseCalls += 1 }
        ).pauseIfPlaying()

        assertEquals(1, playingPauseCalls)
        assertEquals(0, stoppedPauseCalls)
    }

    @Test
    fun actionsFromPlaybackStateIgnoresMissingDependencies() {
        var pauseCalls = 0
        PlaybackNoisyReceiverManager.actionsFromPlaybackState(
            null,
            Runnable { pauseCalls += 1 }
        ).pauseIfPlaying()
        PlaybackNoisyReceiverManager.actionsFromPlaybackState(
            { true },
            null
        ).pauseIfPlaying()

        assertEquals(0, pauseCalls)
    }

    private class FakeRegistrar(
        private val throwOnUnregister: Boolean = false
    ) : PlaybackNoisyReceiverManager.Registrar {
        var registerCalls = 0
        var unregisterCalls = 0
        var receiver: BroadcastReceiver? = null
        var filter: IntentFilter? = null

        override fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
            registerCalls += 1
            this.receiver = receiver
            this.filter = filter
        }

        override fun unregister(receiver: BroadcastReceiver) {
            unregisterCalls += 1
            if (throwOnUnregister) {
                throw IllegalArgumentException("already unregistered")
            }
        }
    }

    private class FakeActions : PlaybackNoisyReceiverManager.Actions {
        override fun pauseIfPlaying() = Unit
    }
}
