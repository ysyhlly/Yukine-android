package app.yukine

import app.yukine.model.Track
import app.yukine.ui.LibraryGroupActions
import app.yukine.ui.LibraryGroupUiState
import app.yukine.ui.TrackListHeaderAction
import app.yukine.ui.TrackListHeaderMetric
import app.yukine.ui.TrackListModeAction
import java.util.ArrayList

internal class LibraryGroupsRenderController(
    private val viewModel: LibraryViewModel,
    private val listener: Listener
) {
    interface Listener {
        fun selectLibraryGroup(key: String, title: String)

        fun clearLibraryGroupSelection()

        fun closeLibraryGroup()

        fun playTrackList(tracks: List<Track>, index: Int)

        fun confirmDeleteGroup(title: String, tracks: List<Track>)

        fun publishLibraryGroups(title: String, rows: ArrayList<LibraryGroupUiState>)

        fun publishLibraryGroupsChrome(
            actions: List<LibraryGroupActions>,
            emptyText: String,
            modeActions: List<TrackListModeAction>
        )

        fun renderTrackList(
            title: String,
            tracks: ArrayList<Track>,
            headerMetrics: ArrayList<TrackListHeaderMetric>,
            headerActions: ArrayList<TrackListHeaderAction>
        )
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
            groupRows.add(
                LibraryGroupUiState(
                    rowId,
                    title,
                    LibraryGrouping.groupSubtitle(tracks, libraryMode),
                    LibraryGrouping.groupArtworkUri(tracks, libraryMode)
                )
            )
            groupActions.add(
                LibraryGroupActions(
                    Runnable { listener.selectLibraryGroup(key, title) },
                    Runnable { listener.playTrackList(tracks, 0) },
                    true,
                    Runnable { listener.confirmDeleteGroup(title, tracks) }
                )
            )
        }

        val title = LibraryGrouping.modeTitle(libraryMode)
        val emptyText = "No $title groups"
        viewModel.clearTrackList()
        viewModel.updateLibraryGroups(title, groupRows)
        listener.publishLibraryGroupsChrome(groupActions, emptyText, modeActions)
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
