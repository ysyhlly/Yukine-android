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
import java.util.Set;
import javax.inject.Inject;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Playlist;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.playback.PlaybackStateSnapshot;
import app.yukine.queue.QueueIntent;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.streaming.StreamingAudioQuality;
import app.yukine.streaming.StreamingPlaylist;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.streaming.StreamingTrack;
import app.yukine.ui.LibraryGroupActions;
import app.yukine.ui.LibraryGroupUiState;
import app.yukine.ui.TrackListAlbumCardUiState;
import app.yukine.ui.TrackListHeaderAction;
import app.yukine.ui.TrackListHeaderMetric;
import app.yukine.ui.TrackListLabels;
import app.yukine.ui.TrackListModeAction;
import app.yukine.ui.TrackRowActions;
import app.yukine.ui.TrackRowUiState;
import app.yukine.streaming.StreamingQualityPreference;

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
    @Inject MainHomeDashboardRenderListenerFactory homeDashboardRenderListenerFactory;
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
    @Inject MainQueueRenderListenerFactory queueRenderListenerFactory;
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
    private MainPlaybackStore playbackStore;
    @Inject MainPlaybackStoreFactory playbackStoreFactory;
    @Inject MainNowPlayingGatewayFactory nowPlayingGatewayFactory;
    @Inject MainNowPlayingPlaybackGatewayFactory nowPlayingPlaybackGatewayFactory;
    @Inject NowPlayingPlaybackServiceStarter nowPlayingPlaybackServiceStarter;
    @Inject MainNowPlayingStateListenerFactory nowPlayingStateListenerFactory;
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
    @Inject ArtistInfoRepository artistInfoRepository;
    @Inject MainLibraryGroupsRenderListenerFactory libraryGroupsRenderListenerFactory;
    @Inject MainCollectionsRenderListenerFactory collectionsRenderListenerFactory;
    private StatusMessageController statusMessageController;
    @Inject MainSettingsRuntimeApplierFactory settingsRuntimeApplierFactory;
    private MainPermissionController permissionController;
    private MainUiShellController uiShellController;
    private TrackShareLauncher trackShareLauncher;
    private DocumentPickerController documentPickerController;
    private BackgroundImagePickerController backgroundImagePickerController;
    private BackupRestoreLauncher backupRestoreLauncher;
    private DownloadRequestController downloadRequestController;
    private LuoxueSourceImportController luoxueSourceImportController;
    private LuoxueSourceImportDialogController luoxueSourceImportDialogController;
    private PlaybackServiceConnectionController playbackServiceConnectionController;
    private PlaybackStateEventController playbackStateEventController;
    private MainTabRenderDispatcher tabRenderDispatcher;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsContextProvider settingsContextProvider;
    private TrackListRenderController trackListRenderController;
    private QueueRenderController queueRenderController;
    private QueueActionController queueActionController;
    private PlaybackActionController playbackActionController;
    private PlaybackStartController playbackStartController;
    private NowPlayingStateController nowPlayingStateController;
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
    private HomeDashboardRenderController homeDashboardRenderController;
    private LibraryGroupsRenderController libraryGroupsRenderController;
    private LibraryPlaylistsRenderController libraryPlaylistsRenderController;
    private CollectionsRenderController collectionsRenderController;
    private PlayHistoryActionController playHistoryActionController;
    private LyricsViewModel lyricsViewModel;
    private PlaybackServiceHostPort playbackService;
    private MainPlaybackStartListener playbackStartListener;
    private int unifiedStreamingPlaybackRequestId = 0;
    private boolean scrollContentToTopOnNextRender;
    private app.yukine.navigation.EchoNavHostState navHostState;

    private NetworkDialogController networkDialogController;
    private PlaylistDialogController playlistDialogController;
    private ConfirmationDialogController confirmationDialogController;
    private OnboardingController onboardingController;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViewModels(createActivityViewModels());
        MainActivityStreamingActionGateway streamingActionGateway = createStreamingActionGateway();
        initializeStreamingPlaybackCoordinator();
        initializeRouteStoresAndStatus();
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
                this::adaptiveStreamingQuality,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                launch -> StreamingAuthLauncher.INSTANCE.launch(MainActivityBase.this, launch),
                (tracks, index) -> playTrackListFromHost(tracks, index),
                provider -> {
                    if (streamingPlaylistController != null) {
                        streamingPlaylistController.onStreamingLoginSuccess(provider);
                    }
                },
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
                () -> playbackStore,
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                positionMs -> nowPlayingViewModel.seekTo(positionMs),
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                )
        ));
        nowPlayingViewModel.bindPlaybackGateway(nowPlayingPlaybackGatewayFactory.create(
                () -> playbackService
        ));
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
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                status -> statusMessageController.setStatus(status),
                (trackId, favorite) -> viewModel.setFavorite(trackId, favorite),
                () -> nowPlayingStateController.renderNowBar(),
                this::renderSelectedTab,
                this::loadCollections,
                track -> {
                    if (MainActivityBase.this.playlistDialogController != null) {
                        MainActivityBase.this.playlistDialogController.showAddToPlaylist(track);
                    }
                },
                routeController,
                this::applySearch,
                () -> documentPickerController.openAudioFilePicker(),
                allowCachedFirst -> loadLibrary(allowCachedFirst)
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
        playbackStore = playbackStoreFactory.create(playbackViewModel);
        statusMessageController = new StatusMessageController(
                statusMessageViewModel,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                message -> uiShellController.updateStatus(message)
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
        permissionController = new MainPermissionController(this, permissionListenerFactory.create(
                () -> permissionController != null && permissionController.hasAudioPermission(),
                allowCachedFirst -> loadLibrary(allowCachedFirst),
                this::showOnboarding,
                this::mountNavHostShell
        ));
        documentPickerController = new DocumentPickerController(this, documentPickerListenerFactory.create(
                this::importSelectedAudioUris,
                this::importSelectedAudioFolder,
                this::setCustomDownloadFolder,
                this::importSelectedM3uFile,
                (exportUri, playlistId, playlistName) -> libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName),
                this::importSelectedPlaylistM3uFile,
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
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                page -> settingsStore == null
                        ? BackgroundTransform.IDENTITY
                        : settingsStore.pageBackgrounds().transformFor(page)
        );
        backupRestoreLauncher = new BackupRestoreLauncher(
                this,
                statusKey -> {
                    statusMessageController.setStatusKey(statusKey);
                    return kotlin.Unit.INSTANCE;
                }
        );
        final LuoxueSourceStore luoxueSourceStore = new LuoxueSourceStore(this);
        luoxueSourceImportController = new LuoxueSourceImportController(
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                ),
                () -> documentPickerController,
                new ContentResolverLuoxueSourceDocumentReader(getContentResolver()),
                sources -> luoxueSourceStore.saveAll(sources),
                task -> executors.io(task),
                task -> executors.network(task),
                task -> mainHandler.post(task),
                status -> statusMessageController.setStatus(status),
                () -> {
                    refreshStreamingProviders();
                    renderSelectedTab();
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
                playbackStore,
                this::playbackQueueSnapshot,
                playbackStateEventListenerFactory.create(
                        this::selectedTab,
                        () -> lyricsViewModel == null ? -1L : lyricsViewModel.trackId(),
                        (playbackSpeed, appVolume) -> {
                            settingsStore.setPlaybackSpeed(playbackSpeed);
                            settingsStore.setAppVolume(appVolume);
                        },
                        this::loadLyrics,
                        this::loadCollections,
                        () -> nowPlayingStateController.renderNowBar(),
                        snapshot -> homeDashboardViewModel.updatePlayback(snapshot),
                        this::renderSelectedTab,
                        this::updateNowPlayingContent,
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
                        () -> settingsStore.playbackRestoreEnabled(),
                        () -> settingsStore.replayGainEnabled(),
                        service -> playbackService = service,
                        () -> playbackService = null,
                        () -> playbackStore.reset(),
                        () -> playbackStartController.playPendingTracksIfNeeded(),
                        this::renderSelectedTab,
                        () -> nowPlayingStateController.renderNowBar()
                )
        );
        playbackServiceConnectionController = new PlaybackServiceConnectionController(
                this,
                playbackStateEventController,
                playbackServiceHostController
        );
    }

    private void initializeNavigationRendering() {
        tabRenderDispatcher = new MainTabRenderDispatcher(
                this::renderHome,
                this::renderLibrary,
                this::renderCollections,
                this::renderQueue,
                this::renderNetwork,
                this::renderSettings,
                this::renderSearch
        );
        trackListRenderController = new TrackListRenderController(libraryViewModel, trackListRenderListenerFactory.create(
                (tracks, index) -> libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                track -> libraryViewModel.onEvent(new LibraryEvent.AddToPlaylist(track)),
                track -> downloadRequestController.downloadTrack(track),
                tracks -> downloadRequestController.downloadTracks(tracks),
                track -> networkDialogController.showEditStream(track),
                track -> confirmationDialogController.confirmDeleteTrack(track),
                state -> publishTrackListChromeState(state)
        ));
    }

    private void initializePlaybackControllers() {
        heartbeatSeedBinder = new HeartbeatRecommendationSeedBinder(
                () -> playbackService == null ? null : playbackService.snapshot(),
                this::playbackQueueSnapshot,
                () -> playbackStore == null ? null : playbackStore.snapshot(),
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
        homeDashboardRenderController = new HomeDashboardRenderController(homeDashboardViewModel, homeDashboardRenderListenerFactory.create(
                mode -> {
                    routeController.setLibraryMode(mode);
                    navigateToTab(TAB_LIBRARY, true, true);
                },
                this::continueDashboardPlayback,
                () -> nowPlayingStateController.renderNowBar(),
                this::playTrackListFromHost,
                () -> loadLibrary(true),
                () -> navigateToTab(TAB_QUEUE, true, true),
                () -> libraryStore.allTracks(),
                () -> navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB),
                () -> {
                    routeController.navigateToTab(TAB_COLLECTIONS, true);
                    renderCollections();
                    renderSelectedTab();
                },
                () -> {
                    refreshUnifiedSearch(false);
                    navigateToTab(TAB_SEARCH, true, true);
                    syncNavHostState();
                },
                () -> runRecommendationAction(new RecommendationAction.PlayDaily(StreamingProviderName.NETEASE)),
                () -> runRecommendationAction(new RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE)),
                actions -> homeDashboardViewModel.updateHomeDashboardActions(actions)
        ));
        queueRenderController = new QueueRenderController(queueRenderListenerFactory.create(
                this::playTrackListFromHost,
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                track -> playlistDialogController.showAddToPlaylist(track),
                track -> queueActionController.removeQueueTrack(track),
                () -> queueActionController.confirmClearQueue(),
                this::handleAppBack
        ));
        playbackActionController = new PlaybackActionController(
                nowPlayingViewModel,
                playbackActionListenerFactory.create(
                        this::resolveCurrentStreamingQueueTrackIfNeeded,
                        () -> playbackService == null ? playbackStore.snapshot() : playbackService.snapshot(),
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
                        this::adaptiveStreamingQuality,
                        this::selectedStreamingQuality,
                        this::playbackQueueSnapshot,
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
                () -> navigateToTab(TAB_QUEUE, true, true)
        );
        playbackStartController = new PlaybackStartController(
                streamingPlaybackController::resolveAndPlayStreamingTrack,
                nowPlayingViewModel::playTrackList,
                this::applyPlaybackActionResult,
                playbackStartListener
        );
        nowPlayingStateController = new NowPlayingStateController(nowPlayingViewModel, nowPlayingStateListenerFactory.create(
                () -> playbackStore != null && libraryStore != null,
                () -> playbackStore.snapshot(),
                () -> libraryStore.favoriteIds(),
                () -> lyricsViewModel == null ? new LyricsState() : lyricsViewModel.stateSnapshot(),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                (trackTitle, artist, coverUri, playing, activeLine) -> FloatingLyricsPublisher.update(
                        trackTitle,
                        artist,
                        coverUri,
                        playing,
                        activeLine
                ),
                this::bindQueueViewModelInputs
        ));
        queueActionController = new QueueActionController(
                nowPlayingViewModel,
                queueActionListenerFactory.create(
                        this::applyPlaybackActionResult,
                        () -> playbackService != null,
                        (fromIndex, toIndex) -> playbackService.moveQueueTrack(fromIndex, toIndex),
                        () -> nowPlayingStateController.renderNowBar(),
                        this::renderSelectedTab,
                        () -> confirmationDialogController.confirmClearQueue(),
                        () -> AppLanguage.text(settingsStore.languageMode(), "queue.empty"),
                        status -> statusMessageController.setStatus(status)
                )
        );
        lyricsViewModel.bindReloadGateway(
                () -> playbackStore == null ? null : playbackStore.snapshot().currentTrack,
                this::neteaseProviderTrackIdForLyrics,
                status -> statusMessageController.setStatus(status)
        );
    }

    private void initializeSettingsEffects() {
        settingsViewModel.bindEffectListener(effect -> {
            if (effect instanceof SettingsEffect.ShowStatus) {
                statusMessageController.setStatus(((SettingsEffect.ShowStatus) effect).getMessage());
            } else if (effect == SettingsEffect.OpenNetworkSources.INSTANCE) {
                navigateToNetworkTabPage(NETWORK_HOME);
            } else if (effect == SettingsEffect.OpenDownloads.INSTANCE) {
                navigateToTab(TAB_DOWNLOADS, true, true);
            } else if (effect == SettingsEffect.LoadLibrary.INSTANCE) {
                loadLibrary(false);
            } else if (effect == SettingsEffect.OpenAudioFilePicker.INSTANCE) {
                documentPickerController.openAudioFilePicker();
            } else if (effect == SettingsEffect.OpenAudioFolderPicker.INSTANCE) {
                documentPickerController.openAudioFolderPicker();
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
                        () -> loadLibrary(true),
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
                        status -> statusMessageController.setStatus(status),
                        () -> MainActivityBase.this.renderSelectedTab()
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
        settingsStore.load(loadSettingsPreferencesUseCase.execute());
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
                        () -> navigateNetworkPage(MainRoutes.NETWORK_HOME),
                        streamingSearchActionHandler,
                        () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                        (provider, providerPlaylistId) ->
                                streamingPlaylistController.importStreamingPlaylistFromProviderRef(provider, providerPlaylistId),
                        provider -> streamingPlaylistController.showAccountPlaylistSyncPicker(provider),
                        provider -> streamingPlaylistController.importStreamingLikedTracks(provider),
                        action -> runRecommendationAction(action),
                        () -> streamingPlaylistImportDialogController.showImportDialog(),
                        () -> streamingManualCookieController.showStreamingCookieDialog(),
                        (labels, actions) -> streamingViewModel.updateStreamingSearchChrome(labels, actions)
                ));
        initializeRenderOwners(streamingSearchRenderController);
        return streamingSearchRenderController;
    }

    private void initializeRenderOwners(StreamingSearchRenderController streamingSearchRenderController) {
        libraryGroupsRenderController = new LibraryGroupsRenderController(
                libraryViewModel,
                libraryGroupsRenderListenerFactory.create(
                        (key, title) -> libraryViewModel.onEvent(new LibraryEvent.OpenGroup(key, title)),
                        () -> routeController.clearLibraryGroup(),
                        () -> libraryViewModel.onEvent(LibraryEvent.BackFromGroup.INSTANCE),
                        () -> settingsStore.languageMode(),
                        (tracks, index) -> libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                        (title, tracks) -> confirmationDialogController.confirmDeleteTracks(title, tracks),
                        this::publishLibraryGroupsChromeState,
                        this::renderLibraryGroupTrackList
                ),
                artistInfoRepository,
                action -> mainHandler.post(action)
        );
        libraryPlaylistsRenderController = new LibraryPlaylistsRenderController(libraryViewModel, new LibraryPlaylistsRenderController.Listener() {
            @Override
            public void openFavoritePlaylist(String title) {
                libraryViewModel.onEvent(new LibraryEvent.OpenGroup("virtual:favorites", title));
            }

            @Override
            public void openPlayHistory(String title) {
                libraryViewModel.onEvent(new LibraryEvent.OpenGroup("virtual:play-history", title));
            }

            @Override
            public void openPlaylist(long playlistId, String title) {
                libraryViewModel.onEvent(new LibraryEvent.OpenPlaylist(playlistId, title));
            }

            @Override
            public void playPlaylist(long playlistId) {
                libraryViewModel.onEvent(new LibraryEvent.PlayPlaylist(playlistId));
            }

            @Override
            public void confirmDeletePlaylist(Playlist playlist) {
                playlistDialogController.confirmDeletePlaylist(playlist);
            }

            @Override
            public void backFromPlaylist() {
                libraryViewModel.onEvent(LibraryEvent.BackFromGroup.INSTANCE);
            }

            @Override
            public void playTrackList(List<Track> tracks, int index) {
                libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index));
            }

            @Override
            public void publishLibraryGroupsChrome(LibraryGroupsChromeState state) {
                publishLibraryGroupsChromeState(state);
            }

            @Override
            public void renderPlaylistTracks(LibraryPlaylistTrackListRequest request) {
                renderLibraryPlaylistTrackList(request);
            }
        });
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
                        libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction, MainActivityBase.this::onSelectedPlaylistTrackMoved),
                (playlistId, track) ->
                        libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track, MainActivityBase.this::onSelectedPlaylistTrackRemoved)
        ));
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
        settingsViewModel.bindPreferenceGateway(applySettingsPreferenceUseCase::execute);
        settingsViewModel.bindStoreMirror(settingsStore::sync);
        SettingsRuntimeApplier settingsRuntimeApplier = settingsRuntimeApplierFactory.create(
                () -> uiShellController.applyThemeSurface(),
                () -> playbackService == null ? null : new MainSettingsPlaybackServiceControls(playbackService),
                () -> lyricsViewModel,
                () -> permissionController
        );
        settingsViewModel.bindRuntimeEffectListener(settingsRuntimeApplier::apply);
        playlistDialogController = createPlaylistDialogController();
    }

    private void initializeNetworkOwners(StreamingSearchRenderController streamingSearchRenderController) {
        networkActionsViewModel.bindUseCases(networkActionUseCases);
        networkActionsViewModel.bindListener(networkActionsListenerFactory.create(
                nowPlayingViewModel,
                this::replaceLibrary,
                this::navigateToNetworkTabPage,
                this::loadCollections,
                status -> statusMessageController.setStatus(status)
        ));
        networkRequestController = new NetworkRequestController(
                networkActionsViewModel,
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status)
        );
        lyricsViewModel.bindListener(new LyricsStateRefreshListener(
                this::selectedTab,
                () -> nowPlayingStateController.renderNowBar(),
                this::updateNowPlayingContent,
                this::renderSelectedTab
        ));
        settingsContextProvider = new SettingsContextProvider(
                settingsStore,
                libraryStore,
                permissionController,
                playbackServiceConnectionController,
                playbackStore,
                lyricsViewModel,
                streamingGatewaySettingsStore
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
                status -> statusMessageController.setStatus(status),
                this::renderAndPersistSelectedTab
        );
        NetworkTrackListRenderController trackListRenderer =
                new NetworkTrackListRenderController(new NetworkTrackListRenderController.Listener() {
                    @Override
                    public void navigateNetworkPage(String page) {
                        MainActivityBase.this.navigateNetworkPage(page);
                    }

                    @Override
                    public void clearRemoteSourceAndNavigateNetworkPage(String page) {
                        routeController.clearSelectedRemoteSource();
                        MainActivityBase.this.navigateNetworkPage(page);
                    }

                    @Override
                    public void syncRemoteSource(long sourceId) {
                        sourcesEvents.syncRemoteSource(sourceId);
                    }

                    @Override
                    public void playRemoteSourceTracks(RemoteSource source) {
                        sourcesEvents.playRemoteSourceTracks(source);
                    }

                    @Override
                    public void renderTrackList(
                            String title,
                            List<Track> tracks,
                            boolean showPlaylistAction,
                            List<String> details,
                            boolean showStreamActions,
                            List<TrackListHeaderMetric> headerMetrics,
                            List<TrackListHeaderAction> headerActions,
                            String emptyText,
                            TrackListLabels labels
                    ) {
                        renderNetworkTrackList(new NetworkTrackListRequest(
                                title,
                                tracks,
                                showPlaylistAction,
                                details,
                                showStreamActions,
                                headerMetrics,
                                headerActions,
                                emptyText,
                                labels
                        ));
                    }
                });
        networkRenderCoordinator = new NetworkRenderCoordinator(
                libraryStore,
                new NetworkMenuRenderController(menuEvents),
                trackListRenderer,
                new NetworkSourcesRenderController(networkSourcesViewModel, sourcesEvents),
                streamingSearchRenderController
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
            public void mountNavHostShell() {
                MainActivityBase.this.mountNavHostShell();
            }

            @Override
            public void loadLibrary(boolean allowCachedFirst) {
                MainActivityBase.this.loadLibrary(allowCachedFirst);
            }

            @Override
            public void navigateToNetworkTabPage(String page) {
                MainActivityBase.this.navigateToNetworkTabPage(page);
            }

            @Override
            public void renderAndPersistSelectedTab() {
                MainActivityBase.this.renderAndPersistSelectedTab();
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
        });
        onboardingController.initialize(repository == null || !repository.loadOnboardingCompleted());
        mountNavHostShell();
        installBackNavigation();
        playbackServiceConnectionController.bind();
        if (!showOnboarding()) {
            permissionController.requestNeededPermissions();
            if (permissionController.hasAudioPermission()) {
                loadLibrary(false);
            } else {
                loadLibrary(true);
            }
        } else {
            loadLibrary(true);
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
        // Manual sync only; no automatic periodic sync.
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
        if (playbackServiceConnectionController != null) {
            playbackServiceConnectionController.release();
        }
        if (streamingPlaybackTaskScheduler != null) {
            streamingPlaybackTaskScheduler.shutdownNow();
        }
        executors.shutdownNow();
        super.onDestroy();
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
        if (playbackStore.snapshot().hasTrack()) {
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
            syncStreamingProvidersAndRender();
            return kotlin.Unit.INSTANCE;
        });
        return job;
    }

    private kotlinx.coroutines.Job applyStreamingGatewayEndpoint(String endpoint) {
        streamingGatewaySettingsStore.setEndpoint(endpoint);
        streamingViewModel.configureStreamingRepository();
        kotlinx.coroutines.Job refreshJob = refreshStreamingProviders();
        renderSelectedTab();
        statusMessageController.setStatus(
                AppLanguage.text(settingsStore.languageMode(), "streaming.gateway.applied")
                        + streamingGatewaySettingsStore.endpoint()
        );
        return refreshJob;
    }

    private void syncStreamingProvidersAndRender() {
        streamingRecommendationViewModel.updateProviders(streamingViewModel.getState().getProviders());
        renderSelectedTab();
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

    private void renderLibraryGroupTrackList(LibraryGroupTrackListRequest request) {
        renderComposeTrackList(
                request.getTitle(),
                request.getTracks(),
                true,
                new ArrayList<String>(),
                false,
                request.getHeaderMetrics(),
                request.getHeaderActions(),
                "",
                new ArrayList<TrackListModeAction>(),
                new TrackListLabels(),
                request.getFooterAlbums()
        );
    }

    private void renderLibraryPlaylistTrackList(LibraryPlaylistTrackListRequest request) {
        renderComposeTrackList(
                request.getTitle(),
                request.getTracks(),
                true,
                new ArrayList<String>(),
                false,
                request.getHeaderMetrics(),
                request.getHeaderActions(),
                request.getEmptyText(),
                request.getModeActions()
        );
    }

    private void publishTrackListChromeState(TrackListChromeState state) {
        libraryViewModel.updateTrackListChrome(
                new LibraryTrackListDestinationState(
                        "",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        new ArrayList<>(state.getActions()),
                        new ArrayList<>(state.getHeaderMetrics()),
                        new ArrayList<>(state.getHeaderActions()),
                        state.getEmptyText(),
                        new ArrayList<>(state.getModeActions()),
                        state.getLabels()
                )
        );
    }

    private void publishLibraryGroupsChromeState(LibraryGroupsChromeState state) {
        libraryViewModel.updateLibraryGroupsChrome(
                new LibraryGroupsDestinationState(
                        "",
                        Collections.emptyList(),
                        new ArrayList<>(state.getActions()),
                        state.getEmptyText(),
                        new ArrayList<>(state.getModeActions())
                )
        );
    }

    private void renderNetworkTrackList(NetworkTrackListRequest request) {
        renderComposeTrackList(
                request.getTitle(),
                request.getTracks(),
                request.getShowPlaylistAction(),
                request.getDetails(),
                request.getShowStreamActions(),
                request.getHeaderMetrics(),
                request.getHeaderActions(),
                request.getEmptyText(),
                request.getLabels()
        );
    }

    private void mountNavHostShell() {
        if (queueViewModel == null) {
            return;
        }
        bindQueueViewModelInputs();
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
        renderSelectedTabForNavHostState();
        syncNavHostState();
        EchoAppHost.installNavHost(this, new ActivityNavHostMount());
    }

    private void renderSelectedTabForNavHostState() {
        if (routeController != null) {
            routeController.persist();
        }
        tabRenderDispatcher.render(selectedTab());
    }

    private void syncNavHostState() {
        if (navHostState == null) {
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
                    selectedTab(),
                    downloadsViewModel.getUiState(),
                    downloadsViewModel.openDirectoryRequests(),
                    downloadsDestinationActions(),
                    searchViewModel.getUiState(),
                    trackDownloadManager,
                    () -> playbackService == null ? 0f : playbackService.realtimeBeat(),
                    () -> playbackService == null ? EMPTY_REALTIME_BANDS : playbackService.realtimeBands(),
                    true
            );
            return;
        }
        navHostState.setSelectedTabRoute(selectedTab());
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

    private void bindQueueViewModelInputs() {
        if (queueViewModel == null) {
            return;
        }
        queueViewModel.bind(
                playbackQueueSnapshot(),
                playbackStore == null ? null : playbackStore.snapshot(),
                libraryStore == null ? java.util.Collections.<Long>emptySet() : libraryStore.favoriteIds(),
                settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()
        );
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
            return () -> navigateToTab(TAB_HOME, true, true);
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
            syncNavHostState();
            return navHostState;
        }

        @Override
        public boolean showOnboarding() {
            return onboardingController != null && onboardingController.showOnboarding();
        }

        @Override
        public boolean audioPermissionGranted() {
            return permissionController != null && permissionController.hasAudioPermission();
        }

        @Override
        public boolean notificationPermissionGranted() {
            return permissionController != null && permissionController.hasNotificationPermission();
        }

        @Override
        public boolean libraryScanCompleted() {
            return onboardingController != null && onboardingController.libraryScanCompleted();
        }

        @Override
        public boolean libraryScanInProgress() {
            return onboardingController != null && onboardingController.libraryScanInProgress();
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
            navigateToTab(tab.getRoute(), true, true);
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
            navigateToTab(result.selectedTab);
        } else {
            renderAndPersistSelectedTab();
        }
        return true;
    }

    private void loadLibrary(final boolean allowCachedFirst) {
        final boolean canScan = permissionController.hasAudioPermission();
        if (!canScan) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "audio.permission.required"));
        }
        libraryViewModel.loadLibraryJava(
                allowCachedFirst,
                canScan,
                result -> {
                    replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus());
                    if (canScan && !allowCachedFirst) {
                        statusMessageController.setStatus(libraryScanResultStatus(result.getTracks().size()));
                    }
                    if (onboardingController != null) {
                        onboardingController.onLibraryScanResult(canScan);
                    }
                },
                status -> {
                    statusMessageController.setStatus(status);
                    renderSelectedTab();
                }
        );
    }

    private String libraryScanResultStatus(int trackCount) {
        if (trackCount <= 0) {
            return AppLanguage.text(settingsStore.languageMode(), "no.music");
        }
        String languageMode = settingsStore.languageMode();
        return AppLanguage.text(languageMode, "library.scan.found.prefix")
                + trackCount
                + AppLanguage.text(languageMode, "library.scan.found.suffix");
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

    private void importSelectedAudioUris(final List<Uri> uris) {
        if (uris.isEmpty()) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "no.audio.files.selected"));
            return;
        }
        statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "importing.audio.files"));
        libraryViewModel.importAudioUrisJava(
                uris,
                result -> replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus())
        );
    }

    private void importSelectedAudioFolder(final Uri treeUri) {
        statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "importing.audio.folder"));
        libraryViewModel.importAudioTreeJava(
                treeUri,
                result -> replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus())
        );
    }

    private void importSelectedM3uFile(final Uri playlistUri) {
        statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "importing.m3u.playlist"));
        libraryViewModel.importStreamM3uJava(
                playlistUri,
                result -> {
                    replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus());
                    navigateToNetworkTabPage(NETWORK_STREAMING);
                }
        );
    }

    private void importSelectedPlaylistM3uFile(final Uri playlistUri) {
        statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "import.playlist.m3u"));
        libraryViewModel.importPlaylistM3uJava(
                playlistUri,
                result -> {
                    if (result.getPlaylistId() >= 0L) {
                        routeController.setSelectedPlaylistId(result.getPlaylistId());
                    }
                    replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus());
                }
        );
    }

    private void replaceLibrary(List<Track> tracks, Set<Long> favorites, String status) {
        libraryStore.replaceLibrary(tracks, favorites, searchQuery());
        renderAndPersistSelectedTab();
        statusMessageController.setStatus(status);
        loadCollections();
        libraryViewModel.parseMissingAudioSpecsJava(result -> applyBackgroundAudioSpecs(
                result.getTracks(),
                result.getFavorites(),
                result.getUpdatedCount()
        ));
    }

    private void applyBackgroundAudioSpecs(List<Track> tracks, Set<Long> favorites, int updatedCount) {
        libraryStore.replaceLibrary(tracks, favorites, searchQuery());
        renderAndPersistSelectedTab();
        loadCollections();
        if (updatedCount > 0) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "audio.specs.updated") + " (" + updatedCount + ")");
        }
    }

    private void loadCollections() {
        libraryViewModel.loadCollectionsJava(selectedPlaylistId(), result -> {
            routeController.setSelectedPlaylistId(result.getSelectedPlaylistId());
            libraryStore.applyCollections(result);
            nowPlayingStateController.renderNowBar();
            if (TAB_COLLECTIONS.equals(selectedTab())
                    || (TAB_LIBRARY.equals(selectedTab()) && LIBRARY_PLAYLISTS.equals(libraryMode()))
                    || TAB_NETWORK.equals(selectedTab())
                    || TAB_SETTINGS.equals(selectedTab())) {
                renderSelectedTab();
            }
        });
    }

    private void refreshUnifiedSearch(boolean searchOnline) {
        if (searchViewModel == null || libraryStore == null) {
            return;
        }
        String query = searchQuery();
        List<Track> localMatches = repository == null
                ? Collections.emptyList()
                : repository.search(libraryStore.allTracks(), query);
        searchViewModel.updateResults(query, localMatches);
        if (searchOnline && streamingSearchActionHandler != null && query != null && !query.trim().isEmpty()) {
            streamingSearchActionHandler.search(query);
        }
    }

    private void performUnifiedSearch(String query) {
        String safeQuery = query == null ? "" : query.trim();
        routeController.setSearchQuery(safeQuery);
        refreshUnifiedSearch(true);
        syncNavHostState();
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
        renderSelectedTabForNavHostState();
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
                selectedStreamingQuality(),
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

    private void switchNowPlayingSource(final NowPlayingEffect.SwitchSource effect) {
        if (effect == null || effect.getTrack() == null) {
            statusMessageController.showFeedback("\u5f53\u524d\u6ca1\u6709\u53ef\u5207\u6362\u7684\u6b4c\u66f2");
            return;
        }
        if (streamingViewModel == null || nowPlayingViewModel == null) {
            statusMessageController.showFeedback("\u97f3\u6e90\u5207\u6362\u6682\u4e0d\u53ef\u7528");
            return;
        }
        final Track current = effect.getTrack();
        final long positionMs = playbackStore == null || playbackStore.snapshot() == null
                ? 0L
                : playbackStore.snapshot().positionMs;
        final app.yukine.streaming.StreamingAudioQuality quality =
                effect.getQuality() == null ? selectedStreamingQuality() : effect.getQuality();
        statusMessageController.showFeedback("\u6b63\u5728\u5207\u6362\u97f3\u6e90\uff1a" + effect.getProvider().getWireName());
        streamingViewModel.resolveStreamingTrackForPlayback(
                effect.getProvider(),
                effect.getProviderTrackId(),
                resolveStreamingPlaybackUseCase.metadataFor(current, effect.getProvider(), effect.getProviderTrackId()),
                quality,
                resolved -> {
                    if (resolved == null) {
                        statusMessageController.showFeedback("\u97f3\u6e90\u5207\u6362\u5931\u8d25\uff0c\u8bf7\u6362\u4e00\u4e2a\u6765\u6e90\u518d\u8bd5");
                        return;
                    }
                    nowPlayingViewModel.replaceCurrentTrackAndResume(resolved, positionMs);
                    statusMessageController.showFeedback("\u5df2\u5207\u6362\u97f3\u6e90\uff1a" + resolved.title);
                    publishPlaybackStore();
                    nowPlayingStateController.renderNowBar();
                    renderSelectedTabForNavHostState();
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
        libraryStore.applySearch(query);
        if (TAB_SEARCH.equals(selectedTab())) {
            refreshUnifiedSearch(true);
        }
        if (TAB_NETWORK.equals(selectedTab())
                && (NETWORK_STREAMING.equals(networkPage())
                || MainRoutes.NETWORK_STREAMING_HUB.equals(networkPage()))) {
            if (streamingSearchActionHandler != null) {
                streamingSearchActionHandler.search(searchQuery());
            }
        }
        if (query != null && !query.trim().isEmpty() && libraryStore.visibleTracks().isEmpty()) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "search.no.results"));
        }
        renderAndPersistSelectedTab();
    }

    private void renderAndPersistSelectedTab() {
        if (routeController != null) {
            routeController.persist();
        }
        renderSelectedTab();
    }

    private void navigateToTab(String tabKey) {
        navigateToTab(tabKey, false, false);
    }

    private void navigateToTab(String tabKey, boolean userInitiated, boolean renderImmediately) {
        tabKey = normalizedTabKey(tabKey);
        String previousDirectory = currentDirectoryKey();
        boolean sameTab = routeController.navigateToTab(tabKey, userInitiated);
        if (userInitiated && sameTab && previousDirectory.equals(currentDirectoryKey())) {
            requestCurrentDirectoryScrollToTop();
        }
        if (routeController != null) {
            routeController.persist();
        }
        if (renderImmediately || sameTab) {
            renderSelectedTab();
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
        renderAndPersistSelectedTab();
        return true;
    }

    private boolean closeLibraryDetailIfNeeded() {
        if (selectedLibraryGroupKey().isEmpty() && selectedPlaylistId() < 0L) {
            return false;
        }
        routeController.clearLibraryGroup();
        routeController.setSelectedPlaylistId(-1L);
        renderAndPersistSelectedTab();
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
        renderAndPersistSelectedTab();
    }

    private void navigateToNetworkTabPage(String page) {
        routeController.setNetworkPage(page);
        navigateToTab(TAB_NETWORK);
    }

    private String normalizedTabKey(String tabKey) {
        return TAB_NOW.equals(tabKey) ? TAB_HOME : tabKey;
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
        scrollContentToTopOnNextRender = true;
        if (TAB_SETTINGS.equals(selectedTab())) {
            settingsViewModel.scrollToTopOnNextRender();
        }
    }

    private void renderSelectedTab() {
        if (routeController != null) {
            routeController.persist();
        }
        renderSelectedTabForNavHostState();
        syncNavHostState();
    }

    private void renderHome() {
        boolean anyStreamingConnected = false;
        for (app.yukine.streaming.StreamingAuthState auth : streamingViewModel.getStreaming().getValue().getAuthStates().values()) {
            if (auth.getConnected()) { anyStreamingConnected = true; break; }
        }
        if (!anyStreamingConnected) {
            for (app.yukine.streaming.StreamingProviderDescriptor provider : streamingViewModel.getStreaming().getValue().getProviders()) {
                if (provider.getAuth().getConnected()) { anyStreamingConnected = true; break; }
            }
        }
        homeDashboardRenderController.render(
                settingsStore.languageMode(),
                libraryStore.allTracks(),
                libraryStore.allTracks(),
                libraryStore.recentRecords(),
                playbackStore.snapshot(),
                anyStreamingConnected
        );
    }

    private void renderLibrary() {
        if (!permissionController.hasAudioPermission()) {
            statusMessageController.setStatus(
                    AppLanguage.text(settingsStore.languageMode(), "audio.permission.required") + ": "
                            + AppLanguage.text(settingsStore.languageMode(), "audio.permission.description")
            );
            return;
        }
        if (libraryStore.visibleTracks().isEmpty() && !LIBRARY_PLAYLISTS.equals(libraryMode())) {
            statusMessageController.setStatus(
                    AppLanguage.text(settingsStore.languageMode(), "no.music") + ": "
                            + AppLanguage.text(settingsStore.languageMode(), "no.music.description")
            );
            return;
        }
        if (LIBRARY_PLAYLISTS.equals(libraryMode())) {
            renderLibraryPlaylists();
            return;
        }
        if (!LIBRARY_SONGS.equals(libraryMode())) {
            renderLibraryGroups();
            return;
        }
        renderComposeTrackList(AppLanguage.text(settingsStore.languageMode(), "songs"), libraryStore.visibleTracks(), true, new ArrayList<String>(), false, new ArrayList<TrackListHeaderMetric>(), new ArrayList<TrackListHeaderAction>(), "", libraryModeActions());
    }

    private void renderComposeTrackList(String title, final List<Track> tracks, boolean showPlaylistAction) {
        ArrayList<String> details = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            details.add("");
        }
        renderComposeTrackList(title, tracks, showPlaylistAction, details);
    }

    private void renderComposeTrackList(String title, final List<Track> tracks, boolean showPlaylistAction, List<String> details) {
        renderComposeTrackList(title, tracks, showPlaylistAction, details, false, new ArrayList<TrackListHeaderMetric>(), new ArrayList<TrackListHeaderAction>(), "", new ArrayList<TrackListModeAction>());
    }

    private void renderComposeTrackList(
            String title,
            final List<Track> tracks,
            boolean showPlaylistAction,
            List<String> details,
            boolean showStreamActions
    ) {
        renderComposeTrackList(title, tracks, showPlaylistAction, details, showStreamActions, new ArrayList<TrackListHeaderMetric>(), new ArrayList<TrackListHeaderAction>(), "", new ArrayList<TrackListModeAction>());
    }

    private void renderComposeTrackList(
            String title,
            final List<Track> tracks,
            boolean showPlaylistAction,
            List<String> details,
            boolean showStreamActions,
            List<TrackListHeaderMetric> headerMetrics,
            List<TrackListHeaderAction> headerActions,
            String emptyText
    ) {
        renderComposeTrackList(title, tracks, showPlaylistAction, details, showStreamActions, headerMetrics, headerActions, emptyText, new ArrayList<TrackListModeAction>());
    }

    private void renderComposeTrackList(
            String title,
            final List<Track> tracks,
            boolean showPlaylistAction,
            List<String> details,
            boolean showStreamActions,
            List<TrackListHeaderMetric> headerMetrics,
            List<TrackListHeaderAction> headerActions,
            String emptyText,
            List<TrackListModeAction> modeActions
    ) {
        renderComposeTrackList(title, tracks, showPlaylistAction, details, showStreamActions, headerMetrics, headerActions, emptyText, modeActions, new TrackListLabels());
    }

    private void renderComposeTrackList(
            String title,
            final List<Track> tracks,
            boolean showPlaylistAction,
            List<String> details,
            boolean showStreamActions,
            List<TrackListHeaderMetric> headerMetrics,
            List<TrackListHeaderAction> headerActions,
            String emptyText,
            TrackListLabels labels
    ) {
        renderComposeTrackList(title, tracks, showPlaylistAction, details, showStreamActions, headerMetrics, headerActions, emptyText, new ArrayList<TrackListModeAction>(), labels);
    }

    private void renderComposeTrackList(
            String title,
            final List<Track> tracks,
            boolean showPlaylistAction,
            List<String> details,
            boolean showStreamActions,
            List<TrackListHeaderMetric> headerMetrics,
            List<TrackListHeaderAction> headerActions,
            String emptyText,
            List<TrackListModeAction> modeActions,
            TrackListLabels labels
    ) {
        renderComposeTrackList(
                title,
                tracks,
                showPlaylistAction,
                details,
                showStreamActions,
                headerMetrics,
                headerActions,
                emptyText,
                modeActions,
                labels,
                new ArrayList<TrackListAlbumCardUiState>()
        );
    }

    private void renderComposeTrackList(
            String title,
            final List<Track> tracks,
            boolean showPlaylistAction,
            List<String> details,
            boolean showStreamActions,
            List<TrackListHeaderMetric> headerMetrics,
            List<TrackListHeaderAction> headerActions,
            String emptyText,
            List<TrackListModeAction> modeActions,
            TrackListLabels labels,
            List<TrackListAlbumCardUiState> footerAlbums
    ) {
        trackListRenderController.render(
                title,
                tracks,
                showPlaylistAction,
                details,
                showStreamActions,
                headerMetrics,
                headerActions,
                emptyText,
                modeActions,
                labels,
                playbackStore.snapshot(),
                libraryStore.favoriteIds(),
                footerAlbums
        );
    }

    private ArrayList<TrackListModeAction> libraryModeActions() {
        String languageMode = settingsStore.languageMode();
        ArrayList<TrackListModeAction> modes = new ArrayList<>();
        addLibraryModeAction(modes, AppLanguage.text(languageMode, "songs"), LIBRARY_SONGS);
        addLibraryModeAction(modes, AppLanguage.text(languageMode, "albums"), LIBRARY_ALBUMS);
        addLibraryModeAction(modes, AppLanguage.text(languageMode, "artists"), LIBRARY_ARTISTS);
        addLibraryModeAction(modes, AppLanguage.text(languageMode, "folders"), LIBRARY_FOLDERS);
        addLibraryModeAction(modes, AppLanguage.text(languageMode, "playlists"), LIBRARY_PLAYLISTS);
        return modes;
    }

    private void addLibraryModeAction(ArrayList<TrackListModeAction> modes, String label, final String mode) {
        modes.add(new TrackListModeAction(label, mode, mode.equals(libraryMode()), new Runnable() {
            @Override
            public void run() {
                libraryViewModel.onEvent(new LibraryEvent.ChangeGroupMode(mode));
            }
        }));
    }

    private void renderLibraryPlaylists() {
        libraryPlaylistsRenderController.render(
                settingsStore.languageMode(),
                libraryStore.playlists(),
                selectedPlaylistId(),
                selectedLibraryGroupKey(),
                selectedPlaylistName(),
                libraryStore.filteredSelectedPlaylistTracks(searchQuery()),
                libraryStore.favoriteTracks(),
                libraryStore.recentRecords(),
                libraryModeActions()
        );
    }

    private void renderLibraryGroups() {
        libraryGroupsRenderController.render(
                settingsStore.languageMode(),
                libraryStore.visibleTracks(),
                libraryMode(),
                selectedLibraryGroupKey(),
                selectedLibraryGroupTitle(),
                libraryModeActions()
        );
    }

    private void renderCollections() {
        collectionsRenderController.render(
                settingsStore.languageMode(),
                libraryStore.favoriteTracks(),
                libraryStore.recentRecords(),
                libraryStore.mostPlayedRecords(),
                libraryStore.playlists(),
                libraryStore.selectedPlaylistTracks(),
                selectedPlaylistId(),
                playbackStore.snapshot(),
                libraryStore.favoriteIds(),
                repository.loadRecentlyAdded(30),
                repository.loadLongUnplayed(30)
        );
    }

    private String selectedPlaylistName() {
        return libraryStore.selectedPlaylistName(selectedPlaylistId());
    }

    private PlaylistDialogController createPlaylistDialogController() {
        return new PlaylistDialogController(
                this,
                () -> settingsStore.languageMode(),
                () -> libraryStore.playlists(),
                new PlaylistDialogController.Listener() {
                    @Override
                    public void createPlaylist(String name) {
                        libraryViewModel.createPlaylistJava(name, MainActivityBase.this::onPlaylistCreated);
                    }

                    @Override
                    public void renamePlaylist(long playlistId, String name) {
                        libraryViewModel.renamePlaylistJava(playlistId, name, MainActivityBase.this::onPlaylistRenamed);
                    }

                    @Override
                    public void deletePlaylist(long playlistId, String name) {
                        libraryViewModel.deletePlaylistJava(playlistId, name, MainActivityBase.this::onPlaylistDeleted);
                    }

                    @Override
                    public void addToDefaultPlaylist(Track track) {
                        libraryViewModel.addToDefaultPlaylistJava(track, (playlistId, added) ->
                                MainActivityBase.this.onDefaultPlaylistTrackAdded(playlistId, added)
                        );
                    }

                    @Override
                    public void addTrackToPlaylist(long playlistId, long trackId) {
                        libraryViewModel.addTrackToPlaylistJava(playlistId, trackId, MainActivityBase.this::onTrackAddedToPlaylist);
                    }
                }
        );
    }

    private void onDefaultPlaylistTrackAdded(long playlistId, boolean added) {
        statusMessageController.setStatus(
                libraryViewModel.defaultPlaylistAddPresentation(added, settingsStore.languageMode()).getStatus()
        );
        routeController.setSelectedPlaylistId(playlistId);
        loadCollections();
    }

    private void onPlaylistCreated(long playlistId) {
        if (playlistId >= 0L) {
            routeController.setSelectedPlaylistId(playlistId);
        }
        statusMessageController.setStatus(
                libraryViewModel.playlistCreatedPresentation(settingsStore.languageMode()).getStatus()
        );
        loadCollections();
    }

    private void onPlaylistRenamed(long playlistId, boolean renamed) {
        if (renamed) {
            routeController.setSelectedPlaylistId(playlistId);
        }
        statusMessageController.setStatus(
                libraryViewModel.playlistRenamedPresentation(renamed, settingsStore.languageMode()).getStatus()
        );
        loadCollections();
    }

    private void onPlaylistDeleted(long playlistId, String name, boolean deleted) {
        if (deleted && selectedPlaylistId() == playlistId) {
            routeController.setSelectedPlaylistId(-1L);
        }
        statusMessageController.setStatus(
                libraryViewModel.playlistDeletedPresentation(name, deleted, settingsStore.languageMode()).getStatus()
        );
        loadCollections();
    }

    private void onSelectedPlaylistTrackRemoved(long playlistId, Track track) {
        routeController.setSelectedPlaylistId(playlistId);
        statusMessageController.setStatus(
                libraryViewModel.selectedPlaylistTrackRemovedPresentation(track, settingsStore.languageMode()).getStatus()
        );
        loadCollections();
    }

    private void onSelectedPlaylistTrackMoved(long playlistId, Track track, int direction, boolean moved) {
        routeController.setSelectedPlaylistId(playlistId);
        statusMessageController.setStatus(
                libraryViewModel.selectedPlaylistTrackMovedPresentation(track, direction, moved, settingsStore.languageMode()).getStatus()
        );
        loadCollections();
    }

    private void onTrackAddedToPlaylist(long playlistId, boolean added) {
        routeController.setSelectedPlaylistId(playlistId);
        statusMessageController.setStatus(
                libraryViewModel.trackAddedToPlaylistPresentation(added, settingsStore.languageMode()).getStatus()
        );
        loadCollections();
    }

    private void renderQueue() {
        if (playbackService == null) {
            statusMessageController.showFeedback(
                    AppLanguage.text(settingsStore.languageMode(), "playback.service.unavailable")
                            + ": "
                            + AppLanguage.text(settingsStore.languageMode(), "playback.service.unavailable.description")
            );
            return;
        }
        queueRenderController.render(
                playbackQueueSnapshot(),
                playbackStore.snapshot(),
                libraryStore.favoriteIds(),
                settingsStore.languageMode()
        );
    }

    private boolean updateNowPlayingContent() {
        return playbackStore.snapshot().currentTrack != null;
    }

    private void renderNetwork() {
        networkRenderCoordinator.render(settingsStore.languageMode(), networkPage(), selectedRemoteSourceId(), searchQuery());
    }

    private void renderSearch() {
        app.yukine.ui.UnifiedSearchActions searchActions = new app.yukine.ui.UnifiedSearchActions(
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
        );
        searchViewModel.updateActions(searchActions);
        refreshUnifiedSearch(false);
    }

    private void renderSettings() {
        if (settingsContextProvider == null) {
            return;
        }
        settingsViewModel.renderPageFromHost(
                SettingsPage.fromRoute(settingsPage()),
                settingsContextProvider.preferencesSnapshot(),
                settingsContextProvider.runtimeStatus()
        );
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
        if (playbackStore != null && libraryStore != null) {
            nowPlayingStateController.publish(playbackStore.snapshot());
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
                navigateToTab(TAB_QUEUE, true, true);
            } else if (effect instanceof NowPlayingEffect.OpenAddToPlaylist openAddToPlaylist) {
                playlistDialogController.showAddToPlaylist(openAddToPlaylist.getTrack());
            } else if (effect instanceof NowPlayingEffect.ShareTrack shareTrack) {
                trackShareLauncher.share(shareTrack.getTrack());
            } else if (effect instanceof NowPlayingEffect.DownloadTrack downloadTrack) {
                downloadRequestController.downloadTrack(downloadTrack.getTrack());
            } else if (effect instanceof NowPlayingEffect.SwitchSource switchSource) {
                switchNowPlayingSource(switchSource);
            } else if (effect instanceof NowPlayingEffect.ShowMessage showMessage) {
                statusMessageController.setStatus(showMessage.getMessage());
            }
        }
    }

    private boolean resolveCurrentStreamingQueueTrackIfNeeded() {
        if (playbackService == null) {
            return false;
        }
        StreamingQueueResolveTarget target = streamingViewModel.prepareCurrentStreamingQueueResolveTarget(
                playbackService.snapshot(),
                playbackQueueSnapshot()
        );
        return target != null && streamingPlaybackController.resolveAndPlayStreamingTrack(target.getTracks(), target.getIndex());
    }

    /**
     * Chooses the selected streaming quality. Auto mode still adapts to the active network.
     */
    private app.yukine.streaming.StreamingAudioQuality adaptiveStreamingQuality() {
        return StreamingQualityPreference.playbackQuality(
                this,
                settingsStore == null ? null : settingsStore.streamingAudioQuality()
        );
    }

    private app.yukine.streaming.StreamingAudioQuality selectedStreamingQuality() {
        String selected = settingsStore == null
                ? StreamingQualityPreference.defaultValue()
                : settingsStore.streamingAudioQuality();
        return StreamingQualityPreference.AUTO.equals(
                StreamingQualityPreference.normalize(selected)
        ) ? adaptiveStreamingQuality() : StreamingQualityPreference.ceilingFor(selected);
    }

    private void applyPlaybackActionResult(PlaybackActionResultUi result) {
        if (result == null) {
            return;
        }
        PlaybackStateSnapshot snapshot = result.snapshot;
        if (snapshot != null) {
            playbackStore.replaceSnapshot(snapshot);
        }
        String status = result.status;
        if (status != null && !status.trim().isEmpty()) {
            statusMessageController.setStatus(status);
        }
        if (result.publishPlaybackState) {
            publishPlaybackStore();
        }
        if (result.renderNowBar) {
            nowPlayingStateController.renderNowBar();
        }
        if (result.renderSelectedTab) {
            renderSelectedTab();
        }
        if (result.navigateNow) {
            routeController.setSelectedTab(TAB_NOW);
            renderSelectedTab();
        }
    }

    private void publishPlaybackStore() {
        if (playbackStore != null) {
            playbackStore.publish(playbackQueueSnapshot());
        }
    }

    private List<Track> playbackQueueSnapshot() {
        return playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot();
    }

}
