package app.yukine

/** Applies the UI/domain consequences of one completed library deletion request. */
internal class LibraryDeletionCompletionOwner(
    private val queueUpdater: RemovedTrackQueueUpdater,
    private val selectionClearer: LibrarySelectionClearer,
    private val languageModeSource: LanguageModeSource,
    private val statusSink: StatusSink,
    private val libraryReloader: LibraryReloader
) : LibraryDeletionCompletionHandler {
    fun interface RemovedTrackQueueUpdater {
        fun removeTracks(trackIds: Set<Long>)
    }

    fun interface LibrarySelectionClearer {
        fun clearSelection()
    }

    fun interface LanguageModeSource {
        fun languageMode(): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    fun interface LibraryReloader {
        fun reload(allowCachedFirst: Boolean)
    }

    override fun onCompleted(result: LibraryDeletionResult) {
        queueUpdater.removeTracks(result.removed.mapTo(linkedSetOf()) { it.id })
        selectionClearer.clearSelection()
        statusSink.setStatus(
            AppLanguage.text(languageModeSource.languageMode(), "library.delete.result")
                .replace("%d", result.removed.size.toString())
                .replace("%f", result.failed.size.toString())
                .replace("%s", result.skipped.size.toString())
        )
        libraryReloader.reload(true)
    }
}

internal fun interface LibraryDeletionCompletionHandler {
    fun onCompleted(result: LibraryDeletionResult)
}
