package app.yukine

import app.yukine.ui.StreamingTrackAction
import app.yukine.ui.UnifiedLocalTrackAction
import app.yukine.ui.UnifiedSearchActions
import app.yukine.ui.UnifiedSearchQueryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchViewModelTest {
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
}
