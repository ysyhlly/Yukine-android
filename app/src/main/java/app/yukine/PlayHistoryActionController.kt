package app.yukine

internal class PlayHistoryActionController(
    private val viewModel: LibraryViewModel,
    private val languageModeProvider: PlayHistoryLanguageModeProvider,
    private val libraryStateStore: PlayHistoryStateStore,
    private val statusSink: PlayHistoryStatusSink,
    private val collectionsReloader: CollectionsReloader
) {
    fun clearPlayHistory() {
        statusSink.setStatus(text("clearing.play.history"))
        viewModel.clearPlayHistory { removed ->
            libraryStateStore.clearPlayHistory()
            statusSink.setStatus(text("cleared.play.history.prefix") + removed)
            collectionsReloader.reload()
        }
    }

    private fun text(key: String): String =
        AppLanguage.text(languageModeProvider.languageMode(), key)
}

internal fun interface PlayHistoryLanguageModeProvider {
    fun languageMode(): String
}

internal fun interface PlayHistoryStatusSink {
    fun setStatus(status: String)
}

internal fun interface PlayHistoryStateStore {
    fun clearPlayHistory()
}
