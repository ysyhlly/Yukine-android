package app.echo.next;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import app.echo.next.data.MusicLibraryRepository;
import app.echo.next.model.Playlist;
import app.echo.next.model.PlaylistImportResult;
import app.echo.next.model.RemoteSource;
import app.echo.next.model.Track;
import app.echo.next.playback.EchoPlaybackService;
import app.echo.next.playback.PlaybackStateSnapshot;
import app.echo.next.ui.EchoDialog;
import app.echo.next.ui.EchoTheme;
import app.echo.next.ui.LibraryGroupActions;
import app.echo.next.ui.LibraryGroupUiState;
import app.echo.next.ui.QueueTrackUiState;
import app.echo.next.ui.StateScreenAction;
import app.echo.next.ui.StateScreenFactory;
import app.echo.next.ui.TrackListHeaderAction;
import app.echo.next.ui.TrackListHeaderMetric;
import app.echo.next.ui.TrackListLabels;
import app.echo.next.ui.TrackListModeAction;
import app.echo.next.ui.TrackRowActions;
import app.echo.next.ui.TrackRowUiState;

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
    private static final String STREAMING_AUTH_REDIRECT_URI = "echo-next://streaming-auth";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MainExecutors executors = new MainExecutors();
    private final StreamingPlaybackTaskScheduler streamingPlaybackTaskScheduler = new StreamingPlaybackTaskScheduler();
    private final ResolveStreamingPlaybackUseCase resolveStreamingPlaybackUseCase = new ResolveStreamingPlaybackUseCase();
    private final StreamingHeartbeatRecommendationUseCase heartbeatRecommendationUseCase =
            new StreamingHeartbeatRecommendationUseCase();
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
    private PlaybackServiceConnectionController playbackServiceConnectionController;
    private PlaybackStateUpdateController playbackStateUpdateController;
    private PlaybackStateEventController playbackStateEventController;
    private MainTabRenderDispatcher tabRenderDispatcher;
    private NetworkRenderCoordinator networkRenderCoordinator;
    private SettingsRenderCoordinator settingsRenderCoordinator;
    private TrackListRenderController trackListRenderController;
    private app.echo.next.streaming.StreamingPlaylistSyncStore streamingPlaylistSyncStore;
    private ImportStreamingPlaylistUseCase importStreamingPlaylistUseCase;
    private SyncStreamingPlaylistUseCase syncStreamingPlaylistUseCase;
    private EnsureStreamingLoginPlaylistUseCase ensureStreamingLoginPlaylistUseCase;
    private GetStreamingPlaylistLinkUseCase getStreamingPlaylistLinkUseCase;
    private QueueRenderController queueRenderController;
    private NetworkSourcesRenderController networkSourcesRenderController;
    private NetworkMenuRenderController networkMenuRenderController;
    private NetworkTrackListRenderController networkTrackListRenderController;
    private StreamingGatewayEventController streamingGatewayEventController;
    private StreamingGatewayController streamingGatewayController;
    private StreamingAuthCallbackController streamingAuthCallbackController;
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

    private NetworkDialogController networkDialogController;
    private PlaylistDialogController playlistDialogController;
    private ConfirmationDialogController confirmationDialogController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);
        viewModel.bindStreamingActionGateway(new ActivityStreamingActionGateway());
        viewModel.bindStreamingPlaybackCoordinator(
                resolveStreamingPlaybackUseCase,
                new ActivityStreamingPlaybackTaskQueue()
        );
        nowPlayingViewModel = new ViewModelProvider(this).get(NowPlayingViewModel.class);
        nowPlayingViewModel.bindGateway(new ActivityNowPlayingGateway());
        nowPlayingViewModel.bindPlaybackGateway(new ActivityNowPlayingPlaybackGateway());
        lyricsViewModel = new ViewModelProvider(this).get(LyricsViewModel.class);
        libraryViewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        libraryViewModel.bindGateway(new ActivityLibraryGateway());
        collectionsViewModel = new ViewModelProvider(this).get(CollectionsViewModel.class);
        settingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        settingsViewModel.bindGateway(new ActivitySettingsGateway());
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
        streamingAuthCallbackController.handleInitialIntent(getIntent());
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
                loadLibrary(false);
            }
        });
        uiShellController = new MainUiShellController(this, new MainUiShellController.Listener() {
            @Override
            public void onSearchChanged(String query) {
                handleLibraryEvent(new LibraryEvent.Search(query));
            }

            @Override
            public void onTabSelected(String tabKey, boolean userInitiated) {
                navigateToTab(tabKey, userInitiated, true);
            }

            @Override
            public void onRouteSelected(String route) {
                if (!route.equals(selectedTab())) {
                    return;
                }
                routeController.setSelectedTab(route);
                renderSelectedTabContent();
            }

            @Override
            public boolean onHorizontalSwipe(boolean next) {
                return handleContentHorizontalSwipe(next);
            }

            @Override
            public String selectedTab() {
                return MainActivity.this.selectedTab();
            }

            @Override
            public void onNowPlayingEvent(NowPlayingEvent event) {
                handleNowPlayingEvent(event);
            }

            @Override
            public void onPrevious() {
                handleNowPlayingEvent(NowPlayingEvent.Previous.INSTANCE);
            }

            @Override
            public void onPlayPause() {
                handleNowPlayingEvent(NowPlayingEvent.PlayPause.INSTANCE);
            }

            @Override
            public void onNext() {
                handleNowPlayingEvent(NowPlayingEvent.Next.INSTANCE);
            }

            @Override
            public void onFavorite() {
                handleNowPlayingEvent(NowPlayingEvent.ToggleFavorite.INSTANCE);
            }

            @Override
            public void onShuffle() {
                handleNowPlayingEvent(NowPlayingEvent.ToggleShuffle.INSTANCE);
            }

            @Override
            public void onBottomPlaybackMode() {
                cycleBottomPlaybackMode();
            }

            @Override
            public void onRepeat() {
                handleNowPlayingEvent(NowPlayingEvent.CycleRepeatMode.INSTANCE);
            }

            @Override
            public void onSeek(long positionMs) {
                handleNowPlayingEvent(new NowPlayingEvent.SeekTo(positionMs));
            }

            @Override
            public void onOpenNowPlayingOverlay() {
                MainActivity.this.openNowPlayingOverlay();
            }

            @Override
            public void onCloseNowPlayingOverlay() {
                MainActivity.this.renderNowBar();
            }

            @Override
            public void onOpenQueueFromNowPlayingOverlay() {
                handleNowPlayingEvent(NowPlayingEvent.OpenQueue.INSTANCE);
            }
        });
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
        playbackServiceConnectionController = new PlaybackServiceConnectionController(
                this,
                playbackStateEventController,
                new PlaybackServiceConnectionController.Listener() {
                    @Override
                    public void onPlaybackServiceConnected(EchoPlaybackService service) {
                        playbackService = service;
                        playbackService.setPlaybackSpeed(settingsStore.playbackSpeed());
                        playbackService.setAppVolume(settingsStore.appVolume());
                        playbackService.setConcurrentPlaybackEnabled(settingsStore.concurrentPlaybackEnabled());
                        playPendingTracksIfNeeded();
                        renderSelectedTab();
                        renderNowBar();
                    }

                    @Override
                    public void onPlaybackServiceDisconnected() {
                        playbackService = null;
                        playbackStore.reset();
                        renderSelectedTab();
                        renderNowBar();
                    }
                }
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
        trackListRenderController = new TrackListRenderController(this, libraryViewModel, new TrackListRenderController.Listener() {
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
            public void addVirtualContent(View view) {
                MainActivity.this.addVirtualContent(view);
            }
        });
        homeDashboardRenderController = new HomeDashboardRenderController(this, viewModel, new HomeDashboardRenderController.Listener() {
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
            public void addVirtualContent(View view) {
                MainActivity.this.addVirtualContent(view);
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
            public void playDailyRecommendations() {
                MainActivity.this.playStreamingDailyRecommendations(
                        app.echo.next.streaming.StreamingProviderName.NETEASE);
            }

            @Override
            public void playHeartbeatRecommendations() {
                MainActivity.this.playStreamingHeartbeatRecommendations(
                        app.echo.next.streaming.StreamingProviderName.NETEASE);
            }
        });
        queueRenderController = new QueueRenderController(this, viewModel, new QueueRenderController.Listener() {
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
            public void addVirtualContent(View view) {
                MainActivity.this.addVirtualContent(view);
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
                    public void importStreamingPlaylist(app.echo.next.streaming.StreamingPlaylist playlist) {
                        if (playlist == null) {
                            return;
                        }
                        MainActivity.this.importStreamingPlaylist(playlist.getProvider(), playlist.getProviderPlaylistId());
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
                    public void addVirtualContent(View view) {
                        MainActivity.this.addVirtualContent(view);
                    }
                }
        );
        streamingSearchRenderController = new StreamingSearchRenderController(
                this,
                viewModel,
                () -> settingsStore.languageMode(),
                streamingSearchEventController
        );
        libraryGroupsRenderController = new LibraryGroupsRenderController(this, libraryViewModel, new LibraryGroupsRenderController.Listener() {
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
            public void publishLibraryGroups(String title, ArrayList<LibraryGroupUiState> rows) {
                MainActivity.this.publishLibraryGroupsUiState(title, rows);
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

            @Override
            public void addVirtualContent(View view) {
                MainActivity.this.addVirtualContent(view);
            }
        });
        collectionsRenderController = new CollectionsRenderController(this, collectionsViewModel, new CollectionsRenderController.Listener() {
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
            public void addVirtualContent(View view) {
                MainActivity.this.addVirtualContent(view);
            }
        });
        nowPlayingRenderController = new NowPlayingRenderController(this, new NowPlayingRenderController.Listener() {
            @Override
            public void addVirtualContent(View view) {
                MainActivity.this.addVirtualContent(view);
            }
        });
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
        streamingPlaylistSyncStore = new app.echo.next.streaming.StreamingPlaylistSyncStore(this);
        importStreamingPlaylistUseCase = new ImportStreamingPlaylistUseCase(
                new MusicLibraryStreamingPlaylistImportOperations(repository, streamingPlaylistSyncStore)
        );
        viewModel.bindStreamingLocalPlaylistImporter(new StreamingLocalPlaylistImporter() {
            @Override
            public app.echo.next.model.PlaylistImportResult importStreamingPlaylist(
                    String playlistName,
                    app.echo.next.streaming.StreamingProviderName provider,
                    String providerPlaylistId,
                    List<app.echo.next.streaming.StreamingTrack> streamingTracks,
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
        });
        syncStreamingPlaylistUseCase = new SyncStreamingPlaylistUseCase(
                new MusicLibraryStreamingPlaylistSyncOperations(repository, streamingPlaylistSyncStore)
        );
        viewModel.bindStreamingLocalPlaylistSyncer(new StreamingLocalPlaylistSyncer() {
            @Override
            public StreamingLocalPlaylistSyncResult syncStreamingPlaylist(
                    app.echo.next.streaming.StreamingPlaylistSyncStore.LinkedPlaylist link,
                    List<app.echo.next.streaming.StreamingTrack> streamingTracks
            ) {
                SyncStreamingPlaylistResult result = syncStreamingPlaylistUseCase.execute(link, streamingTracks);
                return new StreamingLocalPlaylistSyncResult(
                        result.getPlaylistId(),
                        result.getSyncedCount(),
                        result.getEmpty()
                );
            }
        });
        ensureStreamingLoginPlaylistUseCase = new EnsureStreamingLoginPlaylistUseCase(
                new MusicLibraryStreamingLoginPlaylistOperations(repository, streamingPlaylistSyncStore)
        );
        viewModel.bindStreamingLoginPlaylistEnsurer(new StreamingLoginPlaylistEnsurer() {
            @Override
            public StreamingLoginPlaylistResult ensureStreamingLoginPlaylist(
                    String playlistName,
                    app.echo.next.streaming.StreamingProviderName provider
            ) {
                EnsureStreamingLoginPlaylistResult result =
                        ensureStreamingLoginPlaylistUseCase.execute(playlistName, provider);
                return new StreamingLoginPlaylistResult(
                        result.getPlaylistId(),
                        result.getPlaylistName()
                );
            }
        });
        getStreamingPlaylistLinkUseCase = new GetStreamingPlaylistLinkUseCase(
                new StreamingPlaylistSyncStoreLinkOperations(streamingPlaylistSyncStore)
        );
        streamingTrackMatchUseCase = new StreamingTrackMatchUseCase(
                new MusicLibraryStreamingTrackMatchOperations(repository)
        );
        viewModel.bindStreamingTrackMatchStore(new StreamingTrackMatchStore() {
            @Override
            public String directProviderTrackId(
                    Track track,
                    app.echo.next.streaming.StreamingProviderName provider
            ) {
                return streamingTrackMatchUseCase.directProviderTrackId(track, provider);
            }

            @Override
            public String providerTrackIdFor(
                    Track track,
                    app.echo.next.streaming.StreamingProviderName provider
            ) {
                return streamingTrackMatchUseCase.providerTrackIdFor(track, provider);
            }

            @Override
            public void saveProviderTrackId(
                    Track track,
                    app.echo.next.streaming.StreamingProviderName provider,
                    String providerTrackId
            ) {
                streamingTrackMatchUseCase.saveProviderTrackId(track, provider, providerTrackId);
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
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "theme.applied")
                                + AppLanguage.themeLabel(settingsStore.themeMode(), settingsStore.languageMode()));
                    }

                    @Override
                    public void onAccentModeApplied(String accent) {
                        settingsStore.setAccentMode(accent);
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "accent.applied")
                                + AppLanguage.accentLabel(settingsStore.accentMode(), settingsStore.languageMode()));
                    }

                    @Override
                    public void onLanguageModeApplied(String languageMode) {
                        settingsStore.setLanguageMode(languageMode);
                        uiShellController.updateLanguage(settingsStore.languageMode());
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "language.applied")
                                + AppLanguage.labelFor(settingsStore.languageMode()));
                    }

                    @Override
                    public void onPlaybackSpeedApplied(float speed) {
                        settingsStore.setPlaybackSpeed(speed);
                        if (playbackService != null) {
                            playbackService.setPlaybackSpeed(settingsStore.playbackSpeed());
                        }
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "speed.applied")
                                + SettingsPageRenderController.playbackSpeedLabel(settingsStore.playbackSpeed()));
                    }

                    @Override
                    public void onAppVolumeApplied(float volume) {
                        settingsStore.setAppVolume(volume);
                        if (playbackService != null) {
                            playbackService.setAppVolume(settingsStore.appVolume());
                        }
                        renderSelectedTab();
                        renderNowBar();
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "volume.applied")
                                + SettingsPageRenderController.appVolumeLabel(settingsStore.appVolume()));
                    }

                    @Override
                    public void onStreamingAudioQualityApplied(String quality) {
                        settingsStore.setStreamingAudioQuality(quality);
                        renderSelectedTab();
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.quality.applied")
                                + SettingsPageRenderController.streamingQualityLabel(
                                settingsStore.streamingAudioQuality(),
                                settingsStore.languageMode()
                        ));
                    }

                    @Override
                    public void onOnlineLyricsEnabledApplied(boolean enabled) {
                        if (lyricsViewModel != null) {
                            lyricsViewModel.setOnlineEnabled(enabled);
                        }
                        setStatus(enabled
                                ? AppLanguage.text(settingsStore.languageMode(), "online.lyrics.enabled")
                                : AppLanguage.text(settingsStore.languageMode(), "online.lyrics.disabled"));
                        reloadCurrentLyrics();
                        renderSelectedTab();
                    }

                    @Override
                    public void onConcurrentPlaybackEnabledApplied(boolean enabled) {
                        settingsStore.setConcurrentPlaybackEnabled(enabled);
                        if (playbackService != null) {
                            playbackService.setConcurrentPlaybackEnabled(settingsStore.concurrentPlaybackEnabled());
                        }
                        setStatus(enabled
                                ? AppLanguage.text(settingsStore.languageMode(), "concurrent.playback.enabled")
                                : AppLanguage.text(settingsStore.languageMode(), "concurrent.playback.disabled"));
                        renderSelectedTab();
                    }

                    @Override
                    public void onLyricsOffsetApplied(long offsetMs) {
                        if (lyricsViewModel != null) {
                            lyricsViewModel.setOffsetMs(offsetMs);
                        }
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "lyrics.offset.applied")
                                + SettingsPageRenderController.lyricsOffsetLabel(offsetMs));
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
                onLyricsStateChanged();
            }
        });
        SettingsPageEventController settingsPageEventController = new SettingsPageEventController(
                settingsViewModel,
                view -> MainActivity.this.addVirtualContent(view)
        );
        SettingsPageRenderController settingsPageRenderController = new SettingsPageRenderController(
                this,
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
                view -> MainActivity.this.addVirtualContent(view)
        );
        networkMenuRenderController = new NetworkMenuRenderController(this, networkMenuEventController);
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
                () -> MainActivity.this.renderAndPersistSelectedTab(),
                view -> MainActivity.this.addVirtualContent(view)
        );
        networkSourcesRenderController = new NetworkSourcesRenderController(this, networkSourcesViewModel, networkSourcesEventController);
        networkRenderCoordinator = new NetworkRenderCoordinator(
                libraryStore,
                networkMenuRenderController,
                networkTrackListRenderController,
                networkSourcesRenderController,
                streamingSearchRenderController
        );
        uiShellController.applyThemeSurface();
        uiShellController.build(selectedTab(), settingsStore.languageMode());
        installBackNavigation();
        playbackServiceConnectionController.bind();
        permissionController.requestNeededPermissions();
        loadLibrary(true);
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
        streamingAuthCallbackController.handleNewIntent(intent);
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
        if (uiShellController != null && uiShellController.hideNowPlayingOverlayIfVisible()) {
            return true;
        }
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
                app.echo.next.streaming.StreamingProviderName.NETEASE
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
            return;
        }
        persistRouteFields();
        uiShellController.navigateContentRoute(selectedTab());
        renderSelectedTabContent();
    }

    private void renderSelectedTabContent() {
        if (!uiShellController.hasContentHost()) {
            return;
        }
        persistRouteFields();
        uiShellController.updateSelectedContentRoute(selectedTab());
        nowPlayingRenderController.clear();
        useScrollingContentContainer();
        updateTabBar();
        updateHeaderMode();
        tabRenderDispatcher.render(selectedTab());
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

    private void addVirtualContent(View view) {
        uiShellController.addVirtualContent(view);
    }

    private void addStateContent(String message) {
        addStateContent(message, new ArrayList<StateScreenAction>());
    }

    private void addStateContent(String message, List<StateScreenAction> actions) {
        addVirtualContent(StateScreenFactory.create(this, message, actions));
    }

    private void addStateContent(String title, String description, List<StateScreenAction> actions) {
        addVirtualContent(StateScreenFactory.create(this, title, description, actions));
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
            ArrayList<StateScreenAction> actions = new ArrayList<>();
            actions.add(new StateScreenAction(AppLanguage.text(settingsStore.languageMode(), "grant.access"), new Runnable() {
                @Override
                public void run() {
                    permissionController.requestNeededPermissions();
                }
            }));
            addStateContent(
                    AppLanguage.text(settingsStore.languageMode(), "audio.permission.required"),
                    AppLanguage.text(settingsStore.languageMode(), "audio.permission.description"),
                    actions
            );
            return;
        }
        if (libraryStore.visibleTracks().isEmpty() && !LIBRARY_PLAYLISTS.equals(libraryMode())) {
            ArrayList<StateScreenAction> actions = new ArrayList<>();
            actions.add(new StateScreenAction(AppLanguage.text(settingsStore.languageMode(), "scan.library"), new Runnable() {
                @Override
                public void run() {
                    loadLibrary(false);
                }
            }));
            actions.add(new StateScreenAction(AppLanguage.text(settingsStore.languageMode(), "import.audio.files"), new Runnable() {
                @Override
                public void run() {
                    openAudioFilePicker();
                }
            }));
            addStateContent(
                    AppLanguage.text(settingsStore.languageMode(), "no.music"),
                    AppLanguage.text(settingsStore.languageMode(), "no.music.description"),
                    actions
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
                    playlist.trackCount > 0
            ));
        }
        publishLibraryGroupsUiState(AppLanguage.text(languageMode, "playlists"), rows);
        addVirtualContent(app.echo.next.ui.LibraryGroupsScreenFactory.create(
                this,
                viewModel.getLibraryGroups(),
                actions,
                AppLanguage.text(languageMode, "no.playlists"),
                libraryModeActions()
        ));
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
                setStatus(added
                        ? AppLanguage.text(settingsStore.languageMode(), "added.to.playlist")
                        : AppLanguage.text(settingsStore.languageMode(), "could.not.add.to.playlist"));
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
            setStatus(AppLanguage.text(settingsStore.languageMode(), "playlist.created"));
            loadCollections();
        });
    }

    private void renamePlaylist(final long playlistId, final String name) {
        libraryViewModel.renamePlaylistJava(playlistId, name, (renamedPlaylistId, renamed) -> {
            if (renamed) {
                routeController.setSelectedPlaylistId(renamedPlaylistId);
            }
            setStatus(renamed
                    ? AppLanguage.text(settingsStore.languageMode(), "playlist.renamed")
                    : AppLanguage.text(settingsStore.languageMode(), "playlist.rename.failed"));
            loadCollections();
        });
    }

    private void deletePlaylist(final long playlistId, final String name) {
        libraryViewModel.deletePlaylistJava(playlistId, name, (deletedPlaylistId, deletedName, deleted) -> {
            if (deleted && selectedPlaylistId() == deletedPlaylistId) {
                routeController.setSelectedPlaylistId(-1L);
            }
            setStatus(deleted
                    ? AppLanguage.text(settingsStore.languageMode(), "deleted.playlist.prefix") + deletedName
                    : AppLanguage.text(settingsStore.languageMode(), "could.not.delete.playlist"));
            loadCollections();
        });
    }

    private void removeSelectedPlaylistTrack(final long playlistId, final Track track) {
        libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track, (removedPlaylistId, removedTrack) -> {
            routeController.setSelectedPlaylistId(removedPlaylistId);
            setStatus(AppLanguage.text(settingsStore.languageMode(), "removed.from.playlist.prefix") + removedTrack.title);
            loadCollections();
        });
    }

    private void moveSelectedPlaylistTrack(final long playlistId, final Track track, final int trackIndex, final int direction) {
        libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction, (movedPlaylistId, movedTrack, movedDirection, moved) -> {
            routeController.setSelectedPlaylistId(movedPlaylistId);
            if (moved) {
                setStatus((movedDirection < 0
                        ? AppLanguage.text(settingsStore.languageMode(), "moved.up.prefix")
                        : AppLanguage.text(settingsStore.languageMode(), "moved.down.prefix")) + movedTrack.title);
            } else {
                setStatus(AppLanguage.text(settingsStore.languageMode(), "move.failed"));
            }
            loadCollections();
        });
    }

    private void addTrackToPlaylist(final long playlistId, final long trackId) {
        libraryViewModel.addTrackToPlaylistJava(playlistId, trackId, (addedPlaylistId, added) -> {
            routeController.setSelectedPlaylistId(addedPlaylistId);
            setStatus(added
                    ? AppLanguage.text(settingsStore.languageMode(), "added.to.playlist")
                    : AppLanguage.text(settingsStore.languageMode(), "could.not.add.to.playlist"));
            loadCollections();
        });
    }

    private void renderQueue() {
        if (playbackService == null) {
            ArrayList<StateScreenAction> actions = new ArrayList<>();
            actions.add(new StateScreenAction(AppLanguage.text(settingsStore.languageMode(), "tab.library"), new Runnable() {
                @Override
                public void run() {
                    navigateToTab(TAB_LIBRARY, true, true);
                }
            }));
            addStateContent(
                    AppLanguage.text(settingsStore.languageMode(), "playback.service.unavailable"),
                    AppLanguage.text(settingsStore.languageMode(), "playback.service.unavailable.description"),
                    actions
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

    private void deleteAllStreams() {
        networkRequestController.deleteAllStreams();
    }

    private void deleteTrack(final long trackId, final String status) {
        networkRequestController.deleteTrack(trackId, status);
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
        long playlistId = selectedPlaylistId();
        if (playlistId < 0L) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.no.tracks.to.import"));
            return;
        }
        java.util.List<Track> tracks = libraryStore.selectedPlaylistTracks();
        if (tracks == null || tracks.isEmpty()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.no.tracks.to.import"));
            return;
        }
        showStreamingProviderPicker(selectedPlaylistName(), tracks);
    }

    private void importFavoritesToStreaming() {
        java.util.List<Track> tracks = libraryStore.favoriteTracks();
        if (tracks == null || tracks.isEmpty()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.no.tracks.to.import"));
            return;
        }
        showStreamingProviderPicker(
                AppLanguage.text(settingsStore.languageMode(), "favorites"),
                tracks
        );
    }

    private void syncSelectedPlaylistFromStreaming() {
        long playlistId = routeController.selectedPlaylistId();
        if (playlistId < 0L) {
            return;
        }
        app.echo.next.streaming.StreamingPlaylistSyncStore.LinkedPlaylist link =
                getStreamingPlaylistLinkUseCase.execute(playlistId);
        if (link == null) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.not.linked"));
            return;
        }
        setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.sync.started"));
        syncOneStreamingPlaylist(link);
    }

    private void showStreamingProviderPicker(String playlistName, java.util.List<Track> tracks) {
        java.util.List<app.echo.next.streaming.StreamingProviderDescriptor> providers =
                viewModel.getStreaming().getValue().getProviders();
        java.util.List<app.echo.next.streaming.StreamingProviderDescriptor> selectable = new ArrayList<>();
        for (app.echo.next.streaming.StreamingProviderDescriptor descriptor : providers) {
            if (descriptor == null) continue;
            if (!descriptor.getCapabilities().getSupportsSearch()) continue;
            if (descriptor.getName() == app.echo.next.streaming.StreamingProviderName.MOCK) {
                // Skip the offline mock; users do not want to import their library into a demo provider.
                continue;
            }
            selectable.add(descriptor);
        }
        if (selectable.isEmpty()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.no.providers"));
            return;
        }
        final String[] labels = new String[selectable.size()];
        for (int i = 0; i < selectable.size(); i++) {
            labels[i] = selectable.get(i).getDisplayName();
        }
        EchoDialog.builder(this)
                .setTitle(AppLanguage.text(settingsStore.languageMode(), "choose.streaming.provider"))
                .setItems(labels, (dialog, which) -> {
                    app.echo.next.streaming.StreamingProviderDescriptor descriptor = selectable.get(which);
                    runStreamingPlaylistImport(descriptor.getName(), playlistName, tracks);
                })
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .show();
    }

    private void runStreamingPlaylistImport(
            app.echo.next.streaming.StreamingProviderName provider,
            String playlistName,
            java.util.List<Track> tracks
    ) {
        setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.import.matched.prefix") + "...");
        navigateToNetworkTabPage(NETWORK_STREAMING);
        viewModel.importPlaylistToStreamingJava(provider, playlistName, tracks, summary -> {
            if (summary == null) {
                return;
            }
            String status = AppLanguage.text(settingsStore.languageMode(), "streaming.import.matched.prefix")
                    + summary.getMatchedTracks().size()
                    + " / "
                    + summary.getTotalRequested();
            if (!summary.getUnresolvedTracks().isEmpty()) {
                status = status + " (" + summary.getUnresolvedTracks().size()
                        + AppLanguage.text(settingsStore.languageMode(), "streaming.import.unresolved.suffix") + ")";
            }
            setStatus(status);
        });
    }

    // ---- Import a streaming playlist INTO the local library ----

    private void showImportStreamingPlaylistDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint(AppLanguage.text(settingsStore.languageMode(), "streaming.paste.playlist.link"));
        input.setTextColor(EchoTheme.textArgb(this));
        input.setHintTextColor(EchoTheme.mutedArgb(this));
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        EchoDialog.builder(this)
                .setTitle(AppLanguage.text(settingsStore.languageMode(), "streaming.import.playlist.from"))
                .setView(input)
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .setPositiveButton(AppLanguage.text(settingsStore.languageMode(), "ok"), (dialog, which) ->
                        importStreamingPlaylistFromLink(input.getText().toString()))
                .show();
    }

    private void showStreamingCookieDialog() {
        final app.echo.next.streaming.StreamingProviderName provider =
                viewModel.getStreaming().getValue().getSelectedProvider();
        if (provider == app.echo.next.streaming.StreamingProviderName.MOCK
                || provider == app.echo.next.streaming.StreamingProviderName.M3U8
                || provider == app.echo.next.streaming.StreamingProviderName.PLUGIN) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.choose.login.provider"));
            return;
        }
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("MUSIC_U=...; os=pc; appver=...");
        input.setTextColor(EchoTheme.textArgb(this));
        input.setHintTextColor(EchoTheme.mutedArgb(this));
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        EchoDialog.builder(this)
                .setTitle(AppLanguage.text(settingsStore.languageMode(), "streaming.manual.cookie"))
                .setView(input)
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .setPositiveButton(AppLanguage.text(settingsStore.languageMode(), "ok"), (dialog, which) ->
                        saveStreamingCookie(provider, input.getText().toString()))
                .show();
    }

    private void saveStreamingCookie(
            app.echo.next.streaming.StreamingProviderName provider,
            String cookieHeader
    ) {
        String cookie = cookieHeader == null ? "" : cookieHeader.trim();
        if (cookie.isEmpty()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.cookie.empty"));
            return;
        }
        String callbackUri = STREAMING_AUTH_REDIRECT_URI
                + "?provider=" + provider.getWireName()
                + "&manualCookie=1";
        viewModel.completeStreamingAuth(provider, callbackUri, cookie, loggedInProvider -> {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.cookie.saved"));
            onStreamingLoginSuccess(loggedInProvider);
        });
    }

    private void importStreamingPlaylistFromLink(String linkOrId) {
        app.echo.next.streaming.StreamingProviderName fallback =
                viewModel.getStreaming().getValue().getSelectedProvider();
        app.echo.next.streaming.StreamingPlaylistLinkParser.ParsedPlaylistRef ref =
                app.echo.next.streaming.StreamingPlaylistLinkParser.INSTANCE.parse(linkOrId, fallback);
        if (ref == null) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.playlist.link.invalid"));
            return;
        }
        importStreamingPlaylist(ref.getProvider(), ref.getProviderPlaylistId());
    }

    private void importStreamingPlaylist(
        app.echo.next.streaming.StreamingProviderName provider,
            String providerPlaylistId
    ) {
        setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.resolving"));
        viewModel.importStreamingPlaylistToLocal(provider, providerPlaylistId, result -> {
            if (result == null || result.getEmpty()) {
                setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.playlist.empty"));
                return;
            }
            String status = AppLanguage.text(settingsStore.languageMode(), "streaming.playlist.imported.prefix")
                    + result.getPlaylistName() + " (" + result.getPlaylistAddedCount() + ")";
            setStatus(status);
            showStreamingPlaylistLoadedDialog(status);
            loadCollections();
        });
    }

    private void showStreamingPlaylistLoadedDialog(String message) {
        EchoDialog.builder(this)
                .setTitle(AppLanguage.text(settingsStore.languageMode(), "streaming.playlist.load.success.title"))
                .setMessage(message)
                .setPositiveButton(AppLanguage.text(settingsStore.languageMode(), "ok"), null)
                .show();
    }

    // ---- Pull the user's liked/favorite tracks FROM streaming INTO a local playlist ----

    private void showImportStreamingFavoritesProviderPicker() {
        java.util.List<app.echo.next.streaming.StreamingProviderDescriptor> providers =
                viewModel.getStreaming().getValue().getProviders();
        java.util.List<app.echo.next.streaming.StreamingProviderDescriptor> selectable = new ArrayList<>();
        for (app.echo.next.streaming.StreamingProviderDescriptor descriptor : providers) {
            if (descriptor == null) continue;
            if (descriptor.getName() == app.echo.next.streaming.StreamingProviderName.MOCK) {
                // Skip the offline mock; it has no real account favorites to pull.
                continue;
            }
            selectable.add(descriptor);
        }
        if (selectable.isEmpty()) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.no.providers"));
            return;
        }
        final String[] labels = new String[selectable.size()];
        for (int i = 0; i < selectable.size(); i++) {
            labels[i] = selectable.get(i).getDisplayName();
        }
        EchoDialog.builder(this)
                .setTitle(AppLanguage.text(settingsStore.languageMode(), "choose.streaming.provider"))
                .setItems(labels, (dialog, which) ->
                        importStreamingLikedTracks(selectable.get(which).getName()))
                .setNegativeButton(AppLanguage.text(settingsStore.languageMode(), "cancel"), null)
                .show();
    }

    private void importStreamingLikedTracks(app.echo.next.streaming.StreamingProviderName provider) {
        if (provider == null) {
            return;
        }
        // Resolve a friendly provider display name for the playlist title.
        String displayName = provider.getWireName();
        for (app.echo.next.streaming.StreamingProviderDescriptor desc : viewModel.getStreaming().getValue().getProviders()) {
            if (desc.getName() == provider) {
                displayName = desc.getDisplayName();
                break;
            }
        }
        final String languageMode = settingsStore.languageMode();
        final String playlistName =
                AppLanguage.text(languageMode, "streaming.liked.playlist.prefix")
                        + displayName
                        + AppLanguage.text(languageMode, "streaming.liked.playlist.suffix");

        setStatus(AppLanguage.text(languageMode, "streaming.resolving"));
        navigateToNetworkTabPage(NETWORK_STREAMING);
        viewModel.importStreamingLikedTracksToLocal(provider, playlistName, result -> {
            if (result == null || result.getEmpty()) {
                setStatus(AppLanguage.text(languageMode, "streaming.liked.empty"));
                return;
            }
            String status = AppLanguage.text(languageMode, "streaming.liked.imported.prefix")
                    + result.getPlaylistName() + " (" + result.getPlaylistAddedCount() + ")";
            setStatus(status);
            showStreamingPlaylistLoadedDialog(status);
            loadCollections();
        });
    }

    /**
     * Fetches NetEase 每日推荐 (daily recommendations) and plays the returned list immediately. The
     * tracks are streaming placeholders, so {@link #playTrackList} resolves each playback URL lazily
     * and the next track is pre-resolved by the existing prefetch path.
     */
    private void playStreamingDailyRecommendations(app.echo.next.streaming.StreamingProviderName provider) {
        provider = recommendationProvider(provider);
        if (provider == null) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.recommend.daily.empty"));
            return;
        }
        stopHeartbeatRecommendationMode();
        final String languageMode = settingsStore.languageMode();
        setStatus(AppLanguage.text(languageMode, "streaming.recommend.daily.loading"));
        viewModel.fetchDailyRecommendations(provider, streamingTracks ->
                openRecommendationPlaylist(
                        streamingTracks,
                        AppLanguage.text(languageMode, "streaming.recommend.daily.empty"),
                        AppLanguage.text(languageMode, "streaming.recommend.daily")
                ));
    }

    /**
     * Fetches NetEase 心动推荐 (heartbeat / intelligence list, seeded from liked songs) and plays the
     * returned list immediately.
     */
    private void playStreamingHeartbeatRecommendations(app.echo.next.streaming.StreamingProviderName provider) {
        provider = recommendationProvider(provider);
        if (provider == null) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.recommend.heartbeat.empty"));
            return;
        }
        final String languageMode = settingsStore.languageMode();
        setStatus(AppLanguage.text(languageMode, "streaming.recommend.heartbeat.loading"));
        heartbeatRecommendationUseCase.startLoading(provider);
        List<Track> seedCandidates = heartbeatSeedCandidates();
        String seedTrackId = streamingProviderTrackIdFromCandidates(seedCandidates, provider);
        String playlistId = seedTrackId;
        if (seedTrackId == null || seedTrackId.isEmpty()) {
            resolveHeartbeatSeedFromQueue(provider, seedCandidates, languageMode);
            return;
        }
        fetchHeartbeatRecommendations(provider, seedTrackId, playlistId, languageMode);
    }

    private void fetchHeartbeatRecommendations(
            app.echo.next.streaming.StreamingProviderName provider,
            String seedTrackId,
            String playlistId,
            String languageMode
    ) {
        viewModel.fetchHeartbeatRecommendations(provider, seedTrackId, playlistId, streamingTracks ->
                playHeartbeatRecommendationTracks(
                        streamingTracks,
                        AppLanguage.text(languageMode, "streaming.recommend.heartbeat.result.empty"),
                        AppLanguage.text(languageMode, "streaming.recommend.heartbeat.playing")
                ));
    }

    private app.echo.next.streaming.StreamingProviderName recommendationProvider(
            app.echo.next.streaming.StreamingProviderName requested
    ) {
        if (requested == app.echo.next.streaming.StreamingProviderName.NETEASE) {
            return requested;
        }
        for (app.echo.next.streaming.StreamingProviderDescriptor provider : viewModel.getStreaming().getValue().getProviders()) {
            if (provider.getName() == app.echo.next.streaming.StreamingProviderName.NETEASE) {
                return app.echo.next.streaming.StreamingProviderName.NETEASE;
            }
        }
        return app.echo.next.streaming.StreamingProviderName.NETEASE;
    }

    private List<Track> heartbeatSeedCandidates() {
        PlaybackStateSnapshot serviceSnapshot = playbackService == null ? null : playbackService.snapshot();
        List<Track> serviceQueue = playbackService == null ? null : playbackService.queueSnapshot();
        PlaybackStateSnapshot storeSnapshot = playbackStore == null ? null : playbackStore.snapshot();
        List<Track> viewModelQueue = viewModel == null || viewModel.getPlayback().getValue() == null
                ? null
                : viewModel.getPlayback().getValue().getQueue();
        return streamingTrackMatchUseCase.heartbeatSeedCandidates(
                serviceSnapshot,
                serviceQueue,
                storeSnapshot,
                viewModelQueue
        );
    }

    private String streamingProviderTrackIdFromCandidates(
            List<Track> candidates,
            app.echo.next.streaming.StreamingProviderName provider
    ) {
        return streamingTrackMatchUseCase.providerTrackIdFromCandidates(candidates, provider);
    }

    private String currentStreamingProviderTrackId(app.echo.next.streaming.StreamingProviderName provider) {
        return streamingProviderTrackIdFromCandidates(heartbeatSeedCandidates(), provider);
    }

    private void resolveHeartbeatSeedFromQueue(
            app.echo.next.streaming.StreamingProviderName provider,
            List<Track> candidates,
            String languageMode
    ) {
        if (candidates == null || candidates.isEmpty()) {
            logHeartbeatSeedMiss(provider, snapshotQueueForHeartbeat());
            heartbeatRecommendationUseCase.markLoadingFinished();
            setStatus(AppLanguage.text(languageMode, "streaming.recommend.heartbeat.empty"));
            return;
        }
        viewModel.resolveHeartbeatRecommendationSeed(provider, candidates, resolvedTrackId -> {
            if (!heartbeatRecommendationUseCase.canContinueLoading(provider)) {
                return;
            }
            if (resolvedTrackId != null && !resolvedTrackId.isEmpty()) {
                fetchHeartbeatRecommendations(provider, resolvedTrackId, resolvedTrackId, languageMode);
                return;
            }
            logHeartbeatSeedMiss(provider, snapshotQueueForHeartbeat());
            heartbeatRecommendationUseCase.markLoadingFinished();
            setStatus(AppLanguage.text(languageMode, "streaming.recommend.heartbeat.empty"));
        });
    }

    private List<Track> snapshotQueueForHeartbeat() {
        List<Track> serviceQueue = playbackService == null ? null : playbackService.queueSnapshot();
        List<Track> viewModelQueue = viewModel == null || viewModel.getPlayback().getValue() == null
                ? null
                : viewModel.getPlayback().getValue().getQueue();
        PlaybackStateSnapshot storeSnapshot = playbackStore == null ? null : playbackStore.snapshot();
        return streamingTrackMatchUseCase.snapshotQueueForHeartbeat(
                serviceQueue,
                viewModelQueue,
                storeSnapshot
        );
    }

    private String streamingProviderTrackId(Track track, app.echo.next.streaming.StreamingProviderName provider) {
        return streamingTrackMatchUseCase.directProviderTrackId(track, provider);
    }

    private void logHeartbeatSeedMiss(
            app.echo.next.streaming.StreamingProviderName provider,
            List<Track> queue
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Heartbeat seed missing provider=").append(provider == null ? "null" : provider.getWireName());
        PlaybackStateSnapshot snapshot = playbackService == null ? playbackStore.snapshot() : playbackService.snapshot();
        PlaybackStateSnapshot storeSnapshot = playbackStore == null ? null : playbackStore.snapshot();
        builder.append(", currentIndex=").append(snapshot == null ? -1 : snapshot.currentIndex);
        builder.append(", queueSize=").append(queue == null ? 0 : queue.size());
        if (snapshot != null && snapshot.currentTrack != null) {
            builder.append(", snapshotDataPath=").append(snapshot.currentTrack.dataPath);
            builder.append(", snapshotTitle=").append(snapshot.currentTrack.title);
        }
        if (storeSnapshot != null && storeSnapshot.currentTrack != null && storeSnapshot != snapshot) {
            builder.append(", storeDataPath=").append(storeSnapshot.currentTrack.dataPath);
            builder.append(", storeTitle=").append(storeSnapshot.currentTrack.title);
        }
        if (queue != null) {
            int limit = Math.min(queue.size(), 5);
            for (int i = 0; i < limit; i++) {
                Track track = queue.get(i);
                builder.append(", q").append(i).append("=");
                if (track == null) {
                    builder.append("null");
                } else {
                    builder.append(track.dataPath).append("|").append(track.title).append("|").append(track.artist);
                }
            }
        }
        Log.w(TAG, builder.toString());
    }

    private String currentStreamingProviderPlaylistId(app.echo.next.streaming.StreamingProviderName provider) {
        return currentStreamingProviderTrackId(provider);
    }

    /**
     * Shared tail for the recommendation entry points: maps streaming tracks to placeholder
     * {@link Track}s and starts playback from the top, or shows {@code emptyStatus} when nothing came
     * back. Runs on the main thread (delivered from the ViewModel callback).
     */
    private void openRecommendationPlaylist(
            java.util.List<app.echo.next.streaming.StreamingTrack> streamingTracks,
            String emptyStatus,
            String title
    ) {
        if (streamingTracks == null || streamingTracks.isEmpty()) {
            setStatus(emptyStatus);
            return;
        }
        ArrayList<Track> placeholders = placeholderTracksFor(streamingTracks);
        if (placeholders.isEmpty()) {
            setStatus(emptyStatus);
            return;
        }
        libraryStore.showRecommendationStreamList(title, placeholders);
        setStatus(title + " (" + placeholders.size() + ")");
        navigateToNetworkTabPage(NETWORK_STREAM_LIST);
        renderSelectedTab();
    }

    private void playHeartbeatRecommendationTracks(
            java.util.List<app.echo.next.streaming.StreamingTrack> streamingTracks,
            String emptyStatus,
            String playingStatus
    ) {
        ArrayList<Track> placeholders = heartbeatRecommendationUseCase.playlistPlaceholders(streamingTracks);
        if (placeholders.isEmpty()) {
            setStatus(emptyStatus);
            return;
        }
        setStatus(playingStatus + " (" + placeholders.size() + ")");
        ensurePlaybackServiceStarted();
        playHeartbeatRecommendationTrackList(placeholders, 0);
    }

    private ArrayList<Track> placeholderTracksFor(
            java.util.List<app.echo.next.streaming.StreamingTrack> streamingTracks
    ) {
        ArrayList<Track> placeholders = new ArrayList<>();
        if (streamingTracks == null) {
            return placeholders;
        }
        for (app.echo.next.streaming.StreamingTrack streamingTrack : streamingTracks) {
            if (streamingTrack == null) {
                continue;
            }
            placeholders.add(app.echo.next.streaming.StreamingPlaybackAdapter.INSTANCE.placeholderTrack(streamingTrack));
        }
        return placeholders;
    }

    private ArrayList<Track> heartbeatPlaceholderTracksFor(
            java.util.List<app.echo.next.streaming.StreamingTrack> streamingTracks
    ) {
        return heartbeatRecommendationUseCase.appendPlaceholders(streamingTracks);
    }

    private void onStreamingLoginSuccess(app.echo.next.streaming.StreamingProviderName provider) {
        // Build the local playlist name, for example "My NetEase Playlist".
        String displayName = provider.getWireName();
        for (app.echo.next.streaming.StreamingProviderDescriptor desc : viewModel.getStreaming().getValue().getProviders()) {
            if (desc.getName() == provider) {
                displayName = desc.getDisplayName();
                break;
            }
        }
        String languageMode = settingsStore.languageMode();
        String prefix = AppLanguage.text(languageMode, "streaming.my.playlist.prefix");
        String suffix = AppLanguage.text(languageMode, "streaming.my.playlist.suffix");
        final String playlistName = prefix + displayName + suffix;

        viewModel.ensureStreamingLoginPlaylist(playlistName, provider, result -> {
            setStatus(AppLanguage.text(languageMode, "streaming.playlist.created") + ": " + playlistName);
            if (result != null && result.getPlaylistId() >= 0L) {
                routeController.setSelectedPlaylistId(result.getPlaylistId());
            }
            loadCollections();
        });
    }

    private void syncStreamingPlaylists(java.util.List<app.echo.next.streaming.StreamingPlaylistSyncStore.LinkedPlaylist> links) {
        for (app.echo.next.streaming.StreamingPlaylistSyncStore.LinkedPlaylist link : links) {
            syncOneStreamingPlaylist(link);
        }
    }

    private void syncOneStreamingPlaylist(app.echo.next.streaming.StreamingPlaylistSyncStore.LinkedPlaylist link) {
        viewModel.syncStreamingPlaylistToLocal(link, result -> {
            if (result == null || result.getEmpty()) {
                setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.playlist.empty"));
                return;
            }
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.sync.complete") + " (" + result.getSyncedCount() + ")");
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
        if (uiShellController == null || playbackStore == null || libraryStore == null) {
            return;
        }
        NowPlayingUiState state = publishNowPlayingState(playbackStore.snapshot());
        uiShellController.updateNowBar(state.getOverlayState());
        uiShellController.updateNowPlayingOverlay(state);
    }

    private void openNowPlayingOverlay() {
        if (uiShellController == null || playbackStore == null || libraryStore == null) {
            return;
        }
        PlaybackStateSnapshot snapshot = playbackStore.snapshot();
        if (snapshot == null || !snapshot.hasTrack()) {
            return;
        }
        ensureLyricsLoaded(snapshot.currentTrack);
        NowPlayingUiState state = publishNowPlayingState(snapshot);
        uiShellController.showNowPlayingOverlay(state);
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
        return nowPlayingViewModel.getUiState().getValue();
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
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.resolving"));
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
        heartbeatRecommendationUseCase.stop();
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
        PlaybackStateSnapshot snapshot = playbackService == null
                ? playbackStore.snapshot()
                : playbackService.snapshot();
        if (snapshot != null
                && app.echo.next.streaming.StreamingPlaybackAdapter.INSTANCE.isUnresolvedStreamingTrack(snapshot.currentTrack)) {
            if (resolveCurrentStreamingQueueTrackIfNeeded()) {
                return;
            }
        }
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
        PlaybackStateSnapshot snapshot = playbackService.snapshot();
        if (snapshot == null
                || !app.echo.next.streaming.StreamingPlaybackAdapter.INSTANCE.isUnresolvedStreamingTrack(snapshot.currentTrack)) {
            return false;
        }
        List<Track> queue = playbackService.queueSnapshot();
        if (queue == null || queue.isEmpty()) {
            return resolveAndPlayStreamingTrack(Collections.singletonList(snapshot.currentTrack), 0);
        }
        int safeIndex = Math.max(0, Math.min(snapshot.currentIndex, queue.size() - 1));
        return resolveAndPlayStreamingTrack(queue, safeIndex);
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
        HeartbeatRefillRequest refill = heartbeatRecommendationUseCase.prepareRefill(snapshot);
        if (refill == null) {
            return;
        }
        final app.echo.next.streaming.StreamingProviderName provider = refill.getProvider();
        String seedTrackId = currentStreamingProviderTrackId(provider);
        String playlistId = currentStreamingProviderPlaylistId(provider);
        if (seedTrackId == null || seedTrackId.isEmpty()) {
            stopHeartbeatRecommendationMode();
            return;
        }
        viewModel.fetchHeartbeatRecommendations(provider, seedTrackId, playlistId, streamingTracks -> {
            if (!heartbeatRecommendationUseCase.accepts(provider) || playbackService == null) {
                heartbeatRecommendationUseCase.markLoadingFinished(provider);
                return;
            }
            ArrayList<Track> placeholders = heartbeatPlaceholderTracksFor(streamingTracks);
            if (placeholders.isEmpty()) {
                placeholders = placeholderTracksFor(streamingTracks);
            }
            if (placeholders.isEmpty()) {
                return;
            }
            nowPlayingViewModel.appendToQueue(placeholders);
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.recommend.heartbeat.playing")
                    + " (+" + placeholders.size() + ")");
        });
    }

    /**
     * Chooses the selected streaming quality. Auto mode still adapts to the active network.
     */
    private app.echo.next.streaming.StreamingAudioQuality adaptiveStreamingQuality() {
        return StreamingQualityPreference.playbackQuality(
                this,
                settingsStore == null ? null : settingsStore.streamingAudioQuality()
        );
    }

    private void recoverStreamingBuffering(PlaybackStateSnapshot snapshot) {
        if (playbackService == null || snapshot == null) {
            return;
        }
        app.echo.next.streaming.StreamingAudioQuality selectedQuality = selectedStreamingQuality();
        app.echo.next.streaming.StreamingAudioQuality adaptiveQuality = adaptiveStreamingQuality();
        app.echo.next.streaming.StreamingAudioQuality recoveryQuality = viewModel.recoverStreamingBuffering(
                snapshot,
                selectedQuality,
                adaptiveQuality,
                resolved -> {
                    if (resolved == null) {
                        return;
                    }
                    nowPlayingViewModel.replaceCurrentTrackAndResume(resolved.getTrack(), resolved.getPositionMs());
                    setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.quality.downgraded")
                            + SettingsPageRenderController.streamingQualityLabel(
                            StreamingQualityPreference.valueFor(resolved.getQuality()),
                            settingsStore.languageMode()
                    ));
                }
        );
        if (recoveryQuality == null) {
            return;
        }
        setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.quality.downgrading")
                + SettingsPageRenderController.streamingQualityLabel(
                StreamingQualityPreference.valueFor(recoveryQuality),
                settingsStore.languageMode()
        ));
    }

    private app.echo.next.streaming.StreamingAudioQuality selectedStreamingQuality() {
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
                        setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.resolve.failed"));
                        return;
                    }
                    applyPlaybackActionResult(
                            nowPlayingViewModel.playTrackList(resolved.getTracks(), resolved.getIndex())
                    );
                }
        );
        if (scheduled) {
            setStatus(AppLanguage.text(settingsStore.languageMode(), "streaming.resolving"));
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

    private final class ActivityLibraryGateway implements LibraryGateway {
        @Override
        public void playTrackList(List<Track> tracks, int index) {
            MainActivity.this.playTrackList(tracks, index);
        }

        @Override
        public void showStatusKey(String key) {
            MainActivity.this.setStatus(AppLanguage.text(settingsStore.languageMode(), key));
        }

        @Override
        public void applyFavorite(long trackId, boolean favorite) {
            libraryStore.setFavorite(trackId, favorite);
            publishLibraryState();
            renderNowBar();
            renderSelectedTab();
        }

        @Override
        public void addToPlaylist(Track track) {
            MainActivity.this.showAddToPlaylistDialog(track);
        }

        @Override
        public void changeGroupMode(String mode) {
            routeController.setLibraryMode(mode);
            routeController.clearLibraryGroup();
            if (!LIBRARY_PLAYLISTS.equals(mode)) {
                routeController.setSelectedPlaylistId(-1L);
            }
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
            openAudioFilePicker();
        }

        @Override
        public void scanLibrary() {
            loadLibrary(false);
        }
    }

    private final class ActivityStreamingActionGateway implements MainActivityStreamingActionGateway {
        @Override
        public app.echo.next.streaming.StreamingAudioQuality streamingPlaybackQuality() {
            return MainActivity.this.adaptiveStreamingQuality();
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
            ArrayList<Track> tracks = new ArrayList<>();
            tracks.add(track);
            MainActivity.this.playTrackList(tracks, 0);
        }

        @Override
        public void onStreamingLoginSuccess(app.echo.next.streaming.StreamingProviderName provider) {
            MainActivity.this.onStreamingLoginSuccess(provider);
        }
    }

    private final class ActivityStreamingPlaybackTaskQueue implements StreamingPlaybackTaskQueue {
        @Override
        public void scheduleCurrentPlaybackRecovery(StreamingPlaybackTask task) {
            if (task == null) {
                return;
            }
            streamingPlaybackTaskScheduler.schedule(
                    StreamingPlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                    completion -> task.run(completion::complete)
            );
        }

        @Override
        public void scheduleCurrentUrlResolve(StreamingPlaybackTask task) {
            if (task == null) {
                return;
            }
            streamingPlaybackTaskScheduler.schedule(
                    StreamingPlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE,
                    completion -> task.run(completion::complete)
            );
        }

        @Override
        public void scheduleNextUrlResolve(StreamingPlaybackTask task) {
            if (task == null) {
                return;
            }
            streamingPlaybackTaskScheduler.schedule(
                    StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE,
                    completion -> task.run(completion::complete)
            );
        }
    }

    private final class ActivityNowPlayingGateway implements NowPlayingGateway {
        @Override
        public void playPause() {
            MainActivity.this.togglePlayback();
        }

        @Override
        public void next() {
            MainActivity.this.skipToNext();
        }

        @Override
        public void previous() {
            MainActivity.this.skipToPrevious();
        }

        @Override
        public void seekTo(long positionMs) {
            nowPlayingViewModel.seekTo(positionMs);
        }

        @Override
        public void toggleFavorite() {
            Track track = playbackStore == null ? null : playbackStore.snapshot().currentTrack;
            if (track != null) {
                handleLibraryEvent(new LibraryEvent.ToggleFavorite(track));
            }
        }

        @Override
        public void toggleShuffle() {
            MainActivity.this.toggleShuffle();
        }

        @Override
        public void cycleRepeatMode() {
            MainActivity.this.cycleRepeat();
        }

        @Override
        public String statusMessage(String key) {
            return AppLanguage.text(
                    settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                    key
            );
        }
    }

    private final class ActivityNowPlayingPlaybackGateway implements NowPlayingPlaybackGateway {
        @Override
        public boolean serviceConnected() {
            return playbackService != null;
        }

        @Override
        public void startPlaybackService(String action) {
            Intent intent = new Intent(MainActivity.this, EchoPlaybackService.class);
            if (action != null) {
                intent.setAction(action);
            }
            MainActivity.this.startService(intent);
        }

        @Override
        public PlaybackStateSnapshot snapshot() {
            return playbackService == null ? null : playbackService.snapshot();
        }

        @Override
        public List<Track> queueSnapshot() {
            return playbackService == null ? Collections.emptyList() : playbackService.queueSnapshot();
        }

        @Override
        public void skipToPrevious() {
            if (playbackService != null) {
                playbackService.skipToPrevious();
            }
        }

        @Override
        public void skipToNext() {
            if (playbackService != null) {
                playbackService.skipToNext();
            }
        }

        @Override
        public void seekTo(long positionMs) {
            if (playbackService != null) {
                playbackService.seekTo(positionMs);
            }
        }

        @Override
        public void removeTracksById(Set<Long> trackIds) {
            if (playbackService != null) {
                playbackService.removeTracksById(trackIds);
            }
        }

        @Override
        public void clearQueue() {
            if (playbackService != null) {
                playbackService.clearQueue();
            }
        }

        @Override
        public void replaceQueuedTrack(Track updated) {
            if (playbackService != null) {
                playbackService.replaceQueuedTrack(updated);
            }
        }

        @Override
        public void replaceQueuedTrackById(long oldTrackId, Track updated) {
            if (playbackService != null) {
                playbackService.replaceQueuedTrackById(oldTrackId, updated);
            }
        }

        @Override
        public void retainTracksById(Set<Long> trackIds) {
            if (playbackService != null) {
                playbackService.retainTracksById(trackIds);
            }
        }

        @Override
        public void precacheTrack(Track track) {
            if (playbackService != null) {
                playbackService.precacheTrack(track);
            }
        }

        @Override
        public void appendToQueue(List<Track> tracks) {
            if (playbackService != null) {
                playbackService.appendToQueue(tracks);
            }
        }

        @Override
        public void replaceCurrentTrackAndResume(Track track, long positionMs) {
            if (playbackService != null) {
                playbackService.replaceCurrentTrackAndResume(track, positionMs);
            }
        }

        @Override
        public void startSleepTimerMinutes(int minutes) {
            if (playbackService != null) {
                playbackService.startSleepTimerMinutes(minutes);
            }
        }

        @Override
        public void cancelSleepTimer() {
            if (playbackService != null) {
                playbackService.cancelSleepTimer();
            }
        }

        @Override
        public void playQueue(List<Track> tracks, int index) {
            if (playbackService != null) {
                playbackService.playQueue(new ArrayList<>(tracks), index);
            }
        }

        @Override
        public void pause() {
            if (playbackService != null) {
                playbackService.pause();
            }
        }

        @Override
        public void play() {
            if (playbackService != null) {
                playbackService.play();
            }
        }

        @Override
        public void setShuffleEnabled(boolean enabled) {
            if (playbackService != null) {
                playbackService.setShuffleEnabled(enabled);
            }
        }

        @Override
        public void cycleRepeatMode() {
            if (playbackService != null) {
                playbackService.cycleRepeatMode();
            }
        }

        @Override
        public void setRepeatMode(int repeatMode) {
            if (playbackService != null) {
                playbackService.setRepeatMode(repeatMode);
            }
        }
    }

    private final class ActivitySettingsGateway implements SettingsGateway {
        @Override
        public void navigateSettingsPage(String page) {
            MainActivity.this.navigateSettingsPage(page);
        }

        @Override
        public void openNetworkSources() {
            MainActivity.this.navigateToNetworkTabPage(NETWORK_HOME);
        }

        @Override
        public void loadLibrary() {
            MainActivity.this.loadLibrary(false);
        }

        @Override
        public void openAudioFilePicker() {
            MainActivity.this.openAudioFilePicker();
        }

        @Override
        public void openAudioFolderPicker() {
            MainActivity.this.openAudioFolderPicker();
        }

        @Override
        public void setOnlineLyricsEnabled(boolean enabled) {
            MainActivity.this.setOnlineLyricsEnabled(enabled);
        }

        @Override
        public void reloadCurrentLyrics() {
            MainActivity.this.reloadCurrentLyrics();
        }

        @Override
        public void applyLyricsOffset(long offsetMs) {
            MainActivity.this.applyLyricsOffset(offsetMs);
        }

        @Override
        public void startSleepTimer(int minutes) {
            MainActivity.this.startSleepTimer(minutes);
        }

        @Override
        public void cancelSleepTimer() {
            MainActivity.this.cancelSleepTimer();
        }

        @Override
        public void applyPlaybackSpeed(float speed) {
            MainActivity.this.applyPlaybackSpeed(speed);
        }

        @Override
        public void applyAppVolume(float volume) {
            MainActivity.this.applyAppVolume(volume);
        }

        @Override
        public void applyStreamingAudioQuality(String quality) {
            MainActivity.this.applyStreamingAudioQuality(quality);
        }

        @Override
        public void setConcurrentPlaybackEnabled(boolean enabled) {
            MainActivity.this.setConcurrentPlaybackEnabled(enabled);
        }

        @Override
        public void applyThemeMode(String mode) {
            MainActivity.this.applyThemeMode(mode);
        }

        @Override
        public void applyAccentMode(String accent) {
            MainActivity.this.applyAccentMode(accent);
        }

        @Override
        public void applyLanguageMode(String languageMode) {
            MainActivity.this.applyLanguageMode(languageMode);
        }

        @Override
        public void applyStreamingGatewayEndpoint(String endpoint) {
            MainActivity.this.applyStreamingGatewayEndpoint(endpoint);
        }
    }
}
