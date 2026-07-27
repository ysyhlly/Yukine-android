package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.Track

/**
 * Thin UI bridge from Collections screen runnables to platform sinks + [CollectionsIntentOwner].
 * Business mutations live on the intent owner / ViewModel — not in this adapter.
 */
internal class CollectionsActionAdapter(
    private val intents: CollectionsIntentOwner,
    private val playlistM3uPicker: PlaylistM3uPicker,
    private val playHistoryClearConfirmer: PlayHistoryClearConfirmer,
    private val backRequester: BackRequester,
    private val statusKeySink: StatusKeySink,
    private val playlistExportDocumentOpener: PlaylistExportDocumentOpener,
    private val selectedPlaylistIdSource: SelectedPlaylistIdSource,
    private val selectedPlaylistTracksSource: SelectedPlaylistTracksSource,
    private val selectedPlaylistNameSource: SelectedPlaylistNameSource,
    private val selectedPlaylistStreamingImporter: SelectedPlaylistStreamingImporter,
    private val favoritesStreamingImporter: FavoritesStreamingImporter,
    private val streamingFavoritesImporter: StreamingFavoritesImporter,
    private val selectedPlaylistStreamingSyncer: SelectedPlaylistStreamingSyncer,
    private val selectedPlaylistTogetherCreator: SelectedPlaylistTogetherCreator =
        SelectedPlaylistTogetherCreator {}
) : CollectionsStateBinding.Listener {
    fun interface PlaylistM3uPicker {
        fun openPlaylistM3uFilePicker()
    }

    fun interface PlayHistoryClearConfirmer {
        fun confirmClearPlayHistory()
    }

    fun interface BackRequester {
        fun requestBack()
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

    fun interface SelectedPlaylistTogetherCreator {
        fun createTogether(playlistId: Long)
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

    override fun showCreatePlaylist() {
        intents.createPlaylist()
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
        intents.play(tracks, index)
    }

    override fun toggleFavorite(track: Track) {
        intents.toggleFavorite(track)
    }

    override fun showAddToPlaylist(track: Track) {
        intents.addToPlaylist(track)
    }

    override fun downloadTrack(track: Track) {
        intents.download(track)
    }

    override fun downloadTracks(tracks: List<Track>) {
        intents.downloadAll(tracks)
    }

    override fun selectPlaylist(playlistId: Long) {
        intents.select(playlistId)
    }

    override fun showRenamePlaylist(playlist: Playlist) {
        intents.renamePlaylist(playlist)
    }

    override fun confirmDeletePlaylist(playlist: Playlist) {
        intents.deletePlaylist(playlist)
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

    override fun createTogetherFromSelectedPlaylist() {
        val playlistId = selectedPlaylistIdSource.selectedPlaylistId()
        if (playlistId >= 0L) selectedPlaylistTogetherCreator.createTogether(playlistId)
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
        intents.moveSelectedPlaylistTrack(playlistId, track, trackIndex, direction)
    }

    override fun removeSelectedPlaylistTrack(playlistId: Long, track: Track) {
        intents.removeSelectedPlaylistTrack(playlistId, track)
    }
}
