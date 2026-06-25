package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.model.Track
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

}
