package app.yukine

import android.content.ContentResolver
import android.net.Uri
import app.yukine.streaming.LuoxueImportedSource
import app.yukine.streaming.LuoxueSourceImporter
import app.yukine.streaming.LuoxueSourceStoreManager

internal class LuoxueSourceImportController(
    private val textProvider: LuoxueSourceImportTextProvider,
    private val documentPickerProvider: LuoxueSourceDocumentPickerProvider,
    private val documentReader: LuoxueSourceDocumentReader,
    private val sourceStore: LuoxueSourceStoreManager,
    private val ioExecutor: LuoxueSourceTaskExecutor,
    private val networkExecutor: LuoxueSourceTaskExecutor,
    private val mainPoster: LuoxueSourceTaskPoster,
    private val statusSink: LuoxueSourceImportStatusSink,
    private val completionAction: LuoxueSourceImportCompletionAction
) {
    fun openFilePicker() {
        val picker = documentPickerProvider.documentPicker()
        if (picker == null) {
            statusSink.setStatus(text("streaming.lx.import.failed"))
            return
        }
        picker.openLuoxueSourceFilePicker()
    }

    fun importSelectedUris(uris: List<Uri>?) {
        if (uris.isNullOrEmpty()) {
            statusSink.setStatus(text("streaming.lx.source.none"))
            return
        }
        statusSink.setStatus(text("streaming.lx.importing"))
        ioExecutor.execute {
            val parsed = mutableListOf<LuoxueImportedSource>()
            var failed = 0
            uris.forEach { uri ->
                val text = documentReader.readText(uri)
                if (text == null) {
                    failed += 1
                } else {
                    parsed += LuoxueSourceImporter.parseMany(text, uri.toString())
                }
            }
            val failedCount = failed
            mainPoster.post {
                saveImportedSources(parsed, failedCount)
            }
        }
    }

    fun importFromUrls(rawUrls: String?) {
        val urls = LuoxueSourceImporter.urlLines(rawUrls)
        if (urls.isEmpty()) {
            statusSink.setStatus(text("streaming.lx.source.url.empty"))
            return
        }
        statusSink.setStatus(text("streaming.lx.importing"))
        networkExecutor.execute {
            val parsed = mutableListOf<LuoxueImportedSource>()
            var failed = 0
            urls.forEach { url ->
                try {
                    val script = LuoxueSourceImporter.fetchUrl(url)
                    parsed += LuoxueSourceImporter.parseMany(script, url)
                } catch (_: Exception) {
                    failed += 1
                }
            }
            val failedCount = failed
            mainPoster.post {
                saveImportedSources(parsed, failedCount)
            }
        }
    }

    internal fun saveImportedSources(sources: List<LuoxueImportedSource>?, failedCount: Int) {
        val cleanSources = sources.orEmpty()
        val imported = if (cleanSources.isEmpty()) 0 else sourceStore.saveAll(cleanSources)
        var status = text("streaming.lx.source.imported") + imported
        if (failedCount > 0) {
            status += "\uff0c" + text("streaming.lx.source.failed") + failedCount
        }
        if (imported == 0 && failedCount == 0) {
            status = text("streaming.lx.source.none")
        }
        statusSink.setStatus(status)
        completionAction.onImportComplete()
    }

    fun importedSources(): List<LuoxueImportedSource> = sourceStore.load()

    fun setSourceEnabled(sourceId: String, enabled: Boolean): Boolean {
        return sourceStore.setEnabled(sourceId, enabled).also(::publishSourceChange)
    }

    fun moveSource(sourceId: String, direction: Int): Boolean {
        return sourceStore.move(sourceId, direction).also(::publishSourceChange)
    }

    fun removeSource(sourceId: String): Boolean {
        return sourceStore.remove(sourceId).also(::publishSourceChange)
    }

    fun setAllSourcesEnabled(enabled: Boolean): Boolean {
        return sourceStore.setAllEnabled(enabled).also(::publishSourceChange)
    }

    private fun publishSourceChange(changed: Boolean) {
        statusSink.setStatus(
            text(
                if (changed) {
                    "streaming.lx.source.updated"
                } else {
                    "streaming.lx.source.update.failed"
                }
            )
        )
        if (changed) {
            completionAction.onImportComplete()
        }
    }

    private fun text(key: String): String = textProvider.text(key)
}

internal fun interface LuoxueSourceImportTextProvider {
    fun text(key: String): String
}

internal fun interface LuoxueSourceDocumentPickerProvider {
    fun documentPicker(): LuoxueSourceFilePicker?
}

internal fun interface LuoxueSourceFilePicker {
    fun openLuoxueSourceFilePicker()
}

internal fun interface LuoxueSourceDocumentReader {
    fun readText(uri: Uri): String?
}

internal class ContentResolverLuoxueSourceDocumentReader(
    private val contentResolver: ContentResolver?
) : LuoxueSourceDocumentReader {
    override fun readText(uri: Uri): String? {
        val read = M3uDocumentHelper.readText(contentResolver, uri)
        return if (read == null || !read.success) null else read.text
    }
}

internal fun interface LuoxueSourceTaskExecutor {
    fun execute(task: Runnable)
}

internal fun interface LuoxueSourceTaskPoster {
    fun post(task: Runnable)
}

internal fun interface LuoxueSourceImportStatusSink {
    fun setStatus(status: String)
}

internal fun interface LuoxueSourceImportCompletionAction {
    fun onImportComplete()
}
