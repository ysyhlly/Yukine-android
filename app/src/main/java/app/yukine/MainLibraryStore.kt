package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.RemoteSource
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import java.util.ArrayList
import java.util.HashSet

internal fun interface MainLibraryStoreFactory {
    fun create(viewModel: MainActivityViewModel): MainLibraryStore
}

internal class MainLibraryStore(
    private val searchUseCase: LibrarySearchUseCase,
    private val viewModel: MainActivityViewModel
) {
    private val combinedSearchUseCase = LibraryCombinedSearchUseCase(searchUseCase)

    fun allTracks(): ArrayList<Track> {
        return ArrayList(state().allTracks)
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
        val cachedTracks = ArrayList(tracks)
        viewModel.replaceLibrary(cachedTracks, searchUseCase.execute(cachedTracks, searchQuery), HashSet(favorites))
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
        viewModel.updateVisibleTracks(
            combinedSearchUseCase.execute(allTracks(), selectedPlaylistTracks(), query)
        )
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
}
