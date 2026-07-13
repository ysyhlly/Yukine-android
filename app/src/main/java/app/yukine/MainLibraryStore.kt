package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.HashSet

/** A fully prepared, owned library payload that is safe to publish without another full-list copy. */
internal data class PreparedLibraryReplacement(
    val sourceCandidatesByTrackId: Map<Long, List<Track>>,
    val allTracks: List<Track>,
    val visibleTracks: List<Track>,
    val favoriteTrackIds: Set<Long>
)

internal class MainLibraryStore @JvmOverloads constructor(
    private val searchUseCase: LibrarySearchUseCase,
    private val viewModel: MainActivityViewModel,
    private val preparationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val combinedSearchUseCase = LibraryCombinedSearchUseCase(searchUseCase)
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
        replacementJob = viewModel.viewModelScope.launch {
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
        viewModel.replaceLibrary(
            prepared.sourceCandidatesByTrackId,
            prepared.allTracks,
            prepared.visibleTracks,
            prepared.favoriteTrackIds
        )
        libraryRevision++
    }

    fun applyCollections(snapshot: LibraryCollectionsResult) {
        viewModel.applyCollections(
            HashSet(snapshot.favoriteIds),
            ArrayList(snapshot.favoriteTracks),
            ArrayList(snapshot.recentRecords),
            ArrayList(snapshot.mostPlayedRecords),
            ArrayList(snapshot.playlists),
            ArrayList(snapshot.selectedPlaylistTracks),
            ArrayList(snapshot.remoteSources)
        )
    }

    fun applySearch(query: String?) {
        latestSearchQuery = normalizedSearchQuery(query)
        viewModel.updateVisibleTracks(searchVisibleTracks(state(), latestSearchQuery))
    }

    /** Keeps per-keystroke filtering and duplicate merging off the UI thread. */
    fun applySearchAsync(query: String?, onApplied: Runnable) {
        latestSearchQuery = normalizedSearchQuery(query)
        searchJob?.cancel()
        searchJob = viewModel.viewModelScope.launch {
            while (true) {
                val queryForSearch = latestSearchQuery
                val source = state()
                val revision = libraryRevision
                val visibleTracks = withContext(preparationDispatcher) {
                    searchVisibleTracks(source, queryForSearch)
                }
                if (queryForSearch == latestSearchQuery && revision == libraryRevision) {
                    viewModel.updateVisibleTracks(visibleTracks)
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
        return ArrayList(searchUseCase.execute(tracks, searchQuery))
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

    private fun state(): LibraryStoreState {
        return viewModel.library.value
    }

    private fun normalizedSearchQuery(query: String?): String = query.orEmpty().trim()

    private fun searchVisibleTracks(current: LibraryStoreState, query: String): List<Track> {
        if (query.isEmpty() && current.selectedPlaylistTracks.isEmpty()) {
            return current.allTracks
        }
        val searchedTracks = combinedSearchUseCase.execute(
            current.allTracks,
            current.selectedPlaylistTracks,
            query
        )
        return LibraryTrackMergePolicy.merge(searchedTracks)
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
            LibraryTrackMergePolicy.merge(searchUseCase.execute(sourceTracks, searchQuery))
        }
        return PreparedLibraryReplacement(
            sourceCandidatesByTrackId = librarySnapshot.sourceCandidatesByTrackId,
            allTracks = librarySnapshot.mergedTracks,
            visibleTracks = visibleTracks,
            favoriteTrackIds = favoriteTrackIds
        )
    }
}
