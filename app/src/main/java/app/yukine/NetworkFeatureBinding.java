package app.yukine;

import androidx.activity.ComponentActivity;

import app.yukine.model.Track;

/** Owns Activity-scoped network screens, actions, dialogs and rendering lifecycle. */
final class NetworkFeatureBinding {
    private final ComponentActivity activity;
    private final NavigationViewModel navigationViewModel;
    private final NetworkMenuViewModel menuViewModel;
    private final NetworkActionsViewModel actionsViewModel;
    private final NetworkSourcesViewModel sourcesViewModel;
    private final NetworkActionUseCases actionUseCases;
    private final MainSettingsStore settingsStore;
    private final StatusMessageController statusMessages;
    private final NavigationFeatureBinding navigation;
    private final LibraryFeatureBinding library;
    private final PlaybackFeatureBinding playback;

    private NetworkRequestController requestController;
    private NetworkDialogController dialogController;
    private ConfirmationDialogController confirmationDialogController;
    private NetworkRenderCoordinator renderCoordinator;

    NetworkFeatureBinding(
            ComponentActivity activity,
            MainActivityViewModels viewModels,
            NetworkActionUseCases actionUseCases,
            MainSettingsStore settingsStore,
            StatusMessageController statusMessages,
            NavigationFeatureBinding navigation,
            LibraryFeatureBinding library,
            PlaybackFeatureBinding playback
    ) {
        this.activity = activity;
        this.navigationViewModel = viewModels.getNavigationViewModel();
        this.menuViewModel = viewModels.getNetworkMenuViewModel();
        this.actionsViewModel = (NetworkActionsViewModel) viewModels.getNetworkActionsViewModel();
        this.sourcesViewModel = viewModels.getNetworkSourcesViewModel();
        this.actionUseCases = actionUseCases;
        this.settingsStore = settingsStore;
        this.statusMessages = statusMessages;
        this.navigation = navigation;
        this.library = library;
        this.playback = playback;
    }

    void bindUi(
            StreamingSearchRenderController streamingSearchRenderController,
            DocumentPickerController documentPickerController,
            SettingsFeatureBinding settings
    ) {
        actionsViewModel.bindUseCases(actionUseCases);
        actionsViewModel.bindListener(new MainNetworkActionsListener(
                playback.getNowPlayingViewModel(),
                library::replaceLibrary,
                navigation::navigateToNetworkTabPage,
                library::loadCollections,
                statusMessages::setStatus
        ));
        requestController = new NetworkRequestController(
                actionsViewModel,
                key -> AppLanguage.text(languageMode(), key),
                statusMessages::setStatus
        );
        DialogLanguageProvider dialogLanguageProvider = this::languageMode;
        dialogController = new NetworkDialogController(activity, dialogLanguageProvider, requestController);
        confirmationDialogController = new ConfirmationDialogController(
                activity,
                dialogLanguageProvider,
                new ConfirmationActions(
                        library::clearPlayHistory,
                        playback.getQueueActionController()::clearQueue,
                        requestController::deleteAllStreams,
                        requestController::deleteTrack,
                        requestController::deleteTracks,
                        requestController::deleteRemoteSource
                )
        );
        NetworkMenuEventController menuEvents = new NetworkMenuEventController(
                navigation::navigateNetworkPage,
                navigation::handleBack,
                dialogController::showAddStream,
                dialogController::showImportM3u,
                dialogController::showAddWebDav,
                documentPickerController::openM3uFilePicker,
                () -> library.store().streamTracks(),
                () -> library.store().streamTrackCount(),
                () -> library.store().webDavTracks(),
                () -> library.store().remoteSources(),
                requestController::syncAllWebDavSources,
                confirmationDialogController::confirmDeleteAllStreams,
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                key -> AppLanguage.text(languageMode(), key),
                statusMessages::setStatus,
                menuViewModel
        );
        NetworkSourcesEventController sourcesEvents = new NetworkSourcesEventController(
                navigation.getRouteController(),
                requestController,
                sourceId -> library.store().remoteSourceName(sourceId, languageMode()),
                sourceId -> library.store().webDavTracksForSource(sourceId),
                dialogController::showEditWebDav,
                confirmationDialogController::confirmDeleteRemoteSource,
                (tracks, index) -> playback.getPlaybackStartController().playTrackList(tracks, index),
                key -> AppLanguage.text(languageMode(), key),
                statusMessages::setStatus
        );
        NetworkTrackListRenderController trackListRenderer = new NetworkTrackListRenderController(
                new NetworkTrackListOwner(
                        navigation.getRouteController(),
                        sourcesEvents,
                        library.trackListStatePublisher()
                )
        );
        renderCoordinator = new NetworkRenderCoordinator(
                library.store(),
                new NetworkMenuRenderController(menuEvents),
                trackListRenderer,
                new NetworkSourcesRenderController(sourcesViewModel, sourcesEvents),
                streamingSearchRenderController
        );
        renderCoordinator.bindStateSources(
                navigationViewModel.getState(),
                library.viewModel().getLibrary(),
                settings.viewModel().getState()
        );
    }

    void editStream(Track track) {
        if (dialogController != null) {
            dialogController.showEditStream(track);
        }
    }

    void confirmClearPlayHistory() {
        if (confirmationDialogController != null) {
            confirmationDialogController.confirmClearPlayHistory();
        }
    }

    void confirmClearQueue() {
        if (confirmationDialogController != null) {
            confirmationDialogController.confirmClearQueue();
        }
    }

    void release() {
        actionsViewModel.bindListener(null);
        actionsViewModel.bindUseCases(null);
        if (renderCoordinator != null) {
            renderCoordinator.release();
        }
    }

    private String languageMode() {
        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
    }
}
