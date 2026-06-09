package app.echo.next

import app.echo.next.model.Track

internal class NetworkRequestController(
    private val operations: NetworkOperationSink,
    private val labels: Labels,
    private val listener: Listener
) {
    interface Labels {
        fun text(key: String): String
    }

    interface Listener {
        fun setStatus(status: String)
    }

    fun addStreamUrl(title: String, url: String) {
        listener.setStatus(labels.text("adding.stream"))
        operations.addStreamUrl(title, url)
    }

    fun updateStreamUrl(oldTrack: Track?, title: String, url: String) {
        listener.setStatus(labels.text("updating.stream"))
        operations.updateStreamUrl(oldTrack, title, url)
    }

    fun importM3uPlaylist(url: String) {
        listener.setStatus(labels.text("importing.m3u.playlist"))
        operations.importM3uPlaylist(url)
    }

    fun deleteAllStreams() {
        listener.setStatus(labels.text("deleting.streams"))
        operations.deleteAllStreams()
    }

    fun deleteTrack(trackId: Long, status: String) {
        listener.setStatus(labels.text("deleting.stream"))
        operations.deleteTrack(trackId, status)
    }

    fun deleteRemoteSource(sourceId: Long) {
        listener.setStatus(labels.text("deleting.source"))
        operations.deleteRemoteSource(sourceId)
    }

    fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ) {
        listener.setStatus(labels.text("saving.webdav.source"))
        operations.saveWebDavSource(sourceId, name, baseUrl, username, password, rootPath)
    }

    fun testRemoteSource(sourceId: Long) {
        listener.setStatus(labels.text("test") + "...")
        operations.testRemoteSource(sourceId)
    }

    fun syncRemoteSource(sourceId: Long, sourceName: String) {
        listener.setStatus(labels.text("syncing") + sourceName)
        operations.syncRemoteSource(sourceId, sourceName)
    }

    fun syncAllWebDavSources(sourceIds: List<Long>) {
        listener.setStatus(labels.text("syncing.webdav.sources"))
        operations.syncAllWebDavSources(sourceIds)
    }
}
