package app.yukine

import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStreamingPlaylistImportDialogListenerTest {
    @Test
    fun delegatesImportDialogCallbacksToInjectedOwners() {
        val calls = mutableListOf<String>()
        val listener = MainStreamingPlaylistImportDialogListener(
            selectedProviderSource = StreamingImportSelectedProviderSource {
                calls += "selected"
                StreamingProviderName.LUOXUE
            },
            luoxueDialogPresenter = LuoxueSourceImportDialogPresenter {
                calls += "luoxue"
            },
            playlistLinkImportSink = StreamingPlaylistLinkImportSink {
                calls += "link:$it"
            }
        )

        assertEquals(StreamingProviderName.LUOXUE, listener.selectedProvider())
        listener.showLuoxueSourceImportDialog()
        listener.importStreamingPlaylistFromLink("https://example.test/playlist")

        assertEquals(listOf("selected", "luoxue", "link:https://example.test/playlist"), calls)
    }

    @Test
    fun directConstructionCreatesStreamingPlaylistImportDialogControllerListener() {
        val calls = mutableListOf<String>()
        val listener = MainStreamingPlaylistImportDialogListener(
            StreamingImportSelectedProviderSource { StreamingProviderName.NETEASE },
            LuoxueSourceImportDialogPresenter { calls += "luoxue" },
            StreamingPlaylistLinkImportSink { calls += "link:$it" }
        )

        assertEquals(StreamingProviderName.NETEASE, listener.selectedProvider())
        listener.showLuoxueSourceImportDialog()
        listener.importStreamingPlaylistFromLink("100")

        assertEquals(listOf("luoxue", "link:100"), calls)
    }
}
