package app.echo.next

import android.content.Intent
import android.net.Uri
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        val playlist = app.echo.next.streaming.StreamingPlaylist(
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
    fun resolvedPlaybackControllerWrapsTrackAsSingleItemQueue() {
        val player = FakePlayer()
        val controller = StreamingResolvedPlaybackController(player)
        val resolvedTrack = track(7L)

        controller.playResolvedTrack(resolvedTrack)

        assertEquals(0, player.index)
        assertEquals(1, player.tracks.size)
        assertSame(resolvedTrack, player.tracks[0])
    }

    @Test
    fun authCallbackControllerDelegatesInitialAndNewIntents() {
        val handler = FakeAuthCallbackHandler()
        val controller = StreamingAuthCallbackController(handler)

        assertTrue(controller.handleInitialIntent(null))
        assertTrue(controller.handleNewIntent(null))

        assertEquals(listOf(null, null), handler.intents)
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

        override fun importStreamingPlaylist(playlist: app.echo.next.streaming.StreamingPlaylist) {
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

    private class FakeContentSink : StreamingSearchEventController.ContentSink {
        override fun addVirtualContent(view: android.view.View) = Unit
    }

    private class FakePlayer : StreamingResolvedPlaybackController.Player {
        var tracks: List<Track> = emptyList()
        var index: Int = -1

        override fun playTrackList(tracks: List<Track>, index: Int) {
            this.tracks = tracks
            this.index = index
        }
    }

    private class FakeAuthCallbackHandler : StreamingAuthCallbackHandler {
        val intents = ArrayList<Intent?>()

        override fun handleAuthCallback(intent: Intent?): Boolean {
            intents.add(intent)
            return true
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
