package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCollectionUseCasesTest {
    @Test
    fun loadCollectionsUsesRequestedPlaylistWhenItExists() {
        val operations = FakeLibraryCollectionOperations()
        operations.playlists = listOf(playlist(1L), playlist(2L))
        operations.playlistTracks[2L] = listOf(track(20L))
        operations.favoriteIds = setOf(20L)
        operations.favoriteTracks = listOf(track(20L))
        operations.playedSince = listOf(playRecord(20L))
        operations.mostPlayed = listOf(playRecord(21L))
        operations.remoteSources = listOf(remoteSource(8L))

        val result = LoadLibraryCollectionsUseCase(operations, clockMs = { 10_000L }).execute(2L)

        assertEquals(2L, result.selectedPlaylistId)
        assertEquals(setOf(20L), result.favoriteIds)
        assertEquals(1, result.favoriteTracks.size)
        assertEquals(1, result.recentRecords.size)
        assertEquals(1, result.mostPlayedRecords.size)
        assertEquals(1, result.remoteSources.size)
        assertEquals(listOf(20L), result.selectedPlaylistTracks.map { it.id })
        assertTrue(operations.events.contains("playedSince:${10_000L - 4L * 7L * 24L * 60L * 60L * 1000L}:2000"))
    }

    @Test
    fun loadCollectionsEnsuresDefaultAndFallsBackToFirstPlaylist() {
        val operations = FakeLibraryCollectionOperations(defaultPlaylistId = 99L)
        operations.playlists = listOf(playlist(3L), playlist(4L))
        operations.playlistTracks[3L] = listOf(track(30L))
        operations.recentlyPlayed = listOf(playRecord(30L))

        val result = LoadLibraryCollectionsUseCase(operations, clockMs = { 20_000L }).execute(-1L)

        assertEquals(3L, result.selectedPlaylistId)
        assertEquals(listOf(30L), result.selectedPlaylistTracks.map { it.id })
        assertEquals(listOf(30L), result.recentRecords.map { it.track.id })
        assertEquals("ensureDefault", operations.events.first())
        assertTrue(operations.events.contains("recent:2000"))
    }

    @Test
    fun clearPlayHistoryReturnsRemovedCount() {
        val operations = FakeLibraryCollectionOperations(removedHistory = 5)

        val removed = ClearPlayHistoryUseCase(operations).execute()

        assertEquals(5, removed)
        assertEquals(listOf("clearHistory"), operations.events)
    }

    @Test
    fun setFavoriteIgnoresInvalidTrackId() {
        val operations = FakeLibraryCollectionOperations()

        SetLibraryFavoriteUseCase(operations).execute(-1L, true)

        assertEquals(emptyList<String>(), operations.events)
    }

    @Test
    fun setFavoriteDelegatesValidTrackId() {
        val operations = FakeLibraryCollectionOperations()

        SetLibraryFavoriteUseCase(operations).execute(7L, true)

        assertEquals(listOf("favorite:7:true"), operations.events)
    }

    private class FakeLibraryCollectionOperations(
        private val defaultPlaylistId: Long = -1L,
        private val removedHistory: Int = 0
    ) : LibraryCollectionOperations {
        val events = mutableListOf<String>()
        var playlists: List<Playlist> = emptyList()
        var favoriteIds: Set<Long> = emptySet()
        var favoriteTracks: List<Track> = emptyList()
        var playedSince: List<TrackPlayRecord> = emptyList()
        var recentlyPlayed: List<TrackPlayRecord> = emptyList()
        var mostPlayed: List<TrackPlayRecord> = emptyList()
        var remoteSources: List<RemoteSource> = emptyList()
        val playlistTracks = mutableMapOf<Long, List<Track>>()

        override fun ensureDefaultPlaylist(): Long {
            events.add("ensureDefault")
            return defaultPlaylistId
        }

        override fun loadPlaylists(): List<Playlist> {
            events.add("playlists")
            return playlists
        }

        override fun loadFavoriteIds(): Set<Long> {
            events.add("favoriteIds")
            return favoriteIds
        }

        override fun loadFavoriteTracks(): List<Track> {
            events.add("favoriteTracks")
            return favoriteTracks
        }

        override fun loadPlayedSince(sinceMs: Long, limit: Int): List<TrackPlayRecord> {
            events.add("playedSince:$sinceMs:$limit")
            return playedSince
        }

        override fun loadRecentlyPlayed(limit: Int): List<TrackPlayRecord> {
            events.add("recent:$limit")
            return recentlyPlayed
        }

        override fun loadMostPlayed(limit: Int): List<TrackPlayRecord> {
            events.add("most:$limit")
            return mostPlayed
        }

        override fun loadRemoteSources(): List<RemoteSource> {
            events.add("remoteSources")
            return remoteSources
        }

        override fun loadPlaylistTracks(playlistId: Long): List<Track> {
            events.add("playlistTracks:$playlistId")
            return playlistTracks[playlistId].orEmpty()
        }

        override fun clearPlayHistory(): Int {
            events.add("clearHistory")
            return removedHistory
        }

        override fun setFavorite(trackId: Long, favorite: Boolean) {
            events.add("favorite:$trackId:$favorite")
        }
    }

    private fun playlist(id: Long): Playlist = Playlist(id, "Playlist $id", 1, 0L, 0L)

    private fun track(id: Long): Track = Track(id, "Song $id", "Artist", "Album", 120_000L, null, "file:$id.mp3")

    private fun playRecord(trackId: Long): TrackPlayRecord = TrackPlayRecord(track(trackId), 0L, 1)

    private fun remoteSource(id: Long): RemoteSource =
        RemoteSource(id, RemoteSource.TYPE_WEBDAV, "nas", "https://example.com", "", "", "", "", 0L)
}
