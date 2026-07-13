package app.yukine;

import android.os.Handler;

import androidx.activity.ComponentActivity;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;

/** Owns Activity-scoped library/search/collections assembly and lifecycle. */
final class LibraryFeatureBinding {
    private final ComponentActivity activity;
    private final NavigationViewModel navigationViewModel;
    private final SettingsViewModel settingsViewModel;
    private final MainSettingsStore settingsStore;
    private final NavigationFeatureBinding navigation;
    private final StatusMessageController statusMessages;
    private final MusicLibraryRepository repository;
    private final ToggleFavoriteUseCase toggleFavoriteUseCase;
    private final LoadPlaylistTracksUseCase loadPlaylistTracksUseCase;
    private final LibraryCollectionGateway collectionGateway;
    private final LibraryImportGateway importGateway;
    private final LibraryDocumentGateway documentGateway;
    private final LibraryPlaylistActionGateway playlistActionGateway;
    private final Handler mainHandler;
    private final ArtistInfoRepository artistInfoRepository;

    private final SearchViewModel searchViewModel;
    private final LibraryViewModel viewModel;
    private final CollectionsViewModel collectionsViewModel;
    private final HomeDashboardViewModel homeDashboardViewModel;
    private final LibraryDataStateOwner store;
    private final LibraryCollectionsOwner collectionsOwner;

    private LibraryImportOwner importOwner;
    private LibraryDeletionCompletionOwner deletionCompletionOwner;
    private HiddenLibraryRestoreOwner hiddenLibraryRestoreOwner;
    private TrackListRenderController trackListRenderController;
    private TrackListStatePublisher trackListStatePublisher;
    private LibraryGroupsRenderController groupsRenderController;
    private LibraryPlaylistsRenderController playlistsRenderController;
    private LibraryRenderOwner renderOwner;
    private CollectionsRenderController collectionsRenderController;
    private PlayHistoryActionController playHistoryActionController;
    private UnifiedSearchOwner unifiedSearchOwner;
    private PlaylistMutationOwner playlistMutationOwner;
    private PlaylistDialogController playlistDialogController;
    private MainHomeDashboardRenderListener homeDashboardIntentHandler;

    LibraryFeatureBinding(
            ComponentActivity activity,
            MainActivityViewModels viewModels,
            NavigationViewModel navigationViewModel,
            SettingsViewModel settingsViewModel,
            MainSettingsStore settingsStore,
            NavigationFeatureBinding navigation,
            StatusMessageController statusMessages,
            MusicLibraryRepository repository,
            ToggleFavoriteUseCase toggleFavoriteUseCase,
            LoadPlaylistTracksUseCase loadPlaylistTracksUseCase,
            LibraryCollectionGateway collectionGateway,
            LibraryImportGateway importGateway,
            LibraryDocumentGateway documentGateway,
            LibraryPlaylistActionGateway playlistActionGateway,
            HomeDashboardRepository homeDashboardRepository,
            Handler mainHandler,
            ArtistInfoRepository artistInfoRepository
    ) {
        this.activity = activity;
        this.navigationViewModel = navigationViewModel;
        this.settingsViewModel = settingsViewModel;
        this.settingsStore = settingsStore;
        this.navigation = navigation;
        this.statusMessages = statusMessages;
        this.repository = repository;
        this.toggleFavoriteUseCase = toggleFavoriteUseCase;
        this.loadPlaylistTracksUseCase = loadPlaylistTracksUseCase;
        this.collectionGateway = collectionGateway;
        this.importGateway = importGateway;
        this.documentGateway = documentGateway;
        this.playlistActionGateway = playlistActionGateway;
        this.mainHandler = mainHandler;
        this.artistInfoRepository = artistInfoRepository;
        this.searchViewModel = viewModels.getSearchViewModel();
        this.viewModel = viewModels.getLibraryViewModel();
        this.collectionsViewModel = viewModels.getCollectionsViewModel();
        this.homeDashboardViewModel = viewModels.getHomeDashboardViewModel();
        this.homeDashboardViewModel.bindRepository(homeDashboardRepository);
        this.store = viewModel.dataOwner();
        this.collectionsOwner = new LibraryCollectionsOwner(
                viewModel,
                navigation.getRouteController(),
                store
        );
    }

    LibraryViewModel viewModel() {
        return viewModel;
    }

    LibraryDataStateOwner store() {
        return store;
    }

    LibraryCollectionsOwner collectionsOwner() {
        return collectionsOwner;
    }

    LibraryImportOwner importOwner() {
        return importOwner;
    }

    LibraryDeletionCompletionOwner deletionCompletionOwner() {
        return deletionCompletionOwner;
    }

    PlaylistDialogController playlistDialogController() {
        return playlistDialogController;
    }

    MainHomeDashboardRenderListener homeDashboardIntentHandler() {
        return homeDashboardIntentHandler;
    }

    void bindPlaybackStateSources(
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming,
            LyricsViewModel lyricsViewModel
    ) {
        playback.bindStateSources(
                viewModel.getLibrary(),
                lyricsViewModel,
                settingsViewModel,
                streaming.viewModel(),
                homeDashboardViewModel,
                homeDashboardIntentHandler
        );
    }

    TrackListStatePublisher trackListStatePublisher() {
        return trackListStatePublisher;
    }

    void bindPlatform(
            MainPermissionController permissionController,
            PlaybackFeatureBinding playback,
            Consumer<Boolean> onboardingScanResult
    ) {
        importOwner = new LibraryImportOwner(
                viewModel,
                store,
                navigation.getRouteController(),
                permissionController::hasAudioPermission,
                this::languageMode,
                statusMessages::setStatus,
                collectionsOwner::load,
                onboardingScanResult::accept,
                () -> navigation.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING)
        );
        deletionCompletionOwner = new LibraryDeletionCompletionOwner(
                playback.getNowPlayingViewModel()::removeQueueTracks,
                () -> viewModel.presentationOwner().onAction(app.yukine.ui.LibraryAction.ClearSelection.INSTANCE),
                this::languageMode,
                statusMessages::setStatus,
                importOwner::loadLibrary
        );
    }

    StreamingSearchRenderController bindUi(
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming,
            DocumentPickerController documentPickerController,
            LibraryFileDeleteLauncher fileDeleteLauncher,
            DownloadRequestController downloadRequestController,
            MainPermissionController permissionController,
            LuoxueSourceImportDialogController luoxueSourceImportDialogController,
            Consumer<Track> editStream,
            MainCollectionsRenderListener.PlayHistoryClearConfirmer confirmClearPlayHistory
    ) {
        playlistMutationOwner = new PlaylistMutationOwner(
                viewModel,
                navigation.getRouteController(),
                this::languageMode,
                statusMessages::setStatus,
                collectionsOwner::load
        );
        playlistDialogController = new PlaylistDialogController(
                activity,
                this::languageMode,
                store::playlists,
                playlistMutationOwner
        );
        trackListRenderController = new TrackListRenderController(
                viewModel,
                new MainTrackListRenderListener(
                        (tracks, index) -> viewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                        track -> viewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                        track -> viewModel.onEvent(new LibraryEvent.AddToPlaylist(track)),
                        downloadRequestController::downloadTrack,
                        downloadRequestController::downloadTracks,
                        editStream::accept,
                        track -> fileDeleteLauncher.request(
                                java.util.Collections.singletonList(track),
                                navigation.selectedPlaylistId()
                        )
                )
        );
        trackListStatePublisher = new TrackListStatePublisher(
                trackListRenderController,
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playback.readModel()
        );
        searchViewModel.bindStateSources(
                navigationViewModel.getSearchQuery(),
                viewModel.getLibrary(),
                repository::search
        );
        viewModel.bindPlaylistActionGateway(playlistActionGateway);
        playHistoryActionController = new PlayHistoryActionController(
                viewModel,
                this::languageMode,
                store::clearPlayHistory,
                status -> {
                    statusMessages.setStatus(status);
                    return kotlin.Unit.INSTANCE;
                },
                collectionsOwner::load
        );
        StreamingSearchRenderController streamingSearchRenderController = streaming.bindSearch(
                navigation,
                luoxueSourceImportDialogController
        );
        unifiedSearchOwner = new UnifiedSearchOwner(
                navigation.getRouteController(),
                searchViewModel,
                streaming.viewModel(),
                viewModel,
                store,
                streaming.searchActionHandler(),
                settingsStore,
                streaming.qualityPolicy(),
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                message -> {
                    statusMessages.showFeedback(message);
                    return kotlin.Unit.INSTANCE;
                },
                message -> {
                    statusMessages.setStatus(message);
                    return kotlin.Unit.INSTANCE;
                }
        );
        searchViewModel.updateActions(unifiedSearchOwner.actions());
        bindGateway(playback, documentPickerController, fileDeleteLauncher, downloadRequestController);
        bindRenderOwners(
                playback,
                streaming,
                documentPickerController,
                fileDeleteLauncher,
                downloadRequestController,
                permissionController,
                confirmClearPlayHistory
        );
        hiddenLibraryRestoreOwner = new HiddenLibraryRestoreOwner(
                viewModel,
                () -> importOwner.loadLibrary(true),
                settingsViewModel::refreshSettingsContext
        );
        homeDashboardIntentHandler = new MainHomeDashboardRenderListener(
                mode -> {
                    navigation.getRouteController().setLibraryMode(mode);
                    navigation.navigateToTab(app.yukine.navigation.LibraryTab.INSTANCE, true);
                },
                playback::continueDashboardPlayback,
                () -> navigation.navigateToTab(app.yukine.navigation.NowTab.INSTANCE, true),
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                () -> importOwner.loadLibrary(true),
                () -> navigation.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                () -> navigation.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB),
                () -> navigation.navigateToTab(app.yukine.navigation.CollectionsTab.INSTANCE, true),
                () -> navigation.navigateToTab(app.yukine.navigation.SearchTab.INSTANCE, true),
                () -> streaming.runRecommendationAction(
                        new RecommendationAction.PlayDaily(app.yukine.streaming.StreamingProviderName.NETEASE)
                ),
                () -> streaming.runRecommendationAction(
                        new RecommendationAction.PlayHeartbeat(app.yukine.streaming.StreamingProviderName.NETEASE)
                )
        );
        return streamingSearchRenderController;
    }

    private void bindGateway(
            PlaybackFeatureBinding playback,
            DocumentPickerController documentPickerController,
            LibraryFileDeleteLauncher fileDeleteLauncher,
            DownloadRequestController downloadRequestController
    ) {
        viewModel.bindFavoriteIdsProvider(store::favoriteIds);
        viewModel.bindGateway(new MainLibraryGateway(
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                this::languageMode,
                statusMessages::setStatus,
                store::setFavorite,
                collectionsOwner::load,
                playlistDialogController::showAddToPlaylist,
                navigation.getRouteController(),
                unifiedSearchOwner::applyCurrentSearch,
                documentPickerController::openAudioFilePicker,
                importOwner::loadLibrary,
                tracks -> fileDeleteLauncher.request(tracks, navigation.selectedPlaylistId()),
                downloadRequestController::downloadTracks
        ));
    }

    private void bindRenderOwners(
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming,
            DocumentPickerController documentPickerController,
            LibraryFileDeleteLauncher fileDeleteLauncher,
            DownloadRequestController downloadRequestController,
            MainPermissionController permissionController,
            MainCollectionsRenderListener.PlayHistoryClearConfirmer confirmClearPlayHistory
    ) {
        LibraryPlaylistsIntentOwner playlistIntents = new LibraryPlaylistsIntentOwner(
                viewModel,
                playlist -> {
                    playlistDialogController.confirmDeletePlaylist(playlist);
                    return kotlin.Unit.INSTANCE;
                },
                request -> {
                    trackListStatePublisher.publishLibraryPlaylist(request);
                    return kotlin.Unit.INSTANCE;
                }
        );
        groupsRenderController = new LibraryGroupsRenderController(
                viewModel,
                new MainLibraryGroupsRenderListener(
                        (key, title) -> viewModel.onEvent(new LibraryEvent.OpenGroup(key, title)),
                        () -> navigation.getRouteController().clearLibraryGroup(),
                        () -> viewModel.onEvent(LibraryEvent.BackFromGroup.INSTANCE),
                        this::languageMode,
                        (tracks, index) -> viewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                        (title, tracks) -> fileDeleteLauncher.request(tracks, -1L),
                        playlistIntents::publishLibraryGroupsChrome,
                        trackListStatePublisher::publishLibraryGroup
                ),
                artistInfoRepository,
                action -> mainHandler.post(action)
        );
        playlistsRenderController = new LibraryPlaylistsRenderController(viewModel, playlistIntents);
        collectionsRenderController = new CollectionsRenderController(
                collectionsViewModel,
                new MainCollectionsRenderListener(
                        playlistDialogController::showCreatePlaylist,
                        documentPickerController::openPlaylistM3uFilePicker,
                        confirmClearPlayHistory,
                        navigation::handleBack,
                        (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                        track -> viewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                        playlistDialogController::showAddToPlaylist,
                        downloadRequestController::downloadTrack,
                        downloadRequestController::downloadTracks,
                        collectionsOwner::selectAndLoad,
                        playlistDialogController::showRenamePlaylist,
                        playlistDialogController::confirmDeletePlaylist,
                        navigation::selectedPlaylistId,
                        store::selectedPlaylistTracks,
                        this::selectedPlaylistName,
                        statusMessages::setStatusKey,
                        documentPickerController::openPlaylistExportDocument,
                        streaming::importSelectedPlaylistToStreaming,
                        streaming::importFavoritesToStreaming,
                        streaming::showImportStreamingFavoritesProviderPicker,
                        streaming::syncSelectedPlaylistFromStreaming,
                        (playlistId, track, trackIndex, direction) -> viewModel.playlistOwner().moveSelectedPlaylistTrackJava(
                                playlistId,
                                track,
                                trackIndex,
                                direction,
                                playlistMutationOwner::onSelectedPlaylistTrackMoved
                        ),
                        (playlistId, track) -> viewModel.playlistOwner().removeSelectedPlaylistTrackJava(
                                playlistId,
                                track,
                                playlistMutationOwner::onSelectedPlaylistTrackRemoved
                        )
                )
        );
        collectionsRenderController.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playback.readModel(),
                () -> new CollectionsInsightSnapshot(
                        repository.loadRecentlyAdded(30),
                        repository.loadLongUnplayed(30)
                )
        );
        viewModel.bindFavoriteWriter(toggleFavoriteUseCase::execute);
        viewModel.bindPlaylistTrackLoader(loadPlaylistTracksUseCase::execute);
        viewModel.bindCollectionGateway(collectionGateway);
        viewModel.bindImportGateway(importGateway);
        viewModel.bindDocumentGateway(documentGateway);
        viewModel.bindExclusionGateway(new LibraryExclusionGateway() {
            @Override
            public boolean restoreLibraryExclusion(String sourceKey) {
                return repository.restoreLibraryExclusion(sourceKey);
            }

            @Override
            public int restoreAllLibraryExclusions() {
                return repository.restoreAllLibraryExclusions();
            }
        });
        renderOwner = new LibraryRenderOwner(
                store,
                viewModel,
                trackListRenderController,
                groupsRenderController,
                playlistsRenderController,
                permissionController::hasAudioPermission,
                statusMessages::setStatus
        );
        renderOwner.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playback.readModel()
        );
    }

    List<Track> sourceCandidatesFor(Track track) {
        return store.sourceCandidatesFor(track);
    }

    void loadCollections() {
        collectionsOwner.load();
    }

    void loadLibrary(boolean allowCachedFirst) {
        importOwner.loadLibrary(allowCachedFirst);
    }

    void cancelLibraryLoad() {
        importOwner.cancelLibraryLoad();
    }

    void replaceLibrary(List<Track> tracks, Set<Long> favorites, String status) {
        importOwner.replaceLibrary(tracks, favorites, status);
    }

    void importAudioUris(List<android.net.Uri> uris) {
        importOwner.importAudioUris(uris);
    }

    void importAudioFolder(android.net.Uri uri) {
        importOwner.importAudioFolder(uri);
    }

    void importStreamM3u(android.net.Uri uri) {
        importOwner.importStreamM3u(uri);
    }

    void importPlaylistM3u(android.net.Uri uri) {
        importOwner.importPlaylistM3u(uri);
    }

    void exportPlaylist(android.net.Uri uri, long playlistId, String playlistName) {
        viewModel.loadOwner().exportPlaylistJava(uri, playlistId, playlistName);
    }

    void clearPlayHistory() {
        playHistoryActionController.clearPlayHistory();
    }

    void restoreHidden(String sourceKey) {
        hiddenLibraryRestoreOwner.restore(sourceKey);
    }

    void restoreAllHidden() {
        hiddenLibraryRestoreOwner.restoreAll();
    }

    String selectedPlaylistName() {
        return store.selectedPlaylistName(navigation.selectedPlaylistId());
    }

    void release() {
        searchViewModel.bindStateSources(null, null, null);
        viewModel.bindGateway(null);
        viewModel.bindPlaylistTrackLoader(null);
        viewModel.bindFavoriteWriter(null);
        viewModel.bindFavoriteIdsProvider(null);
        viewModel.bindCollectionGateway(null);
        viewModel.bindImportGateway(null);
        viewModel.bindDocumentGateway(null);
        viewModel.bindPlaylistActionGateway(null);
        renderOwner.release();
        collectionsRenderController.release();
    }

    private String languageMode() {
        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
    }
}
