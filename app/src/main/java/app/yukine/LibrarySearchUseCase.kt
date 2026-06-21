package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track

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

internal class LibraryCombinedSearchUseCase(
    private val searchUseCase: LibrarySearchUseCase
) {
    fun execute(
        libraryTracks: List<Track>,
        selectedPlaylistTracks: List<Track>,
        query: String?
    ): List<Track> {
        val libraryResults = searchUseCase.execute(libraryTracks, query)
        val playlistResults = searchUseCase.execute(selectedPlaylistTracks, query)
        if (playlistResults.isEmpty()) {
            return libraryResults
        }
        val merged = ArrayList<Track>(libraryResults.size + playlistResults.size)
        val seenKeys = HashSet<String>()
        for (track in libraryResults + playlistResults) {
            if (seenKeys.add(trackSearchKey(track))) {
                merged.add(track)
            }
        }
        return merged
    }

    private fun trackSearchKey(track: Track): String =
        if (track.id >= 0L) {
            "id:${track.id}"
        } else {
            "path:${track.dataPath.orEmpty()}"
        }
}
