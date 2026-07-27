package app.yukine.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.yukine.ui.NowBarState
import app.yukine.ui.NowBar
import app.yukine.ui.SeekAction

@Composable
fun EchoNowBar(
    state: NowBarState,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onPrevious: Runnable,
    onPlayPause: Runnable,
    onNext: Runnable,
    onFavorite: Runnable,
    onShuffle: Runnable,
    onRepeat: Runnable,
    onSeek: SeekAction
) {
    // Reset waveform open state with track identity so a prior track cannot leave
    // one expanded frame on the next song.
    val trackIdentity = remember(
        state.track.trackId,
        state.track.contentUri,
        state.track.dataPath
    ) {
        Triple(state.track.trackId, state.track.contentUri, state.track.dataPath)
    }
    var waveformExpanded by remember(trackIdentity) { mutableStateOf(false) }
    NowBar(
        state = state,
        waveformExpanded = waveformExpanded,
        onExpandWaveform = { waveformExpanded = true },
        onCollapseWaveform = { waveformExpanded = false },
        onPrevious = onPrevious,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onFavorite = onFavorite,
        onShuffle = onShuffle,
        onRepeat = onRepeat,
        onOpenNowPlaying = Runnable { onOpenNowPlaying() },
        onOpenQueue = Runnable { onOpenQueue() },
        onSeek = onSeek
    )
}
