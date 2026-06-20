package app.yukine

import app.yukine.model.Track

internal class NetworkMenuDialogBindings(
    private val showAddStreamAction: Runnable,
    private val showImportM3uAction: Runnable,
    private val showAddWebDavAction: Runnable
) : NetworkMenuEventController.Dialogs {
    override fun showAddStream() {
        showAddStreamAction.run()
    }

    override fun showImportM3u() {
        showImportM3uAction.run()
    }

    override fun showAddWebDav() {
        showAddWebDavAction.run()
    }
}

internal class NetworkMenuPlayerBindings(
    private val playTrackListAction: TrackListPlaybackAction
) : NetworkMenuEventController.Player {
    override fun playTrackList(tracks: List<Track>, index: Int) {
        playTrackListAction.play(tracks, index)
    }
}
