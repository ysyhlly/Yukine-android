package app.yukine

import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.StreamingSearchActions
import app.yukine.ui.StreamingSearchLabels

internal fun interface StreamingSearchNetworkNavigator {
    fun backToNetworkHome()
}

internal fun interface StreamingSearchSelectedProviderSource {
    fun selectedProvider(): StreamingProviderName
}

internal fun interface StreamingPlaylistProviderRefImporter {
    fun importStreamingPlaylistFromProviderRef(provider: StreamingProviderName, providerPlaylistId: String)
}

internal fun interface StreamingAccountPlaylistSyncPicker {
    fun showAccountPlaylistSyncPicker(provider: StreamingProviderName)
}

internal fun interface StreamingLikedTracksImporter {
    fun importStreamingLikedTracks(provider: StreamingProviderName)
}

internal fun interface StreamingRecommendationActionRunner {
    fun runRecommendationAction(action: RecommendationAction)
}

internal fun interface StreamingPlaylistImportDialogPresenter {
    fun showImportDialog()
}

internal fun interface LuoxueSourceManagerPresenter {
    fun showSourceManager()
}

internal fun interface StreamingManualCookiePresenter {
    fun showStreamingCookieDialog()
}

internal fun interface StreamingSearchChromePublisher {
    fun publishStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions)
}

internal fun interface MainStreamingSearchRenderListenerFactory {
    fun create(
        networkNavigator: StreamingSearchNetworkNavigator,
        actionHandler: StreamingSearchActionHandler,
        selectedProviderSource: StreamingSearchSelectedProviderSource,
        playlistImporter: StreamingPlaylistProviderRefImporter,
        accountPlaylistSyncPicker: StreamingAccountPlaylistSyncPicker,
        likedTracksImporter: StreamingLikedTracksImporter,
        recommendationActionRunner: StreamingRecommendationActionRunner,
        playlistImportDialogPresenter: StreamingPlaylistImportDialogPresenter,
        luoxueSourceManagerPresenter: LuoxueSourceManagerPresenter,
        manualCookiePresenter: StreamingManualCookiePresenter,
        chromePublisher: StreamingSearchChromePublisher
    ): StreamingSearchRenderController.Listener
}

internal class MainStreamingSearchRenderListener(
    private val networkNavigator: StreamingSearchNetworkNavigator,
    private val actionHandler: StreamingSearchActionHandler,
    private val selectedProviderSource: StreamingSearchSelectedProviderSource,
    private val playlistImporter: StreamingPlaylistProviderRefImporter,
    private val accountPlaylistSyncPicker: StreamingAccountPlaylistSyncPicker,
    private val likedTracksImporter: StreamingLikedTracksImporter,
    private val recommendationActionRunner: StreamingRecommendationActionRunner,
    private val playlistImportDialogPresenter: StreamingPlaylistImportDialogPresenter,
    private val luoxueSourceManagerPresenter: LuoxueSourceManagerPresenter,
    private val manualCookiePresenter: StreamingManualCookiePresenter,
    private val chromePublisher: StreamingSearchChromePublisher
) : StreamingSearchRenderController.Listener {
    override fun backToNetworkHome() {
        networkNavigator.backToNetworkHome()
    }

    override fun selectProvider(provider: StreamingProviderName) {
        actionHandler.selectProvider(provider)
    }

    override fun search(query: String) {
        actionHandler.search(query)
    }

    override fun login(provider: StreamingProviderName) {
        actionHandler.login(provider)
    }

    override fun signOut(provider: StreamingProviderName) {
        actionHandler.signOut(provider)
    }

    override fun openAuthLaunch() {
        actionHandler.openAuthLaunch()
    }

    override fun playStreamingTrack(track: app.yukine.streaming.StreamingTrack) {
        actionHandler.playStreamingTrack(track)
    }

    override fun playResolvedTrack(track: app.yukine.model.Track) {
        actionHandler.playResolvedTrack(track)
    }

    override fun loadNextPage() {
        actionHandler.loadNextPage()
    }

    override fun importStreamingPlaylist(playlist: StreamingPlaylist) {
        playlistImporter.importStreamingPlaylistFromProviderRef(
            playlist.provider,
            playlist.providerPlaylistId
        )
    }

    override fun loadUserPlaylists() {
        accountPlaylistSyncPicker.showAccountPlaylistSyncPicker(selectedProviderSource.selectedProvider())
    }

    override fun importLikedTracks() {
        likedTracksImporter.importStreamingLikedTracks(selectedProviderSource.selectedProvider())
    }

    override fun playDailyRecommendations() {
        recommendationActionRunner.runRecommendationAction(
            RecommendationAction.PlayDaily(selectedProviderSource.selectedProvider())
        )
    }

    override fun playHeartbeatRecommendations() {
        recommendationActionRunner.runRecommendationAction(
            RecommendationAction.PlayHeartbeat(selectedProviderSource.selectedProvider())
        )
    }

    override fun pasteImportPlaylist() {
        playlistImportDialogPresenter.showImportDialog()
    }

    override fun manageLuoxueSources() {
        luoxueSourceManagerPresenter.showSourceManager()
    }

    override fun inputProviderCookie() {
        manualCookiePresenter.showStreamingCookieDialog()
    }

    override fun publishStreamingSearchChrome(labels: StreamingSearchLabels, actions: StreamingSearchActions) {
        chromePublisher.publishStreamingSearchChrome(labels, actions)
    }
}
