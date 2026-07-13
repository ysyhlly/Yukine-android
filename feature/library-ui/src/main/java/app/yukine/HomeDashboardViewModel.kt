package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.ui.HomeDashboardActions
import app.yukine.ui.HomeDashboardUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface HomeDashboardIntentHandler {
    fun openLibraryMode(mode: String)
    fun continuePlayback(track: Track?)
    fun openNowPlaying()
    fun playTrack(track: Track)
    fun refreshLibrary()
    fun openQueue()
    fun shuffleAll(tracks: List<Track>)
    fun openStreaming()
    fun openCollections()
    fun openSearch()
    fun playDailyRecommendations()
    fun playHeartbeatRecommendations()
}

class HomeDashboardViewModel @JvmOverloads constructor(
    private var dashboardRepository: HomeDashboardRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeDashboardDestinationState())
    val uiState: StateFlow<HomeDashboardDestinationState> = _uiState.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private var playbackBinding: Job? = null
    private var dashboardBinding: Job? = null

    fun bindRepository(repository: HomeDashboardRepository?) {
        dashboardRepository = repository
    }

    fun bindPlayback(
        playbackReadModel: PlaybackReadModel?,
        languageMode: StateFlow<String>?
    ) {
        playbackBinding?.cancel()
        playbackBinding = null
        if (playbackReadModel == null || languageMode == null) return
        playbackBinding = viewModelScope.launch {
            combine(playbackReadModel.state, languageMode) { playback, languageMode ->
                playback to languageMode
            }.collect { (playback, languageMode) ->
                updatePlayback(playback, languageMode)
            }
        }
    }

    fun bindStateSources(
        playbackReadModel: PlaybackReadModel?,
        libraryState: StateFlow<LibraryStoreState>?,
        streamingState: StateFlow<StreamingSearchState>?,
        languageMode: StateFlow<String>?,
        intentHandler: HomeDashboardIntentHandler?
    ) {
        bindPlayback(playbackReadModel, languageMode)
        dashboardBinding?.cancel()
        dashboardBinding = null
        if (
            playbackReadModel == null ||
            libraryState == null ||
            streamingState == null ||
            languageMode == null ||
            intentHandler == null
        ) {
            return
        }
        val libraryInputs = libraryState
            .map { DashboardLibraryInputs(it.allTracks, it.visibleTracks, it.recentRecords) }
            .distinctUntilChanged()
        val streamingConnected = streamingState
            .map(::isStreamingConnected)
            .distinctUntilChanged()
        val currentTrackId = playbackReadModel.state
            .map { it.currentTrack?.id }
            .distinctUntilChanged()
        dashboardBinding = viewModelScope.launch {
            combine(libraryInputs, streamingConnected, currentTrackId) {
                    library,
                    connected,
                    _ ->
                DashboardInputs(library, connected)
            }.collectLatest { inputs ->
                val playback = playbackReadModel.state.value
                updateActions(inputs.library, playback, intentHandler)
                loadHomeDashboard(
                    localTracks = inputs.library.visibleTracks.ifEmpty { inputs.library.allTracks },
                    localRecords = inputs.library.recentRecords,
                    localPlayback = playback,
                    streamingConnected = inputs.streamingConnected
                )
            }
        }
    }

    fun updateHomeDashboard(content: HomeDashboardUiState) {
        _uiState.value = _uiState.value.copy(content = content)
    }

    fun updateHomeDashboardActions(actions: HomeDashboardActions) {
        _uiState.value = _uiState.value.copy(actions = actions)
    }

    fun updateStreamingConnected(connected: Boolean) {
        val current = _uiState.value.content
        if (current.streamingConnected != connected) {
            _uiState.value = _uiState.value.copy(content = current.copy(streamingConnected = connected))
        }
    }

    private fun updatePlayback(snapshot: PlaybackStateSnapshot?, languageMode: String) {
        val playback = snapshot ?: PlaybackStateSnapshot.empty()
        val track = playback.currentTrack ?: return
        val durationMs = when {
            playback.durationMs > 0L -> playback.durationMs
            track.durationMs > 0L -> track.durationMs
            else -> 0L
        }
        val progress = if (durationMs <= 0L) {
            0f
        } else {
            (playback.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
        val current = _uiState.value.content
        _uiState.value = _uiState.value.copy(
            content = current.copy(
                heroSubtitle = text(languageMode, "home.hero.subtitle.track.prefix") +
                    track.artist +
                    text(languageMode, "home.hero.subtitle.track.middle") +
                    track.title +
                    text(languageMode, "home.hero.subtitle.track.suffix"),
                continueTitle = track.title,
                continueSubtitle = track.subtitle(),
                continueDetail = if (playback.playing) {
                    text(languageMode, "now.playing")
                } else {
                    text(languageMode, "home.continue.playing")
                },
                continueAlbumArtUri = track.albumArtUri,
                continueProgress = progress,
                continuePlaying = playback.playing
            )
        )
    }

    private fun text(languageMode: String, key: String): String = AppLanguage.text(languageMode, key)

    fun fetchHomeDashboard(
        localTracks: List<Track>,
        localRecords: List<TrackPlayRecord>,
        localPlayback: PlaybackStateSnapshot?,
        streamingConnected: Boolean = false,
        onComplete: Runnable? = null
    ): Job {
        return viewModelScope.launch {
            loadHomeDashboard(localTracks, localRecords, localPlayback, streamingConnected)
            onComplete?.run()
        }
    }

    private suspend fun loadHomeDashboard(
        localTracks: List<Track>,
        localRecords: List<TrackPlayRecord>,
        localPlayback: PlaybackStateSnapshot?,
        streamingConnected: Boolean
    ) {
        _loading.value = true
        try {
            val repo = dashboardRepository
            val state = if (repo != null) {
                repo.fetchHome(localTracks, localRecords, localPlayback)
            } else {
                HomeDashboardStateFactory.create(
                    "zh",
                    localTracks,
                    localTracks,
                    localRecords,
                    localPlayback
                )
            }
            _uiState.value = _uiState.value.copy(
                content = state.copy(streamingConnected = streamingConnected)
            )
        } finally {
            _loading.value = false
        }
    }

    private fun updateActions(
        inputs: DashboardLibraryInputs,
        playback: PlaybackStateSnapshot,
        intentHandler: HomeDashboardIntentHandler
    ) {
        val recentTracks = inputs.recentRecords
            .filter { it.track != null }
            .sortedByDescending { it.playedAt }
            .take(8)
            .map { it.track }
        val continueTrack = playback.currentTrack
            ?: recentTracks.firstOrNull()
            ?: inputs.visibleTracks.firstOrNull()
            ?: inputs.allTracks.firstOrNull()
        _uiState.value = _uiState.value.copy(
            actions = HomeDashboardActions(
                onOpenStat = listOf(
                    LibraryGrouping.SONGS,
                    LibraryGrouping.ALBUMS,
                    LibraryGrouping.ARTISTS,
                    LibraryGrouping.FOLDERS
                ).map { mode -> Runnable { intentHandler.openLibraryMode(mode) } },
                onContinue = Runnable { intentHandler.continuePlayback(continueTrack) },
                onOpenNowPlaying = Runnable { intentHandler.openNowPlaying() },
                onPlayRecent = recentTracks.map { track -> Runnable { intentHandler.playTrack(track) } },
                onRefresh = Runnable { intentHandler.refreshLibrary() },
                onViewQueue = Runnable { intentHandler.openQueue() },
                onShuffleAll = Runnable { intentHandler.shuffleAll(inputs.allTracks) },
                onRecentTabChanged = {},
                onDailyRecommend = Runnable { intentHandler.playDailyRecommendations() },
                onHeartbeatRecommend = Runnable { intentHandler.playHeartbeatRecommendations() },
                onOpenCollections = Runnable { intentHandler.openCollections() },
                onConnectStreaming = Runnable { intentHandler.openStreaming() },
                onSearch = Runnable { intentHandler.openSearch() }
            )
        )
    }

    private fun isStreamingConnected(state: StreamingSearchState): Boolean {
        return state.authStates.values.any { it.connected } ||
            state.providers.any { it.auth.connected }
    }

    private data class DashboardLibraryInputs(
        val allTracks: List<Track>,
        val visibleTracks: List<Track>,
        val recentRecords: List<TrackPlayRecord>
    )

    private data class DashboardInputs(
        val library: DashboardLibraryInputs,
        val streamingConnected: Boolean
    )
}
