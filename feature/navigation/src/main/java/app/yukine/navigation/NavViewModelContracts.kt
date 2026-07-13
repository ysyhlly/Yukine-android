package app.yukine.navigation

import app.yukine.playback.PlaybackStateSnapshot
import kotlinx.coroutines.flow.StateFlow

interface PlaybackSnapshotProvider {
    val playbackSnapshot: StateFlow<PlaybackStateSnapshot>
}
