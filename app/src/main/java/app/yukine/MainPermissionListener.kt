package app.yukine

internal fun interface AudioPermissionStatusSource {
    fun hasAudioPermission(): Boolean
}

internal fun interface PermissionResultLibraryLoader {
    fun loadLibrary(allowCachedFirst: Boolean)
}

internal fun interface OnboardingVisibilitySource {
    fun showOnboarding(): Boolean
}

internal fun interface PermissionResultNavHostMounter {
    fun mountNavHostShell()
}

internal fun interface MainPermissionListenerFactory {
    fun create(
        audioPermissionStatusSource: AudioPermissionStatusSource,
        libraryLoader: PermissionResultLibraryLoader,
        onboardingVisibilitySource: OnboardingVisibilitySource,
        navHostMounter: PermissionResultNavHostMounter
    ): MainPermissionController.Listener
}

internal class MainPermissionListener(
    private val audioPermissionStatusSource: AudioPermissionStatusSource,
    private val libraryLoader: PermissionResultLibraryLoader,
    private val onboardingVisibilitySource: OnboardingVisibilitySource,
    private val navHostMounter: PermissionResultNavHostMounter
) : MainPermissionController.Listener {
    override fun onAudioPermissionResult() {
        if (audioPermissionStatusSource.hasAudioPermission()) {
            libraryLoader.loadLibrary(false)
        }
        if (onboardingVisibilitySource.showOnboarding()) {
            navHostMounter.mountNavHostShell()
        }
    }
}
