package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.ui.NowPlayingController
import app.echo.next.ui.NowPlayingUiState

internal class NowPlayingRenderController(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun addVirtualContent(view: View)
    }

    private var controller: NowPlayingController? = null

    fun clear() {
        controller = null
    }

    fun render(
        playbackStore: MainPlaybackStore,
        lyricsState: LyricsState?,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ): Boolean {
        val state = createState(playbackStore, lyricsState, languageMode) ?: return false
        controller = NowPlayingController(context, state)
        listener.addVirtualContent(controller!!.view)
        return true
    }

    fun update(
        playbackStore: MainPlaybackStore,
        lyricsState: LyricsState?,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ): Boolean {
        val currentController = controller ?: return false
        val state = createState(playbackStore, lyricsState, languageMode) ?: return false
        currentController.updateState(state)
        return true
    }

    private fun createState(
        playbackStore: MainPlaybackStore,
        lyricsState: LyricsState?,
        languageMode: String
    ): NowPlayingUiState? {
        val state = lyricsState ?: LyricsState()
        return NowPlayingStateFactory.create(
            playbackStore.snapshot(),
            state.lines,
            LyricsStatusText.status(languageMode, state.statusKind, state.loadedLineCount),
            state.offsetMs,
            languageMode
        )
    }
}