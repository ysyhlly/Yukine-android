package app.echo.next

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.echo.next.model.LyricsLine
import app.echo.next.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

fun interface LyricsStateListener {
    fun onLyricsStateChanged()
}

fun interface LyricsLoader {
    fun load(track: Track, onlineEnabled: Boolean, neteaseProviderTrackId: String): List<LyricsLine>
}

internal class LoadTrackLyricsUseCaseLyricsLoader(
    private val useCase: LoadTrackLyricsUseCase
) : LyricsLoader {
    override fun load(track: Track, onlineEnabled: Boolean, neteaseProviderTrackId: String): List<LyricsLine> =
        useCase.execute(track, onlineEnabled, neteaseProviderTrackId)
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

class LyricsViewModel : ViewModel() {
    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state

    private var lyricsLoader: LyricsLoader? = null
    private var listener: LyricsStateListener? = null
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

    fun bindListener(listener: LyricsStateListener?) {
        this.listener = listener
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
    fun load(track: Track?, neteaseProviderTrackId: String? = ""): Job {
        val token = ++requestToken
        val providerTrackId = neteaseProviderTrackId.orEmpty()
        val requestOnline = _state.value.onlineEnabled
        val nextStatus = if (track == null) {
            LyricsStatusKind.NO_TRACK
        } else if (requestOnline || providerTrackId.trim().isNotEmpty()) {
            LyricsStatusKind.LOADING
        } else {
            LyricsStatusKind.LOADING_LOCAL
        }
        updateState(
            _state.value.copy(
                trackId = track?.id ?: -1L,
                lines = emptyList(),
                loadedLineCount = 0,
                statusKind = nextStatus
            )
        )
        if (track == null) {
            return viewModelScope.launch { }
        }

        val requestedTrackId = track.id
        return viewModelScope.launch {
            val loadedLines = withContext(Dispatchers.IO) {
                lyricsLoader?.load(track, requestOnline, providerTrackId).orEmpty()
            }
            if (token != requestToken || _state.value.trackId != requestedTrackId) {
                return@launch
            }
            updateState(
                _state.value.copy(
                    lines = loadedLines,
                    loadedLineCount = loadedLines.size,
                    statusKind = if (loadedLines.isEmpty()) {
                        if (requestOnline) LyricsStatusKind.NOT_FOUND else LyricsStatusKind.LOCAL_NOT_FOUND
                    } else {
                        LyricsStatusKind.LOADED
                    }
                )
            )
        }
    }

    private fun updateState(next: LyricsState) {
        _state.value = next
        listener?.onLyricsStateChanged()
    }
}