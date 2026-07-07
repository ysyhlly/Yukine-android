package app.yukine

import app.yukine.ui.SettingsAction
import app.yukine.ui.SettingsMetric
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import java.util.ArrayList

internal fun interface TrackListPlaybackAction {
    fun play(tracks: List<Track>, index: Int)
}

internal class NetworkMenuEventController(
    private val navigator: Navigator,
    private val showAddStreamAction: Runnable,
    private val showImportM3uAction: Runnable,
    private val showAddWebDavAction: Runnable,
    private val documentPicker: DocumentPicker,
    private val streamTracksProvider: StreamTracksProvider,
    private val streamTrackCountProvider: StreamTrackCountProvider,
    private val webDavTracksProvider: WebDavTracksProvider,
    private val remoteSourcesProvider: RemoteSourcesProvider,
    private val requests: Requests,
    private val deleteConfirmation: DeleteConfirmation,
    private val player: TrackListPlaybackAction,
    private val labels: Labels,
    private val statusSink: StatusSink,
    private val networkMenuViewModel: NetworkMenuViewModel
) : NetworkMenuRenderController.Listener {
    fun interface Navigator {
        fun navigateNetworkPage(page: String)
    }

    fun interface DocumentPicker {
        fun openM3uFilePicker()
    }

    fun interface StreamTracksProvider {
        fun streamTracks(): ArrayList<Track>
    }

    fun interface StreamTrackCountProvider {
        fun streamTrackCount(): Int
    }

    fun interface WebDavTracksProvider {
        fun webDavTracks(): ArrayList<Track>
    }

    fun interface RemoteSourcesProvider {
        fun remoteSources(): List<RemoteSource>
    }

    fun interface Requests {
        fun syncAllWebDavSources(sourceIds: List<Long>)
    }

    fun interface DeleteConfirmation {
        fun confirmDeleteAllStreams()
    }

    fun interface Labels {
        fun text(key: String): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    override fun navigateNetworkPage(page: String) {
        navigator.navigateNetworkPage(page)
    }

    override fun showAddStream() {
        showAddStreamAction.run()
    }

    override fun showImportM3u() {
        showImportM3uAction.run()
    }

    override fun openM3uFilePicker() {
        documentPicker.openM3uFilePicker()
    }

    override fun playAllStreams() {
        val streams = streamTracksProvider.streamTracks()
        if (streams.isEmpty()) {
            statusSink.setStatus(labels.text("no.streams.to.play"))
            return
        }
        player.play(streams, 0)
    }

    override fun confirmDeleteAllStreams() {
        if (streamTrackCountProvider.streamTrackCount() == 0) {
            statusSink.setStatus(labels.text("no.streams.to.delete"))
            return
        }
        deleteConfirmation.confirmDeleteAllStreams()
    }

    override fun showAddWebDav() {
        showAddWebDavAction.run()
    }

    override fun syncAllWebDavSources() {
        val sourceIds = ArrayList<Long>()
        remoteSourcesProvider.remoteSources().forEach { source ->
            if (RemoteSource.TYPE_WEBDAV == source.type) {
                sourceIds.add(source.id)
            }
        }
        if (sourceIds.isEmpty()) {
            statusSink.setStatus(labels.text("no.webdav.sources"))
            return
        }
        requests.syncAllWebDavSources(sourceIds)
    }

    override fun playAllWebDavTracks() {
        val tracks = webDavTracksProvider.webDavTracks()
        if (tracks.isEmpty()) {
            statusSink.setStatus(labels.text("no.webdav.tracks.to.play"))
            return
        }
        player.play(tracks, 0)
    }

    override fun publishNetworkMenu(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ) {
        networkMenuViewModel.updateMenu(title, metrics, actions)
    }
}
