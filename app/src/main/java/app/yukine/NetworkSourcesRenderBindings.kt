package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.ui.NetworkSourceActions
import app.yukine.ui.NetworkSourceLabels
import app.yukine.ui.TrackListHeaderAction

internal data class NetworkSourcesChromeState(
    val actions: List<NetworkSourceActions>,
    val headerActions: List<TrackListHeaderAction>,
    val emptyText: String,
    val labels: NetworkSourceLabels
)

internal fun interface NetworkSourcesChromeSink {
    fun publish(state: NetworkSourcesChromeState)
}

internal class NetworkSourcesRenderBindings(
    private val events: NetworkSourcesRenderController.Listener,
    private val chromeSink: NetworkSourcesChromeSink
) : NetworkSourcesRenderController.Listener {
    override fun backToNetwork() {
        events.backToNetwork()
    }

    override fun testRemoteSource(sourceId: Long) {
        events.testRemoteSource(sourceId)
    }

    override fun syncRemoteSource(sourceId: Long) {
        events.syncRemoteSource(sourceId)
    }

    override fun playRemoteSourceTracks(source: RemoteSource) {
        events.playRemoteSourceTracks(source)
    }

    override fun openRemoteSourceTracks(sourceId: Long) {
        events.openRemoteSourceTracks(sourceId)
    }

    override fun showEditWebDav(source: RemoteSource) {
        events.showEditWebDav(source)
    }

    override fun confirmDeleteRemoteSource(source: RemoteSource) {
        events.confirmDeleteRemoteSource(source)
    }

    override fun publishNetworkSourcesChrome(
        actions: List<NetworkSourceActions>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        labels: NetworkSourceLabels
    ) {
        chromeSink.publish(
            NetworkSourcesChromeState(
                actions = actions,
                headerActions = headerActions,
                emptyText = emptyText,
                labels = labels
            )
        )
    }
}
