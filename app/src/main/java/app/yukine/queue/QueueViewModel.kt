package app.yukine.queue

import androidx.lifecycle.ViewModel
import app.yukine.AppLanguage
import app.yukine.QueueDestinationState
import app.yukine.QueueDestinationStateProvider
import app.yukine.TrackRowKeyPolicy
import app.yukine.TrackRowStateFactory
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.QueueScreenLabels
import app.yukine.ui.QueueTrackUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Intents emitted by the Queue screen. The host (MainActivity) collects [intents] and
 * delegates each one to the existing playback gateways. This keeps the screen free of
 * any reference to the service connection or the legacy render controllers.
 */
sealed interface QueueIntent {
    data class PlayAt(val tracks: List<Track>, val index: Int) : QueueIntent
    data class ToggleFavorite(val track: Track) : QueueIntent
    data class AddToPlaylist(val track: Track) : QueueIntent
    data class Remove(val track: Track) : QueueIntent
    data class Move(val fromIndex: Int, val toIndex: Int) : QueueIntent
    data object ClearQueue : QueueIntent
    data object Back : QueueIntent
}

/**
 * Reference MVVM ViewModel for the Queue tab.
 *
 * Inputs (queue, playback snapshot, favorites, language) are pushed in via [bind] from the
 * host's existing state flows. The ViewModel derives the immutable [uiState] consumed by the
 * Compose screen and the matching positional action list, and forwards user actions as
 * [QueueIntent]s on [intents].
 *
 * This is the template the remaining tabs follow: state in via flows, intents out via a
 * SharedFlow, zero direct coupling to the playback service or the Java shell.
 */
class QueueViewModel : ViewModel(), QueueDestinationStateProvider {
    private companion object {
        const val EAGER_QUEUE_ROW_LIMIT = 24
    }


    private val _uiState = MutableStateFlow(QueueDestinationState())
    override val uiState: StateFlow<QueueDestinationState> = _uiState.asStateFlow()

    private val _labels = MutableStateFlow(QueueScreenLabels())
    override val labels: StateFlow<QueueScreenLabels> = _labels.asStateFlow()

    private val _intents = MutableSharedFlow<QueueIntent>(extraBufferCapacity = 16)
    val intents: SharedFlow<QueueIntent> = _intents.asSharedFlow()

    /**
     * Optional Java-friendly intent sink. The (Java) host can't easily collect [intents]
     * (a Kotlin SharedFlow), so it may set this listener to receive each [QueueIntent]
     * synchronously on emission instead. Both paths fire; the SharedFlow remains for Kotlin
     * collectors.
     */
    fun interface IntentListener {
        fun onIntent(intent: QueueIntent)
    }

    @Volatile
    private var intentListener: IntentListener? = null

    fun bindIntentListener(listener: IntentListener?) {
        intentListener = listener
    }

    /** Snapshot of the tracks currently backing each rendered row, by row index. */
    @Volatile
    private var boundTracks: List<Track> = emptyList()

    /**
     * Recomputes the queue UI state from the latest playback inputs.
     * Called by the host whenever the queue, snapshot, favorites, or language change.
     */
    fun bind(
        queue: List<Track>?,
        playbackState: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>,
        languageMode: String
    ) {
        val tracks = queue ?: emptyList()
        boundTracks = tracks
        val currentTrack = playbackState?.currentTrack
        val currentQueueIndex = playbackState?.currentIndex
            ?.takeIf { it in tracks.indices }
        val keyFactory = QueueRowKeyFactory(tracks)
        val eagerTracks = tracks.take(EAGER_QUEUE_ROW_LIMIT)
        val eagerRows = eagerTracks.mapIndexed { index, track ->
            rowForTrack(
                keyFactory,
                index,
                track,
                currentTrack,
                favoriteIds,
                isCurrent = currentQueueIndex?.let { it == index }
                    ?: (currentTrack != null && currentTrack.id == track.id)
            )
        }
        val lazyRows = HashMap<Int, QueueTrackUiState>()
        _uiState.value = QueueDestinationState(
            rows = eagerRows,
            rowCount = tracks.size,
            rowAt = { index ->
                tracks.getOrNull(index)?.let { track ->
                    if (index < eagerRows.size) {
                        eagerRows[index]
                    } else {
                        lazyRows.getOrPut(index) {
                            rowForTrack(
                                keyFactory,
                                index,
                                track,
                                currentTrack,
                                favoriteIds,
                                isCurrent = currentQueueIndex?.let { it == index }
                                    ?: (currentTrack != null && currentTrack.id == track.id)
                            )
                        }
                    }
                }
            }
        )
        _labels.value = QueueScreenLabels(
            title = AppLanguage.text(languageMode, "tab.queue"),
            back = AppLanguage.text(languageMode, "back"),
            clearQueue = AppLanguage.text(languageMode, "clear.queue.title"),
            empty = AppLanguage.text(languageMode, "queue.empty"),
            emptyDescription = AppLanguage.text(languageMode, "queue.empty.description"),
            tracks = AppLanguage.text(languageMode, "tracks"),
            favorite = AppLanguage.text(languageMode, "favorite"),
            addToPlaylist = AppLanguage.text(languageMode, "add.to.playlist"),
            remove = AppLanguage.text(languageMode, "remove"),
            dragReorder = AppLanguage.text(languageMode, "queue.drag.reorder")
        )
    }

    /** The tracks backing the current rows; used by the screen to build positional intents. */
    fun tracks(): List<Track> = boundTracks

    override fun onPlayAt(index: Int) {
        val tracks = boundTracks
        if (index in tracks.indices) {
            emit(QueueIntent.PlayAt(tracks, index))
        }
    }

    override fun onToggleFavorite(index: Int) {
        boundTracks.getOrNull(index)?.let { emit(QueueIntent.ToggleFavorite(it)) }
    }

    override fun onAddToPlaylist(index: Int) {
        boundTracks.getOrNull(index)?.let { emit(QueueIntent.AddToPlaylist(it)) }
    }

    override fun onRemove(index: Int) {
        boundTracks.getOrNull(index)?.let { emit(QueueIntent.Remove(it)) }
    }

    override fun onMove(fromIndex: Int, toIndex: Int) {
        val tracks = boundTracks
        if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
            emit(QueueIntent.Move(fromIndex, toIndex))
        }
    }

    override fun onClearQueue() = emit(QueueIntent.ClearQueue)

    override fun onBack() = emit(QueueIntent.Back)

    private fun emit(intent: QueueIntent) {
        intentListener?.onIntent(intent)
        _intents.tryEmit(intent)
    }

    private fun rowForTrack(
        keyFactory: QueueRowKeyFactory,
        index: Int,
        track: Track,
        currentTrack: Track?,
        favoriteIds: Set<Long>,
        isCurrent: Boolean
    ) = TrackRowStateFactory.queueRow(
        keyFactory.keyFor(index),
        track,
        currentTrack,
        favoriteIds
    ).copy(current = isCurrent)

    private class QueueRowKeyFactory(private val tracks: List<Track>) {
        // Queue rows are lazy. Do not reserve one slot for every track while only the first
        // viewport is rendered; large libraries otherwise allocate an unnecessary full array.
        private val keys = HashMap<Int, String>()
        private val occurrences = HashMap<Long, Int>()
        private var scannedUntil = -1

        fun keyFor(index: Int): String {
            if (index < 0 || index >= tracks.size) {
                return "missing:$index"
            }
            keys[index]?.let { return it }
            if (index > scannedUntil) {
                for (i in (scannedUntil + 1)..index) {
                    val trackId = tracks[i].id
                    val occurrence = (occurrences[trackId] ?: 0) + 1
                    occurrences[trackId] = occurrence
                    keys[i] = "$trackId:$occurrence"
                }
                scannedUntil = index
            }
            return keys[index] ?: TrackRowKeyPolicy.occurrenceKey(tracks, index)
        }
    }
}
