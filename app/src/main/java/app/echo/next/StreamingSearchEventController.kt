package app.echo.next

import app.echo.next.model.Track
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import app.echo.next.ui.StreamingSearchActions
import app.echo.next.ui.StreamingSearchLabels

internal class StreamingSearchEventController(
    private val actionsController: StreamingSearchActionHandler,
    private val navigator: Navigator,
    private val contentSink: ContentSink
) : StreamingSearchRenderController.Listener {
    interface Navigator {
        fun backToNetworkHome()

        fun importStreamingPlaylist(playlist: StreamingPlaylist)

        fun loadUserPlaylists()

        fun importLikedTracks()

        fun playDailyRecommendations()

        fun playHeartbeatRecommendations()

        fun pasteImportPlaylist()

        fun inputProviderCookie()
    }

    interface ContentSink {
        fun publishStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions) = Unit
    }

    override fun backToNetworkHome() {
        navigator.backToNetworkHome()
    }

    override fun selectProvider(provider: StreamingProviderName) {
        actionsController.selectProvider(provider)
    }

    override fun search(query: String) {
        actionsController.search(query)
    }

    override fun login(provider: StreamingProviderName) {
        actionsController.login(provider)
    }

    override fun signOut(provider: StreamingProviderName) {
        actionsController.signOut(provider)
    }

    override fun openAuthLaunch() {
        actionsController.openAuthLaunch()
    }

    override fun playStreamingTrack(track: StreamingTrack) {
        actionsController.playStreamingTrack(track)
    }

    override fun playResolvedTrack(track: Track) {
        actionsController.playResolvedTrack(track)
    }

    override fun loadNextPage() {
        actionsController.loadNextPage()
    }

    override fun importStreamingPlaylist(playlist: StreamingPlaylist) {
        navigator.importStreamingPlaylist(playlist)
    }

    override fun loadUserPlaylists() {
        navigator.loadUserPlaylists()
    }

    override fun importLikedTracks() {
        navigator.importLikedTracks()
    }

    override fun playDailyRecommendations() {
        navigator.playDailyRecommendations()
    }

    override fun playHeartbeatRecommendations() {
        navigator.playHeartbeatRecommendations()
    }

    override fun pasteImportPlaylist() {
        navigator.pasteImportPlaylist()
    }

    override fun inputProviderCookie() {
        navigator.inputProviderCookie()
    }

    override fun publishStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions) {
        contentSink.publishStreamingSearchChrome(labels, actions)
    }
}
