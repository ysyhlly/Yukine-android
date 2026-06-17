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
import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;
import app.yukine.ui.LibraryGroupActions;
import app.yukine.ui.LibraryGroupUiState;
import app.yukine.ui.QueueTrackUiState;
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

    private MainActivityViewModel viewModel;
    private NowPlayingViewModel nowPlayingViewModel;
    private app.yukine.queue.QueueViewModel queueViewModel;
    private LibraryViewModel libraryViewModel;
    private CollectionsViewModel collectionsViewModel;
    private SettingsViewModel settingsViewModel;
    private NetworkSourcesViewModel networkSourcesViewModel;
    private MainRouteController routeController;
    private MainStatePublisher statePublisher;
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
    private NetworkSourcesRenderController networkSourcesRenderController;
    private NetworkMenuRenderController networkMenuRenderController;
    private NetworkTrackListRenderController networkTrackListRenderController;
    private StreamingGatewayEventController streamingGatewayEventController;
    private StreamingGatewayController streamingGatewayController;
    private StreamingAuthCallbackController streamingAuthCallbackController;
    private ActivityIntentController activityIntentController;
    private StreamingSearchEventController streamingSearchEventController;
    private StreamingSearchRenderController streamingSearchRenderController;
    private NetworkActionsViewModel networkActionsViewModel;
    private NetworkRequestController networkRequestController;
    private HomeDashboardRenderController homeDashboardRenderController;
    private LibraryGroupsRenderController libraryGroupsRenderController;
    private CollectionsRenderController collectionsRenderController;
    private NowPlayingRenderController nowPlayingRenderController;
    private PlaylistExportController playlistExportController;
    private LyricsViewModel lyricsViewModel;
    private EchoPlaybackService playbackService;
    private List<Track> pendingPlaybackTracks = Collections.emptyList();
    private int pendingPlaybackIndex = -1;
    private boolean scrollContentToTopOnNextRender;
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
    private app.yukine.navigation.EchoNavHostState navHostState;

    private NetworkDialogController networkDialogController;
    private PlaylistDialogController playlistDialogController;
    private ConfirmationDialogController confirmationDialogController;

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
        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);
        viewModel.bindStreamingActionGateway(new StreamingActionGatewayBindings(
                this::adaptiveStreamingQuality,
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                launch -> StreamingAuthLauncher.INSTANCE.launch(MainActivity.this, launch),
                this::playTrackList,
                this::onStreamingLoginSuccess
        ));
        viewModel.bindStreamingPlaybackCoordinator(
                resolveStreamingPlaybackUseCase,
                new StreamingPlaybackTaskQueueAdapter(streamingPlaybackTaskScheduler)
        );
        nowPlayingViewModel = new ViewModelProvider(this).get(NowPlayingViewModel.class);
        nowPlayingViewModel.bindGateway(new NowPlayingGatewayBindings(
                this::togglePlayback,
                this::skipToNext,
                this::skipToPrevious,
                nowPlayingViewModel::seekTo,
                () -> playbackStore == null ? null : playbackStore.snapshot().currentTrack,
                track -> handleLibraryEvent(new LibraryEvent.ToggleFavorite(track)),
                this::toggleShuffle,
                this::cycleRepeat,
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
        lyricsViewModel = new ViewModelProvider(this).get(LyricsViewModel.class);
        libraryViewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        libraryViewModel.bindGateway(new LibraryGatewayBindings(
                (tracks, index) -> MainActivity.this.playTrackList(tracks, index),
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> MainActivity.this.setStatus(status),
                (trackId, favorite) -> {
                    libraryStore.setFavorite(trackId, favorite);
                    publishLibraryState();
                    renderNowBar();
                    renderSelectedTab();
                },
                track -> MainActivity.this.showAddToPlaylistDialog(track),
                mode -> {
                    routeController.setLibraryMode(mode);
                    routeController.clearLibraryGroup();
                    if (!LIBRARY_PLAYLISTS.equals(mode)) {
                        routeController.setSelectedPlaylistId(-1L);
                    }
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
        settingsViewModel.bindGateway(new SettingsGatewayBindings(
                this::navigateSettingsPage,
                () -> navigateToNetworkTabPage(NETWORK_HOME),
                () -> loadLibrary(false),
                this::openAudioFilePicker,
                this::openAudioFolderPicker,
                this::setOnlineLyricsEnabled,
                this::reloadCurrentLyrics,
                this::applyLyricsOffset,
                this::startSleepTimer,
                this::cancelSleepTimer,
                this::applyPlaybackSpeed,
                this::applyAppVolume,
                this::applyStreamingAudioQuality,
                this::setConcurrentPlaybackEnabled,
                this::applyThemeMode,
                this::applyAccentMode,
                this::applyLanguageMode,
                this::applyStreamingGatewayEndpoint
        ));
        networkSourcesViewModel = new ViewModelProvider(this).get(NetworkSourcesViewModel.class);
        streamingGatewayEventController = new StreamingGatewayEventController(
                viewModel,
                new StreamingGatewayEventController.Host() {
                    @Override
                    public String languageMode() {
                        return settingsStore.languageMode();
                    }

                    @Override
                    public void renderSelectedTab() {
                        MainActivity.this.renderSelectedTab();
                    }

                    @Override
                    public void setStatus(String message) {
                        MainActivity.this.setStatus(message);
                    }
                }
        );
        streamingGatewayController = new StreamingGatewayController(
                streamingGatewaySettingsStore,
                streamingGatewayEventController,
                streamingGatewayEventController
        );
        streamingGatewayController.configureRepository();
        streamingGatewayEventController.refreshStreamingProviders();
        streamingAuthCallbackController = new StreamingAuthCallbackController(viewModel);
        activityIntentController = new ActivityIntentController(streamingAuthCallbackController);
        activityIntentController.handleInitialIntent(getIntent());
        routeController = new MainRouteController(viewModel);
        statePublisher = new MainStatePublisher(viewModel);
        playbackStore = new MainPlaybackStore(viewModel);
        statusMessageController = new StatusMessageController(new StatusMessageController.Host() {
            @Override
            public String languageMode() {
                return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
            }

            @Override
            public void updateStatus(String message) {
                uiShellController.updateStatus(message);
            }
        });
        permissionController = new MainPermissionController(this, new MainPermissionController.Listener() {
            @Override
            public void onAudioPermissionResult() {
                if (permissionController.hasAudioPermission()) {
                    loadLibrary(false);
                }
            }
        });
        uiShellController = new MainUiShellController(this);
        documentPickerController = new DocumentPickerController(this, new DocumentPickerController.Listener() {
            @Override
            public void importAudioUris(ArrayList<Uri> uris) {
                MainActivity.this.importSelectedAudioUris(uris);
            }

            @Override
            public void importAudioFolder(Uri treeUri) {
                MainActivity.this.importSelectedAudioFolder(treeUri);
            }

            @Override
            public void importStreamM3u(Uri playlistUri) {
                MainActivity.this.importSelectedM3uFile(playlistUri);
            }

            @Override
            public void exportPlaylist(Uri exportUri) {
                playlistExportController.exportSelectedPlaylistToUri(exportUri);
            }

            @Override
            public void importPlaylistM3u(Uri playlistUri) {
                MainActivity.this.importSelectedPlaylistM3uFile(playlistUri);
            }
        });
        playlistExportController = new PlaylistExportController(new PlaylistExportController.Listener() {
            @Override
            public void openPlaylistExportDocument(String playlistName) {
                documentPickerController.openPlaylistExportDocument(playlistName);
            }

            @Override
            public void exportPlaylist(Uri exportUri, long playlistId, String playlistName) {
                libraryViewModel.exportPlaylistJava(
                        exportUri,
                        playlistId,
                        playlistName,
                        exported -> setStatus(exported
                                ? AppLanguage.text(settingsStore.languageMode(), "playlist.exported")
                                : AppLanguage.text(settingsStore.languageMode(), "playlist.export.failed"))
                );
            }

            @Override
            public void setStatus(String status) {
                MainActivity.this.setStatus(status);
            }
        });
        playbackStateUpdateController = new PlaybackStateUpdateController();
        playbackStateEventController = new PlaybackStateEventController(
                mainHandler,
                playbackStore,
                playbackStateUpdateController,
                new PlaybackStateEventController.ServiceQueueSource() {
                    @Override
                    public EchoPlaybackService service() {
                        return playbackService;
                    }
                },
                new PlaybackStateEventController.Listener() {
                    @Override
                    public String selectedTab() {
                        return MainActivity.this.selectedTab();
                    }

                    @Override
                    public long currentLyricsTrackId() {
                        return lyricsViewModel == null ? -1L : lyricsViewModel.trackId();
                    }

                    @Override
                    public void savePlaybackSettings(float playbackSpeed, float appVolume) {
                        settingsStore.setPlaybackSpeed(playbackSpeed);
                        settingsStore.setAppVolume(appVolume);
                    }

                    @Override
                    public void loadLyrics(Track track) {
                        MainActivity.this.loadLyrics(track);
                    }

                    @Override
                    public void loadCollections() {
                        MainActivity.this.loadCollections();
                    }

                    @Override
                    public void renderNowBar() {
                        MainActivity.this.renderNowBar();
                    }

                    @Override
                    public void updateHomeDashboardPlayback(PlaybackStateSnapshot snapshot) {
                        viewModel.updateHomeDashboardPlayback(snapshot);
                    }

                    @Override
                    public void renderSelectedTab() {
                        MainActivity.this.renderSelectedTab();
                    }

                    @Override
                    public void updateNowPlayingContent() {
                        MainActivity.this.updateNowPlayingContent();
                    }

                    @Override
                    public void preResolveNextStreamingTrack(PlaybackStateSnapshot snapshot) {
                        MainActivity.this.preResolveNextStreamingTrack(snapshot);
                    }

                    @Override
                    public void recoverStreamingBuffering(PlaybackStateSnapshot snapshot) {
                        MainActivity.this.recoverStreamingBuffering(snapshot);
                    }

                    @Override
                    public void setStatus(String status) {
                        MainActivity.this.setStatus(status);
                    }
                }
        );
        playbackServiceHostController = new PlaybackServiceHostController(
                new PlaybackServiceHostBindings(
                        () -> settingsStore.playbackSpeed(),
                        () -> settingsStore.appVolume(),
                        () -> settingsStore.concurrentPlaybackEnabled(),
                        service -> playbackService = service,
                        () -> playbackStore.reset(),
                        this::playPendingTracksIfNeeded,
                        this::renderSelectedTab,
                        this::renderNowBar
                )
        );
        playbackServiceConnectionController = new PlaybackServiceConnectionController(
                this,
                playbackStateEventController,
                playbackServiceHostController
        );
        tabRenderDispatcher = new MainTabRenderDispatcher(new MainTabRenderDispatcher.Renderer() {
            @Override
            public void renderHome() {
                MainActivity.this.renderHome();
            }

            @Override
            public void renderLibrary() {
                MainActivity.this.renderLibrary();
            }

            @Override
            public void renderCollections() {
                MainActivity.this.renderCollections();
            }

            @Override
            public void renderQueue() {
                MainActivity.this.renderQueue();
            }

            @Override
            public void renderNowPlaying() {
                MainActivity.this.renderNowPlaying();
            }

            @Override
            public void renderNetwork() {
                MainActivity.this.renderNetwork();
            }

            @Override
            public void renderSettings() {
                MainActivity.this.renderSettings();
            }
        });
        trackListRenderController = new TrackListRenderController(libraryViewModel, new TrackListRenderController.Listener() {
            @Override
            public void playTrackList(List<Track> tracks, int index) {
                handleLibraryEvent(new LibraryEvent.PlayTrackList(tracks, index));
            }

            @Override
            public void toggleFavorite(Track track) {
                handleLibraryEvent(new LibraryEvent.ToggleFavorite(track));
            }

            @Override
            public void showAddToPlaylist(Track track) {
                handleLibraryEvent(new LibraryEvent.AddToPlaylist(track));
            }

            @Override
            public void showEditStream(Track track) {
                MainActivity.this.showEditStreamDialog(track);
            }

            @Override
            public void confirmDeleteTrack(Track track) {
                MainActivity.this.confirmDeleteTrack(track);
            }

            @Override
            public void publishTrackList(String title, ArrayList<TrackRowUiState> rows) {
                MainActivity.this.publishTrackListUiState(title, rows);
            }

            @Override
            public void publishTrackListChrome(
                    List<TrackRowActions> actions,
                    List<TrackListHeaderMetric> headerMetrics,
                    List<TrackListHeaderAction> headerActions,
                    String emptyText,
                    List<TrackListModeAction> modeActions,
                    TrackListLabels labels
            ) {
                navTrackListActions = new ArrayList<>(actions);
                navTrackListHeaderMetrics = new ArrayList<>(headerMetrics);
                navTrackListHeaderActions = new ArrayList<>(headerActions);
                navTrackListEmptyText = emptyText;
                navTrackListModeActions = new ArrayList<>(modeActions);
                navTrackListLabels = labels;
                navLibraryGroupActions = Collections.emptyList();
                navLibraryGroupEmptyText = "";
                navLibraryGroupModeActions = Collections.emptyList();
            }

        });
        homeDashboardRenderController = new HomeDashboardRenderController(viewModel, new HomeDashboardRenderController.Listener() {
            @Override
            public void openLibraryMode(String mode) {
                routeController.setLibraryMode(mode);
                routeController.clearLibraryGroup();
                navigateToTab(TAB_LIBRARY, true, true);
            }

            @Override
            public void continuePlayback(Track track) {
                if (playbackStore.snapshot().hasTrack()) {
                    togglePlayback();
                    return;
                }
                if (track != null) {
                    playTrackList(Collections.singletonList(track), 0);
                }
            }

            @Override
            public void openNowPlaying() {
                renderNowBar();
            }

            @Override
            public void playTrack(Track track) {
                playTrackList(Collections.singletonList(track), 0);
            }

            @Override
            public void refreshLibrary() {
                loadLibrary(true);
            }

            @Override
            public void openQueue() {
                navigateToTab(TAB_QUEUE, true, true);
            }

            @Override
            public void shuffleAll() {
                List<Track> allTracks = libraryStore.allTracks();
                if (!allTracks.isEmpty()) {
                    List<Track> shuffled = new ArrayList<>(allTracks);
                    Collections.shuffle(shuffled);
                    playTrackList(shuffled, 0);
                }
            }

            @Override
            public void openStreaming() {
                navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB);
            }

            @Override
            public void openCollections() {
                MainActivity.this.openCollectionsFromHome();
            }

            @Override
            public void playDailyRecommendations() {
                MainActivity.this.playStreamingDailyRecommendations(
                        app.yukine.streaming.StreamingProviderName.NETEASE);
            }

            @Override
            public void playHeartbeatRecommendations() {
                MainActivity.this.playStreamingHeartbeatRecommendations(
                        app.yukine.streaming.StreamingProviderName.NETEASE);
            }

            @Override
            public void publishHomeDashboardActions(app.yukine.ui.HomeDashboardActions actions) {
                navHomeActions = actions;
            }
        });
        queueRenderController = new QueueRenderController(viewModel, new QueueRenderController.Listener() {
            @Override
            public void playTrackList(List<Track> tracks, int index) {
                MainActivity.this.playTrackList(tracks, index);
            }

            @Override
            public void toggleFavorite(Track track) {
                handleLibraryEvent(new LibraryEvent.ToggleFavorite(track));
            }

            @Override
            public void showAddToPlaylist(Track track) {
                MainActivity.this.showAddToPlaylistDialog(track);
            }

            @Override
            public void removeQueueTrack(Track track) {
                MainActivity.this.removeQueueTrack(track);
            }

            @Override
            public void confirmClearQueue() {
                MainActivity.this.confirmClearQueue();
            }

            @Override
            public void requestBack() {
                MainActivity.this.handleAppBack();
            }

            @Override
            public void publishQueue(ArrayList<QueueTrackUiState> rows) {
                MainActivity.this.publishQueueUiState(rows);
            }

            @Override
            public void publishQueueChrome(
                    List<app.yukine.ui.QueueTrackActions> actions,
                    Runnable onClearQueue,
                    app.yukine.ui.QueueScreenLabels labels,
                    Runnable onBack
            ) {
            }

            @Override
            public void addStateContent(String message) {
                MainActivity.this.addStateContent(message);
            }
        });
        networkTrackListRenderController = new NetworkTrackListRenderController(new NetworkTrackListRenderController.Listener() {
            @Override
            public void navigateNetworkPage(String page) {
                MainActivity.this.navigateNetworkPage(page);
            }

            @Override
            public void clearRemoteSourceAndNavigateNetworkPage(String page) {
                MainActivity.this.clearRemoteSourceAndNavigateNetworkPage(page);
            }

            @Override
            public void syncRemoteSource(long sourceId) {
                MainActivity.this.syncRemoteSource(sourceId);
            }

            @Override
            public void playRemoteSourceTracks(RemoteSource source) {
                MainActivity.this.playRemoteSourceTracks(source);
            }

            @Override
            public void playTrackList(List<Track> tracks, int index) {
                MainActivity.this.playTrackList(tracks, index);
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
                MainActivity.this.renderComposeTrackList(
                        title,
                        tracks,
                        showPlaylistAction,
                        details,
                        showStreamActions,
                        headerMetrics,
                        headerActions,
                        emptyText,
                        labels
                );
            }
        });
        streamingSearchEventController = new StreamingSearchEventController(
                viewModel,
                new StreamingSearchEventController.Navigator() {
                    @Override
                    public void backToNetworkHome() {
                        MainActivity.this.navigateNetworkPage(NETWORK_HOME);
                    }

                    @Override
                    public void importStreamingPlaylist(app.yukine.streaming.StreamingPlaylist playlist) {
                        if (playlist == null) {
                            return;
                        }
                        MainActivity.this.importStreamingPlaylistFromProviderRef(
                                playlist.getProvider(),
                                playlist.getProviderPlaylistId()
                        );
                    }

                    @Override
                    public void loadUserPlaylists() {
                        viewModel.loadUserPlaylists(viewModel.getStreaming().getValue().getSelectedProvider());
                    }

                    @Override
                    public void importLikedTracks() {
                        MainActivity.this.importStreamingLikedTracks(
                                viewModel.getStreaming().getValue().getSelectedProvider());
                    }

                    @Override
                    public void playDailyRecommendations() {
                        MainActivity.this.playStreamingDailyRecommendations(
                                viewModel.getStreaming().getValue().getSelectedProvider());
                    }

                    @Override
                    public void playHeartbeatRecommendations() {
                        MainActivity.this.playStreamingHeartbeatRecommendations(
                                viewModel.getStreaming().getValue().getSelectedProvider());
                    }

                    @Override
                    public void pasteImportPlaylist() {
                        MainActivity.this.showImportStreamingPlaylistDialog();
                    }

                    @Override
                    public void inputProviderCookie() {
                        MainActivity.this.showStreamingCookieDialog();
                    }
                },
                new StreamingSearchEventController.ContentSink() {
                    @Override
                    public void publishStreamingSearchChrome(
                            app.yukine.ui.StreamingSearchLabels labels,
                            app.yukine.ui.StreamingSearchActions actions
                    ) {
                        navStreamingSearchLabels = labels;
                        navStreamingSearchActions = actions;
                    }
                }
        );
        streamingSearchRenderController = new StreamingSearchRenderController(
                viewModel,
                () -> settingsStore.languageMode(),
                streamingSearchEventController
        );
        libraryGroupsRenderController = new LibraryGroupsRenderController(libraryViewModel, new LibraryGroupsRenderController.Listener() {
            @Override
            public void selectLibraryGroup(String key, String title) {
                handleLibraryEvent(new LibraryEvent.OpenGroup(key, title));
            }

            @Override
            public void clearLibraryGroupSelection() {
                routeController.clearLibraryGroup();
            }

            @Override
            public void closeLibraryGroup() {
                handleLibraryEvent(LibraryEvent.BackFromGroup.INSTANCE);
            }

            @Override
            public void playTrackList(List<Track> tracks, int index) {
                handleLibraryEvent(new LibraryEvent.PlayTrackList(tracks, index));
            }

            @Override
            public void confirmDeleteGroup(String title, List<Track> tracks) {
                MainActivity.this.confirmDeleteTracks(title, tracks);
            }

            @Override
            public void publishLibraryGroups(String title, ArrayList<LibraryGroupUiState> rows) {
                MainActivity.this.publishLibraryGroupsUiState(title, rows);
            }

            @Override
            public void publishLibraryGroupsChrome(
                    List<LibraryGroupActions> actions,
                    String emptyText,
                    List<TrackListModeAction> modeActions
            ) {
                navLibraryGroupActions = new ArrayList<>(actions);
                navLibraryGroupEmptyText = emptyText;
                navLibraryGroupModeActions = new ArrayList<>(modeActions);
                navTrackListActions = Collections.emptyList();
                navTrackListHeaderMetrics = Collections.emptyList();
                navTrackListHeaderActions = Collections.emptyList();
                navTrackListEmptyText = "";
            }

            @Override
            public void renderTrackList(
                    String title,
                    ArrayList<Track> tracks,
                    ArrayList<TrackListHeaderMetric> headerMetrics,
                    ArrayList<TrackListHeaderAction> headerActions
            ) {
                MainActivity.this.renderComposeTrackList(title, tracks, true, new ArrayList<String>(), false, headerMetrics, headerActions, "");
            }

        });
        collectionsRenderController = new CollectionsRenderController(collectionsViewModel, new CollectionsRenderController.Listener() {
            @Override
            public void showCreatePlaylist() {
                MainActivity.this.showCreatePlaylistDialog();
            }

            @Override
            public void openPlaylistM3uFilePicker() {
                MainActivity.this.openPlaylistM3uFilePicker();
            }

            @Override
            public void confirmClearPlayHistory() {
                MainActivity.this.confirmClearPlayHistory();
            }

            @Override
            public void requestBack() {
                MainActivity.this.handleAppBack();
            }

            @Override
            public void playTrackList(List<Track> tracks, int index) {
                MainActivity.this.playTrackList(tracks, index);
            }

            @Override
            public void toggleFavorite(Track track) {
                handleLibraryEvent(new LibraryEvent.ToggleFavorite(track));
            }

            @Override
            public void showAddToPlaylist(Track track) {
                MainActivity.this.showAddToPlaylistDialog(track);
            }

            @Override
            public void selectPlaylist(long playlistId) {
                routeController.setSelectedPlaylistId(playlistId);
                loadCollections();
            }

            @Override
            public void showRenamePlaylist(Playlist playlist) {
                MainActivity.this.showRenamePlaylistDialog(playlist);
            }

            @Override
            public void confirmDeletePlaylist(Playlist playlist) {
                MainActivity.this.confirmDeletePlaylist(playlist);
            }

            @Override
            public void openSelectedPlaylistExportDocument() {
                playlistExportController.openSelectedPlaylistExportDocument(
                        selectedPlaylistId(),
                        selectedPlaylistName(),
                        !libraryStore.selectedPlaylistTracks().isEmpty()
                );
            }

            @Override
            public void importSelectedPlaylistToStreaming() {
                MainActivity.this.importSelectedPlaylistToStreaming();
            }

            @Override
            public void importFavoritesToStreaming() {
                MainActivity.this.importFavoritesToStreaming();
            }

            @Override
            public void importStreamingFavorites() {
                MainActivity.this.showImportStreamingFavoritesProviderPicker();
            }

            @Override
            public void syncSelectedPlaylistFromStreaming() {
                MainActivity.this.syncSelectedPlaylistFromStreaming();
            }

            @Override
            public void moveSelectedPlaylistTrack(long playlistId, Track track, int trackIndex, int direction) {
                MainActivity.this.moveSelectedPlaylistTrack(playlistId, track, trackIndex, direction);
            }

            @Override
            public void removeSelectedPlaylistTrack(long playlistId, Track track) {
                MainActivity.this.removeSelectedPlaylistTrack(playlistId, track);
            }

            @Override
            public void publishCollectionsActions(app.yukine.ui.CollectionsActions actions) {
                navCollectionsActions = actions;
            }

        });
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
        viewModel.bindStreamingLocalPlaylistOperations(new StreamingLocalPlaylistOperations() {
            @Override
            public app.yukine.model.PlaylistImportResult importStreamingPlaylist(
                    String playlistName,
                    app.yukine.streaming.StreamingProviderName provider,
                    String providerPlaylistId,
                    List<app.yukine.streaming.StreamingTrack> streamingTracks,
                    boolean linkWhenProviderPlaylistIdBlank
            ) {
                return importStreamingPlaylistUseCase.execute(
                        playlistName,
                        provider,
                        providerPlaylistId,
                        streamingTracks,
                        linkWhenProviderPlaylistIdBlank
                );
            }

            @Override
            public StreamingLocalPlaylistSyncResult syncStreamingPlaylist(
                    app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist link,
                    List<app.yukine.streaming.StreamingTrack> streamingTracks
            ) {
                SyncStreamingPlaylistResult result = syncStreamingPlaylistUseCase.execute(link, streamingTracks);
                return new StreamingLocalPlaylistSyncResult(
                        result.getPlaylistId(),
                        result.getSyncedCount(),
                        result.getEmpty()
                );
            }

            @Override
            public StreamingLoginPlaylistResult ensureStreamingLoginPlaylist(
                    String playlistName,
                    app.yukine.streaming.StreamingProviderName provider
            ) {
                EnsureStreamingLoginPlaylistResult result =
                        ensureStreamingLoginPlaylistUseCase.execute(playlistName, provider);
                return new StreamingLoginPlaylistResult(
                        result.getPlaylistId(),
                        result.getPlaylistName()
                );
            }

            @Override
            public app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist linkedPlaylist(long localPlaylistId) {
                return streamingPlaylistLinkUseCase.execute(localPlaylistId);
            }
        });
        streamingTrackMatchUseCase = new StreamingTrackMatchUseCase(
                new MusicLibraryStreamingTrackMatchOperations(repository)
        );
        viewModel.bindStreamingTrackMatchStore(new StreamingTrackMatchStore() {
            @Override
            public String directProviderTrackId(
                    Track track,
                    app.yukine.streaming.StreamingProviderName provider
            ) {
                return streamingTrackMatchUseCase.directProviderTrackId(track, provider);
            }

            @Override
            public String providerTrackIdFor(
                    Track track,
                    app.yukine.streaming.StreamingProviderName provider
            ) {
                return streamingTrackMatchUseCase.providerTrackIdFor(track, provider);
            }

            @Override
            public void saveProviderTrackId(
                    Track track,
                    app.yukine.streaming.StreamingProviderName provider,
                    String providerTrackId
            ) {
                streamingTrackMatchUseCase.saveProviderTrackId(track, provider, providerTrackId);
            }

            @Override
            public String providerTrackIdFromCandidates(
                    List<Track> candidates,
                    app.yukine.streaming.StreamingProviderName provider
            ) {
                return streamingTrackMatchUseCase.providerTrackIdFromCandidates(candidates, provider);
            }

            @Override
            public List<Track> heartbeatSeedCandidates(
                    PlaybackStateSnapshot serviceSnapshot,
                    List<Track> serviceQueue,
                    PlaybackStateSnapshot storeSnapshot,
                    List<Track> viewModelQueue
            ) {
                return streamingTrackMatchUseCase.heartbeatSeedCandidates(
                        serviceSnapshot,
                        serviceQueue,
                        storeSnapshot,
                        viewModelQueue
                );
            }

            @Override
            public List<Track> snapshotQueueForHeartbeat(
                    List<Track> serviceQueue,
                    List<Track> viewModelQueue,
                    PlaybackStateSnapshot storeSnapshot
            ) {
                return streamingTrackMatchUseCase.snapshotQueueForHeartbeat(
                        serviceQueue,
                        viewModelQueue,
                        storeSnapshot
                );
            }

            @Override
            public String heartbeatSeedMissMessage(
                    app.yukine.streaming.StreamingProviderName provider,
                    PlaybackStateSnapshot snapshot,
                    PlaybackStateSnapshot storeSnapshot,
                    List<Track> queue
            ) {
                return streamingTrackMatchUseCase.heartbeatSeedMissMessage(
                        provider,
                        snapshot,
                        storeSnapshot,
                        queue
                );
            }
        });
        toggleFavoriteUseCase = new ToggleFavoriteUseCase(
                new MusicLibraryFavoriteOperations(repository)
        );
        libraryViewModel.bindFavoriteWriter(new LibraryFavoriteWriter() {
            @Override
            public boolean writeFavorite(Track track, boolean favorite) {
                return toggleFavoriteUseCase.execute(track, favorite);
            }
        });

        loadPlaylistTracksUseCase = new LoadPlaylistTracksUseCase(
                new MusicLibraryPlaylistTrackOperations(repository)
        );
        libraryViewModel.bindPlaylistTrackLoader(new LibraryPlaylistTrackLoader() {
            @Override
            public List<Track> loadPlaylistTracks(long playlistId) {
                return loadPlaylistTracksUseCase.execute(playlistId);
            }
        });
        loadLyricsSettingsUseCase = new LoadLyricsSettingsUseCase(
                new MusicLibraryLyricsSettingsOperations(repository)
        );
        LoadedLyricsSettings loadedLyricsSettings = loadLyricsSettingsUseCase.execute();
        LibraryCollectionOperations libraryCollectionOperations =
                new MusicLibraryCollectionOperations(repository);
        LibraryImportOperations libraryImportOperations =
                new MusicLibraryImportOperations(repository);        libraryViewModel.bindCollectionGateway(new LibraryCollectionGateway() {
            @Override
            public LibraryCollectionsResult loadCollections(long selectedPlaylistId) {
                LibraryCollectionsSnapshot loaded = new LoadLibraryCollectionsUseCase(libraryCollectionOperations)
                        .execute(selectedPlaylistId);
                return new LibraryCollectionsResult(
                        loaded.selectedPlaylistId,
                        loaded.favoriteIds,
                        loaded.favoriteTracks,
                        loaded.recentRecords,
                        loaded.mostPlayedRecords,
                        loaded.playlists,
                        loaded.remoteSources,
                        loaded.selectedPlaylistTracks
                );
            }

            @Override
            public int clearPlayHistory() {
                return new ClearPlayHistoryUseCase(libraryCollectionOperations).execute();
            }

            @Override
            public void setFavorite(long trackId, boolean favorite) {
                new SetLibraryFavoriteUseCase(libraryCollectionOperations).execute(trackId, favorite);
            }
        });
        libraryViewModel.bindImportGateway(new LibraryImportGateway() {
            private final LoadLibraryUseCase loadLibraryUseCase = new LoadLibraryUseCase(libraryImportOperations);
            private final ImportAudioUrisUseCase importAudioUrisUseCase = new ImportAudioUrisUseCase(libraryImportOperations);
            private final ImportAudioTreeUseCase importAudioTreeUseCase = new ImportAudioTreeUseCase(libraryImportOperations);
            private final ParseMissingAudioSpecsUseCase parseMissingAudioSpecsUseCase = new ParseMissingAudioSpecsUseCase(libraryImportOperations);

            @Override
            public LibraryLoadResultUi loadCached() {
                LibraryLoadResult result = loadLibraryUseCase.cached();
                return new LibraryLoadResultUi(result.getTracks(), result.getFavorites(), "Library updated");
            }

            @Override
            public LibraryLoadResultUi refresh() {
                LibraryLoadResult result = loadLibraryUseCase.refresh();
                return new LibraryLoadResultUi(result.getTracks(), result.getFavorites(), "Library updated");
            }

            @Override
            public LibraryLoadResultUi importAudioUris(List<Uri> uris) {
                LibraryLoadResult result = importAudioUrisUseCase.execute(uris);
                return new LibraryLoadResultUi(result.getTracks(), result.getFavorites(), "Library updated");
            }

            @Override
            public LibraryLoadResultUi importAudioTree(Uri treeUri) {
                LibraryLoadResult result = importAudioTreeUseCase.execute(treeUri);
                return new LibraryLoadResultUi(result.getTracks(), result.getFavorites(), "Library updated");
            }

            @Override
            public LibraryAudioSpecsResultUi parseMissingAudioSpecs() {
                AudioSpecsParseResult result = parseMissingAudioSpecsUseCase.execute();
                return new LibraryAudioSpecsResultUi(
                        result.getUpdatedCount(),
                        result.getTracks(),
                        result.getFavorites()
                );
            }
        });
        libraryViewModel.bindDocumentGateway(new LibraryDocumentGateway() {
            private final LoadLibraryUseCase loadLibraryUseCase = new LoadLibraryUseCase(libraryImportOperations);
            private final ImportStreamM3uTextUseCase importStreamM3uTextUseCase = new ImportStreamM3uTextUseCase(libraryImportOperations);
            private final ImportPlaylistM3uTextUseCase importPlaylistM3uTextUseCase = new ImportPlaylistM3uTextUseCase(libraryImportOperations);
            private final LoadPlaylistExportTracksUseCase loadPlaylistExportTracksUseCase = new LoadPlaylistExportTracksUseCase(libraryImportOperations);

            @Override
            public LibraryLoadResultUi importStreamM3u(Uri playlistUri) {
                M3uDocumentHelper.ReadResult playlistRead = M3uDocumentHelper.readText(getContentResolver(), playlistUri);
                StreamM3uImportResult imported = playlistRead.success
                        ? importStreamM3uTextUseCase.execute(playlistRead.text)
                        : null;
                LibraryLoadResult fallback = imported == null ? loadLibraryUseCase.cached() : null;
                String status = M3uDocumentHelper.localImportStatus(
                        playlistRead,
                        imported == null ? null : imported.getImportResult()
                );
                return new LibraryLoadResultUi(
                        imported == null ? fallback.getTracks() : imported.getTracks(),
                        imported == null ? fallback.getFavorites() : imported.getFavorites(),
                        status
                );
            }

            @Override
            public LibraryPlaylistImportResultUi importPlaylistM3u(Uri playlistUri) {
                M3uDocumentHelper.ReadResult playlistRead = M3uDocumentHelper.readText(getContentResolver(), playlistUri);
                PlaylistM3uImportResult imported = playlistRead.success
                        ? importPlaylistM3uTextUseCase.execute(
                                playlistRead.text,
                                M3uDocumentHelper.playlistFallbackName(playlistUri)
                        )
                        : null;
                LibraryLoadResult fallback = imported == null ? loadLibraryUseCase.cached() : null;
                PlaylistImportResult importResult = imported == null ? null : imported.getImportResult();
                long playlistId = importResult != null && importResult.playlistId >= 0L ? importResult.playlistId : -1L;
                String status = M3uDocumentHelper.playlistImportStatus(playlistRead, importResult);
                return new LibraryPlaylistImportResultUi(
                        playlistId,
                        imported == null ? fallback.getTracks() : imported.getTracks(),
                        imported == null ? fallback.getFavorites() : imported.getFavorites(),
                        status
                );
            }

            @Override
            public boolean exportPlaylist(Uri exportUri, long playlistId, String playlistName) {
                List<Track> tracks = loadPlaylistExportTracksUseCase.execute(playlistId);
                return M3uDocumentHelper.writeText(
                        getContentResolver(),
                        exportUri,
                        M3uDocumentHelper.buildPlaylistText(playlistName, tracks)
                );
            }
        });
        settingsViewModel.bindPreferenceGateway(update ->
                new ApplySettingsPreferenceUseCase(
                        new MusicLibrarySettingsPreferenceOperations(repository)
                ).execute(update)
        );
        settingsViewModel.bindAppliedListener(
                new SettingsAppliedListener() {
                    @Override
                    public void onThemeModeApplied(String mode) {
                        settingsStore.setThemeMode(mode);
                        uiShellController.applyThemeSurface();
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                0L
                        ).getThemeApplied());
                    }

                    @Override
                    public void onAccentModeApplied(String accent) {
                        settingsStore.setAccentMode(accent);
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                0L
                        ).getAccentApplied());
                    }

                    @Override
                    public void onLanguageModeApplied(String languageMode) {
                        settingsStore.setLanguageMode(languageMode);
                        uiShellController.updateLanguage(settingsStore.languageMode());
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                0L
                        ).getLanguageApplied());
                    }

                    @Override
                    public void onPlaybackSpeedApplied(float speed) {
                        settingsStore.setPlaybackSpeed(speed);
                        if (playbackService != null) {
                            playbackService.setPlaybackSpeed(settingsStore.playbackSpeed());
                        }
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                0L
                        ).getPlaybackSpeedApplied());
                    }

                    @Override
                    public void onAppVolumeApplied(float volume) {
                        settingsStore.setAppVolume(volume);
                        if (playbackService != null) {
                            playbackService.setAppVolume(settingsStore.appVolume());
                        }
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                0L
                        ).getAppVolumeApplied());
                    }

                    @Override
                    public void onStreamingAudioQualityApplied(String quality) {
                        settingsStore.setStreamingAudioQuality(quality);
                        renderSelectedTab();
                        setStatus(viewModel.prepareStreamingStatusText(
                                settingsStore.streamingAudioQuality()
                        ).getStreamingQualityApplied());
                    }

                    @Override
                    public void onOnlineLyricsEnabledApplied(boolean enabled) {
                        if (lyricsViewModel != null) {
                            lyricsViewModel.setOnlineEnabled(enabled);
                        }
                        SettingsAppliedStatusText statusText = settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                0L
                        );
                        setStatus(enabled
                                ? statusText.getOnlineLyricsEnabled()
                                : statusText.getOnlineLyricsDisabled());
                        reloadCurrentLyrics();
                        renderSelectedTab();
                    }

                    @Override
                    public void onConcurrentPlaybackEnabledApplied(boolean enabled) {
                        settingsStore.setConcurrentPlaybackEnabled(enabled);
                        if (playbackService != null) {
                            playbackService.setConcurrentPlaybackEnabled(settingsStore.concurrentPlaybackEnabled());
                        }
                        SettingsAppliedStatusText statusText = settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                0L
                        );
                        setStatus(enabled
                                ? statusText.getConcurrentPlaybackEnabled()
                                : statusText.getConcurrentPlaybackDisabled());
                        renderSelectedTab();
                    }

                    @Override
                    public void onLyricsOffsetApplied(long offsetMs) {
                        if (lyricsViewModel != null) {
                            lyricsViewModel.setOffsetMs(offsetMs);
                        }
                        setStatus(settingsViewModel.prepareAppliedStatusText(
                                settingsStore.languageMode(),
                                settingsStore.themeMode(),
                                settingsStore.accentMode(),
                                settingsStore.playbackSpeed(),
                                settingsStore.appVolume(),
                                offsetMs
                        ).getLyricsOffsetApplied());
                        renderSelectedTab();
                        if (TAB_NOW.equals(selectedTab())) {
                            renderNowBar();
                        }
                    }
                }
        );
        PlaylistActionOperations playlistActionOperations =
                new MusicLibraryPlaylistActionOperations(repository);
        libraryViewModel.bindPlaylistActionGateway(new LibraryPlaylistActionGateway() {
            private final AddToDefaultPlaylistUseCase addToDefaultPlaylistUseCase = new AddToDefaultPlaylistUseCase(playlistActionOperations);
            private final CreatePlaylistUseCase createPlaylistUseCase = new CreatePlaylistUseCase(playlistActionOperations);
            private final RenamePlaylistUseCase renamePlaylistUseCase = new RenamePlaylistUseCase(playlistActionOperations);
            private final DeletePlaylistUseCase deletePlaylistUseCase = new DeletePlaylistUseCase(playlistActionOperations);
            private final AddTrackToPlaylistUseCase addTrackToPlaylistUseCase = new AddTrackToPlaylistUseCase(playlistActionOperations);
            private final RemoveTrackFromPlaylistUseCase removeTrackFromPlaylistUseCase = new RemoveTrackFromPlaylistUseCase(playlistActionOperations);
            private final MovePlaylistTrackUseCase movePlaylistTrackUseCase = new MovePlaylistTrackUseCase(playlistActionOperations);

            @Override
            public LibraryDefaultPlaylistAddResultUi addToDefaultPlaylist(Track track) {
                DefaultPlaylistAddResult result = addToDefaultPlaylistUseCase.execute(track);
                return result == null ? null : new LibraryDefaultPlaylistAddResultUi(result.playlistId, result.added);
            }

            @Override
            public long createPlaylist(String name) {
                return createPlaylistUseCase.execute(name);
            }

            @Override
            public boolean renamePlaylist(long playlistId, String name) {
                return renamePlaylistUseCase.execute(playlistId, name);
            }

            @Override
            public boolean deletePlaylist(long playlistId) {
                return deletePlaylistUseCase.execute(playlistId);
            }

            @Override
            public boolean removeTrackFromPlaylist(long playlistId, Track track) {
                return removeTrackFromPlaylistUseCase.execute(playlistId, track);
            }

            @Override
            public boolean movePlaylistTrack(long playlistId, Track track, int trackIndex, int direction) {
                return movePlaylistTrackUseCase.execute(playlistId, track, trackIndex, direction);
            }

            @Override
            public boolean addTrackToPlaylist(long playlistId, long trackId) {
                return addTrackToPlaylistUseCase.execute(playlistId, trackId);
            }
        });
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
                new NetworkActionsViewModel.Listener() {
                    @Override
                    public void onStreamAdded(List<Track> cached, Set<Long> favorites, String status) {
                        replaceLibrary(cached, favorites, status);
                    }

                    @Override
                    public void onStreamUpdated(long oldTrackId, Track updated, List<Track> cached, Set<Long> favorites, String status) {
                        if (updated != null) {
                            syncUpdatedStreamQueue(oldTrackId, updated);
                        }
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(NETWORK_STREAM_LIST);
                    }

                    @Override
                    public void onStreamPlaylistImported(List<Track> cached, Set<Long> favorites, String status) {
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(NETWORK_STREAMING);
                    }

                    @Override
                    public void onAllStreamsDeleted(List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(NETWORK_STREAMING);
                    }

                    @Override
                    public void onTrackDeleted(List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(NETWORK_STREAM_LIST);
                    }

                    @Override
                    public void onRemoteSourceDeleted(List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(NETWORK_SOURCES);
                        loadCollections();
                    }

                    @Override
                    public void onWebDavSourceSaved(long sourceId, List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(sourceId > 0L ? NETWORK_SOURCES : NETWORK_WEBDAV);
                        loadCollections();
                    }

                    @Override
                    public void onRemoteSourceTested(String status) {
                        setStatus(status);
                        loadCollections();
                    }

                    @Override
                    public void onRemoteSourceSynced(List<Track> cached, Set<Long> favorites, String status) {
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(NETWORK_SOURCES);
                    }

                    @Override
                    public void onAllWebDavSourcesSynced(List<Track> cached, Set<Long> favorites, String status) {
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(NETWORK_WEBDAV);
                    }
                }
        );
        networkRequestController = new NetworkRequestController(
                networkActionsViewModel,
                new NetworkRequestController.Labels() {
                    @Override
                    public String text(String key) {
                        return AppLanguage.text(settingsStore.languageMode(), key);
                    }
                },
                new NetworkRequestController.Listener() {
                    @Override
                    public void setStatus(String status) {
                        MainActivity.this.setStatus(status);
                    }
                }
        );
        lyricsViewModel.configure(
                new LoadTrackLyricsUseCaseLyricsLoader(
                        new LoadTrackLyricsUseCase(new LyricsRepositoryLoadOperations())
                ),
                loadedLyricsSettings.onlineLyricsEnabled,
                loadedLyricsSettings.lyricsOffsetMs
        );
        lyricsViewModel.bindListener(new LyricsStateListener() {
            @Override
            public void onLyricsStateChanged() {
                MainActivity.this.onLyricsStateChanged();
            }
        });
        SettingsPageEventController settingsPageEventController = new SettingsPageEventController(
                settingsViewModel,
                new SettingsPageEventController.ContentSink() {
                    @Override
                    public void publishSettingsChrome(
                            List<app.yukine.ui.SettingsAction> actions,
                            app.yukine.ui.SettingsListScrollState scrollState
                    ) {
                        navSettingsActions = new ArrayList<>(actions);
                        navSettingsScrollState = scrollState;
                    }
                }
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
        networkDialogController = new NetworkDialogController(this, new NetworkDialogController.LanguageProvider() {
            @Override
            public String languageMode() {
                return settingsStore.languageMode();
            }
        }, networkDialogEventController);
        playlistDialogController = new PlaylistDialogController(this, new PlaylistDialogController.LanguageProvider() {
            @Override
            public String languageMode() {
                return settingsStore.languageMode();
            }
        }, new PlaylistDialogController.Listener() {
            @Override
            public void createPlaylist(String name) {
                MainActivity.this.createPlaylist(name);
            }

            @Override
            public void renamePlaylist(long playlistId, String name) {
                MainActivity.this.renamePlaylist(playlistId, name);
            }

            @Override
            public void deletePlaylist(long playlistId, String name) {
                MainActivity.this.deletePlaylist(playlistId, name);
            }

            @Override
            public void addTrackToPlaylist(long playlistId, long trackId) {
                MainActivity.this.addTrackToPlaylist(playlistId, trackId);
            }
        });
        confirmationDialogController = new ConfirmationDialogController(this, new ConfirmationDialogController.LanguageProvider() {
            @Override
            public String languageMode() {
                return settingsStore.languageMode();
            }
        }, new ConfirmationDialogController.Listener() {
            @Override
            public void clearPlayHistory() {
                MainActivity.this.clearPlayHistory();
            }

            @Override
            public void clearQueue() {
                MainActivity.this.clearQueue();
            }

            @Override
            public void deleteAllStreams() {
                MainActivity.this.deleteAllStreams();
            }

            @Override
            public void deleteTrack(long trackId, String status) {
                MainActivity.this.deleteTrack(trackId, status);
            }

            @Override
            public void deleteTracks(List<Long> trackIds, String status) {
                MainActivity.this.deleteTracks(trackIds, status);
            }

            @Override
            public void deleteRemoteSource(long sourceId, String name) {
                MainActivity.this.deleteRemoteSource(sourceId, name);
            }
        });
        NetworkMenuEventController networkMenuEventController = new NetworkMenuEventController(
                page -> MainActivity.this.navigateNetworkPage(page),
                new NetworkMenuEventController.Dialogs() {
                    @Override
                    public void showAddStream() {
                        networkDialogController.showAddStream();
                    }

                    @Override
                    public void showImportM3u() {
                        networkDialogController.showImportM3u();
                    }

                    @Override
                    public void showAddWebDav() {
                        networkDialogController.showAddWebDav();
                    }
                },
                () -> documentPickerController.openM3uFilePicker(),
                new NetworkMenuEventController.LibrarySource() {
                    @Override
                    public ArrayList<Track> streamTracks() {
                        return libraryStore.streamTracks();
                    }

                    @Override
                    public int streamTrackCount() {
                        return libraryStore.streamTrackCount();
                    }

                    @Override
                    public ArrayList<Track> webDavTracks() {
                        return libraryStore.webDavTracks();
                    }

                    @Override
                    public List<RemoteSource> remoteSources() {
                        return libraryStore.remoteSources();
                    }
                },
                sourceIds -> networkRequestController.syncAllWebDavSources(sourceIds),
                () -> confirmationDialogController.confirmDeleteAllStreams(),
                new NetworkMenuEventController.Player() {
                    @Override
                    public void playTrackList(List<Track> tracks, int index) {
                        MainActivity.this.playTrackList(tracks, index);
                    }
                },
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> MainActivity.this.setStatus(status),
                new NetworkMenuEventController.ContentSink() {
                    @Override
                    public void publishNetworkMenu(
                            String title,
                            List<app.yukine.ui.SettingsMetric> metrics,
                            List<app.yukine.ui.SettingsAction> actions
                    ) {
                        navNetworkMenuTitle = title;
                        navNetworkMenuMetrics = new ArrayList<>(metrics);
                        navNetworkMenuActions = new ArrayList<>(actions);
                    }

                }
        );
        networkMenuRenderController = new NetworkMenuRenderController(networkMenuEventController);
        NetworkSourcesEventController networkSourcesEventController = new NetworkSourcesEventController(
                routeController,
                networkRequestController,
                new NetworkSourcesEventController.LibrarySource() {
                    @Override
                    public String remoteSourceName(long sourceId) {
                        return libraryStore.remoteSourceName(sourceId);
                    }

                    @Override
                    public ArrayList<Track> webDavTracksForSource(long sourceId) {
                        return libraryStore.webDavTracksForSource(sourceId);
                    }
                },
                source -> networkDialogController.showEditWebDav(source),
                source -> confirmationDialogController.confirmDeleteRemoteSource(source),
                new NetworkSourcesEventController.Player() {
                    @Override
                    public void playTrackList(List<Track> tracks, int index) {
                        MainActivity.this.playTrackList(tracks, index);
                    }
                },
                key -> AppLanguage.text(settingsStore.languageMode(), key),
                status -> MainActivity.this.setStatus(status),
                statePublisher,
                () -> MainActivity.this.renderAndPersistSelectedTab()
        );
        networkSourcesRenderController = new NetworkSourcesRenderController(
                networkSourcesViewModel,
                new NetworkSourcesRenderController.Listener() {
                    @Override
                    public void backToNetwork() {
                        networkSourcesEventController.backToNetwork();
                    }

                    @Override
                    public void testRemoteSource(long sourceId) {
                        networkSourcesEventController.testRemoteSource(sourceId);
                    }

                    @Override
                    public void syncRemoteSource(long sourceId) {
                        networkSourcesEventController.syncRemoteSource(sourceId);
                    }

                    @Override
                    public void playRemoteSourceTracks(RemoteSource source) {
                        networkSourcesEventController.playRemoteSourceTracks(source);
                    }

                    @Override
                    public void openRemoteSourceTracks(long sourceId) {
                        networkSourcesEventController.openRemoteSourceTracks(sourceId);
                    }

                    @Override
                    public void showEditWebDav(RemoteSource source) {
                        networkSourcesEventController.showEditWebDav(source);
                    }

                    @Override
                    public void confirmDeleteRemoteSource(RemoteSource source) {
                        networkSourcesEventController.confirmDeleteRemoteSource(source);
                    }

                    @Override
                    public void publishNetworkSources(String title, ArrayList<app.yukine.ui.NetworkSourceUiState> rows) {
                        networkSourcesEventController.publishNetworkSources(title, rows);
                    }

                    @Override
                    public void publishNetworkSourcesChrome(
                            List<app.yukine.ui.NetworkSourceActions> actions,
                            List<TrackListHeaderAction> headerActions,
                            String emptyText,
                            app.yukine.ui.NetworkSourceLabels labels
                    ) {
                        navNetworkSourceActions = new ArrayList<>(actions);
                        navNetworkSourceHeaderActions = new ArrayList<>(headerActions);
                        navNetworkSourceEmptyText = emptyText;
                        navNetworkSourceLabels = labels;
                    }

                }
        );
        networkRenderCoordinator = new NetworkRenderCoordinator(
                libraryStore,
                networkMenuRenderController,
                networkTrackListRenderController,
                networkSourcesRenderController,
                streamingSearchRenderController
        );
        uiShellController.applyThemeSurface();
        mountNavHostShell();
        installBackNavigation();
        playbackServiceConnectionController.bind();
        permissionController.requestNeededPermissions();
        loadLibraryOnStartup();
        loadCollections();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Manual sync only; no automatic periodic sync.
    }

    @Override
    protected void onPause() {
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
        if (statePublisher == null) {
            return;
        }
        playbackStore.publish(playbackService);
    }

    private void publishTrackListUiState(String title, ArrayList<TrackRowUiState> rows) {
        if (statePublisher == null) {
            return;
        }
        statePublisher.publishTrackList(title, rows);
    }

    private void publishLibraryGroupsUiState(String title, ArrayList<LibraryGroupUiState> rows) {
        if (statePublisher == null) {
            return;
        }
        statePublisher.publishLibraryGroups(title, rows);
    }

    private void publishQueueUiState(ArrayList<QueueTrackUiState> rows) {
        if (statePublisher == null) {
            return;
        }
        statePublisher.publishQueue(rows);
    }

    private void mountNavHostShell() {
        if (queueViewModel == null) {
            return;
        }
        bindQueueViewModelInputs();
        queueViewModel.bindIntentListener(intent -> handleQueueIntent(intent));
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
                    nowPlayingViewModel,
                    libraryViewModel,
                    collectionsViewModel,
                    settingsViewModel,
                    networkSourcesViewModel,
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
                    selectedTab()
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

    private void handleQueueIntent(app.yukine.queue.QueueIntent intent) {
        if (intent instanceof app.yukine.queue.QueueIntent.PlayAt) {
            app.yukine.queue.QueueIntent.PlayAt playAt = (app.yukine.queue.QueueIntent.PlayAt) intent;
            playTrackList(playAt.getTracks(), playAt.getIndex());
        } else if (intent instanceof app.yukine.queue.QueueIntent.ToggleFavorite) {
            handleLibraryEvent(new LibraryEvent.ToggleFavorite(
                    ((app.yukine.queue.QueueIntent.ToggleFavorite) intent).getTrack()));
        } else if (intent instanceof app.yukine.queue.QueueIntent.AddToPlaylist) {
            showAddToPlaylistDialog(((app.yukine.queue.QueueIntent.AddToPlaylist) intent).getTrack());
        } else if (intent instanceof app.yukine.queue.QueueIntent.Remove) {
            removeQueueTrack(((app.yukine.queue.QueueIntent.Remove) intent).getTrack());
        } else if (intent instanceof app.yukine.queue.QueueIntent.Move) {
            app.yukine.queue.QueueIntent.Move move = (app.yukine.queue.QueueIntent.Move) intent;
            moveQueueTrack(move.getFromIndex(), move.getToIndex());
        } else if (intent instanceof app.yukine.queue.QueueIntent.ClearQueue) {
            confirmClearQueue();
        } else if (intent instanceof app.yukine.queue.QueueIntent.Back) {
            handleAppBack();
        }
    }

    private final class ActivityNavHostMount implements EchoNavHostMount {
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
        public void onTabChanged(app.yukine.navigation.TabRoute tab) {
            navigateToTab(tab.getRoute(), true, true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionController.handlePermissionsResult(requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        documentPickerController.handleActivityResult(requestCode, resultCode, data);
    }

    private void installBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (handleAppBack()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
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
                result -> replaceLibrary(result.getTracks(), result.getFavorites(), result.getStatus()),
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
        return viewModel.streamingProviderTrackIdFor(
                track,
                app.yukine.streaming.StreamingProviderName.NETEASE
        );
    }

    private void onLyricsStateChanged() {
        renderNowBar();
        if (TAB_NOW.equals(selectedTab())) {
            if (!updateNowPlayingContent()) {
                renderSelectedTab();
            }
        }
    }

    private void applySearch() {
        libraryStore.applySearch(searchQuery());
        renderAndPersistSelectedTab();
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
        routeController.clearLibraryGroup();
        if (!LIBRARY_PLAYLISTS.equals(mode)) {
            routeController.setSelectedPlaylistId(-1L);
        }
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
        homeDashboardRenderController.render(
                settingsStore.languageMode(),
                libraryStore.allTracks(),
                libraryStore.allTracks(),
                libraryStore.recentRecords(),
                playbackStore.snapshot()
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
        String languageMode = settingsStore.languageMode();
        ArrayList<Playlist> playlists = libraryStore.playlists();
        if (selectedPlaylistId() >= 0L && selectedLibraryGroupKey().startsWith("playlist:")) {
            renderLibraryPlaylistTracks();
            return;
        }
        ArrayList<LibraryGroupUiState> rows = new ArrayList<>();
        ArrayList<LibraryGroupActions> actions = new ArrayList<>();
        for (final Playlist playlist : playlists) {
            rows.add(new LibraryGroupUiState(
                    "playlist:" + playlist.id,
                    playlist.name,
                    CollectionRowStateFactory.trackCountLabel(playlist.trackCount, languageMode)
            ));
            actions.add(new LibraryGroupActions(
                    new Runnable() {
                        @Override
                        public void run() {
                            handleLibraryEvent(new LibraryEvent.OpenPlaylist(playlist.id, playlist.name));
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            handleLibraryEvent(new LibraryEvent.PlayPlaylist(playlist.id));
                        }
                    },
                    playlist.trackCount > 0,
                    new Runnable() {
                        @Override
                        public void run() {
                            confirmDeletePlaylist(playlist);
                        }
                    }
            ));
        }
        String title = AppLanguage.text(languageMode, "playlists");
        String emptyText = AppLanguage.text(languageMode, "no.playlists");
        ArrayList<TrackListModeAction> modeActions = libraryModeActions();
        publishLibraryGroupsUiState(title, rows);
        libraryViewModel.clearTrackList();
        libraryViewModel.updateLibraryGroups(title, rows);
        navLibraryGroupActions = new ArrayList<>(actions);
        navLibraryGroupEmptyText = emptyText;
        navLibraryGroupModeActions = new ArrayList<>(modeActions);
        navTrackListActions = Collections.emptyList();
        navTrackListHeaderMetrics = Collections.emptyList();
        navTrackListHeaderActions = Collections.emptyList();
        navTrackListEmptyText = "";
    }

    private void renderLibraryPlaylistTracks() {
        String languageMode = settingsStore.languageMode();
        ArrayList<Track> tracks = libraryStore.selectedPlaylistTracks();
        ArrayList<TrackListHeaderMetric> headerMetrics = new ArrayList<>();
        headerMetrics.add(new TrackListHeaderMetric(AppLanguage.text(languageMode, "songs"), String.valueOf(tracks.size())));
        ArrayList<TrackListHeaderAction> headerActions = new ArrayList<>();
        headerActions.add(new TrackListHeaderAction(AppLanguage.text(languageMode, "back.to.playlists"), new Runnable() {
            @Override
            public void run() {
                handleLibraryEvent(LibraryEvent.BackFromGroup.INSTANCE);
            }
        }));
        if (!tracks.isEmpty()) {
            headerActions.add(new TrackListHeaderAction(AppLanguage.text(languageMode, "play.playlist"), new Runnable() {
                @Override
                public void run() {
                    handleLibraryEvent(new LibraryEvent.PlayTrackList(tracks, 0));
                }
            }));
        }
        renderComposeTrackList(
                selectedPlaylistName(),
                tracks,
                true,
                new ArrayList<String>(),
                false,
                headerMetrics,
                headerActions,
                AppLanguage.text(languageMode, "no.tracks.in.playlist"),
                libraryModeActions()
        );
    }

    private void renderLibraryGroups() {
        libraryGroupsRenderController.render(
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
                libraryStore.favoriteIds()
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
                setStatus(libraryViewModel.defaultPlaylistAddPresentation(
                        added,
                        settingsStore.languageMode()
                ).getStatus());
                routeController.setSelectedPlaylistId(playlistId);
                loadCollections();
            });
            return;
        }

        playlistDialogController.showAddToPlaylist(track, libraryStore.playlists());
    }

    private void createPlaylist(final String name) {
        libraryViewModel.createPlaylistJava(name, playlistId -> {
            if (playlistId >= 0L) {
                routeController.setSelectedPlaylistId(playlistId);
            }
            setStatus(libraryViewModel.playlistCreatedPresentation(
                    settingsStore.languageMode()
            ).getStatus());
            loadCollections();
        });
    }

    private void renamePlaylist(final long playlistId, final String name) {
        libraryViewModel.renamePlaylistJava(playlistId, name, (renamedPlaylistId, renamed) -> {
            if (renamed) {
                routeController.setSelectedPlaylistId(renamedPlaylistId);
            }
            setStatus(libraryViewModel.playlistRenamedPresentation(
                    renamed,
                    settingsStore.languageMode()
            ).getStatus());
            loadCollections();
        });
    }

    private void deletePlaylist(final long playlistId, final String name) {
        libraryViewModel.deletePlaylistJava(playlistId, name, (deletedPlaylistId, deletedName, deleted) -> {
            if (deleted && selectedPlaylistId() == deletedPlaylistId) {
                routeController.setSelectedPlaylistId(-1L);
            }
            setStatus(libraryViewModel.playlistDeletedPresentation(
                    deletedName,
                    deleted,
                    settingsStore.languageMode()
            ).getStatus());
            loadCollections();
        });
    }

    private void removeSelectedPlaylistTrack(final long playlistId, final Track track) {
        libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track, (removedPlaylistId, removedTrack) -> {
            routeController.setSelectedPlaylistId(removedPlaylistId);
            setStatus(libraryViewModel.selectedPlaylistTrackRemovedPresentation(
                    removedTrack,
                    settingsStore.languageMode()
            ).getStatus());
            loadCollections();
        });
    }

    private void moveSelectedPlaylistTrack(final long playlistId, final Track track, final int trackIndex, final int direction) {
        libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction, (movedPlaylistId, movedTrack, movedDirection, moved) -> {
            routeController.setSelectedPlaylistId(movedPlaylistId);
            setStatus(libraryViewModel.selectedPlaylistTrackMovedPresentation(
                    movedTrack,
                    movedDirection,
                    moved,
                    settingsStore.languageMode()
            ).getStatus());
            loadCollections();
        });
    }

    private void addTrackToPlaylist(final long playlistId, final long trackId) {
        libraryViewModel.addTrackToPlaylistJava(playlistId, trackId, (addedPlaylistId, added) -> {
            routeController.setSelectedPlaylistId(addedPlaylistId);
            setStatus(libraryViewModel.trackAddedToPlaylistPresentation(
                    added,
                    settingsStore.languageMode()
            ).getStatus());
            loadCollections();
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
        applyPlaybackActionResult(nowPlayingViewModel.removeQueueTrack(track));
    }

    private void confirmClearQueue() {
        if (!nowPlayingViewModel.hasQueue()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "queue.empty"));
            return;
        }
        confirmationDialogController.confirmClearQueue();
    }

    private void clearQueue() {
        applyPlaybackActionResult(nowPlayingViewModel.clearQueue());
    }

    private void moveQueueTrack(int fromIndex, int toIndex) {
        if (playbackService == null) {
            return;
        }
        playbackService.moveQueueTrack(fromIndex, toIndex);
        renderNowBar();
        renderSelectedTab();
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
        playTrackList(tracks, 0);
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

    private void applyThemeMode(String nextMode) {
        settingsViewModel.applyThemeMode(nextMode);
    }

    private void applyAccentMode(String nextAccent) {
        settingsViewModel.applyAccentMode(nextAccent);
    }

    private void applyLanguageMode(String nextLanguageMode) {
        settingsViewModel.applyLanguageMode(nextLanguageMode);
    }

    private void applyStreamingGatewayEndpoint(String endpoint) {
        streamingGatewayController.applyEndpoint(endpoint);
    }

    private void importSelectedPlaylistToStreaming() {
        StreamingPlaylistExportRequest request = viewModel.prepareStreamingPlaylistExportRequest(
                selectedPlaylistName(),
                libraryStore.selectedPlaylistTracks()
        );
        setStatus(request.getStatus());
        if (!request.getValid()) {
            return;
        }
        showStreamingProviderPicker(request.getPlaylistName(), request.getTracks());
    }

    private void importFavoritesToStreaming() {
        StreamingPlaylistExportRequest request = viewModel.prepareStreamingFavoritesExportRequest(
                libraryStore.favoriteTracks()
        );
        setStatus(request.getStatus());
        if (!request.getValid()) {
            return;
        }
        showStreamingProviderPicker(request.getPlaylistName(), request.getTracks());
    }

    private void syncSelectedPlaylistFromStreaming() {
        StreamingPlaylistSyncStartRequest request = viewModel.prepareStreamingPlaylistSyncStartRequest(
                routeController.selectedPlaylistId()
        );
        if (request == null) {
            return;
        }
        setStatus(request.getStatus());
        if (!request.getValid() || request.getLink() == null) {
            return;
        }
        syncOneStreamingPlaylist(request.getLink());
    }

    private void showStreamingProviderPicker(String playlistName, java.util.List<Track> tracks) {
        StreamingProviderPickerRequest request = viewModel.prepareStreamingImportProviderPickerRequest(
                viewModel.getStreaming().getValue().getProviders(),
                true
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
                    runStreamingPlaylistImport(descriptor.getName(), playlistName, tracks);
                })
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .show();
    }

    private void runStreamingPlaylistImport(
            app.yukine.streaming.StreamingProviderName provider,
            String playlistName,
            java.util.List<Track> tracks
    ) {
        setStatus(viewModel.prepareStreamingPlaylistExportRequest(playlistName, tracks).getStatus());
        navigateToNetworkTabPage(NETWORK_STREAMING);
        viewModel.importPlaylistToStreamingJava(provider, playlistName, tracks, importStatus -> {
            setStatus(viewModel.prepareStreamingPlaylistExportPresentation(importStatus).getStatus());
        });
    }

    // ---- Import a streaming playlist INTO the local library ----

    private void showImportStreamingPlaylistDialog() {
        StreamingPlaylistImportDialogState dialogState = viewModel.prepareStreamingPlaylistImportDialogState();
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
                        importStreamingPlaylistFromLink(input.getText().toString()))
                .show();
    }

    private void showStreamingCookieDialog() {
        final StreamingManualCookieDialogState dialogState = viewModel.prepareManualCookieDialogState(
                viewModel.getStreaming().getValue().getSelectedProvider()
        );
        if (dialogState.getUnavailable() || dialogState.getProvider() == null) {
            setStatus(dialogState.getUnavailableStatus());
            return;
        }
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
                        saveStreamingCookie(dialogState.getProvider(), input.getText().toString()))
                .show();
    }

    private void saveStreamingCookie(
            app.yukine.streaming.StreamingProviderName provider,
            String cookieHeader
    ) {
        StreamingManualCookieAuthRequest request = viewModel.prepareManualCookieAuthRequest(provider, cookieHeader);
        if (request == null) {
            setStatus(viewModel.manualCookieEmptyStatus());
            return;
        }
        viewModel.completeStreamingAuth(request.getProvider(), request.getCallbackUri(), request.getCookieHeader(), loggedInProvider -> {
            setStatus(request.getSavedStatus());
            onStreamingLoginSuccess(loggedInProvider);
        });
    }

    private void importStreamingPlaylistFromLink(String linkOrId) {
        StreamingPlaylistImportStartRequest request = viewModel.prepareStreamingPlaylistImportStartRequest(
                linkOrId,
                viewModel.getStreaming().getValue().getSelectedProvider()
        );
        if (!request.getValid() || request.getProvider() == null) {
            setStatus(request.getInvalidStatus());
            return;
        }
        importStreamingPlaylist(request.getProvider(), request.getProviderPlaylistId(), request.getResolvingStatus());
    }

    private void importStreamingPlaylistFromProviderRef(
            app.yukine.streaming.StreamingProviderName provider,
            String providerPlaylistId
    ) {
        StreamingPlaylistImportStartRequest request = viewModel.prepareStreamingPlaylistImportStartRequest(
                providerPlaylistId,
                provider
        );
        if (!request.getValid() || request.getProvider() == null) {
            setStatus(request.getInvalidStatus());
            return;
        }
        importStreamingPlaylist(request.getProvider(), request.getProviderPlaylistId(), request.getResolvingStatus());
    }

    private void importStreamingPlaylist(
        app.yukine.streaming.StreamingProviderName provider,
            String providerPlaylistId,
            String resolvingStatus
    ) {
        setStatus(resolvingStatus);
        viewModel.importStreamingPlaylistToLocal(provider, providerPlaylistId, result -> {
            StreamingLocalPlaylistImportPresentation presentation =
                    viewModel.prepareStreamingPlaylistImportPresentation(result);
            setStatus(presentation.getStatus());
            if (presentation.getEmpty()) {
                return;
            }
            if (presentation.getShowLoadedDialog()) {
                showStreamingPlaylistLoadedDialog(presentation.getStatus());
            }
            loadCollections();
        });
    }

    private void showStreamingPlaylistLoadedDialog(String message) {
        EchoDialog.builder(this)
                .setTitle(viewModel.streamingPlaylistLoadedDialogTitle())
                .setMessage(message)
                .setPositiveButton(AppLanguage.text(settingsStore.languageMode(), "ok"), null)
                .show();
    }

    // ---- Pull the user's liked/favorite tracks FROM streaming INTO a local playlist ----

    private void showImportStreamingFavoritesProviderPicker() {
        StreamingProviderPickerRequest request = viewModel.prepareStreamingImportProviderPickerRequest(
                viewModel.getStreaming().getValue().getProviders(),
                false
        );
        if (!request.getValid()) {
            setStatus(request.getEmptyStatus());
            return;
        }
        EchoDialog.builder(this)
                .setTitle(request.getTitle())
                .setItems(request.getPickerState().getLabels(), (dialog, which) ->
                        importStreamingLikedTracks(request.getPickerState().getProviders().get(which).getName()))
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .show();
    }

    private void importStreamingLikedTracks(app.yukine.streaming.StreamingProviderName provider) {
        if (provider == null) {
            return;
        }
        final String playlistName = viewModel.prepareStreamingLikedPlaylistName(provider);

        setStatus(viewModel.prepareStreamingPlaybackStatusText(null).getResolving());
        navigateToNetworkTabPage(NETWORK_STREAMING);
        viewModel.importStreamingLikedTracksToLocal(provider, playlistName, result -> {
            StreamingLocalPlaylistImportPresentation presentation =
                    viewModel.prepareStreamingLikedImportPresentation(result);
            setStatus(presentation.getStatus());
            if (presentation.getEmpty()) {
                return;
            }
            if (presentation.getShowLoadedDialog()) {
                showStreamingPlaylistLoadedDialog(presentation.getStatus());
            }
            loadCollections();
        });
    }

    /**
     * Fetches NetEase 每日推荐 (daily recommendations) and plays the returned list immediately. The
     * tracks are streaming placeholders, so {@link #playTrackList} resolves each playback URL lazily
     * and the next track is pre-resolved by the existing prefetch path.
     */
    private void playStreamingDailyRecommendations(app.yukine.streaming.StreamingProviderName provider) {
        StreamingDailyRecommendationRequest request =
                viewModel.prepareStreamingDailyRecommendationRequest(provider);
        if (request == null) {
            setStatus(viewModel.streamingDailyRecommendationEmptyStatus());
            return;
        }
        setStatus(request.getLoadingStatus());
        viewModel.fetchDailyRecommendations(request.getProvider(), streamingTracks ->
                openRecommendationPlaylist(
                        streamingTracks,
                        request.getEmptyStatus(),
                        request.getTitle()
                ));
    }

    /**
     * Fetches NetEase 心动推荐 (heartbeat / intelligence list, seeded from liked songs) and plays the
     * returned list immediately.
     */
    private void playStreamingHeartbeatRecommendations(app.yukine.streaming.StreamingProviderName provider) {
        StreamingHeartbeatRecommendationRequest request =
                viewModel.prepareStreamingHeartbeatRecommendationRequest(provider);
        if (request == null) {
            setStatus(viewModel.streamingHeartbeatRecommendationEmptyStatus());
            return;
        }
        setStatus(request.getLoadingStatus());
        HeartbeatRecommendationSeedRequest seedRequest = heartbeatRecommendationSeedRequest(request.getProvider());
        if (seedRequest.getHasSeed()) {
            fetchHeartbeatRecommendations(
                    request,
                    seedRequest.getSeedTrackId(),
                    seedRequest.getPlaylistId()
            );
            return;
        }
        resolveHeartbeatSeedFromQueue(request, seedRequest);
    }

    private void fetchHeartbeatRecommendations(
            StreamingHeartbeatRecommendationRequest request,
            String seedTrackId,
            String playlistId
    ) {
        viewModel.fetchHeartbeatRecommendations(request.getProvider(), seedTrackId, playlistId, streamingTracks ->
                playHeartbeatRecommendationTracks(
                        streamingTracks,
                        request.getEmptyStatus(),
                        request.getPlayingStatus()
                ));
    }

    private HeartbeatRecommendationSeedRequest heartbeatRecommendationSeedRequest(
            app.yukine.streaming.StreamingProviderName provider
    ) {
        PlaybackStateSnapshot serviceSnapshot = playbackService == null ? null : playbackService.snapshot();
        List<Track> serviceQueue = playbackService == null ? null : playbackService.queueSnapshot();
        PlaybackStateSnapshot storeSnapshot = playbackStore == null ? null : playbackStore.snapshot();
        List<Track> viewModelQueue = viewModel == null || viewModel.getPlayback().getValue() == null
                ? null
                : viewModel.getPlayback().getValue().getQueue();
        return viewModel.prepareHeartbeatRecommendationSeedRequest(
                provider,
                serviceSnapshot,
                serviceQueue,
                storeSnapshot,
                viewModelQueue,
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

    private void resolveHeartbeatSeedFromQueue(
            StreamingHeartbeatRecommendationRequest recommendationRequest,
            HeartbeatRecommendationSeedRequest seedRequest
    ) {
        if (!seedRequest.getHasCandidates()) {
            logHeartbeatSeedMiss(seedRequest);
            viewModel.markHeartbeatRecommendationLoadingFinished();
            setStatus(recommendationRequest.getEmptyStatus());
            return;
        }
        viewModel.resolveHeartbeatRecommendationSeed(
                recommendationRequest.getProvider(),
                seedRequest.getCandidates(),
                resolvedTrackId -> {
            if (!viewModel.canContinueHeartbeatRecommendationLoading(recommendationRequest.getProvider())) {
                return;
            }
            if (resolvedTrackId != null && !resolvedTrackId.isEmpty()) {
                fetchHeartbeatRecommendations(recommendationRequest, resolvedTrackId, resolvedTrackId);
                return;
            }
            logHeartbeatSeedMiss(seedRequest);
            viewModel.markHeartbeatRecommendationLoadingFinished();
            setStatus(recommendationRequest.getEmptyStatus());
        });
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
    private void openRecommendationPlaylist(
            java.util.List<app.yukine.streaming.StreamingTrack> streamingTracks,
            String emptyStatus,
            String title
    ) {
        StreamingRecommendationPresentation presentation =
                viewModel.prepareStreamingRecommendationPresentation(streamingTracks, emptyStatus, title);
        if (presentation.getEmpty()) {
            setStatus(presentation.getEmptyStatus());
            return;
        }
        setStatus(presentation.getReadyStatus());
        ensurePlaybackServiceStarted();
        playTrackList(presentation.getTracks(), 0);
        navigateToTab(TAB_QUEUE, true, true);
    }

    private void playHeartbeatRecommendationTracks(
            java.util.List<app.yukine.streaming.StreamingTrack> streamingTracks,
            String emptyStatus,
            String playingStatus
    ) {
        StreamingRecommendationPresentation presentation =
                viewModel.prepareHeartbeatRecommendationPresentation(streamingTracks, emptyStatus, playingStatus);
        if (presentation.getEmpty()) {
            setStatus(presentation.getEmptyStatus());
            return;
        }
        setStatus(presentation.getReadyStatus());
        ensurePlaybackServiceStarted();
        playHeartbeatRecommendationTrackList(presentation.getTracks(), 0);
    }

    private void onStreamingLoginSuccess(app.yukine.streaming.StreamingProviderName provider) {
        final StreamingLoginPlaylistRequest request = viewModel.prepareStreamingLoginPlaylistRequest(provider);

        viewModel.ensureStreamingLoginPlaylist(request.getPlaylistName(), request.getProvider(), result -> {
            StreamingLoginPlaylistPresentation presentation =
                    viewModel.prepareStreamingLoginPlaylistPresentation(request, result);
            setStatus(presentation.getStatus());
            if (presentation.getPlaylistId() >= 0L) {
                routeController.setSelectedPlaylistId(presentation.getPlaylistId());
            }
            loadCollections();
        });
    }

    private void syncStreamingPlaylists(java.util.List<app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist> links) {
        for (app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist link : links) {
            syncOneStreamingPlaylist(link);
        }
    }

    private void syncOneStreamingPlaylist(app.yukine.streaming.StreamingPlaylistSyncStore.LinkedPlaylist link) {
        viewModel.syncStreamingPlaylistToLocal(link, result -> {
            StreamingLocalPlaylistSyncPresentation presentation =
                    viewModel.prepareStreamingPlaylistSyncPresentation(result);
            setStatus(presentation.getStatus());
            if (presentation.getEmpty()) {
                return;
            }
            loadCollections();
        });
    }
    private void applyPlaybackSpeed(float speed) {
        settingsViewModel.applyPlaybackSpeed(speed);
    }

    private void applyAppVolume(float volume) {
        settingsViewModel.applyAppVolume(volume);
    }

    private void applyStreamingAudioQuality(String quality) {
        settingsViewModel.applyStreamingAudioQuality(quality);
    }

    private void setConcurrentPlaybackEnabled(boolean enabled) {
        settingsViewModel.setConcurrentPlaybackEnabled(enabled);
    }

    private void startSleepTimer(int minutes) {
        applyPlaybackActionResult(nowPlayingViewModel.startSleepTimer(minutes));
    }

    private void cancelSleepTimer() {
        applyPlaybackActionResult(nowPlayingViewModel.cancelSleepTimer());
    }

    private void setOnlineLyricsEnabled(boolean enabled) {
        settingsViewModel.setOnlineLyricsEnabled(enabled);
    }

    private void applyLyricsOffset(long offsetMs) {
        settingsViewModel.applyLyricsOffset(offsetMs);
    }

    private void reloadCurrentLyrics() {
        if (lyricsViewModel != null) {
            Track track = playbackStore.snapshot().currentTrack;
            lyricsViewModel.load(track, neteaseProviderTrackIdForLyrics(track));
        }
        setStatus(playbackStore.snapshot().currentTrack == null
                ? AppLanguage.text(settingsStore.languageMode(), "no.track.selected")
                : AppLanguage.text(settingsStore.languageMode(), "reloading.lyrics"));
    }

    private String remoteSourceName(long sourceId) {
        return libraryStore.remoteSourceName(sourceId);
    }

    private void renderNowBar() {
        if (playbackStore == null || libraryStore == null) {
            return;
        }
        publishNowPlayingState(playbackStore.snapshot());
        // Keep the queue view model in sync with the live playback queue. Without this the
        // queue is only bound once at startup (with an empty snapshot), so it always shows
        // no tracks even after playback starts.
        bindQueueViewModelInputs();
    }

    private NowPlayingUiState publishNowPlayingState(PlaybackStateSnapshot snapshot) {
        if (nowPlayingViewModel == null) {
            return new NowPlayingUiState();
        }
        nowPlayingViewModel.updateState(
                snapshot,
                libraryStore == null ? Collections.<Long>emptySet() : libraryStore.favoriteIds(),
                lyricsState(),
                settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()
        );
        NowPlayingUiState state = nowPlayingViewModel.getUiState().getValue();
        FloatingLyricsPublisher.update(
                state.getTrackTitle(),
                state.getLyrics().getLines()
        );
        return state;
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
        if (nowPlayingViewModel == null) {
            return;
        }
        for (NowPlayingEffect effect : nowPlayingViewModel.drainEffects()) {
            if (effect instanceof NowPlayingEffect.OpenQueue) {
                navigateToTab(TAB_QUEUE, true, true);
            } else if (effect instanceof NowPlayingEffect.OpenAddToPlaylist) {
                showAddToPlaylistDialog(((NowPlayingEffect.OpenAddToPlaylist) effect).getTrack());
            } else if (effect instanceof NowPlayingEffect.ShowMessage) {
                setStatus(((NowPlayingEffect.ShowMessage) effect).getMessage());
            }
        }
    }

    private void playTrackList(List<Track> tracks, int index) {
        stopHeartbeatRecommendationMode();
        playTrackListInternal(tracks, index);
    }

    private void playHeartbeatRecommendationTrackList(List<Track> tracks, int index) {
        playTrackListInternal(tracks, index);
    }

    private void playTrackListInternal(List<Track> tracks, int index) {
        ensurePlaybackServiceStarted();
        if (playbackService == null) {
            pendingPlaybackTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);
            pendingPlaybackIndex = index;
            setStatus(viewModel.prepareStreamingPlaybackStatusText(null).getResolving());
            return;
        }
        if (resolveAndPlayStreamingTrack(tracks, index)) {
            return;
        }
        applyPlaybackActionResult(nowPlayingViewModel.playTrackList(tracks, index));
    }

    private void playPendingTracksIfNeeded() {
        if (playbackService == null || pendingPlaybackTracks == null || pendingPlaybackTracks.isEmpty()) {
            return;
        }
        List<Track> tracks = new ArrayList<>(pendingPlaybackTracks);
        int index = pendingPlaybackIndex;
        pendingPlaybackTracks = Collections.emptyList();
        pendingPlaybackIndex = -1;
        playTrackListInternal(tracks, index);
    }

    private void ensurePlaybackServiceStarted() {
        startService(new Intent(this, EchoPlaybackService.class));
    }

    private void stopHeartbeatRecommendationMode() {
        viewModel.stopHeartbeatRecommendationMode();
    }

    private void skipToPrevious() {
        nowPlayingViewModel.skipToPrevious();
        resolveCurrentStreamingQueueTrackIfNeeded();
    }

    private void skipToNext() {
        nowPlayingViewModel.skipToNext();
        resolveCurrentStreamingQueueTrackIfNeeded();
    }

    private void togglePlayback() {
        if (resolveCurrentStreamingQueueTrackIfNeeded()) {
            return;
        }
        PlaybackStateSnapshot snapshot = playbackService == null
                ? playbackStore.snapshot()
                : playbackService.snapshot();
        List<Track> fallbackTracks = libraryStore == null
                ? Collections.emptyList()
                : libraryStore.visibleTracks();
        applyPlaybackActionResult(
                nowPlayingViewModel.togglePlayback(snapshot, fallbackTracks)
        );
    }

    private boolean resolveCurrentStreamingQueueTrackIfNeeded() {
        if (playbackService == null) {
            return false;
        }
        StreamingQueueResolveTarget target = viewModel.prepareCurrentStreamingQueueResolveTarget(
                playbackService.snapshot(),
                playbackService.queueSnapshot()
        );
        return target != null && resolveAndPlayStreamingTrack(target.getTracks(), target.getIndex());
    }

    private void preResolveNextStreamingTrack(PlaybackStateSnapshot snapshot) {
        if (playbackService == null || snapshot == null || !snapshot.playing) {
            return;
        }
        maybeAppendHeartbeatRecommendations(snapshot);
        List<Track> queue = playbackService.queueSnapshot();
        viewModel.preResolveNextStreamingTrack(
                snapshot,
                queue,
                adaptiveStreamingQuality(),
                (oldTrackId, resolved) -> {
                    if (resolved == null) {
                        return;
                    }
                    nowPlayingViewModel.replaceQueuedTrack(oldTrackId, resolved);
                    nowPlayingViewModel.precacheTrack(resolved);
                });
    }

    private void maybeAppendHeartbeatRecommendations(PlaybackStateSnapshot snapshot) {
        if (playbackService == null) {
            return;
        }
        HeartbeatRefillRequest refill = viewModel.prepareHeartbeatRecommendationRefill(snapshot);
        if (refill == null) {
            return;
        }
        final app.yukine.streaming.StreamingProviderName provider = refill.getProvider();
        HeartbeatRecommendationSeedRequest request = heartbeatRecommendationSeedRequest(provider);
        if (!request.getHasSeed()) {
            stopHeartbeatRecommendationMode();
            return;
        }
        viewModel.fetchHeartbeatRecommendations(provider, request.getSeedTrackId(), request.getPlaylistId(), streamingTracks -> {
            if (!viewModel.acceptsHeartbeatRecommendationRefill(provider) || playbackService == null) {
                viewModel.markHeartbeatRecommendationRefillFinished(provider);
                return;
            }
            StreamingRecommendationPresentation presentation =
                    viewModel.prepareHeartbeatRecommendationAppendPresentation(streamingTracks);
            if (presentation.getEmpty()) {
                return;
            }
            nowPlayingViewModel.appendToQueue(presentation.getTracks());
            setStatus(presentation.getReadyStatus());
        });
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

    private void recoverStreamingBuffering(PlaybackStateSnapshot snapshot) {
        if (playbackService == null || snapshot == null) {
            return;
        }
        app.yukine.streaming.StreamingAudioQuality selectedQuality = selectedStreamingQuality();
        app.yukine.streaming.StreamingAudioQuality adaptiveQuality = adaptiveStreamingQuality();
        app.yukine.streaming.StreamingAudioQuality recoveryQuality = viewModel.recoverStreamingBuffering(
                snapshot,
                selectedQuality,
                adaptiveQuality,
                resolved -> {
                    if (resolved == null) {
                        return;
                    }
                    nowPlayingViewModel.replaceCurrentTrackAndResume(resolved.getTrack(), resolved.getPositionMs());
                    setStatus(viewModel.prepareStreamingPlaybackStatusText(resolved.getQuality()).getQualityDowngraded());
                }
        );
        if (recoveryQuality == null) {
            return;
        }
        setStatus(viewModel.prepareStreamingPlaybackStatusText(recoveryQuality).getQualityDowngrading());
    }

    private app.yukine.streaming.StreamingAudioQuality selectedStreamingQuality() {
        String selected = settingsStore == null
                ? StreamingQualityPreference.defaultValue()
                : settingsStore.streamingAudioQuality();
        return StreamingQualityPreference.AUTO.equals(
                StreamingQualityPreference.normalize(selected)
        ) ? adaptiveStreamingQuality() : StreamingQualityPreference.ceilingFor(selected);
    }

    private boolean resolveAndPlayStreamingTrack(List<Track> tracks, int index) {
        boolean scheduled = viewModel.resolveStreamingTrackListForPlayback(
                tracks,
                index,
                adaptiveStreamingQuality(),
                resolved -> {
                    if (resolved == null) {
                        setStatus(viewModel.prepareStreamingPlaybackStatusText(null).getResolveFailed());
                        return;
                    }
                    applyPlaybackActionResult(
                            nowPlayingViewModel.playTrackList(resolved.getTracks(), resolved.getIndex())
                    );
                }
        );
        if (scheduled) {
            setStatus(viewModel.prepareStreamingPlaybackStatusText(null).getResolving());
        }
        return scheduled;
    }

    private void toggleShuffle() {
        applyPlaybackActionResult(
                nowPlayingViewModel.toggleShuffle(playbackStore.snapshot())
        );
    }

    private void cycleBottomPlaybackMode() {
        applyPlaybackActionResult(
                nowPlayingViewModel.cycleBottomPlaybackMode(playbackStore.snapshot())
        );
    }

    private void cycleRepeat() {
        applyPlaybackActionResult(nowPlayingViewModel.cycleRepeat());
    }

    private void applyPlaybackActionResult(PlaybackActionResultUi result) {
        if (result == null) {
            return;
        }
        if (result.snapshot != null) {
            playbackStore.replaceSnapshot(result.snapshot);
        }
        if (result.status != null && !result.status.trim().isEmpty()) {
            setStatus(result.status);
        }
        if (result.publishPlaybackState) {
            publishPlaybackState();
        }
        if (result.renderNowBar) {
            renderNowBar();
        }
        if (result.renderSelectedTab) {
            renderSelectedTab();
        }
        if (result.navigateNow) {
            routeController.setSelectedTab(TAB_NOW);
            renderSelectedTab();
        }
    }

}
