package app.yukine.now

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yukine.NowPlayingEvent
import app.yukine.NowPlayingViewModel
import app.yukine.ui.EchoStateCard
import app.yukine.ui.NowPlayingGestureActions

@Composable
fun NowPlayingDestination(
    viewModel: NowPlayingViewModel,
    defaultImmersive: Boolean = false,
    onDefaultImmersiveConsumed: () -> Unit = {},
    gesturesEnabled: Boolean = true,
    onAppVolumeChanged: (Float) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val track = state.currentTrack
    if (track == null || state.trackId < 0L) {
        EchoStateCard(
            title = "还没有正在播放",
            description = "播放一首歌后，这里会显示封面、歌词和队列信息。"
        )
        return
    }
    app.yukine.ui.NowPlayingScreen(
        state = app.yukine.ui.NowPlayingUiState(
            pageTitle = "正在播放",
            title = state.trackTitle,
            subtitle = listOfNotNull(state.artist.takeIf { it.isNotBlank() }, state.album).joinToString(" / "),
            queueMetricLabel = "已播放",
            queueLabel = state.overlayState.elapsed,
            durationMetricLabel = "总时长",
            durationLabel = app.yukine.model.Track.formatDuration(state.durationMs),
            statusLabel = state.errorMessage.orEmpty(),
            albumArtUri = track.albumArtUri,
            lyricsTitle = state.lyrics.title,
            lyricsStatus = state.lyrics.status,
            lyrics = state.lyrics.lines,
            artistName = state.artist,
            albumName = state.album.orEmpty(),
            audioSpec = track.audioSpecSummary(),
            appVolume = state.appVolume
        ),
        defaultImmersive = defaultImmersive,
        onDefaultImmersiveConsumed = onDefaultImmersiveConsumed,
        gestureActions = if (gesturesEnabled) {
            NowPlayingGestureActions(
                onPrevious = Runnable { viewModel.onEvent(NowPlayingEvent.Previous) },
                onNext = Runnable { viewModel.onEvent(NowPlayingEvent.Next) },
                onVolumeChange = onAppVolumeChanged
            )
        } else {
            NowPlayingGestureActions.Empty
        }
    )
}
