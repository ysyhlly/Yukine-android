package app.yukine

import app.yukine.model.Track

internal class MainNetworkActionsListener(
    private val nowPlayingViewModel: NowPlayingViewModel,
    private val libraryReplacementSink: LibraryReplacementSink,
    private val networkNavigator: NetworkNavigator,
    private val collectionsReloader: CollectionsReloader,
    private val statusSink: StatusSink
) : NetworkActionsViewModel.Listener {
    fun interface LibraryReplacementSink {
        fun replaceLibrary(cached: List<Track>, favorites: Set<Long>, status: String)
    }

    fun interface NetworkNavigator {
        fun navigateToNetworkPage(page: String)
    }

    fun interface CollectionsReloader {
        fun loadCollections()
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    override fun onStreamAdded(cached: List<Track>, favorites: Set<Long>, status: String) {
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
    }

    override fun onStreamUpdated(
        oldTrackId: Long,
        updated: Track?,
        cached: List<Track>,
        favorites: Set<Long>,
        status: String
    ) {
        if (updated != null) nowPlayingViewModel.replaceQueuedTrack(oldTrackId, updated)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_STREAM_LIST)
    }

    override fun onStreamPlaylistImported(cached: List<Track>, favorites: Set<Long>, status: String) {
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_STREAMING)
    }

    override fun onAllStreamsDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_STREAMING)
    }

    override fun onTrackDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_STREAM_LIST)
    }

    override fun onRemoteSourceDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_SOURCES)
        collectionsReloader.loadCollections()
    }

    override fun onWebDavSourceSaved(sourceId: Long, cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(
            if (sourceId > 0L) MainRoutes.NETWORK_SOURCES else MainRoutes.NETWORK_WEBDAV
        )
        collectionsReloader.loadCollections()
    }

    override fun onRemoteSourceTested(status: String) {
        statusSink.setStatus(status)
        collectionsReloader.loadCollections()
    }

    override fun onRemoteSourceSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_SOURCES)
    }

    override fun onAllWebDavSourcesSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_WEBDAV)
    }
}
