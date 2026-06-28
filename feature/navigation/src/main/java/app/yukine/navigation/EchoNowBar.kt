package app.yukine.navigation

import androidx.compose.runtime.Composable
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
    NowBar(
        state = state,
        waveformExpanded = false,
        onExpandWaveform = { },
        onCollapseWaveform = { },
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
