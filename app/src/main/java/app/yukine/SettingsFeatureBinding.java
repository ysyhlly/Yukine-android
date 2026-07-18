package app.yukine;

import android.os.Handler;

import androidx.activity.ComponentActivity;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.ui.EchoTheme;

/** Owns Activity-scoped settings state, typed effects, runtime application and lifecycle. */
final class SettingsFeatureBinding {
    private final SettingsViewModel viewModel;
    private final MainSettingsStore settingsStore;
    private final LoadSettingsPreferencesUseCase loadPreferencesUseCase;
    private final LoadLyricsSettingsUseCase loadLyricsSettingsUseCase;

    private SettingsContextProvider contextProvider;
    private SettingsEffectOwner effectOwner;
    private BackgroundImageSelectionOwner backgroundImageSelectionOwner;
    private BackgroundImagePickerController backgroundImagePickerController;
    private Handler mainHandler;
    private MainExecutors executors;
    private LyricsViewModel lyricsViewModel;
    private LyricsLoader lyricsLoader;
    private CustomBackgroundAccentController customBackgroundAccentController;
    private boolean released;

    SettingsFeatureBinding(
            MainActivityViewModels viewModels,
            MainSettingsStore settingsStore,
            LoadSettingsPreferencesUseCase loadPreferencesUseCase,
            ApplySettingsPreferenceUseCase applyPreferenceUseCase,
            LoadLyricsSettingsUseCase loadLyricsSettingsUseCase
    ) {
        this.viewModel = viewModels.getSettingsViewModel();
        this.settingsStore = settingsStore;
        this.loadPreferencesUseCase = loadPreferencesUseCase;
        this.loadLyricsSettingsUseCase = loadLyricsSettingsUseCase;
        viewModel.bindPreferenceGateway(applyPreferenceUseCase::execute);
        viewModel.bindStoreMirror(settingsStore::sync);
    }

    SettingsViewModel viewModel() {
        return viewModel;
    }

    void bindFeatures(
            ComponentActivity activity,
            Handler mainHandler,
            MainExecutors executors,
            StatusMessageController statusMessages,
            MainUiShellController uiShellController,
            CustomBackgroundAccentController customBackgroundAccentController,
            NavigationFeatureBinding navigation,
            LibraryFeatureBinding library,
            PlaybackFeatureBinding playback,
            StreamingFeatureBinding streaming,
            LyricsViewModel lyricsViewModel,
            LyricsLoader lyricsLoader,
            MainPermissionController permissionController,
            DocumentPickerController documentPickerController,
            LuoxueSourceImportDialogController luoxueSourceImportDialogController,
            BackupRestoreLauncher backupRestoreLauncher,
            NavigationViewModel navigationViewModel,
            StreamingGatewaySettingsStore streamingGatewaySettingsStore,
            LuoxueSourceStore luoxueSourceStore,
            MusicLibraryRepository repository,
            Runnable importCurrentLyrics,
            Runnable importLyricsDirectory,
            Runnable viewLyricsImportReport
    ) {
        this.mainHandler = mainHandler;
        this.executors = executors;
        this.lyricsViewModel = lyricsViewModel;
        this.lyricsLoader = lyricsLoader;
        this.customBackgroundAccentController = customBackgroundAccentController;
        backgroundImageSelectionOwner = new BackgroundImageSelectionOwner(
                viewModel,
                settingsStore::pageBackgrounds,
                this::languageMode,
                statusMessages::setStatus
        );
        backgroundImagePickerController = new BackgroundImagePickerController(
                activity,
                backgroundImageSelectionOwner,
                executors::io,
                task -> mainHandler.post(task),
                this::languageMode
        );
        IdentityEnhancementProxyDialogController identityProxyDialogController =
                new IdentityEnhancementProxyDialogController(
                        activity,
                        new IdentityEnhancementSettingsStore(activity),
                        this::languageMode,
                        statusMessages::setStatus
                );
        effectOwner = new SettingsEffectOwner(
                new SettingsNavigationEffectActions(
                        statusMessages::setStatus,
                        navigation.getRouteController()::setSettingsPage,
                        navigation::navigateToNetworkTabPage,
                        () -> navigation.navigateToTab(app.yukine.navigation.DownloadsTab.INSTANCE, true)
                ),
                new SettingsLibraryEffectActions(
                        permissionController::requestNeededPermissions,
                        () -> library.loadLibrary(false),
                        documentPickerController::openAudioFilePicker,
                        documentPickerController::openAudioFolderPicker,
                        () -> executors.io(() -> {
                            IdentityBackfillScheduler.INSTANCE.rebuildOrReuseBlocking(activity);
                            mainHandler.post(() -> {
                                statusMessages.setStatus(AppLanguage.text(languageMode(), "identity.backfill.started"));
                                viewModel.refreshSettingsContext();
                            });
                        }),
                        () -> {
                            IdentityBackfillScheduler.INSTANCE.cancel(activity);
                            statusMessages.setStatus(AppLanguage.text(languageMode(), "identity.backfill.cancelled"));
                            viewModel.refreshSettingsContext();
                        },
                        luoxueSourceImportDialogController::showSourceManager,
                        luoxueSourceImportDialogController::showImportDialog,
                        library::restoreHidden,
                        library::restoreAllHidden
                ),
                new SettingsPlaybackEffectActions(
                        () -> lyricsViewModel.reloadCurrentLyrics(languageMode()),
                        minutes -> playback.applyActionResult(
                                playback.getNowPlayingViewModel().startSleepTimer(minutes)
                        ),
                        () -> playback.applyActionResult(
                                playback.getNowPlayingViewModel().cancelSleepTimer()
                        ),
                        viewModel.lyricsOwner()::openFloatingLyricsPermission,
                        importCurrentLyrics,
                        importLyricsDirectory,
                        viewLyricsImportReport
                ),
                new SettingsFileEffectActions(
                        backgroundImagePickerController::open,
                        backupRestoreLauncher::exportBackup,
                        backupRestoreLauncher::importBackup
                ),
                new SettingsStreamingEffectActions(
                        streaming::applyEndpoint,
                        identityProxyDialogController::show
                )
        );
        viewModel.bindEffectListener(effectOwner);
        SettingsRuntimeApplier runtimeApplier = new SettingsRuntimeApplier(
                uiShellController::applyThemeSurface,
                customBackgroundAccentController::refresh,
                () -> !playback.isConnected()
                        ? null
                        : new MainSettingsPlaybackServiceControls(playback.getConnection()),
                () -> new MainSettingsLyricsControls(lyricsViewModel),
                () -> new MainSettingsFloatingLyricsControls(activity, () -> permissionController)
        );
        viewModel.bindRuntimeEffectListener(runtimeApplier::apply);
        contextProvider = new SettingsContextProvider(
                activity,
                settingsStore,
                library.store(),
                permissionController,
                playback.getConnection(),
                playback.getPlaybackViewModel(),
                lyricsViewModel,
                streamingGatewaySettingsStore,
                luoxueSourceStore,
                repository
        );
        viewModel.bindContextLoader(contextProvider);
        viewModel.bindRouteState(navigationViewModel.getSettingsRouteState());
    }

    void initialize(Runnable completion) {
        if (executors == null || mainHandler == null) {
            if (completion != null) completion.run();
            return;
        }
        executors.io(() -> {
            LoadedSettingsPreferences preferences = loadPreferencesUseCase.execute();
            LoadedLyricsSettings lyricsSettings = loadLyricsSettingsUseCase.execute();
            mainHandler.post(() -> {
                if (released) return;
                settingsStore.load(preferences);
                if (lyricsViewModel != null) {
                    lyricsViewModel.configure(
                            lyricsLoader,
                            lyricsSettings.onlineLyricsEnabled,
                            lyricsSettings.lyricsOffsetMs
                    );
                }
                viewModel.refreshSettingsContext();
                if (customBackgroundAccentController != null
                        && EchoTheme.ACCENT_DYNAMIC_BACKGROUND.equals(settingsStore.accentMode())) {
                    customBackgroundAccentController.refresh(settingsStore.pageBackgrounds());
                }
                if (completion != null) completion.run();
            });
        });
    }

    void release() {
        released = true;
        viewModel.bindRouteState(null);
        viewModel.bindContextLoader(null);
        viewModel.bindEffectListener(null);
        viewModel.bindRuntimeEffectListener(null);
        viewModel.bindPreferenceGateway(null);
        viewModel.bindStoreMirror(null);
    }

    private String languageMode() {
        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
    }
}
