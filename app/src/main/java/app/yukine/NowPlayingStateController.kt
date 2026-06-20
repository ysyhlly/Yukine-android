package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal class NowPlayingStateController(
    private val viewModel: NowPlayingViewModel,
    private val listener: Listener
) {
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
        publish(listener.playbackSnapshot())
        listener.syncQueueInputs()
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
}
