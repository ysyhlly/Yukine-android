package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import app.echo.next.ui.StreamingSearchActions
import app.echo.next.ui.StreamingSearchScreenFactory

internal class StreamingSearchRenderController(
    private val context: Context,
    private val viewModel: MainActivityViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun backToNetworkHome()

        fun selectProvider(provider: StreamingProviderName)

        fun search(query: String)

        fun login(provider: StreamingProviderName)

        fun signOut(provider: StreamingProviderName)

        fun openAuthLaunch()

        fun playStreamingTrack(track: StreamingTrack)

        fun playResolvedTrack(track: Track)

        fun loadNextPage()

        fun importStreamingPlaylist(playlist: StreamingPlaylist)

        fun loadUserPlaylists()

        fun importLikedTracks()

        fun playDailyRecommendations()

        fun playHeartbeatRecommendations()

        fun pasteImportPlaylist()

        fun inputProviderCookie()

        fun addVirtualContent(view: View)
    }

    fun render() {
        listener.addVirtualContent(
            StreamingSearchScreenFactory.create(
                context,
                viewModel.streaming,
                StreamingSearchActions(
                    onBack = Runnable { listener.backToNetworkHome() },
                    onSelectProvider = { provider -> listener.selectProvider(provider) },
                    onSearch = { query -> listener.search(query) },
                    onLogin = { provider -> listener.login(provider) },
                    onSignOut = { provider -> listener.signOut(provider) },
                    onOpenAuthLaunch = Runnable { listener.openAuthLaunch() },
                    onPlayTrack = { track -> listener.playStreamingTrack(track) },
                    onPlayResolvedTrack = { track -> listener.playResolvedTrack(track) },
                    onNextPage = Runnable { listener.loadNextPage() },
                    onImportPlaylist = { playlist -> listener.importStreamingPlaylist(playlist) },
                    onLoadUserPlaylists = Runnable { listener.loadUserPlaylists() },
                    onImportLikedTracks = Runnable { listener.importLikedTracks() },
                    onDailyRecommend = Runnable { listener.playDailyRecommendations() },
                    onHeartbeatRecommend = Runnable { listener.playHeartbeatRecommendations() },
                    onPasteImport = Runnable { listener.pasteImportPlaylist() },
                    onInputCookie = Runnable { listener.inputProviderCookie() }
                )
            )
        )
    }
}
