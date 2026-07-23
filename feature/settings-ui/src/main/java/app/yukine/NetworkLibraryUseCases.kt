package app.yukine

import app.yukine.model.StreamImportResult
import app.yukine.model.Track
import app.yukine.model.TrackIdentity

data class NetworkLibrarySnapshot(
    val cached: List<Track>,
    val favorites: Set<Long>
)

data class AddStreamUrlResult(
    val track: Track?,
    val snapshot: NetworkLibrarySnapshot
)

data class UpdateStreamUrlResult(
    val updated: Track?,
    val snapshot: NetworkLibrarySnapshot
)

data class ImportStreamPlaylistResult(
    val importResult: StreamImportResult?,
    val snapshot: NetworkLibrarySnapshot
)

data class SaveWebDavSourceResult(
    val savedSourceId: Long,
    val snapshot: NetworkLibrarySnapshot
)

interface NetworkLibraryOperations {
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
        rootPath: String,
        allowInsecureTls: Boolean
    ): Long
    fun loadCachedTracks(): List<Track>
    fun loadFavoriteIds(): Set<Long>
}

class AddStreamUrlUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(title: String, url: String): AddStreamUrlResult =
        AddStreamUrlResult(
            track = operations.addStreamUrl(title, url),
            snapshot = operations.snapshot()
        )
}

class UpdateStreamUrlUseCase(
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

class ImportStreamPlaylistUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(url: String): ImportStreamPlaylistResult =
        ImportStreamPlaylistResult(
            importResult = operations.importM3uPlaylistWithResult(url),
            snapshot = operations.snapshot()
        )
}

class DeleteAllStreamsUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(): NetworkLibrarySnapshot {
        operations.deleteAllStreams()
        return operations.snapshot()
    }
}

class DeleteNetworkTrackUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(trackId: Long): NetworkLibrarySnapshot {
        if (TrackIdentity.isUsable(trackId)) {
            operations.deleteTrack(trackId)
        }
        return operations.snapshot()
    }
}

class DeleteNetworkTracksUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(trackIds: List<Long>): NetworkLibrarySnapshot {
        operations.deleteTracks(trackIds.filter { it >= 0L }.distinct())
        return operations.snapshot()
    }
}

class DeleteRemoteSourceUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(sourceId: Long): NetworkLibrarySnapshot {
        if (sourceId >= 0L) {
            operations.deleteRemoteSource(sourceId)
        }
        return operations.snapshot()
    }
}

class SaveWebDavSourceUseCase(
    private val operations: NetworkLibraryOperations
) {
    fun execute(
        sourceId: Long,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        rootPath: String,
        allowInsecureTls: Boolean
    ): SaveWebDavSourceResult =
        SaveWebDavSourceResult(
            savedSourceId = operations.saveWebDavSource(
                sourceId,
                name,
                baseUrl,
                username,
                password,
                rootPath,
                allowInsecureTls
            ),
            snapshot = operations.snapshot()
        )
}

private fun NetworkLibraryOperations.snapshot(): NetworkLibrarySnapshot =
    NetworkLibrarySnapshot(
        cached = loadCachedTracks(),
        favorites = loadFavoriteIds()
    )
