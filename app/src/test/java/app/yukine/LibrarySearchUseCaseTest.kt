package app.yukine

import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchUseCaseTest {
    @Test
    fun delegatesSearchToOperations() {
        val operations = FakeLibrarySearchOperations()
        operations.result = listOf(track(2L))

        val result = LibrarySearchUseCase(operations).execute(listOf(track(1L), track(2L)), "hello")

        assertEquals(listOf(2L), result.map { it.id })
        assertEquals(listOf("search:2:hello"), operations.events)
    }

    private class FakeLibrarySearchOperations : LibrarySearchOperations {
        val events = mutableListOf<String>()
        var result: List<Track> = emptyList()

        override fun search(source: List<Track>, query: String?): List<Track> {
            events.add("search:${source.size}:$query")
            return result
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Song $id", "Artist", "Album", 120_000L, null, "file:$id.mp3")
}
