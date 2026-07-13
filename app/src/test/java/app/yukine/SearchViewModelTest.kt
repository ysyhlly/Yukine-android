package app.yukine

import app.yukine.ui.StreamingTrackAction
import app.yukine.ui.UnifiedLocalTrackAction
import app.yukine.ui.UnifiedSearchActions
import app.yukine.ui.UnifiedSearchQueryAction
import app.yukine.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = LibraryMainDispatcherRule()

    @Test
    fun updateResultsPreservesSearchInputActions() {
        val viewModel = SearchViewModel()
        var latestQuery = ""
        val actions = UnifiedSearchActions(
            onQueryChange = UnifiedSearchQueryAction { query -> latestQuery = query },
            onSearch = UnifiedSearchQueryAction { },
            onPlayLocalTrack = UnifiedLocalTrackAction { },
            onPlayStreamingTrack = StreamingTrackAction { },
            onLoadMoreStreaming = Runnable { },
            onExit = Runnable { }
        )

        viewModel.updateActions(actions)
        viewModel.updateResults("echo", emptyList())
        viewModel.uiState.value.actions.onQueryChange.run("周杰伦")

        assertSame(actions, viewModel.uiState.value.actions)
        assertEquals("周杰伦", latestQuery)
    }

    @Test
    fun updateResultsDoesNotOverwriteUnsubmittedInputWithStaleRouteQuery() {
        val viewModel = SearchViewModel()

        viewModel.updateQuery("周杰伦")
        viewModel.updateResults("", emptyList())

        assertEquals("周杰伦", viewModel.uiState.value.query)
    }

    @Test
    fun routeAndLibraryFlowsDriveLocalResultsWithoutTabRendering() = runTest {
        val viewModel = SearchViewModel()
        val query = MutableStateFlow("echo")
        val library = MutableStateFlow(
            LibraryStoreState(allTracks = listOf(track(1L, "Echo One"), track(2L, "Other")))
        )

        viewModel.bindStateSources(query, library) { tracks, query ->
            tracks.filter { it.title.contains(query, ignoreCase = true) }
        }
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.uiState.value.localTracks.map { it.id })

        library.value = LibraryStoreState(allTracks = listOf(track(3L, "Echo Three")))
        advanceUntilIdle()

        assertEquals(listOf(3L), viewModel.uiState.value.localTracks.map { it.id })
    }

    @Test
    fun clearSearchPreservesBoundActions() {
        val viewModel = SearchViewModel()
        val actions = UnifiedSearchActions(
            onQueryChange = UnifiedSearchQueryAction { },
            onSearch = UnifiedSearchQueryAction { },
            onPlayLocalTrack = UnifiedLocalTrackAction { },
            onPlayStreamingTrack = StreamingTrackAction { },
            onLoadMoreStreaming = Runnable { },
            onExit = Runnable { }
        )
        viewModel.updateActions(actions)

        viewModel.clearSearch()

        assertSame(actions, viewModel.uiState.value.actions)
    }

    private fun track(id: Long, title: String): Track =
        Track(id, title, "Artist", "Album", 120_000L, null, "file:$id.mp3")
}
