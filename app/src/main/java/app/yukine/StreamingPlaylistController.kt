package app.yukine

import app.yukine.streaming.StreamingPlaylistSyncStore
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

        fun selectedPlaylistName(): String

        fun selectedPlaylistTracks(): List<app.yukine.model.Track>

        fun favoriteTracks(): List<app.yukine.model.Track>

        fun selectedStreamingProvider(): StreamingProviderName?

        fun showStreamingProviderPicker(playlistName: String, tracks: List<app.yukine.model.Track>)

        fun navigateToStreaming()

        fun showStreamingPlaylistLoadedDialog(message: String)

        fun setStatus(status: String)

        fun renderSelectedTab()
    }

    fun importSelectedPlaylistToStreaming() {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.prepareStreamingPlaylistExportRequest(
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
        val request = streamingViewModel.prepareStreamingFavoritesExportRequest(
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
            streamingViewModel.prepareStreamingPlaylistExportRequest(playlistName, tracks, languageMode).status
        )
        listener.navigateToStreaming()
        streamingViewModel.importPlaylistToStreaming(provider, playlistName, tracks) { summary ->
            val importStatus = streamingViewModel.streamingPlaylistImportStatus(summary)
            listener.setStatus(
                streamingViewModel.prepareStreamingPlaylistExportPresentation(importStatus, languageMode).status
            )
        }
    }

    fun importStreamingPlaylistFromLink(linkOrId: String?) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.prepareStreamingPlaylistImportStartRequest(
            linkOrId,
            listener.selectedStreamingProvider(),
            languageMode
        )
        if (!request.valid || request.provider == null) {
            listener.setStatus(request.invalidStatus)
            return
        }
        importStreamingPlaylist(request.provider, request.providerPlaylistId, request.resolvingStatus, languageMode)
    }

    fun importStreamingPlaylistFromProviderRef(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.prepareStreamingPlaylistImportStartRequest(
            providerPlaylistId,
            provider,
            languageMode
        )
        if (!request.valid || request.provider == null) {
            listener.setStatus(request.invalidStatus)
            return
        }
        importStreamingPlaylist(request.provider, request.providerPlaylistId, request.resolvingStatus, languageMode)
    }

    fun importStreamingLikedTracks(provider: StreamingProviderName?) {
        if (provider == null) {
            return
        }
        val languageMode = languageProvider.languageMode()
        val playlistName = streamingViewModel.prepareStreamingLikedPlaylistName(provider, languageMode)
        listener.setStatus(streamingViewModel.prepareStreamingPlaybackStatusText(languageMode, null).resolving)
        listener.navigateToStreaming()
        streamingViewModel.importStreamingLikedTracksToLocal(provider, playlistName) { result ->
            val presentation = streamingViewModel.prepareStreamingLikedImportPresentation(result, languageMode)
            handleImportPresentation(presentation)
        }
    }

    fun syncSelectedPlaylistFromStreaming() {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.prepareStreamingPlaylistSyncStartRequest(
            listener.selectedPlaylistId(),
            languageMode
        )
            ?: return
        listener.setStatus(request.status)
        if (!request.valid || request.link == null) {
            return
        }
        syncStreamingPlaylist(request.link, languageMode)
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
        streamingViewModel.syncStreamingPlaylistToLocal(link) { result ->
            val presentation = streamingViewModel.prepareStreamingPlaylistSyncPresentation(result, languageMode)
            listener.setStatus(presentation.status)
            if (presentation.empty) {
                return@syncStreamingPlaylistToLocal
            }
            listener.loadCollections()
        }
    }

    private fun importStreamingPlaylist(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        resolvingStatus: String,
        languageMode: String
    ) {
        listener.setStatus(resolvingStatus)
        streamingViewModel.importStreamingPlaylistToLocal(provider, providerPlaylistId) { result ->
            val presentation = streamingViewModel.prepareStreamingPlaylistImportPresentation(result, languageMode)
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
        listener.loadCollections()
    }

    fun onStreamingLoginSuccess(provider: StreamingProviderName) {
        val languageMode = languageProvider.languageMode()
        val request = streamingViewModel.prepareStreamingLoginPlaylistRequest(provider, languageMode)
        streamingViewModel.ensureStreamingLoginPlaylist(request.playlistName, request.provider) { result ->
            val presentation = streamingViewModel.prepareStreamingLoginPlaylistPresentation(
                request,
                result,
                languageMode
            )
            listener.setStatus(presentation.status)
            if (presentation.playlistId >= 0L) {
                listener.setSelectedPlaylistId(presentation.playlistId)
            }
            listener.loadCollections()
            listener.renderSelectedTab()
        }
    }
}
