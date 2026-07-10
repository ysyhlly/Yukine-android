package app.yukine

import androidx.lifecycle.ViewModel
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.navigation.PlaybackSnapshotProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted

data class PlaybackViewState(
    val snapshot: PlaybackStateSnapshot = PlaybackStateSnapshot.empty(),
    val queue: List<Track> = emptyList(),
    /** Revision of the snapshot that supplied [queue], or null before a full queue is published. */
    val publishedQueueRevision: Long? = null
)

class PlaybackViewModel : ViewModel(), PlaybackSnapshotProvider {
    private val playbackState = MutableStateFlow(PlaybackViewState())
    private var lastHistoryRefreshTrackId = -1L

    val playback: StateFlow<PlaybackViewState> = playbackState.asStateFlow()

    override val playbackSnapshot: StateFlow<PlaybackStateSnapshot> = playbackState
        .map { it.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackStateSnapshot.empty())

    fun replacePlaybackSnapshot(snapshot: PlaybackStateSnapshot?): PlaybackStateSnapshot {
        val previous = playbackState.value.snapshot
        playbackState.value = playbackState.value.copy(
            snapshot = snapshot ?: PlaybackStateSnapshot.empty()
        )
        return previous
    }

    fun resetPlayback() {
        playbackState.value = PlaybackViewState()
        lastHistoryRefreshTrackId = -1L
    }

    fun updatePlayback(snapshot: PlaybackStateSnapshot?, queue: List<Track>) {
        val publishedSnapshot = snapshot ?: PlaybackStateSnapshot.empty()
        playbackState.value = PlaybackViewState(
            snapshot = publishedSnapshot,
            queue = queue.toList(),
            publishedQueueRevision = publishedSnapshot.queueRevision
        )
    }

    fun lastHistoryRefreshTrackId(): Long {
        return lastHistoryRefreshTrackId
    }

    fun setLastHistoryRefreshTrackId(trackId: Long) {
        lastHistoryRefreshTrackId = trackId
    }
}
