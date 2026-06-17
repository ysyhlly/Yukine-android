package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingAuthKind
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingActionGatewayBindingsTest {
    @Test
    fun delegatesStreamingActionGatewayCallsToBindings() {
        val calls = mutableListOf<String>()
        val launch = MainActivityStreamingAuthLaunch(
            StreamingProviderName.NETEASE,
            "https://music.163.com/login",
            StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE
        )
        val track = Track(
            11L,
            "Song",
            "Artist",
            "Album",
            240000L,
            Uri.parse("https://example.test/song.mp3"),
            "https://example.test/song.mp3"
        )
        var playedTracks: List<Track>? = null
        var playedIndex = -1
        var receivedLaunch: MainActivityStreamingAuthLaunch? = null
        val gateway = StreamingActionGatewayBindings(
            qualityProvider = StreamingPlaybackQualityProvider {
                calls += "quality"
                StreamingAudioQuality.LOSSLESS
            },
            languageModeProvider = StreamingLanguageModeProvider {
                calls += "language"
                "zh"
            },
            authLaunchAction = StreamingAuthLaunchAction { nextLaunch ->
                receivedLaunch = nextLaunch
                calls += "auth"
                true
            },
            trackListPlayAction = StreamingTrackListPlayAction { tracks, index ->
                playedTracks = tracks
                playedIndex = index
                calls += "play:${tracks.size}:$index:${tracks.first().title}"
            },
            loginSuccessAction = StreamingLoginSuccessAction { provider ->
                calls += "login:$provider"
            }
        )

        assertEquals(StreamingAudioQuality.LOSSLESS, gateway.streamingPlaybackQuality())
        assertEquals("zh", gateway.languageMode())
        assertTrue(gateway.openAuthLaunch(launch))
        gateway.playResolvedTrack(track)
        gateway.onStreamingLoginSuccess(StreamingProviderName.NETEASE)

        assertSame(launch, receivedLaunch)
        assertEquals(listOf(track), playedTracks)
        assertEquals(0, playedIndex)
        assertEquals(
            listOf(
                "quality",
                "language",
                "auth",
                "play:1:0:Song",
                "login:NETEASE"
            ),
            calls
        )
    }
}
