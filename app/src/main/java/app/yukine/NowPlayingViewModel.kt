package app.yukine

import androidx.lifecycle.ViewModel
import android.net.Uri
import app.yukine.model.Track
import app.yukine.model.TrackIdentity
import app.yukine.playback.PlaybackRepeatMode
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.LuoxueTrackMetadataResolver
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.ui.NowBarState
import app.yukine.ui.nowBarEmptyState
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface NowPlayingEffect {
    data object OpenQueue : NowPlayingEffect
    data class OpenAddToPlaylist(val track: Track) : NowPlayingEffect
    data class ShareTrack(val track: Track) : NowPlayingEffect
    data class DownloadTrack(val track: Track) : NowPlayingEffect
    data class SwitchSource(
        val track: Track,
        val provider: StreamingProviderName,
        val providerTrackId: String,
        val quality: StreamingAudioQuality?,
        val requestId: Long
    ) : NowPlayingEffect
    data class SwitchLibrarySource(
        val current: Track,
        val replacement: Track,
        val requestId: Long
    ) : NowPlayingEffect
    data class ShowMessage(val message: String) : NowPlayingEffect
}

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

fun interface NowPlayingStateObserver {
    fun onStateChanged(state: NowPlayingUiState, lyricsState: LyricsState)
}

data class PlaybackActionResultUi(
    @JvmField val status: String?
)

interface NowPlayingPlaybackGateway {
    fun serviceConnected(): Boolean
    fun snapshot(): PlaybackStateSnapshot?
    fun queueSnapshot(): List<Track>
    fun skipToPrevious()
    fun skipToNext()
    fun seekTo(positionMs: Long)
    fun removeTracksById(trackIds: Set<Long>)
    fun clearQueue()
    fun moveQueueTrack(fromIndex: Int, toIndex: Int)
    fun replaceQueuedTrack(updated: Track)
    fun updateQueuedTrackArtwork(trackId: Long, artworkUri: Uri) = Unit
    fun replaceQueuedTracks(updated: List<Track>) {
        updated.forEach { replaceQueuedTrack(it) }
    }
    fun replaceQueuedTrackById(oldTrackId: Long, updated: Track)
    fun retainTracksById(trackIds: Set<Long>)
    fun warmPlaybackTrack(track: Track)
    fun appendToQueue(tracks: List<Track>)
    fun replaceCurrentTrackAndResume(track: Track, positionMs: Long)
    fun replaceCurrentSourceAndResume(expectedTrackId: Long, track: Track, positionMs: Long) = Unit
    fun startSleepTimerMinutes(minutes: Int)
    fun cancelSleepTimer()
    fun playQueue(tracks: List<Track>, index: Int)
    fun pause()
    fun play()
    fun setShuffleEnabled(enabled: Boolean)
    fun cycleRepeatMode()
    fun setRepeatMode(repeatMode: Int)
}

class NowPlayingViewModel : ViewModel(), NowPlayingScreenStateProvider {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    override val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()
    override val nowBarState: StateFlow<NowBarState> = _uiState
        .map { it.overlayState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, nowBarEmptyState())

    private val pendingEffects = ArrayDeque<NowPlayingEffect>()
    private var gateway: NowPlayingGateway? = null
    private var playbackGateway: NowPlayingPlaybackGateway? = null
    private var sourceCandidatesProvider: ((Track) -> List<Track>)? = null
    private var luoxueTrackMetadataResolver: LuoxueTrackMetadataResolver? = null
    private val sourceSwitchGeneration = AtomicLong(0L)
    private val artworkResolutionTrackIds = mutableSetOf<Long>()
    private var stateSourcesBinding: Job? = null
    private var stateObserver: NowPlayingStateObserver? = null

    fun bindStateObserver(observer: NowPlayingStateObserver?) {
        stateObserver = observer
        observer?.onStateChanged(_uiState.value, LyricsState())
    }

    fun bindStateSources(
        playbackReadModel: app.yukine.playback.PlaybackReadModel?,
        libraryState: StateFlow<LibraryStoreState>?,
        lyricsState: StateFlow<LyricsState>?,
        settingsState: StateFlow<SettingsState>?
    ) {
        stateSourcesBinding?.cancel()
        stateSourcesBinding = null
        if (
            playbackReadModel == null ||
            libraryState == null ||
            lyricsState == null ||
            settingsState == null
        ) {
            return
        }
        val favorites = libraryState
            .map { it.favoriteTrackIds }
            .distinctUntilChanged()
        val language = settingsState
            .map { it.preferences.languageMode }
            .distinctUntilChanged()
        stateSourcesBinding = viewModelScope.launch {
            combine(playbackReadModel.state, favorites, lyricsState, language) {
                    playback,
                    favoriteIds,
                    lyrics,
                    languageMode ->
                NowPlayingStateInputs(playback, favoriteIds, lyrics, languageMode)
            }.collect { inputs ->
                updateState(
                    inputs.playback,
                    inputs.favoriteIds,
                    inputs.lyrics,
                    inputs.languageMode
                )
            }
        }
    }

    fun bindGateway(nextGateway: NowPlayingGateway?) {
        gateway = nextGateway
    }

    fun bindPlaybackGateway(nextGateway: NowPlayingPlaybackGateway?) {
        playbackGateway = nextGateway
    }

    fun bindSourceCandidatesProvider(nextProvider: ((Track) -> List<Track>)?) {
        sourceCandidatesProvider = nextProvider
    }

    fun bindLuoxueTrackMetadataResolver(nextResolver: LuoxueTrackMetadataResolver?) {
        luoxueTrackMetadataResolver = nextResolver
        artworkResolutionTrackIds.clear()
    }

    fun isLatestSourceSwitchRequest(requestId: Long): Boolean {
        return requestId == sourceSwitchGeneration.get()
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
            overlayState = overlay,
            appVolume = snapshot.appVolume
        )
        stateObserver?.onStateChanged(_uiState.value, lyricsState ?: LyricsState())
        resolveMissingLuoxueArtwork(track)
    }

    private fun resolveMissingLuoxueArtwork(track: Track?) {
        val resolver = luoxueTrackMetadataResolver ?: return
        val candidate = track ?: return
        if (candidate.albumArtUri != null ||
            StreamingPlaybackAdapter.streamingProviderName(candidate.dataPath) != StreamingProviderName.LUOXUE ||
            !artworkResolutionTrackIds.add(candidate.id)
        ) {
            return
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val coverUrl = resolver.resolveCoverUrl(candidate)
            val artworkUri = coverUrl
                ?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let(Uri::parse)
                ?: return@launch
            updateQueuedTrackArtwork(candidate.id, artworkUri)
        }
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
                if (TrackIdentity.isUsable(_uiState.value.trackId)) {
                    gateway?.toggleFavorite()
                } else {
                    emitNoTrackMessage()
                }
            }
            NowPlayingEvent.AddToPlaylist -> {
                val track = _uiState.value.currentTrack
                if (track != null && TrackIdentity.isUsable(track.id)) {
                    emitEffect(NowPlayingEffect.OpenAddToPlaylist(track))
                } else {
                    emitNoTrackMessage()
                }
            }
            NowPlayingEvent.ShareCurrentTrack -> {
                val track = _uiState.value.currentTrack
                if (track != null && TrackIdentity.isUsable(track.id)) {
                    emitEffect(NowPlayingEffect.ShareTrack(track))
                } else {
                    emitNoTrackMessage()
                }
            }
            NowPlayingEvent.DownloadCurrentTrack -> {
                val track = _uiState.value.currentTrack
                if (track != null && TrackIdentity.isUsable(track.id)) {
                    emitEffect(NowPlayingEffect.DownloadTrack(track))
                } else {
                    emitNoTrackMessage()
                }
            }
            NowPlayingEvent.ToggleShuffle -> gateway?.toggleShuffle()
            NowPlayingEvent.CycleRepeatMode -> gateway?.cycleRepeatMode()
            is NowPlayingEvent.SwitchSource -> switchSource(
                event.track,
                event.provider,
                event.providerTrackId,
                event.quality
            )
            is NowPlayingEvent.SwitchLibrarySource -> switchLocalSource(
                event.current,
                event.replacement
            )
        }
    }

    fun drainEffects(): List<NowPlayingEffect> {
        val drained = ArrayList<NowPlayingEffect>(pendingEffects.size)
        while (pendingEffects.isNotEmpty()) {
            drained.add(pendingEffects.removeFirst())
        }
        return drained
    }

    fun switchSource(
        track: Track?,
        provider: StreamingProviderName?,
        providerTrackId: String?,
        quality: StreamingAudioQuality?
    ) {
        val safeTrack = track ?: return emitNoTrackMessage()
        val safeProvider = provider ?: return
        val safeTrackId = providerTrackId?.trim().orEmpty()
        if (safeTrackId.isBlank()) {
            emitEffect(NowPlayingEffect.ShowMessage("该音源缺少歌曲 ID，暂不能切换"))
            return
        }
        val requestId = sourceSwitchGeneration.incrementAndGet()
        emitEffect(
            NowPlayingEffect.SwitchSource(
                safeTrack,
                safeProvider,
                safeTrackId,
                quality,
                requestId
            )
        )
    }

    override fun sourceCandidatesFor(track: Track): List<Track> {
        return sourceCandidatesProvider?.invoke(track).orEmpty()
    }

    fun switchLocalSource(current: Track, replacement: Track) {
        if (current.id == replacement.id && current.dataPath == replacement.dataPath) {
            return
        }
        val requestId = sourceSwitchGeneration.incrementAndGet()
        emitEffect(NowPlayingEffect.SwitchLibrarySource(current, replacement, requestId))
    }

    fun skipToPrevious() {
        playbackGateway?.skipToPrevious()
    }

    fun skipToNext() {
        playbackGateway?.skipToNext()
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
        return stateChanged("Status: ${track.title}")
    }

    fun hasQueue(): Boolean {
        val player = playbackGateway ?: return false
        return player.serviceConnected() && (player.snapshot()?.queueSize ?: 0) > 0
    }

    fun clearQueue(): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Queue is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Queue is not connected")
        }
        player.clearQueue()
        return stateChanged("Status")
    }

    fun replaceQueuedTrack(oldTrackId: Long, updated: Track?) {
        val player = playbackGateway ?: return
        if (updated == null) {
            return
        }
        if (oldTrackId == updated.id) {
            player.replaceQueuedTrack(updated)
        } else {
            player.replaceQueuedTrackById(oldTrackId, updated)
        }
    }

    fun removeQueueTracks(trackIds: Set<Long>) {
        if (trackIds.isEmpty()) return
        playbackGateway?.removeTracksById(trackIds)
    }

    fun updateQueuedTrackArtwork(trackId: Long, artworkUri: Uri?) {
        val safeUri = artworkUri ?: return
        playbackGateway?.updateQueuedTrackArtwork(trackId, safeUri)
    }

    /**
     * Streaming placeholders retain their stable IDs after URL resolution, so a full pre-resolve
     * window can cross the service boundary once. Different physical IDs keep the existing
     * single-item replacement path because it also owns duplicate-collapse behavior.
     */
    fun replaceQueuedTracks(replacements: Map<Long, Track>) {
        val player = playbackGateway ?: return
        if (replacements.isEmpty()) {
            return
        }
        val sameIdReplacements = linkedMapOf<Long, Track>()
        replacements.forEach { (oldTrackId, replacement) ->
            if (oldTrackId == replacement.id) {
                sameIdReplacements[replacement.id] = replacement
            } else {
                player.replaceQueuedTrackById(oldTrackId, replacement)
            }
        }
        if (sameIdReplacements.isNotEmpty()) {
            player.replaceQueuedTracks(sameIdReplacements.values.toList())
        }
    }

    fun warmPlaybackTrack(track: Track?) {
        val player = playbackGateway ?: return
        if (track == null) {
            return
        }
        player.warmPlaybackTrack(track)
    }

    fun appendToQueue(tracks: List<Track>?) {
        val player = playbackGateway ?: return
        if (tracks.isNullOrEmpty()) {
            return
        }
        player.appendToQueue(tracks)
    }

    fun replaceCurrentTrackAndResume(track: Track?, positionMs: Long) {
        val player = playbackGateway ?: return
        if (track == null) {
            return
        }
        player.replaceCurrentTrackAndResume(track, positionMs)
    }

    fun replaceCurrentSourceAndResume(expectedTrackId: Long, track: Track?, positionMs: Long) {
        val player = playbackGateway ?: return
        if (track == null) {
            return
        }
        player.replaceCurrentSourceAndResume(expectedTrackId, track, positionMs)
    }

    fun retainTracks(tracksToKeep: List<Track>?) {
        val player = playbackGateway ?: return
        if (tracksToKeep == null) {
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
        return PlaybackActionResultUi("Sleep timer set: $minutes minutes")
    }

    fun cancelSleepTimer(): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        player.cancelSleepTimer()
        return PlaybackActionResultUi("Sleep timer cancelled")
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
        return PlaybackActionResultUi(null)
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
            player.play()
        } else if (!fallbackTracks.isNullOrEmpty()) {
            player.playQueue(fallbackTracks, 0)
        }
        return PlaybackActionResultUi(null)
    }

    fun toggleShuffle(playbackState: PlaybackStateSnapshot?): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        player.setShuffleEnabled(playbackState == null || !playbackState.shuffleEnabled)
        return PlaybackActionResultUi(null)
    }

    fun cycleRepeat(): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        player.cycleRepeatMode()
        return PlaybackActionResultUi(null)
    }

    fun cycleBottomPlaybackMode(playbackState: PlaybackStateSnapshot?): PlaybackActionResultUi {
        val player = playbackGateway ?: return statusOnly("Playback service is not connected")
        if (!player.serviceConnected()) {
            return statusOnly("Playback service is not connected")
        }
        val shuffleEnabled = playbackState?.shuffleEnabled == true
        val repeatMode = playbackState?.repeatMode ?: PlaybackRepeatMode.REPEAT_ALL
        when {
            shuffleEnabled -> {
                player.setShuffleEnabled(false)
                player.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)
            }
            repeatMode == PlaybackRepeatMode.REPEAT_ALL -> {
                player.setRepeatMode(PlaybackRepeatMode.REPEAT_ONE)
            }
            repeatMode == PlaybackRepeatMode.REPEAT_ONE -> {
                player.setRepeatMode(PlaybackRepeatMode.REPEAT_OFF)
            }
            repeatMode == PlaybackRepeatMode.REPEAT_OFF -> {
                player.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)
            }
            else -> {
                player.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)
            }
        }
        return PlaybackActionResultUi(null)
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
    }

    private fun repeatModeUi(repeatMode: Int): RepeatModeUi {
        return when (repeatMode) {
            PlaybackRepeatMode.REPEAT_ONE -> RepeatModeUi.One
            PlaybackRepeatMode.REPEAT_OFF -> RepeatModeUi.Off
            else -> RepeatModeUi.All
        }
    }

    private data class NowPlayingStateInputs(
        val playback: PlaybackStateSnapshot,
        val favoriteIds: Set<Long>,
        val lyrics: LyricsState,
        val languageMode: String
    )

    private fun stateChanged(status: String): PlaybackActionResultUi {
        return PlaybackActionResultUi(status)
    }

    private fun statusOnly(status: String): PlaybackActionResultUi {
        return PlaybackActionResultUi(status)
    }
}
