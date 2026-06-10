package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingPlaylist
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import app.echo.next.ui.StreamingSearchActions
import app.echo.next.ui.StreamingSearchLabels
import app.echo.next.ui.StreamingSearchScreenFactory

internal class StreamingSearchRenderController(
    private val context: Context,
    private val viewModel: MainActivityViewModel,
    private val languageProvider: LanguageProvider,
    private val listener: Listener
) {
    fun interface LanguageProvider {
        fun languageMode(): String
    }

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
                labels(),
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

    private fun labels(): StreamingSearchLabels {
        val languageMode = languageProvider.languageMode()
        return StreamingSearchLabels(
            title = text(languageMode, "streaming.title"),
            back = text(languageMode, "back"),
            searchPrefix = text(languageMode, "streaming.search.prefix"),
            searchSuffix = text(languageMode, "streaming.search.suffix"),
            sourceDefault = text(languageMode, "streaming.source.default"),
            searchUnavailableSuffix = text(languageMode, "streaming.search.unavailable.suffix"),
            importPlaylistFromStreaming = text(languageMode, "streaming.import.playlist.from"),
            loadAccountPlaylists = text(languageMode, "streaming.load.account.playlists"),
            importLikedTracks = text(languageMode, "streaming.import.liked"),
            dailyRecommendations = text(languageMode, "streaming.recommend.daily"),
            heartbeatRecommendations = text(languageMode, "streaming.recommend.heartbeat"),
            backupAccountConnection = text(languageMode, "streaming.account.connect.backup"),
            accountActions = text(languageMode, "streaming.account.actions"),
            discoverMusic = text(languageMode, "streaming.discover.music"),
            advancedTools = text(languageMode, "streaming.advanced.tools"),
            loadingAccountPlaylists = text(languageMode, "streaming.account.playlists.loading"),
            accountPlaylists = text(languageMode, "streaming.account.playlists"),
            openLoginPrefix = text(languageMode, "streaming.open.login.prefix"),
            openLoginSuffix = text(languageMode, "streaming.open.login.suffix"),
            loading = text(languageMode, "loading"),
            streamingRequestFailed = text(languageMode, "streaming.request.failed"),
            playlistImportFailed = text(languageMode, "playlist.import.failed"),
            accountPlaylistsFailed = text(languageMode, "streaming.account.playlists.failed"),
            matchingLocalTracks = text(languageMode, "streaming.matching.local.tracks"),
            playlistImportPrefix = text(languageMode, "streaming.playlist.import.title.prefix"),
            matched = text(languageMode, "streaming.import.matched.prefix").trim(),
            unresolved = text(languageMode, "streaming.import.unresolved.suffix").trim(),
            results = text(languageMode, "results"),
            matchedStreamingTracks = text(languageMode, "streaming.matched.tracks"),
            songs = text(languageMode, "songs"),
            albums = text(languageMode, "albums"),
            artists = text(languageMode, "artists"),
            playlists = text(languageMode, "playlists"),
            videos = text(languageMode, "videos"),
            noResults = text(languageMode, "streaming.no.results"),
            loadMore = text(languageMode, "streaming.load.more"),
            playResolvedTrack = text(languageMode, "streaming.play.resolved.track"),
            signedIn = text(languageMode, "streaming.status.signed.in"),
            onlineAuthenticated = text(languageMode, "streaming.status.online.authenticated"),
            online = text(languageMode, "streaming.status.online"),
            unavailable = text(languageMode, "streaming.status.unavailable"),
            ready = text(languageMode, "streaming.status.ready"),
            needsAccount = text(languageMode, "streaming.status.needs.account"),
            disabled = text(languageMode, "streaming.status.disabled"),
            error = text(languageMode, "streaming.status.error"),
            trackCountSuffix = text(languageMode, "streaming.track.count.suffix")
        )
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
}
