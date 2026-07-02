package app.yukine

import android.net.Uri
import app.yukine.model.PlaylistImportResult
import app.yukine.model.StreamImportResult
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContentResolverLibraryDocumentGatewayTest {
    @Test
    fun failedStreamImportFallsBackToCachedSnapshot() {
        val operations = FakeLibraryImportOperations()
        operations.cached = listOf(track(1L))
        operations.favorites = setOf(1L)
        val gateway = ContentResolverLibraryDocumentGateway(null, operations)

        val result = gateway.importStreamM3u(Uri.parse("content://missing/stream.m3u8"))

        assertEquals(listOf(1L), result.tracks.map { it.id })
        assertEquals(setOf(1L), result.favorites)
        assertEquals("Local M3U import failed", result.status)
        assertEquals(listOf("cached", "favorites"), operations.events)
    }

    @Test
    fun failedPlaylistImportFallsBackToCachedSnapshot() {
        val operations = FakeLibraryImportOperations()
        operations.cached = listOf(track(2L))
        operations.favorites = setOf(2L)
        val gateway = ContentResolverLibraryDocumentGateway(null, operations)

        val result = gateway.importPlaylistM3u(Uri.parse("content://missing/list.m3u8"))

        assertEquals(-1L, result.playlistId)
        assertEquals(listOf(2L), result.tracks.map { it.id })
        assertEquals(setOf(2L), result.favorites)
        assertEquals("Playlist import failed", result.status)
        assertEquals(listOf("cached", "favorites"), operations.events)
    }

    @Test
    fun exportPlaylistReturnsFalseWhenDocumentCannotBeOpened() {
        val operations = FakeLibraryImportOperations()
        operations.playlistTracks = listOf(track(5L))
        val gateway = ContentResolverLibraryDocumentGateway(null, operations)

        val exported = gateway.exportPlaylist(Uri.parse("content://missing/export.m3u8"), 8L, "List")

        assertFalse(exported)
        assertEquals(listOf("playlistTracks:8"), operations.events)
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
