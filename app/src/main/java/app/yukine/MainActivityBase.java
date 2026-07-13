package app.yukine;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.activity.ComponentActivity;

import java.util.Collections;
import javax.inject.Inject;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.ui.LibraryUiLabels;
import app.yukine.ui.EchoTheme;
import app.yukine.model.Track;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.streaming.LuoxueTrackMetadataResolver;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.streaming.cache.StreamingCacheRepository;
import app.yukine.ui.LibraryGroupActions;
import app.yukine.ui.LibraryGroupUiState;
import app.yukine.ui.TrackRowActions;
import app.yukine.ui.TrackRowUiState;

public abstract class MainActivityBase extends ComponentActivity {
    @Inject Handler mainHandler;
    @Inject MainExecutors executors;
    @Inject LuoxueSourceStore luoxueSourceStore;
    @Inject LuoxueTrackMetadataResolver luoxueTrackMetadataResolver;
    @Inject StreamingCacheRepository streamingCacheRepository;
    @Inject StreamingPlaybackTaskScheduler streamingPlaybackTaskScheduler;
    @Inject ResolveStreamingPlaybackUseCase resolveStreamingPlaybackUseCase;
    @Inject StreamingTrackMatchUseCase streamingTrackMatchUseCase;
    @Inject ToggleFavoriteUseCase toggleFavoriteUseCase;
    @Inject LoadPlaylistTracksUseCase loadPlaylistTracksUseCase;
    @Inject LibraryCollectionGateway libraryCollectionGateway;
    @Inject LibraryImportGateway libraryImportGateway;
    @Inject LibraryDocumentGateway libraryDocumentGateway;
    @Inject LibraryPlaylistActionGateway libraryPlaylistActionGateway;
    @Inject LoadSettingsPreferencesUseCase loadSettingsPreferencesUseCase;
    @Inject ApplySettingsPreferenceUseCase applySettingsPreferenceUseCase;
    @Inject StreamingLocalPlaylistOperations streamingLocalPlaylistOperations;
    @Inject LibrarySearchUseCase librarySearchUseCase;
    @Inject LoadLyricsSettingsUseCase loadLyricsSettingsUseCase;
    @Inject LyricsLoader lyricsLoader;
    @Inject NetworkActionUseCases networkActionUseCases;

    @Inject
    StreamingGatewaySettingsStore streamingGatewaySettingsStore;

    @Inject
    MusicLibraryRepository repository;

    @Inject
    TrackDownloadManager trackDownloadManager;

    @Inject
    TrackShareOperations trackShareOperations;

    private MainActivityViewModels activityViewModels;
    private MainActivityViewModel viewModel;
    private NavigationViewModel navigationViewModel;
    private PlaybackFeatureBinding playbackFeatureBinding;
    private HomeDashboardViewModel homeDashboardViewModel;
    private DownloadsViewModel downloadsViewModel;
    private SearchViewModel searchViewModel;
    private LibraryViewModel libraryViewModel;
    private CollectionsViewModel collectionsViewModel;
      private SettingsViewModel settingsViewModel;
      private NetworkMenuViewModel networkMenuViewModel;
      private StatusMessageViewModel statusMessageViewModel;
      private NetworkSourcesViewModel networkSourcesViewModel;
    private StreamingFeatureBinding streamingFeatureBinding;
    private NavigationFeatureBinding navigationFeatureBinding;
    private MainLibraryStore libraryStore;
    private LibraryCollectionsOwner libraryCollectionsOwner;
    @Inject MainSettingsStore settingsStore;
    @Inject NowPlayingPlaybackServiceStarter nowPlayingPlaybackServiceStarter;
    @Inject PlaybackServiceCommandQueue playbackServiceCommandQueue;
    @Inject LibraryDeletionUseCase libraryDeletionUseCase;
    @Inject ArtistInfoRepository artistInfoRepository;
    private StatusMessageController statusMessageController;
    private MainPermissionController permissionController;
    private PermissionResultOwner permissionResultOwner;
    private MainUiShellController uiShellController;
    private TrackShareLauncher trackShareLauncher;
    private CustomBackgroundAccentController customBackgroundAccentController;
    private LibraryFileDeleteLauncher libraryFileDeleteLauncher;
    private LibraryDeletionCompletionOwner libraryDeletionCompletionOwner;
    private DocumentPickerController documentPickerController;
    private BackgroundImagePickerController backgroundImagePickerController;
    private BackgroundImageSelectionOwner backgroundImageSelectionOwner;
    private BackupRestoreLauncher backupRestoreLauncher;
    private DownloadRequestController downloadRequestController;
    private DownloadDirectoryOwner downloadDirectoryOwner;
    private LuoxueSourceImportController luoxueSourceImportController;
    private LuoxueSourceImportDialogController luoxueSourceImportDialogController;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsContextProvider settingsContextProvider;
    private SettingsEffectOwner settingsEffectOwner;
    private HiddenLibraryRestoreOwner hiddenLibraryRestoreOwner;
    private TrackListRenderController trackListRenderController;
    private TrackListStatePublisher trackListStatePublisher;
    private NetworkActionsViewModel networkActionsViewModel;
    private NetworkRequestController networkRequestController;
    private MainHomeDashboardRenderListener homeDashboardIntentHandler;
    private LibraryGroupsRenderController libraryGroupsRenderController;
    private LibraryPlaylistsRenderController libraryPlaylistsRenderController;
    private LibraryRenderOwner libraryRenderOwner;
    private LibraryImportOwner libraryImportOwner;
    private CollectionsRenderController collectionsRenderController;
    private PlayHistoryActionController playHistoryActionController;
    private LyricsViewModel lyricsViewModel;
    private UnifiedSearchOwner unifiedSearchOwner;

    private NetworkDialogController networkDialogController;
    private PlaylistDialogController playlistDialogController;
    private PlaylistMutationOwner playlistMutationOwner;
    private ConfirmationDialogController confirmationDialogController;
    private OnboardingOwner onboardingOwner;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViewModels(createActivityViewModels());
        initializeRouteStoresAndStatus();
        initializeStreamingFeatureBinding();
        initializeLibraryStateOwners();
        initializePlaybackFeatureBinding();
        initializeDownloadRequests();
        initializePlatformControllers();
        initializeNavigationRendering();
        initializePlaybackControllers();
        initializeSettingsEffects();
        streamingFeatureBinding.bindDialogs(
                libraryCollectionsOwner,
                libraryImportOwner,
                luoxueSourceImportDialogController
        );
        StreamingSearchRenderController streamingSearchRenderController = initializeStoresAndDataGateways();
        initializeNetworkOwners(streamingSearchRenderController);
        initializeNowPlayingEffectOwner();
        initializeOnboardingAndStartup();
    }

    protected abstract MainActivityViewModels createActivityViewModels();

    private void initializeLibraryStateOwners() {
        libraryStore = new MainLibraryStore(librarySearchUseCase, viewModel);
        libraryCollectionsOwner = new LibraryCollectionsOwner(
                libraryViewModel,
                navigationFeatureBinding.getRouteController(),
                libraryStore
        );
    }

    private void initializeViewModels(MainActivityViewModels viewModels) {
        activityViewModels = viewModels;
        viewModel = viewModels.getMainActivityViewModel();
        navigationViewModel = viewModels.getNavigationViewModel();
        homeDashboardViewModel = viewModels.getHomeDashboardViewModel();
        downloadsViewModel = viewModels.getDownloadsViewModel();
        searchViewModel = viewModels.getSearchViewModel();
        lyricsViewModel = viewModels.getLyricsViewModel();
        libraryViewModel = viewModels.getLibraryViewModel();
        collectionsViewModel = viewModels.getCollectionsViewModel();
        settingsViewModel = viewModels.getSettingsViewModel();
        networkMenuViewModel = viewModels.getNetworkMenuViewModel();
        networkActionsViewModel = (NetworkActionsViewModel) viewModels.getNetworkActionsViewModel();
        statusMessageViewModel = (StatusMessageViewModel) viewModels.getStatusMessageViewModel();
        networkSourcesViewModel = viewModels.getNetworkSourcesViewModel();
    }

    private void initializeStreamingFeatureBinding() {
        streamingFeatureBinding = new StreamingFeatureBinding(
                this,
                activityViewModels,
                mainHandler,
                executors,
                settingsStore,
                statusMessageController,
                streamingGatewaySettingsStore,
                streamingCacheRepository,
                streamingPlaybackTaskScheduler,
                resolveStreamingPlaybackUseCase,
                streamingTrackMatchUseCase,
                streamingLocalPlaylistOperations,
                luoxueTrackMetadataResolver
        );
    }

    private void initializePlaybackFeatureBinding() {
        playbackFeatureBinding = new PlaybackFeatureBinding(
                this,
                activityViewModels,
                mainHandler,
                settingsStore,
                nowPlayingPlaybackServiceStarter,
                playbackServiceCommandQueue,
                resolveStreamingPlaybackUseCase,
                luoxueTrackMetadataResolver,
                streamingFeatureBinding.qualityPolicy(),
                statusMessageController
        );
        playbackFeatureBinding.bindConnection(
                lyricsViewModel,
                libraryCollectionsOwner,
                streamingFeatureBinding.viewModel(),
                libraryViewModel,
                track -> libraryStore == null
                        ? Collections.<Track>emptyList()
                        : libraryStore.sourceCandidatesFor(track),
                streamingFeatureBinding::preResolveNext,
                streamingFeatureBinding::recoverBuffering,
                streamingFeatureBinding::resolveCurrentQueueTrackIfNeeded
        );
    }

    private void initializeDownloadRequests() {
        downloadRequestController = new DownloadRequestController(
                () -> trackDownloadManager,
                downloadsViewModel,
                resolveStreamingPlaybackUseCase,
                new DownloadQualityDialogController(
                        this,
                        () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()
                ),
                streamingFeatureBinding.downloadResolver(),
                message -> {
                    statusMessageController.showFeedback(message);
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    private void initializeLibraryGateway() {
        libraryViewModel.bindFavoriteIdsProvider(
                () -> libraryStore == null ? Collections.emptySet() : libraryStore.favoriteIds()
        );
        libraryViewModel.bindGateway(new MainLibraryGateway(
                (tracks, index) -> playbackFeatureBinding.getPlaybackStartController().playTrackList(tracks, index),
                () -> settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                (trackId, favorite) -> viewModel.setFavorite(trackId, favorite),
                libraryCollectionsOwner::load,
                track -> MainActivityBase.this.playlistDialogController.showAddToPlaylist(track),
                navigationFeatureBinding.getRouteController(),
                unifiedSearchOwner::applyCurrentSearch,
                () -> documentPickerController.openAudioFilePicker(),
                allowCachedFirst -> libraryImportOwner.loadLibrary(allowCachedFirst),
                tracks -> libraryFileDeleteLauncher.request(
                        tracks,
                        navigationFeatureBinding.selectedPlaylistId()
                ),
                tracks -> downloadRequestController.downloadTracks(tracks)
        ));
    }

    private void initializeRouteStoresAndStatus() {
        uiShellController = new MainUiShellController(this);
        navigationFeatureBinding = new NavigationFeatureBinding(
                this,
                navigationViewModel,
                settingsViewModel,
                settingsStore
        );
        statusMessageController = new StatusMessageController(
                statusMessageViewModel,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                message -> uiShellController.updateStatus(message),
                () -> settingsStore != null && settingsStore.debugPromptsEnabled()
        );
        trackShareLauncher = new TrackShareLauncher(
                this,
                trackShareOperations,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                () -> settingsStore == null ? TrackShareStyle.defaultValue() : settingsStore.shareStyle(),
                new TrackShareStatusSink() {
                    @Override
                    public void showFeedback(String message) {
                        statusMessageController.showFeedback(message);
                    }

                    @Override
                    public void setStatus(String message) {
                        statusMessageController.setStatus(message);
                    }
                }
        );
    }

    private void initializePlatformControllers() {
        customBackgroundAccentController = new CustomBackgroundAccentController(
                getContentResolver(),
                task -> executors.io(task),
                task -> mainHandler.post(task),
                EchoTheme::setCustomBackgroundAccentArgb
        );
        permissionResultOwner = new PermissionResultOwner(
                () -> permissionController != null && permissionController.hasAudioPermission(),
                allowCachedFirst -> libraryImportOwner.loadLibrary(allowCachedFirst),
                () -> {
                    if (onboardingOwner != null) {
                        onboardingOwner.onPermissionsChanged();
                    }
                }
        );
        permissionController = new MainPermissionController(this, permissionResultOwner);
        libraryImportOwner = new LibraryImportOwner(
                libraryViewModel,
                libraryStore,
                navigationFeatureBinding.getRouteController(),
                () -> permissionController != null && permissionController.hasAudioPermission(),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                libraryCollectionsOwner::load,
                canScan -> {
                    if (onboardingOwner != null) {
                        onboardingOwner.onLibraryScanResult(canScan);
                    }
                },
                () -> navigationFeatureBinding.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING)
        );
        downloadDirectoryOwner = new DownloadDirectoryOwner(
                trackDownloadManager,
                downloadsViewModel,
                statusMessageController::showFeedback
        );
        documentPickerController = new DocumentPickerController(this, new DocumentPickerActions(
                libraryImportOwner::importAudioUris,
                libraryImportOwner::importAudioFolder,
                downloadDirectoryOwner::setCustomDirectory,
                libraryImportOwner::importStreamM3u,
                (exportUri, playlistId, playlistName) -> libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName),
                libraryImportOwner::importPlaylistM3u,
                uris -> luoxueSourceImportController.importSelectedUris(uris)
        ));
        backgroundImageSelectionOwner = new BackgroundImageSelectionOwner(
                settingsViewModel,
                settingsStore::pageBackgrounds,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                statusMessageController::setStatus
        );
        backgroundImagePickerController = new BackgroundImagePickerController(
                this,
                backgroundImageSelectionOwner,
                task -> executors.io(task),
                task -> mainHandler.post(task),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()
        );
        libraryDeletionCompletionOwner = new LibraryDeletionCompletionOwner(
                playbackFeatureBinding.getNowPlayingViewModel()::removeQueueTracks,
                () -> libraryViewModel.onLibraryAction(app.yukine.ui.LibraryAction.ClearSelection.INSTANCE),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                statusMessageController::setStatus,
                libraryImportOwner::loadLibrary
        );
        libraryFileDeleteLauncher = new LibraryFileDeleteLauncher(
                this,
                libraryDeletionUseCase,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                libraryDeletionCompletionOwner
        );
        backupRestoreLauncher = new BackupRestoreLauncher(
                this,
                statusKey -> {
                    statusMessageController.setStatusKey(statusKey);
                    return kotlin.Unit.INSTANCE;
                },
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()
        );
        luoxueSourceImportController = new LuoxueSourceImportController(
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                ),
                () -> documentPickerController,
                new ContentResolverLuoxueSourceDocumentReader(getContentResolver()),
                luoxueSourceStore,
                task -> executors.io(task),
                task -> executors.network(task),
                task -> mainHandler.post(task),
                status -> statusMessageController.setStatus(status),
                streamingFeatureBinding::onLuoxueSourcesChanged
        );
        luoxueSourceImportDialogController = new LuoxueSourceImportDialogController(
                this,
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                ),
                luoxueSourceImportController
        );
    }

    private void initializeNowPlayingEffectOwner() {
        playbackFeatureBinding.bindEffects(
                () -> navigationFeatureBinding.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                playlistDialogController::showAddToPlaylist,
                trackShareLauncher::share,
                downloadRequestController::downloadTrack
        );
    }

    private void initializeNavigationRendering() {
        trackListRenderController = new TrackListRenderController(libraryViewModel, new MainTrackListRenderListener(
                (tracks, index) -> libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                track -> libraryViewModel.onEvent(new LibraryEvent.AddToPlaylist(track)),
                track -> downloadRequestController.downloadTrack(track),
                tracks -> downloadRequestController.downloadTracks(tracks),
                track -> networkDialogController.showEditStream(track),
                track -> libraryFileDeleteLauncher.request(
                        java.util.Collections.singletonList(track),
                        navigationFeatureBinding.selectedPlaylistId()
                )
        ));
        trackListStatePublisher = new TrackListStatePublisher(
                trackListRenderController,
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playbackFeatureBinding.readModel()
        );
    }

    private void initializePlaybackControllers() {
        streamingFeatureBinding.bindPlayback(
                playbackFeatureBinding,
                navigationFeatureBinding,
                libraryStore,
                lyricsViewModel,
                () -> confirmationDialogController.confirmClearQueue()
        );
        homeDashboardIntentHandler = new MainHomeDashboardRenderListener(
                mode -> {
                    navigationFeatureBinding.getRouteController().setLibraryMode(mode);
                    navigationFeatureBinding.navigateToTab(app.yukine.navigation.LibraryTab.INSTANCE, true);
                },
                playbackFeatureBinding::continueDashboardPlayback,
                () -> navigationFeatureBinding.navigateToTab(app.yukine.navigation.NowTab.INSTANCE, true),
                (tracks, index) -> playbackFeatureBinding.getPlaybackStartController().playTrackList(tracks, index),
                () -> libraryImportOwner.loadLibrary(true),
                () -> navigationFeatureBinding.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                () -> navigationFeatureBinding.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB),
                () -> navigationFeatureBinding.navigateToTab(app.yukine.navigation.CollectionsTab.INSTANCE, true),
                () -> navigationFeatureBinding.navigateToTab(app.yukine.navigation.SearchTab.INSTANCE, true),
                () -> streamingFeatureBinding.runRecommendationAction(
                        new RecommendationAction.PlayDaily(StreamingProviderName.NETEASE)
                ),
                () -> streamingFeatureBinding.runRecommendationAction(
                        new RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE)
                )
        );
    }

    private void initializeSettingsEffects() {
        hiddenLibraryRestoreOwner = new HiddenLibraryRestoreOwner(
                libraryViewModel,
                () -> libraryImportOwner.loadLibrary(true),
                settingsViewModel::refreshSettingsContext
        );
        settingsEffectOwner = new SettingsEffectOwner(
                new SettingsNavigationEffectActions(
                        statusMessageController::setStatus,
                        navigationFeatureBinding.getRouteController()::setSettingsPage,
                        navigationFeatureBinding::navigateToNetworkTabPage,
                        () -> navigationFeatureBinding.navigateToTab(
                                app.yukine.navigation.DownloadsTab.INSTANCE,
                                true
                        )
                ),
                new SettingsLibraryEffectActions(
                        permissionController::requestNeededPermissions,
                        () -> libraryImportOwner.loadLibrary(false),
                        documentPickerController::openAudioFilePicker,
                        documentPickerController::openAudioFolderPicker,
                        luoxueSourceImportDialogController::showSourceManager,
                        luoxueSourceImportDialogController::showImportDialog,
                        hiddenLibraryRestoreOwner::restore,
                        hiddenLibraryRestoreOwner::restoreAll
                ),
                new SettingsPlaybackEffectActions(
                        () -> lyricsViewModel.reloadCurrentLyrics(settingsStore.languageMode()),
                        minutes -> playbackFeatureBinding.applyActionResult(
                                playbackFeatureBinding.getNowPlayingViewModel().startSleepTimer(minutes)
                        ),
                        () -> playbackFeatureBinding.applyActionResult(
                                playbackFeatureBinding.getNowPlayingViewModel().cancelSleepTimer()
                        ),
                        settingsViewModel::openFloatingLyricsPermission
                ),
                new SettingsFileEffectActions(
                        backgroundImagePickerController::open,
                        backupRestoreLauncher::exportBackup,
                        backupRestoreLauncher::importBackup
                ),
                streamingFeatureBinding::applyEndpoint
        );
        settingsViewModel.bindEffectListener(settingsEffectOwner);
    }

    private StreamingSearchRenderController initializeStoresAndDataGateways() {
        searchViewModel.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                repository::search
        );
        settingsStore.load(loadSettingsPreferencesUseCase.execute());
        if (EchoTheme.ACCENT_DYNAMIC_BACKGROUND.equals(settingsStore.accentMode())) {
            customBackgroundAccentController.refresh(settingsStore.pageBackgrounds());
        }
        libraryViewModel.bindPlaylistActionGateway(libraryPlaylistActionGateway);
        playHistoryActionController = new PlayHistoryActionController(
                libraryViewModel,
                () -> settingsStore.languageMode(),
                viewModel::clearPlayHistory,
                status -> { statusMessageController.setStatus(status); return kotlin.Unit.INSTANCE; },
                libraryCollectionsOwner::load
        );
        StreamingSearchRenderController streamingSearchRenderController = streamingFeatureBinding.bindSearch(
                navigationFeatureBinding,
                luoxueSourceImportDialogController
        );
        unifiedSearchOwner = new UnifiedSearchOwner(
                navigationFeatureBinding.getRouteController(),
                searchViewModel,
                streamingFeatureBinding.viewModel(),
                libraryViewModel,
                libraryStore,
                streamingFeatureBinding.searchActionHandler(),
                settingsStore,
                streamingFeatureBinding.qualityPolicy(),
                (tracks, index) -> playbackFeatureBinding.getPlaybackStartController().playTrackList(tracks, index),
                message -> {
                    statusMessageController.showFeedback(message);
                    return kotlin.Unit.INSTANCE;
                },
                message -> {
                    statusMessageController.setStatus(message);
                    return kotlin.Unit.INSTANCE;
                }
        );
        searchViewModel.updateActions(unifiedSearchOwner.actions());
        initializeLibraryGateway();
        initializeRenderOwners(streamingSearchRenderController);
        return streamingSearchRenderController;
    }

    private void initializeRenderOwners(StreamingSearchRenderController streamingSearchRenderController) {
        LibraryPlaylistsIntentOwner playlistIntents = new LibraryPlaylistsIntentOwner(
                libraryViewModel,
                playlist -> {
                    playlistDialogController.confirmDeletePlaylist(playlist);
                    return kotlin.Unit.INSTANCE;
                },
                request -> {
                    trackListStatePublisher.publishLibraryPlaylist(request);
                    return kotlin.Unit.INSTANCE;
                }
        );
        libraryGroupsRenderController = new LibraryGroupsRenderController(
                libraryViewModel,
                new MainLibraryGroupsRenderListener(
                        (key, title) -> libraryViewModel.onEvent(new LibraryEvent.OpenGroup(key, title)),
                        () -> navigationFeatureBinding.getRouteController().clearLibraryGroup(),
                        () -> libraryViewModel.onEvent(LibraryEvent.BackFromGroup.INSTANCE),
                        () -> settingsStore.languageMode(),
                        (tracks, index) -> libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                        (title, tracks) -> libraryFileDeleteLauncher.request(tracks, -1L),
                        playlistIntents::publishLibraryGroupsChrome,
                        trackListStatePublisher::publishLibraryGroup
                ),
                artistInfoRepository,
                action -> mainHandler.post(action)
        );
        libraryPlaylistsRenderController = new LibraryPlaylistsRenderController(
                libraryViewModel,
                playlistIntents
        );
        collectionsRenderController = new CollectionsRenderController(collectionsViewModel, new MainCollectionsRenderListener(
                () -> playlistDialogController.showCreatePlaylist(),
                () -> documentPickerController.openPlaylistM3uFilePicker(),
                () -> confirmationDialogController.confirmClearPlayHistory(),
                navigationFeatureBinding::handleBack,
                (tracks, index) -> playbackFeatureBinding.getPlaybackStartController().playTrackList(tracks, index),
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                track -> playlistDialogController.showAddToPlaylist(track),
                track -> downloadRequestController.downloadTrack(track),
                tracks -> downloadRequestController.downloadTracks(tracks),
                libraryCollectionsOwner::selectAndLoad,
                playlist -> playlistDialogController.showRenamePlaylist(playlist),
                playlist -> playlistDialogController.confirmDeletePlaylist(playlist),
                navigationFeatureBinding::selectedPlaylistId,
                () -> libraryStore.selectedPlaylistTracks(),
                this::selectedPlaylistName,
                key -> statusMessageController.setStatusKey(key),
                (playlistId, playlistName) -> documentPickerController.openPlaylistExportDocument(playlistId, playlistName),
                streamingFeatureBinding::importSelectedPlaylistToStreaming,
                streamingFeatureBinding::importFavoritesToStreaming,
                streamingFeatureBinding::showImportStreamingFavoritesProviderPicker,
                streamingFeatureBinding::syncSelectedPlaylistFromStreaming,
                (playlistId, track, trackIndex, direction) ->
                        libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction, playlistMutationOwner::onSelectedPlaylistTrackMoved),
                (playlistId, track) ->
                        libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track, playlistMutationOwner::onSelectedPlaylistTrackRemoved)
        ));
        collectionsRenderController.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playbackFeatureBinding.readModel(),
                () -> new CollectionsInsightSnapshot(
                        repository.loadRecentlyAdded(30),
                        repository.loadLongUnplayed(30)
                )
        );
        libraryViewModel.bindFavoriteWriter((track, favorite) -> toggleFavoriteUseCase.execute(track, favorite));

        libraryViewModel.bindPlaylistTrackLoader(playlistId -> loadPlaylistTracksUseCase.execute(playlistId));
        LoadedLyricsSettings loadedLyricsSettings = loadLyricsSettingsUseCase.execute();
        lyricsViewModel.configure(
                lyricsLoader,
                loadedLyricsSettings.onlineLyricsEnabled,
                loadedLyricsSettings.lyricsOffsetMs
        );
        libraryViewModel.bindCollectionGateway(libraryCollectionGateway);
        libraryViewModel.bindImportGateway(libraryImportGateway);
        libraryViewModel.bindDocumentGateway(libraryDocumentGateway);
        libraryViewModel.bindExclusionGateway(new LibraryExclusionGateway() {
            @Override
            public boolean restoreLibraryExclusion(String sourceKey) {
                return repository.restoreLibraryExclusion(sourceKey);
            }

            @Override
            public int restoreAllLibraryExclusions() {
                return repository.restoreAllLibraryExclusions();
            }
        });
        libraryRenderOwner = new LibraryRenderOwner(
                libraryStore,
                libraryViewModel,
                trackListRenderController,
                libraryGroupsRenderController,
                libraryPlaylistsRenderController,
                () -> permissionController != null && permissionController.hasAudioPermission(),
                status -> statusMessageController.setStatus(status)
        );
        libraryRenderOwner.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playbackFeatureBinding.readModel()
        );
        settingsViewModel.bindPreferenceGateway(applySettingsPreferenceUseCase::execute);
        settingsViewModel.bindStoreMirror(settingsStore::sync);
        SettingsRuntimeApplier settingsRuntimeApplier = new SettingsRuntimeApplier(
                () -> uiShellController.applyThemeSurface(),
                customBackgroundAccentController::refresh,
                () -> !playbackFeatureBinding.isConnected()
                        ? null
                        : new MainSettingsPlaybackServiceControls(playbackFeatureBinding.getConnection()),
                () -> lyricsViewModel == null ? null : new MainSettingsLyricsControls(lyricsViewModel),
                () -> new MainSettingsFloatingLyricsControls(
                        MainActivityBase.this,
                        () -> permissionController
                )
        );
        settingsViewModel.bindRuntimeEffectListener(settingsRuntimeApplier::apply);
        playlistMutationOwner = new PlaylistMutationOwner(
                libraryViewModel,
                navigationFeatureBinding.getRouteController(),
                () -> settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                libraryCollectionsOwner::load
        );
        playlistDialogController = new PlaylistDialogController(
                this,
                () -> settingsStore.languageMode(),
                () -> libraryStore.playlists(),
                playlistMutationOwner
        );
    }

    private void initializeNetworkOwners(StreamingSearchRenderController streamingSearchRenderController) {
        networkActionsViewModel.bindUseCases(networkActionUseCases);
        networkActionsViewModel.bindListener(new MainNetworkActionsListener(
                playbackFeatureBinding.getNowPlayingViewModel(),
                libraryImportOwner::replaceLibrary,
                navigationFeatureBinding::navigateToNetworkTabPage,
                libraryCollectionsOwner::load,
                status -> statusMessageController.setStatus(status)
        ));
        networkRequestController = new NetworkRequestController(
                networkActionsViewModel,
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status)
        );
        settingsContextProvider = new SettingsContextProvider(
                settingsStore,
                libraryStore,
                permissionController,
                playbackFeatureBinding.getConnection(),
                playbackFeatureBinding.getPlaybackViewModel(),
                lyricsViewModel,
                streamingGatewaySettingsStore,
                luoxueSourceStore,
                repository
        );
        settingsViewModel.bindContextLoader(settingsContextProvider);
        settingsViewModel.bindRouteState(navigationViewModel.getState());
        settingsViewModel.refreshSettingsContext();
        playbackFeatureBinding.bindStateSources(
                viewModel,
                lyricsViewModel,
                settingsViewModel,
                streamingFeatureBinding.viewModel(),
                homeDashboardViewModel,
                homeDashboardIntentHandler
        );
        DialogLanguageProvider dialogLanguageProvider =
                () -> settingsStore.languageMode();
        networkDialogController = new NetworkDialogController(this, dialogLanguageProvider, networkRequestController);
        confirmationDialogController = new ConfirmationDialogController(
                this,
                dialogLanguageProvider,
                new ConfirmationActions(
                        playHistoryActionController::clearPlayHistory,
                        playbackFeatureBinding.getQueueActionController()::clearQueue,
                        networkRequestController::deleteAllStreams,
                        networkRequestController::deleteTrack,
                        networkRequestController::deleteTracks,
                        networkRequestController::deleteRemoteSource
                )
        );
        initializeNetworkRendering(streamingSearchRenderController);
        streamingFeatureBinding.handleInitialIntent(getIntent());
        uiShellController.applyThemeSurface();
    }

    private void initializeNetworkRendering(StreamingSearchRenderController streamingSearchRenderController) {
        NetworkMenuEventController menuEvents = new NetworkMenuEventController(
                navigationFeatureBinding::navigateNetworkPage,
                navigationFeatureBinding::handleBack,
                () -> networkDialogController.showAddStream(),
                () -> networkDialogController.showImportM3u(),
                () -> networkDialogController.showAddWebDav(),
                () -> documentPickerController.openM3uFilePicker(),
                () -> libraryStore.streamTracks(),
                () -> libraryStore.streamTrackCount(),
                () -> libraryStore.webDavTracks(),
                () -> libraryStore.remoteSources(),
                sourceIds -> networkRequestController.syncAllWebDavSources(sourceIds),
                () -> confirmationDialogController.confirmDeleteAllStreams(),
                (tracks, index) -> playbackFeatureBinding.getPlaybackStartController().playTrackList(tracks, index),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status),
                networkMenuViewModel
        );
        NetworkSourcesEventController sourcesEvents = new NetworkSourcesEventController(
                navigationFeatureBinding.getRouteController(),
                networkRequestController,
                sourceId -> libraryStore.remoteSourceName(sourceId, settingsStore.languageMode()),
                sourceId -> libraryStore.webDavTracksForSource(sourceId),
                source -> networkDialogController.showEditWebDav(source),
                source -> confirmationDialogController.confirmDeleteRemoteSource(source),
                (tracks, index) -> playbackFeatureBinding.getPlaybackStartController().playTrackList(tracks, index),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status)
        );
        NetworkTrackListRenderController trackListRenderer = new NetworkTrackListRenderController(
                new NetworkTrackListOwner(
                        navigationFeatureBinding.getRouteController(),
                        sourcesEvents,
                        trackListStatePublisher
                )
        );
        networkRenderCoordinator = new NetworkRenderCoordinator(
                libraryStore,
                new NetworkMenuRenderController(menuEvents),
                trackListRenderer,
                new NetworkSourcesRenderController(networkSourcesViewModel, sourcesEvents),
                streamingSearchRenderController
        );
        networkRenderCoordinator.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                settingsViewModel.getState()
        );
    }

    private void initializeOnboardingAndStartup() {
        onboardingOwner = new OnboardingOwner(
                new OnboardingPermissionAccess(
                        () -> permissionController != null && permissionController.hasAudioPermission(),
                        () -> permissionController != null && permissionController.hasNotificationPermission(),
                        permissionController::requestNeededPermissions
                ),
                new OnboardingLibraryAccess(
                        libraryImportOwner::loadLibrary,
                        libraryImportOwner::cancelLibraryLoad
                ),
                navigationFeatureBinding::navigateToNetworkTabPage,
                documentPickerController::openPlaylistM3uFilePicker,
                new OnboardingCompletionStore(
                        () -> repository == null || !repository.loadOnboardingCompleted(),
                        () -> {
                            if (repository != null) {
                                repository.saveOnboardingCompleted(true);
                            }
                        }
                ),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                statusMessageController::setStatus,
                new HandlerOnboardingScheduler(mainHandler)
        );
        onboardingOwner.initialize();
        navigationFeatureBinding.bindRoot(
                activityViewModels,
                onboardingOwner,
                permissionController,
                playbackFeatureBinding.getNowPlayingEffectOwner(),
                playlistDialogController,
                playbackFeatureBinding.getQueueActionController(),
                documentPickerController,
                trackDownloadManager,
                playbackFeatureBinding.getConnection()
        );
        playbackFeatureBinding.bindService();
        if (!onboardingOwner.showOnboarding()) {
            permissionController.requestNeededPermissions();
            if (permissionController.hasAudioPermission()) {
                libraryImportOwner.loadLibrary(false);
            } else {
                libraryImportOwner.loadLibrary(true);
            }
        } else {
            libraryImportOwner.loadLibrary(true);
        }
        libraryCollectionsOwner.load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        playbackFeatureBinding.setAppVisible(true);
        // Stored throttles keep this lightweight; it only verifies/renews sessions that are due.
        streamingFeatureBinding.onResume();
    }

    @Override
    protected void onPause() {
        playbackFeatureBinding.setAppVisible(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releaseViewModelHostBindings();
        if (navigationFeatureBinding != null) {
            navigationFeatureBinding.release();
        }
        if (onboardingOwner != null) {
            onboardingOwner.release();
        }
        if (networkRenderCoordinator != null) {
            networkRenderCoordinator.release();
        }
        if (libraryRenderOwner != null) {
            libraryRenderOwner.release();
        }
        if (collectionsRenderController != null) {
            collectionsRenderController.release();
        }
        if (playbackFeatureBinding != null) {
            playbackFeatureBinding.release();
        }
        if (streamingFeatureBinding != null) {
            streamingFeatureBinding.release();
        }
        executors.shutdownNow();
        super.onDestroy();
    }

    /**
     * ViewModels survive configuration changes, while every callback assembled by this host points
     * at the current Activity or one of its activity-scoped owners. Clear those boundaries before
     * releasing the host so asynchronous work cannot publish into a destroyed Activity.
     */
    private void releaseViewModelHostBindings() {
        if (searchViewModel != null) {
            searchViewModel.bindStateSources(null, null, null);
        }
        if (settingsViewModel != null) {
            settingsViewModel.bindRouteState(null);
            settingsViewModel.bindContextLoader(null);
            settingsViewModel.bindEffectListener(null);
            settingsViewModel.bindRuntimeEffectListener(null);
            settingsViewModel.bindPreferenceGateway(null);
            settingsViewModel.bindStoreMirror(null);
        }
        if (networkActionsViewModel != null) {
            networkActionsViewModel.bindListener(null);
            networkActionsViewModel.bindUseCases(null);
        }
        if (libraryViewModel != null) {
            libraryViewModel.bindGateway(null);
            libraryViewModel.bindPlaylistTrackLoader(null);
            libraryViewModel.bindFavoriteWriter(null);
            libraryViewModel.bindFavoriteIdsProvider(null);
            libraryViewModel.bindCollectionGateway(null);
            libraryViewModel.bindImportGateway(null);
            libraryViewModel.bindDocumentGateway(null);
            libraryViewModel.bindPlaylistActionGateway(null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (streamingFeatureBinding != null) {
            streamingFeatureBinding.handleNewIntent(intent);
        }
    }

    private String selectedPlaylistName() {
        return libraryStore.selectedPlaylistName(navigationFeatureBinding.selectedPlaylistId());
    }


}
