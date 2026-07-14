package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track

internal class CollectionsActionAdapter(
    private val playlistCreator: PlaylistCreator,
    private val playlistM3uPicker: PlaylistM3uPicker,
    private val playHistoryClearConfirmer: PlayHistoryClearConfirmer,
    private val backRequester: BackRequester,
    private val trackListPlayer: TrackListPlayer,
    private val favoriteToggler: FavoriteToggler,
    private val playlistAdder: PlaylistAdder,
    private val trackDownloader: TrackDownloader,
    private val tracksDownloader: TracksDownloader,
    private val playlistSelector: PlaylistSelector,
    private val playlistRenamer: PlaylistRenamer,
    private val playlistDeleteConfirmer: PlaylistDeleteConfirmer,
    private val selectedPlaylistIdSource: SelectedPlaylistIdSource,
    private val selectedPlaylistTracksSource: SelectedPlaylistTracksSource,
    private val selectedPlaylistNameSource: SelectedPlaylistNameSource,
    private val statusKeySink: StatusKeySink,
    private val playlistExportDocumentOpener: PlaylistExportDocumentOpener,
    private val selectedPlaylistStreamingImporter: SelectedPlaylistStreamingImporter,
    private val favoritesStreamingImporter: FavoritesStreamingImporter,
    private val streamingFavoritesImporter: StreamingFavoritesImporter,
    private val selectedPlaylistStreamingSyncer: SelectedPlaylistStreamingSyncer,
    private val selectedPlaylistTrackMover: SelectedPlaylistTrackMover,
    private val selectedPlaylistTrackRemover: SelectedPlaylistTrackRemover
) : CollectionsStateBinding.Listener {
    fun interface PlaylistCreator {
        fun showCreatePlaylist()
    }

    fun interface PlaylistM3uPicker {
        fun openPlaylistM3uFilePicker()
    }

    fun interface PlayHistoryClearConfirmer {
        fun confirmClearPlayHistory()
    }

    fun interface BackRequester {
        fun requestBack()
    }

    fun interface TrackListPlayer {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface FavoriteToggler {
        fun toggleFavorite(track: Track)
    }

    fun interface PlaylistAdder {
        fun showAddToPlaylist(track: Track)
    }

    fun interface TrackDownloader {
        fun downloadTrack(track: Track)
    }

    fun interface TracksDownloader {
        fun downloadTracks(tracks: List<Track>)
    }

    fun interface PlaylistSelector {
        fun selectPlaylist(playlistId: Long)
    }

    fun interface PlaylistRenamer {
        fun showRenamePlaylist(playlist: Playlist)
    }

    fun interface PlaylistDeleteConfirmer {
        fun confirmDeletePlaylist(playlist: Playlist)
    }

    fun interface SelectedPlaylistIdSource {
        fun selectedPlaylistId(): Long
    }

    fun interface SelectedPlaylistTracksSource {
        fun selectedPlaylistTracks(): List<Track>
    }

    fun interface SelectedPlaylistNameSource {
        fun selectedPlaylistName(): String
    }

    fun interface StatusKeySink {
        fun setStatusKey(key: String)
    }

    fun interface PlaylistExportDocumentOpener {
        fun openPlaylistExportDocument(playlistId: Long, playlistName: String)
    }

    fun interface SelectedPlaylistStreamingImporter {
        fun importSelectedPlaylistToStreaming()
    }

    fun interface FavoritesStreamingImporter {
        fun importFavoritesToStreaming()
    }

    fun interface StreamingFavoritesImporter {
        fun importStreamingFavorites()
    }

    fun interface SelectedPlaylistStreamingSyncer {
        fun syncSelectedPlaylistFromStreaming()
    }

    fun interface SelectedPlaylistTrackMover {
        fun moveSelectedPlaylistTrack(playlistId: Long, track: Track, trackIndex: Int, direction: Int)
    }

    fun interface SelectedPlaylistTrackRemover {
        fun removeSelectedPlaylistTrack(playlistId: Long, track: Track)
    }

    override fun showCreatePlaylist() {
        playlistCreator.showCreatePlaylist()
    }

    override fun openPlaylistM3uFilePicker() {
        playlistM3uPicker.openPlaylistM3uFilePicker()
    }

    override fun confirmClearPlayHistory() {
        playHistoryClearConfirmer.confirmClearPlayHistory()
    }

    override fun requestBack() {
        backRequester.requestBack()
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        trackListPlayer.playTrackList(tracks, index)
    }

    override fun toggleFavorite(track: Track) {
        favoriteToggler.toggleFavorite(track)
    }

    override fun showAddToPlaylist(track: Track) {
        playlistAdder.showAddToPlaylist(track)
    }

    override fun downloadTrack(track: Track) {
        trackDownloader.downloadTrack(track)
    }

    override fun downloadTracks(tracks: List<Track>) {
        tracksDownloader.downloadTracks(tracks)
    }

    override fun selectPlaylist(playlistId: Long) {
        playlistSelector.selectPlaylist(playlistId)
    }

    override fun showRenamePlaylist(playlist: Playlist) {
        playlistRenamer.showRenamePlaylist(playlist)
    }

    override fun confirmDeletePlaylist(playlist: Playlist) {
        playlistDeleteConfirmer.confirmDeletePlaylist(playlist)
    }

    override fun openSelectedPlaylistExportDocument() {
        val selectedPlaylistId = selectedPlaylistIdSource.selectedPlaylistId()
        if (selectedPlaylistId < 0L || selectedPlaylistTracksSource.selectedPlaylistTracks().isEmpty()) {
            statusKeySink.setStatusKey("no.tracks.in.playlist")
            return
        }
        playlistExportDocumentOpener.openPlaylistExportDocument(
            selectedPlaylistId,
            selectedPlaylistNameSource.selectedPlaylistName()
        )
    }

    override fun importSelectedPlaylistToStreaming() {
        selectedPlaylistStreamingImporter.importSelectedPlaylistToStreaming()
    }

    override fun importFavoritesToStreaming() {
        favoritesStreamingImporter.importFavoritesToStreaming()
    }

    override fun importStreamingFavorites() {
        streamingFavoritesImporter.importStreamingFavorites()
    }

    override fun syncSelectedPlaylistFromStreaming() {
        selectedPlaylistStreamingSyncer.syncSelectedPlaylistFromStreaming()
    }

    override fun moveSelectedPlaylistTrack(playlistId: Long, track: Track, trackIndex: Int, direction: Int) {
        selectedPlaylistTrackMover.moveSelectedPlaylistTrack(playlistId, track, trackIndex, direction)
    }

    override fun removeSelectedPlaylistTrack(playlistId: Long, track: Track) {
        selectedPlaylistTrackRemover.removeSelectedPlaylistTrack(playlistId, track)
    }
}
