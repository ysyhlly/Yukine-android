package app.yukine

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistExportBindingsTest {
    @Test
    fun forwardsDocumentOpenAndExportResults() {
        val calls = mutableListOf<String>()
        var nextExportResult = true
        val bindings = PlaylistExportBindings(
            languageModeProvider = PlaylistExportLanguageModeProvider { AppLanguage.MODE_ENGLISH },
            openDocumentAction = PlaylistExportDocumentOpener { calls += "open:$it" },
            exportAction = PlaylistExportAction { uri, playlistId, playlistName, callback ->
                calls += "export:$uri:$playlistId:$playlistName"
                callback.onResult(nextExportResult)
            },
            statusSink = SettingsStatusSink { calls += "status:$it" }
        )
        val exportUri = Uri.parse("content://playlist/export")

        bindings.openPlaylistExportDocument("Road Mix")
        bindings.exportPlaylist(exportUri, 42L, "Road Mix")
        nextExportResult = false
        bindings.exportPlaylist(exportUri, 43L, "Quiet Mix")
        bindings.setStatus("Status")

        assertEquals(
            listOf(
                "open:Road Mix",
                "export:content://playlist/export:42:Road Mix",
                "status:${AppLanguage.text(AppLanguage.MODE_ENGLISH, "playlist.exported")}",
                "export:content://playlist/export:43:Quiet Mix",
                "status:${AppLanguage.text(AppLanguage.MODE_ENGLISH, "playlist.export.failed")}",
                "status:Status"
            ),
            calls
        )
    }
}
