package app.yukine

import app.yukine.model.Track

internal class NetworkDialogEventController(
    private val requestController: NetworkRequestController
) : NetworkDialogController.Listener {
    override fun addStream(title: String, url: String) {
        requestController.addStreamUrl(title, url)
    }

    override fun importM3u(url: String) {
        requestController.importM3uPlaylist(url)
    }

    override fun updateStream(track: Track, title: String, url: String) {
        requestController.updateStreamUrl(track, title, url)
    }

    override fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ) {
        requestController.saveWebDavSource(sourceId, name, baseUrl, username, password, rootPath)
    }
}
