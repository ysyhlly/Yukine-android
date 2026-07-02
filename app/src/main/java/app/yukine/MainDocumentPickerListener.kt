package app.yukine

import android.net.Uri
import java.util.ArrayList

internal fun interface DocumentAudioUrisImporter {
    fun importAudioUris(uris: ArrayList<Uri>)
}

internal fun interface DocumentAudioFolderImporter {
    fun importAudioFolder(treeUri: Uri)
}

internal fun interface DocumentDownloadFolderChooser {
    fun chooseDownloadFolder(treeUri: Uri)
}

internal fun interface DocumentStreamM3uImporter {
    fun importStreamM3u(playlistUri: Uri)
}

internal fun interface DocumentPlaylistExporter {
    fun exportPlaylist(exportUri: Uri, playlistId: Long, playlistName: String)
}

internal fun interface DocumentPlaylistM3uImporter {
    fun importPlaylistM3u(playlistUri: Uri)
}

internal fun interface DocumentLuoxueSourceUrisImporter {
    fun importLuoxueSourceUris(uris: ArrayList<Uri>)
}

internal fun interface MainDocumentPickerListenerFactory {
    fun create(
        audioUrisImporter: DocumentAudioUrisImporter,
        audioFolderImporter: DocumentAudioFolderImporter,
        downloadFolderChooser: DocumentDownloadFolderChooser,
        streamM3uImporter: DocumentStreamM3uImporter,
        playlistExporter: DocumentPlaylistExporter,
        playlistM3uImporter: DocumentPlaylistM3uImporter,
        luoxueSourceUrisImporter: DocumentLuoxueSourceUrisImporter
    ): DocumentPickerController.Listener
}

internal class MainDocumentPickerListener(
    private val audioUrisImporter: DocumentAudioUrisImporter,
    private val audioFolderImporter: DocumentAudioFolderImporter,
    private val downloadFolderChooser: DocumentDownloadFolderChooser,
    private val streamM3uImporter: DocumentStreamM3uImporter,
    private val playlistExporter: DocumentPlaylistExporter,
    private val playlistM3uImporter: DocumentPlaylistM3uImporter,
    private val luoxueSourceUrisImporter: DocumentLuoxueSourceUrisImporter
) : DocumentPickerController.Listener {
    override fun importAudioUris(uris: ArrayList<Uri>) {
        audioUrisImporter.importAudioUris(uris)
    }

    override fun importAudioFolder(treeUri: Uri) {
        audioFolderImporter.importAudioFolder(treeUri)
    }

    override fun chooseDownloadFolder(treeUri: Uri) {
        downloadFolderChooser.chooseDownloadFolder(treeUri)
    }

    override fun importStreamM3u(playlistUri: Uri) {
        streamM3uImporter.importStreamM3u(playlistUri)
    }

    override fun exportPlaylist(exportUri: Uri, playlistId: Long, playlistName: String) {
        playlistExporter.exportPlaylist(exportUri, playlistId, playlistName)
    }

    override fun importPlaylistM3u(playlistUri: Uri) {
        playlistM3uImporter.importPlaylistM3u(playlistUri)
    }

    override fun importLuoxueSourceUris(uris: ArrayList<Uri>) {
        luoxueSourceUrisImporter.importLuoxueSourceUris(uris)
    }
}
