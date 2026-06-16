package app.echo.next

import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.ui.NetworkSourceActions
import app.echo.next.ui.NetworkSourceLabels
import app.echo.next.ui.NetworkSourceUiState
import app.echo.next.ui.TrackListHeaderAction
import java.util.ArrayList

internal class NetworkSourcesEventController(
    private val routeController: MainRouteController,
    private val requestController: NetworkRequestController,
    private val librarySource: LibrarySource,
    private val dialogs: Dialogs,
    private val deleteConfirmation: DeleteConfirmation,
    private val player: Player,
    private val labels: Labels,
    private val statusSink: StatusSink,
    private val statePublisher: MainStatePublisher,
    private val renderer: Renderer
) : NetworkSourcesRenderController.Listener {
    interface LibrarySource {
        fun remoteSourceName(sourceId: Long): String

        fun webDavTracksForSource(sourceId: Long): ArrayList<Track>
    }

    fun interface Dialogs {
        fun showEditWebDav(source: RemoteSource)
    }

    fun interface DeleteConfirmation {
        fun confirmDeleteRemoteSource(source: RemoteSource)
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

    fun interface Renderer {
        fun renderAndPersistSelectedTab()
    }

    override fun backToNetwork() {
        routeController.clearSelectedRemoteSource()
        routeController.setNetworkPage(MainRoutes.NETWORK_HOME)
        renderer.renderAndPersistSelectedTab()
    }

    override fun testRemoteSource(sourceId: Long) {
        requestController.testRemoteSource(sourceId)
    }

    override fun syncRemoteSource(sourceId: Long) {
        requestController.syncRemoteSource(sourceId, librarySource.remoteSourceName(sourceId))
    }

    override fun playRemoteSourceTracks(source: RemoteSource) {
        val tracks = librarySource.webDavTracksForSource(source.id)
        if (tracks.isEmpty()) {
            statusSink.setStatus(labels.text("no.source.tracks.to.play"))
            return
        }
        player.playTrackList(tracks, 0)
    }

    override fun openRemoteSourceTracks(sourceId: Long) {
        routeController.setSelectedRemoteSourceId(sourceId)
        routeController.setNetworkPage(MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS)
        renderer.renderAndPersistSelectedTab()
    }

    override fun showEditWebDav(source: RemoteSource) {
        dialogs.showEditWebDav(source)
    }

    override fun confirmDeleteRemoteSource(source: RemoteSource) {
        deleteConfirmation.confirmDeleteRemoteSource(source)
    }

    override fun publishNetworkSources(title: String, rows: ArrayList<NetworkSourceUiState>) {
        statePublisher.publishNetworkSources(title, rows)
    }

    override fun publishNetworkSourcesChrome(
        actions: List<NetworkSourceActions>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        labels: NetworkSourceLabels
    ) = Unit
}
