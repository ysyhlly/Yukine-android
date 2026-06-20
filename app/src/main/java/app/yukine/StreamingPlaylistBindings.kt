package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName

internal fun interface StreamingPlaylistIdProvider {
    fun playlistId(): Long
}

internal fun interface StreamingPlaylistIdSetter {
    fun set(playlistId: Long)
}

internal fun interface StreamingPlaylistNameProvider {
    fun playlistName(): String
}

internal fun interface StreamingPlaylistTrackProvider {
    fun tracks(): List<Track>
}

internal fun interface StreamingPlaylistProviderPicker {
    fun show(playlistName: String, tracks: List<Track>)
}

internal fun interface StreamingPlaylistLoadedDialog {
    fun show(message: String)
}

internal fun interface StreamingSelectedProviderProvider {
    fun provider(): StreamingProviderName?
}

internal class StreamingPlaylistBindings(
    private val selectedPlaylistIdProvider: StreamingPlaylistIdProvider,
    private val selectedPlaylistIdSetter: StreamingPlaylistIdSetter,
    private val collectionsLoader: QueueNoArgAction,
    private val selectedPlaylistNameProvider: StreamingPlaylistNameProvider,
    private val selectedPlaylistTrackProvider: StreamingPlaylistTrackProvider,
    private val favoriteTrackProvider: StreamingPlaylistTrackProvider,
    private val selectedProviderProvider: StreamingSelectedProviderProvider,
    private val providerPicker: StreamingPlaylistProviderPicker,
    private val streamingNavigator: QueueNoArgAction,
    private val loadedDialog: StreamingPlaylistLoadedDialog,
    private val statusSink: QueueStatusSink,
    private val selectedTabRenderer: QueueNoArgAction
) : StreamingPlaylistController.Listener {
    override fun selectedPlaylistId(): Long {
        return selectedPlaylistIdProvider.playlistId()
    }

    override fun setSelectedPlaylistId(playlistId: Long) {
        selectedPlaylistIdSetter.set(playlistId)
    }

    override fun loadCollections() {
        collectionsLoader.run()
    }

    override fun selectedPlaylistName(): String {
        return selectedPlaylistNameProvider.playlistName()
    }

    override fun selectedPlaylistTracks(): List<Track> {
        return selectedPlaylistTrackProvider.tracks()
    }

    override fun favoriteTracks(): List<Track> {
        return favoriteTrackProvider.tracks()
    }

    override fun selectedStreamingProvider(): StreamingProviderName? {
        return selectedProviderProvider.provider()
    }

    override fun showStreamingProviderPicker(playlistName: String, tracks: List<Track>) {
        providerPicker.show(playlistName, tracks)
    }

    override fun navigateToStreaming() {
        streamingNavigator.run()
    }

    override fun showStreamingPlaylistLoadedDialog(message: String) {
        loadedDialog.show(message)
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }

    override fun renderSelectedTab() {
        selectedTabRenderer.run()
    }
}
