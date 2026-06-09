package app.echo.next

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.app.Activity
import androidx.activity.ComponentActivity
import java.util.ArrayList

internal class DocumentPickerController(
    private val activity: ComponentActivity,
    private val listener: Listener
) {
    interface Listener {
        fun importAudioUris(uris: ArrayList<Uri>)

        fun importAudioFolder(treeUri: Uri)

        fun importStreamM3u(playlistUri: Uri)

        fun exportPlaylist(exportUri: Uri)

        fun importPlaylistM3u(playlistUri: Uri)
    }

    fun openAudioFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "audio/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        activity.startActivityForResult(intent, REQUEST_IMPORT_AUDIO_FILES)
    }

    fun openAudioFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        activity.startActivityForResult(intent, REQUEST_IMPORT_AUDIO_FOLDER)
    }

    fun openM3uFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        M3uDocumentHelper.configureReadIntent(intent)
        activity.startActivityForResult(intent, REQUEST_IMPORT_M3U_FILE)
    }

    fun openPlaylistM3uFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        M3uDocumentHelper.configureReadIntent(intent)
        activity.startActivityForResult(intent, REQUEST_IMPORT_PLAYLIST_M3U_FILE)
    }

    fun openPlaylistExportDocument(playlistName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/vnd.apple.mpegurl"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, M3uDocumentHelper.MIME_TYPES)
        intent.putExtra(Intent.EXTRA_TITLE, M3uDocumentHelper.exportFileName(playlistName))
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        activity.startActivityForResult(intent, REQUEST_EXPORT_PLAYLIST_M3U)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return isDocumentRequest(requestCode)
        }
        if (requestCode == REQUEST_IMPORT_AUDIO_FILES) {
            val uris = selectedAudioUris(data)
            for (uri in uris) {
                takePersistableReadPermission(data, uri)
            }
            listener.importAudioUris(uris)
            return true
        }
        if (requestCode == REQUEST_IMPORT_AUDIO_FOLDER) {
            val treeUri = data.data
            if (treeUri != null) {
                takePersistableReadPermission(data, treeUri)
                listener.importAudioFolder(treeUri)
            }
            return true
        }
        if (requestCode == REQUEST_IMPORT_M3U_FILE) {
            val playlistUri = data.data
            if (playlistUri != null) {
                takePersistableReadPermission(data, playlistUri)
                listener.importStreamM3u(playlistUri)
            }
            return true
        }
        if (requestCode == REQUEST_EXPORT_PLAYLIST_M3U) {
            val exportUri = data.data
            if (exportUri != null) {
                listener.exportPlaylist(exportUri)
            }
            return true
        }
        if (requestCode == REQUEST_IMPORT_PLAYLIST_M3U_FILE) {
            val playlistUri = data.data
            if (playlistUri != null) {
                takePersistableReadPermission(data, playlistUri)
                listener.importPlaylistM3u(playlistUri)
            }
            return true
        }
        return false
    }

    private fun isDocumentRequest(requestCode: Int): Boolean =
        requestCode == REQUEST_IMPORT_AUDIO_FILES ||
            requestCode == REQUEST_IMPORT_AUDIO_FOLDER ||
            requestCode == REQUEST_IMPORT_M3U_FILE ||
            requestCode == REQUEST_EXPORT_PLAYLIST_M3U ||
            requestCode == REQUEST_IMPORT_PLAYLIST_M3U_FILE

    private fun selectedAudioUris(data: Intent?): ArrayList<Uri> {
        val uris = ArrayList<Uri>()
        if (data == null) {
            return uris
        }
        val clipData: ClipData? = data.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(index).uri
                if (uri != null) {
                    uris.add(uri)
                }
            }
        } else if (data.data != null) {
            uris.add(data.data!!)
        }
        return uris
    }

    private fun takePersistableReadPermission(data: Intent?, uri: Uri?) {
        if (data == null || uri == null) {
            return
        }
        var flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (flags == 0) {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            activity.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (ignored: SecurityException) {
            // Some providers grant only transient access; import still works for this session.
        } catch (ignored: IllegalArgumentException) {
            // Some providers grant only transient access; import still works for this session.
        }
    }

    companion object {
        @JvmField val REQUEST_IMPORT_AUDIO_FILES: Int = 4002
        @JvmField val REQUEST_IMPORT_AUDIO_FOLDER: Int = 4003
        @JvmField val REQUEST_IMPORT_M3U_FILE: Int = 4004
        @JvmField val REQUEST_EXPORT_PLAYLIST_M3U: Int = 4005
        @JvmField val REQUEST_IMPORT_PLAYLIST_M3U_FILE: Int = 4006
    }
}
