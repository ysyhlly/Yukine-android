package app.yukine.playback

import app.yukine.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackConnectionState {
    Disconnected,
    Connecting,
    Connected
}

data class PlaybackQueueSnapshot(
    val revision: Long = Long.MIN_VALUE,
    val currentIndex: Int = -1,
    val tracks: List<Track> = emptyList()
)

/**
 * The observable application boundary for playback state.
 *
 * The service remains the runtime owner. UI state holders consume this read-only projection and
 * never bind to, cast, or retain the concrete service implementation.
 */
interface PlaybackReadModel {
    val state: StateFlow<PlaybackStateSnapshot>
    val queue: StateFlow<PlaybackQueueSnapshot>
    val connection: StateFlow<PlaybackConnectionState>
}

internal class MutablePlaybackReadModel : PlaybackReadModel {
    private val mutableState = MutableStateFlow(PlaybackStateSnapshot.empty())
    override val state: StateFlow<PlaybackStateSnapshot> = mutableState.asStateFlow()

    private val mutableQueue = MutableStateFlow(PlaybackQueueSnapshot())
    override val queue: StateFlow<PlaybackQueueSnapshot> = mutableQueue.asStateFlow()

    private val mutableConnection = MutableStateFlow(PlaybackConnectionState.Disconnected)
    override val connection: StateFlow<PlaybackConnectionState> = mutableConnection.asStateFlow()

    fun markConnecting() {
        mutableConnection.value = PlaybackConnectionState.Connecting
    }

    fun markConnected() {
        mutableConnection.value = PlaybackConnectionState.Connected
    }

    fun publish(snapshot: PlaybackStateSnapshot, queueProvider: () -> List<Track>) {
        val publishedQueue = mutableQueue.value
        if (
            publishedQueue.revision != snapshot.queueRevision ||
            publishedQueue.tracks.size != snapshot.queueSize
        ) {
            mutableQueue.value = PlaybackQueueSnapshot(
                revision = snapshot.queueRevision,
                currentIndex = snapshot.currentIndex,
                tracks = queueProvider().toList()
            )
        } else if (publishedQueue.currentIndex != snapshot.currentIndex) {
            mutableQueue.value = publishedQueue.copy(currentIndex = snapshot.currentIndex)
        }
        mutableState.value = snapshot
    }

    fun clear() {
        mutableConnection.value = PlaybackConnectionState.Disconnected
        mutableQueue.value = PlaybackQueueSnapshot()
        mutableState.value = PlaybackStateSnapshot.empty()
    }
}
