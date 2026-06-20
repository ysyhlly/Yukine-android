package app.yukine

import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels

internal fun interface NetworkPageAction {
    fun run(page: String)
}

internal fun interface RemoteSourceIdAction {
    fun run(sourceId: Long)
}

internal fun interface RemoteSourceAction {
    fun run(source: RemoteSource)
}

internal data class NetworkTrackListRequest(
    val title: String,
    val tracks: List<Track>,
    val showPlaylistAction: Boolean,
    val details: List<String>,
    val showStreamActions: Boolean,
    val headerMetrics: List<TrackListHeaderMetric>,
    val headerActions: List<TrackListHeaderAction>,
    val emptyText: String,
    val labels: TrackListLabels
)

internal fun interface NetworkTrackListRenderer {
    fun render(request: NetworkTrackListRequest)
}

internal class NetworkTrackListRenderBindings(
    private val navigateNetworkPageAction: NetworkPageAction,
    private val clearRemoteSourceAndNavigateAction: NetworkPageAction,
    private val syncRemoteSourceAction: RemoteSourceIdAction,
    private val playRemoteSourceTracksAction: RemoteSourceAction,
    private val playTrackListAction: TrackListPlaybackAction,
    private val trackListRenderer: NetworkTrackListRenderer
) : NetworkTrackListRenderController.Listener {
    override fun navigateNetworkPage(page: String) {
        navigateNetworkPageAction.run(page)
    }

    override fun clearRemoteSourceAndNavigateNetworkPage(page: String) {
        clearRemoteSourceAndNavigateAction.run(page)
    }

    override fun syncRemoteSource(sourceId: Long) {
        syncRemoteSourceAction.run(sourceId)
    }

    override fun playRemoteSourceTracks(source: RemoteSource) {
        playRemoteSourceTracksAction.run(source)
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        playTrackListAction.play(tracks, index)
    }

    override fun renderTrackList(
        title: String,
        tracks: List<Track>,
        showPlaylistAction: Boolean,
        details: List<String>,
        showStreamActions: Boolean,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        labels: TrackListLabels
    ) {
        trackListRenderer.render(
            NetworkTrackListRequest(
                title = title,
                tracks = tracks,
                showPlaylistAction = showPlaylistAction,
                details = details,
                showStreamActions = showStreamActions,
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                emptyText = emptyText,
                labels = labels
            )
        )
    }
}
