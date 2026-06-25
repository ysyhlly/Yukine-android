package app.yukine

import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName

internal fun interface StreamingProviderProvider {
    fun provider(): StreamingProviderName
}

internal fun interface StreamingPlaylistRefImporter {
    fun importPlaylist(provider: StreamingProviderName, providerPlaylistId: String)
}

internal fun interface StreamingProviderAction {
    fun run(provider: StreamingProviderName)
}

internal class StreamingSearchNavigatorBindings(
    private val navigateNetworkPageAction: NetworkPageAction,
    private val selectedProviderProvider: StreamingProviderProvider,
    private val playlistRefImporter: StreamingPlaylistRefImporter,
    private val syncAccountPlaylistsAction: StreamingProviderAction,
    private val importLikedTracksAction: StreamingProviderAction,
    private val recommendationActionRunner: RecommendationActionRunner,
    private val pasteImportPlaylistAction: Runnable,
    private val inputProviderCookieAction: Runnable
) : StreamingSearchEventController.Navigator {
    override fun backToNetworkHome() {
        navigateNetworkPageAction.run(MainRoutes.NETWORK_HOME)
    }

    override fun importStreamingPlaylist(playlist: StreamingPlaylist) {
        playlistRefImporter.importPlaylist(playlist.provider, playlist.providerPlaylistId)
    }

    override fun loadUserPlaylists() {
        syncAccountPlaylistsAction.run(selectedProviderProvider.provider())
    }

    override fun importLikedTracks() {
        importLikedTracksAction.run(selectedProviderProvider.provider())
    }

    override fun playDailyRecommendations() {
        recommendationActionRunner.run(RecommendationAction.PlayDaily(selectedProviderProvider.provider()))
    }

    override fun playHeartbeatRecommendations() {
        recommendationActionRunner.run(RecommendationAction.PlayHeartbeat(selectedProviderProvider.provider()))
    }

    override fun pasteImportPlaylist() {
        pasteImportPlaylistAction.run()
    }

    override fun inputProviderCookie() {
        inputProviderCookieAction.run()
    }
}
