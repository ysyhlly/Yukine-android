package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCollectionGatewayBindingsTest {
    @Test
    fun loadCollectionsMapsUseCaseSnapshotToViewModelResult() {
        val operations = FakeLibraryCollectionOperations()
        operations.playlists = listOf(playlist(1L), playlist(2L))
        operations.favoriteIds = setOf(20L)
        operations.favoriteTracks = listOf(track(20L))
        operations.playedSince = listOf(playRecord(20L))
        operations.mostPlayed = listOf(playRecord(21L))
        operations.remoteSources = listOf(remoteSource(8L))
        operations.playlistTracks[2L] = listOf(track(22L))
        val gateway = LibraryCollectionGatewayBindings(operations)

        val result = gateway.loadCollections(2L)

        assertEquals(2L, result.selectedPlaylistId)
        assertEquals(setOf(20L), result.favoriteIds)
        assertEquals(listOf(20L), result.favoriteTracks.map { it.id })
        assertEquals(listOf(20L), result.recentRecords.map { it.track.id })
        assertEquals(listOf(21L), result.mostPlayedRecords.map { it.track.id })
        assertEquals(listOf(1L, 2L), result.playlists.map { it.id })
        assertEquals(listOf(8L), result.remoteSources.map { it.id })
        assertEquals(listOf(22L), result.selectedPlaylistTracks.map { it.id })
        assertTrue(operations.events.contains("playlistTracks:2"))
    }

    @Test
    fun clearPlayHistoryForwardsToUseCase() {
        val operations = FakeLibraryCollectionOperations(removedHistory = 4)
        val gateway = LibraryCollectionGatewayBindings(operations)

        val removed = gateway.clearPlayHistory()

        assertEquals(4, removed)
        assertEquals(listOf("clearHistory"), operations.events)
    }

    @Test
    fun setFavoriteForwardsValidTrackToUseCase() {
        val operations = FakeLibraryCollectionOperations()
        val gateway = LibraryCollectionGatewayBindings(operations)

        gateway.setFavorite(7L, true)

        assertEquals(listOf("favorite:7:true"), operations.events)
    }

    @Test
    fun setFavoriteKeepsUseCaseInvalidIdGuard() {
        val operations = FakeLibraryCollectionOperations()
        val gateway = LibraryCollectionGatewayBindings(operations)

        gateway.setFavorite(-1L, true)

        assertEquals(emptyList<String>(), operations.events)
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
            events.add("playedSince:$limit")
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

    private fun playlist(id: Long): Playlist =
        Playlist(id, "Playlist $id", 1, 0L, 0L)

    private fun track(id: Long): Track =
        Track(id, "Song $id", "Artist", "Album", 120_000L, null, "file:$id.mp3")

    private fun playRecord(trackId: Long): TrackPlayRecord =
        TrackPlayRecord(track(trackId), 0L, 1)

    private fun remoteSource(id: Long): RemoteSource =
        RemoteSource(id, RemoteSource.TYPE_WEBDAV, "nas", "https://example.com", "", "", "", "", 0L)
}
