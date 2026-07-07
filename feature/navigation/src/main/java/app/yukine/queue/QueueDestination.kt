package app.yukine.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.yukine.QueueDestinationStateProvider
import app.yukine.ui.QueueScreen
import app.yukine.ui.QueueTrackActions

/**
 * Native Compose destination for the Queue tab.
 *
 * The screen reads immutable state and labels from a narrow provider contract via
 * [collectAsState], and forwards user actions back through the same contract. There is no
 * ComposeView factory, render controller, or imperative state push from the Java shell.
 *
 * Positional actions are rebuilt whenever the rendered rows change. Each row index maps 1:1
 * to the tracks the app ViewModel bound, so callbacks hand the index back to the provider,
 * which resolves it against its own snapshot.
 */
@Composable
fun QueueDestination(provider: QueueDestinationStateProvider, modifier: Modifier = Modifier) {
    val uiState by provider.uiState.collectAsState()
    val labels by provider.labels.collectAsState()

    val rowCount = uiState.rowCount
    val rowAt = uiState.rowAt
    val actionForIndex = remember(provider) {
        { index: Int ->
            QueueTrackActions(
                onPlay = Runnable { provider.onPlayAt(index) },
                onFavorite = Runnable { provider.onToggleFavorite(index) },
                onAddToPlaylist = Runnable { provider.onAddToPlaylist(index) },
                onRemove = Runnable { provider.onRemove(index) },
                onMove = { fromIndex, toIndex -> provider.onMove(fromIndex, toIndex) }
            )
        }
    }

    Box(modifier = modifier) {
        QueueScreen(
            trackCount = rowCount,
            trackAt = rowAt,
            actionForIndex = actionForIndex,
            onMove = { fromIndex, toIndex -> provider.onMove(fromIndex, toIndex) },
            onClearQueue = Runnable { provider.onClearQueue() },
            labels = labels,
            onBack = Runnable { provider.onBack() }
        )
    }
}
