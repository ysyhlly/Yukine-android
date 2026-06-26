package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingEventControllersTest {
    @Test
    fun searchEventControllerForwardsActionsToHandler() {
        val handler = FakeSearchActionHandler()
        val navigator = FakeNavigator()
        val controller = StreamingSearchEventController(handler, navigator, FakeContentSink())
        val streamingTrack = StreamingTrack(
            provider = StreamingProviderName.SPOTIFY,
            providerTrackId = "spotify-1",
            title = "Track",
            artist = "Artist"
        )
        val resolvedTrack = track(42L)

        controller.backToNetworkHome()
        controller.selectProvider(StreamingProviderName.SPOTIFY)
        controller.search("query")
        controller.login(StreamingProviderName.SPOTIFY)
        controller.signOut(StreamingProviderName.SPOTIFY)
        controller.openAuthLaunch()
        controller.playStreamingTrack(streamingTrack)
        controller.playResolvedTrack(resolvedTrack)
        controller.loadNextPage()

        assertEquals(1, navigator.backCalls)
        assertEquals(
            listOf(
                "select:SPOTIFY",
                "search:query",
                "login:SPOTIFY",
                "signOut:SPOTIFY",
                "openAuth",
                "playStreaming:spotify-1",
                "playResolved:42",
                "nextPage"
            ),
            handler.calls
        )
    }

    @Test
    fun searchEventControllerForwardsImportActionsToNavigator() {
        val handler = FakeSearchActionHandler()
        val navigator = FakeNavigator()
        val controller = StreamingSearchEventController(handler, navigator, FakeContentSink())
        val playlist = app.yukine.streaming.StreamingPlaylist(
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "pl-1",
            title = "My Playlist"
        )

        controller.importStreamingPlaylist(playlist)
        controller.loadUserPlaylists()
        controller.importLikedTracks()
        controller.pasteImportPlaylist()
        controller.inputProviderCookie()

        assertEquals(1, navigator.importCalls)
        assertEquals(1, navigator.loadUserPlaylistsCalls)
        assertEquals(1, navigator.importLikedTracksCalls)
        assertEquals(1, navigator.pasteImportCalls)
        assertEquals(1, navigator.inputProviderCookieCalls)
    }

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

    private class FakeSearchActionHandler : StreamingSearchActionHandler {
        val calls = ArrayList<String>()

        override fun selectProvider(provider: StreamingProviderName) {
            calls.add("select:${provider.name}")
        }

        override fun search(query: String) {
            calls.add("search:$query")
        }

        override fun login(provider: StreamingProviderName) {
            calls.add("login:${provider.name}")
        }

        override fun signOut(provider: StreamingProviderName) {
            calls.add("signOut:${provider.name}")
        }

        override fun openAuthLaunch() {
            calls.add("openAuth")
        }

        override fun playStreamingTrack(track: StreamingTrack) {
            calls.add("playStreaming:${track.providerTrackId}")
        }

        override fun playResolvedTrack(track: Track) {
            calls.add("playResolved:${track.id}")
        }

        override fun loadNextPage() {
            calls.add("nextPage")
        }
    }

    private class FakeNavigator : StreamingSearchEventController.Navigator {
        var backCalls = 0
        var importCalls = 0
        var loadUserPlaylistsCalls = 0
        var importLikedTracksCalls = 0
        var playDailyRecommendationsCalls = 0
        var playHeartbeatRecommendationsCalls = 0
        var pasteImportCalls = 0
        var inputProviderCookieCalls = 0

        override fun backToNetworkHome() {
            backCalls++
        }

        override fun importStreamingPlaylist(playlist: app.yukine.streaming.StreamingPlaylist) {
            importCalls++
        }

        override fun loadUserPlaylists() {
            loadUserPlaylistsCalls++
        }

        override fun importLikedTracks() {
            importLikedTracksCalls++
        }

        override fun playDailyRecommendations() {
            playDailyRecommendationsCalls++
        }

        override fun playHeartbeatRecommendations() {
            playHeartbeatRecommendationsCalls++
        }

        override fun pasteImportPlaylist() {
            pasteImportCalls++
        }

        override fun inputProviderCookie() {
            inputProviderCookieCalls++
        }
    }

    private class FakeContentSink : StreamingSearchContentSink {
        override fun publishStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions) = Unit
    }

    private class FakeGateway : MainActivityStreamingActionGateway {
        val loginSuccessProviders = ArrayList<app.yukine.streaming.StreamingProviderName>()
        val manualCookieProviders = ArrayList<app.yukine.streaming.StreamingProviderName>()

        override fun streamingPlaybackQuality() = app.yukine.streaming.StreamingAudioQuality.LOSSLESS

        override fun languageMode(): String = AppLanguage.MODE_ENGLISH

        override fun openAuthLaunch(launch: MainActivityStreamingAuthLaunch?): Boolean = false

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

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, android.net.Uri.EMPTY, "file:$id")
}
