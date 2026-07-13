package app.yukine

import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainLibraryPlaylistActionGatewayTest {
    @Test
    fun addToDefaultPlaylistMapsUseCaseResult() {
        val operations = FakePlaylistActionOperations(defaultPlaylistId = 9L, addTrackResult = true)
        val gateway = MainLibraryPlaylistActionGateway(operations)

        val result = gateway.addToDefaultPlaylist(track(7L))

        assertEquals(9L, result?.playlistId)
        assertTrue(result?.added == true)
        assertEquals(listOf("ensureDefault", "add:9:7"), operations.events)
    }

    @Test
    fun addToDefaultPlaylistIgnoresMissingTrack() {
        val operations = FakePlaylistActionOperations()
        val gateway = MainLibraryPlaylistActionGateway(operations)

        val result = gateway.addToDefaultPlaylist(null)

        assertNull(result)
        assertEquals(emptyList<String>(), operations.events)
    }

    @Test
    fun playlistCrudAndTrackMutationsDelegateThroughUseCases() {
        val operations = FakePlaylistActionOperations(
            createdPlaylistId = 11L,
            renameResult = true,
            deleteResult = true,
            addTrackResult = true,
            moveResult = true
        )
        val gateway = MainLibraryPlaylistActionGateway(operations)
        val track = track(7L)

        assertEquals(11L, gateway.createPlaylist("New"))
        assertTrue(gateway.renamePlaylist(11L, "Renamed"))
        assertTrue(gateway.deletePlaylist(11L))
        assertTrue(gateway.addTrackToPlaylist(5L, 7L))
        assertTrue(gateway.removeTrackFromPlaylist(5L, track))
        assertTrue(gateway.movePlaylistTrack(5L, track, 2, -1))

        assertEquals(
            listOf(
                "create:New",
                "rename:11:Renamed",
                "delete:11",
                "add:5:7",
                "remove:5:7",
                "move:5:2:-1"
            ),
            operations.events
        )
    }

    @Test
    fun invalidPlaylistInputsAreIgnoredByUseCases() {
        val operations = FakePlaylistActionOperations()
        val gateway = MainLibraryPlaylistActionGateway(operations)

        assertFalse(gateway.renamePlaylist(-1L, "Nope"))
        assertFalse(gateway.deletePlaylist(-1L))
        assertFalse(gateway.addTrackToPlaylist(-1L, 7L))
        assertFalse(gateway.removeTrackFromPlaylist(-1L, track(7L)))
        assertFalse(gateway.movePlaylistTrack(-1L, track(7L), 0, 1))

        assertEquals(emptyList<String>(), operations.events)
    }

    private class FakePlaylistActionOperations(
        private val defaultPlaylistId: Long = -1L,
        private val createdPlaylistId: Long = -1L,
        private val renameResult: Boolean = false,
        private val deleteResult: Boolean = false,
        private val addTrackResult: Boolean = false,
        private val removeTrackResult: Boolean = true,
        private val moveResult: Boolean = false
    ) : PlaylistActionOperations {
        val events = mutableListOf<String>()

        override fun ensureDefaultPlaylist(): Long {
            events += "ensureDefault"
            return defaultPlaylistId
        }

        override fun createPlaylist(name: String): Long {
            events += "create:$name"
            return createdPlaylistId
        }

        override fun renamePlaylist(playlistId: Long, name: String): Boolean {
            events += "rename:$playlistId:$name"
            return renameResult
        }

        override fun deletePlaylist(playlistId: Long): Boolean {
            events += "delete:$playlistId"
            return deleteResult
        }

        override fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
            events += "add:$playlistId:$trackId"
            return addTrackResult
        }

        override fun removeTrackFromPlaylist(playlistId: Long, trackId: Long): Boolean {
            events += "remove:$playlistId:$trackId"
            return removeTrackResult
        }

        override fun movePlaylistTrackAt(playlistId: Long, trackIndex: Int, direction: Int): Boolean {
            events += "move:$playlistId:$trackIndex:$direction"
            return moveResult
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Song", "Artist", "Album", 120_000L, null, "file:song.mp3")
}
