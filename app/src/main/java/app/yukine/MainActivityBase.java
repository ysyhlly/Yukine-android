package app.yukine;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

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
    private static final String TAB_HOME = MainRoutes.TAB_HOME;
    private static final String TAB_QUEUE = MainRoutes.TAB_QUEUE;
    private static final String NETWORK_HOME = MainRoutes.NETWORK_HOME;
    private static final String NETWORK_STREAMING = MainRoutes.NETWORK_STREAMING;
    private static final String SETTINGS_HOME = MainRoutes.SETTINGS_HOME;
    private static final float[] EMPTY_REALTIME_BANDS = new float[0];
    private static final String LIBRARY_SONGS = LibraryGrouping.SONGS;
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
    @Inject MainHeartbeatRecommendationListenerFactory heartbeatRecommendationListenerFactory;
    @Inject MainRecommendationActionCallbacksFactory recommendationActionCallbacksFactory;
    @Inject MainStreamingPlaylistDialogListenerFactory streamingPlaylistDialogListenerFactory;
    @Inject MainStreamingPlaylistListenerFactory streamingPlaylistListenerFactory;
    @Inject MainStreamingPlaylistImportDialogListenerFactory streamingPlaylistImportDialogListenerFactory;
    @Inject MainStreamingManualCookieListenerFactory streamingManualCookieListenerFactory;
    @Inject MainStreamingActionGatewayFactory streamingActionGatewayFactory;
    @Inject MainStreamingSearchActionHandlerFactory streamingSearchActionHandlerFactory;
    @Inject MainStreamingSearchRenderListenerFactory streamingSearchRenderListenerFactory;
    @Inject MainTrackListRenderListenerFactory trackListRenderListenerFactory;
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

    private MainActivityViewModel viewModel;
    private NavigationViewModel navigationViewModel;
    private PlaybackViewModel playbackViewModel;
    private NowPlayingViewModel nowPlayingViewModel;
    private HomeDashboardViewModel homeDashboardViewModel;
    private app.yukine.queue.QueueViewModel queueViewModel;
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
    private MainRouteController routeController;
    private MainNavigationIntentOwner navigationIntentOwner;
    private MainLibraryStore libraryStore;
    @Inject MainSettingsStore settingsStore;
    @Inject MainNowPlayingGatewayFactory nowPlayingGatewayFactory;
    @Inject MainNowPlayingPlaybackGatewayFactory nowPlayingPlaybackGatewayFactory;
    @Inject NowPlayingPlaybackServiceStarter nowPlayingPlaybackServiceStarter;
    @Inject PlaybackServiceCommandQueue playbackServiceCommandQueue;
    @Inject MainPlaybackActionListenerFactory playbackActionListenerFactory;
    @Inject MainQueueActionListenerFactory queueActionListenerFactory;
    @Inject MainStreamingPlaybackListenerFactory streamingPlaybackListenerFactory;
    @Inject MainPlaybackStartListenerFactory playbackStartListenerFactory;
    @Inject MainPlaybackStateEventListenerFactory playbackStateEventListenerFactory;
    @Inject MainPlaybackServiceHostFactory playbackServiceHostFactory;
    @Inject MainLibraryStoreFactory libraryStoreFactory;
    @Inject MainPlayHistoryActionControllerFactory playHistoryActionControllerFactory;
    @Inject MainNetworkActionsListenerFactory networkActionsListenerFactory;
    @Inject MainLibraryGatewayFactory libraryGatewayFactory;
    @Inject LibraryDeletionUseCase libraryDeletionUseCase;
    @Inject ArtistInfoRepository artistInfoRepository;
    @Inject MainLibraryGroupsRenderListenerFactory libraryGroupsRenderListenerFactory;
    @Inject MainCollectionsRenderListenerFactory collectionsRenderListenerFactory;
    private StatusMessageController statusMessageController;
    @Inject MainSettingsRuntimeApplierFactory settingsRuntimeApplierFactory;
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
    private PlaybackServiceConnectionController playbackServiceConnectionController;
    private StreamingPlaybackQualityPolicy streamingPlaybackQualityPolicy;
    private NowPlayingSourceSwitchOwner nowPlayingSourceSwitchOwner;
    private NowPlayingEffectOwner nowPlayingEffectOwner;
    private PlaybackStateEventController playbackStateEventController;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsContextProvider settingsContextProvider;
    private SettingsEffectOwner settingsEffectOwner;
    private HiddenLibraryRestoreOwner hiddenLibraryRestoreOwner;
    private StreamingProviderSettingsOwner streamingProviderSettingsOwner;
    private TrackListRenderController trackListRenderController;
    private TrackListStatePublisher trackListStatePublisher;
    private QueueActionController queueActionController;
    private PlaybackActionController playbackActionController;
    private PlaybackStartController playbackStartController;
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
    private PlaybackServiceHostPort playbackService;
    private MainPlaybackStartListener playbackStartListener;
    private UnifiedSearchOwner unifiedSearchOwner;
    private app.yukine.navigation.EchoNavHostState navHostState;
    private boolean navHostInstalled;

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
        MainActivityStreamingActionGateway streamingActionGateway = createStreamingActionGateway();
        initializeStreamingPlaybackCoordinator();
        initializeNowPlayingGateways();
        initializeDownloadRequests();
        initializeStreamingStartup(streamingActionGateway);
        initializePlatformControllers();
        initializePlaybackLifecycleControllers();
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

    private void initializeViewModels(MainActivityViewModels viewModels) {
        viewModel = viewModels.getMainActivityViewModel();
        navigationViewModel = viewModels.getNavigationViewModel();
        playbackViewModel = viewModels.getPlaybackViewModel();
        streamingViewModel = viewModels.getStreamingViewModel();
        streamingRecommendationViewModel = viewModels.getStreamingRecommendationViewModel();
        homeDashboardViewModel = viewModels.getHomeDashboardViewModel();
        nowPlayingViewModel = viewModels.getNowPlayingViewModel();
        queueViewModel = viewModels.getQueueViewModel();
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
        return streamingActionGatewayFactory.create(
                streamingPlaybackQualityPolicy::adaptive,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                launch -> StreamingAuthLauncher.INSTANCE.launch(MainActivityBase.this, launch),
                (tracks, index) -> playTrackListFromHost(tracks, index),
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

    private void initializeNowPlayingGateways() {
        nowPlayingViewModel.bindGateway(nowPlayingGatewayFactory.create(
                () -> playbackActionController,
                () -> playbackViewModel == null ? null : playbackViewModel.getPlaybackSnapshot().getValue(),
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                positionMs -> nowPlayingViewModel.seekTo(positionMs),
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                )
        ));
        nowPlayingViewModel.bindPlaybackGateway(nowPlayingPlaybackGatewayFactory.create(
                () -> playbackServiceConnectionController,
                () -> playbackServiceConnectionController
        ));
        nowPlayingViewModel.bindLuoxueTrackMetadataResolver(luoxueTrackMetadataResolver);
        nowPlayingViewModel.bindSourceCandidatesProvider(
                track -> libraryStore == null
                        ? Collections.<Track>emptyList()
                        : libraryStore.sourceCandidatesFor(track)
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
        libraryViewModel.bindGateway(libraryGatewayFactory.create(
                (tracks, index) -> MainActivityBase.this.playTrackListFromHost(tracks, index),
                () -> settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                (trackId, favorite) -> viewModel.setFavorite(trackId, favorite),
                this::loadCollections,
                track -> MainActivityBase.this.playlistDialogController.showAddToPlaylist(track),
                routeController,
                unifiedSearchOwner::applyCurrentSearch,
                () -> documentPickerController.openAudioFilePicker(),
                allowCachedFirst -> libraryImportOwner.loadLibrary(allowCachedFirst),
                tracks -> libraryFileDeleteLauncher.request(tracks, selectedPlaylistId()),
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
        routeController = new MainRouteController(navigationViewModel);
        navigationIntentOwner = new MainNavigationIntentOwner(
                routeController,
                settingsViewModel::scrollToTopOnNextRender
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
                routeController,
                () -> permissionController != null && permissionController.hasAudioPermission(),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                this::loadCollections,
                canScan -> {
                    if (onboardingOwner != null) {
                        onboardingOwner.onLibraryScanResult(canScan);
                    }
                },
                () -> navigateToNetworkTabPage(NETWORK_STREAMING)
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
                nowPlayingViewModel::removeQueueTracks,
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
                            if (nowPlayingViewModel != null) {
                                nowPlayingViewModel.bindLuoxueTrackMetadataResolver(luoxueTrackMetadataResolver);
                            }
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

    private void initializePlaybackLifecycleControllers() {
        playbackStateEventController = new PlaybackStateEventController(
                mainHandler,
                playbackStateEventListenerFactory.create(
                        () -> lyricsViewModel == null ? -1L : lyricsViewModel.trackId(),
                        (playbackSpeed, appVolume) -> {
                            settingsStore.setPlaybackSpeed(playbackSpeed);
                            settingsStore.setAppVolume(appVolume);
                        },
                        this::loadLyrics,
                        this::loadCollections,
                        snapshot -> streamingPlaybackController.preResolveNextStreamingTrack(snapshot),
                        snapshot -> streamingPlaybackController.recoverStreamingBuffering(snapshot),
                        this::resolveCurrentStreamingQueueTrackIfNeeded,
                        status -> statusMessageController.setStatus(status)
                )
        );
        PlaybackServiceHostController playbackServiceHostController = new PlaybackServiceHostController(
                playbackServiceHostFactory.create(
                        () -> settingsStore.playbackSpeed(),
                        () -> settingsStore.appVolume(),
                        () -> settingsStore.concurrentPlaybackEnabled(),
                        () -> settingsStore.statusBarLyricsEnabled(),
                        () -> settingsStore.systemMediaLyricsTitleEnabled(),
                        () -> settingsStore.playbackRestoreEnabled(),
                        () -> settingsStore.replayGainEnabled(),
                        service -> playbackService = service,
                        () -> playbackService = null,
                        () -> playbackViewModel.resetPlayback(),
                        () -> playbackStartController.playPendingTracksIfNeeded()
                )
        );
        playbackServiceConnectionController = new PlaybackServiceConnectionController(
                this,
                playbackStateEventController,
                playbackServiceHostController,
                nowPlayingPlaybackServiceStarter,
                playbackServiceCommandQueue
        );
        playbackViewModel.bind(playbackServiceConnectionController);
        nowPlayingSourceSwitchOwner = new NowPlayingSourceSwitchOwner(
                resolveStreamingPlaybackUseCase,
                streamingViewModel,
                nowPlayingViewModel,
                playbackServiceConnectionController,
                streamingPlaybackQualityPolicy,
                statusMessageController
        );
    }

    private void initializeNowPlayingEffectOwner() {
        nowPlayingEffectOwner = new NowPlayingEffectOwner(
                nowPlayingViewModel,
                () -> navigationIntentOwner.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                playlistDialogController::showAddToPlaylist,
                trackShareLauncher::share,
                downloadRequestController::downloadTrack,
                nowPlayingSourceSwitchOwner::handle,
                nowPlayingSourceSwitchOwner::handle,
                statusMessageController::setStatus
        );
    }

    private void initializeNavigationRendering() {
        trackListRenderController = new TrackListRenderController(libraryViewModel, trackListRenderListenerFactory.create(
                (tracks, index) -> libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                track -> libraryViewModel.onEvent(new LibraryEvent.AddToPlaylist(track)),
                track -> downloadRequestController.downloadTrack(track),
                tracks -> downloadRequestController.downloadTracks(tracks),
                track -> networkDialogController.showEditStream(track),
                track -> libraryFileDeleteLauncher.request(
                        java.util.Collections.singletonList(track),
                        selectedPlaylistId()
                )
        ));
        trackListStatePublisher = new TrackListStatePublisher(
                trackListRenderController,
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playbackServiceConnectionController
        );
    }

    private void initializePlaybackControllers() {
        heartbeatSeedBinder = new HeartbeatRecommendationSeedBinder(
                () -> playbackService == null ? null : playbackService.snapshot(),
                () -> playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot(),
                this::playbackSnapshot,
                () -> playbackViewModel == null || playbackViewModel.getPlayback().getValue() == null
                        ? Collections.emptyList()
                        : playbackViewModel.getPlayback().getValue().getQueue(),
                this::heartbeatLibraryContextTracks
        );
        recommendationActionCallbacks = recommendationActionCallbacksFactory.create(
                status -> statusMessageController.setStatus(status),
                presentation -> playbackStartController.playRecommendation(presentation),
                provider -> heartbeatSeedBinder == null
                        ? new HeartbeatRecommendationSeedRequest()
                        : heartbeatSeedBinder.request(provider),
                presentation -> playbackStartController.playHeartbeatRecommendation(presentation),
                this::logHeartbeatSeedMiss
        );
        homeDashboardIntentHandler = new MainHomeDashboardRenderListener(
                mode -> {
                    routeController.setLibraryMode(mode);
                    navigationIntentOwner.navigateToTab(app.yukine.navigation.LibraryTab.INSTANCE, true);
                },
                this::continueDashboardPlayback,
                () -> navigationIntentOwner.navigateToTab(app.yukine.navigation.NowTab.INSTANCE, true),
                this::playTrackListFromHost,
                () -> libraryImportOwner.loadLibrary(true),
                () -> navigationIntentOwner.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                () -> navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB),
                () -> navigationIntentOwner.navigateToTab(app.yukine.navigation.CollectionsTab.INSTANCE, true),
                () -> navigationIntentOwner.navigateToTab(app.yukine.navigation.SearchTab.INSTANCE, true),
                () -> runRecommendationAction(new RecommendationAction.PlayDaily(StreamingProviderName.NETEASE)),
                () -> runRecommendationAction(new RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE))
        );
        playbackActionController = new PlaybackActionController(
                nowPlayingViewModel,
                playbackActionListenerFactory.create(
                        this::resolveCurrentStreamingQueueTrackIfNeeded,
                        () -> playbackService == null ? playbackSnapshot() : playbackService.snapshot(),
                        () -> libraryStore == null ? Collections.emptyList() : libraryStore.visibleTracks(),
                        this::applyPlaybackActionResult
                )
        );
        heartbeatRecommendationController = new HeartbeatRecommendationController(
                streamingRecommendationViewModel,
                () -> settingsStore.languageMode(),
                heartbeatRecommendationListenerFactory.create(
                        () -> playbackService != null,
                        provider -> heartbeatSeedBinder == null
                                ? new HeartbeatRecommendationSeedRequest()
                                : heartbeatSeedBinder.request(provider),
                        () -> streamingRecommendationViewModel.stopHeartbeatRecommendationMode(),
                        presentation -> nowPlayingViewModel.appendToQueue(presentation.getTracks()),
                        presentation -> playbackStartController.playHeartbeatRecommendation(presentation),
                        this::logHeartbeatSeedMiss,
                        status -> statusMessageController.setStatus(status)
                )
        );
        streamingPlaybackController = new StreamingPlaybackController(
                streamingViewModel,
                nowPlayingViewModel,
                streamingPlaybackListenerFactory.create(
                        () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        streamingPlaybackQualityPolicy::adaptive,
                        streamingPlaybackQualityPolicy::selected,
                        () -> settingsStore != null && settingsStore.refuseAutomaticQualityDowngrade(),
                        new StreamingQueueReadSource() {
                            @Override
                            public List<Track> queueSnapshot() {
                                return playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot();
                            }

                            @Override
                            public int queueSize() {
                                return playbackService == null ? 0 : playbackService.queueSize();
                            }

                            @Override
                            public Track queueTrackAt(int index) {
                                return playbackService == null ? null : playbackService.queueTrackAt(index);
                            }
                        },
                        snapshot -> heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot),
                        this::applyPlaybackActionResult,
                        status -> statusMessageController.setStatus(status)
                )
        );
        playbackStartListener = playbackStartListenerFactory.create(
                () -> streamingRecommendationViewModel.stopHeartbeatRecommendationMode(),
                () -> nowPlayingPlaybackServiceStarter.startPlaybackService(null),
                () -> playbackService != null,
                () -> streamingViewModel.prepareStreamingPlaybackStatusText(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        null
                ).getResolving(),
                status -> statusMessageController.setStatus(status),
                () -> navigationIntentOwner.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true)
        );
        playbackStartController = new PlaybackStartController(
                streamingPlaybackController::resolveAndPlayStreamingTrack,
                nowPlayingViewModel::playTrackList,
                this::applyPlaybackActionResult,
                playbackStartListener
        );
        nowPlayingViewModel.bindStateObserver(FloatingLyricsPublisher::update);
        queueActionController = new QueueActionController(
                nowPlayingViewModel,
                queueActionListenerFactory.create(
                        this::applyPlaybackActionResult,
                        () -> playbackService != null,
                        (fromIndex, toIndex) -> playbackService.moveQueueTrack(fromIndex, toIndex),
                        () -> confirmationDialogController.confirmClearQueue(),
                        () -> AppLanguage.text(settingsStore.languageMode(), "queue.empty"),
                        status -> statusMessageController.setStatus(status)
                )
        );
        lyricsViewModel.bindReloadGateway(
                () -> playbackSnapshot().currentTrack,
                this::neteaseProviderTrackIdForLyrics,
                status -> statusMessageController.setStatus(status)
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
                        routeController::setSettingsPage,
                        this::navigateToNetworkTabPage,
                        () -> navigationIntentOwner.navigateToTab(
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
                        minutes -> applyPlaybackActionResult(nowPlayingViewModel.startSleepTimer(minutes)),
                        () -> applyPlaybackActionResult(nowPlayingViewModel.cancelSleepTimer()),
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
                streamingPlaylistDialogListenerFactory.create(
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
                streamingPlaylistListenerFactory.create(
                        () -> routeController.selectedPlaylistId(),
                        playlistId -> routeController.setSelectedPlaylistId(playlistId),
                        () -> MainActivityBase.this.loadCollections(),
                        () -> libraryImportOwner.loadLibrary(true),
                        () -> MainActivityBase.this.selectedPlaylistName(),
                        () -> libraryStore.selectedPlaylistTracks(),
                        () -> libraryStore.favoriteTracks(),
                        () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                        (playlistName, tracks) ->
                                streamingPlaylistDialogController.showStreamingProviderPicker(playlistName, tracks),
                        () -> navigateToNetworkTabPage(NETWORK_STREAMING),
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
                streamingPlaylistImportDialogListenerFactory.create(
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
                streamingManualCookieListenerFactory.create(
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
        libraryStore = libraryStoreFactory.create(viewModel);
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
        playHistoryActionController = playHistoryActionControllerFactory.create(
                libraryViewModel,
                () -> settingsStore.languageMode(),
                viewModel::clearPlayHistory,
                status -> { statusMessageController.setStatus(status); return kotlin.Unit.INSTANCE; },
                () -> loadCollections()
        );
        streamingSearchActionHandler = streamingSearchActionHandlerFactory.create(streamingViewModel, streamingActionGateway);
        unifiedSearchOwner = new UnifiedSearchOwner(
                routeController,
                searchViewModel,
                streamingViewModel,
                libraryViewModel,
                libraryStore,
                streamingSearchActionHandler,
                settingsStore,
                streamingPlaybackQualityPolicy,
                this::playTrackListFromHost,
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
                streamingSearchRenderListenerFactory.create(
                        navigationIntentOwner::handleBack,
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
                libraryGroupsRenderListenerFactory.create(
                        (key, title) -> libraryViewModel.onEvent(new LibraryEvent.OpenGroup(key, title)),
                        () -> routeController.clearLibraryGroup(),
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
        collectionsRenderController = new CollectionsRenderController(collectionsViewModel, collectionsRenderListenerFactory.create(
                () -> playlistDialogController.showCreatePlaylist(),
                () -> documentPickerController.openPlaylistM3uFilePicker(),
                () -> confirmationDialogController.confirmClearPlayHistory(),
                navigationIntentOwner::handleBack,
                (tracks, index) -> MainActivityBase.this.playTrackListFromHost(tracks, index),
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                track -> playlistDialogController.showAddToPlaylist(track),
                track -> downloadRequestController.downloadTrack(track),
                tracks -> downloadRequestController.downloadTracks(tracks),
                playlistId -> {
                    routeController.setSelectedPlaylistId(playlistId);
                    loadCollections();
                },
                playlist -> playlistDialogController.showRenamePlaylist(playlist),
                playlist -> playlistDialogController.confirmDeletePlaylist(playlist),
                this::selectedPlaylistId,
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
                playbackServiceConnectionController,
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
                playbackServiceConnectionController
        );
        settingsViewModel.bindPreferenceGateway(applySettingsPreferenceUseCase::execute);
        settingsViewModel.bindStoreMirror(settingsStore::sync);
        SettingsRuntimeApplier settingsRuntimeApplier = settingsRuntimeApplierFactory.create(
                () -> uiShellController.applyThemeSurface(),
                customBackgroundAccentController::refresh,
                () -> playbackService == null ? null : new MainSettingsPlaybackServiceControls(playbackService),
                () -> lyricsViewModel,
                () -> permissionController
        );
        settingsViewModel.bindRuntimeEffectListener(settingsRuntimeApplier::apply);
        playlistMutationOwner = new PlaylistMutationOwner(
                libraryViewModel,
                routeController,
                () -> settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                this::loadCollections
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
        networkActionsViewModel.bindListener(networkActionsListenerFactory.create(
                nowPlayingViewModel,
                libraryImportOwner::replaceLibrary,
                this::navigateToNetworkTabPage,
                this::loadCollections,
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
                playbackServiceConnectionController,
                playbackViewModel,
                lyricsViewModel,
                streamingGatewaySettingsStore,
                luoxueSourceStore,
                repository
        );
        settingsViewModel.bindContextLoader(settingsContextProvider);
        settingsViewModel.bindRouteState(navigationViewModel.getState());
        settingsViewModel.refreshSettingsContext();
        nowPlayingViewModel.bindStateSources(
                playbackServiceConnectionController,
                viewModel.getLibrary(),
                lyricsViewModel.getState(),
                settingsViewModel.getState()
        );
        homeDashboardViewModel.bindStateSources(
                playbackServiceConnectionController,
                viewModel.getLibrary(),
                streamingViewModel.getStreaming(),
                settingsViewModel.getState(),
                homeDashboardIntentHandler
        );
        queueViewModel.bindStateSources(
                playbackServiceConnectionController,
                viewModel.getLibrary(),
                settingsViewModel.getState()
        );
        DialogLanguageProvider dialogLanguageProvider =
                () -> settingsStore.languageMode();
        networkDialogController = new NetworkDialogController(this, dialogLanguageProvider, networkRequestController);
        confirmationDialogController = new ConfirmationDialogController(
                this,
                dialogLanguageProvider,
                new ConfirmationActions(
                        playHistoryActionController::clearPlayHistory,
                        queueActionController::clearQueue,
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
                this::navigateNetworkPage,
                navigationIntentOwner::handleBack,
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
                (tracks, index) -> playTrackListFromHost(tracks, index),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status),
                networkMenuViewModel
        );
        NetworkSourcesEventController sourcesEvents = new NetworkSourcesEventController(
                routeController,
                networkRequestController,
                sourceId -> libraryStore.remoteSourceName(sourceId, settingsStore.languageMode()),
                sourceId -> libraryStore.webDavTracksForSource(sourceId),
                source -> networkDialogController.showEditWebDav(source),
                source -> confirmationDialogController.confirmDeleteRemoteSource(source),
                (tracks, index) -> playTrackListFromHost(tracks, index),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status)
        );
        NetworkTrackListRenderController trackListRenderer = new NetworkTrackListRenderController(
                new NetworkTrackListOwner(routeController, sourcesEvents, trackListStatePublisher)
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
                this::navigateToNetworkTabPage,
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
        installNavHostShell();
        installBackNavigation();
        playbackServiceConnectionController.bind();
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
        loadCollections();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playbackService != null) {
            playbackService.setAppVisible(true);
        }
        // Stored throttles keep this lightweight; it only verifies/renews sessions that are due.
        streamingViewModel.maintainStreamingAuthSessions();
    }

    @Override
    protected void onPause() {
        if (playbackService != null) {
            playbackService.setAppVisible(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releaseViewModelHostBindings();
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
        if (playbackServiceConnectionController != null) {
            playbackServiceConnectionController.release();
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
        if (nowPlayingViewModel != null) {
            nowPlayingViewModel.bindStateObserver(null);
            nowPlayingViewModel.bindStateSources(null, null, null, null);
            nowPlayingViewModel.bindGateway(null);
            nowPlayingViewModel.bindPlaybackGateway(null);
            nowPlayingViewModel.bindSourceCandidatesProvider(null);
            nowPlayingViewModel.bindLuoxueTrackMetadataResolver(null);
        }
        if (queueViewModel != null) {
            queueViewModel.bindStateSources(null, null, null);
            queueViewModel.bindIntentListener(null);
        }
        if (homeDashboardViewModel != null) {
            homeDashboardViewModel.bindStateSources(null, null, null, null, null);
        }
        if (searchViewModel != null) {
            searchViewModel.bindStateSources(null, null, null);
        }
        if (playbackViewModel != null) {
            playbackViewModel.bind(null);
        }
        if (lyricsViewModel != null) {
            lyricsViewModel.bindReloadGateway(null, null, null);
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

    private String selectedTab() {
        return routeController == null ? TAB_HOME : routeController.selectedTab();
    }

    private boolean isQueueVisible() {
        return TAB_QUEUE.equals(selectedTab())
                || (navHostState != null && navHostState.getQueueSheetVisible());
    }

    private String libraryMode() {
        return routeController == null ? LIBRARY_SONGS : routeController.libraryMode();
    }

    private String selectedLibraryGroupKey() {
        return routeController == null ? "" : routeController.selectedLibraryGroupKey();
    }

    private String selectedLibraryGroupTitle() {
        return routeController == null ? "" : routeController.selectedLibraryGroupTitle();
    }

    private long selectedPlaylistId() {
        return routeController == null ? -1L : routeController.selectedPlaylistId();
    }

    private String networkPage() {
        return routeController == null ? NETWORK_HOME : routeController.networkPage();
    }

    private String settingsPage() {
        return routeController == null ? SETTINGS_HOME : routeController.settingsPage();
    }

    private long selectedRemoteSourceId() {
        return routeController == null ? -1L : routeController.selectedRemoteSourceId();
    }

    private void continueDashboardPlayback(Track track) {
        if (playbackSnapshot().hasTrack()) {
            playbackActionController.togglePlayback();
            return;
        }
        if (track != null) {
            playTrackListFromHost(Collections.singletonList(track), 0);
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

    private void playTrackListFromHost(List<Track> tracks, int index) {
        if (playbackStartController != null) {
            playbackStartController.playTrackList(tracks, index);
            return;
        }
        if (playbackStartListener != null) {
            playbackStartListener.savePendingPlayback(
                    tracks == null ? Collections.emptyList() : new ArrayList<>(tracks),
                    index
            );
            playbackStartListener.setStatus(playbackStartListener.resolvingStatus());
            return;
        }
    }

    private void installNavHostShell() {
        if (queueViewModel == null || navHostInstalled) {
            return;
        }
        queueViewModel.bindIntentListener(new QueueIntentOwner(
                event -> {
                    libraryViewModel.onEvent(event);
                    return kotlin.Unit.INSTANCE;
                },
                track -> {
                    playlistDialogController.showAddToPlaylist(track);
                    return kotlin.Unit.INSTANCE;
                },
                track -> {
                    queueActionController.removeQueueTrack(track);
                    return kotlin.Unit.INSTANCE;
                },
                (fromIndex, toIndex) -> {
                    queueActionController.moveQueueTrack(fromIndex, toIndex);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    queueActionController.confirmClearQueue();
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    navigationIntentOwner.handleBack();
                    return kotlin.Unit.INSTANCE;
                }
        ));
        createNavHostState();
        navHostInstalled = true;
        EchoAppHost.installNavHost(this, new MainNavHostMount(
                () -> navHostState,
                onboardingOwner.getState(),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                new app.yukine.ui.OnboardingActions(
                        () -> permissionController.requestNeededPermissions(),
                        onboardingOwner::scanLibrary,
                        onboardingOwner::importPlaylist,
                        onboardingOwner::openStreaming,
                        onboardingOwner::finish
                ),
                () -> navigationIntentOwner.navigateToTab(app.yukine.navigation.HomeTab.INSTANCE, true),
                event -> {
                    nowPlayingEffectOwner.handle(event);
                    return kotlin.Unit.INSTANCE;
                },
                tab -> {
                    navigationIntentOwner.navigateToTab(tab, true);
                    return kotlin.Unit.INSTANCE;
                }
        ));
    }

    private void createNavHostState() {
        navHostState = new app.yukine.navigation.EchoNavHostState(
                    navigationViewModel.getState(),
                    homeDashboardViewModel.getUiState(),
                    nowPlayingViewModel,
                    queueViewModel,
                    libraryViewModel.getLibraryGroups(),
                    libraryViewModel.getTrackList(),
                    collectionsViewModel,
                    settingsViewModel.getState(),
                    settingsViewModel.getChromeState(),
                    settingsViewModel.getScrollState(),
                    networkMenuViewModel.getUiState(),
                    networkSourcesViewModel.getUiState(),
                    streamingViewModel.getStreaming(),
                    playbackViewModel,
                    downloadsViewModel.getUiState(),
                    downloadsViewModel.openDirectoryRequests(),
                    new DownloadsDestinationOwner(
                            downloadsViewModel,
                            trackDownloadManager,
                            () -> {
                                documentPickerController.openDownloadFolderPicker();
                                return kotlin.Unit.INSTANCE;
                            }
                    ).actions(),
                    searchViewModel.getUiState(),
                    trackDownloadManager,
                    () -> playbackService == null ? 0f : playbackService.realtimeBeat(),
                    () -> playbackService == null ? EMPTY_REALTIME_BANDS : playbackService.realtimeBands(),
                    true,
                    visible -> { },
                    libraryViewModel::onLibraryAction
        );
    }

    private void installBackNavigation() {
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navigationIntentOwner.handleBack()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    private void loadCollections() {
        libraryViewModel.loadCollectionsJava(selectedPlaylistId(), result -> {
            routeController.setSelectedPlaylistId(result.getSelectedPlaylistId());
            libraryStore.applyCollections(result);
        });
    }

    private void loadLyrics(final Track track) {
        if (lyricsViewModel != null) {
            lyricsViewModel.load(track, neteaseProviderTrackIdForLyrics(track));
        }
    }

    private String neteaseProviderTrackIdForLyrics(Track track) {
        return streamingViewModel.streamingProviderTrackIdFor(
                track,
                app.yukine.streaming.StreamingProviderName.NETEASE
        );
    }

    private void navigateNetworkPage(String page) {
        routeController.setNetworkPage(page);
    }

    private void navigateToNetworkTabPage(String page) {
        routeController.navigateToNetworkPageFromCurrent(page);
    }

    private String selectedPlaylistName() {
        return libraryStore.selectedPlaylistName(selectedPlaylistId());
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
        if (playbackService == null) {
            return false;
        }
        PlaybackStateSnapshot snapshot = playbackService.snapshot();
        StreamingQueueResolveTarget target = streamingViewModel.prepareCurrentStreamingQueueResolveTarget(
                snapshot,
                playbackService.queueSnapshot()
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

    private void applyPlaybackActionResult(PlaybackActionResultUi result) {
        if (result == null) {
            return;
        }
        String status = result.status;
        if (status != null && !status.trim().isEmpty()) {
            statusMessageController.setStatus(status);
        }
    }

    private PlaybackStateSnapshot playbackSnapshot() {
        if (playbackServiceConnectionController != null) {
            return playbackServiceConnectionController.getState().getValue();
        }
        if (playbackViewModel != null) {
            return playbackViewModel.getPlaybackSnapshot().getValue();
        }
        return PlaybackStateSnapshot.empty();
    }

    private List<Track> publishedPlaybackQueue(PlaybackStateSnapshot snapshot) {
        if (snapshot == null || playbackServiceConnectionController == null) {
            return null;
        }
        app.yukine.playback.PlaybackQueueSnapshot published =
                playbackServiceConnectionController.getQueue().getValue();
        if (published.getRevision() != snapshot.queueRevision
                || published.getTracks().size() != snapshot.queueSize) {
            return null;
        }
        return published.getTracks();
    }

    private List<Track> playbackQueueSnapshot() {
        List<Track> published = publishedPlaybackQueue(playbackSnapshot());
        if (published != null) {
            return published;
        }
        return playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot();
    }

}
