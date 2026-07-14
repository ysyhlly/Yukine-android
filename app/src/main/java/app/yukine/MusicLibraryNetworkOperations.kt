package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.StreamImportResult
import app.yukine.model.Track
import app.yukine.model.WebDavSyncResult

internal class MusicLibraryNetworkLibraryOperations(
    private val repository: MusicLibraryRepository
) : NetworkLibraryOperations {
    override fun addStreamUrl(title: String, url: String): Track? = repository.addStreamUrl(title, url)
    override fun updateStreamUrl(trackId: Long, title: String, url: String): Track? =
        repository.updateStreamUrl(trackId, title, url)
    override fun importM3uPlaylistWithResult(url: String): StreamImportResult? =
        repository.importM3uPlaylistWithResult(url)
    override fun deleteAllStreams() {
        repository.deleteAllStreams()
    }
    override fun deleteTrack(trackId: Long) {
        repository.deleteTrack(trackId)
    }
    override fun deleteTracks(trackIds: List<Long>) = trackIds.forEach(repository::deleteTrack)
    override fun deleteRemoteSource(sourceId: Long) = repository.deleteRemoteSource(sourceId)
    override fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ): Long = repository.saveWebDavSource(sourceId, name, baseUrl, username, password, rootPath)
    override fun loadCachedTracks(): List<Track> = repository.loadCachedTracks()
    override fun loadFavoriteIds(): Set<Long> = repository.loadFavoriteIds()
}

internal class MusicLibraryWebDavSourceOperations(
    private val repository: MusicLibraryRepository
) : WebDavSourceOperations {
    override fun testRemoteSource(sourceId: Long): String = repository.testRemoteSource(sourceId)
    override fun syncRemoteSource(sourceId: Long): WebDavSyncResult? = repository.syncRemoteSource(sourceId)
    override fun loadCachedTracks(): List<Track> = repository.loadCachedTracks()
    override fun loadFavoriteIds(): Set<Long> = repository.loadFavoriteIds()
}
