package app.yukine

internal class OnboardingController(
    private val listener: Listener
) {
    interface Listener {
        fun hasAudioPermission(): Boolean

        fun hasNotificationPermission(): Boolean

        fun requestNeededPermissions()

        fun mountNavHostShell()

        fun loadLibrary(allowCachedFirst: Boolean)

        fun navigateToNetworkTabPage(page: String)

        fun renderAndPersistSelectedTab()

        fun openPlaylistM3uFilePicker()

        fun onboardingCompleted()
    }

    private var visible: Boolean = true
    private var libraryScanCompleted: Boolean = false
    private var libraryScanInProgress: Boolean = false

    fun initialize(shouldShow: Boolean) {
        visible = shouldShow
    }

    fun showOnboarding(): Boolean = visible

    fun libraryScanCompleted(): Boolean = libraryScanCompleted

    fun libraryScanInProgress(): Boolean = libraryScanInProgress

    fun finishOnboarding() {
        completeOnboarding { listener.renderAndPersistSelectedTab() }
    }

    fun openStreamingFromOnboarding() {
        completeOnboarding { listener.navigateToNetworkTabPage(MainRoutes.NETWORK_STREAMING) }
    }

    fun scanLibraryFromOnboarding() {
        if (!listener.hasAudioPermission()) {
            listener.requestNeededPermissions()
            listener.mountNavHostShell()
            return
        }
        libraryScanInProgress = true
        listener.mountNavHostShell()
        listener.loadLibrary(false)
    }

    fun importPlaylistFromOnboarding() {
        if (!canFinishOnboarding()) {
            listener.mountNavHostShell()
            return
        }
        listener.openPlaylistM3uFilePicker()
    }

    fun onLibraryScanResult(canScan: Boolean) {
        if (visible && libraryScanInProgress) {
            libraryScanInProgress = false
            libraryScanCompleted = canScan
        }
    }

    fun canFinishOnboarding(): Boolean {
        return listener.hasAudioPermission()
            && listener.hasNotificationPermission()
            && libraryScanCompleted
    }

    fun onboardingMissingSetupMessage(): String {
        val missing = ArrayList<String>()
        if (!listener.hasAudioPermission()) {
            missing.add("音频权限")
        }
        if (!listener.hasNotificationPermission()) {
            missing.add("通知权限")
        }
        if (!libraryScanCompleted) {
            missing.add(if (libraryScanInProgress) "等待曲库扫描完成" else "扫描本地曲库")
        }
        return "完成后才能进入：" + missing.joinToString("、")
    }

    private fun completeOnboarding(afterComplete: Runnable?) {
        if (!canFinishOnboarding()) {
            listener.mountNavHostShell()
            return
        }
        visible = false
        listener.onboardingCompleted()
        listener.mountNavHostShell()
        afterComplete?.run()
    }
}
