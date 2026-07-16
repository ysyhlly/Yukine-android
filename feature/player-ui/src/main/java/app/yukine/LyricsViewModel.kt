package app.yukine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.model.LyricsLine
import app.yukine.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LyricsStatusKind {
    NOT_LOADED,
    NO_TRACK,
    LOADING,
    LOADING_LOCAL,
    NOT_FOUND,
    LOCAL_NOT_FOUND,
    LOADED
}

data class LyricsState @JvmOverloads constructor(
    val trackId: Long = -1L,
    val lines: List<LyricsLine> = emptyList(),
    val onlineEnabled: Boolean = false,
    val offsetMs: Long = 0L,
    val statusKind: LyricsStatusKind = LyricsStatusKind.NOT_LOADED,
    val loadedLineCount: Int = 0
)

fun interface LyricsLoader {
    suspend fun load(track: Track, onlineEnabled: Boolean, neteaseProviderTrackId: String): List<LyricsLine>
}

fun interface CurrentLyricsTrackProvider {
    fun currentTrack(): Track?
}

fun interface LyricsProviderTrackIdResolver {
    fun providerTrackId(track: Track?): String
}

fun interface LyricsReloadStatusSink {
    fun setStatus(status: String)
}

object LyricsStatusText {
    @JvmStatic
    fun status(languageMode: String?, statusKind: LyricsStatusKind?, loadedLineCount: Int): String {
        val mode = languageMode ?: AppLanguage.MODE_SYSTEM
        return when (statusKind ?: LyricsStatusKind.NOT_LOADED) {
            LyricsStatusKind.NO_TRACK -> AppLanguage.text(mode, "no.track.selected")
            LyricsStatusKind.LOADING -> AppLanguage.text(mode, "loading.lyrics")
            LyricsStatusKind.LOADING_LOCAL -> AppLanguage.text(mode, "loading.local.lyrics")
            LyricsStatusKind.NOT_FOUND -> AppLanguage.text(mode, "no.lyrics.found")
            LyricsStatusKind.LOCAL_NOT_FOUND -> AppLanguage.text(mode, "no.local.lyrics.found")
            LyricsStatusKind.LOADED -> AppLanguage.text(mode, "loaded.lyrics.prefix") +
                loadedLineCount +
                AppLanguage.text(mode, "loaded.lyrics.suffix")
            LyricsStatusKind.NOT_LOADED -> AppLanguage.text(mode, "lyrics.not.loaded")
        }
    }
}

class LyricsViewModel @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    companion object {
        private const val LOAD_TIMEOUT_MS = 8000L
    }

    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state

    private var lyricsLoader: LyricsLoader? = null
    private var currentTrackProvider: CurrentLyricsTrackProvider? = null
    private var providerTrackIdResolver: LyricsProviderTrackIdResolver? = null
    private var reloadStatusSink: LyricsReloadStatusSink? = null
    private var requestToken = 0L

    fun configure(
        lyricsLoader: LyricsLoader?,
        onlineEnabled: Boolean,
        offsetMs: Long
    ) {
        this.lyricsLoader = lyricsLoader
        updateState(
            _state.value.copy(
                onlineEnabled = onlineEnabled,
                offsetMs = offsetMs
            )
        )
    }

    fun bindReloadGateway(
        currentTrackProvider: CurrentLyricsTrackProvider?,
        providerTrackIdResolver: LyricsProviderTrackIdResolver?,
        reloadStatusSink: LyricsReloadStatusSink?
    ) {
        this.currentTrackProvider = currentTrackProvider
        this.providerTrackIdResolver = providerTrackIdResolver
        this.reloadStatusSink = reloadStatusSink
    }

    fun stateSnapshot(): LyricsState = _state.value

    fun trackId(): Long = _state.value.trackId

    fun lines(): List<LyricsLine> = _state.value.lines

    fun status(languageMode: String?): String = LyricsStatusText.status(
        languageMode,
        _state.value.statusKind,
        _state.value.loadedLineCount
    )

    fun onlineEnabled(): Boolean = _state.value.onlineEnabled

    fun setOnlineEnabled(enabled: Boolean) {
        updateState(_state.value.copy(onlineEnabled = enabled))
    }

    fun offsetMs(): Long = _state.value.offsetMs

    fun setOffsetMs(offsetMs: Long) {
        updateState(_state.value.copy(offsetMs = offsetMs))
    }

    fun reload(track: Track?): Job {
        updateState(
            _state.value.copy(
                trackId = -1L,
                lines = emptyList(),
                loadedLineCount = 0
            )
        )
        return load(track)
    }

    @JvmOverloads
    fun reloadCurrentLyrics(languageMode: String? = AppLanguage.MODE_SYSTEM): Job {
        val track = currentTrackProvider?.currentTrack()
        val job = loadPlaybackTrack(track)
        reloadStatusSink?.setStatus(
            AppLanguage.text(
                languageMode ?: AppLanguage.MODE_SYSTEM,
                if (track == null) "no.track.selected" else "reloading.lyrics"
            )
        )
        return job
    }

    fun loadPlaybackTrack(track: Track?): Job {
        if (track == null) {
            return load(null, "")
        }
        val lookupToken = ++requestToken
        return viewModelScope.launch {
            val providerTrackId = withContext(ioDispatcher) {
                runCatching {
                    providerTrackIdResolver?.providerTrackId(track).orEmpty()
                }.getOrDefault("")
            }
            if (lookupToken != requestToken) {
                return@launch
            }
            load(track, providerTrackId).join()
        }
    }

    @JvmOverloads
    fun load(track: Track?, neteaseProviderTrackId: String? = ""): Job {
        val token = ++requestToken
        val previous = _state.value
        val providerTrackId = neteaseProviderTrackId.orEmpty()
        val requestOnline = previous.onlineEnabled
        val nextStatus = if (track == null) {
            LyricsStatusKind.NO_TRACK
        } else if (requestOnline || providerTrackId.trim().isNotEmpty()) {
            LyricsStatusKind.LOADING
        } else {
            LyricsStatusKind.LOADING_LOCAL
        }
        val keepPreviousLines = track != null && previous.trackId == track.id && previous.lines.isNotEmpty()
        updateState(
            previous.copy(
                trackId = track?.id ?: -1L,
                lines = if (keepPreviousLines) previous.lines else emptyList(),
                loadedLineCount = if (keepPreviousLines) previous.loadedLineCount else 0,
                statusKind = nextStatus
            )
        )
        if (track == null) {
            return viewModelScope.launch { }
        }

        val requestedTrackId = track.id
        val timeoutJob = viewModelScope.launch {
            delay(LOAD_TIMEOUT_MS)
            if (token != requestToken || _state.value.trackId != requestedTrackId) {
                return@launch
            }
            val current = _state.value
            if (current.statusKind != LyricsStatusKind.LOADING && current.statusKind != LyricsStatusKind.LOADING_LOCAL) {
                return@launch
            }
            val keepFallbackLines = current.lines.isNotEmpty()
            updateState(
                current.copy(
                    lines = if (keepFallbackLines) current.lines else emptyList(),
                    loadedLineCount = if (keepFallbackLines) current.loadedLineCount else 0,
                    statusKind = if (keepFallbackLines) {
                        LyricsStatusKind.LOADED
                    } else if (requestOnline) {
                        LyricsStatusKind.NOT_FOUND
                    } else {
                        LyricsStatusKind.LOCAL_NOT_FOUND
                    }
                )
            )
        }
        val loadJob = viewModelScope.launch {
            val loadedLines = withContext(ioDispatcher) {
                lyricsLoader?.load(track, requestOnline, providerTrackId).orEmpty()
            }
            if (token != requestToken || _state.value.trackId != requestedTrackId) {
                return@launch
            }
            val current = _state.value
            val keepFallbackLines = loadedLines.isEmpty() && current.trackId == requestedTrackId && current.lines.isNotEmpty()
            updateState(
                current.copy(
                    lines = if (keepFallbackLines) current.lines else loadedLines,
                    loadedLineCount = if (keepFallbackLines) current.loadedLineCount else loadedLines.size,
                    statusKind = if (loadedLines.isEmpty()) {
                        if (keepFallbackLines) LyricsStatusKind.LOADED
                        else if (requestOnline) LyricsStatusKind.NOT_FOUND
                        else LyricsStatusKind.LOCAL_NOT_FOUND
                    } else {
                        LyricsStatusKind.LOADED
                    }
                )
            )
        }
        loadJob.invokeOnCompletion { timeoutJob.cancel() }
        return loadJob
    }

    private fun updateState(next: LyricsState) {
        _state.value = next
    }
}
