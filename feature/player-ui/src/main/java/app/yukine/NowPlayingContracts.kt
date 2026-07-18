package app.yukine

import app.yukine.model.Track
import app.yukine.model.LyricsTrackRole
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.ui.LyricUiLine
import app.yukine.ui.NowBarState
import app.yukine.ui.nowBarEmptyState
import kotlinx.coroutines.flow.StateFlow

interface NowBarStateProvider {
    val nowBarState: StateFlow<NowBarState>
}

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
    data object ImportCurrentLyrics : NowPlayingEvent
    data object ClearCurrentLyrics : NowPlayingEvent
    data class SetLyricsTrackVisible(
        val role: LyricsTrackRole,
        val visible: Boolean
    ) : NowPlayingEvent
    data object ToggleShuffle : NowPlayingEvent
    data object CycleRepeatMode : NowPlayingEvent
    data class SwitchSource(
        val track: Track,
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val quality: StreamingAudioQuality?
    ) : NowPlayingEvent
    data class SwitchLibrarySource(
        val current: Track,
        val replacement: Track
    ) : NowPlayingEvent
}

enum class RepeatModeUi {
    Off,
    All,
    One
}

data class LyricsUiState(
    val title: String = "",
    val status: String = "",
    val lines: List<LyricUiLine> = emptyList(),
    val primaryVisible: Boolean = true,
    val translationVisible: Boolean = true,
    val romanizationVisible: Boolean = false
)

data class NowPlayingTrackState(
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val trackId: Long = -1L,
    val currentTrack: Track? = null
)

data class NowPlayingProgressState(
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val appVolume: Float = 1.0f
)

data class NowPlayingModesState(
    val favorite: Boolean = false,
    val lyricsVisible: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeUi = RepeatModeUi.All
)

data class NowPlayingLabelsState(val errorMessage: String? = null)

data class NowPlayingArtworkState(val coverUri: String? = null)

data class NowPlayingUiState(
    val track: NowPlayingTrackState = NowPlayingTrackState(),
    val progress: NowPlayingProgressState = NowPlayingProgressState(),
    val modes: NowPlayingModesState = NowPlayingModesState(),
    val lyrics: LyricsUiState = LyricsUiState(),
    val labels: NowPlayingLabelsState = NowPlayingLabelsState(),
    val artwork: NowPlayingArtworkState = NowPlayingArtworkState(),
    val overlayState: NowBarState = nowBarEmptyState(),
)

interface NowPlayingScreenStateProvider : NowBarStateProvider {
    val uiState: StateFlow<NowPlayingUiState>

    /**
     * Returns alternate playable copies of the same logical song. The screen owns their
     * presentation so library and playback owners only need to provide [Track] candidates.
     */
    fun sourceCandidatesFor(track: Track): List<Track> = emptyList()
}
