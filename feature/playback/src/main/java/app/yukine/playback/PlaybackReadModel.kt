package app.yukine.playback

import app.yukine.model.Track
import kotlinx.coroutines.flow.StateFlow

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
