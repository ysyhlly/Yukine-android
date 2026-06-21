package app.yukine

import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSearchNavigatorBindingsTest {
    @Test
    fun forwardsNavigationImportsAndProviderActions() {
        val calls = mutableListOf<String>()
        val bindings = StreamingSearchNavigatorBindings(
            navigateNetworkPageAction = NetworkPageAction { calls += "navigate:$it" },
            selectedProviderProvider = StreamingProviderProvider { StreamingProviderName.NETEASE },
            playlistRefImporter = StreamingPlaylistRefImporter { provider, playlistId ->
                calls += "playlist:$provider:$playlistId"
            },
            loadUserPlaylistsAction = StreamingProviderAction { calls += "load:$it" },
            importLikedTracksAction = StreamingProviderAction { calls += "liked:$it" },
            dailyRecommendationsAction = StreamingRecommendationAction { calls += "daily:$it" },
            heartbeatRecommendationsAction = StreamingRecommendationAction { calls += "heartbeat:$it" },
            pasteImportPlaylistAction = Runnable { calls += "paste" },
            inputProviderCookieAction = Runnable { calls += "cookie" }
        )

        bindings.backToNetworkHome()
        bindings.importStreamingPlaylist(
            StreamingPlaylist(
                provider = StreamingProviderName.SPOTIFY,
                providerPlaylistId = "pl-1",
                title = "Playlist"
            )
        )
        bindings.loadUserPlaylists()
        bindings.importLikedTracks()
        bindings.playDailyRecommendations()
        bindings.playHeartbeatRecommendations()
        bindings.pasteImportPlaylist()
        bindings.inputProviderCookie()

        assertEquals(
            listOf(
                "navigate:${MainRoutes.NETWORK_HOME}",
                "playlist:${StreamingProviderName.SPOTIFY}:pl-1",
                "load:${StreamingProviderName.NETEASE}",
                "liked:${StreamingProviderName.NETEASE}",
                "daily:${StreamingProviderName.NETEASE}",
                "heartbeat:${StreamingProviderName.NETEASE}",
                "paste",
                "cookie"
            ),
            calls
        )
    }
}
