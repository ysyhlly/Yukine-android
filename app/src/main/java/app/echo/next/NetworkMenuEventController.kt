package app.echo.next

import app.echo.next.ui.SettingsAction
import app.echo.next.ui.SettingsMetric
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import java.util.ArrayList

internal class NetworkMenuEventController(
    private val navigator: Navigator,
    private val dialogs: Dialogs,
    private val documentPicker: DocumentPicker,
    private val librarySource: LibrarySource,
    private val requests: Requests,
    private val deleteConfirmation: DeleteConfirmation,
    private val player: Player,
    private val labels: Labels,
    private val statusSink: StatusSink,
    private val contentSink: ContentSink
) : NetworkMenuRenderController.Listener {
    fun interface Navigator {
        fun navigateNetworkPage(page: String)
    }

    interface Dialogs {
        fun showAddStream()

        fun showImportM3u()

        fun showAddWebDav()
    }

    fun interface DocumentPicker {
        fun openM3uFilePicker()
    }

    interface LibrarySource {
        fun streamTracks(): ArrayList<Track>

        fun streamTrackCount(): Int

        fun webDavTracks(): ArrayList<Track>

        fun remoteSources(): List<RemoteSource>
    }

    fun interface Requests {
        fun syncAllWebDavSources(sourceIds: List<Long>)
    }

    fun interface DeleteConfirmation {
        fun confirmDeleteAllStreams()
    }

    fun interface Player {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface Labels {
        fun text(key: String): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    interface ContentSink {
        fun publishNetworkMenu(title: String, metrics: List<SettingsMetric>, actions: List<SettingsAction>) = Unit
    }

    override fun navigateNetworkPage(page: String) {
        navigator.navigateNetworkPage(page)
    }

    override fun showAddStream() {
        dialogs.showAddStream()
    }

    override fun showImportM3u() {
        dialogs.showImportM3u()
    }

    override fun openM3uFilePicker() {
        documentPicker.openM3uFilePicker()
    }

    override fun playAllStreams() {
        val streams = librarySource.streamTracks()
        if (streams.isEmpty()) {
            statusSink.setStatus(labels.text("no.streams.to.play"))
            return
        }
        player.playTrackList(streams, 0)
    }

    override fun confirmDeleteAllStreams() {
        if (librarySource.streamTrackCount() == 0) {
            statusSink.setStatus(labels.text("no.streams.to.delete"))
            return
        }
        deleteConfirmation.confirmDeleteAllStreams()
    }

    override fun showAddWebDav() {
        dialogs.showAddWebDav()
    }

    override fun syncAllWebDavSources() {
        val sourceIds = ArrayList<Long>()
        librarySource.remoteSources().forEach { source ->
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
        val tracks = librarySource.webDavTracks()
        if (tracks.isEmpty()) {
            statusSink.setStatus(labels.text("no.webdav.tracks.to.play"))
            return
        }
        player.playTrackList(tracks, 0)
    }

    override fun publishNetworkMenu(
        title: String,
        metrics: List<SettingsMetric>,
        actions: List<SettingsAction>
    ) {
        contentSink.publishNetworkMenu(title, metrics, actions)
    }
}
