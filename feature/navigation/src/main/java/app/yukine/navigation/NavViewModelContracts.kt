package app.yukine.navigation

import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.NowBarState
import kotlinx.coroutines.flow.StateFlow

interface NowBarStateProvider {
    val nowBarState: StateFlow<NowBarState>
}

interface PlaybackSnapshotProvider {
    val playbackSnapshot: StateFlow<PlaybackStateSnapshot>
}
