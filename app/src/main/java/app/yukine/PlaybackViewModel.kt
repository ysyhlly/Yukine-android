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

data class MainActivityPlaybackState(
    val snapshot: PlaybackStateSnapshot = PlaybackStateSnapshot.empty(),
    val queue: List<Track> = emptyList()
)

class PlaybackViewModel : ViewModel(), PlaybackSnapshotProvider {
    private val playbackState = MutableStateFlow(MainActivityPlaybackState())
    private var lastHistoryRefreshTrackId = -1L

    val playback: StateFlow<MainActivityPlaybackState> = playbackState.asStateFlow()

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
        playbackState.value = MainActivityPlaybackState()
        lastHistoryRefreshTrackId = -1L
    }

    fun updatePlayback(snapshot: PlaybackStateSnapshot?, queue: List<Track>) {
        playbackState.value = MainActivityPlaybackState(
            snapshot = snapshot ?: PlaybackStateSnapshot.empty(),
            queue = queue.toList()
        )
    }

    fun lastHistoryRefreshTrackId(): Long {
        return lastHistoryRefreshTrackId
    }

    fun setLastHistoryRefreshTrackId(trackId: Long) {
        lastHistoryRefreshTrackId = trackId
    }
}
