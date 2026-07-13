package app.yukine

import app.yukine.model.RemoteSource

/** Owns navigation, source actions and typed publication for network track lists. */
internal class NetworkTrackListOwner(
    private val navigate: (String) -> Unit,
    private val clearSelectedSource: () -> Unit,
    private val syncSource: (Long) -> Unit,
    private val playSource: (RemoteSource) -> Unit,
    private val publishRequest: (NetworkTrackListRequest) -> Unit
) : NetworkTrackListRenderController.Listener {
    constructor(
        routeController: MainRouteController,
        sourceEvents: NetworkSourcesEventController,
        publisher: TrackListStatePublisher
    ) : this(
        navigate = routeController::setNetworkPage,
        clearSelectedSource = routeController::clearSelectedRemoteSource,
        syncSource = sourceEvents::syncRemoteSource,
        playSource = sourceEvents::playRemoteSourceTracks,
        publishRequest = publisher::publishNetwork
    )

    override fun navigateNetworkPage(page: String) {
        navigate(page)
    }

    override fun clearRemoteSourceAndNavigateNetworkPage(page: String) {
        clearSelectedSource()
        navigate(page)
    }

    override fun syncRemoteSource(sourceId: Long) {
        syncSource(sourceId)
    }

    override fun playRemoteSourceTracks(source: RemoteSource) {
        playSource(source)
    }

    override fun publish(request: NetworkTrackListRequest) {
        publishRequest(request)
    }
}
