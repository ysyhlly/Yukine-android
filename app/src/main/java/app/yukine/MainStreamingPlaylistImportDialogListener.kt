package app.yukine

import app.yukine.streaming.StreamingProviderName

internal fun interface StreamingImportSelectedProviderSource {
    fun selectedProvider(): StreamingProviderName?
}

internal fun interface LuoxueSourceImportDialogPresenter {
    fun showLuoxueSourceImportDialog()
}

internal fun interface StreamingPlaylistLinkImportSink {
    fun importStreamingPlaylistFromLink(linkOrId: String?)
}

internal class MainStreamingPlaylistImportDialogListener(
    private val selectedProviderSource: StreamingImportSelectedProviderSource,
    private val luoxueDialogPresenter: LuoxueSourceImportDialogPresenter,
    private val playlistLinkImportSink: StreamingPlaylistLinkImportSink
) : StreamingPlaylistImportDialogController.Listener {
    override fun selectedProvider(): StreamingProviderName? =
        selectedProviderSource.selectedProvider()

    override fun showLuoxueSourceImportDialog() {
        luoxueDialogPresenter.showLuoxueSourceImportDialog()
    }

    override fun importStreamingPlaylistFromLink(linkOrId: String?) {
        playlistLinkImportSink.importStreamingPlaylistFromLink(linkOrId)
    }
}
