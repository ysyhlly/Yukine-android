package app.yukine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAuthCallbackBindingsTest {
    @Test
    fun rejectsInvalidCallbacksBeforeCallingViewModel() {
        val bindings = StreamingAuthCallbackBindings(
            StreamingViewModel(),
            FakeGateway()
        )

        assertFalse(bindings.handleAuthCallback("bad://callback", null))
        assertFalse(bindings.handleAuthCallback(null, "cookie=1"))
    }

    @Test
    fun manualCookieCallbackOpensManualImportInsteadOfCompletingLogin() {
        val gateway = FakeGateway()
        val bindings = StreamingAuthCallbackBindings(
            StreamingViewModel(),
            gateway
        )

        assertTrue(bindings.handleAuthCallback("echo-next://streaming-auth?provider=qqmusic&manualCookie=1", null))

        assertEquals(listOf(app.yukine.streaming.StreamingProviderName.QQ_MUSIC), gateway.manualCookieProviders)
        assertEquals(emptyList<app.yukine.streaming.StreamingProviderName>(), gateway.loginSuccessProviders)
    }

    private class FakeGateway : MainActivityStreamingActionGateway {
        val loginSuccessProviders = ArrayList<app.yukine.streaming.StreamingProviderName>()
        val manualCookieProviders = ArrayList<app.yukine.streaming.StreamingProviderName>()

        override fun streamingPlaybackQuality() = app.yukine.streaming.StreamingAudioQuality.LOSSLESS

        override fun languageMode(): String = AppLanguage.MODE_ENGLISH

        override fun openAuthLaunch(launch: MainActivityStreamingAuthLaunch?): Boolean = false

        override fun playResolvedTrack(track: app.yukine.model.Track) = Unit

        override fun onStreamingLoginSuccess(provider: app.yukine.streaming.StreamingProviderName) {
            loginSuccessProviders += provider
        }

        override fun openManualCookieImport(provider: app.yukine.streaming.StreamingProviderName) {
            manualCookieProviders += provider
        }
    }
}
