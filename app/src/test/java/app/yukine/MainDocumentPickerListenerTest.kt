package app.yukine

import android.net.FakeUri
import app.yukine.di.PlatformModule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayList

class MainDocumentPickerListenerTest {
    @Test
    fun delegatesDocumentPickerCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val audioUris = arrayListOf<android.net.Uri>(FakeUri("content://audio/1"))
        val luoxueUris = arrayListOf<android.net.Uri>(FakeUri("content://luoxue/1"))
        val listener = MainDocumentPickerListener(
            audioUrisImporter = DocumentAudioUrisImporter { calls += "audio:${it.first()}" },
            audioFolderImporter = DocumentAudioFolderImporter { calls += "folder:$it" },
            downloadFolderChooser = DocumentDownloadFolderChooser { calls += "download:$it" },
            streamM3uImporter = DocumentStreamM3uImporter { calls += "stream:$it" },
            playlistExporter = DocumentPlaylistExporter { uri, playlistId, playlistName ->
                calls += "export:$uri:$playlistId:$playlistName"
            },
            playlistM3uImporter = DocumentPlaylistM3uImporter { calls += "playlist:$it" },
            luoxueSourceUrisImporter = DocumentLuoxueSourceUrisImporter { calls += "luoxue:${it.first()}" }
        )

        listener.importAudioUris(audioUris)
        listener.importAudioFolder(FakeUri("content://folder"))
        listener.chooseDownloadFolder(FakeUri("content://download"))
        listener.importStreamM3u(FakeUri("content://stream.m3u"))
        listener.exportPlaylist(FakeUri("content://export.m3u"), 42L, "Daily")
        listener.importPlaylistM3u(FakeUri("content://playlist.m3u"))
        listener.importLuoxueSourceUris(luoxueUris)

        assertEquals(
            listOf(
                "audio:content://audio/1",
                "folder:content://folder",
                "download:content://download",
                "stream:content://stream.m3u",
                "export:content://export.m3u:42:Daily",
                "playlist:content://playlist.m3u",
                "luoxue:content://luoxue/1"
            ),
            calls
        )
    }

    @Test
    fun factoryCreatesDocumentPickerControllerListener() {
        val factory = PlatformModule.provideMainDocumentPickerListenerFactory()
        val calls = mutableListOf<String>()
        val listener = factory.create(
            DocumentAudioUrisImporter { calls += "audio:${it.size}" },
            DocumentAudioFolderImporter { calls += "folder" },
            DocumentDownloadFolderChooser { calls += "download" },
            DocumentStreamM3uImporter { calls += "stream" },
            DocumentPlaylistExporter { _, playlistId, playlistName -> calls += "export:$playlistId:$playlistName" },
            DocumentPlaylistM3uImporter { calls += "playlist" },
            DocumentLuoxueSourceUrisImporter { calls += "luoxue:${it.size}" }
        )

        listener.importAudioUris(ArrayList(listOf(FakeUri("content://audio"))))
        listener.importAudioFolder(FakeUri("content://folder"))
        listener.chooseDownloadFolder(FakeUri("content://download"))
        listener.importStreamM3u(FakeUri("content://stream"))
        listener.exportPlaylist(FakeUri("content://export"), 7L, "Mix")
        listener.importPlaylistM3u(FakeUri("content://playlist"))
        listener.importLuoxueSourceUris(ArrayList(listOf(FakeUri("content://one"), FakeUri("content://two"))))

        assertEquals(
            listOf("audio:1", "folder", "download", "stream", "export:7:Mix", "playlist", "luoxue:2"),
            calls
        )
    }
}
