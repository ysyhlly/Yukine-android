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

/** Typed feature actions emitted by the platform document picker. */
internal data class DocumentPickerActions @JvmOverloads constructor(
    val audioUrisImporter: DocumentAudioUrisImporter,
    val audioFolderImporter: DocumentAudioFolderImporter,
    val downloadFolderChooser: DocumentDownloadFolderChooser,
    val streamM3uImporter: DocumentStreamM3uImporter,
    val playlistExporter: DocumentPlaylistExporter,
    val playlistM3uImporter: DocumentPlaylistM3uImporter,
    val luoxueSourceUrisImporter: DocumentLuoxueSourceUrisImporter,
    val audioFolderPermissionFailure: Runnable = Runnable {}
)
