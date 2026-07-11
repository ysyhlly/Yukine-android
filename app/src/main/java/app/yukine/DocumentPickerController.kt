package app.yukine

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.ArrayList

internal fun interface DocumentActivityResultLauncher {
    fun launch(intent: Intent, onResult: (ActivityResult) -> Unit)
}

internal class DocumentPickerController @JvmOverloads constructor(
    private val activity: ComponentActivity,
    private val listener: Listener,
    activityResultLauncher: DocumentActivityResultLauncher? = null
) : LuoxueSourceFilePicker {
    private val activityResultLauncher: DocumentActivityResultLauncher =
        activityResultLauncher ?: ActivityResultDocumentLauncher(activity)

    interface Listener {
        fun importAudioUris(uris: ArrayList<Uri>)

        fun importAudioFolder(treeUri: Uri)

        fun chooseDownloadFolder(treeUri: Uri)

        fun importStreamM3u(playlistUri: Uri)

        fun exportPlaylist(exportUri: Uri, playlistId: Long, playlistName: String)

        fun importPlaylistM3u(playlistUri: Uri)

        fun importLuoxueSourceUris(uris: ArrayList<Uri>)
    }

    fun openAudioFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "audio/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        launch(intent, DocumentAction.ImportAudioFiles)
    }

    fun openAudioFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        launch(intent, DocumentAction.ImportAudioFolder)
    }

    fun openDownloadFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        launch(intent, DocumentAction.DownloadFolder)
    }

    fun openM3uFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        M3uDocumentHelper.configureReadIntent(intent)
        launch(intent, DocumentAction.ImportStreamM3u)
    }

    fun openPlaylistM3uFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        M3uDocumentHelper.configureReadIntent(intent)
        launch(intent, DocumentAction.ImportPlaylistM3u)
    }

    override fun openLuoxueSourceFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/javascript", "text/javascript", "text/plain", "application/octet-stream", "*/*"))
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        launch(intent, DocumentAction.ImportLuoxueSource)
    }

    private var pendingPlaylistExportId: Long = -1L
    private var pendingPlaylistExportName: String = ""

    fun openPlaylistExportDocument(playlistId: Long, playlistName: String) {
        pendingPlaylistExportId = playlistId
        pendingPlaylistExportName = playlistName
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/vnd.apple.mpegurl"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, M3uDocumentHelper.MIME_TYPES)
        intent.putExtra(Intent.EXTRA_TITLE, M3uDocumentHelper.exportFileName(playlistName))
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        launch(intent, DocumentAction.ExportPlaylistM3u)
    }

    private fun launch(intent: Intent, action: DocumentAction) {
        activityResultLauncher.launch(intent) { result ->
            handleResult(action, result)
        }
    }

    private fun handleResult(action: DocumentAction, result: ActivityResult) {
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            return
        }
        when (action) {
            DocumentAction.ImportAudioFiles -> {
                val uris = selectedAudioUris(data)
                for (uri in uris) {
                    takePersistableReadPermission(data, uri)
                }
                listener.importAudioUris(uris)
            }
            DocumentAction.ImportAudioFolder -> {
                val treeUri = data.data
                if (treeUri != null) {
                    takePersistableReadPermission(data, treeUri)
                    listener.importAudioFolder(treeUri)
                }
            }
            DocumentAction.DownloadFolder -> {
                val treeUri = data.data
                if (treeUri != null) {
                    takePersistableReadWritePermission(data, treeUri)
                    listener.chooseDownloadFolder(treeUri)
                }
            }
            DocumentAction.ImportStreamM3u -> {
                val playlistUri = data.data
                if (playlistUri != null) {
                    takePersistableReadPermission(data, playlistUri)
                    listener.importStreamM3u(playlistUri)
                }
            }
            DocumentAction.ExportPlaylistM3u -> {
                val exportUri = data.data
                if (exportUri != null) {
                    val playlistId = pendingPlaylistExportId
                    val playlistName = pendingPlaylistExportName
                    pendingPlaylistExportId = -1L
                    pendingPlaylistExportName = ""
                    if (playlistId >= 0L) {
                        listener.exportPlaylist(exportUri, playlistId, playlistName)
                    }
                }
            }
            DocumentAction.ImportPlaylistM3u -> {
                val playlistUri = data.data
                if (playlistUri != null) {
                    takePersistableReadPermission(data, playlistUri)
                    listener.importPlaylistM3u(playlistUri)
                }
            }
            DocumentAction.ImportLuoxueSource -> {
                val uris = selectedDocumentUris(data)
                for (uri in uris) {
                    takePersistableReadPermission(data, uri)
                }
                listener.importLuoxueSourceUris(uris)
            }
        }
    }

    private fun selectedAudioUris(data: Intent?): ArrayList<Uri> {
        return selectedDocumentUris(data)
    }

    private fun selectedDocumentUris(data: Intent?): ArrayList<Uri> {
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

    private fun takePersistableReadWritePermission(data: Intent?, uri: Uri?) {
        if (data == null || uri == null) {
            return
        }
        var flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (flags == 0) {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        try {
            activity.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (ignored: SecurityException) {
            // Some providers grant only transient access.
        } catch (ignored: IllegalArgumentException) {
            // Some providers grant only transient access.
        }
    }

    private enum class DocumentAction {
        ImportAudioFiles,
        ImportAudioFolder,
        DownloadFolder,
        ImportStreamM3u,
        ExportPlaylistM3u,
        ImportPlaylistM3u,
        ImportLuoxueSource
    }

    private class ActivityResultDocumentLauncher(
        activity: ComponentActivity
    ) : DocumentActivityResultLauncher {
        private var callback: ((ActivityResult) -> Unit)? = null
        private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            callback?.invoke(result)
        }

        override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
            callback = onResult
            launcher.launch(intent)
        }
    }
}
