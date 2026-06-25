package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.dashboard.DashboardRepository
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.HomeDashboardActions
import app.yukine.ui.HomeDashboardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainActivityHomeDashboardUiState(
    val content: HomeDashboardUiState = HomeDashboardUiState(),
    val actions: HomeDashboardActions = emptyHomeDashboardActions()
)

internal fun emptyHomeDashboardActions(): HomeDashboardActions = HomeDashboardActions(
    onOpenStat = emptyList(),
    onContinue = Runnable { },
    onOpenNowPlaying = Runnable { },
    onPlayRecent = emptyList(),
    onRefresh = Runnable { },
    onViewQueue = Runnable { },
    onShuffleAll = Runnable { },
    onRecentTabChanged = { }
)

@HiltViewModel
class HomeDashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository?
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainActivityHomeDashboardUiState())
    val uiState: StateFlow<MainActivityHomeDashboardUiState> = _uiState.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

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

    fun updatePlayback(snapshot: PlaybackStateSnapshot?) {
        updatePlayback(snapshot, AppLanguage.MODE_CHINESE)
    }

    fun updatePlayback(snapshot: PlaybackStateSnapshot?, languageMode: String) {
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
        _loading.value = true
        return viewModelScope.launch {
            val repo = dashboardRepository
            val state = if (repo != null) {
                repo.fetchHome(localTracks, localRecords, localPlayback)
            } else {
                HomeDashboardStateFactory.create(
                    "zh",
                    localTracks,
                    if (localTracks.isNotEmpty()) localTracks else emptyList(),
                    localRecords,
                    localPlayback
                )
            }
            _uiState.value = _uiState.value.copy(content = state.copy(streamingConnected = streamingConnected))
            _loading.value = false
            onComplete?.run()
        }
    }
}
