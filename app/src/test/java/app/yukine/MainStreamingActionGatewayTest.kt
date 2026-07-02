package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStreamingActionGatewayTest {
    @Test
    fun qualityLanguageAndAuthDelegateToHostCapabilities() {
        val events = mutableListOf<String>()
        val launch = StreamingSearchAuthLaunch(
            StreamingProviderName.NETEASE,
            "https://login.example",
            StreamingAuthKind.REMOTE_GATEWAY
        )
        val gateway = gateway(events, authLaunchResult = true)

        assertEquals(StreamingAudioQuality.HIRES, gateway.streamingPlaybackQuality())
        assertEquals(AppLanguage.MODE_ENGLISH, gateway.languageMode())
        assertTrue(gateway.openAuthLaunch(launch))

        assertEquals(
            listOf(
                "quality",
                "language",
                "auth:https://login.example"
            ),
            events
        )
    }

    @Test
    fun resolvedTrackPlaysSingleTrackThroughHostPlaybackPath() {
        val events = mutableListOf<String>()
        val gateway = gateway(events)

        gateway.playResolvedTrack(track(9L))

        assertEquals(listOf("play:9:0"), events)
    }

    @Test
    fun loginAndManualCookieActionsDelegateInOrder() {
        val events = mutableListOf<String>()
        val gateway = gateway(events)

        gateway.onStreamingLoginSuccess(StreamingProviderName.QQ_MUSIC)
        gateway.openManualCookieImport(StreamingProviderName.NETEASE)

        assertEquals(
            listOf(
                "login:qqmusic",
                "select:netease",
                "manualCookie"
            ),
            events
        )
    }

    private fun gateway(
        events: MutableList<String>,
        authLaunchResult: Boolean = false
    ): MainActivityStreamingActionGateway {
        return MainStreamingActionGateway(
            qualityProvider = {
                events += "quality"
                StreamingAudioQuality.HIRES
            },
            languageModeProvider = {
                events += "language"
                AppLanguage.MODE_ENGLISH
            },
            authLauncher = { launch ->
                events += "auth:${launch?.launchUrl.orEmpty()}"
                authLaunchResult
            },
            trackPlayer = { tracks, index -> events += "play:${tracks[index].id}:$index" },
            loginSuccessHandler = { provider -> events += "login:${provider.wireName}" },
            providerSelector = { provider -> events += "select:${provider.wireName}" },
            manualCookiePresenter = { events += "manualCookie" }
        )
    }

    private fun track(id: Long): Track {
        return Track(
            id,
            "Track $id",
            "Artist",
            "Album",
            1000L,
            Uri.EMPTY,
            "/music/$id.mp3"
        )
    }
}
