package app.yukine;

import android.os.Handler;

import app.yukine.data.MusicLibraryRepository;

/** Owns onboarding state and first-run library startup policy. */
final class OnboardingFeatureBinding {
    private final OnboardingOwner owner;
    private final MainPermissionController permissionController;
    private final LibraryFeatureBinding library;

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
        this.owner = new OnboardingOwner(
                new OnboardingPermissionAccess(
                        permissionController::hasAudioPermission,
                        permissionController::hasNotificationPermission,
                        permissionController::requestNeededPermissions
                ),
                new OnboardingLibraryAccess(library::loadLibrary, library::cancelLibraryLoad),
                navigation::navigateToNetworkTabPage,
                documentPickerController::openPlaylistM3uFilePicker,
                new OnboardingCompletionStore(
                        () -> repository == null || !repository.loadOnboardingCompleted(),
                        () -> {
                            if (repository != null) {
                                repository.saveOnboardingCompleted(true);
                            }
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

    void initialize() {
        owner.initialize();
    }

    void startLibrary() {
        if (!owner.showOnboarding()) {
            permissionController.requestNeededPermissions();
            library.loadLibrary(!permissionController.hasAudioPermission());
        } else {
            library.loadLibrary(true);
        }
        library.loadCollections();
    }

    void release() {
        owner.release();
    }
}
