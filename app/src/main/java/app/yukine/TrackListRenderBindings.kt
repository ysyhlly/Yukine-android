package app.yukine

import app.yukine.model.Track
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListLabels
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackRowActions

internal fun interface LibraryEventSink {
    fun send(event: LibraryEvent)
}

internal fun interface TrackAction {
    fun run(track: Track)
}

internal fun interface TrackListAction {
    fun run(tracks: List<Track>)
}

internal data class TrackListChromeState(
    val actions: List<TrackRowActions>,
    val headerMetrics: List<TrackListHeaderMetric>,
    val headerActions: List<TrackListHeaderAction>,
    val emptyText: String,
    val modeActions: List<TrackListModeAction>,
    val labels: TrackListLabels
)

internal fun interface TrackListChromeSink {
    fun publish(state: TrackListChromeState)
}

internal class TrackListRenderBindings(
    private val libraryEventSink: LibraryEventSink,
    private val editStreamAction: TrackAction,
    private val confirmDeleteTrackAction: TrackAction,
    private val downloadTrackAction: TrackAction,
    private val downloadTracksAction: TrackListAction,
    private val chromeSink: TrackListChromeSink
) : TrackListRenderController.Listener {
    override fun playTrackList(tracks: List<Track>, index: Int) {
        libraryEventSink.send(LibraryEvent.PlayTrackList(tracks, index))
    }

    override fun toggleFavorite(track: Track) {
        libraryEventSink.send(LibraryEvent.ToggleFavorite(track))
    }

    override fun showAddToPlaylist(track: Track) {
        libraryEventSink.send(LibraryEvent.AddToPlaylist(track))
    }

    override fun downloadTrack(track: Track) {
        downloadTrackAction.run(track)
    }

    override fun downloadTracks(tracks: List<Track>) {
        downloadTracksAction.run(tracks)
    }

    override fun showEditStream(track: Track) {
        editStreamAction.run(track)
    }

    override fun confirmDeleteTrack(track: Track) {
        confirmDeleteTrackAction.run(track)
    }

    override fun publishTrackListChrome(
        actions: List<TrackRowActions>,
        headerMetrics: List<TrackListHeaderMetric>,
        headerActions: List<TrackListHeaderAction>,
        emptyText: String,
        modeActions: List<TrackListModeAction>,
        labels: TrackListLabels
    ) {
        chromeSink.publish(
            TrackListChromeState(
                actions = ArrayList(actions),
                headerMetrics = ArrayList(headerMetrics),
                headerActions = ArrayList(headerActions),
                emptyText = emptyText,
                modeActions = ArrayList(modeActions),
                labels = labels
            )
        )
    }
}
