package app.yukine.ui

import android.net.Uri
import androidx.compose.runtime.Immutable
import app.yukine.model.Track
import app.yukine.playback.PlaybackRepeatMode

internal enum class NowBarDockPosition {
    Expanded,
    BottomLeft,
    BottomRight,
    TopCloud,
    TopCloudExpanded,
    TopCloudCollapsed
}

@Immutable
data class NowBarTrackState(
    val title: String = "",
    val subtitle: String = "",
    val trackId: Long = -1L,
    val contentUri: Uri? = null,
    val dataPath: String = "",
    val canExpand: Boolean = false
)

@Immutable
class WaveformSamples private constructor(private val values: FloatArray) {
    val size: Int get() = values.size

    internal fun valuesForRendering(): FloatArray = values

    override fun equals(other: Any?): Boolean =
        other is WaveformSamples && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        val Empty = WaveformSamples(FloatArray(0))

        fun of(values: FloatArray?): WaveformSamples =
            if (values == null || values.isEmpty()) Empty else WaveformSamples(values.copyOf())
    }
}

@Immutable
data class NowBarWaveformState(
    val samples: WaveformSamples = WaveformSamples.Empty,
    val generatedBars: Int = 0,
    val cachedProgress: Float = 0f
)

@Immutable
data class NowBarProgressState(
    val elapsed: String = Track.formatDuration(0),
    val duration: String = Track.formatDuration(0),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val waveform: NowBarWaveformState = NowBarWaveformState()
)

@Immutable
data class NowBarModesState(
    val favorite: Boolean = false,
    val favoriteEnabled: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = PlaybackRepeatMode.REPEAT_ALL
)

@Immutable
data class NowBarLyricsState(
    val title: String = "",
    val status: String = "",
    val lines: List<LyricUiLine> = emptyList()
)

@Immutable
data class NowBarLabels(
    val favorite: String = "",
    val favorited: String = "",
    val shuffle: String = "",
    val inOrder: String = "",
    val repeatOne: String = "",
    val repeatAll: String = "",
    val repeatOff: String = "",
    val nowPlaying: String = "",
    val close: String = "",
    val showLyrics: String = "",
    val showArtwork: String = "",
    val noLyricsFound: String = "",
    val previous: String = "",
    val play: String = "",
    val pause: String = "",
    val next: String = "",
    val queue: String = "",
    val more: String = "",
    val addToPlaylist: String = "",
    val playbackProgress: String = "",
    val expandWaveform: String = "",
    val playbackErrorTitle: String = "",
    val retry: String = "",
    val dockLeft: String = "",
    val dockRight: String = "",
    val expandNowBar: String = "",
    val dockTop: String = "",
    val restoreBottom: String = "",
    val collapseTopCloud: String = "",
    val showTopCloud: String = "",
    val expandTopCloud: String = "",
    val compactTopCloud: String = ""
)

@Immutable
data class NowBarArtworkState(val albumArtUri: Uri? = null)

@Immutable
data class NowBarErrorState(val message: String = "")

@Immutable
data class NowBarState(
    val track: NowBarTrackState = NowBarTrackState(),
    val progress: NowBarProgressState = NowBarProgressState(),
    val modes: NowBarModesState = NowBarModesState(),
    val lyrics: NowBarLyricsState = NowBarLyricsState(),
    val labels: NowBarLabels = NowBarLabels(),
    val artwork: NowBarArtworkState = NowBarArtworkState(),
    val error: NowBarErrorState = NowBarErrorState()
)

@Immutable
internal data class NowBarProgressSlice(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val elapsed: String,
    val duration: String,
    val trackId: Long,
    val contentUriString: String?,
    val dataPath: String,
    val waveformBars: FloatArray,
    val waveformGeneratedBars: Int,
    val waveformCachedProgress: Float,
    val playbackProgressLabel: String,
    val expandWaveformLabel: String
)

@Immutable
internal data class NowBarTrackSlice(
    val artUriString: String?,
    val title: String,
    val subtitle: String,
    val canExpand: Boolean
)

@Immutable
internal data class NowBarTransportSlice(
    val playing: Boolean,
    val previousLabel: String,
    val playLabel: String,
    val pauseLabel: String,
    val nextLabel: String
)

@Immutable
internal data class NowBarModeSlice(
    val favoriteEnabled: Boolean,
    val favorite: Boolean,
    val favoriteLabel: String,
    val favoritedLabel: String,
    val shuffleEnabled: Boolean,
    val shuffleLabel: String,
    val inOrderLabel: String,
    val repeatOneLabel: String,
    val repeatAllLabel: String,
    val repeatOffLabel: String,
    val queueLabel: String,
    val repeatMode: Int
)

fun nowBarEmptyState() = NowBarState(
    track = NowBarTrackState(title = "\u672a\u9009\u4e2d\u6b4c\u66f2"),
    labels = NowBarLabels(
        favorite = "\u6536\u85cf",
        favorited = "\u5df2\u6536\u85cf",
        shuffle = "\u968f\u673a",
        inOrder = "\u987a\u5e8f",
        repeatOne = "\u5355\u66f2\u5faa\u73af",
        repeatAll = "\u5217\u8868\u5faa\u73af",
        repeatOff = "\u5173\u95ed\u5faa\u73af"
    )
)

fun interface SeekAction {
    fun seekTo(positionMs: Long)
}
