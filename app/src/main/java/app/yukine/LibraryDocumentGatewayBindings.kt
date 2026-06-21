package app.yukine

import android.content.ContentResolver
import android.net.Uri

internal class LibraryDocumentGatewayBindings(
    private val contentResolver: ContentResolver?,
    operations: LibraryImportOperations
) : LibraryDocumentGateway {
    private val loadLibraryUseCase = LoadLibraryUseCase(operations)
    private val importStreamM3uTextUseCase = ImportStreamM3uTextUseCase(operations)
    private val importPlaylistM3uTextUseCase = ImportPlaylistM3uTextUseCase(operations)
    private val loadPlaylistExportTracksUseCase = LoadPlaylistExportTracksUseCase(operations)

    override fun importStreamM3u(playlistUri: Uri?): LibraryLoadResultUi {
        val playlistRead = M3uDocumentHelper.readText(contentResolver, playlistUri)
        val imported = if (playlistRead.success) {
            importStreamM3uTextUseCase.execute(playlistRead.text)
        } else {
            null
        }
        val fallback = if (imported == null) loadLibraryUseCase.cached() else null
        val status = M3uDocumentHelper.localImportStatus(
            playlistRead,
            imported?.importResult
        )
        return LibraryLoadResultUi(
            tracks = imported?.tracks ?: fallback?.tracks.orEmpty(),
            favorites = imported?.favorites ?: fallback?.favorites.orEmpty(),
            status = status
        )
    }

    override fun importPlaylistM3u(playlistUri: Uri?): LibraryPlaylistImportResultUi {
        val playlistRead = M3uDocumentHelper.readText(contentResolver, playlistUri)
        val imported = if (playlistRead.success) {
            importPlaylistM3uTextUseCase.execute(
                playlistRead.text,
                M3uDocumentHelper.playlistFallbackName(playlistUri)
            )
        } else {
            null
        }
        val fallback = if (imported == null) loadLibraryUseCase.cached() else null
        val importResult = imported?.importResult
        val playlistId = importResult?.playlistId?.takeIf { it >= 0L } ?: -1L
        val status = M3uDocumentHelper.playlistImportStatus(playlistRead, importResult)
        return LibraryPlaylistImportResultUi(
            playlistId = playlistId,
            tracks = imported?.tracks ?: fallback?.tracks.orEmpty(),
            favorites = imported?.favorites ?: fallback?.favorites.orEmpty(),
            status = status
        )
    }

    override fun exportPlaylist(exportUri: Uri?, playlistId: Long, playlistName: String): Boolean {
        val tracks = loadPlaylistExportTracksUseCase.execute(playlistId)
        return M3uDocumentHelper.writeText(
            contentResolver,
            exportUri,
            M3uDocumentHelper.buildPlaylistText(playlistName, tracks)
        )
    }
}
