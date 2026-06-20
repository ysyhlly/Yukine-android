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

    @Test
    fun combinedSearchIncludesCurrentPlaylistMatchesAndDeduplicatesLibraryMatches() {
        val operations = FilteringLibrarySearchOperations()
        val useCase = LibraryCombinedSearchUseCase(LibrarySearchUseCase(operations))

        val result = useCase.execute(
            libraryTracks = listOf(
                track(1L, "Library Alpha"),
                track(2L, "Shared Echo")
            ),
            selectedPlaylistTracks = listOf(
                track(2L, "Shared Echo"),
                track(3L, "Playlist Echo")
            ),
            query = "echo"
        )

        assertEquals(listOf(2L, 3L), result.map { it.id })
        assertEquals(listOf("search:2:echo", "search:2:echo"), operations.events)
    }

    private class FakeLibrarySearchOperations : LibrarySearchOperations {
        val events = mutableListOf<String>()
        var result: List<Track> = emptyList()

        override fun search(source: List<Track>, query: String?): List<Track> {
            events.add("search:${source.size}:$query")
            return result
        }
    }

    private class FilteringLibrarySearchOperations : LibrarySearchOperations {
        val events = mutableListOf<String>()

        override fun search(source: List<Track>, query: String?): List<Track> {
            events.add("search:${source.size}:$query")
            val normalized = query.orEmpty().lowercase()
            if (normalized.isBlank()) {
                return source
            }
            return source.filter { track ->
                track.title.lowercase().contains(normalized) ||
                    track.artist.lowercase().contains(normalized) ||
                    track.album.lowercase().contains(normalized)
            }
        }
    }

    private fun track(id: Long): Track =
        track(id, "Song $id")

    private fun track(id: Long, title: String): Track =
        Track(id, title, "Artist", "Album", 120_000L, null, "file:$id.mp3")
}
