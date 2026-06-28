package app.yukine

internal class PlayHistoryActionController(
    private val viewModel: LibraryViewModel,
    private val languageModeProvider: () -> String,
    private val libraryStateStore: PlayHistoryStateStore,
    private val statusSink: (String) -> Unit,
    private val collectionsReloadAction: Runnable
) {
    fun clearPlayHistory() {
        statusSink(text("clearing.play.history"))
        viewModel.clearPlayHistory { removed ->
            libraryStateStore.clearPlayHistory()
            statusSink(text("cleared.play.history.prefix") + removed)
            collectionsReloadAction.run()
        }
    }

    private fun text(key: String): String =
        AppLanguage.text(languageModeProvider(), key)
}

internal fun interface PlayHistoryStateStore {
    fun clearPlayHistory()
}
