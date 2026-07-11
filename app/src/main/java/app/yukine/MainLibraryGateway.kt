package app.yukine

import app.yukine.model.Track

internal fun interface MainLibraryGatewayFactory {
    fun create(
        trackListPlayer: MainLibraryGateway.TrackListPlayer,
        languageModeProvider: MainLibraryGateway.LanguageModeProvider,
        statusSink: MainLibraryGateway.StatusSink,
        favoriteApplier: MainLibraryGateway.FavoriteApplier,
        nowBarRenderer: MainLibraryGateway.NowBarRenderer,
        selectedTabRenderer: MainLibraryGateway.SelectedTabRenderer,
        collectionsLoader: MainLibraryGateway.CollectionsLoader,
        playlistAdder: MainLibraryGateway.PlaylistAdder,
        routeActions: LibraryRouteActions,
        searchApplier: MainLibraryGateway.SearchApplier,
        audioImporter: MainLibraryGateway.AudioImporter,
        libraryScanner: MainLibraryGateway.LibraryScanner,
        deleteRequester: MainLibraryGateway.DeleteRequester,
        tracksDownloader: MainLibraryGateway.TracksDownloader
    ): LibraryGateway
}

internal interface LibraryRouteActions {
    fun setLibraryMode(mode: String)
    fun selectLibraryGroup(key: String, title: String)
    fun setSelectedPlaylistId(playlistId: Long)
    fun clearLibraryGroup()
    fun setSearchQuery(query: String)
}

internal class MainLibraryGateway(
    private val trackListPlayer: TrackListPlayer,
    private val languageModeProvider: LanguageModeProvider,
    private val statusSink: StatusSink,
    private val favoriteApplier: FavoriteApplier,
    private val nowBarRenderer: NowBarRenderer,
    private val selectedTabRenderer: SelectedTabRenderer,
    private val collectionsLoader: CollectionsLoader,
    private val playlistAdder: PlaylistAdder,
    private val routeActions: LibraryRouteActions,
    private val searchApplier: SearchApplier,
    private val audioImporter: AudioImporter,
    private val libraryScanner: LibraryScanner,
    private val deleteRequester: DeleteRequester = DeleteRequester { },
    private val tracksDownloader: TracksDownloader = TracksDownloader { }
) : LibraryGateway {
    fun interface TrackListPlayer {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    fun interface LanguageModeProvider {
        fun languageMode(): String
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    fun interface FavoriteApplier {
        fun setFavorite(trackId: Long, favorite: Boolean)
    }

    fun interface NowBarRenderer {
        fun renderNowBar()
    }

    fun interface SelectedTabRenderer {
        fun renderSelectedTab()
    }

    fun interface CollectionsLoader {
        fun loadCollections()
    }

    fun interface PlaylistAdder {
        fun showAddToPlaylist(track: Track)
    }

    fun interface SearchApplier {
        fun applySearch()
    }

    fun interface AudioImporter {
        fun openAudioFilePicker()
    }

    fun interface LibraryScanner {
        fun scanLibrary(allowCachedFirst: Boolean)
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        trackListPlayer.playTrackList(tracks, index)
    }

    override fun showStatusKey(key: String) {
        statusSink.setStatus(AppLanguage.text(languageModeProvider.languageMode(), key))
    }

    override fun applyFavorite(trackId: Long, favorite: Boolean) {
        favoriteApplier.setFavorite(trackId, favorite)
        nowBarRenderer.renderNowBar()
        selectedTabRenderer.renderSelectedTab()
        collectionsLoader.loadCollections()
    }

    override fun addToPlaylist(track: Track) {
        playlistAdder.showAddToPlaylist(track)
    }

    override fun changeGroupMode(mode: String) {
        routeActions.setLibraryMode(mode)
        selectedTabRenderer.renderSelectedTab()
    }

    override fun openGroup(key: String, title: String) {
        routeActions.selectLibraryGroup(key, title)
        selectedTabRenderer.renderSelectedTab()
    }

    override fun openPlaylist(playlistId: Long, title: String) {
        routeActions.selectLibraryGroup("playlist:$playlistId", title)
        routeActions.setSelectedPlaylistId(playlistId)
        collectionsLoader.loadCollections()
    }

    override fun backFromGroup() {
        routeActions.clearLibraryGroup()
        routeActions.setSelectedPlaylistId(-1L)
        selectedTabRenderer.renderSelectedTab()
    }

    override fun search(query: String) {
        routeActions.setSearchQuery(query)
        searchApplier.applySearch()
    }

    override fun importFiles() {
        audioImporter.openAudioFilePicker()
    }

    override fun scanLibrary() {
        libraryScanner.scanLibrary(false)
    }

    fun interface DeleteRequester {
        fun request(tracks: List<Track>)
    }

    fun interface TracksDownloader {
        fun download(tracks: List<Track>)
    }

    override fun refreshLibrary() {
        selectedTabRenderer.renderSelectedTab()
    }

    override fun requestDeleteTracks(tracks: List<Track>) {
        deleteRequester.request(tracks)
    }

    override fun downloadTracks(tracks: List<Track>) {
        tracksDownloader.download(tracks)
    }
}
