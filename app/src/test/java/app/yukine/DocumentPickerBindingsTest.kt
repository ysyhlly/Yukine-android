package app.yukine

import android.net.Uri
import java.util.ArrayList
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentPickerBindingsTest {
    @Test
    fun forwardsDocumentPickerResultsToActions() {
        val calls = mutableListOf<String>()
        val audioUris = arrayListOf(Uri.parse("content://audio/1"))
        val folderUri = Uri.parse("content://tree/music")
        val streamM3uUri = Uri.parse("content://playlist/stream")
        val exportUri = Uri.parse("content://playlist/export")
        val playlistM3uUri = Uri.parse("content://playlist/local")
        var capturedAudioUris: ArrayList<Uri>? = null
        var capturedFolderUri: Uri? = null
        var capturedDownloadFolderUri: Uri? = null
        var capturedStreamM3uUri: Uri? = null
        var capturedExportUri: Uri? = null
        var capturedPlaylistM3uUri: Uri? = null
        val bindings = DocumentPickerBindings(
            importAudioUrisAction = DocumentAudioUrisImportAction { uris ->
                capturedAudioUris = uris
                calls += "audio"
            },
            importAudioFolderAction = DocumentUriAction {
                capturedFolderUri = it
                calls += "folder"
            },
            chooseDownloadFolderAction = DocumentUriAction {
                capturedDownloadFolderUri = it
                calls += "downloadFolder"
            },
            importStreamM3uAction = DocumentUriAction {
                capturedStreamM3uUri = it
                calls += "stream"
            },
            exportPlaylistAction = DocumentUriAction {
                capturedExportUri = it
                calls += "export"
            },
            importPlaylistM3uAction = DocumentUriAction {
                capturedPlaylistM3uUri = it
                calls += "playlist"
            }
        )

        bindings.importAudioUris(ArrayList(audioUris))
        bindings.importAudioFolder(folderUri)
        bindings.chooseDownloadFolder(folderUri)
        bindings.importStreamM3u(streamM3uUri)
        bindings.exportPlaylist(exportUri)
        bindings.importPlaylistM3u(playlistM3uUri)

        assertEquals(
            listOf(
                "audio",
                "folder",
                "downloadFolder",
                "stream",
                "export",
                "playlist"
            ),
            calls
        )
        assertEquals(audioUris, capturedAudioUris)
        assertSame(folderUri, capturedFolderUri)
        assertSame(folderUri, capturedDownloadFolderUri)
        assertSame(streamM3uUri, capturedStreamM3uUri)
        assertSame(exportUri, capturedExportUri)
        assertSame(playlistM3uUri, capturedPlaylistM3uUri)
    }
}
