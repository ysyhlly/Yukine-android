package app.yukine

import android.net.Uri

private const val LIBRARY_UPDATED_STATUS = "Library updated"

internal class LibraryImportGatewayBindings(
    operations: LibraryImportOperations
) : LibraryImportGateway {
    private val loadLibraryUseCase = LoadLibraryUseCase(operations)
    private val importAudioUrisUseCase = ImportAudioUrisUseCase(operations)
    private val importAudioTreeUseCase = ImportAudioTreeUseCase(operations)
    private val parseMissingAudioSpecsUseCase = ParseMissingAudioSpecsUseCase(operations)

    override fun loadCached(): LibraryLoadResultUi =
        loadLibraryUseCase.cached().toUi()

    override fun refresh(): LibraryLoadResultUi =
        loadLibraryUseCase.refresh().toUi()

    override fun importAudioUris(uris: List<Uri>): LibraryLoadResultUi =
        importAudioUrisUseCase.execute(uris).toUi()

    override fun importAudioTree(treeUri: Uri): LibraryLoadResultUi =
        importAudioTreeUseCase.execute(treeUri).toUi()

    override fun parseMissingAudioSpecs(): LibraryAudioSpecsResultUi {
        val result = parseMissingAudioSpecsUseCase.execute()
        return LibraryAudioSpecsResultUi(
            updatedCount = result.updatedCount,
            tracks = result.tracks,
            favorites = result.favorites
        )
    }

    private fun LibraryLoadResult.toUi(): LibraryLoadResultUi =
        LibraryLoadResultUi(tracks, favorites, LIBRARY_UPDATED_STATUS)
}
