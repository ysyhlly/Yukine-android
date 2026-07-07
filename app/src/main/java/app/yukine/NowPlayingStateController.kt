package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal class NowPlayingStateController(
    private val viewModel: NowPlayingViewModel,
    private val listener: Listener
) {
    private var lastSyncedQueueKey = QueueKey.empty()

    interface Listener {
        fun storesReady(): Boolean

        fun playbackSnapshot(): PlaybackStateSnapshot

        fun favoriteIds(): Set<Long>

        fun lyricsState(): LyricsState?

        fun languageMode(): String

        fun publishFloatingLyrics(state: NowPlayingUiState)

        fun syncQueueInputs()
    }

    fun renderNowBar() {
        if (!listener.storesReady()) {
            return
        }
        val snapshot = listener.playbackSnapshot()
        publish(snapshot)
        syncQueueInputsIfChanged(snapshot)
    }

    fun publish(snapshot: PlaybackStateSnapshot): NowPlayingUiState {
        viewModel.updateState(
            snapshot,
            listener.favoriteIds(),
            listener.lyricsState(),
            listener.languageMode()
        )
        val state = viewModel.uiState.value
        listener.publishFloatingLyrics(state)
        return state
    }

    private fun syncQueueInputsIfChanged(snapshot: PlaybackStateSnapshot) {
        val nextKey = QueueKey.from(snapshot)
        if (nextKey == lastSyncedQueueKey) {
            return
        }
        lastSyncedQueueKey = nextKey
        listener.syncQueueInputs()
    }

    private data class QueueKey(
        val currentTrackId: Long,
        val currentIndex: Int,
        val queueSize: Int
    ) {
        companion object {
            fun empty(): QueueKey = QueueKey(-1L, -1, 0)

            fun from(snapshot: PlaybackStateSnapshot): QueueKey =
                QueueKey(
                    snapshot.currentTrack?.id ?: -1L,
                    snapshot.currentIndex,
                    snapshot.queueSize
                )
        }
    }
}
