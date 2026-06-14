package app.echo.next

import app.echo.next.model.Playlist
import app.echo.next.model.RemoteSource
import app.echo.next.model.Track
import app.echo.next.model.TrackPlayRecord
import java.util.ArrayList
import java.util.HashSet

internal class MainLibraryStore(
    private val searchUseCase: LibrarySearchUseCase,
    private val viewModel: MainActivityViewModel
) {
    private var recommendationStreamTitle: String = ""
    private var recommendationStreamTracks = ArrayList<Track>()

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

    fun clearPlayHistory() {
        viewModel.clearPlayHistory()
    }

    fun applySearch(query: String?) {
        viewModel.updateVisibleTracks(searchUseCase.execute(allTracks(), query))
    }

    fun toggleFavorite(trackId: Long): Boolean {
        return viewModel.toggleFavorite(trackId)
    }

    fun setFavorite(trackId: Long, favorite: Boolean) {
        viewModel.setFavorite(trackId, favorite)
    }

    fun selectedPlaylistName(selectedPlaylistId: Long): String {
        for (playlist in playlists()) {
            if (playlist.id == selectedPlaylistId) {
                return playlist.name
            }
        }
        return "Playlist"
    }

    fun showRecommendationStreamList(title: String, tracks: List<Track>) {
        recommendationStreamTitle = title
        recommendationStreamTracks = ArrayList(tracks)
    }

    fun clearRecommendationStreamList() {
        recommendationStreamTitle = ""
        recommendationStreamTracks = ArrayList()
    }

    fun hasRecommendationStreamList(): Boolean {
        return recommendationStreamTracks.isNotEmpty()
    }

    fun recommendationStreamTitle(): String {
        return recommendationStreamTitle.ifBlank { "Daily recommendations" }
    }

    fun recommendationStreamTracks(): ArrayList<Track> {
        return ArrayList(recommendationStreamTracks)
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

    private fun state(): MainActivityLibraryState {
        return viewModel.library.value
    }
}
