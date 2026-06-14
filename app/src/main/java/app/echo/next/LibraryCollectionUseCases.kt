package app.echo.next

import app.echo.next.data.MusicLibraryRepository
import app.echo.next.model.Playlist
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.model.TrackPlayRecord

private const val PLAY_HISTORY_RECAP_LIMIT = 2000
private const val PLAY_HISTORY_RECAP_WINDOW_MS = 4L * 7L * 24L * 60L * 60L * 1000L

internal data class LibraryCollectionsSnapshot(
    @JvmField val selectedPlaylistId: Long,
    @JvmField val favoriteIds: Set<Long>,
    @JvmField val favoriteTracks: List<Track>,
    @JvmField val recentRecords: List<TrackPlayRecord>,
    @JvmField val mostPlayedRecords: List<TrackPlayRecord>,
    @JvmField val playlists: List<Playlist>,
    @JvmField val remoteSources: List<RemoteSource>,
    @JvmField val selectedPlaylistTracks: List<Track>
)

internal interface LibraryCollectionOperations {
    fun ensureDefaultPlaylist(): Long
    fun loadPlaylists(): List<Playlist>
    fun loadFavoriteIds(): Set<Long>
    fun loadFavoriteTracks(): List<Track>
    fun loadPlayedSince(sinceMs: Long, limit: Int): List<TrackPlayRecord>
    fun loadRecentlyPlayed(limit: Int): List<TrackPlayRecord>
    fun loadMostPlayed(limit: Int): List<TrackPlayRecord>
    fun loadRemoteSources(): List<RemoteSource>
    fun loadPlaylistTracks(playlistId: Long): List<Track>
    fun clearPlayHistory(): Int
    fun setFavorite(trackId: Long, favorite: Boolean)
}

internal class MusicLibraryCollectionOperations(
    private val repository: MusicLibraryRepository
) : LibraryCollectionOperations {
    override fun ensureDefaultPlaylist(): Long = repository.ensureDefaultPlaylist()

    override fun loadPlaylists(): List<Playlist> = repository.loadPlaylists()

    override fun loadFavoriteIds(): Set<Long> = repository.loadFavoriteIds()

    override fun loadFavoriteTracks(): List<Track> = repository.loadFavoriteTracks()

    override fun loadPlayedSince(sinceMs: Long, limit: Int): List<TrackPlayRecord> =
        repository.loadPlayedSince(sinceMs, limit)

    override fun loadRecentlyPlayed(limit: Int): List<TrackPlayRecord> =
        repository.loadRecentlyPlayed(limit)

    override fun loadMostPlayed(limit: Int): List<TrackPlayRecord> =
        repository.loadMostPlayed(limit)

    override fun loadRemoteSources(): List<RemoteSource> = repository.loadRemoteSources()

    override fun loadPlaylistTracks(playlistId: Long): List<Track> =
        repository.loadPlaylistTracks(playlistId)

    override fun clearPlayHistory(): Int = repository.clearPlayHistory()

    override fun setFavorite(trackId: Long, favorite: Boolean) {
        repository.setFavorite(trackId, favorite)
    }
}

internal class LoadLibraryCollectionsUseCase @JvmOverloads constructor(
    private val operations: LibraryCollectionOperations,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    fun execute(selectedPlaylistId: Long): LibraryCollectionsSnapshot {
        val requestedPlaylistId = if (selectedPlaylistId < 0L) {
            operations.ensureDefaultPlaylist()
        } else {
            selectedPlaylistId
        }
        val playlists = operations.loadPlaylists()
        val selectedExists = playlists.any { playlist -> playlist.id == requestedPlaylistId }
        val loadedPlaylistId = when {
            selectedExists -> requestedPlaylistId
            playlists.isNotEmpty() -> playlists[0].id
            else -> -1L
        }
        var recentRecords = operations.loadPlayedSince(
            clockMs() - PLAY_HISTORY_RECAP_WINDOW_MS,
            PLAY_HISTORY_RECAP_LIMIT
        )
        if (recentRecords.isEmpty()) {
            recentRecords = operations.loadRecentlyPlayed(PLAY_HISTORY_RECAP_LIMIT)
        }
        val selectedPlaylistTracks = if (loadedPlaylistId < 0L) {
            emptyList()
        } else {
            operations.loadPlaylistTracks(loadedPlaylistId)
        }
        return LibraryCollectionsSnapshot(
            selectedPlaylistId = loadedPlaylistId,
            favoriteIds = operations.loadFavoriteIds(),
            favoriteTracks = operations.loadFavoriteTracks(),
            recentRecords = recentRecords,
            mostPlayedRecords = operations.loadMostPlayed(PLAY_HISTORY_RECAP_LIMIT),
            playlists = playlists,
            remoteSources = operations.loadRemoteSources(),
            selectedPlaylistTracks = selectedPlaylistTracks
        )
    }
}

internal class ClearPlayHistoryUseCase(
    private val operations: LibraryCollectionOperations
) {
    fun execute(): Int = operations.clearPlayHistory()
}

internal class SetLibraryFavoriteUseCase(
    private val operations: LibraryCollectionOperations
) {
    fun execute(trackId: Long, favorite: Boolean) {
        if (trackId < 0L) {
            return
        }
        operations.setFavorite(trackId, favorite)
    }
}
