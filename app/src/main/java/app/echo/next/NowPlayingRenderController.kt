package app.echo.next

import app.echo.next.ui.NowPlayingUiState

internal class NowPlayingRenderController {
    fun clear() = Unit

    fun render(
        playbackStore: MainPlaybackStore,
        lyricsState: LyricsState?,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ): Boolean {
        return createState(playbackStore, lyricsState, languageMode) != null
    }

    fun update(
        playbackStore: MainPlaybackStore,
        lyricsState: LyricsState?,
        languageMode: String = AppLanguage.MODE_ENGLISH
    ): Boolean {
        return createState(playbackStore, lyricsState, languageMode) != null
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
