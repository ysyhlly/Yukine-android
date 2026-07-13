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
        fun navigateToNetworkPage(page: NetworkPage)
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
        networkNavigator.navigateToNetworkPage(NetworkPage.StreamList)
    }

    override fun onStreamPlaylistImported(cached: List<Track>, favorites: Set<Long>, status: String) {
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(NetworkPage.Streaming)
    }

    override fun onAllStreamsDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(NetworkPage.Streaming)
    }

    override fun onTrackDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(NetworkPage.StreamList)
    }

    override fun onRemoteSourceDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(NetworkPage.Sources)
        collectionsReloader.loadCollections()
    }

    override fun onWebDavSourceSaved(sourceId: Long, cached: List<Track>, favorites: Set<Long>, status: String) {
        nowPlayingViewModel.retainTracks(cached)
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(
            if (sourceId > 0L) NetworkPage.Sources else NetworkPage.WebDav
        )
        collectionsReloader.loadCollections()
    }

    override fun onRemoteSourceTested(status: String) {
        statusSink.setStatus(status)
        collectionsReloader.loadCollections()
    }

    override fun onRemoteSourceSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(NetworkPage.Sources)
    }

    override fun onAllWebDavSourcesSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
        libraryReplacementSink.replaceLibrary(cached, favorites, status)
        networkNavigator.navigateToNetworkPage(NetworkPage.WebDav)
    }
}
