package app.yukine;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.activity.ComponentActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.ui.LibraryUiLabels;
import app.yukine.ui.EchoTheme;
import app.yukine.model.Playlist;
import app.yukine.model.Track;
import app.yukine.playback.PlaybackStateSnapshot;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.streaming.LuoxueTrackMetadataResolver;
import app.yukine.streaming.StreamingAudioQuality;
import app.yukine.streaming.StreamingPlaylist;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.streaming.StreamingTrack;
import app.yukine.streaming.cache.StreamingCacheRepository;
import app.yukine.ui.LibraryGroupActions;
import app.yukine.ui.LibraryGroupUiState;
import app.yukine.ui.TrackRowActions;
import app.yukine.ui.TrackRowUiState;

public abstract class MainActivityBase extends ComponentActivity {
    private static final String TAG = "MainActivity";
    private static final String NETWORK_STREAMING = MainRoutes.NETWORK_STREAMING;
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
    private StreamingViewModel streamingViewModel;
    private StreamingRecommendationViewModel streamingRecommendationViewModel;
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
    private StreamingPlaybackQualityPolicy streamingPlaybackQualityPolicy;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsContextProvider settingsContextProvider;
    private SettingsEffectOwner settingsEffectOwner;
    private HiddenLibraryRestoreOwner hiddenLibraryRestoreOwner;
    private StreamingProviderSettingsOwner streamingProviderSettingsOwner;
    private TrackListRenderController trackListRenderController;
    private TrackListStatePublisher trackListStatePublisher;
    private StreamingAuthCallbackController streamingAuthCallbackController;
    private StreamingSearchActionHandler streamingSearchActionHandler;
    private StreamingPlaybackController streamingPlaybackController;
    private StreamingPlaylistController streamingPlaylistController;
    private StreamingPlaylistDialogController streamingPlaylistDialogController;
    private StreamingPlaylistImportDialogController streamingPlaylistImportDialogController;
    private StreamingManualCookieDialogController streamingManualCookieDialogController;
    private StreamingManualCookieController streamingManualCookieController;
    private HeartbeatRecommendationController heartbeatRecommendationController;
    private RecommendationActionCallbacks recommendationActionCallbacks;
    private HeartbeatRecommendationSeedBinder heartbeatSeedBinder;
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
        initializeLibraryStateOwners();
        MainActivityStreamingActionGateway streamingActionGateway = createStreamingActionGateway();
        initializeStreamingPlaybackCoordinator();
        initializePlaybackFeatureBinding();
        initializeDownloadRequests();
        initializeStreamingStartup(streamingActionGateway);
        initializePlatformControllers();
        initializeNavigationRendering();
        initializePlaybackControllers();
        initializeSettingsEffects();
        initializeStreamingOwners(streamingActionGateway);
        StreamingSearchRenderController streamingSearchRenderController =
                initializeStoresAndDataGateways(streamingActionGateway);
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
        streamingViewModel = viewModels.getStreamingViewModel();
        streamingRecommendationViewModel = viewModels.getStreamingRecommendationViewModel();
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

    private MainActivityStreamingActionGateway createStreamingActionGateway() {
        return new MainStreamingActionGateway(
                streamingPlaybackQualityPolicy::adaptive,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                launch -> StreamingAuthLauncher.INSTANCE.launch(MainActivityBase.this, launch),
                (tracks, index) -> playbackFeatureBinding.getPlaybackStartController().playTrackList(tracks, index),
                provider -> streamingPlaylistController.onStreamingLoginSuccess(provider),
                provider -> streamingViewModel.selectStreamingProvider(provider),
                () -> {
                    if (streamingManualCookieController != null) {
                        streamingManualCookieController.showStreamingCookieDialog();
                    }
                }
        );
    }

    private void initializeStreamingPlaybackCoordinator() {
        streamingViewModel.bindStreamingPlaybackCoordinator(
                resolveStreamingPlaybackUseCase,
                streamingPlaybackTaskScheduler
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
                streamingPlaybackQualityPolicy,
                statusMessageController
        );
        playbackFeatureBinding.bindConnection(
                lyricsViewModel,
                libraryCollectionsOwner,
                streamingViewModel,
                libraryViewModel,
                track -> libraryStore == null
                        ? Collections.<Track>emptyList()
                        : libraryStore.sourceCandidatesFor(track),
                snapshot -> streamingPlaybackController.preResolveNextStreamingTrack(snapshot),
                snapshot -> streamingPlaybackController.recoverStreamingBuffering(snapshot),
                this::resolveCurrentStreamingQueueTrackIfNeeded
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
                (request, quality, callback) -> streamingViewModel.resolveStreamingTrackForPlayback(
                        request.getProvider(),
                        request.getProviderTrackId(),
                        request.getMetadata(),
                        quality,
                        callback::onResolved
                ),
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

    private void initializeStreamingStartup(MainActivityStreamingActionGateway streamingActionGateway) {
        streamingProviderSettingsOwner = new StreamingProviderSettingsOwner(
                streamingGatewaySettingsStore,
                streamingViewModel,
                streamingRecommendationViewModel,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                statusMessageController::setStatus
        );
        streamingProviderSettingsOwner.configureAndRefresh();
        streamingAuthCallbackController = new StreamingAuthCallbackController(
                streamingViewModel,
                streamingActionGateway
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
        streamingPlaybackQualityPolicy = new StreamingPlaybackQualityPolicy(this, settingsStore);
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
                () -> navigationFeatureBinding.navigateToNetworkTabPage(NETWORK_STREAMING)
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
                () -> {
                    executors.io(() -> {
                        streamingCacheRepository.clearSearchAndPlaybackForProviderBlocking(StreamingProviderName.LUOXUE);
                        mainHandler.post(() -> {
                            playbackFeatureBinding.getNowPlayingViewModel()
                                    .bindLuoxueTrackMetadataResolver(luoxueTrackMetadataResolver);
                            streamingProviderSettingsOwner.refresh();
                        });
                    });
                }
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
        heartbeatSeedBinder = new HeartbeatRecommendationSeedBinder(
                playbackFeatureBinding::snapshot,
                playbackFeatureBinding::queueSnapshot,
                playbackFeatureBinding::snapshot,
                () -> playbackFeatureBinding.getPlaybackViewModel().getPlayback().getValue() == null
                        ? Collections.emptyList()
                        : playbackFeatureBinding.getPlaybackViewModel().getPlayback().getValue().getQueue(),
                this::heartbeatLibraryContextTracks
        );
        recommendationActionCallbacks = new MainRecommendationActionCallbacks(
                status -> statusMessageController.setStatus(status),
                presentation -> playbackFeatureBinding.getPlaybackStartController().playRecommendation(presentation),
                provider -> heartbeatSeedBinder == null
                        ? new HeartbeatRecommendationSeedRequest()
                        : heartbeatSeedBinder.request(provider),
                presentation -> playbackFeatureBinding.getPlaybackStartController().playHeartbeatRecommendation(presentation),
                this::logHeartbeatSeedMiss
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
                () -> runRecommendationAction(new RecommendationAction.PlayDaily(StreamingProviderName.NETEASE)),
                () -> runRecommendationAction(new RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE))
        );
        heartbeatRecommendationController = new HeartbeatRecommendationController(
                streamingRecommendationViewModel,
                () -> settingsStore.languageMode(),
                new MainHeartbeatRecommendationListener(
                        playbackFeatureBinding::isConnected,
                        provider -> heartbeatSeedBinder == null
                                ? new HeartbeatRecommendationSeedRequest()
                                : heartbeatSeedBinder.request(provider),
                        () -> streamingRecommendationViewModel.stopHeartbeatRecommendationMode(),
                        presentation -> playbackFeatureBinding.getNowPlayingViewModel().appendToQueue(presentation.getTracks()),
                        presentation -> playbackFeatureBinding.getPlaybackStartController().playHeartbeatRecommendation(presentation),
                        this::logHeartbeatSeedMiss,
                        status -> statusMessageController.setStatus(status)
                )
        );
        streamingPlaybackController = new StreamingPlaybackController(
                streamingViewModel,
                playbackFeatureBinding.getNowPlayingViewModel(),
                new MainStreamingPlaybackListener(
                        () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        streamingPlaybackQualityPolicy::adaptive,
                        streamingPlaybackQualityPolicy::selected,
                        () -> settingsStore != null && settingsStore.refuseAutomaticQualityDowngrade(),
                        new StreamingQueueReadSource() {
                            @Override
                            public List<Track> queueSnapshot() {
                                return playbackFeatureBinding.queueSnapshot();
                            }

                            @Override
                            public int queueSize() {
                                return playbackFeatureBinding.getConnection().queueSize();
                            }

                            @Override
                            public Track queueTrackAt(int index) {
                                return playbackFeatureBinding.getConnection().queueTrackAt(index);
                            }
                        },
                        snapshot -> heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot),
                        playbackFeatureBinding::applyActionResult,
                        status -> statusMessageController.setStatus(status)
                )
        );
        playbackFeatureBinding.bindActions(
                streamingPlaybackController::resolveAndPlayStreamingTrack,
                this::resolveCurrentStreamingQueueTrackIfNeeded,
                () -> libraryStore == null ? Collections.emptyList() : libraryStore.visibleTracks(),
                streamingRecommendationViewModel::stopHeartbeatRecommendationMode,
                () -> streamingViewModel.prepareStreamingPlaybackStatusText(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        null
                ).getResolving(),
                () -> navigationFeatureBinding.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                () -> confirmationDialogController.confirmClearQueue(),
                lyricsViewModel,
                track -> streamingViewModel.streamingProviderTrackIdFor(
                        track,
                        app.yukine.streaming.StreamingProviderName.NETEASE
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
                streamingProviderSettingsOwner::applyEndpoint
        );
        settingsViewModel.bindEffectListener(settingsEffectOwner);
    }

    private void initializeStreamingOwners(MainActivityStreamingActionGateway streamingActionGateway) {
        final StreamingPlaylistController[] streamingPlaylistControllerRef = new StreamingPlaylistController[1];
        streamingPlaylistDialogController = new StreamingPlaylistDialogController(
                this,
                streamingViewModel,
                new StreamingPlaylistDialogController.LanguageProvider() {
                    @Override
                    public String languageMode() {
                        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
                    }

                    @Override
                    public String text(String key) {
                        return AppLanguage.text(languageMode(), key);
                    }
                },
                new MainStreamingPlaylistDialogListener(
                        status -> statusMessageController.setStatus(status),
                        (provider, playlistName, tracks) -> {
                            if (streamingPlaylistControllerRef[0] != null) {
                                streamingPlaylistControllerRef[0].runStreamingPlaylistImport(provider, playlistName, tracks);
                            }
                        },
                        (provider, playlists) -> {
                            if (streamingPlaylistControllerRef[0] != null) {
                                streamingPlaylistControllerRef[0].importSelectedAccountPlaylists(provider, playlists);
                            }
                        },
                        provider -> {
                            if (streamingPlaylistControllerRef[0] != null) {
                                streamingPlaylistControllerRef[0].importStreamingLikedTracks(provider);
                            }
                        }
                )
        );
        streamingPlaylistController = new StreamingPlaylistController(
                streamingViewModel,
                () -> settingsStore.languageMode(),
                new MainStreamingPlaylistListener(
                        navigationFeatureBinding::selectedPlaylistId,
                        playlistId -> navigationFeatureBinding.getRouteController().setSelectedPlaylistId(playlistId),
                        () -> libraryCollectionsOwner.load(),
                        () -> libraryImportOwner.loadLibrary(true),
                        () -> MainActivityBase.this.selectedPlaylistName(),
                        () -> libraryStore.selectedPlaylistTracks(),
                        () -> libraryStore.favoriteTracks(),
                        () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                        (playlistName, tracks) ->
                                streamingPlaylistDialogController.showStreamingProviderPicker(playlistName, tracks),
                        () -> navigationFeatureBinding.navigateToNetworkTabPage(NETWORK_STREAMING),
                        message -> streamingPlaylistDialogController.showStreamingPlaylistLoadedDialog(message),
                        (provider, playlists) ->
                                streamingPlaylistDialogController.showAccountPlaylistImportPicker(provider, playlists),
                        status -> statusMessageController.setStatus(status)
                )
        );
        streamingPlaylistControllerRef[0] = streamingPlaylistController;
        streamingPlaylistImportDialogController = new StreamingPlaylistImportDialogController(
                this,
                streamingViewModel,
                new StreamingPlaylistImportDialogController.LanguageProvider() {
                    @Override
                    public String languageMode() {
                        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
                    }

                    @Override
                    public String text(String key) {
                        return AppLanguage.text(languageMode(), key);
                    }
                },
                new MainStreamingPlaylistImportDialogListener(
                        () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                        () -> luoxueSourceImportDialogController.showImportDialog(),
                        linkOrId -> streamingPlaylistController.importStreamingPlaylistFromLink(linkOrId)
                )
        );
        streamingManualCookieDialogController = new StreamingManualCookieDialogController(
                this,
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                ),
                (provider, cookieHeader) -> streamingManualCookieController.saveStreamingCookie(provider, cookieHeader)
        );
        streamingManualCookieController = new StreamingManualCookieController(
                streamingViewModel,
                () -> settingsStore.languageMode(),
                new MainStreamingManualCookieListener(
                        () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                        dialogState -> streamingManualCookieDialogController.show(dialogState),
                        provider -> streamingPlaylistController.onStreamingLoginSuccess(provider),
                        status -> statusMessageController.setStatus(status)
                )
        );
    }

    private StreamingSearchRenderController initializeStoresAndDataGateways(
            MainActivityStreamingActionGateway streamingActionGateway
    ) {
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
        streamingSearchActionHandler = new DefaultStreamingSearchActionHandler(streamingViewModel, streamingActionGateway);
        unifiedSearchOwner = new UnifiedSearchOwner(
                navigationFeatureBinding.getRouteController(),
                searchViewModel,
                streamingViewModel,
                libraryViewModel,
                libraryStore,
                streamingSearchActionHandler,
                settingsStore,
                streamingPlaybackQualityPolicy,
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
        StreamingSearchRenderController streamingSearchRenderController = new StreamingSearchRenderController(
                () -> settingsStore.languageMode(),
                new MainStreamingSearchRenderListener(
                        navigationFeatureBinding::handleBack,
                        streamingSearchActionHandler,
                        () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                        (provider, providerPlaylistId) ->
                                streamingPlaylistController.importStreamingPlaylistFromProviderRef(provider, providerPlaylistId),
                        provider -> streamingPlaylistController.showAccountPlaylistSyncPicker(provider),
                        provider -> streamingPlaylistController.importStreamingLikedTracks(provider),
                        action -> runRecommendationAction(action),
                        () -> streamingPlaylistImportDialogController.showImportDialog(),
                        () -> luoxueSourceImportDialogController.showSourceManager(),
                        () -> streamingManualCookieController.showStreamingCookieDialog(),
                        (labels, actions) -> streamingViewModel.updateStreamingSearchChrome(labels, actions)
                ));
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
                () -> streamingPlaylistController.importSelectedPlaylistToStreaming(),
                () -> streamingPlaylistController.importFavoritesToStreaming(),
                () -> streamingPlaylistDialogController.showImportStreamingFavoritesProviderPicker(),
                () -> streamingPlaylistController.syncSelectedPlaylistFromStreaming(),
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
        streamingViewModel.bindStreamingLocalPlaylistOperations(streamingLocalPlaylistOperations);
        streamingViewModel.bindStreamingTrackMatchStore(streamingTrackMatchUseCase);
        streamingRecommendationViewModel.bindStreamingTrackMatchStore(streamingTrackMatchUseCase);
        if (heartbeatSeedBinder != null) {
            heartbeatSeedBinder.bind(streamingTrackMatchUseCase);
        }
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
                streamingViewModel,
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
        streamingAuthCallbackController.handleInitialIntent(getIntent());
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
        streamingViewModel.maintainStreamingAuthSessions();
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
        if (streamingPlaybackTaskScheduler != null) {
            streamingPlaybackTaskScheduler.shutdownNow();
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
        if (streamingViewModel != null) {
            streamingViewModel.bindStreamingPlaybackCoordinator(null, null);
            streamingViewModel.bindStreamingLocalPlaylistOperations(null);
            streamingViewModel.bindStreamingTrackMatchStore(null);
        }
        if (streamingRecommendationViewModel != null) {
            streamingRecommendationViewModel.bindStreamingTrackMatchStore(null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (streamingAuthCallbackController != null) {
            streamingAuthCallbackController.handleNewIntent(intent);
        }
    }

    private void runRecommendationAction(RecommendationAction action) {
        if (recommendationActionCallbacks == null) {
            return;
        }
        streamingRecommendationViewModel.onAction(
                action,
                settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                recommendationActionCallbacks
        );
    }

    private String selectedPlaylistName() {
        return libraryStore.selectedPlaylistName(navigationFeatureBinding.selectedPlaylistId());
    }

    private List<Track> heartbeatLibraryContextTracks() {
        if (libraryStore == null) {
            return Collections.emptyList();
        }
        ArrayList<Track> selectedPlaylistTracks = libraryStore.selectedPlaylistTracks();
        if (selectedPlaylistTracks != null && !selectedPlaylistTracks.isEmpty()) {
            return selectedPlaylistTracks;
        }
        return libraryStore.visibleTracks();
    }

    private void logHeartbeatSeedMiss(HeartbeatRecommendationSeedRequest request) {
        if (request == null || request.getSeedMissingMessage() == null || request.getSeedMissingMessage().isEmpty()) {
            return;
        }
        Log.w(TAG, request.getSeedMissingMessage());
    }

    private boolean resolveCurrentStreamingQueueTrackIfNeeded() {
        if (!playbackFeatureBinding.isConnected()) {
            return false;
        }
        PlaybackStateSnapshot snapshot = playbackFeatureBinding.snapshot();
        StreamingQueueResolveTarget target = streamingViewModel.prepareCurrentStreamingQueueResolveTarget(
                snapshot,
                playbackFeatureBinding.queueSnapshot()
        );
        Track currentTrack = snapshot == null ? null : snapshot.currentTrack;
        return target != null
                && currentTrack != null
                && streamingPlaybackController.resolveAndResumeCurrentStreamingTrack(
                        target.getTracks(),
                        target.getIndex(),
                        currentTrack.id,
                        snapshot.positionMs
                );
    }

}
