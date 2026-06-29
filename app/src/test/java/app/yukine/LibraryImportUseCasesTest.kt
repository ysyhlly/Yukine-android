package app.yukine

import android.net.Uri
import android.net.FakeUri
import app.yukine.model.PlaylistImportResult
import app.yukine.model.StreamImportResult
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryImportUseCasesTest {
    @Test
    fun loadLibraryReturnsCachedAndFreshSnapshots() {
        val operations = FakeLibraryImportOperations()
        operations.cached = listOf(track(1L))
        operations.refreshed = listOf(track(2L))
        operations.favorites = setOf(2L)
        val useCase = LoadLibraryUseCase(operations)

        val cached = useCase.cached()
        val fresh = useCase.refresh()

        assertEquals(listOf(1L), cached.tracks.map { it.id })
        assertEquals(listOf(2L), fresh.tracks.map { it.id })
        assertEquals(setOf(2L), fresh.favorites)
        assertEquals(listOf("cached", "favorites", "refresh", "favorites"), operations.events)
    }

    @Test
    fun importAudioUrisDelegatesAndReloadsCachedSnapshot() {
        val operations = FakeLibraryImportOperations()
        operations.cached = listOf(track(7L))
        val result = ImportAudioUrisUseCase(operations).execute(emptyList())

        assertEquals(listOf(7L), result.tracks.map { it.id })
        assertEquals(listOf("importUris:0", "cached", "favorites"), operations.events)
    }

    @Test
    fun parseMissingAudioSpecsSkipsSnapshotWhenNothingChanged() {
        val operations = FakeLibraryImportOperations()
        operations.updatedSpecs = 0

        val result = ParseMissingAudioSpecsUseCase(operations).execute()

        assertEquals(0, result.updatedCount)
        assertTrue(result.tracks.isEmpty())
        assertEquals(listOf("parseSpecs"), operations.events)
    }

    @Test
    fun parseMissingAudioSpecsReloadsSnapshotWhenUpdated() {
        val operations = FakeLibraryImportOperations()
        operations.updatedSpecs = 2
        operations.cached = listOf(track(1L), track(2L))

        val result = ParseMissingAudioSpecsUseCase(operations).execute()

        assertEquals(2, result.updatedCount)
        assertEquals(2, result.tracks.size)
        assertEquals(listOf("parseSpecs", "cached", "favorites"), operations.events)
    }

    @Test
    fun importM3uTextUseCasesReturnImportResultAndSnapshot() {
        val operations = FakeLibraryImportOperations()
        operations.streamImportResult = StreamImportResult(listOf(track(1L)), 1, 1, 0)
        operations.playlistImportResult = PlaylistImportResult(5L, "List", 1, 1, 1, 0)
        operations.cached = listOf(track(1L))

        val stream = ImportStreamM3uTextUseCase(operations).execute("#EXTM3U")
        val playlist = ImportPlaylistM3uTextUseCase(operations).execute("#EXTM3U", "List")

        assertEquals(1, stream.importResult?.candidateCount)
        assertEquals(5L, playlist.importResult?.playlistId)
        assertEquals(
            listOf(
                "importStreamText:#EXTM3U",
                "cached",
                "favorites",
                "importPlaylistText:#EXTM3U:List",
                "cached",
                "favorites"
            ),
            operations.events
        )
    }

    @Test
    fun loadPlaylistExportTracksIgnoresInvalidId() {
        val operations = FakeLibraryImportOperations()

        val result = LoadPlaylistExportTracksUseCase(operations).execute(-1L)

        assertTrue(result.isEmpty())
        assertTrue(operations.events.isEmpty())
    }

    @Test
    fun loadPlaylistExportTracksDelegatesValidId() {
        val operations = FakeLibraryImportOperations()
        operations.playlistTracks = listOf(track(9L))

        val result = LoadPlaylistExportTracksUseCase(operations).execute(3L)

        assertEquals(listOf(9L), result.map { it.id })
        assertEquals(listOf("playlistTracks:3"), operations.events)
    }

    @Test
    fun mainLibraryImportGatewayMapsUseCaseResultsToViewModelUiModels() {
        val operations = FakeLibraryImportOperations()
        operations.cached = listOf(track(1L))
        operations.refreshed = listOf(track(2L))
        operations.favorites = setOf(2L)
        operations.updatedSpecs = 2
        val gateway = MainLibraryImportGateway(operations)

        val cached = gateway.loadCached()
        val refreshed = gateway.refresh()
        val importedUris = gateway.importAudioUris(listOf(FakeUri("content://tracks/1")))
        val importedTree = gateway.importAudioTree(FakeUri("content://tree/library"))
        val parsedSpecs = gateway.parseMissingAudioSpecs()

        assertEquals(listOf(1L), cached.tracks.map { it.id })
        assertEquals("Library updated", cached.status)
        assertEquals(listOf(2L), refreshed.tracks.map { it.id })
        assertEquals(setOf(2L), refreshed.favorites)
        assertEquals(listOf(1L), importedUris.tracks.map { it.id })
        assertEquals(listOf(1L), importedTree.tracks.map { it.id })
        assertEquals(2, parsedSpecs.updatedCount)
        assertEquals(listOf(1L), parsedSpecs.tracks.map { it.id })
        assertEquals(setOf(2L), parsedSpecs.favorites)
        assertEquals(
            listOf(
                "cached",
                "favorites",
                "refresh",
                "favorites",
                "importUris:1",
                "cached",
                "favorites",
                "importTree:content://tree/library",
                "cached",
                "favorites",
                "parseSpecs",
                "cached",
                "favorites"
            ),
            operations.events
        )
    }

    private class FakeLibraryImportOperations : LibraryImportOperations {
        val events = mutableListOf<String>()
        var cached: List<Track> = emptyList()
        var refreshed: List<Track> = emptyList()
        var favorites: Set<Long> = emptySet()
        var updatedSpecs: Int = 0
        var streamImportResult: StreamImportResult? = null
        var playlistImportResult: PlaylistImportResult? = null
        var playlistTracks: List<Track> = emptyList()

        override fun loadCachedTracks(): List<Track> {
            events.add("cached")
            return cached
        }

        override fun loadFavoriteIds(): Set<Long> {
            events.add("favorites")
            return favorites
        }

        override fun refreshFromDevice(): List<Track> {
            events.add("refresh")
            return refreshed
        }

        override fun importAudioUris(uris: List<Uri>) {
            events.add("importUris:${uris.size}")
        }

        override fun importAudioTree(treeUri: Uri) {
            events.add("importTree:$treeUri")
        }

        override fun parseMissingAudioSpecs(): Int {
            events.add("parseSpecs")
            return updatedSpecs
        }

        override fun importM3uTextWithResult(text: String): StreamImportResult? {
            events.add("importStreamText:$text")
            return streamImportResult
        }

        override fun importM3uTextAsPlaylist(text: String, playlistName: String): PlaylistImportResult? {
            events.add("importPlaylistText:$text:$playlistName")
            return playlistImportResult
        }

        override fun loadPlaylistTracks(playlistId: Long): List<Track> {
            events.add("playlistTracks:$playlistId")
            return playlistTracks
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Song $id", "Artist", "Album", 120_000L, null, "file:$id.mp3")
}
