package app.yukine

import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.QueueTrackUiState
import kotlinx.coroutines.flow.StateFlow

data class QueueDestinationState(
    val rows: List<QueueTrackUiState> = emptyList()
)

interface QueueDestinationStateProvider {
    val uiState: StateFlow<QueueDestinationState>
    val labels: StateFlow<QueueScreenLabels>
    fun onPlayAt(index: Int)
    fun onToggleFavorite(index: Int)
    fun onAddToPlaylist(index: Int)
    fun onRemove(index: Int)
    fun onMove(fromIndex: Int, toIndex: Int)
    fun onClearQueue()
    fun onBack()
}
