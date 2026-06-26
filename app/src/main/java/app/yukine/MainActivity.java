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
import app.yukine.playback.AudioEffectSettings;
import app.yukine.playback.EchoPlaybackService;
import app.yukine.playback.PlaybackStateSnapshot;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.streaming.StreamingAudioQuality;
import app.yukine.streaming.StreamingPlaylist;
import app.yukine.streaming.StreamingPlaylistSyncStore;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.streaming.StreamingTrack;
import app.yukine.ui.HomeDashboardActions;
import app.yukine.ui.LibraryGroupActions;
import app.yukine.ui.LibraryGroupUiState;
import app.yukine.ui.LyricUiLine;
import app.yukine.ui.CollectionsActions;
import app.yukine.ui.QueueScreenLabels;
import app.yukine.ui.QueueTrackActions;
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
        MainActivityStreamingActionGateway streamingActionGateway = new MainActivityStreamingActionGateway() {
            @Override
            public StreamingAudioQuality streamingPlaybackQuality() {
                return adaptiveStreamingQuality();
            }

            @Override
            public String languageMode() {
                return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
            }

            @Override
            public boolean openAuthLaunch(MainActivityStreamingAuthLaunch launch) {
                return StreamingAuthLauncher.INSTANCE.launch(MainActivity.this, launch);
            }

            @Override
            public void playResolvedTrack(Track track) {
                playTrackListFromHost(java.util.Collections.singletonList(track), 0);
            }

            @Override
            public void onStreamingLoginSuccess(StreamingProviderName provider) {
                streamingPlaylistController.onStreamingLoginSuccess(provider);
            }

            @Override
            public void openManualCookieImport(StreamingProviderName provider) {
                openManualStreamingCookieImport(provider);
            }
        };
        streamingViewModel.bindStreamingPlaybackCoordinator(
                resolveStreamingPlaybackUseCase,
                new StreamingPlaybackTaskQueueAdapter(streamingPlaybackTaskScheduler)
        );
        homeDashboardViewModel = new ViewModelProvider(this).get(HomeDashboardViewModel.class);
        nowPlayingViewModel = new ViewModelProvider(this).get(NowPlayingViewModel.class);
        nowPlayingViewModel.bindGateway(new NowPlayingGateway() {
            @Override
            public void playPause() {
                playbackActionController.togglePlayback();
            }

            @Override
            public void next() {
                playbackActionController.skipToNext();
            }

            @Override
            public void previous() {
                playbackActionController.skipToPrevious();
            }

            @Override
            public void seekTo(long positionMs) {
                nowPlayingViewModel.seekTo(positionMs);
            }

            @Override
            public void toggleFavorite() {
                Track currentTrack = playbackStore == null ? null : playbackStore.snapshot().currentTrack;
                if (currentTrack != null) {
                    libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(currentTrack));
                }
            }

            @Override
            public void toggleShuffle() {
                playbackActionController.toggleShuffle();
            }

            @Override
            public void cycleRepeatMode() {
                playbackActionController.cycleRepeat();
            }

            @Override
            public String statusMessage(String key) {
                return AppLanguage.text(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        key
                );
            }
        });
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
        libraryViewModel.bindGateway(new LibraryGateway() {
            @Override
            public void playTrackList(List<Track> tracks, int index) {
                MainActivity.this.playTrackListFromHost(tracks, index);
            }

            @Override
            public void showStatusKey(String key) {
                statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), key));
            }

            @Override
            public void applyFavorite(long trackId, boolean favorite) {
                viewModel.setFavorite(trackId, favorite);
                nowPlayingStateController.renderNowBar();
                renderSelectedTab();
                loadCollections();
            }

            @Override
            public void addToPlaylist(Track track) {
                MainActivity.this.playlistDialogController.showAddToPlaylist(track);
            }

            @Override
            public void changeGroupMode(String mode) {
                routeController.setLibraryMode(mode);
                renderSelectedTab();
            }

            @Override
            public void openGroup(String key, String title) {
                routeController.selectLibraryGroup(key, title);
                renderSelectedTab();
            }

            @Override
            public void openPlaylist(long playlistId, String title) {
                routeController.selectLibraryGroup("playlist:" + playlistId, title);
                routeController.setSelectedPlaylistId(playlistId);
                loadCollections();
            }

            @Override
            public void backFromGroup() {
                routeController.clearLibraryGroup();
                routeController.setSelectedPlaylistId(-1L);
                renderSelectedTab();
            }

            @Override
            public void search(String query) {
                routeController.setSearchQuery(query);
                applySearch();
            }

            @Override
            public void importFiles() {
                documentPickerController.openAudioFilePicker();
            }

            @Override
            public void scanLibrary() {
                loadLibrary(false);
            }
        });
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
                streamingViewModel,
                streamingActionGateway
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
        documentPickerController = new DocumentPickerController(this, new DocumentPickerController.Listener() {
            @Override
            public void importAudioUris(ArrayList<Uri> uris) {
                importSelectedAudioUris(uris);
            }

            @Override
            public void importAudioFolder(Uri treeUri) {
                importSelectedAudioFolder(treeUri);
            }

            @Override
            public void chooseDownloadFolder(Uri treeUri) {
                setCustomDownloadFolder(treeUri);
            }

            @Override
            public void importStreamM3u(Uri playlistUri) {
                importSelectedM3uFile(playlistUri);
            }

            @Override
            public void exportPlaylist(Uri exportUri) {
                playlistExportController.exportSelectedPlaylistToUri(exportUri);
            }

            @Override
            public void importPlaylistM3u(Uri playlistUri) {
                importSelectedPlaylistM3uFile(playlistUri);
            }

            @Override
            public void importLuoxueSourceUris(ArrayList<Uri> uris) {
                luoxueSourceImportController.importSelectedUris(uris);
            }
        });
        downloadDirectoryPickerController = new DownloadDirectoryPickerController(
                () -> documentPickerController,
                message -> statusMessageController.showFeedback(message)
        );
        backgroundImagePickerController = new BackgroundImagePickerController(
                this,
                new BackgroundImagePickerController.Listener() {
                    @Override
                    public void backgroundImagePicked(String page, Uri uri, BackgroundTransform transform) {
                        settingsViewModel.applyPageBackgrounds(
                                settingsStore.pageBackgrounds().withBackground(page, uri.toString(), transform),
                                page,
                                false
                        );
                    }

                    @Override
                    public void backgroundImageCopyFailed(String page) {
                        statusMessageController.setStatus(AppLanguage.text(
                                settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                                "page.background.copy.failed"
                        ));
                    }
                },
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
                new ContentResolverLuoxueSourceDocumentReader(getContentResolver()),
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
                        nowPlayingStateController.renderNowBar();
                    }

                    @Override
                    public void updateHomeDashboardPlayback(PlaybackStateSnapshot snapshot) {
                        homeDashboardViewModel.updatePlayback(snapshot);
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
                        streamingPlaybackController.recoverStreamingBuffering(snapshot);
                    }

                    @Override
                    public boolean resolveCurrentStreamingTrackIfNeeded() {
                        return MainActivity.this.resolveCurrentStreamingQueueTrackIfNeeded();
                    }

                    @Override
                    public void setStatus(String status) {
                        statusMessageController.setStatus(status);
                    }
                }
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
        trackListRenderController = new TrackListRenderController(libraryViewModel, new TrackListRenderController.Listener() {
            @Override
            public void playTrackList(List<Track> tracks, int index) {
                libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index));
            }

            @Override
            public void toggleFavorite(Track track) {
                libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track));
            }

            @Override
            public void showAddToPlaylist(Track track) {
                libraryViewModel.onEvent(new LibraryEvent.AddToPlaylist(track));
            }

            @Override
            public void downloadTrack(Track track) {
                downloadRequestController.downloadTrack(track);
            }

            @Override
            public void downloadTracks(List<Track> tracks) {
                downloadRequestController.downloadTracks(tracks);
            }

            @Override
            public void showEditStream(Track track) {
                networkDialogController.showEditStream(track);
            }

            @Override
            public void confirmDeleteTrack(Track track) {
                confirmationDialogController.confirmDeleteTrack(track);
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
                publishTrackListChromeState(new TrackListChromeState(
                        new ArrayList<>(actions),
                        new ArrayList<>(headerMetrics),
                        new ArrayList<>(headerActions),
                        emptyText,
                        new ArrayList<>(modeActions),
                        labels
                ));
            }
        });
        recommendationActionController = new RecommendationActionController(
                streamingRecommendationViewModel,
                () -> settingsStore.languageMode(),
                new RecommendationActionCallbacks() {
                    @Override
                    public void setStatus(String status) {
                        statusMessageController.setStatus(status);
                    }

                    @Override
                    public void playDailyRecommendation(StreamingRecommendationPresentation presentation) {
                        playbackStartController.playRecommendation(presentation);
                    }

                    @Override
                    public HeartbeatRecommendationSeedRequest seedRequest(StreamingProviderName provider) {
                        return heartbeatSeedRequestProvider.request(provider);
                    }

                    @Override
                    public void playHeartbeatRecommendation(StreamingRecommendationPresentation presentation) {
                        playbackStartController.playHeartbeatRecommendation(presentation);
                    }

                    @Override
                    public void logSeedMiss(HeartbeatRecommendationSeedRequest request) {
                        MainActivity.this.logHeartbeatSeedMiss(request);
                    }
                }
        );
        homeDashboardRenderController = new HomeDashboardRenderController(homeDashboardViewModel, new HomeDashboardRenderController.Listener() {
            @Override
            public void openLibraryMode(String mode) {
                routeController.setLibraryMode(mode);
                navigateToTab(TAB_LIBRARY, true, true);
            }

            @Override
            public void continuePlayback(Track track) {
                continueDashboardPlayback(track);
            }

            @Override
            public void openNowPlaying() {
                nowPlayingStateController.renderNowBar();
            }

            @Override
            public void playTrack(Track track) {
                playTrackListFromHost(Collections.singletonList(track), 0);
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
                    playTrackListFromHost(shuffled, 0);
                }
            }

            @Override
            public void openStreaming() {
                navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING_HUB);
            }

            @Override
            public void openCollections() {
                routeController.navigateToTab(TAB_COLLECTIONS, true);
                renderCollections();
                renderSelectedTab();
            }

            @Override
            public void openSearch() {
                refreshUnifiedSearch(false);
                navigateToTab(TAB_SEARCH, true, true);
                syncNavHostState();
            }

            @Override
            public void playDailyRecommendations() {
                recommendationActionController.run(new RecommendationAction.PlayDaily(StreamingProviderName.NETEASE));
            }

            @Override
            public void playHeartbeatRecommendations() {
                recommendationActionController.run(new RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE));
            }

            @Override
            public void publishHomeDashboardActions(HomeDashboardActions actions) {
                homeDashboardViewModel.updateHomeDashboardActions(actions);
            }
        });
        queueRenderController = new QueueRenderController(new QueueRenderController.Listener() {
            @Override
            public void playTrackList(List<Track> tracks, int index) {
                playTrackListFromHost(tracks, index);
            }

            @Override
            public void toggleFavorite(Track track) {
                libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track));
            }

            @Override
            public void showAddToPlaylist(Track track) {
                playlistDialogController.showAddToPlaylist(track);
            }

            @Override
            public void removeQueueTrack(Track track) {
                MainActivity.this.removeQueueTrack(track);
            }

            @Override
            public void confirmClearQueue() {
                queueActionController.confirmClearQueue();
            }

            @Override
            public void requestBack() {
                handleAppBack();
            }

            @Override
            public void publishQueueChrome(
                    List<QueueTrackActions> actions,
                    Runnable onClearQueue,
                    QueueScreenLabels labels,
                    Runnable onBack
            ) {
            }
        });
        queueIntentController = new QueueIntentController(new QueueIntentController.Listener() {
            @Override
            public void playTrackList(List<Track> tracks, int index) {
                libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index));
            }

            @Override
            public void toggleFavorite(Track track) {
                libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track));
            }

            @Override
            public void showAddToPlaylist(Track track) {
                playlistDialogController.showAddToPlaylist(track);
            }

            @Override
            public void removeQueueTrack(Track track) {
                MainActivity.this.removeQueueTrack(track);
            }

            @Override
            public void moveQueueTrack(int fromIndex, int toIndex) {
                MainActivity.this.moveQueueTrack(fromIndex, toIndex);
            }

            @Override
            public void confirmClearQueue() {
                queueActionController.confirmClearQueue();
            }

            @Override
            public void back() {
                MainActivity.this.handleAppBack();
            }
        });
        playbackActionController = new PlaybackActionController(nowPlayingViewModel, new PlaybackActionController.Listener() {
            @Override
            public boolean resolveCurrentStreamingQueueTrackIfNeeded() {
                return MainActivity.this.resolveCurrentStreamingQueueTrackIfNeeded();
            }

            @Override
            public PlaybackStateSnapshot playbackSnapshot() {
                return playbackService == null ? playbackStore.snapshot() : playbackService.snapshot();
            }

            @Override
            public List<Track> fallbackTracks() {
                return libraryStore == null ? Collections.emptyList() : libraryStore.visibleTracks();
            }

            @Override
            public void applyPlaybackActionResult(PlaybackActionResultUi result) {
                MainActivity.this.applyPlaybackActionResult(result);
            }
        });
        heartbeatRecommendationController = new HeartbeatRecommendationController(streamingRecommendationViewModel, () -> settingsStore.languageMode(), new HeartbeatRecommendationController.Listener() {
            @Override
            public boolean hasPlaybackService() {
                return playbackService != null;
            }

            @Override
            public HeartbeatRecommendationSeedRequest seedRequest(StreamingProviderName provider) {
                return heartbeatSeedRequestProvider.request(provider);
            }

            @Override
            public void stopHeartbeatRecommendationMode() {
                streamingRecommendationViewModel.stopHeartbeatRecommendationMode();
            }

            @Override
            public void appendToQueue(StreamingRecommendationPresentation presentation) {
                nowPlayingViewModel.appendToQueue(presentation.getTracks());
            }

            @Override
            public void playHeartbeatRecommendation(StreamingRecommendationPresentation presentation) {
                playbackStartController.playHeartbeatRecommendation(presentation);
            }

            @Override
            public void logSeedMiss(HeartbeatRecommendationSeedRequest request) {
                MainActivity.this.logHeartbeatSeedMiss(request);
            }

            @Override
            public void setStatus(String status) {
                statusMessageController.setStatus(status);
            }
        });
        streamingPlaybackController = new StreamingPlaybackController(streamingViewModel, nowPlayingViewModel, new StreamingPlaybackController.Listener() {
            @Override
            public String languageMode() {
                return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
            }

            @Override
            public StreamingAudioQuality adaptiveStreamingQuality() {
                return MainActivity.this.adaptiveStreamingQuality();
            }

            @Override
            public StreamingAudioQuality selectedStreamingQuality() {
                return MainActivity.this.selectedStreamingQuality();
            }

            @Override
            public List<Track> queueSnapshot() {
                return playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot();
            }

            @Override
            public void maybeAppendHeartbeatRecommendations(PlaybackStateSnapshot snapshot) {
                heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot);
            }

            @Override
            public void applyPlaybackActionResult(PlaybackActionResultUi result) {
                MainActivity.this.applyPlaybackActionResult(result);
            }

            @Override
            public void setStatus(String status) {
                statusMessageController.setStatus(status);
            }
        });
        PlaybackController playbackStartPlaybackController = new PlaybackStartControllerAdapter(
                streamingPlaybackController::resolveAndPlayStreamingTrack,
                nowPlayingViewModel::playTrackList,
                this::applyPlaybackActionResult
        );
        playbackStartController = new PlaybackStartController(new PlaybackStartController.Listener() {
            @Override
            public void stopHeartbeatRecommendationMode() {
                streamingRecommendationViewModel.stopHeartbeatRecommendationMode();
            }

            @Override
            public void startPlaybackService() {
                startService(new Intent(MainActivity.this, EchoPlaybackService.class));
            }

            @Override
            public boolean hasPlaybackService() {
                return playbackService != null;
            }

            @Override
            public void savePendingPlayback(List<Track> tracks, int index) {
                pendingPlaybackTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);
                pendingPlaybackIndex = index;
            }

            @Override
            public List<Track> pendingPlaybackTracks() {
                return pendingPlaybackTracks == null ? Collections.emptyList() : pendingPlaybackTracks;
            }

            @Override
            public int pendingPlaybackIndex() {
                return pendingPlaybackIndex;
            }

            @Override
            public void clearPendingPlayback() {
                pendingPlaybackTracks = Collections.emptyList();
                pendingPlaybackIndex = -1;
            }

            @Override
            public String resolvingStatus() {
                return streamingViewModel.prepareStreamingPlaybackStatusText(
                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                        null
                ).getResolving();
            }

            @Override
            public void setStatus(String status) {
                statusMessageController.setStatus(status);
            }

            @Override
            public PlaybackController playbackController() {
                return playbackStartPlaybackController;
            }

            @Override
            public void openQueue() {
                navigateToTab(TAB_QUEUE, true, true);
            }
        });
        nowPlayingEffectController = new NowPlayingEffectController(new NowPlayingEffectController.Listener() {
            @Override
            public void openQueue() {
                navigateToTab(TAB_QUEUE, true, true);
            }

            @Override
            public void openAddToPlaylist(NowPlayingEffect.OpenAddToPlaylist effect) {
                playlistDialogController.showAddToPlaylist(effect.getTrack());
            }

            @Override
            public void shareTrack(NowPlayingEffect.ShareTrack effect) {
                trackShareLauncher.share(effect.getTrack());
            }

            @Override
            public void downloadTrack(NowPlayingEffect.DownloadTrack effect) {
                downloadRequestController.downloadTrack(effect.getTrack());
            }

            @Override
            public void switchSource(NowPlayingEffect.SwitchSource effect) {
                MainActivity.this.switchNowPlayingSource(effect);
            }

            @Override
            public void showMessage(String message) {
                statusMessageController.setStatus(message);
            }
        });
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
        queueActionController = new QueueActionController(nowPlayingViewModel, new QueueActionController.Listener() {
            @Override
            public void applyPlaybackActionResult(PlaybackActionResultUi result) {
                MainActivity.this.applyPlaybackActionResult(result);
            }

            @Override
            public boolean hasPlaybackService() {
                return playbackService != null;
            }

            @Override
            public void moveQueueTrack(int fromIndex, int toIndex) {
                playbackService.moveQueueTrack(fromIndex, toIndex);
            }

            @Override
            public void renderNowBar() {
                nowPlayingStateController.renderNowBar();
            }

            @Override
            public void renderSelectedTab() {
                MainActivity.this.renderSelectedTab();
            }

            @Override
            public void confirmClearQueue() {
                confirmationDialogController.confirmClearQueue();
            }

            @Override
            public String queueEmptyStatus() {
                return AppLanguage.text(settingsStore.languageMode(), "queue.empty");
            }

            @Override
            public void setStatus(String status) {
                statusMessageController.setStatus(status);
            }
        });
        lyricsViewModel.bindReloadGateway(
                () -> playbackStore == null ? null : playbackStore.snapshot().currentTrack,
                this::neteaseProviderTrackIdForLyrics,
                status -> statusMessageController.setStatus(status)
        );
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
                streamingGatewayController.applyEndpoint(((SettingsEffect.ApplyStreamingGatewayEndpoint) effect).getEndpoint());
            }
        });
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
        streamingPlaylistController = new StreamingPlaylistController(streamingViewModel, () -> settingsStore.languageMode(), new StreamingPlaylistController.Listener() {
            @Override
            public long selectedPlaylistId() {
                return routeController.selectedPlaylistId();
            }

            @Override
            public void setSelectedPlaylistId(long playlistId) {
                routeController.setSelectedPlaylistId(playlistId);
            }

            @Override
            public void loadCollections() {
                MainActivity.this.loadCollections();
            }

            @Override
            public void refreshLibraryAfterStreamingImport() {
                MainActivity.this.refreshLibraryAfterStreamingImport();
            }

            @Override
            public String selectedPlaylistName() {
                return MainActivity.this.selectedPlaylistName();
            }

            @Override
            public List<Track> selectedPlaylistTracks() {
                return libraryStore.selectedPlaylistTracks();
            }

            @Override
            public List<Track> favoriteTracks() {
                return libraryStore.favoriteTracks();
            }

            @Override
            public StreamingProviderName selectedStreamingProvider() {
                return streamingViewModel.getStreaming().getValue().getSelectedProvider();
            }

            @Override
            public void showStreamingProviderPicker(String playlistName, List<Track> tracks) {
                streamingPlaylistDialogController.showStreamingProviderPicker(playlistName, tracks);
            }

            @Override
            public void navigateToStreaming() {
                navigateToNetworkTabPage(NETWORK_STREAMING);
            }

            @Override
            public void showStreamingPlaylistLoadedDialog(String message) {
                streamingPlaylistDialogController.showStreamingPlaylistLoadedDialog(message);
            }

            @Override
            public void showAccountPlaylistImportPicker(StreamingProviderName provider, List<StreamingPlaylist> playlists) {
                streamingPlaylistDialogController.showAccountPlaylistImportPicker(provider, playlists);
            }

            @Override
            public void setStatus(String status) {
                statusMessageController.setStatus(status);
            }

            @Override
            public void renderSelectedTab() {
                MainActivity.this.renderSelectedTab();
            }
        });
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
        streamingManualCookieController = new StreamingManualCookieController(
                streamingViewModel,
                () -> settingsStore.languageMode(),
                new StreamingManualCookieController.Listener() {
                    @Override
                    public StreamingProviderName selectedProvider() {
                        return streamingViewModel.getStreaming().getValue().getSelectedProvider();
                    }

                    @Override
                    public void showManualCookieDialog(StreamingManualCookieDialogState dialogState) {
                        streamingManualCookieDialogController.show(dialogState);
                    }

                    @Override
                    public void onStreamingLoginSuccess(StreamingProviderName provider) {
                        streamingPlaylistController.onStreamingLoginSuccess(provider);
                    }

                    @Override
                    public void setStatus(String status) {
                        statusMessageController.setStatus(status);
                    }
                }
        );
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
        libraryViewModel.bindPlaylistActionGateway(new LibraryPlaylistActionGateway() {
            @Override
            public LibraryDefaultPlaylistAddResultUi addToDefaultPlaylist(Track track) {
                DefaultPlaylistAddResult result = new AddToDefaultPlaylistUseCase(playlistActionOperations).execute(track);
                return result == null
                        ? null
                        : new LibraryDefaultPlaylistAddResultUi(result.playlistId, result.added);
            }

            @Override
            public long createPlaylist(String name) {
                return new CreatePlaylistUseCase(playlistActionOperations).execute(name);
            }

            @Override
            public boolean renamePlaylist(long playlistId, String name) {
                return new RenamePlaylistUseCase(playlistActionOperations).execute(playlistId, name);
            }

            @Override
            public boolean deletePlaylist(long playlistId) {
                return new DeletePlaylistUseCase(playlistActionOperations).execute(playlistId);
            }

            @Override
            public boolean removeTrackFromPlaylist(long playlistId, Track track) {
                return new RemoveTrackFromPlaylistUseCase(playlistActionOperations).execute(playlistId, track);
            }

            @Override
            public boolean movePlaylistTrack(long playlistId, Track track, int trackIndex, int direction) {
                return new MovePlaylistTrackUseCase(playlistActionOperations).execute(playlistId, track, trackIndex, direction);
            }

            @Override
            public boolean addTrackToPlaylist(long playlistId, long trackId) {
                return new AddTrackToPlaylistUseCase(playlistActionOperations).execute(playlistId, trackId);
            }
        });
        playHistoryActionController = new PlayHistoryActionController(
                libraryViewModel,
                () -> settingsStore.languageMode(),
                viewModel::clearPlayHistory,
                status -> statusMessageController.setStatus(status),
                () -> loadCollections()
        );
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
                networkRequestController.syncRemoteSource(sourceId, remoteSourceName(sourceId));
            }

            @Override
            public void playRemoteSourceTracks(RemoteSource source) {
                MainActivity.this.playRemoteSourceTracks(source);
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
                renderNetworkTrackList(
                        new NetworkTrackListRequest(
                                title,
                                tracks,
                                showPlaylistAction,
                                details,
                                showStreamActions,
                                headerMetrics,
                                headerActions,
                                emptyText,
                                labels
                        )
                );
            }
        });
        streamingSearchEventController = new StreamingSearchEventController(
                new StreamingSearchActionHandlerBindings(streamingViewModel, streamingActionGateway),
                new StreamingSearchEventController.Navigator() {
                    @Override
                    public void backToNetworkHome() {
                        navigateNetworkPage(MainRoutes.NETWORK_HOME);
                    }

                    @Override
                    public void importStreamingPlaylist(StreamingPlaylist playlist) {
                        streamingPlaylistController.importStreamingPlaylistFromProviderRef(
                                playlist.getProvider(),
                                playlist.getProviderPlaylistId()
                        );
                    }

                    @Override
                    public void loadUserPlaylists() {
                        streamingPlaylistController.showAccountPlaylistSyncPicker(
                                streamingViewModel.getStreaming().getValue().getSelectedProvider()
                        );
                    }

                    @Override
                    public void importLikedTracks() {
                        streamingPlaylistController.importStreamingLikedTracks(
                                streamingViewModel.getStreaming().getValue().getSelectedProvider()
                        );
                    }

                    @Override
                    public void playDailyRecommendations() {
                        recommendationActionController.run(
                                new RecommendationAction.PlayDaily(streamingViewModel.getStreaming().getValue().getSelectedProvider())
                        );
                    }

                    @Override
                    public void playHeartbeatRecommendations() {
                        recommendationActionController.run(
                                new RecommendationAction.PlayHeartbeat(streamingViewModel.getStreaming().getValue().getSelectedProvider())
                        );
                    }

                    @Override
                    public void pasteImportPlaylist() {
                        streamingPlaylistImportDialogController.showImportDialog();
                    }

                    @Override
                    public void inputProviderCookie() {
                        streamingManualCookieController.showStreamingCookieDialog();
                    }
                },
                (labels, actions) -> streamingViewModel.updateStreamingSearchChrome(labels, actions)
        );
        streamingSearchRenderController = new StreamingSearchRenderController(
                () -> settingsStore.languageMode(),
                streamingSearchEventController
        );
        libraryGroupsRenderController = new LibraryGroupsRenderController(libraryViewModel, new LibraryGroupsRenderController.Listener() {
            @Override
            public void selectLibraryGroup(String key, String title) {
                libraryViewModel.onEvent(new LibraryEvent.OpenGroup(key, title));
            }

            @Override
            public void clearLibraryGroupSelection() {
                routeController.clearLibraryGroup();
            }

            @Override
            public void closeLibraryGroup() {
                libraryViewModel.onEvent(LibraryEvent.BackFromGroup.INSTANCE);
            }

            @Override
            public void openFavoritesCollection() {
                String title = AppLanguage.text(settingsStore.languageMode(), "favorite.playlist");
                libraryViewModel.onEvent(new LibraryEvent.OpenGroup("virtual:favorites", title));
            }

            @Override
            public void playTrackList(List<Track> tracks, int index) {
                libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index));
            }

            @Override
            public void confirmDeleteGroup(String title, List<Track> tracks) {
                confirmationDialogController.confirmDeleteTracks(title, tracks);
            }

            @Override
            public void publishLibraryGroupsChrome(
                    List<LibraryGroupActions> actions,
                    String emptyText,
                    List<TrackListModeAction> modeActions
            ) {
                publishLibraryGroupsChromeState(new LibraryGroupsChromeState(
                        new ArrayList<>(actions),
                        emptyText,
                        new ArrayList<>(modeActions)
                ));
            }

            @Override
            public void renderTrackList(
                    String title,
                    ArrayList<Track> tracks,
                    ArrayList<TrackListHeaderMetric> headerMetrics,
                    ArrayList<TrackListHeaderAction> headerActions,
                    ArrayList<TrackListAlbumCardUiState> footerAlbums
            ) {
                renderLibraryGroupTrackList(new LibraryGroupTrackListRequest(
                        title,
                        tracks,
                        headerMetrics,
                        headerActions,
                        footerAlbums
                ));
            }
        }, new ArtistInfoRepository(), action -> mainHandler.post(action));
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
        collectionsRenderController = new CollectionsRenderController(collectionsViewModel, new CollectionsRenderController.Listener() {
            @Override
            public void showCreatePlaylist() {
                playlistDialogController.showCreatePlaylist();
            }

            @Override
            public void openPlaylistM3uFilePicker() {
                documentPickerController.openPlaylistM3uFilePicker();
            }

            @Override
            public void confirmClearPlayHistory() {
                confirmationDialogController.confirmClearPlayHistory();
            }

            @Override
            public void requestBack() {
                handleAppBack();
            }

            @Override
            public void playTrackList(List<Track> tracks, int index) {
                playTrackListFromHost(tracks, index);
            }

            @Override
            public void toggleFavorite(Track track) {
                libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track));
            }

            @Override
            public void showAddToPlaylist(Track track) {
                playlistDialogController.showAddToPlaylist(track);
            }

            @Override
            public void downloadTrack(Track track) {
                downloadRequestController.downloadTrack(track);
            }

            @Override
            public void downloadTracks(List<Track> tracks) {
                downloadRequestController.downloadTracks(tracks);
            }

            @Override
            public void selectPlaylist(long playlistId) {
                routeController.setSelectedPlaylistId(playlistId);
                loadCollections();
            }

            @Override
            public void showRenamePlaylist(Playlist playlist) {
                playlistDialogController.showRenamePlaylist(playlist);
            }

            @Override
            public void confirmDeletePlaylist(Playlist playlist) {
                playlistDialogController.confirmDeletePlaylist(playlist);
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
                streamingPlaylistController.importSelectedPlaylistToStreaming();
            }

            @Override
            public void importFavoritesToStreaming() {
                streamingPlaylistController.importFavoritesToStreaming();
            }

            @Override
            public void importStreamingFavorites() {
                streamingPlaylistDialogController.showImportStreamingFavoritesProviderPicker();
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
            public void publishCollectionsActions(CollectionsActions actions) {
                collectionsViewModel.updateActions(actions);
            }
        });
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
        streamingViewModel.bindStreamingLocalPlaylistOperations(new StreamingLocalPlaylistOperations() {
            @Override
            public boolean playlistExists(long localPlaylistId) {
                return syncStreamingPlaylistUseCase.playlistExists(localPlaylistId);
            }

            @Override
            public PlaylistImportResult importStreamingPlaylist(String playlistName, StreamingProviderName provider, String providerPlaylistId, List<StreamingTrack> streamingTracks, boolean linkWhenProviderPlaylistIdBlank) {
                return importStreamingPlaylistUseCase.execute(
                        playlistName,
                        provider,
                        providerPlaylistId,
                        streamingTracks,
                        linkWhenProviderPlaylistIdBlank
                );
            }

            @Override
            public StreamingLocalPlaylistSyncResult syncStreamingPlaylist(StreamingPlaylistSyncStore.LinkedPlaylist link, List<StreamingTrack> streamingTracks) {
                SyncStreamingPlaylistResult result = syncStreamingPlaylistUseCase.execute(link, streamingTracks);
                return new StreamingLocalPlaylistSyncResult(
                        result.getPlaylistId(),
                        result.getSyncedCount(),
                        result.getEmpty()
                );
            }

            @Override
            public StreamingLoginPlaylistResult ensureStreamingLoginPlaylist(String playlistName, StreamingProviderName provider) {
                EnsureStreamingLoginPlaylistResult result = ensureStreamingLoginPlaylistUseCase.execute(playlistName, provider);
                return new StreamingLoginPlaylistResult(
                        result.getPlaylistId(),
                        result.getPlaylistName()
                );
            }

            @Override
            public StreamingPlaylistSyncStore.LinkedPlaylist linkedPlaylist(long localPlaylistId) {
                return streamingPlaylistLinkUseCase.execute(localPlaylistId);
            }

            @Override
            public StreamingPlaylistSyncStore.LinkedPlaylist linkedPlaylist(StreamingProviderName provider, String providerPlaylistId) {
                return streamingPlaylistLinkUseCase.execute(provider, providerPlaylistId);
            }
        });
        streamingTrackMatchUseCase = new StreamingTrackMatchUseCase(
                new MusicLibraryStreamingTrackMatchOperations(repository)
        );
        streamingViewModel.bindStreamingTrackMatchStore(streamingTrackMatchUseCase);
        streamingRecommendationViewModel.bindStreamingTrackMatchStore(streamingTrackMatchUseCase);
        heartbeatSeedRequestProvider.bind(new HeartbeatRecommendationSeedResolver(
                streamingTrackMatchUseCase,
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
        libraryViewModel.bindCollectionGateway(new LibraryCollectionGateway() {
            @Override
            public LibraryCollectionsResult loadCollections(long selectedPlaylistId) {
                LibraryCollectionsSnapshot loaded = new LoadLibraryCollectionsUseCase(libraryCollectionOperations).execute(selectedPlaylistId);
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
            @Override
            public LibraryLoadResultUi loadCached() {
                return toLibraryLoadResultUi(new LoadLibraryUseCase(libraryImportOperations).cached());
            }

            @Override
            public LibraryLoadResultUi refresh() {
                return toLibraryLoadResultUi(new LoadLibraryUseCase(libraryImportOperations).refresh());
            }

            @Override
            public LibraryLoadResultUi importAudioUris(List<Uri> uris) {
                return toLibraryLoadResultUi(new ImportAudioUrisUseCase(libraryImportOperations).execute(uris));
            }

            @Override
            public LibraryLoadResultUi importAudioTree(Uri treeUri) {
                return toLibraryLoadResultUi(new ImportAudioTreeUseCase(libraryImportOperations).execute(treeUri));
            }

            @Override
            public LibraryAudioSpecsResultUi parseMissingAudioSpecs() {
                AudioSpecsParseResult result = new ParseMissingAudioSpecsUseCase(libraryImportOperations).execute();
                return new LibraryAudioSpecsResultUi(
                        result.getUpdatedCount(),
                        result.getTracks(),
                        result.getFavorites()
                );
            }
        });
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
                () -> playbackService == null ? null : new SettingsPlaybackServiceControls() {
                    @Override
                    public void setPlaybackSpeed(float speed) {
                        playbackService.setPlaybackSpeed(speed);
                    }

                    @Override
                    public void setAppVolume(float volume) {
                        playbackService.setAppVolume(volume);
                    }

                    @Override
                    public void setConcurrentPlaybackEnabled(boolean enabled) {
                        playbackService.setConcurrentPlaybackEnabled(enabled);
                    }

                    @Override
                    public void applyAudioEffectSettings(AudioEffectSettings settings) {
                        playbackService.applyAudioEffectSettings(settings);
                    }

                    @Override
                    public void setStatusBarLyricsEnabled(boolean enabled) {
                        playbackService.setStatusBarLyricsEnabled(enabled);
                    }

                    @Override
                    public void setPlaybackRestoreEnabled(boolean enabled) {
                        playbackService.setPlaybackRestoreEnabled(enabled);
                    }

                    @Override
                    public void setReplayGainEnabled(boolean enabled) {
                        playbackService.setReplayGainEnabled(enabled);
                    }
                },
                () -> lyricsViewModel == null ? null : new SettingsLyricsControls() {
                    @Override
                    public void setOnlineEnabled(boolean enabled) {
                        lyricsViewModel.setOnlineEnabled(enabled);
                    }

                    @Override
                    public void setOffsetMs(long offsetMs) {
                        lyricsViewModel.setOffsetMs(offsetMs);
                    }
                },
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
                        navigateToNetworkTabPage(MainRoutes.NETWORK_STREAM_LIST);
                    }

                    @Override
                    public void onStreamPlaylistImported(List<Track> cached, Set<Long> favorites, String status) {
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING);
                    }

                    @Override
                    public void onAllStreamsDeleted(List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING);
                    }

                    @Override
                    public void onTrackDeleted(List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(MainRoutes.NETWORK_STREAM_LIST);
                    }

                    @Override
                    public void onRemoteSourceDeleted(List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(MainRoutes.NETWORK_SOURCES);
                        loadCollections();
                    }

                    @Override
                    public void onWebDavSourceSaved(long sourceId, List<Track> cached, Set<Long> favorites, String status) {
                        retainPlaybackTracks(cached);
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(sourceId > 0L ? MainRoutes.NETWORK_SOURCES : MainRoutes.NETWORK_WEBDAV);
                        loadCollections();
                    }

                    @Override
                    public void onRemoteSourceTested(String status) {
                        statusMessageController.setStatus(status);
                        loadCollections();
                    }

                    @Override
                    public void onRemoteSourceSynced(List<Track> cached, Set<Long> favorites, String status) {
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(MainRoutes.NETWORK_SOURCES);
                    }

                    @Override
                    public void onAllWebDavSourcesSynced(List<Track> cached, Set<Long> favorites, String status) {
                        replaceLibrary(cached, favorites, status);
                        navigateToNetworkTabPage(MainRoutes.NETWORK_WEBDAV);
                    }
                }
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
        lyricsViewModel.bindListener(new LyricsStateRefreshListener(
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
                new PlaylistDialogController.Listener() {
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
                    public void addToDefaultPlaylist(Track track) {
                        MainActivity.this.addToDefaultPlaylist(track);
                    }

                    @Override
                    public void addTrackToPlaylist(long playlistId, long trackId) {
                        MainActivity.this.addTrackToPlaylist(playlistId, trackId);
                    }
                }
        );
    }

    private void addToDefaultPlaylist(Track track) {
        libraryViewModel.addToDefaultPlaylistJava(track, (playlistId, added) ->
                onDefaultPlaylistTrackAdded(playlistId, added)
        );
    }

    private void createPlaylist(String name) {
        libraryViewModel.createPlaylistJava(name, this::onPlaylistCreated);
    }

    private void renamePlaylist(long playlistId, String name) {
        libraryViewModel.renamePlaylistJava(playlistId, name, this::onPlaylistRenamed);
    }

    private void deletePlaylist(long playlistId, String name) {
        libraryViewModel.deletePlaylistJava(playlistId, name, this::onPlaylistDeleted);
    }

    private void removeSelectedPlaylistTrack(long playlistId, Track track) {
        libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track, this::onSelectedPlaylistTrackRemoved);
    }

    private void moveSelectedPlaylistTrack(long playlistId, Track track, int trackIndex, int direction) {
        libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction, this::onSelectedPlaylistTrackMoved);
    }

    private void addTrackToPlaylist(long playlistId, long trackId) {
        libraryViewModel.addTrackToPlaylistJava(playlistId, trackId, this::onTrackAddedToPlaylist);
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
            playbackStore.publish(playbackService);
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

    private LibraryLoadResultUi toLibraryLoadResultUi(LibraryLoadResult result) {
        return new LibraryLoadResultUi(result.getTracks(), result.getFavorites(), "Library updated");
    }

}

