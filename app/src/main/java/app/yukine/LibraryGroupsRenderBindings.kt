package app.yukine

import app.yukine.model.Track
import app.yukine.MainActivityLibraryGroupsUiState
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import app.yukine.ui.TrackListAlbumCardUiState
import java.util.ArrayList

internal fun interface LibraryGroupSelectionClearer {
    fun clear()
}

internal fun interface TrackGroupDeleteConfirmer {
    fun confirm(title: String, tracks: List<Track>)
}

internal data class LibraryGroupsChromeState(
    val actions: List<LibraryGroupActions>,
    val emptyText: String,
    val modeActions: List<TrackListModeAction>
)

internal fun interface LibraryGroupsChromeSink {
    fun publish(state: LibraryGroupsChromeState)
}

internal fun interface LibraryGroupsChromeStateSink {
    fun publish(state: LibraryGroupsChromeState)
}

internal data class LibraryGroupTrackListRequest(
    val title: String,
    val tracks: ArrayList<Track>,
    val headerMetrics: ArrayList<TrackListHeaderMetric>,
    val headerActions: ArrayList<TrackListHeaderAction>,
    val footerAlbums: ArrayList<TrackListAlbumCardUiState> = ArrayList()
)

internal fun interface LibraryGroupTrackListRenderer {
    fun render(request: LibraryGroupTrackListRequest)
}

internal class LibraryGroupsRenderBindings(
    private val libraryEventSink: LibraryEventSink,
    private val libraryGroupSelectionClearer: LibraryGroupSelectionClearer,
    private val openFavoritesCollectionAction: Runnable,
    private val confirmDeleteGroupAction: TrackGroupDeleteConfirmer,
    private val chromeSink: LibraryGroupsChromeSink,
    private val trackListRenderer: LibraryGroupTrackListRenderer,
    private val chromeStateSink: LibraryGroupsChromeStateSink? = null
) : LibraryGroupsRenderController.Listener {
    override fun selectLibraryGroup(key: String, title: String) {
        libraryEventSink.send(LibraryEvent.OpenGroup(key, title))
    }

    override fun clearLibraryGroupSelection() {
        libraryGroupSelectionClearer.clear()
    }

    override fun closeLibraryGroup() {
        libraryEventSink.send(LibraryEvent.BackFromGroup)
    }

    override fun openFavoritesCollection() {
        openFavoritesCollectionAction.run()
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        libraryEventSink.send(LibraryEvent.PlayTrackList(tracks, index))
    }

    override fun confirmDeleteGroup(title: String, tracks: List<Track>) {
        confirmDeleteGroupAction.confirm(title, tracks)
    }

    override fun publishLibraryGroupsChrome(
        actions: List<LibraryGroupActions>,
        emptyText: String,
        modeActions: List<TrackListModeAction>
    ) {
        chromeSink.publish(
            LibraryGroupsChromeState(
                actions = ArrayList(actions),
                emptyText = emptyText,
                modeActions = ArrayList(modeActions)
            )
        )
        chromeStateSink?.publish(
            LibraryGroupsChromeState(
                actions = ArrayList(actions),
                emptyText = emptyText,
                modeActions = ArrayList(modeActions)
            )
        )
    }

    override fun renderTrackList(
        title: String,
        tracks: ArrayList<Track>,
        headerMetrics: ArrayList<TrackListHeaderMetric>,
        headerActions: ArrayList<TrackListHeaderAction>,
        footerAlbums: ArrayList<TrackListAlbumCardUiState>
    ) {
        trackListRenderer.render(
            LibraryGroupTrackListRequest(
                title = title,
                tracks = tracks,
                headerMetrics = headerMetrics,
                headerActions = headerActions,
                footerAlbums = footerAlbums
            )
        )
    }
}
