package app.yukine;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.activity.ComponentActivity;

import javax.inject.Inject;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.ui.EchoTheme;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.streaming.LuoxueTrackMetadataResolver;
import app.yukine.streaming.cache.StreamingCacheRepository;
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
    private LibraryFeatureBinding libraryFeatureBinding;
      private SettingsViewModel settingsViewModel;
      private NetworkMenuViewModel networkMenuViewModel;
      private StatusMessageViewModel statusMessageViewModel;
      private NetworkSourcesViewModel networkSourcesViewModel;
    private StreamingFeatureBinding streamingFeatureBinding;
    private NavigationFeatureBinding navigationFeatureBinding;
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
    private NetworkActionsViewModel networkActionsViewModel;
    private NetworkRequestController networkRequestController;
    private LyricsViewModel lyricsViewModel;

    private NetworkDialogController networkDialogController;
    private ConfirmationDialogController confirmationDialogController;
    private OnboardingOwner onboardingOwner;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViewModels(createActivityViewModels());
        initializeRouteStoresAndStatus();
        initializeStreamingFeatureBinding();
        initializeLibraryFeatureBinding();
        initializePlaybackFeatureBinding();
        initializeDownloadRequests();
        initializePlatformControllers();
        initializePlaybackControllers();
        streamingFeatureBinding.bindDialogs(
                libraryFeatureBinding.collectionsOwner(),
                libraryFeatureBinding.importOwner(),
                luoxueSourceImportDialogController
        );
        StreamingSearchRenderController streamingSearchRenderController = initializeStoresAndDataGateways();
        initializeSettingsEffects();
        initializeNetworkOwners(streamingSearchRenderController);
        initializeNowPlayingEffectOwner();
        initializeOnboardingAndStartup();
    }

    protected abstract MainActivityViewModels createActivityViewModels();

    private void initializeLibraryFeatureBinding() {
        libraryFeatureBinding = new LibraryFeatureBinding(
                this,
                activityViewModels,
                viewModel,
                navigationViewModel,
                settingsViewModel,
                settingsStore,
                navigationFeatureBinding,
                statusMessageController,
                repository,
                librarySearchUseCase,
                toggleFavoriteUseCase,
                loadPlaylistTracksUseCase,
                libraryCollectionGateway,
                libraryImportGateway,
                libraryDocumentGateway,
                libraryPlaylistActionGateway,
                mainHandler,
                artistInfoRepository
        );
    }

    private void initializeViewModels(MainActivityViewModels viewModels) {
        activityViewModels = viewModels;
        viewModel = viewModels.getMainActivityViewModel();
        navigationViewModel = viewModels.getNavigationViewModel();
        homeDashboardViewModel = viewModels.getHomeDashboardViewModel();
        downloadsViewModel = viewModels.getDownloadsViewModel();
        lyricsViewModel = viewModels.getLyricsViewModel();
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
                libraryFeatureBinding.collectionsOwner(),
                streamingFeatureBinding.viewModel(),
                libraryFeatureBinding.viewModel(),
                libraryFeatureBinding::sourceCandidatesFor,
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
                libraryFeatureBinding::loadLibrary,
                () -> {
                    if (onboardingOwner != null) {
                        onboardingOwner.onPermissionsChanged();
                    }
                }
        );
        permissionController = new MainPermissionController(this, permissionResultOwner);
        libraryFeatureBinding.bindPlatform(
                permissionController,
                playbackFeatureBinding,
                canScan -> {
                    if (onboardingOwner != null) {
                        onboardingOwner.onLibraryScanResult(canScan);
                    }
                }
        );
        downloadDirectoryOwner = new DownloadDirectoryOwner(
                trackDownloadManager,
                downloadsViewModel,
                statusMessageController::showFeedback
        );
        documentPickerController = new DocumentPickerController(this, new DocumentPickerActions(
                libraryFeatureBinding::importAudioUris,
                libraryFeatureBinding::importAudioFolder,
                downloadDirectoryOwner::setCustomDirectory,
                libraryFeatureBinding::importStreamM3u,
                libraryFeatureBinding::exportPlaylist,
                libraryFeatureBinding::importPlaylistM3u,
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
        libraryFileDeleteLauncher = new LibraryFileDeleteLauncher(
                this,
                libraryDeletionUseCase,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                libraryFeatureBinding.deletionCompletionOwner()
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
                libraryFeatureBinding.playlistDialogController()::showAddToPlaylist,
                trackShareLauncher::share,
                downloadRequestController::downloadTrack
        );
    }

    private void initializePlaybackControllers() {
        streamingFeatureBinding.bindPlayback(
                playbackFeatureBinding,
                navigationFeatureBinding,
                libraryFeatureBinding.store(),
                lyricsViewModel,
                () -> confirmationDialogController.confirmClearQueue()
        );
    }

    private void initializeSettingsEffects() {
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
                        () -> libraryFeatureBinding.loadLibrary(false),
                        documentPickerController::openAudioFilePicker,
                        documentPickerController::openAudioFolderPicker,
                        luoxueSourceImportDialogController::showSourceManager,
                        luoxueSourceImportDialogController::showImportDialog,
                        libraryFeatureBinding::restoreHidden,
                        libraryFeatureBinding::restoreAllHidden
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
        settingsStore.load(loadSettingsPreferencesUseCase.execute());
        if (EchoTheme.ACCENT_DYNAMIC_BACKGROUND.equals(settingsStore.accentMode())) {
            customBackgroundAccentController.refresh(settingsStore.pageBackgrounds());
        }
        StreamingSearchRenderController streamingSearchRenderController = libraryFeatureBinding.bindUi(
                playbackFeatureBinding,
                streamingFeatureBinding,
                documentPickerController,
                libraryFileDeleteLauncher,
                downloadRequestController,
                permissionController,
                luoxueSourceImportDialogController,
                track -> networkDialogController.showEditStream(track),
                () -> confirmationDialogController.confirmClearPlayHistory()
        );
        LoadedLyricsSettings loadedLyricsSettings = loadLyricsSettingsUseCase.execute();
        lyricsViewModel.configure(
                lyricsLoader,
                loadedLyricsSettings.onlineLyricsEnabled,
                loadedLyricsSettings.lyricsOffsetMs
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
        return streamingSearchRenderController;
    }

    private void initializeNetworkOwners(StreamingSearchRenderController streamingSearchRenderController) {
        networkActionsViewModel.bindUseCases(networkActionUseCases);
        networkActionsViewModel.bindListener(new MainNetworkActionsListener(
                playbackFeatureBinding.getNowPlayingViewModel(),
                libraryFeatureBinding::replaceLibrary,
                navigationFeatureBinding::navigateToNetworkTabPage,
                libraryFeatureBinding::loadCollections,
                status -> statusMessageController.setStatus(status)
        ));
        networkRequestController = new NetworkRequestController(
                networkActionsViewModel,
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status)
        );
        settingsContextProvider = new SettingsContextProvider(
                settingsStore,
                libraryFeatureBinding.store(),
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
                libraryFeatureBinding.homeDashboardIntentHandler()
        );
        DialogLanguageProvider dialogLanguageProvider =
                () -> settingsStore.languageMode();
        networkDialogController = new NetworkDialogController(this, dialogLanguageProvider, networkRequestController);
        confirmationDialogController = new ConfirmationDialogController(
                this,
                dialogLanguageProvider,
                new ConfirmationActions(
                        libraryFeatureBinding::clearPlayHistory,
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
                () -> libraryFeatureBinding.store().streamTracks(),
                () -> libraryFeatureBinding.store().streamTrackCount(),
                () -> libraryFeatureBinding.store().webDavTracks(),
                () -> libraryFeatureBinding.store().remoteSources(),
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
                sourceId -> libraryFeatureBinding.store().remoteSourceName(sourceId, settingsStore.languageMode()),
                sourceId -> libraryFeatureBinding.store().webDavTracksForSource(sourceId),
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
                        libraryFeatureBinding.trackListStatePublisher()
                )
        );
        networkRenderCoordinator = new NetworkRenderCoordinator(
                libraryFeatureBinding.store(),
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
                        libraryFeatureBinding::loadLibrary,
                        libraryFeatureBinding::cancelLibraryLoad
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
                libraryFeatureBinding.playlistDialogController(),
                playbackFeatureBinding.getQueueActionController(),
                documentPickerController,
                trackDownloadManager,
                playbackFeatureBinding.getConnection()
        );
        playbackFeatureBinding.bindService();
        if (!onboardingOwner.showOnboarding()) {
            permissionController.requestNeededPermissions();
            if (permissionController.hasAudioPermission()) {
                libraryFeatureBinding.loadLibrary(false);
            } else {
                libraryFeatureBinding.loadLibrary(true);
            }
        } else {
            libraryFeatureBinding.loadLibrary(true);
        }
        libraryFeatureBinding.loadCollections();
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
        if (libraryFeatureBinding != null) {
            libraryFeatureBinding.release();
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (streamingFeatureBinding != null) {
            streamingFeatureBinding.handleNewIntent(intent);
        }
    }

}
