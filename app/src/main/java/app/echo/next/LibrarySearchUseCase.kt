package app.echo.next

import app.echo.next.data.MusicLibraryRepository
import app.echo.next.model.Track

internal interface LibrarySearchOperations {
    fun search(source: List<Track>, query: String?): List<Track>
}

internal class MusicLibrarySearchOperations(
    private val repository: MusicLibraryRepository
) : LibrarySearchOperations {
    override fun search(source: List<Track>, query: String?): List<Track> =
        repository.search(source, query)
}

internal class LibrarySearchUseCase(
    private val operations: LibrarySearchOperations
) {
    fun execute(source: List<Track>, query: String?): List<Track> =
        operations.search(source, query)
}
