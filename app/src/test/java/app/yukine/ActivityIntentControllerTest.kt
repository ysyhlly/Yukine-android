package app.yukine

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityIntentControllerTest {
    @Test
    fun initialIntentDelegatesStreamingAuthCallback() {
        val handler = RecordingAuthCallbackHandler(handled = true)
        val controller = ActivityIntentController(StreamingAuthCallbackController(handler))
        val intent = Intent()

        assertTrue(controller.handleInitialIntent(intent))
        assertEquals(listOf(null), handler.callbackUris)
        assertEquals(listOf(null), handler.cookieHeaders)
    }

    @Test
    fun newIntentDelegatesStreamingAuthCallback() {
        val handler = RecordingAuthCallbackHandler(handled = false)
        val controller = ActivityIntentController(StreamingAuthCallbackController(handler))

        assertFalse(controller.handleNewIntent(null))
        assertEquals(listOf(null), handler.callbackUris)
        assertEquals(listOf(null), handler.cookieHeaders)
    }

    private class RecordingAuthCallbackHandler(
        private val handled: Boolean
    ) : StreamingAuthCallbackHandler {
        val callbackUris = mutableListOf<String?>()
        val cookieHeaders = mutableListOf<String?>()

        override fun handleAuthCallback(callbackUri: String?, cookieHeader: String?): Boolean {
            callbackUris += callbackUri
            cookieHeaders += cookieHeader
            return handled
        }
    }
}
