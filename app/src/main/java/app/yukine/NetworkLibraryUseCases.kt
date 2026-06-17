package app.yukine

import app.yukine.data.MusicLibraryRepository
import app.yukine.model.StreamImportResult
import app.yukine.model.Track

internal data class NetworkLibrarySnapshot(
    val cached: List<Track>,
    val favorites: Set<Long>
)

internal data class AddStreamUrlResult(
    val track: Track?,
    val snapshot: NetworkLibrarySnapshot
)

internal data class UpdateStreamUrlResult(
    val updated: Track?,
    val snapshot: NetworkLibrarySnapshot
)

internal data class ImportStreamPlaylistResult(
    val importResult: StreamImportResult?,
    val snapshot: NetworkLibrarySnapshot
)

internal data class SaveWebDavSourceResult(
    val savedSourceId: Long,
    val snapshot: NetworkLibrarySnapshot
)

internal interface NetworkLibraryOperations {
    fun addStreamUrl(title: String, url: String): Track?
    fun updateStreamUrl(trackId: Long, title: String, url: String): Track?
    fun importM3uPlaylistWithResult(url: String): StreamImportResult?
    fun deleteAllStreams()
    fun deleteTrack(trackId: Long)
    fun deleteTracks(trackIds: List<Long>)
    fun deleteRemoteSource(sourceId: Long)
    fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ): Long
    fun loadCachedTracks(): List<Track>
    fun loadFavoriteIds(): Set<Long>
}

internal class MusicLibraryNetworkLibraryOperations(
    private val repository: MusicLibraryRepository
) : NetworkLibraryOperations {
    override fun addStreamUrl(title: String, url: String): Track? =
        repository.addStreamUrl(title, url)

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

    override fun deleteTracks(trackIds: List<Long>) {
        for (trackId in trackIds) {
            repository.deleteTrack(trackId)
        }
    }

    override fun deleteRemoteSource(sourceId: Long) {
        repository.deleteRemoteSource(sourceId)
    }

    override fun saveWebDavSource(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ): Long =
        repository.saveWebDavSource(sourceId, name, baseUrl, username, password, rootPath)

    override fun loadCachedTracks(): List<Track> = repository.loadCachedTracks()

    override fun loadFavoriteIds(): Set<Long> = repository.loadFavoriteIds()
}

internal class AddStreamUrlUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(title: String, url: String): AddStreamUrlResult =
        AddStreamUrlResult(
            track = operations.addStreamUrl(title, url),
            snapshot = operations.snapshot()
        )
}

internal class UpdateStreamUrlUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(oldTrack: Track?, title: String, url: String): UpdateStreamUrlResult? {
        if (oldTrack == null) {
            return null
        }
        return UpdateStreamUrlResult(
            updated = operations.updateStreamUrl(oldTrack.id, title, url),
            snapshot = operations.snapshot()
        )
    }
}

internal class ImportStreamPlaylistUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(url: String): ImportStreamPlaylistResult =
        ImportStreamPlaylistResult(
            importResult = operations.importM3uPlaylistWithResult(url),
            snapshot = operations.snapshot()
        )
}

internal class DeleteAllStreamsUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(): NetworkLibrarySnapshot {
        operations.deleteAllStreams()
        return operations.snapshot()
    }
}

internal class DeleteNetworkTrackUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(trackId: Long): NetworkLibrarySnapshot {
        if (trackId >= 0L) {
            operations.deleteTrack(trackId)
        }
        return operations.snapshot()
    }
}

internal class DeleteNetworkTracksUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(trackIds: List<Long>): NetworkLibrarySnapshot {
        operations.deleteTracks(trackIds.filter { it >= 0L }.distinct())
        return operations.snapshot()
    }
}

internal class DeleteRemoteSourceUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(sourceId: Long): NetworkLibrarySnapshot {
        if (sourceId >= 0L) {
            operations.deleteRemoteSource(sourceId)
        }
        return operations.snapshot()
    }
}

internal class SaveWebDavSourceUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String
    ): SaveWebDavSourceResult =
        SaveWebDavSourceResult(
            savedSourceId = operations.saveWebDavSource(sourceId, name, baseUrl, username, password, rootPath),
            snapshot = operations.snapshot()
        )
}

private fun NetworkLibraryOperations.snapshot(): NetworkLibrarySnapshot =
    NetworkLibrarySnapshot(
        cached = loadCachedTracks(),
        favorites = loadFavoriteIds()
    )
