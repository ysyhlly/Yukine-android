package app.yukine

import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName

internal class StreamingPlaylistController(
    private val streamingViewModel: StreamingViewModel,
    private val languageProvider: LanguageProvider,
    private val listener: Listener
) {
    fun interface LanguageProvider {
        fun languageMode(): String
    }

    interface Listener {
        fun selectedPlaylistId(): Long

        fun setSelectedPlaylistId(playlistId: Long)

        fun loadCollections()

        fun refreshLibraryAfterStreamingImport()

        fun selectedPlaylistName(): String

        fun selectedPlaylistTracks(): List<app.yukine.model.Track>

        fun favoriteTracks(): List<app.yukine.model.Track>

        fun selectedStreamingProvider(): StreamingProviderName?

        fun showStreamingProviderPicker(playlistName: String, tracks: List<app.yukine.model.Track>)

        fun navigateToStreaming()

        fun showStreamingPlaylistLoadedDialog(message: String)

        fun showAccountPlaylistImportPicker(provider: StreamingProviderName, playlists: List<StreamingPlaylist>)

        fun setStatus(status: String)
    }

    fun importSelectedPlaylistToStreaming() {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.playlists.prepareStreamingPlaylistExportRequest(
            listener.selectedPlaylistName(),
            listener.selectedPlaylistTracks(),
            languageMode
        )
        listener.setStatus(request.status)
        if (!request.valid) {
            return
        }
        listener.showStreamingProviderPicker(request.playlistName, request.tracks)
    }

    fun importFavoritesToStreaming() {
        val request = streamingViewModel.playlists.prepareStreamingFavoritesExportRequest(
            listener.favoriteTracks(),
            languageProvider.languageMode()
        )
        listener.setStatus(request.status)
        if (!request.valid) {
            return
        }
        listener.showStreamingProviderPicker(request.playlistName, request.tracks)
    }

    fun runStreamingPlaylistImport(
        provider: StreamingProviderName,
        playlistName: String,
        tracks: List<app.yukine.model.Track>
    ) {
        val languageMode = languageProvider.languageMode()
        listener.setStatus(
            streamingViewModel.playlists.prepareStreamingPlaylistExportRequest(playlistName, tracks, languageMode).status
        )
        listener.navigateToStreaming()
        streamingViewModel.playlists.importPlaylistToStreaming(provider, playlistName, tracks) { summary ->
            val importStatus = streamingViewModel.playlists.streamingPlaylistImportStatus(summary)
            listener.setStatus(
                streamingViewModel.playlists.prepareStreamingPlaylistExportPresentation(importStatus, languageMode).status
            )
        }
    }

    fun importStreamingPlaylistFromLink(linkOrId: String?) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.playlists.prepareStreamingPlaylistImportStartRequest(
            linkOrId,
            listener.selectedStreamingProvider(),
            languageMode
        )
        val provider = request.provider
        if (!request.valid || provider == null) {
            listener.setStatus(request.invalidStatus)
            return
        }
        importStreamingPlaylist(provider, request.providerPlaylistId, request.resolvingStatus, languageMode)
    }

    fun importStreamingPlaylistFromProviderRef(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.playlists.prepareStreamingPlaylistImportStartRequest(
            providerPlaylistId,
            provider,
            languageMode
        )
        val requestProvider = request.provider
        if (!request.valid || requestProvider == null) {
            listener.setStatus(request.invalidStatus)
            return
        }
        importStreamingPlaylist(requestProvider, request.providerPlaylistId, request.resolvingStatus, languageMode)
    }

    fun importStreamingLikedTracks(provider: StreamingProviderName?) {
        if (provider == null) {
            return
        }
        val languageMode = languageProvider.languageMode()
        val playlistName = streamingViewModel.playlists.prepareStreamingLikedPlaylistName(provider, languageMode)
        listener.setStatus(StreamingStatusTextFactory.playback(languageMode, null).resolving)
        listener.navigateToStreaming()
        streamingViewModel.playlists.importStreamingLikedTracksToLocal(provider, playlistName) { result ->
            val presentation = streamingViewModel.playlists.prepareStreamingLikedImportPresentation(result, languageMode)
            handleImportPresentation(presentation)
        }
    }

    fun syncSelectedPlaylistFromStreaming() {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.playlists.prepareStreamingPlaylistSyncStartRequest(
            listener.selectedPlaylistId(),
            languageMode
        )
            ?: return
        listener.setStatus(request.status)
        val link = request.link
        if (!request.valid || link == null) {
            return
        }
        syncStreamingPlaylist(link, languageMode)
    }

    fun syncStreamingPlaylists(links: List<StreamingPlaylistSyncStore.LinkedPlaylist>) {
        val languageMode = languageProvider.languageMode()
        links.forEach { link -> syncStreamingPlaylist(link, languageMode) }
    }

    fun syncStreamingPlaylist(link: StreamingPlaylistSyncStore.LinkedPlaylist) {
        syncStreamingPlaylist(link, languageProvider.languageMode())
    }

    private fun syncStreamingPlaylist(
        link: StreamingPlaylistSyncStore.LinkedPlaylist,
        languageMode: String
    ) {
        streamingViewModel.playlists.syncStreamingPlaylistToLocal(link) { result ->
            val presentation = streamingViewModel.playlists.prepareStreamingPlaylistSyncPresentation(result, languageMode)
            listener.setStatus(presentation.status)
            if (presentation.empty) {
                return@syncStreamingPlaylistToLocal
            }
            listener.refreshLibraryAfterStreamingImport()
        }
    }

    private fun importStreamingPlaylist(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        resolvingStatus: String,
        languageMode: String
    ) {
        listener.setStatus(resolvingStatus)
        streamingViewModel.playlists.importStreamingPlaylistToLocal(provider, providerPlaylistId) { result ->
            val presentation = streamingViewModel.playlists.prepareStreamingPlaylistImportPresentation(result, languageMode)
            handleImportPresentation(presentation)
        }
    }

    private fun handleImportPresentation(presentation: StreamingLocalPlaylistImportPresentation) {
        listener.setStatus(presentation.status)
        if (presentation.empty) {
            return
        }
        if (presentation.showLoadedDialog) {
            listener.showStreamingPlaylistLoadedDialog(presentation.status)
        }
        listener.refreshLibraryAfterStreamingImport()
    }

    fun onStreamingLoginSuccess(provider: StreamingProviderName) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.playlists.prepareStreamingLoginPlaylistRequest(provider, languageMode)
        streamingViewModel.playlists.ensureStreamingLoginPlaylist(request.playlistName, request.provider) { result ->
            val presentation = streamingViewModel.playlists.prepareStreamingLoginPlaylistPresentation(
                request,
                result,
                languageMode
            )
            listener.setStatus(presentation.status)
            if (presentation.playlistId >= 0L) {
                listener.setSelectedPlaylistId(presentation.playlistId)
            }
            listener.refreshLibraryAfterStreamingImport()
            showAccountPlaylistSyncPicker(provider)
        }
    }

    fun showAccountPlaylistSyncPicker(provider: StreamingProviderName) {
        val languageMode = languageProvider.languageMode()
        listener.setStatus(AppLanguage.text(languageMode, "streaming.account.playlists.loading"))
        streamingViewModel.playlists.fetchAccountPlaylistsForImport(provider) { playlists ->
            if (playlists.isEmpty()) {
                listener.setStatus(AppLanguage.text(languageMode, "streaming.no.account.playlists"))
                return@fetchAccountPlaylistsForImport
            }
            listener.showAccountPlaylistImportPicker(provider, playlists)
        }
    }

    fun importSelectedAccountPlaylists(provider: StreamingProviderName, playlists: List<StreamingPlaylist>) {
        val languageMode = languageProvider.languageMode()
        if (playlists.isEmpty()) {
            listener.setStatus(AppLanguage.text(languageMode, "streaming.no.account.playlists"))
            return
        }
        listener.setStatus(AppLanguage.text(languageMode, "streaming.sync.started"))
        streamingViewModel.playlists.importAccountPlaylistsToLocal(provider, playlists) { result ->
            val status = AppLanguage.text(languageMode, "streaming.account.playlists.imported") +
                result.importedPlaylistCount +
                "/" +
                result.playlistCount +
                "\uff0c" +
                AppLanguage.text(languageMode, "songs") +
                " " +
                result.importedTrackCount +
                if (result.failedCount > 0) {
                    "\uff0c" + AppLanguage.text(languageMode, "streaming.account.playlists.failed.count") + result.failedCount
                } else {
                    ""
                }
            listener.setStatus(status)
            listener.refreshLibraryAfterStreamingImport()
        }
    }
}
