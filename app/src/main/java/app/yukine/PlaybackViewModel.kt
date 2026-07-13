package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.navigation.PlaybackSnapshotProvider
import app.yukine.playback.PlaybackReadModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PlaybackViewState(
    val snapshot: PlaybackStateSnapshot = PlaybackStateSnapshot.empty(),
    val queue: List<Track> = emptyList(),
    /** Revision of the snapshot that supplied [queue], or null before a full queue is published. */
    val publishedQueueRevision: Long? = null
)

class PlaybackViewModel : ViewModel(), PlaybackSnapshotProvider {
    private val playbackState = MutableStateFlow(PlaybackViewState())
    private var readModelBinding: Job? = null

    val playback: StateFlow<PlaybackViewState> = playbackState.asStateFlow()

    override val playbackSnapshot: StateFlow<PlaybackStateSnapshot> = playbackState
        .map { it.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackStateSnapshot.empty())

    fun bind(readModel: PlaybackReadModel?) {
        readModelBinding?.cancel()
        readModelBinding = null
        if (readModel == null) {
            resetPlayback()
            return
        }
        readModelBinding = viewModelScope.launch {
            combine(readModel.state, readModel.queue) { snapshot, queue ->
                val queueMatchesSnapshot =
                    queue.revision == snapshot.queueRevision &&
                        queue.tracks.size == snapshot.queueSize
                PlaybackViewState(
                    snapshot = snapshot,
                    queue = if (queueMatchesSnapshot) queue.tracks else emptyList(),
                    publishedQueueRevision = snapshot.queueRevision.takeIf { queueMatchesSnapshot }
                )
            }.collect { next ->
                playbackState.value = next
            }
        }
    }

    fun resetPlayback() {
        playbackState.value = PlaybackViewState()
    }

}
