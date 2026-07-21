package app.yukine

import android.net.Uri
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.PlaylistImportResult
import app.yukine.model.StreamImportResult
import app.yukine.model.Track

internal data class LibraryLoadResult(
    val tracks: List<Track>,
    val favorites: Set<Long>
)

internal data class AudioSpecsParseResult(
    val updatedCount: Int,
    val tracks: List<Track>,
    val favorites: Set<Long>
)

internal data class StreamM3uImportResult(
    val importResult: StreamImportResult?,
    val tracks: List<Track>,
    val favorites: Set<Long>
)

internal data class PlaylistM3uImportResult(
    val importResult: PlaylistImportResult?,
    val tracks: List<Track>,
    val favorites: Set<Long>
)

internal interface LibraryImportOperations {
    fun loadCachedTracks(): List<Track>
    fun loadFavoriteIds(): Set<Long>
    fun refreshFromDevice(): List<Track>
    fun importAudioUris(uris: List<Uri>)
    fun importAudioTree(treeUri: Uri)
    fun parseMissingAudioSpecs(): Int
    fun importM3uTextWithResult(text: String): StreamImportResult?
    fun importM3uTextAsPlaylist(text: String, playlistName: String): PlaylistImportResult?
    fun loadPlaylistTracks(playlistId: Long): List<Track>
}

/** Optional capability so older test doubles and import paths keep their existing contract. */
internal interface LibraryRefreshProgressOperations {
    fun refreshFromDevice(onProgress: (LibraryRefreshProgress) -> Unit): List<Track>
}

internal class MusicLibraryImportOperations(
    private val repository: MusicLibraryRepository
) : LibraryImportOperations, LibraryRefreshProgressOperations {
    override fun loadCachedTracks(): List<Track> = repository.loadCachedTracks()

    override fun loadFavoriteIds(): Set<Long> = repository.loadFavoriteIds()

    override fun refreshFromDevice(): List<Track> = repository.refreshFromDevice()

    override fun refreshFromDevice(onProgress: (LibraryRefreshProgress) -> Unit): List<Track> =
        repository.refreshFromDevice(LibraryRefreshProgressListener(onProgress))

    override fun importAudioUris(uris: List<Uri>) {
        repository.importAudioUris(uris)
    }

    override fun importAudioTree(treeUri: Uri) {
        repository.importAudioTree(treeUri)
    }

    override fun parseMissingAudioSpecs(): Int = repository.parseMissingAudioSpecs()

    override fun importM3uTextWithResult(text: String): StreamImportResult? =
        repository.importM3uTextWithResult(text)

    override fun importM3uTextAsPlaylist(text: String, playlistName: String): PlaylistImportResult? =
        repository.importM3uTextAsPlaylist(text, playlistName)

    override fun loadPlaylistTracks(playlistId: Long): List<Track> =
        repository.loadPlaylistTracks(playlistId)
}

internal class LoadLibraryUseCase(
    private val operations: LibraryImportOperations
) {
    fun cached(): LibraryLoadResult =
        LibraryLoadResult(operations.loadCachedTracks(), operations.loadFavoriteIds())

    fun refresh(): LibraryLoadResult =
        LibraryLoadResult(operations.refreshFromDevice(), operations.loadFavoriteIds())

    fun refresh(onProgress: (LibraryRefreshProgress) -> Unit): LibraryLoadResult {
        val tracks = (operations as? LibraryRefreshProgressOperations)
            ?.refreshFromDevice(onProgress)
            ?: operations.refreshFromDevice()
        return LibraryLoadResult(tracks, operations.loadFavoriteIds())
    }
}

internal class ImportAudioUrisUseCase(
    private val operations: LibraryImportOperations
) {
    fun execute(uris: List<Uri>): LibraryLoadResult {
        operations.importAudioUris(uris)
        return operations.cachedSnapshot()
    }
}

internal class ImportAudioTreeUseCase(
    private val operations: LibraryImportOperations
) {
    fun execute(treeUri: Uri): LibraryLoadResult {
        operations.importAudioTree(treeUri)
        return operations.cachedSnapshot()
    }
}

internal class ParseMissingAudioSpecsUseCase(
    private val operations: LibraryImportOperations
) {
    fun execute(): AudioSpecsParseResult {
        val updatedCount = operations.parseMissingAudioSpecs()
        return if (updatedCount <= 0) {
            AudioSpecsParseResult(updatedCount, emptyList(), emptySet())
        } else {
            AudioSpecsParseResult(
                updatedCount,
                operations.loadCachedTracks(),
                operations.loadFavoriteIds()
            )
        }
    }
}

internal class ImportStreamM3uTextUseCase(
    private val operations: LibraryImportOperations
) {
    fun execute(text: String): StreamM3uImportResult =
        StreamM3uImportResult(
            operations.importM3uTextWithResult(text),
            operations.loadCachedTracks(),
            operations.loadFavoriteIds()
        )
}

internal class ImportPlaylistM3uTextUseCase(
    private val operations: LibraryImportOperations
) {
    fun execute(text: String, playlistName: String): PlaylistM3uImportResult =
        PlaylistM3uImportResult(
            operations.importM3uTextAsPlaylist(text, playlistName),
            operations.loadCachedTracks(),
            operations.loadFavoriteIds()
        )
}

internal class LoadPlaylistExportTracksUseCase(
    private val operations: LibraryImportOperations
) {
    fun execute(playlistId: Long): List<Track> {
        if (playlistId < 0L) {
            return emptyList()
        }
        return operations.loadPlaylistTracks(playlistId)
    }
}

private fun LibraryImportOperations.cachedSnapshot(): LibraryLoadResult =
    LibraryLoadResult(loadCachedTracks(), loadFavoriteIds())

internal class MainLibraryImportGateway(
    private val operations: LibraryImportOperations
) : LibraryImportGateway, LibraryRefreshProgressGateway {
    override fun loadCached(): LibraryLoadResultUi =
        LoadLibraryUseCase(operations).cached().toUi().copy(scanned = false)

    override fun refresh(): LibraryLoadResultUi =
        LoadLibraryUseCase(operations).refresh().toUi()

    override fun refresh(onProgress: (LibraryRefreshProgress) -> Unit): LibraryLoadResultUi =
        LoadLibraryUseCase(operations).refresh(onProgress).toUi()

    override fun importAudioUris(uris: List<Uri>): LibraryLoadResultUi =
        ImportAudioUrisUseCase(operations).execute(uris).toUi()

    override fun importAudioTree(treeUri: Uri): LibraryLoadResultUi =
        ImportAudioTreeUseCase(operations).execute(treeUri).toUi()

    override fun parseMissingAudioSpecs(): LibraryAudioSpecsResultUi {
        val result = ParseMissingAudioSpecsUseCase(operations).execute()
        return LibraryAudioSpecsResultUi(
            updatedCount = result.updatedCount,
            tracks = result.tracks,
            favorites = result.favorites
        )
    }

    private fun LibraryLoadResult.toUi(): LibraryLoadResultUi =
        LibraryLoadResultUi(tracks, favorites, "Library updated")
}
