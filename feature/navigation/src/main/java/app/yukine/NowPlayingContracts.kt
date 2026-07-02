package app.yukine

import app.yukine.model.Track
import app.yukine.navigation.NowBarStateProvider
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.LyricUiLine
import app.yukine.ui.NowBarState
import app.yukine.ui.nowBarEmptyState
import kotlinx.coroutines.flow.StateFlow

sealed interface NowPlayingEvent {
    data object PlayPause : NowPlayingEvent
    data object Next : NowPlayingEvent
    data object Previous : NowPlayingEvent
    data class SeekTo(val positionMs: Long) : NowPlayingEvent
    data object ToggleLyrics : NowPlayingEvent
    data object OpenQueue : NowPlayingEvent
    data object ToggleFavorite : NowPlayingEvent
    data object AddToPlaylist : NowPlayingEvent
    data object ShareCurrentTrack : NowPlayingEvent
    data object DownloadCurrentTrack : NowPlayingEvent
    data object ToggleShuffle : NowPlayingEvent
    data object CycleRepeatMode : NowPlayingEvent
}

enum class RepeatModeUi {
    Off,
    All,
    One
}

data class LyricsUiState(
    val title: String = "",
    val status: String = "",
    val lines: List<LyricUiLine> = emptyList()
)

data class NowPlayingUiState @JvmOverloads constructor(
    val trackTitle: String = "",
    val artist: String = "",
    val album: String? = null,
    val coverUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lyricsVisible: Boolean = false,
    val lyrics: LyricsUiState = LyricsUiState(),
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeUi = RepeatModeUi.All,
    val errorMessage: String? = null,
    val trackId: Long = -1L,
    val currentTrack: Track? = null,
    val overlayState: NowBarState = nowBarEmptyState(),
    val appVolume: Float = 1.0f
)

interface NowPlayingScreenStateProvider : NowBarStateProvider {
    val uiState: StateFlow<NowPlayingUiState>
    fun switchSource(
        track: Track?,
        provider: StreamingProviderName?,
        providerTrackId: String?,
        quality: StreamingAudioQuality?
    )
}
