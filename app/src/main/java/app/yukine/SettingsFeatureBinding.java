package app.yukine;

import android.os.Handler;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.streaming.LuoxueSourceStore;
import app.yukine.streaming.KugouExperimentalSyncStore;
import app.yukine.ui.EchoTheme;

import java.util.List;

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
    private ComponentActivity activity;
    private MainPermissionController permissionController;
    private LiveData<List<WorkInfo>> identityBackfillWorkInfos;
    private Observer<List<WorkInfo>> identityBackfillObserver;
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
            DiagnosticExportLauncher diagnosticExportLauncher,
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
        this.activity = activity;
        this.permissionController = permissionController;
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
        KugouExperimentalSyncStore kugouExperimentalSyncStore =
                new KugouExperimentalSyncStore(activity);
        effectOwner = new SettingsEffectOwner(
                new SettingsNavigationEffectActions(
                        statusMessages::setStatus,
                        navigation.getRouteController()::setSettingsPage,
                        navigation::navigateToNetworkTabPage,
                        () -> navigation.navigateToTab(app.yukine.navigation.DownloadsTab.INSTANCE, true),
                        () -> new GitHubUpdateChecker(
                                activity,
                                java.util.concurrent.Executors.newSingleThreadExecutor(),
                                mainHandler,
                                this::languageMode
                        ).check()
                ),
                new SettingsLibraryEffectActions(
                        permissionController::requestNeededPermissions,
                        () -> library.loadLibrary(false),
                        documentPickerController::openAudioFilePicker,
                        documentPickerController::openAudioFolderPicker,
                        () -> executors.io(() -> {
                            IdentityBackfillScheduleResult result =
                                    IdentityBackfillScheduler.INSTANCE.rebuildOrReuseBlocking(activity);
                            mainHandler.post(() -> {
                                showIdentityBackfillScheduleFeedback(statusMessages, result);
                                viewModel.refreshSettingsContext();
                            });
                        }),
                        () -> {
                            IdentityBackfillScheduler.INSTANCE.cancel(activity);
                            statusMessages.showFeedback(
                                    AppLanguage.text(languageMode(), "identity.backfill.cancelled")
                            );
                            viewModel.refreshSettingsContext();
                        },
                        luoxueSourceImportDialogController::showSourceManager,
                        luoxueSourceImportDialogController::showImportDialog,
                        library::restoreHidden,
                        library::restoreAllHidden,
                        mode -> executors.io(() -> {
                            repository.saveLibraryDedupMode(mode);
                            IdentityBackfillScheduler.INSTANCE.scheduleManual(activity, true);
                            mainHandler.post(() -> {
                                statusMessages.setStatus(AppLanguage.text(
                                        languageMode(),
                                        "library.dedup.mode.changed"
                                ));
                                viewModel.refreshSettingsContext();
                            });
                        }),
                        (leftRecordingId, rightRecordingId) -> executors.io(() -> {
                            app.yukine.data.DuplicateBatchConfirmResult result =
                                    repository.confirmDuplicateCandidate(
                                            leftRecordingId,
                                            rightRecordingId
                                    );
                            mainHandler.post(() -> {
                                library.loadLibrary(false);
                                statusMessages.setStatus(
                                        AppLanguage.text(languageMode(), "library.dedup.confirm.result") +
                                                " " + result.getConfirmed() + "/" +
                                                (result.getConfirmed() + result.getSkipped() + result.getFailed())
                                );
                                viewModel.refreshSettingsContext();
                            });
                        }),
                        () -> executors.io(() -> {
                            app.yukine.data.DuplicateBatchConfirmResult result =
                                    repository.confirmHighConfidenceDuplicateCandidates(50);
                            mainHandler.post(() -> {
                                library.loadLibrary(false);
                                statusMessages.setStatus(
                                        AppLanguage.text(languageMode(), "library.dedup.confirm.result") +
                                                " " + result.getConfirmed() + "/" +
                                                (result.getConfirmed() + result.getSkipped() + result.getFailed())
                                );
                                viewModel.refreshSettingsContext();
                            });
                        })
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
                        backupRestoreLauncher::importBackup,
                        diagnosticExportLauncher::showOptions
                ),
                new SettingsStreamingEffectActions(
                        streaming::applyEndpoint,
                        identityProxyDialogController::show,
                        enabled -> {
                            kugouExperimentalSyncStore.setUserEnabled(enabled);
                            statusMessages.setStatus(enabled
                                    ? "酷狗实验同步已启用；契约门禁未通过时保持只读"
                                    : "酷狗实验同步已关闭");
                            viewModel.refreshSettingsContext();
                            streaming.refreshProviders();
                        }
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
        bindIdentityBackfillStatus(activity);
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
                reconcileFloatingLyricsState();
                if (completion != null) completion.run();
            });
        });
    }

    void release() {
        released = true;
        if (identityBackfillWorkInfos != null && identityBackfillObserver != null) {
            identityBackfillWorkInfos.removeObserver(identityBackfillObserver);
        }
        identityBackfillWorkInfos = null;
        identityBackfillObserver = null;
        viewModel.bindRouteState(null);
        viewModel.bindContextLoader(null);
        viewModel.bindEffectListener(null);
        viewModel.bindRuntimeEffectListener(null);
        viewModel.bindPreferenceGateway(null);
        viewModel.bindStoreMirror(null);
    }

    void onResume() {
        viewModel.refreshSettingsContext();
        reconcileFloatingLyricsState();
    }

    private void bindIdentityBackfillStatus(ComponentActivity activity) {
        identityBackfillWorkInfos = WorkManager.getInstance(activity.getApplicationContext())
                .getWorkInfosForUniqueWorkLiveData(IdentityBackfillScheduler.UNIQUE_WORK_NAME);
        identityBackfillObserver = workInfos -> {
            if (released || workInfos == null || executors == null || mainHandler == null) return;
            executors.io(() -> {
                IdentityBackfillScheduler.INSTANCE.syncRuntimeStatus(activity, workInfos);
                mainHandler.post(() -> {
                    if (!released) viewModel.refreshSettingsContext();
                });
            });
        };
        identityBackfillWorkInfos.observe(activity, identityBackfillObserver);
    }

    private void showIdentityBackfillScheduleFeedback(
            StatusMessageController statusMessages,
            IdentityBackfillScheduleResult result
    ) {
        String key;
        switch (result.getKind()) {
            case ENQUEUED:
                key = "identity.backfill.queued";
                break;
            case ALREADY_ACTIVE:
                key = "identity.backfill.already.active";
                break;
            case FAILED:
            default:
                key = "identity.backfill.schedule.failed";
                break;
        }
        String message = AppLanguage.text(languageMode(), key);
        if (result.getKind() == IdentityBackfillScheduleKind.FAILED
                && !result.getErrorMessage().isBlank()) {
            message += ": " + result.getErrorMessage();
        }
        statusMessages.showFeedback(message);
    }

    private void reconcileFloatingLyricsState() {
        if (activity == null || permissionController == null || released) return;
        boolean permissionGranted = permissionController.hasOverlayPermission();
        FloatingLyricsEnableRequestStore requestStore =
                new FloatingLyricsEnableRequestStore(activity);
        if (requestStore.consumeIfGranted(permissionGranted)) {
            viewModel.lyricsOwner().setFloatingLyricsEnabled(true);
            return;
        }
        if (!settingsStore.floatingLyricsEnabled()) return;
        if (permissionGranted) {
            FloatingLyricsService.start(activity);
        } else {
            viewModel.lyricsOwner().setFloatingLyricsEnabled(false);
        }
    }

    private String languageMode() {
        return settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode();
    }
}
