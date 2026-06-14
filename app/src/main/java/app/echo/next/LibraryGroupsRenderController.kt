package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.model.Track
import app.echo.next.ui.LibraryGroupActions
import app.echo.next.ui.LibraryGroupUiState
import app.echo.next.ui.LibraryGroupsScreenFactory
import app.echo.next.ui.TrackListHeaderAction
import app.echo.next.ui.TrackListHeaderMetric
import app.echo.next.ui.TrackListModeAction
import java.util.ArrayList

internal class LibraryGroupsRenderController(
    private val context: Context,
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun selectLibraryGroup(key: String, title: String)

        fun clearLibraryGroupSelection()

        fun closeLibraryGroup()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun publishLibraryGroups(title: String, rows: ArrayList<LibraryGroupUiState>)

        fun renderTrackList(
            title: String,
            tracks: ArrayList<Track>,
            headerMetrics: ArrayList<TrackListHeaderMetric>,
            headerActions: ArrayList<TrackListHeaderAction>
        )

        fun addVirtualContent(view: View)
    }

    fun render(
        visibleTracks: List<Track>,
        libraryMode: String,
        selectedLibraryGroupKey: String,
        selectedLibraryGroupTitle: String,
        modeActions: List<TrackListModeAction>
    ) {
        val groups = LibraryGrouping.groupTracks(visibleTracks, libraryMode)
        if (selectedLibraryGroupKey.isNotEmpty()) {
            val selectedTracks = groups[selectedLibraryGroupKey]
            if (selectedTracks != null) {
                renderGroupDetail(selectedLibraryGroupTitle, selectedTracks)
                return
            }
        }

        listener.clearLibraryGroupSelection()
        val groupRows = ArrayList<LibraryGroupUiState>()
        val groupActions = ArrayList<LibraryGroupActions>()
        for ((key, tracks) in groups) {
            val title = LibraryGrouping.groupTitle(key, libraryMode)
            val rowId = "$libraryMode:${if (key.isEmpty()) "unknown" else key}"
            groupRows.add(LibraryGroupUiState(rowId, title, LibraryGrouping.groupSubtitle(tracks, libraryMode)))
            groupActions.add(
                LibraryGroupActions(
                    Runnable { listener.selectLibraryGroup(key, title) },
                    Runnable { listener.playTrackList(tracks, 0) }
                )
            )
        }

        val title = LibraryGrouping.modeTitle(libraryMode)
        viewModel.updateLibraryGroups(title, groupRows)
        listener.addVirtualContent(
            LibraryGroupsScreenFactory.create(
                context,
                viewModel.libraryGroups,
                groupActions,
                "No $title groups",
                modeActions
            )
        )
    }

    private fun renderGroupDetail(selectedLibraryGroupTitle: String, tracks: ArrayList<Track>) {
        val headerMetrics = ArrayList<TrackListHeaderMetric>()
        headerMetrics.add(TrackListHeaderMetric("Metric", tracks.size.toString()))
        val headerActions = ArrayList<TrackListHeaderAction>()
        headerActions.add(TrackListHeaderAction("Back", Runnable {
            listener.closeLibraryGroup()
        }))
        headerActions.add(TrackListHeaderAction("Play group", Runnable {
            listener.playTrackList(tracks, 0)
        }))
        listener.renderTrackList(selectedLibraryGroupTitle, tracks, headerMetrics, headerActions)
    }
}
