package app.yukine

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentPickerControllerTest {
    @Test
    fun openAudioFilePickerLaunchesOpenDocumentAndImportsSelectedUris() {
        val launcher = RecordingDocumentActivityResultLauncher()
        val listener = RecordingListener()
        val controller = DocumentPickerController(
            activity = activity(),
            actions = listener.actions(),
            activityResultLauncher = launcher
        )
        val first = Uri.parse("content://audio/first")
        val second = Uri.parse("content://audio/second")

        controller.openAudioFilePicker()

        val intent = launcher.launches.single()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("audio/*", intent.type)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
        assertEquals(true, intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))

        launcher.emit(
            ActivityResult(
                Activity.RESULT_OK,
                Intent()
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .also { data ->
                        data.clipData = ClipData(
                            "audio",
                            arrayOf("audio/mpeg"),
                            ClipData.Item(first)
                        ).apply {
                            addItem(ClipData.Item(second))
                        }
                    }
            )
        )

        assertEquals(listOf(listOf(first, second)), listener.audioImports)
    }

    @Test
    fun openDownloadFolderPickerLaunchesTreeAndEmitsChosenFolder() {
        val launcher = RecordingDocumentActivityResultLauncher()
        val listener = RecordingListener()
        val controller = DocumentPickerController(
            activity = activity(),
            actions = listener.actions(),
            activityResultLauncher = launcher
        )
        val treeUri = Uri.parse("content://tree/downloads")

        controller.openDownloadFolderPicker()

        val intent = launcher.launches.single()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertTrue((intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)

        launcher.emit(
            ActivityResult(
                Activity.RESULT_OK,
                Intent()
                    .setData(treeUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            )
        )

        assertEquals(listOf(treeUri), listener.downloadFolders)
    }

    @Test
    fun openPlaylistExportDocumentLaunchesCreateDocumentAndExportsResultUri() {
        val launcher = RecordingDocumentActivityResultLauncher()
        val listener = RecordingListener()
        val controller = DocumentPickerController(
            activity = activity(),
            actions = listener.actions(),
            activityResultLauncher = launcher
        )
        val exportUri = Uri.parse("content://playlist/export")

        controller.openPlaylistExportDocument(8L, "Road Mix")

        val intent = launcher.launches.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/vnd.apple.mpegurl", intent.type)
        assertEquals("Road Mix.m3u8", intent.getStringExtra(Intent.EXTRA_TITLE))

        launcher.emit(ActivityResult(Activity.RESULT_OK, Intent().setData(exportUri)))

        assertEquals(listOf(PlaylistExport(exportUri, 8L, "Road Mix")), listener.playlistExports)
    }

    @Test
    fun canceledResultDoesNotNotifyListener() {
        val launcher = RecordingDocumentActivityResultLauncher()
        val listener = RecordingListener()
        val controller = DocumentPickerController(
            activity = activity(),
            actions = listener.actions(),
            activityResultLauncher = launcher
        )

        controller.openM3uFilePicker()
        launcher.emit(ActivityResult(Activity.RESULT_CANCELED, null))

        assertTrue(listener.allEvents().isEmpty())
    }

    private fun activity(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private class RecordingDocumentActivityResultLauncher : DocumentActivityResultLauncher {
        val launches = mutableListOf<Intent>()
        private var callback: ((ActivityResult) -> Unit)? = null

        override fun launch(intent: Intent, onResult: (ActivityResult) -> Unit) {
            launches += intent
            callback = onResult
        }

        fun emit(result: ActivityResult) {
            callback?.invoke(result)
        }
    }

    private class RecordingListener {
        val audioImports = mutableListOf<List<Uri>>()
        val audioFolders = mutableListOf<Uri>()
        val downloadFolders = mutableListOf<Uri>()
        val streamM3uImports = mutableListOf<Uri>()
        val playlistExports = mutableListOf<PlaylistExport>()
        val playlistM3uImports = mutableListOf<Uri>()
        val luoxueSourceImports = mutableListOf<List<Uri>>()

        fun importAudioUris(uris: ArrayList<Uri>) {
            audioImports += uris.toList()
        }

        fun importAudioFolder(treeUri: Uri) {
            audioFolders += treeUri
        }

        fun chooseDownloadFolder(treeUri: Uri) {
            downloadFolders += treeUri
        }

        fun importStreamM3u(playlistUri: Uri) {
            streamM3uImports += playlistUri
        }

        fun exportPlaylist(exportUri: Uri, playlistId: Long, playlistName: String) {
            playlistExports += PlaylistExport(exportUri, playlistId, playlistName)
        }

        fun importPlaylistM3u(playlistUri: Uri) {
            playlistM3uImports += playlistUri
        }

        fun importLuoxueSourceUris(uris: ArrayList<Uri>) {
            luoxueSourceImports += uris.toList()
        }

        fun allEvents(): List<Any> =
            audioImports +
                audioFolders +
                downloadFolders +
                streamM3uImports +
                playlistExports +
                playlistM3uImports +
                luoxueSourceImports

        fun actions(): DocumentPickerActions = DocumentPickerActions(
            audioUrisImporter = ::importAudioUris,
            audioFolderImporter = ::importAudioFolder,
            downloadFolderChooser = ::chooseDownloadFolder,
            streamM3uImporter = ::importStreamM3u,
            playlistExporter = ::exportPlaylist,
            playlistM3uImporter = ::importPlaylistM3u,
            luoxueSourceUrisImporter = ::importLuoxueSourceUris
        )
    }

    private data class PlaylistExport(
        val uri: Uri,
        val playlistId: Long,
        val playlistName: String
    )
}
