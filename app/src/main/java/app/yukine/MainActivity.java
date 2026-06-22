package app.yukine;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
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
import app.yukine.streaming.StreamingAudioQuality;
import app.yukine.streaming.StreamingPlaylist;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;
import app.yukine.ui.LibraryGroupActions;
import app.yukine.ui.LibraryGroupUiState;
import app.yukine.ui.LyricUiLine;
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
    private NetworkSourcesViewModel networkSourcesViewModel;
    private StreamingViewModel streamingViewModel;
    private MainRouteController routeController;
    private MainLibraryStore libraryStore;
    private MainSettingsStore settingsStore;
    private MainPlaybackStore playbackStore;
    private StatusMessageController statusMessageController;
    private MainPermissionController permissionController;
    private MainUiShellController uiShellController;
    private DocumentPickerController documentPickerController;
    private PlaybackServiceHostController playbackServiceHostController;
    private PlaybackServiceConnectionController playbackServiceConnectionController;
    private PlaybackStateUpdateController playbackStateUpdateController;
    private PlaybackStateEventController playbackStateEventController;
    private MainTabRenderDispatcher tabRenderDispatcher;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsRenderCoordinator settingsRenderCoordinator;
    private TrackListRenderController trackListRenderController;
    private app.yukine.streaming.StreamingPlaylistSyncStore streamingPlaylistSyncStore;
    private QueueRenderController queueRenderController;
    private QueueIntentController queueIntentController;
    private QueueActionController queueActionController;
    private PlaybackActionController playbackActionController;
    private PlaybackStartController playbackStartController;
    private PlaybackActionResultController playbackActionResultController;
    private LyricsReloadController lyricsReloadController;
    private NowPlayingEffectController nowPlayingEffectController;
    private NowPlayingStateController nowPlayingStateController;
    private SettingsActionController settingsActionController;
    private NetworkSourcesRenderController networkSourcesRenderController;
    private NetworkMenuRenderController networkMenuRenderController;
    private NetworkTrackListRenderController networkTrackListRenderController;
    private StreamingGatewayEventController streamingGatewayEventController;
    private StreamingGatewayController streamingGatewayController;
    private StreamingAuthCallbackController streamingAuthCallbackController;
    private ActivityIntentController activityIntentController;
    private StreamingSearchEventController streamingSearchEventController;
    private StreamingSearchRenderController streamingSearchRenderController;
    private StreamingPlaybackController streamingPlaybackController;
    private StreamingPlaylistController streamingPlaylistController;
    private StreamingManualCookieController streamingManualCookieController;
    private DailyRecommendationController dailyRecommendationController;
    private HeartbeatRecommendationController heartbeatRecommendationController;
    private NetworkActionsViewModel networkActionsViewModel;
    private NetworkRequestController networkRequestController;
    private HomeDashboardRenderController homeDashboardRenderController;
    private LibraryGroupsRenderController libraryGroupsRenderController;
    private LibraryPlaylistsRenderController libraryPlaylistsRenderController;
    private CollectionsRenderController collectionsRenderController;
    private NowPlayingRenderController nowPlayingRenderController;
    private PlaylistExportController playlistExportController;
    private PlaylistActionResultController playlistActionResultController;
    private LyricsViewModel lyricsViewModel;
    private EchoPlaybackService playbackService;
    private List<Track> pendingPlaybackTracks = Collections.emptyList();
    private int pendingPlaybackIndex = -1;
    private boolean scrollContentToTopOnNextRender;
    private boolean onboardingVisible;
    private app.yukine.ui.HomeDashboardActions navHomeActions = emptyHomeDashboardActions();
    private List<TrackRowActions> navTrackListActions = Collections.emptyList();
    private List<TrackListHeaderMetric> navTrackListHeaderMetrics = Collections.emptyList();
    private List<TrackListHeaderAction> navTrackListHeaderActions = Collections.emptyList();
    private String navTrackListEmptyText = "";
    private List<TrackListModeAction> navTrackListModeActions = Collections.emptyList();
    private TrackListLabels navTrackListLabels = new TrackListLabels();
    private List<LibraryGroupActions> navLibraryGroupActions = Collections.emptyList();
    private String navLibraryGroupEmptyText = "";
    private List<TrackListModeAction> navLibraryGroupModeActions = Collections.emptyList();
    private app.yukine.ui.CollectionsActions navCollectionsActions = emptyCollectionsActions();
    private List<app.yukine.ui.SettingsAction> navSettingsActions = Collections.emptyList();
    private app.yukine.ui.SettingsListScrollState navSettingsScrollState = new app.yukine.ui.SettingsListScrollState();
    private List<app.yukine.ui.NetworkSourceActions> navNetworkSourceActions = Collections.emptyList();
    private List<TrackListHeaderAction> navNetworkSourceHeaderActions = Collections.emptyList();
    private String navNetworkSourceEmptyText = "";
    private app.yukine.ui.NetworkSourceLabels navNetworkSourceLabels = new app.yukine.ui.NetworkSourceLabels();
    private String navNetworkMenuTitle = "";
    private List<app.yukine.ui.SettingsMetric> navNetworkMenuMetrics = Collections.emptyList();
    private List<app.yukine.ui.SettingsAction> navNetworkMenuActions = Collections.emptyList();
    private app.yukine.ui.StreamingSearchLabels navStreamingSearchLabels =
            app.yukine.ui.StreamingSearchLabels.empty();
    private app.yukine.ui.StreamingSearchActions navStreamingSearchActions =
            app.yukine.ui.StreamingSearchActions.empty();
    private app.yukine.ui.UnifiedSearchActions navSearchActions =
            app.yukine.ui.UnifiedSearchActions.empty();
    private app.yukine.navigation.EchoNavHostState navHostState;

    private NetworkDialogController networkDialogController;
    private PlaylistDialogController playlistDialogController;
    private ConfirmationDialogController confirmationDialogController;
    private boolean onboardingLibraryScanCompleted;
    private boolean onboardingLibraryScanInProgress;

    private static app.yukine.ui.HomeDashboardActions emptyHomeDashboardActions() {
        return new app.yukine.ui.HomeDashboardActions(
                Collections.<Runnable>emptyList(),
                new Runnable() { @Override public void run() {} },
                new Runnable() { @Override public void run() {} },
                Collections.<Runnable>emptyList(),
                new Runnable() { @Override public void run() {} },
                new Runnable() { @Override public void run() {} },
                new Runnable() { @Override public void run() {} },
                index -> kotlin.Unit.INSTANCE,
                new Runnable() { @Override public void run() {} },
                new Runnable() { @Override public void run() {} },
                new Runnable() { @Override public void run() {} },
                new Runnable() { @Override public void run() {} },
                new Runnable() { @Override public void run() {} }
        );
    }

    private static app.yukine.ui.CollectionsActions emptyCollectionsActions() {
        return new app.yukine.ui.CollectionsActions(
                null,
                Collections.<Runnable>emptyList(),
                Collections.<app.yukine.ui.CollectionTrackSectionActions>emptyList(),
                Collections.<app.yukine.ui.PlaylistRowActions>emptyList(),
                Collections.<Runnable>emptyList(),
                Collections.<app.yukine.ui.PlaylistTrackActions>emptyList()
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onboardingVisible = repository != null && !repository.loadOnboardingCompleted();
        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);
        navigationViewModel = new ViewModelProvider(this).get(NavigationViewModel.class);
        playbackViewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        streamingViewModel = new ViewModelProvider(this).get(StreamingViewModel.class);
        StreamingActionGatewayBindings streamingActionGatewayBindings = new StreamingActionGatewayBindings(
                this::adaptiveStreamingQuality,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                launch -> StreamingAuthLauncher.INSTANCE.launch(MainActivity.this, launch),
                this::playTrackListFromHost,
                provider -> streamingPlaylistController.onStreamingLoginSuccess(provider)
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
                track -> handleLibraryEvent(new LibraryEvent.ToggleFavorite(track)),
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
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        lyricsViewModel = new ViewModelProvider(this).get(LyricsViewModel.class);
        libraryViewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        libraryViewModel.bindGateway(new LibraryGatewayBindings(
                this::playTrackListFromHost,
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> MainActivity.this.setStatus(status),
                (trackId, favorite) -> {
                    libraryStore.setFavorite(trackId, favorite);
                    publishLibraryState();
                    renderNowBar();
                    renderSelectedTab();
                    loadCollections();
                },
                track -> MainActivity.this.showAddToPlaylistDialog(track),
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
                this::openAudioFilePicker,
                () -> loadLibrary(false)
        ));
        collectionsViewModel = new ViewModelProvider(this).get(CollectionsViewModel.class);
        settingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        networkSourcesViewModel = new ViewModelProvider(this).get(NetworkSourcesViewModel.class);
        streamingGatewayEventController = new StreamingGatewayEventController(
                streamingViewModel,
                new StreamingGatewayHostBindings(
                        () -> settingsStore.languageMode(),
                        this::renderSelectedTab,
                        this::setStatus
                )
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
        activityIntentController = new ActivityIntentController(streamingAuthCallbackController);
        routeController = new MainRouteController(navigationViewModel);
        playbackStore = new MainPlaybackStore(playbackViewModel);
        statusMessageController = new StatusMessageController(new StatusMessageHostBindings(
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                message -> uiShellController.updateStatus(message)
        ));
        permissionController = new MainPermissionController(this, new PermissionResultBindings(
                () -> permissionController.hasAudioPermission(),
                () -> loadLibrary(false)
        ));
        uiShellController = new MainUiShellController(this);
        documentPickerController = new DocumentPickerController(this, new DocumentPickerBindings(
                this::importSelectedAudioUris,
                this::importSelectedAudioFolder,
                this::setCustomDownloadFolder,
                this::importSelectedM3uFile,
                exportUri -> playlistExportController.exportSelectedPlaylistToUri(exportUri),
                this::importSelectedPlaylistM3uFile
        ));
        playlistExportController = new PlaylistExportController(new PlaylistExportBindings(
                () -> settingsStore.languageMode(),
                playlistName -> documentPickerController.openPlaylistExportDocument(playlistName),
                (exportUri, playlistId, playlistName, callback) -> libraryViewModel.exportPlaylistJava(
                        exportUri,
                        playlistId,
                        playlistName,
                        callback::onResult
                ),
                this::setStatus
        ));
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
                        this::renderNowBar,
                        snapshot -> homeDashboardViewModel.updatePlayback(snapshot),
                        this::renderSelectedTab,
                        this::updateNowPlayingContent,
                        this::preResolveNextStreamingTrack,
                        snapshot -> streamingPlaybackController.recoverStreamingBuffering(snapshot),
                        this::setStatus
                )
        );
        playbackServiceHostController = new PlaybackServiceHostController(
                new PlaybackServiceHostBindings(
                        () -> settingsStore.playbackSpeed(),
                        () -> settingsStore.appVolume(),
                        () -> settingsStore.concurrentPlaybackEnabled(),
                        () -> settingsStore.statusBarLyricsEnabled(),
                        () -> settingsStore.playbackRestoreEnabled(),
                        () -> settingsStore.replayGainEnabled(),
                        service -> {
                            playbackService = service;
                            playbackService.setAppVisible(true);
                        },
                        () -> playbackStore.reset(),
                        () -> playbackStartController.playPendingTracksIfNeeded(),
                        this::renderSelectedTab,
                        this::renderNowBar
                )
        );
        playbackServiceConnectionController = new PlaybackServiceConnectionController(
                this,
                playbackStateEventController,
                playbackServiceHostController
        );
        tabRenderDispatcher = new MainTabRenderDispatcher(new MainTabRendererBindings(
                this::renderHome,
                this::renderLibrary,
                this::renderCollections,
                this::renderQueue,
                this::renderNowPlaying,
                this::renderNetwork,
                this::renderSettings,
                this::renderSearch
        ));
        trackListRenderController = new TrackListRenderController(libraryViewModel, new TrackListRenderBindings(
                this::handleLibraryEvent,
                this::showEditStreamDialog,
                this::confirmDeleteTrack,
                this::downloadTrack,
                this::downloadTracks,
                this::publishTrackListChromeState
        ));
        homeDashboardRenderController = new HomeDashboardRenderController(homeDashboardViewModel, new HomeDashboardRenderBindings(
                this::openLibraryModeFromHome,
                this::continueDashboardPlayback,
                this::renderNowBar,
                this::playTrackListFromHost,
                () -> loadLibrary(true),
                () -> navigateToTab(TAB_QUEUE, true, true),
                () -> libraryStore.allTracks(),
                () -> navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB),
                this::openCollectionsFromHome,
                this::playStreamingDailyRecommendations,
                this::playStreamingHeartbeatRecommendations,
                actions -> navHomeActions = actions,
                this::openSearchFromHome
        ));
        queueRenderController = new QueueRenderController(new QueueRenderBindings(
                this::playTrackListFromHost,
                this::handleLibraryEvent,
                this::showAddToPlaylistDialog,
                this::removeQueueTrack,
                this::confirmClearQueue,
                this::handleAppBack,
                state -> {
                },
                this::addStateContent
        ));
        queueIntentController = new QueueIntentController(new QueueIntentBindings(
                this::handleLibraryEvent,
                this::showAddToPlaylistDialog,
                this::removeQueueTrack,
                this::moveQueueTrack,
                this::confirmClearQueue,
                this::handleAppBack
        ));
        playbackActionResultController = new PlaybackActionResultController(new PlaybackActionResultBindings(
                snapshot -> playbackStore.replaceSnapshot(snapshot),
                this::setStatus,
                this::publishPlaybackState,
                this::renderNowBar,
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
        dailyRecommendationController = new DailyRecommendationController(streamingViewModel, () -> settingsStore.languageMode(), new DailyRecommendationBindings(
                streamingViewModel::prepareStreamingRecommendationPresentation,
                this::openRecommendationPlaylist,
                this::setStatus
        ));
        heartbeatRecommendationController = new HeartbeatRecommendationController(streamingViewModel, () -> settingsStore.languageMode(), new HeartbeatRecommendationBindings(
                () -> playbackService != null,
                this::heartbeatRecommendationSeedRequest,
                streamingViewModel::stopHeartbeatRecommendationMode,
                presentation -> nowPlayingViewModel.appendToQueue(presentation.getTracks()),
                this::playHeartbeatRecommendationTracks,
                this::logHeartbeatSeedMiss,
                this::setStatus
        ));
        streamingPlaybackController = new StreamingPlaybackController(streamingViewModel, nowPlayingViewModel, new StreamingPlaybackBindings(
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                this::adaptiveStreamingQuality,
                this::selectedStreamingQuality,
                () -> playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot(),
                snapshot -> heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot),
                this::applyPlaybackActionResult,
                this::setStatus
        ));
        playbackStartController = new PlaybackStartController(new PlaybackStartBindings(
                streamingViewModel::stopHeartbeatRecommendationMode,
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
                this::setStatus,
                streamingPlaybackController::resolveAndPlayStreamingTrack,
                nowPlayingViewModel::playTrackList,
                this::applyPlaybackActionResult
        ));
        nowPlayingEffectController = new NowPlayingEffectController(new NowPlayingEffectBindings(
                () -> navigateToTab(TAB_QUEUE, true, true),
                effect -> showAddToPlaylistDialog(effect.getTrack()),
                effect -> shareTrack(effect.getTrack()),
                effect -> downloadTrack(effect.getTrack()),
                this::switchNowPlayingSource,
                this::setStatus
        ));
        nowPlayingStateController = new NowPlayingStateController(nowPlayingViewModel, new NowPlayingStateBindings(
                () -> playbackStore != null && libraryStore != null,
                () -> playbackStore.snapshot(),
                () -> libraryStore.favoriteIds(),
                this::lyricsState,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                state -> FloatingLyricsPublisher.update(
                        state.getTrackTitle(),
                        state.getArtist(),
                        state.getCoverUri(),
                        state.isPlaying(),
                        activeLyricLine(state)
                ),
                this::bindQueueViewModelInputs
        ));
        queueActionController = new QueueActionController(nowPlayingViewModel, new QueueActionBindings(
                this::applyPlaybackActionResult,
                () -> playbackService != null,
                (fromIndex, toIndex) -> playbackService.moveQueueTrack(fromIndex, toIndex),
                this::renderNowBar,
                this::renderSelectedTab,
                () -> confirmationDialogController.confirmClearQueue(),
                () -> AppLanguage.text(settingsStore.languageMode(), "queue.empty"),
                this::setStatus
        ));
        lyricsReloadController = new LyricsReloadController(new LyricsReloadBindings(
                () -> playbackStore == null ? null : playbackStore.snapshot().currentTrack,
                this::neteaseProviderTrackIdForLyrics,
                (track, providerTrackId) -> {
                    if (lyricsViewModel != null) {
                        lyricsViewModel.load(track, providerTrackId);
                    }
                },
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                this::setStatus
        ));
        settingsActionController = new SettingsActionController(settingsViewModel, nowPlayingViewModel, new SettingsActionBindings(
                endpoint -> streamingGatewayController.applyEndpoint(endpoint),
                this::applyPlaybackActionResult,
                () -> lyricsReloadController.reloadCurrentLyrics()
        ));
        settingsViewModel.bindGateway(new SettingsGatewayBindings(
                this::navigateSettingsPage,
                () -> navigateToNetworkTabPage(NETWORK_HOME),
                () -> loadLibrary(false),
                this::openAudioFilePicker,
                this::openAudioFolderPicker,
                settingsActionController::setOnlineLyricsEnabled,
                settingsActionController::reloadCurrentLyrics,
                settingsActionController::applyLyricsOffset,
                settingsActionController::startSleepTimer,
                settingsActionController::cancelSleepTimer,
                settingsActionController::applyPlaybackSpeed,
                settingsActionController::applyAppVolume,
                settingsActionController::applyStreamingAudioQuality,
                settingsActionController::applyShareStyle,
                settingsActionController::setConcurrentPlaybackEnabled,
                settingsActionController::applyAudioEffectSettings,
                settingsActionController::setStatusBarLyricsEnabled,
                settingsActionController::setFloatingLyricsEnabled,
                settingsActionController::openFloatingLyricsPermission,
                settingsActionController::setNowPlayingGesturesEnabled,
                settingsActionController::setPlaybackRestoreEnabled,
                settingsActionController::setReplayGainEnabled,
                this::exportBackup,
                this::importBackup,
                settingsActionController::applyThemeMode,
                settingsActionController::applyAccentMode,
                settingsActionController::applyLanguageMode,
                settingsActionController::applyStreamingGatewayEndpoint,
                () -> navigateToTab(TAB_DOWNLOADS, true, true)
        ));
        streamingPlaylistController = new StreamingPlaylistController(streamingViewModel, () -> settingsStore.languageMode(), new StreamingPlaylistBindings(
                () -> routeController.selectedPlaylistId(),
                playlistId -> routeController.setSelectedPlaylistId(playlistId),
                this::loadCollections,
                this::selectedPlaylistName,
                () -> libraryStore.selectedPlaylistTracks(),
                () -> libraryStore.favoriteTracks(),
                () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                this::showStreamingProviderPicker,
                () -> navigateToNetworkTabPage(NETWORK_STREAMING),
                this::showStreamingPlaylistLoadedDialog,
                this::showAccountPlaylistImportPicker,
                this::setStatus,
                this::renderSelectedTab
        ));
        streamingManualCookieController = new StreamingManualCookieController(streamingViewModel, () -> settingsStore.languageMode(), new StreamingManualCookieBindings(
                () -> streamingViewModel.getStreaming().getValue().getSelectedProvider(),
                this::showStreamingCookieDialogContent,
                streamingPlaylistController::onStreamingLoginSuccess,
                this::setStatus
        ));
        networkTrackListRenderController = new NetworkTrackListRenderController(new NetworkTrackListRenderBindings(
                this::navigateNetworkPage,
                this::clearRemoteSourceAndNavigateNetworkPage,
                this::syncRemoteSource,
                this::playRemoteSourceTracks,
                this::playTrackListFromHost,
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
                        this::playStreamingDailyRecommendations,
                        this::playStreamingHeartbeatRecommendations,
                        this::showImportStreamingPlaylistDialog,
                        streamingManualCookieController::showStreamingCookieDialog
                ),
                new StreamingSearchChromeBindings(state -> {
                    navStreamingSearchLabels = state.getLabels();
                    navStreamingSearchActions = state.getActions();
                })
        );
        streamingSearchRenderController = new StreamingSearchRenderController(
                viewModel,
                () -> settingsStore.languageMode(),
                streamingSearchEventController
        );
        libraryGroupsRenderController = new LibraryGroupsRenderController(libraryViewModel, new LibraryGroupsRenderBindings(
                this::handleLibraryEvent,
                () -> routeController.clearLibraryGroup(),
                this::openFavoritesCollectionFromLibrary,
                this::confirmDeleteTracks,
                this::publishLibraryGroupsChromeState,
                this::renderLibraryGroupTrackList
        ));
        libraryPlaylistsRenderController = new LibraryPlaylistsRenderController(libraryViewModel, new LibraryPlaylistsRenderBindings(
                this::handleLibraryEvent,
                this::confirmDeletePlaylist,
                this::publishLibraryGroupsChromeState,
                this::renderLibraryPlaylistTrackList
        ));
        collectionsRenderController = new CollectionsRenderController(collectionsViewModel, new CollectionsRenderBindings(
                this::showCreatePlaylistDialog,
                this::openPlaylistM3uFilePicker,
                this::confirmClearPlayHistory,
                this::handleAppBack,
                this::playTrackListFromHost,
                this::handleLibraryEvent,
                this::showAddToPlaylistDialog,
                this::downloadTrack,
                this::downloadTracks,
                this::selectPlaylistFromCollections,
                this::showRenamePlaylistDialog,
                this::confirmDeletePlaylist,
                this::openSelectedPlaylistExportDocument,
                streamingPlaylistController::importSelectedPlaylistToStreaming,
                streamingPlaylistController::importFavoritesToStreaming,
                this::showImportStreamingFavoritesProviderPicker,
                this::syncSelectedPlaylistFromStreaming,
                this::moveSelectedPlaylistTrack,
                this::removeSelectedPlaylistTrack,
                actions -> navCollectionsActions = actions
        ));
        nowPlayingRenderController = new NowPlayingRenderController();
        syncRouteFieldsFromViewModel();
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
        streamingViewModel.bindStreamingTrackMatchStore(new StreamingTrackMatchStoreBindings(streamingTrackMatchUseCase));
        toggleFavoriteUseCase = new ToggleFavoriteUseCase(
                new MusicLibraryFavoriteOperations(repository)
        );
        libraryViewModel.bindFavoriteWriter(new LibraryFavoriteWriterBindings(toggleFavoriteUseCase));

        loadPlaylistTracksUseCase = new LoadPlaylistTracksUseCase(
                new MusicLibraryPlaylistTrackOperations(repository)
        );
        libraryViewModel.bindPlaylistTrackLoader(new LibraryPlaylistTrackLoaderBindings(loadPlaylistTracksUseCase));
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
        settingsViewModel.bindAppliedListener(new SettingsAppliedListenerBindings(
                settingsStore,
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
                },
                offsetMs -> settingsViewModel.prepareAppliedStatusText(
                        settingsStore.languageMode(),
                        settingsStore.themeMode(),
                        settingsStore.accentMode(),
                        settingsStore.playbackSpeed(),
                        settingsStore.appVolume(),
                        offsetMs
                ),
                quality -> streamingViewModel.prepareStreamingStatusText(settingsStore.languageMode(), quality).getStreamingQualityApplied(),
                this::setStatus,
                this::renderSelectedTab,
                this::renderNowBar,
                () -> lyricsReloadController.reloadCurrentLyrics(),
                this::selectedTab
        ));
        PlaylistActionOperations playlistActionOperations =
                new MusicLibraryPlaylistActionOperations(repository);
        libraryViewModel.bindPlaylistActionGateway(new LibraryPlaylistActionGatewayBindings(playlistActionOperations));
        playlistActionResultController = new PlaylistActionResultController(
                libraryViewModel,
                () -> settingsStore.languageMode(),
                this::selectedPlaylistId,
                playlistId -> routeController.setSelectedPlaylistId(playlistId),
                this::setStatus,
                this::loadCollections
        );
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
                        this::setStatus,
                        this::loadCollections
                )
        );
        networkRequestController = new NetworkRequestController(
                networkActionsViewModel,
                new NetworkRequestLabels(key -> AppLanguage.text(settingsStore.languageMode(), key)),
                new NetworkRequestStatusListener(this::setStatus)
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
                this::renderNowBar,
                this::updateNowPlayingContent,
                this::renderSelectedTab
        ));
        SettingsPageEventController settingsPageEventController = new SettingsPageEventController(
                settingsViewModel,
                new SettingsPageChromeBindings(state -> {
                    navSettingsActions = new ArrayList<>(state.getActions());
                    navSettingsScrollState = state.getScrollState();
                })
        );
        SettingsPageRenderController settingsPageRenderController = new SettingsPageRenderController(
                settingsViewModel,
                settingsPageEventController
        );
        settingsRenderCoordinator = new SettingsRenderCoordinator(
                settingsPageRenderController,
                settingsStore,
                libraryStore,
                permissionController,
                playbackServiceConnectionController,
                playbackStore,
                lyricsViewModel,
                streamingGatewaySettingsStore
        );
        NetworkDialogEventController networkDialogEventController = new NetworkDialogEventController(networkRequestController);
        DialogLanguageProviderBindings dialogLanguageProvider =
                new DialogLanguageProviderBindings(() -> settingsStore.languageMode());
        networkDialogController = new NetworkDialogController(this, dialogLanguageProvider, networkDialogEventController);
        playlistDialogController = new PlaylistDialogController(this, dialogLanguageProvider, new PlaylistDialogBindings(
                this::createPlaylist,
                this::renamePlaylist,
                this::deletePlaylist,
                this::addTrackToPlaylist
        ));
        confirmationDialogController = new ConfirmationDialogController(this, dialogLanguageProvider, new ConfirmationDialogBindings(
                this::clearPlayHistory,
                this::clearQueue,
                this::deleteAllStreams,
                this::deleteTrack,
                this::deleteTracks,
                this::deleteRemoteSource
        ));
        NetworkMenuEventController networkMenuEventController = new NetworkMenuEventController(
                page -> MainActivity.this.navigateNetworkPage(page),
                new NetworkMenuDialogBindings(
                        networkDialogController::showAddStream,
                        networkDialogController::showImportM3u,
                        networkDialogController::showAddWebDav
                ),
                () -> documentPickerController.openM3uFilePicker(),
                new NetworkMenuLibrarySourceBindings(libraryStore),
                sourceIds -> networkRequestController.syncAllWebDavSources(sourceIds),
                () -> confirmationDialogController.confirmDeleteAllStreams(),
                new NetworkMenuPlayerBindings(this::playTrackListFromHost),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> MainActivity.this.setStatus(status),
                new NetworkMenuChromeBindings(state -> {
                    navNetworkMenuTitle = state.getTitle();
                    navNetworkMenuMetrics = new ArrayList<>(state.getMetrics());
                    navNetworkMenuActions = new ArrayList<>(state.getActions());
                })
        );
        networkMenuRenderController = new NetworkMenuRenderController(networkMenuEventController);
        NetworkSourcesEventController networkSourcesEventController = new NetworkSourcesEventController(
                routeController,
                networkRequestController,
                new NetworkSourcesLibrarySourceBindings(libraryStore),
                source -> networkDialogController.showEditWebDav(source),
                source -> confirmationDialogController.confirmDeleteRemoteSource(source),
                new NetworkSourcesPlayerBindings(this::playTrackListFromHost),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> MainActivity.this.setStatus(status),
                () -> MainActivity.this.renderAndPersistSelectedTab()
        );
        networkSourcesRenderController = new NetworkSourcesRenderController(
                networkSourcesViewModel,
                new NetworkSourcesRenderBindings(
                        networkSourcesEventController,
                        state -> {
                            navNetworkSourceActions = new ArrayList<>(state.getActions());
                            navNetworkSourceHeaderActions = new ArrayList<>(state.getHeaderActions());
                            navNetworkSourceEmptyText = state.getEmptyText();
                            navNetworkSourceLabels = state.getLabels();
                        }
                )
        );
        networkRenderCoordinator = new NetworkRenderCoordinator(
                libraryStore,
                networkMenuRenderController,
                networkTrackListRenderController,
                networkSourcesRenderController,
                streamingSearchRenderController
        );
        activityIntentController.handleInitialIntent(getIntent());
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
        if (!canFinishOnboarding()) {
            setStatus(onboardingMissingSetupMessage());
            mountNavHostShell();
            return;
        }
        onboardingVisible = false;
        if (repository != null) {
            repository.saveOnboardingCompleted(true);
        }
        mountNavHostShell();
        renderAndPersistSelectedTab();
    }

    private void openStreamingFromOnboarding() {
        if (!canFinishOnboarding()) {
            setStatus(onboardingMissingSetupMessage());
            mountNavHostShell();
            return;
        }
        onboardingVisible = false;
        if (repository != null) {
            repository.saveOnboardingCompleted(true);
        }
        navigateToNetworkTabPage(NETWORK_STREAMING);
        mountNavHostShell();
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
        activityIntentController.handleNewIntent(intent);
    }

    private void syncRouteFieldsFromViewModel() {
        if (routeController == null) {
            return;
        }
        routeController.restoreFromViewModel();
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

    private void persistRouteFields() {
        if (routeController == null) {
            return;
        }
        routeController.persist();
    }

    private void publishPlaybackState() {
        if (playbackStore == null) {
            return;
        }
        playbackStore.publish(playbackService);
    }

    private void openLibraryModeFromHome(String mode) {
        routeController.setLibraryMode(mode);
        navigateToTab(TAB_LIBRARY, true, true);
    }

    private void openFavoritesCollectionFromLibrary() {
        String title = AppLanguage.text(settingsStore.languageMode(), "favorite.playlist");
        handleLibraryEvent(new LibraryEvent.OpenGroup("virtual:favorites", title));
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
            setStatus(AppLanguage.text(
                    settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                    "streaming.resolving"
            ));
            return;
        }
        playbackStartController.playTrackList(tracks, index);
    }

    private void selectPlaylistFromCollections(long playlistId) {
        routeController.setSelectedPlaylistId(playlistId);
        loadCollections();
    }

    private void openSelectedPlaylistExportDocument() {
        playlistExportController.openSelectedPlaylistExportDocument(
                selectedPlaylistId(),
                selectedPlaylistName(),
                !libraryStore.selectedPlaylistTracks().isEmpty()
        );
    }

    private void publishTrackListChromeState(TrackListChromeState state) {
        navTrackListActions = state.getActions();
        navTrackListHeaderMetrics = state.getHeaderMetrics();
        navTrackListHeaderActions = state.getHeaderActions();
        navTrackListEmptyText = state.getEmptyText();
        navTrackListModeActions = state.getModeActions();
        navTrackListLabels = state.getLabels();
        navLibraryGroupActions = Collections.emptyList();
        navLibraryGroupEmptyText = "";
        navLibraryGroupModeActions = Collections.emptyList();
    }

    private void publishLibraryGroupsChromeState(LibraryGroupsChromeState state) {
        navLibraryGroupActions = state.getActions();
        navLibraryGroupEmptyText = state.getEmptyText();
        navLibraryGroupModeActions = state.getModeActions();
        navTrackListActions = Collections.emptyList();
        navTrackListHeaderMetrics = Collections.emptyList();
        navTrackListHeaderActions = Collections.emptyList();
        navTrackListEmptyText = "";
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
                ""
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
        persistRouteFields();
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
                    networkSourcesViewModel,
                    streamingViewModel,
                    playbackViewModel,
                    navHomeActions,
                    navTrackListActions,
                    navTrackListHeaderMetrics,
                    navTrackListHeaderActions,
                    navTrackListEmptyText,
                    navTrackListModeActions,
                    navTrackListLabels,
                    navLibraryGroupActions,
                    navLibraryGroupEmptyText,
                    navLibraryGroupModeActions,
                    navCollectionsActions,
                    navSettingsActions,
                    navSettingsScrollState,
                    navNetworkSourceActions,
                    navNetworkSourceHeaderActions,
                    navNetworkSourceEmptyText,
                    navNetworkSourceLabels,
                    navNetworkMenuTitle,
                    navNetworkMenuMetrics,
                    navNetworkMenuActions,
                    navStreamingSearchLabels,
                    navStreamingSearchActions,
                    settingsStore.nowPlayingGesturesEnabled(),
                    selectedTab(),
                    downloadsViewModel,
                    trackDownloadManager,
                    () -> playbackService == null ? 0f : playbackService.realtimeBeat(),
                    () -> playbackService == null ? new float[0] : playbackService.realtimeBands(),
                    true,
                    searchViewModel,
                    navSearchActions,
                    this::openSearchFromHome,
                    this::openDownloadFolderPicker,
                    event -> {
                        handleNowPlayingEvent(event);
                        return kotlin.Unit.INSTANCE;
                    }
            );
            return;
        }
        navHostState.setSelectedTabRoute(selectedTab());
        navHostState.setHomeActions(navHomeActions);
        navHostState.setTrackListActions(navTrackListActions);
        navHostState.setTrackListHeaderMetrics(navTrackListHeaderMetrics);
        navHostState.setTrackListHeaderActions(navTrackListHeaderActions);
        navHostState.setTrackListEmptyText(navTrackListEmptyText);
        navHostState.setTrackListModeActions(navTrackListModeActions);
        navHostState.setTrackListLabels(navTrackListLabels);
        navHostState.setLibraryGroupActions(navLibraryGroupActions);
        navHostState.setLibraryGroupEmptyText(navLibraryGroupEmptyText);
        navHostState.setLibraryGroupModeActions(navLibraryGroupModeActions);
        navHostState.setCollectionsActions(navCollectionsActions);
        navHostState.setSettingsActions(navSettingsActions);
        navHostState.setSettingsScrollState(navSettingsScrollState);
        navHostState.setNetworkSourceActions(navNetworkSourceActions);
        navHostState.setNetworkSourceHeaderActions(navNetworkSourceHeaderActions);
        navHostState.setNetworkSourceEmptyText(navNetworkSourceEmptyText);
        navHostState.setNetworkSourceLabels(navNetworkSourceLabels);
        navHostState.setNetworkMenuTitle(navNetworkMenuTitle);
        navHostState.setNetworkMenuMetrics(navNetworkMenuMetrics);
        navHostState.setNetworkMenuActions(navNetworkMenuActions);
        navHostState.setStreamingSearchLabels(navStreamingSearchLabels);
        navHostState.setStreamingSearchActions(navStreamingSearchActions);
        navHostState.setSearchActions(navSearchActions);
        navHostState.setOpenSearchAction(this::openSearchFromHome);
        navHostState.setOpenDownloadDirectoryPickerAction(this::openDownloadFolderPicker);
        navHostState.setNowPlayingEventHandler(event -> {
            handleNowPlayingEvent(event);
            return kotlin.Unit.INSTANCE;
        });
        navHostState.setNowPlayingGesturesEnabled(settingsStore.nowPlayingGesturesEnabled());
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionController.handlePermissionsResult(requestCode);
        if (onboardingVisible) {
            mountNavHostShell();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        documentPickerController.handleActivityResult(requestCode, resultCode, data);
        handleBackupResult(requestCode, resultCode, data);
    }

    private void installBackNavigation() {
        new BackNavigationBindings(this::handleAppBack).install(this, getOnBackPressedDispatcher());
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
        setStatus(AppLanguage.text(
                settingsStore.languageMode(),
                canScan ? "loading.library" : "audio.permission.required"
        ));
        libraryViewModel.loadLibraryJava(
                allowCachedFirst,
                canScan,
                result -> {
                    replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus());
                    if (onboardingVisible && onboardingLibraryScanInProgress) {
                        onboardingLibraryScanInProgress = false;
                        onboardingLibraryScanCompleted = canScan;
                        mountNavHostShell();
                    }
                },
                status -> {
                    setStatus(status);
                    renderSelectedTab();
                }
        );
    }

    private void loadLibraryOnStartup() {
        if (permissionController.hasAudioPermission()) {
            loadLibrary(false);
            return;
        }
        loadLibrary(true);
    }

    private void openAudioFilePicker() {
        documentPickerController.openAudioFilePicker();
    }

    private void openAudioFolderPicker() {
        documentPickerController.openAudioFolderPicker();
    }

    private void openDownloadFolderPicker() {
        if (documentPickerController == null) {
            showActionFeedback("\u76ee\u5f55\u9009\u62e9\u6682\u4e0d\u53ef\u7528");
            return;
        }
        documentPickerController.openDownloadFolderPicker();
    }

    private void setCustomDownloadFolder(final Uri treeUri) {
        if (trackDownloadManager == null || treeUri == null) {
            showActionFeedback("\u65e0\u6cd5\u4fdd\u5b58\u4e0b\u8f7d\u76ee\u5f55");
            return;
        }
        trackDownloadManager.setCustomDownloadDirectory(treeUri);
        if (downloadsViewModel != null) {
            downloadsViewModel.refresh(trackDownloadManager);
        }
        showActionFeedback("\u5df2\u8bbe\u7f6e\u4e0b\u8f7d\u76ee\u5f55\uff1a" + trackDownloadManager.downloadDirectoryLabel());
    }

    private void openM3uFilePicker() {
        documentPickerController.openM3uFilePicker();
    }

    private void openPlaylistM3uFilePicker() {
        documentPickerController.openPlaylistM3uFilePicker();
    }

    private void importSelectedAudioUris(final List<Uri> uris) {
        if (uris.isEmpty()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "no.audio.files.selected"));
            return;
        }
        setStatus(AppLanguage.text(settingsStore.languageMode(), "importing.audio.files"));
        libraryViewModel.importAudioUrisJava(
                uris,
                result -> replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus())
        );
    }

    private void importSelectedAudioFolder(final Uri treeUri) {
        setStatus(AppLanguage.text(settingsStore.languageMode(), "importing.audio.folder"));
        libraryViewModel.importAudioTreeJava(
                treeUri,
                result -> replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus())
        );
    }

    private void importSelectedM3uFile(final Uri playlistUri) {
        setStatus(AppLanguage.text(settingsStore.languageMode(), "importing.m3u.playlist"));
        libraryViewModel.importStreamM3uJava(
                playlistUri,
                result -> {
                    replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus());
                    navigateToNetworkTabPage(NETWORK_STREAMING);
                }
        );
    }

    private void importSelectedPlaylistM3uFile(final Uri playlistUri) {
        setStatus(AppLanguage.text(settingsStore.languageMode(), "import.playlist.m3u"));
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
        setStatus(status);
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
            setStatus(AppLanguage.text(settingsStore.languageMode(), "audio.specs.updated") + " (" + updatedCount + ")");
        }
    }

    private void loadCollections() {
        libraryViewModel.loadCollectionsJava(selectedPlaylistId(), result -> {
            routeController.setSelectedPlaylistId(result.getSelectedPlaylistId());
            libraryStore.applyCollections(result);
            renderNowBar();
            if (TAB_COLLECTIONS.equals(selectedTab())
                    || (TAB_LIBRARY.equals(selectedTab()) && LIBRARY_PLAYLISTS.equals(libraryMode()))
                    || TAB_NETWORK.equals(selectedTab())
                    || TAB_SETTINGS.equals(selectedTab())) {
                renderSelectedTab();
            }
        });
    }

    private void openCollectionsFromHome() {
        routeController.navigateToTab(TAB_COLLECTIONS, true);
        renderCollections();
        renderSelectedTab();
    }

    private void openSearchFromHome() {
        refreshUnifiedSearch(false);
        navigateToTab(TAB_SEARCH, true, true);
        syncNavHostState();
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
            showActionFeedback(reason == null || reason.trim().isEmpty() ? "\u8be5\u5728\u7ebf\u6b4c\u66f2\u6682\u4e0d\u53ef\u64ad\u653e" : reason);
            return;
        }
        showActionFeedback("\u6b63\u5728\u89e3\u6790\u5728\u7ebf\u6b4c\u66f2\uff1a" + track.getTitle());
        streamingViewModel.resolveStreamingTrackForPlayback(
                track.getProvider(),
                track.getProviderTrackId(),
                track,
                selectedStreamingQuality(),
                resolved -> {
                    if (resolved == null) {
                        showActionFeedback("\u5728\u7ebf\u6b4c\u66f2\u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
                        return;
                    }
                    playTrackListFromHost(Collections.singletonList(resolved), 0);
                    showActionFeedback("\u5f00\u59cb\u64ad\u653e\uff1a" + resolved.title);
                }
        );
    }

    private void switchNowPlayingSource(final NowPlayingEffect.SwitchSource effect) {
        if (effect == null || effect.getTrack() == null) {
            showActionFeedback("\u5f53\u524d\u6ca1\u6709\u53ef\u5207\u6362\u7684\u6b4c\u66f2");
            return;
        }
        if (streamingViewModel == null || nowPlayingViewModel == null) {
            showActionFeedback("\u97f3\u6e90\u5207\u6362\u6682\u4e0d\u53ef\u7528");
            return;
        }
        final Track current = effect.getTrack();
        final long positionMs = playbackStore == null || playbackStore.snapshot() == null
                ? 0L
                : playbackStore.snapshot().positionMs;
        final app.yukine.streaming.StreamingAudioQuality quality =
                effect.getQuality() == null ? selectedStreamingQuality() : effect.getQuality();
        showActionFeedback("\u6b63\u5728\u5207\u6362\u97f3\u6e90\uff1a" + effect.getProvider().getWireName());
        streamingViewModel.resolveStreamingTrackForPlayback(
                effect.getProvider(),
                effect.getProviderTrackId(),
                resolveStreamingPlaybackUseCase.metadataFor(current, effect.getProvider(), effect.getProviderTrackId()),
                quality,
                resolved -> {
                    if (resolved == null) {
                        showActionFeedback("\u97f3\u6e90\u5207\u6362\u5931\u8d25\uff0c\u8bf7\u6362\u4e00\u4e2a\u6765\u6e90\u518d\u8bd5");
                        return;
                    }
                    nowPlayingViewModel.replaceCurrentTrackAndResume(resolved, positionMs);
                    showActionFeedback("\u5df2\u5207\u6362\u97f3\u6e90\uff1a" + resolved.title);
                    publishPlaybackState();
                    renderNowBar();
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
        libraryStore.applySearch(searchQuery());
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
            setStatus(onboardingMissingSetupMessage());
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
            setStatus(onboardingMissingSetupMessage());
            mountNavHostShell();
            return;
        }
        openPlaylistM3uFilePicker();
    }

    private void publishLibraryState() {
        if (libraryViewModel == null) {
            return;
        }
        libraryViewModel.updateState(
                routeController == null ? null : routeController.current(),
                viewModel == null ? null : viewModel.getLibrary().getValue()
        );
    }

    private void handleLibraryEvent(LibraryEvent event) {
        if (libraryViewModel == null || event == null) {
            return;
        }
        publishLibraryState();
        libraryViewModel.onEvent(event);
        publishLibraryState();
        // Library events (mode switch, open group, back) re-run the render controllers which
        // update the nav* action fields. Push them into the Compose nav host state so the
        // album/artist/folder rows and mode selector become clickable; otherwise the screen
        // keeps the stale (empty) action snapshot and taps do nothing.
        if (navHostState != null) {
            syncNavHostState();
        }
    }

    private void renderAndPersistSelectedTab() {
        persistRouteFields();
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
        persistRouteFields();
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
        if (!NETWORK_STREAM_LIST.equals(page) && libraryStore != null) {
            libraryStore.clearRecommendationStreamList();
        }
        routeController.setNetworkPage(page);
        renderAndPersistSelectedTab();
    }

    private void navigateToNetworkTabPage(String page) {
        if (!NETWORK_STREAM_LIST.equals(page) && libraryStore != null) {
            libraryStore.clearRecommendationStreamList();
        }
        routeController.setNetworkPage(page);
        navigateToTab(TAB_NETWORK);
    }

    private void clearRemoteSourceAndNavigateNetworkPage(String page) {
        routeController.clearSelectedRemoteSource();
        navigateNetworkPage(page);
    }

    private void navigateSettingsPage(String page) {
        routeController.setSettingsPage(page);
        renderAndPersistSelectedTab();
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
        if (TAB_SETTINGS.equals(selectedTab()) && settingsRenderCoordinator != null) {
            settingsRenderCoordinator.scrollToTopOnNextRender();
        }
    }

    private void renderSelectedTab() {
        if (!uiShellController.hasContentHost()) {
            renderSelectedTabForNavHostState();
            syncNavHostState();
            return;
        }
        persistRouteFields();
        uiShellController.navigateContentRoute(selectedTab());
        renderSelectedTabContent();
    }

    private void renderSelectedTabContent() {
        if (!uiShellController.hasContentHost()) {
            renderSelectedTabForNavHostState();
            syncNavHostState();
            return;
        }
        persistRouteFields();
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

    private void addStateContent(String message) {
        setStatus(message);
    }

    private void addStateContent(String message, List<?> actions) {
        setStatus(message);
    }

    private void addStateContent(String title, String description, List<?> actions) {
        setStatus(description == null || description.isEmpty() ? title : title + ": " + description);
    }

    private void updateTabBar() {
        uiShellController.updateTabBar(selectedTab());
    }

    private void setStatus(String status) {
        if (statusMessageController == null || uiShellController == null) {
            return;
        }
        statusMessageController.setStatus(status);
    }

    private void showActionFeedback(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        setStatus(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
        publishLibraryState();
        if (!permissionController.hasAudioPermission()) {
            addStateContent(
                    AppLanguage.text(settingsStore.languageMode(), "audio.permission.required"),
                    AppLanguage.text(settingsStore.languageMode(), "audio.permission.description"),
                    Collections.emptyList()
            );
            return;
        }
        if (libraryStore.visibleTracks().isEmpty() && !LIBRARY_PLAYLISTS.equals(libraryMode())) {
            addStateContent(
                    AppLanguage.text(settingsStore.languageMode(), "no.music"),
                    AppLanguage.text(settingsStore.languageMode(), "no.music.description"),
                    Collections.emptyList()
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
                libraryStore.favoriteIds()
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
                handleLibraryEvent(new LibraryEvent.ChangeGroupMode(mode));
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

    private void confirmClearPlayHistory() {
        confirmationDialogController.confirmClearPlayHistory();
    }

    private void clearPlayHistory() {
        setStatus(AppLanguage.text(settingsStore.languageMode(), "clearing.play.history"));
        libraryViewModel.clearPlayHistoryJava(removed -> {
            libraryStore.clearPlayHistory();
            setStatus(AppLanguage.text(settingsStore.languageMode(), "cleared.play.history.prefix") + removed);
            loadCollections();
        });
    }

    private String selectedPlaylistName() {
        return libraryStore.selectedPlaylistName(selectedPlaylistId());
    }

    private void showCreatePlaylistDialog() {
        playlistDialogController.showCreatePlaylist();
    }

    private void showRenamePlaylistDialog(final Playlist playlist) {
        playlistDialogController.showRenamePlaylist(playlist);
    }

    private void confirmDeletePlaylist(final Playlist playlist) {
        playlistDialogController.confirmDeletePlaylist(playlist);
    }

    private void showAddToPlaylistDialog(final Track track) {
        if (libraryStore.playlists().isEmpty()) {
            libraryViewModel.addToDefaultPlaylistJava(track, (playlistId, added) -> {
                playlistActionResultController.onDefaultPlaylistTrackAdded(playlistId, added);
            });
            return;
        }

        playlistDialogController.showAddToPlaylist(track, libraryStore.playlists());
    }

    private void shareTrack(final Track track) {
        if (track == null) {
            showActionFeedback(AppLanguage.text(settingsStore.languageMode(), "no.track.selected"));
            return;
        }
        if (trackShareManager == null) {
            showActionFeedback("\u5206\u4eab\u670d\u52a1\u6682\u4e0d\u53ef\u7528");
            return;
        }
        try {
            String shareStyle = settingsStore == null ? TrackShareStyle.defaultValue() : settingsStore.shareStyle();
            showActionFeedback("\u6b63\u5728\u6253\u5f00\u5206\u4eab\u9762\u677f\uff1a" + track.title);
            if (TrackShareStyle.PLATFORM_CARD.equals(TrackShareStyle.normalize(shareStyle))
                    && nativeMusicShareManager != null
                    && nativeMusicShareManager.share(this, track, trackShareManager.musicSharePayload(track))) {
                setStatus("\u5df2\u53d1\u8d77\u539f\u751f\u97f3\u4e50\u5361\u7247\u5206\u4eab\uff1a" + track.title);
                return;
            }
            Intent send = trackShareManager.share(this, track, shareStyle);
            startActivity(Intent.createChooser(send, "\u5206\u4eab\u5230"));
            setStatus("\u5df2\u6253\u5f00\u5206\u4eab\u9762\u677f\uff1a" + track.title);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to share track", error);
            showActionFeedback("\u65e0\u6cd5\u6253\u5f00\u5206\u4eab\u9762\u677f");
        }
    }
    private void downloadTrack(final Track track) {
        if (track == null) {
            showActionFeedback(AppLanguage.text(settingsStore.languageMode(), "no.track.selected"));
            return;
        }
        showDownloadQualityDialog("\u9009\u62e9\u4e0b\u8f7d\u97f3\u8d28", quality -> downloadTrackWithQuality(track, quality, false));
    }

    private void downloadTrackWithQuality(final Track track, final StreamingAudioQuality quality, final boolean silent) {
        StreamingDownloadResolveRequest request = resolveStreamingPlaybackUseCase.prepareDownload(track);
        if (request != null) {
            if (!silent) {
                showActionFeedback("\u6b63\u5728\u89e3\u6790 " + downloadQualityLabel(quality) + " \u4e0b\u8f7d\u5730\u5740\uff1a" + track.title);
            }
            streamingViewModel.resolveStreamingTrackForPlayback(
                    request.getProvider(),
                    request.getProviderTrackId(),
                    request.getMetadata(),
                    quality,
                    resolved -> {
                        if (resolved == null) {
                            if (!silent) {
                                showActionFeedback("\u4e0b\u8f7d\u5730\u5740\u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u5148\u786e\u8ba4\u8be5\u97f3\u6e90\u53ef\u4ee5\u64ad\u653e");
                            }
                            return;
                        }
                        enqueueTrackDownload(resolved, quality, silent);
                    }
            );
            return;
        }
        enqueueTrackDownload(track, quality, silent);
    }

    private void downloadTracks(final List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            showActionFeedback("\u5f53\u524d\u6b4c\u5355\u6ca1\u6709\u53ef\u4e0b\u8f7d\u7684\u6b4c\u66f2");
            return;
        }
        showDownloadQualityDialog("\u9009\u62e9\u6b4c\u5355\u4e0b\u8f7d\u97f3\u8d28", quality -> {
            showActionFeedback("\u5df2\u521b\u5efa\u4e0b\u8f7d\u961f\u5217\uff1a" + tracks.size() + " \u9996\uff0c\u97f3\u8d28\uff1a" + downloadQualityLabel(quality));
            setStatus("\u4e0b\u8f7d\u961f\u5217\u5df2\u521b\u5efa\uff1a" + tracks.size() + " \u9996\u3002\u53ef\u5230\u201c\u4e0b\u8f7d\u7ba1\u7406\u201d\u67e5\u770b\u8fdb\u5ea6\u3002");
            for (Track track : tracks) {
                downloadTrackWithQuality(track, quality, true);
            }
        });
    }

    private void enqueueTrackDownload(final Track track, final StreamingAudioQuality quality, final boolean silent) {
        if (trackDownloadManager == null) {
            if (!silent) {
                showActionFeedback("\u4e0b\u8f7d\u670d\u52a1\u6682\u4e0d\u53ef\u7528");
            }
            return;
        }
        TrackDownloadResult result = trackDownloadManager.enqueue(track, quality);
        if (!silent) {
            showActionFeedback(result.getMessage());
        }
        if (downloadsViewModel != null) {
            downloadsViewModel.refresh(trackDownloadManager);
        }
        if (result.getStarted() && !silent) {
            setStatus(result.getMessage() + "\u3002\u53ef\u5230\u201c\u4e0b\u8f7d\u7ba1\u7406\u201d\u67e5\u770b\u8fdb\u5ea6\u3002");
        }
    }

    private void showDownloadQualityDialog(final String title, final DownloadQualityCallback callback) {
        StreamingAudioQuality[] qualities = new StreamingAudioQuality[]{
                StreamingAudioQuality.STANDARD,
                StreamingAudioQuality.HIGH,
                StreamingAudioQuality.LOSSLESS,
                StreamingAudioQuality.HIRES
        };
        CharSequence[] labels = new CharSequence[]{
                "\u6807\u51c6 - \u66f4\u7a33\uff0c\u4f53\u79ef\u5c0f",
                "\u9ad8\u97f3\u8d28 - \u63a8\u8350",
                "\u65e0\u635f - \u4f18\u5148 FLAC/\u9ad8\u7801\u7387",
                "Hi-Res - \u53ef\u7528\u65f6\u4f7f\u7528\u6700\u9ad8\u89c4\u683c"
        };
        EchoDialog.builder(this)
                .setTitle(title)
                .setMessage("\u5982\u679c\u97f3\u6e90\u4e0d\u652f\u6301\u6240\u9009\u97f3\u8d28\uff0c\u89e3\u6790\u4f1a\u5931\u8d25\u6216\u7531\u97f3\u6e90\u4fa7\u964d\u7ea7\u3002")
                .setItems(labels, (dialog, which) -> callback.run(qualities[which]))
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private String downloadQualityLabel(StreamingAudioQuality quality) {
        if (quality == StreamingAudioQuality.STANDARD) {
            return "\u6807\u51c6";
        }
        if (quality == StreamingAudioQuality.HIGH) {
            return "\u9ad8\u97f3\u8d28";
        }
        if (quality == StreamingAudioQuality.LOSSLESS) {
            return "\u65e0\u635f";
        }
        return "Hi-Res";
    }

    private interface DownloadQualityCallback {
        void run(StreamingAudioQuality quality);
    }

    private void createPlaylist(final String name) {
        libraryViewModel.createPlaylistJava(name, playlistId -> {
            playlistActionResultController.onPlaylistCreated(playlistId);
        });
    }

    private void renamePlaylist(final long playlistId, final String name) {
        libraryViewModel.renamePlaylistJava(playlistId, name, (renamedPlaylistId, renamed) -> {
            playlistActionResultController.onPlaylistRenamed(renamedPlaylistId, renamed);
        });
    }

    private void deletePlaylist(final long playlistId, final String name) {
        libraryViewModel.deletePlaylistJava(playlistId, name, (deletedPlaylistId, deletedName, deleted) -> {
            playlistActionResultController.onPlaylistDeleted(deletedPlaylistId, deletedName, deleted);
        });
    }

    private void removeSelectedPlaylistTrack(final long playlistId, final Track track) {
        libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track, (removedPlaylistId, removedTrack) -> {
            playlistActionResultController.onSelectedPlaylistTrackRemoved(removedPlaylistId, removedTrack);
        });
    }

    private void moveSelectedPlaylistTrack(final long playlistId, final Track track, final int trackIndex, final int direction) {
        libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction, (movedPlaylistId, movedTrack, movedDirection, moved) -> {
            playlistActionResultController.onSelectedPlaylistTrackMoved(movedPlaylistId, movedTrack, movedDirection, moved);
        });
    }

    private void addTrackToPlaylist(final long playlistId, final long trackId) {
        libraryViewModel.addTrackToPlaylistJava(playlistId, trackId, (addedPlaylistId, added) -> {
            playlistActionResultController.onTrackAddedToPlaylist(addedPlaylistId, added);
        });
    }

    private void renderQueue() {
        if (playbackService == null) {
            addStateContent(
                    AppLanguage.text(settingsStore.languageMode(), "playback.service.unavailable"),
                    AppLanguage.text(settingsStore.languageMode(), "playback.service.unavailable.description"),
                    Collections.emptyList()
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

    private void confirmClearQueue() {
        queueActionController.confirmClearQueue();
    }

    private void clearQueue() {
        queueActionController.clearQueue();
    }

    private void moveQueueTrack(int fromIndex, int toIndex) {
        queueActionController.moveQueueTrack(fromIndex, toIndex);
    }

    private void renderNowPlaying() {
        Track track = playbackStore.snapshot().currentTrack;
        if (track == null) {
            addStateContent(AppLanguage.text(settingsStore.languageMode(), "no.track.selected"));
            return;
        }
        if (!nowPlayingRenderController.render(playbackStore, lyricsState(), settingsStore.languageMode())) {
            addStateContent(AppLanguage.text(settingsStore.languageMode(), "no.track.selected"));
        }
    }

    private boolean updateNowPlayingContent() {
        return nowPlayingRenderController.update(playbackStore, lyricsState(), settingsStore.languageMode());
    }

    private void renderNetwork() {
        networkRenderCoordinator.render(settingsStore.languageMode(), networkPage(), selectedRemoteSourceId(), searchQuery());
    }

    private void renderSearch() {
        navSearchActions = new app.yukine.ui.UnifiedSearchActions(
                this::updateUnifiedSearchQuery,
                this::performUnifiedSearch,
                this::playUnifiedSearchTrack,
                this::playUnifiedStreamingTrack,
                this::loadMoreUnifiedStreamingResults,
                this::clearUnifiedSearchOnExit
        );
        refreshUnifiedSearch(false);
    }

    private ArrayList<Track> webDavTracksForSource(long sourceId) {
        return libraryStore.webDavTracksForSource(sourceId);
    }

    private void showEditStreamDialog(final Track track) {
        networkDialogController.showEditStream(track);
    }

    private void syncUpdatedStreamQueue(long oldTrackId, Track updated) {
        nowPlayingViewModel.replaceQueuedTrack(oldTrackId, updated);
    }

    private void playRemoteSourceTracks(RemoteSource source) {
        if (source == null) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "source.not.found"));
            return;
        }
        ArrayList<Track> tracks = webDavTracksForSource(source.id);
        if (tracks.isEmpty()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "no.source.tracks.to.play"));
            return;
        }
        playTrackListFromHost(tracks, 0);
    }

    private void confirmDeleteTrack(final Track track) {
        confirmationDialogController.confirmDeleteTrack(track);
    }

    private void confirmDeleteTracks(final String title, final List<Track> tracks) {
        confirmationDialogController.confirmDeleteTracks(title, tracks);
    }

    private void deleteAllStreams() {
        networkRequestController.deleteAllStreams();
    }

    private void deleteTrack(final long trackId, final String status) {
        networkRequestController.deleteTrack(trackId, status);
    }

    private void deleteTracks(final List<Long> trackIds, final String status) {
        networkRequestController.deleteTracks(trackIds, status);
    }

    private void deleteRemoteSource(final long sourceId, final String name) {
        networkRequestController.deleteRemoteSource(sourceId);
    }

    private void retainPlaybackTracks(List<Track> tracksToKeep) {
        nowPlayingViewModel.retainTracks(tracksToKeep);
    }

    private void testRemoteSource(final long sourceId) {
        networkRequestController.testRemoteSource(sourceId);
    }

    private void syncRemoteSource(final long sourceId) {
        final String sourceName = remoteSourceName(sourceId);
        networkRequestController.syncRemoteSource(sourceId, sourceName);
    }

    private void renderSettings() {
        settingsRenderCoordinator.render(settingsPage());
    }

    private void syncSelectedPlaylistFromStreaming() {
        streamingPlaylistController.syncSelectedPlaylistFromStreaming();
    }

    private void showStreamingProviderPicker(String playlistName, java.util.List<Track> tracks) {
        StreamingProviderPickerRequest request = streamingViewModel.prepareStreamingImportProviderPickerRequest(
                streamingViewModel.getStreaming().getValue().getProviders(),
                true,
                settingsStore.languageMode()
        );
        if (!request.getValid()) {
            setStatus(request.getEmptyStatus());
            return;
        }
        EchoDialog.builder(this)
                .setTitle(request.getTitle())
                .setItems(request.getPickerState().getLabels(), (dialog, which) -> {
                    app.yukine.streaming.StreamingProviderDescriptor descriptor =
                            request.getPickerState().getProviders().get(which);
                    streamingPlaylistController.runStreamingPlaylistImport(descriptor.getName(), playlistName, tracks);
                })
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .show();
    }

    // ---- Import a streaming playlist INTO the local library ----

    private void showImportStreamingPlaylistDialog() {
        StreamingPlaylistImportDialogState dialogState =
                streamingViewModel.prepareStreamingPlaylistImportDialogState(settingsStore.languageMode());
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint(dialogState.getHint());
        input.setTextColor(EchoTheme.textArgb(this));
        input.setHintTextColor(EchoTheme.mutedArgb(this));
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        EchoDialog.builder(this)
                .setTitle(dialogState.getTitle())
                .setView(input)
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .setPositiveButton(AppLanguage.text(settingsStore.languageMode(), "ok"), (dialog, which) ->
                        streamingPlaylistController.importStreamingPlaylistFromLink(input.getText().toString()))
                .show();
    }

    private void showStreamingCookieDialogContent(final StreamingManualCookieDialogState dialogState) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(dialogState.getHint());
        input.setTextColor(EchoTheme.textArgb(this));
        input.setHintTextColor(EchoTheme.mutedArgb(this));
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        EchoDialog.builder(this)
                .setTitle(dialogState.getTitle())
                .setView(input)
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .setPositiveButton(AppLanguage.text(settingsStore.languageMode(), "ok"), (dialog, which) ->
                        streamingManualCookieController.saveStreamingCookie(dialogState.getProvider(), input.getText().toString()))
                .show();
    }

    private void showStreamingPlaylistLoadedDialog(String message) {
        EchoDialog.builder(this)
                .setTitle(streamingViewModel.streamingPlaylistLoadedDialogTitle(settingsStore.languageMode()))
                .setMessage(message)
                .setPositiveButton(AppLanguage.text(settingsStore.languageMode(), "ok"), null)
                .show();
    }

    private void showAccountPlaylistImportPicker(
            final StreamingProviderName provider,
        final java.util.List<StreamingPlaylist> playlists
    ) {
        if (playlists == null || playlists.isEmpty()) {
            setStatus(StreamingAccountPlaylistImportText.noAccountPlaylists(settingsStore.languageMode()));
            return;
        }
        final java.util.ArrayList<StreamingPlaylist> available = new java.util.ArrayList<>();
        for (StreamingPlaylist playlist : playlists) {
            if (playlist != null && playlist.getProviderPlaylistId() != null
                    && !playlist.getProviderPlaylistId().trim().isEmpty()) {
                available.add(playlist);
            }
        }
        if (available.isEmpty()) {
            setStatus(StreamingAccountPlaylistImportText.noAccountPlaylists(settingsStore.languageMode()));
            return;
        }
        final java.util.ArrayList<android.widget.CheckBox> boxes = new java.util.ArrayList<>();
        android.widget.LinearLayout list = new android.widget.LinearLayout(this);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        int verticalPad = Math.round(8 * getResources().getDisplayMetrics().density);
        for (StreamingPlaylist playlist : available) {
            android.widget.CheckBox box = new android.widget.CheckBox(this);
            box.setChecked(true);
            box.setText(accountPlaylistLabel(playlist));
            box.setTextColor(EchoTheme.textArgb(this));
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(EchoTheme.accentArgb(this)));
            box.setPadding(0, verticalPad, 0, verticalPad);
            boxes.add(box);
            list.addView(box, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        EchoDialog.builder(this)
                .setTitle(StreamingAccountPlaylistImportText.title(settingsStore.languageMode()))
                .setMessage(StreamingAccountPlaylistImportText.message(settingsStore.languageMode()))
                .setView(list)
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .setPositiveButton(StreamingAccountPlaylistImportText.confirm(settingsStore.languageMode()),
                        (dialog, which) -> {
                            java.util.ArrayList<StreamingPlaylist> selected = new java.util.ArrayList<>();
                            for (int i = 0; i < boxes.size(); i++) {
                                if (boxes.get(i).isChecked()) {
                                    selected.add(available.get(i));
                                }
                            }
                            streamingPlaylistController.importSelectedAccountPlaylists(provider, selected);
                        })
                .show();
    }

    private String accountPlaylistLabel(StreamingPlaylist playlist) {
        String title = playlist.getTitle() == null || playlist.getTitle().trim().isEmpty()
                ? AppLanguage.text(settingsStore.languageMode(), "playlist")
                : playlist.getTitle();
        Integer count = playlist.getTrackCount();
        if (count == null || count < 0) {
            return title;
        }
        return title + " · " + count + StreamingAccountPlaylistImportText.trackCountSuffix(settingsStore.languageMode());
    }

    // ---- Pull the user's liked/favorite tracks FROM streaming INTO a local playlist ----

    private void showImportStreamingFavoritesProviderPicker() {
        StreamingProviderPickerRequest request = streamingViewModel.prepareStreamingImportProviderPickerRequest(
                streamingViewModel.getStreaming().getValue().getProviders(),
                false,
                settingsStore.languageMode()
        );
        if (!request.getValid()) {
            setStatus(request.getEmptyStatus());
            return;
        }
        EchoDialog.builder(this)
                .setTitle(request.getTitle())
                .setItems(request.getPickerState().getLabels(), (dialog, which) ->
                        streamingPlaylistController.importStreamingLikedTracks(request.getPickerState().getProviders().get(which).getName()))
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .show();
    }

    /**
     * Fetches NetEase 姣忔棩鎺ㄨ崘 (daily recommendations) and plays the returned list immediately. The
     * tracks are streaming placeholders, so {@link #playTrackList} resolves each playback URL lazily
     * and the next track is pre-resolved by the existing prefetch path.
     */
    private void playStreamingDailyRecommendations(app.yukine.streaming.StreamingProviderName provider) {
        dailyRecommendationController.playStreamingDailyRecommendations(provider);
    }

    /**
     * Fetches NetEase 蹇冨姩鎺ㄨ崘 (heartbeat / intelligence list, seeded from liked songs) and plays the
     * returned list immediately.
     */
    private void playStreamingHeartbeatRecommendations(app.yukine.streaming.StreamingProviderName provider) {
        heartbeatRecommendationController.playStreamingHeartbeatRecommendations(provider);
    }

    private HeartbeatRecommendationSeedRequest heartbeatRecommendationSeedRequest(
            app.yukine.streaming.StreamingProviderName provider
    ) {
        PlaybackStateSnapshot serviceSnapshot = playbackService == null ? null : playbackService.snapshot();
        List<Track> serviceQueue = playbackService == null ? null : playbackService.queueSnapshot();
        PlaybackStateSnapshot storeSnapshot = playbackStore == null ? null : playbackStore.snapshot();
        List<Track> playbackStateQueue = playbackViewModel == null || playbackViewModel.getPlayback().getValue() == null
                ? null
                : playbackViewModel.getPlayback().getValue().getQueue();
        return streamingViewModel.prepareHeartbeatRecommendationSeedRequest(
                provider,
                serviceSnapshot,
                serviceQueue,
                storeSnapshot,
                playbackStateQueue,
                heartbeatLibraryContextTracks()
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

    private String streamingProviderTrackId(Track track, app.yukine.streaming.StreamingProviderName provider) {
        return streamingTrackMatchUseCase.directProviderTrackId(track, provider);
    }

    private void logHeartbeatSeedMiss(HeartbeatRecommendationSeedRequest request) {
        if (request == null || request.getSeedMissingMessage() == null || request.getSeedMissingMessage().isEmpty()) {
            return;
        }
        Log.w(TAG, request.getSeedMissingMessage());
    }

    /**
     * Shared tail for the recommendation entry points: maps streaming tracks to placeholder
     * {@link Track}s and starts playback from the top, or shows {@code emptyStatus} when nothing came
     * back. Runs on the main thread (delivered from the ViewModel callback).
     */
    private void openRecommendationPlaylist(StreamingRecommendationPresentation presentation) {
        if (presentation.getEmpty()) {
            setStatus(presentation.getEmptyStatus());
            return;
        }
        setStatus(presentation.getReadyStatus());
        playTrackListFromHost(presentation.getTracks(), 0);
        navigateToTab(TAB_QUEUE, true, true);
    }

    private void playHeartbeatRecommendationTracks(
            java.util.List<app.yukine.streaming.StreamingTrack> streamingTracks,
            String emptyStatus,
            String playingStatus
    ) {
        StreamingRecommendationPresentation presentation =
                streamingViewModel.prepareHeartbeatRecommendationPresentation(streamingTracks, emptyStatus, playingStatus);
        if (presentation.getEmpty()) {
            setStatus(presentation.getEmptyStatus());
            return;
        }
        setStatus(presentation.getReadyStatus());
        playbackStartController.playHeartbeatRecommendationTrackList(presentation.getTracks(), 0);
    }

    private void syncStreamingPlaylists(java.util.List<app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist> links) {
        streamingPlaylistController.syncStreamingPlaylists(links);
    }

    private String remoteSourceName(long sourceId) {
        return libraryStore.remoteSourceName(sourceId);
    }

    private void renderNowBar() {
        if (nowPlayingStateController != null) {
            nowPlayingStateController.renderNowBar();
        }
    }

    private NowPlayingUiState publishNowPlayingState(PlaybackStateSnapshot snapshot) {
        return nowPlayingStateController == null
                ? new NowPlayingUiState()
                : nowPlayingStateController.publish(snapshot);
    }

    private void handleNowPlayingEvent(NowPlayingEvent event) {
        if (nowPlayingViewModel == null || event == null) {
            return;
        }
        if (playbackStore != null && libraryStore != null) {
            publishNowPlayingState(playbackStore.snapshot());
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

    // Backup / Restore

    private static final int REQUEST_EXPORT_BACKUP = 9010;
    private static final int REQUEST_IMPORT_BACKUP = 9011;

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "yukine-backup.zip");
        startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMPORT_BACKUP);
    }

    private void handleBackupResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        String languageMode = settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
        if (requestCode == REQUEST_EXPORT_BACKUP) {
            boolean ok = app.yukine.backup.BackupManager.INSTANCE.export(this, uri);
            setStatus(AppLanguage.text(languageMode, ok ? "backup.export.success" : "backup.export.failed"));
        } else if (requestCode == REQUEST_IMPORT_BACKUP) {
            boolean ok = app.yukine.backup.BackupManager.INSTANCE.restore(this, uri);
            setStatus(AppLanguage.text(languageMode, ok ? "backup.import.success" : "backup.import.failed"));
        }
    }

}
