package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.HashSet
import java.util.Locale

data class LibraryStoreState(
    val sourceCandidatesByTrackId: Map<Long, List<Track>> = emptyMap(),
    val allTracks: List<Track> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val favoriteTrackIds: Set<Long> = emptySet(),
    val favoriteTracks: List<Track> = emptyList(),
    val recentRecords: List<TrackPlayRecord> = emptyList(),
    val mostPlayedRecords: List<TrackPlayRecord> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val remoteSources: List<RemoteSource> = emptyList()
)

/** A fully prepared, owned library payload that is safe to publish without another full-list copy. */
internal data class PreparedLibraryReplacement(
    val sourceCandidatesByTrackId: Map<Long, List<Track>>,
    val allTracks: List<Track>,
    val visibleTracks: List<Track>,
    val favoriteTrackIds: Set<Long>
)

/** The only mutable owner of the library data snapshot used by library, player and navigation. */
internal class LibraryDataStateOwner @JvmOverloads constructor(
    private val scope: CoroutineScope,
    private val preparationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val mutableState = MutableStateFlow(LibraryStoreState())
    val state: StateFlow<LibraryStoreState> = mutableState.asStateFlow()
    private var replacementJob: Job? = null
    private var searchJob: Job? = null
    private var libraryRevision = 0L

    /**
     * Search is normally entered from the main thread, while a fresh MediaStore result can still
     * be grouping on Default. Keeping the latest normalized query lets that result publish the
     * correct visible list instead of briefly restoring an older query.
     */
    @Volatile
    private var latestSearchQuery = ""

    fun allTracks(): ArrayList<Track> {
        return ArrayList(state().allTracks)
    }

    fun sourceCandidatesFor(track: Track?): ArrayList<Track> {
        val trackId = track?.id ?: return ArrayList()
        return ArrayList(state().sourceCandidatesByTrackId[trackId].orEmpty())
    }

    fun visibleTracks(): ArrayList<Track> {
        return ArrayList(state().visibleTracks)
    }

    fun favoriteTracks(): ArrayList<Track> {
        return ArrayList(state().favoriteTracks)
    }

    fun recentRecords(): ArrayList<TrackPlayRecord> {
        return ArrayList(state().recentRecords)
    }

    fun mostPlayedRecords(): ArrayList<TrackPlayRecord> {
        return ArrayList(state().mostPlayedRecords)
    }

    fun playlists(): ArrayList<Playlist> {
        return ArrayList(state().playlists)
    }

    fun selectedPlaylistTracks(): ArrayList<Track> {
        return ArrayList(state().selectedPlaylistTracks)
    }

    fun remoteSources(): ArrayList<RemoteSource> {
        return ArrayList(state().remoteSources)
    }

    fun favoriteIds(): Set<Long> {
        return HashSet(state().favoriteTrackIds)
    }

    fun replaceLibrary(tracks: List<Track>, favorites: Set<Long>, searchQuery: String?) {
        latestSearchQuery = normalizedSearchQuery(searchQuery)
        applyPreparedLibrary(prepareLibrary(tracks, favorites, latestSearchQuery))
    }

    /**
     * Runs the costly full-library grouping and metadata normalization off the UI thread. Only
     * publishing the prepared snapshot and rendering its current tab happen on the main thread.
     * A newer replacement cancels the stale publication so an older scan cannot overwrite it.
     */
    fun replaceLibraryAsync(
        tracks: List<Track>,
        favorites: Set<Long>,
        searchQuery: String?,
        onApplied: Runnable
    ) {
        latestSearchQuery = normalizedSearchQuery(searchQuery)
        replacementJob?.cancel()
        searchJob?.cancel()
        replacementJob = scope.launch {
            val base = withContext(preparationDispatcher) {
                prepareLibraryBase(tracks, favorites)
            }
            var prepared: PreparedLibraryReplacement
            while (true) {
                val query = latestSearchQuery
                prepared = withContext(preparationDispatcher) {
                    base.toReplacement(query)
                }
                if (query == latestSearchQuery) {
                    break
                }
            }
            applyPreparedLibrary(prepared)
            onApplied.run()
        }
    }

    private fun prepareLibrary(
        tracks: List<Track>,
        favorites: Set<Long>,
        searchQuery: String
    ): PreparedLibraryReplacement = prepareLibraryBase(tracks, favorites).toReplacement(searchQuery)

    private fun prepareLibraryBase(
        tracks: List<Track>,
        favorites: Set<Long>
    ): PreparedLibraryBase {
        val sourceTracks = tracks.toList()
        return PreparedLibraryBase(
            sourceTracks = sourceTracks,
            librarySnapshot = LibraryTrackMergePolicy.snapshot(sourceTracks),
            favoriteTrackIds = HashSet(favorites)
        )
    }

    private fun applyPreparedLibrary(prepared: PreparedLibraryReplacement) {
        val current = mutableState.value
        mutableState.value = current.copy(
            sourceCandidatesByTrackId = prepared.sourceCandidatesByTrackId,
            allTracks = prepared.allTracks,
            visibleTracks = prepared.visibleTracks,
            favoriteTrackIds = prepared.favoriteTrackIds
        )
        libraryRevision++
    }

    fun applyCollections(snapshot: LibraryCollectionsResult) {
        mutableState.value = mutableState.value.copy(
            favoriteTrackIds = HashSet(snapshot.favoriteIds),
            favoriteTracks = ArrayList(snapshot.favoriteTracks),
            recentRecords = ArrayList(snapshot.recentRecords),
            mostPlayedRecords = ArrayList(snapshot.mostPlayedRecords),
            playlists = ArrayList(snapshot.playlists),
            selectedPlaylistTracks = ArrayList(snapshot.selectedPlaylistTracks),
            remoteSources = ArrayList(snapshot.remoteSources)
        )
    }

    fun applySearch(query: String?) {
        latestSearchQuery = normalizedSearchQuery(query)
        updateVisibleTracks(searchVisibleTracks(state(), latestSearchQuery))
    }

    /** Keeps per-keystroke filtering and duplicate merging off the UI thread. */
    fun applySearchAsync(query: String?, onApplied: Runnable) {
        latestSearchQuery = normalizedSearchQuery(query)
        searchJob?.cancel()
        searchJob = scope.launch {
            while (true) {
                val queryForSearch = latestSearchQuery
                val source = state()
                val revision = libraryRevision
                val visibleTracks = withContext(preparationDispatcher) {
                    searchVisibleTracks(source, queryForSearch)
                }
                if (queryForSearch == latestSearchQuery && revision == libraryRevision) {
                    updateVisibleTracks(visibleTracks)
                    onApplied.run()
                    return@launch
                }
            }
        }
    }

    fun selectedPlaylistName(selectedPlaylistId: Long): String {
        for (playlist in playlists()) {
            if (playlist.id == selectedPlaylistId) {
                return playlist.name
            }
        }
        return "Playlist"
    }

    fun streamTrackCount(): Int {
        return NetworkLibrary.streamTrackCount(allTracks())
    }

    fun streamTracks(): ArrayList<Track> {
        return NetworkLibrary.streamTracks(allTracks())
    }

    fun webDavSourceCount(): Int {
        return NetworkLibrary.webDavSourceCount(remoteSources())
    }

    fun webDavTracks(): ArrayList<Track> {
        return NetworkLibrary.webDavTracks(allTracks())
    }

    fun webDavTracksForSource(sourceId: Long): ArrayList<Track> {
        return NetworkLibrary.webDavTracksForSource(allTracks(), sourceId)
    }

    fun filteredTracks(tracks: List<Track>, searchQuery: String?): ArrayList<Track> {
        return ArrayList(search(tracks, normalizedSearchQuery(searchQuery)))
    }

    fun filteredSelectedPlaylistTracks(searchQuery: String?): ArrayList<Track> {
        return filteredTracks(selectedPlaylistTracks(), searchQuery)
    }

    fun streamTrackDetails(tracks: List<Track>): ArrayList<String> {
        return NetworkLibrary.streamTrackDetails(tracks)
    }

    fun webDavTrackDetails(tracks: List<Track>): ArrayList<String> {
        return NetworkLibrary.webDavTrackDetails(tracks, remoteSources())
    }

    fun webDavTrackDetails(tracks: List<Track>, languageMode: String): ArrayList<String> {
        return NetworkLibrary.webDavTrackDetails(tracks, remoteSources(), languageMode)
    }

    fun selectedRemoteSource(selectedRemoteSourceId: Long): RemoteSource? {
        return NetworkLibrary.selectedRemoteSource(remoteSources(), selectedRemoteSourceId)
    }

    fun remoteSourceName(sourceId: Long): String {
        return NetworkLibrary.remoteSourceName(remoteSources(), sourceId)
    }

    fun remoteSourceName(sourceId: Long, languageMode: String): String {
        return NetworkLibrary.remoteSourceName(remoteSources(), sourceId, languageMode)
    }

    fun clearPlayHistory() {
        mutableState.value = mutableState.value.copy(
            recentRecords = emptyList(),
            mostPlayedRecords = emptyList()
        )
    }

    fun toggleFavorite(trackId: Long): Boolean {
        val favorite = trackId !in mutableState.value.favoriteTrackIds
        setFavorite(trackId, favorite)
        return favorite
    }

    fun setFavorite(trackId: Long, favorite: Boolean) {
        val current = mutableState.value
        val favoriteIds = current.favoriteTrackIds.toMutableSet().apply {
            if (favorite) add(trackId) else remove(trackId)
        }
        mutableState.value = current.copy(
            favoriteTrackIds = favoriteIds,
            favoriteTracks = updateFavoriteTracks(current, trackId, favorite)
        )
    }

    private fun state(): LibraryStoreState {
        return mutableState.value
    }

    private fun updateVisibleTracks(visibleTracks: List<Track>) {
        val current = mutableState.value
        if (current.visibleTracks !== visibleTracks) {
            mutableState.value = current.copy(visibleTracks = visibleTracks)
        }
    }

    private fun normalizedSearchQuery(query: String?): String = query.orEmpty().trim()

    private fun searchVisibleTracks(current: LibraryStoreState, query: String): List<Track> {
        if (query.isEmpty() && current.selectedPlaylistTracks.isEmpty()) {
            return current.allTracks
        }
        val searchedTracks = search(current.allTracks, query) + search(current.selectedPlaylistTracks, query)
        return LibraryTrackMergePolicy.merge(searchedTracks)
    }

    private fun search(source: List<Track>, query: String): List<Track> {
        if (query.isBlank()) return source
        val normalized = query.trim().lowercase(Locale.ROOT)
        return source.filter { track ->
            track.title.lowercase(Locale.ROOT).contains(normalized) ||
                track.artist.lowercase(Locale.ROOT).contains(normalized) ||
                track.album.lowercase(Locale.ROOT).contains(normalized)
        }
    }

    private fun updateFavoriteTracks(
        current: LibraryStoreState,
        trackId: Long,
        favorite: Boolean
    ): List<Track> {
        if (!favorite) return current.favoriteTracks.filterNot { it.id == trackId }
        if (current.favoriteTracks.any { it.id == trackId }) return current.favoriteTracks
        val track = current.allTracks.firstOrNull { it.id == trackId }
            ?: current.visibleTracks.firstOrNull { it.id == trackId }
            ?: current.selectedPlaylistTracks.firstOrNull { it.id == trackId }
            ?: return current.favoriteTracks
        return current.favoriteTracks + track
    }

    private data class PreparedLibraryBase(
        val sourceTracks: List<Track>,
        val librarySnapshot: LibraryTrackMergePolicy.Snapshot,
        val favoriteTrackIds: Set<Long>
    )

    private fun PreparedLibraryBase.toReplacement(searchQuery: String): PreparedLibraryReplacement {
        val visibleTracks = if (searchQuery.isEmpty()) {
            librarySnapshot.mergedTracks
        } else {
            LibraryTrackMergePolicy.merge(search(sourceTracks, searchQuery))
        }
        return PreparedLibraryReplacement(
            sourceCandidatesByTrackId = librarySnapshot.sourceCandidatesByTrackId,
            allTracks = librarySnapshot.mergedTracks,
            visibleTracks = visibleTracks,
            favoriteTrackIds = favoriteTrackIds
        )
    }
}
