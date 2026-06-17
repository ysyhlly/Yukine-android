package app.echo.next.now

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.echo.next.NowPlayingViewModel
import app.echo.next.ui.EchoStateCard

@Composable
fun NowPlayingDestination(viewModel: NowPlayingViewModel) {
    val state by viewModel.uiState.collectAsState()
    val track = state.currentTrack
    if (track == null || state.trackId < 0L) {
        EchoStateCard(
            title = "No track selected",
            description = "Play a track to open Now Playing."
        )
        return
    }
    app.echo.next.ui.NowPlayingScreen(
        state = app.echo.next.ui.NowPlayingUiState(
            pageTitle = "Now",
            title = state.trackTitle,
            subtitle = listOfNotNull(state.artist.takeIf { it.isNotBlank() }, state.album).joinToString(" / "),
            queueMetricLabel = "Elapsed",
            queueLabel = state.overlayState.elapsed,
            durationMetricLabel = "Duration",
            durationLabel = app.echo.next.model.Track.formatDuration(state.durationMs),
            statusLabel = state.errorMessage.orEmpty(),
            albumArtUri = track.albumArtUri,
            lyricsTitle = state.lyrics.title,
            lyricsStatus = state.lyrics.status,
            lyrics = state.lyrics.lines,
            artistName = state.artist,
            albumName = state.album.orEmpty(),
            audioSpec = track.audioSpecSummary()
        )
    )
}
