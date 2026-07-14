package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels
import app.yukine.ui.StreamingUsageNoticeLabels

class StreamingSearchStateReducer(
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

        fun manageLuoxueSources()

        fun inputProviderCookie()

        fun publishStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions)
    }

    fun reduce() {
        val labels = labels()
        val actions = StreamingSearchActions(
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
            onManageLuoxueSources = Runnable { listener.manageLuoxueSources() },
            onInputCookie = Runnable { listener.inputProviderCookie() }
        )
        listener.publishStreamingSearchChrome(labels, actions)
    }

    private fun labels(): StreamingSearchLabels {
        val languageMode = languageProvider.languageMode()
        return StreamingSearchLabels(
            languageMode = languageMode,
            usageNotice = StreamingUsageNoticeLabels(
                title = text(languageMode, "streaming.usage.notice.title"),
                body = text(languageMode, "streaming.usage.notice.body")
            ),
            title = text(languageMode, "streaming.title"),
            back = text(languageMode, "back"),
            searchPrefix = text(languageMode, "streaming.search.prefix"),
            searchSuffix = text(languageMode, "streaming.search.suffix"),
            sourceDefault = text(languageMode, "streaming.source.default"),
            searchUnavailableSuffix = text(languageMode, "streaming.search.unavailable.suffix"),
            importPlaylistFromStreaming = text(languageMode, "streaming.import.playlist.from"),
            importLuoxueSource = text(languageMode, "streaming.lx.import.source"),
            manageLuoxueSources = text(languageMode, "streaming.lx.source.manager"),
            luoxueImportHint = text(languageMode, "streaming.lx.import.hint"),
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
            localLoginSaved = text(languageMode, "streaming.status.local.login.saved"),
            notSignedIn = text(languageMode, "streaming.status.not.signed.in"),
            sessionVerified = text(languageMode, "streaming.status.session.verified"),
            sessionPendingVerification = text(languageMode, "streaming.status.session.pending.verification"),
            sessionInvalid = text(languageMode, "streaming.status.session.invalid"),
            localLoginComplete = text(languageMode, "streaming.status.local.login.complete"),
            gatewayLocalLogin = text(languageMode, "streaming.status.gateway.local.login"),
            gatewayRequired = text(languageMode, "streaming.status.gateway.required"),
            loginEntryMissing = text(languageMode, "streaming.status.login.entry.missing"),
            openLoginPage = text(languageMode, "streaming.status.open.login.page"),
            neteaseLikedPlaylistEmpty = text(languageMode, "streaming.status.netease.liked.empty"),
            neteaseAccountIdMissing = text(languageMode, "streaming.status.netease.account.id.missing"),
            neteaseLoginRequiredPlaylists = text(languageMode, "streaming.status.netease.login.required.playlists"),
            trackCountSuffix = text(languageMode, "streaming.track.count.suffix")
        )
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)
}
