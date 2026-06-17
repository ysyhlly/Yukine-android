package app.yukine

import app.yukine.model.StreamImportResult
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLibraryUseCasesTest {
    @Test
    fun addStreamUrlDelegatesAndLoadsSnapshot() {
        val operations = FakeNetworkLibraryOperations()
        operations.addedTrack = track(7L)
        operations.cached = listOf(track(7L))
        operations.favorites = setOf(7L)

        val result = AddStreamUrlUseCase(operations).execute("Radio", "https://example.com/radio.mp3")

        assertEquals(7L, result.track?.id)
        assertEquals(listOf(7L), result.snapshot.cached.map { it.id })
        assertEquals(setOf(7L), result.snapshot.favorites)
        assertEquals(listOf("add:Radio:https://example.com/radio.mp3", "cached", "favorites"), operations.events)
    }

    @Test
    fun updateStreamUrlIgnoresMissingTrack() {
        val operations = FakeNetworkLibraryOperations()

        val result = UpdateStreamUrlUseCase(operations).execute(null, "Radio", "https://example.com")

        assertNull(result)
        assertTrue(operations.events.isEmpty())
    }

    @Test
    fun importStreamPlaylistReturnsImportResultAndSnapshot() {
        val operations = FakeNetworkLibraryOperations()
        operations.importResult = StreamImportResult(listOf(track(1L), track(2L)), 2, 1, 1)
        operations.cached = listOf(track(1L), track(2L))

        val result = ImportStreamPlaylistUseCase(operations).execute("https://example.com/list.m3u")

        assertEquals(2, result.importResult?.candidateCount)
        assertEquals(2, result.snapshot.cached.size)
        assertEquals(listOf("import:https://example.com/list.m3u", "cached", "favorites"), operations.events)
    }

    @Test
    fun deleteOperationsRefreshSnapshot() {
        val operations = FakeNetworkLibraryOperations()
        operations.cached = listOf(track(3L))

        DeleteAllStreamsUseCase(operations).execute()
        DeleteNetworkTrackUseCase(operations).execute(3L)
        DeleteNetworkTracksUseCase(operations).execute(listOf(3L, 5L, 3L, -1L))
        DeleteRemoteSourceUseCase(operations).execute(4L)

        assertEquals(
            listOf(
                "deleteAll",
                "cached",
                "favorites",
                "deleteTrack:3",
                "cached",
                "favorites",
                "deleteTracks:3,5",
                "cached",
                "favorites",
                "deleteSource:4",
                "cached",
                "favorites"
            ),
            operations.events
        )
    }

    @Test
    fun invalidDeleteIdsOnlyRefreshSnapshot() {
        val operations = FakeNetworkLibraryOperations()

        DeleteNetworkTrackUseCase(operations).execute(-1L)
        DeleteRemoteSourceUseCase(operations).execute(-1L)

        assertEquals(listOf("cached", "favorites", "cached", "favorites"), operations.events)
    }

    @Test
    fun saveWebDavSourceDelegatesAndRefreshesSnapshot() {
        val operations = FakeNetworkLibraryOperations()
        operations.savedSourceId = 12L

        val result = SaveWebDavSourceUseCase(operations).execute(
            sourceId = -1L,
            name = "nas",
            baseUrl = "https://example.com",
            username = "u",
            password = "p",
            rootPath = "music"
        )

        assertEquals(12L, result.savedSourceId)
        assertEquals(
            listOf("saveSource:-1:nas:https://example.com:u:p:music", "cached", "favorites"),
            operations.events
        )
    }

    private class FakeNetworkLibraryOperations : NetworkLibraryOperations {
        val events = mutableListOf<String>()
        var cached: List<Track> = emptyList()
        var favorites: Set<Long> = emptySet()
        var addedTrack: Track? = null
        var updatedTrack: Track? = null
        var importResult: StreamImportResult? = null
        var savedSourceId: Long = -1L

        override fun addStreamUrl(title: String, url: String): Track? {
            events.add("add:$title:$url")
            return addedTrack
        }

        override fun updateStreamUrl(trackId: Long, title: String, url: String): Track? {
            events.add("update:$trackId:$title:$url")
            return updatedTrack
        }

        override fun importM3uPlaylistWithResult(url: String): StreamImportResult? {
            events.add("import:$url")
            return importResult
        }

        override fun deleteAllStreams() {
            events.add("deleteAll")
        }

        override fun deleteTrack(trackId: Long) {
            events.add("deleteTrack:$trackId")
        }

        override fun deleteTracks(trackIds: List<Long>) {
            events.add("deleteTracks:${trackIds.joinToString(",")}")
        }

        override fun deleteRemoteSource(sourceId: Long) {
            events.add("deleteSource:$sourceId")
        }

        override fun saveWebDavSource(
            sourceId: Long,
            name: String,
            baseUrl: String,
            username: String,
            password: String,
            rootPath: String
        ): Long {
            events.add("saveSource:$sourceId:$name:$baseUrl:$username:$password:$rootPath")
            return savedSourceId
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
        Track(id, "Song $id", "Artist", "Album", 120_000L, null, "file:$id.mp3")
}
