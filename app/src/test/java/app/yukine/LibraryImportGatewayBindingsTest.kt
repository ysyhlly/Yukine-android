package app.yukine

import android.net.Uri
import app.yukine.model.PlaylistImportResult
import app.yukine.model.StreamImportResult
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryImportGatewayBindingsTest {
    @Test
    fun loadCachedAndRefreshMapSnapshotsToUiResults() {
        val operations = FakeLibraryImportOperations()
        operations.cached = listOf(track(1L))
        operations.refreshed = listOf(track(2L))
        operations.favorites = setOf(2L)
        val gateway = LibraryImportGatewayBindings(operations)

        val cached = gateway.loadCached()
        val refreshed = gateway.refresh()

        assertEquals(listOf(1L), cached.tracks.map { it.id })
        assertEquals(listOf(2L), refreshed.tracks.map { it.id })
        assertEquals(setOf(2L), refreshed.favorites)
        assertEquals("Library updated", cached.status)
        assertEquals("Library updated", refreshed.status)
        assertEquals(listOf("cached", "favorites", "refresh", "favorites"), operations.events)
    }

    @Test
    fun importAudioUrisAndTreeReloadCachedSnapshot() {
        val operations = FakeLibraryImportOperations()
        operations.cached = listOf(track(7L))
        val gateway = LibraryImportGatewayBindings(operations)
        val uri = Uri.parse("content://music/7")

        val urisResult = gateway.importAudioUris(listOf(uri))
        val treeResult = gateway.importAudioTree(uri)

        assertEquals(listOf(7L), urisResult.tracks.map { it.id })
        assertEquals(listOf(7L), treeResult.tracks.map { it.id })
        assertEquals("Library updated", urisResult.status)
        assertEquals("Library updated", treeResult.status)
        assertEquals(
            listOf("importUris:1", "cached", "favorites", "importTree", "cached", "favorites"),
            operations.events
        )
    }

    @Test
    fun parseMissingAudioSpecsMapsUpdatedSnapshot() {
        val operations = FakeLibraryImportOperations()
        operations.updatedSpecs = 2
        operations.cached = listOf(track(1L), track(2L))
        operations.favorites = setOf(1L)
        val gateway = LibraryImportGatewayBindings(operations)

        val result = gateway.parseMissingAudioSpecs()

        assertEquals(2, result.updatedCount)
        assertEquals(listOf(1L, 2L), result.tracks.map { it.id })
        assertEquals(setOf(1L), result.favorites)
        assertEquals(listOf("parseSpecs", "cached", "favorites"), operations.events)
    }

    @Test
    fun parseMissingAudioSpecsKeepsEmptySnapshotWhenNothingChanged() {
        val operations = FakeLibraryImportOperations()
        operations.updatedSpecs = 0
        val gateway = LibraryImportGatewayBindings(operations)

        val result = gateway.parseMissingAudioSpecs()

        assertEquals(0, result.updatedCount)
        assertEquals(emptyList<Track>(), result.tracks)
        assertEquals(emptySet<Long>(), result.favorites)
        assertEquals(listOf("parseSpecs"), operations.events)
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
            events.add("importTree")
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
