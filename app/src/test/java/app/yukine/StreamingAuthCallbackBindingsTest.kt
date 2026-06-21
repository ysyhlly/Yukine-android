package app.yukine

import org.junit.Assert.assertFalse
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

    private class FakeGateway : MainActivityStreamingActionGateway {
        override fun streamingPlaybackQuality() = app.yukine.streaming.StreamingAudioQuality.LOSSLESS

        override fun languageMode(): String = AppLanguage.MODE_ENGLISH

        override fun openAuthLaunch(launch: MainActivityStreamingAuthLaunch?): Boolean = false

        override fun playResolvedTrack(track: app.yukine.model.Track) = Unit

        override fun onStreamingLoginSuccess(provider: app.yukine.streaming.StreamingProviderName) = Unit
    }
}
