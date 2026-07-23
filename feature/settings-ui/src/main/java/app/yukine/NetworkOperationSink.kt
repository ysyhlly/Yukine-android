package app.yukine

import app.yukine.model.Track

interface NetworkOperationSink {
    fun addStreamUrl(title: String, url: String)

    fun updateStreamUrl(oldTrack: Track?, title: String, url: String)

    fun importM3uPlaylist(url: String)

    fun deleteAllStreams()

    fun deleteTrack(trackId: Long, status: String)

    fun deleteTracks(trackIds: List<Long>, status: String)

    fun deleteRemoteSource(sourceId: Long)

    fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String,
        allowInsecureTls: Boolean
    )

    fun testRemoteSource(sourceId: Long)

    fun syncRemoteSource(sourceId: Long, sourceName: String)

    fun syncAllWebDavSources(sourceIds: List<Long>)
}
