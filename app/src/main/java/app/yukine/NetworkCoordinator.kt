package app.yukine

import app.yukine.model.Track

internal class NetworkCoordinator(
    private val networkActionsViewModel: NetworkActionsViewModel,
    private val libraryStore: MainLibraryStore,
    private val settingsStore: MainSettingsStore,
    private val statusMessageController: StatusMessageController,
    private val listener: Listener
) {

    interface Listener {
        fun replaceLibrary(tracks: List<Track>, favorites: Set<Long>, status: String)
        fun navigateToNetworkTabPage(page: String)
        fun navigateNetworkPage(page: String)
        fun loadCollections()
        fun playTrackListFromHost(tracks: List<Track>, index: Int)
        fun renderAndPersistSelectedTab()
        fun openM3uFilePicker()
        fun nowPlayingRetainTracks(tracks: List<Track>)
    }

    lateinit var networkRequestController: NetworkRequestController
        private set
    lateinit var networkDialogController: NetworkDialogController
        private set
    lateinit var confirmationDialogController: ConfirmationDialogController
        private set
    lateinit var networkRenderCoordinator: NetworkRenderCoordinator
        private set

    fun initialize(
        activity: androidx.activity.ComponentActivity,
        routeController: MainRouteController,
        documentPickerController: DocumentPickerController,
        networkMenuViewModel: NetworkMenuViewModel,
        networkSourcesViewModel: NetworkSourcesViewModel,
        networkTrackListRenderController: NetworkTrackListRenderController,
        streamingSearchRenderController: StreamingSearchRenderController,
        playHistoryActionController: PlayHistoryActionController,
        queueActionController: QueueActionController,
        repository: app.yukine.data.MusicLibraryRepository
    ) {
        val webDavSourceOperations = MusicLibraryWebDavSourceOperations(repository)
        val networkLibraryOperations = MusicLibraryNetworkLibraryOperations(repository)

        networkActionsViewModel.bindUseCases(
            NetworkActionUseCases(
                TestWebDavSourceUseCase(webDavSourceOperations),
                SyncWebDavSourceUseCase(webDavSourceOperations),
                SyncAllWebDavSourcesUseCase(webDavSourceOperations),
                AddStreamUrlUseCase(networkLibraryOperations),
                UpdateStreamUrlUseCase(networkLibraryOperations),
                ImportStreamPlaylistUseCase(networkLibraryOperations),
                DeleteAllStreamsUseCase(networkLibraryOperations),
                DeleteNetworkTrackUseCase(networkLibraryOperations),
                DeleteNetworkTracksUseCase(networkLibraryOperations),
                DeleteRemoteSourceUseCase(networkLibraryOperations),
                SaveWebDavSourceUseCase(networkLibraryOperations)
            )
        )

        networkActionsViewModel.bindListener(object : NetworkActionsViewModel.Listener {
            override fun onStreamAdded(cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.replaceLibrary(cached, favorites, status)
            }
            override fun onStreamUpdated(oldTrackId: Long, updated: Track?, cached: List<Track>, favorites: Set<Long>, status: String) {
                if (updated != null) {
                    // nowPlayingViewModel replacement handled by listener
                }
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAM_LIST)
            }
            override fun onStreamPlaylistImported(cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING)
            }
            override fun onAllStreamsDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.nowPlayingRetainTracks(cached)
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING)
            }
            override fun onTrackDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.nowPlayingRetainTracks(cached)
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAM_LIST)
            }
            override fun onRemoteSourceDeleted(cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.nowPlayingRetainTracks(cached)
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(MainRoutes.NETWORK_SOURCES)
                listener.loadCollections()
            }
            override fun onWebDavSourceSaved(sourceId: Long, cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.nowPlayingRetainTracks(cached)
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(if (sourceId > 0L) MainRoutes.NETWORK_SOURCES else MainRoutes.NETWORK_WEBDAV)
                listener.loadCollections()
            }
            override fun onRemoteSourceTested(status: String) {
                statusMessageController.setStatus(status)
                listener.loadCollections()
            }
            override fun onRemoteSourceSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(MainRoutes.NETWORK_SOURCES)
            }
            override fun onAllWebDavSourcesSynced(cached: List<Track>, favorites: Set<Long>, status: String) {
                listener.replaceLibrary(cached, favorites, status)
                listener.navigateToNetworkTabPage(MainRoutes.NETWORK_WEBDAV)
            }
        })

        networkRequestController = NetworkRequestController(
            networkActionsViewModel,
            object : NetworkRequestController.Labels {
                override fun text(key: String): String =
                    AppLanguage.text(settingsStore.languageMode(), key)
            },
            object : NetworkRequestController.Listener {
                override fun setStatus(status: String) {
                    statusMessageController.setStatus(status)
                }
            }
        )

        val dialogLanguageProvider = DialogLanguageProvider { settingsStore.languageMode() }
        networkDialogController = NetworkDialogController(activity, dialogLanguageProvider, networkRequestController)

        confirmationDialogController = ConfirmationDialogController(
            activity,
            dialogLanguageProvider,
            object : ConfirmationDialogController.Listener {
                override fun clearPlayHistory() { playHistoryActionController.clearPlayHistory() }
                override fun clearQueue() { queueActionController.clearQueue() }
                override fun deleteAllStreams() { networkRequestController.deleteAllStreams() }
                override fun deleteTrack(trackId: Long, status: String) { networkRequestController.deleteTrack(trackId, status) }
                override fun deleteTracks(trackIds: List<Long>, status: String) { networkRequestController.deleteTracks(trackIds, status) }
                override fun deleteRemoteSource(sourceId: Long, name: String) { networkRequestController.deleteRemoteSource(sourceId) }
            }
        )

        val networkMenuEventController = NetworkMenuEventController(
            { page -> listener.navigateNetworkPage(page) },
            { networkDialogController.showAddStream() },
            { networkDialogController.showImportM3u() },
            { networkDialogController.showAddWebDav() },
            { documentPickerController.openM3uFilePicker() },
            { libraryStore.streamTracks() },
            { libraryStore.streamTrackCount() },
            { libraryStore.webDavTracks() },
            { libraryStore.remoteSources() },
            { sourceIds -> networkRequestController.syncAllWebDavSources(sourceIds) },
            { confirmationDialogController.confirmDeleteAllStreams() },
            { tracks, index -> listener.playTrackListFromHost(tracks, index) },
            { key -> AppLanguage.text(settingsStore.languageMode(), key) },
            { status -> statusMessageController.setStatus(status) },
            networkMenuViewModel
        )

        val networkMenuRenderController = NetworkMenuRenderController(networkMenuEventController)

        val networkSourcesEventController = NetworkSourcesEventController(
            routeController,
            networkRequestController,
            { sourceId -> libraryStore.remoteSourceName(sourceId) },
            { sourceId -> libraryStore.webDavTracksForSource(sourceId) },
            { source -> networkDialogController.showEditWebDav(source) },
            { source -> confirmationDialogController.confirmDeleteRemoteSource(source) },
            { tracks, index -> listener.playTrackListFromHost(tracks, index) },
            { key -> AppLanguage.text(settingsStore.languageMode(), key) },
            { status -> statusMessageController.setStatus(status) },
            { listener.renderAndPersistSelectedTab() }
        )

        val networkSourcesRenderController = NetworkSourcesRenderController(
            networkSourcesViewModel,
            networkSourcesEventController
        )

        networkRenderCoordinator = NetworkRenderCoordinator(
            libraryStore,
            networkMenuRenderController,
            networkTrackListRenderController,
            networkSourcesRenderController,
            streamingSearchRenderController
        )
    }

    fun renderNetwork(languageMode: String, networkPage: String, selectedRemoteSourceId: Long, searchQuery: String) {
        networkRenderCoordinator.render(languageMode, networkPage, selectedRemoteSourceId, searchQuery)
    }
}
