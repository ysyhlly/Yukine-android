package app.echo.next.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.echo.next.NowPlayingEvent
import app.echo.next.NowPlayingViewModel
import app.echo.next.ui.NowBar
import app.echo.next.ui.SeekAction

@Composable
fun EchoNowBar(
    viewModel: NowPlayingViewModel,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val overlay = state.overlayState
    var waveformExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(overlay.trackId, overlay.contentUri, overlay.dataPath) {
        waveformExpanded = false
    }

    NowBar(
        state = overlay,
        waveformExpanded = waveformExpanded,
        onExpandWaveform = { waveformExpanded = true },
        onCollapseWaveform = { waveformExpanded = false },
        onPrevious = Runnable { viewModel.onEvent(NowPlayingEvent.Previous) },
        onPlayPause = Runnable { viewModel.onEvent(NowPlayingEvent.PlayPause) },
        onNext = Runnable { viewModel.onEvent(NowPlayingEvent.Next) },
        onFavorite = Runnable { viewModel.onEvent(NowPlayingEvent.ToggleFavorite) },
        onShuffle = Runnable { viewModel.onEvent(NowPlayingEvent.ToggleShuffle) },
        onRepeat = Runnable { viewModel.onEvent(NowPlayingEvent.CycleRepeatMode) },
        onOpenNowPlaying = Runnable { onOpenNowPlaying() },
        onOpenQueue = Runnable { onOpenQueue() },
        onSeek = SeekAction { positionMs -> viewModel.onEvent(NowPlayingEvent.SeekTo(positionMs)) }
    )
}
