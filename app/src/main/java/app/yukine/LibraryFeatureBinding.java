package app.yukine;

import android.os.Handler;

import androidx.activity.ComponentActivity;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.data.RecordingMatchRepository;
import app.yukine.model.Playlist;
import app.yukine.model.Track;
import app.yukine.streaming.StreamingProviderName;

/** Owns Activity-scoped library/search/collections assembly and lifecycle. */
final class LibraryFeatureBinding {
    private final ComponentActivity activity;
    private final NavigationViewModel navigationViewModel;
    private final SettingsViewModel settingsViewModel;
    private final MainSettingsStore settingsStore;
    private final NavigationFeatureBinding navigation;
    private final StatusMessageController statusMessages;
    private final MusicLibraryRepository repository;
    private final ToggleFavoriteUseCase toggleFavoriteUseCase;
    private final LoadPlaylistTracksUseCase loadPlaylistTracksUseCase;
    private final LibraryCollectionGateway collectionGateway;
    private final LibraryImportGateway importGateway;
    private final LibraryDocumentGateway documentGateway;
    private final LibraryPlaylistActionGateway playlistActionGateway;
    private final Handler mainHandler;
    private final MainExecutors executors;
    private final LibraryMultiSourceSyncCoordinator multiSourceSync;
    private final FavoriteSyncRuntimeOwner favoriteSyncRuntime;
    private final FavoriteSyncCoordinator favoriteSyncCoordinator;
    private final RecordingMatchRepository recordingMatchRepository;

    private final SearchViewModel searchViewModel;
    private final LibraryViewModel viewModel;
    private final CollectionsViewModel collectionsViewModel;
    private final HomeDashboardViewModel homeDashboardViewModel;
    private final FavoriteSyncViewModel favoriteSyncViewModel;
    private final LibraryDataStateOwner store;
    private final LibraryCollectionsOwner collectionsOwner;

    private LibraryImportOwner importOwner;
    private LibraryAudioVerificationOwner audioVerificationOwner;
    private LibraryWebDavSyncOwner librarySyncOwner;
    private LibraryDeletionCompletionOwner deletionCompletionOwner;
    private HiddenLibraryRestoreOwner hiddenLibraryRestoreOwner;
    private TrackListStateReducer trackListStateReducer;
    private TrackListStatePublisher trackListStatePublisher;
    private LibraryGroupsStateReducer groupsStateReducer;
    private LibraryPlaylistsStateReducer playlistsStateReducer;
    private LibraryStateBinding libraryStateBinding;
    private CollectionsStateBinding collectionsStateBinding;
    private PlayHistoryActionController playHistoryActionController;
    private UnifiedSearchOwner unifiedSearchOwner;
    private PlaylistMutationOwner playlistMutationOwner;
    private PlaylistDialogController playlistDialogController;
    private ArtistIdentityDialogController artistIdentityDialogController;
    private final RecordingMatchViewModel recordingMatchViewModel;
    private HomeDashboardActionAdapter homeDashboardIntentHandler;

    LibraryFeatureBinding(
            ComponentActivity activity,
            MainActivityViewModels viewModels,
            NavigationViewModel navigationViewModel,
            SettingsViewModel settingsViewModel,
            MainSettingsStore settingsStore,
            NavigationFeatureBinding navigation,
            StatusMessageController statusMessages,
            MusicLibraryRepository repository,
            ToggleFavoriteUseCase toggleFavoriteUseCase,
            LoadPlaylistTracksUseCase loadPlaylistTracksUseCase,
            LibraryCollectionGateway collectionGateway,
            LibraryImportGateway importGateway,
            LibraryDocumentGateway documentGateway,
            LibraryPlaylistActionGateway playlistActionGateway,
            HomeDashboardRepository homeDashboardRepository,
            Handler mainHandler,
            MainExecutors executors,
            RecordingMatchRepository recordingMatchRepository,
            LibraryMultiSourceSyncCoordinator multiSourceSync,
            FavoriteSyncCoordinator favoriteSyncCoordinator
    ) {
        this.activity = activity;
        this.navigationViewModel = navigationViewModel;
        this.settingsViewModel = settingsViewModel;
        this.settingsStore = settingsStore;
        this.navigation = navigation;
        this.statusMessages = statusMessages;
        this.repository = repository;
        this.toggleFavoriteUseCase = toggleFavoriteUseCase;
        this.loadPlaylistTracksUseCase = loadPlaylistTracksUseCase;
        this.collectionGateway = collectionGateway;
        this.importGateway = importGateway;
        this.documentGateway = documentGateway;
        this.playlistActionGateway = playlistActionGateway;
        this.favoriteSyncCoordinator = favoriteSyncCoordinator;
        this.mainHandler = mainHandler;
        this.executors = executors;
        this.recordingMatchRepository = recordingMatchRepository;
        this.recordingMatchViewModel = viewModels.getRecordingMatchViewModel();
        this.recordingMatchViewModel.bindDataSource(recordingMatchRepository);
        this.multiSourceSync = multiSourceSync;
        this.searchViewModel = viewModels.getSearchViewModel();
        this.viewModel = viewModels.getLibraryViewModel();
        this.collectionsViewModel = viewModels.getCollectionsViewModel();
        this.homeDashboardViewModel = viewModels.getHomeDashboardViewModel();
        this.favoriteSyncViewModel = viewModels.getFavoriteSyncViewModel();
        this.homeDashboardViewModel.bindRepository(homeDashboardRepository);
        this.store = viewModel.dataOwner();
        this.store.bindMergeIdentityProvider(multiSourceSync::persistedMergeIdentityFor);
        this.store.bindRecordingIdentitySnapshotProvider(repository::loadTrackRecordingIdentities);
        this.store.bindArtistIdentityProvider(track -> multiSourceSync.artistIdentitiesFor(track).stream()
                .map(identity -> new LibraryArtistGroupIdentity(
                        identity.getArtistId(),
                        identity.getDisplayName().isBlank()
                                ? identity.getCreditedName()
                                : identity.getDisplayName(),
                        identity.getAvatarUrl()
                ))
                .toList());
        this.collectionsOwner = new LibraryCollectionsOwner(
                viewModel,
                navigation.getRouteController(),
                store
        );
        this.favoriteSyncRuntime = new FavoriteSyncRuntimeOwner(
                activity.getLifecycle(),
                favoriteSyncCoordinator,
                viewModels.getFavoriteSyncViewModel(),
                collectionsOwner::load
        );
    }

    LibraryViewModel viewModel() {
        return viewModel;
    }

    LibraryDataStateOwner store() {
        return store;
    }

    LibraryCollectionsOwner collectionsOwner() {
        return collectionsOwner;
    }

    LibraryImportOwner importOwner() {
        return importOwner;
    }

    LibraryDeletionCompletionOwner deletionCompletionOwner() {
        return deletionCompletionOwner;
    }

    PlaylistDialogController playlistDialogController() {
        return playlistDialogController;
    }

    HomeDashboardActionAdapter homeDashboardIntentHandler() {
        return homeDashboardIntentHandler;
    }

    void bindPlaybackStateSources(
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming,
            LyricsViewModel lyricsViewModel
    ) {
        playback.bindStateSources(
                viewModel.getLibrary(),
                store.getFavoriteTrackIds(),
                lyricsViewModel,
                settingsViewModel,
                streaming.viewModel(),
                homeDashboardViewModel,
                homeDashboardIntentHandler
        );
    }

    TrackListStatePublisher trackListStatePublisher() {
        return trackListStatePublisher;
    }

    void bindPlatform(
            MainPermissionController permissionController,
            PlaybackFeatureBinding playback,
            Consumer<Boolean> onboardingScanResult
    ) {
        audioVerificationOwner = new LibraryAudioVerificationOwner(activity, repository, () -> {
            multiSourceSync.refreshIdentitySnapshot();
            List<Track> tracks = repository.loadCachedTracks();
            Set<Long> favoriteIds = repository.loadFavoriteIds();
            mainHandler.post(() -> {
                if (importOwner != null) {
                    importOwner.republishCanonicalLibrary(tracks, favoriteIds);
                }
            });
        });
        importOwner = new LibraryImportOwner(
                viewModel,
                store,
                navigation.getRouteController(),
                permissionController::hasAudioPermission,
                this::languageMode,
                statusMessages::setStatus,
                collectionsOwner::load,
                onboardingScanResult::accept,
                () -> navigation.navigateToNetworkTabPage(NetworkPage.Streaming),
                audioVerificationOwner::schedule
        );
        recordingMatchViewModel.bindIdentityChangedListener(() ->
                executors.io(() -> {
                    try {
                        multiSourceSync.refreshIdentitySnapshot();
                        favoriteSyncCoordinator.reconcileCanonicalState();
                    } catch (RuntimeException ignored) {
                        // Refresh the visible library even if identity reconciliation fails.
                    } finally {
                        mainHandler.post(() -> {
                            navigation.getRouteController().clearLibraryGroup();
                            importOwner.loadLibrary(false);
                            collectionsOwner.load();
                        });
                    }
                })
        );
        deletionCompletionOwner = new LibraryDeletionCompletionOwner(
                playback.getNowPlayingViewModel()::removeQueueTracks,
                () -> viewModel.presentationOwner().onAction(app.yukine.ui.LibraryAction.ClearSelection.INSTANCE),
                this::languageMode,
                statusMessages::setStatus,
                importOwner::loadLibrary
        );
    }

    StreamingSearchStateReducer bindUi(
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming,
            DocumentPickerController documentPickerController,
            LibraryFileDeleteLauncher fileDeleteLauncher,
            DownloadRequestController downloadRequestController,
            MainPermissionController permissionController,
            LuoxueSourceImportDialogController luoxueSourceImportDialogController,
            Consumer<Track> editStream,
            CollectionsActionAdapter.PlayHistoryClearConfirmer confirmClearPlayHistory
    ) {
        playlistMutationOwner = new PlaylistMutationOwner(
                viewModel,
                navigation.getRouteController(),
                this::languageMode,
                statusMessages::setStatus,
                collectionsOwner::load
        );
        playlistDialogController = new PlaylistDialogController(
                activity,
                this::languageMode,
                store::playlists,
                playlistMutationOwner
        );
        artistIdentityDialogController = new ArtistIdentityDialogController(
                activity,
                mainHandler,
                this::languageMode,
                repository,
                multiSourceSync::refreshIdentitySnapshot,
                () -> {
                    navigation.getRouteController().clearLibraryGroup();
                    importOwner.loadLibrary(false);
                },
                statusMessages
        );
        trackListStateReducer = new TrackListStateReducer(
                viewModel,
                new TrackListActionAdapter(
                        (tracks, index) -> viewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                        track -> viewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                        track -> viewModel.onEvent(new LibraryEvent.AddToPlaylist(track)),
                        downloadRequestController::downloadTrack,
                        downloadRequestController::downloadTracks,
                        editStream::accept,
                        track -> fileDeleteLauncher.request(
                                java.util.Collections.singletonList(track),
                                navigation.selectedPlaylistId()
                        ),
                        track -> {
                            navigation.navigateToTab(app.yukine.navigation.LibraryTab.INSTANCE, false);
                            recordingMatchViewModel.open(track.id, languageMode());
                        }
                )
        );
        trackListStatePublisher = new TrackListStatePublisher(
                trackListStateReducer,
                viewModel.getLibrary(),
                viewModel.getFavoritePendingTrackIds(),
                settingsViewModel.getState(),
                playback.readModel()
        );
        searchViewModel.bindStateSources(
                navigationViewModel.getSearchQuery(),
                viewModel.getLibrary(),
                repository::search
        );
        viewModel.bindPlaylistActionGateway(playlistActionGateway);
        playHistoryActionController = new PlayHistoryActionController(
                viewModel,
                this::languageMode,
                store::clearPlayHistory,
                status -> {
                    statusMessages.setStatus(status);
                    return kotlin.Unit.INSTANCE;
                },
                collectionsOwner::load
        );
        StreamingSearchStateReducer streamingSearchStateReducer = streaming.bindSearch(
                navigation,
                luoxueSourceImportDialogController
        );
        unifiedSearchOwner = new UnifiedSearchOwner(
                navigation.getRouteController(),
                searchViewModel,
                streaming.viewModel(),
                viewModel,
                store,
                streaming.searchActionHandler(),
                settingsStore,
                streaming.qualityPolicy(),
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                message -> {
                    statusMessages.showFeedback(message);
                    return kotlin.Unit.INSTANCE;
                },
                message -> {
                    statusMessages.setStatus(message);
                    return kotlin.Unit.INSTANCE;
                }
        );
        searchViewModel.updateActions(unifiedSearchOwner.actions());
        librarySyncOwner = new LibraryWebDavSyncOwner(
                new MusicLibraryWebDavSyncOperations(repository),
                viewModel.presentationOwner(),
                snapshot -> importOwner.replaceLibrary(
                        snapshot.getCached(),
                        snapshot.getFavorites(),
                        snapshot.getStatus()
                ),
                this::languageMode,
                statusMessages::setStatus,
                multiSourceSync,
                () -> importOwner.loadLibrary(false)
        );
        bindGateway(playback, documentPickerController, fileDeleteLauncher, downloadRequestController);
        bindStateOwners(
                playback,
                streaming,
                documentPickerController,
                fileDeleteLauncher,
                downloadRequestController,
                permissionController,
                confirmClearPlayHistory
        );
        librarySyncOwner.initialize();
        hiddenLibraryRestoreOwner = new HiddenLibraryRestoreOwner(
                viewModel,
                () -> importOwner.loadLibrary(true),
                settingsViewModel::refreshSettingsContext
        );
        homeDashboardIntentHandler = new HomeDashboardActionAdapter(
                mode -> {
                    navigation.getRouteController().setLibraryMode(mode);
                    navigation.navigateToTab(app.yukine.navigation.LibraryTab.INSTANCE, true);
                },
                playback::continueDashboardPlayback,
                () -> navigation.navigateToTab(app.yukine.navigation.NowTab.INSTANCE, true),
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                () -> importOwner.loadLibrary(true),
                navigation::openQueueSheet,
                () -> navigation.navigateToNetworkTabPage(NetworkPage.StreamingHub),
                () -> navigation.navigateToTab(app.yukine.navigation.SearchTab.INSTANCE, true),
                () -> streaming.runRecommendationAction(
                        new RecommendationAction.PlayDaily(app.yukine.streaming.StreamingProviderName.NETEASE)
                ),
                () -> streaming.runRecommendationAction(
                        new RecommendationAction.PlayHeartbeat(app.yukine.streaming.StreamingProviderName.NETEASE)
                ),
                playback.getNowPlayingViewModel()::skipToNext
        );
        return streamingSearchStateReducer;
    }

    private void bindGateway(
            PlaybackFeatureBinding playback,
            DocumentPickerController documentPickerController,
            LibraryFileDeleteLauncher fileDeleteLauncher,
            DownloadRequestController downloadRequestController
    ) {
        viewModel.bindFavoriteIdsProvider(store::favoriteIds);
        viewModel.bindGateway(new MainLibraryGateway(
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                this::languageMode,
                statusMessages::setStatus,
                store::setFavorites,
                collectionsOwner::load,
                playlistDialogController::showAddToPlaylist,
                navigation.getRouteController(),
                unifiedSearchOwner::applyCurrentSearch,
                documentPickerController::openAudioFilePicker,
                importOwner::loadLibrary,
                tracks -> fileDeleteLauncher.request(tracks, navigation.selectedPlaylistId()),
                downloadRequestController::downloadTracks,
                () -> librarySyncOwner.syncNow(),
                librarySyncOwner::setAutoSyncEnabled
        ));
    }

    private void bindStateOwners(
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming,
            DocumentPickerController documentPickerController,
            LibraryFileDeleteLauncher fileDeleteLauncher,
            DownloadRequestController downloadRequestController,
            MainPermissionController permissionController,
            CollectionsActionAdapter.PlayHistoryClearConfirmer confirmClearPlayHistory
    ) {
        LibraryPlaylistsIntentOwner playlistIntents = new LibraryPlaylistsIntentOwner(
                viewModel,
                playlist -> {
                    playlistDialogController.confirmDeletePlaylist(playlist);
                    return kotlin.Unit.INSTANCE;
                },
                request -> {
                    trackListStatePublisher.publishLibraryPlaylist(request);
                    return kotlin.Unit.INSTANCE;
                }
        );
        groupsStateReducer = new LibraryGroupsStateReducer(
                viewModel,
                new LibraryGroupsActionAdapter(
                        (key, title) -> viewModel.onEvent(new LibraryEvent.OpenGroup(key, title)),
                        () -> navigation.getRouteController().clearLibraryGroup(),
                        () -> viewModel.onEvent(LibraryEvent.BackFromGroup.INSTANCE),
                        this::languageMode,
                        (tracks, index) -> viewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index)),
                        (title, tracks) -> fileDeleteLauncher.request(tracks, -1L),
                        playlistIntents::publishLibraryGroupsChrome,
                        trackListStatePublisher::publishLibraryGroup,
                        artistIdentityDialogController::show
                ),
                action -> mainHandler.post(action),
                new RoomArtistLocalInfoSource(repository)
        );
        playlistsStateReducer = new LibraryPlaylistsStateReducer(viewModel, playlistIntents);
        LibraryPlaylistSourcesLoader playlistSourcesLoader = playlists -> {
            Map<Long, StreamingProviderName> playlistSources = new HashMap<>();
            for (Playlist playlist : playlists) {
                StreamingProviderName provider = streaming.linkedPlaylistProvider(playlist.id);
                provider = PlaylistSourceResolver.resolve(
                        provider,
                        repository.loadPlaylistTracks(playlist.id)
                );
                if (provider != null) {
                    playlistSources.put(playlist.id, provider);
                }
            }
            return playlistSources;
        };
        collectionsStateBinding = new CollectionsStateBinding(
                collectionsViewModel,
                new CollectionsActionAdapter(
                        playlistDialogController::showCreatePlaylist,
                        documentPickerController::openPlaylistM3uFilePicker,
                        confirmClearPlayHistory,
                        navigation::handleBack,
                        (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                        track -> viewModel.onEvent(new LibraryEvent.ToggleFavorite(track)),
                        playlistDialogController::showAddToPlaylist,
                        downloadRequestController::downloadTrack,
                        downloadRequestController::downloadTracks,
                        collectionsOwner::selectAndLoad,
                        playlistDialogController::showRenamePlaylist,
                        playlistDialogController::confirmDeletePlaylist,
                        navigation::selectedPlaylistId,
                        store::selectedPlaylistTracks,
                        this::selectedPlaylistName,
                        statusMessages::setStatusKey,
                        documentPickerController::openPlaylistExportDocument,
                        streaming::importSelectedPlaylistToStreaming,
                        streaming::importFavoritesToStreaming,
                        streaming::showImportStreamingFavoritesProviderPicker,
                        streaming::syncSelectedPlaylistFromStreaming,
                        (playlistId, track, trackIndex, direction) -> viewModel.playlistOwner().moveSelectedPlaylistTrackJava(
                                playlistId,
                                track,
                                trackIndex,
                                direction,
                                playlistMutationOwner::onSelectedPlaylistTrackMoved
                        ),
                        (playlistId, track) -> viewModel.playlistOwner().removeSelectedPlaylistTrackJava(
                                playlistId,
                                track,
                                playlistMutationOwner::onSelectedPlaylistTrackRemoved
                        )
                )
        );
        collectionsStateBinding.bindFavoriteSync(favoriteSyncViewModel);
        collectionsStateBinding.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playback.readModel(),
                playlists -> {
                    return new CollectionsInsightSnapshot(
                            repository.loadRecentlyAdded(30),
                            repository.loadLongUnplayed(30),
                            playlistSourcesLoader.load(playlists)
                    );
                }
        );
        viewModel.bindFavoriteWriter(toggleFavoriteUseCase::execute);
        viewModel.bindPlaylistTrackLoader(loadPlaylistTracksUseCase::execute);
        viewModel.bindCollectionGateway(collectionGateway);
        viewModel.bindImportGateway(importGateway);
        viewModel.bindDocumentGateway(documentGateway);
        viewModel.bindExclusionGateway(new LibraryExclusionGateway() {
            @Override
            public boolean restoreLibraryExclusion(String sourceKey) {
                return repository.restoreLibraryExclusion(sourceKey);
            }

            @Override
            public int restoreAllLibraryExclusions() {
                return repository.restoreAllLibraryExclusions();
            }
        });
        libraryStateBinding = new LibraryStateBinding(
                store,
                viewModel,
                trackListStateReducer,
                groupsStateReducer,
                playlistsStateReducer,
                permissionController::hasAudioPermission,
                statusMessages::setStatus,
                playlistSourcesLoader
        );
        libraryStateBinding.bindStateSources(
                navigationViewModel.getState(),
                viewModel.getLibrary(),
                settingsViewModel.getState(),
                playback.readModel()
        );
    }

    List<Track> sourceCandidatesFor(Track track) {
        java.util.ArrayList<Track> candidates = store.sourceCandidatesFor(track);
        if (librarySyncOwner != null) {
            for (Track candidate : librarySyncOwner.sourceCandidatesFor(track)) {
                boolean duplicate = false;
                for (Track existing : candidates) {
                    if (existing.dataPath.equals(candidate.dataPath)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    void loadCollections() {
        collectionsOwner.load();
    }

    void loadLibrary(boolean allowCachedFirst) {
        importOwner.loadLibrary(allowCachedFirst);
    }

    void cancelLibraryLoad() {
        importOwner.cancelLibraryLoad();
    }

    void replaceLibrary(List<Track> tracks, Set<Long> favorites, String status) {
        importOwner.replaceLibrary(tracks, favorites, status);
    }

    void importAudioUris(List<android.net.Uri> uris) {
        importOwner.importAudioUris(uris);
    }

    void importAudioFolder(android.net.Uri uri) {
        importOwner.importAudioFolder(uri);
    }

    void importStreamM3u(android.net.Uri uri) {
        importOwner.importStreamM3u(uri);
    }

    void importPlaylistM3u(android.net.Uri uri) {
        importOwner.importPlaylistM3u(uri);
    }

    void exportPlaylist(android.net.Uri uri, long playlistId, String playlistName) {
        viewModel.loadOwner().exportPlaylistJava(uri, playlistId, playlistName);
    }

    void clearPlayHistory() {
        playHistoryActionController.clearPlayHistory();
    }

    void restoreHidden(String sourceKey) {
        hiddenLibraryRestoreOwner.restore(sourceKey);
    }

    void restoreAllHidden() {
        hiddenLibraryRestoreOwner.restoreAll();
    }

    String selectedPlaylistName() {
        return store.selectedPlaylistName(navigation.selectedPlaylistId());
    }

    void release() {
        searchViewModel.bindStateSources(null, null, null);
        recordingMatchViewModel.bindIdentityChangedListener(null);
        recordingMatchViewModel.bindDataSource(null);
        viewModel.bindGateway(null);
        viewModel.bindPlaylistTrackLoader(null);
        viewModel.bindFavoriteWriter(null);
        viewModel.bindFavoriteIdsProvider(null);
        viewModel.bindCollectionGateway(null);
        viewModel.bindImportGateway(null);
        viewModel.bindDocumentGateway(null);
        viewModel.bindPlaylistActionGateway(null);
        store.bindMergeIdentityProvider(null);
        store.bindArtistIdentityProvider(null);
        if (librarySyncOwner != null) {
            librarySyncOwner.release();
        }
        if (audioVerificationOwner != null) {
            audioVerificationOwner.release();
        }
        libraryStateBinding.release();
        collectionsStateBinding.release();
        favoriteSyncRuntime.close();
    }

    private String languageMode() {
        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
    }
}
