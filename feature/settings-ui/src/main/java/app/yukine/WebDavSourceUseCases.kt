package app.yukine

import app.yukine.model.Track
import app.yukine.model.WebDavSyncResult

interface WebDavSourceOperations {
    fun testRemoteSource(sourceId: Long): String

    fun syncRemoteSource(sourceId: Long): WebDavSyncResult?

    fun loadCachedTracks(): List<Track>

    fun loadFavoriteIds(): Set<Long>
}

data class WebDavSourceSnapshot(
    val cached: List<Track>,
    val favorites: Set<Long>,
    val status: String
)

class TestWebDavSourceUseCase(
    private val operations: WebDavSourceOperations
) {
    fun execute(sourceId: Long): String =
        operations.testRemoteSource(sourceId)
}

class SyncWebDavSourceUseCase(
    private val operations: WebDavSourceOperations
) {
    fun execute(sourceId: Long, sourceName: String): WebDavSourceSnapshot {
        val status = try {
            val result = operations.syncRemoteSource(sourceId)
            result?.summary() ?: "WebDAV sync finished"
        } catch (error: RuntimeException) {
            "WebDAV sync failed: $sourceName ${safeMessage(error)}"
        }
        return operations.snapshot(status)
    }
}

class SyncAllWebDavSourcesUseCase(
    private val operations: WebDavSourceOperations
) {
    fun execute(sourceIds: List<Long>): WebDavSourceSnapshot {
        var total = 0
        var added = 0
        var removed = 0
        var kept = 0
        var success = 0
        var failed = 0
        for (sourceId in sourceIds) {
            try {
                val result = operations.syncRemoteSource(sourceId)
                if (result != null) {
                    total += result.trackCount()
                    added += result.addedCount
                    removed += result.removedCount
                    kept += result.keptCount
                }
                success++
            } catch (ignored: RuntimeException) {
                failed++
            }
        }
        var status = "WebDAV sync: added $added, removed $removed, kept $kept, tracks $total, ok $success"
        if (failed > 0) {
            status += ", failed $failed"
        }
        return operations.snapshot(status)
    }
}

private fun WebDavSourceOperations.snapshot(status: String): WebDavSourceSnapshot =
    WebDavSourceSnapshot(loadCachedTracks(), loadFavoriteIds(), status)

private fun safeMessage(error: RuntimeException): String {
    val message = error.message
    return if (message == null || message.isEmpty()) error.javaClass.simpleName else message
}
