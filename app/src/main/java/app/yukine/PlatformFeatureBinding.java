package app.yukine;

import android.os.Handler;

import androidx.activity.ComponentActivity;

import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.ui.EchoTheme;
import app.yukine.data.CustomLyricsRepository;
import app.yukine.data.MusicLibraryRepository;

/** Owns Android permission, document, share, download and backup launcher delegation. */
final class PlatformFeatureBinding {
    private final ComponentActivity activity;
    private final Handler mainHandler;
    private final MainExecutors executors;
    private final MainSettingsStore settingsStore;
    private final TrackDownloadManager trackDownloadManager;
    private final ResolveStreamingPlaybackUseCase resolvePlaybackUseCase;
    private final LibraryDeletionUseCase libraryDeletionUseCase;
    private final LuoxueSourceStore luoxueSourceStore;
    private final DownloadsViewModel downloadsViewModel;
    private final LyricsViewModel lyricsViewModel;
    private final CustomLyricsRepository customLyricsRepository;
    private final MusicLibraryRepository musicLibraryRepository;

    private final MainUiShellController uiShellController;
    private final StatusMessageController statusMessageController;
    private final TrackShareLauncher trackShareLauncher;

    private MainPermissionController permissionController;
    private CustomBackgroundAccentController customBackgroundAccentController;
    private LibraryFileDeleteLauncher libraryFileDeleteLauncher;
    private DocumentPickerController documentPickerController;
    private BackupRestoreLauncher backupRestoreLauncher;
    private DiagnosticExportLauncher diagnosticExportLauncher;
    private DownloadRequestController downloadRequestController;
    private DownloadDirectoryOwner downloadDirectoryOwner;
    private LuoxueSourceImportController luoxueSourceImportController;
    private LuoxueSourceImportDialogController luoxueSourceImportDialogController;
    private OnboardingOwner onboardingOwner;
    private LyricsImportCoordinator lyricsImportCoordinator;

    PlatformFeatureBinding(
            ComponentActivity activity,
            MainActivityViewModels viewModels,
            Handler mainHandler,
            MainExecutors executors,
            MainSettingsStore settingsStore,
            TrackShareOperations trackShareOperations,
            TrackDownloadManager trackDownloadManager,
            ResolveStreamingPlaybackUseCase resolvePlaybackUseCase,
            LibraryDeletionUseCase libraryDeletionUseCase,
            LuoxueSourceStore luoxueSourceStore,
            CustomLyricsRepository customLyricsRepository,
            MusicLibraryRepository musicLibraryRepository
    ) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.executors = executors;
        this.settingsStore = settingsStore;
        this.trackDownloadManager = trackDownloadManager;
        this.resolvePlaybackUseCase = resolvePlaybackUseCase;
        this.libraryDeletionUseCase = libraryDeletionUseCase;
        this.luoxueSourceStore = luoxueSourceStore;
        this.customLyricsRepository = customLyricsRepository;
        this.musicLibraryRepository = musicLibraryRepository;
        this.downloadsViewModel = viewModels.getDownloadsViewModel();
        this.lyricsViewModel = viewModels.getLyricsViewModel();
        this.uiShellController = new MainUiShellController(activity);
        this.statusMessageController = new StatusMessageController(
                (StatusMessageViewModel) viewModels.getStatusMessageViewModel(),
                this::languageMode,
                uiShellController::updateStatus,
                settingsStore::debugPromptsEnabled
        );
        this.trackShareLauncher = new TrackShareLauncher(
                activity,
                trackShareOperations,
                this::languageMode,
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

    StatusMessageController statusMessages() {
        return statusMessageController;
    }

    MainUiShellController uiShellController() {
        return uiShellController;
    }

    MainPermissionController permissionController() {
        return permissionController;
    }

    CustomBackgroundAccentController customBackgroundAccentController() {
        return customBackgroundAccentController;
    }

    LibraryFileDeleteLauncher libraryFileDeleteLauncher() {
        return libraryFileDeleteLauncher;
    }

    DocumentPickerController documentPickerController() {
        return documentPickerController;
    }

    BackupRestoreLauncher backupRestoreLauncher() {
        return backupRestoreLauncher;
    }

    DiagnosticExportLauncher diagnosticExportLauncher() {
        return diagnosticExportLauncher;
    }

    DownloadRequestController downloadRequestController() {
        return downloadRequestController;
    }

    LuoxueSourceImportDialogController luoxueSourceImportDialogController() {
        return luoxueSourceImportDialogController;
    }

    TrackDownloadManager trackDownloadManager() {
        return trackDownloadManager;
    }

    void importCurrentLyrics(PlaybackFeatureBinding playback) {
        app.yukine.model.Track track = playback.snapshot().currentTrack;
        if (track == null) {
            statusMessageController.setStatus(AppLanguage.text(languageMode(), "no.track.selected"));
            return;
        }
        lyricsImportCoordinator.importForTrack(track);
    }

    void importLyricsDirectory() {
        lyricsImportCoordinator.openBatchDirectory();
    }

    void showLyricsImportReport() {
        lyricsImportCoordinator.showLatestReport();
    }

    void bindFeatures(
            LibraryFeatureBinding library,
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming
    ) {
        customBackgroundAccentController = new CustomBackgroundAccentController(
                activity.getContentResolver(),
                executors::io,
                task -> mainHandler.post(task),
                EchoTheme::setCustomBackgroundAccentArgb
        );
        PermissionResultOwner permissionResultOwner = new PermissionResultOwner(
                () -> permissionController != null && permissionController.hasAudioPermission(),
                library::loadLibrary,
                () -> {
                    if (onboardingOwner != null) {
                        onboardingOwner.onPermissionsChanged();
                    }
                }
        );
        permissionController = new MainPermissionController(activity, permissionResultOwner);
        library.bindPlatform(
                permissionController,
                playback,
                canScan -> {
                    if (onboardingOwner != null) {
                        onboardingOwner.onLibraryScanResult(canScan);
                    }
                }
        );
        downloadDirectoryOwner = new DownloadDirectoryOwner(
                trackDownloadManager,
                downloadsViewModel,
                statusMessageController::showFeedback
        );
        documentPickerController = new DocumentPickerController(activity, new DocumentPickerActions(
                library::importAudioUris,
                library::importAudioFolder,
                downloadDirectoryOwner::setCustomDirectory,
                library::importStreamM3u,
                library::exportPlaylist,
                library::importPlaylistM3u,
                uris -> luoxueSourceImportController.importSelectedUris(uris)
        ));
        libraryFileDeleteLauncher = new LibraryFileDeleteLauncher(
                activity,
                libraryDeletionUseCase,
                this::languageMode,
                library.deletionCompletionOwner()
        );
        backupRestoreLauncher = new BackupRestoreLauncher(
                activity,
                statusKey -> {
                    statusMessageController.setStatusKey(statusKey);
                    return kotlin.Unit.INSTANCE;
                },
                this::languageMode
        );
        diagnosticExportLauncher = new DiagnosticExportLauncher(
                activity,
                statusKey -> {
                    statusMessageController.setStatusKey(statusKey);
                    return kotlin.Unit.INSTANCE;
                },
                this::languageMode
        );
        luoxueSourceImportController = new LuoxueSourceImportController(
                key -> AppLanguage.text(languageMode(), key),
                () -> documentPickerController,
                new ContentResolverLuoxueSourceDocumentReader(activity.getContentResolver()),
                luoxueSourceStore,
                executors::io,
                executors::network,
                task -> mainHandler.post(task),
                statusMessageController::setStatus,
                streaming::onLuoxueSourcesChanged
        );
        luoxueSourceImportDialogController = new LuoxueSourceImportDialogController(
                activity,
                key -> AppLanguage.text(languageMode(), key),
                luoxueSourceImportController
        );
        downloadRequestController = new DownloadRequestController(
                () -> trackDownloadManager,
                downloadsViewModel,
                resolvePlaybackUseCase,
                new DownloadQualityDialogController(activity, this::languageMode),
                streaming.downloadResolver(),
                message -> {
                    statusMessageController.showFeedback(message);
                    return kotlin.Unit.INSTANCE;
                }
        );
        lyricsImportCoordinator = new LyricsImportCoordinator(
                activity,
                customLyricsRepository,
                musicLibraryRepository,
                executors::io,
                task -> mainHandler.post(task),
                statusMessageController::setStatus,
                () -> {
                    lyricsViewModel.reloadCurrentLyrics(languageMode());
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    void bindPlaybackEffects(
            PlaybackFeatureBinding playback,
            NavigationFeatureBinding navigation,
            LibraryFeatureBinding library
    ) {
        playback.bindEffects(
                () -> navigation.navigateToTab(app.yukine.navigation.QueueTab.INSTANCE, true),
                library.playlistDialogController()::showAddToPlaylist,
                trackShareLauncher::share,
                downloadRequestController::downloadTrack,
                lyricsImportCoordinator::importForTrack,
                lyricsImportCoordinator::clearForTrack
        );
    }

    void attachOnboarding(OnboardingOwner onboardingOwner) {
        this.onboardingOwner = onboardingOwner;
    }

    void applyThemeSurface() {
        uiShellController.applyThemeSurface();
    }

    void shutdown() {
        executors.shutdownNow();
    }

    private String languageMode() {
        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
    }
}
