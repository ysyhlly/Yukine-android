package app.echo.next

import android.content.Context
import android.view.View
import app.echo.next.model.LyricsLine
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
        lyricsController: LyricsController?,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ): Boolean {
        val state = createState(playbackStore, lyricsController, languageMode) ?: return false
        controller = NowPlayingController(context, state)
        listener.addVirtualContent(controller!!.view)
        return true
    }

    fun update(
        playbackStore: MainPlaybackStore,
        lyricsController: LyricsController?,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ): Boolean {
        val currentController = controller ?: return false
        val state = createState(playbackStore, lyricsController, languageMode) ?: return false
        currentController.updateState(state)
        return true
    }

    private fun createState(
        playbackStore: MainPlaybackStore,
        lyricsController: LyricsController?,
        languageMode: String
    ): NowPlayingUiState? {
        if (lyricsController == null) {
            return NowPlayingStateFactory.create(
                playbackStore.snapshot(),
                emptyList<LyricsLine>(),
                AppLanguage.text(languageMode, "lyrics.not.loaded"),
                0L,
                languageMode
            )
        }
        return NowPlayingStateFactory.create(
            playbackStore.snapshot(),
            lyricsController.lines(),
            lyricsController.status(),
            lyricsController.offsetMs(),
            languageMode
        )
    }
}
