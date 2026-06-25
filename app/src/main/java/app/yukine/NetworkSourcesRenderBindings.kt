package app.yukine

import app.yukine.model.RemoteSource

internal class NetworkSourcesRenderBindings(
    private val events: NetworkSourcesRenderController.Listener
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
}
