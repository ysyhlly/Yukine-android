package app.yukine;

import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import androidx.activity.ComponentActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.PlaybackStateSnapshot;
import app.yukine.streaming.LuoxueTrackMetadataResolver;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.streaming.cache.StreamingCacheRepository;

/**
 * Activity-scoped owner for streaming assembly and lifecycle.
 *
 * <p>The staged bind methods express real cross-feature dependencies without returning the
 * individual controllers to the Activity. Streaming policy, listeners, auth recovery and
 * teardown remain inside this boundary.</p>
 */
final class StreamingFeatureBinding {
    private static final String TAG = "StreamingFeature";

    private final ComponentActivity activity;
    private final Handler mainHandler;
    private final MainExecutors executors;
    private final MainSettingsStore settingsStore;
    private final StatusMessageController statusMessages;
    private final StreamingCacheRepository cacheRepository;
    private final StreamingPlaybackTaskScheduler playbackTaskScheduler;
    private final ResolveStreamingPlaybackUseCase resolvePlaybackUseCase;
    private final StreamingTrackMatchUseCase trackMatchUseCase;
    private final StreamingLocalPlaylistOperations localPlaylistOperations;
    private final LuoxueTrackMetadataResolver luoxueTrackMetadataResolver;
    private final StreamingViewModel viewModel;
    private final StreamingRecommendationViewModel recommendationViewModel;
    private final StreamingPlaybackQualityPolicy qualityPolicy;
    private final StreamingProviderSettingsOwner providerSettingsOwner;

    private PlaybackFeatureBinding playback;
    private NavigationFeatureBinding navigation;
    private MainLibraryStore libraryStore;
    private LibraryCollectionsOwner libraryCollectionsOwner;
    private LibraryImportOwner libraryImportOwner;
    private LuoxueSourceImportDialogController luoxueSourceImportDialogController;

    private MainActivityStreamingActionGateway actionGateway;
    private StreamingAuthCallbackController authCallbackController;
    private StreamingSearchActionHandler searchActionHandler;
    private StreamingPlaybackController playbackController;
    private StreamingPlaylistController playlistController;
    private StreamingPlaylistDialogController playlistDialogController;
    private StreamingPlaylistImportDialogController playlistImportDialogController;
    private StreamingManualCookieDialogController manualCookieDialogController;
    private StreamingManualCookieController manualCookieController;
    private HeartbeatRecommendationController heartbeatRecommendationController;
    private RecommendationActionCallbacks recommendationActionCallbacks;
    private HeartbeatRecommendationSeedBinder heartbeatSeedBinder;

    StreamingFeatureBinding(
            ComponentActivity activity,
            MainActivityViewModels viewModels,
            Handler mainHandler,
            MainExecutors executors,
            MainSettingsStore settingsStore,
            StatusMessageController statusMessages,
            StreamingGatewaySettingsStore gatewaySettingsStore,
            StreamingCacheRepository cacheRepository,
            StreamingPlaybackTaskScheduler playbackTaskScheduler,
            ResolveStreamingPlaybackUseCase resolvePlaybackUseCase,
            StreamingTrackMatchUseCase trackMatchUseCase,
            StreamingLocalPlaylistOperations localPlaylistOperations,
            LuoxueTrackMetadataResolver luoxueTrackMetadataResolver
    ) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.executors = executors;
        this.settingsStore = settingsStore;
        this.statusMessages = statusMessages;
        this.cacheRepository = cacheRepository;
        this.playbackTaskScheduler = playbackTaskScheduler;
        this.resolvePlaybackUseCase = resolvePlaybackUseCase;
        this.trackMatchUseCase = trackMatchUseCase;
        this.localPlaylistOperations = localPlaylistOperations;
        this.luoxueTrackMetadataResolver = luoxueTrackMetadataResolver;
        this.viewModel = viewModels.getStreamingViewModel();
        this.recommendationViewModel = viewModels.getStreamingRecommendationViewModel();
        this.qualityPolicy = new StreamingPlaybackQualityPolicy(activity, settingsStore);
        this.providerSettingsOwner = new StreamingProviderSettingsOwner(
                gatewaySettingsStore,
                viewModel,
                recommendationViewModel,
                this::languageMode,
                statusMessages::setStatus
        );
        viewModel.bindStreamingPlaybackCoordinator(resolvePlaybackUseCase, playbackTaskScheduler);
        providerSettingsOwner.configureAndRefresh();
    }

    StreamingViewModel viewModel() {
        return viewModel;
    }

    StreamingPlaybackQuality qualityPolicy() {
        return qualityPolicy;
    }

    MainActivityStreamingActionGateway actionGateway() {
        return actionGateway;
    }

    StreamingSearchActionHandler searchActionHandler() {
        return searchActionHandler;
    }

    void bindPlayback(
            PlaybackFeatureBinding playback,
            NavigationFeatureBinding navigation,
            MainLibraryStore libraryStore,
            LyricsViewModel lyricsViewModel,
            QueueClearQueueConfirmer clearQueueConfirmer
    ) {
        this.playback = playback;
        this.navigation = navigation;
        this.libraryStore = libraryStore;
        actionGateway = new MainStreamingActionGateway(
                qualityPolicy::adaptive,
                this::languageMode,
                launch -> StreamingAuthLauncher.INSTANCE.launch(activity, launch),
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                provider -> {
                    if (playlistController != null) {
                        playlistController.onStreamingLoginSuccess(provider);
                    }
                },
                viewModel::selectStreamingProvider,
                () -> {
                    if (manualCookieController != null) {
                        manualCookieController.showStreamingCookieDialog();
                    }
                }
        );
        authCallbackController = new StreamingAuthCallbackController(viewModel, actionGateway);
        heartbeatSeedBinder = new HeartbeatRecommendationSeedBinder(
                playback::snapshot,
                playback::queueSnapshot,
                playback::snapshot,
                () -> playback.getPlaybackViewModel().getPlayback().getValue() == null
                        ? Collections.emptyList()
                        : playback.getPlaybackViewModel().getPlayback().getValue().getQueue(),
                this::heartbeatLibraryContextTracks
        );
        recommendationActionCallbacks = new MainRecommendationActionCallbacks(
                statusMessages::setStatus,
                presentation -> playback.getPlaybackStartController().playRecommendation(presentation),
                provider -> heartbeatSeedBinder.request(provider),
                presentation -> playback.getPlaybackStartController().playHeartbeatRecommendation(presentation),
                this::logHeartbeatSeedMiss
        );
        heartbeatRecommendationController = new HeartbeatRecommendationController(
                recommendationViewModel,
                this::languageMode,
                new MainHeartbeatRecommendationListener(
                        playback::isConnected,
                        provider -> heartbeatSeedBinder.request(provider),
                        recommendationViewModel::stopHeartbeatRecommendationMode,
                        presentation -> playback.getNowPlayingViewModel().appendToQueue(presentation.getTracks()),
                        presentation -> playback.getPlaybackStartController().playHeartbeatRecommendation(presentation),
                        this::logHeartbeatSeedMiss,
                        statusMessages::setStatus
                )
        );
        playbackController = new StreamingPlaybackController(
                viewModel,
                playback.getNowPlayingViewModel(),
                new MainStreamingPlaybackListener(
                        this::languageMode,
                        qualityPolicy::adaptive,
                        qualityPolicy::selected,
                        settingsStore::refuseAutomaticQualityDowngrade,
                        new StreamingQueueReadSource() {
                            @Override
                            public List<Track> queueSnapshot() {
                                return playback.queueSnapshot();
                            }

                            @Override
                            public int queueSize() {
                                return playback.getConnection().queueSize();
                            }

                            @Override
                            public Track queueTrackAt(int index) {
                                return playback.getConnection().queueTrackAt(index);
                            }
                        },
                        snapshot -> heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot),
                        playback::applyActionResult,
                        statusMessages::setStatus
                )
        );
        playback.bindActions(
                playbackController::resolveAndPlayStreamingTrack,
                this::resolveCurrentQueueTrackIfNeeded,
                () -> libraryStore == null ? Collections.emptyList() : libraryStore.visibleTracks(),
                recommendationViewModel::stopHeartbeatRecommendationMode,
                () -> viewModel.prepareStreamingPlaybackStatusText(languageMode(), null).getResolving(),
                () -> navigation.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                clearQueueConfirmer,
                lyricsViewModel,
                track -> viewModel.streamingProviderTrackIdFor(track, StreamingProviderName.NETEASE)
        );
        bindStores();
    }

    void bindDialogs(
            LibraryCollectionsOwner libraryCollectionsOwner,
            LibraryImportOwner libraryImportOwner,
            LuoxueSourceImportDialogController luoxueSourceImportDialogController
    ) {
        this.libraryCollectionsOwner = libraryCollectionsOwner;
        this.libraryImportOwner = libraryImportOwner;
        this.luoxueSourceImportDialogController = luoxueSourceImportDialogController;
        playlistDialogController = new StreamingPlaylistDialogController(
                activity,
                viewModel,
                new StreamingPlaylistDialogController.LanguageProvider() {
                    @Override
                    public String languageMode() {
                        return StreamingFeatureBinding.this.languageMode();
                    }

                    @Override
                    public String text(String key) {
                        return AppLanguage.text(languageMode(), key);
                    }
                },
                new MainStreamingPlaylistDialogListener(
                        statusMessages::setStatus,
                        (provider, playlistName, tracks) ->
                                playlistController.runStreamingPlaylistImport(provider, playlistName, tracks),
                        (provider, playlists) ->
                                playlistController.importSelectedAccountPlaylists(provider, playlists),
                        provider -> playlistController.importStreamingLikedTracks(provider)
                )
        );
        playlistController = new StreamingPlaylistController(
                viewModel,
                this::languageMode,
                new MainStreamingPlaylistListener(
                        navigation::selectedPlaylistId,
                        playlistId -> navigation.getRouteController().setSelectedPlaylistId(playlistId),
                        libraryCollectionsOwner::load,
                        () -> libraryImportOwner.loadLibrary(true),
                        this::selectedPlaylistName,
                        libraryStore::selectedPlaylistTracks,
                        libraryStore::favoriteTracks,
                        () -> viewModel.getStreaming().getValue().getSelectedProvider(),
                        playlistDialogController::showStreamingProviderPicker,
                        () -> navigation.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING),
                        playlistDialogController::showStreamingPlaylistLoadedDialog,
                        playlistDialogController::showAccountPlaylistImportPicker,
                        statusMessages::setStatus
                )
        );
        playlistImportDialogController = new StreamingPlaylistImportDialogController(
                activity,
                viewModel,
                new StreamingPlaylistImportDialogController.LanguageProvider() {
                    @Override
                    public String languageMode() {
                        return StreamingFeatureBinding.this.languageMode();
                    }

                    @Override
                    public String text(String key) {
                        return AppLanguage.text(languageMode(), key);
                    }
                },
                new MainStreamingPlaylistImportDialogListener(
                        () -> viewModel.getStreaming().getValue().getSelectedProvider(),
                        luoxueSourceImportDialogController::showImportDialog,
                        playlistController::importStreamingPlaylistFromLink
                )
        );
        manualCookieDialogController = new StreamingManualCookieDialogController(
                activity,
                key -> AppLanguage.text(languageMode(), key),
                (provider, cookieHeader) -> manualCookieController.saveStreamingCookie(provider, cookieHeader)
        );
        manualCookieController = new StreamingManualCookieController(
                viewModel,
                this::languageMode,
                new MainStreamingManualCookieListener(
                        () -> viewModel.getStreaming().getValue().getSelectedProvider(),
                        manualCookieDialogController::show,
                        playlistController::onStreamingLoginSuccess,
                        statusMessages::setStatus
                )
        );
    }

    StreamingSearchRenderController bindSearch(
            NavigationFeatureBinding navigation,
            LuoxueSourceImportDialogController luoxueSourceImportDialogController
    ) {
        searchActionHandler = new DefaultStreamingSearchActionHandler(viewModel, actionGateway);
        return new StreamingSearchRenderController(
                this::languageMode,
                new MainStreamingSearchRenderListener(
                        navigation::handleBack,
                        searchActionHandler,
                        () -> viewModel.getStreaming().getValue().getSelectedProvider(),
                        playlistController::importStreamingPlaylistFromProviderRef,
                        playlistController::showAccountPlaylistSyncPicker,
                        playlistController::importStreamingLikedTracks,
                        this::runRecommendationAction,
                        playlistImportDialogController::showImportDialog,
                        luoxueSourceImportDialogController::showSourceManager,
                        manualCookieController::showStreamingCookieDialog,
                        viewModel::updateStreamingSearchChrome
                )
        );
    }

    void runRecommendationAction(RecommendationAction action) {
        if (recommendationActionCallbacks == null) {
            return;
        }
        recommendationViewModel.onAction(action, languageMode(), recommendationActionCallbacks);
    }

    void importSelectedPlaylistToStreaming() {
        playlistController.importSelectedPlaylistToStreaming();
    }

    void importFavoritesToStreaming() {
        playlistController.importFavoritesToStreaming();
    }

    void showImportStreamingFavoritesProviderPicker() {
        playlistDialogController.showImportStreamingFavoritesProviderPicker();
    }

    void syncSelectedPlaylistFromStreaming() {
        playlistController.syncSelectedPlaylistFromStreaming();
    }

    void applyEndpoint(String endpoint) {
        providerSettingsOwner.applyEndpoint(endpoint);
    }

    void refreshProviders() {
        providerSettingsOwner.refresh();
    }

    void onLuoxueSourcesChanged() {
        executors.io(() -> {
            cacheRepository.clearSearchAndPlaybackForProviderBlocking(StreamingProviderName.LUOXUE);
            mainHandler.post(() -> {
                if (playback != null) {
                    playback.getNowPlayingViewModel().bindLuoxueTrackMetadataResolver(luoxueTrackMetadataResolver);
                }
                providerSettingsOwner.refresh();
            });
        });
    }

    StreamingDownloadResolver downloadResolver() {
        return (request, quality, callback) -> viewModel.resolveStreamingTrackForPlayback(
                request.getProvider(),
                request.getProviderTrackId(),
                request.getMetadata(),
                quality,
                callback::onResolved
        );
    }

    void preResolveNext(PlaybackStateSnapshot snapshot) {
        if (playbackController != null) {
            playbackController.preResolveNextStreamingTrack(snapshot);
        }
    }

    void recoverBuffering(PlaybackStateSnapshot snapshot) {
        if (playbackController != null) {
            playbackController.recoverStreamingBuffering(snapshot);
        }
    }

    boolean resolveCurrentQueueTrackIfNeeded() {
        if (playback == null || playbackController == null || !playback.isConnected()) {
            return false;
        }
        PlaybackStateSnapshot snapshot = playback.snapshot();
        StreamingQueueResolveTarget target = viewModel.prepareCurrentStreamingQueueResolveTarget(
                snapshot,
                playback.queueSnapshot()
        );
        Track currentTrack = snapshot == null ? null : snapshot.currentTrack;
        return target != null
                && currentTrack != null
                && playbackController.resolveAndResumeCurrentStreamingTrack(
                        target.getTracks(),
                        target.getIndex(),
                        currentTrack.id,
                        snapshot.positionMs
                );
    }

    void handleInitialIntent(Intent intent) {
        if (authCallbackController != null) {
            authCallbackController.handleInitialIntent(intent);
        }
    }

    void handleNewIntent(Intent intent) {
        if (authCallbackController != null) {
            authCallbackController.handleNewIntent(intent);
        }
    }

    void onResume() {
        viewModel.maintainStreamingAuthSessions();
    }

    void release() {
        viewModel.bindStreamingPlaybackCoordinator(null, null);
        viewModel.bindStreamingLocalPlaylistOperations(null);
        viewModel.bindStreamingTrackMatchStore(null);
        recommendationViewModel.bindStreamingTrackMatchStore(null);
        playbackTaskScheduler.shutdownNow();
    }

    private void bindStores() {
        viewModel.bindStreamingLocalPlaylistOperations(localPlaylistOperations);
        viewModel.bindStreamingTrackMatchStore(trackMatchUseCase);
        recommendationViewModel.bindStreamingTrackMatchStore(trackMatchUseCase);
        heartbeatSeedBinder.bind(trackMatchUseCase);
    }

    private String selectedPlaylistName() {
        return libraryStore.selectedPlaylistName(navigation.selectedPlaylistId());
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
        if (request != null && request.getSeedMissingMessage() != null
                && !request.getSeedMissingMessage().isEmpty()) {
            Log.w(TAG, request.getSeedMissingMessage());
        }
    }

    private String languageMode() {
        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
    }
}
