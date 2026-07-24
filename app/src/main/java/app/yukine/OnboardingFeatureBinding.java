package app.yukine;

import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.yukine.data.MusicLibraryRepository;

/** Owns onboarding state and first-run library startup policy. */
final class OnboardingFeatureBinding {
    private final OnboardingOwner owner;
    private final MainPermissionController permissionController;
    private final LibraryFeatureBinding library;
    private final MusicLibraryRepository repository;
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler;
    private boolean released;

    OnboardingFeatureBinding(
            MainPermissionController permissionController,
            DocumentPickerController documentPickerController,
            LibraryFeatureBinding library,
            NavigationFeatureBinding navigation,
            MusicLibraryRepository repository,
            MainSettingsStore settingsStore,
            StatusMessageController statusMessages,
            Handler mainHandler
    ) {
        this.permissionController = permissionController;
        this.library = library;
        this.repository = repository;
        this.mainHandler = mainHandler;
        this.owner = new OnboardingOwner(
                new OnboardingPermissionAccess(
                        permissionController::hasAudioPermission,
                        permissionController::hasNotificationPermission,
                        permissionController::requestAudioPermission
                ),
                new OnboardingLibraryAccess(library::loadLibrary, library::cancelLibraryLoad),
                navigation::navigateToNetworkTabPage,
                documentPickerController::openPlaylistM3uFilePicker,
                documentPickerController::openAudioFolderPicker,
                new OnboardingCompletionStore(
                        () -> {
                            throw new IllegalStateException("Onboarding visibility must be loaded asynchronously");
                        },
                        () -> {
                            if (repository != null) persistenceExecutor.execute(
                                    () -> repository.saveOnboardingCompleted(true)
                            );
                        }
                ),
                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),
                statusMessages::setStatus,
                new HandlerOnboardingScheduler(mainHandler)
        );
    }

    OnboardingOwner owner() {
        return owner;
    }

    void initialize(Runnable completion) {
        persistenceExecutor.execute(() -> {
            boolean shouldShow = repository == null || !repository.loadOnboardingCompleted();
            int folderCount = repository == null
                    ? 0
                    : repository.loadLocalMusicFolderSources().size();
            mainHandler.post(() -> {
                if (released) return;
                owner.initialize(shouldShow);
                owner.onLocalMusicFolderCountChanged(folderCount);
                if (completion != null) completion.run();
            });
        });
    }

    void startLibrary() {
        // Startup is cache-only. Device-wide and selected-folder refreshes are user initiated.
        library.loadCachedLibrary();
        library.loadCollections();
    }

    void release() {
        released = true;
        owner.release();
        persistenceExecutor.shutdown();
    }
}
