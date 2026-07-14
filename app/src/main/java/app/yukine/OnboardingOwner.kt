package app.yukine

import android.os.Handler
import kotlinx.coroutines.flow.StateFlow

internal fun interface OnboardingBooleanSource {
    fun value(): Boolean
}

internal fun interface OnboardingAction {
    fun run()
}

internal class OnboardingPermissionAccess(
    private val audioPermissionGranted: OnboardingBooleanSource,
    private val notificationPermissionGranted: OnboardingBooleanSource,
    private val permissionRequester: OnboardingAction
) {
    fun hasAudioPermission(): Boolean = audioPermissionGranted.value()

    fun hasNotificationPermission(): Boolean = notificationPermissionGranted.value()

    fun requestNeededPermissions() = permissionRequester.run()
}

internal fun interface OnboardingLibraryLoader {
    fun load(allowCachedFirst: Boolean)
}

internal class OnboardingLibraryAccess(
    private val loader: OnboardingLibraryLoader,
    private val canceller: OnboardingAction
) {
    fun loadLibrary(allowCachedFirst: Boolean) = loader.load(allowCachedFirst)

    fun cancelLibraryLoad() = canceller.run()
}

internal fun interface OnboardingNetworkNavigator {
    fun navigate(page: NetworkPage)
}

internal class OnboardingCompletionStore(
    private val shouldShowSource: OnboardingBooleanSource,
    private val completedWriter: OnboardingAction
) {
    fun shouldShow(): Boolean = shouldShowSource.value()

    fun markCompleted() = completedWriter.run()
}

internal fun interface OnboardingLanguageSource {
    fun languageMode(): String
}

internal fun interface OnboardingStatusSink {
    fun setStatus(status: String)
}

internal class HandlerOnboardingScheduler(
    private val handler: Handler
) : OnboardingController.Scheduler {
    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        handler.postDelayed(runnable, delayMs)
    }

    override fun removeCallbacks(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}

/** Owns onboarding state, feature reactions, persistence, and timeout presentation. */
internal class OnboardingOwner(
    private val permissionAccess: OnboardingPermissionAccess,
    private val libraryAccess: OnboardingLibraryAccess,
    private val networkNavigator: OnboardingNetworkNavigator,
    private val playlistPicker: OnboardingAction,
    private val completionStore: OnboardingCompletionStore,
    private val languageSource: OnboardingLanguageSource,
    private val statusSink: OnboardingStatusSink,
    scheduler: OnboardingController.Scheduler
) : OnboardingController.Listener {
    private val controller = OnboardingController(this, scheduler)

    val state: StateFlow<OnboardingUiState> = controller.state

    fun initialize() {
        controller.initialize(completionStore.shouldShow())
    }

    fun initialize(shouldShow: Boolean) {
        controller.initialize(shouldShow)
    }

    fun showOnboarding(): Boolean = controller.showOnboarding()

    fun onPermissionsChanged() = controller.onPermissionsChanged()

    fun onLibraryScanResult(canScan: Boolean) = controller.onLibraryScanResult(canScan)

    fun scanLibrary() = controller.scanLibraryFromOnboarding()

    fun importPlaylist() = controller.importPlaylistFromOnboarding()

    fun openStreaming() = controller.openStreamingFromOnboarding()

    fun finish() = controller.finishOnboarding()

    fun release() = controller.release()

    override fun hasAudioPermission(): Boolean = permissionAccess.hasAudioPermission()

    override fun hasNotificationPermission(): Boolean = permissionAccess.hasNotificationPermission()

    override fun requestNeededPermissions() = permissionAccess.requestNeededPermissions()

    override fun loadLibrary(allowCachedFirst: Boolean) = libraryAccess.loadLibrary(allowCachedFirst)

    override fun cancelLibraryLoad() = libraryAccess.cancelLibraryLoad()

    override fun onLibraryScanTimedOut() {
        statusSink.setStatus(AppLanguage.text(languageSource.languageMode(), "library.scan.timeout"))
    }

    override fun navigateToNetworkTabPage(page: NetworkPage) = networkNavigator.navigate(page)

    override fun openPlaylistM3uFilePicker() = playlistPicker.run()

    override fun onboardingCompleted() = completionStore.markCompleted()
}
