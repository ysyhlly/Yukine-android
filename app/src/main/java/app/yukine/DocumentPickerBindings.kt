package app.yukine

import android.net.Uri
import java.util.ArrayList

internal fun interface DocumentAudioUrisImportAction {
    fun import(uris: ArrayList<Uri>)
}

internal fun interface DocumentUriAction {
    fun run(uri: Uri)
}

internal class DocumentPickerBindings(
    private val importAudioUrisAction: DocumentAudioUrisImportAction,
    private val importAudioFolderAction: DocumentUriAction,
    private val chooseDownloadFolderAction: DocumentUriAction,
    private val importStreamM3uAction: DocumentUriAction,
    private val exportPlaylistAction: DocumentUriAction,
    private val importPlaylistM3uAction: DocumentUriAction,
    private val importLuoxueSourceUrisAction: DocumentAudioUrisImportAction
) : DocumentPickerController.Listener {
    override fun importAudioUris(uris: ArrayList<Uri>) {
        importAudioUrisAction.import(uris)
    }

    override fun importAudioFolder(treeUri: Uri) {
        importAudioFolderAction.run(treeUri)
    }

    override fun chooseDownloadFolder(treeUri: Uri) {
        chooseDownloadFolderAction.run(treeUri)
    }

    override fun importStreamM3u(playlistUri: Uri) {
        importStreamM3uAction.run(playlistUri)
    }

    override fun exportPlaylist(exportUri: Uri) {
        exportPlaylistAction.run(exportUri)
    }

    override fun importPlaylistM3u(playlistUri: Uri) {
        importPlaylistM3uAction.run(playlistUri)
    }

    override fun importLuoxueSourceUris(uris: ArrayList<Uri>) {
        importLuoxueSourceUrisAction.import(uris)
    }
}
