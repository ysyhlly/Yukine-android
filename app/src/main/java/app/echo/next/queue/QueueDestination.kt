package app.echo.next.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.echo.next.ui.QueueScreen
import app.echo.next.ui.QueueTrackActions

/**
 * Native Compose destination for the Queue tab.
 *
 * This is the reference slice for the MVVM cutover: the screen reads its immutable state and
 * labels directly from [QueueViewModel] via [collectAsState], and forwards every user action
 * back through the ViewModel's intent methods. There is no ComposeView factory, no render
 * controller, and no imperative state push from the Java shell — the host only feeds the
 * ViewModel via [QueueViewModel.bind] and collects [QueueViewModel.intents].
 *
 * Positional actions are rebuilt whenever the rendered rows change. Each row index maps 1:1
 * to the tracks the ViewModel bound, so the action callbacks just hand the index back to the
 * ViewModel, which resolves it against its own snapshot.
 */
@Composable
fun QueueDestination(viewModel: QueueViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val labels by viewModel.labels.collectAsState()

    val rows = uiState.rows
    val actions = remember(rows) {
        rows.indices.map { index ->
            QueueTrackActions(
                onPlay = Runnable { viewModel.onPlayAt(index) },
                onFavorite = Runnable { viewModel.onToggleFavorite(index) },
                onAddToPlaylist = Runnable { viewModel.onAddToPlaylist(index) },
                onRemove = Runnable { viewModel.onRemove(index) },
                onMove = { fromIndex, toIndex -> viewModel.onMove(fromIndex, toIndex) }
            )
        }
    }

    Box(modifier = modifier) {
        QueueScreen(
            tracks = rows,
            actions = actions,
            onClearQueue = Runnable { viewModel.onClearQueue() },
            labels = labels,
            onBack = Runnable { viewModel.onBack() }
        )
    }
}
