package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val remoteSources: List<RemoteSource> = emptyList(),
    val recentlyAddedTracks: List<Track> = emptyList(),
    val longUnplayedTracks: List<Track> = emptyList()
)

/** A fully prepared, owned library payload that is safe to publish without another full-list copy. */
internal data class PreparedLibraryReplacement(
    val sourceCandidatesByTrackId: Map<Long, List<Track>>,
    val allTracks: List<Track>,
    val visibleTracks: List<Track>,
    val favoriteTrackIds: Set<Long>
)

/** The only mutable owner of the library data snapshot used by library, player and navigation. */
class LibraryDataStateOwner @JvmOverloads constructor(
    private val scope: CoroutineScope,
    private val preparationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val mutableState = MutableStateFlow(LibraryStoreState())
    val state: StateFlow<LibraryStoreState> = mutableState.asStateFlow()
    private val mutableFavoriteTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<Long>> = mutableFavoriteTrackIds.asStateFlow()
    private val mutableFavoritePendingTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoritePendingTrackIds: StateFlow<Set<Long>> = mutableFavoritePendingTrackIds.asStateFlow()
    private val favoriteMutationLock = Any()
    private var replacementJob: Job? = null
    private var searchJob: Job? = null
    private var libraryRevision = 0L

    @Volatile
    private var mergeIdentityProvider: ((Track) -> String?)? = null

    @Volatile
    private var mergeIdentitySnapshotProvider: (() -> Map<Long, String>)? = null

    @Volatile
    private var recordingIdentitySnapshotProvider: (() -> Map<Long, Long>)? = null

    @Volatile
    private var artistIdentityProvider: ((Track) -> List<LibraryArtistGroupIdentity>)? = null

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

    /** Binds the optional catalog identity used to merge rows across local and remote metadata. */
    fun bindMergeIdentityProvider(provider: ((Track) -> String?)?) {
        mergeIdentityProvider = provider
    }

    /**
     * Binds the authoritative Room-backed identity snapshot used by an async library replacement.
     * The provider is invoked once on [preparationDispatcher], so a scan/sync commit cannot be
     * hidden by an older process cache and the display path still performs no per-track queries.
     */
    fun bindMergeIdentitySnapshotProvider(provider: (() -> Map<Long, String>)?) {
        mergeIdentitySnapshotProvider = provider
    }

    /** Binds the integer recording snapshot used by the production library hot path. */
    fun bindRecordingIdentitySnapshotProvider(provider: (() -> Map<Long, Long>)?) {
        recordingIdentitySnapshotProvider = provider
    }

    fun bindArtistIdentityProvider(provider: ((Track) -> List<LibraryArtistGroupIdentity>)?) {
        artistIdentityProvider = provider
    }

    fun artistIdentitiesFor(track: Track): List<LibraryArtistGroupIdentity> =
        artistIdentityProvider?.invoke(track).orEmpty()

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
        searchJob?.cancel()
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
        val recordingSnapshotProvider = recordingIdentitySnapshotProvider
        val recordingIdentities = recordingSnapshotProvider?.invoke().orEmpty()
        val snapshotProvider = mergeIdentitySnapshotProvider
        val identitySnapshot = snapshotProvider?.invoke().orEmpty()
        val fallbackIdentityProvider = mergeIdentityProvider ?: { _: Track -> null }
        val librarySnapshot = if (recordingSnapshotProvider != null) {
            LibraryTrackMergePolicy.persistedRecordingSnapshot(sourceTracks) { track ->
                recordingIdentities[track.id]
            }
        } else {
            val mergeIdentitiesByTrackId = sourceTracks.associate { track ->
                val persistedIdentity = identitySnapshot[track.id]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "recording:$it" }
                track.id to if (snapshotProvider != null) {
                    persistedIdentity
                } else {
                    fallbackIdentityProvider(track)
                }
            }
            LibraryTrackMergePolicy.persistedSnapshot(sourceTracks) { track ->
                mergeIdentitiesByTrackId[track.id]
            }
        }
        return PreparedLibraryBase(
            librarySnapshot = librarySnapshot,
            favoriteTrackIds = HashSet(favorites)
        )
    }

    private fun applyPreparedLibrary(prepared: PreparedLibraryReplacement) {
        val current = mutableState.value
        mutableState.value = current.copy(
            sourceCandidatesByTrackId = prepared.sourceCandidatesByTrackId,
            allTracks = prepared.allTracks,
            visibleTracks = prepared.visibleTracks,
            favoriteTrackIds = prepared.favoriteTrackIds,
            favoriteTracks = prepared.allTracks.filter { it.id in prepared.favoriteTrackIds }
        )
        mutableFavoriteTrackIds.value = prepared.favoriteTrackIds
        libraryRevision++
    }

    fun applyCollections(snapshot: LibraryCollectionsResult) {
        val favoriteIds = HashSet(snapshot.favoriteIds)
        mutableState.value = mutableState.value.copy(
            favoriteTrackIds = favoriteIds,
            favoriteTracks = ArrayList(snapshot.favoriteTracks),
            recentRecords = ArrayList(snapshot.recentRecords),
            mostPlayedRecords = ArrayList(snapshot.mostPlayedRecords),
            playlists = ArrayList(snapshot.playlists),
            selectedPlaylistTracks = ArrayList(snapshot.selectedPlaylistTracks),
            remoteSources = ArrayList(snapshot.remoteSources),
            recentlyAddedTracks = ArrayList(snapshot.recentlyAddedTracks),
            longUnplayedTracks = ArrayList(snapshot.longUnplayedTracks)
        )
        mutableFavoriteTrackIds.value = favoriteIds
    }

    fun applySearch(query: String?) {
        latestSearchQuery = normalizedSearchQuery(query)
        searchJob?.cancel()
        updateVisibleTracks(searchVisibleTracks(state(), latestSearchQuery))
    }

    /** Debounces per-keystroke filtering and keeps it off the UI thread. */
    fun applySearchAsync(query: String?, onApplied: Runnable) {
        latestSearchQuery = normalizedSearchQuery(query)
        searchJob?.cancel()
        searchJob = scope.launch {
            if (latestSearchQuery.isNotEmpty()) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            val queryForSearch = latestSearchQuery
            val source = state()
            val revision = libraryRevision
            val visibleTracks = withContext(preparationDispatcher) {
                searchVisibleTracks(source, queryForSearch)
            }
            if (queryForSearch == latestSearchQuery && revision == libraryRevision) {
                updateVisibleTracks(visibleTracks)
                onApplied.run()
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
        setFavorites(setOf(trackId), favorite)
    }

    fun setFavorites(trackIds: Set<Long>, favorite: Boolean) {
        if (trackIds.isEmpty()) return
        val current = mutableState.value
        val favoriteIds = current.favoriteTrackIds.toMutableSet().apply {
            if (favorite) addAll(trackIds) else removeAll(trackIds)
        }
        if (favoriteIds == current.favoriteTrackIds) return
        mutableState.value = current.copy(
            favoriteTrackIds = favoriteIds,
            favoriteTracks = updateFavoriteTracks(current, trackIds, favorite)
        )
        mutableFavoriteTrackIds.value = favoriteIds
    }

    fun beginFavoriteMutation(trackId: Long): Boolean =
        beginFavoriteMutations(listOf(trackId)).isNotEmpty()

    fun beginFavoriteMutations(trackIds: Collection<Long>): Set<Long> =
        synchronized(favoriteMutationLock) {
            val current = mutableFavoritePendingTrackIds.value
            val accepted = trackIds
                .asSequence()
                .filter { it !in current }
                .toCollection(LinkedHashSet())
            if (accepted.isNotEmpty()) {
                mutableFavoritePendingTrackIds.value = current + accepted
            }
            accepted
        }

    fun endFavoriteMutations(trackIds: Collection<Long>) {
        if (trackIds.isEmpty()) return
        synchronized(favoriteMutationLock) {
            val current = mutableFavoritePendingTrackIds.value
            val remaining = current - trackIds.toSet()
            if (remaining != current) {
                mutableFavoritePendingTrackIds.value = remaining
            }
        }
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
        return persistedRepresentatives(searchedTracks, current.sourceCandidatesByTrackId)
    }

    /**
     * Search consumes the canonical grouping prepared during library replacement. It must not
     * rebuild clusters on every keystroke. A playlist alias is redirected to the representative
     * already selected for its persisted recording, then duplicate representatives are removed.
     */
    private fun persistedRepresentatives(
        tracks: List<Track>,
        sourceCandidatesByTrackId: Map<Long, List<Track>>
    ): List<Track> {
        val representatives = LinkedHashMap<Long, Track>(tracks.size)
        tracks.forEach { track ->
            val representative = sourceCandidatesByTrackId[track.id]?.firstOrNull() ?: track
            representatives.putIfAbsent(representative.id, representative)
        }
        return representatives.values.toList()
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
        trackIds: Set<Long>,
        favorite: Boolean
    ): List<Track> {
        if (!favorite) return current.favoriteTracks.filterNot { it.id in trackIds }
        val existingIds = current.favoriteTracks.asSequence().mapTo(HashSet()) { it.id }
        val candidates = LinkedHashMap<Long, Track>()
        current.allTracks.forEach { candidates.putIfAbsent(it.id, it) }
        current.visibleTracks.forEach { candidates.putIfAbsent(it.id, it) }
        current.selectedPlaylistTracks.forEach { candidates.putIfAbsent(it.id, it) }
        val updated = ArrayList(current.favoriteTracks)
        trackIds.forEach { trackId ->
            if (existingIds.add(trackId)) {
                candidates[trackId]?.let(updated::add)
            }
        }
        return updated
    }

    private data class PreparedLibraryBase(
        val librarySnapshot: LibraryTrackMergePolicy.Snapshot,
        val favoriteTrackIds: Set<Long>
    )

    private fun PreparedLibraryBase.toReplacement(searchQuery: String): PreparedLibraryReplacement {
        val visibleTracks = if (searchQuery.isEmpty()) {
            librarySnapshot.mergedTracks
        } else {
            search(librarySnapshot.mergedTracks, searchQuery)
        }
        return PreparedLibraryReplacement(
            sourceCandidatesByTrackId = librarySnapshot.sourceCandidatesByTrackId,
            allTracks = librarySnapshot.mergedTracks,
            visibleTracks = visibleTracks,
            favoriteTrackIds = favoriteTrackIds
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
