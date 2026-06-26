package app.yukine

import app.yukine.model.Track

internal fun interface LibraryReplacementAction {
    fun replace(tracks: List<Track>, favorites: Set<Long>, status: String)
}

internal fun interface PlaybackTracksRetainer {
    fun retain(tracksToKeep: List<Track>)
}

internal fun interface StreamQueueSynchronizer {
    fun sync(oldTrackId: Long, updated: Track)
}

internal fun interface CollectionsLoader {
    fun load()
}

internal class NetworkActionsResultBindings(
    private val replaceLibraryAction: LibraryReplacementAction,
    private val playbackTracksRetainer: PlaybackTracksRetainer,
    private val streamQueueSynchronizer: StreamQueueSynchronizer,
    private val navigateNetworkPageAction: NetworkPageAction,
    private val statusSink: SettingsStatusSink,
    private val collectionsLoader: CollectionsLoader
) : NetworkActionsViewModel.Listener {
    override fun onStreamAdded(cached: List<Track>, favorites: Set<Long>, status: String) {
        replaceLibraryAction.replace(cached, favorites, status)
    }

    override fun onStreamUpdated(
        oldTrackId: Long,
        updated: Track?,
        cached: List<Track>,
        favorites: Set<Long>,
        status: String
    ) {
        if (updated != null) {
            streamQueueSynchronizer.sync(oldTrackId, updated)
        }
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(MainRoutes.NETWORK_STREAM_LIST)
    }

    override fun onStreamPlaylistImported(cached: List<Track>, favorites: Set<Long>, status: String) {
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(MainRoutes.NETWORK_STREAMING)
    }

    override fun onAllStreamsDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        playbackTracksRetainer.retain(cached)
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(MainRoutes.NETWORK_STREAMING)
    }

    override fun onTrackDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        playbackTracksRetainer.retain(cached)
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(MainRoutes.NETWORK_STREAM_LIST)
    }

    override fun onRemoteSourceDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
        playbackTracksRetainer.retain(cached)
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(MainRoutes.NETWORK_SOURCES)
        collectionsLoader.load()
    }

    override fun onWebDavSourceSaved(sourceId: Long, cached: List<Track>, favorites: Set<Long>, status: String) {
        playbackTracksRetainer.retain(cached)
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(if (sourceId > 0L) MainRoutes.NETWORK_SOURCES else MainRoutes.NETWORK_WEBDAV)
        collectionsLoader.load()
    }

    override fun onRemoteSourceTested(status: String) {
        statusSink.set(status)
        collectionsLoader.load()
    }

    override fun onRemoteSourceSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(MainRoutes.NETWORK_SOURCES)
    }

    override fun onAllWebDavSourcesSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
        replaceLibraryAction.replace(cached, favorites, status)
        navigateNetworkPageAction.run(MainRoutes.NETWORK_WEBDAV)
    }
}
