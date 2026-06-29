package app.yukine

import app.yukine.model.Track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingEventControllersTest {
    @Test
    fun authCallbackControllerDelegatesInitialAndNewIntents() {
        val streamingViewModel = StreamingViewModel()
        val gateway = FakeGateway()
        val controller = StreamingAuthCallbackController(streamingViewModel, gateway)

        assertFalse(controller.handleInitialIntent(null))
        assertFalse(controller.handleNewIntent(null))
        assertFalse(controller.handleInitialIntent(fakeIntent("bad://callback")))
        assertTrue(
            controller.handleInitialIntent(
                fakeIntent("echo-next://streaming-auth?provider=qqmusic&manualCookie=1")
            )
        )

        assertEquals(
            listOf(app.yukine.streaming.StreamingProviderName.QQ_MUSIC),
            gateway.manualCookieProviders
        )
    }

    private class FakeGateway : MainActivityStreamingActionGateway {
        val loginSuccessProviders = ArrayList<app.yukine.streaming.StreamingProviderName>()
        val manualCookieProviders = ArrayList<app.yukine.streaming.StreamingProviderName>()

        override fun streamingPlaybackQuality() = app.yukine.streaming.StreamingAudioQuality.LOSSLESS

        override fun languageMode(): String = AppLanguage.MODE_ENGLISH

        override fun openAuthLaunch(launch: StreamingSearchAuthLaunch?): Boolean = false

        override fun playResolvedTrack(track: Track) = Unit

        override fun onStreamingLoginSuccess(provider: app.yukine.streaming.StreamingProviderName) {
            loginSuccessProviders += provider
        }

        override fun openManualCookieImport(provider: app.yukine.streaming.StreamingProviderName) {
            manualCookieProviders += provider
        }
    }

    private fun fakeIntent(data: String): android.content.Intent =
        android.content.Intent().apply { this.data = android.net.Uri.parse(data) }
}
