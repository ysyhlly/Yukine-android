package app.yukine

import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistActionUseCasesTest {
    @Test
    fun addToDefaultPlaylistEnsuresPlaylistThenAddsTrack() {
        val operations = FakePlaylistActionOperations(defaultPlaylistId = 9L, addTrackResult = true)
        val result = AddToDefaultPlaylistUseCase(operations).execute(track(7L))

        assertEquals(9L, result?.playlistId)
        assertTrue(result?.added == true)
        assertEquals(listOf("ensureDefault", "add:9:7"), operations.events)
    }

    @Test
    fun addToDefaultPlaylistIgnoresMissingTrack() {
        val operations = FakePlaylistActionOperations()

        val result = AddToDefaultPlaylistUseCase(operations).execute(null)

        assertNull(result)
        assertEquals(emptyList<String>(), operations.events)
    }

    @Test
    fun playlistCrudDelegatesToOperations() {
        val operations = FakePlaylistActionOperations(
            createdPlaylistId = 11L,
            renameResult = true,
            deleteResult = true
        )

        assertEquals(11L, CreatePlaylistUseCase(operations).execute("New"))
        assertTrue(RenamePlaylistUseCase(operations).execute(11L, "Renamed"))
        assertTrue(DeletePlaylistUseCase(operations).execute(11L))

        assertEquals(
            listOf("create:New", "rename:11:Renamed", "delete:11"),
            operations.events
        )
    }

    @Test
    fun invalidPlaylistIdsAreIgnored() {
        val operations = FakePlaylistActionOperations()

        assertFalse(RenamePlaylistUseCase(operations).execute(-1L, "Nope"))
        assertFalse(DeletePlaylistUseCase(operations).execute(-1L))
        assertFalse(AddTrackToPlaylistUseCase(operations).execute(-1L, 7L))
        assertFalse(RemoveTrackFromPlaylistUseCase(operations).execute(-1L, track(7L)))
        assertFalse(MovePlaylistTrackUseCase(operations).execute(-1L, track(7L), 0, 1))

        assertEquals(emptyList<String>(), operations.events)
    }

    @Test
    fun trackMutationUseCasesDelegateToOperations() {
        val operations = FakePlaylistActionOperations(addTrackResult = true, moveResult = true)
        val track = track(7L)

        assertTrue(AddTrackToPlaylistUseCase(operations).execute(5L, 7L))
        assertTrue(RemoveTrackFromPlaylistUseCase(operations).execute(5L, track))
        assertTrue(MovePlaylistTrackUseCase(operations).execute(5L, track, 2, -1))

        assertEquals(
            listOf("add:5:7", "remove:5:7", "move:5:2:-1"),
            operations.events
        )
    }

    private class FakePlaylistActionOperations(
        private val defaultPlaylistId: Long = -1L,
        private val createdPlaylistId: Long = -1L,
        private val renameResult: Boolean = false,
        private val deleteResult: Boolean = false,
        private val addTrackResult: Boolean = false,
        private val moveResult: Boolean = false
    ) : PlaylistActionOperations {
        val events = mutableListOf<String>()

        override fun ensureDefaultPlaylist(): Long {
            events.add("ensureDefault")
            return defaultPlaylistId
        }

        override fun createPlaylist(name: String): Long {
            events.add("create:$name")
            return createdPlaylistId
        }

        override fun renamePlaylist(playlistId: Long, name: String): Boolean {
            events.add("rename:$playlistId:$name")
            return renameResult
        }

        override fun deletePlaylist(playlistId: Long): Boolean {
            events.add("delete:$playlistId")
            return deleteResult
        }

        override fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean {
            events.add("add:$playlistId:$trackId")
            return addTrackResult
        }

        override fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
            events.add("remove:$playlistId:$trackId")
        }

        override fun movePlaylistTrackAt(playlistId: Long, trackIndex: Int, direction: Int): Boolean {
            events.add("move:$playlistId:$trackIndex:$direction")
            return moveResult
        }
    }

    private fun track(id: Long): Track = Track(id, "Song", "Artist", "Album", 120_000L, null, "file:song.mp3")
}
