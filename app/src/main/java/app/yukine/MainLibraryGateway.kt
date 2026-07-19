package app.yukine

import app.yukine.model.Track

internal interface LibraryRouteActions {
    fun setLibraryMode(mode: String)
    fun selectLibraryGroup(key: String, title: String)
    fun setSelectedPlaylistId(playlistId: Long)
    fun clearLibraryGroup()
    fun setSearchQuery(query: String)

    fun openLibraryPlaylist(playlistId: Long, title: String) {
        selectLibraryGroup("playlist:$playlistId", title)
        setSelectedPlaylistId(playlistId)
    }

    fun closeLibraryGroup() {
        clearLibraryGroup()
        setSelectedPlaylistId(-1L)
    }
}

internal class MainLibraryGateway(
    private val trackListPlayer: TrackListPlayer,
    private val languageModeProvider: LanguageModeProvider,
    private val statusSink: StatusSink,
    private val favoriteApplier: FavoriteApplier,
    private val collectionsLoader: CollectionsLoader,
    private val playlistAdder: PlaylistAdder,
    private val routeActions: LibraryRouteActions,
    private val searchApplier: SearchApplier,
    private val audioImporter: AudioImporter,
    private val libraryScanner: LibraryScanner,
    private val deleteRequester: DeleteRequester = DeleteRequester { },
    private val tracksDownloader: TracksDownloader = TracksDownloader { },
    private val librarySynchronizer: LibrarySynchronizer = LibrarySynchronizer { },
    private val automaticSyncSetter: AutomaticSyncSetter = AutomaticSyncSetter { }
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
        fun setFavorites(trackIds: Set<Long>, favorite: Boolean)
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

    fun interface LibrarySynchronizer {
        fun sync()
    }

    fun interface AutomaticSyncSetter {
        fun setEnabled(enabled: Boolean)
    }

    override fun playTrackList(tracks: List<Track>, index: Int) {
        trackListPlayer.playTrackList(tracks, index)
    }

    override fun showStatusKey(key: String) {
        statusSink.setStatus(AppLanguage.text(languageModeProvider.languageMode(), key))
    }

    override fun applyFavorite(trackId: Long, favorite: Boolean) {
        favoriteApplier.setFavorites(setOf(trackId), favorite)
        collectionsLoader.loadCollections()
    }

    override fun applyFavorites(trackIds: Set<Long>, favorite: Boolean) {
        if (trackIds.isEmpty()) return
        favoriteApplier.setFavorites(trackIds, favorite)
        collectionsLoader.loadCollections()
    }

    override fun addToPlaylist(track: Track) {
        playlistAdder.showAddToPlaylist(track)
    }

    override fun changeGroupMode(mode: String) {
        routeActions.setLibraryMode(mode)
    }

    override fun openGroup(key: String, title: String) {
        routeActions.selectLibraryGroup(key, title)
    }

    override fun openPlaylist(playlistId: Long, title: String) {
        routeActions.openLibraryPlaylist(playlistId, title)
        collectionsLoader.loadCollections()
    }

    override fun backFromGroup() {
        routeActions.closeLibraryGroup()
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

    override fun syncWebDavLibrary() {
        librarySynchronizer.sync()
    }

    override fun setAutomaticSyncEnabled(enabled: Boolean) {
        automaticSyncSetter.setEnabled(enabled)
    }

    fun interface DeleteRequester {
        fun request(tracks: List<Track>)
    }

    fun interface TracksDownloader {
        fun download(tracks: List<Track>)
    }

    override fun requestDeleteTracks(tracks: List<Track>) {
        deleteRequester.request(tracks)
    }

    override fun downloadTracks(tracks: List<Track>) {
        tracksDownloader.download(tracks)
    }
}
