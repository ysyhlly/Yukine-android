package app.echo.next

import androidx.lifecycle.ViewModel
import app.echo.next.model.Track
import app.echo.next.playback.EchoPlaybackService
import app.echo.next.playback.PlaybackStateSnapshot
import app.echo.next.ui.LyricUiLine
import app.echo.next.ui.NowBarController
import app.echo.next.ui.NowBarState
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NowPlayingEvent {
    data object PlayPause : NowPlayingEvent
    data object Next : NowPlayingEvent
    data object Previous : NowPlayingEvent
    data class SeekTo(val positionMs: Long) : NowPlayingEvent
    data object ToggleLyrics : NowPlayingEvent
    data object OpenQueue : NowPlayingEvent
    data object ToggleFavorite : NowPlayingEvent
    data object AddToPlaylist : NowPlayingEvent
    data object ToggleShuffle : NowPlayingEvent
    data object CycleRepeatMode : NowPlayingEvent
}

fun interface NowPlayingEventHandler {
    fun onEvent(event: NowPlayingEvent)
}

sealed interface NowPlayingEffect {
    data object OpenQueue : NowPlayingEffect
    data class OpenAddToPlaylist(val track: Track) : NowPlayingEffect
    data class ShowMessage(val message: String) : NowPlayingEffect
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
    val overlayState: NowBarState = NowBarController.emptyState()
)

interface NowPlayingGateway {
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleFavorite()
    fun toggleShuffle()
    fun cycleRepeatMode()
    fun statusMessage(key: String): String
}

data class PlaybackActionResultUi(
    @JvmField val snapshot: PlaybackStateSnapshot?,
    @JvmField val status: String?,
    @JvmField val publishPlaybackState: Boolean,
    @JvmField val renderSelectedTab: Boolean,
    @JvmField val renderNowBar: Boolean,
    @JvmField val navigateNow: Boolean
)

interface NowPlayingPlaybackGateway {
    fun serviceConnected(): Boolean
    fun startPlaybackService(action: String?)
    fun snapshot(): PlaybackStateSnapshot?
    fun queueSnapshot(): List<Track>
    fun skipToPrevious()
    fun skipToNext()
    fun seekTo(positionMs: Long)
    fun removeTracksById(trackIds: Set<Long>)
    fun clearQueue()
    fun replaceQueuedTrack(updated: Track)
    fun replaceQueuedTrackById(oldTrackId: Long, updated: Track)
    fun retainTracksById(trackIds: Set<Long>)
    fun precacheTrack(track: Track)
    fun appendToQueue(tracks: List<Track>)
    fun replaceCurrentTrackAndResume(track: Track, positionMs: Long)
    fun startSleepTimerMinutes(minutes: Int)
    fun cancelSleepTimer()
    fun playQueue(tracks: List<Track>, index: Int)
    fun pause()
    fun play()
    fun setShuffleEnabled(enabled: Boolean)
    fun cycleRepeatMode()
    fun setRepeatMode(repeatMode: Int)
}

class NowPlayingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<NowPlayingEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<NowPlayingEffect> = _effects.asSharedFlow()

    private val pendingEffects = ArrayDeque<NowPlayingEffect>()
    private var gateway: NowPlayingGateway? = null
    private var playbackGateway: NowPlayingPlaybackGateway? = null

    fun bindGateway(nextGateway: NowPlayingGateway?) {
        gateway = nextGateway
    }

    fun bindPlaybackGateway(nextGateway: NowPlayingPlaybackGateway?) {
        playbackGateway = nextGateway
    }

    fun updateState(
        playbackState: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>,
        lyricsState: LyricsState?,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ) {
        val snapshot = playbackState ?: PlaybackStateSnapshot.empty()
        val previous = _uiState.value
        val overlay = NowBarStateFactory.create(
            snapshot,
            favoriteIds,
            lyricsState,
            languageMode
        )
        val track = snapshot.currentTrack
        val sameTrack = previous.trackId == overlay.trackId
        _uiState.value = NowPlayingUiState(
            trackTitle = track?.title ?: overlay.title,
            artist = track?.artist.orEmpty(),
            album = track?.album,
            coverUri = track?.albumArtUri?.toString(),
            isPlaying = snapshot.playing,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            isFavorite = overlay.favorite,
            lyricsVisible = if (sameTrack) previous.lyricsVisible else false,
            lyrics = LyricsUiState(
                title = overlay.lyricsTitle,
                status = overlay.lyricsStatus,
                lines = overlay.lyrics
            ),
            shuffleEnabled = snapshot.shuffleEnabled,
            repeatMode = repeatModeUi(snapshot.repeatMode),
            errorMessage = overlay.playbackErrorMessage.takeIf { it.isNotBlank() },
            trackId = overlay.trackId,
            currentTrack = track,
            overlayState = overlay
        )
    }

    fun onEvent(event: NowPlayingEvent) {
        when (event) {
            NowPlayingEvent.PlayPause -> gateway?.playPause()
            NowPlayingEvent.Next -> gateway?.next()
            NowPlayingEvent.Previous -> gateway?.previous()
            is NowPlayingEvent.SeekTo -> gateway?.seekTo(event.positionMs)
            NowPlayingEvent.ToggleLyrics -> toggleLyrics()
            NowPlayingEvent.OpenQueue -> emitEffect(NowPlayingEffect.OpenQueue)
            NowPlayingEvent.ToggleFavorite -> {
                if (_uiState.value.trackId >= 0L) {
                    gateway?.toggleFavorite()
                } else {
                    emitNoTrackMessage()
                }
            }
            NowPlayingEvent.AddToPlaylist -> {
                val track = _uiState.value.currentTrack
                if (track != null && track.id >= 0L) {
                    emitEffect(NowPlayingEffect.OpenAddToPlaylist(track))
                } else {
                    emitNoTrackMessage()
                }
            }
            NowPlayingEvent.ToggleShuffle -> gateway?.toggleShuffle()
            NowPlayingEvent.CycleRepeatMode -> gateway?.cycleRepeatMode()
        }
    }

    fun drainEffects(): List<NowPlayingEffect> {
        val drained = ArrayList<NowPlayingEffect>(pendingEffects.size)
        while (pendingEffects.isNotEmpty()) {
            drained.add(pendingEffects.removeFirst())
        }
        return drained
    }

    fun skipToPrevious() {
        val player = playbackGateway ?: return
        if (player.serviceConnected()) {
            player.skipToPrevious()
        } else {
            player.startPlaybackService(EchoPlaybackService.ACTION_PREVIOUS)
        }
    }

    fun skipToNext() {
        val player = playbackGateway ?: return
        if (player.serviceConnected()) {
            player.skipToNext()
        } else {
            player.startPlaybackService(EchoPlaybackService.ACTION_NEXT)
        }
    }

    fun seekTo(positionMs: Long) {
        playbackGateway?.seekTo(positionMs)
    }

    fun removeQueueTrack(track: Track?): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Queue is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Queue is not connected")
        }
        if (track == null) {
            return statusOnly("Status")
        }
        player.removeTracksById(setOf(track.id))
        return stateChanged(player.snapshot(), "Status: ${track.title}")
    }

    fun hasQueue(): Boolean {
        val player = playbackGateway ?: return false
        return player.serviceConnected() && player.queueSnapshot().isNotEmpty()
    }

    fun clearQueue(): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Queue is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Queue is not connected")
        }
        player.clearQueue()
        return stateChanged(player.snapshot(), "Status")
    }

    fun replaceQueuedTrack(oldTrackId: Long, updated: Track?) {
        val player = playbackGateway ?: return
        if (!player.serviceConnected() || updated == null) {
            return
        }
        if (oldTrackId == updated.id) {
            player.replaceQueuedTrack(updated)
        } else {
            player.replaceQueuedTrackById(oldTrackId, updated)
        }
    }

    fun precacheTrack(track: Track?) {
        val player = playbackGateway ?: return
        if (!player.serviceConnected() || track == null) {
            return
        }
        player.precacheTrack(track)
    }

    fun appendToQueue(tracks: List<Track>?) {
        val player = playbackGateway ?: return
        if (!player.serviceConnected() || tracks.isNullOrEmpty()) {
            return
        }
        player.appendToQueue(tracks)
    }

    fun replaceCurrentTrackAndResume(track: Track?, positionMs: Long) {
        val player = playbackGateway ?: return
        if (!player.serviceConnected() || track == null) {
            return
        }
        player.replaceCurrentTrackAndResume(track, positionMs)
    }

    fun retainTracks(tracksToKeep: List<Track>?) {
        val player = playbackGateway ?: return
        if (!player.serviceConnected() || tracksToKeep == null) {
            return
        }
        player.retainTracksById(tracksToKeep.map { track -> track.id }.toSet())
    }

    fun startSleepTimer(minutes: Int): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        player.startSleepTimerMinutes(minutes)
        return PlaybackActionResultUi(
            player.snapshot(),
            "Sleep timer set: $minutes minutes",
            publishPlaybackState = false,
            renderSelectedTab = true,
            renderNowBar = true,
            navigateNow = false
        )
    }

    fun cancelSleepTimer(): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        player.cancelSleepTimer()
        return PlaybackActionResultUi(
            player.snapshot(),
            "Sleep timer cancelled",
            publishPlaybackState = false,
            renderSelectedTab = true,
            renderNowBar = true,
            navigateNow = false
        )
    }

    fun playTrackList(tracks: List<Track>?, index: Int): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        if (tracks.isNullOrEmpty()) {
            return statusOnly("No tracks to play")
        }
        player.playQueue(tracks, index)
        return PlaybackActionResultUi(player.snapshot(), null, false, false, true, false)
    }

    fun togglePlayback(
        playbackState: PlaybackStateSnapshot?,
        fallbackTracks: List<Track>?
    ): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        if (playbackState != null && playbackState.playing) {
            player.pause()
        } else if (playbackState != null && playbackState.currentTrack != null) {
            player.startPlaybackService(null)
            player.play()
        } else if (!fallbackTracks.isNullOrEmpty()) {
            player.startPlaybackService(null)
            player.playQueue(fallbackTracks, 0)
        }
        return PlaybackActionResultUi(player.snapshot(), null, false, false, true, false)
    }

    fun toggleShuffle(playbackState: PlaybackStateSnapshot?): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        player.setShuffleEnabled(playbackState == null || !playbackState.shuffleEnabled)
        return PlaybackActionResultUi(player.snapshot(), null, false, false, true, false)
    }

    fun cycleRepeat(): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        player.cycleRepeatMode()
        return PlaybackActionResultUi(player.snapshot(), null, false, false, true, false)
    }

    fun cycleBottomPlaybackMode(playbackState: PlaybackStateSnapshot?): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        val shuffleEnabled = playbackState?.shuffleEnabled == true
        val repeatMode = playbackState?.repeatMode ?: EchoPlaybackService.REPEAT_ALL
        when {
            shuffleEnabled -> {
                player.setShuffleEnabled(false)
                player.setRepeatMode(EchoPlaybackService.REPEAT_ONE)
            }
            repeatMode == EchoPlaybackService.REPEAT_ONE -> {
                player.setRepeatMode(EchoPlaybackService.REPEAT_ALL)
            }
            else -> {
                player.setRepeatMode(EchoPlaybackService.REPEAT_ALL)
                player.setShuffleEnabled(true)
            }
        }
        return PlaybackActionResultUi(player.snapshot(), null, false, false, true, false)
    }

    private fun toggleLyrics() {
        val current = _uiState.value
        _uiState.value = current.copy(lyricsVisible = !current.lyricsVisible)
    }

    private fun emitNoTrackMessage() {
        val message = gateway?.statusMessage("no.track.selected").orEmpty()
        if (message.isNotBlank()) {
            emitEffect(NowPlayingEffect.ShowMessage(message))
        }
    }

    private fun emitEffect(effect: NowPlayingEffect) {
        pendingEffects.add(effect)
        _effects.tryEmit(effect)
    }

    private fun repeatModeUi(repeatMode: Int): RepeatModeUi {
        return when (repeatMode) {
            EchoPlaybackService.REPEAT_ONE -> RepeatModeUi.One
            EchoPlaybackService.REPEAT_OFF -> RepeatModeUi.Off
            else -> RepeatModeUi.All
        }
    }

    private fun stateChanged(snapshot: PlaybackStateSnapshot?, status: String): PlaybackActionResultUi {
        return PlaybackActionResultUi(snapshot, status, true, true, true, false)
    }

    private fun statusOnly(status: String): PlaybackActionResultUi {
        return PlaybackActionResultUi(null, status, false, false, false, false)
    }
}
