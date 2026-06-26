package app.yukine;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Playlist;
import app.yukine.model.PlaylistImportResult;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.playback.EchoPlaybackService;
import app.yukine.playback.PlaybackStateSnapshot;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.streaming.StreamingAudioQuality;
import app.yukine.streaming.StreamingPlaylist;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.ui.LibraryGroupActions;
import app.yukine.ui.LibraryGroupUiState;
import app.yukine.ui.LyricUiLine;
import app.yukine.ui.TrackListAlbumCardUiState;
import app.yukine.ui.TrackListHeaderAction;
import app.yukine.ui.TrackListHeaderMetric;
import app.yukine.ui.TrackListLabels;
import app.yukine.ui.TrackListModeAction;
import app.yukine.ui.TrackRowActions;
import app.yukine.ui.TrackRowUiState;

@AndroidEntryPoint
public final class MainActivity extends ComponentActivity {
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MainExecutors executors = new MainExecutors();
    private final StreamingPlaybackTaskScheduler streamingPlaybackTaskScheduler = new StreamingPlaybackTaskScheduler();
    private final ResolveStreamingPlaybackUseCase resolveStreamingPlaybackUseCase = new ResolveStreamingPlaybackUseCase();
    private StreamingTrackMatchUseCase streamingTrackMatchUseCase;
    private ToggleFavoriteUseCase toggleFavoriteUseCase;
    private LoadPlaylistTracksUseCase loadPlaylistTracksUseCase;
    private LoadLyricsSettingsUseCase loadLyricsSettingsUseCase;

    @Inject
    StreamingGatewaySettingsStore streamingGatewaySettingsStore;

    @Inject
    MusicLibraryRepository repository;

    @Inject
    TrackDownloadManager trackDownloadManager;

    @Inject
    TrackShareManager trackShareManager;

    @Inject
    NativeMusicShareManager nativeMusicShareManager;

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
    private MainSettingsStore settingsStore;
    private MainPlaybackStore playbackStore;
    private StatusMessageController statusMessageController;
    private MainPermissionController permissionController;
    private MainUiShellController uiShellController;
    private TrackShareLauncher trackShareLauncher;
    private DocumentPickerController documentPickerController;
    private BackgroundImagePickerController backgroundImagePickerController;
    private BackupRestoreLauncher backupRestoreLauncher;
    private DownloadRequestController downloadRequestController;
    private DownloadDirectoryPickerController downloadDirectoryPickerController;
    private LuoxueSourceImportController luoxueSourceImportController;
    private LuoxueSourceImportDialogController luoxueSourceImportDialogController;
    private PlaybackServiceHostController playbackServiceHostController;
    private PlaybackServiceConnectionController playbackServiceConnectionController;
    private PlaybackStateUpdateController playbackStateUpdateController;
    private PlaybackStateEventController playbackStateEventController;
    private MainTabRenderDispatcher tabRenderDispatcher;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsContextProvider settingsContextProvider;
    private SettingsPageRenderController settingsPageRenderController;
    private TrackListRenderController trackListRenderController;
    private app.yukine.streaming.StreamingPlaylistSyncStore streamingPlaylistSyncStore;
    private QueueRenderController queueRenderController;
    private QueueIntentController queueIntentController;
    private QueueActionController queueActionController;
    private PlaybackActionController playbackActionController;
    private PlaybackStartController playbackStartController;
    private PlaybackActionResultController playbackActionResultController;
    private NowPlayingEffectController nowPlayingEffectController;
    private NowPlayingStateController nowPlayingStateController;
    private NetworkSourcesRenderController networkSourcesRenderController;
    private NetworkMenuRenderController networkMenuRenderController;
    private NetworkTrackListRenderController networkTrackListRenderController;
    private StreamingGatewayEventController streamingGatewayEventController;
    private StreamingGatewayController streamingGatewayController;
    private StreamingAuthCallbackController streamingAuthCallbackController;
    private StreamingSearchEventController streamingSearchEventController;
    private StreamingSearchRenderController streamingSearchRenderController;
    private StreamingPlaybackController streamingPlaybackController;
    private StreamingPlaylistController streamingPlaylistController;
    private StreamingPlaylistDialogController streamingPlaylistDialogController;
    private StreamingPlaylistImportDialogController streamingPlaylistImportDialogController;
    private StreamingManualCookieDialogController streamingManualCookieDialogController;
    private StreamingManualCookieController streamingManualCookieController;
    private HeartbeatRecommendationController heartbeatRecommendationController;
    private RecommendationActionController recommendationActionController;
    private final LateBoundHeartbeatSeedRequestProvider heartbeatSeedRequestProvider =
            new LateBoundHeartbeatSeedRequestProvider();
    private NetworkActionsViewModel networkActionsViewModel;
    private NetworkRequestController networkRequestController;
    private HomeDashboardRenderController homeDashboardRenderController;
    private LibraryGroupsRenderController libraryGroupsRenderController;
    private LibraryPlaylistsRenderController libraryPlaylistsRenderController;
    private CollectionsRenderController collectionsRenderController;
    private NowPlayingRenderController nowPlayingRenderController;
    private PlaylistExportController playlistExportController;
    private PlaylistActionResultController playlistActionResultController;
    private PlayHistoryActionController playHistoryActionController;
    private LyricsViewModel lyricsViewModel;
    private EchoPlaybackService playbackService;
    private List<Track> pendingPlaybackTracks = Collections.emptyList();
    private int pendingPlaybackIndex = -1;
    private int unifiedStreamingPlaybackRequestId = 0;
    private boolean scrollContentToTopOnNextRender;
    private boolean onboardingVisible;
    private app.yukine.navigation.EchoNavHostState navHostState;

    private NetworkDialogController networkDialogController;
    private PlaylistDialogController playlistDialogController;
    private ConfirmationDialogController confirmationDialogController;
    private boolean onboardingLibraryScanCompleted;
    private boolean onboardingLibraryScanInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onboardingVisible = repository != null && !repository.loadOnboardingCompleted();
        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);
        navigationViewModel = new ViewModelProvider(this).get(NavigationViewModel.class);
        playbackViewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        streamingViewModel = new ViewModelProvider(this).get(StreamingViewModel.class);
        streamingRecommendationViewModel = new ViewModelProvider(this).get(StreamingRecommendationViewModel.class);
        StreamingActionGatewayBindings streamingActionGatewayBindings = new StreamingActionGatewayBindings(
                this::adaptiveStreamingQuality,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                launch -> StreamingAuthLauncher.INSTANCE.launch(MainActivity.this, launch),
                this::playTrackListFromHost,
                provider -> streamingPlaylistController.onStreamingLoginSuccess(provider),
                this::openManualStreamingCookieImport
        );
        streamingViewModel.bindStreamingPlaybackCoordinator(
                resolveStreamingPlaybackUseCase,
                new StreamingPlaybackTaskQueueAdapter(streamingPlaybackTaskScheduler)
        );
        homeDashboardViewModel = new ViewModelProvider(this).get(HomeDashboardViewModel.class);
        nowPlayingViewModel = new ViewModelProvider(this).get(NowPlayingViewModel.class);
        nowPlayingViewModel.bindGateway(new NowPlayingGatewayBindings(
                () -> playbackActionController.togglePlayback(),
                () -> playbackActionController.skipToNext(),
                () -> playbackActionController.skipToPrevious(),
                nowPlayingViewModel::seekTo,
                () -> playbackStore == null ? null : playbackStore.snapshot().currentTrack,
                track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                () -> playbackActionController.toggleShuffle(),
                () -> playbackActionController.cycleRepeat(),
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                )
        ));
        nowPlayingViewModel.bindPlaybackGateway(new NowPlayingPlaybackGatewayAdapter(
                () -> playbackService,
                action -> {
                    Intent intent = new Intent(MainActivity.this, EchoPlaybackService.class);
                    if (action != null) {
                        intent.setAction(action);
                    }
                    MainActivity.this.startService(intent);
                }
        ));
        queueViewModel = new ViewModelProvider(this).get(app.yukine.queue.QueueViewModel.class);
        downloadsViewModel = new ViewModelProvider(this).get(DownloadsViewModel.class);
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
                message -> statusMessageController.showFeedback(message)
        );
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        lyricsViewModel = new ViewModelProvider(this).get(LyricsViewModel.class);
        libraryViewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        libraryViewModel.bindFavoriteIdsProvider(
                () -> libraryStore == null ? Collections.emptySet() : libraryStore.favoriteIds()
        );
        libraryViewModel.bindGateway(new LibraryGatewayBindings(
                this::playTrackListFromHost,
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status),
                (trackId, favorite) -> {
                    viewModel.setFavorite(trackId, favorite);
                    nowPlayingStateController.renderNowBar();
                    renderSelectedTab();
                    loadCollections();
                },
                track -> MainActivity.this.playlistDialogController.showAddToPlaylist(track),
                mode -> {
                    routeController.setLibraryMode(mode);
                    renderSelectedTab();
                },
                (key, title) -> {
                    routeController.selectLibraryGroup(key, title);
                    renderSelectedTab();
                },
                (playlistId, title) -> {
                    routeController.selectLibraryGroup("playlist:" + playlistId, title);
                    routeController.setSelectedPlaylistId(playlistId);
                    loadCollections();
                },
                () -> {
                    routeController.clearLibraryGroup();
                    routeController.setSelectedPlaylistId(-1L);
                    renderSelectedTab();
                },
                query -> {
                    routeController.setSearchQuery(query);
                    applySearch();
                },
                () -> documentPickerController.openAudioFilePicker(),
                () -> loadLibrary(false)
        ));
        collectionsViewModel = new ViewModelProvider(this).get(CollectionsViewModel.class);
          settingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
          networkMenuViewModel = new ViewModelProvider(this).get(NetworkMenuViewModel.class);
          statusMessageViewModel = new ViewModelProvider(this).get(StatusMessageViewModel.class);
          networkSourcesViewModel = new ViewModelProvider(this).get(NetworkSourcesViewModel.class);
        streamingGatewayEventController = new StreamingGatewayEventController(
                streamingViewModel,
                () -> settingsStore.languageMode(),
                () -> {
                    streamingRecommendationViewModel.updateProviders(streamingViewModel.getState().getProviders());
                    renderSelectedTab();
                },
                status -> statusMessageController.setStatus(status)
        );
        streamingGatewayController = new StreamingGatewayController(
                streamingGatewaySettingsStore,
                streamingGatewayEventController,
                streamingGatewayEventController
        );
        streamingGatewayController.configureRepository();
        streamingGatewayEventController.refreshStreamingProviders();
        streamingAuthCallbackController = new StreamingAuthCallbackController(
                new StreamingAuthCallbackBindings(streamingViewModel, streamingActionGatewayBindings)
        );
        routeController = new MainRouteController(navigationViewModel);
        playbackStore = new MainPlaybackStore(playbackViewModel);
        statusMessageController = new StatusMessageController(
                statusMessageViewModel,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                message -> uiShellController.updateStatus(message)
        );
        trackShareLauncher = new TrackShareLauncher(
                this,
                new TrackShareManagerOperations(trackShareManager, nativeMusicShareManager),
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
        permissionController = new MainPermissionController(this, new MainPermissionController.Listener() {
            @Override
            public void onAudioPermissionResult() {
                if (permissionController.hasAudioPermission()) {
                    loadLibrary(false);
                }
                if (onboardingVisible) {
                    mountNavHostShell();
                }
            }
        });
        uiShellController = new MainUiShellController(this);
        documentPickerController = new DocumentPickerController(this, new DocumentPickerBindings(
                this::importSelectedAudioUris,
                this::importSelectedAudioFolder,
                this::setCustomDownloadFolder,
                this::importSelectedM3uFile,
                exportUri -> playlistExportController.exportSelectedPlaylistToUri(exportUri),
                this::importSelectedPlaylistM3uFile,
                uris -> luoxueSourceImportController.importSelectedUris(uris)
        ));
        downloadDirectoryPickerController = new DownloadDirectoryPickerController(
                () -> documentPickerController,
                message -> statusMessageController.showFeedback(message)
        );
        backgroundImagePickerController = new BackgroundImagePickerController(
                this,
                new BackgroundImagePickerBindings(
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
                statusKey -> statusMessageController.setStatusKey(statusKey)
        );
        final LuoxueSourceStore luoxueSourceStore = new LuoxueSourceStore(this);
        luoxueSourceImportController = new LuoxueSourceImportController(
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                ),
                () -> documentPickerController,
                new LuoxueSourceDocumentReaderBindings(getContentResolver()),
                sources -> luoxueSourceStore.saveAll(sources),
                task -> executors.io(task),
                task -> executors.network(task),
                task -> mainHandler.post(task),
                status -> statusMessageController.setStatus(status),
                () -> {
                    streamingGatewayEventController.refreshStreamingProviders();
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
        playlistExportController = new PlaylistExportController(new PlaylistExportController.Listener() {
            @Override
            public void openPlaylistExportDocument(String playlistName) {
                documentPickerController.openPlaylistExportDocument(playlistName);
            }

            @Override
            public void exportPlaylist(Uri exportUri, long playlistId, String playlistName) {
                libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName, exported -> {
                });
            }

            @Override
            public void setStatus(String status) {
                statusMessageController.setStatus(status);
            }
        });
        playbackStateUpdateController = new PlaybackStateUpdateController();
        playbackStateEventController = new PlaybackStateEventController(
                mainHandler,
                playbackStore,
                playbackStateUpdateController,
                new PlaybackServiceQueueSourceBindings(() -> playbackService),
                new PlaybackStateEventBindings(
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
                        this::preResolveNextStreamingTrack,
                        snapshot -> streamingPlaybackController.recoverStreamingBuffering(snapshot),
                        this::resolveCurrentStreamingQueueTrackIfNeeded,
                        status -> statusMessageController.setStatus(status)
                )
        );
        playbackServiceHostController = new PlaybackServiceHostController(
                new PlaybackServiceHostController.Host() {
                    @Override
                    public float playbackSpeed() {
                        return settingsStore.playbackSpeed();
                    }

                    @Override
                    public float appVolume() {
                        return settingsStore.appVolume();
                    }

                    @Override
                    public boolean concurrentPlaybackEnabled() {
                        return settingsStore.concurrentPlaybackEnabled();
                    }

                    @Override
                    public boolean statusBarLyricsEnabled() {
                        return settingsStore.statusBarLyricsEnabled();
                    }

                    @Override
                    public boolean playbackRestoreEnabled() {
                        return settingsStore.playbackRestoreEnabled();
                    }

                    @Override
                    public boolean replayGainEnabled() {
                        return settingsStore.replayGainEnabled();
                    }

                    @Override
                    public void attachPlaybackService(EchoPlaybackService service) {
                        playbackService = service;
                        playbackService.setAppVisible(true);
                    }

                    @Override
                    public void clearPlaybackService() {
                        playbackService = null;
                    }

                    @Override
                    public void resetPlaybackStore() {
                        playbackStore.reset();
                    }

                    @Override
                    public void playPendingTracksIfNeeded() {
                        playbackStartController.playPendingTracksIfNeeded();
                    }

                    @Override
                    public void renderSelectedTab() {
                        MainActivity.this.renderSelectedTab();
                    }

                    @Override
                    public void renderNowBar() {
                        nowPlayingStateController.renderNowBar();
                    }
                }
        );
        playbackServiceConnectionController = new PlaybackServiceConnectionController(
                this,
                playbackStateEventController,
                playbackServiceHostController
        );
        tabRenderDispatcher = new MainTabRenderDispatcher(
                this::renderHome,
                this::renderLibrary,
                this::renderCollections,
                this::renderQueue,
                this::renderNetwork,
                this::renderSettings,
                this::renderSearch
        );
        trackListRenderController = new TrackListRenderController(libraryViewModel, new TrackListRenderBindings(
                event -> libraryViewModel.onEvent(event),
                track -> networkDialogController.showEditStream(track),
                track -> confirmationDialogController.confirmDeleteTrack(track),
                track -> downloadRequestController.downloadTrack(track),
                tracks -> downloadRequestController.downloadTracks(tracks),
                state -> {
                },
                this::publishTrackListChromeState
        ));
        recommendationActionController = new RecommendationActionController(
                streamingRecommendationViewModel,
                () -> settingsStore.languageMode(),
                new RecommendationActionBindings(
                        presentation -> playbackStartController.playRecommendation(presentation),
                        heartbeatSeedRequestProvider,
                        presentation -> playbackStartController.playHeartbeatRecommendation(presentation),
                        this::logHeartbeatSeedMiss,
                        status -> statusMessageController.setStatus(status)
                )
        );
        homeDashboardRenderController = new HomeDashboardRenderController(homeDashboardViewModel, new HomeDashboardRenderBindings(
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
                action -> recommendationActionController.run(action),
                homeDashboardViewModel::updateHomeDashboardActions,
                () -> {
                    refreshUnifiedSearch(false);
                    navigateToTab(TAB_SEARCH, true, true);
                    syncNavHostState();
                }
        ));
        queueRenderController = new QueueRenderController(new QueueRenderBindings(
                this::playTrackListFromHost,
                event -> libraryViewModel.onEvent(event),
                track -> playlistDialogController.showAddToPlaylist(track),
                this::removeQueueTrack,
                () -> queueActionController.confirmClearQueue(),
                this::handleAppBack,
                state -> {
                }
        ));
        queueIntentController = new QueueIntentController(new QueueIntentBindings(
                event -> libraryViewModel.onEvent(event),
                track -> playlistDialogController.showAddToPlaylist(track),
                this::removeQueueTrack,
                this::moveQueueTrack,
                () -> queueActionController.confirmClearQueue(),
                this::handleAppBack
        ));
        playbackActionResultController = new PlaybackActionResultController(new PlaybackActionResultBindings(
                snapshot -> playbackStore.replaceSnapshot(snapshot),
                status -> statusMessageController.setStatus(status),
                () -> playbackStore.publish(playbackService),
                () -> nowPlayingStateController.renderNowBar(),
                this::renderSelectedTab,
                () -> {
                    routeController.setSelectedTab(TAB_NOW);
                    renderSelectedTab();
                }
        ));
        playbackActionController = new PlaybackActionController(nowPlayingViewModel, new PlaybackActionBindings(
                this::resolveCurrentStreamingQueueTrackIfNeeded,
                () -> playbackService == null ? playbackStore.snapshot() : playbackService.snapshot(),
                () -> libraryStore == null ? Collections.emptyList() : libraryStore.visibleTracks(),
                this::applyPlaybackActionResult
        ));
        heartbeatRecommendationController = new HeartbeatRecommendationController(streamingRecommendationViewModel, () -> settingsStore.languageMode(), new HeartbeatRecommendationBindings(
                () -> playbackService != null,
                heartbeatSeedRequestProvider,
                streamingRecommendationViewModel::stopHeartbeatRecommendationMode,
                presentation -> nowPlayingViewModel.appendToQueue(presentation.getTracks()),
                presentation -> playbackStartController.playHeartbeatRecommendation(presentation),
                this::logHeartbeatSeedMiss,
                status -> statusMessageController.setStatus(status)
        ));
        streamingPlaybackController = new StreamingPlaybackController(streamingViewModel, nowPlayingViewModel, new StreamingPlaybackBindings(
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                this::adaptiveStreamingQuality,
                this::selectedStreamingQuality,
                () -> playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot(),
                snapshot -> heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot),
                this::applyPlaybackActionResult,
                status -> statusMessageController.setStatus(status)
        ));
        playbackStartController = new PlaybackStartController(new PlaybackStartBindings(
                streamingRecommendationViewModel::stopHeartbeatRecommendationMode,
                () -> startService(new Intent(this, EchoPlaybackService.class)),
                () -> playbackService != null,
                (tracks, index) -> {
                    pendingPlaybackTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);
                    pendingPlaybackIndex = index;
                },
                () -> pendingPlaybackTracks == null ? Collections.emptyList() : pendingPlaybackTracks,
                () -> pendingPlaybackIndex,
                () -> {
                    pendingPlaybackTracks = Collections.emptyList();
                    pendingPlaybackIndex = -1;
                },
                () -> streamingViewModel.prepareStreamingPlaybackStatusText(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        null
                ).getResolving(),
                status -> statusMessageController.setStatus(status),
                new PlaybackStartControllerAdapter(
                        streamingPlaybackController::resolveAndPlayStreamingTrack,
                        nowPlayingViewModel::playTrackList,
                        this::applyPlaybackActionResult
                ),
                () -> navigateToTab(TAB_QUEUE, true, true)
        ));
        nowPlayingEffectController = new NowPlayingEffectController(new NowPlayingEffectBindings(
                () -> navigateToTab(TAB_QUEUE, true, true),
                effect -> playlistDialogController.showAddToPlaylist(effect.getTrack()),
                effect -> trackShareLauncher.share(effect.getTrack()),
                effect -> downloadRequestController.downloadTrack(effect.getTrack()),
                this::switchNowPlayingSource,
                status -> statusMessageController.setStatus(status)
        ));
        nowPlayingStateController = new NowPlayingStateController(nowPlayingViewModel, new NowPlayingStateController.Listener() {
            @Override
            public boolean storesReady() {
                return playbackStore != null && libraryStore != null;
            }

            @Override
            public PlaybackStateSnapshot playbackSnapshot() {
                return playbackStore.snapshot();
            }

            @Override
            public Set<Long> favoriteIds() {
                return libraryStore.favoriteIds();
            }

            @Override
            public LyricsState lyricsState() {
                return MainActivity.this.lyricsState();
            }

            @Override
            public String languageMode() {
                return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
            }

            @Override
            public void publishFloatingLyrics(NowPlayingUiState state) {
                FloatingLyricsPublisher.update(
                        state.getTrackTitle(),
                        state.getArtist(),
                        state.getCoverUri(),
                        state.isPlaying(),
                        activeLyricLine(state)
                );
            }

            @Override
            public void syncQueueInputs() {
                bindQueueViewModelInputs();
            }
        });
        queueActionController = new QueueActionController(nowPlayingViewModel, new QueueActionBindings(
                this::applyPlaybackActionResult,
                () -> playbackService != null,
                (fromIndex, toIndex) -> playbackService.moveQueueTrack(fromIndex, toIndex),
                () -> nowPlayingStateController.renderNowBar(),
                this::renderSelectedTab,
                () -> confirmationDialogController.confirmClearQueue(),
                () -> AppLanguage.text(settingsStore.languageMode(), "queue.empty"),
                status -> statusMessageController.setStatus(status)
        ));
        lyricsViewModel.bindReloadGateway(
                () -> playbackStore == null ? null : playbackStore.snapshot().currentTrack,
                this::neteaseProviderTrackIdForLyrics,
                status -> statusMessageController.setStatus(status)
        );
        SettingsEffectBindings settingsEffectBindings = new SettingsEffectBindings(
                status -> statusMessageController.setStatus(status),
                () -> navigateToNetworkTabPage(NETWORK_HOME),
                () -> navigateToTab(TAB_DOWNLOADS, true, true),
                () -> loadLibrary(false),
                () -> documentPickerController.openAudioFilePicker(),
                () -> documentPickerController.openAudioFolderPicker(),
                () -> lyricsViewModel.reloadCurrentLyrics(settingsStore.languageMode()),
                minutes -> applyPlaybackActionResult(nowPlayingViewModel.startSleepTimer(minutes)),
                () -> applyPlaybackActionResult(nowPlayingViewModel.cancelSleepTimer()),
                settingsViewModel::openFloatingLyricsPermission,
                page -> backgroundImagePickerController.open(page),
                backupRestoreLauncher::exportBackup,
                backupRestoreLauncher::importBackup,
                endpoint -> streamingGatewayController.applyEndpoint(endpoint)
        );
        settingsViewModel.bindEffectListener(settingsEffectBindings::onEffect);
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
                new StreamingPlaylistDialogController.Listener() {
                    @Override
                    public void setStatus(String status) {
                        statusMessageController.setStatus(status);
                    }

                    @Override
                    public void runStreamingPlaylistImport(StreamingProviderName provider, String playlistName, java.util.List<Track> tracks) {
                        if (streamingPlaylistControllerRef[0] != null) {
                            streamingPlaylistControllerRef[0].runStreamingPlaylistImport(provider, playlistName, tracks);
                        }
                    }

                    @Override
                    public void importSelectedAccountPlaylists(StreamingProviderName provider, java.util.List<StreamingPlaylist> playlists) {
                        if (streamingPlaylistControllerRef[0] != null) {
                            streamingPlaylistControllerRef[0].importSelectedAccountPlaylists(provider, playlists);
                        }
                    }

                    @Override
                    public void importStreamingLikedTracks(StreamingProviderName provider) {
                        if (streamingPlaylistControllerRef[0] != null) {
                            streamingPlaylistControllerRef[0].importStreamingLikedTracks(provider);
                        }
                    }
                }
        );
        streamingPlaylistController = new StreamingPlaylistController(streamingViewModel, () -> settingsStore.languageMode(), new StreamingPlaylistBindings(
                () -> routeController.selectedPlaylistId(),
                playlistId -> routeController.setSelectedPlaylistId(playlistId),
                this::loadCollections,
                this::refreshLibraryAfterStreamingImport,
                this::selectedPlaylistName,
                () -> libraryStore.selectedPlaylistTracks(),
                () -> libraryStore.favoriteTracks(),
                () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                streamingPlaylistDialogController::showStreamingProviderPicker,
                () -> navigateToNetworkTabPage(NETWORK_STREAMING),
                streamingPlaylistDialogController::showStreamingPlaylistLoadedDialog,
                streamingPlaylistDialogController::showAccountPlaylistImportPicker,
                status -> statusMessageController.setStatus(status),
                this::renderSelectedTab
        ));
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
                new StreamingPlaylistImportDialogController.Listener() {
                    @Override
                    public StreamingProviderName selectedProvider() {
                        return streamingViewModel.getStreaming().getValue().getSelectedProvider();
                    }

                    @Override
                    public void showLuoxueSourceImportDialog() {
                        luoxueSourceImportDialogController.showImportDialog();
                    }

                    @Override
                    public void importStreamingPlaylistFromLink(String linkOrId) {
                        streamingPlaylistController.importStreamingPlaylistFromLink(linkOrId);
                    }
                }
        );
        streamingManualCookieDialogController = new StreamingManualCookieDialogController(
                this,
                key -> AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                ),
                (provider, cookieHeader) -> streamingManualCookieController.saveStreamingCookie(provider, cookieHeader)
        );
        streamingManualCookieController = new StreamingManualCookieController(streamingViewModel, () -> settingsStore.languageMode(), new StreamingManualCookieBindings(
                () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                streamingManualCookieDialogController::show,
                streamingPlaylistController::onStreamingLoginSuccess,
                status -> statusMessageController.setStatus(status)
        ));
        libraryStore = new MainLibraryStore(
                new LibrarySearchUseCase(new MusicLibrarySearchOperations(repository)),
                viewModel
        );
        settingsStore = new MainSettingsStore();
        settingsStore.load(
                new LoadSettingsPreferencesUseCase(
                        new MusicLibrarySettingsPreferenceLoadOperations(repository)
                ).execute()
        );
        streamingPlaylistSyncStore = new app.yukine.streaming.StreamingPlaylistSyncStore(this);
        PlaylistActionOperations playlistActionOperations =
                new MusicLibraryPlaylistActionOperations(repository, streamingPlaylistSyncStore);
        libraryViewModel.bindPlaylistActionGateway(new LibraryPlaylistActionGatewayBindings(playlistActionOperations));
        playlistActionResultController = new PlaylistActionResultController(
                libraryViewModel,
                () -> settingsStore.languageMode(),
                this::selectedPlaylistId,
                playlistId -> routeController.setSelectedPlaylistId(playlistId),
                status -> statusMessageController.setStatus(status),
                this::loadCollections
        );
        playHistoryActionController = new PlayHistoryActionController(
                libraryViewModel,
                () -> settingsStore.languageMode(),
                viewModel::clearPlayHistory,
                status -> statusMessageController.setStatus(status),
                this::loadCollections
        );
        networkTrackListRenderController = new NetworkTrackListRenderController(new NetworkTrackListRenderBindings(
                this::navigateNetworkPage,
                this::clearRemoteSourceAndNavigateNetworkPage,
                sourceId -> networkRequestController.syncRemoteSource(sourceId, remoteSourceName(sourceId)),
                this::playRemoteSourceTracks,
                this::renderNetworkTrackList
        ));
        streamingSearchEventController = new StreamingSearchEventController(
                new StreamingSearchActionHandlerBindings(streamingViewModel, streamingActionGatewayBindings),
                new StreamingSearchNavigatorBindings(
                        this::navigateNetworkPage,
                        () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                        streamingPlaylistController::importStreamingPlaylistFromProviderRef,
                        streamingPlaylistController::showAccountPlaylistSyncPicker,
                        streamingPlaylistController::importStreamingLikedTracks,
                        recommendationActionController,
                        streamingPlaylistImportDialogController::showImportDialog,
                        streamingManualCookieController::showStreamingCookieDialog
                ),
                (labels, actions) -> streamingViewModel.updateStreamingSearchChrome(labels, actions)
        );
        streamingSearchRenderController = new StreamingSearchRenderController(
                () -> settingsStore.languageMode(),
                streamingSearchEventController
        );
        libraryGroupsRenderController = new LibraryGroupsRenderController(libraryViewModel, new LibraryGroupsRenderBindings(
                event -> libraryViewModel.onEvent(event),
                () -> routeController.clearLibraryGroup(),
                () -> {
                    String title = AppLanguage.text(settingsStore.languageMode(), "favorite.playlist");
                    libraryViewModel.onEvent(new LibraryEvent.OpenGroup("virtual:favorites", title));
                },
                (title, tracks) -> confirmationDialogController.confirmDeleteTracks(title, tracks),
                state -> {
                },
                this::renderLibraryGroupTrackList,
                this::publishLibraryGroupsChromeState
        ), new ArtistInfoRepository(), action -> mainHandler.post(action));
        libraryPlaylistsRenderController = new LibraryPlaylistsRenderController(libraryViewModel, new LibraryPlaylistsRenderBindings(
                event -> libraryViewModel.onEvent(event),
                playlist -> playlistDialogController.confirmDeletePlaylist(playlist),
                state -> {
                },
                this::renderLibraryPlaylistTrackList,
                this::publishLibraryGroupsChromeState
        ));
        collectionsRenderController = new CollectionsRenderController(collectionsViewModel, new CollectionsRenderBindings(
                () -> playlistDialogController.showCreatePlaylist(),
                () -> documentPickerController.openPlaylistM3uFilePicker(),
                () -> confirmationDialogController.confirmClearPlayHistory(),
                this::handleAppBack,
                this::playTrackListFromHost,
                event -> libraryViewModel.onEvent(event),
                track -> playlistDialogController.showAddToPlaylist(track),
                track -> downloadRequestController.downloadTrack(track),
                tracks -> downloadRequestController.downloadTracks(tracks),
                playlistId -> {
                    routeController.setSelectedPlaylistId(playlistId);
                    loadCollections();
                },
                playlist -> playlistDialogController.showRenamePlaylist(playlist),
                playlist -> playlistDialogController.confirmDeletePlaylist(playlist),
                () -> playlistExportController.openSelectedPlaylistExportDocument(
                        selectedPlaylistId(),
                        selectedPlaylistName(),
                        !libraryStore.selectedPlaylistTracks().isEmpty()
                ),
                streamingPlaylistController::importSelectedPlaylistToStreaming,
                streamingPlaylistController::importFavoritesToStreaming,
                streamingPlaylistDialogController::showImportStreamingFavoritesProviderPicker,
                this::syncSelectedPlaylistFromStreaming,
                playlistActionResultController::moveSelectedPlaylistTrack,
                playlistActionResultController::removeSelectedPlaylistTrack,
                collectionsViewModel::updateActions
        ));
        nowPlayingRenderController = new NowPlayingRenderController();
        final ImportStreamingPlaylistUseCase importStreamingPlaylistUseCase = new ImportStreamingPlaylistUseCase(
                new MusicLibraryStreamingPlaylistImportOperations(repository, streamingPlaylistSyncStore)
        );
        final SyncStreamingPlaylistUseCase syncStreamingPlaylistUseCase = new SyncStreamingPlaylistUseCase(
                new MusicLibraryStreamingPlaylistSyncOperations(repository, streamingPlaylistSyncStore)
        );
        final EnsureStreamingLoginPlaylistUseCase ensureStreamingLoginPlaylistUseCase = new EnsureStreamingLoginPlaylistUseCase(
                new MusicLibraryStreamingLoginPlaylistOperations(repository, streamingPlaylistSyncStore)
        );
        final GetStreamingPlaylistLinkUseCase streamingPlaylistLinkUseCase =
                new GetStreamingPlaylistLinkUseCase(
                        new StreamingPlaylistSyncStoreLinkOperations(streamingPlaylistSyncStore)
                );
        streamingViewModel.bindStreamingLocalPlaylistOperations(new StreamingLocalPlaylistOperationsBindings(
                importStreamingPlaylistUseCase,
                syncStreamingPlaylistUseCase,
                ensureStreamingLoginPlaylistUseCase,
                streamingPlaylistLinkUseCase
        ));
        streamingTrackMatchUseCase = new StreamingTrackMatchUseCase(
                new MusicLibraryStreamingTrackMatchOperations(repository)
        );
        StreamingTrackMatchStoreBindings streamingTrackMatchStore = new StreamingTrackMatchStoreBindings(streamingTrackMatchUseCase);
        streamingViewModel.bindStreamingTrackMatchStore(streamingTrackMatchStore);
        streamingRecommendationViewModel.bindStreamingTrackMatchStore(streamingTrackMatchStore);
        heartbeatSeedRequestProvider.bind(new HeartbeatRecommendationSeedResolverBindings(
                new HeartbeatRecommendationSeedResolver(streamingTrackMatchStore),
                () -> playbackService == null ? null : playbackService.snapshot(),
                () -> playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot(),
                () -> playbackStore == null ? null : playbackStore.snapshot(),
                () -> playbackViewModel == null || playbackViewModel.getPlayback().getValue() == null
                        ? Collections.emptyList()
                        : playbackViewModel.getPlayback().getValue().getQueue(),
                this::heartbeatLibraryContextTracks
        ));
        toggleFavoriteUseCase = new ToggleFavoriteUseCase(
                new MusicLibraryFavoriteOperations(repository)
        );
        libraryViewModel.bindFavoriteWriter((track, favorite) -> toggleFavoriteUseCase.execute(track, favorite));

        loadPlaylistTracksUseCase = new LoadPlaylistTracksUseCase(
                new MusicLibraryPlaylistTrackOperations(repository)
        );
        libraryViewModel.bindPlaylistTrackLoader(playlistId -> loadPlaylistTracksUseCase.execute(playlistId));
        loadLyricsSettingsUseCase = new LoadLyricsSettingsUseCase(
                new MusicLibraryLyricsSettingsOperations(repository)
        );
        LoadedLyricsSettings loadedLyricsSettings = loadLyricsSettingsUseCase.execute();
        LibraryCollectionOperations libraryCollectionOperations =
                new MusicLibraryCollectionOperations(repository);
        LibraryImportOperations libraryImportOperations =
                new MusicLibraryImportOperations(repository);
        libraryViewModel.bindCollectionGateway(new LibraryCollectionGatewayBindings(libraryCollectionOperations));
        libraryViewModel.bindImportGateway(new LibraryImportGatewayBindings(libraryImportOperations));
        libraryViewModel.bindDocumentGateway(new LibraryDocumentGatewayBindings(getContentResolver(), libraryImportOperations));
        settingsViewModel.bindPreferenceGateway(update ->
                new ApplySettingsPreferenceUseCase(
                        new MusicLibrarySettingsPreferenceOperations(repository)
                ).execute(update)
        );
        settingsViewModel.bindStoreMirror(settingsStore::sync);
        SettingsRuntimeApplier settingsRuntimeApplier = new SettingsRuntimeApplier(
                () -> uiShellController.applyThemeSurface(),
                languageMode -> uiShellController.updateLanguage(languageMode),
                () -> playbackService == null ? null : new SettingsPlaybackServiceControlsBindings(
                        playbackService::setPlaybackSpeed,
                        playbackService::setAppVolume,
                        playbackService::setConcurrentPlaybackEnabled,
                        playbackService::applyAudioEffectSettings,
                        playbackService::setStatusBarLyricsEnabled,
                        playbackService::setPlaybackRestoreEnabled,
                        playbackService::setReplayGainEnabled
                ),
                () -> lyricsViewModel == null ? null : new SettingsLyricsControlsBindings(
                        lyricsViewModel::setOnlineEnabled,
                        lyricsViewModel::setOffsetMs
                ),
                () -> new SettingsFloatingLyricsControls() {
                    @Override
                    public boolean apply(boolean enabled) {
                        if (!enabled) {
                            FloatingLyricsService.stop(MainActivity.this);
                            return true;
                        }
                        if (!permissionController.hasOverlayPermission()) {
                            FloatingLyricsService.stop(MainActivity.this);
                            permissionController.openOverlayPermissionSettings();
                            return false;
                        }
                        FloatingLyricsService.start(MainActivity.this);
                        return true;
                    }

                    @Override
                    public void openPermissionSettings() {
                        permissionController.openOverlayPermissionSettings();
                    }
                }
        );
        settingsViewModel.bindRuntimeEffectListener(settingsRuntimeApplier::apply);
        playlistDialogController = createPlaylistDialogController();
        WebDavSourceOperations webDavSourceOperations =
                new MusicLibraryWebDavSourceOperations(repository);
        NetworkLibraryOperations networkLibraryOperations =
                new MusicLibraryNetworkLibraryOperations(repository);
        networkActionsViewModel = new ViewModelProvider(this).get(NetworkActionsViewModel.class);
        networkActionsViewModel.bindUseCases(
                new NetworkActionUseCases(
                        new TestWebDavSourceUseCase(webDavSourceOperations),
                        new SyncWebDavSourceUseCase(webDavSourceOperations),
                        new SyncAllWebDavSourcesUseCase(webDavSourceOperations),
                        new AddStreamUrlUseCase(networkLibraryOperations),
                        new UpdateStreamUrlUseCase(networkLibraryOperations),
                        new ImportStreamPlaylistUseCase(networkLibraryOperations),
                        new DeleteAllStreamsUseCase(networkLibraryOperations),
                        new DeleteNetworkTrackUseCase(networkLibraryOperations),
                        new DeleteNetworkTracksUseCase(networkLibraryOperations),
                        new DeleteRemoteSourceUseCase(networkLibraryOperations),
                        new SaveWebDavSourceUseCase(networkLibraryOperations)
                )
        );
        networkActionsViewModel.bindListener(
                new NetworkActionsResultBindings(
                        this::replaceLibrary,
                        this::retainPlaybackTracks,
                        this::syncUpdatedStreamQueue,
                        this::navigateToNetworkTabPage,
                        status -> statusMessageController.setStatus(status),
                        this::loadCollections
                )
        );
        networkRequestController = new NetworkRequestController(
                networkActionsViewModel,
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status)
        );
        lyricsViewModel.configure(
                new LoadTrackLyricsUseCaseLyricsLoader(
                        new LoadTrackLyricsUseCase(new LyricsRepositoryLoadOperations())
                ),
                loadedLyricsSettings.onlineLyricsEnabled,
                loadedLyricsSettings.lyricsOffsetMs
        );
        lyricsViewModel.bindListener(new LyricsStateListenerBindings(
                this::selectedTab,
                () -> nowPlayingStateController.renderNowBar(),
                this::updateNowPlayingContent,
                this::renderSelectedTab
        ));
        settingsPageRenderController = new SettingsPageRenderController(settingsViewModel);
        settingsContextProvider = new SettingsContextProvider(
                settingsStore,
                libraryStore,
                permissionController,
                playbackServiceConnectionController,
                playbackStore,
                lyricsViewModel,
                streamingGatewaySettingsStore
        );
        NetworkDialogEventController networkDialogEventController = new NetworkDialogEventController(networkRequestController);
        DialogLanguageProvider dialogLanguageProvider =
                () -> settingsStore.languageMode();
        networkDialogController = new NetworkDialogController(this, dialogLanguageProvider, networkDialogEventController);
        confirmationDialogController = new ConfirmationDialogController(this, dialogLanguageProvider, new ConfirmationDialogBindings(
                () -> playHistoryActionController.clearPlayHistory(),
                () -> queueActionController.clearQueue(),
                () -> networkRequestController.deleteAllStreams(),
                (trackId, status) -> networkRequestController.deleteTrack(trackId, status),
                (trackIds, status) -> networkRequestController.deleteTracks(trackIds, status),
                sourceId -> networkRequestController.deleteRemoteSource(sourceId)
        ));
        NetworkMenuEventController networkMenuEventController = new NetworkMenuEventController(
                page -> MainActivity.this.navigateNetworkPage(page),
                networkDialogController::showAddStream,
                networkDialogController::showImportM3u,
                networkDialogController::showAddWebDav,
                () -> documentPickerController.openM3uFilePicker(),
                libraryStore::streamTracks,
                libraryStore::streamTrackCount,
                libraryStore::webDavTracks,
                libraryStore::remoteSources,
                sourceIds -> networkRequestController.syncAllWebDavSources(sourceIds),
                () -> confirmationDialogController.confirmDeleteAllStreams(),
                (tracks, index) -> playTrackListFromHost(tracks, index),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status),
                (title, metrics, actions) -> networkMenuViewModel.updateMenu(title, metrics, actions)
        );
        networkMenuRenderController = new NetworkMenuRenderController(networkMenuEventController);
        NetworkSourcesEventController networkSourcesEventController = new NetworkSourcesEventController(
                routeController,
                networkRequestController,
                libraryStore::remoteSourceName,
                libraryStore::webDavTracksForSource,
                source -> networkDialogController.showEditWebDav(source),
                source -> confirmationDialogController.confirmDeleteRemoteSource(source),
                (tracks, index) -> playTrackListFromHost(tracks, index),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> statusMessageController.setStatus(status),
                () -> MainActivity.this.renderAndPersistSelectedTab()
        );
        networkSourcesRenderController = new NetworkSourcesRenderController(
                networkSourcesViewModel,
                networkSourcesEventController
        );
        networkRenderCoordinator = new NetworkRenderCoordinator(
                libraryStore,
                networkMenuRenderController,
                networkTrackListRenderController,
                networkSourcesRenderController,
                streamingSearchRenderController
        );
        streamingAuthCallbackController.handleInitialIntent(getIntent());
        uiShellController.applyThemeSurface();
        mountNavHostShell();
        installBackNavigation();
        playbackServiceConnectionController.bind();
        if (!onboardingVisible) {
            permissionController.requestNeededPermissions();
            loadLibraryOnStartup();
        } else {
            loadLibrary(true);
        }
        loadCollections();
    }

    private void finishOnboarding() {
        completeOnboarding(() -> renderAndPersistSelectedTab());
    }

    private void openStreamingFromOnboarding() {
        completeOnboarding(() -> navigateToNetworkTabPage(NETWORK_STREAMING));
    }

    private void completeOnboarding(Runnable afterComplete) {
        if (!canFinishOnboarding()) {
            statusMessageController.setStatus(onboardingMissingSetupMessage());
            mountNavHostShell();
            return;
        }
        onboardingVisible = false;
        if (repository != null) {
            repository.saveOnboardingCompleted(true);
        }
        mountNavHostShell();
        if (afterComplete != null) {
            afterComplete.run();
        }
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
        playbackServiceConnectionController.release();
        executors.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        streamingAuthCallbackController.handleNewIntent(intent);
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

    private void playTrackListFromHost(List<Track> tracks, int index) {
        if (playbackStartController == null) {
            pendingPlaybackTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);
            pendingPlaybackIndex = index;
            statusMessageController.setStatus(AppLanguage.text(
                    settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                    "streaming.resolving"
            ));
            return;
        }
        playbackStartController.playTrackList(tracks, index);
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
                new MainActivityTrackListUiState(
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
                new MainActivityLibraryGroupsUiState(
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
        queueViewModel.bindIntentListener(intent -> queueIntentController.handle(intent));
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
                    viewModel,
                    navigationViewModel,
                    homeDashboardViewModel,
                    nowPlayingViewModel,
                    libraryViewModel,
                    collectionsViewModel,
                    settingsViewModel,
                    networkMenuViewModel,
                    networkSourcesViewModel,
                    streamingViewModel,
                    playbackViewModel,
                    selectedTab(),
                    downloadsViewModel,
                    trackDownloadManager,
                    () -> playbackService == null ? 0f : playbackService.realtimeBeat(),
                    () -> playbackService == null ? EMPTY_REALTIME_BANDS : playbackService.realtimeBands(),
                    true
            );
            return;
        }
        navHostState.setSelectedTabRoute(selectedTab());
    }

    private void bindQueueViewModelInputs() {
        if (queueViewModel == null) {
            return;
        }
        queueViewModel.bind(
                playbackService == null ? new ArrayList<>() : playbackService.queueSnapshot(),
                playbackStore == null ? null : playbackStore.snapshot(),
                libraryStore == null ? java.util.Collections.<Long>emptySet() : libraryStore.favoriteIds(),
                settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()
        );
    }

    private static String activeLyricLine(NowPlayingUiState state) {
        if (state == null || state.getLyrics() == null) {
            return "";
        }
        List<LyricUiLine> lines = state.getLyrics().getLines();
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        for (LyricUiLine line : lines) {
            if (line != null && line.getActive()) {
                return line.getText();
            }
        }
        LyricUiLine first = lines.get(0);
        return first == null ? "" : first.getText();
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
        public app.yukine.queue.QueueViewModel queueViewModel() {
            return queueViewModel;
        }

        @Override
        public SearchViewModel searchViewModel() {
            return searchViewModel;
        }

        @Override
        public java.lang.Runnable openDownloadDirectoryPickerAction() {
            return () -> downloadDirectoryPickerController.open();
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
            return onboardingVisible;
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
            return onboardingLibraryScanCompleted;
        }

        @Override
        public boolean libraryScanInProgress() {
            return onboardingLibraryScanInProgress;
        }

        @Override
        public app.yukine.ui.OnboardingActions onboardingActions() {
            return new app.yukine.ui.OnboardingActions(
                    () -> {
                        if (permissionController != null) {
                            permissionController.requestNeededPermissions();
                        }
                    },
                    () -> scanLibraryFromOnboarding(),
                    () -> importPlaylistFromOnboarding(),
                    () -> openStreamingFromOnboarding(),
                    () -> finishOnboarding()
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
                    if (onboardingVisible && onboardingLibraryScanInProgress) {
                        onboardingLibraryScanInProgress = false;
                        onboardingLibraryScanCompleted = canScan;
                        mountNavHostShell();
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

    private void loadLibraryOnStartup() {
        if (permissionController.hasAudioPermission()) {
            loadLibrary(false);
            return;
        }
        loadLibrary(true);
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

    private void refreshLibraryAfterStreamingImport() {
        loadLibrary(true);
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
        if (searchOnline && streamingSearchEventController != null && query != null && !query.trim().isEmpty()) {
            streamingSearchEventController.search(query);
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
                    playbackStore.publish(playbackService);
                    nowPlayingStateController.renderNowBar();
                    renderSelectedTabForNavHostState();
                }
        );
    }

    private void loadMoreUnifiedStreamingResults() {
        if (streamingSearchEventController != null) {
            streamingSearchEventController.loadNextPage();
        }
    }

    private void loadLyrics(final Track track) {
        if (lyricsViewModel != null) {
            lyricsViewModel.load(track, neteaseProviderTrackIdForLyrics(track));
        }
    }

    private void ensureLyricsLoaded(final Track track) {
        if (lyricsViewModel != null && track != null && lyricsViewModel.trackId() != track.id) {
            lyricsViewModel.load(track, neteaseProviderTrackIdForLyrics(track));
        }
    }

    private LyricsState lyricsState() {
        return lyricsViewModel == null ? new LyricsState() : lyricsViewModel.stateSnapshot();
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
            if (streamingSearchEventController != null) {
                streamingSearchEventController.search(searchQuery());
            }
        }
        if (query != null && !query.trim().isEmpty() && libraryStore.visibleTracks().isEmpty()) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "search.no.results"));
        }
        renderAndPersistSelectedTab();
    }

    private boolean canFinishOnboarding() {
        return permissionController != null
                && permissionController.hasAudioPermission()
                && permissionController.hasNotificationPermission()
                && onboardingLibraryScanCompleted;
    }

    private String onboardingMissingSetupMessage() {
        List<String> missing = new ArrayList<>();
        if (permissionController == null || !permissionController.hasAudioPermission()) {
            missing.add("\u97f3\u9891\u6743\u9650");
        }
        if (permissionController == null || !permissionController.hasNotificationPermission()) {
            missing.add("\u901a\u77e5\u6743\u9650");
        }
        if (!onboardingLibraryScanCompleted) {
            missing.add(onboardingLibraryScanInProgress ? "\u7b49\u5f85\u66f2\u5e93\u626b\u63cf\u5b8c\u6210" : "\u626b\u63cf\u672c\u5730\u66f2\u5e93");
        }
        return "\u5b8c\u6210\u540e\u624d\u80fd\u8fdb\u5165\uff1a" + String.join("\u3001", missing);
    }

    private void scanLibraryFromOnboarding() {
        if (permissionController == null || !permissionController.hasAudioPermission()) {
            statusMessageController.setStatus(onboardingMissingSetupMessage());
            if (permissionController != null) {
                permissionController.requestNeededPermissions();
            }
            mountNavHostShell();
            return;
        }
        onboardingLibraryScanInProgress = true;
        mountNavHostShell();
        loadLibrary(false);
    }

    private void importPlaylistFromOnboarding() {
        if (!canFinishOnboarding()) {
            statusMessageController.setStatus(onboardingMissingSetupMessage());
            mountNavHostShell();
            return;
        }
        documentPickerController.openPlaylistM3uFilePicker();
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
        uiShellController.updateTabBar(tabKey);
        if (renderImmediately || sameTab || !uiShellController.hasTabBar()) {
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
        uiShellController.prepareHorizontalContentTransition(next);
        routeController.setLibraryMode(mode);
        renderAndPersistSelectedTab();
        return true;
    }

    private boolean closeLibraryDetailIfNeeded() {
        if (selectedLibraryGroupKey().isEmpty() && selectedPlaylistId() < 0L) {
            return false;
        }
        uiShellController.prepareHorizontalContentTransition(false);
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

    private void clearRemoteSourceAndNavigateNetworkPage(String page) {
        routeController.clearSelectedRemoteSource();
        navigateNetworkPage(page);
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
        if (TAB_SETTINGS.equals(selectedTab()) && settingsPageRenderController != null) {
            settingsPageRenderController.scrollToTopOnNextRender();
        }
    }

    private void renderSelectedTab() {
        if (!uiShellController.hasContentHost()) {
            renderSelectedTabForNavHostState();
            syncNavHostState();
            return;
        }
        if (routeController != null) {
            routeController.persist();
        }
        uiShellController.navigateContentRoute(selectedTab());
        renderSelectedTabContent();
    }

    private void renderSelectedTabContent() {
        if (!uiShellController.hasContentHost()) {
            renderSelectedTabForNavHostState();
            syncNavHostState();
            return;
        }
        if (routeController != null) {
            routeController.persist();
        }
        uiShellController.updateSelectedContentRoute(selectedTab());
        nowPlayingRenderController.clear();
        useScrollingContentContainer();
        updateTabBar();
        updateHeaderMode();
        tabRenderDispatcher.render(selectedTab());
        syncNavHostState();
        final android.widget.ScrollView scrollView = uiShellController.getScrollView();
        if (scrollView != null && scrollContentToTopOnNextRender) {
            scrollContentToTopOnNextRender = false;
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.scrollTo(0, 0);
                }
            });
        } else {
            scrollContentToTopOnNextRender = false;
        }
    }

    private void useScrollingContentContainer() {
        uiShellController.useScrollingContentContainer();
    }

    private void updateTabBar() {
        uiShellController.updateTabBar(selectedTab());
    }


    private void updateHeaderMode() {
        if (!uiShellController.hasHeader()) {
            return;
        }
        boolean emptyHome = TAB_HOME.equals(selectedTab())
                && selectedLibraryGroupKey().isEmpty();
        uiShellController.setHeaderExpanded(!emptyHome);
        // The search bar only applies to track-based library tabs; keep Home clean.
        boolean searchable = !TAB_HOME.equals(selectedTab())
                && !TAB_NETWORK.equals(selectedTab())
                && !TAB_SETTINGS.equals(selectedTab());
        uiShellController.setSearchBarVisible(searchable);
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
                new PlaylistDialogBindings(
                        playlistActionResultController::createPlaylist,
                        playlistActionResultController::renamePlaylist,
                        playlistActionResultController::deletePlaylist,
                        playlistActionResultController::addToDefaultPlaylist,
                        playlistActionResultController::addTrackToPlaylist
                )
        );
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
                playbackService.queueSnapshot(),
                playbackStore.snapshot(),
                libraryStore.favoriteIds(),
                settingsStore.languageMode()
        );
    }

    private void removeQueueTrack(Track track) {
        queueActionController.removeQueueTrack(track);
    }

    private void moveQueueTrack(int fromIndex, int toIndex) {
        queueActionController.moveQueueTrack(fromIndex, toIndex);
    }

    private void renderNowPlaying() {
        Track track = playbackStore.snapshot().currentTrack;
        if (track == null) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "no.track.selected"));
            return;
        }
        if (!nowPlayingRenderController.render(playbackStore, lyricsState(), settingsStore.languageMode())) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "no.track.selected"));
        }
    }

    private boolean updateNowPlayingContent() {
        return nowPlayingRenderController.update(playbackStore, lyricsState(), settingsStore.languageMode());
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
                this::loadMoreUnifiedStreamingResults,
                this::clearUnifiedSearchOnExit
        );
        searchViewModel.updateActions(searchActions);
        refreshUnifiedSearch(false);
    }

    private ArrayList<Track> webDavTracksForSource(long sourceId) {
        return libraryStore.webDavTracksForSource(sourceId);
    }

    private void syncUpdatedStreamQueue(long oldTrackId, Track updated) {
        nowPlayingViewModel.replaceQueuedTrack(oldTrackId, updated);
    }

    private void playRemoteSourceTracks(RemoteSource source) {
        if (source == null) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "source.not.found"));
            return;
        }
        ArrayList<Track> tracks = webDavTracksForSource(source.id);
        if (tracks.isEmpty()) {
            statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), "no.source.tracks.to.play"));
            return;
        }
        playTrackListFromHost(tracks, 0);
    }

    private void retainPlaybackTracks(List<Track> tracksToKeep) {
        nowPlayingViewModel.retainTracks(tracksToKeep);
    }

    private void renderSettings() {
        if (settingsContextProvider == null || settingsPageRenderController == null) {
            return;
        }
        settingsViewModel.renderPageFromHost(
                SettingsPage.fromRoute(settingsPage()),
                settingsContextProvider.preferencesSnapshot(),
                settingsContextProvider.runtimeStatus()
        );
    }

    private void syncSelectedPlaylistFromStreaming() {
        streamingPlaylistController.syncSelectedPlaylistFromStreaming();
    }

    private void openManualStreamingCookieImport(final StreamingProviderName provider) {
        streamingViewModel.selectStreamingProvider(provider);
        if (streamingManualCookieController == null) {
            return;
        }
        streamingManualCookieController.showStreamingCookieDialog();
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

    private String streamingProviderTrackId(Track track, app.yukine.streaming.StreamingProviderName provider) {
        return streamingTrackMatchUseCase.directProviderTrackId(track, provider);
    }

    private void logHeartbeatSeedMiss(HeartbeatRecommendationSeedRequest request) {
        if (request == null || request.getSeedMissingMessage() == null || request.getSeedMissingMessage().isEmpty()) {
            return;
        }
        Log.w(TAG, request.getSeedMissingMessage());
    }

    private void syncStreamingPlaylists(java.util.List<app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist> links) {
        streamingPlaylistController.syncStreamingPlaylists(links);
    }

    private String remoteSourceName(long sourceId) {
        return libraryStore.remoteSourceName(sourceId);
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
        if (nowPlayingViewModel == null || nowPlayingEffectController == null) {
            return;
        }
        nowPlayingEffectController.handle(nowPlayingViewModel.drainEffects());
    }

    private boolean resolveCurrentStreamingQueueTrackIfNeeded() {
        if (playbackService == null) {
            return false;
        }
        StreamingQueueResolveTarget target = streamingViewModel.prepareCurrentStreamingQueueResolveTarget(
                playbackService.snapshot(),
                playbackService.queueSnapshot()
        );
        return target != null && streamingPlaybackController.resolveAndPlayStreamingTrack(target.getTracks(), target.getIndex());
    }

    private void preResolveNextStreamingTrack(PlaybackStateSnapshot snapshot) {
        streamingPlaybackController.preResolveNextStreamingTrack(snapshot);
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
        if (playbackActionResultController != null) {
            playbackActionResultController.apply(result);
        }
    }

}

