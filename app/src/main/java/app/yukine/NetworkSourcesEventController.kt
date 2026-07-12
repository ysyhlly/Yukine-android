package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.model.Track
import java.util.ArrayList

internal class NetworkSourcesEventController(
    private val routeController: MainRouteController,
    private val requestController: NetworkRequestController,
    private val remoteSourceNameProvider: RemoteSourceNameProvider,
    private val webDavTracksForSourceProvider: WebDavTracksForSourceProvider,
    private val showEditWebDavAction: ShowEditWebDavAction,
    private val deleteConfirmation: DeleteConfirmation,
    private val player: TrackListPlaybackAction,
    private val labels: Labels,
    private val statusSink: StatusSink,
    private val renderer: Renderer
) : NetworkSourcesRenderController.Listener {
    fun interface RemoteSourceNameProvider {
        fun remoteSourceName(sourceId: Long): String
    }

    fun interface WebDavTracksForSourceProvider {
        fun webDavTracksForSource(sourceId: Long): ArrayList<Track>
    }

    fun interface ShowEditWebDavAction {
        fun showEditWebDav(source: RemoteSource)
    }

    fun interface DeleteConfirmation {
        fun confirmDeleteRemoteSource(source: RemoteSource)
    }

    fun interface Labels {
        fun text(key: String): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    fun interface Renderer {
        fun renderSelectedTabAfterStateChange()
    }

    override fun backToNetwork() {
        val result = routeController.applyBackNavigation()
        if (!result.handled) {
            routeController.clearSelectedRemoteSource()
            routeController.setNetworkPage(MainRoutes.NETWORK_HOME)
        }
        renderer.renderSelectedTabAfterStateChange()
    }

    override fun testRemoteSource(sourceId: Long) {
        requestController.testRemoteSource(sourceId)
    }

    override fun syncRemoteSource(sourceId: Long) {
        requestController.syncRemoteSource(sourceId, remoteSourceNameProvider.remoteSourceName(sourceId))
    }

    override fun playRemoteSourceTracks(source: RemoteSource) {
        val tracks = webDavTracksForSourceProvider.webDavTracksForSource(source.id)
        if (tracks.isEmpty()) {
            statusSink.setStatus(labels.text("no.source.tracks.to.play"))
            return
        }
        player.play(tracks, 0)
    }

    override fun openRemoteSourceTracks(sourceId: Long) {
        routeController.setSelectedRemoteSourceId(sourceId)
        routeController.setNetworkPage(MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS)
        renderer.renderSelectedTabAfterStateChange()
    }

    override fun showEditWebDav(source: RemoteSource) {
        showEditWebDavAction.showEditWebDav(source)
    }

    override fun confirmDeleteRemoteSource(source: RemoteSource) {
        deleteConfirmation.confirmDeleteRemoteSource(source)
    }

}
