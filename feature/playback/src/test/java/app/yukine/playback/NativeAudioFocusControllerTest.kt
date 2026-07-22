package app.yukine.playback

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import app.yukine.playback.manager.NativeAudioFocusController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class NativeAudioFocusControllerTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var handler: Handler
    private lateinit var callback: RecordingCallback
    private lateinit var controller: NativeAudioFocusController

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        handler = Handler(Looper.getMainLooper())
        callback = RecordingCallback()
        controller = NativeAudioFocusController(context, handler)
        controller.callback = callback
    }

    @Test
    fun acquireRequestsAudioFocus() {
        val shadow = shadowOf(audioManager)
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        val granted = controller.acquire()

        assertTrue(granted)
        assertEquals(NativeAudioFocusController.FocusState.HELD, controller.focusState)
    }

    @Test
    fun releaseAbandonsFocusAndResetsState() {
        controller.acquire()
        controller.release()

        assertEquals(NativeAudioFocusController.FocusState.IDLE, controller.focusState)
    }

    @Test
    fun exclusiveModeCounteractsOnFocusLoss() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        val shadow = shadowOf(audioManager)
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        controller.acquire()

        // Make the counteraction re-acquire fail so state stays RECLAIMING
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        // Simulate focus loss
        simulateFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper()

        // Should have attempted counteraction (legacy broadcast sent)
        assertEquals(NativeAudioFocusController.FocusState.RECLAIMING, controller.focusState)
    }

    @Test
    fun cooperativeModeDoesNotRequestAudioFocus() {
        controller.setMode(NativeAudioFocusController.Mode.COOPERATIVE)

        val granted = controller.acquire()

        assertTrue(granted)
        assertEquals(NativeAudioFocusController.FocusState.HELD, controller.focusState)
        // No actual system focus request should have been made
        assertEquals(null, shadowOf(audioManager).lastAudioFocusRequest)
    }

    @Test
    fun cooperativeModeReleaseIsNoOp() {
        controller.setMode(NativeAudioFocusController.Mode.COOPERATIVE)
        controller.acquire()

        controller.release()

        assertEquals(NativeAudioFocusController.FocusState.IDLE, controller.focusState)
        // No abandon should have been called (no focus was ever held)
        assertEquals(null, shadowOf(audioManager).lastAudioFocusRequest)
    }

    @Test
    fun switchFromExclusiveToCooperativeAbandonsFocus() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        controller.acquire()
        assertEquals(NativeAudioFocusController.FocusState.HELD, controller.focusState)

        // Switch to cooperative — should abandon system focus
        controller.setMode(NativeAudioFocusController.Mode.COOPERATIVE)

        assertEquals(NativeAudioFocusController.FocusState.IDLE, controller.focusState)
        assertFalse(controller.isExclusiveMode())
    }

    @Test
    fun exclusiveModeIgnoresDucking() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        controller.acquire()

        simulateFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        ShadowLooper.idleMainLooper()

        // No callback for ducking in exclusive mode
        assertEquals(0, callback.focusLostTransientlyCalls)
        assertEquals(0, callback.focusLostPermanentlyCalls)
    }

    @Test
    fun focusGainNotifiesCallback() {
        controller.acquire()

        simulateFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        ShadowLooper.idleMainLooper()

        assertEquals(1, callback.focusGainedCalls)
        assertEquals(NativeAudioFocusController.FocusState.HELD, controller.focusState)
    }

    @Test
    fun modeSwitchUpdatesExclusiveFlag() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        assertTrue(controller.isExclusiveMode())

        controller.setMode(NativeAudioFocusController.Mode.COOPERATIVE)
        assertFalse(controller.isExclusiveMode())
    }

    @Test
    fun destroyReleasesResourcesAndClearsCallback() {
        controller.acquire()
        controller.destroy()

        assertEquals(NativeAudioFocusController.FocusState.IDLE, controller.focusState)
        // Callback cleared - no NPE on subsequent events
    }

    @Test
    fun markUserVolumeChangeOpensProtectWindow() {
        assertFalse(controller.isInVolumeProtectWindow())

        controller.markUserVolumeChange()

        assertTrue(controller.isInVolumeProtectWindow())
    }

    @Test
    fun focusStateChangesNotified() {
        val states = mutableListOf<NativeAudioFocusController.FocusState>()
        controller.onFocusStateChanged = { states.add(it) }

        controller.acquire()
        simulateFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        ShadowLooper.idleMainLooper()

        assertTrue(states.contains(NativeAudioFocusController.FocusState.HELD))
    }

    @Test
    fun debouncedFocusLossSchedulesDeferredCounteraction() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        controller.acquire()

        // First focus loss — triggers immediate counteraction (acquire fails by default in shadow)
        simulateFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper()

        // Second focus loss within debounce window — should NOT be dropped
        simulateFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper()

        // Set focus to be granted before deferred action fires
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        // Advance past debounce window — deferred counteraction fires and succeeds
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        // Deferred counteraction should have triggered a play request
        assertTrue(callback.playRequestedCalls >= 1)
        assertEquals(NativeAudioFocusController.FocusState.HELD, controller.focusState)
    }

    @Test
    fun deferredCounteractionCancelledOnRelease() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        controller.acquire()

        // First focus loss — immediate counteraction
        simulateFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper()

        // Second focus loss within debounce — schedules deferred
        simulateFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper()

        // User pauses — release cancels deferred counteraction
        controller.release()
        val callsBefore = callback.playRequestedCalls

        // Advance past debounce window
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        // No additional play requests after release
        assertEquals(callsBefore, callback.playRequestedCalls)
    }

    @Test
    fun releaseKeepsUnrelatedHandlerCallbacks() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        controller.acquire()
        var unrelatedCallbackRan = false
        handler.postDelayed({ unrelatedCallbackRan = true }, 100L)

        controller.release()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100L))

        assertTrue(unrelatedCallbackRan)
    }

    @Test
    fun counteractSkippedWhenStateIdle() {
        controller.setMode(NativeAudioFocusController.Mode.EXCLUSIVE)
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        controller.acquire()

        // First focus loss — triggers counteraction
        simulateFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper()
        val callsAfterFirst = callback.playRequestedCalls

        // Release (user pause) — state becomes IDLE
        controller.release()
        assertEquals(NativeAudioFocusController.FocusState.IDLE, controller.focusState)

        // Advance time — any pending deferred action should be no-op due to IDLE guard
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

        assertEquals(callsAfterFirst, callback.playRequestedCalls)
    }

    @Test
    fun acquireUpdatesStateToHeldOnGrant() {
        val shadow = shadowOf(audioManager)
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        controller.acquire()

        assertEquals(NativeAudioFocusController.FocusState.HELD, controller.focusState)
    }

    private fun simulateFocusChange(focusChange: Int) {
        // Use Robolectric to simulate audio focus change callback
        val shadow = shadowOf(audioManager)
        shadow.getLastAudioFocusRequest()?.listener?.onAudioFocusChange(focusChange)
    }

    private class RecordingCallback : NativeAudioFocusController.Callback {
        var focusGainedCalls = 0
        var focusLostPermanentlyCalls = 0
        var focusLostTransientlyCalls = 0
        var playRequestedCalls = 0

        override fun onFocusGained() { focusGainedCalls++ }
        override fun onFocusLostPermanently() { focusLostPermanentlyCalls++ }
        override fun onFocusLostTransiently() { focusLostTransientlyCalls++ }
        override fun onPlayRequested() { playRequestedCalls++ }
    }
}
