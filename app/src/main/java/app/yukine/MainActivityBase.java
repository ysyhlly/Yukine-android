package app.yukine;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
import app.yukine.queue.QueueIntent;
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
    private static final String TAB_LIBRARY = MainRoutes.TAB_LIBRARY;
    private static final String TAB_COLLECTIONS = MainRoutes.TAB_COLLECTIONS;
    private static final String TAB_QUEUE = MainRoutes.TAB_QUEUE;
    private static final String TAB_NOW = MainRoutes.TAB_NOW;
    private static final String TAB_NETWORK = MainRoutes.TAB_NETWORK;
    private static final String TAB_DOWNLOADS = MainRoutes.TAB_DOWNLOADS;
    private static final String TAB_SEARCH = MainRoutes.TAB_SEARCH;
    private static final String TAB_SETTINGS = MainRoutes.TAB_SETTINGS;
    private static final String NETWORK_HOME = MainRoutes.NETWORK_HOME;
    private static final String NETWORK_STREAMING = MainRoutes.NETWORK_STREAMING;
    private static final String NETWORK_STREAM_LIST = MainRoutes.NETWORK_STREAM_LIST;
    private static final String NETWORK_WEBDAV = MainRoutes.NETWORK_WEBDAV;
    private static final String NETWORK_WEBDAV_TRACKS = MainRoutes.NETWORK_WEBDAV_TRACKS;
    private static final String NETWORK_WEBDAV_SOURCE_TRACKS = MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS;
    private static final String NETWORK_SOURCES = MainRoutes.NETWORK_SOURCES;
    private static final String SETTINGS_HOME = MainRoutes.SETTINGS_HOME;
    private static final float[] EMPTY_REALTIME_BANDS = new float[0];
    private static final String LIBRARY_HOME = LibraryGrouping.HOME;
    private static final String LIBRARY_SONGS = LibraryGrouping.SONGS;
    private static final String LIBRARY_ALBUMS = LibraryGrouping.ALBUMS;
    private static final String LIBRARY_ARTISTS = LibraryGrouping.ARTISTS;
    private static final String LIBRARY_FOLDERS = LibraryGrouping.FOLDERS;
    private static final String LIBRARY_PLAYLISTS = LibraryGrouping.PLAYLISTS;
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
    @Inject MainDocumentPickerListenerFactory documentPickerListenerFactory;
    @Inject MainBackgroundImagePickerListenerFactory backgroundImagePickerListenerFactory;
    @Inject MainPermissionListenerFactory permissionListenerFactory;
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
    private MainUiShellController uiShellController;
    private TrackShareLauncher trackShareLauncher;
    private CustomBackgroundAccentController customBackgroundAccentController;
    private LibraryFileDeleteLauncher libraryFileDeleteLauncher;
    private DocumentPickerController documentPickerController;
    private BackgroundImagePickerController backgroundImagePickerController;
    private BackupRestoreLauncher backupRestoreLauncher;
    private DownloadRequestController downloadRequestController;
    private LuoxueSourceImportController luoxueSourceImportController;
    private LuoxueSourceImportDialogController luoxueSourceImportDialogController;
    private PlaybackServiceConnectionController playbackServiceConnectionController;
    private StreamingPlaybackQualityPolicy streamingPlaybackQualityPolicy;
    private NowPlayingSourceSwitchOwner nowPlayingSourceSwitchOwner;
    private PlaybackStateEventController playbackStateEventController;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsContextProvider settingsContextProvider;
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
    private int unifiedStreamingPlaybackRequestId = 0;
    private app.yukine.navigation.EchoNavHostState navHostState;
    private boolean navHostInstalled;

    private NetworkDialogController networkDialogController;
    private PlaylistDialogController playlistDialogController;
    private PlaylistMutationOwner playlistMutationOwner;
    private ConfirmationDialogController confirmationDialogController;
    private OnboardingController onboardingController;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViewModels(createActivityViewModels());
        initializeRouteStoresAndStatus();
        MainActivityStreamingActionGateway streamingActionGateway = createStreamingActionGateway();
        initializeStreamingPlaybackCoordinator();
        initializeNowPlayingGateways();
        initializeDownloadRequests();
        initializeLibraryGateway();
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
                this::applySearch,
                () -> documentPickerController.openAudioFilePicker(),
                allowCachedFirst -> libraryImportOwner.loadLibrary(allowCachedFirst),
                tracks -> libraryFileDeleteLauncher.request(tracks, selectedPlaylistId()),
                tracks -> downloadRequestController.downloadTracks(tracks)
        ));
    }

    private void initializeStreamingStartup(MainActivityStreamingActionGateway streamingActionGateway) {
        streamingViewModel.configureStreamingRepository();
        refreshStreamingProviders();
        streamingAuthCallbackController = new StreamingAuthCallbackController(
                streamingViewModel,
                streamingActionGateway
        );
    }

    private void initializeRouteStoresAndStatus() {
        uiShellController = new MainUiShellController(this);
        routeController = new MainRouteController(navigationViewModel);
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
        permissionController = new MainPermissionController(this, permissionListenerFactory.create(
                () -> permissionController != null && permissionController.hasAudioPermission(),
                allowCachedFirst -> libraryImportOwner.loadLibrary(allowCachedFirst),
                () -> {
                    if (onboardingController != null) {
                        onboardingController.onPermissionsChanged();
                    }
                }
        ));
        libraryImportOwner = new LibraryImportOwner(
                libraryViewModel,
                libraryStore,
                routeController,
                () -> permissionController != null && permissionController.hasAudioPermission(),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                this::loadCollections,
                canScan -> {
                    if (onboardingController != null) {
                        onboardingController.onLibraryScanResult(canScan);
                    }
                },
                () -> navigateToNetworkTabPage(NETWORK_STREAMING)
        );
        documentPickerController = new DocumentPickerController(this, documentPickerListenerFactory.create(
                libraryImportOwner::importAudioUris,
                libraryImportOwner::importAudioFolder,
                this::setCustomDownloadFolder,
                libraryImportOwner::importStreamM3u,
                (exportUri, playlistId, playlistName) -> libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName),
                libraryImportOwner::importPlaylistM3u,
                uris -> luoxueSourceImportController.importSelectedUris(uris)
        ));
        backgroundImagePickerController = new BackgroundImagePickerController(
                this,
                backgroundImagePickerListenerFactory.create(
                        (page, uri, transform) -> settingsViewModel.applyPageBackgrounds(
                                settingsStore.pageBackgrounds().withBackground(page, uri.toString(), transform),
                                page,
                                false
                        ),
                        page -> statusMessageController.setStatus(AppLanguage.text(
                                settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                                "page.background.copy.failed"
                        ))
                ),
                task -> executors.io(task),
                task -> mainHandler.post(task),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()
        );
        libraryFileDeleteLauncher = new LibraryFileDeleteLauncher(
                this,
                libraryDeletionUseCase,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                result -> {
                    java.util.Set<Long> removedIds = new java.util.HashSet<>();
                    for (Track track : result.getRemoved()) {
                        removedIds.add(track.id);
                    }
                    nowPlayingViewModel.removeQueueTracks(removedIds);
                    libraryViewModel.onLibraryAction(app.yukine.ui.LibraryAction.ClearSelection.INSTANCE);
                    int removed = result.getRemoved().size();
                    int failed = result.getFailed().size();
                    int skipped = result.getSkipped().size();
                    statusMessageController.setStatus(
                            AppLanguage.text(settingsStore.languageMode(), "library.delete.result")
                                    .replace("%d", String.valueOf(removed))
                                    .replace("%f", String.valueOf(failed))
                                    .replace("%s", String.valueOf(skipped))
                    );
                    libraryImportOwner.loadLibrary(true);
                    return kotlin.Unit.INSTANCE;
                }
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
                            refreshStreamingProviders();
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

    private void initializeNavigationRendering() {
        searchViewModel.updateActions(new app.yukine.ui.UnifiedSearchActions(
                this::updateUnifiedSearchQuery,
                this::performUnifiedSearch,
                this::playUnifiedSearchTrack,
                this::playUnifiedStreamingTrack,
                () -> {
                    if (streamingSearchActionHandler != null) {
                        streamingSearchActionHandler.loadNextPage();
                    }
                },
                this::clearUnifiedSearchOnExit
        ));
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
                    navigateToTab(app.yukine.navigation.LibraryTab.INSTANCE, true);
                },
                this::continueDashboardPlayback,
                () -> navigateToTab(app.yukine.navigation.NowTab.INSTANCE, true),
                this::playTrackListFromHost,
                () -> libraryImportOwner.loadLibrary(true),
                () -> navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                () -> navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB),
                () -> routeController.navigateToTab(app.yukine.navigation.CollectionsTab.INSTANCE, true),
                () -> navigateToTab(app.yukine.navigation.SearchTab.INSTANCE, true),
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
                () -> navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true)
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
        settingsViewModel.bindEffectListener(effect -> {
            if (effect instanceof SettingsEffect.ShowStatus) {
                statusMessageController.setStatus(((SettingsEffect.ShowStatus) effect).getMessage());
            } else if (effect instanceof SettingsEffect.NavigatePage) {
                routeController.setSettingsPage(((SettingsEffect.NavigatePage) effect).getPage());
            } else if (effect instanceof SettingsEffect.OpenNetworkPage) {
                navigateToNetworkTabPage(((SettingsEffect.OpenNetworkPage) effect).getPage());
            } else if (effect == SettingsEffect.OpenDownloads.INSTANCE) {
                navigateToTab(app.yukine.navigation.DownloadsTab.INSTANCE, true);
            } else if (effect == SettingsEffect.RequestNeededPermissions.INSTANCE) {
                if (permissionController != null) {
                    permissionController.requestNeededPermissions();
                }
            } else if (effect == SettingsEffect.LoadLibrary.INSTANCE) {
                libraryImportOwner.loadLibrary(false);
            } else if (effect == SettingsEffect.OpenAudioFilePicker.INSTANCE) {
                documentPickerController.openAudioFilePicker();
            } else if (effect == SettingsEffect.OpenAudioFolderPicker.INSTANCE) {
                documentPickerController.openAudioFolderPicker();
            } else if (effect == SettingsEffect.OpenLuoxueSourceManager.INSTANCE) {
                luoxueSourceImportDialogController.showSourceManager();
            } else if (effect == SettingsEffect.ImportLuoxueSource.INSTANCE) {
                luoxueSourceImportDialogController.showImportDialog();
            } else if (effect == SettingsEffect.ReloadCurrentLyrics.INSTANCE) {
                lyricsViewModel.reloadCurrentLyrics(settingsStore.languageMode());
            } else if (effect instanceof SettingsEffect.StartSleepTimer) {
                applyPlaybackActionResult(nowPlayingViewModel.startSleepTimer(((SettingsEffect.StartSleepTimer) effect).getMinutes()));
            } else if (effect == SettingsEffect.CancelSleepTimer.INSTANCE) {
                applyPlaybackActionResult(nowPlayingViewModel.cancelSleepTimer());
            } else if (effect == SettingsEffect.OpenFloatingLyricsPermission.INSTANCE) {
                settingsViewModel.openFloatingLyricsPermission();
            } else if (effect instanceof SettingsEffect.ChoosePageBackground) {
                backgroundImagePickerController.open(((SettingsEffect.ChoosePageBackground) effect).getPage());
            } else if (effect == SettingsEffect.ExportBackup.INSTANCE) {
                backupRestoreLauncher.exportBackup();
            } else if (effect == SettingsEffect.ImportBackup.INSTANCE) {
                backupRestoreLauncher.importBackup();
            } else if (effect instanceof SettingsEffect.ApplyStreamingGatewayEndpoint) {
                applyStreamingGatewayEndpoint(((SettingsEffect.ApplyStreamingGatewayEndpoint) effect).getEndpoint());
            } else if (effect instanceof SettingsEffect.RestoreHiddenLibraryItem) {
                libraryViewModel.restoreHiddenLibraryItemJava(
                        ((SettingsEffect.RestoreHiddenLibraryItem) effect).getSourceKey(),
                        changed -> refreshAfterHiddenLibraryRestore(changed)
                );
            } else if (effect == SettingsEffect.RestoreAllHiddenLibraryItems.INSTANCE) {
                libraryViewModel.restoreAllHiddenLibraryItemsJava(
                        changed -> refreshAfterHiddenLibraryRestore(changed)
                );
            }
        });
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
        StreamingSearchRenderController streamingSearchRenderController = new StreamingSearchRenderController(
                () -> settingsStore.languageMode(),
                streamingSearchRenderListenerFactory.create(
                        this::handleAppBack,
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
                this::handleAppBack,
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
        confirmationDialogController = new ConfirmationDialogController(this, dialogLanguageProvider, new ConfirmationDialogController.Listener() {
            @Override
            public void clearPlayHistory() {
                playHistoryActionController.clearPlayHistory();
            }

            @Override
            public void clearQueue() {
                queueActionController.clearQueue();
            }

            @Override
            public void deleteAllStreams() {
                networkRequestController.deleteAllStreams();
            }

            @Override
            public void deleteTrack(long trackId, String status) {
                networkRequestController.deleteTrack(trackId, status);
            }

            @Override
            public void deleteTracks(List<Long> trackIds, String status) {
                networkRequestController.deleteTracks(trackIds, status);
            }

            @Override
            public void deleteRemoteSource(long sourceId, String name) {
                networkRequestController.deleteRemoteSource(sourceId);
            }
        });
        initializeNetworkRendering(streamingSearchRenderController);
        streamingAuthCallbackController.handleInitialIntent(getIntent());
        uiShellController.applyThemeSurface();
    }

    private void initializeNetworkRendering(StreamingSearchRenderController streamingSearchRenderController) {
        NetworkMenuEventController menuEvents = new NetworkMenuEventController(
                this::navigateNetworkPage,
                this::handleAppBack,
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
        onboardingController = new OnboardingController(new OnboardingController.Listener() {
            @Override
            public boolean hasAudioPermission() {
                return permissionController != null && permissionController.hasAudioPermission();
            }

            @Override
            public boolean hasNotificationPermission() {
                return permissionController != null && permissionController.hasNotificationPermission();
            }

            @Override
            public void requestNeededPermissions() {
                if (permissionController != null) {
                    permissionController.requestNeededPermissions();
                }
            }

            @Override
            public void loadLibrary(boolean allowCachedFirst) {
                libraryImportOwner.loadLibrary(allowCachedFirst);
            }

            @Override
            public void cancelLibraryLoad() {
                if (libraryViewModel != null) {
                    libraryViewModel.cancelLibraryLoad();
                }
            }

            @Override
            public void onLibraryScanTimedOut() {
                statusMessageController.setStatus(
                        AppLanguage.text(settingsStore.languageMode(), "library.scan.timeout")
                );
            }

            @Override
            public void navigateToNetworkTabPage(String page) {
                MainActivityBase.this.navigateToNetworkTabPage(page);
            }

            @Override
            public void openPlaylistM3uFilePicker() {
                documentPickerController.openPlaylistM3uFilePicker();
            }

            @Override
            public void onboardingCompleted() {
                if (repository != null) {
                    repository.saveOnboardingCompleted(true);
                }
            }
        }, new OnboardingController.Scheduler() {
            @Override
            public void postDelayed(Runnable runnable, long delayMs) {
                mainHandler.postDelayed(runnable, delayMs);
            }

            @Override
            public void removeCallbacks(Runnable runnable) {
                mainHandler.removeCallbacks(runnable);
            }
        });
        onboardingController.initialize(repository == null || !repository.loadOnboardingCompleted());
        installNavHostShell();
        installBackNavigation();
        playbackServiceConnectionController.bind();
        if (!showOnboarding()) {
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

    private boolean showOnboarding() {
        return onboardingController != null && onboardingController.showOnboarding();
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
        if (onboardingController != null) {
            onboardingController.release();
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

    private String searchQuery() {
        return routeController == null ? "" : routeController.searchQuery();
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

    private kotlinx.coroutines.Job refreshStreamingProviders() {
        kotlinx.coroutines.Job job = streamingViewModel.refreshStreamingProviders();
        job.invokeOnCompletion(error -> {
            syncStreamingProviders();
            return kotlin.Unit.INSTANCE;
        });
        return job;
    }

    private kotlinx.coroutines.Job applyStreamingGatewayEndpoint(String endpoint) {
        streamingGatewaySettingsStore.setEndpoint(endpoint);
        streamingViewModel.configureStreamingRepository();
        kotlinx.coroutines.Job refreshJob = refreshStreamingProviders();
        statusMessageController.setStatus(
                AppLanguage.text(settingsStore.languageMode(), "streaming.gateway.applied")
                        + streamingGatewaySettingsStore.endpoint()
        );
        return refreshJob;
    }

    private void syncStreamingProviders() {
        streamingRecommendationViewModel.updateProviders(streamingViewModel.getState().getProviders());
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
        queueViewModel.bindIntentListener(intent -> {
            if (intent instanceof QueueIntent.PlayAt playAt) {
                libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(playAt.getTracks(), playAt.getIndex()));
                return;
            }
            if (intent instanceof QueueIntent.ToggleFavorite toggleFavorite) {
                libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(toggleFavorite.getTrack()));
                return;
            }
            if (intent instanceof QueueIntent.AddToPlaylist addToPlaylist) {
                playlistDialogController.showAddToPlaylist(addToPlaylist.getTrack());
                return;
            }
            if (intent instanceof QueueIntent.Remove remove) {
                queueActionController.removeQueueTrack(remove.getTrack());
                return;
            }
            if (intent instanceof QueueIntent.Move move) {
                queueActionController.moveQueueTrack(move.getFromIndex(), move.getToIndex());
                return;
            }
            if (intent instanceof QueueIntent.ClearQueue) {
                queueActionController.confirmClearQueue();
                return;
            }
            if (intent instanceof QueueIntent.Back) {
                MainActivityBase.this.handleAppBack();
            }
        });
        createNavHostState();
        navHostInstalled = true;
        EchoAppHost.installNavHost(this, new ActivityNavHostMount());
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
                    downloadsDestinationActions(),
                    searchViewModel.getUiState(),
                    trackDownloadManager,
                    () -> playbackService == null ? 0f : playbackService.realtimeBeat(),
                    () -> playbackService == null ? EMPTY_REALTIME_BANDS : playbackService.realtimeBands(),
                    true,
                    visible -> { },
                    libraryViewModel::onLibraryAction
        );
    }

    private app.yukine.DownloadsDestinationActions downloadsDestinationActions() {
        return new app.yukine.DownloadsDestinationActions(
                () -> {
                    downloadsViewModel.refresh(trackDownloadManager);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    downloadsViewModel.useMusicDirectory(trackDownloadManager);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    downloadsViewModel.useDownloadsDirectory(trackDownloadManager);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    downloadsViewModel.chooseDirectory(trackDownloadManager);
                    return kotlin.Unit.INSTANCE;
                },
                id -> {
                    downloadsViewModel.pause(trackDownloadManager, id);
                    return kotlin.Unit.INSTANCE;
                },
                id -> {
                    downloadsViewModel.resume(trackDownloadManager, id);
                    return kotlin.Unit.INSTANCE;
                },
                id -> {
                    downloadsViewModel.remove(trackDownloadManager, id);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    downloadsViewModel.pauseAll(trackDownloadManager);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    downloadsViewModel.resumeAll(trackDownloadManager);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    openDownloadDirectoryPickerFromNav();
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    private void openDownloadDirectoryPickerFromNav() {
        if (documentPickerController == null) {
            statusMessageController.showFeedback(AppLanguage.text(
                    settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                    "download.directory.picker.unavailable"
            ));
            return;
        }
        documentPickerController.openDownloadFolderPicker();
    }

    private final class ActivityNavHostMount implements EchoNavHostMount {
        @Override
        public String languageMode() {
            return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
        }

        @Override
        public java.util.List<app.yukine.navigation.EchoTabItem> tabs() {
            java.util.ArrayList<app.yukine.navigation.EchoTabItem> tabs = new java.util.ArrayList<>();
            String lang = settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
            tabs.add(new app.yukine.navigation.EchoTabItem(
                    app.yukine.navigation.HomeTab.INSTANCE, AppLanguage.tabLabel(lang, TAB_HOME)));
            tabs.add(new app.yukine.navigation.EchoTabItem(
                    app.yukine.navigation.LibraryTab.INSTANCE, AppLanguage.tabLabel(lang, TAB_LIBRARY)));
            tabs.add(new app.yukine.navigation.EchoTabItem(
                    app.yukine.navigation.QueueTab.INSTANCE, AppLanguage.text(lang, "tab.playing")));
            tabs.add(new app.yukine.navigation.EchoTabItem(
                    app.yukine.navigation.SettingsTab.INSTANCE, AppLanguage.tabLabel(lang, TAB_SETTINGS)));
            return tabs;
        }

        @Override
        public java.lang.Runnable closeNowPlayingAction() {
            return () -> navigateToTab(app.yukine.navigation.HomeTab.INSTANCE, true);
        }

        @Override
        public kotlin.jvm.functions.Function1<app.yukine.NowPlayingEvent, kotlin.Unit> nowPlayingEventHandler() {
            return event -> {
                handleNowPlayingEvent(event);
                return kotlin.Unit.INSTANCE;
            };
        }

        @Override
        public app.yukine.navigation.EchoNavHostState hostState() {
            return navHostState;
        }

        @Override
        public kotlinx.coroutines.flow.StateFlow<OnboardingUiState> onboardingState() {
            return onboardingController.getState();
        }

        @Override
        public app.yukine.ui.OnboardingActions onboardingActions() {
            return new app.yukine.ui.OnboardingActions(
                    () -> {
                        if (permissionController != null) {
                            permissionController.requestNeededPermissions();
                        }
                    },
                    () -> {
                        if (onboardingController != null) {
                            onboardingController.scanLibraryFromOnboarding();
                        }
                    },
                    () -> {
                        if (onboardingController != null) {
                            onboardingController.importPlaylistFromOnboarding();
                        }
                    },
                    () -> {
                        if (onboardingController != null) {
                            onboardingController.openStreamingFromOnboarding();
                        }
                    },
                    () -> {
                        if (onboardingController != null) {
                            onboardingController.finishOnboarding();
                        }
                    }
            );
        }

        @Override
        public void onTabChanged(app.yukine.navigation.TabRoute tab) {
            navigateToTab(tab, true);
        }
    }

    private void installBackNavigation() {
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (handleAppBack()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    private boolean handleAppBack() {
        MainBackNavigationPolicy.Result result = routeController.applyBackNavigation();
        if (!result.handled) {
            return false;
        }
        if (result.navigateTab) {
            app.yukine.navigation.TabRoute destination =
                    app.yukine.navigation.TabRoute.Companion.fromKey(result.selectedTab);
            navigateToTab(destination == null ? app.yukine.navigation.HomeTab.INSTANCE : destination);
        }
        return true;
    }

    private void setCustomDownloadFolder(final Uri treeUri) {
        if (trackDownloadManager == null || treeUri == null) {
            statusMessageController.showFeedback("\u65e0\u6cd5\u4fdd\u5b58\u4e0b\u8f7d\u76ee\u5f55");
            return;
        }
        trackDownloadManager.setCustomDownloadDirectory(treeUri);
        if (downloadsViewModel != null) {
            downloadsViewModel.refresh(trackDownloadManager);
        }
        statusMessageController.showFeedback("\u5df2\u8bbe\u7f6e\u4e0b\u8f7d\u76ee\u5f55\uff1a" + trackDownloadManager.downloadDirectoryLabel());
    }

    private void loadCollections() {
        libraryViewModel.loadCollectionsJava(selectedPlaylistId(), result -> {
            routeController.setSelectedPlaylistId(result.getSelectedPlaylistId());
            libraryStore.applyCollections(result);
        });
    }

    private void performUnifiedSearch(String query) {
        String safeQuery = query == null ? "" : query.trim();
        routeController.setSearchQuery(safeQuery);
        if (streamingSearchActionHandler != null && !safeQuery.isEmpty()) {
            streamingSearchActionHandler.search(safeQuery);
        }
    }

    private void updateUnifiedSearchQuery(String query) {
        if (searchViewModel != null) {
            searchViewModel.updateQuery(query == null ? "" : query);
        }
    }

    private void clearUnifiedSearchOnExit() {
        if (routeController != null && searchQuery() != null && !searchQuery().isEmpty()) {
            routeController.setSearchQuery("");
        }
        if (searchViewModel != null) {
            searchViewModel.clearSearch();
        }
        if (streamingViewModel != null) {
            streamingViewModel.clearStreamingSearchSession();
        }
        unifiedStreamingPlaybackRequestId++;
        if (libraryStore != null) {
            libraryStore.applySearch("");
        }
        if (libraryViewModel != null) {
            libraryViewModel.syncSearchQuery("");
        }
    }

    private void playUnifiedSearchTrack(Track track) {
        if (track != null) {
            playTrackListFromHost(Collections.singletonList(track), 0);
        }
    }

    private void playUnifiedStreamingTrack(app.yukine.streaming.StreamingTrack track) {
        if (track == null) {
            return;
        }
        if (!track.getPlayable()) {
            String reason = track.getUnavailableReason();
            statusMessageController.showFeedback(reason == null || reason.trim().isEmpty() ? "\u8be5\u5728\u7ebf\u6b4c\u66f2\u6682\u4e0d\u53ef\u64ad\u653e" : reason);
            return;
        }
        final int requestId = ++unifiedStreamingPlaybackRequestId;
        statusMessageController.showFeedback("\u6b63\u5728\u89e3\u6790\u5728\u7ebf\u6b4c\u66f2\uff1a" + track.getTitle());
        streamingViewModel.resolveStreamingTrackForPlayback(
                track.getProvider(),
                track.getProviderTrackId(),
                track,
                streamingPlaybackQualityPolicy.selected(),
                resolved -> {
                    if (requestId != unifiedStreamingPlaybackRequestId) {
                        return;
                    }
                    if (resolved == null) {
                        statusMessageController.showFeedback("\u5728\u7ebf\u6b4c\u66f2\u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
                        return;
                    }
                    playTrackListFromHost(Collections.singletonList(resolved), 0);
                    statusMessageController.showFeedback("\u5f00\u59cb\u64ad\u653e\uff1a" + resolved.title);
                }
        );
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

    private void applySearch() {
        String query = searchQuery();
        libraryStore.applySearchAsync(query, () -> {
            if (query != null && !query.trim().isEmpty() && libraryStore.visibleTracks().isEmpty()) {
                statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "search.no.results"));
            }
        });
        if (TAB_SEARCH.equals(selectedTab())) {
            if (streamingSearchActionHandler != null && searchQuery() != null && !searchQuery().trim().isEmpty()) {
                streamingSearchActionHandler.search(searchQuery());
            }
        }
        if (TAB_NETWORK.equals(selectedTab())
                && (NETWORK_STREAMING.equals(networkPage())
                || MainRoutes.NETWORK_STREAMING_HUB.equals(networkPage()))) {
            if (streamingSearchActionHandler != null) {
                streamingSearchActionHandler.search(searchQuery());
            }
        }
    }

    private void navigateToTab(app.yukine.navigation.TabRoute tab) {
        navigateToTab(tab, false);
    }

    private void navigateToTab(
            app.yukine.navigation.TabRoute tab,
            boolean userInitiated
    ) {
        String previousDirectory = currentDirectoryKey();
        boolean sameTab = routeController.navigateToTab(tab, userInitiated);
        if (userInitiated && sameTab && previousDirectory.equals(currentDirectoryKey())) {
            requestCurrentDirectoryScrollToTop();
        }
    }

    private boolean handleContentHorizontalSwipe(boolean next) {
        if (!TAB_LIBRARY.equals(selectedTab())) {
            return false;
        }
        if (!next && closeLibraryDetailIfNeeded()) {
            return true;
        }
        ArrayList<String> modes = librarySwipeModes();
        int currentIndex = modes.indexOf(libraryMode());
        if (currentIndex < 0) {
            return false;
        }
        int nextIndex = next ? currentIndex + 1 : currentIndex - 1;
        if (nextIndex < 0 || nextIndex >= modes.size()) {
            return false;
        }
        String mode = modes.get(nextIndex);
        routeController.setLibraryMode(mode);
        return true;
    }

    private boolean closeLibraryDetailIfNeeded() {
        if (selectedLibraryGroupKey().isEmpty() && selectedPlaylistId() < 0L) {
            return false;
        }
        routeController.clearLibraryGroup();
        routeController.setSelectedPlaylistId(-1L);
        return true;
    }

    private ArrayList<String> librarySwipeModes() {
        ArrayList<String> modes = new ArrayList<>();
        modes.add(LIBRARY_SONGS);
        modes.add(LIBRARY_ALBUMS);
        modes.add(LIBRARY_ARTISTS);
        modes.add(LIBRARY_FOLDERS);
        modes.add(LIBRARY_PLAYLISTS);
        return modes;
    }

    private void navigateNetworkPage(String page) {
        routeController.setNetworkPage(page);
    }

    private void navigateToNetworkTabPage(String page) {
        routeController.navigateToNetworkPageFromCurrent(page);
    }

    private String currentDirectoryKey() {
        String tab = selectedTab();
        if (TAB_NETWORK.equals(tab)) {
            return tab + "|" + networkPage() + "|" + selectedRemoteSourceId();
        }
        if (TAB_SETTINGS.equals(tab)) {
            return tab + "|" + settingsPage();
        }
        if (TAB_LIBRARY.equals(tab)) {
            return tab + "|" + libraryMode() + "|" + selectedLibraryGroupKey() + "|" + selectedPlaylistId();
        }
        return tab;
    }

    private void requestCurrentDirectoryScrollToTop() {
        if (TAB_SETTINGS.equals(selectedTab())) {
            settingsViewModel.scrollToTopOnNextRender();
        }
    }

    private String selectedPlaylistName() {
        return libraryStore.selectedPlaylistName(selectedPlaylistId());
    }

    private void refreshAfterHiddenLibraryRestore(boolean changed) {
        if (changed) {
            libraryImportOwner.loadLibrary(true);
        }
        settingsViewModel.refreshSettingsContext();
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

    private void handleNowPlayingEvent(NowPlayingEvent event) {
        if (nowPlayingViewModel == null || event == null) {
            return;
        }
        nowPlayingViewModel.onEvent(event);
        handleNowPlayingEffects();
    }

    private void handleNowPlayingEffects() {
        if (nowPlayingViewModel == null) {
            return;
        }
        List<NowPlayingEffect> effects = nowPlayingViewModel.drainEffects();
        if (effects == null || effects.isEmpty()) {
            return;
        }
        for (NowPlayingEffect effect : effects) {
            if (effect == NowPlayingEffect.OpenQueue.INSTANCE) {
                navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true);
            } else if (effect instanceof NowPlayingEffect.OpenAddToPlaylist openAddToPlaylist) {
                playlistDialogController.showAddToPlaylist(openAddToPlaylist.getTrack());
            } else if (effect instanceof NowPlayingEffect.ShareTrack shareTrack) {
                trackShareLauncher.share(shareTrack.getTrack());
            } else if (effect instanceof NowPlayingEffect.DownloadTrack downloadTrack) {
                downloadRequestController.downloadTrack(downloadTrack.getTrack());
            } else if (effect instanceof NowPlayingEffect.SwitchSource switchSource) {
                nowPlayingSourceSwitchOwner.handle(switchSource);
            } else if (effect instanceof NowPlayingEffect.SwitchLibrarySource switchLibrarySource) {
                nowPlayingSourceSwitchOwner.handle(switchLibrarySource);
            } else if (effect instanceof NowPlayingEffect.ShowMessage showMessage) {
                statusMessageController.setStatus(showMessage.getMessage());
            }
        }
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
