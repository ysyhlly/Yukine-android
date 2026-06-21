package app.yukine

import android.net.Uri

internal fun interface PlaylistExportDocumentOpener {
    fun open(playlistName: String)
}

internal fun interface PlaylistExportAction {
    fun export(exportUri: Uri, playlistId: Long, playlistName: String, callback: PlaylistExportResultCallback)
}

internal fun interface PlaylistExportResultCallback {
    fun onResult(exported: Boolean)
}

internal fun interface PlaylistExportLanguageModeProvider {
    fun languageMode(): String
}

internal class PlaylistExportBindings(
    private val languageModeProvider: PlaylistExportLanguageModeProvider,
    private val openDocumentAction: PlaylistExportDocumentOpener,
    private val exportAction: PlaylistExportAction,
    private val statusSink: SettingsStatusSink
) : PlaylistExportController.Listener {
    override fun openPlaylistExportDocument(playlistName: String) {
        openDocumentAction.open(playlistName)
    }

    override fun exportPlaylist(exportUri: Uri, playlistId: Long, playlistName: String) {
        exportAction.export(exportUri, playlistId, playlistName) { exported ->
            statusSink.set(
                AppLanguage.text(
                    languageModeProvider.languageMode(),
                    if (exported) "playlist.exported" else "playlist.export.failed"
                )
            )
        }
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
