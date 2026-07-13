package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylist
import app.yukine.streaming.StreamingProviderName

internal fun interface StreamingPlaylistIdSource {
    fun selectedPlaylistId(): Long
}

internal fun interface StreamingPlaylistIdSink {
    fun setSelectedPlaylistId(playlistId: Long)
}

internal fun interface StreamingCollectionsLoader {
    fun loadCollections()
}

internal fun interface StreamingLibraryRefreshSink {
    fun refreshLibraryAfterStreamingImport()
}

internal fun interface StreamingSelectedPlaylistNameSource {
    fun selectedPlaylistName(): String
}

internal fun interface StreamingSelectedPlaylistTracksSource {
    fun selectedPlaylistTracks(): List<Track>
}

internal fun interface StreamingFavoriteTracksSource {
    fun favoriteTracks(): List<Track>
}

internal fun interface StreamingSelectedProviderSource {
    fun selectedStreamingProvider(): StreamingProviderName?
}

internal fun interface StreamingProviderPickerPresenter {
    fun showStreamingProviderPicker(playlistName: String, tracks: List<Track>)
}

internal fun interface StreamingNavigationSink {
    fun navigateToStreaming()
}

internal fun interface StreamingPlaylistLoadedDialogPresenter {
    fun showStreamingPlaylistLoadedDialog(message: String)
}

internal fun interface StreamingAccountPlaylistImportPickerPresenter {
    fun showAccountPlaylistImportPicker(provider: StreamingProviderName, playlists: List<StreamingPlaylist>)
}

internal fun interface StreamingPlaylistStatusSink {
    fun setStatus(status: String)
}

internal class MainStreamingPlaylistListener(
    private val playlistIdSource: StreamingPlaylistIdSource,
    private val playlistIdSink: StreamingPlaylistIdSink,
    private val collectionsLoader: StreamingCollectionsLoader,
    private val libraryRefreshSink: StreamingLibraryRefreshSink,
    private val playlistNameSource: StreamingSelectedPlaylistNameSource,
    private val playlistTracksSource: StreamingSelectedPlaylistTracksSource,
    private val favoriteTracksSource: StreamingFavoriteTracksSource,
    private val selectedProviderSource: StreamingSelectedProviderSource,
    private val providerPickerPresenter: StreamingProviderPickerPresenter,
    private val navigationSink: StreamingNavigationSink,
    private val loadedDialogPresenter: StreamingPlaylistLoadedDialogPresenter,
    private val accountPlaylistPickerPresenter: StreamingAccountPlaylistImportPickerPresenter,
    private val statusSink: StreamingPlaylistStatusSink
) : StreamingPlaylistController.Listener {
    override fun selectedPlaylistId(): Long =
        playlistIdSource.selectedPlaylistId()

    override fun setSelectedPlaylistId(playlistId: Long) {
        playlistIdSink.setSelectedPlaylistId(playlistId)
    }

    override fun loadCollections() {
        collectionsLoader.loadCollections()
    }

    override fun refreshLibraryAfterStreamingImport() {
        libraryRefreshSink.refreshLibraryAfterStreamingImport()
    }

    override fun selectedPlaylistName(): String =
        playlistNameSource.selectedPlaylistName()

    override fun selectedPlaylistTracks(): List<Track> =
        playlistTracksSource.selectedPlaylistTracks()

    override fun favoriteTracks(): List<Track> =
        favoriteTracksSource.favoriteTracks()

    override fun selectedStreamingProvider(): StreamingProviderName? =
        selectedProviderSource.selectedStreamingProvider()

    override fun showStreamingProviderPicker(playlistName: String, tracks: List<Track>) {
        providerPickerPresenter.showStreamingProviderPicker(playlistName, tracks)
    }

    override fun navigateToStreaming() {
        navigationSink.navigateToStreaming()
    }

    override fun showStreamingPlaylistLoadedDialog(message: String) {
        loadedDialogPresenter.showStreamingPlaylistLoadedDialog(message)
    }

    override fun showAccountPlaylistImportPicker(provider: StreamingProviderName, playlists: List<StreamingPlaylist>) {
        accountPlaylistPickerPresenter.showAccountPlaylistImportPicker(provider, playlists)
    }

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }

}
