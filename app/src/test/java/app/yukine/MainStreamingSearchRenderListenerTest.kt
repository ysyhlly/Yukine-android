package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStreamingSearchRenderListenerTest {
    @Test
    fun delegatesStreamingSearchRenderCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val listener = listener(calls)
        val streamingTrack = streamingSearchTrack("s1")
        val resolvedTrack = streamingSearchResolvedTrack(1L)
        val playlist = StreamingPlaylist(StreamingProviderName.NETEASE, "100", "Daily")

        listener.backToNetworkHome()
        listener.selectProvider(StreamingProviderName.QQ_MUSIC)
        listener.search("hello")
        listener.login(StreamingProviderName.NETEASE)
        listener.signOut(StreamingProviderName.KUGOU)
        listener.openAuthLaunch()
        listener.playStreamingTrack(streamingTrack)
        listener.playResolvedTrack(resolvedTrack)
        listener.loadNextPage()
        listener.importStreamingPlaylist(playlist)
        listener.loadUserPlaylists()
        listener.importLikedTracks()
        listener.playDailyRecommendations()
        listener.playHeartbeatRecommendations()
        listener.pasteImportPlaylist()
        listener.inputProviderCookie()
        listener.publishStreamingSearchChrome(StreamingSearchLabels.empty(), StreamingSearchActions.empty())

        assertEquals(
            listOf(
                "back",
                "select:qqmusic",
                "search:hello",
                "login:netease",
                "signOut:kugou",
                "auth",
                "playStreaming:s1",
                "playResolved:1",
                "next",
                "playlist:netease:100",
                "account:qqmusic",
                "liked:qqmusic",
                "recommend:PlayDaily:qqmusic",
                "recommend:PlayHeartbeat:qqmusic",
                "paste",
                "cookie",
                "chrome"
            ),
            calls
        )
    }

    @Test
    fun factoryCreatesStreamingSearchRenderControllerListener() {
        val calls = mutableListOf<String>()
        val listener = StreamingModule.provideMainStreamingSearchRenderListenerFactory().create(
            StreamingSearchNetworkNavigator { calls += "back" },
            FakeStreamingSearchActionHandler(calls),
            StreamingSearchSelectedProviderSource { StreamingProviderName.NETEASE },
            StreamingPlaylistProviderRefImporter { provider, providerPlaylistId ->
                calls += "playlist:${provider.wireName}:$providerPlaylistId"
            },
            StreamingAccountPlaylistSyncPicker { calls += "account:${it.wireName}" },
            StreamingLikedTracksImporter { calls += "liked:${it.wireName}" },
            StreamingRecommendationActionRunner {
                calls += "recommend:${it.javaClass.simpleName}:${it.provider?.wireName}"
            },
            StreamingPlaylistImportDialogPresenter { calls += "paste" },
            StreamingManualCookiePresenter { calls += "cookie" },
            StreamingSearchChromePublisher { _, _ -> calls += "chrome" }
        )

        listener.selectProvider(StreamingProviderName.KUGOU)
        listener.loadUserPlaylists()
        listener.playDailyRecommendations()
        listener.publishStreamingSearchChrome(StreamingSearchLabels.empty(), StreamingSearchActions.empty())

        assertEquals(listOf("select:kugou", "account:netease", "recommend:PlayDaily:netease", "chrome"), calls)
    }

    private fun listener(calls: MutableList<String>): StreamingSearchRenderController.Listener =
        MainStreamingSearchRenderListener(
            networkNavigator = StreamingSearchNetworkNavigator { calls += "back" },
            actionHandler = FakeStreamingSearchActionHandler(calls),
            selectedProviderSource = StreamingSearchSelectedProviderSource { StreamingProviderName.QQ_MUSIC },
            playlistImporter = StreamingPlaylistProviderRefImporter { provider, providerPlaylistId ->
                calls += "playlist:${provider.wireName}:$providerPlaylistId"
            },
            accountPlaylistSyncPicker = StreamingAccountPlaylistSyncPicker { calls += "account:${it.wireName}" },
            likedTracksImporter = StreamingLikedTracksImporter { calls += "liked:${it.wireName}" },
            recommendationActionRunner = StreamingRecommendationActionRunner {
                calls += "recommend:${it.javaClass.simpleName}:${it.provider?.wireName}"
            },
            playlistImportDialogPresenter = StreamingPlaylistImportDialogPresenter { calls += "paste" },
            manualCookiePresenter = StreamingManualCookiePresenter { calls += "cookie" },
            chromePublisher = StreamingSearchChromePublisher { _, _ -> calls += "chrome" }
        )
}

private class FakeStreamingSearchActionHandler(
    private val calls: MutableList<String>
) : StreamingSearchActionHandler {
    override fun selectProvider(provider: StreamingProviderName) {
        calls += "select:${provider.wireName}"
    }

    override fun search(query: String) {
        calls += "search:$query"
    }

    override fun login(provider: StreamingProviderName) {
        calls += "login:${provider.wireName}"
    }

    override fun signOut(provider: StreamingProviderName) {
        calls += "signOut:${provider.wireName}"
    }

    override fun openAuthLaunch() {
        calls += "auth"
    }

    override fun playStreamingTrack(track: StreamingTrack) {
        calls += "playStreaming:${track.providerTrackId}"
    }

    override fun playResolvedTrack(track: Track) {
        calls += "playResolved:${track.id}"
    }

    override fun loadNextPage() {
        calls += "next"
    }
}

private fun streamingSearchTrack(id: String): StreamingTrack =
    StreamingTrack(
        provider = StreamingProviderName.NETEASE,
        providerTrackId = id,
        title = "Streaming $id",
        artist = "Artist"
    )

private fun streamingSearchResolvedTrack(id: Long): Track =
    Track(id, "Track $id", "Artist", "Album", 1_000L, Uri.EMPTY, "file:$id")
