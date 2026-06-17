package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.model.WebDavSyncResult
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavSourceUseCasesTest {
    @Test
    fun testSourceDelegatesAndReturnsStatus() {
        val operations = FakeWebDavSourceOperations()
        operations.testStatuses[7L] = "ok"

        val status = TestWebDavSourceUseCase(operations).execute(7L)

        assertEquals("ok", status)
        assertEquals(listOf("test:7"), operations.events)
    }

    @Test
    fun syncSourceReturnsSummaryAndSnapshot() {
        val operations = FakeWebDavSourceOperations()
        operations.results[3L] = WebDavSyncResult(listOf(track(1), track(2)), 2, 1, 4)
        operations.cached = listOf(track(8))
        operations.favorites = setOf(8L)

        val snapshot = SyncWebDavSourceUseCase(operations).execute(3L, "Home")

        assertEquals("added 2, removed 1, kept 4", snapshot.status)
        assertEquals(listOf(8L), snapshot.cached.map { it.id })
        assertEquals(setOf(8L), snapshot.favorites)
        assertEquals(listOf("sync:3", "cached", "favorites"), operations.events)
    }

    @Test
    fun syncSourceConvertsRuntimeExceptionToStatus() {
        val operations = FakeWebDavSourceOperations()
        operations.failures[5L] = RuntimeException("timeout")

        val snapshot = SyncWebDavSourceUseCase(operations).execute(5L, "NAS")

        assertEquals("WebDAV sync failed: NAS timeout", snapshot.status)
        assertEquals(listOf("sync:5", "cached", "favorites"), operations.events)
    }

    @Test
    fun syncAllSourcesAggregatesSuccessAndFailureCounts() {
        val operations = FakeWebDavSourceOperations()
        operations.results[1L] = WebDavSyncResult(listOf(track(1), track(2)), 2, 0, 1)
        operations.results[2L] = WebDavSyncResult(listOf(track(3)), 1, 1, 0)
        operations.failures[3L] = RuntimeException("denied")

        val snapshot = SyncAllWebDavSourcesUseCase(operations).execute(listOf(1L, 2L, 3L))

        assertEquals(
            "WebDAV sync: added 3, removed 1, kept 1, tracks 3, ok 2, failed 1",
            snapshot.status
        )
        assertEquals(listOf("sync:1", "sync:2", "sync:3", "cached", "favorites"), operations.events)
    }

    private class FakeWebDavSourceOperations : WebDavSourceOperations {
        val events = mutableListOf<String>()
        val testStatuses = mutableMapOf<Long, String>()
        val results = mutableMapOf<Long, WebDavSyncResult?>()
        val failures = mutableMapOf<Long, RuntimeException>()
        var cached: List<Track> = emptyList()
        var favorites: Set<Long> = emptySet()

        override fun testRemoteSource(sourceId: Long): String {
            events.add("test:$sourceId")
            return testStatuses[sourceId] ?: ""
        }

        override fun syncRemoteSource(sourceId: Long): WebDavSyncResult? {
            events.add("sync:$sourceId")
            failures[sourceId]?.let { throw it }
            return results[sourceId]
        }

        override fun loadCachedTracks(): List<Track> {
            events.add("cached")
            return cached
        }

        override fun loadFavoriteIds(): Set<Long> {
            events.add("favorites")
            return favorites
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "webdav:$id")
}
