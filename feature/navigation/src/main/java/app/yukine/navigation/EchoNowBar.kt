package app.yukine.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var waveformExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.trackId, state.contentUri, state.dataPath) {
        waveformExpanded = false
    }
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
